package com.example.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var alpha: Float,
        var speedX: Float,
        var speedY: Float,
        var alphaSpeed: Float,
        var color: Int
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var tick = 0f
    private var running = false

    private val colors = intArrayOf(
        0xFF7C6DFF.toInt(),
        0xFF00E5FF.toInt(),
        0xFF9E91FF.toInt(),
        0xFF00B8CC.toInt()
    )

    private val runnable = object : Runnable {
        override fun run() {
            tick += 0.016f
            updateParticles()
            invalidate()
            if (running) handler.postDelayed(this, 16)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        initParticles(w, h)
    }

    private fun initParticles(w: Int, h: Int) {
        particles.clear()
        repeat(40) {
            particles.add(createParticle(w, h, Random.nextFloat()))
        }
    }

    private fun createParticle(w: Int, h: Int, initialPhase: Float): Particle {
        return Particle(
            x = Random.nextFloat() * w,
            y = Random.nextFloat() * h,
            radius = Random.nextFloat() * 3f + 1f,
            alpha = initialPhase,
            speedX = (Random.nextFloat() - 0.5f) * 0.4f,
            speedY = (Random.nextFloat() - 0.5f) * 0.4f,
            alphaSpeed = Random.nextFloat() * 0.005f + 0.002f,
            color = colors[Random.nextInt(colors.size)]
        )
    }

    private fun updateParticles() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        for (p in particles) {
            p.x += p.speedX
            p.y += p.speedY
            p.alpha = (sin(tick * p.alphaSpeed * 100 + p.x).toFloat() + 1f) / 2f * 0.6f + 0.1f

            if (p.x < 0) p.x = w
            if (p.x > w) p.x = 0f
            if (p.y < 0) p.y = h
            if (p.y > h) p.y = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (p in particles) {
            val baseColor = p.color
            val a = (p.alpha * 255).toInt().coerceIn(0, 255)
            val color = (baseColor and 0x00FFFFFF) or (a shl 24)
            paint.color = color
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        handler.post(runnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        handler.removeCallbacks(runnable)
    }
}
