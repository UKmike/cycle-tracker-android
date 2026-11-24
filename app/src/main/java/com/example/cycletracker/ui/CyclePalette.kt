package com.example.cycletracker.ui

import androidx.compose.ui.graphics.Color
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.domain.CycleModel
import com.example.cycletracker.domain.DayClassification
import com.example.cycletracker.domain.FertilityStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared color palette, raised-cosine cycle ring blending, and thread-safe date formatters
 * used by both the Compose UI and [com.example.cycletracker.widget.CycleWidgetProvider].
 */
object CyclePalette {

    // Core phase ARGB ints (compile-time constants — zero Color.parseColor() overhead)
    // GreenFertile deepened slightly to #047857 for >= 4.5:1 WCAG AA contrast with white text
    const val ARGB_GREEN_FERTILE: Int = 0xFF047857.toInt()
    const val ARGB_GREEN_BRIGHT: Int = 0xFF10B981.toInt()
    const val ARGB_GREEN_DARK: Int = 0xFF065F46.toInt()
    const val ARGB_YELLOW_COULD_BE: Int = 0xFFFBBF24.toInt()
    const val ARGB_BLUE_NOT_FERTILE: Int = 0xFF38BDF8.toInt()
    const val ARGB_RED_PERIOD: Int = 0xFFDC2626.toInt()
    const val ARGB_RED_PREDICTED: Int = 0xFFFCA5A5.toInt()

    // Widget obsidian card gradient
    const val ARGB_WIDGET_BG_START: Int = 0xFF262626.toInt()
    const val ARGB_WIDGET_BG_MID: Int = 0xFF141414.toInt()
    const val ARGB_WIDGET_BG_END: Int = 0xFF050505.toInt()

    // Widget text accents
    const val ARGB_WIDGET_TEXT_PERIOD: Int = 0xFFEF4444.toInt()
    const val ARGB_WIDGET_FOOTER_PERIOD: Int = 0xFFFCA5A5.toInt()
    const val ARGB_WIDGET_TEXT_LIKELY: Int = 0xFFFCA5A5.toInt()
    const val ARGB_WIDGET_FOOTER_LIKELY: Int = 0xFFF87171.toInt()
    const val ARGB_WIDGET_FOOTER_FERTILE: Int = 0xFF6EE7B7.toInt()
    const val ARGB_WIDGET_FOOTER_COULD_BE: Int = 0xFFFDE68A.toInt()
    const val ARGB_WIDGET_FOOTER_NOT_FERTILE: Int = 0xFF7DD3FC.toInt()

    // Day-marker halo & dot pairs
    const val ARGB_HALO_PERIOD: Int = 0xFFFCA5A5.toInt()
    const val ARGB_DOT_PERIOD: Int = 0xFFB91C1C.toInt()
    const val ARGB_HALO_FERTILE: Int = 0xFF6EE7B7.toInt()
    const val ARGB_DOT_FERTILE: Int = 0xFF047857.toInt()
    const val ARGB_HALO_COULD_BE: Int = 0xFFFEF08A.toInt()
    const val ARGB_DOT_COULD_BE: Int = 0xFFD97706.toInt()
    const val ARGB_HALO_NOT_FERTILE: Int = 0xFFBAE6FD.toInt()
    const val ARGB_DOT_NOT_FERTILE: Int = 0xFF0284C7.toInt()

    // Compose Color counterparts
    val GreenFertile = Color(ARGB_GREEN_FERTILE)
    val GreenBright = Color(ARGB_GREEN_BRIGHT)
    val GreenDark = Color(ARGB_GREEN_DARK)
    val YellowCouldBe = Color(ARGB_YELLOW_COULD_BE)
    val BlueNotFertile = Color(ARGB_BLUE_NOT_FERTILE)
    val RedPeriod = Color(ARGB_RED_PERIOD)
    val RedPredicted = Color(ARGB_RED_PREDICTED)

