package com.example.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver.kt
 * Automatically restarts the Aria background service when the phone reboots.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AssistantBackgroundService.start(context)
        }
    }
}
