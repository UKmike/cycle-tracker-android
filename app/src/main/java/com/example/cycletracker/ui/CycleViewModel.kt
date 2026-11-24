package com.example.cycletracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.cycletracker.data.CycleBackupCodec
import com.example.cycletracker.data.CycleRepository
import com.example.cycletracker.data.CycleSettings
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.domain.CycleModel
import com.example.cycletracker.domain.DayClassification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime

data class CycleUiState(
    val today: LocalDate,
    val periodStarts: List<LocalDate>,
    val settings: CycleSettings,
    val model: CycleModel,
    val selectedDate: LocalDate,
    val todayClassification: DayClassification,
    val midStageTransition: CyclePalette.MidStageTransition?,
    val needsMiddayRefreshToday: Boolean,
    val isAfterNoon: Boolean
)

class CycleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CycleRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<CycleUiState> = _uiState.asStateFlow()

    private fun buildState(
        starts: List<LocalDate>,
        settings: CycleSettings,
        model: CycleModel,
        today: LocalDate,
        selectedDate: LocalDate,
        nowTime: LocalTime = LocalTime.now()
    ): CycleUiState {
        val threeDays = CycleCalculator.classifyRange(
            startDate = today.minusDays(1),
            dayCount = 3,
            periodStarts = starts,
            model = model,
            periodLength = settings.periodLength
        )
        val yesterdayInfo = threeDays[0]
        val todayInfo = threeDays[1]
        val tomorrowInfo = threeDays[2]
        val transition = CyclePalette.resolveMidStageTransition(
            yesterdayInfo = yesterdayInfo,
            todayInfo = todayInfo,
            tomorrowInfo = tomorrowInfo,
            hasData = model.hasData,
            nowTime = nowTime
        )
        val needsMidday = CyclePalette.needsMiddayRefresh(
            yesterdayInfo = yesterdayInfo,
            todayInfo = todayInfo,
            tomorrowInfo = tomorrowInfo,
            hasData = model.hasData
        )
        return CycleUiState(
            today = today,
            periodStarts = starts,
            settings = settings,
            model = model,
            selectedDate = selectedDate,
            todayClassification = todayInfo,
            midStageTransition = transition,
            needsMiddayRefreshToday = needsMidday,
            isAfterNoon = !nowTime.isBefore(LocalTime.NOON)
        )
    }

    private fun loadInitialState(): CycleUiState {
        val starts = repository.getPeriodStarts()
        val settings = repository.getSettings()
        val model = CycleCalculator.computeModel(starts)
        val today = LocalDate.now()
        return buildState(starts, settings, model, today, selectedDate = today)
    }

    /**
     * Refreshes [CycleUiState.today] and midday-to-midday transition state when the user resumes
     * the app or crosses midnight / midday (on stage-transition days).
     */
    fun refreshTimeStateIfNeeded() {
        val nowDate = LocalDate.now()
        val nowTime = LocalTime.now()
        val afterNoon = !nowTime.isBefore(LocalTime.NOON)
        _uiState.update { state ->
            val dateChanged = state.today != nowDate
            val halfDayChanged = state.isAfterNoon != afterNoon
            if (!dateChanged && (!halfDayChanged || !state.needsMiddayRefreshToday)) {
                return@update state
            }
            val nextSelected = if (dateChanged && state.selectedDate == state.today) {
                nowDate
            } else {
                state.selectedDate
            }
            buildState(state.periodStarts, state.settings, state.model, nowDate, nextSelected, nowTime)
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { state ->
            if (state.selectedDate == date) state else state.copy(selectedDate = date)
        }
    }

    fun goToToday() {
        val now = LocalDate.now()
        _uiState.update { state ->
            if (state.today == now) {
                state.copy(selectedDate = now)
            } else {
                buildState(state.periodStarts, state.settings, state.model, now, selectedDate = now)
            }
        }
    }

    fun togglePeriodStart(date: LocalDate) {
        val updated = repository.togglePeriodStart(date)
        refreshWithStarts(updated)
    }

    fun addPeriodStart(date: LocalDate) {
        val updated = repository.addPeriodStart(date)
        refreshWithStarts(updated)
    }

    fun removePeriodStart(date: LocalDate) {
        val updated = repository.removePeriodStart(date)
        refreshWithStarts(updated)
    }

    fun loadSampleHistory() {
        val today = LocalDate.now()
        val updated = repository.replaceAll(
            listOf(
                today.minusDays(72),
                today.minusDays(43),
                today.minusDays(15)
            )
        )
        refreshWithStarts(updated)
    }

    fun updatePeriodLength(days: Int) {
        val updated = repository.updateSettings { it.copy(periodLength = days) }
        refreshWithSettings(updated)
    }

    fun setPeriodReminder(enabled: Boolean, daysBefore: Int = _uiState.value.settings.periodReminderDaysBefore) {
        val updated = repository.updateSettings {
            it.copy(
                periodReminderEnabled = enabled,
                periodReminderDaysBefore = daysBefore
            )
        }
        refreshWithSettings(updated)
    }

    fun setFertileReminder(enabled: Boolean) {
        val updated = repository.updateSettings { it.copy(fertileReminderEnabled = enabled) }
        refreshWithSettings(updated)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        val updated = repository.updateSettings { it.copy(appLockEnabled = enabled) }
        refreshWithSettings(updated)
    }

    fun exportBackupJson(): String {
        val state = _uiState.value
        return CycleBackupCodec.encode(
            periodStarts = state.periodStarts,
            periodLength = state.settings.periodLength,
            exportedAt = LocalDate.now()
        )
    }

    fun importBackupJson(json: String): Result<Int> {
        return CycleBackupCodec.decode(json).map { backup ->
            val (starts, settings) = repository.restoreBackup(backup)
            val model = CycleCalculator.computeModel(starts)
            val today = LocalDate.now()
            _uiState.update { state ->
                buildState(starts, settings, model, today, state.selectedDate)
            }
            starts.size
        }
    }

    private fun refreshWithStarts(starts: List<LocalDate>) {
        val model = CycleCalculator.computeModel(starts)
        val today = LocalDate.now()
        _uiState.update { state ->
            buildState(starts, state.settings, model, today, state.selectedDate)
        }
    }

    private fun refreshWithSettings(settings: CycleSettings) {
        val today = LocalDate.now()
        _uiState.update { state ->
            buildState(state.periodStarts, settings, state.model, today, state.selectedDate)
        }
    }
}
