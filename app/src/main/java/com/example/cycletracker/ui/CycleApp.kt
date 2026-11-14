package com.example.cycletracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cycletracker.R
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.domain.CycleModel
import com.example.cycletracker.domain.DayClassification
import com.example.cycletracker.domain.FertilityStatus
import com.example.cycletracker.domain.PeriodStatus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val GreenFertile = Color(0xFF059669)
private val YellowCouldBe = Color(0xFFFBBF24)
private val BlueNotFertile = Color(0xFF38BDF8)
private val RedPeriod = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleApp(viewModel: CycleViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now()
    val isTodayLogged = state.periodStarts.contains(today)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "Maya logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF8F6))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Maya",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (state.periodStarts.isEmpty()) {
                        TextButton(onClick = { viewModel.loadSampleHistory() }) {
                            Text("Sample")
                        }
                    } else {
                        TextButton(onClick = { viewModel.clearAll() }) {
                            Text("Reset")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.togglePeriodStart(today) },
                containerColor = if (isTodayLogged) GreenFertile else RedPeriod,
                contentColor = Color.White,
                icon = {
                    Icon(
                        imageVector = if (isTodayLogged) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = if (isTodayLogged) "Logged Today" else "Period Today",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Home Screen Widget Preview
            HomeWidgetPreviewCard(
                todayInfo = state.todayClassification,
                hasData = state.model.hasData,
                periodStarts = state.periodStarts,
                model = state.model
            )

            // 2. Interactive Calendar
            CalendarCard(
                month = state.currentMonth,
                selectedDate = state.selectedDate,
                periodStarts = state.periodStarts,
                model = state.model,
                onPrevMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onToday = viewModel::goToToday,
                onSelectDate = viewModel::selectDate
            )

            // 3. Selected Day Action Bar
            SelectedDayCard(
                selectedDate = state.selectedDate,
                periodStarts = state.periodStarts,
                model = state.model,
                onTogglePeriodStart = { viewModel.togglePeriodStart(state.selectedDate) }
            )

            // 4. Adaptive Cycle Insights
            InsightsCard(model = state.model)

            // 5. Period History
            HistoryCard(
                periodStarts = state.periodStarts,
                onRemove = viewModel::removePeriodStart
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HomeWidgetPreviewCard(
    todayInfo: DayClassification,
    hasData: Boolean,
    periodStarts: List<LocalDate>,
    model: CycleModel
) {
    val gradColors: List<Color>
    val contentColor: Color
    val statusText: String

    when (todayInfo.fertility) {
        FertilityStatus.FERTILE -> {
            gradColors = listOf(Color(0xFF10B981), GreenFertile, Color(0xFF047857))
            contentColor = Color.White
            statusText = "Fertile"
        }
        FertilityStatus.COULD_BE_FERTILE -> {
            gradColors = listOf(Color(0xFFFDE047), YellowCouldBe, Color(0xFFF59E0B))
            contentColor = Color(0xFF451A03)
            statusText = "Could Be Fertile"
        }
        FertilityStatus.NOT_FERTILE -> {
            gradColors = listOf(Color(0xFF7DD3FC), BlueNotFertile, Color(0xFF0EA5E9))
            contentColor = Color(0xFF082F49)
            statusText = "Not Fertile"
        }
    }

    val cycleColors: List<Color> = if (hasData && periodStarts.isNotEmpty()) {
        val cycleStart = periodStarts.sorted().last()
        val cycleLen = model.averageCycleLength.coerceAtLeast(1)
        (0 until cycleLen).map { offset ->
            val dayInfo = CycleCalculator.classifyDate(
                date = cycleStart.plusDays(offset.toLong()),
                periodStarts = periodStarts,
                model = model
            )
            when {
                dayInfo.periodStatus == PeriodStatus.LOGGED_START ||
                    dayInfo.periodStatus == PeriodStatus.LOGGED -> RedPeriod
                dayInfo.fertility == FertilityStatus.FERTILE -> GreenFertile
                dayInfo.fertility == FertilityStatus.COULD_BE_FERTILE -> YellowCouldBe
                else -> BlueNotFertile
            }
        }
    } else {
        emptyList()
    }

    val ringBackingColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(colors = gradColors))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.48f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = contentColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = statusText,
                            color = contentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "Home Widget",
                        color = contentColor.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (hasData && todayInfo.daysToNextPeriod != null) {
                            todayInfo.daysToNextPeriod.toString()
                        } else "--",
                        color = contentColor,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 44.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (todayInfo.daysToNextPeriod == 1) "day" else "days",
                        color = contentColor.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Text(
                    text = when {
                        !hasData -> "Log your last period start to begin"
                        todayInfo.daysToNextPeriod == 0 -> "Period expected today"
                        else -> "to next period"
                    },
                    color = contentColor.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                if (hasData && todayInfo.nextPeriodDate != null) {
                    Spacer(Modifier.height(12.dp))
                    val fmt = DateTimeFormatter.ofPattern("MMM d")
                    Text(
                        text = "Cycle Day ${todayInfo.cycleDay}  •  Next period: ${todayInfo.nextPeriodDate.format(fmt)}",
                        color = contentColor.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (cycleColors.isNotEmpty()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidth = 6.dp.toPx()
                val inset = strokeWidth / 2f
                val cornerRadius = 28.dp.toPx().coerceAtMost(
                    (size.minDimension - strokeWidth) / 2f
                )
                val left = inset
                val top = inset
                val right = size.width - inset
                val bottom = size.height - inset
                val midX = size.width / 2f

                // Build rounded-rectangle perimeter starting at top-center (period start) clockwise
                val perimeter = Path().apply {
                    moveTo(midX, top)
                    lineTo(right - cornerRadius, top)
                    arcTo(
                        rect = Rect(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(right, bottom - cornerRadius)
                    arcTo(
                        rect = Rect(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(left + cornerRadius, bottom)
                    arcTo(
                        rect = Rect(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(left, top + cornerRadius)
                    arcTo(
                        rect = Rect(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(midX, top)
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(perimeter, forceClosed = false)
                val totalLength = pathMeasure.length
                val count = cycleColors.size
                val segmentPath = Path()

                // Subtle backing track so colored segments pop against the card edge
                drawPath(
                    path = perimeter,
                    color = ringBackingColor,
                    style = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Butt)
                )

                cycleColors.forEachIndexed { index, color ->
                    val startDist = totalLength * index / count
                    val endDist = totalLength * (index + 1) / count
                    segmentPath.reset()
                    pathMeasure.getSegment(startDist, endDist, segmentPath, startWithMoveTo = true)
                    drawPath(
                        path = segmentPath,
                        color = color,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }

                // Top-center tick marking Cycle Day 1 (period start)
                drawLine(
                    color = Color.White,
                    start = Offset(midX, top - strokeWidth * 0.6f),
                    end = Offset(midX, top + strokeWidth * 0.6f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Highlight current cycle day on the outline
                val currentDay = todayInfo.cycleDay
                if (currentDay != null) {
                    val clampedIndex = ((currentDay - 1) % count + count) % count
                    val midDist = totalLength * (clampedIndex + 0.5f) / count
                    val markerPos = pathMeasure.getPosition(midDist)
                    drawCircle(
                        color = Color.White,
                        radius = 8.5.dp.toPx(),
                        center = markerPos
                    )
                    drawCircle(
                        color = Color(0xFF111827),
                        radius = 6.dp.toPx(),
                        center = markerPos
                    )
                    drawCircle(
                        color = cycleColors[clampedIndex],
                        radius = 3.5.dp.toPx(),
                        center = markerPos
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    periodStarts: List<LocalDate>,
    model: CycleModel,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val currentMonth = remember { YearMonth.from(today) }
    val monthsPast = 12
    val monthsFuture = 12
    val months = remember(currentMonth) {
        (-monthsPast..monthsFuture).map { offset -> currentMonth.plusMonths(offset.toLong()) }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = monthsPast)
    val coroutineScope = rememberCoroutineScope()
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = {
                        onToday()
                        coroutineScope.launch {
                            listState.animateScrollToItem(monthsPast)
                        }
                    }
                ) {
                    Text("Today", fontSize = 12.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
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

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(370.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(
                    items = months,
                    key = { it.toString() }
                ) { itemMonth ->
                    MonthSection(
                        month = itemMonth,
                        monthFormatter = monthFormatter,
                        today = today,
                        selectedDate = selectedDate,
                        periodStarts = periodStarts,
                        model = model,
                        onSelectDate = onSelectDate
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LegendItem(GreenFertile, "Fertile")
                LegendItem(YellowCouldBe, "Could be fertile")
                LegendItem(BlueNotFertile, "Not fertile")
                LegendItem(RedPeriod, "Period / Likely next")
            }
        }
    }
}

@Composable
private fun MonthSection(
    month: YearMonth,
    monthFormatter: DateTimeFormatter,
    today: LocalDate,
    selectedDate: LocalDate,
    periodStarts: List<LocalDate>,
    model: CycleModel,
    onSelectDate: (LocalDate) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    // Monday = 0 .. Sunday = 6
    val leadingEmpty = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingEmpty + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Text(
            text = month.format(monthFormatter),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        val dayNum = cellIndex - leadingEmpty + 1
                        if (dayNum < 1 || dayNum > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.atDay(dayNum)
                            val info = CycleCalculator.classifyDate(date, periodStarts, model)
                            val isSelected = date == selectedDate
                            val isToday = date == today

                            val (cellBg, cellBorder, cellText) = when {
                                !model.hasData -> Triple(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.colorScheme.onSurface
                                )
                                info.periodStatus == PeriodStatus.LOGGED_START ||
                                    info.periodStatus == PeriodStatus.LOGGED -> Triple(
                                    RedPeriod,
                                    Color(0xFF991B1B),
                                    Color.White
                                )
                                info.periodStatus == PeriodStatus.PREDICTED_START ||
                                    info.periodStatus == PeriodStatus.PREDICTED -> Triple(
                                    Color(0xFFFCA5A5),
                                    RedPeriod,
                                    Color(0xFF7F1D1D)
                                )
                                info.fertility == FertilityStatus.FERTILE -> Triple(
                                    GreenFertile,
                                    Color(0xFF047857),
                                    Color.White
                                )
                                info.fertility == FertilityStatus.COULD_BE_FERTILE -> Triple(
                                    YellowCouldBe,
                                    Color(0xFFD97706),
                                    Color(0xFF451A03)
                                )
                                else -> Triple(
                                    BlueNotFertile,
                                    Color(0xFF0284C7),
                                    Color(0xFF082F49)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cellBg)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else cellBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelectDate(date) }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = cellText,
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(cellText)
                                            .align(Alignment.BottomEnd)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedDayCard(
    selectedDate: LocalDate,
    periodStarts: List<LocalDate>,
    model: CycleModel,
    onTogglePeriodStart: () -> Unit
) {
    val info = CycleCalculator.classifyDate(selectedDate, periodStarts, model)
    val isLoggedStart = periodStarts.contains(selectedDate)
    val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedDate.format(dateFmt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                val statusLabel = when {
                    !model.hasData -> "Tap 'Period Started' to begin tracking"
                    info.periodStatus == PeriodStatus.LOGGED_START -> "Period Start • Cycle Day ${info.cycleDay}"
                    info.periodStatus == PeriodStatus.LOGGED -> "Period • Cycle Day ${info.cycleDay}"
                    info.periodStatus == PeriodStatus.PREDICTED_START ||
                        info.periodStatus == PeriodStatus.PREDICTED -> "Likely Next Period • Cycle Day ${info.cycleDay}"
                    info.fertility == FertilityStatus.FERTILE -> "Fertile • Cycle Day ${info.cycleDay}"
                    info.fertility == FertilityStatus.COULD_BE_FERTILE -> "Could Be Fertile • Cycle Day ${info.cycleDay}"
                    else -> "Not Fertile • Cycle Day ${info.cycleDay}"
                }
                Text(
                    text = statusLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            if (isLoggedStart) {
                OutlinedButton(onClick = onTogglePeriodStart) {
                    Text("Remove", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onTogglePeriodStart,
                    colors = ButtonDefaults.buttonColors(containerColor = RedPeriod, contentColor = Color.White)
                ) {
                    Text("Period Started", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(model: CycleModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cycle Insights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (model.cyclesUsed == 0) "Default (28d)" else "Adapted (${model.cyclesUsed} cycles)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Avg Cycle", "${model.averageCycleLength}d", Modifier.weight(1f))
                StatTile("Variation", "±${model.variabilityDays}d", Modifier.weight(1f))
                StatTile("Ovulation", "Day ${model.ovulationDay}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryCard(
    periodStarts: List<LocalDate>,
    onRemove: (LocalDate) -> Unit
) {
    val sortedDesc = periodStarts.sortedDescending()
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Period History (${sortedDesc.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            if (sortedDesc.isEmpty()) {
                Text(
                    "No period start dates logged yet. Tap any calendar day to log one.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sortedDesc.forEachIndexed { index, date ->
                    val cycleLen = if (index > 0) {
                        ChronoUnit.DAYS.between(date, sortedDesc[index - 1]).toInt()
                    } else null

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(date.format(fmt), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (cycleLen != null) {
                                Text(
                                    "$cycleLen day cycle",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { onRemove(date) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
