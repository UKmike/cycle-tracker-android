package com.example.cycletracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.cycletracker.MainActivity
import com.example.cycletracker.R
import com.example.cycletracker.data.CycleRepository
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.domain.CycleModel
import com.example.cycletracker.domain.DayClassification
import com.example.cycletracker.domain.FertilityStatus
import com.example.cycletracker.domain.PeriodStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

class CycleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAllWidgets(context)
        }
    }

    companion object {
        private val shortDateFmt = DateTimeFormatter.ofPattern("MMM d")

        private val COLOR_GREEN = Color.parseColor("#059669")
        private val COLOR_YELLOW = Color.parseColor("#FBBF24")
        private val COLOR_LIGHT_BLUE = Color.parseColor("#38BDF8")
        private val COLOR_RED_PERIOD = Color.parseColor("#DC2626")

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CycleWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            optionsOverride: Bundle? = null
        ) {
            val options = optionsOverride ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val isCompact1x1 = minWidthDp < 110 || minHeightDp < 90

            val repo = CycleRepository(context)
            val starts = repo.getPeriodStarts()
            val today = LocalDate.now()
            val model = CycleCalculator.computeModel(starts)
            val info = CycleCalculator.classifyDate(today, starts, model)

            val views = RemoteViews(context.packageName, R.layout.widget_cycle)

            // Launch MainActivity on tap without duplicating it on the back stack
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val blackGradStart = Color.parseColor("#262626")
            val blackGradMid = Color.parseColor("#141414")
            val blackGradEnd = Color.parseColor("#050505")

            val style = when {
                info.periodStatus == PeriodStatus.LOGGED_START ||
                    info.periodStatus == PeriodStatus.LOGGED -> WidgetStyle(
                    blackGradStart,
                    blackGradMid,
                    blackGradEnd,
                    Color.parseColor("#EF4444"),
                    Color.parseColor("#FCA5A5"),
                    "Period"
                )
                info.periodStatus == PeriodStatus.PREDICTED_START ||
                    info.periodStatus == PeriodStatus.PREDICTED -> WidgetStyle(
                    blackGradStart,
                    blackGradMid,
                    blackGradEnd,
                    Color.parseColor("#FCA5A5"),
                    Color.parseColor("#F87171"),
                    "Likely Period"
                )
                info.fertility == FertilityStatus.FERTILE -> WidgetStyle(
                    blackGradStart,
                    blackGradMid,
                    blackGradEnd,
                    Color.parseColor("#10B981"),
                    Color.parseColor("#6EE7B7"),
                    "Fertile"
                )
                info.fertility == FertilityStatus.COULD_BE_FERTILE -> WidgetStyle(
                    blackGradStart,
                    blackGradMid,
                    blackGradEnd,
                    COLOR_YELLOW,
                    Color.parseColor("#FDE68A"),
                    "Could Be Fertile"
                )
                else -> WidgetStyle(
                    blackGradStart,
                    blackGradMid,
                    blackGradEnd,
                    COLOR_LIGHT_BLUE,
                    Color.parseColor("#7DD3FC"),
                    "Not Fertile"
                )
            }

            val portraitWidthDp = max(minWidthDp, 56)
            val portraitHeightDp = max(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp),
                max(minHeightDp, 56)
            )

            // Render rounded card + gradient + frosted glass sheen + cycle outline
            val bgBitmap = renderWidgetBackgroundBitmap(
                context = context,
                widthDp = portraitWidthDp,
                heightDp = portraitHeightDp,
                isCompact = isCompact1x1,
                gradStart = style.gradStart,
                gradMid = style.gradMid,
                gradEnd = style.gradEnd,
                periodStarts = starts,
                model = model,
                todayInfo = info
            )
            views.setImageViewBitmap(R.id.widget_bg_image, bgBitmap)

            views.setTextViewText(R.id.widget_status_badge, style.statusLabel)
            views.setTextColor(R.id.widget_status_badge, style.textColor)
            views.setTextColor(R.id.widget_days_number, style.textColor)
            views.setTextColor(R.id.widget_days_unit, style.textColor)
            views.setTextColor(R.id.widget_compact_unit, style.textColor)
            views.setTextColor(R.id.widget_subtitle, style.textColor)
            views.setTextColor(R.id.widget_footer, style.footerColor)

            // Adapt layout density for 1x1 vs larger sizes
            if (isCompact1x1) {
                val padPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics
                ).toInt()
                views.setViewPadding(R.id.widget_content, padPx, padPx, padPx, padPx)
                views.setViewVisibility(R.id.widget_status_badge, View.GONE)
                views.setViewVisibility(R.id.widget_days_unit, View.GONE)
                views.setViewVisibility(R.id.widget_compact_unit, View.VISIBLE)
                views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                views.setViewVisibility(R.id.widget_footer, View.GONE)
                views.setTextViewTextSize(R.id.widget_days_number, TypedValue.COMPLEX_UNIT_SP, 22f)
                views.setTextViewTextSize(R.id.widget_compact_unit, TypedValue.COMPLEX_UNIT_SP, 12f)
            } else {
                val padPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 20f, context.resources.displayMetrics
                ).toInt()
                views.setViewPadding(R.id.widget_content, padPx, padPx, padPx, padPx)
                views.setViewVisibility(R.id.widget_status_badge, View.VISIBLE)
                views.setViewVisibility(R.id.widget_days_unit, View.VISIBLE)
                views.setViewVisibility(R.id.widget_compact_unit, View.GONE)
                views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
                views.setTextViewTextSize(R.id.widget_days_number, TypedValue.COMPLEX_UNIT_SP, 34f)
                views.setTextViewTextSize(R.id.widget_days_unit, TypedValue.COMPLEX_UNIT_SP, 13f)
                views.setTextViewTextSize(R.id.widget_subtitle, TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            if (!model.hasData || info.daysToNextPeriod == null || info.nextPeriodDate == null) {
                views.setTextViewText(R.id.widget_days_number, "--")
                views.setTextViewText(R.id.widget_days_unit, "days")
                views.setTextViewText(R.id.widget_compact_unit, "Days")
                views.setTextViewText(R.id.widget_subtitle, "to next period")
                views.setTextViewText(R.id.widget_footer, "Tap to log period")
            } else {
                val days = info.daysToNextPeriod
                val unit = if (days == 1) "day" else "days"
                val compactUnit = if (days == 1) "Day" else "Days"
                views.setTextViewText(R.id.widget_days_number, days.toString())
                views.setTextViewText(R.id.widget_days_unit, unit)
                views.setTextViewText(R.id.widget_compact_unit, compactUnit)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    if (days == 0) "period expected today" else "to next period"
                )
                views.setTextViewText(
                    R.id.widget_footer,
                    "Day ${info.cycleDay} • Next ${info.nextPeriodDate.format(shortDateFmt)}"
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun renderWidgetBackgroundBitmap(
            context: Context,
            widthDp: Int,
            heightDp: Int,
            isCompact: Boolean,
            gradStart: Int,
            gradMid: Int,
            gradEnd: Int,
            periodStarts: List<LocalDate>,
            model: CycleModel,
            todayInfo: DayClassification
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            // Cap bitmap dimensions to keep RemoteViews memory low while staying crisp
            val maxDim = 600f
            val rawW = widthDp * density
            val rawH = heightDp * density
            val scale = min(1f, maxDim / max(rawW, rawH))
            val w = max(140, (rawW * scale).toInt())
            val h = max(140, (rawH * scale).toInt())
            val dp = density * scale

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val strokeWidth = (if (isCompact) 4.2f else 6f) * dp
            val markerOverflow = (if (isCompact) 3.5f else 5.5f) * dp
            val ringInset = markerOverflow + strokeWidth / 2f
            val cardGap = (if (isCompact) 1.5f else 2.5f) * dp
            val cardInset = ringInset + strokeWidth / 2f + cardGap

            val outerCorner = min(
                (if (isCompact) 18f else 28f) * dp,
                (min(w, h) - ringInset * 2f) / 2f
            )
            val innerCorner = max(
                4f * dp,
                outerCorner - (cardInset - ringInset)
            )

            // 1. Draw diagonal gradient rounded card fill + frosted glass sheen + specular border
            val cardRect = RectF(cardInset, cardInset, w - cardInset, h - cardInset)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    cardRect.left, cardRect.top,
                    cardRect.right, cardRect.bottom,
                    intArrayOf(gradStart, gradMid, gradEnd),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, innerCorner, innerCorner, fillPaint)

            // Frosted top-left glass sheen
            val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    cardRect.left, cardRect.top,
                    cardRect.left, cardRect.top + cardRect.height() * 0.65f,
                    intArrayOf(Color.argb(28, 255, 255, 255), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, innerCorner, innerCorner, sheenPaint)

            // Delicate inner specular glass border
            val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = 1.2f * dp
                shader = LinearGradient(
                    cardRect.left, cardRect.top,
                    cardRect.right, cardRect.bottom,
                    intArrayOf(Color.argb(64, 255, 255, 255), Color.argb(16, 255, 255, 255)),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            val borderRect = RectF(
                cardRect.left + 0.6f * dp,
                cardRect.top + 0.6f * dp,
                cardRect.right - 0.6f * dp,
                cardRect.bottom - 0.6f * dp
            )
            canvas.drawRoundRect(borderRect, innerCorner, innerCorner, glassBorderPaint)

            // 2. If cycle data exists, draw the cycle-colored perimeter outline starting at top-center
            if (model.hasData && periodStarts.isNotEmpty()) {
                val sortedStarts = periodStarts.sorted()
                val cycleStart = sortedStarts.last()
                val cycleLen = max(1, model.averageCycleLength)
                val cycleColors = (0 until cycleLen).map { offset ->
                    val dayInfo = CycleCalculator.classifyDate(
                        date = cycleStart.plusDays(offset.toLong()),
                        periodStarts = sortedStarts,
                        model = model
                    )
                    when {
                        dayInfo.periodStatus == PeriodStatus.LOGGED_START ||
                            dayInfo.periodStatus == PeriodStatus.LOGGED -> COLOR_RED_PERIOD
                        dayInfo.fertility == FertilityStatus.FERTILE -> COLOR_GREEN
                        dayInfo.fertility == FertilityStatus.COULD_BE_FERTILE -> COLOR_YELLOW
                        else -> COLOR_LIGHT_BLUE
                    }
                }

                val left = ringInset
                val top = ringInset
                val right = w - ringInset
                val bottom = h - ringInset
                val midX = w / 2f

                val perimeter = Path().apply {
                    moveTo(midX, top)
                    lineTo(right - outerCorner, top)
                    arcTo(
                        RectF(right - 2 * outerCorner, top, right, top + 2 * outerCorner),
                        -90f, 90f, false
                    )
                    lineTo(right, bottom - outerCorner)
                    arcTo(
                        RectF(right - 2 * outerCorner, bottom - 2 * outerCorner, right, bottom),
                        0f, 90f, false
                    )
                    lineTo(left + outerCorner, bottom)
                    arcTo(
                        RectF(left, bottom - 2 * outerCorner, left + 2 * outerCorner, bottom),
                        90f, 90f, false
                    )
                    lineTo(left, top + outerCorner)
                    arcTo(
                        RectF(left, top, left + 2 * outerCorner, top + 2 * outerCorner),
                        180f, 90f, false
                    )
                    lineTo(midX, top)
                }

                val pm = PathMeasure(perimeter, false)
                val totalLength = pm.length
                val segPath = Path()
                val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidth
                    strokeCap = Paint.Cap.BUTT
                }

                val count = cycleColors.size
                cycleColors.forEachIndexed { index, segColor ->
                    val startDist = totalLength * index / count
                    val endDist = totalLength * (index + 1) / count
                    segPath.reset()
                    pm.getSegment(startDist, endDist, segPath, true)
                    segPaint.color = segColor
                    canvas.drawPath(segPath, segPaint)
                }

                // Top-center tick marking Cycle Day 1 (period start)
                val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    this.strokeWidth = (if (isCompact) 1.6f else 2f) * dp
                    strokeCap = Paint.Cap.ROUND
                    color = Color.WHITE
                }
                canvas.drawLine(
                    midX, top - strokeWidth * 0.6f,
                    midX, top + strokeWidth * 0.6f,
                    tickPaint
                )

                // Highlight current cycle day on the outline (same 3-layer marker as app)
                val currentDay = todayInfo.cycleDay
                if (currentDay != null) {
                    val clampedIndex = ((currentDay - 1) % count + count) % count
                    val midDist = totalLength * (clampedIndex + 0.5f) / count
                    val pos = FloatArray(2)
                    pm.getPosTan(midDist, pos, null)

                    val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                    }
                    val outerR = (if (isCompact) 5.2f else 8.5f) * dp
                    val midR = (if (isCompact) 3.7f else 6f) * dp
                    val innerR = (if (isCompact) 2.1f else 3.5f) * dp

                    markerPaint.color = Color.WHITE
                    canvas.drawCircle(pos[0], pos[1], outerR, markerPaint)
                    markerPaint.color = Color.parseColor("#111827")
                    canvas.drawCircle(pos[0], pos[1], midR, markerPaint)
                    markerPaint.color = cycleColors[clampedIndex]
                    canvas.drawCircle(pos[0], pos[1], innerR, markerPaint)
                }
            }

            return bitmap
        }

        private data class WidgetStyle(
            val gradStart: Int,
            val gradMid: Int,
            val gradEnd: Int,
            val textColor: Int,
            val footerColor: Int,
            val statusLabel: String
        )
    }
}
