package com.example.assistant

/**
 * CommandParser.kt
 *
 * PURPOSE: Understand what the user is asking — completely offline.
 *
 * HOW IT WORKS:
 * 1. We define a list of known command keywords (open, call, alarm, search).
 * 2. We clean up the user's input (lowercase, trim).
 * 3. We check if the input starts with or contains a known keyword.
 * 4. If no keyword matches exactly, we use auto-correct (Levenshtein distance)
 *    to find the closest word the user might have meant.
 *
 * Beginners: Levenshtein distance counts how many single-character edits
 * (insert, delete, replace) are needed to turn one word into another.
 * e.g. "opeen" → "open" costs 1 edit.
 */

// Sealed class = a type-safe way to represent "one of these fixed options"
sealed class ParsedCommand {
    data class OpenApp(val appName: String) : ParsedCommand()
    data class MakeCall(val contact: String) : ParsedCommand()
    data class SetAlarm(val time: String) : ParsedCommand()
    data class Search(val query: String) : ParsedCommand()
    data class Unknown(val original: String, val suggestion: String?) : ParsedCommand()
}

object CommandParser {

    // The keywords we know about, paired with the command type they map to
    private val commandKeywords = listOf(
        "open", "launch", "start",       // → OpenApp
        "call", "dial", "phone",          // → MakeCall
        "alarm", "remind", "reminder",    // → SetAlarm
        "search", "find", "google", "look up"  // → Search
    )

    /**
     * Main entry point.
     * Takes raw user text and returns a ParsedCommand.
     */
    fun parse(input: String): ParsedCommand {
        val text = input.trim().lowercase()

        return when {
            // --- Open App ---
            startsWithAny(text, listOf("open", "launch", "start")) -> {
                val appName = removeKeyword(text, listOf("open", "launch", "start"))
                ParsedCommand.OpenApp(appName)
            }

            // --- Make Call ---
            startsWithAny(text, listOf("call", "dial", "phone")) -> {
                val contact = removeKeyword(text, listOf("call", "dial", "phone"))
                ParsedCommand.MakeCall(contact)
            }

            // --- Set Alarm ---
            text.contains("alarm") || text.contains("remind") || text.contains("reminder") -> {
                val time = extractTime(text)
                ParsedCommand.SetAlarm(time)
            }

            // --- Search ---
            startsWithAny(text, listOf("search", "find", "google", "look up")) -> {
                val query = removeKeyword(text, listOf("search", "find", "google", "look up"))
                ParsedCommand.Search(query)
            }

            // --- Unknown: try auto-correct ---
            else -> {
                val suggestion = tryAutoCorrect(text)
                ParsedCommand.Unknown(input, suggestion)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper: check if text starts with any of the given keywords
    // -----------------------------------------------------------------------
    private fun startsWithAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { kw -> text.startsWith(kw + " ") || text == kw }
    }

    // -----------------------------------------------------------------------
    // Helper: remove the leading keyword from the text to get the argument
    // e.g. "open camera" → "camera"
    // -----------------------------------------------------------------------
    private fun removeKeyword(text: String, keywords: List<String>): String {
        for (kw in keywords) {
            if (text.startsWith(kw + " ")) return text.removePrefix(kw + " ").trim()
            if (text == kw) return ""
        }
        return text
    }

    // -----------------------------------------------------------------------
    // Helper: try to extract a time from "set alarm 7am" or "alarm at 6:30"
    // -----------------------------------------------------------------------
    private fun extractTime(text: String): String {
        // Simple regex: matches things like "7am", "6:30pm", "08:00", "7 am"
        val timeRegex = Regex("""\d{1,2}(:\d{2})?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        val match = timeRegex.find(text)
        return match?.value?.trim() ?: text
            .replace("alarm", "").replace("set", "").replace("remind", "").trim()
    }

    // -----------------------------------------------------------------------
    // Auto-correct: find the closest known keyword to the first word typed
    // -----------------------------------------------------------------------
    private fun tryAutoCorrect(text: String): String? {
        val firstWord = text.split(" ").first()
        val allKeywords = listOf("open", "launch", "call", "dial", "alarm", "search", "find")

        // Find the keyword with the lowest edit distance
        val best = allKeywords.minByOrNull { kw -> levenshtein(firstWord, kw) }
        val bestDistance = if (best != null) levenshtein(firstWord, best) else Int.MAX_VALUE

        // Only suggest if the distance is ≤ 2 (close enough to be a typo)
        return if (bestDistance <= 2) {
            val rest = text.removePrefix(firstWord).trim()
            if (rest.isNotEmpty()) "$best $rest" else best
        } else null
    }

    // -----------------------------------------------------------------------
    // Levenshtein distance: how many edits to turn 'a' into 'b'
    // This is a classic dynamic-programming algorithm.
    // -----------------------------------------------------------------------
    fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        // dp[i][j] = edit distance between a[0..i] and b[0..j]
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i  // delete all of a
        for (j in 0..n) dp[0][j] = j  // insert all of b

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]          // characters match — no cost
                } else {
                    1 + minOf(
                        dp[i - 1][j],          // delete from a
                        dp[i][j - 1],          // insert into a
                        dp[i - 1][j - 1]       // replace
                    )
                }
            }
        }
        return dp[m][n]
    }
}
