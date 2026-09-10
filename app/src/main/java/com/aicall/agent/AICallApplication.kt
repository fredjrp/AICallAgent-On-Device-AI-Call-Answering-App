package com.aicall.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aicall.agent.util.Logger

class AICallApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.i("AICallApplication", "AICallAgent application initialized")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_INCALL,
                "In-Call AI Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification for live call audio processing"
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_INCALL = "aicall_incall_channel"
        const val NOTIFICATION_ID_INCALL = 1001
    }
}
