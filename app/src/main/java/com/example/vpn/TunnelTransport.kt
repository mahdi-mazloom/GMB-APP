package com.example.vpn

import java.io.InputStream
import java.io.OutputStream

/**
 * Interface representing a bi-directional TCP stream through the tunnel.
 */
interface TunnelStream : AutoCloseable {
    val input: InputStream
    val output: OutputStream
    override fun close()
}

/**
 * Unified transport layer supporting both SSH Direct-TCPIP and VMess WebSocket AEAD.
 */
interface TunnelTransport {
    val protocolName: String
    val isConnected: Boolean
    suspend fun openTcpStream(destHost: String, destPort: Int, timeoutMs: Int = 10000): TunnelStream
    fun close()
}
