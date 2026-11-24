package com.example.cycletracker.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.cycletracker.domain.CycleModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val HistoryCardShape = RoundedCornerShape(20.dp)
private val HistoryDateStyle = TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum"
)
private val HistoryBadgeStyle = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum"
)
private val AvgDashIntervals = floatArrayOf(10f, 8f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    periodStarts: List<LocalDate>,
    model: CycleModel,
    onRemove: (LocalDate) -> Unit,
    onUndoRemove: (LocalDate) -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onLogFirstPeriod: () -> Unit,
    onBack: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val handleRemove: (LocalDate) -> Unit = { date ->
        onRemove(date)
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val formatted = date.format(CyclePalette.HISTORY_DATE_FMT)
            val result = snackbarHostState.showSnackbar(
                message = "Removed $formatted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndoRemove(date)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Text(
                        text = "Cycle History",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CycleLengthGraphCard(
                    periodStarts = periodStarts,
                    model = model,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    fillHeight = true
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InsightsCard(model = model, compact = true)
                    HistoryCard(
                        periodStarts = periodStarts,
                        onRemove = handleRemove,
                        onJumpToDate = onJumpToDate,
                        onLogFirstPeriod = onLogFirstPeriod,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CycleLengthGraphCard(
                    periodStarts = periodStarts,
                    model = model
                )
                InsightsCard(model = model)
                HistoryCard(
                    periodStarts = periodStarts,
                    onRemove = handleRemove,
                    onJumpToDate = onJumpToDate,
                    onLogFirstPeriod = onLogFirstPeriod,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CycleLengthGraphCard(
    periodStarts: List<LocalDate>,
    model: CycleModel,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false
) {
    // periodStarts from CycleUiState is already distinct and sorted ascending
    val allCycles = remember(periodStarts) {
        if (periodStarts.size < 2) emptyList()
        else List(periodStarts.size - 1) { i ->
            periodStarts[i] to ChronoUnit.DAYS.between(periodStarts[i], periodStarts[i + 1]).toInt()
        }
    }
    val recentCycles = remember(allCycles) { allCycles.takeLast(12) }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotCenterColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    val valueStyle = remember(onSurfaceColor) {
        TextStyle(
            color = onSurfaceColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
            fontFeatureSettings = "tnum"
        )
    }
    val avgPillStyle = remember {
        TextStyle(
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).em,
            fontFeatureSettings = "tnum"
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = HistoryCardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                val bounds = remember(recentCycles, model.averageCycleLength) {
                    val rawMin = minOf(recentCycles.minOf { it.second }, model.averageCycleLength)
                    val rawMax = maxOf(recentCycles.maxOf { it.second }, model.averageCycleLength)
                    val minY = (rawMin - 2).coerceAtLeast(0)
                    val maxY = (rawMax + 2).coerceAtLeast(minY + 4)
                    Triple(minY, (minY + maxY) / 2, maxY)
                }
                val (minY, midY, maxY) = bounds
                val spanY = (maxY - minY).toFloat()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fillHeight) Modifier.weight(1f) else Modifier.height(126.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .width(34.dp)
                            .fillMaxHeight()
                            .padding(top = 14.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("${maxY}d", fontSize = 10.sp, color = labelColor)
                        Text("${midY}d", fontSize = 10.sp, color = labelColor)
                        Text("${minY}d", fontSize = 10.sp, color = labelColor)
                    }

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .drawWithCache {
                                val topPad = 22.dp.toPx()
                                val bottomPad = 8.dp.toPx()
                                val sidePad = 12.dp.toPx()
                                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                                val plotW = (size.width - sidePad * 2f).coerceAtLeast(1f)
                                val bottomY = topPad + plotH

                                fun yFor(days: Float): Float =
                                    topPad + plotH * (1f - (days - minY) / spanY)

                                val gridYs = floatArrayOf(
                                    yFor(maxY.toFloat()),
                                    yFor(midY.toFloat()),
                                    yFor(minY.toFloat())
                                )
                                val gridStroke = 1.dp.toPx()
                                val avgY = yFor(model.averageCycleLength.toFloat())
                                val avgStroke = 1.2.dp.toPx()
                                val avgDash = PathEffect.dashPathEffect(AvgDashIntervals, 0f)
                                val avgColor = CyclePalette.RedPeriod.copy(alpha = 0.50f)

                                val points = recentCycles.mapIndexed { idx, (_, days) ->
                                    val x = if (recentCycles.size == 1) {
                                        size.width / 2f
                                    } else {
                                        sidePad + plotW * idx / (recentCycles.size - 1)
                                    }
                                    Offset(x, yFor(days.toFloat()))
                                }

                                // Fritsch-Carlson monotone cubic Hermite spline (smooth & never overshoots)
                                val linePath: Path?
                                val fillPath: Path?
                                val fillBrush: Brush?
                                val lineStroke = Stroke(
                                    width = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                                if (points.size >= 2) {
                                    linePath = buildMonotoneCubicPath(points)
                                    fillPath = Path().apply {
                                        addPath(linePath)
                                        lineTo(points.last().x, bottomY)
                                        lineTo(points.first().x, bottomY)
                                        close()
                                    }
                                    fillBrush = Brush.verticalGradient(
                                        colors = listOf(
                                            CyclePalette.RedPeriod.copy(alpha = 0.22f),
                                            Color.Transparent
                                        ),
                                        startY = topPad,
                                        endY = bottomY
                                    )
                                } else {
                                    linePath = null
                                    fillPath = null
                                    fillBrush = null
                                }

                                val dotOuterR = 4.5.dp.toPx()
                                val dotInnerR = 2.dp.toPx()
                                val latestHaloR = 8.5.dp.toPx()
                                val latestHaloColor = CyclePalette.RedPeriod.copy(alpha = 0.22f)
                                val labelLift = 5.dp.toPx()
                                val staggerLift = 11.dp.toPx()

                                // Pre-measure point labels and stagger vertically if crowded horizontally
                                val measuredPoints = points.mapIndexed { idx, pt ->
                                    val measured = textMeasurer.measure(
                                        text = recentCycles[idx].second.toString(),
                                        style = valueStyle
                                    )
                                    val lx = (pt.x - measured.size.width / 2f)
                                        .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                                    Triple(measured, lx, pt.y)
                                }
                                val placedLabels = measuredPoints.mapIndexed { idx, (measured, lx, py) ->
                                    var ly = (py - measured.size.height - labelLift).coerceAtLeast(0f)
                                    if (idx > 0) {
                                        val (prevM, prevX, prevY) = measuredPoints[idx - 1]
                                        val prevRight = prevX + prevM.size.width + 2.dp.toPx()
                                        if (lx < prevRight && abs(py - prevY) < measured.size.height * 1.15f) {
                                            ly = (ly - staggerLift).coerceAtLeast(0f)
                                        }
                                    }
                                    measured to Offset(lx, ly)
                                }

                                // Inline "Avg 28d" pill badge on the dashed average reference line,
                                // positioned dynamically to avoid overlapping any data point or label.
                                val avgPillText = textMeasurer.measure(
                                    text = "Avg ${model.averageCycleLength}d",
                                    style = avgPillStyle
                                )
                                val pillPadH = 5.dp.toPx()
                                val pillPadV = 2.dp.toPx()
                                val pillW = avgPillText.size.width + pillPadH * 2f
                                val pillH = avgPillText.size.height + pillPadV * 2f
                                val canvasW = size.width
                                val canvasH = size.height
                                val pillMarginX = 2.dp.toPx()
                                val maxPillLeft = (canvasW - pillW - pillMarginX).coerceAtLeast(0f)
                                val maxPillTop = (canvasH - pillH).coerceAtLeast(0f)

                                val candidateXs = buildList {
                                    add(maxPillLeft)
                                    add(pillMarginX.coerceAtMost(maxPillLeft))
                                    add(((canvasW - pillW) / 2f).coerceIn(pillMarginX, maxPillLeft))
                                    for (i in 0 until points.size - 1) {
                                        val midX = (points[i].x + points[i + 1].x) / 2f - pillW / 2f
                                        add(midX.coerceIn(pillMarginX, maxPillLeft))
                                    }
                                }
                                val candidateYs = listOf(
                                    (avgY - pillH / 2f).coerceIn(0f, maxPillTop),
                                    (avgY - pillH - 3.dp.toPx()).coerceIn(0f, maxPillTop),
                                    (avgY + 3.dp.toPx()).coerceIn(0f, maxPillTop)
                                )

                                fun clearanceFor(left: Float, top: Float): Float {
                                    val right = left + pillW
                                    val bottom = top + pillH
                                    var minGap = Float.MAX_VALUE
                                    for (idx in points.indices) {
                                        val pt = points[idx]
                                        val r = if (idx == points.lastIndex) latestHaloR else dotOuterR
                                        val dx = when {
                                            pt.x < left -> left - pt.x
                                            pt.x > right -> pt.x - right
                                            else -> 0f
                                        }
                                        val dy = when {
                                            pt.y < top -> top - pt.y
                                            pt.y > bottom -> pt.y - bottom
                                            else -> 0f
                                        }
                                        val distToDot = kotlin.math.hypot(dx, dy) - r
                                        if (distToDot < minGap) minGap = distToDot

                                        val (lbl, lblPos) = placedLabels[idx]
                                        val lblRight = lblPos.x + lbl.size.width
                                        val lblBottom = lblPos.y + lbl.size.height
                                        val ldx = maxOf(0f, maxOf(left - lblRight, lblPos.x - right))
                                        val ldy = maxOf(0f, maxOf(top - lblBottom, lblPos.y - bottom))
                                        val distToLbl = if (ldx == 0f && ldy == 0f) {
                                            -minOf(
                                                minOf(right, lblRight) - maxOf(left, lblPos.x),
                                                minOf(bottom, lblBottom) - maxOf(top, lblPos.y)
                                            )
                                        } else {
                                            kotlin.math.hypot(ldx, ldy)
                                        }
                                        if (distToLbl < minGap) minGap = distToLbl
                                    }
                                    return minGap
                                }

                                var bestLeft = maxPillLeft
                                var bestTop = candidateYs.first()
                                var bestScore = Float.NEGATIVE_INFINITY
                                for ((yIdx, cy) in candidateYs.withIndex()) {
                                    // Slightly prefer staying centered on the dashed line (yIdx == 0)
                                    val yBias = if (yIdx == 0) 3.dp.toPx() else 0f
                                    for (cx in candidateXs) {
                                        val score = clearanceFor(cx, cy) + yBias
                                        if (score > bestScore) {
                                            bestScore = score
                                            bestLeft = cx
                                            bestTop = cy
                                        }
                                    }
                                }

                                val pillLeft = bestLeft
                                val pillTop = bestTop
                                val pillRight = pillLeft + pillW
                                val pillCenteredOnLine = abs((pillTop + pillH / 2f) - avgY) < pillH * 0.6f
                                val pillRadius = CornerRadius(pillH / 2f, pillH / 2f)
                                val avgPillBg = CyclePalette.RedPeriod.copy(alpha = 0.90f)

                                onDrawBehind {
                                    for (gy in gridYs) {
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(0f, gy),
                                            end = Offset(size.width, gy),
                                            strokeWidth = gridStroke
                                        )
                                    }

                                    if (pillCenteredOnLine) {
                                        if (pillLeft > 4f) {
                                            drawLine(
                                                color = avgColor,
                                                start = Offset(0f, avgY),
                                                end = Offset((pillLeft - 4f).coerceAtLeast(0f), avgY),
                                                strokeWidth = avgStroke,
                                                pathEffect = avgDash
                                            )
                                        }
                                        if (pillRight + 4f < size.width) {
                                            drawLine(
                                                color = avgColor,
                                                start = Offset(pillRight + 4f, avgY),
                                                end = Offset(size.width, avgY),
                                                strokeWidth = avgStroke,
                                                pathEffect = avgDash
                                            )
                                        }
                                    } else {
                                        drawLine(
                                            color = avgColor,
                                            start = Offset(0f, avgY),
                                            end = Offset(size.width, avgY),
                                            strokeWidth = avgStroke,
                                            pathEffect = avgDash
                                        )
                                    }

                                    if (linePath != null && fillPath != null && fillBrush != null) {
                                        drawPath(path = fillPath, brush = fillBrush)
                                        drawPath(path = linePath, color = CyclePalette.RedPeriod, style = lineStroke)
                                    }

                                    points.forEachIndexed { idx, pt ->
                                        if (idx == points.lastIndex) {
                                            drawCircle(
                                                color = latestHaloColor,
                                                radius = latestHaloR,
                                                center = pt
                                            )
                                        }
                                        drawCircle(color = CyclePalette.RedPeriod, radius = dotOuterR, center = pt)
                                        drawCircle(color = dotCenterColor, radius = dotInnerR, center = pt)
                                        val (measured, offset) = placedLabels[idx]
                                        drawText(textLayoutResult = measured, topLeft = offset)
                                    }

                                    // Draw Avg pill last so it is always legible and never obscured
                                    drawRoundRect(
                                        color = avgPillBg,
                                        topLeft = Offset(pillLeft, pillTop),
                                        size = Size(pillW, pillH),
                                        cornerRadius = pillRadius
                                    )
                                    drawText(
                                        textLayoutResult = avgPillText,
                                        topLeft = Offset(pillLeft + pillPadH, pillTop + pillPadV)
                                    )
                                }
                            }
                    )
                }

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
                    Text(
                        recentCycles.first().first.format(CyclePalette.AXIS_DATE_FMT),
                        fontSize = 10.sp,
                        color = labelColor
                    )
                    if (recentCycles.size >= 3) {
                        Text(
                            recentCycles[recentCycles.size / 2].first.format(CyclePalette.AXIS_DATE_FMT),
                            fontSize = 10.sp,
                            color = labelColor
                        )
                    }
                    if (recentCycles.size >= 2) {
                        Text(
                            recentCycles.last().first.format(CyclePalette.AXIS_DATE_FMT),
                            fontSize = 10.sp,
                            color = labelColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fritsch-Carlson monotone cubic Hermite interpolation through [points] sorted by x.
 * Guarantees smooth curves with zero overshoot past local extrema.
 */
private fun buildMonotoneCubicPath(points: List<Offset>): Path {
    val n = points.size
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    if (n == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    val dx = FloatArray(n - 1)
    val slope = FloatArray(n - 1)
    for (i in 0 until n - 1) {
        dx[i] = (points[i + 1].x - points[i].x).coerceAtLeast(1e-3f)
        slope[i] = (points[i + 1].y - points[i].y) / dx[i]
    }

    val tangent = FloatArray(n)
    tangent[0] = slope[0]
    tangent[n - 1] = slope[n - 2]
    for (i in 1 until n - 1) {
        val mPrev = slope[i - 1]
        val mNext = slope[i]
        tangent[i] = if (mPrev * mNext <= 0f) {
            0f
        } else {
            val w1 = 2f * dx[i] + dx[i - 1]
            val w2 = dx[i] + 2f * dx[i - 1]
            (w1 + w2) / (w1 / mPrev + w2 / mNext)
        }
    }

    for (i in 0 until n - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val h = dx[i] / 3f
        path.cubicTo(
            p0.x + h, p0.y + tangent[i] * h,
            p1.x - h, p1.y - tangent[i + 1] * h,
            p1.x, p1.y
        )
    }
    return path
}

@Composable
private fun InsightsCard(model: CycleModel, compact: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = HistoryCardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 7.dp else 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cycle Insights",
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (model.cyclesUsed == 0) "Default (28d)" else "Adapted (${model.cyclesUsed} cycles)",
                    fontSize = if (compact) 10.sp else 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
            ) {
                StatTile("Avg Cycle", "${model.averageCycleLength}d", Modifier.weight(1f), compact)
                StatTile("Variation", "±${model.variabilityDays}d", Modifier.weight(1f), compact)
                StatTile("Ovulation", "Day ${model.ovulationDay}", Modifier.weight(1f), compact)
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 10.dp else 14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 5.dp else 10.dp
            )
        ) {
            Text(
                text = label,
                fontSize = if (compact) 10.sp else 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = TextStyle(
                    fontSize = if (compact) 15.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.03).em,
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

@Composable
private fun HistoryCard(
    periodStarts: List<LocalDate>,
    onRemove: (LocalDate) -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onLogFirstPeriod: () -> Unit,
    modifier: Modifier = Modifier
) {
    // O(1) reversed view since periodStarts is already sorted ascending
    val sortedDesc = remember(periodStarts) { periodStarts.asReversed() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = HistoryCardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Period History (${sortedDesc.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (sortedDesc.isNotEmpty()) {
                    Text(
                        text = "Tap a date to jump",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (sortedDesc.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MayaLogo(modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "No period start dates logged yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Log your latest period start to begin tracking your cycle and fertile windows.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onLogFirstPeriod,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyclePalette.RedPeriod,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Log First Period", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
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
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp)
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
                                .clickable { onJumpToDate(date) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = date.format(CyclePalette.HISTORY_DATE_FMT),
                                style = HistoryDateStyle,
                                modifier = Modifier.weight(1.3f)
                            )
                            Box(
                                modifier = Modifier.weight(0.9f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cycleLen != null) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    ) {
                                        Text(
                                            text = "$cycleLen days",
                                            style = HistoryBadgeStyle,
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
