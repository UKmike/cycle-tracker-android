package com.example.cycletracker.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MayaLogo(modifier = Modifier.size(32.dp))
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Live Home Screen Widget Preview
                HomeWidgetPreviewCard(
                    todayInfo = state.todayClassification,
                    hasData = state.model.hasData,
                    periodStarts = state.periodStarts,
                    model = state.model,
                    modifier = Modifier.zIndex(1f)
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

            // 2x2 Calendar Key aligned at bottom-left within the FAB's vertical space
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 168.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendItem(GreenFertile, "Fertile", Modifier.weight(1f))
                    LegendItem(YellowCouldBe, "Could be", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendItem(BlueNotFertile, "Not fertile", Modifier.weight(1f))
                    LegendItem(RedPeriod, "Period", Modifier.weight(1f))
                }
            }
        }

        if (showDetailsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDetailsSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CycleLengthGraphCard(
                        periodStarts = state.periodStarts,
                        model = state.model
                    )
                    InsightsCard(model = state.model)
                    HistoryCard(
                        periodStarts = state.periodStarts,
                        onRemove = viewModel::removePeriodStart,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MayaLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.105f
        val ringRadius = (size.minDimension - strokeWidth) / 2f

        // Warm off-white inner fill
        drawCircle(
            color = Color(0xFFFFF8F6),
            radius = ringRadius,
            center = center
        )

        // Thin 4-color ring with smooth fades between adjacent quadrants
        drawCircle(
            brush = Brush.sweepGradient(
                0.00f to Color(0xFF8A7390), // Red -> Blue blend (right)
                0.125f to BlueNotFertile,   // Bottom-right
                0.25f to Color(0xFF9AC08E), // Blue -> Yellow blend (bottom)
                0.375f to YellowCouldBe,    // Bottom-left
                0.50f to Color(0xFF80AB47), // Yellow -> Green blend (left)
                0.625f to GreenFertile,     // Top-left
                0.75f to Color(0xFF705E47), // Green -> Red blend (top)
                0.875f to RedPeriod,        // Top-right
                1.00f to Color(0xFF8A7390), // Red -> Blue blend (right)
                center = center
            ),
            radius = ringRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Center heart motif
        val heart = Path().apply {
            moveTo(w * 0.50f, h * 0.42f)
            cubicTo(
                w * 0.50f, h * 0.34f,
                w * 0.30f, h * 0.32f,
                w * 0.30f, h * 0.48f
            )
            cubicTo(
                w * 0.30f, h * 0.61f,
                w * 0.50f, h * 0.72f,
                w * 0.50f, h * 0.75f
            )
            cubicTo(
                w * 0.50f, h * 0.72f,
                w * 0.70f, h * 0.61f,
                w * 0.70f, h * 0.48f
            )
            cubicTo(
                w * 0.70f, h * 0.32f,
                w * 0.50f, h * 0.34f,
                w * 0.50f, h * 0.42f
            )
            close()
        }
        drawPath(path = heart, color = RedPeriod)

        // Subtle highlight on left lobe
        val highlight = Path().apply {
            moveTo(w * 0.365f, h * 0.435f)
            cubicTo(
                w * 0.38f, h * 0.398f,
                w * 0.42f, h * 0.392f,
                w * 0.445f, h * 0.412f
            )
            cubicTo(
                w * 0.418f, h * 0.42f,
                w * 0.392f, h * 0.444f,
                w * 0.38f, h * 0.48f
            )
            cubicTo(
                w * 0.37f, h * 0.468f,
                w * 0.36f, h * 0.452f,
                w * 0.365f, h * 0.435f
            )
            close()
        }
        drawPath(path = highlight, color = Color(0xFFFCA5A5))
    }
}

@Composable
private fun HomeWidgetPreviewCard(
    todayInfo: DayClassification,
    hasData: Boolean,
    periodStarts: List<LocalDate>,
    model: CycleModel,
    modifier: Modifier = Modifier
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
    val reducedMotion = rememberReducedMotion()

    val isPeriodWave = todayInfo.periodStatus == PeriodStatus.LOGGED_START ||
        todayInfo.periodStatus == PeriodStatus.LOGGED ||
        todayInfo.periodStatus == PeriodStatus.PREDICTED_START ||
        todayInfo.periodStatus == PeriodStatus.PREDICTED
    val isFertileWave = !isPeriodWave && todayInfo.fertility == FertilityStatus.FERTILE
    val isNotFertileWave = !isPeriodWave && todayInfo.fertility == FertilityStatus.NOT_FERTILE
    val primaryDurationMs = when {
        isNotFertileWave -> 6200
        isFertileWave -> 3800
        else -> 4600
    }
    val secondaryDurationMs = (primaryDurationMs * 1.9f).toInt()

    val waveTransition = rememberInfiniteTransition(label = "cycleTide")
    val primaryPhaseState = waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = primaryDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "primaryWavePhase"
    )
    val secondaryPhaseState = waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = secondaryDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryWavePhase"
    )

    // Normalized height [0f..1f] of the floating wave crest dot at x = 0.78 * width:
    // 1f when the dot is at its highest point on screen, 0f when at its lowest.
    // Read only inside graphicsLayer / draw lambdas so animation frames don't trigger recomposition.
    val dotGlowLevel: () -> Float = remember(reducedMotion, primaryPhaseState, secondaryPhaseState) {
        if (reducedMotion) {
            { 0.4f }
        } else {
            val dotAngle1 = (2.0 * PI * (0.78 / 0.72)).toFloat()
            val dotAngle2 = (2.0 * PI * (0.78 / 1.44)).toFloat()
            {
                val s = sin(dotAngle1 + primaryPhaseState.value)
                val c = cos(dotAngle2 - secondaryPhaseState.value)
                val rawNorm = (s + 0.45f * c) / 1.45f
                ((1f - rawNorm) * 0.5f).coerceIn(0f, 1f)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        val glowBlur = maxWidth * 0.045f
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        // 1. Ambient glow behind the card, using the app's live cycle ring colors
        //    and swelling in sync with the floating wave dot's height.
        if (cycleColors.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val b = dotGlowLevel()
                        val s = lerpFloat(0.97f, 1.045f, b)
                        scaleX = s
                        scaleY = s
                        alpha = lerpFloat(0.12f, 0.85f, b)
                    }
                    .then(
                        if (canBlur) Modifier.blur(glowBlur, BlurredEdgeTreatment.Unbounded)
                        else Modifier
                    )
                    .drawWithCache {
                        val brush = cycleRingSweepBrush(size, cycleColors)
                        val r = CornerRadius(28.dp.toPx().coerceAtMost(size.minDimension / 2f))
                        val spread = glowBlur.toPx()
                        onDrawBehind {
                            if (canBlur) {
                                drawRoundRect(brush = brush, cornerRadius = r)
                            } else {
                                // Android 11 and below fallback: fading concentric outlines
                                val steps = 8
                                for (i in 0 until steps) {
                                    val out = spread * i / steps
                                    val a = (1f - i.toFloat() / steps).let { it * it } * 0.35f
                                    drawRoundRect(
                                        brush = brush,
                                        topLeft = Offset(-out, -out),
                                        size = Size(size.width + out * 2f, size.height + out * 2f),
                                        cornerRadius = CornerRadius(r.x + out),
                                        style = Stroke(width = spread / steps * 2f),
                                        alpha = a
                                    )
                                }
                                drawRoundRect(brush = brush, cornerRadius = r, alpha = 0.5f)
                            }
                        }
                    }
            )
        }

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
            FlowingCycleTideOverlay(
                todayInfo = todayInfo,
                contentColor = contentColor,
                primaryPhase = { primaryPhaseState.value },
                secondaryPhase = { secondaryPhaseState.value },
                modifier = Modifier.matchParentSize()
            )
            Column(modifier = Modifier.padding(14.dp)) {
                val displayStatus = if (hasData && todayInfo.daysLate > 0) "Period Late" else statusText
                Surface(
                    color = contentColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = displayStatus,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                val displayCount = when {
                    !hasData -> null
                    todayInfo.daysLate > 0 -> todayInfo.daysLate
                    else -> todayInfo.daysToNextPeriod
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = displayCount?.toString() ?: "--",
                        color = contentColor,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (displayCount == 1) "day" else "days",
                        color = contentColor.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = when {
                        !hasData -> "Log your last period start to begin"
                        todayInfo.daysLate > 0 -> "late"
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
                    val footerText = if (todayInfo.daysLate > 0 && todayInfo.expectedPeriodDate != null) {
                        "Cycle Day ${model.averageCycleLength + todayInfo.daysLate}  •  Expected: ${todayInfo.expectedPeriodDate.format(fmt)}"
                    } else {
                        "Cycle Day ${todayInfo.cycleDay}  •  Next period: ${todayInfo.nextPeriodDate.format(fmt)}"
                    }
                    Text(
                        text = footerText,
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
                val top = inset
                val midX = size.width / 2f

                // Build rounded-rectangle perimeter starting at top-center (period start) clockwise
                val perimeter = Path()
                populateWidgetPerimeterPath(
                    path = perimeter,
                    width = size.width,
                    height = size.height,
                    strokeWidth = strokeWidth,
                    cornerRadius = cornerRadius
                )

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

                val totalSteps = count * 8
                for (step in 0 until totalSteps) {
                    val d0 = totalLength * step / totalSteps
                    val d1 = (totalLength * (step + 1) / totalSteps + 0.6f).coerceAtMost(totalLength)
                    val dayPos = count * (step + 0.5f) / totalSteps
                    segmentPath.reset()
                    pathMeasure.getSegment(d0, d1, segmentPath, startWithMoveTo = true)
                    drawPath(
                        path = segmentPath,
                        color = sampleSmoothCycleColorCompose(cycleColors, dayPos),
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
            }

            // Subtle ring brightness boost synced to the floating wave dot's height
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val strokeWidth = 6.dp.toPx()
                        val cornerRadius = 28.dp.toPx().coerceAtMost(
                            (size.minDimension - strokeWidth) / 2f
                        )
                        val perimeter = Path()
                        populateWidgetPerimeterPath(
                            path = perimeter,
                            width = size.width,
                            height = size.height,
                            strokeWidth = strokeWidth,
                            cornerRadius = cornerRadius
                        )
                        val brush = cycleRingSweepBrush(size, cycleColors)
                        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        onDrawBehind {
                            val b = dotGlowLevel()
                            val boostAlpha = lerpFloat(0f, 0.28f, b)
                            if (boostAlpha > 0.005f) {
                                drawPath(
                                    path = perimeter,
                                    brush = brush,
                                    alpha = boostAlpha,
                                    style = stroke,
                                    blendMode = BlendMode.Plus
                                )
                            }
                        }
                    }
            )

            // Highlight current cycle day on the outline with a quiet pulsing ripple
            val currentDay = todayInfo.cycleDay
            if (currentDay != null) {
                val count = cycleColors.size
                val clampedIndex = ((currentDay - 1) % count + count) % count
                PulsingDayMarkerOverlay(
                    dayFraction = (clampedIndex + 0.5f) / count,
                    phaseColor = cycleColors[clampedIndex],
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

private fun populateWidgetPerimeterPath(
    path: Path,
    width: Float,
    height: Float,
    strokeWidth: Float,
    cornerRadius: Float
) {
    val inset = strokeWidth / 2f
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    val midX = width / 2f

    path.reset()
    path.moveTo(midX, top)
    path.lineTo(right - cornerRadius, top)
    path.arcTo(
        rect = Rect(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    path.lineTo(right, bottom - cornerRadius)
    path.arcTo(
        rect = Rect(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    path.lineTo(left + cornerRadius, bottom)
    path.arcTo(
        rect = Rect(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    path.lineTo(left, top + cornerRadius)
    path.arcTo(
        rect = Rect(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    path.lineTo(midX, top)
}

@Composable
private fun PulsingDayMarkerOverlay(
    dayFraction: Float,
    phaseColor: Color,
    modifier: Modifier = Modifier
) {
    val (haloColor, dotColor) = when (phaseColor) {
        RedPeriod -> Color(0xFFFCA5A5) to Color(0xFFB91C1C)
        GreenFertile -> Color(0xFF6EE7B7) to Color(0xFF047857)
        YellowCouldBe -> Color(0xFFFEF08A) to Color(0xFFD97706)
        else -> Color(0xFFBAE6FD) to Color(0xFF0284C7)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "markerPulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    val perimeterPath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    Canvas(modifier = modifier) {
        val strokeWidth = 6.dp.toPx()
        val cornerRadius = 28.dp.toPx().coerceAtMost(
            (size.minDimension - strokeWidth) / 2f
        )
        populateWidgetPerimeterPath(
            path = perimeterPath,
            width = size.width,
            height = size.height,
            strokeWidth = strokeWidth,
            cornerRadius = cornerRadius
        )
        pathMeasure.setPath(perimeterPath, forceClosed = false)
        val markerPos = pathMeasure.getPosition(pathMeasure.length * dayFraction)

        val haloRadius = 9.5.dp.toPx()

        // Soft expanding "you are here" ripple during the first 72% of the cycle,
        // leaving a brief calm pause before the next ripple begins.
        if (pulseProgress < 0.72f) {
            val prog = pulseProgress / 0.72f
            val eased = 1f - (1f - prog) * (1f - prog) * (1f - prog)
            val rippleRadius = haloRadius + eased * 13.dp.toPx()
            val rippleAlpha = (1f - prog) * 0.75f
            val rippleStroke = (2.2f * (1f - prog * 0.4f)).dp.toPx()
            drawCircle(
                color = haloColor.copy(alpha = rippleAlpha),
                radius = rippleRadius,
                center = markerPos,
                style = Stroke(width = rippleStroke)
            )
        }

        drawCircle(
            color = haloColor,
            radius = haloRadius,
            center = markerPos
        )
        drawCircle(
            color = dotColor,
            radius = 6.dp.toPx(),
            center = markerPos
        )
    }
}

@Composable
private fun FlowingCycleTideOverlay(
    todayInfo: DayClassification,
    contentColor: Color,
    primaryPhase: () -> Float,
    secondaryPhase: () -> Float,
    modifier: Modifier = Modifier
) {
    val isPeriod = todayInfo.periodStatus == PeriodStatus.LOGGED_START ||
        todayInfo.periodStatus == PeriodStatus.LOGGED ||
        todayInfo.periodStatus == PeriodStatus.PREDICTED_START ||
        todayInfo.periodStatus == PeriodStatus.PREDICTED
    val isFertile = !isPeriod && todayInfo.fertility == FertilityStatus.FERTILE
    val isNotFertile = !isPeriod && todayInfo.fertility == FertilityStatus.NOT_FERTILE

    val ampDp = when {
        isFertile -> 14.dp
        isNotFertile -> 7.dp
        else -> 10.dp
    }

    val wavePath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val p1 = primaryPhase()
        val p2 = secondaryPhase()
        val amp = ampDp.toPx()
        val freq1 = (2.0 * PI / (w * 0.72f)).toFloat()
        val freq2 = (2.0 * PI / (w * 1.44f)).toFloat()
        val stepPx = 6.dp.toPx().coerceAtLeast(4f)

        fun waveY(x: Float, baseY: Float, layerPhaseOffset: Float): Float {
            return baseY +
                sin(x * freq1 + p1 + layerPhaseOffset) * amp +
                cos(x * freq2 - p2) * (amp * 0.45f)
        }

        // Two overlapping translucent fluid wave layers
        val layers = listOf(
            Triple(h * 0.64f, 0f, 0.14f),
            Triple(h * 0.75f, 1.8f, 0.10f)
        )
        for ((baseY, phaseOffset, alpha) in layers) {
            wavePath.reset()
            wavePath.moveTo(0f, h)
            var x = 0f
            while (x < w) {
                wavePath.lineTo(x, waveY(x, baseY, phaseOffset))
                x += stepPx
            }
            wavePath.lineTo(w, waveY(w, baseY, phaseOffset))
            wavePath.lineTo(w, h)
            wavePath.close()
            drawPath(path = wavePath, color = contentColor.copy(alpha = alpha))
        }

        // Glowing crest dot riding the top wave on the open right side
        val dotX = w * 0.78f
        val dotY = waveY(dotX, h * 0.64f, 0f)
        drawCircle(
            color = contentColor.copy(alpha = 0.28f),
            radius = 9.dp.toPx(),
            center = Offset(dotX, dotY)
        )
        drawCircle(
            color = contentColor.copy(alpha = 0.75f),
            radius = 4.dp.toPx(),
            center = Offset(dotX, dotY)
        )
    }
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Sweep gradient built from the app's live [cycleColors], starting at 12 o'clock clockwise
 * with smooth phase transitions and a hard Blue | Red edge at 12 o'clock.
 */
private fun cycleRingSweepBrush(size: Size, cycleColors: List<Color>): ShaderBrush {
    val count = cycleColors.size
    val samples = (count * 4).coerceAtLeast(32)
    val colors = IntArray(samples + 1)
    val stops = FloatArray(samples + 1)
    for (i in 0..samples) {
        val frac = i.toFloat() / samples
        val dayPos = (frac * count).coerceIn(0.01f, count - 0.01f)
        colors[i] = sampleSmoothCycleColorCompose(cycleColors, dayPos).toArgb()
        stops[i] = frac
    }
    val cx = size.width / 2f
    val cy = size.height / 2f
    val shader = android.graphics.SweepGradient(cx, cy, colors, stops).apply {
        setLocalMatrix(android.graphics.Matrix().apply { setRotate(-90f, cx, cy) })
    }
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
    }
}

/** True when the user has turned animations off (animator duration scale = 0). */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

private fun sampleSmoothCycleColorCompose(
    colors: List<Color>,
    dayPos: Float,
    blendRadius: Float = 1.6f
): Color {
    val count = colors.size
    if (count == 0) return Color.Transparent
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
            rSum += c.red * weight
            gSum += c.green * weight
            bSum += c.blue * weight
            wSum += weight
        }
    }
    if (wSum <= 0f) return colors[dayPos.toInt().coerceIn(0, count - 1)]
    return Color(
        red = (rSum / wSum).coerceIn(0f, 1f),
        green = (gSum / wSum).coerceIn(0f, 1f),
        blue = (bSum / wSum).coerceIn(0f, 1f),
        alpha = 1f
    )
}

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
    val monthsPast = 120
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
                val todayButtonBg = rememberWallpaperAccentColor(MaterialTheme.colorScheme.primary)
                val todayButtonFg = if (todayButtonBg.luminance() > 0.5f) Color(0xFF111827) else Color.White
                Surface(
                    onClick = {
                        onToday()
                        coroutineScope.launch {
                            listState.animateScrollToItem(monthsPast)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = todayButtonBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))

            // Merged Selected Day Status Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedDate.format(selectedDateFmt),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                val statusLabel = when {
                    !model.hasData -> "Tap 'Log Period' to begin"
                    selectedDate == today && selectedInfo.daysLate > 0 -> {
                        val unit = if (selectedInfo.daysLate == 1) "day" else "days"
                        "Period ${selectedInfo.daysLate} $unit late • Day ${model.averageCycleLength + selectedInfo.daysLate}"
                    }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun rememberWallpaperAccentColor(fallback: Color): Color {
    val context = LocalContext.current
    var accentColor by remember(context) {
        mutableStateOf(readWallpaperPrimaryColor(context) ?: fallback)
    }

    DisposableEffect(context, fallback) {
        val updateColor = {
            accentColor = readWallpaperPrimaryColor(context) ?: fallback
        }
        updateColor()

        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateColor()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)

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
            lifecycleOwner?.lifecycle?.removeObserver(observer)
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
private fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
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
private fun CycleLengthGraphCard(
    periodStarts: List<LocalDate>,
    model: CycleModel
) {
    val sortedAsc = remember(periodStarts) { periodStarts.sorted() }
    val allCycles = remember(sortedAsc) {
        (0 until (sortedAsc.size - 1)).map { i ->
            sortedAsc[i] to ChronoUnit.DAYS.between(sortedAsc[i], sortedAsc[i + 1]).toInt()
        }
    }
    val recentCycles = remember(allCycles) { allCycles.takeLast(12) }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotCenterColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cycle Length Trend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        allCycles.isEmpty() -> "No cycles yet"
                        allCycles.size > 12 -> "Last 12 cycles"
                        allCycles.size == 1 -> "1 cycle"
                        else -> "${allCycles.size} cycles"
                    },
                    fontSize = 11.sp,
                    color = labelColor
                )
            }

            if (recentCycles.isEmpty()) {
                Text(
                    text = "Log at least 2 period start dates to see your cycle length graph.",
                    fontSize = 12.sp,
                    color = labelColor
                )
            } else {
                val rawMin = minOf(recentCycles.minOf { it.second }, model.averageCycleLength)
                val rawMax = maxOf(recentCycles.maxOf { it.second }, model.averageCycleLength)
                val minY = (rawMin - 2).coerceAtLeast(0)
                val maxY = (rawMax + 2).coerceAtLeast(minY + 4)
                val midY = (minY + maxY) / 2
                val spanY = (maxY - minY).toFloat()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(34.dp)
                            .fillMaxHeight()
                            .padding(top = 12.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("${maxY}d", fontSize = 10.sp, color = labelColor)
                        Text("${midY}d", fontSize = 10.sp, color = labelColor)
                        Text("${minY}d", fontSize = 10.sp, color = labelColor)
                    }

                    Spacer(Modifier.width(6.dp))

                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        val topPad = 18.dp.toPx()
                        val bottomPad = 8.dp.toPx()
                        val sidePad = 12.dp.toPx()
                        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                        val plotW = (size.width - sidePad * 2f).coerceAtLeast(1f)

                        fun yFor(days: Float): Float =
                            topPad + plotH * (1f - (days - minY) / spanY)

                        // Subtle horizontal gridlines
                        listOf(maxY.toFloat(), midY.toFloat(), minY.toFloat()).forEach { level ->
                            val y = yFor(level)
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Dashed average reference line
                        val avgY = yFor(model.averageCycleLength.toFloat())
                        drawLine(
                            color = RedPeriod.copy(alpha = 0.45f),
                            start = Offset(0f, avgY),
                            end = Offset(size.width, avgY),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        )

                        val points = recentCycles.mapIndexed { idx, (_, days) ->
                            val x = if (recentCycles.size == 1) {
                                size.width / 2f
                            } else {
                                sidePad + plotW * idx / (recentCycles.size - 1)
                            }
                            Offset(x, yFor(days.toFloat()))
                        }

                        if (points.size >= 2) {
                            val linePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 1 until points.size) {
                                    lineTo(points[i].x, points[i].y)
                                }
                            }
                            val bottomY = topPad + plotH
                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo(points.last().x, bottomY)
                                lineTo(points.first().x, bottomY)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        RedPeriod.copy(alpha = 0.22f),
                                        Color.Transparent
                                    ),
                                    startY = topPad,
                                    endY = bottomY
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = RedPeriod,
                                style = Stroke(
                                    width = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        points.forEachIndexed { idx, pt ->
                            drawCircle(
                                color = RedPeriod,
                                radius = 4.5.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = dotCenterColor,
                                radius = 2.dp.toPx(),
                                center = pt
                            )

                            val measured = textMeasurer.measure(
                                text = recentCycles[idx].second.toString(),
                                style = valueStyle
                            )
                            val labelX = (pt.x - measured.size.width / 2f)
                                .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                            val labelY = (pt.y - measured.size.height - 5.dp.toPx()).coerceAtLeast(0f)
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(labelX, labelY)
                            )
                        }
                    }
                }

                val axisFmt = remember { DateTimeFormatter.ofPattern("MMM yy") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 4.dp),
                    horizontalArrangement = if (recentCycles.size == 1) {
                        Arrangement.Center
                    } else {
                        Arrangement.SpaceBetween
                    }
                ) {
                    Text(recentCycles.first().first.format(axisFmt), fontSize = 10.sp, color = labelColor)
                    if (recentCycles.size >= 3) {
                        Text(
                            recentCycles[recentCycles.size / 2].first.format(axisFmt),
                            fontSize = 10.sp,
                            color = labelColor
                        )
                    }
                    if (recentCycles.size >= 2) {
                        Text(recentCycles.last().first.format(axisFmt), fontSize = 10.sp, color = labelColor)
                    }
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
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onRemove: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedDesc = remember(periodStarts) { periodStarts.sortedDescending() }
    val fmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Period History (${sortedDesc.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            if (sortedDesc.isEmpty()) {
                Text(
                    "No period start dates logged yet. Tap any calendar day to log one.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Column headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start Date",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = "Cycle Days",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.9f)
                    )
                    Spacer(Modifier.width(36.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = sortedDesc,
                        key = { _, date -> date.toEpochDay() }
                    ) { index, date ->
                        val cycleLen = if (index > 0) {
                            ChronoUnit.DAYS.between(date, sortedDesc[index - 1]).toInt()
                        } else null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = date.format(fmt),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1.3f)
                            )
                            Box(
                                modifier = Modifier.weight(0.9f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cycleLen != null) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "$cycleLen days",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Current",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemove(date) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (index < sortedDesc.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
