package com.example.cycletracker.ui

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.domain.CycleModel
import com.example.cycletracker.domain.DayClassification
import com.example.cycletracker.domain.FertilityStatus
import com.example.cycletracker.domain.PeriodStatus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private val DaysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val CalendarCardShape = RoundedCornerShape(20.dp)
private val CellShapePortrait = RoundedCornerShape(10.dp)
private val CellShapeLandscape = RoundedCornerShape(8.dp)
private val TodayPillShape = RoundedCornerShape(50)

private val DayTextStyleNormal = TextStyle(
    fontSize = 12.5.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum"
)
private val DayTextStyleToday = TextStyle(
    fontSize = 12.5.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum"
)

private val PredictedDashIntervals = floatArrayOf(10f, 7f)

private data class DayCellVisual(
    val date: LocalDate,
    val dayNum: Int,
    val bgColor: Color,
    val bgBrush: Brush?,
    val borderColor: Color,
    val textColor: Color,
    val isPredictedPeriod: Boolean,
    val dropletMode: DropletMode
)

private enum class DropletMode {
    NONE,
    SOLID,
    OUTLINE
}

@Composable
internal fun CalendarCard(
    modifier: Modifier = Modifier,
    today: LocalDate,
    selectedDate: LocalDate,
    periodStarts: List<LocalDate>,
    model: CycleModel,
    periodLength: Int,
    scrollToDateRequest: LocalDate?,
    onScrollRequestConsumed: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    isCompactLandscape: Boolean = false
) {
    val currentMonth = remember(today) { YearMonth.from(today) }
    val monthsPast = 120
    val monthsFuture = 12
    val months = remember(currentMonth) {
        (-monthsPast..monthsFuture).map { offset -> currentMonth.plusMonths(offset.toLong()) }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = monthsPast)
    val coroutineScope = rememberCoroutineScope()

    val visibleMonthLabel by remember(months) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex.coerceIn(0, months.lastIndex)
            months[idx].format(CyclePalette.MONTH_YEAR_FMT)
        }
    }

    // Jump straight to a requested date (e.g. tapped from HistoryScreen)
    LaunchedEffect(scrollToDateRequest, months) {
        val target = scrollToDateRequest ?: return@LaunchedEffect
        val targetMonth = YearMonth.from(target)
        val targetIndex = months.indexOf(targetMonth)
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
        onScrollRequestConsumed()
    }

    val selectedMonth = YearMonth.from(selectedDate)
    val todayMonth = YearMonth.from(today)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CalendarCardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isCompactLandscape) 12.dp else 14.dp,
                    vertical = if (isCompactLandscape) 8.dp else 10.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = visibleMonthLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val todayButtonBg = rememberWallpaperAccentColor(MaterialTheme.colorScheme.primary)
                val todayButtonFg = if (todayButtonBg.luminance() > 0.5f) Color(0xFF111827) else Color.White
                Surface(
                    onClick = {
                        onToday()
                        coroutineScope.launch {
                            if (isCompactLandscape) {
                                val viewportH = listState.layoutInfo.viewportSize.height
                                val monthH = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == monthsPast }?.size
                                    ?: listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size
                                    ?: viewportH
                                val leadingEmpty = currentMonth.atDay(1).dayOfWeek.value - 1
                                val totalRows = (leadingEmpty + currentMonth.lengthOfMonth() + 6) / 7
                                val todayRow = (leadingEmpty + today.dayOfMonth - 1) / 7
                                val headerFrac = 0.12f
                                val rowCenterY = monthH * (
                                    headerFrac + (1f - headerFrac) * ((todayRow + 0.5f) / totalRows)
                                )
                                val scrollOffset = (rowCenterY - viewportH * 0.58f).toInt()
                                listState.animateScrollToItem(monthsPast, scrollOffset)
                            } else {
                                listState.animateScrollToItem(monthsPast)
                            }
                        }
                    },
                    shape = TodayPillShape,
                    color = todayButtonBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = MayaIcons.Today,
                            contentDescription = null,
                            tint = todayButtonFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Today",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = todayButtonFg
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                DaysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(if (isCompactLandscape) 2.dp else 4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            val listBottomPadding = if (isCompactLandscape) {
                72.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            } else {
                4.dp
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 6.dp else 10.dp)
            ) {
                items(
                    items = months,
                    key = { it.year * 12 + it.monthValue }
                ) { itemMonth ->
                    MonthSection(
                        month = itemMonth,
                        todayDayOfMonth = if (itemMonth == todayMonth) today.dayOfMonth else null,
                        selectedDayOfMonth = if (itemMonth == selectedMonth) selectedDate.dayOfMonth else null,
                        periodStarts = periodStarts,
                        model = model,
                        periodLength = periodLength,
                        onSelectDate = onSelectDate,
                        isCompactLandscape = isCompactLandscape
                    )
                    Spacer(Modifier.height(if (isCompactLandscape) 4.dp else 8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }

            if (!isCompactLandscape) {
                val selectedInfo = remember(selectedDate, periodStarts, model, periodLength) {
                    CycleCalculator.classifyDate(selectedDate, periodStarts, model, periodLength)
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))

                SelectedDayStatusBlock(
                    selectedDate = selectedDate,
                    today = today,
                    selectedInfo = selectedInfo,
                    model = model,
                    centered = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun SelectedDayStatusBlock(
    selectedDate: LocalDate,
    today: LocalDate,
    selectedInfo: DayClassification,
    model: CycleModel,
    centered: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = selectedDate.format(CyclePalette.SELECTED_DATE_FMT),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (centered) FontWeight.SemiBold else FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
        Text(
            text = formatSelectedDayStatus(
                selectedDate = selectedDate,
                today = today,
                selectedInfo = selectedInfo,
                model = model
            ),
            fontSize = if (centered) 12.sp else 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
    }
}

internal fun formatSelectedDayStatus(
    selectedDate: LocalDate,
    today: LocalDate,
    selectedInfo: DayClassification,
    model: CycleModel
): String = when {
    !model.hasData -> "Tap 'Log Period' to begin"
    selectedDate == today && selectedInfo.daysLate > 0 -> {
        val unit = if (selectedInfo.daysLate == 1) "day" else "days"
        "Period ${selectedInfo.daysLate} $unit late • Day ${model.averageCycleLength + selectedInfo.daysLate}"
    }
    selectedInfo.periodStatus == PeriodStatus.LOGGED_START -> "Period Start • Day ${selectedInfo.cycleDay}"
    selectedInfo.periodStatus == PeriodStatus.LOGGED -> "Period • Day ${selectedInfo.cycleDay}"
    selectedInfo.isPredictedPeriod -> "Likely Period • Day ${selectedInfo.cycleDay}"
    selectedInfo.fertility == FertilityStatus.FERTILE -> "Fertile • Day ${selectedInfo.cycleDay}"
    selectedInfo.fertility == FertilityStatus.COULD_BE_FERTILE -> "Could Be Fertile • Day ${selectedInfo.cycleDay}"
    else -> "Not Fertile • Day ${selectedInfo.cycleDay}"
}

@Composable
private fun rememberWallpaperAccentColor(fallback: Color): Color {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accentColor by remember(context, fallback) {
        mutableStateOf(readWallpaperPrimaryColor(context) ?: fallback)
    }

    DisposableEffect(context, lifecycleOwner, fallback) {
        val updateColor = {
            accentColor = readWallpaperPrimaryColor(context) ?: fallback
        }
        var skipInitialResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipInitialResume) {
                    skipInitialResume = false
                } else {
                    updateColor()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val wm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.getInstance(context)
        } else null
        val listener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.OnColorsChangedListener { _, which ->
                if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
                    updateColor()
                }
            }
        } else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wm != null && listener != null) {
            wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wm != null && listener != null) {
                wm.removeOnColorsChangedListener(listener)
            }
        }
    }

    return accentColor
}

