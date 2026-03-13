package com.example.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.assistant.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "PKassist Settings"

        prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)

        setupVersionSection()
        setupGeminiSection()
        setupPermissionsSection()
        setupBackgroundSection()
        setupAccessibilitySection()
        setupOverlaySection()
    }

    // ─── Version / Update ────────────────────────────────────────────────────
    private fun setupVersionSection() {
        val localVersion = packageManager.getPackageInfo(packageName, 0).versionName
        binding.tvCurrentVersion.text = "Current version: v$localVersion"

        binding.btnCheckUpdate.setOnClickListener {
            binding.btnCheckUpdate.isEnabled = false
            binding.btnCheckUpdate.text = "Checking…"
            lifecycleScope.launch {
                val remote = UpdateChecker.fetchRemoteVersion()
                val localCode = packageManager.getPackageInfo(packageName, 0).versionCode
                binding.btnCheckUpdate.isEnabled = true
                binding.btnCheckUpdate.text = "Check for Update"
                if (remote == null) {
                    Toast.makeText(this@SettingsActivity,
                        "Could not reach update server. Check internet.", Toast.LENGTH_SHORT).show()
                } else if (remote.versionCode > localCode) {
                    UpdateChecker.checkAndPrompt(this@SettingsActivity)
                } else {
                    Toast.makeText(this@SettingsActivity,
                        "✓ You have the latest version (v${remote.versionName})", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─── Gemini AI ───────────────────────────────────────────────────────────
    private fun setupGeminiSection() {
        binding.etApiKey.setText(prefs.getString("gemini_api_key", ""))

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            prefs.edit().putString("gemini_api_key", key).apply()
            Toast.makeText(this, if (key.isNotEmpty()) "✓ API key saved!" else "API key cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnGetKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://aistudio.google.com/app/apikey")))
        }
    }

    // ─── Permissions ─────────────────────────────────────────────────────────
    private fun setupPermissionsSection() {
        updatePermissionStatus()

        binding.btnGrantPermissions.setOnClickListener {
            PermissionHelper.requestAll(this, 100)
        }

        binding.btnOpenAppSettings.setOnClickListener {
            PermissionHelper.openAppSettings(this)
        }
    }

    private fun updatePermissionStatus() {
        val granted = PermissionHelper.grantedCount(this)
        val total = PermissionHelper.RUNTIME_PERMISSIONS.size
        val allOk = granted == total
        binding.tvPermissionStatus.text =
            if (allOk) "✓ All $total permissions granted"
            else "⚠ $granted / $total permissions granted"
        binding.tvPermissionStatus.setTextColor(
            getColor(if (allOk) android.R.color.holo_green_dark else android.R.color.holo_orange_dark)
        )
    }

    // ─── Background Service ──────────────────────────────────────────────────
    private fun setupBackgroundSection() {
        binding.switchBackground.isChecked = prefs.getBoolean("bg_service", true)
        binding.switchBackground.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("bg_service", isChecked).apply()
            if (isChecked) AssistantBackgroundService.start(this)
            else AssistantBackgroundService.stop(this)
            Toast.makeText(this,
                if (isChecked) "Background service ON" else "Background service OFF",
                Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Accessibility ───────────────────────────────────────────────────────
    private fun setupAccessibilitySection() {
        binding.btnAccessibility.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility")
                .setMessage("1. Tap 'Open Settings'\n2. Find 'PKassist' in the list\n3. Tap it → turn ON\n4. Come back here\n\nThis allows PKassist to go back, take screenshots, lock screen, and more.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ─── Overlay (Draw Over Apps) ────────────────────────────────────────────
    private fun setupOverlaySection() {
        binding.btnOverlay.setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        val accessEnabled = AssistantAccessibilityService.isEnabled()
        binding.tvAccessibilityStatus.text =
            if (accessEnabled) "✓ Accessibility: Enabled" else "✗ Accessibility: Disabled — tap button below"
        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (accessEnabled) android.R.color.holo_green_dark else android.R.color.holo_orange_dark)
        )
        val overlayOk = PermissionHelper.canDrawOverlays(this)
        binding.tvOverlayStatus.text =
            if (overlayOk) "✓ Overlay: Allowed" else "✗ Overlay: Not allowed"
        binding.tvOverlayStatus.setTextColor(
            getColor(if (overlayOk) android.R.color.holo_green_dark else android.R.color.holo_orange_dark)
        )
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStatus()
    }
}
