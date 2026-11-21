package com.example.cycletracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleCalculatorTest {

    @Test
    fun adaptsCycleLengthFromHistory() {
        val starts = listOf(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31), // 30 days
            LocalDate.of(2026, 10, 2)  // 32 days
        )
        val model = CycleCalculator.computeModel(starts)
        assertTrue(model.hasData)
        assertEquals(2, model.cyclesUsed)
        assertEquals(31, model.averageCycleLength) // weighted: (30*1 + 32*2)/3 = 31.33 -> 31
        assertEquals(1, model.variabilityDays)
        assertEquals(17, model.ovulationDay) // 31 - 14
    }

    @Test
    fun classifiesFertileCouldBeAndNotFertile() {
        val start = LocalDate.of(2026, 11, 1)
        val starts = listOf(start)
        val model = CycleCalculator.computeModel(starts) // default 28d, ovulation day 14

        // Day 1 (Nov 1): period start, not fertile
        val day1 = CycleCalculator.classifyDate(start, starts, model)
        assertEquals(PeriodStatus.LOGGED_START, day1.periodStatus)
        assertEquals(FertilityStatus.NOT_FERTILE, day1.fertility)
        assertEquals(28, day1.daysToNextPeriod)

        // Day 10 (Nov 10): could be fertile (ovulation - 4)
        val day10 = CycleCalculator.classifyDate(start.plusDays(9), starts, model)
        assertEquals(FertilityStatus.COULD_BE_FERTILE, day10.fertility)

        // Day 14 (Nov 14): fertile (ovulation day)
        val day14 = CycleCalculator.classifyDate(start.plusDays(13), starts, model)
        assertEquals(FertilityStatus.FERTILE, day14.fertility)

        // Day 18 (Nov 18): 3 days after fertile period ends (Day 15) -> could be fertile
        val day18 = CycleCalculator.classifyDate(start.plusDays(17), starts, model)
        assertEquals(FertilityStatus.COULD_BE_FERTILE, day18.fertility)

        // Day 22 (Nov 22): luteal phase, not fertile
        val day22 = CycleCalculator.classifyDate(start.plusDays(21), starts, model)
        assertEquals(FertilityStatus.NOT_FERTILE, day22.fertility)

        // Day 29 (Nov 29): predicted next period start
        val day29 = CycleCalculator.classifyDate(start.plusDays(28), starts, model)
        assertEquals(PeriodStatus.PREDICTED_START, day29.periodStatus)
    }

    @Test
    fun irregularCyclesUseFullOginoKnausBounds() {
        // 24-day cycle followed by 38-day cycle -> min=24, max=38, weighted avg=33
        val starts = listOf(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 25), // 24 days
            LocalDate.of(2026, 11, 2)  // 38 days
        )
        val model = CycleCalculator.computeModel(starts)
        assertEquals(24, model.minCycleLength)
        assertEquals(38, model.maxCycleLength)
        assertEquals(33, model.averageCycleLength)

        val anchor = starts.last()
        // Ogino-Knaus start: 24 - 18 = Day 6
        val day5 = CycleCalculator.classifyDate(anchor.plusDays(4), starts, model)
        val day6 = CycleCalculator.classifyDate(anchor.plusDays(5), starts, model)
        assertEquals(FertilityStatus.NOT_FERTILE, day5.fertility)
        assertEquals(FertilityStatus.COULD_BE_FERTILE, day6.fertility)

        // Ogino-Knaus end: 38 - 11 = Day 27
        val day27 = CycleCalculator.classifyDate(anchor.plusDays(26), starts, model)
        val day28 = CycleCalculator.classifyDate(anchor.plusDays(27), starts, model)
        assertEquals(FertilityStatus.COULD_BE_FERTILE, day27.fertility)
        assertEquals(FertilityStatus.NOT_FERTILE, day28.fertility)
    }

    @Test
    fun tracksDaysLateAfterExpectedStart() {
        val start = LocalDate.of(2026, 11, 1)
        val starts = listOf(start)
        val model = CycleCalculator.computeModel(starts) // 28-day default

        val expectedDay = start.plusDays(28)
        val onExpected = CycleCalculator.classifyDate(expectedDay, starts, model)
        assertEquals(0, onExpected.daysToNextPeriod)
        assertEquals(0, onExpected.daysLate)
        assertEquals(expectedDay, onExpected.expectedPeriodDate)

        val threeDaysLate = CycleCalculator.classifyDate(expectedDay.plusDays(3), starts, model)
        assertEquals(3, threeDaysLate.daysLate)
        assertEquals(expectedDay, threeDaysLate.expectedPeriodDate)
    }

    @Test
    fun classifyRangeMatchesClassifyDateAcrossAllRegions() {
        // Deliberately unsorted with a duplicate and an outlier (>60d gap)
        val rawStarts = listOf(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 9, 2),
            LocalDate.of(2026, 9, 2), // duplicate
            LocalDate.of(2026, 10, 31)
        )
        val model = CycleCalculator.computeModel(rawStarts)
        val rangeStart = LocalDate.of(2026, 4, 20) // well before firstStart
        val totalDays = 240 // spans before firstStart, outlier gap, normal cycles, predicted & late

        val batch = CycleCalculator.classifyRange(rangeStart, totalDays, rawStarts, model, periodLength = 6)
        assertEquals(totalDays, batch.size)

        for (offset in 0 until totalDays) {
            val d = rangeStart.plusDays(offset.toLong())
            val single = CycleCalculator.classifyDate(d, rawStarts, model, periodLength = 6)
            assertEquals("Mismatch on $d", single, batch[offset])
        }
    }

    @Test
    fun customPeriodLengthAdjustsLoggedAndPredictedSpan() {
        val start = LocalDate.of(2026, 11, 1)
        val starts = listOf(start)
        val model = CycleCalculator.computeModel(starts)

        // With periodLength = 3: Day 3 is LOGGED, Day 4 is null
        assertEquals(PeriodStatus.LOGGED, CycleCalculator.classifyDate(start.plusDays(2), starts, model, 3).periodStatus)
        assertNull(CycleCalculator.classifyDate(start.plusDays(3), starts, model, 3).periodStatus)

        // With periodLength = 7: Day 7 is LOGGED, Day 8 is null; predicted Day 7 is PREDICTED
        assertEquals(PeriodStatus.LOGGED, CycleCalculator.classifyDate(start.plusDays(6), starts, model, 7).periodStatus)
        assertNull(CycleCalculator.classifyDate(start.plusDays(7), starts, model, 7).periodStatus)
        assertEquals(
            PeriodStatus.PREDICTED,
            CycleCalculator.classifyDate(start.plusDays(28 + 6), starts, model, 7).periodStatus
        )
    }

    @Test
    fun slidingWindowUsesOnlyLastSixValidCycles() {
        // 8 valid cycles: first two are 20d (should drop off window), last six are 30d
        val starts = mutableListOf(LocalDate.of(2026, 1, 1))
        val gaps = listOf(20, 20, 30, 30, 30, 30, 30, 30)
        for (g in gaps) {
            starts.add(starts.last().plusDays(g.toLong()))
        }
        val model = CycleCalculator.computeModel(starts)
        assertEquals(6, model.cyclesUsed)
        assertEquals(30, model.averageCycleLength)
        assertEquals(30, model.minCycleLength)
        assertEquals(30, model.maxCycleLength)
        assertEquals(0, model.variabilityDays)
    }

    @Test
    fun emptyStartsReturnsDefaultEmptyModelAndRange() {
        val model = CycleCalculator.computeModel(emptyList())
        assertFalse(model.hasData)
        assertEquals(CycleCalculator.DEFAULT_CYCLE_LENGTH, model.averageCycleLength)

        val range = CycleCalculator.classifyRange(LocalDate.of(2026, 11, 1), 5, emptyList(), model)
        assertEquals(5, range.size)
        assertTrue(range.all { it.fertility == FertilityStatus.NOT_FERTILE && it.periodStatus == null })

        val reminders = CycleCalculator.computeNextReminderDates(LocalDate.of(2026, 11, 1), emptyList(), model)
        assertNull(reminders.periodReminderDate)
        assertNull(reminders.fertileReminderDate)
    }

    @Test
    fun computesNextReminderDatesAccurately() {
        val start = LocalDate.of(2026, 11, 1)
        val starts = listOf(start)
        val model = CycleCalculator.computeModel(starts) // 28d avg, ovulation day 14, fertile starts day 12 (Nov 12)

        // On Nov 5: fertile reminder = Nov 12, period reminder (2d before Nov 29) = Nov 27
        val r1 = CycleCalculator.computeNextReminderDates(
            earliestDate = LocalDate.of(2026, 11, 5),
            periodStarts = starts,
            model = model,
            periodLeadDays = 2
        )
        assertEquals(LocalDate.of(2026, 11, 27), r1.periodReminderDate)
        assertEquals(LocalDate.of(2026, 11, 29), r1.periodTargetDate)
        assertEquals(LocalDate.of(2026, 11, 12), r1.fertileReminderDate)

        // On Nov 13 (day after fertile started): fertile rolls to next cycle (Dec 10)
        val r2 = CycleCalculator.computeNextReminderDates(
            earliestDate = LocalDate.of(2026, 11, 13),
            periodStarts = starts,
            model = model,
            periodLeadDays = 2
        )
        assertEquals(LocalDate.of(2026, 11, 27), r2.periodReminderDate)
        assertEquals(LocalDate.of(2026, 12, 10), r2.fertileReminderDate)

        // On Nov 28 (after 2d lead day Nov 27 passed): period rolls to cycle 2 (Dec 27 - 2 = Dec 25)
        val r3 = CycleCalculator.computeNextReminderDates(
            earliestDate = LocalDate.of(2026, 11, 28),
            periodStarts = starts,
            model = model,
            periodLeadDays = 2
        )
        assertEquals(LocalDate.of(2026, 12, 25), r3.periodReminderDate)
        assertEquals(LocalDate.of(2026, 12, 27), r3.periodTargetDate)
    }
}
