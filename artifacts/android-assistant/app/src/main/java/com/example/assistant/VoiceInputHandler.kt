package com.example.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * VoiceInputHandler.kt
 *
 * PURPOSE: Handle everything related to microphone / voice input.
 *
 * HOW IT WORKS:
 * Android's built-in SpeechRecognizer converts spoken audio to text.
 * We start it, listen for results, and call the provided callbacks.
 *
 * Beginners: A "callback" is a function you pass in so this class can
 * call it when something happens (e.g. "here is the recognized text").
 */
class VoiceInputHandler(
    private val activity: Activity,
    private val onResult: (String) -> Unit,       // called when speech is recognized
    private val onError: (String) -> Unit,         // called when something goes wrong
    private val onListening: () -> Unit            // called when the mic is active
) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Check whether the device has a speech recognizer installed.
     * Most Android phones do, but tablets or stripped-down ROMs might not.
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(activity)

    /**
     * Start listening. The mic opens, and Android transcribes the audio.
     * Results come back via the onResult callback.
     */
    fun startListening() {
        // Clean up any previous instance
        speechRecognizer?.destroy()

        // Create a new recognizer attached to this Activity's context
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        speechRecognizer?.setRecognitionListener(recognitionListener)

        // Build the intent that tells Android HOW to recognize speech
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM  // natural free-form speech
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)    // we only need the top result
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        onListening()  // notify the UI that we're now listening
        speechRecognizer?.startListening(intent)
    }

    /** Stop listening and release the microphone. */
    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // -----------------------------------------------------------------------
    // RecognitionListener: Android calls these methods as speech is processed
    // -----------------------------------------------------------------------
    private val recognitionListener = object : RecognitionListener {

        override fun onResults(results: Bundle) {
            // results contains a ranked list of possible transcriptions
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                onResult(text)   // pass the recognized text back to MainActivity
            } else {
                onError("Could not understand. Please try again.")
            }
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO             -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT            -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                SpeechRecognizer.ERROR_NETWORK           -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT   -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH          -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY   -> "Recognizer busy — try again"
                SpeechRecognizer.ERROR_SERVER            -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT    -> "No speech detected"
                else                                     -> "Unknown error ($error)"
            }
            onError(message)
        }

        // The methods below are part of the interface but we don't need them for this simple app
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
