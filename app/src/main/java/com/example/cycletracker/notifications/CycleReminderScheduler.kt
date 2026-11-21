package com.example.cycletracker.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.cycletracker.data.CycleRepository
import com.example.cycletracker.data.CycleSettings
import com.example.cycletracker.domain.CycleCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules optional, privacy-first local notifications using battery-friendly
 * non-wakeup [AlarmManager.RTC] alarms at 9:00 AM local time.
 */
object CycleReminderScheduler {

    const val ACTION_PERIOD_REMINDER = "com.example.cycletracker.action.PERIOD_REMINDER"
    const val ACTION_FERTILE_REMINDER = "com.example.cycletracker.action.FERTILE_REMINDER"
    const val EXTRA_LEAD_DAYS = "lead_days"

    private const val REQ_PERIOD_REMINDER = 2001
    private const val REQ_FERTILE_REMINDER = 2002
    private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    fun reschedule(
        context: Context,
        startsOverride: List<LocalDate>? = null,
        settingsOverride: CycleSettings? = null,
        allowToday: Boolean = true
    ) {
        val repo = CycleRepository(context)
        val settings = settingsOverride ?: repo.getSettings()
        if (!settings.periodReminderEnabled && !settings.fertileReminderEnabled) {
            cancelAlarm(context, ACTION_PERIOD_REMINDER, REQ_PERIOD_REMINDER)
            cancelAlarm(context, ACTION_FERTILE_REMINDER, REQ_FERTILE_REMINDER)
            return
        }

        val starts = startsOverride ?: repo.getPeriodStarts()
        val model = CycleCalculator.computeModel(starts)
        if (!model.hasData || starts.isEmpty()) {
            cancelAlarm(context, ACTION_PERIOD_REMINDER, REQ_PERIOD_REMINDER)
            cancelAlarm(context, ACTION_FERTILE_REMINDER, REQ_FERTILE_REMINDER)
            return
        }

        val zone = ZoneId.systemDefault()
        val nowTime = LocalTime.now(zone)
        val today = LocalDate.now(zone)
        val earliestDate = if (allowToday && nowTime.isBefore(REMINDER_TIME)) {
            today
        } else {
            today.plusDays(1)
        }

        val dates = CycleCalculator.computeNextReminderDates(
            earliestDate = earliestDate,
            periodStarts = starts,
            model = model,
            periodLeadDays = settings.periodReminderDaysBefore
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (settings.periodReminderEnabled && dates.periodReminderDate != null) {
            val triggerMillis = dates.periodReminderDate
                .atTime(REMINDER_TIME)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_PERIOD_REMINDER
                putExtra(EXTRA_LEAD_DAYS, settings.periodReminderDaysBefore)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                REQ_PERIOD_REMINDER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.set(AlarmManager.RTC, triggerMillis, pi)
        } else {
            cancelAlarm(context, ACTION_PERIOD_REMINDER, REQ_PERIOD_REMINDER)
        }

        if (settings.fertileReminderEnabled && dates.fertileReminderDate != null) {
            val triggerMillis = dates.fertileReminderDate
                .atTime(REMINDER_TIME)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_FERTILE_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context,
                REQ_FERTILE_REMINDER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.set(AlarmManager.RTC, triggerMillis, pi)
        } else {
            cancelAlarm(context, ACTION_FERTILE_REMINDER, REQ_FERTILE_REMINDER)
        }
    }

    private fun cancelAlarm(context: Context, action: String, requestCode: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
        }
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(existing)
        existing.cancel()
    }
}
