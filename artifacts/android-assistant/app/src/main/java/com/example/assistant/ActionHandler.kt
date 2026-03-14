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
            is ParsedCommand.OpenApp   -> openApp(context, command.appName)
            is ParsedCommand.MakeCall  -> makeCall(context, command.contact)
            is ParsedCommand.SetAlarm  -> setAlarm(context, command.time)
            is ParsedCommand.TakeSelfie -> takeSelfie(context, command.front)
            is ParsedCommand.Search    -> search(context, command.query)
            is ParsedCommand.Navigate  -> navigate(command.action)
            is ParsedCommand.Unknown   -> handleUnknown(command)
        }
    }

    // ── Selfie / Camera capture ───────────────────────────────────────────────
    private fun takeSelfie(context: Context, front: Boolean): String {
        // Strategy 1: Direct front/back camera intent
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            if (front) putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            if (front) "Opening front camera for selfie 🤳" else "Opening camera 📸"
        } catch (e: Exception) {
            // Fallback: open any camera app
            try {
                val fallback = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                "Opening camera 📸"
            } catch (e2: Exception) {
                "Could not open camera. Check camera permission in Settings."
            }
        }
    }

    // ── Accessibility navigation ──────────────────────────────────────────────
    private fun navigate(action: String): String {
        if (!AssistantAccessibilityService.isEnabled()) {
            return "Accessibility service is OFF.\nGo to Settings → Accessibility → Aria → Enable it."
        }
        return when (action) {
            "back"          -> { AssistantAccessibilityService.goBack();     "Going back ←" }
            "home"          -> { AssistantAccessibilityService.goHome();     "Going to home screen 🏠" }
            "recents"       -> { AssistantAccessibilityService.showRecent(); "Showing recent apps" }
            "screenshot"    -> { AssistantAccessibilityService.screenshot(); "Screenshot taken 📸" }
            "notifications" -> { AssistantAccessibilityService.showNotif();  "Opening notifications 🔔" }
            "lock"          -> { AssistantAccessibilityService.lockScreen(); "Screen locked 🔒" }
            "power_menu"    -> "Power menu not supported on this device."
            else            -> "Unknown navigation action"
        }
    }

    // ── Open an app — tries multiple strategies ───────────────────────────────
    private fun openApp(context: Context, appName: String): String {
        if (appName.isBlank()) return "Please say which app to open. Example: 'open camera'"

        val name = appName.trim().lowercase()

        val specialIntent: Intent? = when {
            name.contains("camera") && !name.contains("front") ->
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            name.contains("video") && name.contains("camera") ->
                Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
            name.contains("gallery") || name.contains("photos") || name.contains("photo") ->
                Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
            name.contains("dial") || name.contains("phone") || name.contains("dialer") ->
                Intent(Intent.ACTION_DIAL)
            name.contains("contact") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/"))
            name.contains("message") || name.contains("sms") || name.contains("whatsapp") && name.contains("message") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) }
            name.contains("browser") || name.contains("chrome") || name.contains("internet") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_BROWSER) }
            name.contains("email") || name.contains("gmail") || name.contains("mail") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_EMAIL) }
            name.contains("maps") || name.contains("map") || name.contains("navigation") ->
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MAPS) }
            name.contains("music") || name.contains("player") || name.contains("song") ->
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
                context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))
            name.contains("whatsapp") ->
                context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            name.contains("instagram") ->
                context.packageManager.getLaunchIntentForPackage("com.instagram.android")
            name.contains("spotify") ->
                context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            name.contains("netflix") ->
                context.packageManager.getLaunchIntentForPackage("com.netflix.mediaclient")
            name.contains("twitter") || name.contains(" x ") || name == "x" ->
                context.packageManager.getLaunchIntentForPackage("com.twitter.android")
                    ?: context.packageManager.getLaunchIntentForPackage("com.x.android")
            name.contains("facebook") ->
                context.packageManager.getLaunchIntentForPackage("com.facebook.katana")
            name.contains("telegram") ->
                context.packageManager.getLaunchIntentForPackage("org.telegram.messenger")
            name.contains("zoom") ->
                context.packageManager.getLaunchIntentForPackage("us.zoom.videomeetings")
            name.contains("snapchat") ->
                context.packageManager.getLaunchIntentForPackage("com.snapchat.android")
            name.contains("play store") || name.contains("playstore") ->
                context.packageManager.getLaunchIntentForPackage("com.android.vending")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store"))
            name.contains("amazon") ->
                context.packageManager.getLaunchIntentForPackage("com.amazon.mShop.android.shopping")
            name.contains("flipkart") ->
                context.packageManager.getLaunchIntentForPackage("com.flipkart.android")
            name.contains("paytm") ->
                context.packageManager.getLaunchIntentForPackage("net.one97.paytm")
            name.contains("gpay") || name.contains("google pay") ->
                context.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.paisa.user")
            name.contains("phonepe") ->
                context.packageManager.getLaunchIntentForPackage("com.phonepe.app")
            name.contains("swiggy") ->
                context.packageManager.getLaunchIntentForPackage("in.swiggy.android")
            name.contains("zomato") ->
                context.packageManager.getLaunchIntentForPackage("com.application.zomato")
            else -> null
        }

        if (specialIntent != null) {
            specialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(specialIntent)
                "Opening $appName…"
            } catch (e: Exception) {
                tryLaunchByName(context, name)
            }
        }

        return tryLaunchByName(context, name)
    }

    private fun tryLaunchByName(context: Context, name: String): String {
        val pm   = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Exact label match first, then partial
        val match = apps.firstOrNull { info ->
            pm.getApplicationLabel(info).toString().lowercase() == name
        } ?: apps.firstOrNull { info ->
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

        // Offer to open Play Store as a fallback
        return try {
            val playIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=${Uri.encode(name)}&c=apps")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playIntent)
            "'$name' is not installed. Opening Play Store to find it…"
        } catch (e: Exception) {
            "App '$name' not found on this device."
        }
    }

    // ── Make a call ───────────────────────────────────────────────────────────
    private fun makeCall(context: Context, contact: String): String {
        if (contact.isBlank()) return "Please say a name or number. Example: 'call 98XXXXXXXX'"
        val digits   = contact.replace(Regex("[+\\-\\s().]"), "")
        val isNumber = digits.all { it.isDigit() } && digits.isNotEmpty()
        val uri      = if (isNumber) Uri.parse("tel:$digits") else Uri.parse("tel:$contact")
        val intent   = Intent(Intent.ACTION_CALL, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return try {
            context.startActivity(intent)
            "Calling $contact…"
        } catch (e: SecurityException) {
            "Phone permission denied. Go to Settings → Apps → Aria → Permissions → Phone → Allow"
        } catch (e: Exception) {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(dialIntent)
            "Opening dial pad for $contact…"
        }
    }

    // ── Set alarm ─────────────────────────────────────────────────────────────
    private fun setAlarm(context: Context, timeText: String): String {
        if (timeText.isBlank()) return "Please say a time. Example: 'set alarm 7am' or 'alarm 6:30pm'"
        val (hour, minute, display) = parseTime(timeText)
        if (hour == null) return "Couldn't understand '$timeText'.\nTry: 'alarm 7am' or 'alarm 6:30pm'"

        // Strategy 1: AlarmClock.ACTION_SET_ALARM (works on most phones)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Aria alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Alarm set for $display ✓"
        } catch (e: Exception) {
            // Strategy 2: Open clock app directly
            tryAlarmFallback(context, hour, minute, display)
        }
    }

    private fun tryAlarmFallback(context: Context, hour: Int, minute: Int, display: String): String {
        val clockPackages = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.samsung.android.app.clockpackage",
            "com.miui.clock",
            "com.oneplus.clock",
            "com.realme.clock",
            "com.oppo.clock"
        )
        val pm = context.packageManager
        for (pkg in clockPackages) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                setPackage(pkg)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Aria alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return "Alarm set for $display ✓"
            } catch (_: Exception) { }
        }
        // Last resort: open alarm list so user can add manually
        return try {
            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "Couldn't set alarm automatically. Clock app opened — please add alarm for $display manually."
        } catch (_: Exception) {
            "No alarm app found on this device."
        }
    }

    private fun parseTime(text: String): Triple<Int?, Int, String> {
        // Remove non-time words
        val cleaned = text.lowercase()
            .replace("p.m.", "pm").replace("a.m.", "am")
            .replace("p. m.", "pm").replace("a. m.", "am")
            .replace("alarm", "").replace("set", "").replace("at", "")
            .replace("baje", "pm").replace("subah", "am").replace("shaam", "pm")
            .replace("raat", "pm").replace("dopahar", "pm").replace("dophar", "pm")
            .trim()

        val regex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        val match  = regex.find(cleaned) ?: return Triple(null, 0, "")
        var hour   = match.groupValues[1].toIntOrNull() ?: return Triple(null, 0, "")
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val ampm   = match.groupValues[3].lowercase()

        // Guess AM/PM for common patterns if not specified
        if (ampm.isEmpty()) {
            hour = when {
                hour in 1..5  -> hour + 12  // 1-5 without AM/PM → assume PM (afternoon/evening)
                hour == 12    -> 12
                else          -> hour
            }
        } else {
            if (ampm == "pm" && hour != 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
        }

        val displayMinute = minute.toString().padStart(2, '0')
        val origHour      = match.groupValues[1]
        val display       = "$origHour:$displayMinute${if (ampm.isNotEmpty()) " $ampm" else ""}"
        return Triple(hour, minute, display)
    }

    // ── Web search ────────────────────────────────────────────────────────────
    private fun search(context: Context, query: String): String {
        if (query.isBlank()) return "Please say what to search. Example: 'search weather today'"
        val uri    = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return try {
            context.startActivity(intent)
            "Searching for \"$query\"…"
        } catch (e: Exception) {
            "No browser found to perform search."
        }
    }

    // ── Unknown command ───────────────────────────────────────────────────────
    private fun handleUnknown(command: ParsedCommand.Unknown): String {
        return if (command.suggestion != null) {
            "Did you mean: \"${command.suggestion}\"?\n\nTry again with that command."
        } else {
            "Command not recognized.\n\nTry:\n• open camera\n• take selfie\n• call 98XXXXXXXX\n• set alarm 7am\n• search recipes\n• go back / go home\n• take screenshot"
        }
    }
}
