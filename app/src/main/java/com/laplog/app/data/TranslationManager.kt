package com.laplog.app.data

import android.util.Log
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Translation manager with 3-tier strategy:
 * 1. Built-in dictionary (offline, instant)
 * 2. Database cache (offline, fast)
 * 3. MyMemory API (online, fallback)
 */
class TranslationManager(
    private val sessionDao: SessionDao
) {
    companion object {
        private const val TAG = "TranslationManager"
        private const val MYMEMORY_API_URL = "https://api.mymemory.translated.net/get"
    }

    /**
     * Translate session name to all languages and save to database
     * Called when a new session is saved with a name
     */
    suspend fun translateAndSaveSessionName(sessionId: Long, name: String) = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Auto-translating session $sessionId name: '$name'")

        // Get current language from PreferencesManager
        // For now, we'll assume the name is in the source language and translate to all others
        // We'll try from EN first, then RU, then ZH

        val nameEn = translate(name, "en", "en") ?: name
        val nameRu = translate(name, "en", "ru")
        val nameZh = translate(name, "en", "zh")

        AppLogger.d(TAG, "Session $sessionId translations - EN: '$nameEn', RU: '$nameRu', ZH: '$nameZh'")

        sessionDao.updateSessionNameTranslations(
            sessionId = sessionId,
            nameEn = nameEn,
            nameRu = nameRu,
            nameZh = nameZh
        )

        AppLogger.i(TAG, "Session $sessionId: Name translations saved")
    }

    /**
     * Translate session name and notes to target language
     * Returns null if no translation needed or available
     */
    suspend fun translateSession(
        sessionId: Long,
        currentLang: String,
        targetLang: String,
        name: String?,
        notes: String?
    ): Pair<String?, String?> = withContext(Dispatchers.IO) {
        AppLogger.d("TranslationManager", "translateSession: sessionId=$sessionId, $currentLang -> $targetLang, name='$name'")

        // No translation needed if same language
        if (currentLang == targetLang) {
            AppLogger.d("TranslationManager", "Session $sessionId: Same language, skipping translation")
            return@withContext Pair(name, notes)
        }

        val translatedName = name?.let { translate(it, currentLang, targetLang) }
        val translatedNotes = notes?.let { translate(it, currentLang, targetLang) }

        AppLogger.i("TranslationManager", "Session $sessionId: Translation complete - name: '${name}' -> '${translatedName}', target: $targetLang")
        Pair(translatedName, translatedNotes)
    }

    /**
     * Translate text with 3-tier strategy
     */
    private suspend fun translate(
        text: String,
        fromLang: String,
        toLang: String
    ): String? {
        if (text.isBlank()) return text

        AppLogger.d("TranslationManager", "Translating: '$text' ($fromLang -> $toLang)")

        // Tier 1: Check built-in dictionary
        BuiltInDictionary.getTranslation(text, toLang)?.let {
            AppLogger.i("TranslationManager", "Translation from built-in dictionary: '$text' -> '$it' ($toLang)")
            return it
        }

        // Tier 2: Check database cache (would need to query session by original text)
        // For now, skip as it requires complex querying

        // Tier 3: Try online API
        try {
            AppLogger.d("TranslationManager", "Attempting MyMemory API translation: '$text' ($fromLang -> $toLang)")
            val translation = translateWithMyMemory(text, fromLang, toLang)
            if (translation != null) {
                AppLogger.i("TranslationManager", "Translation from MyMemory API: '$text' -> '$translation' ($toLang)")
                return translation
            } else {
                AppLogger.w("TranslationManager", "MyMemory API returned null for: '$text' ($fromLang -> $toLang)")
            }
        } catch (e: Exception) {
            AppLogger.e("TranslationManager", "Error translating with API: ${e.message}", e)
        }

        // Return original if no translation available
        AppLogger.w("TranslationManager", "No translation available for: '$text' ($fromLang -> $toLang), returning null")
        return null
    }

    /**
     * Translate using MyMemory API
     * Free API: 1000 words/day without key
     */
    private suspend fun translateWithMyMemory(
        text: String,
        fromLang: String,
        toLang: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val langPair = "${mapLangCode(fromLang)}|${mapLangCode(toLang)}"
            val urlString = "$MYMEMORY_API_URL?q=$encodedText&langpair=$langPair"

            AppLogger.d("TranslationManager", "MyMemory API request: $langPair")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            AppLogger.d("TranslationManager", "MyMemory API response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                // Parse JSON response
                val jsonResponse = JSONObject(response)
                val responseData = jsonResponse.getJSONObject("responseData")
                val translatedText = responseData.getString("translatedText")

                AppLogger.d("TranslationManager", "MyMemory API result: '$text' -> '$translatedText'")

                // Check if translation actually happened (API sometimes returns original text)
                if (translatedText != text) {
                    return@withContext translatedText
                } else {
                    AppLogger.w("TranslationManager", "MyMemory API returned same text (no translation)")
                }
            } else {
                AppLogger.w("TranslationManager", "MyMemory API returned code: $responseCode")
            }

            connection.disconnect()
        } catch (e: Exception) {
            AppLogger.e("TranslationManager", "MyMemory API error: ${e.message}", e)
        }

        return@withContext null
    }

    /**
     * Map app language codes to MyMemory API codes
     */
    private fun mapLangCode(langCode: String): String {
        return when (langCode) {
            "en" -> "en-US"
            "ru" -> "ru-RU"
            "zh" -> "zh-CN"
            else -> "en-US"
        }
    }
}
