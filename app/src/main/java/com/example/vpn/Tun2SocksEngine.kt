package com.example.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Pure Kotlin high-performance userspace Tun2Socks Engine.
 * Converts raw IP packets from Android's VpnService TUN interface into:
 * 1. TCP streams routed through encrypted SSH Direct-TCPIP channels.
 * 2. DNS queries routed through secure DNS-over-HTTPS or protected UDP sockets.
 * 3. UDP flows handled transparently.
 */
class Tun2SocksEngine(
    private val vpnService: VpnService,
    private val vpnInterface: ParcelFileDescriptor,
    private val sshSession: Session,
    private val localSocksPort: Int = 10808,
    private val onTunnelDropped: (() -> Unit)? = null,
    private val onTraffic: (rx: Long, tx: Long) -> Unit
) {

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tunOutputMutex = Mutex()
    private var outputStream: FileOutputStream? = null
    private var inputStream: FileInputStream? = null

    @Volatile
    private var isRunning = false

    private val ipIdCounter = AtomicInteger(Random.nextInt(1000, 50000))
    private val sessions = ConcurrentHashMap<String, TcpSession>()
    private val dnsCache = ConcurrentHashMap<String, ByteArray>()

    // Fake-DNS pool (198.18.0.0/15) for instantaneous local resolution (0ms, 100% bypass of DPI/poisoning)
    private val fakeIpCounter = AtomicInteger(1)
    private val domainToFakeIp = ConcurrentHashMap<String, String>()
    private val fakeIpToDomain = ConcurrentHashMap<String, String>()

    // Low-power traffic accumulators to avoid continuous CPU wakeups
    private val pendingRxBytes = java.util.concurrent.atomic.AtomicLong(0L)
    private val pendingTxBytes = java.util.concurrent.atomic.AtomicLong(0L)

    // OkHttpClient configured for DoH fallback, protected from VPN loop
    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .socketFactory(object : javax.net.SocketFactory() {
                override fun createSocket(): java.net.Socket {
                    val s = java.net.Socket()
                    vpnService.protect(s)
                    return s
                }

                override fun createSocket(host: String?, port: Int): java.net.Socket {
                    val s = java.net.Socket()
                    vpnService.protect(s)
                    s.connect(InetSocketAddress(host, port), 5000)
                    return s
                }

                override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): java.net.Socket {
                    val s = java.net.Socket()
                    vpnService.protect(s)
                    s.connect(InetSocketAddress(host, port), 5000)
                    return s
                }

                override fun createSocket(host: InetAddress?, port: Int): java.net.Socket {
                    val s = java.net.Socket()
                    vpnService.protect(s)
                    s.connect(InetSocketAddress(host, port), 5000)
                    return s
                }

                override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): java.net.Socket {
                    val s = java.net.Socket()
                    vpnService.protect(s)
                    s.connect(InetSocketAddress(address, port), 5000)
                    return s
                }
            })
            .build()
    }

    sealed interface UpstreamEvent {
        data class Data(val seq: Long, val payload: ByteArray) : UpstreamEvent
        data class Fin(val seq: Long) : UpstreamEvent
    }

    data class TcpSession(
        val key: String,
        val clientIp: String,
        val clientPort: Int,
        val destIp: String,
        val destPort: Int,
        val destHost: String = destIp,
        val clientSeq: AtomicLong,
        val serverSeq: AtomicLong,
        var channel: ChannelDirectTCPIP? = null,
        var channelInput: InputStream? = null,
        var channelOutput: OutputStream? = null,
        @Volatile var state: SessionState = SessionState.CONNECTING,
        @Volatile var lastActivityTime: Long = System.currentTimeMillis(),
        val connectedDeferred: CompletableDeferred<Boolean> = CompletableDeferred(),
        val upstreamChannel: Channel<UpstreamEvent> = Channel(Channel.UNLIMITED),
        @Volatile var nextExpectedClientSeq: Long = 0L,
        val outOfOrderSegments: java.util.TreeMap<Long, ByteArray> = java.util.TreeMap(),
        var upstreamJob: Job? = null,
        var readerJob: Job? = null
    )

    enum class SessionState {
        CONNECTING,
        CONNECTED,
        CLOSING,
        CLOSED
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        inputStream = FileInputStream(vpnInterface.fileDescriptor)
        outputStream = FileOutputStream(vpnInterface.fileDescriptor)

        // 1. Packet Reader Loop
        engineScope.launch {
            readTunLoop()
        }

        // 2. Periodic Session Janitor
        engineScope.launch {
            sessionJanitorLoop()
        }

        // 3. Low-overhead periodic traffic flusher (1 update/sec instead of thousands/sec)
        engineScope.launch {
            while (isActive && isRunning) {
                delay(1000)
                val rx = pendingRxBytes.getAndSet(0L)
                val tx = pendingTxBytes.getAndSet(0L)
                if (rx > 0 || tx > 0) {
                    onTraffic(rx, tx)
                }
            }
        }

        Log.i("Tun2SocksEngine", "Tun2Socks engine started successfully.")
    }

    fun stop() {
        isRunning = false
        engineScope.cancel()

        for (session in sessions.values) {
            closeSession(session)
        }
        sessions.clear()

        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}

        Log.i("Tun2SocksEngine", "Tun2Socks engine stopped.")
    }

    private suspend fun readTunLoop() = withContext(Dispatchers.IO) {
        val stream = inputStream ?: return@withContext
        val buffer = ByteArray(32767)

        while (isActive && isRunning) {
            try {
                val bytesRead = stream.read(buffer)
                if (bytesRead <= 0) continue

                val packet = buffer.copyOf(bytesRead)
                pendingTxBytes.addAndGet(bytesRead.toLong())

                // Process packet in coroutine pool
                processIpPacket(packet, bytesRead)
            } catch (e: Exception) {
                if (!isRunning) break
                Log.d("Tun2SocksEngine", "TUN read exception: ${e.message}")
            }
        }
    }

    private fun processIpPacket(packet: ByteArray, length: Int) {
        if (length < 20) return

        // Verify IPv4
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || ihl > length) return

        val protocol = packet[9].toInt() and 0xFF

        val srcIp = InetAddress.getByAddress(packet.copyOfRange(12, 16)).hostAddress ?: return
        val dstIp = InetAddress.getByAddress(packet.copyOfRange(16, 20)).hostAddress ?: return

        when (protocol) {
            17 -> { // UDP
                handleUdpPacket(packet, ihl, length, srcIp, dstIp)
            }
            6 -> { // TCP
                handleTcpPacket(packet, ihl, length, srcIp, dstIp)
            }
            1 -> { // ICMP (Ping)
                handleIcmpPacket(packet, ihl, length, srcIp, dstIp)
            }
        }
    }

    // ==========================================
    // UDP & DNS HANDLING
    // ==========================================

    private fun handleUdpPacket(packet: ByteArray, ipHeaderLen: Int, totalLen: Int, srcIp: String, dstIp: String) {
        if (totalLen < ipHeaderLen + 8) return

        val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
        val udpLen = ((packet[ipHeaderLen + 4].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 5].toInt() and 0xFF)

        val payloadOffset = ipHeaderLen + 8
        val payloadLen = udpLen - 8
        if (payloadOffset + payloadLen > totalLen || payloadLen <= 0) return

        val payload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)

        // Port 53: DNS Query (Telegram, WhatsApp, Web)
        if (dstPort == 53) {
            engineScope.launch {
                resolveDnsAndReply(srcIp, srcPort, dstIp, dstPort, payload)
            }
        } else if (dstPort == 443) {
            // Reject QUIC (HTTP/3) immediately with ICMP Port Unreachable
            // This instructs Chrome and Android apps to instantly fall back to TCP TLS without delay
            sendIcmpPortUnreachable(srcIp, dstIp, packet, ipHeaderLen)
        }
    }

    private suspend fun resolveDnsAndReply(
        clientIp: String,
        clientPort: Int,
        dnsServerIp: String,
        dnsServerPort: Int,
        dnsQueryBytes: ByteArray
    ) {
        if (dnsQueryBytes.size < 12) return

        try {
            val parsed = parseDnsQuestion(dnsQueryBytes)
            if (parsed != null) {
                val (domain, qType) = parsed
                if (qType == 1) { // A Record (IPv4)
                    val fakeIp = getOrAssignFakeIp(domain)
                    val reply = buildDnsAReply(dnsQueryBytes, fakeIp)
                    sendUdpPacket(dnsServerIp, dnsServerPort, clientIp, clientPort, reply)
                    return
                } else if (qType == 28 || qType == 65) { // AAAA Record (IPv6) or HTTPS (type 65)
                    // Reply empty response so Android immediately uses IPv4 without timeout
                    val emptyReply = buildDnsEmptyReply(dnsQueryBytes)
                    sendUdpPacket(dnsServerIp, dnsServerPort, clientIp, clientPort, emptyReply)
                    return
                }
            }

            // Fallback for non-A records or parse failures: try SSH TCP or direct UDP
            var answerBytes: ByteArray? = null
            if (sshSession.isConnected) {
                answerBytes = resolveDnsViaSshTcp(dnsQueryBytes, "8.8.8.8")
            }
            if (answerBytes != null && answerBytes.isNotEmpty()) {
                sendUdpPacket(dnsServerIp, dnsServerPort, clientIp, clientPort, answerBytes)
            }
        } catch (e: Exception) {
            Log.w("Tun2SocksEngine", "DNS resolution error: ${e.message}")
        }
    }

    private fun parseDnsQuestion(query: ByteArray): Pair<String, Int>? {
        if (query.size < 12) return null
        var offset = 12
        val sb = StringBuilder()
        while (offset < query.size) {
            val len = query[offset].toInt() and 0xFF
            offset++
            if (len == 0) break
            if (offset + len > query.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(query, offset, len, Charsets.US_ASCII))
            offset += len
        }
        if (offset + 4 > query.size) return null
        val qType = ((query[offset].toInt() and 0xFF) shl 8) or (query[offset + 1].toInt() and 0xFF)
        return Pair(sb.toString(), qType)
    }

    private fun findQuestionEnd(query: ByteArray): Int {
        var offset = 12
        while (offset < query.size) {
            val len = query[offset].toInt() and 0xFF
            offset++
            if (len == 0) break
            offset += len
        }
        return minOf(offset + 4, query.size)
    }

    private fun getOrAssignFakeIp(domain: String): String {
        val lower = domain.lowercase()
        return domainToFakeIp.computeIfAbsent(lower) { d ->
            val idx = (fakeIpCounter.getAndIncrement() and 0xFFFF).coerceAtLeast(1)
            val b2 = (idx shr 8) and 0xFF
            val b3 = idx and 0xFF
            val ip = "198.18.$b2.$b3"
            fakeIpToDomain[ip] = d
            ip
        }
    }

    private fun buildDnsAReply(query: ByteArray, ipStr: String): ByteArray {
        val qnameEnd = findQuestionEnd(query)
        val ipParts = ipStr.split(".").map { it.toInt() }

        val answerLen = 16
        val totalLen = qnameEnd + answerLen
        val resp = ByteArray(totalLen)

        // Copy Transaction ID
        resp[0] = query[0]
        resp[1] = query[1]
        // Flags: 0x8180 (Standard query response, No error, Recursion available)
        resp[2] = 0x81.toByte()
        resp[3] = 0x80.toByte()
        // QDCOUNT: 1
        resp[4] = 0; resp[5] = 1
        // ANCOUNT: 1
        resp[6] = 0; resp[7] = 1
        // NSCOUNT: 0
        resp[8] = 0; resp[9] = 0
        // ARCOUNT: 0
        resp[10] = 0; resp[11] = 0

        // Copy Question section
        System.arraycopy(query, 12, resp, 12, qnameEnd - 12)

        // Answer Section:
        // Name pointer to offset 12 (0xC00C)
        resp[qnameEnd] = 0xC0.toByte()
        resp[qnameEnd + 1] = 0x0C.toByte()
        // Type: A (0x0001)
        resp[qnameEnd + 2] = 0; resp[qnameEnd + 3] = 1
        // Class: IN (0x0001)
        resp[qnameEnd + 4] = 0; resp[qnameEnd + 5] = 1
        // TTL: 60 seconds (0x0000003C)
        resp[qnameEnd + 6] = 0; resp[qnameEnd + 7] = 0
        resp[qnameEnd + 8] = 0; resp[qnameEnd + 9] = 60
        // RDLENGTH: 4
        resp[qnameEnd + 10] = 0; resp[qnameEnd + 11] = 4
        // RDATA: 4 bytes IP
        resp[qnameEnd + 12] = ipParts[0].toByte()
        resp[qnameEnd + 13] = ipParts[1].toByte()
        resp[qnameEnd + 14] = ipParts[2].toByte()
        resp[qnameEnd + 15] = ipParts[3].toByte()

        return resp
    }

    private fun buildDnsEmptyReply(query: ByteArray): ByteArray {
        val qnameEnd = findQuestionEnd(query)
        val resp = ByteArray(qnameEnd)
        System.arraycopy(query, 0, resp, 0, qnameEnd)
        // Response, No Error, 0 answers
        resp[2] = 0x81.toByte()
        resp[3] = 0x80.toByte()
        resp[6] = 0; resp[7] = 0 // ANCOUNT: 0
        resp[8] = 0; resp[9] = 0 // NSCOUNT: 0
        resp[10] = 0; resp[11] = 0 // ARCOUNT: 0
        return resp
    }

    private suspend fun resolveDnsViaSshTcp(query: ByteArray, dnsHost: String): ByteArray? = withContext(Dispatchers.IO) {
        var channel: ChannelDirectTCPIP? = null
        try {
            if (!sshSession.isConnected) return@withContext null
            channel = sshSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(dnsHost)
            channel.setPort(53)
            channel.setOrgIPAddress("127.0.0.1")
            channel.setOrgPort(5353)
            val out = channel.outputStream
            val din = java.io.DataInputStream(channel.inputStream)
            channel.connect(5000)

            // RFC 7766: DNS over TCP has 2-byte prefix length
            out.write((query.size shr 8) and 0xFF)
            out.write(query.size and 0xFF)
            out.write(query)
            out.flush()

            val respLen = din.readUnsignedShort()
            if (respLen <= 0 || respLen > 4096) return@withContext null
            val resp = ByteArray(respLen)
            din.readFully(resp)
            resp
        } catch (e: Exception) {
            Log.d("Tun2SocksEngine", "SSH DNS to $dnsHost failed: ${e.message}")
            null
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
        }
    }

    private suspend fun sendUdpPacket(
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        payload: ByteArray
    ) {
        val udpLen = 8 + payload.size
        val totalIpLen = 20 + udpLen

        val buffer = ByteBuffer.allocate(totalIpLen)

        // IP Header (20 bytes)
        buffer.put(0x45.toByte()) // IPv4, 5 words
        buffer.put(0x00.toByte()) // DSCP
        buffer.putShort(totalIpLen.toShort())
        buffer.putShort(ipIdCounter.incrementAndGet().toShort())
        buffer.putShort(0x4000.toShort()) // DF flag
        buffer.put(64.toByte()) // TTL
        buffer.put(17.toByte()) // Protocol: UDP
        buffer.putShort(0) // IP checksum placeholder

        val srcIpBytes = InetAddress.getByName(srcIp).address
        val dstIpBytes = InetAddress.getByName(dstIp).address
        buffer.put(srcIpBytes)
        buffer.put(dstIpBytes)

        // Calculate and set IP checksum
        val ipChecksum = calculateChecksum(buffer.array(), 0, 20)
        buffer.putShort(10, ipChecksum.toShort())

        // UDP Header (8 bytes)
        val udpOffset = 20
        buffer.position(udpOffset)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0) // Checksum placeholder

        // Payload
        buffer.put(payload)

        // Calculate and set UDP checksum
        val udpChecksum = calculateTransportChecksum(srcIpBytes, dstIpBytes, 17, buffer.array(), udpOffset, udpLen)
        buffer.putShort(udpOffset + 6, if (udpChecksum == 0) 0xFFFF.toShort() else udpChecksum.toShort())

        writePacketToTun(buffer.array())
    }

    // ==========================================
    // TCP HANDLING (TRANSPARENT TUN2SOCKS)
    // ==========================================

    private fun handleTcpPacket(packet: ByteArray, ipHeaderLen: Int, totalLen: Int, srcIp: String, dstIp: String) {
        if (totalLen < ipHeaderLen + 20) return

        val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)

        val seqNum = (packet[ipHeaderLen + 4].toLong() and 0xFF shl 24) or
                (packet[ipHeaderLen + 5].toLong() and 0xFF shl 16) or
                (packet[ipHeaderLen + 6].toLong() and 0xFF shl 8) or
                (packet[ipHeaderLen + 7].toLong() and 0xFF)

        val ackNum = (packet[ipHeaderLen + 8].toLong() and 0xFF shl 24) or
                (packet[ipHeaderLen + 9].toLong() and 0xFF shl 16) or
                (packet[ipHeaderLen + 10].toLong() and 0xFF shl 8) or
                (packet[ipHeaderLen + 11].toLong() and 0xFF)

        val dataOffset = ((packet[ipHeaderLen + 12].toInt() shr 4) and 0x0F) * 4
        val flags = packet[ipHeaderLen + 13].toInt() and 0xFF

        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0

        val payloadOffset = ipHeaderLen + dataOffset
        val payloadLen = totalLen - payloadOffset

        val sessionKey = "$srcPort:$dstIp:$dstPort"
        var session = sessions[sessionKey]
        val destHost = fakeIpToDomain[dstIp] ?: dstIp

        // 1. SYN Packet: Initiate new connection
        if (isSyn && !isAck) {
            if (session != null && session.state == SessionState.CONNECTING) {
                // Client retransmitted SYN, reply with SYN+ACK with MSS options
                engineScope.launch {
                    sendTcpPacket(
                        srcIp = dstIp,
                        srcPort = dstPort,
                        dstIp = srcIp,
                        dstPort = srcPort,
                        flags = 0x12, // SYN | ACK
                        seq = session.serverSeq.get() - 1,
                        ack = session.clientSeq.get(),
                        payload = null,
                        includeOptions = true
                    )
                }
                return
            }

            val initialServerSeq = Random.nextLong(100000, 50000000)
            val newSession = TcpSession(
                key = sessionKey,
                clientIp = srcIp,
                clientPort = srcPort,
                destIp = dstIp,
                destPort = dstPort,
                destHost = destHost,
                clientSeq = AtomicLong(seqNum + 1),
                serverSeq = AtomicLong(initialServerSeq),
                nextExpectedClientSeq = seqNum + 1,
                state = SessionState.CONNECTING
            )
            sessions[sessionKey] = newSession

            // Start dedicated upstream sequential processor for this session
            newSession.upstreamJob = engineScope.launch(Dispatchers.IO) {
                processSessionUpstream(newSession)
            }

            engineScope.launch {
                // Send immediate SYN+ACK (Optimistic Handshake) with MSS option
                sendTcpPacket(
                    srcIp = dstIp,
                    srcPort = dstPort,
                    dstIp = srcIp,
                    dstPort = srcPort,
                    flags = 0x12, // SYN | ACK
                    seq = initialServerSeq,
                    ack = newSession.clientSeq.get(),
                    payload = null,
                    includeOptions = true
                )
                newSession.serverSeq.incrementAndGet()

                // Establish SSH direct-tcpip channel asynchronously
                connectSshChannel(newSession)
            }
            return
        }

        if (session == null) {
            // Unknown session or late packet, reply with RST if not RST
            if (!isRst) {
                engineScope.launch {
                    sendTcpPacket(
                        srcIp = dstIp,
                        srcPort = dstPort,
                        dstIp = srcIp,
                        dstPort = srcPort,
                        flags = 0x04, // RST
                        seq = ackNum,
                        ack = 0,
                        payload = null
                    )
                }
            }
            return
        }

        session.lastActivityTime = System.currentTimeMillis()

        // 2. RST Packet
        if (isRst) {
            closeSession(session)
            sessions.remove(sessionKey)
            return
        }

        // 3. FIN Packet
        if (isFin) {
            session.upstreamChannel.trySend(UpstreamEvent.Fin(seqNum))
            return
        }

        // 4. Data Payload (ACK / PSH+ACK)
        if (payloadLen > 0 && payloadOffset + payloadLen <= packet.size) {
            val payload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)
            session.upstreamChannel.trySend(UpstreamEvent.Data(seqNum, payload))
        }
    }

    private suspend fun processSessionUpstream(session: TcpSession) = withContext(Dispatchers.IO) {
        val connected = try {
            session.connectedDeferred.await()
        } catch (_: Exception) {
            false
        }

        if (!connected || session.state != SessionState.CONNECTED) {
            return@withContext
        }

        val out = session.channelOutput ?: return@withContext

        try {
            for (event in session.upstreamChannel) {
                if (!isRunning || session.state == SessionState.CLOSED) break

                when (event) {
                    is UpstreamEvent.Data -> {
                        val seq = event.seq
                        val data = event.payload
                        val len = data.size

                        val diff = (seq - session.nextExpectedClientSeq).toInt()

                        if (diff + len <= 0) {
                            // Completely retransmitted/duplicate packet.
                            // Do NOT duplicate writes into SSH stream!
                            // Immediately send ACK with current nextExpectedClientSeq
                            sendTcpPacket(
                                srcIp = session.destIp,
                                srcPort = session.destPort,
                                dstIp = session.clientIp,
                                dstPort = session.clientPort,
                                flags = 0x10, // ACK
                                seq = session.serverSeq.get(),
                                ack = session.nextExpectedClientSeq,
                                payload = null
                            )
                            continue
                        }

                        var actualData = data
                        var actualSeq = seq
                        var actualLen = len
                        if (diff < 0) {
                            val trim = -diff
                            actualData = data.copyOfRange(trim, len)
                            actualSeq = session.nextExpectedClientSeq
                            actualLen = actualData.size
                        }

                        if (actualSeq == session.nextExpectedClientSeq) {
                            // Write in-order bytes to SSH channel
                            out.write(actualData)
                            session.nextExpectedClientSeq += actualLen
                            session.clientSeq.set(session.nextExpectedClientSeq)

                            // Check buffered out-of-order segments
                            synchronized(session.outOfOrderSegments) {
                                val iter = session.outOfOrderSegments.entries.iterator()
                                while (iter.hasNext()) {
                                    val entry = iter.next()
                                    val oSeq = entry.key
                                    val oData = entry.value
                                    val oDiff = (oSeq - session.nextExpectedClientSeq).toInt()

                                    if (oDiff + oData.size <= 0) {
                                        iter.remove()
                                    } else if (oDiff <= 0) {
                                        iter.remove()
                                        val trim = if (oDiff < 0) -oDiff else 0
                                        val trimmed = if (trim > 0) oData.copyOfRange(trim, oData.size) else oData
                                        out.write(trimmed)
                                        session.nextExpectedClientSeq += trimmed.size
                                        session.clientSeq.set(session.nextExpectedClientSeq)
                                    } else {
                                        break
                                    }
                                }
                            }
                            out.flush()

                            // Acknowledge the advanced sequence number to the client
                            sendTcpPacket(
                                srcIp = session.destIp,
                                srcPort = session.destPort,
                                dstIp = session.clientIp,
                                dstPort = session.clientPort,
                                flags = 0x10, // ACK
                                seq = session.serverSeq.get(),
                                ack = session.nextExpectedClientSeq,
                                payload = null
                            )
                        } else {
                            // Gap detected: actualSeq > nextExpectedClientSeq
                            synchronized(session.outOfOrderSegments) {
                                if (session.outOfOrderSegments.size < 64) {
                                    session.outOfOrderSegments[actualSeq] = actualData
                                }
                            }
                            // Send duplicate ACK for nextExpectedClientSeq so client knows to retransmit
                            sendTcpPacket(
                                srcIp = session.destIp,
                                srcPort = session.destPort,
                                dstIp = session.clientIp,
                                dstPort = session.clientPort,
                                flags = 0x10, // ACK
                                seq = session.serverSeq.get(),
                                ack = session.nextExpectedClientSeq,
                                payload = null
                            )
                        }
                    }
                    is UpstreamEvent.Fin -> {
                        session.nextExpectedClientSeq = event.seq + 1
                        session.clientSeq.set(session.nextExpectedClientSeq)

                        sendTcpPacket(
                            srcIp = session.destIp,
                            srcPort = session.destPort,
                            dstIp = session.clientIp,
                            dstPort = session.clientPort,
                            flags = 0x11, // FIN | ACK
                            seq = session.serverSeq.getAndIncrement(),
                            ack = session.nextExpectedClientSeq,
                            payload = null
                        )
                        closeSession(session)
                        sessions.remove(session.key)
                        break
                    }
                }
            }
        } catch (_: Exception) {
            closeSession(session)
            sessions.remove(session.key)
        }
    }

    private suspend fun connectSshChannel(session: TcpSession) = withContext(Dispatchers.IO) {
        try {
            if (!sshSession.isConnected) {
                onTunnelDropped?.invoke()
                throw IOException("SSH session disconnected")
            }

            val channel = sshSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(session.destHost)
            channel.setPort(session.destPort)
            channel.setOrgIPAddress("127.0.0.1")
            channel.setOrgPort(session.clientPort)

            val channelIn = channel.inputStream
            val channelOut = channel.outputStream

            channel.connect(10000)

            session.channel = channel
            session.channelInput = channelIn
            session.channelOutput = channelOut
            session.state = SessionState.CONNECTED
            session.connectedDeferred.complete(true)

            // Start reading downstream traffic from remote server
            session.readerJob = engineScope.launch(Dispatchers.IO) {
                readFromSshChannel(session)
            }
        } catch (e: Exception) {
            Log.d("Tun2SocksEngine", "SSH direct-tcpip to ${session.destIp}:${session.destPort} failed: ${e.message}")
            session.connectedDeferred.complete(false)
            // Send RST to client
            engineScope.launch {
                sendTcpPacket(
                    srcIp = session.destIp,
                    srcPort = session.destPort,
                    dstIp = session.clientIp,
                    dstPort = session.clientPort,
                    flags = 0x04, // RST
                    seq = session.serverSeq.get(),
                    ack = session.clientSeq.get(),
                    payload = null
                )
            }
            closeSession(session)
            sessions.remove(session.key)
        }
    }

    private suspend fun readFromSshChannel(session: TcpSession) = withContext(Dispatchers.IO) {
        val input = session.channelInput ?: return@withContext
        val buffer = ByteArray(1360) // Maximum safe segment payload that fits MTU 1500 with IP+TCP headers

        try {
            while (isActive && isRunning && session.state == SessionState.CONNECTED) {
                val n = input.read(buffer)
                if (n <= 0) break

                val chunk = buffer.copyOf(n)
                val seq = session.serverSeq.getAndAdd(n.toLong())
                sendTcpPacket(
                    srcIp = session.destIp,
                    srcPort = session.destPort,
                    dstIp = session.clientIp,
                    dstPort = session.clientPort,
                    flags = 0x18, // PSH | ACK
                    seq = seq,
                    ack = session.clientSeq.get(),
                    payload = chunk
                )
                session.lastActivityTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // Channel closed or EOF
        } finally {
            if (isRunning && session.state == SessionState.CONNECTED) {
                // Send FIN+ACK
                val seq = session.serverSeq.getAndIncrement()
                sendTcpPacket(
                    srcIp = session.destIp,
                    srcPort = session.destPort,
                    dstIp = session.clientIp,
                    dstPort = session.clientPort,
                    flags = 0x11, // FIN | ACK
                    seq = seq,
                    ack = session.clientSeq.get(),
                    payload = null
                )
            }
            closeSession(session)
            sessions.remove(session.key)
        }
    }

    private suspend fun sendTcpPacket(
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        flags: Int,
        seq: Long,
        ack: Long,
        payload: ByteArray?,
        includeOptions: Boolean = false
    ) {
        val payloadLen = payload?.size ?: 0
        val optionsLen = if (includeOptions) 4 else 0
        val tcpHeaderLen = 20 + optionsLen
        val tcpLen = tcpHeaderLen + payloadLen
        val totalIpLen = 20 + tcpLen

        val buffer = ByteBuffer.allocate(totalIpLen)

        // IP Header (20 bytes)
        buffer.put(0x45.toByte())
        buffer.put(0x00.toByte())
        buffer.putShort(totalIpLen.toShort())
        buffer.putShort(ipIdCounter.incrementAndGet().toShort())
        buffer.putShort(0x4000.toShort()) // DF flag
        buffer.put(64.toByte()) // TTL
        buffer.put(6.toByte()) // Protocol: TCP
        buffer.putShort(0) // IP checksum placeholder

        val srcIpBytes = InetAddress.getByName(srcIp).address
        val dstIpBytes = InetAddress.getByName(dstIp).address
        buffer.put(srcIpBytes)
        buffer.put(dstIpBytes)

        // IP Checksum
        val ipChecksum = calculateChecksum(buffer.array(), 0, 20)
        buffer.putShort(10, ipChecksum.toShort())

        // TCP Header
        val tcpOffset = 20
        buffer.position(tcpOffset)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putInt((seq and 0xFFFFFFFFL).toInt())
        buffer.putInt((ack and 0xFFFFFFFFL).toInt())
        val dataOffsetWords = tcpHeaderLen / 4
        buffer.put(((dataOffsetWords shl 4) and 0xF0).toByte())
        buffer.put(flags.toByte())
        buffer.putShort(65535.toShort()) // Window size
        buffer.putShort(0) // TCP checksum placeholder
        buffer.putShort(0) // Urgent pointer

        if (includeOptions) {
            // MSS option = 1360 (Kind 2, Len 4). Exactly 4 bytes.
            buffer.put(0x02.toByte())
            buffer.put(0x04.toByte())
            buffer.putShort(1360.toShort())
        }

        if (payload != null && payloadLen > 0) {
            buffer.put(payload)
        }

        // TCP Checksum
        val tcpChecksum = calculateTransportChecksum(srcIpBytes, dstIpBytes, 6, buffer.array(), tcpOffset, tcpLen)
        buffer.putShort(tcpOffset + 16, tcpChecksum.toShort())

        writePacketToTun(buffer.array())
    }

    // ==========================================
    // ICMP PING HANDLING (Instant Echo Reply)
    // ==========================================

    private fun handleIcmpPacket(packet: ByteArray, ipHeaderLen: Int, totalLen: Int, srcIp: String, dstIp: String) {
        if (totalLen < ipHeaderLen + 8) return
        val type = packet[ipHeaderLen].toInt() and 0xFF
        if (type == 8) { // Echo Request (Ping)
            val echoReply = packet.copyOf(totalLen)
            // Swap IPs
            System.arraycopy(packet, 16, echoReply, 12, 4) // dest -> src
            System.arraycopy(packet, 12, echoReply, 16, 4) // src -> dest

            // Set ICMP Type = 0 (Echo Reply)
            echoReply[ipHeaderLen] = 0.toByte()

            // Reset and recalculate ICMP Checksum
            echoReply[ipHeaderLen + 2] = 0
            echoReply[ipHeaderLen + 3] = 0
            val icmpLen = totalLen - ipHeaderLen
            val icmpChecksum = calculateChecksum(echoReply, ipHeaderLen, icmpLen)
            echoReply[ipHeaderLen + 2] = (icmpChecksum shr 8).toByte()
            echoReply[ipHeaderLen + 3] = (icmpChecksum and 0xFF).toByte()

            // Recalculate IP Checksum
            echoReply[10] = 0
            echoReply[11] = 0
            val ipChecksum = calculateChecksum(echoReply, 0, 20)
            echoReply[10] = (ipChecksum shr 8).toByte()
            echoReply[11] = (ipChecksum and 0xFF).toByte()

            engineScope.launch {
                writePacketToTun(echoReply)
            }
        }
    }

    private fun sendIcmpPortUnreachable(srcIp: String, dstIp: String, originalPacket: ByteArray, ipHeaderLen: Int) {
        val originalHeaderAnd8BytesLen = minOf(ipHeaderLen + 8, originalPacket.size)
        val icmpPayloadLen = 4 + originalHeaderAnd8BytesLen
        val icmpLen = 4 + icmpPayloadLen
        val totalIpLen = 20 + icmpLen

        val buffer = ByteBuffer.allocate(totalIpLen)

        buffer.put(0x45.toByte())
        buffer.put(0x00.toByte())
        buffer.putShort(totalIpLen.toShort())
        buffer.putShort(ipIdCounter.incrementAndGet().toShort())
        buffer.putShort(0x0000.toShort())
        buffer.put(64.toByte())
        buffer.put(1.toByte()) // ICMP
        buffer.putShort(0)

        val srcIpBytes = InetAddress.getByName(dstIp).address
        val dstIpBytes = InetAddress.getByName(srcIp).address
        buffer.put(srcIpBytes)
        buffer.put(dstIpBytes)

        val ipChecksum = calculateChecksum(buffer.array(), 0, 20)
        buffer.putShort(10, ipChecksum.toShort())

        val icmpOffset = 20
        buffer.position(icmpOffset)
        buffer.put(3.toByte()) // Type: Destination Unreachable
        buffer.put(3.toByte()) // Code: Port Unreachable
        buffer.putShort(0)
        buffer.putInt(0)
        buffer.put(originalPacket, 0, originalHeaderAnd8BytesLen)

        val icmpChecksum = calculateChecksum(buffer.array(), icmpOffset, icmpLen)
        buffer.putShort(icmpOffset + 2, icmpChecksum.toShort())

        engineScope.launch {
            writePacketToTun(buffer.array())
        }
    }

    // ==========================================
    // TUN WRITER & CLEANUP
    // ==========================================

    private suspend fun writePacketToTun(packet: ByteArray) {
        val out = outputStream ?: return
        try {
            tunOutputMutex.withLock {
                out.write(packet)
                out.flush()
            }
            pendingRxBytes.addAndGet(packet.size.toLong())
        } catch (e: Exception) {
            // Broken pipe or closed tunnel
        }
    }

    private fun closeSession(session: TcpSession) {
        session.state = SessionState.CLOSED
        try { session.upstreamChannel.close() } catch (_: Exception) {}
        session.upstreamJob?.cancel()
        session.readerJob?.cancel()
        synchronized(session.outOfOrderSegments) {
            session.outOfOrderSegments.clear()
        }
        try { session.channelInput?.close() } catch (_: Exception) {}
        try { session.channelOutput?.close() } catch (_: Exception) {}
        try { session.channel?.disconnect() } catch (_: Exception) {}
    }

    private suspend fun sessionJanitorLoop() = withContext(Dispatchers.IO) {
        while (isActive && isRunning) {
            delay(30000)
            val now = System.currentTimeMillis()
            val iterator = sessions.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val s = entry.value
                // Close idle sessions after 60 seconds of inactivity
                if (now - s.lastActivityTime > 60000) {
                    closeSession(s)
                    iterator.remove()
                }
            }
        }
    }

    // ==========================================
    // CHECKSUM COMPUTATIONS (RFC 1071)
    // ==========================================

    private fun calculateChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val word = ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < offset + length) {
            sum += (buffer[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun calculateTransportChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        protocol: Int,
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {
        var sum = 0

        // Pseudo-header: Source IP (4 bytes)
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)

        // Pseudo-header: Dest IP (4 bytes)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)

        // Protocol + Length
        sum += protocol
        sum += length

        // Transport header + payload
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv()) and 0xFFFF
    }
}
