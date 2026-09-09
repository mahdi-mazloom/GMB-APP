package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RedeemLicenseRequest(
    val username: String,
    val code: String
)
