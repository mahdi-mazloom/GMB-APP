package com.example.data.remote

import android.util.Base64
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ShahanUserResponse(
    val data: ShahanUserData? = null,
    val response_code: Int? = null,
    val response_desc: String? = null
)

@JsonClass(generateAdapter = true)
data class ShahanUserData(
    val id: Any? = null,
    val username: String? = null,
    val password: String? = null,
    val multiuser: String? = null,
    val startdate: String? = null,
    val finishdate: String? = null,
    val enable: String? = null,
    val traffic: String? = null,
    val days: Any? = null,
    val sshnapsternetv: String? = null,
    val sshnetmod: String? = null
)

data class SshConnectionConfig(
    val host: String = "ip.connection-net.ir",
    val port: Int = 2280,
    val username: String = "",
    val password: String = "",
    val udpgwPort: Int = 7300
)

interface ShahanPanelService {
    @FormUrlEncoded
    @POST("apiV1/api.php")
    suspend fun getUserInfo(
        @Query("token") token: String,
        @Field("method") method: String = "userinfo",
        @Field("username") username: String
    ): ShahanUserResponse

    @FormUrlEncoded
    @POST("apiV1/api.php")
    suspend fun getUserTraffic(
        @Query("token") token: String,
        @Field("method") method: String = "getusertraffic",
        @Field("username") username: String
    ): ResponseBody
}

object ShahanPanelClient {
    const val DEFAULT_BASE_URL = "http://ip.connection-net.ir/"
    const val API_TOKEN = "Cus10aJyI4ylsIF5"

    fun create(baseUrl: String = DEFAULT_BASE_URL): ShahanPanelService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val retrofit = Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        return retrofit.create(ShahanPanelService::class.java)
    }

    /**
     * Safely parses sshnapsternetv base64 string to extract SSH host, port, credentials and udpgwPort.
     */
    fun parseSshConfig(rawConfig: String?, fallbackUser: String, fallbackPass: String): SshConnectionConfig {
        if (rawConfig.isNullOrBlank()) {
            return SshConnectionConfig(
                host = "ip.connection-net.ir",
                port = 2280,
                username = fallbackUser,
                password = fallbackPass,
                udpgwPort = 7300
            )
        }

        try {
            val base64Payload = if (rawConfig.contains("://")) {
                rawConfig.substringAfter("://")
            } else {
                rawConfig
            }
            val decodedJson = String(Base64.decode(base64Payload.trim(), Base64.DEFAULT))
            val json = JSONObject(decodedJson)

            val host = json.optString("sshHost", "ip.connection-net.ir").ifEmpty { "ip.connection-net.ir" }
            var port = json.optInt("sshPort", 2280)
            if (port == 22 || port <= 0) {
                // Port 2280 is confirmed open on ip.connection-net.ir
                port = 2280
            }
            val user = json.optString("sshUsername", fallbackUser).ifEmpty { fallbackUser }
            val pass = json.optString("sshPassword", fallbackPass).ifEmpty { fallbackPass }
            val udpgw = json.optInt("udpgwPort", 7300)

            return SshConnectionConfig(
                host = host,
                port = port,
                username = user,
                password = pass,
                udpgwPort = if (udpgw > 0) udpgw else 7300
            )
        } catch (e: Exception) {
            return SshConnectionConfig(
                host = "ip.connection-net.ir",
                port = 2280,
                username = fallbackUser,
                password = fallbackPass,
                udpgwPort = 7300
            )
        }
    }

    /**
     * Parses traffic response JSON safely to retrieve consumed traffic in MB.
     */
    fun parseConsumedTrafficMb(rawJson: String?): Long {
        if (rawJson.isNullOrBlank()) return 0L
        try {
            val json = JSONObject(rawJson)
            val dataObj = json.optJSONObject("data")
            if (dataObj != null) {
                if (dataObj.has("total")) {
                    return dataObj.optLong("total", 0L)
                }
                val zeroObj = dataObj.optJSONObject("0")
                if (zeroObj != null && zeroObj.has("total")) {
                    return zeroObj.optLong("total", 0L)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return 0L
    }
}
