package com.example.cycletracker.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cycletracker.domain.CycleCalculator
import com.example.cycletracker.domain.CycleModel
import com.example.cycletracker.domain.DayClassification
import com.example.cycletracker.domain.FertilityStatus
import com.example.cycletracker.domain.PeriodStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val GreenFertile = Color(0xFF059669)
private val YellowCouldBe = Color(0xFFFBBF24)
private val BlueNotFertile = Color(0xFF2563EB)
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
                    Text(
                        text = "Cycle",
                        fontWeight = FontWeight.Bold
                    )
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
                    Button(
                        onClick = { viewModel.togglePeriodStart(today) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTodayLogged) GreenFertile else RedPeriod,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isTodayLogged) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isTodayLogged) "Logged Today" else "Period Today",
                            fontSize = 12.sp
                        )
                    }
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
                hasData = state.model.hasData
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

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeWidgetPreviewCard(
    todayInfo: DayClassification,
    hasData: Boolean
) {
    val bgColor: Color
    val contentColor: Color
    val statusText: String

    when (todayInfo.fertility) {
        FertilityStatus.FERTILE -> {
            bgColor = GreenFertile
            contentColor = Color.White
            statusText = "Fertile"
        }
        FertilityStatus.COULD_BE_FERTILE -> {
            bgColor = YellowCouldBe
            contentColor = Color(0xFF451A03)
            statusText = "Could Be Fertile"
        }
        FertilityStatus.NOT_FERTILE -> {
            bgColor = BlueNotFertile
            contentColor = Color.White
            statusText = "Not Fertile"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
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
    val monthTitle = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    val firstOfMonth = month.atDay(1)
    // Sunday = 0 .. Saturday = 6
    val leadingEmpty = firstOfMonth.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()

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
                    text = monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    TextButton(onClick = onToday) {
                        Text("Today", fontSize = 12.sp)
                    }
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val totalCells = leadingEmpty + daysInMonth
            val rows = (totalCells + 6) / 7

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

                                val (cellBg, cellBorder, dotColor) = when {
                                    !model.hasData -> Triple(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        Color.Transparent
                                    )
                                    info.periodStatus == PeriodStatus.LOGGED_START ||
                                        info.periodStatus == PeriodStatus.LOGGED -> Triple(
                                        RedPeriod.copy(alpha = 0.22f),
                                        RedPeriod,
                                        RedPeriod
                                    )
                                    info.periodStatus == PeriodStatus.PREDICTED_START ||
                                        info.periodStatus == PeriodStatus.PREDICTED -> Triple(
                                        RedPeriod.copy(alpha = 0.08f),
                                        RedPeriod.copy(alpha = 0.55f),
                                        RedPeriod.copy(alpha = 0.6f)
                                    )
                                    info.fertility == FertilityStatus.FERTILE -> Triple(
                                        GreenFertile.copy(alpha = 0.16f),
                                        GreenFertile.copy(alpha = 0.45f),
                                        GreenFertile
                                    )
                                    info.fertility == FertilityStatus.COULD_BE_FERTILE -> Triple(
                                        YellowCouldBe.copy(alpha = 0.18f),
                                        YellowCouldBe.copy(alpha = 0.55f),
                                        YellowCouldBe
                                    )
                                    else -> Triple(
                                        BlueNotFertile.copy(alpha = 0.10f),
                                        BlueNotFertile.copy(alpha = 0.28f),
                                        BlueNotFertile
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(cellBg)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else cellBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSelectDate(date) }
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.align(Alignment.TopStart)
                                    )
                                    if (model.hasData) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                    colors = ButtonDefaults.buttonColors(containerColor = RosePeriod, contentColor = Color.White)
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
