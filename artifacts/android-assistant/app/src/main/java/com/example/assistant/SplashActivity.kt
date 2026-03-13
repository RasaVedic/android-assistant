package com.example.assistant

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.ImageView
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.ivLogo)
        val center = findViewById<View>(R.id.layoutCenter)
        val bottom = findViewById<View>(R.id.layoutBottom)
        val dot1 = findViewById<View>(R.id.dot1)
        val dot2 = findViewById<View>(R.id.dot2)
        val dot3 = findViewById<View>(R.id.dot3)

        // ── Phase 1: Logo + name appear (0–900ms) ────────────────────────────
        logo.scaleX = 0.3f
        logo.scaleY = 0.3f

        val logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.3f, 1.0f).apply {
            duration = 700
            interpolator = OvershootInterpolator(1.5f)
        }
        val logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.3f, 1.0f).apply {
            duration = 700
            interpolator = OvershootInterpolator(1.5f)
        }
        val centerFade = ObjectAnimator.ofFloat(center, "alpha", 0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
        }

        val phase1 = AnimatorSet().apply {
            playTogether(logoScaleX, logoScaleY, centerFade)
        }

        // ── Phase 2: Bottom dots appear (900ms delay) ─────────────────────────
        handler.postDelayed({
            val bottomFade = ObjectAnimator.ofFloat(bottom, "alpha", 0f, 1f).apply {
                duration = 400
                start()
            }
            animateDots(dot1, dot2, dot3)
        }, 900)

        // ── Phase 3: Navigate after 3.5 seconds ───────────────────────────────
        handler.postDelayed({
            navigateNext()
        }, 3500)

        phase1.start()
    }

    private fun animateDots(dot1: View, dot2: View, dot3: View) {
        val dots = listOf(dot1, dot2, dot3)
        var index = 0
        val dotHandler = Handler(Looper.getMainLooper())
        val dotRunnable = object : Runnable {
            override fun run() {
                dots.forEach { it.alpha = 0.25f }
                dots[index % 3].alpha = 1f
                index++
                dotHandler.postDelayed(this, 300)
            }
        }
        dotHandler.post(dotRunnable)
    }

    private fun navigateNext() {
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val skipLogin = prefs.getBoolean("skip_login", false)
        val userEmail = prefs.getString("user_email", null)

        val intent = if (skipLogin || userEmail != null) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
