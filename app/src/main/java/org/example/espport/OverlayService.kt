package org.example.espport

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.view.Gravity

class OverlayService : Service() {
 private lateinit var wm: WindowManager
 private var view: View? = null
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onCreate() {
  super.onCreate()
  wm = getSystemService(WINDOW_SERVICE) as WindowManager
  view = object : View(this) {
   private val paint = Paint(3).apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f }
   private val label = Paint(3).apply { color = Color.WHITE; textSize = 36f }
   override fun onDraw(c: Canvas) {
    super.onDraw(c)
    // Synthetic positions: reusable native world-to-screen mathematics.
    val matrix = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)
    val points = arrayOf(floatArrayOf(-0.6f,0f,0f),floatArrayOf(0f,0f,0f),floatArrayOf(0.6f,0f,0f))
    for ((index, pos) in points.withIndex()) {
     val screen = NativeMath.project(pos, matrix, width.toFloat(), height.toFloat())
     val boxH = height * 0.15f
     c.drawRect(screen[0]-boxH/4, screen[1]-boxH/2, screen[0]+boxH/4,screen[1]+boxH/2,paint)
     c.drawText("Sample ${index+1}",screen[0]-boxH/4,screen[1]-boxH/2-12,label)
    }
   }
  }
  val params = WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
  wm.addView(view,params)
 }
 override fun onDestroy() { view?.let { wm.removeView(it) }; view = null; super.onDestroy() }
}
object NativeMath {
 init { System.loadLibrary("espmath") }
 external fun project(position: FloatArray, matrix: FloatArray, width: Float, height: Float): FloatArray
}
