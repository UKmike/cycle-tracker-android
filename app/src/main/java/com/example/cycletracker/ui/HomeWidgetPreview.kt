package com.example.cycletracker.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.cycletracker.domain.CycleModel
import com.example.cycletracker.domain.DayClassification
import com.example.cycletracker.domain.FertilityStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val CardCornerShape = RoundedCornerShape(24.dp)
private val PillShape = RoundedCornerShape(50)
private val CardSheenBrush = Brush.verticalGradient(
    colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
)
private val CardBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.48f),
        Color.White.copy(alpha = 0.12f)
    )
)
private val GravityDescentEasing = CubicBezierEasing(0.32f, 0f, 0.85f, 0.36f)
private val ZeroFloatProvider: () -> Float = { 0f }

private val HeroCountStyle = TextStyle(
    fontSize = 44.sp,
    fontWeight = FontWeight.ExtraBold,
    lineHeight = 44.sp,
    letterSpacing = (-0.04).em,
    fontFeatureSettings = "tnum"
)
private val HeroFooterStyle = TextStyle(
    fontSize = 11.sp,
    fontFeatureSettings = "tnum"
)

private val PeriodGradColors = listOf(Color(0xFFEF4444), CyclePalette.RedPeriod, Color(0xFFB91C1C))
private val LikelyPeriodGradColors = listOf(CyclePalette.RedPredicted, Color(0xFFF87171), Color(0xFFEF4444))
private val FertileGradColors = listOf(CyclePalette.GreenBright, CyclePalette.GreenFertile, CyclePalette.GreenDark)
private val CouldBeGradColors = listOf(Color(0xFFFDE047), CyclePalette.YellowCouldBe, Color(0xFFF59E0B))
private val NotFertileGradColors = listOf(Color(0xFF7DD3FC), CyclePalette.BlueNotFertile, Color(0xFF0EA5E9))

@Composable
internal fun MayaLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val strokeWidth = w * 0.105f
            val ringRadius = (size.minDimension - strokeWidth) / 2f
            val ringStroke = Stroke(width = strokeWidth)
            val ringBrush = Brush.sweepGradient(
                0.00f to Color(0xFF8A7390),
                0.125f to CyclePalette.BlueNotFertile,
                0.25f to Color(0xFF9AC08E),
                0.375f to CyclePalette.YellowCouldBe,
                0.50f to Color(0xFF80AB47),
                0.625f to CyclePalette.GreenFertile,
                0.75f to Color(0xFF705E47),
                0.875f to CyclePalette.RedPeriod,
                1.00f to Color(0xFF8A7390),
                center = center
            )

            val heart = Path().apply {
                moveTo(w * 0.50f, h * 0.42f)
                cubicTo(w * 0.50f, h * 0.34f, w * 0.30f, h * 0.32f, w * 0.30f, h * 0.48f)
                cubicTo(w * 0.30f, h * 0.61f, w * 0.50f, h * 0.72f, w * 0.50f, h * 0.75f)
                cubicTo(w * 0.50f, h * 0.72f, w * 0.70f, h * 0.61f, w * 0.70f, h * 0.48f)
                cubicTo(w * 0.70f, h * 0.32f, w * 0.50f, h * 0.34f, w * 0.50f, h * 0.42f)
                close()
            }

            val highlight = Path().apply {
                moveTo(w * 0.365f, h * 0.435f)
                cubicTo(w * 0.38f, h * 0.398f, w * 0.42f, h * 0.392f, w * 0.445f, h * 0.412f)
                cubicTo(w * 0.418f, h * 0.42f, w * 0.392f, h * 0.444f, w * 0.38f, h * 0.48f)
                cubicTo(w * 0.37f, h * 0.468f, w * 0.36f, h * 0.452f, w * 0.365f, h * 0.435f)
                close()
            }

            onDrawBehind {
                drawCircle(color = Color(0xFFFFF8F6), radius = ringRadius, center = center)
                drawCircle(brush = ringBrush, radius = ringRadius, center = center, style = ringStroke)
                drawPath(path = heart, color = CyclePalette.RedPeriod)
                drawPath(path = highlight, color = CyclePalette.RedPredicted)
            }
        }
    )
}

