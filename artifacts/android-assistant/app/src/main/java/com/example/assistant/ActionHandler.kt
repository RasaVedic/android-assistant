package com.example.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore

/**
 * ActionHandler.kt
 * Executes the parsed command using Android Intents.
 */
object ActionHandler {

    fun execute(context: Context, command: ParsedCommand): String {
        return when (command) {
            is ParsedCommand.OpenApp  -> openApp(context, command.appName)
            is ParsedCommand.MakeCall -> makeCall(context, command.contact)
            is ParsedCommand.SetAlarm -> setAlarm(context, command.time)
            is ParsedCommand.Search   -> search(context, command.query)
            is ParsedCommand.Unknown  -> handleUnknown(command)
        }
    }

    // -----------------------------------------------------------------------
    // Open an app — tries multiple strategies
    // -----------------------------------------------------------------------
    private fun openApp(context: Context, appName: String): String {
        if (appName.isBlank()) return "Please say which app to open. Example: 'open camera'"

        val name = appName.trim().lowercase()

        // Strategy 1: Special intents for common apps (most reliable)
        val specialIntent: Intent? = when {
            name.contains("camera") -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            name.contains("video") && name.contains("camera") -> Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
            name.contains("gallery") || name.contains("photos") ->
                Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
            name.contains("dial") || name.contains("phone") || name.contains("dialer") ->
                Intent(Intent.ACTION_DIAL)
            name.contains("contact") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/"))
            name.contains("message") || name.contains("sms") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) }
            name.contains("browser") || name.contains("chrome") || name.contains("internet") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_BROWSER) }
            name.contains("email") || name.contains("gmail") || name.contains("mail") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_EMAIL) }
            name.contains("maps") || name.contains("map") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MAPS) }
            name.contains("music") || name.contains("player") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) }
            name.contains("calculator") || name.contains("calc") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CALCULATOR) }
            name.contains("calendar") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CALENDAR) }
            name.contains("clock") || name.contains("alarm") ->
                Intent(AlarmClock.ACTION_SHOW_ALARMS)
            name.contains("settings") || name.contains("setting") ->
                Intent(android.provider.Settings.ACTION_SETTINGS)
            name.contains("wifi") || name.contains("wi-fi") ->
                Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            name.contains("bluetooth") ->
                Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            name.contains("youtube") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))
            name.contains("whatsapp") ->
                context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            name.contains("instagram") ->
                context.packageManager.getLaunchIntentForPackage("com.instagram.android")
            name.contains("spotify") ->
                context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            name.contains("netflix") ->
                context.packageManager.getLaunchIntentForPackage("com.netflix.mediaclient")
            name.contains("twitter") || name.contains("x app") ->
                context.packageManager.getLaunchIntentForPackage("com.twitter.android")
            name.contains("facebook") ->
                context.packageManager.getLaunchIntentForPackage("com.facebook.katana")
            name.contains("telegram") ->
                context.packageManager.getLaunchIntentForPackage("org.telegram.messenger")
            name.contains("zoom") ->
                context.packageManager.getLaunchIntentForPackage("us.zoom.videomeetings")
            name.contains("play store") || name.contains("playstore") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store"))
            else -> null
        }

        if (specialIntent != null) {
            specialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(specialIntent)
                "Opening $appName…"
            } catch (e: Exception) {
                // Strategy 2: Search all installed apps
                tryLaunchByName(context, name)
            }
        }

        // Strategy 2: Search installed apps by label name
        return tryLaunchByName(context, name)
    }

    private fun tryLaunchByName(context: Context, name: String): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.firstOrNull { info ->
            pm.getApplicationLabel(info).toString().lowercase().contains(name)
        }
        if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(launchIntent)
                    "Opening ${pm.getApplicationLabel(match)}…"
                } catch (e: Exception) {
                    "Could not open ${pm.getApplicationLabel(match)}"
                }
            }
        }
        return "App '$name' not found. Check spelling or install it first."
    }

    // -----------------------------------------------------------------------
    // Make a call
    // -----------------------------------------------------------------------
    private fun makeCall(context: Context, contact: String): String {
        if (contact.isBlank()) return "Please say a name or number. Example: 'call 98XXXXXXXX'"
        val digits = contact.replace(Regex("[+\\-\\s().]"), "")
        val isNumber = digits.all { it.isDigit() } && digits.isNotEmpty()
        val uri = if (isNumber) Uri.parse("tel:$digits") else Uri.parse("tel:$contact")
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Calling $contact…"
        } catch (e: SecurityException) {
            "Phone permission denied. Go to Settings → Apps → Assistant → Permissions → Phone → Allow"
        } catch (e: Exception) {
            // Fall back to dial screen (no CALL_PHONE permission needed)
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            "Opening dial pad for $contact…"
        }
    }

    // -----------------------------------------------------------------------
    // Set alarm
    // -----------------------------------------------------------------------
    private fun setAlarm(context: Context, timeText: String): String {
        if (timeText.isBlank()) return "Please say a time. Example: 'set alarm 7am'"
        val (hour, minute, display) = parseTime(timeText)
        if (hour == null) return "Couldn't understand '$timeText'. Try: 'alarm 7am' or 'alarm 6:30pm'"
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Assistant alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Alarm set for $display ✓"
        } catch (e: Exception) {
            "No alarm app found on this device."
        }
    }

    private fun parseTime(text: String): Triple<Int?, Int, String> {
        val regex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return Triple(null, 0, "")
        var hour = match.groupValues[1].toIntOrNull() ?: return Triple(null, 0, "")
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val ampm = match.groupValues[3].lowercase()
        if (ampm == "pm" && hour != 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        val displayMinute = minute.toString().padStart(2, '0')
        val display = "${match.groupValues[1]}:$displayMinute${if (ampm.isNotEmpty()) " $ampm" else ""}"
        return Triple(hour, minute, display)
    }

    // -----------------------------------------------------------------------
    // Web search
    // -----------------------------------------------------------------------
    private fun search(context: Context, query: String): String {
        if (query.isBlank()) return "Please say what to search. Example: 'search weather today'"
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return try {
            context.startActivity(intent)
            "Searching for \"$query\"…"
        } catch (e: Exception) {
            "No browser found to perform search."
        }
    }

    // -----------------------------------------------------------------------
    // Unknown command
    // -----------------------------------------------------------------------
    private fun handleUnknown(command: ParsedCommand.Unknown): String {
        return if (command.suggestion != null) {
            "Did you mean: \"${command.suggestion}\"?\n\nTry again with that command."
        } else {
            "Command not recognized.\n\nTry:\n• open camera\n• call 98XXXXXXXX\n• set alarm 7am\n• search recipes"
        }
    }
}
