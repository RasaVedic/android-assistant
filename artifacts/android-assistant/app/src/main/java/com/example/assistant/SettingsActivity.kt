package com.example.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.assistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "PKassist Settings"

        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)

        // Load saved Gemini key
        binding.etApiKey.setText(prefs.getString("gemini_api_key", ""))

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            prefs.edit().putString("gemini_api_key", key).apply()
            Toast.makeText(this, if (key.isNotEmpty()) "✓ API key saved!" else "API key cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnGetKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey")))
        }

        // Background service toggle
        binding.switchBackground.isChecked = prefs.getBoolean("bg_service", true)
        binding.switchBackground.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("bg_service", isChecked).apply()
            if (isChecked) AssistantBackgroundService.start(this)
            else AssistantBackgroundService.stop(this)
            Toast.makeText(this, if (isChecked) "Background service ON" else "Background service OFF", Toast.LENGTH_SHORT).show()
        }

        // Accessibility service
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find 'PKassist' and enable it", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = AssistantAccessibilityService.isEnabled()
        binding.tvAccessibilityStatus.text = if (enabled) "✓ Accessibility: Enabled" else "✗ Accessibility: Disabled"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
