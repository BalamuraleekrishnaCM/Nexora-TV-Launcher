package com.nexora.tv

import android.service.dreams.DreamService
import android.graphics.Color
import android.view.Gravity
import android.widget.TextClock
import android.widget.LinearLayout
import android.widget.TextView

class AmbientDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(8, 13, 28))
        }
        root.addView(TextClock(this).apply {
            format12Hour = "h:mm a"
            format24Hour = "HH:mm"
            textSize = 62f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "NEXORA AMBIENT"
            textSize = 16f
            letterSpacing = 0.3f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        })
        setContentView(root)
    }
}
