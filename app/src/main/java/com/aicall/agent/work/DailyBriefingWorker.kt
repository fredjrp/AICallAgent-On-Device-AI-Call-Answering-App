package com.aicall.agent.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aicall.agent.AICallApplication
import com.aicall.agent.data.CallHistoryRepository
import com.aicall.agent.ui.MainActivity
import com.aicall.agent.util.Logger
import com.aicall.agent.util.PreferencesManager
import java.util.concurrent.TimeUnit

/**
 * Scheduled daily briefing generator running via WorkManager.
 * Computes call summary, bookings, and failure states strictly from local on-device data.
 */
class DailyBriefingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "DailyBriefingWorker"

    override suspend fun doWork(): Result {
        Logger.i(tag, "Executing scheduled daily briefing compilation...")
        val historyRepo = CallHistoryRepository.getInstance(applicationContext)
        val stats = historyRepo.getTodayStats()

        val totalCalls = stats.first
        val avgDuration = stats.second
        val missedCalls = stats.third

        postBriefingNotification(totalCalls, avgDuration, missedCalls)
        return Result.success()
    }

    private fun postBriefingNotification(totalCalls: Int, avgDuration: String, missedCalls: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_BRIEFING,
                "Daily Call Briefings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily evening summaries of voice calls handled by Front Desk"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, flags)

        val summaryText = if (totalCalls == 0) {
            "No calls received today. Front Desk is standing by."
        } else {
            "$totalCalls calls handled today (Avg: $avgDuration) • $missedCalls missed."
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_BRIEFING)
            .setContentTitle("Daily Front Desk Briefing")
            .setContentText(summaryText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BRIEFING, notification)
    }

    companion object {
        const val CHANNEL_ID_BRIEFING = "aicall_briefing_channel"
        const val NOTIFICATION_ID_BRIEFING = 2001
        private const val WORK_NAME = "daily_frontdesk_briefing_work"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<DailyBriefingWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Logger.i("DailyBriefingWorker", "Daily briefing worker scheduled with WorkManager.")
        }
    }
}
