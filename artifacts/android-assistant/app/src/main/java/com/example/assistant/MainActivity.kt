package com.example.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.assistant.databinding.ActivityMainBinding
import com.example.assistant.databinding.ItemCommandBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandItem(
    val command: String,
    val result: String,
    val isOnline: Boolean,
    val time: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)

class CommandAdapter(private val items: MutableList<CommandItem>) :
    RecyclerView.Adapter<CommandAdapter.ViewHolder>() {

    private var lastAnimatedPosition = -1

    class ViewHolder(val binding: ItemCommandBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCommandBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvCommandText.text = "\"${item.command}\""
        holder.binding.tvResult.text = item.result
        holder.binding.tvTimestamp.text = item.time

        if (item.isOnline) {
            holder.binding.chipMode.text = "Gemini"
            holder.binding.chipMode.setChipBackgroundColorResource(R.color.status_online)
            holder.binding.viewAccent.setBackgroundResource(R.color.status_online)
        } else {
            holder.binding.chipMode.text = "Offline"
            holder.binding.chipMode.setChipBackgroundColorResource(R.color.brand_primary)
            holder.binding.viewAccent.setBackgroundResource(R.color.brand_primary)
        }

        if (position > lastAnimatedPosition) {
            val anim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.slide_in_bottom)
            holder.itemView.startAnimation(anim)
            lastAnimatedPosition = position
        }
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var geminiHelper: GeminiHelper
    private val commandHistory = mutableListOf<CommandItem>()
    private lateinit var adapter: CommandAdapter
    private var micAnimator: ObjectAnimator? = null

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
        stopListeningState()
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

        if (!PermissionHelper.allGranted(this)) {
            PermissionHelper.requestAll(this, 100)
        }

        AssistantBackgroundService.start(this)

        lifecycleScope.launch {
            UpdateChecker.checkAndPrompt(this@MainActivity)
        }

        adapter = CommandAdapter(commandHistory)
        binding.rvHistory.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvHistory.adapter = adapter

        updateStatusChip()
        updateEmptyState()

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
                overridePendingTransition(R.anim.activity_enter, R.anim.activity_exit)
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
            startListeningState()
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            stopListeningState()
            addToHistory("Voice", "Speech recognition not available on this device. Use text input.", false)
        }
    }

    // -----------------------------------------------------------------------
    // Mic listening state — pulse animation + label
    // -----------------------------------------------------------------------
    private fun startListeningState() {
        binding.tvListening.visibility = View.VISIBLE
        micAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.btnMic,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f, 1f)
        ).apply {
            duration = 700
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopListeningState() {
        binding.tvListening.visibility = View.GONE
        micAnimator?.cancel()
        micAnimator = null
        binding.btnMic.scaleX = 1f
        binding.btnMic.scaleY = 1f
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
            if (online && hasKey) R.color.status_online else R.color.brand_primary
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
        updateStatusChip()
    }

    override fun onDestroy() {
        super.onDestroy()
        micAnimator?.cancel()
    }
}
