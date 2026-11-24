package com.example.cycletracker.data

import android.content.Context
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.notifications.CycleReminderScheduler
import com.example.cycletracker.widget.CycleWidgetProvider
import java.time.LocalDate

data class CycleSettings(
    val periodLength: Int = CycleCalculator.DEFAULT_PERIOD_LENGTH,
    val periodReminderEnabled: Boolean = false,
    val periodReminderDaysBefore: Int = 2,
    val fertileReminderEnabled: Boolean = false,
    val appLockEnabled: Boolean = false
)

class CycleRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPeriodStarts(): List<LocalDate> = readRawPeriodStarts().sorted()

    fun getSettings(): CycleSettings = CycleSettings(
        periodLength = prefs.getInt(KEY_PERIOD_LENGTH, CycleCalculator.DEFAULT_PERIOD_LENGTH)
            .coerceIn(CycleCalculator.MIN_PERIOD_LENGTH, CycleCalculator.MAX_PERIOD_LENGTH),
        periodReminderEnabled = prefs.getBoolean(KEY_PERIOD_REMINDER_ENABLED, false),
        periodReminderDaysBefore = prefs.getInt(KEY_PERIOD_REMINDER_DAYS_BEFORE, 2).coerceIn(1, 5),
        fertileReminderEnabled = prefs.getBoolean(KEY_FERTILE_REMINDER_ENABLED, false),
        appLockEnabled = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    )

    fun togglePeriodStart(date: LocalDate): List<LocalDate> {
        val current = readRawPeriodStarts()
        if (!current.remove(date)) {
            current.add(date)
        }
        return savePeriodStartsAndNotify(current)
    }

    fun addPeriodStart(date: LocalDate): List<LocalDate> {
        val current = readRawPeriodStarts()
        current.add(date)
        return savePeriodStartsAndNotify(current)
    }

    fun removePeriodStart(date: LocalDate): List<LocalDate> {
        val current = readRawPeriodStarts()
        current.remove(date)
        return savePeriodStartsAndNotify(current)
    }

    fun replaceAll(dates: Collection<LocalDate>): List<LocalDate> {
        return savePeriodStartsAndNotify(dates.toSet())
    }

    fun restoreBackup(backup: CycleBackup): Pair<List<LocalDate>, CycleSettings> {
        val currentSettings = getSettings()
        val updatedSettings = currentSettings.copy(
            periodLength = backup.periodLength.coerceIn(
                CycleCalculator.MIN_PERIOD_LENGTH,
                CycleCalculator.MAX_PERIOD_LENGTH
            )
        )
        val sorted = backup.periodStarts.distinct().sorted()
        prefs.edit()
            .putStringSet(KEY_PERIOD_STARTS, sorted.mapTo(HashSet(sorted.size)) { it.toString() })
            .putInt(KEY_PERIOD_LENGTH, updatedSettings.periodLength)
            .apply()
        CycleWidgetProvider.updateAllWidgets(appContext)
        CycleReminderScheduler.reschedule(appContext, sorted, updatedSettings)
        return sorted to updatedSettings
    }

    fun updateSettings(transform: (CycleSettings) -> CycleSettings): CycleSettings {
        val old = getSettings()
        val updated = transform(old).let {
            it.copy(
                periodLength = it.periodLength.coerceIn(
                    CycleCalculator.MIN_PERIOD_LENGTH,
                    CycleCalculator.MAX_PERIOD_LENGTH
                ),
                periodReminderDaysBefore = it.periodReminderDaysBefore.coerceIn(1, 5)
            )
        }
        prefs.edit()
            .putInt(KEY_PERIOD_LENGTH, updated.periodLength)
            .putBoolean(KEY_PERIOD_REMINDER_ENABLED, updated.periodReminderEnabled)
            .putInt(KEY_PERIOD_REMINDER_DAYS_BEFORE, updated.periodReminderDaysBefore)
            .putBoolean(KEY_FERTILE_REMINDER_ENABLED, updated.fertileReminderEnabled)
            .putBoolean(KEY_APP_LOCK_ENABLED, updated.appLockEnabled)
            .apply()

        if (old.periodLength != updated.periodLength) {
            CycleWidgetProvider.updateAllWidgets(appContext)
        }
        if (old.periodReminderEnabled != updated.periodReminderEnabled ||
            old.periodReminderDaysBefore != updated.periodReminderDaysBefore ||
            old.fertileReminderEnabled != updated.fertileReminderEnabled ||
            old.periodLength != updated.periodLength
        ) {
            CycleReminderScheduler.reschedule(appContext, settingsOverride = updated)
        }
        return updated
    }

    fun getLastWidgetRenderSlot(): Long = prefs.getLong(KEY_LAST_WIDGET_RENDER_SLOT, Long.MIN_VALUE)

    fun setLastWidgetRenderSlot(slot: Long) {
        prefs.edit().putLong(KEY_LAST_WIDGET_RENDER_SLOT, slot).apply()
    }

    private fun readRawPeriodStarts(): MutableSet<LocalDate> {
        val raw = prefs.getStringSet(KEY_PERIOD_STARTS, null) ?: return HashSet()
        val result = HashSet<LocalDate>(raw.size)
        for (entry in raw) {
            runCatching { LocalDate.parse(entry) }.getOrNull()?.let { result.add(it) }
        }
        return result
    }

    private fun savePeriodStartsAndNotify(dates: Set<LocalDate>): List<LocalDate> {
        val sorted = dates.sorted()
        prefs.edit()
            .putStringSet(KEY_PERIOD_STARTS, dates.mapTo(HashSet(dates.size)) { it.toString() })
            .apply()
        CycleWidgetProvider.updateAllWidgets(appContext)
        CycleReminderScheduler.reschedule(appContext, startsOverride = sorted)
        return sorted
    }

    companion object {
        private const val PREFS_NAME = "cycle_tracker_prefs"
        private const val KEY_PERIOD_STARTS = "period_starts"
        private const val KEY_PERIOD_LENGTH = "period_length"
        private const val KEY_PERIOD_REMINDER_ENABLED = "period_reminder_enabled"
        private const val KEY_PERIOD_REMINDER_DAYS_BEFORE = "period_reminder_days_before"
        private const val KEY_FERTILE_REMINDER_ENABLED = "fertile_reminder_enabled"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_LAST_WIDGET_RENDER_SLOT = "last_widget_render_slot"
    }
}
