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
    val nextPeriodDate: LocalDate?,
    val daysLate: Int = 0,
    val expectedPeriodDate: LocalDate? = null
) {
    val isLoggedPeriod: Boolean
        get() = periodStatus == PeriodStatus.LOGGED_START || periodStatus == PeriodStatus.LOGGED

    val isPredictedPeriod: Boolean
        get() = periodStatus == PeriodStatus.PREDICTED_START || periodStatus == PeriodStatus.PREDICTED

    val isAnyPeriod: Boolean
        get() = periodStatus != null
}

data class ReminderScheduleDates(
    val periodReminderDate: LocalDate?,
    val periodTargetDate: LocalDate?,
    val fertileReminderDate: LocalDate?
)

object CycleCalculator {

    const val DEFAULT_CYCLE_LENGTH = 28
    const val DEFAULT_PERIOD_LENGTH = 5
    const val MIN_PERIOD_LENGTH = 2
    const val MAX_PERIOD_LENGTH = 10

    fun computeModel(periodStarts: List<LocalDate>): CycleModel {
        val sorted = ensureSortedDistinct(periodStarts)
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

        val intervals = ArrayList<Int>(max(0, sorted.size - 1))
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
        val startIdx = max(0, intervals.size - 6)
        val recentCount = intervals.size - startIdx
        var weightedSum = 0.0
        var weightTotal = 0.0
        var minLen = Int.MAX_VALUE
        var maxLen = Int.MIN_VALUE
        for (offset in 0 until recentCount) {
            val len = intervals[startIdx + offset]
            val weight = (offset + 1).toDouble()
            weightedSum += len * weight
            weightTotal += weight
            if (len < minLen) minLen = len
            if (len > maxLen) maxLen = len
        }

        val avg = (weightedSum / weightTotal).roundToInt()
        val variability = ((maxLen - minLen) / 2.0).roundToInt()
        val ovulation = max(10, avg - 14)

        return CycleModel(
            hasData = true,
            averageCycleLength = avg,
            minCycleLength = minLen,
            maxCycleLength = maxLen,
            variabilityDays = variability,
            cyclesUsed = recentCount,
            ovulationDay = ovulation
        )
    }

    fun classifyDate(
        date: LocalDate,
        periodStarts: List<LocalDate>,
        model: CycleModel = computeModel(periodStarts),
        periodLength: Int = DEFAULT_PERIOD_LENGTH
    ): DayClassification {
        val sorted = ensureSortedDistinct(periodStarts)
        if (sorted.isEmpty()) {
            return emptyClassification(date)
        }
        val clampedPeriodLen = periodLength.coerceIn(MIN_PERIOD_LENGTH, MAX_PERIOD_LENGTH)
        return classifyDateSorted(
            date = date,
            sorted = sorted,
            firstStart = sorted.first(),
            lastStart = sorted.last(),
            expectedPeriodDate = sorted.last().plusDays(model.averageCycleLength.toLong()),
            model = model,
            periodLength = clampedPeriodLen
        )
    }

    /**
     * Batch-classifies [dayCount] consecutive dates starting at [startDate], normalizing
     * [periodStarts] and anchor invariants once for the entire range.
     */
    fun classifyRange(
        startDate: LocalDate,
        dayCount: Int,
        periodStarts: List<LocalDate>,
        model: CycleModel = computeModel(periodStarts),
        periodLength: Int = DEFAULT_PERIOD_LENGTH
    ): List<DayClassification> {
        if (dayCount <= 0) return emptyList()
        val sorted = ensureSortedDistinct(periodStarts)
        if (sorted.isEmpty()) {
            return List(dayCount) { offset -> emptyClassification(startDate.plusDays(offset.toLong())) }
        }
        val firstStart = sorted.first()
        val lastStart = sorted.last()
        val expectedPeriodDate = lastStart.plusDays(model.averageCycleLength.toLong())
        val clampedPeriodLen = periodLength.coerceIn(MIN_PERIOD_LENGTH, MAX_PERIOD_LENGTH)
        return List(dayCount) { offset ->
            classifyDateSorted(
                date = startDate.plusDays(offset.toLong()),
                sorted = sorted,
                firstStart = firstStart,
                lastStart = lastStart,
                expectedPeriodDate = expectedPeriodDate,
                model = model,
                periodLength = clampedPeriodLen
            )
        }
    }

