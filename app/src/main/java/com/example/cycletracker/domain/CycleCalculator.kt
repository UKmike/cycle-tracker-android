package com.example.cycletracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FertilityStatus {
    FERTILE,
    COULD_BE_FERTILE,
    NOT_FERTILE
}

enum class PeriodStatus {
    LOGGED_START,
    LOGGED,
    PREDICTED_START,
    PREDICTED
}

data class CycleModel(
    val hasData: Boolean,
    val averageCycleLength: Int,
    val minCycleLength: Int,
    val maxCycleLength: Int,
    val variabilityDays: Int,
    val cyclesUsed: Int,
    val ovulationDay: Int
)

data class DayClassification(
    val date: LocalDate,
    val fertility: FertilityStatus,
    val periodStatus: PeriodStatus?,
    val cycleDay: Int?,
    val daysToNextPeriod: Int?,
    val nextPeriodDate: LocalDate?
)

object CycleCalculator {

    const val DEFAULT_CYCLE_LENGTH = 28
    const val DEFAULT_PERIOD_LENGTH = 5

    fun computeModel(periodStarts: List<LocalDate>): CycleModel {
        val sorted = periodStarts.distinct().sorted()
        if (sorted.isEmpty()) {
            return CycleModel(
                hasData = false,
                averageCycleLength = DEFAULT_CYCLE_LENGTH,
                minCycleLength = DEFAULT_CYCLE_LENGTH,
                maxCycleLength = DEFAULT_CYCLE_LENGTH,
                variabilityDays = 0,
                cyclesUsed = 0,
                ovulationDay = 14
            )
        }

        val intervals = mutableListOf<Int>()
        for (i in 1 until sorted.size) {
            val gap = ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]).toInt()
            if (gap in 15..60) {
                intervals.add(gap)
            }
        }

        if (intervals.isEmpty()) {
            return CycleModel(
                hasData = true,
                averageCycleLength = DEFAULT_CYCLE_LENGTH,
                minCycleLength = DEFAULT_CYCLE_LENGTH,
                maxCycleLength = DEFAULT_CYCLE_LENGTH,
                variabilityDays = 0,
                cyclesUsed = 0,
                ovulationDay = 14
            )
        }

        // Weighted moving average of up to the last 6 cycles
        val recent = intervals.takeLast(6)
        var weightedSum = 0.0
        var weightTotal = 0.0
        recent.forEachIndexed { index, len ->
            val weight = (index + 1).toDouble()
            weightedSum += len * weight
            weightTotal += weight
        }

        val avg = (weightedSum / weightTotal).roundToInt()
        val minLen = recent.min()
        val maxLen = recent.max()
        val variability = ((maxLen - minLen) / 2.0).roundToInt()
        val ovulation = max(10, avg - 14)

        return CycleModel(
            hasData = true,
            averageCycleLength = avg,
            minCycleLength = minLen,
            maxCycleLength = maxLen,
            variabilityDays = variability,
            cyclesUsed = recent.size,
            ovulationDay = ovulation
        )
    }

    fun classifyDate(
        date: LocalDate,
        periodStarts: List<LocalDate>,
        model: CycleModel = computeModel(periodStarts),
        periodLength: Int = DEFAULT_PERIOD_LENGTH
    ): DayClassification {
        val sorted = periodStarts.distinct().sorted()
        if (sorted.isEmpty()) {
            return DayClassification(
                date = date,
                fertility = FertilityStatus.NOT_FERTILE,
                periodStatus = null,
                cycleDay = null,
                daysToNextPeriod = null,
                nextPeriodDate = null
            )
        }

        val firstStart = sorted.first()
        val lastStart = sorted.last()
        val avg = model.averageCycleLength

        // 1. Check if date falls within a logged period
        var periodStatus: PeriodStatus? = null
        for (start in sorted) {
            val diff = ChronoUnit.DAYS.between(start, date).toInt()
            if (diff == 0) {
                periodStatus = PeriodStatus.LOGGED_START
                break
            }
            if (diff in 1 until periodLength) {
                periodStatus = PeriodStatus.LOGGED
                break
            }
        }

        // 2. Anchor the cycle for this date
        var cycleAnchor = lastStart
        var cycleLengthForAnchor = avg

        if (date.isBefore(firstStart)) {
            val gap = ChronoUnit.DAYS.between(date, firstStart).toInt()
            val cyclesBack = (gap + avg - 1) / avg
            cycleAnchor = firstStart.minusDays((cyclesBack * avg).toLong())
        } else if (date.isBefore(lastStart)) {
            for (i in 0 until sorted.size - 1) {
                if (!date.isBefore(sorted[i]) && date.isBefore(sorted[i + 1])) {
                    cycleAnchor = sorted[i]
                    val actualLen = ChronoUnit.DAYS.between(sorted[i], sorted[i + 1]).toInt()
                    cycleLengthForAnchor = if (actualLen in 15..60) actualLen else avg
                    break
                }
            }
        } else {
            val daysSinceLast = ChronoUnit.DAYS.between(lastStart, date).toInt()
            val cycleIndex = daysSinceLast / avg
            cycleAnchor = lastStart.plusDays((cycleIndex * avg).toLong())
            cycleLengthForAnchor = avg
        }

        val cycleDay = ChronoUnit.DAYS.between(cycleAnchor, date).toInt() + 1
        val nextPeriodDate = cycleAnchor.plusDays(cycleLengthForAnchor.toLong())
        val daysToNextPeriod = ChronoUnit.DAYS.between(date, nextPeriodDate).toInt()

        // Predicted period if after the current cycle's expected start
        if (periodStatus == null && !date.isBefore(lastStart.plusDays(avg.toLong()))) {
            if (cycleDay == 1) {
                periodStatus = PeriodStatus.PREDICTED_START
            } else if (cycleDay in 2..periodLength) {
                periodStatus = PeriodStatus.PREDICTED
            }
        }

        // 3. Fertility classification
        val ovulationDay = max(10, cycleLengthForAnchor - 14)
        val fertileStart = ovulationDay - 2
        val fertileEnd = ovulationDay + 1

        val extraBuffer = min(3, model.variabilityDays)
        val couldStart = max(periodLength + 1, ovulationDay - 5 - extraBuffer)
        val couldEnd = min(cycleLengthForAnchor - 1, fertileEnd + 3 + extraBuffer)

        val fertility = when {
            cycleDay in fertileStart..fertileEnd -> FertilityStatus.FERTILE
            cycleDay in couldStart..couldEnd -> FertilityStatus.COULD_BE_FERTILE
            else -> FertilityStatus.NOT_FERTILE
        }

        return DayClassification(
            date = date,
            fertility = fertility,
            periodStatus = periodStatus,
            cycleDay = cycleDay,
            daysToNextPeriod = daysToNextPeriod,
            nextPeriodDate = nextPeriodDate
        )
    }
}
