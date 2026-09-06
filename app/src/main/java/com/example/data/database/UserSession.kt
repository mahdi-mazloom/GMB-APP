package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_sessions")
data class UserSession(
    @PrimaryKey val id: Int = 1, // Only one active user session conserved at once
    val username: String,
    val token: String,
    val remainingDays: Int,
    val finishDate: String = "",
    val shamsiFinishDate: String = "",
    val consumedTrafficMb: Long = 0L,
    val totalTrafficMb: Long = 0L,
    val status: String, // e.g., "active", "expired"
    val sshHost: String,
    val sshPort: Int,
    val sshUsername: String,
    val sshPassword: String,
    val apiBaseUrl: String,
    val udpgwPort: Int = 7300
)
