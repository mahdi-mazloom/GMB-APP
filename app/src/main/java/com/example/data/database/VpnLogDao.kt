package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnLogDao {
    @Query("SELECT * FROM vpn_logs ORDER BY timestamp DESC LIMIT 100")
    fun getLogs(): Flow<List<VpnLog>>

    @Insert
    suspend fun insertLog(log: VpnLog)

    @Query("DELETE FROM vpn_logs")
    suspend fun clearLogs()
}