    // Thread-safe shared formatters
    val SHORT_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    val SELECTED_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    val MONTH_YEAR_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val AXIS_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy")
    val HISTORY_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun computeCycleDayArgbColors(
        periodStarts: List<LocalDate>,
        model: CycleModel,
        periodLength: Int = CycleCalculator.DEFAULT_PERIOD_LENGTH
    ): IntArray {
        if (!model.hasData || periodStarts.isEmpty()) return IntArray(0)
        val cycleStart = periodStarts.last()
        val cycleLen = model.averageCycleLength.coerceAtLeast(1)
        val days = CycleCalculator.classifyRange(
            startDate = cycleStart,
            dayCount = cycleLen,
            periodStarts = periodStarts,
            model = model,
            periodLength = periodLength
        )
        return IntArray(days.size) { idx ->
            val dayInfo = days[idx]
            when {
                dayInfo.isLoggedPeriod -> ARGB_RED_PERIOD
                dayInfo.fertility == FertilityStatus.FERTILE -> ARGB_GREEN_FERTILE
                dayInfo.fertility == FertilityStatus.COULD_BE_FERTILE -> ARGB_YELLOW_COULD_BE
                else -> ARGB_BLUE_NOT_FERTILE
            }
        }
    }

    enum class VisualStage {
        LOGGED_PERIOD,
        PREDICTED_PERIOD,
        FERTILE,
        COULD_BE_FERTILE,
        NOT_FERTILE
    }

    fun visualStage(info: DayClassification): VisualStage = when {
        info.isLoggedPeriod -> VisualStage.LOGGED_PERIOD
        info.isPredictedPeriod -> VisualStage.PREDICTED_PERIOD
        info.fertility == FertilityStatus.FERTILE -> VisualStage.FERTILE
        info.fertility == FertilityStatus.COULD_BE_FERTILE -> VisualStage.COULD_BE_FERTILE
        else -> VisualStage.NOT_FERTILE
    }

    data class MidStageTransition(
        val partnerInfo: DayClassification,
        /** -1 when blending with yesterday (00:00..11:59), +1 when blending with tomorrow (12:00..23:59). */
        val dayDirection: Int
    )

    /**
     * True when [dayBefore] transitions smoothly into a different visual stage on [dayAfter],
     * matching the faded calendar boundaries (skipping the sharp non-period -> period boundary).
     */
    fun hasSmoothStageTransition(
        dayBefore: DayClassification,
        dayAfter: DayClassification
    ): Boolean {
        if (visualStage(dayBefore) == visualStage(dayAfter)) return false
        if (!dayBefore.isAnyPeriod && dayAfter.isAnyPeriod) return false
        return true
    }

    /**
     * Resolves the active mid-stage transition for [todayInfo] at [nowTime]:
     * - 00:00..11:59 on the day after a smooth transition -> blends with [yesterdayInfo] (-1)
     * - 12:00..23:59 on the day before a smooth transition -> blends with [tomorrowInfo] (+1)
     */
    fun resolveMidStageTransition(
        yesterdayInfo: DayClassification,
        todayInfo: DayClassification,
        tomorrowInfo: DayClassification,
        hasData: Boolean,
        nowTime: LocalTime = LocalTime.now()
    ): MidStageTransition? {
        if (!hasData) return null
        val afterNoon = !nowTime.isBefore(LocalTime.NOON)
        return when {
            !afterNoon && hasSmoothStageTransition(yesterdayInfo, todayInfo) ->
                MidStageTransition(partnerInfo = yesterdayInfo, dayDirection = -1)
            afterNoon && hasSmoothStageTransition(todayInfo, tomorrowInfo) ->
                MidStageTransition(partnerInfo = tomorrowInfo, dayDirection = +1)
            else -> null
        }
    }

    /**
     * True when today requires a 12:00 PM refresh (either leaving an incoming morning mid-stage
     * or entering an outgoing afternoon mid-stage).
     */
    fun needsMiddayRefresh(
        yesterdayInfo: DayClassification,
        todayInfo: DayClassification,
        tomorrowInfo: DayClassification,
        hasData: Boolean
    ): Boolean {
        if (!hasData) return false
        return hasSmoothStageTransition(yesterdayInfo, todayInfo) ||
            hasSmoothStageTransition(todayInfo, tomorrowInfo)
    }

