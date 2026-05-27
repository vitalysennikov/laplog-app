package com.laplog.app.data.database.dao

import androidx.room.*
import com.laplog.app.data.database.entity.SessionNameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionNameDao {

    @Query("SELECT * FROM session_names ORDER BY name ASC")
    fun getAllFlow(): Flow<List<SessionNameEntity>>

    @Query("SELECT * FROM session_names ORDER BY name ASC")
    suspend fun getAll(): List<SessionNameEntity>

    @Query("SELECT * FROM session_names WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SessionNameEntity?

    @Query("SELECT * FROM session_names WHERE id = :id")
    suspend fun getById(id: Long): SessionNameEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SessionNameEntity): Long

    @Update
    suspend fun update(entity: SessionNameEntity)

    @Query("UPDATE session_names SET name = :newName WHERE id = :id")
    suspend fun rename(id: Long, newName: String)

    @Query("DELETE FROM session_names WHERE id = :id")
    suspend fun deleteById(id: Long)
}
