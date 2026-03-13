package com.example.assistant

/**
 * CommandParser.kt — Offline command understanding for PKassist.
 *
 * Parses raw text into a typed ParsedCommand using keyword matching
 * and auto-correct (Levenshtein distance for typos).
 */

sealed class ParsedCommand {
    data class OpenApp(val appName: String)   : ParsedCommand()
    data class MakeCall(val contact: String)  : ParsedCommand()
    data class SetAlarm(val time: String)     : ParsedCommand()
    data class Search(val query: String)      : ParsedCommand()
    data class Navigate(val action: String)   : ParsedCommand()  // go back, home, screenshot…
    data class Unknown(val original: String, val suggestion: String?) : ParsedCommand()
}

object CommandParser {

    fun parse(input: String): ParsedCommand {
        val text = input.trim().lowercase()

        return when {
            // ── Navigation / accessibility ──────────────────────────────────
            matchesAny(text, listOf("go back", "back")) ->
                ParsedCommand.Navigate("back")
            matchesAny(text, listOf("go home", "home screen", "home")) ->
                ParsedCommand.Navigate("home")
            matchesAny(text, listOf("recent apps", "recents", "recent")) ->
                ParsedCommand.Navigate("recents")
            matchesAny(text, listOf("screenshot", "take screenshot", "screen shot")) ->
                ParsedCommand.Navigate("screenshot")
            matchesAny(text, listOf("notifications", "pull down", "notification bar")) ->
                ParsedCommand.Navigate("notifications")
            matchesAny(text, listOf("lock screen", "lock phone", "lock")) ->
                ParsedCommand.Navigate("lock")
            matchesAny(text, listOf("power menu", "power off", "shutdown")) ->
                ParsedCommand.Navigate("power_menu")

            // ── Open App ────────────────────────────────────────────────────
            startsWithAny(text, listOf("open", "launch", "start")) ->
                ParsedCommand.OpenApp(removeKeyword(text, listOf("open", "launch", "start")))

            // ── Call ────────────────────────────────────────────────────────
            startsWithAny(text, listOf("call", "dial", "phone", "ring")) ->
                ParsedCommand.MakeCall(removeKeyword(text, listOf("call", "dial", "phone", "ring")))

            // ── Alarm / Reminder ─────────────────────────────────────────────
            text.contains("alarm") || text.contains("remind") || text.contains("wake me") ->
                ParsedCommand.SetAlarm(extractTime(text))

            // ── Search ───────────────────────────────────────────────────────
            startsWithAny(text, listOf("search", "find", "google", "look up", "youtube")) ->
                ParsedCommand.Search(removeKeyword(text, listOf("search", "find", "google", "look up", "youtube")))

            // ── Auto-correct fallback ────────────────────────────────────────
            else -> ParsedCommand.Unknown(input, tryAutoCorrect(text))
        }
    }

    private fun matchesAny(text: String, phrases: List<String>): Boolean =
        phrases.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") || text.contains(" $it ") }

    private fun startsWithAny(text: String, keywords: List<String>): Boolean =
        keywords.any { kw -> text.startsWith("$kw ") || text == kw }

    private fun removeKeyword(text: String, keywords: List<String>): String {
        for (kw in keywords) {
            if (text.startsWith("$kw ")) return text.removePrefix("$kw ").trim()
            if (text == kw) return ""
        }
        return text
    }

    private fun extractTime(text: String): String {
        val timeRegex = Regex("""\d{1,2}(:\d{2})?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        val match = timeRegex.find(text)
        return match?.value?.trim()
            ?: text.replace("alarm", "").replace("set", "").replace("remind", "")
                .replace("wake me at", "").trim()
    }

    private fun tryAutoCorrect(text: String): String? {
        val firstWord = text.split(" ").first()
        val keywords = listOf("open", "launch", "call", "dial", "alarm", "search", "find", "back", "home", "screenshot")
        val best = keywords.minByOrNull { levenshtein(firstWord, it) }
        val bestDistance = if (best != null) levenshtein(firstWord, best) else Int.MAX_VALUE
        return if (bestDistance <= 2) {
            val rest = text.removePrefix(firstWord).trim()
            if (rest.isNotEmpty()) "$best $rest" else best
        } else null
    }

    fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
            else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        }
        return dp[m][n]
    }
}
