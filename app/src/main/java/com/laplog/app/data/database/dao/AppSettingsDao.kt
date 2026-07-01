package com.laplog.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laplog.app.data.database.entity.AppSettingEntity

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: AppSettingEntity)
}
