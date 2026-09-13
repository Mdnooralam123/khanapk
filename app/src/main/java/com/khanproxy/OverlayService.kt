package com.khanproxy

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var box: LinearLayout
    private lateinit var tvKey: TextView
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            build()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun build() {
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(24, 16, 24, 16)
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#0D1A14"))
        bg.cornerRadius = 22f
        bg.setStroke(3, Color.parseColor("#4ADE80"))
        box.background = bg

        val h = LinearLayout(this); h.orientation = LinearLayout.HORIZONTAL

        tvKey = TextView(this)
        tvKey.text = "KEY: —"
        tvKey.setTextColor(Color.parseColor("#4ADE80"))
        tvKey.textSize = 14f
        tvKey.typeface = Typeface.DEFAULT_BOLD
        h.addView(tvKey, LinearLayout.LayoutParams(0, -2, 1f))

        val closeB = Button(this); closeB.text = "X"
        closeB.setTextColor(Color.WHITE); closeB.textSize = 12f
        closeB.setBackgroundColor(Color.parseColor("#FF3045"))
        h.addView(closeB, LinearLayout.LayoutParams(80, 80))
        box.addView(h)

        val sub = TextView(this)
        sub.text = "Live · refresh 10s"
        sub.setTextColor(Color.parseColor("#7A9A87"))
        sub.textSize = 10f
        box.addView(sub)

        closeB.setOnClickListener { stopSelf() }

        box.setOnTouchListener(object : View.OnTouchListener {
            private var iX = 0; private var iY = 0; private var tX = 0f; private var tY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { iX = params.x; iY = params.y; tX = e.rawX; tY = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = iX + (e.rawX - tX).toInt()
                        params.y = iY + (e.rawY - tY).toInt()
                        try { wm.updateViewLayout(box, params) } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })

        val t = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, t,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 200 }

        try { wm.addView(box, params) } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val key = intent?.getStringExtra("key") ?: ""
        if (key.isNotEmpty()) {
            try { tvKey.text = "KEY: $key" } catch (_: Exception) {}
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { wm.removeView(box) } catch (_: Exception) {}
        super.onDestroy()
    }
}
