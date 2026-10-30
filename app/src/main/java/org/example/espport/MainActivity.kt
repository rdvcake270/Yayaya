package org.example.espport

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36,48,36,24) }
  val status = TextView(this).apply { text = "Android ESP Port • sample entity renderer\nRoot is optional for this demo." }
  val root = Button(this).apply { text = "Check KernelSU root"; setOnClickListener {
   Thread { val result = try { val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start(); val output = process.inputStream.bufferedReader().readText(); val code = process.waitFor(); "Exit $code: $output" } catch (e: Exception) { e.toString() }; runOnUiThread { status.text = result } }.start()
  } }
  val start = Button(this).apply { text = "Start sample overlay"; setOnClickListener {
   if (!Settings.canDrawOverlays(this@MainActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
   else startService(Intent(this@MainActivity, OverlayService::class.java))
  } }
  val stop = Button(this).apply { text = "Stop overlay"; setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) } }
  layout.addView(status); layout.addView(root); layout.addView(start); layout.addView(stop); setContentView(layout)
 }
}
