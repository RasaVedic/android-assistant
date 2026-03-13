package com.example.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.example.assistant.databinding.ActivityMainBinding
import com.example.assistant.databinding.ItemCommandBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.util.Locale

data class CommandItem(val command: String, val result: String, val isOnline: Boolean)

class CommandAdapter(private val items: MutableList<CommandItem>) :
    RecyclerView.Adapter<CommandAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCommandBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCommandBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvCommandText.text = "\"${item.command}\""
        holder.binding.tvResult.text = item.result
        holder.binding.chipMode.text = if (item.isOnline) "Gemini" else "Offline"
        holder.binding.chipMode.setChipBackgroundColorResource(
            if (item.isOnline) R.color.teal_200 else R.color.purple_200
        )
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var geminiHelper: GeminiHelper
    private val commandHistory = mutableListOf<CommandItem>()
    private lateinit var adapter: CommandAdapter

    // -----------------------------------------------------------------------
    // Permission launcher for CALL_PHONE
    // -----------------------------------------------------------------------
    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled in ActionHandler fallback */ }

    // -----------------------------------------------------------------------
    // Voice recognition — activity-based (most reliable, works on all phones)
    // -----------------------------------------------------------------------
    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull()?.trim() ?: ""
            if (text.isNotBlank()) {
                binding.etCommand.setText(text)
                handleCommand(text)
            }
        }
    }

    // -----------------------------------------------------------------------
    // onCreate
    // -----------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        geminiHelper = GeminiHelper(this)

        // RecyclerView setup
        adapter = CommandAdapter(commandHistory)
        binding.rvHistory.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvHistory.adapter = adapter

        updateStatusChip()
        updateEmptyState()

        // Request CALL_PHONE permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }

        setupListeners()
    }

    // -----------------------------------------------------------------------
    // Toolbar menu
    // -----------------------------------------------------------------------
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_clear -> {
                commandHistory.clear()
                adapter.notifyDataSetChanged()
                updateEmptyState()
                updateCommandCount()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // -----------------------------------------------------------------------
    // Button listeners
    // -----------------------------------------------------------------------
    private fun setupListeners() {
        binding.btnMic.setOnClickListener { startVoiceInput() }

        binding.btnSend.setOnClickListener { submitTextCommand() }

        binding.etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitTextCommand(); true
            } else false
        }
    }

    private fun submitTextCommand() {
        val text = binding.etCommand.text?.toString()?.trim() ?: ""
        if (text.isBlank()) return
        binding.etCommand.setText("")
        hideKeyboard()
        handleCommand(text)
    }

    // -----------------------------------------------------------------------
    // Voice input — uses system dialog (reliable on all Android phones)
    // -----------------------------------------------------------------------
    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            addToHistory("Voice", "Speech recognition not available on this device. Use text input.", false)
        }
    }

    // -----------------------------------------------------------------------
    // Main pipeline: offline → Gemini (if key set + online)
    // -----------------------------------------------------------------------
    private fun handleCommand(input: String) {
        updateStatusChip()
        lifecycleScope.launch {
            val online = isOnline()
            val command: ParsedCommand

            if (online && geminiHelper.hasApiKey()) {
                val geminiResponse = geminiHelper.interpretCommand(input)
                command = if (geminiResponse != null) {
                    geminiHelper.parseGeminiResponse(geminiResponse)
                } else {
                    CommandParser.parse(input)
                }
            } else {
                command = CommandParser.parse(input)
            }

            val resultMessage = ActionHandler.execute(this@MainActivity, command)
            val usedOnline = online && geminiHelper.hasApiKey()
            addToHistory(input, resultMessage, usedOnline)
        }
    }

    // -----------------------------------------------------------------------
    // Add item to history list
    // -----------------------------------------------------------------------
    private fun addToHistory(command: String, result: String, isOnline: Boolean) {
        commandHistory.add(CommandItem(command, result, isOnline))
        adapter.notifyItemInserted(commandHistory.size - 1)
        binding.rvHistory.scrollToPosition(commandHistory.size - 1)
        updateEmptyState()
        updateCommandCount()
    }

    // -----------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------
    private fun updateEmptyState() {
        val isEmpty = commandHistory.isEmpty()
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun updateCommandCount() {
        binding.tvCommandCount.text = "${commandHistory.size} command${if (commandHistory.size != 1) "s" else ""}"
    }

    private fun updateStatusChip() {
        val online = isOnline()
        val hasKey = geminiHelper.hasApiKey()
        binding.chipStatus.text = when {
            online && hasKey -> "Online · Gemini"
            online           -> "Online · No API key"
            else             -> "Offline"
        }
        binding.chipStatus.setChipBackgroundColorResource(
            if (online && hasKey) R.color.teal_700 else R.color.purple_200
        )
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        updateStatusChip() // Refresh after returning from Settings
    }
}