    /**
     * Computes the next refresh epoch millis: today at 12:00 PM if [needsMiddayRefreshToday] is true
     * and it is currently before midday; otherwise tomorrow at 00:00 AM.
     */
    fun computeNextRefreshMillis(
        needsMiddayRefreshToday: Boolean,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Long {
        val target = if (needsMiddayRefreshToday && now.toLocalTime().isBefore(LocalTime.NOON)) {
            now.toLocalDate().atTime(LocalTime.NOON).atZone(now.zone)
        } else {
            now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        }
        return target.toInstant().toEpochMilli()
    }

    fun blendArgb(a: Int, b: Int, t: Float = 0.5f): Int {
        val inv = 1f - t
        val alpha = (((a ushr 24) and 0xFF) * inv + ((b ushr 24) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        val r = (((a ushr 16) and 0xFF) * inv + ((b ushr 16) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        val g = (((a ushr 8) and 0xFF) * inv + ((b ushr 8) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        val bl = ((a and 0xFF) * inv + (b and 0xFF) * t).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (r shl 16) or (g shl 8) or bl
    }

    fun markerColorsArgb(phaseArgb: Int, nextPhaseArgb: Int? = null): Long {
        val base = singleMarkerColorsArgb(phaseArgb)
        if (nextPhaseArgb == null || nextPhaseArgb == phaseArgb) return base
        val next = singleMarkerColorsArgb(nextPhaseArgb)
        val halo = blendArgb(unpackHalo(base), unpackHalo(next), 0.5f)
        val dot = blendArgb(unpackDot(base), unpackDot(next), 0.5f)
        return (halo.toLong() shl 32) or (dot.toLong() and 0xFFFFFFFFL)
    }

    private fun singleMarkerColorsArgb(phaseArgb: Int): Long {
        val halo: Int
        val dot: Int
        when (phaseArgb) {
            ARGB_RED_PERIOD -> {
                halo = ARGB_HALO_PERIOD
                dot = ARGB_DOT_PERIOD
            }
            ARGB_GREEN_FERTILE -> {
                halo = ARGB_HALO_FERTILE
                dot = ARGB_DOT_FERTILE
            }
            ARGB_YELLOW_COULD_BE -> {
                halo = ARGB_HALO_COULD_BE
                dot = ARGB_DOT_COULD_BE
            }
            else -> {
                halo = ARGB_HALO_NOT_FERTILE
                dot = ARGB_DOT_NOT_FERTILE
            }
        }
        return (halo.toLong() shl 32) or (dot.toLong() and 0xFFFFFFFFL)
    }

    fun unpackHalo(packed: Long): Int = (packed ushr 32).toInt()
    fun unpackDot(packed: Long): Int = packed.toInt()

    /**
     * Pure-Kotlin raised-cosine color blender over primitive [IntArray] ARGB colors
     * (works identically on Android and JVM unit tests with zero boxing).
     */
    fun sampleSmoothCycleColorArgb(
        colors: IntArray,
        dayPos: Float,
        blendRadius: Float = 1.6f
    ): Int {
        val count = colors.size
        if (count == 0) return 0
        var wSum = 0f
        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        val startIdx = max(0, (dayPos - blendRadius).toInt())
        val endIdx = min(count - 1, (dayPos + blendRadius).toInt() + 1)
        for (i in startIdx..endIdx) {
            val center = i + 0.5f
            val dist = abs(dayPos - center)
            if (dist <= blendRadius) {
                val weight = 0.5f * (1f + cos((PI * dist / blendRadius).toFloat()))
                val c = colors[i]
                rSum += ((c ushr 16) and 0xFF) * weight
                gSum += ((c ushr 8) and 0xFF) * weight
                bSum += (c and 0xFF) * weight
                wSum += weight
            }
        }
        if (wSum <= 0f) return colors[dayPos.toInt().coerceIn(0, count - 1)]
        val r = (rSum / wSum).roundToInt().coerceIn(0, 255)
        val g = (gSum / wSum).roundToInt().coerceIn(0, 255)
        val b = (bSum / wSum).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
