package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_logs")
data class VpnLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val level: String = "INFO" // INFO, ERROR, WARNING, SUCCESS
)
