package com.example.assistant

/**
 * CommandParser.kt — Offline command understanding for Aria.
 *
 * Parses raw text into a typed ParsedCommand using keyword matching
 * and auto-correct (Levenshtein distance for typos).
 *
 * Supports Hindi-influenced phrasing common in Indian English:
 *   "alarm lagao 7 baje", "camera kholo", "call karo 9XXXXXXXXX"
 */

sealed class ParsedCommand {
    data class OpenApp(val appName: String)    : ParsedCommand()
    data class MakeCall(val contact: String)   : ParsedCommand()
    data class SetAlarm(val time: String)      : ParsedCommand()
    data class TakeSelfie(val front: Boolean)  : ParsedCommand()
    data class Search(val query: String)       : ParsedCommand()
    data class Navigate(val action: String)    : ParsedCommand()
    data class Unknown(val original: String, val suggestion: String?) : ParsedCommand()
}

object CommandParser {

    fun parse(input: String): ParsedCommand {
        val text = input.trim().lowercase()

        return when {
            // ── Navigation / accessibility ────────────────────────────────────
            matchesAny(text, listOf("go back", "back", "piche jao", "wapas")) ->
                ParsedCommand.Navigate("back")
            matchesAny(text, listOf("go home", "home screen", "home", "ghar")) ->
                ParsedCommand.Navigate("home")
            matchesAny(text, listOf("recent apps", "recents", "recent", "switch app")) ->
                ParsedCommand.Navigate("recents")
            matchesAny(text, listOf("screenshot", "take screenshot", "screen shot", "screen capture")) ->
                ParsedCommand.Navigate("screenshot")
            matchesAny(text, listOf("notifications", "pull down", "notification bar", "notif")) ->
                ParsedCommand.Navigate("notifications")
            matchesAny(text, listOf("lock screen", "lock phone", "lock", "phone lock")) ->
                ParsedCommand.Navigate("lock")
            matchesAny(text, listOf("power menu", "power off", "shutdown", "restart phone")) ->
                ParsedCommand.Navigate("power_menu")

            // ── Selfie / Camera capture ───────────────────────────────────────
            // "take selfie", "click selfie", "selfie lo", "front camera"
            text.contains("selfie") || (text.contains("front") && text.contains("camera")) ||
            text.contains("click photo") || text.contains("take photo") || text.contains("capture photo") ->
                ParsedCommand.TakeSelfie(front = !text.contains("back camera") && !text.contains("rear"))

            // ── Open App ──────────────────────────────────────────────────────
            startsWithAny(text, listOf("open", "launch", "start", "kholo", "chalao", "show")) ->
                ParsedCommand.OpenApp(removeKeyword(text, listOf("open", "launch", "start", "kholo", "chalao", "show")))

            // ── Call ──────────────────────────────────────────────────────────
            startsWithAny(text, listOf("call", "dial", "phone", "ring", "call karo")) ->
                ParsedCommand.MakeCall(removeKeyword(text, listOf("call", "dial", "phone", "ring", "call karo")))

            // ── Alarm / Reminder ──────────────────────────────────────────────
            // Handles: "set alarm 7am", "alarm at 7pm", "alarm 7:30", "wake me up at 6",
            //          "remind me at 8", "alarm lagao", "subah 7 baje alarm"
            text.contains("alarm") || text.contains("remind") ||
            text.contains("wake me") || text.contains("alarm lagao") ||
            (text.contains("baje") && (text.contains("uthana") || text.contains("alarm") || text.contains("subah"))) ->
                ParsedCommand.SetAlarm(extractTime(text))

            // ── Search ────────────────────────────────────────────────────────
            startsWithAny(text, listOf("search", "find", "google", "look up", "youtube", "dhundho")) ->
                ParsedCommand.Search(removeKeyword(text, listOf("search", "find", "google", "look up", "youtube", "dhundho")))

            // ── Auto-correct fallback ─────────────────────────────────────────
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

    fun extractTime(text: String): String {
        // Match patterns: 7am, 7:30pm, 7 am, 7:30 am, 19:00, 7 baje, 7 o'clock
        val timeRegex = Regex("""\d{1,2}(?::\d{2})?\s*(?:am|pm|baje|o'clock|oclock)?""", RegexOption.IGNORE_CASE)
        val match = timeRegex.find(text)
        return match?.value?.trim()
            ?: text.replace(Regex("(alarm|set|remind|wake me(?: up)?(?:\\s+at)?|at|lagao|subah|shaam)"), "").trim()
    }

    private fun tryAutoCorrect(text: String): String? {
        val firstWord = text.split(" ").first()
        val keywords  = listOf("open", "launch", "call", "dial", "alarm", "search", "find",
                                "back", "home", "screenshot", "selfie")
        val best         = keywords.minByOrNull { levenshtein(firstWord, it) }
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
