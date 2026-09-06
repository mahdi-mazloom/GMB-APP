package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val status: String,                 // "success" or "error"
    val message: String? = null,
    val token: String? = null,
    val username: String? = null,
    val remainingDays: Int? = null,
    val gmbConfig: String? = null,       // Config string formatted as gmb://... or JSON
    val sshHost: String? = null,
    val sshPort: Int? = null,
    val sshUsername: String? = null,
    val sshPassword: String? = null
)
