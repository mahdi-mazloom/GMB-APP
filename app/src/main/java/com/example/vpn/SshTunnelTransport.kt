package com.example.vpn

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SshTunnelTransport(
    val session: Session,
    private val onTunnelDropped: (() -> Unit)? = null
) : TunnelTransport {

    override val protocolName: String = "SSH Direct-TCPIP"

    override val isConnected: Boolean
        get() = session.isConnected

    override suspend fun openTcpStream(destHost: String, destPort: Int, timeoutMs: Int): TunnelStream = withContext(Dispatchers.IO) {
        if (!session.isConnected) {
            onTunnelDropped?.invoke()
            throw IOException("SSH session disconnected")
        }

        val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel.setHost(destHost)
        channel.setPort(destPort)
        channel.setOrgIPAddress("127.0.0.1")
        channel.setOrgPort(5353)

        val channelIn = channel.inputStream
        val channelOut = channel.outputStream

        channel.connect(timeoutMs)

        object : TunnelStream {
            override val input: InputStream = channelIn
            override val output: OutputStream = channelOut

            override fun close() {
                try { channelIn.close() } catch (_: Exception) {}
                try { channelOut.close() } catch (_: Exception) {}
                try { channel.disconnect() } catch (_: Exception) {}
            }
        }
    }

    override fun close() {
        try {
            if (session.isConnected) {
                session.disconnect()
            }
        } catch (_: Exception) {}
    }
}
