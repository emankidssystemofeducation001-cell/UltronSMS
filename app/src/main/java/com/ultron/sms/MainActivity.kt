package com.ultron.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.SEND_SMS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart  = findViewById<Button>(R.id.btnStart)
        val tvStatus  = findViewById<TextView>(R.id.tvStatus)

        btnStart.setOnClickListener {
            val missing = PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
            } else {
                startSmsService()
                tvStatus.text = "✅ Service Running\nPolling every 30 seconds"
                btnStart.text = "✅ RUNNING"
                btnStart.isEnabled = false
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (results.all { it == PackageManager.PERMISSION_GRANTED }) {
            startSmsService()
            findViewById<TextView>(R.id.tvStatus).text = "✅ Service Running"
            val btn = findViewById<Button>(R.id.btnStart)
            btn.text = "✅ RUNNING"
            btn.isEnabled = false
        }
    }

    private fun startSmsService() {
        val intent = Intent(this, SmsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
