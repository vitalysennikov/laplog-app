package com.laplog.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val totalDuration: Long,
    val name: String? = null,
    val notes: String? = null,
    // Translations cache
    val name_en: String? = null,
    val name_ru: String? = null,
    val name_zh: String? = null,
    val notes_en: String? = null,
    val notes_ru: String? = null,
    val notes_zh: String? = null
)
