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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.lerp
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
    val isSelectedLogged = state.periodStarts.contains(state.selectedDate)
    var showDetailsSheet by remember { mutableStateOf(false) }

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
                    }
                    IconButton(onClick = { showDetailsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Cycle Insights & History"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.togglePeriodStart(state.selectedDate) },
                containerColor = if (isSelectedLogged) GreenFertile else RedPeriod,
                contentColor = Color.White,
                icon = {
                    Icon(
                        imageVector = if (isSelectedLogged) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = if (isSelectedLogged) "Remove Period" else "Log Period",
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
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Live Home Screen Widget Preview
            HomeWidgetPreviewCard(
                todayInfo = state.todayClassification,
                hasData = state.model.hasData,
                periodStarts = state.periodStarts,
                model = state.model
            )

            // 2. Merged Interactive Calendar + Selected Day Section (fills remaining height)
            CalendarCard(
                modifier = Modifier.weight(1f),
                selectedDate = state.selectedDate,
                periodStarts = state.periodStarts,
                model = state.model,
                onToday = viewModel::goToToday,
                onSelectDate = viewModel::selectDate
            )
        }

        if (showDetailsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDetailsSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InsightsCard(model = state.model)
                    HistoryCard(
                        periodStarts = state.periodStarts,
                        onRemove = viewModel::removePeriodStart
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
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

    when {
        todayInfo.periodStatus == PeriodStatus.LOGGED_START ||
            todayInfo.periodStatus == PeriodStatus.LOGGED -> {
            gradColors = listOf(Color(0xFFEF4444), RedPeriod, Color(0xFFB91C1C))
            contentColor = Color.White
            statusText = "Period"
        }
        todayInfo.periodStatus == PeriodStatus.PREDICTED_START ||
            todayInfo.periodStatus == PeriodStatus.PREDICTED -> {
            gradColors = listOf(Color(0xFFFCA5A5), Color(0xFFF87171), Color(0xFFEF4444))
            contentColor = Color(0xFF7F1D1D)
            statusText = "Likely Period"
        }
        todayInfo.fertility == FertilityStatus.FERTILE -> {
            gradColors = listOf(Color(0xFF10B981), GreenFertile, Color(0xFF047857))
            contentColor = Color.White
            statusText = "Fertile"
        }
        todayInfo.fertility == FertilityStatus.COULD_BE_FERTILE -> {
            gradColors = listOf(Color(0xFFFDE047), YellowCouldBe, Color(0xFFF59E0B))
            contentColor = Color(0xFF451A03)
            statusText = "Could Be Fertile"
        }
        else -> {
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
            Column(modifier = Modifier.padding(14.dp)) {
                Surface(
                    color = contentColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = statusText,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (hasData && todayInfo.daysToNextPeriod != null) {
                            todayInfo.daysToNextPeriod.toString()
                        } else "--",
                        color = contentColor,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (todayInfo.daysToNextPeriod == 1) "day" else "days",
                        color = contentColor.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = when {
                        !hasData -> "Log your last period start to begin"
                        todayInfo.daysToNextPeriod == 0 -> "Period expected today"
                        else -> "to next period"
                    },
                    color = contentColor.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                if (hasData && todayInfo.nextPeriodDate != null) {
                    Spacer(Modifier.height(6.dp))
                    val fmt = DateTimeFormatter.ofPattern("MMM d")
                    Text(
                        text = "Cycle Day ${todayInfo.cycleDay}  •  Next period: ${todayInfo.nextPeriodDate.format(fmt)}",
                        color = contentColor.copy(alpha = 0.85f),
                        fontSize = 11.sp
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

                val fadeFraction = 0.30f
                val subSteps = 8
                cycleColors.forEachIndexed { index, color ->
                    val segStart = totalLength * index / count
                    val segEnd = totalLength * (index + 1) / count
                    val prevColor = if (index > 0) cycleColors[index - 1] else color
                    val nextColor = if (index < count - 1) cycleColors[index + 1] else color

                    if (prevColor == color && nextColor == color) {
                        segmentPath.reset()
                        pathMeasure.getSegment(segStart, segEnd, segmentPath, startWithMoveTo = true)
                        drawPath(
                            path = segmentPath,
                            color = color,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    } else {
                        for (s in 0 until subSteps) {
                            val u0 = s.toFloat() / subSteps
                            val u1 = (s + 1).toFloat() / subSteps
                            val uMid = (u0 + u1) * 0.5f
                            val stepColor = when {
                                uMid < fadeFraction && prevColor != color -> {
                                    val midEdge = lerp(prevColor, color, 0.5f)
                                    lerp(midEdge, color, uMid / fadeFraction)
                                }
                                uMid > 1f - fadeFraction && nextColor != color -> {
                                    val midEdge = lerp(color, nextColor, 0.5f)
                                    lerp(color, midEdge, (uMid - (1f - fadeFraction)) / fadeFraction)
                                }
                                else -> color
                            }
                            val d0 = segStart + (segEnd - segStart) * u0
                            val d1 = (segStart + (segEnd - segStart) * u1 + 0.5f).coerceAtMost(segEnd)
                            segmentPath.reset()
                            pathMeasure.getSegment(d0, d1, segmentPath, startWithMoveTo = true)
                            drawPath(
                                path = segmentPath,
                                color = stepColor,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                        }
                    }
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
    modifier: Modifier = Modifier,
    selectedDate: LocalDate,
    periodStarts: List<LocalDate>,
    model: CycleModel,
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

    val selectedInfo = CycleCalculator.classifyDate(selectedDate, periodStarts, model)
    val selectedDateFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    onClick = {
                        onToday()
                        coroutineScope.launch {
                            listState.animateScrollToItem(monthsPast)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Today",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
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

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LegendItem(GreenFertile, "Fertile")
                LegendItem(YellowCouldBe, "Could be")
                LegendItem(BlueNotFertile, "Not fertile")
                LegendItem(RedPeriod, "Period")
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))

            // Merged Selected Day Status Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = selectedDate.format(selectedDateFmt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val statusLabel = when {
                    !model.hasData -> "Tap 'Log Period' to begin"
                    selectedInfo.periodStatus == PeriodStatus.LOGGED_START -> "Period Start • Day ${selectedInfo.cycleDay}"
                    selectedInfo.periodStatus == PeriodStatus.LOGGED -> "Period • Day ${selectedInfo.cycleDay}"
                    selectedInfo.periodStatus == PeriodStatus.PREDICTED_START ||
                        selectedInfo.periodStatus == PeriodStatus.PREDICTED -> "Likely Period • Day ${selectedInfo.cycleDay}"
                    selectedInfo.fertility == FertilityStatus.FERTILE -> "Fertile • Day ${selectedInfo.cycleDay}"
                    selectedInfo.fertility == FertilityStatus.COULD_BE_FERTILE -> "Could Be Fertile • Day ${selectedInfo.cycleDay}"
                    else -> "Not Fertile • Day ${selectedInfo.cycleDay}"
                }
                Text(
                    text = statusLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                                            .align(Alignment.TopEnd)
                                    )
                                }
                                if (info.periodStatus == PeriodStatus.LOGGED_START) {
                                    Canvas(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        val w = size.width
                                        val h = size.height
                                        val drop = Path().apply {
                                            moveTo(w * 0.5f, 0f)
                                            cubicTo(
                                                w * 0.5f, 0f,
                                                w * 0.92f, h * 0.45f,
                                                w * 0.92f, h * 0.66f
                                            )
                                            cubicTo(
                                                w * 0.92f, h * 0.87f,
                                                w * 0.73f, h,
                                                w * 0.5f, h
                                            )
                                            cubicTo(
                                                w * 0.27f, h,
                                                w * 0.08f, h * 0.87f,
                                                w * 0.08f, h * 0.66f
                                            )
                                            cubicTo(
                                                w * 0.08f, h * 0.45f,
                                                w * 0.5f, 0f,
                                                w * 0.5f, 0f
                                            )
                                            close()
                                        }
                                        drawPath(path = drop, color = Color.White)
                                    }
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
