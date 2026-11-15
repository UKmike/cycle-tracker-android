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
}
