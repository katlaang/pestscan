package com.pestscan.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FarmEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun farmDao(): FarmDao
}
