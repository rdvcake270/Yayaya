package org.example.espport

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(36, 48, 36, 24)
        }
        val status = TextView(this).apply {
            text = "Android ESP renderer demo\nRoot is optional for the sample overlay."
            textSize = 18f
        }
        val rootCheck = Button(this).apply {
            text = "Check KernelSU root"
            setOnClickListener { button ->
                button.isEnabled = false
                Thread {
                    val message = try {
                        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                        val output = process.inputStream.bufferedReader().use { it.readText() }
                        "Root check exit ${process.waitFor()}: $output"
                    } catch (e: Exception) {
                        e.message ?: "Root check failed"
                    }
                    runOnUiThread {
                        status.text = message
                        button.isEnabled = true
                    }
                }.start()
            }
        }
        val address = EditText(this).apply {
            hint = "Memory address (for example 0x12345678)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val readMemory = Button(this).apply {
            text = "Read target float (root)"
            setOnClickListener { button ->
                val requestedAddress = address.text.toString().trim()
                button.isEnabled = false
                status.text = "Requesting root access and reading $requestedAddress…"
                Thread {
                    val message = try {
                        val value = RootProcessMemoryReader().readFloat32(requestedAddress)
                        "${RootProcessMemoryReader.TARGET_PACKAGE} @ $requestedAddress: $value"
                    } catch (e: Exception) {
                        e.message ?: "Memory read failed"
                    }
                    runOnUiThread {
                        status.text = message
                        button.isEnabled = true
                    }
                }.start()
            }
        }
        val probeOffsets = Button(this).apply {
            text = "Probe dump offsets on target"
            setOnClickListener { button ->
                button.isEnabled = false
                status.text = "Tracing the dump offset chain in ${RootProcessMemoryReader.TARGET_PACKAGE}…"
                Thread {
                    val message = RootProcessMemoryReader().probeOffsets()
                    runOnUiThread {
                        status.text = message
                        button.isEnabled = true
                    }
                }.start()
            }
        }
        val start = Button(this).apply {
            text = "Start sample overlay"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
                }
            }
        }
        val stop = Button(this).apply {
            text = "Stop overlay"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        }

        layout.addView(status)
        layout.addView(rootCheck)
        layout.addView(address, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(readMemory)
        layout.addView(probeOffsets)
        layout.addView(start)
        layout.addView(stop)
        setContentView(layout)
    }
}
