package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.assistant.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: android.content.SharedPreferences

    private val GITHUB_URL = "https://github.com/RasaVedic/android-assistant"
    private val WEBSITE_URL = "https://rasavedic.github.io"
    private val PRIVACY_URL = "https://rasavedic.github.io/aria/privacy"
    private val TERMS_URL = "https://rasavedic.github.io/aria/terms"
    private val FEEDBACK_EMAIL = "rasavedic@gmail.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)

        setupVersionSection()
        setupGeminiSection()
        setupPermissionsSection()
        setupBackgroundSection()
        setupAccessibilitySection()
        setupOverlaySection()
        setupAccountSection()
        setupAboutSection()
        setupSupportSection()
        setupLegalSection()
    }

    // ─── Version / Update ────────────────────────────────────────────────────
    private fun setupVersionSection() {
        val localVersion = packageManager.getPackageInfo(packageName, 0).versionName
        binding.tvCurrentVersion.text = "Current version: v$localVersion"
        binding.tvAboutVersion.text = "Aria AI — Version $localVersion"

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
                        "Could not reach update server.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, if (key.isNotEmpty()) "✓ API key saved!" else "API key cleared",
                Toast.LENGTH_SHORT).show()
        }

        binding.btnGetKey.setOnClickListener {
            openUrl("https://aistudio.google.com/app/apikey")
        }
    }

    // ─── Permissions ─────────────────────────────────────────────────────────
    private fun setupPermissionsSection() {
        updatePermissionStatus()
        binding.btnGrantPermissions.setOnClickListener { PermissionHelper.requestAll(this, 100) }
        binding.btnOpenAppSettings.setOnClickListener { PermissionHelper.openAppSettings(this) }
    }

    private fun updatePermissionStatus() {
        val granted = PermissionHelper.grantedCount(this)
        val total = PermissionHelper.RUNTIME_PERMISSIONS.size
        val allOk = granted == total
        binding.tvPermissionStatus.text =
            if (allOk) "✓ All $total permissions granted"
            else "⚠ $granted / $total permissions granted"
        binding.tvPermissionStatus.setTextColor(
            getColor(if (allOk) R.color.status_online else R.color.status_warning)
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
                .setMessage("1. Tap 'Open Settings'\n2. Find 'Aria' in the list\n3. Tap it → turn ON\n4. Come back here\n\nThis allows Aria to go back, take screenshots, lock screen, and more.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ─── Overlay ─────────────────────────────────────────────────────────────
    private fun setupOverlaySection() {
        binding.btnOverlay.setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }
    }

    // ─── Account ─────────────────────────────────────────────────────────────
    private fun setupAccountSection() {
        refreshAccountUI()

        binding.btnSignIn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btnSignOut.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    AuthManager.signOut(this)
                    refreshAccountUI()
                    Toast.makeText(this, "Signed out successfully.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Firebase API key setup
        binding.etFirebaseApiKey.setText(AuthManager.getFirebaseApiKey(this) ?: "")
        binding.btnSaveFirebaseKey.setOnClickListener {
            val key = binding.etFirebaseApiKey.text?.toString()?.trim() ?: ""
            AuthManager.saveFirebaseApiKey(this, key)
            Toast.makeText(this,
                if (key.isNotEmpty()) "✓ Firebase key saved! Now you can sign in."
                else "Firebase key cleared.",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAccountUI() {
        val isLoggedIn = AuthManager.isLoggedIn(this)
        binding.layoutLoggedIn.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        binding.layoutLoggedOut.visibility = if (isLoggedIn) View.GONE else View.VISIBLE

        if (isLoggedIn) {
            binding.tvUserEmail.text = "Signed in as: ${AuthManager.getCurrentUserEmail(this)}"
        }
    }

    // ─── About ───────────────────────────────────────────────────────────────
    private fun setupAboutSection() {
        binding.btnGitHub.setOnClickListener { openUrl(GITHUB_URL) }
        binding.btnWebsite.setOnClickListener { openUrl(WEBSITE_URL) }
    }

    // ─── Support ─────────────────────────────────────────────────────────────
    private fun setupSupportSection() {
        binding.btnSendFeedback.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$FEEDBACK_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, "Aria AI — Feedback")
                putExtra(Intent.EXTRA_TEXT, "Hi, I have feedback about Aria AI:\n\n")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No email app found.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnReportBug.setOnClickListener {
            openUrl("$GITHUB_URL/issues/new")
        }

        binding.btnContactUs.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$FEEDBACK_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, "Aria AI — Contact")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No email app found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Legal ───────────────────────────────────────────────────────────────
    private fun setupLegalSection() {
        binding.btnPrivacyPolicy.setOnClickListener { openUrl(PRIVACY_URL) }
        binding.btnTerms.setOnClickListener { openUrl(TERMS_URL) }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        refreshAccountUI()
        val accessEnabled = AssistantAccessibilityService.isEnabled()
        binding.tvAccessibilityStatus.text =
            if (accessEnabled) "✓ Accessibility: Enabled" else "✗ Accessibility: Disabled — tap button below"
        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (accessEnabled) R.color.status_online else R.color.status_warning)
        )
        val overlayOk = PermissionHelper.canDrawOverlays(this)
        binding.tvOverlayStatus.text =
            if (overlayOk) "✓ Overlay: Allowed" else "✗ Overlay: Not allowed"
        binding.tvOverlayStatus.setTextColor(
            getColor(if (overlayOk) R.color.status_online else R.color.status_warning)
        )
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStatus()
    }
}
