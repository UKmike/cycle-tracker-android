package com.example.cycletracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.cycletracker.data.CycleRepository
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.domain.CycleModel
import com.example.cycletracker.domain.DayClassification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class CycleUiState(
    val periodStarts: List<LocalDate> = emptyList(),
    val model: CycleModel = CycleCalculator.computeModel(emptyList()),
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val todayClassification: DayClassification = CycleCalculator.classifyDate(LocalDate.now(), emptyList())
)

class CycleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CycleRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<CycleUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): CycleUiState {
        val starts = repository.getPeriodStarts()
        val model = CycleCalculator.computeModel(starts)
        val today = LocalDate.now()
        return CycleUiState(
            periodStarts = starts,
            model = model,
            currentMonth = YearMonth.from(today),
            selectedDate = today,
            todayClassification = CycleCalculator.classifyDate(today, starts, model)
        )
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun previousMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
    }

    fun nextMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
    }

    fun goToToday() {
        val today = LocalDate.now()
        _uiState.update {
            it.copy(
                currentMonth = YearMonth.from(today),
                selectedDate = today
            )
        }
    }

    fun togglePeriodStart(date: LocalDate) {
        val updated = repository.togglePeriodStart(date)
        refreshWithStarts(updated)
    }

    fun removePeriodStart(date: LocalDate) {
        val updated = repository.removePeriodStart(date)
        refreshWithStarts(updated)
    }

    fun clearAll() {
        val updated = repository.clearAll()
        refreshWithStarts(updated)
    }

    fun loadSampleHistory() {
        val today = LocalDate.now()
        repository.clearAll()
        repository.addPeriodStart(today.minusDays(72))
        repository.addPeriodStart(today.minusDays(43))
        val updated = repository.addPeriodStart(today.minusDays(15))
        refreshWithStarts(updated)
    }

    private fun refreshWithStarts(starts: List<LocalDate>) {
        val model = CycleCalculator.computeModel(starts)
        val today = LocalDate.now()
        _uiState.update {
            it.copy(
                periodStarts = starts,
                model = model,
                todayClassification = CycleCalculator.classifyDate(today, starts, model)
            )
        }
    }
}
