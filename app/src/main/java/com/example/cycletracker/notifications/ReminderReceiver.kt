package com.example.cycletracker.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.cycletracker.MainActivity
import com.example.cycletracker.R
import com.example.cycletracker.data.CycleRepository
import com.example.cycletracker.domain.CycleCalculator

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val repo = CycleRepository(context)
        val settings = repo.getSettings()
        val starts = repo.getPeriodStarts()
        val model = CycleCalculator.computeModel(starts)

        if (!model.hasData || starts.isEmpty()) return

        when (action) {
            CycleReminderScheduler.ACTION_PERIOD_REMINDER -> {
                if (settings.periodReminderEnabled) {
                    val lead = intent.getIntExtra(
                        CycleReminderScheduler.EXTRA_LEAD_DAYS,
                        settings.periodReminderDaysBefore
                    ).coerceIn(1, 5)
                    val unit = if (lead == 1) "day" else "days"
                    postNotification(
                        context = context,
                        notificationId = NOTIF_ID_PERIOD,
                        title = "Period approaching",
                        body = "Your next period is expected in $lead $unit."
                    )
                }
            }
            CycleReminderScheduler.ACTION_FERTILE_REMINDER -> {
                if (settings.fertileReminderEnabled) {
                    postNotification(
                        context = context,
                        notificationId = NOTIF_ID_FERTILE,
                        title = "Fertile window",
                        body = "Your fertile window begins today."
                    )
                }
            }
            else -> return
        }

        // Schedule the next cycle's reminders (excluding today to avoid double-delivery)
        CycleReminderScheduler.reschedule(
            context = context,
            startsOverride = starts,
            settingsOverride = settings,
            allowToday = false
        )
    }

    private fun postNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_heart)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cycle Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Optional local reminders before expected periods and fertile windows"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "maya_cycle_reminders"
        private const val NOTIF_ID_PERIOD = 3001
        private const val NOTIF_ID_FERTILE = 3002
    }
}
