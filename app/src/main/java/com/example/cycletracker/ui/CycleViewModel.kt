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

data class CycleUiState(
    val today: LocalDate,
    val periodStarts: List<LocalDate>,
    val settings: CycleSettings,
    val model: CycleModel,
    val selectedDate: LocalDate,
    val todayClassification: DayClassification
)

class CycleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CycleRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<CycleUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): CycleUiState {
        val starts = repository.getPeriodStarts()
        val settings = repository.getSettings()
        val model = CycleCalculator.computeModel(starts)
        val today = LocalDate.now()
        return CycleUiState(
            today = today,
            periodStarts = starts,
            settings = settings,
            model = model,
            selectedDate = today,
            todayClassification = CycleCalculator.classifyDate(
                date = today,
                periodStarts = starts,
                model = model,
                periodLength = settings.periodLength
            )
        )
    }

    /**
     * Refreshes [CycleUiState.today] and [CycleUiState.todayClassification] when the user
     * resumes the app on a new calendar day after leaving it in Recents overnight.
     */
    fun refreshTodayIfNeeded() {
        val now = LocalDate.now()
        _uiState.update { state ->
            if (state.today == now) return@update state
            val nextSelected = if (state.selectedDate == state.today) now else state.selectedDate
            state.copy(
                today = now,
                selectedDate = nextSelected,
                todayClassification = CycleCalculator.classifyDate(
                    date = now,
                    periodStarts = state.periodStarts,
                    model = state.model,
                    periodLength = state.settings.periodLength
                )
            )
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
                state.copy(
                    today = now,
                    selectedDate = now,
                    todayClassification = CycleCalculator.classifyDate(
                        date = now,
                        periodStarts = state.periodStarts,
                        model = state.model,
                        periodLength = state.settings.periodLength
                    )
                )
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
                state.copy(
                    today = today,
                    periodStarts = starts,
                    settings = settings,
                    model = model,
                    todayClassification = CycleCalculator.classifyDate(
                        date = today,
                        periodStarts = starts,
                        model = model,
                        periodLength = settings.periodLength
                    )
                )
            }
            starts.size
        }
    }

    private fun refreshWithStarts(starts: List<LocalDate>) {
        val model = CycleCalculator.computeModel(starts)
        val today = LocalDate.now()
        _uiState.update { state ->
            state.copy(
                today = today,
                periodStarts = starts,
                model = model,
                todayClassification = CycleCalculator.classifyDate(
                    date = today,
                    periodStarts = starts,
                    model = model,
                    periodLength = state.settings.periodLength
                )
            )
        }
    }

    private fun refreshWithSettings(settings: CycleSettings) {
        val today = LocalDate.now()
        _uiState.update { state ->
            state.copy(
                today = today,
                settings = settings,
                todayClassification = CycleCalculator.classifyDate(
                    date = today,
                    periodStarts = state.periodStarts,
                    model = state.model,
                    periodLength = settings.periodLength
                )
            )
        }
    }
}
