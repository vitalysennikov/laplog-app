package com.laplog.app.data.database.dao

import androidx.room.*
import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertLaps(laps: List<LapEntity>)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    fun getLapsForSession(sessionId: Long): Flow<List<LapEntity>>

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE startTime < :beforeTime")
    suspend fun deleteSessionsBefore(beforeTime: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("UPDATE sessions SET comment = :comment WHERE id = :sessionId")
    suspend fun updateSessionComment(sessionId: Long, comment: String)
}
