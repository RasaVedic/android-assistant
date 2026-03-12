package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock

/**
 * ActionHandler.kt
 *
 * PURPOSE: Execute the action that CommandParser identified.
 *
 * HOW IT WORKS:
 * Android uses "Intents" to ask other apps to do things.
 * For example, to open the camera we create an Intent with the camera app's
 * package name and call startActivity(). Android routes it to the right app.
 *
 * Beginners: Think of an Intent as a message you send to Android saying
 * "please open this app" or "please dial this number".
 */
object ActionHandler {

    /**
     * Execute a parsed command and return a human-readable result message.
     *
     * @param context  Android context (needed to start activities / send intents)
     * @param command  The parsed command from CommandParser
     * @return         A string describing what happened (shown to the user)
     */
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
    // Open an installed app by name
    // -----------------------------------------------------------------------
    private fun openApp(context: Context, appName: String): String {
        if (appName.isBlank()) return "Please specify an app name. Example: 'open camera'"

        val pm = context.packageManager

        // Map of friendly names → package names for common apps
        val knownApps = mapOf(
            "camera"    to "com.android.camera2",
            "chrome"    to "com.android.chrome",
            "maps"      to "com.google.android.apps.maps",
            "youtube"   to "com.google.android.youtube",
            "gmail"     to "com.google.android.gm",
            "settings"  to "com.android.settings",
            "calculator" to "com.android.calculator2",
            "calendar"  to "com.google.android.calendar",
            "photos"    to "com.google.android.apps.photos",
            "spotify"   to "com.spotify.music",
            "whatsapp"  to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "twitter"   to "com.twitter.android",
            "facebook"  to "com.facebook.katana",
            "clock"     to "com.google.android.deskclock",
            "contacts"  to "com.android.contacts",
            "messages"  to "com.google.android.apps.messaging",
            "phone"     to "com.android.dialer",
            "files"     to "com.google.android.documentsui",
            "browser"   to "com.android.browser"
        )

        // Find exact or partial match in our known apps map
        val packageName = knownApps.entries
            .firstOrNull { (key, _) -> appName.contains(key, ignoreCase = true) }
            ?.value

        if (packageName != null) {
            // Try to launch by known package name
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return "Opening $appName…"
            }
        }

        // Fallback: search all installed apps for a name match
        val allApps = pm.getInstalledApplications(0)
        val match = allApps.firstOrNull { appInfo ->
            pm.getApplicationLabel(appInfo).toString()
                .contains(appName, ignoreCase = true)
        }

        if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return "Opening ${pm.getApplicationLabel(match)}…"
            }
        }

        return "App '$appName' not found. Is it installed?"
    }

    // -----------------------------------------------------------------------
    // Make a phone call
    // -----------------------------------------------------------------------
    private fun makeCall(context: Context, contact: String): String {
        if (contact.isBlank()) return "Please say a name or number. Example: 'call 555-1234'"

        // If the contact looks like a phone number (digits, +, -, spaces), dial directly
        val isPhoneNumber = contact.replace(Regex("[+\\-\\s()]"), "").all { it.isDigit() }

        val intent = if (isPhoneNumber) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.replace(" ", "")}"))
        } else {
            // Search the contacts app for the name
            Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contact}")
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            "Calling $contact…"
        } catch (e: SecurityException) {
            "CALL_PHONE permission not granted. Please allow it in Settings."
        }
    }

    // -----------------------------------------------------------------------
    // Set an alarm using Android's built-in AlarmClock API
    // -----------------------------------------------------------------------
    private fun setAlarm(context: Context, timeText: String): String {
        if (timeText.isBlank()) return "Please specify a time. Example: 'set alarm 7am'"

        // Parse hour and minute from the time string
        val (hour, minute, message) = parseTime(timeText)

        if (hour == null) {
            return "Couldn't understand the time '$timeText'. Try: 'alarm 7am' or 'alarm 6:30pm'"
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Assistant reminder")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)  // false = show the alarm app so user confirms
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
        return "Setting alarm for $message…"
    }

    /**
     * Parse a time string like "7am", "6:30pm", "08:00" into (hour24, minute, displayText).
     * Returns (null, 0, "") if the string can't be parsed.
     */
    private fun parseTime(text: String): Triple<Int?, Int, String> {
        // Match patterns like: 7am, 7 am, 7:30am, 07:30, 7:30 pm
        val regex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return Triple(null, 0, "")

        var hour = match.groupValues[1].toIntOrNull() ?: return Triple(null, 0, "")
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val ampm = match.groupValues[3].lowercase()

        // Convert to 24-hour
        if (ampm == "pm" && hour != 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0

        val displayMinute = minute.toString().padStart(2, '0')
        val displayAmPm = if (ampm.isNotEmpty()) " $ampm" else ""
        val display = "${match.groupValues[1]}:$displayMinute$displayAmPm"

        return Triple(hour, minute, display)
    }

    // -----------------------------------------------------------------------
    // Search using the device's default browser or Google app
    // -----------------------------------------------------------------------
    private fun search(context: Context, query: String): String {
        if (query.isBlank()) return "Please specify what to search. Example: 'search weather'"

        val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
        return "Searching for '$query'…"
    }

    // -----------------------------------------------------------------------
    // Handle unknown commands (with optional auto-correct suggestion)
    // -----------------------------------------------------------------------
    private fun handleUnknown(command: ParsedCommand.Unknown): String {
        return if (command.suggestion != null) {
            "I didn't understand '${command.original}'.\n\nDid you mean: '${command.suggestion}'?\n\nTry again with that command!"
        } else {
            "I didn't understand '${command.original}'.\n\nTry commands like:\n• open camera\n• call John\n• set alarm 7am\n• search recipes"
        }
    }
}
