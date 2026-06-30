package com.ultron.sms

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SmsService : Service() {

    private val SERVER_URL = "http://192.168.1.4:8000"
    private val CHANNEL_ID = "ultron_sms_channel"
    private val NOTIF_ID = 1

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sentCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Starting..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            updateNotification("Running — polls every 30s")
            while (true) {
                try {
                    pollAndSend()
                } catch (e: Exception) {
                    updateNotification("Error: ${e.message?.take(50)}")
                }
                delay(30_000)
            }
        }
        return START_STICKY
    }

    private fun pollAndSend() {
        val response = client.newCall(
            Request.Builder().url("$SERVER_URL/sms-next").get().build()
        ).execute()

        val body = response.body?.string()?.trim() ?: return
        if (body == "{}" || body.isEmpty()) return

        val json = JSONObject(body)
        val phone   = json.optString("phone", "")
        val message = json.optString("message", "")
        val id      = json.optLong("id", 0)

        if (phone.isEmpty()) return

        sendSms(phone, message)
        markSent(id)
        sentCount++
        updateNotification("Sent $sentCount SMS | Last: $phone")
    }

    private fun sendSms(phone: String, message: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = smsManager.divideMessage(message)
        if (parts.size == 1) {
            smsManager.sendTextMessage(phone, null, message, null, null)
        } else {
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
        }
    }

    private fun markSent(id: Long) {
        try {
            client.newCall(
                Request.Builder()
                    .url("$SERVER_URL/sms-queue/sent/$id")
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().close()
        } catch (_: Exception) {}
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Ultron SMS")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pi)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Ultron SMS")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pi)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Ultron SMS Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background SMS sender" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