private fun readWallpaperPrimaryColor(context: Context): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
    val colors = WallpaperManager.getInstance(context)
        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
    val candidates = listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)
    val hsv = FloatArray(3)
    val vivid = candidates.firstOrNull { c ->
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[1] >= 0.20f && hsv[2] >= 0.15f
    } ?: colors.primaryColor
    return Color(vivid.toArgb())
}

@Composable
private fun MonthSection(
    month: YearMonth,
    todayDayOfMonth: Int?,
    selectedDayOfMonth: Int?,
    periodStarts: List<LocalDate>,
    model: CycleModel,
    periodLength: Int,
    onSelectDate: (LocalDate) -> Unit,
    isCompactLandscape: Boolean = false
) {
    val firstOfMonth = month.atDay(1)
    val leadingEmpty = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingEmpty + daysInMonth
    val rows = (totalCells + 6) / 7
    val cellGap = if (isCompactLandscape) 4.dp else 6.dp
    val cellAspect = if (isCompactLandscape) 1.45f else 1f
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Classify [firstOfMonth - 1 .. lastOfMonth + 1] in a SINGLE batch (32 days vs 93 calls)
    // and pre-build cell visuals once per (month, periodStarts, model, periodLength, theme).
    val dayVisuals = remember(
        month,
        periodStarts,
        model,
        periodLength,
        surfaceColor,
        outlineVariantColor,
        onSurfaceColor
    ) {
        val classifications = CycleCalculator.classifyRange(
            startDate = firstOfMonth.minusDays(1),
            dayCount = daysInMonth + 2,
            periodStarts = periodStarts,
            model = model,
            periodLength = periodLength
        )
        List(daysInMonth) { idx ->
            val info = classifications[idx + 1]
            val prevInfo = classifications[idx]
            val nextInfo = classifications[idx + 2]

            val bgColor: Color
            val borderColor: Color
            val textColor: Color
            when {
                !model.hasData -> {
                    bgColor = surfaceColor
                    borderColor = outlineVariantColor
                    textColor = onSurfaceColor
                }
                info.isLoggedPeriod -> {
                    bgColor = CyclePalette.RedPeriod
                    borderColor = Color(0xFF991B1B)
                    textColor = Color.White
                }
                info.isPredictedPeriod -> {
                    bgColor = CyclePalette.RedPredicted
                    borderColor = CyclePalette.RedPeriod
                    textColor = Color(0xFF7F1D1D)
                }
                info.fertility == FertilityStatus.FERTILE -> {
                    bgColor = CyclePalette.GreenFertile
                    borderColor = CyclePalette.GreenDark
                    textColor = Color.White
                }
                info.fertility == FertilityStatus.COULD_BE_FERTILE -> {
                    bgColor = CyclePalette.YellowCouldBe
                    borderColor = Color(0xFFD97706)
                    textColor = Color(0xFF451A03)
                }
                else -> {
                    bgColor = CyclePalette.BlueNotFertile
                    borderColor = Color(0xFF0284C7)
                    textColor = Color(0xFF082F49)
                }
            }

            // Use a solid background on ~75% of cells where neighbors match, avoiding GPU LinearGradient shaders
            val bgBrush: Brush? = if (!model.hasData) {
                null
            } else {
                val curIsPeriod = info.isAnyPeriod
                val sharpLeft = !prevInfo.isAnyPeriod && curIsPeriod
                val sharpRight = !curIsPeriod && nextInfo.isAnyPeriod
                val prevColor = calendarBaseColor(prevInfo)
                val nextColor = calendarBaseColor(nextInfo)
                val leftColor = if (sharpLeft || prevColor == bgColor) bgColor else lerpColor(bgColor, prevColor, 0.30f)
                val rightColor = if (sharpRight || nextColor == bgColor) bgColor else lerpColor(bgColor, nextColor, 0.30f)
                if (leftColor == bgColor && rightColor == bgColor) {
                    null
                } else {
                    Brush.horizontalGradient(listOf(leftColor, bgColor, rightColor))
                }
            }

            val dropletMode = when (info.periodStatus) {
                PeriodStatus.LOGGED_START -> DropletMode.SOLID
                PeriodStatus.PREDICTED_START -> DropletMode.OUTLINE
                else -> DropletMode.NONE
            }

            DayCellVisual(
                date = info.date,
                dayNum = idx + 1,
                bgColor = bgColor,
                bgBrush = bgBrush,
                borderColor = borderColor,
                textColor = textColor,
                isPredictedPeriod = model.hasData && info.isPredictedPeriod,
                dropletMode = dropletMode
            )
        }
    }

    val monthTitle = remember(month) { month.format(CyclePalette.MONTH_YEAR_FMT) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isCompactLandscape) 4.dp else 10.dp)
    ) {
        Text(
            text = monthTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = onSurfaceColor,
            modifier = Modifier.padding(bottom = if (isCompactLandscape) 4.dp else 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellGap)
                ) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - leadingEmpty + 1
                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(cellAspect))
                        } else {
                            CalendarDayCell(
                                visual = dayVisuals[dayNum - 1],
                                isSelected = dayNum == selectedDayOfMonth,
                                isToday = dayNum == todayDayOfMonth,
                                isCompactLandscape = isCompactLandscape,
                                cellAspect = cellAspect,
                                onSelectDate = onSelectDate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    visual: DayCellVisual,
    isSelected: Boolean,
    isToday: Boolean,
    isCompactLandscape: Boolean,
    cellAspect: Float,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = if (isCompactLandscape) CellShapeLandscape else CellShapePortrait
    val cornerDp = if (isCompactLandscape) 8.dp else 10.dp
    val selectionOuterColor = MaterialTheme.colorScheme.onSurface
    val selectionInnerGapColor = MaterialTheme.colorScheme.surface

    val bgModifier = if (visual.bgBrush != null) {
        Modifier.background(visual.bgBrush)
    } else {
        Modifier.background(visual.bgColor)
    }

    val borderModifier = when {
        isSelected -> {
            // Double-ring selection border (inner surface contrast ring + crisp outer stroke)
            Modifier.drawWithCache {
                val outerWidth = 2.25.dp.toPx()
                val innerWidth = 1.5.dp.toPx()
                val outerStroke = Stroke(width = outerWidth)
                val innerStroke = Stroke(width = innerWidth)
                val outerCorner = CornerRadius(cornerDp.toPx())
                val outerInset = outerWidth / 2f
                val innerInset = outerWidth + innerWidth / 2f
                val innerCorner = CornerRadius((cornerDp.toPx() - outerWidth).coerceAtLeast(2f))
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = selectionInnerGapColor,
                        topLeft = Offset(innerInset, innerInset),
                        size = Size(size.width - innerInset * 2f, size.height - innerInset * 2f),
                        cornerRadius = innerCorner,
                        style = innerStroke
                    )
                    drawRoundRect(
                        color = selectionOuterColor,
                        topLeft = Offset(outerInset, outerInset),
                        size = Size(size.width - outerInset * 2f, size.height - outerInset * 2f),
                        cornerRadius = outerCorner,
                        style = outerStroke
                    )
                }
            }
        }
        visual.isPredictedPeriod -> {
            // Subtle dashed border distinguishing predicted periods from confirmed logged periods
            Modifier.drawWithCache {
                val strokeWidth = 1.25.dp.toPx()
                val inset = strokeWidth / 2f
                val dashStroke = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(PredictedDashIntervals, 0f)
                )
                val corner = CornerRadius(cornerDp.toPx())
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = visual.borderColor,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = corner,
                        style = dashStroke
                    )
                }
            }
        }
        else -> {
            Modifier.border(width = 1.dp, color = visual.borderColor, shape = shape)
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(cellAspect)
            .clip(shape)
            .then(bgModifier)
            .then(borderModifier)
            .clickable { onSelectDate(visual.date) }
            .padding(4.dp)
    ) {
        Text(
            text = visual.dayNum.toString(),
            color = visual.textColor,
            style = if (isToday) DayTextStyleToday else DayTextStyleNormal,
            modifier = Modifier.align(Alignment.TopStart)
        )
        if (isToday) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(visual.textColor)
                    .align(Alignment.TopEnd)
            )
        }
        if (visual.dropletMode != DropletMode.NONE) {
            val isOutline = visual.dropletMode == DropletMode.OUTLINE
            val dropColor = if (isOutline) visual.textColor else Color.White
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val drop = Path().apply {
                            moveTo(w * 0.5f, 0f)
                            cubicTo(w * 0.5f, 0f, w * 0.92f, h * 0.45f, w * 0.92f, h * 0.66f)
                            cubicTo(w * 0.92f, h * 0.87f, w * 0.73f, h, w * 0.5f, h)
                            cubicTo(w * 0.27f, h, w * 0.08f, h * 0.87f, w * 0.08f, h * 0.66f)
                            cubicTo(w * 0.08f, h * 0.45f, w * 0.5f, 0f, w * 0.5f, 0f)
                            close()
                        }
                        val outlineStroke = if (isOutline) Stroke(width = 1.2.dp.toPx()) else null
                        onDrawBehind {
                            if (outlineStroke != null) {
                                drawPath(path = drop, color = dropColor, style = outlineStroke)
                            } else {
                                drawPath(path = drop, color = dropColor)
                            }
                        }
                    }
            )
        }
    }
}

private fun calendarBaseColor(info: DayClassification): Color = when {
    info.isLoggedPeriod -> CyclePalette.RedPeriod
    info.isPredictedPeriod -> CyclePalette.RedPredicted
    info.fertility == FertilityStatus.FERTILE -> CyclePalette.GreenFertile
    info.fertility == FertilityStatus.COULD_BE_FERTILE -> CyclePalette.YellowCouldBe
    else -> CyclePalette.BlueNotFertile
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = lerpFloat(a.red, b.red, t),
    green = lerpFloat(a.green, b.green, t),
    blue = lerpFloat(a.blue, b.blue, t),
    alpha = lerpFloat(a.alpha, b.alpha, t)
)
