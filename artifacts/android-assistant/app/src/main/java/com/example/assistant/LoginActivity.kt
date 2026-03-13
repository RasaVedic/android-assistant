package com.example.assistant

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private var isSignIn = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val tabAuth = findViewById<TabLayout>(R.id.tabAuth)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val tilConfirmPassword = findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        val btnEmailAuth = findViewById<Button>(R.id.btnEmailAuth)
        val btnGoogle = findViewById<Button>(R.id.btnGoogle)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val tvSkip = findViewById<TextView>(R.id.tvSkip)
        val tvError = findViewById<TextView>(R.id.tvError)

        // ── Tab switching ─────────────────────────────────────────────────────
        tabAuth.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                isSignIn = tab.position == 0
                btnEmailAuth.text = if (isSignIn) "Sign In" else "Create Account"
                tilConfirmPassword.visibility = if (isSignIn) View.GONE else View.VISIBLE
                tvForgotPassword.visibility = if (isSignIn) View.VISIBLE else View.GONE
                tvError.visibility = View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // ── Email Auth ────────────────────────────────────────────────────────
        btnEmailAuth.setOnClickListener {
            val email = etEmail.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString() ?: ""
            val confirmPass = etConfirmPassword.text?.toString() ?: ""

            if (email.isEmpty()) { showError(tvError, "Please enter your email."); return@setOnClickListener }
            if (password.length < 6) { showError(tvError, "Password must be at least 6 characters."); return@setOnClickListener }
            if (!isSignIn && password != confirmPass) { showError(tvError, "Passwords do not match."); return@setOnClickListener }

            setLoading(btnEmailAuth, true)
            tvError.visibility = View.GONE

            lifecycleScope.launch {
                val result = if (isSignIn) {
                    AuthManager.signInWithEmail(this@LoginActivity, email, password)
                } else {
                    AuthManager.registerWithEmail(this@LoginActivity, email, password)
                }
                setLoading(btnEmailAuth, false)

                if (result.success) {
                    goToMain()
                } else {
                    showError(tvError, result.error ?: "Something went wrong.")
                }
            }
        }

        // ── Forgot Password ───────────────────────────────────────────────────
        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // ── Google Sign-In (needs Firebase config) ────────────────────────────
        btnGoogle.setOnClickListener {
            Toast.makeText(
                this,
                "Google Sign-In: Add Firebase config in Settings → Account first.",
                Toast.LENGTH_LONG
            ).show()
        }

        // ── Skip ──────────────────────────────────────────────────────────────
        tvSkip.setOnClickListener {
            AuthManager.skipLogin(this)
            goToMain()
        }
    }

    private fun showError(tvError: TextView, message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun setLoading(btn: Button, loading: Boolean) {
        btn.isEnabled = !loading
        btn.text = if (loading) {
            if (isSignIn) "Signing in…" else "Creating account…"
        } else {
            if (isSignIn) "Sign In" else "Create Account"
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
