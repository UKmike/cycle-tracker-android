package com.example.cycletracker.domain

import org.junit.Assert.assertEquals
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

        // Day 22 (Nov 22): luteal phase, not fertile
        val day22 = CycleCalculator.classifyDate(start.plusDays(21), starts, model)
        assertEquals(FertilityStatus.NOT_FERTILE, day22.fertility)

        // Day 29 (Nov 29): predicted next period start
        val day29 = CycleCalculator.classifyDate(start.plusDays(28), starts, model)
        assertEquals(PeriodStatus.PREDICTED_START, day29.periodStatus)
    }
}
