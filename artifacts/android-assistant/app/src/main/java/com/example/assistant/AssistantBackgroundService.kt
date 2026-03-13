package com.example.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * AssistantBackgroundService.kt
 *
 * Foreground service that:
 * 1. Keeps Aria alive in the background (persistent notification)
 * 2. Runs continuous voice listening — just like Google Assistant.
 *    When the user says a command, it is executed even if another app is open.
 * 3. Automatically checks for new Aria version every hour and sends a
 *    system notification when a new version is available.
 *
 * Voice listening is only active when the user has enabled it via
 * Settings → Background Listening switch.
 */
class AssistantBackgroundService : Service() {

    companion object {
        const val CHANNEL_ID = "aria_service_channel"
        const val NOTIF_ID   = 1001

        const val ACTION_START_LISTENING = "com.example.assistant.START_LISTENING"
        const val ACTION_STOP_LISTENING  = "com.example.assistant.STOP_LISTENING"

        private var instance: AssistantBackgroundService? = null

        fun start(context: Context) {
            val intent = Intent(context, AssistantBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantBackgroundService::class.java))
        }

        /** Called from MainActivity to kick off one background recognition cycle */
        fun startOneShot(context: Context) {
            instance?.startOneShotRecognition()
        }
    }

    private val serviceJob   = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val handler      = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningActive = false
    private var continuousListeningEnabled = false

    // Update check every 60 minutes
    private val updateCheckIntervalMs = 60 * 60 * 1000L
    private val updateCheckRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch {
                UpdateChecker.checkAndNotifyInBackground(this@AssistantBackgroundService)
            }
            handler.postDelayed(this, updateCheckIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        // Start periodic update checks
        handler.postDelayed(updateCheckRunnable, 5 * 60 * 1000L) // first check after 5 min
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                continuousListeningEnabled = true
                startContinuousListening()
            }
            ACTION_STOP_LISTENING -> {
                continuousListeningEnabled = false
                stopListening()
            }
        }

        // Check prefs: if bg listening was previously ON, restore it
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("bg_voice_listening", false)) {
            continuousListeningEnabled = true
            startContinuousListening()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        stopListening()
        handler.removeCallbacksAndMessages(null)
        serviceJob.cancel()
        super.onDestroy()
    }

    // ── Continuous voice recognition ──────────────────────────────────────────

    private fun startContinuousListening() {
        if (isListeningActive) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        startOneShotRecognition()
    }

    fun startOneShotRecognition() {
        if (isListeningActive) return
        isListeningActive = true

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: android.os.Bundle) {
                isListeningActive = false
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text    = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    handleBackgroundCommand(text)
                }
                // Restart listening after a short pause (continuous loop)
                if (continuousListeningEnabled) {
                    handler.postDelayed({ startOneShotRecognition() }, 1200)
                }
            }

            override fun onError(error: Int) {
                isListeningActive = false
                // Restart automatically on most errors (network, no-match, timeout)
                if (continuousListeningEnabled) {
                    val delayMs = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 5000L
                        else -> 1500L
                    }
                    handler.postDelayed({ startOneShotRecognition() }, delayMs)
                }
            }

            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isListeningActive = false
        }
    }

    private fun stopListening() {
        isListeningActive = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // ── Process background commands ───────────────────────────────────────────

    private fun handleBackgroundCommand(input: String) {
        serviceScope.launch {
            val geminiHelper = GeminiHelper(this@AssistantBackgroundService)
            val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
            val online = isOnline()

            val command = if (online && geminiHelper.hasApiKey()) {
                val geminiResponse = geminiHelper.interpretCommand(input)
                if (geminiResponse != null) geminiHelper.parseGeminiResponse(geminiResponse)
                else CommandParser.parse(input)
            } else {
                CommandParser.parse(input)
            }

            ActionHandler.execute(this@AssistantBackgroundService, command)
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()
}
