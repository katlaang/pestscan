package com.pestscan.mobile.domain.model

import java.time.Instant

data class Farm(
    val id: Long,
    val name: String,
    val licensedArea: Double?,
    val updatedAt: Instant
)
