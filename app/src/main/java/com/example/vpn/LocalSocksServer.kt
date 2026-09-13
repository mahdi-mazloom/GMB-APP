package com.example.vpn

import android.util.Log
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Universal local proxy server supporting both SOCKS5 and HTTP CONNECT proxying,
 * routed securely through unified TunnelTransport (SSH Direct-TCPIP or VMess AEAD).
 */
class LocalSocksServer(
    private val transport: TunnelTransport,
    val port: Int = 10808,
    private val onTraffic: ((rx: Long, tx: Long) -> Unit)? = null
) {
    private var serverSocket: ServerSocket? = null
    // Low-overhead bounded thread pool with background priority to preserve battery and UI fluidity
    private val executor = Executors.newFixedThreadPool(24) { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    @Volatile
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        serverSocket = ServerSocket(port, 100, InetAddress.getByName("0.0.0.0"))

        executor.execute {
            while (isRunning && serverSocket?.isClosed == false) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.tcpNoDelay = true
                    executor.execute {
                        handleClient(clientSocket)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.w("LocalSocksServer", "Accept error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        var tunnelStream: TunnelStream? = null
        try {
            val rawInput = clientSocket.getInputStream()
            val rawOutput = clientSocket.getOutputStream()
            val bufferedInput = BufferedInputStream(rawInput)
            val bufferedOutput = BufferedOutputStream(rawOutput)

            // Peek or read the first byte
            bufferedInput.mark(10)
            val firstByte = bufferedInput.read()
            bufferedInput.reset()

            if (firstByte == 5) {
                // SOCKS5 Protocol
                handleSocks5(clientSocket, bufferedInput, bufferedOutput)
            } else {
                // HTTP / HTTP CONNECT Protocol (Standard HTTP Proxy)
                handleHttpProxy(clientSocket, bufferedInput, bufferedOutput)
            }
        } catch (e: Exception) {
            // Socket or network error
        } finally {
            try { tunnelStream?.close() } catch (_: Exception) {}
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handleSocks5(
        clientSocket: Socket,
        input: InputStream,
        output: OutputStream
    ) {
        val dis = DataInputStream(input)

        // 1. SOCKS5 Greeting
        val version = dis.readUnsignedByte()
        if (version != 5) {
            clientSocket.close()
            return
        }

        val nMethods = dis.readUnsignedByte()
        val methods = ByteArray(nMethods)
        dis.readFully(methods)

        // Reply: Version 5, Method 0 (No authentication)
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()

        // 2. SOCKS5 Request
        val reqVer = dis.readUnsignedByte()
        val cmd = dis.readUnsignedByte()
        val rsv = dis.readUnsignedByte()
        val atyp = dis.readUnsignedByte()

        if (cmd != 1) {
            // CMD 1 = CONNECT. (Others: 2=BIND, 3=UDP ASSOCIATE not supported directly over TCP SOCKS)
            output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Command not supported
            output.flush()
            clientSocket.close()
            return
        }

        val targetHost: String
        val targetPort: Int

        when (atyp) {
            1 -> {
                // IPv4: 4 bytes
                val ipBytes = ByteArray(4)
                dis.readFully(ipBytes)
                targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: "0.0.0.0"
                targetPort = dis.readUnsignedShort()
            }
            3 -> {
                // Domain name: 1 byte len + ASCII domain
                val domainLen = dis.readUnsignedByte()
                val domainBytes = ByteArray(domainLen)
                dis.readFully(domainBytes)
                targetHost = String(domainBytes, Charsets.US_ASCII)
                targetPort = dis.readUnsignedShort()
            }
            4 -> {
                // IPv6: 16 bytes
                val ipBytes = ByteArray(16)
                dis.readFully(ipBytes)
                targetHost = InetAddress.getByAddress(ipBytes).hostAddress ?: "::"
                targetPort = dis.readUnsignedShort()
            }
            else -> {
                output.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Address type not supported
                output.flush()
                clientSocket.close()
                return
            }
        }

        // 3. Connect via TunnelTransport
        if (!transport.isConnected) {
            output.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()
            clientSocket.close()
            return
        }

        val stream: TunnelStream
        try {
            stream = runBlocking {
                transport.openTcpStream(targetHost, targetPort, 15000)
            }
        } catch (e: Exception) {
            try {
                // SOCKS5 0x05 = Connection refused / Host unreachable
                output.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
            } catch (_: Exception) {}
            clientSocket.close()
            return
        }

        // 4. Send success reply to client
        // Reply: VER=5, REP=0(succeeded), RSV=0, ATYP=1(IPv4), BND.ADDR=0.0.0.0, BND.PORT=0
        output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()

        // 5. Transfer data
        pipeBidirectional(input, output, stream.input, stream.output, stream, clientSocket)
    }

    private fun handleHttpProxy(
        clientSocket: Socket,
        input: InputStream,
        output: OutputStream
    ) {
        // Read the first request line (e.g., "CONNECT example.com:443 HTTP/1.1" or "GET http://example.com/ HTTP/1.1")
        val reader = input.bufferedReader(Charsets.ISO_8859_1)
        val firstLine = reader.readLine() ?: return
        val parts = firstLine.split(" ")
        if (parts.size < 2) return

        val method = parts[0].uppercase()
        val target = parts[1]

        val targetHost: String
        val targetPort: Int

        if (method == "CONNECT") {
            // HTTPS Tunnel: target is "host:port"
            val hostPort = target.split(":")
            targetHost = hostPort[0]
            targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443

            // Read remaining headers until blank line
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
            }

            if (!transport.isConnected) {
                output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                output.flush()
                return
            }

            val stream: TunnelStream
            try {
                stream = runBlocking {
                    transport.openTcpStream(targetHost, targetPort, 15000)
                }
            } catch (e: Exception) {
                try {
                    output.write("HTTP/1.1 504 Gateway Timeout\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                    output.flush()
                } catch (_: Exception) {}
                clientSocket.close()
                return
            }

            // Send 200 Connection Established
            output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            output.flush()

            pipeBidirectional(input, output, stream.input, stream.output, stream, clientSocket)
        } else {
            // Standard HTTP request: extract host and port from URL or Host header
            var hostHeader = ""
            val allHeaders = mutableListOf(firstLine)
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrBlank()) break
                allHeaders.add(headerLine!!)
                if (headerLine!!.startsWith("Host:", ignoreCase = true)) {
                    hostHeader = headerLine!!.substring(5).trim()
                }
            }

            val hostPort = hostHeader.split(":")
            targetHost = hostPort[0]
            targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 80 else 80

            if (targetHost.isBlank() || !transport.isConnected) {
                output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                output.flush()
                return
            }

            val stream: TunnelStream
            try {
                stream = runBlocking {
                    transport.openTcpStream(targetHost, targetPort, 15000)
                }
            } catch (e: Exception) {
                try {
                    output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                    output.flush()
                } catch (_: Exception) {}
                clientSocket.close()
                return
            }

            // Forward the original HTTP request headers
            val streamOut = stream.output
            for (header in allHeaders) {
                streamOut.write((header + "\r\n").toByteArray(Charsets.ISO_8859_1))
            }
            streamOut.write("\r\n".toByteArray(Charsets.ISO_8859_1))
            streamOut.flush()

            pipeBidirectional(input, output, stream.input, stream.output, stream, clientSocket)
        }
    }

    private fun pipeBidirectional(
        clientIn: InputStream,
        clientOut: OutputStream,
        channelIn: InputStream,
        channelOut: OutputStream,
        stream: AutoCloseable,
        clientSocket: Socket
    ) {
        val latch = java.util.concurrent.CountDownLatch(2)

        executor.execute {
            try {
                val buffer = ByteArray(8192)
                var accumulatedTx = 0L
                while (isRunning) {
                    val read = clientIn.read(buffer)
                    if (read == -1) break
                    channelOut.write(buffer, 0, read)
                    channelOut.flush()
                    accumulatedTx += read
                    if (accumulatedTx >= 32768) {
                        onTraffic?.invoke(0L, accumulatedTx)
                        accumulatedTx = 0L
                    }
                }
                if (accumulatedTx > 0) onTraffic?.invoke(0L, accumulatedTx)
            } catch (_: Exception) {
            } finally {
                try { stream.close() } catch (_: Exception) {}
                latch.countDown()
            }
        }

        executor.execute {
            try {
                val buffer = ByteArray(8192)
                var accumulatedRx = 0L
                while (isRunning) {
                    val read = channelIn.read(buffer)
                    if (read == -1) break
                    clientOut.write(buffer, 0, read)
                    clientOut.flush()
                    accumulatedRx += read
                    if (accumulatedRx >= 32768) {
                        onTraffic?.invoke(accumulatedRx, 0L)
                        accumulatedRx = 0L
                    }
                }
                if (accumulatedRx > 0) onTraffic?.invoke(accumulatedRx, 0L)
            } catch (_: Exception) {
            } finally {
                try { clientSocket.close() } catch (_: Exception) {}
                latch.countDown()
            }
        }

        try {
            latch.await(10, java.util.concurrent.TimeUnit.MINUTES)
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        try {
            executor.shutdownNow()
        } catch (_: Exception) {}
    }
}
