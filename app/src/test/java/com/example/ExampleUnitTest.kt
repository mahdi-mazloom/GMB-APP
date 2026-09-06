package com.example

import com.jcraft.jsch.*
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.Properties
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class ExampleUnitTest {
  @Test
  fun testHttpsViaSsh() {
    val jsch = JSch()
    val session = jsch.getSession("mahdi", "ssh.mahdis-net.ir", 2280)
    session.setPassword("109109")
    session.setConfig(Properties().apply {
      put("StrictHostKeyChecking", "no")
      put("PreferredAuthentications", "password,keyboard-interactive")
    })
    session.connect(15000)
    println("--- SSH CONNECTED ---")

    try {
      val ch = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
      ch.setHost("www.google.com")
      ch.setPort(443)
      ch.setOrgIPAddress("127.0.0.1")
      ch.setOrgPort(12345)
      val input = ch.inputStream
      val output = ch.outputStream
      ch.connect(10000)
      println("--- DIRECT-TCPIP TO 443 CONNECTED: ${ch.isConnected} ---")

      // Wrap in a custom socket or simulate SSL
      val dummySocket = object : Socket() {
        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = output
        override fun isConnected(): Boolean = true
        override fun isClosed(): Boolean = false
      }

      val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
      val sslSocket = sslFactory.createSocket(dummySocket, "www.google.com", 443, true) as SSLSocket
      sslSocket.startHandshake()
      println("--- SSL HANDSHAKE SUCCESSFUL: ${sslSocket.session.cipherSuite} ---")
      sslSocket.close()
    } catch (e: Exception) {
      println("--- SSL HANDSHAKE FAILED: ${e.javaClass.name}: ${e.message} ---")
      e.printStackTrace()
    } finally {
      session.disconnect()
    }
  }
}

