package com.example.cycletracker.data

import android.content.Context
import com.example.cycletracker.widget.CycleWidgetProvider
import java.time.LocalDate

class CycleRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPeriodStarts(): List<LocalDate> {
        val raw = prefs.getStringSet(KEY_PERIOD_STARTS, null) ?: return emptyList()
        return raw.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
    }

    fun togglePeriodStart(date: LocalDate): List<LocalDate> {
        val current = getPeriodStarts().toMutableSet()
        if (current.contains(date)) {
            current.remove(date)
        } else {
            current.add(date)
        }
        saveAndNotify(current)
        return current.sorted()
    }

    fun addPeriodStart(date: LocalDate): List<LocalDate> {
        val current = getPeriodStarts().toMutableSet()
        current.add(date)
        saveAndNotify(current)
        return current.sorted()
    }

    fun removePeriodStart(date: LocalDate): List<LocalDate> {
        val current = getPeriodStarts().toMutableSet()
        current.remove(date)
        saveAndNotify(current)
        return current.sorted()
    }

    fun clearAll(): List<LocalDate> {
        saveAndNotify(emptySet())
        return emptyList()
    }

    private fun saveAndNotify(dates: Set<LocalDate>) {
        prefs.edit()
            .putStringSet(KEY_PERIOD_STARTS, dates.map { it.toString() }.toSet())
            .apply()
        CycleWidgetProvider.updateAllWidgets(context)
    }

    companion object {
        private const val PREFS_NAME = "cycle_tracker_prefs"
        private const val KEY_PERIOD_STARTS = "period_starts"
    }
}
