package com.example.assistant

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val etEmail = findViewById<TextInputEditText>(R.id.etResetEmail)
        val btnSend = findViewById<Button>(R.id.btnSendReset)
        val tvStatus = findViewById<TextView>(R.id.tvResetStatus)
        val tvBack = findViewById<TextView>(R.id.tvBackToLogin)

        btnSend.setOnClickListener {
            val email = etEmail.text?.toString()?.trim() ?: ""
            if (email.isEmpty()) {
                showStatus(tvStatus, "Please enter your email address.", isError = true)
                return@setOnClickListener
            }

            btnSend.isEnabled = false
            btnSend.text = "Sending…"
            tvStatus.visibility = View.GONE

            lifecycleScope.launch {
                val result = AuthManager.sendPasswordReset(this@ForgotPasswordActivity, email)
                btnSend.isEnabled = true
                btnSend.text = "Send Reset Link"

                if (result.success) {
                    showStatus(tvStatus, "✓ Reset link sent! Check your email inbox.", isError = false)
                    btnSend.isEnabled = false
                } else {
                    showStatus(tvStatus, result.error ?: "Failed to send reset email.", isError = true)
                }
            }
        }

        tvBack.setOnClickListener { finish() }
    }

    private fun showStatus(tvStatus: TextView, message: String, isError: Boolean) {
        tvStatus.text = message
        tvStatus.setTextColor(
            getColor(if (isError) android.R.color.holo_red_light else R.color.status_online)
        )
        tvStatus.visibility = View.VISIBLE
    }
}
