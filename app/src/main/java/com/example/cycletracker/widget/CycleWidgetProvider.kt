package com.example.cycletracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.cycletracker.MainActivity
import com.example.cycletracker.R
import com.example.cycletracker.data.CycleRepository
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.domain.FertilityStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

            // Color the widget: Green = Fertile, Yellow = Could be fertile, Red = Not fertile
            val (bgRes, textColor, footerColor, statusLabel) = when (info.fertility) {
                FertilityStatus.FERTILE -> WidgetStyle(
                    R.drawable.widget_bg_green,
                    Color.WHITE,
                    Color.parseColor("#D1FAE5"),
                    "Fertile"
                )
                FertilityStatus.COULD_BE_FERTILE -> WidgetStyle(
                    R.drawable.widget_bg_yellow,
                    Color.parseColor("#451A03"),
                    Color.parseColor("#78350F"),
                    "Could Be Fertile"
                )
                FertilityStatus.NOT_FERTILE -> WidgetStyle(
                    R.drawable.widget_bg_blue,
                    Color.parseColor("#082F49"),
                    Color.parseColor("#0369A1"),
                    "Not Fertile"
                )
            }

            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            views.setTextViewText(R.id.widget_status_badge, statusLabel)
            views.setTextColor(R.id.widget_status_badge, textColor)
            views.setTextColor(R.id.widget_days_number, textColor)
            views.setTextColor(R.id.widget_days_unit, textColor)
            views.setTextColor(R.id.widget_subtitle, textColor)
            views.setTextColor(R.id.widget_footer, footerColor)

            // Adapt layout density for 1x1 vs larger sizes
            if (isCompact1x1) {
                val padPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 6f, context.resources.displayMetrics
                ).toInt()
                views.setViewPadding(R.id.widget_root, padPx, padPx, padPx, padPx)
                views.setViewVisibility(R.id.widget_status_badge, View.GONE)
                views.setViewVisibility(R.id.widget_days_unit, View.GONE)
                views.setViewVisibility(R.id.widget_footer, View.GONE)
                views.setTextViewTextSize(R.id.widget_days_number, TypedValue.COMPLEX_UNIT_SP, 22f)
                views.setTextViewTextSize(R.id.widget_subtitle, TypedValue.COMPLEX_UNIT_SP, 10f)
            } else {
                val padPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 14f, context.resources.displayMetrics
                ).toInt()
                views.setViewPadding(R.id.widget_root, padPx, padPx, padPx, padPx)
                views.setViewVisibility(R.id.widget_status_badge, View.VISIBLE)
                views.setViewVisibility(R.id.widget_days_unit, View.VISIBLE)
                views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
                views.setTextViewTextSize(R.id.widget_days_number, TypedValue.COMPLEX_UNIT_SP, 34f)
                views.setTextViewTextSize(R.id.widget_subtitle, TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            if (!model.hasData || info.daysToNextPeriod == null || info.nextPeriodDate == null) {
                views.setTextViewText(R.id.widget_days_number, "--")
                views.setTextViewText(R.id.widget_days_unit, "days")
                views.setTextViewText(
                    R.id.widget_subtitle,
                    if (isCompact1x1) "log period" else "to next period"
                )
                views.setTextViewText(R.id.widget_footer, "Tap to log period")
            } else {
                val days = info.daysToNextPeriod
                views.setTextViewText(R.id.widget_days_number, days.toString())
                views.setTextViewText(R.id.widget_days_unit, if (days == 1) "day" else "days")
                views.setTextViewText(
                    R.id.widget_subtitle,
                    when {
                        isCompact1x1 -> if (days == 0) "today" else "d to period"
                        days == 0 -> "period expected today"
                        else -> "to next period"
                    }
                )
                views.setTextViewText(
                    R.id.widget_footer,
                    "Day ${info.cycleDay} • Next ${info.nextPeriodDate.format(shortDateFmt)}"
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private data class WidgetStyle(
            val bgRes: Int,
            val textColor: Int,
            val footerColor: Int,
            val statusLabel: String
        )
    }
}
