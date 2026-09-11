package com.aicall.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aicall.agent.shizuku.ShizukuAudioAccess
import com.aicall.agent.util.Logger
import com.aicall.agent.work.DailyBriefingWorker

class AICallApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.i("AICallApplication", "AICallAgent application initialized")
        createNotificationChannels()
        ShizukuAudioAccess.init()
        DailyBriefingWorker.schedule(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        ShizukuAudioAccess.destroy()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val inCallChannel = NotificationChannel(
                CHANNEL_ID_INCALL,
                "In-Call Phone Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming calls and live telephone answering"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(inCallChannel)

            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Service Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when Shizuku service or critical permissions are disconnected"
            }
            notificationManager?.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_INCALL = "aicall_incall_channel"
        const val CHANNEL_ID_ALERTS = "aicall_alerts_channel"
        const val NOTIFICATION_ID_INCALL = 1001
        const val NOTIFICATION_ID_SHIZUKU_ALERT = 1002
    }
}
