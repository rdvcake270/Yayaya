package org.example.espport

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.concurrent.Executors

/** Full-screen Android equivalent of DiagnosticOverlay and EspDrawing. */
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var drawing: EspDrawingView
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val provider: EspFrameProvider = RootBoxFrameProvider()
    private var stopped = false

    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            worker.execute {
                val frame = runCatching { provider.readFrame() }
                    .getOrElse { EspFrame(status = "ESP: ${it.message ?: "read failed"}") }
                main.post {
                    if (!stopped) {
                        drawing.update(frame)
                        main.postDelayed(this, 80L)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        drawing = EspDrawingView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(drawing, params)
        main.post(tick)
    }

    override fun onDestroy() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        if (::drawing.isInitialized) windowManager.removeView(drawing)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class EspDrawingView : View(this@OverlayService) {
        private var frame = EspFrame()
        private var receivedAt = 0L
        private val boxPen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        private val bonePen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 1f }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBE000000.toInt() }
        private val healthPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun update(next: EspFrame) {
            frame = next
            receivedAt = System.currentTimeMillis()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            if (!frame.valid || System.currentTimeMillis() - receivedAt > 300L) {
                drawStatus(canvas, frame.status)
                return
            }
            frame.boxes.forEach { drawBox(canvas, it) }
            canvas.drawRoundRect(RectF(12f, 12f, 380f, 48f), 5f, 5f, backgroundPaint)
            canvas.drawText("TEST ESP · ${frame.accepted} alvos · somente leitura", 22f, 36f, textPaint)
        }

        private fun drawStatus(canvas: Canvas, status: String) {
            canvas.drawRoundRect(RectF(12f, 12f, width - 12f, 88f), 5f, 5f, backgroundPaint)
            val line = status.lineSequence().lastOrNull { it.isNotBlank() } ?: "Waiting for root-memory probe"
            canvas.drawText(line.take(120), 22f, 44f, textPaint)
            canvas.drawText("Root diagnostic · boxes unavailable", 22f, 72f, textPaint)
        }

        private fun drawBox(canvas: Canvas, item: EspFrameBox) {
            val x = item.x * width
            val top = item.top * height
            val boxWidth = item.width * width
            val boxHeight = item.height * height
            if (!x.isFinite() || !top.isFinite() || boxHeight < 3f || item.maxHp <= 0) return
            canvas.drawRect(x - boxWidth / 2f, top, x + boxWidth / 2f, top + boxHeight, boxPen)
            item.bones.forEach { bone ->
                if (bone.size == 4 && bone.all(Float::isFinite)) {
                    canvas.drawLine(bone[0] * width, bone[1] * height, bone[2] * width, bone[3] * height, bonePen)
                }
            }
            val label = "${item.name.ifBlank { "Entidade" }} [${item.distance.toInt()} m] · ${item.hp}/${item.maxHp}"
            canvas.drawText(label, x - textPaint.measureText(label) / 2f, maxOf(24f, top - 8f), textPaint)
            val ratio = (item.hp.toFloat() / item.maxHp).coerceIn(0f, 1f)
            val barLeft = x - boxWidth / 2f - 8f
            healthPaint.color = Color.BLACK
            canvas.drawRect(barLeft, top, barLeft + 4f, top + boxHeight, healthPaint)
            healthPaint.color = Color.GREEN
            canvas.drawRect(barLeft, top + boxHeight * (1f - ratio), barLeft + 4f, top + boxHeight, healthPaint)
        }
    }
}