private fun heroThemeFor(info: DayClassification): Triple<List<Color>, Color, String> = when {
    info.isLoggedPeriod -> Triple(PeriodGradColors, Color.White, "Period")
    info.isPredictedPeriod -> Triple(LikelyPeriodGradColors, Color(0xFF7F1D1D), "Likely Period")
    info.fertility == FertilityStatus.FERTILE -> Triple(FertileGradColors, Color.White, "Fertile")
    info.fertility == FertilityStatus.COULD_BE_FERTILE -> Triple(CouldBeGradColors, Color(0xFF451A03), "Could Be Fertile")
    else -> Triple(NotFertileGradColors, Color(0xFF082F49), "Not Fertile")
}

@Composable
internal fun HomeWidgetPreviewCard(
    todayInfo: DayClassification,
    midStageTransition: CyclePalette.MidStageTransition?,
    hasData: Boolean,
    periodStarts: List<LocalDate>,
    model: CycleModel,
    periodLength: Int,
    modifier: Modifier = Modifier
) {
    val (todayGradColors, todayContentColor, statusText) = heroThemeFor(todayInfo)
    val gradColors: List<Color>
    val contentColor: Color

    if (midStageTransition != null) {
        val (partnerGradColors, _, _) = heroThemeFor(midStageTransition.partnerInfo)
        gradColors = remember(todayGradColors, partnerGradColors) {
            List(todayGradColors.size) { i ->
                Color(CyclePalette.blendArgb(todayGradColors[i].toArgb(), partnerGradColors[i].toArgb(), 0.5f))
            }
        }
        contentColor = if (gradColors[1].luminance() > 0.26f) Color(0xFF111827) else Color.White
    } else {
        gradColors = todayGradColors
        contentColor = todayContentColor
    }

    val cardBgBrush = remember(gradColors) { Brush.linearGradient(colors = gradColors) }
    val cycleColors = remember(hasData, periodStarts, model, periodLength) {
        if (hasData && periodStarts.isNotEmpty()) {
            CyclePalette.computeCycleDayArgbColors(periodStarts, model, periodLength)
        } else {
            IntArray(0)
        }
    }

    val ringBackingColor = MaterialTheme.colorScheme.surface
    val reducedMotion = rememberReducedMotion()

    val isPeriodWave = todayInfo.isAnyPeriod
    val isFertileWave = !isPeriodWave && todayInfo.fertility == FertilityStatus.FERTILE
    val isNotFertileWave = !isPeriodWave && todayInfo.fertility == FertilityStatus.NOT_FERTILE
    val primaryDurationMs = when {
        isNotFertileWave -> 6200
        isFertileWave -> 3800
        else -> 4600
    }
    val secondaryDurationMs = (primaryDurationMs * 1.9f).toInt()
    val waveAmpDp = when {
        isFertileWave -> 14.dp
        isNotFertileWave -> 7.dp
        else -> 10.dp
    }

    val primaryPhaseProvider: () -> Float
    val secondaryPhaseProvider: () -> Float
    if (reducedMotion) {
        primaryPhaseProvider = ZeroFloatProvider
        secondaryPhaseProvider = ZeroFloatProvider
    } else {
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
        primaryPhaseProvider = remember(primaryPhaseState) { { primaryPhaseState.value } }
        secondaryPhaseProvider = remember(secondaryPhaseState) { { secondaryPhaseState.value } }
    }

    val jumpScope = rememberCoroutineScope()
    val jumpOffsetFraction = remember { Animatable(0f) }
    val landingSplash = remember { Animatable(0f) }
    var jumpJob by remember { mutableStateOf<Job?>(null) }

    // Normalized height [0f..1f] of the floating wave crest dot at x = 0.78 * width.
    // Read only inside draw lambdas so animation frames don't trigger recomposition.
    val dotGlowLevel: () -> Float = remember(
        reducedMotion,
        primaryPhaseProvider,
        secondaryPhaseProvider,
        jumpOffsetFraction
    ) {
        val dotAngle1 = (2.0 * PI * (0.78 / 0.72)).toFloat()
        val dotAngle2 = (2.0 * PI * (0.78 / 1.44)).toFloat()
        val provider: () -> Float = if (reducedMotion) {
            { 0.4f }
        } else {
            {
                val s = sin((dotAngle1 + primaryPhaseProvider()).toDouble()).toFloat()
                val c = cos((dotAngle2 - secondaryPhaseProvider()).toDouble()).toFloat()
                val rawNorm = (s + 0.45f * c) / 1.45f
                val waveHeight = ((1f - rawNorm) * 0.5f).coerceIn(0f, 1f)
                (waveHeight + jumpOffsetFraction.value * 0.65f).coerceIn(0f, 1f)
            }
        }
        provider
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (reducedMotion) return@clickable
                jumpJob?.cancel()
                jumpJob = jumpScope.launch {
                    landingSplash.snapTo(0f)
                    // Launch or boost smoothly from current airborne height
                    jumpOffsetFraction.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = FastOutSlowInEasing
                        )
                    )
                    jumpOffsetFraction.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 340,
                            easing = GravityDescentEasing
                        )
                    )
                    landingSplash.snapTo(0.01f)
                    landingSplash.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 420,
                            easing = FastOutSlowInEasing
                        )
                    )
                    landingSplash.snapTo(0f)
                }
            }
    ) {
        val glowBlur = (maxWidth * 0.038f).coerceAtMost(14.dp)

        // 1. Pre-rendered ambient glow behind the card — computed ONCE per (size, cycleColors)
        //    into a padded offscreen Bitmap so each frame is a single GPU textured quad blit
        //    instead of running a full-card Gaussian blur at 60-120 fps.
        if (cycleColors.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f) {
                            return@drawWithCache onDrawBehind {}
                        }
                        val blurPx = glowBlur.toPx().coerceAtLeast(1f)
                        val r = 28.dp.toPx().coerceAtMost(size.minDimension / 2f)
                        // A Gaussian kernel with radius >= 12px has negligible energy above 0.5x Nyquist,
                        // giving 4x less bitmap RAM and texture bandwidth with identical visual softness.
                        val bitmapScale = if (blurPx >= 12f) 0.5f else 1f
                        val padPx = ceil(blurPx * 2.5f + 4f)
                        val bmpW = max(1, ceil((w + padPx * 2f) * bitmapScale).toInt())
                        val bmpH = max(1, ceil((h + padPx * 2f) * bitmapScale).toInt())
                        val glowBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        val offCanvas = AndroidCanvas(glowBitmap)
                        offCanvas.scale(bitmapScale, bitmapScale)
                        offCanvas.translate(padPx, padPx)
                        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            shader = cycleRingSweepShader(size, cycleColors)
                            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                        }
                        offCanvas.drawRoundRect(0f, 0f, w, h, r, r, glowPaint)

                        val dstRect = RectF(-padPx, -padPx, w + padPx, h + padPx)
                        val blitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                        onDrawBehind {
                            val b = dotGlowLevel()
                            val sx = lerpFloat(0.97f, 1.022f, b)
                            val sy = lerpFloat(0.97f, 1.075f, b)
                            val a = lerpFloat(0.14f, 0.85f, b)
                            blitPaint.alpha = (a * 255f).toInt().coerceIn(0, 255)
                            scale(scaleX = sx, scaleY = sy, pivot = center) {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawBitmap(glowBitmap, null, dstRect, blitPaint)
                                }
                            }
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(CardCornerShape)
                .background(cardBgBrush)
                .background(CardSheenBrush)
                .border(
                    width = 1.dp,
                    brush = CardBorderBrush,
                    shape = CardCornerShape
                )
        ) {
            FlowingCycleTideOverlay(
                ampDp = waveAmpDp,
                contentColor = contentColor,
                primaryPhase = primaryPhaseProvider,
                secondaryPhase = secondaryPhaseProvider,
                jumpHeightFraction = { jumpOffsetFraction.value },
                landingSplash = { landingSplash.value },
                modifier = Modifier.matchParentSize()
            )
            Column(modifier = Modifier.padding(14.dp)) {
                val displayStatus = if (hasData && todayInfo.daysLate > 0) "Period Late" else statusText
                Surface(
                    color = contentColor.copy(alpha = 0.18f),
                    shape = PillShape
                ) {
                    Text(
                        text = displayStatus,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.02.em,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                val displayCount = when {
                    !hasData -> null
                    todayInfo.daysLate > 0 -> todayInfo.daysLate
                    else -> todayInfo.daysToNextPeriod
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = displayCount?.toString() ?: "--",
                        color = contentColor,
                        style = HeroCountStyle
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (displayCount == 1) "day" else "days",
                        color = contentColor.copy(alpha = 0.92f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.015).em,
                        modifier = Modifier.padding(bottom = 5.dp)
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
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em
                )

                if (hasData && todayInfo.nextPeriodDate != null) {
                    Spacer(Modifier.height(6.dp))
                    val footerText = if (todayInfo.daysLate > 0 && todayInfo.expectedPeriodDate != null) {
                        "Cycle Day ${model.averageCycleLength + todayInfo.daysLate}  •  Expected: ${
                            todayInfo.expectedPeriodDate.format(CyclePalette.SHORT_DATE_FMT)
                        }"
                    } else {
                        "Cycle Day ${todayInfo.cycleDay}  •  Next period: ${
                            todayInfo.nextPeriodDate.format(CyclePalette.SHORT_DATE_FMT)
                        }"
                    }
                    Text(
                        text = footerText,
                        color = contentColor.copy(alpha = 0.85f),
                        style = HeroFooterStyle
                    )
                }
            }
        }

        if (cycleColors.isNotEmpty()) {
            // 2. Pre-rendered static cycle perimeter ring (backing track + 224 smooth arc-length
            //    segments + top-center white tick) cached into a native-1x padded Bitmap so every
            //    animation frame blits 1 texture instead of tessellating 226 stroked paths.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f) {
                            return@drawWithCache onDrawBehind {}
                        }
                        val strokeWidth = 6.dp.toPx()
                        val backingExtra = 3.dp.toPx()
                        val tickStroke = 2.dp.toPx()
                        val pad = ceil(strokeWidth + 4.dp.toPx()).toInt()
                        val bmpW = max(1, ceil(w).toInt() + pad * 2)
                        val bmpH = max(1, ceil(h).toInt() + pad * 2)
                        val ringBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        val offCanvas = AndroidCanvas(ringBitmap)
                        offCanvas.translate(pad.toFloat(), pad.toFloat())

                        val inset = strokeWidth / 2f
                        val cornerRadius = 28.dp.toPx().coerceAtMost(
                            (size.minDimension - strokeWidth) / 2f
                        )
                        val top = inset
                        val midX = w / 2f

                        val perimeter = buildAndroidPerimeterPath(
                            width = w,
                            height = h,
                            strokeWidth = strokeWidth,
                            cornerRadius = cornerRadius
                        )

                        // Backing track
                        val backingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            this.strokeWidth = strokeWidth + backingExtra
                            strokeCap = Paint.Cap.BUTT
                            color = ringBackingColor.toArgb()
                        }
                        offCanvas.drawPath(perimeter, backingPaint)

                        // Smooth arc-length-accurate color segments
                        val pm = AndroidPathMeasure(perimeter, false)
                        val totalLength = pm.length
                        val segPath = AndroidPath()
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
                            offCanvas.drawPath(segPath, segPaint)
                        }

                        // Top-center tick marking Cycle Day 1 (period start)
                        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            this.strokeWidth = tickStroke
                            strokeCap = Paint.Cap.ROUND
                            color = android.graphics.Color.WHITE
                        }
                        offCanvas.drawLine(
                            midX, top - strokeWidth * 0.6f,
                            midX, top + strokeWidth * 0.6f,
                            tickPaint
                        )

                        val padF = pad.toFloat()
                        onDrawBehind {
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawBitmap(ringBitmap, -padF, -padF, null)
                            }
                        }
                    }
            )

            // 3. Subtle ring brightness boost synced to the floating wave dot's height
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

            // 4. Current cycle day marker with quiet pulsing ripple (position cached in drawWithCache).
            //    During a midday-to-midday transition, move halfway between the two days and blend colors.
            val currentDay = todayInfo.cycleDay
            if (currentDay != null) {
                val count = cycleColors.size
                val clampedIndex = ((currentDay - 1) % count + count) % count
                val daySlot = when (midStageTransition?.dayDirection) {
                    -1 -> clampedIndex.toFloat()
                    +1 -> ((clampedIndex + 1f) % count)
                    else -> clampedIndex + 0.5f
                }
                val partnerIndex = when (midStageTransition?.dayDirection) {
                    -1 -> (clampedIndex - 1 + count) % count
                    +1 -> (clampedIndex + 1) % count
                    else -> null
                }
                PulsingDayMarkerOverlay(
                    dayFraction = daySlot / count,
                    phaseArgb = cycleColors[clampedIndex],
                    partnerPhaseArgb = partnerIndex?.let { cycleColors[it] },
                    reducedMotion = reducedMotion,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

private fun buildAndroidPerimeterPath(
    width: Float,
    height: Float,
    strokeWidth: Float,
    cornerRadius: Float
): AndroidPath {
    val inset = strokeWidth / 2f
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    val midX = width / 2f
    val arcRect = RectF()
    return AndroidPath().apply {
        moveTo(midX, top)
        lineTo(right - cornerRadius, top)
        arcRect.set(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius)
        arcTo(arcRect, -90f, 90f, false)
        lineTo(right, bottom - cornerRadius)
        arcRect.set(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom)
        arcTo(arcRect, 0f, 90f, false)
        lineTo(left + cornerRadius, bottom)
        arcRect.set(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom)
        arcTo(arcRect, 90f, 90f, false)
        lineTo(left, top + cornerRadius)
        arcRect.set(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius)
        arcTo(arcRect, 180f, 90f, false)
        lineTo(midX, top)
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
    phaseArgb: Int,
    partnerPhaseArgb: Int?,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val packed = CyclePalette.markerColorsArgb(phaseArgb, partnerPhaseArgb)
    val haloColor = Color(CyclePalette.unpackHalo(packed))
    val dotColor = Color(CyclePalette.unpackDot(packed))

    val pulseProvider: () -> Float = if (reducedMotion) {
        { 1f }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "markerPulse")
        val pulseState = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseProgress"
        )
        remember(pulseState) { { pulseState.value } }
    }

    Box(
        modifier = modifier.drawWithCache {
            val strokeWidth = 6.dp.toPx()
            val cornerRadius = 28.dp.toPx().coerceAtMost(
                (size.minDimension - strokeWidth) / 2f
            )
            val perimeterPath = Path()
            populateWidgetPerimeterPath(
                path = perimeterPath,
                width = size.width,
                height = size.height,
                strokeWidth = strokeWidth,
                cornerRadius = cornerRadius
            )
            val pathMeasure = PathMeasure()
            pathMeasure.setPath(perimeterPath, forceClosed = false)
            val markerPos = pathMeasure.getPosition(pathMeasure.length * dayFraction)
            val haloRadius = 9.5.dp.toPx()
            val dotRadius = 6.dp.toPx()
            val maxRippleExpand = 13.dp.toPx()
            val baseRippleStroke = 2.2.dp.toPx()

            onDrawBehind {
                val pulseProgress = pulseProvider()
                if (pulseProgress < 0.72f) {
                    val prog = pulseProgress / 0.72f
                    val inv = 1f - prog
                    val eased = 1f - inv * inv * inv
                    val rippleRadius = haloRadius + eased * maxRippleExpand
                    val rippleAlpha = inv * 0.75f
                    val rippleStroke = baseRippleStroke * (1f - prog * 0.4f)
                    drawCircle(
                        color = haloColor.copy(alpha = rippleAlpha),
                        radius = rippleRadius,
                        center = markerPos,
                        style = Stroke(width = rippleStroke)
                    )
                }

                drawCircle(color = haloColor, radius = haloRadius, center = markerPos)
                drawCircle(color = dotColor, radius = dotRadius, center = markerPos)
            }
        }
    )
}

@Composable
private fun FlowingCycleTideOverlay(
    ampDp: Dp,
    contentColor: Color,
    primaryPhase: () -> Float,
    secondaryPhase: () -> Float,
    jumpHeightFraction: () -> Float,
    landingSplash: () -> Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) {
                return@drawWithCache onDrawBehind {}
            }

            val amp = ampDp.toPx()
            val amp2 = amp * 0.45f
            val freq1 = (2.0 * PI / (w * 0.72f)).toFloat()
            val freq2 = (2.0 * PI / (w * 1.44f)).toFloat()
            val stepPx = 6.dp.toPx().coerceAtLeast(4f)
            val baseY1 = h * 0.64f
            val baseY2 = h * 0.75f
            val layer1Color = contentColor.copy(alpha = 0.14f)
            val layer2Color = contentColor.copy(alpha = 0.10f)
            val dotX = w * 0.78f
            val topMarginPx = 14.dp.toPx()
            val minJumpPx = 24.dp.toPx()
            val maxJumpCapPx = 54.dp.toPx()
            val dp1 = 1.dp.toPx()
            val dotCoreRadius = 4.dp.toPx()
            val dxStepPx = 6.0f * dp1

            val wavePath1 = Path()
            val wavePath2 = Path()

            // Zero-allocation circular buffer of recent airborne (timestampNs, jumpHeight) samples
            // so the trail follows the true trajectory across 60/90/120 Hz and mid-air boosts.
            val histCap = 32
            val histTimeNs = LongArray(histCap)
            val histHeight = FloatArray(histCap)
            var histHead = 0
            var histCount = 0

            onDrawBehind {
                val p1 = primaryPhase()
                val p2 = secondaryPhase()
                val p1Layer2 = p1 + 1.8f

                wavePath1.reset()
                wavePath2.reset()
                wavePath1.moveTo(0f, h)
                wavePath2.moveTo(0f, h)

                // Single pass evaluates the shared secondary cos() term once per x with zero heap allocs
                var x = 0f
                while (x < w) {
                    val xf1 = x * freq1
                    val sec = cos(x * freq2 - p2) * amp2
                    wavePath1.lineTo(x, baseY1 + sin(xf1 + p1) * amp + sec)
                    wavePath2.lineTo(x, baseY2 + sin(xf1 + p1Layer2) * amp + sec)
                    x += stepPx
                }
                val endSec = cos(w * freq2 - p2) * amp2
                val wf1 = w * freq1
                wavePath1.lineTo(w, baseY1 + sin(wf1 + p1) * amp + endSec)
                wavePath1.lineTo(w, h)
                wavePath1.close()
                wavePath2.lineTo(w, baseY2 + sin(wf1 + p1Layer2) * amp + endSec)
                wavePath2.lineTo(w, h)
                wavePath2.close()

                drawPath(path = wavePath1, color = layer1Color)
                drawPath(path = wavePath2, color = layer2Color)

                // Glowing crest dot riding the top wave (launches or boosts smoothly when tapped)
                val waveSurfaceY = baseY1 +
                    sin(dotX * freq1 + p1) * amp +
                    cos(dotX * freq2 - p2) * amp2
                val maxJumpPx = (waveSurfaceY - topMarginPx).coerceIn(minJumpPx, maxJumpCapPx)
                val j = jumpHeightFraction().coerceIn(0f, 1f)
                val dotY = waveSurfaceY - j * maxJumpPx

                val splash = landingSplash()
                if (splash > 0.01f && splash < 0.99f) {
                    drawCircle(
                        color = contentColor.copy(alpha = (1f - splash) * 0.55f),
                        radius = (6f + splash * 14f) * dp1,
                        center = Offset(dotX, waveSurfaceY),
                        style = Stroke(width = (1.8f * (1f - splash * 0.5f)) * dp1)
                    )
                }

                // True-trajectory comet trail: bottom-left on takeoff -> horizontal-left at peak ->
                // top-left on descent, and curves naturally through mid-air boost re-launches.
                if (j > 0.005f) {
                    val nowNs = System.nanoTime()
                    val lastIdx = (histHead - 1 + histCap) % histCap
                    if (histCount == 0 || nowNs - histTimeNs[lastIdx] >= 2_000_000L) {
                        histTimeNs[histHead] = nowNs
                        histHeight[histHead] = j
                        histHead = (histHead + 1) % histCap
                        if (histCount < histCap) histCount++
                    }

                    if (histCount >= 2) {
                        val oldestIdx = (histHead - histCount + histCap) % histCap
                        val oldestNs = histTimeNs[oldestIdx]
                        val edgeFade = (j / 0.14f).coerceIn(0f, 1f)
                        val dtStepNs = 34_000_000L

                        for (i in 1..3) {
                            val targetNs = nowNs - i * dtStepNs
                            if (targetNs < oldestNs) continue

                            // Find bracketing samples around targetNs
                            var jTrail = histHeight[oldestIdx]
                            for (k in 1 until histCount) {
                                val idxB = (histHead - k + histCap) % histCap
                                val idxA = (histHead - k - 1 + histCap) % histCap
                                val tB = histTimeNs[idxB]
                                val tA = histTimeNs[idxA]
                                if (targetNs in tA..tB) {
                                    val span = (tB - tA).coerceAtLeast(1L)
                                    val fracT = (targetNs - tA).toFloat() / span.toFloat()
                                    jTrail = lerpFloat(histHeight[idxA], histHeight[idxB], fracT)
                                    break
                                }
                            }

                            val trailX = dotX - i * dxStepPx
                            val trailY = waveSurfaceY - jTrail * maxJumpPx
                            val frac = i / 3f
                            val entryFade = ((targetNs - oldestNs) / 28_000_000f).coerceIn(0f, 1f)
                            val alpha = 0.38f * (1f - frac * 0.65f) * entryFade * edgeFade
                            drawCircle(
                                color = contentColor.copy(alpha = alpha),
                                radius = (3.4f - i * 0.7f) * dp1,
                                center = Offset(trailX, trailY)
                            )
                        }
                    }
                } else {
                    histCount = 0
                }

                drawCircle(
                    color = contentColor.copy(alpha = lerpFloat(0.28f, 0.44f, j)),
                    radius = lerpFloat(9f, 11.5f, j) * dp1,
                    center = Offset(dotX, dotY)
                )
                drawCircle(
                    color = contentColor.copy(alpha = lerpFloat(0.75f, 0.95f, j)),
                    radius = dotCoreRadius,
                    center = Offset(dotX, dotY)
                )
            }
        }
    )
}

internal fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun cycleRingSweepShader(size: Size, cycleColors: IntArray): Shader {
    val count = cycleColors.size
    val samples = (count * 4).coerceAtLeast(32)
    val colors = IntArray(samples + 1)
    val stops = FloatArray(samples + 1)
    for (i in 0..samples) {
        val frac = i.toFloat() / samples
        val dayPos = (frac * count).coerceIn(0.01f, count - 0.01f)
        colors[i] = CyclePalette.sampleSmoothCycleColorArgb(cycleColors, dayPos)
        stops[i] = frac
    }
    val cx = size.width / 2f
    val cy = size.height / 2f
    return SweepGradient(cx, cy, colors, stops).apply {
        setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cy) })
    }
}

private fun cycleRingSweepBrush(size: Size, cycleColors: IntArray): ShaderBrush {
    val shader = cycleRingSweepShader(size, cycleColors)
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader = shader
    }
}

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
