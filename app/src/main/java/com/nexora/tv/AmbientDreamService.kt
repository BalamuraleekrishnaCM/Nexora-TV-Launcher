package com.nexora.tv

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Lightweight system screensaver. Activation and timeout are controlled by the
 * Android TV firmware; some OEMs do not expose third-party dreams.
 */
class AmbientDreamService : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private var clockPanel: LinearLayout? = null
    private var frame: FrameLayout? = null
    private var position = 0

    private val moveClock = object : Runnable {
        override fun run() {
            val container = frame ?: return
            val panel = clockPanel ?: return
            val width = container.width
            val height = container.height
            if (width > 0 && height > 0) {
                val points = arrayOf(
                    0.15f to 0.18f, 0.68f to 0.22f,
                    0.30f to 0.65f, 0.62f to 0.67f
                )
                val point = points[position % points.size]
                panel.translationX = (width * point.first - panel.width / 2).coerceIn(0f, (width - panel.width).toFloat().coerceAtLeast(0f))
                panel.translationY = (height * point.second - panel.height / 2).coerceIn(0f, (height - panel.height).toFloat().coerceAtLeast(0f))
                position++
            }
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val prefs = getSharedPreferences("nexora", MODE_PRIVATE)
        val mode = prefs.getString("ui_mode", "balanced") ?: "balanced"
        val background = if (mode == "lite") Color.rgb(4, 8, 15) else Color.rgb(9, 17, 35)
        val root = FrameLayout(this).apply { setBackgroundColor(background) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
        }
        panel.addView(TextClock(this).apply {
            format12Hour = "h:mm a"
            format24Hour = "HH:mm"
            textSize = 58f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        panel.addView(TextView(this).apply {
            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
            textSize = 19f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        })
        panel.addView(TextView(this).apply {
            text = "NEXORA AMBIENT"
            textSize = 12f
            letterSpacing = 0.2f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        })
        root.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        clockPanel = panel
        frame = root
        setContentView(root)
        handler.postDelayed(moveClock, 1500L)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(moveClock)
        clockPanel = null
        frame = null
        super.onDetachedFromWindow()
    }
}
