package com.laplog.app.data

import android.util.Log
import com.laplog.app.data.database.dao.SessionDao
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
        // No translation needed if same language
        if (currentLang == targetLang) {
            return@withContext Pair(name, notes)
        }

        val translatedName = name?.let { translate(it, currentLang, targetLang) }
        val translatedNotes = notes?.let { translate(it, currentLang, targetLang) }

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

        // Tier 1: Check built-in dictionary
        BuiltInDictionary.getTranslation(text, toLang)?.let {
            Log.d(TAG, "Translation from built-in dictionary: $text -> $it")
            return it
        }

        // Tier 2: Check database cache (would need to query session by original text)
        // For now, skip as it requires complex querying

        // Tier 3: Try online API
        try {
            val translation = translateWithMyMemory(text, fromLang, toLang)
            if (translation != null) {
                Log.d(TAG, "Translation from MyMemory API: $text -> $translation")
                return translation
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error translating with API: ${e.message}")
        }

        // Return original if no translation available
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

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                // Parse JSON response
                val jsonResponse = JSONObject(response)
                val responseData = jsonResponse.getJSONObject("responseData")
                val translatedText = responseData.getString("translatedText")

                // Check if translation actually happened (API sometimes returns original text)
                if (translatedText != text) {
                    return@withContext translatedText
                }
            } else {
                Log.w(TAG, "MyMemory API returned code: $responseCode")
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "MyMemory API error", e)
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
