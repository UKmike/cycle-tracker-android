package com.example.cycletracker.data

import com.example.cycletracker.ui.CyclePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
}
