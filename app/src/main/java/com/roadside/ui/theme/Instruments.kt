package com.roadside.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Animated instrument pieces: the listening waveform, the analysis sweep and the completion
 * mark.
 *
 * These visualise *state*, never data. The waveform is an "I am listening" indicator driven
 * by a clock, not by microphone amplitude — showing invented levels next to a real diagnosis
 * would be a fabricated measurement, which this app does not do.
 */

/**
 * Listening waveform. Bars breathe in a travelling wave for as long as [active] is true, and
 * settle flat when it stops.
 */
@Composable
fun ListeningWaveform(
    active: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    color: Color = RoadSideColors.amber
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "phase"
    )
    val settle = remember { Animatable(0f) }
    LaunchedEffect(active) { settle.animateTo(if (active) 1f else 0f, tween(Motion.REVEAL_MS)) }

    Canvas(modifier) {
        val gap = size.width / (barCount * 2f)
        val barWidth = gap
        val mid = size.height / 2f
        for (i in 0 until barCount) {
            // Two offset sines give an irregular, organic envelope rather than a clean pulse.
            val t = i / barCount.toFloat()
            val env = abs(sin(phase + t * 6f)) * 0.7f + abs(sin(phase * 0.5f + t * 11f)) * 0.3f
            val h = (size.height * 0.12f + size.height * 0.78f * env) * settle.value +
                size.height * 0.04f
            val x = gap + i * (barWidth + gap) * 2f
            drawLine(
                color = color.copy(alpha = 0.45f + 0.55f * env),
                start = Offset(x, mid - h / 2f),
                end = Offset(x, mid + h / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Pulsing ring behind the record control — a slow, calm "live" indicator.
 */
@Composable
fun PulseRing(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = RoadSideColors.amber
) {
    if (!active) return
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = Motion.decelerate)),
        label = "pulseP"
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(
            color = color.copy(alpha = (1f - p) * 0.35f),
            radius = r * (0.7f + 0.3f * p),
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = color.copy(alpha = (1f - p) * 0.18f),
            radius = r * (0.7f + 0.55f * p),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Analysis sweep: an indeterminate arc that rotates while on-device inference runs. Its
 * presence means "working", and it carries no percentage, because the pipeline cannot report
 * meaningful progress.
 */
@Composable
fun AnalysisSweep(
    modifier: Modifier = Modifier,
    color: Color = RoadSideColors.amber
) {
    val t = rememberInfiniteTransition(label = "sweep")
    val angle by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "sweepAngle"
    )
    val extent by t.animateFloat(
        initialValue = 40f, targetValue = 190f,
        animationSpec = infiniteRepeatable(tween(900, easing = Motion.standard), RepeatMode.Reverse),
        label = "sweepExtent"
    )
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2f
        drawArc(
            color = RoadSideColors.containerHighest,
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = angle, sweepAngle = extent, useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * Completion mark: the ring fills and a tick draws itself once, when a guide is finished.
 */
@Composable
fun CompletionMark(
    modifier: Modifier = Modifier,
    color: Color = RoadSideColors.safe
) {
    val ring = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        ring.animateTo(1f, tween(520, easing = Motion.decelerate))
        tick.animateTo(1f, tween(320, easing = Motion.standard))
    }
    Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color.copy(alpha = 0.18f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = color,
            startAngle = -90f, sweepAngle = 360f * ring.value, useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Tick: two segments drawn in sequence as `tick` advances 0 → 1.
        val w = size.width
        val a = Offset(w * 0.30f, w * 0.52f)
        val b = Offset(w * 0.44f, w * 0.66f)
        val c = Offset(w * 0.71f, w * 0.37f)
        val first = (tick.value / 0.4f).coerceIn(0f, 1f)
        val second = ((tick.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (first > 0f) {
            drawLine(color, a, Offset(a.x + (b.x - a.x) * first, a.y + (b.y - a.y) * first),
                strokeWidth = stroke, cap = StrokeCap.Round)
        }
        if (second > 0f) {
            drawLine(color, b, Offset(b.x + (c.x - b.x) * second, b.y + (c.y - b.y) * second),
                strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * Shutter flash for a camera capture: a brief white veil that fades out, plus a scale nudge
 * on the viewport supplied by the caller.
 */
@Composable
fun ShutterFlash(trigger: Int, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            alpha.snapTo(0.85f)
            alpha.animateTo(0f, tween(420, easing = Motion.decelerate))
        }
    }
    if (alpha.value > 0.001f) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha.value))
        )
    }
}

/**
 * Elapsed-time readout for an active recording, in the technical mono face.
 */
@Composable
fun ElapsedReadout(seconds: Int, modifier: Modifier = Modifier) {
    val text = "%02d:%02d".format(seconds / 60, seconds % 60)
    androidx.compose.material3.Text(
        text,
        style = RoadSideType.labelDataLg,
        color = RoadSideColors.primary,
        modifier = modifier
    )
}

/** Circular icon well used for leading glyphs inside cards. */
@Composable
fun IconWell(
    modifier: Modifier = Modifier,
    tint: Color = RoadSideColors.primary,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoadSideShapes.pill)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** A thin progress rail used by the guide. Animates smoothly between steps. */
@Composable
fun ProgressRail(progress: Float, modifier: Modifier = Modifier) {
    val p by androidx.compose.animation.core.animateFloatAsState(
        progress.coerceIn(0f, 1f),
        tween(Motion.SCREEN_MS, easing = Motion.standard),
        label = "rail"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoadSideShapes.pill)
            .background(RoadSideColors.containerHighest)
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(8.dp)
                .clip(RoadSideShapes.pill)
                .background(RoadSideColors.amber)
        )
    }
}

/** Scales content in from slightly small — used for result cards. */
@Composable
fun AppearScale(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val s = remember { Animatable(0.94f) }
    LaunchedEffect(visible) { if (visible) s.animateTo(1f, Motion.settle()) }
    Box(modifier.scale(s.value)) { content() }
}
