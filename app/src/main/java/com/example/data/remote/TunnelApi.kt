package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface TunnelApi {
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    companion object {
        fun create(baseUrl: String): TunnelApi {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            // Ensure the base URL ends with a slash for Retrofit compliance
            val formattedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val retrofit = Retrofit.Builder()
                .baseUrl(formattedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            return retrofit.create(TunnelApi::class.java)
        }
    }
}
