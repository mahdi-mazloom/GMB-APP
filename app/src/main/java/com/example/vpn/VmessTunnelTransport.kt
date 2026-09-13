package com.example.vpn

import android.net.VpnService
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-performance VMess AEAD over WebSocket transport implementation in pure Kotlin.
 * Seamlessly integrates into Tun2SocksEngine and LocalSocksServer to provide direct
 * hardware-accelerated proxying for Irancell and Rightel connections.
 */
class VmessTunnelTransport(
    private val vpnService: VpnService?,
    private val config: VmessConfig = VmessDefaultConfig.INSTANCE,
    private val onTunnelDropped: (() -> Unit)? = null
) : TunnelTransport {

    companion object {
        private const val TAG = "VmessTunnelTransport"
        private const val KDF_SALT_CONST = "VMess AEAD KDF"

        private fun readExact(inStream: InputStream, b: ByteArray) {
            var read = 0
            while (read < b.size) {
                val r = inStream.read(b, read, b.size - read)
                if (r < 0) throw IOException("Unexpected EOF during readExact")
                read += r
            }
        }
    }

    override val protocolName: String = "VMess AEAD WebSocket"

    @Volatile
    private var active = true

    override val isConnected: Boolean
        get() = active

    override suspend fun openTcpStream(destHost: String, destPort: Int, timeoutMs: Int): TunnelStream = withContext(Dispatchers.IO) {
        if (!active) {
            onTunnelDropped?.invoke()
            throw IOException("VMess transport is closed")
        }

        val socket = Socket()
        socket.tcpNoDelay = true
        socket.soTimeout = 30000

        // CRITICAL: Protect socket from Android TUN routing loop
        vpnService?.protect(socket)

        try {
            socket.connect(InetSocketAddress(config.serverHost, config.serverPort), timeoutMs)
        } catch (e: Exception) {
            socket.close()
            Log.e(TAG, "Failed to connect to VMess server ${config.serverHost}:${config.serverPort}: ${e.message}")
            throw IOException("Cannot connect to VMess server: ${e.message}", e)
        }

        val rawOut = socket.getOutputStream()
        val rawIn = socket.getInputStream()

        // 1. Perform WebSocket Handshake
        try {
            doWebSocketHandshake(rawOut, rawIn, config.wsHost, config.wsPath)
        } catch (e: Exception) {
            socket.close()
            Log.e(TAG, "WebSocket upgrade failed: ${e.message}")
            throw IOException("VMess WebSocket upgrade failed: ${e.message}", e)
        }

        // 2. VMess AEAD Handshake
        val random = SecureRandom()
        val uuidBytes = uuidToBytes(config.uuid)
        val cmdKey = md5(uuidBytes + "c48619fe-8f02-49e0-b9e9-edf763e17e21".toByteArray(Charsets.UTF_8))

        val nowSeconds = System.currentTimeMillis() / 1000L
        val authIdPlain = ByteArray(16)
        ByteBuffer.wrap(authIdPlain).putLong(nowSeconds)
        val rnd = ByteArray(4).also { random.nextBytes(it) }
        System.arraycopy(rnd, 0, authIdPlain, 8, 4)
        val crc = CRC32()
        crc.update(authIdPlain, 0, 12)
        val crcVal = crc.value.toInt()
        authIdPlain[12] = (crcVal ushr 24).toByte()
        authIdPlain[13] = (crcVal ushr 16).toByte()
        authIdPlain[14] = (crcVal ushr 8).toByte()
        authIdPlain[15] = crcVal.toByte()

        val authIdKey = kdf16(cmdKey, "AES Auth ID Encryption")
        val authId = aesEcbEncrypt(authIdKey, authIdPlain)

        val reqBodyKey = ByteArray(16).also { random.nextBytes(it) }
        val reqBodyIv = ByteArray(16).also { random.nextBytes(it) }
        val respBodyKey = md5(reqBodyKey)
        val respBodyIv = md5(reqBodyIv)
        val respHeaderByte = ByteArray(1).also { random.nextBytes(it) }[0]

        // 3. Build plain VMess header
        val headerOut = ByteArrayOutputStream()
        headerOut.write(1) // Version
        headerOut.write(reqBodyIv)
        headerOut.write(reqBodyKey)
        headerOut.write(respHeaderByte.toInt() and 0xFF)
        headerOut.write(1) // Opt: ChunkStream
        headerOut.write(0x43) // P: 4 padding, Sec: 3 (AES-128-GCM)
        headerOut.write(0) // Reserved
        headerOut.write(1) // Cmd: TCP
        headerOut.write((destPort ushr 8) and 0xFF)
        headerOut.write(destPort and 0xFF)

        val isIpv4 = isIpv4Address(destHost)
        if (isIpv4) {
            headerOut.write(1) // IPv4 type
            headerOut.write(InetAddress.getByName(destHost).address)
        } else {
            headerOut.write(2) // Domain type
            val domainBytes = destHost.toByteArray(Charsets.UTF_8)
            headerOut.write(domainBytes.size and 0xFF)
            headerOut.write(domainBytes)
        }

        val padding = ByteArray(4).also { random.nextBytes(it) }
        headerOut.write(padding)

        val headerPlainNoFnv = headerOut.toByteArray()
        val fnv = fnv1a(headerPlainNoFnv)
        val fullHeaderPlain = ByteBuffer.allocate(headerPlainNoFnv.size + 4)
            .put(headerPlainNoFnv)
            .putInt(fnv)
            .array()

        // 4. Seal Header with AEAD
        val headerLenKey = kdf16(cmdKey, "VMess Header AEAD Key_Length", authId)
        val headerLenIv = kdf(cmdKey, "VMess Header AEAD Nonce_Length", authId).copyOfRange(0, 12)
        val headerLenBytes = ByteBuffer.allocate(2).putShort(fullHeaderPlain.size.toShort()).array()
        val encHeaderLen = aesGcmEncrypt(headerLenKey, headerLenIv, headerLenBytes, authId)

        val headerPayloadKey = kdf16(cmdKey, "VMess Header AEAD Key", authId)
        val headerPayloadIv = kdf(cmdKey, "VMess Header AEAD Nonce", authId).copyOfRange(0, 12)
        val encHeaderPayload = aesGcmEncrypt(headerPayloadKey, headerPayloadIv, fullHeaderPlain, authId)

        val fullReqHeader = ByteArray(authId.size + encHeaderLen.size + encHeaderPayload.size)
        System.arraycopy(authId, 0, fullReqHeader, 0, authId.size)
        System.arraycopy(encHeaderLen, 0, fullReqHeader, authId.size, encHeaderLen.size)
        System.arraycopy(encHeaderPayload, 0, fullReqHeader, authId.size + encHeaderLen.size, encHeaderPayload.size)

        // Send initial VMess header frame
        writeWsBinaryFrame(rawOut, fullReqHeader)

        // 5. Read Server Response Header (Length: 18 bytes, followed by response header)
        val wsIn = WsInputStream(rawIn)

        val encRespLen = ByteArray(18)
        readExact(wsIn, encRespLen)
        val respLenKey = kdf16(respBodyKey, "AEAD Resp Header Len Key")
        val respLenIv = kdf(respBodyIv, "AEAD Resp Header Len IV").copyOfRange(0, 12)
        val decRespLenBytes = aesGcmDecrypt(respLenKey, respLenIv, encRespLen, null)
        val respHeaderLen = ByteBuffer.wrap(decRespLenBytes).short.toInt() and 0xFFFF

        val encRespHeader = ByteArray(respHeaderLen + 16)
        readExact(wsIn, encRespHeader)
        val respHeaderKey = kdf16(respBodyKey, "AEAD Resp Header Key")
        val respHeaderIv = kdf(respBodyIv, "AEAD Resp Header IV").copyOfRange(0, 12)
        val decRespHeader = aesGcmDecrypt(respHeaderKey, respHeaderIv, encRespHeader, null)

        if (decRespHeader.isEmpty() || decRespHeader[0] != respHeaderByte) {
            socket.close()
            throw IOException("VMess server auth mismatch: expected $respHeaderByte but got ${decRespHeader.getOrNull(0)}")
        }

        // 6. Return Streaming Tunnel
        val tunnelIn = VmessDecryptInputStream(wsIn, respBodyKey, respBodyIv)
        val tunnelOut = VmessEncryptOutputStream(rawOut, reqBodyKey, reqBodyIv)

        object : TunnelStream {
            override val input: InputStream = tunnelIn
            override val output: OutputStream = tunnelOut

            override fun close() {
                try { tunnelIn.close() } catch (_: Exception) {}
                try { tunnelOut.close() } catch (_: Exception) {}
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    override fun close() {
        active = false
    }

    // =========================================================================
    // WebSocket Framing
    // =========================================================================
    private fun doWebSocketHandshake(out: OutputStream, inStream: InputStream, wsHost: String, wsPath: String) {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val secKey = Base64.encodeToString(nonce, Base64.NO_WRAP)
        val req = "GET $wsPath HTTP/1.1\r\n" +
                "Host: $wsHost\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $secKey\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"

        out.write(req.toByteArray(Charsets.UTF_8))
        out.flush()

        val line = readHttpLine(inStream)
        if (!line.contains("101")) {
            throw IOException("Invalid WebSocket upgrade response: $line")
        }

        // Skip headers until empty line
        while (true) {
            val headerLine = readHttpLine(inStream)
            if (headerLine.isEmpty()) break
        }
    }

    private fun readHttpLine(inStream: InputStream): String {
        val sb = StringBuilder()
        var prev = 0
        while (true) {
            val b = inStream.read()
            if (b < 0) break
            if (prev == '\r'.code && b == '\n'.code) {
                if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
        return sb.toString()
    }

    private class WsInputStream(private val rawIn: InputStream) : InputStream() {
        private var currentBuf: ByteArray? = null
        private var pos = 0

        override fun read(): Int {
            val b = ByteArray(1)
            val r = read(b, 0, 1)
            return if (r < 0) -1 else (b[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (currentBuf == null || pos >= currentBuf!!.size) {
                currentBuf = readWsBinaryFrame(rawIn) ?: return -1
                pos = 0
            }
            val available = currentBuf!!.size - pos
            val toRead = Math.min(len, available)
            System.arraycopy(currentBuf!!, pos, b, off, toRead)
            pos += toRead
            return toRead
        }

        private fun readWsBinaryFrame(inStream: InputStream): ByteArray? {
            val b1 = inStream.read()
            if (b1 < 0) return null
            val b2 = inStream.read()
            if (b2 < 0) return null

            val opcode = b1 and 0x0F
            if (opcode == 0x08) return null // Connection close

            val isMasked = (b2 and 0x80) != 0
            var len = (b2 and 0x7F).toLong()

            if (len == 126L) {
                val hi = inStream.read()
                val lo = inStream.read()
                if (hi < 0 || lo < 0) return null
                len = ((hi shl 8) or lo).toLong()
            } else if (len == 127L) {
                var l = 0L
                for (i in 0 until 8) {
                    val b = inStream.read()
                    if (b < 0) return null
                    l = (l shl 8) or (b.toLong() and 0xFF)
                }
                len = l
            }

            val mask = if (isMasked) {
                val m = ByteArray(4)
                readExact(inStream, m)
                m
            } else null

            val payload = ByteArray(len.toInt())
            readExact(inStream, payload)

            if (mask != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }
            return payload
        }
    }

    private fun writeWsBinaryFrame(out: OutputStream, payload: ByteArray) {
        val maskKey = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val outBuf = ByteArrayOutputStream()

        outBuf.write(0x82) // FIN + Binary opcode
        val len = payload.size
        if (len <= 125) {
            outBuf.write(0x80 or len)
        } else if (len <= 65535) {
            outBuf.write(0x80 or 126)
            outBuf.write((len ushr 8) and 0xFF)
            outBuf.write(len and 0xFF)
        } else {
            outBuf.write(0x80 or 127)
            val bb = ByteBuffer.allocate(8).putLong(len.toLong())
            outBuf.write(bb.array())
        }

        outBuf.write(maskKey)
        val masked = ByteArray(len)
        for (i in 0 until len) {
            masked[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
        }
        outBuf.write(masked)

        out.write(outBuf.toByteArray())
        out.flush()
    }

    // =========================================================================
    // VMess AEAD Data Chunk Encrypt & Decrypt Streams
    // =========================================================================
    private inner class VmessEncryptOutputStream(
        private val rawOut: OutputStream,
        private val reqBodyKey: ByteArray,
        private val reqBodyIv: ByteArray
    ) : OutputStream() {
        private var chunkCount = 0
        private val buffer = ByteArrayOutputStream()
        private val maxChunkSize = 8192

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var remaining = len
            var offset = off
            while (remaining > 0) {
                val space = maxChunkSize - buffer.size()
                val toWrite = Math.min(remaining, space)
                buffer.write(b, offset, toWrite)
                offset += toWrite
                remaining -= toWrite

                if (buffer.size() >= maxChunkSize) {
                    flushChunk()
                }
            }
        }

        override fun flush() {
            if (buffer.size() > 0) {
                flushChunk()
            }
            rawOut.flush()
        }

        private fun flushChunk() {
            val plain = buffer.toByteArray()
            buffer.reset()
            if (plain.isEmpty()) return

            val nonce = generateChunkNonce(reqBodyIv, chunkCount)
            val encChunk = aesGcmEncrypt(reqBodyKey, nonce, plain, null)
            val totalSize = encChunk.size // ciphertext + 16-byte tag

            val packet = ByteBuffer.allocate(2 + totalSize)
                .putShort(totalSize.toShort())
                .put(encChunk)
                .array()

            writeWsBinaryFrame(rawOut, packet)
            chunkCount++
        }

        override fun close() {
            try { flush() } catch (_: Exception) {}
        }
    }

    private inner class VmessDecryptInputStream(
        private val wsIn: InputStream,
        private val respBodyKey: ByteArray,
        private val respBodyIv: ByteArray
    ) : InputStream() {
        private var chunkCount = 0
        private var currentPlain: ByteArray? = null
        private var pos = 0

        override fun read(): Int {
            val b = ByteArray(1)
            val r = read(b, 0, 1)
            return if (r < 0) -1 else (b[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (currentPlain == null || pos >= currentPlain!!.size) {
                val chunkLenBytes = ByteArray(2)
                val readBytes = wsIn.read(chunkLenBytes)
                if (readBytes < 0) return -1
                if (readBytes < 2) {
                    val second = wsIn.read()
                    if (second < 0) return -1
                    chunkLenBytes[1] = second.toByte()
                }

                val serverChunkLen = ByteBuffer.wrap(chunkLenBytes).short.toInt() and 0xFFFF
                if (serverChunkLen == 0) return -1

                val encServerChunk = ByteArray(serverChunkLen)
                readExact(wsIn, encServerChunk)

                val nonce = generateChunkNonce(respBodyIv, chunkCount)
                currentPlain = aesGcmDecrypt(respBodyKey, nonce, encServerChunk, null)
                pos = 0
                chunkCount++
            }

            val available = currentPlain!!.size - pos
            val toRead = Math.min(len, available)
            System.arraycopy(currentPlain!!, pos, b, off, toRead)
            pos += toRead
            return toRead
        }
    }

    // =========================================================================
    // Cryptographic Helpers (KDF, AES-ECB, AES-GCM, FNV1a)
    // =========================================================================
    private fun generateChunkNonce(iv: ByteArray, count: Int): ByteArray {
        val nonce = ByteArray(12)
        nonce[0] = (count ushr 8).toByte()
        nonce[1] = count.toByte()
        System.arraycopy(iv, 2, nonce, 2, 10)
        return nonce
    }

    private fun kdf(key: ByteArray, vararg paths: Any): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(KDF_SALT_CONST.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        var currentKey = mac.doFinal(key)

        for (path in paths) {
            val pBytes = when (path) {
                is String -> path.toByteArray(Charsets.UTF_8)
                is ByteArray -> path
                else -> path.toString().toByteArray(Charsets.UTF_8)
            }
            val stepMac = Mac.getInstance("HmacSHA256")
            stepMac.init(SecretKeySpec(currentKey, "HmacSHA256"))
            currentKey = stepMac.doFinal(pBytes)
        }
        return currentKey
    }

    private fun kdf16(key: ByteArray, vararg paths: Any): ByteArray =
        kdf(key, *paths).copyOfRange(0, 16)

    private fun aesEcbEncrypt(key: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(plain)
    }

    private fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(plain)
    }

    private fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, cipherText: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(cipherText)
    }

    private fun md5(data: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("MD5").digest(data)

    private fun fnv1a(data: ByteArray): Int {
        var hash = -2128831035 // 0x811C9DC5
        for (b in data) {
            hash = hash xor (b.toInt() and 0xFF)
            hash *= 16777619 // 0x01000193
        }
        return hash
    }

    private fun uuidToBytes(uuidStr: String): ByteArray {
        val uuid = UUID.fromString(uuidStr)
        return ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    }

    private fun isIpv4Address(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }
}
