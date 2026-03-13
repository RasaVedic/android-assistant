package com.example.assistant

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
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
 * Checks GitHub for a newer version of PKassist.
 * Shows a dialog if an update is available.
 */
object UpdateChecker {

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

    /** Call on app start — shows dialog if update available */
    suspend fun checkAndPrompt(context: Context) {
        val remote = fetchRemoteVersion() ?: return
        val local = context.packageManager
            .getPackageInfo(context.packageName, 0).versionCode

        if (remote.versionCode > local) {
            withContext(Dispatchers.Main) {
                showUpdateDialog(context, remote)
            }
        }
    }

    private fun showUpdateDialog(context: Context, info: VersionInfo) {
        AlertDialog.Builder(context)
            .setTitle("🆕 Update Available — v${info.versionName}")
            .setMessage("What's new:\n${info.releaseNotes}\n\nDownload the new APK from GitHub Actions.")
            .setPositiveButton("Download Now") { _, _ ->
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                )
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
