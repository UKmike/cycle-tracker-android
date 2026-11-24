package com.example.cycletracker.data

import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.ui.CyclePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CycleBackupCodecTest {

    @Test
    fun roundTripsPeriodStartsAndPeriodLength() {
        val starts = listOf(
            LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31)
        )
        val json = CycleBackupCodec.encode(starts, periodLength = 6, exportedAt = LocalDate.of(2026, 11, 21))
        val decoded = CycleBackupCodec.decode(json).getOrThrow()

        assertEquals(starts.sorted(), decoded.periodStarts)
        assertEquals(6, decoded.periodLength)
    }

    @Test
    fun decodesEmptyBackupAndClampsOutOfRangePeriodLength() {
        val json = """{"version": 1, "periodLength": 99, "periodStarts": []}"""
        val decoded = CycleBackupCodec.decode(json).getOrThrow()
        assertTrue(decoded.periodStarts.isEmpty())
        assertEquals(10, decoded.periodLength)
    }

    @Test
    fun rejectsMalformedJsonAndInvalidDates() {
        assertTrue(CycleBackupCodec.decode("not json").isFailure)
        assertTrue(CycleBackupCodec.decode("""{"version":1}""").isFailure)
        assertTrue(CycleBackupCodec.decode("""{"periodStarts":["not-a-date"]}""").isFailure)
    }

    @Test
    fun smoothColorSamplerPreservesUniformAndEdgeColors() {
        val solid = intArrayOf(
            CyclePalette.ARGB_RED_PERIOD,
            CyclePalette.ARGB_RED_PERIOD,
            CyclePalette.ARGB_RED_PERIOD
        )
        assertEquals(CyclePalette.ARGB_RED_PERIOD, CyclePalette.sampleSmoothCycleColorArgb(solid, 1.5f))
        assertEquals(0, CyclePalette.sampleSmoothCycleColorArgb(IntArray(0), 0f))

        val packed = CyclePalette.markerColorsArgb(CyclePalette.ARGB_GREEN_FERTILE)
        assertEquals(CyclePalette.ARGB_HALO_FERTILE, CyclePalette.unpackHalo(packed))
        assertEquals(CyclePalette.ARGB_DOT_FERTILE, CyclePalette.unpackDot(packed))
    }

    @Test
    fun midStageSpansMiddayBeforeThroughMiddayAfterAndSkipsBlueToRed() {
        // 28-day default cycle starting Nov 1:
        // Days 1-5 (Nov 1-5): LOGGED_PERIOD (Red)
        // Days 6-8 (Nov 6-8): NOT_FERTILE (Blue)
        // Days 9-11 (Nov 9-11): COULD_BE_FERTILE (Yellow)
        // Days 12-15 (Nov 12-15): FERTILE (Green)
        // Days 16-18 (Nov 16-18): COULD_BE_FERTILE (Yellow)
        // Days 19-28 (Nov 19-28): NOT_FERTILE (Blue)
        // Day 29 (Nov 29): PREDICTED_PERIOD (Likely Red) -> sharp boundary!
        val start = LocalDate.of(2026, 11, 1)
        val starts = listOf(start)
        val model = CycleCalculator.computeModel(starts)

        fun resolve(dayOffset: Long, hour: Int): CyclePalette.MidStageTransition? {
            val d = start.plusDays(dayOffset)
            val days = CycleCalculator.classifyRange(d.minusDays(1), 3, starts, model)
            return CyclePalette.resolveMidStageTransition(days[0], days[1], days[2], true, LocalTime.of(hour, 0))
        }

        // Day 11 (Nov 11, Yellow) -> Day 12 (Nov 12, Green):
        // Nov 11 at 11:00 -> normal Yellow
        assertNull(resolve(10, 11))
        // Nov 11 at 12:00 -> mid-stage blending forward (+1) into Green
        val nov11Noon = resolve(10, 12)
        assertNotNull(nov11Noon)
        assertEquals(+1, nov11Noon!!.dayDirection)
        assertEquals(CyclePalette.VisualStage.FERTILE, CyclePalette.visualStage(nov11Noon.partnerInfo))
        // Nov 12 at 00:00 -> mid-stage blending backward (-1) with Yellow
        val nov12Midnight = resolve(11, 0)
        assertNotNull(nov12Midnight)
        assertEquals(-1, nov12Midnight!!.dayDirection)
        assertEquals(CyclePalette.VisualStage.COULD_BE_FERTILE, CyclePalette.visualStage(nov12Midnight.partnerInfo))
        // Nov 12 at 12:00 -> full Green (mid-stage ends at midday)
        assertNull(resolve(11, 12))

        // Day 28 (Nov 28, Blue) -> Day 29 (Nov 29, Predicted Red): SHARP boundary, NO mid-stage!
        assertNull(resolve(27, 15))
        assertNull(resolve(28, 9))
        val sharpDays = CycleCalculator.classifyRange(start.plusDays(26), 3, starts, model)
        assertFalse(CyclePalette.needsMiddayRefresh(sharpDays[0], sharpDays[1], sharpDays[2], true))

        // Non-transition mid-phase day (Day 22, Blue surrounded by Blue): no midday refresh
        val midBlue = CycleCalculator.classifyRange(start.plusDays(20), 3, starts, model)
        assertFalse(CyclePalette.needsMiddayRefresh(midBlue[0], midBlue[1], midBlue[2], true))
    }

    @Test
    fun nextRefreshMillisSchedulesMiddayOnlyOnTransitionDaysBeforeNoon() {
        val zone = ZoneId.of("UTC")
        val morning = ZonedDateTime.of(2026, 11, 11, 8, 30, 0, 0, zone)
        val afternoon = ZonedDateTime.of(2026, 11, 11, 14, 0, 0, 0, zone)
        val expectedNoon = ZonedDateTime.of(2026, 11, 11, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expectedMidnight = ZonedDateTime.of(2026, 11, 12, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(expectedNoon, CyclePalette.computeNextRefreshMillis(true, morning))
        assertEquals(expectedMidnight, CyclePalette.computeNextRefreshMillis(true, afternoon))
        assertEquals(expectedMidnight, CyclePalette.computeNextRefreshMillis(false, morning))
    }
}
