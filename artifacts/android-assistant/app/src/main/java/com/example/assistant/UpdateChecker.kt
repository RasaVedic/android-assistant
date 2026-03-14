package com.example.assistant

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class VersionInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = ""
)

/**
 * UpdateChecker.kt
 * Checks GitHub for a newer version of Aria.
 * - Shows a dialog when the app is open
 * - Shows a system notification even when the app is in background
 */
object UpdateChecker {

    private const val UPDATE_NOTIF_CHANNEL = "aria_updates"
    private const val UPDATE_NOTIF_ID      = 2001
    private const val PREFS_NAME           = "assistant_prefs"
    private const val KEY_LAST_NOTIF_CODE  = "last_notif_version_code"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Returns remote VersionInfo or null if offline / error */
    suspend fun fetchRemoteVersion(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(BuildConfig.VERSION_CHECK_URL)
                .addHeader("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                Gson().fromJson(body, VersionInfo::class.java)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Call on app start — shows dialog if update available.
     * Also sends a system notification if the update hasn't been notified yet.
     */
    suspend fun checkAndPrompt(context: Context) {
        val remote = fetchRemoteVersion() ?: return
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val local = PackageInfoCompat.getLongVersionCode(packageInfo)

        if (remote.versionCode.toLong() > local) {
            withContext(Dispatchers.Main) {
                // Show in-app dialog
                showUpdateDialog(context, remote)
                // Also send system notification (once per version)
                sendUpdateNotification(context, remote)
            }
        }
    }

    /**
     * Call from background service to silently check and notify without a dialog.
     * Only sends a notification — does not show an AlertDialog.
     */
    suspend fun checkAndNotifyInBackground(context: Context) {
        val remote = fetchRemoteVersion() ?: return
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val local = PackageInfoCompat.getLongVersionCode(packageInfo)

        if (remote.versionCode.toLong() > local) {
            withContext(Dispatchers.Main) {
                sendUpdateNotification(context, remote)
            }
        }
    }

    // ── System notification for update ───────────────────────────────────────

    private fun sendUpdateNotification(context: Context, info: VersionInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getInt(KEY_LAST_NOTIF_CODE, 0)

        // Only notify once per new version — don't spam the user
        if (lastNotified >= info.versionCode) return

        createUpdateNotifChannel(context)

        val downloadUrl = info.downloadUrl.ifBlank { BuildConfig.DOWNLOAD_PAGE_URL }
        val openIntent  = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, UPDATE_NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Aria v${info.versionName} is available!")
            .setContentText("Tap to download the latest update.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("What's new:\n${info.releaseNotes}\n\nTap to download v${info.versionName}."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.stat_sys_download, "Download",  openIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(UPDATE_NOTIF_ID, notification)

        // Remember we've notified for this version
        prefs.edit().putInt(KEY_LAST_NOTIF_CODE, info.versionCode).apply()
    }

    private fun createUpdateNotifChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_NOTIF_CHANNEL,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.update_channel_desc)
                setShowBadge(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    // ── In-app dialog ─────────────────────────────────────────────────────────

    private fun showUpdateDialog(context: Context, info: VersionInfo) {
        val downloadUrl = info.downloadUrl.ifBlank { BuildConfig.DOWNLOAD_PAGE_URL }
        AlertDialog.Builder(context)
            .setTitle("Update Available — v${info.versionName}")
            .setMessage("What's new:\n\n${info.releaseNotes}\n\nTap 'Download' to get the new APK. After downloading, tap the file to install.")
            .setPositiveButton("Download") { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
            }
            .setNeutralButton("View Release") { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DOWNLOAD_PAGE_URL)))
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }
}
