package com.example.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.assistant.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * MainActivity.kt
 *
 * PURPOSE: The main (and only) screen of the app.
 *
 * This class ties all the other pieces together:
 *   - Shows the text input and mic button (from activity_main.xml)
 *   - Asks for microphone permission when needed
 *   - Calls VoiceInputHandler when the mic button is tapped
 *   - Calls CommandParser (offline) or GeminiHelper (online) to understand the command
 *   - Calls ActionHandler to execute the command
 *   - Displays the result to the user
 *
 * Beginners:
 *   - ViewBinding lets us access layout views like `binding.btnMic` instead of `findViewById(R.id.btnMic)`
 *   - lifecycleScope.launch { } runs code in a coroutine (background thread) so the UI stays responsive
 *   - The permission launcher handles the runtime permission dialog (Android 6.0+)
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding: auto-generated class that connects to activity_main.xml
    private lateinit var binding: ActivityMainBinding

    // Our modular helpers
    private lateinit var voiceInputHandler: VoiceInputHandler
    private val geminiHelper = GeminiHelper()

    // -----------------------------------------------------------------------
    // Permission launcher for RECORD_AUDIO
    // When the user responds to the permission dialog, this callback runs.
    // -----------------------------------------------------------------------
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceInput()
        } else {
            showResult("⚠️ Microphone permission denied.\nYou can still type commands below.")
        }
    }

    // -----------------------------------------------------------------------
    // Permission launcher for CALL_PHONE
    // -----------------------------------------------------------------------
    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showResult("⚠️ Phone permission denied. Cannot make calls.")
        }
    }

    // -----------------------------------------------------------------------
    // onCreate: called when the Activity is first created
    // -----------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout and set it as the content view
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the toolbar
        setSupportActionBar(binding.toolbar)

        // Set up voice input handler with callbacks
        voiceInputHandler = VoiceInputHandler(
            activity = this,
            onResult  = { recognizedText -> handleCommand(recognizedText) },
            onError   = { error -> showResult("Voice error: $error") },
            onListening = { showResult("🎤 Listening…") }
        )

        // Update the online/offline chip
        updateStatusChip()

        // Request CALL_PHONE permission upfront so it's ready when needed
        if (!hasCallPermission()) {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }

        setupClickListeners()
    }

    // -----------------------------------------------------------------------
    // Wire up button click listeners
    // -----------------------------------------------------------------------
    private fun setupClickListeners() {

        // Mic button: check permission then start voice input
        binding.btnMic.setOnClickListener {
            when {
                !voiceInputHandler.isAvailable() -> {
                    showResult("Speech recognition is not available on this device.\nUse the text input instead.")
                }
                hasMicPermission() -> startVoiceInput()
                else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Send button: read the text field and process it
        binding.btnSend.setOnClickListener {
            submitTextCommand()
        }

        // "Send" action on the keyboard (the enter key with imeOptions="actionSend")
        binding.etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitTextCommand()
                true
            } else false
        }
    }

    // -----------------------------------------------------------------------
    // Submit the text typed in the input field
    // -----------------------------------------------------------------------
    private fun submitTextCommand() {
        val text = binding.etCommand.text?.toString()?.trim() ?: ""
        if (text.isBlank()) return

        binding.etCommand.setText("")
        hideKeyboard()
        handleCommand(text)
    }

    // -----------------------------------------------------------------------
    // Main command processing pipeline
    // 1. Check if online → use Gemini
    // 2. If offline (or Gemini fails) → use local CommandParser
    // 3. Execute the parsed command via ActionHandler
    // -----------------------------------------------------------------------
    private fun handleCommand(input: String) {
        showResult("You said: \"$input\"\n\nProcessing…")
        updateStatusChip()

        // lifecycleScope.launch runs on the main thread but lets us call suspend functions
        lifecycleScope.launch {
            val command: ParsedCommand

            if (isOnline()) {
                // --- Online path: try Gemini first ---
                val geminiResponse = geminiHelper.interpretCommand(input)

                command = if (geminiResponse != null) {
                    geminiHelper.parseGeminiResponse(geminiResponse)
                } else {
                    // Gemini failed (timeout, bad key, etc.) → fall back to local parser
                    CommandParser.parse(input)
                }
            } else {
                // --- Offline path: use local command parser ---
                command = CommandParser.parse(input)
            }

            // Execute the command and get a result message
            val resultMessage = ActionHandler.execute(this@MainActivity, command)

            // Build a nice display string
            val modeLabel = if (isOnline()) "🌐 Online (Gemini)" else "📵 Offline"
            val display = "Command: \"$input\"\nMode: $modeLabel\n\n$resultMessage"
            showResult(display)
        }
    }

    // -----------------------------------------------------------------------
    // Start voice recognition
    // -----------------------------------------------------------------------
    private fun startVoiceInput() {
        voiceInputHandler.startListening()
    }

    // -----------------------------------------------------------------------
    // Update the Online/Offline chip in the toolbar area
    // -----------------------------------------------------------------------
    private fun updateStatusChip() {
        if (isOnline()) {
            binding.chipStatus.text = "Online – Gemini"
            binding.chipStatus.setChipBackgroundColorResource(R.color.teal_700)
        } else {
            binding.chipStatus.text = "Offline"
            binding.chipStatus.setChipBackgroundColorResource(R.color.purple_200)
        }
    }

    // -----------------------------------------------------------------------
    // Display a message in the result TextView and auto-scroll to the bottom
    // -----------------------------------------------------------------------
    private fun showResult(text: String) {
        binding.tvResult.text = text
        // Scroll to bottom so the latest message is always visible
        binding.scrollResult.post {
            binding.scrollResult.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    // -----------------------------------------------------------------------
    // Check if the device has an active internet connection
    // -----------------------------------------------------------------------
    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // -----------------------------------------------------------------------
    // Permission helpers
    // -----------------------------------------------------------------------
    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasCallPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    // -----------------------------------------------------------------------
    // Hide the soft keyboard
    // -----------------------------------------------------------------------
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    // -----------------------------------------------------------------------
    // Lifecycle: stop the voice recognizer when the Activity is paused
    // -----------------------------------------------------------------------
    override fun onPause() {
        super.onPause()
        voiceInputHandler.stopListening()
    }
}
