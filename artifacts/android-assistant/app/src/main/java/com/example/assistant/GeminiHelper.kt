package com.example.assistant

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * GeminiHelper.kt
 * Sends command to Gemini Flash API when online.
 * API key is read from SharedPreferences (set via Settings screen).
 */
class GeminiHelper(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    suspend fun interpretCommand(userInput: String): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext null

        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$apiKey"

        val prompt = """
            You are an Android assistant command interpreter.
            
            The user said: "$userInput"
            
            Classify this into exactly one of these actions. Respond with ONLY the action line:
            - OPEN <app_name>
            - CALL <name_or_number>
            - ALARM <time>
            - SEARCH <query>
            - NAVIGATE back
            - NAVIGATE home
            - NAVIGATE recents
            - NAVIGATE screenshot
            - NAVIGATE notifications
            - NAVIGATE lock
            - UNKNOWN
            
            Respond with just one line, nothing else.
        """.trimIndent()

        try {
            val requestBody = mapOf(
                "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt)))),
                "generationConfig" to mapOf("temperature" to 0.1, "maxOutputTokens" to 60)
            )
            val json = gson.toJson(requestBody)
            val body = json.toRequestBody(jsonMediaType)
            val request = Request.Builder().url(apiUrl).post(body).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseText = response.body?.string() ?: return@withContext null
                @Suppress("UNCHECKED_CAST")
                val parsed = gson.fromJson(responseText, Map::class.java) as? Map<String, Any>
                val candidates = parsed?.get("candidates") as? List<*>
                val content = (candidates?.firstOrNull() as? Map<*, *>)?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val text = (parts?.firstOrNull() as? Map<*, *>)?.get("text") as? String
                text?.trim()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseGeminiResponse(geminiOutput: String): ParsedCommand {
        val upper = geminiOutput.trim().uppercase()
        val original = geminiOutput.trim()
        return when {
            upper.startsWith("OPEN ")     -> ParsedCommand.OpenApp(original.substring(5).trim())
            upper.startsWith("CALL ")     -> ParsedCommand.MakeCall(original.substring(5).trim())
            upper.startsWith("ALARM ")    -> ParsedCommand.SetAlarm(original.substring(6).trim())
            upper.startsWith("SEARCH ")   -> ParsedCommand.Search(original.substring(7).trim())
            upper.startsWith("NAVIGATE ") -> ParsedCommand.Navigate(original.substring(9).trim().lowercase())
            else                          -> ParsedCommand.Unknown(geminiOutput, null)
        }
    }
}