    /**
     * Computes the next upcoming reminder dates on or after [earliestDate] for local
     * period-upcoming and fertile-window-start notifications.
     */
    fun computeNextReminderDates(
        earliestDate: LocalDate,
        periodStarts: List<LocalDate>,
        model: CycleModel = computeModel(periodStarts),
        periodLeadDays: Int = 2
    ): ReminderScheduleDates {
        val sorted = ensureSortedDistinct(periodStarts)
        if (!model.hasData || sorted.isEmpty()) {
            return ReminderScheduleDates(null, null, null)
        }
        val lastStart = sorted.last()
        val avg = model.averageCycleLength.coerceAtLeast(1)
        val lead = periodLeadDays.coerceIn(1, 5)

        val daysFromLast = ChronoUnit.DAYS.between(lastStart, earliestDate).toInt()
        val minPeriodSpan = daysFromLast + lead
        val periodCycleIndex = if (minPeriodSpan <= avg) 1 else (minPeriodSpan + avg - 1) / avg
        val periodTarget = lastStart.plusDays((periodCycleIndex * avg).toLong())
        val periodReminder = periodTarget.minusDays(lead.toLong())

        val ovulationDay = max(10, avg - 14)
        val fertileStartOffset = ovulationDay - 3 // (ovulationDay - 2) is 1-based cycle day
        val minFertileSpan = daysFromLast - fertileStartOffset
        val fertileCycleIndex = if (minFertileSpan <= 0) 0 else (minFertileSpan + avg - 1) / avg
        val fertileReminder = lastStart.plusDays((fertileCycleIndex * avg + fertileStartOffset).toLong())

        return ReminderScheduleDates(
            periodReminderDate = periodReminder,
            periodTargetDate = periodTarget,
            fertileReminderDate = fertileReminder
        )
    }

    private fun emptyClassification(date: LocalDate) = DayClassification(
        date = date,
        fertility = FertilityStatus.NOT_FERTILE,
        periodStatus = null,
        cycleDay = null,
        daysToNextPeriod = null,
        nextPeriodDate = null
    )

    private fun ensureSortedDistinct(periodStarts: List<LocalDate>): List<LocalDate> {
        if (periodStarts.size <= 1) return periodStarts
        for (i in 1 until periodStarts.size) {
            if (!periodStarts[i - 1].isBefore(periodStarts[i])) {
                return periodStarts.distinct().sorted()
            }
        }
        return periodStarts
    }

    private fun classifyDateSorted(
        date: LocalDate,
        sorted: List<LocalDate>,
        firstStart: LocalDate,
        lastStart: LocalDate,
        expectedPeriodDate: LocalDate,
        model: CycleModel,
        periodLength: Int
    ): DayClassification {
        val avg = model.averageCycleLength

        // Single binary search resolves both logged-period membership and cycle anchor in O(log N)
        val searchIdx = sorted.binarySearch(date)
        val insertionPoint = if (searchIdx >= 0) searchIdx else -searchIdx - 1

        // 1. Check if date falls within a logged period
        var periodStatus: PeriodStatus? = when {
            searchIdx >= 0 -> PeriodStatus.LOGGED_START
            insertionPoint > 0 &&
                ChronoUnit.DAYS.between(sorted[insertionPoint - 1], date) < periodLength -> PeriodStatus.LOGGED
            else -> null
        }

        // 2. Anchor the cycle for this date
        var cycleAnchor = lastStart
        var cycleLengthForAnchor = avg

        if (date.isBefore(firstStart)) {
            val gap = ChronoUnit.DAYS.between(date, firstStart).toInt()
            val cyclesBack = (gap + avg - 1) / avg
            cycleAnchor = firstStart.minusDays((cyclesBack * avg).toLong())
        } else if (date.isBefore(lastStart)) {
            val anchorIdx = if (searchIdx >= 0) searchIdx else insertionPoint - 1
            cycleAnchor = sorted[anchorIdx]
            val actualLen = ChronoUnit.DAYS.between(sorted[anchorIdx], sorted[anchorIdx + 1]).toInt()
            cycleLengthForAnchor = if (actualLen in 15..60) actualLen else avg
        } else {
            val daysSinceLast = ChronoUnit.DAYS.between(lastStart, date).toInt()
            val cycleIndex = daysSinceLast / avg
            cycleAnchor = lastStart.plusDays((cycleIndex * avg).toLong())
            cycleLengthForAnchor = avg
        }

        val cycleDay = ChronoUnit.DAYS.between(cycleAnchor, date).toInt() + 1
        val nextPeriodDate = cycleAnchor.plusDays(cycleLengthForAnchor.toLong())
        val daysSinceLastStart = ChronoUnit.DAYS.between(lastStart, date).toInt()
        val daysLate = if (!date.isBefore(lastStart) && daysSinceLastStart > avg) {
            daysSinceLastStart - avg
        } else {
            0
        }
        val daysToNextPeriod = if (!date.isBefore(lastStart) && daysSinceLastStart == avg) {
            0
        } else {
            ChronoUnit.DAYS.between(date, nextPeriodDate).toInt()
        }

        // Predicted period if on or after the current cycle's expected start
        if (periodStatus == null && !date.isBefore(expectedPeriodDate)) {
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

        val rawCouldStart = if (model.cyclesUsed > 0 && !date.isBefore(lastStart)) {
            min(ovulationDay - 5, model.minCycleLength - 18)
        } else {
            ovulationDay - 5
        }
        val rawCouldEnd = if (model.cyclesUsed > 0 && !date.isBefore(lastStart)) {
            max(fertileEnd + 3, model.maxCycleLength - 11)
        } else {
            fertileEnd + 3
        }
        val couldStart = max(periodLength + 1, rawCouldStart)
        val couldEnd = min(cycleLengthForAnchor - 1, rawCouldEnd)

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
            nextPeriodDate = nextPeriodDate,
            daysLate = daysLate,
            expectedPeriodDate = expectedPeriodDate
        )
    }
}
