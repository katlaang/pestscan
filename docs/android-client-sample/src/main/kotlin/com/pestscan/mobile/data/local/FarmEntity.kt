package com.pestscan.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "farms")
data class FarmEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val licensedArea: Double?,
    val updatedAt: Instant
)
