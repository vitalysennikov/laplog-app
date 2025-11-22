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

    @Query("UPDATE sessions SET name = :name WHERE id = :sessionId")
    suspend fun updateSessionName(sessionId: Long, name: String)

    @Query("UPDATE sessions SET notes = :notes WHERE id = :sessionId")
    suspend fun updateSessionNotes(sessionId: Long, notes: String)

    @Query("SELECT DISTINCT name FROM sessions WHERE name IS NOT NULL AND name != '' ORDER BY name ASC")
    suspend fun getDistinctNames(): List<String>

    @Query("SELECT * FROM sessions WHERE name = :sessionName ORDER BY startTime ASC")
    suspend fun getSessionsByName(sessionName: String): List<SessionEntity>

    @Query("""
        UPDATE sessions
        SET name_en = :nameEn, name_ru = :nameRu, name_zh = :nameZh
        WHERE id = :sessionId
    """)
    suspend fun updateSessionNameTranslations(
        sessionId: Long,
        nameEn: String?,
        nameRu: String?,
        nameZh: String?
    )

    @Query("""
        UPDATE sessions
        SET notes_en = :notesEn, notes_ru = :notesRu, notes_zh = :notesZh
        WHERE id = :sessionId
    """)
    suspend fun updateSessionNotesTranslations(
        sessionId: Long,
        notesEn: String?,
        notesRu: String?,
        notesZh: String?
    )

    @Query("SELECT * FROM sessions WHERE (name IS NOT NULL AND name != '') ORDER BY startTime DESC")
    suspend fun getSessionsNeedingTranslation(): List<SessionEntity>
}
