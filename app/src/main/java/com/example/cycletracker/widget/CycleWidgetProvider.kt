package com.example.cycletracker.widget

import android.app.AlarmManager
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
import com.example.cycletracker.notifications.CycleReminderScheduler
import com.example.cycletracker.ui.CyclePalette
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class CycleWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleMidnightUpdate(context, assumeHasWidgets = true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMidnightUpdate(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val batch = loadWidgetBatchData(context)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, batch)
        }
        batch.repo.setLastWidgetRenderEpochDay(batch.today.toEpochDay())
        scheduleMidnightUpdate(context, assumeHasWidgets = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val batch = loadWidgetBatchData(context)
        updateWidget(context, appWidgetManager, appWidgetId, batch, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MIDNIGHT_UPDATE,
            Intent.ACTION_DATE_CHANGED -> {
                updateAllWidgets(context, onlyIfDateChanged = true)
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                updateAllWidgets(context, onlyIfDateChanged = false)
                CycleReminderScheduler.reschedule(context)
            }
        }
    }

    companion object {
        private const val ACTION_MIDNIGHT_UPDATE =
            "com.example.cycletracker.action.MIDNIGHT_WIDGET_UPDATE"
        private const val MIDNIGHT_REQUEST_CODE = 1001

        fun updateAllWidgets(context: Context, onlyIfDateChanged: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CycleWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) {
                cancelMidnightUpdate(context)
                return
            }

            val repo = CycleRepository(context)
            val today = LocalDate.now()
            val todayEpoch = today.toEpochDay()
            if (onlyIfDateChanged && repo.getLastWidgetRenderEpochDay() == todayEpoch) {
                scheduleMidnightUpdate(context, assumeHasWidgets = true)
                return
            }

            val batch = loadWidgetBatchData(context, repo, today)
            for (id in ids) {
                updateWidget(context, manager, id, batch)
            }
            repo.setLastWidgetRenderEpochDay(todayEpoch)
            scheduleMidnightUpdate(context, assumeHasWidgets = true)
        }

        private fun midnightPendingIntent(context: Context, flags: Int): PendingIntent? {
            val intent = Intent(context, CycleWidgetProvider::class.java).apply {
                action = ACTION_MIDNIGHT_UPDATE
            }
            return PendingIntent.getBroadcast(
                context,
                MIDNIGHT_REQUEST_CODE,
                intent,
                flags
            )
        }

        fun scheduleMidnightUpdate(context: Context, assumeHasWidgets: Boolean = false) {
            if (!assumeHasWidgets) {
                val manager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, CycleWidgetProvider::class.java)
                if (manager.getAppWidgetIds(componentName).isEmpty()) return
            }

            val nextMidnightMillis = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val pi = midnightPendingIntent(
                context,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC, nextMidnightMillis, pi)
        }

        private fun cancelMidnightUpdate(context: Context) {
            val existing = midnightPendingIntent(
                context,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(existing)
            existing.cancel()
        }

        private fun loadWidgetBatchData(
            context: Context,
            repo: CycleRepository = CycleRepository(context),
            today: LocalDate = LocalDate.now()
        ): WidgetBatchData {
            val starts = repo.getPeriodStarts()
            val settings = repo.getSettings()
            val model = CycleCalculator.computeModel(starts)
            val info = CycleCalculator.classifyDate(
                date = today,
                periodStarts = starts,
                model = model,
                periodLength = settings.periodLength
            )
            val cycleColors = CyclePalette.computeCycleDayArgbColors(starts, model, settings.periodLength)

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

            val style = when {
                info.isLoggedPeriod -> WidgetStyle(
                    textColor = CyclePalette.ARGB_WIDGET_TEXT_PERIOD,
                    footerColor = CyclePalette.ARGB_WIDGET_FOOTER_PERIOD,
                    statusLabel = "Period"
                )
                info.isPredictedPeriod -> WidgetStyle(
                    textColor = CyclePalette.ARGB_WIDGET_TEXT_LIKELY,
                    footerColor = CyclePalette.ARGB_WIDGET_FOOTER_LIKELY,
                    statusLabel = "Likely Period"
                )
                info.fertility == FertilityStatus.FERTILE -> WidgetStyle(
                    textColor = CyclePalette.ARGB_GREEN_BRIGHT,
                    footerColor = CyclePalette.ARGB_WIDGET_FOOTER_FERTILE,
                    statusLabel = "Fertile"
                )
                info.fertility == FertilityStatus.COULD_BE_FERTILE -> WidgetStyle(
                    textColor = CyclePalette.ARGB_YELLOW_COULD_BE,
                    footerColor = CyclePalette.ARGB_WIDGET_FOOTER_COULD_BE,
                    statusLabel = "Could Be Fertile"
                )
                else -> WidgetStyle(
                    textColor = CyclePalette.ARGB_BLUE_NOT_FERTILE,
                    footerColor = CyclePalette.ARGB_WIDGET_FOOTER_NOT_FERTILE,
                    statusLabel = "Not Fertile"
                )
            }

            return WidgetBatchData(
                repo = repo,
                today = today,
                model = model,
                info = info,
                cycleColors = cycleColors,
                style = style,
                launchPendingIntent = pendingIntent
            )
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            batch: WidgetBatchData,
            optionsOverride: Bundle? = null
        ) {
            val options = optionsOverride ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val isCompact1x1 = minWidthDp < 110 || minHeightDp < 90

            val model = batch.model
            val info = batch.info
            val style = batch.style

            val views = RemoteViews(context.packageName, R.layout.widget_cycle)
            views.setOnClickPendingIntent(R.id.widget_root, batch.launchPendingIntent)

            val portraitWidthDp = max(minWidthDp, 56)
            val portraitHeightDp = max(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp),
                max(minHeightDp, 56)
            )

            val bgBitmap = renderWidgetBackgroundBitmap(
                context = context,
                widthDp = portraitWidthDp,
                heightDp = portraitHeightDp,
                isCompact = isCompact1x1,
                accentColor = style.textColor,
                cycleColors = batch.cycleColors,
                todayInfo = info
            )
            views.setImageViewBitmap(R.id.widget_bg_image, bgBitmap)

            views.setTextColor(R.id.widget_status_badge, style.textColor)
            views.setTextColor(R.id.widget_days_number, style.textColor)
            views.setTextColor(R.id.widget_days_unit, style.textColor)
            views.setTextColor(R.id.widget_compact_days_number, style.textColor)
            views.setTextColor(R.id.widget_compact_unit, style.textColor)
            views.setTextColor(R.id.widget_subtitle, style.textColor)
            views.setTextColor(R.id.widget_footer, style.footerColor)

            // Adapt layout density for 1x1 (auto-sizing compact text) vs larger sizes
            val metrics = context.resources.displayMetrics
            if (isCompact1x1) {
                val padPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 10f, metrics
                ).toInt()
                views.setViewPadding(R.id.widget_content, padPx, padPx, padPx, padPx)
                views.setViewVisibility(R.id.widget_status_badge, View.GONE)
                views.setViewVisibility(R.id.widget_full_days_row, View.GONE)
                views.setViewVisibility(R.id.widget_compact_days_number, View.VISIBLE)
                views.setViewVisibility(R.id.widget_compact_unit, View.VISIBLE)
                views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                views.setViewVisibility(R.id.widget_footer, View.GONE)
            } else {
                val padPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 20f, metrics
                ).toInt()
                views.setViewPadding(R.id.widget_content, padPx, padPx, padPx, padPx)
                views.setViewVisibility(R.id.widget_status_badge, View.VISIBLE)
                views.setViewVisibility(R.id.widget_full_days_row, View.VISIBLE)
                views.setViewVisibility(R.id.widget_compact_days_number, View.GONE)
                views.setViewVisibility(R.id.widget_compact_unit, View.GONE)
                views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
                views.setTextViewTextSize(R.id.widget_days_number, TypedValue.COMPLEX_UNIT_SP, 42f)
                views.setTextViewTextSize(R.id.widget_days_unit, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setTextViewTextSize(R.id.widget_subtitle, TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            val badgeText: String
            val numberText: String
            val unitText: String
            val compactUnitText: String
            val subtitleText: String
            val footerText: String

            if (!model.hasData || info.daysToNextPeriod == null || info.nextPeriodDate == null) {
                badgeText = style.statusLabel
                numberText = "--"
                unitText = "days"
                compactUnitText = "Days"
                subtitleText = "to next period"
                footerText = "Tap to log period"
            } else if (info.daysLate > 0) {
                val late = info.daysLate
                badgeText = "Period Late"
                numberText = late.toString()
                unitText = if (late == 1) "day" else "days"
                compactUnitText = if (late == 1) "Day late" else "Days late"
                subtitleText = "late"
                val expectedStr = info.expectedPeriodDate?.format(CyclePalette.SHORT_DATE_FMT)
                    ?: info.nextPeriodDate.format(CyclePalette.SHORT_DATE_FMT)
                footerText = "Day ${model.averageCycleLength + late} • Expected $expectedStr"
            } else {
                val days = info.daysToNextPeriod
                badgeText = style.statusLabel
                numberText = days.toString()
                unitText = if (days == 1) "day" else "days"
                compactUnitText = if (days == 1) "Day" else "Days"
                subtitleText = if (days == 0) "period expected today" else "to next period"
                footerText = "Day ${info.cycleDay} • Next ${info.nextPeriodDate.format(CyclePalette.SHORT_DATE_FMT)}"
            }

            views.setTextViewText(R.id.widget_status_badge, badgeText)
            if (isCompact1x1) {
                views.setTextViewText(R.id.widget_compact_days_number, numberText)
                views.setTextViewText(R.id.widget_compact_unit, compactUnitText)
            } else {
                views.setTextViewText(R.id.widget_days_number, numberText)
                views.setTextViewText(R.id.widget_days_unit, unitText)
                views.setTextViewText(R.id.widget_subtitle, subtitleText)
                views.setTextViewText(R.id.widget_footer, footerText)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            bgBitmap.recycle()
        }

        private fun renderWidgetBackgroundBitmap(
            context: Context,
            widthDp: Int,
            heightDp: Int,
            isCompact: Boolean,
            accentColor: Int,
            cycleColors: IntArray,
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
            val haloR = (if (isCompact) 5.5f else 8.5f) * dp
            val dotR = (if (isCompact) 3.5f else 5.5f) * dp
            val cardInset = 1.5f * dp
            // Keep the ring concentrically inset from cardRect by at least haloR + 0.5dp
            // so the full day marker halo never clips at straight edges or rounded corners.
            val ringInset = max(
                cardInset + haloR + 0.5f * dp,
                cardInset + strokeWidth / 2f + (if (isCompact) 2f else 3f) * dp
            )

            val cardCorner = min(
                (if (isCompact) 18f else 28f) * dp,
                (min(w, h) - cardInset * 2f) / 2f
            )
            val ringCorner = max(
                4f * dp,
                cardCorner - (ringInset - cardInset)
            )

            // 1. Diagonal gradient rounded card fill + frosted glass sheen + faint wave silhouette
            val cardRect = RectF(cardInset, cardInset, w - cardInset, h - cardInset)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    cardRect.left, cardRect.top,
                    cardRect.right, cardRect.bottom,
                    intArrayOf(
                        CyclePalette.ARGB_WIDGET_BG_START,
                        CyclePalette.ARGB_WIDGET_BG_MID,
                        CyclePalette.ARGB_WIDGET_BG_END
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cardCorner, cardCorner, fillPaint)

            // Subtle translucent tide silhouette clipped inside the card for visual kinship with the app
            val cardClip = Path().apply {
                addRoundRect(cardRect, cardCorner, cardCorner, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(cardClip)
            drawSubtleWaveSilhouette(canvas, cardRect, accentColor, isCompact, dp)
            canvas.restore()

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
            canvas.drawRoundRect(cardRect, cardCorner, cardCorner, sheenPaint)

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
            canvas.drawRoundRect(borderRect, cardCorner, cardCorner, glassBorderPaint)

            // 2. If cycle data exists, draw the cycle-colored perimeter outline starting at top-center
            if (cycleColors.isNotEmpty()) {
                val left = ringInset
                val top = ringInset
                val right = w - ringInset
                val bottom = h - ringInset
                val midX = w / 2f

                val arcRect = RectF()
                val perimeter = Path().apply {
                    moveTo(midX, top)
                    lineTo(right - ringCorner, top)
                    arcRect.set(right - 2 * ringCorner, top, right, top + 2 * ringCorner)
                    arcTo(arcRect, -90f, 90f, false)
                    lineTo(right, bottom - ringCorner)
                    arcRect.set(right - 2 * ringCorner, bottom - 2 * ringCorner, right, bottom)
                    arcTo(arcRect, 0f, 90f, false)
                    lineTo(left + ringCorner, bottom)
                    arcRect.set(left, bottom - 2 * ringCorner, left + 2 * ringCorner, bottom)
                    arcTo(arcRect, 90f, 90f, false)
                    lineTo(left, top + ringCorner)
                    arcRect.set(left, top, left + 2 * ringCorner, top + 2 * ringCorner)
                    arcTo(arcRect, 180f, 90f, false)
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
                val totalSteps = count * 8
                for (step in 0 until totalSteps) {
                    val d0 = totalLength * step / totalSteps
                    val d1 = min(totalLength, totalLength * (step + 1) / totalSteps + 0.6f)
                    val dayPos = count * (step + 0.5f) / totalSteps
                    segPath.reset()
                    pm.getSegment(d0, d1, segPath, true)
                    segPaint.color = CyclePalette.sampleSmoothCycleColorArgb(cycleColors, dayPos)
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

                // Highlight current cycle day on the outline (opaque halo + deep solid dot)
                val currentDay = todayInfo.cycleDay
                if (currentDay != null) {
                    val clampedIndex = ((currentDay - 1) % count + count) % count
                    val midDist = totalLength * (clampedIndex + 0.5f) / count
                    val pos = FloatArray(2)
                    pm.getPosTan(midDist, pos, null)

                    val packed = CyclePalette.markerColorsArgb(cycleColors[clampedIndex])
                    val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                    }
                    markerPaint.color = CyclePalette.unpackHalo(packed)
                    canvas.drawCircle(pos[0], pos[1], haloR, markerPaint)
                    markerPaint.color = CyclePalette.unpackDot(packed)
                    canvas.drawCircle(pos[0], pos[1], dotR, markerPaint)
                }
            }

            return bitmap
        }

        private fun drawSubtleWaveSilhouette(
            canvas: Canvas,
            cardRect: RectF,
            accentColor: Int,
            isCompact: Boolean,
            dp: Float
        ) {
            val w = cardRect.width()
            val h = cardRect.height()
            if (w <= 0f || h <= 0f) return

            val amp = (if (isCompact) 4.5f else 7.5f) * dp
            val amp2 = amp * 0.45f
            val freq1 = (2.0 * PI / (w * 0.85f)).toFloat()
            val freq2 = (2.0 * PI / (w * 1.7f)).toFloat()
            val stepPx = max(4f, 6f * dp)

            val base1 = cardRect.top + h * 0.72f
            val base2 = cardRect.top + h * 0.81f
            val path1 = Path().apply { moveTo(cardRect.left, cardRect.bottom) }
            val path2 = Path().apply { moveTo(cardRect.left, cardRect.bottom) }

            var x = 0f
            while (x < w) {
                val sec = cos(x * freq2) * amp2
                path1.lineTo(cardRect.left + x, base1 + sin(x * freq1 + 0.6f) * amp + sec)
                path2.lineTo(cardRect.left + x, base2 + sin(x * freq1 + 2.4f) * amp + sec)
                x += stepPx
            }
            val endSec = cos(w * freq2) * amp2
            path1.lineTo(cardRect.right, base1 + sin(w * freq1 + 0.6f) * amp + endSec)
            path1.lineTo(cardRect.right, cardRect.bottom)
            path1.close()
            path2.lineTo(cardRect.right, base2 + sin(w * freq1 + 2.4f) * amp + endSec)
            path2.lineTo(cardRect.right, cardRect.bottom)
            path2.close()

            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val r = Color.red(accentColor)
            val g = Color.green(accentColor)
            val b = Color.blue(accentColor)
            wavePaint.color = Color.argb(22, r, g, b)
            canvas.drawPath(path1, wavePaint)
            wavePaint.color = Color.argb(14, r, g, b)
            canvas.drawPath(path2, wavePaint)
        }

        private data class WidgetStyle(
            val textColor: Int,
            val footerColor: Int,
            val statusLabel: String
        )

        private class WidgetBatchData(
            val repo: CycleRepository,
            val today: LocalDate,
            val model: CycleModel,
            val info: DayClassification,
            val cycleColors: IntArray,
            val style: WidgetStyle,
            val launchPendingIntent: PendingIntent
        )
    }
}
