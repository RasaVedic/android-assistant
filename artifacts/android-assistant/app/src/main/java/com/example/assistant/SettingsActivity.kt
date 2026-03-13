package com.example.assistant

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.assistant.databinding.ActivitySettingsBinding

/**
 * SettingsActivity.kt
 * Lets the user enter their Gemini API key and save it.
 * Key is stored in SharedPreferences (private to this app).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        // Load saved key
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "")
        binding.etApiKey.setText(savedKey)

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            prefs.edit().putString("gemini_api_key", key).apply()
            Toast.makeText(this, if (key.isNotEmpty()) "API key saved!" else "API key cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnGetKey.setOnClickListener {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://aistudio.google.com/app/apikey")
            )
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
