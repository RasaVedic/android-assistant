package com.example.assistant

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
 *
 * PURPOSE: When the device is online, send the user's command to Gemini Flash
 *          for smarter interpretation.
 *
 * HOW IT WORKS:
 * 1. Build a JSON request body that describes our task to Gemini.
 * 2. Send it to the Gemini API via HTTP POST.
 * 3. Parse the JSON response to extract the text reply.
 * 4. Return the reply to MainActivity, which executes it.
 *
 * Beginners:
 * - OkHttp is a popular library for making HTTP requests in Android.
 * - Gson is a library that converts between Kotlin objects and JSON strings.
 * - suspend fun = a function that can be paused (runs on a background thread)
 *   without blocking the main UI thread.
 *
 * SETUP:
 * Replace YOUR_GEMINI_API_KEY below with your key from https://aistudio.google.com/
 */
class GeminiHelper {

    // -----------------------------------------------------------------------
    // Replace this with your actual Gemini API key
    // -----------------------------------------------------------------------
    private val apiKey = "YOUR_GEMINI_API_KEY"

    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$apiKey"

    // OkHttpClient handles the actual HTTP connection.
    // We give it a 15-second timeout so the app doesn't freeze indefinitely.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Send a command to Gemini and return the interpreted action as a string.
     * This is a suspend function — it must be called from a coroutine.
     *
     * Returns null if the call fails (network error, bad key, etc.).
     */
    suspend fun interpretCommand(userInput: String): String? = withContext(Dispatchers.IO) {
        // withContext(Dispatchers.IO) runs the code on a background thread
        // so the UI doesn't freeze while waiting for the network

        try {
            // --- Build the prompt ---
            // We tell Gemini exactly what we want: classify the command into one of our known actions
            val prompt = """
                You are an Android assistant command interpreter.
                
                The user said: "$userInput"
                
                Classify this into exactly one of these actions and respond with ONLY the action line, nothing else:
                - OPEN <app_name>          (e.g. OPEN camera)
                - CALL <name_or_number>    (e.g. CALL John)
                - ALARM <time>             (e.g. ALARM 7:30am)
                - SEARCH <query>           (e.g. SEARCH weather today)
                - UNKNOWN                  (if it doesn't match any action)
                
                Respond with just one line.
            """.trimIndent()

            // --- Build the JSON body ---
            // Gemini API expects: { "contents": [ { "parts": [ { "text": "..." } ] } ] }
            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to prompt)))
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.1,          // low temperature = more predictable/consistent
                    "maxOutputTokens" to 50         // we only need a short reply
                )
            )

            val json = gson.toJson(requestBody)
            val body = json.toRequestBody(jsonMediaType)

            // --- Build and send the HTTP request ---
            val request = Request.Builder()
                .url(apiUrl)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null  // API returned an error status
                }

                val responseText = response.body?.string() ?: return@withContext null

                // --- Parse the Gemini response ---
                // The response JSON looks like:
                // { "candidates": [ { "content": { "parts": [ { "text": "OPEN camera" } ] } } ] }
                @Suppress("UNCHECKED_CAST")
                val parsed = gson.fromJson(responseText, Map::class.java) as? Map<String, Any>
                val candidates = parsed?.get("candidates") as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                val text = firstPart?.get("text") as? String

                text?.trim()
            }
        } catch (e: Exception) {
            // Any exception (no internet, parse error, etc.) → return null
            // The app will fall back to the offline CommandParser
            null
        }
    }

    /**
     * Convert Gemini's plain text response into a ParsedCommand.
     *
     * Gemini returns lines like "OPEN camera" or "ALARM 7am",
     * and we turn those into the same ParsedCommand objects that CommandParser uses.
     */
    fun parseGeminiResponse(geminiOutput: String): ParsedCommand {
        val line = geminiOutput.trim().uppercase()

        return when {
            line.startsWith("OPEN ")   -> ParsedCommand.OpenApp(geminiOutput.removePrefix("OPEN ").trim())
            line.startsWith("CALL ")   -> ParsedCommand.MakeCall(geminiOutput.removePrefix("CALL ").trim())
            line.startsWith("ALARM ")  -> ParsedCommand.SetAlarm(geminiOutput.removePrefix("ALARM ").trim())
            line.startsWith("SEARCH ") -> ParsedCommand.Search(geminiOutput.removePrefix("SEARCH ").trim())
            else                       -> ParsedCommand.Unknown(geminiOutput, null)
        }
    }
}
