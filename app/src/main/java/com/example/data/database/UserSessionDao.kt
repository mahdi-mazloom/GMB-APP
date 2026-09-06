package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSessionDao {
    @Query("SELECT * FROM user_sessions WHERE id = 1 LIMIT 1")
    fun getActiveSession(): Flow<UserSession?>

    @Query("SELECT * FROM user_sessions WHERE id = 1 LIMIT 1")
    suspend fun getActiveSessionOnce(): UserSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: UserSession)

    @Query("DELETE FROM user_sessions")
    suspend fun clearSession()
}
