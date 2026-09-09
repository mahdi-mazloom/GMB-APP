package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RedeemLicenseResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val daysAdded: Int? = null,
    val volumeGBAdded: Int? = null
)
