package com.example

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VmessCryptoTest {

    interface HashFunction {
        fun update(data: ByteArray, offset: Int = 0, len: Int = data.size)
        fun digest(): ByteArray
        fun blockSize(): Int
    }

    class Sha256Hash : HashFunction {
        private val md = MessageDigest.getInstance("SHA-256")
        override fun update(data: ByteArray, offset: Int, len: Int) = md.update(data, offset, len)
        override fun digest(): ByteArray = md.digest()
        override fun blockSize(): Int = 64
    }

    class NestedHmac(private val parentCreator: () -> HashFunction, key: ByteArray) : HashFunction {
        private val inner = parentCreator()
        private val outer = parentCreator()
        private val blockSize = inner.blockSize()
        private val ipad = ByteArray(blockSize)
        private val opad = ByteArray(blockSize)

        init {
            val normalizedKey: ByteArray = if (key.size > blockSize) {
                val h = parentCreator()
                h.update(key)
                h.digest()
            } else {
                key
            }
            val paddedKey = ByteArray(blockSize)
            System.arraycopy(normalizedKey, 0, paddedKey, 0, normalizedKey.size)
            for (i in 0 until blockSize) {
                ipad[i] = (paddedKey[i].toInt() xor 0x36).toByte()
                opad[i] = (paddedKey[i].toInt() xor 0x5c).toByte()
            }
            inner.update(ipad)
        }

        override fun update(data: ByteArray, offset: Int, len: Int) {
            inner.update(data, offset, len)
        }

        override fun digest(): ByteArray {
            val innerDigest = inner.digest()
            outer.update(opad)
            outer.update(innerDigest)
            return outer.digest()
        }

        override fun blockSize(): Int = blockSize
    }

    class HMacCreator(val value: ByteArray, val parent: HMacCreator?) {
        fun create(): HashFunction {
            return if (parent == null) {
                NestedHmac({ Sha256Hash() }, value)
            } else {
                NestedHmac({ parent.create() }, value)
            }
        }
    }

    private fun kdf(key: ByteArray, vararg paths: String): ByteArray {
        var creator = HMacCreator("VMess AEAD KDF".toByteArray(Charsets.ISO_8859_1), null)
        for (p in paths) {
            creator = HMacCreator(p.toByteArray(Charsets.ISO_8859_1), creator)
        }
        val h = creator.create()
        h.update(key)
        return h.digest()
    }

    private fun kdf16(key: ByteArray, vararg paths: String): ByteArray {
        return kdf(key, *paths).copyOfRange(0, 16)
    }

    private fun parseUuid(uuidStr: String): ByteArray {
        val clean = uuidStr.replace("-", "")
        val bytes = ByteArray(16)
        for (i in 0 until 16) {
            bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }

    private fun createAuthId(cmdKey: ByteArray, timestamp: Long): ByteArray {
        val buf = ByteBuffer.allocate(16)
        buf.putLong(timestamp)
        val rand4 = ByteArray(4)
        SecureRandom().nextBytes(rand4)
        buf.put(rand4)

        val crc = CRC32()
        crc.update(buf.array(), 0, 12)
        val crcVal = crc.value.toInt()
        buf.putInt(crcVal)

        val authIdKey = kdf16(cmdKey, "AES Auth ID Encryption")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(authIdKey, "AES"))
        return cipher.doFinal(buf.array())
    }

    private fun fnv1a(data: ByteArray): ByteArray {
        var hash = 0x811c9dc5.toInt()
        for (b in data) {
            hash = hash xor (b.toInt() and 0xff)
            hash *= 0x01000193
        }
        val buf = ByteBuffer.allocate(4)
        buf.putInt(hash)
        return buf.array()
    }

    private fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        if (aad != null && aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        if (aad != null && aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(ciphertext)
    }

    private fun generateChunkNonce(baseNonce: ByteArray, count: Int): ByteArray {
        val nonce = baseNonce.copyOf(12)
        nonce[0] = ((count shr 8) and 0xff).toByte()
        nonce[1] = (count and 0xff).toByte()
        return nonce
    }

    @Test
    fun testLiveVmessOverWebSocket() {
        val serverHost = "ik.mahdis-net.ir"
        val serverPort = 31839
        val wsPath = "/ws-speed"
        val uuidStr = "3119613d-ce1a-4671-b252-a0e8007ad380"
        val targetHost = "connectivitycheck.gstatic.com"
        val targetPort = 80

        println("Connecting to $serverHost:$serverPort...")
        val socket = Socket(serverHost, serverPort)
        socket.soTimeout = 10000
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()

        // 1. WebSocket Upgrade
        val secKey = "dGhlIHNhbXBsZSBub25jZQ=="
        val wsReq = "GET $wsPath HTTP/1.1\r\n" +
                "Host: $serverHost\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $secKey\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"
        out.write(wsReq.toByteArray(Charsets.UTF_8))
        out.flush()

        // Read HTTP upgrade response header
        val headerBuf = ByteArray(4096)
        var totalRead = 0
        while (totalRead < headerBuf.size) {
            val r = inp.read(headerBuf, totalRead, 1)
            if (r <= 0) break
            totalRead += r
            if (totalRead >= 4 &&
                headerBuf[totalRead - 4] == 0x0D.toByte() &&
                headerBuf[totalRead - 3] == 0x0A.toByte() &&
                headerBuf[totalRead - 2] == 0x0D.toByte() &&
                headerBuf[totalRead - 1] == 0x0A.toByte()
            ) {
                break
            }
        }
        val wsResp = String(headerBuf, 0, totalRead, Charsets.ISO_8859_1)
        println("WS Response:\n$wsResp")
        assertTrue(wsResp.contains("101 Switching Protocols"))

        // 2. VMess Credentials
        val uuidBytes = parseUuid(uuidStr)
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(uuidBytes)
        md5.update("c48619fe-8f02-49e0-b9e9-edf763e17e21".toByteArray(Charsets.ISO_8859_1))
        val cmdKey = md5.digest()

        val reqBodyKey = ByteArray(16)
        val reqBodyIv = ByteArray(16)
        SecureRandom().nextBytes(reqBodyKey)
        SecureRandom().nextBytes(reqBodyIv)
        val respHeaderByte = SecureRandom().nextInt(256).toByte()

        val shaKey = MessageDigest.getInstance("SHA-256").digest(reqBodyKey)
        val respBodyKey = shaKey.copyOfRange(0, 16)
        val shaIv = MessageDigest.getInstance("SHA-256").digest(reqBodyIv)
        val respBodyIv = shaIv.copyOfRange(0, 16)

        // 3. VMess Request Header
        val headerOut = ByteArrayOutputStream()
        headerOut.write(1) // Version 1
        headerOut.write(reqBodyIv)
        headerOut.write(reqBodyKey)
        headerOut.write(respHeaderByte.toInt())
        headerOut.write(1) // Option: 1 (ChunkStream)
        val security = 3 // AES128_GCM
        headerOut.write(security)
        headerOut.write(0) // reserved
        headerOut.write(1) // Command: TCP (1)

        // Port (2 bytes BigEndian)
        headerOut.write((targetPort shr 8) and 0xff)
        headerOut.write(targetPort and 0xff)

        // Address: Domain (2)
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        headerOut.write(2) // Domain type
        headerOut.write(hostBytes.size)
        headerOut.write(hostBytes)

        val headerData = headerOut.toByteArray()
        val fnv = fnv1a(headerData)
        val fullHeaderData = headerData + fnv

        // 4. Seal VMess AEAD Header
        val nowSec = System.currentTimeMillis() / 1000
        val authId = createAuthId(cmdKey, nowSec)
        val connectionNonce = ByteArray(8)
        SecureRandom().nextBytes(connectionNonce)

        val authIdStr = String(authId, Charsets.ISO_8859_1)
        val nonceStr = String(connectionNonce, Charsets.ISO_8859_1)

        val lenBytes = ByteBuffer.allocate(2).putShort(fullHeaderData.size.toShort()).array()
        val lenKey = kdf16(cmdKey, "VMess Header AEAD Key_Length", authIdStr, nonceStr)
        val lenIv = kdf(cmdKey, "VMess Header AEAD Nonce_Length", authIdStr, nonceStr).copyOfRange(0, 12)
        val encLen = aesGcmEncrypt(lenKey, lenIv, lenBytes, authId)

        val headerKey = kdf16(cmdKey, "VMess Header AEAD Key", authIdStr, nonceStr)
        val headerIv = kdf(cmdKey, "VMess Header AEAD Nonce", authIdStr, nonceStr).copyOfRange(0, 12)
        val encHeader = aesGcmEncrypt(headerKey, headerIv, fullHeaderData, authId)

        val sealedHeader = authId + encLen + connectionNonce + encHeader

        // 5. Send VMess Payload: HTTP request
        val httpRequest = "GET /generate_204 HTTP/1.1\r\nHost: $targetHost\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
        val chunkNonce = generateChunkNonce(reqBodyIv, 0)
        val encPayload = aesGcmEncrypt(reqBodyKey, chunkNonce, httpRequest)
        val chunkLen = encPayload.size
        val chunkHeader = ByteBuffer.allocate(2).putShort(chunkLen.toShort()).array()

        val fullClientData = sealedHeader + chunkHeader + encPayload

        // Send inside WebSocket Binary Frame (Client to Server requires masking)
        sendWsBinaryFrame(out, fullClientData)
        out.flush()
        println("Sent VMess request + HTTP GET (${fullClientData.size} bytes)")

        // 6. Read server WebSocket frames and decode VMess response!
        class WsInputStream(private val inp: InputStream) : InputStream() {
            private var currentBuf: ByteArray? = null
            private var pos = 0

            override fun read(): Int {
                val b = ByteArray(1)
                val r = read(b, 0, 1)
                return if (r < 0) -1 else (b[0].toInt() and 0xff)
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                while (currentBuf == null || pos >= currentBuf!!.size) {
                    currentBuf = readWsBinaryFrame(inp)
                    pos = 0
                }
                val available = currentBuf!!.size - pos
                val toRead = Math.min(len, available)
                System.arraycopy(currentBuf!!, pos, b, off, toRead)
                pos += toRead
                return toRead
            }
        }

        val vmessIn = WsInputStream(inp)

        // Decrypt Response Header Length (18 bytes)
        val encRespLen = ByteArray(18)
        readExact(vmessIn, encRespLen)

        val respLenKey = kdf16(respBodyKey, "AEAD Resp Header Len Key")
        val respLenIv = kdf(respBodyIv, "AEAD Resp Header Len IV").copyOfRange(0, 12)
        val decRespLenBytes = aesGcmDecrypt(respLenKey, respLenIv, encRespLen, null)
        val respHeaderLen = ByteBuffer.wrap(decRespLenBytes).short.toInt() and 0xffff
        println("Decrypted Response Header Length: $respHeaderLen")

        // Decrypt Response Header
        val encRespHeader = ByteArray(respHeaderLen + 16)
        readExact(vmessIn, encRespHeader)
        val respHeaderKey = kdf16(respBodyKey, "AEAD Resp Header Key")
        val respHeaderIv = kdf(respBodyIv, "AEAD Resp Header IV").copyOfRange(0, 12)
        val decRespHeader = aesGcmDecrypt(respHeaderKey, respHeaderIv, encRespHeader, null)

        println("Decrypted Response Header: ${decRespHeader.joinToString { it.toString() }}")
        assertEquals(respHeaderByte, decRespHeader[0])
        println("Response Header verified perfectly!")

        // Decrypt first data chunk
        val chunkLenBytes = ByteArray(2)
        readExact(vmessIn, chunkLenBytes)
        val serverChunkLen = ByteBuffer.wrap(chunkLenBytes).short.toInt() and 0xffff
        println("Server data chunk length: $serverChunkLen")

        val encServerChunk = ByteArray(serverChunkLen)
        readExact(vmessIn, encServerChunk)

        val serverChunkNonce = generateChunkNonce(respBodyIv, 0)
        val decServerData = aesGcmDecrypt(respBodyKey, serverChunkNonce, encServerChunk, null)
        val httpRespStr = String(decServerData, Charsets.UTF_8)
        println("Decrypted HTTP Response from server:\n$httpRespStr")
        assertTrue(httpRespStr.contains("HTTP/1.1 204") || httpRespStr.contains("HTTP/1.0 204") || httpRespStr.contains("204 No Content"))
        println("TEST COMPLETED WITH 100% SUCCESS!")

        socket.close()
    }

    private fun sendWsBinaryFrame(out: OutputStream, payload: ByteArray) {
        val mask = ByteArray(4)
        SecureRandom().nextBytes(mask)

        out.write(0x82) // FIN + Binary frame opcode 2
        val len = payload.size
        if (len < 126) {
            out.write(len or 0x80)
        } else if (len <= 65535) {
            out.write(126 or 0x80)
            out.write((len shr 8) and 0xff)
            out.write(len and 0xff)
        } else {
            out.write(127 or 0x80)
            val lenBuf = ByteBuffer.allocate(8).putLong(len.toLong()).array()
            out.write(lenBuf)
        }
        out.write(mask)

        val masked = ByteArray(payload.size)
        for (i in payload.indices) {
            masked[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        }
        out.write(masked)
    }

    private fun readExact(inp: InputStream, target: ByteArray, offset: Int = 0, length: Int = target.size - offset) {
        var readTotal = 0
        while (readTotal < length) {
            val r = inp.read(target, offset + readTotal, length - readTotal)
            if (r < 0) throw RuntimeException("EOF while reading $length bytes")
            readTotal += r
        }
    }

    private fun readWsBinaryFrame(inp: InputStream): ByteArray {
        val b0 = inp.read()
        if (b0 < 0) throw RuntimeException("EOF")
        val b1 = inp.read()
        if (b1 < 0) throw RuntimeException("EOF")

        val isMasked = (b1 and 0x80) != 0
        var payloadLen = (b1 and 0x7f).toLong()
        if (payloadLen == 126L) {
            val lenBytes = ByteArray(2)
            readExact(inp, lenBytes)
            payloadLen = (ByteBuffer.wrap(lenBytes).short.toInt() and 0xffff).toLong()
        } else if (payloadLen == 127L) {
            val lenBytes = ByteArray(8)
            readExact(inp, lenBytes)
            payloadLen = ByteBuffer.wrap(lenBytes).long
        }

        val maskKey = if (isMasked) {
            val m = ByteArray(4)
            readExact(inp, m)
            m
        } else null

        val data = ByteArray(payloadLen.toInt())
        readExact(inp, data)

        if (maskKey != null) {
            for (i in data.indices) {
                data[i] = (data[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }
        return data
    }
}
