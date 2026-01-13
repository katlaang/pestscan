package com.pestscan.mobile.data.remote

import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class FarmDto(
    val id: Long,
    val name: String,
    val licensedArea: Double?,
    val updatedAt: Instant
)
