package com.laplog.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val totalDuration: Long, // Active time (without pauses)
    val name: String? = null,
    val notes: String? = null,
    // Translations cache
    val name_en: String? = null,
    val name_ru: String? = null,
    val name_zh: String? = null,
    val notes_en: String? = null,
    val notes_ru: String? = null,
    val notes_zh: String? = null
) {
    /**
     * Get elapsed time from first start to stop (including pauses)
     */
    val elapsedTime: Long
        get() = endTime - startTime

    /**
     * Get total pause time
     */
    val pauseTime: Long
        get() = elapsedTime - totalDuration
    /**
     * Get localized name based on language code
     * @param languageCode "en", "ru", "zh", or null for system default
     * @return Localized name or original name if translation not available
     */
    fun getLocalizedName(languageCode: String?): String? {
        return when (languageCode) {
            "en" -> name_en?.takeIf { it.isNotBlank() && it != "null" } ?: name
            "ru" -> name_ru?.takeIf { it.isNotBlank() && it != "null" } ?: name
            "zh" -> name_zh?.takeIf { it.isNotBlank() && it != "null" } ?: name
            else -> name // System default or unknown language
        }
    }

    /**
     * Get localized notes based on language code
     * @param languageCode "en", "ru", "zh", or null for system default
     * @return Localized notes or original notes if translation not available
     */
    fun getLocalizedNotes(languageCode: String?): String? {
        return when (languageCode) {
            "en" -> notes_en?.takeIf { it.isNotBlank() && it != "null" } ?: notes
            "ru" -> notes_ru?.takeIf { it.isNotBlank() && it != "null" } ?: notes
            "zh" -> notes_zh?.takeIf { it.isNotBlank() && it != "null" } ?: notes
            else -> notes // System default or unknown language
        }
    }
}
