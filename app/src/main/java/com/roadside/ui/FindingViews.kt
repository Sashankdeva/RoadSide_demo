package com.roadside.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.roadside.assist.Finding
import com.roadside.assist.FindingTone
import com.roadside.ui.theme.Gap
import com.roadside.ui.theme.IconWell
import com.roadside.ui.theme.InstrumentCard
import com.roadside.ui.theme.Motion
import com.roadside.ui.theme.RoadSideColors
import com.roadside.ui.theme.RoadSideShapes
import com.roadside.ui.theme.RoadSideType
import com.roadside.ui.theme.SoftDivider
import com.roadside.ui.theme.Space

/** Colour for a finding's tone — the DESIGN.md telemetry semantics. */
fun FindingTone.color(): Color = when (this) {
    FindingTone.ADVISORY -> RoadSideColors.advisory
    FindingTone.CONFIRMED -> RoadSideColors.safe
    FindingTone.NEUTRAL -> RoadSideColors.onSurfaceVariant
}

private fun FindingTone.icon(): ImageVector = when (this) {
    FindingTone.ADVISORY -> Icons.Default.ReportProblem
    FindingTone.CONFIRMED -> Icons.Default.CheckCircle
    FindingTone.NEUTRAL -> Icons.Default.HelpOutline
}

/**
 * A card of observations under one heading ("What we heard", "What we saw").
 *
 * Rows reveal one after another — the evidence-reveal motion — so a photo that produced
 * several observations reads as a list being filled in rather than a block appearing.
 */
@Composable
fun FindingsCard(heading: String, findings: List<Finding>, modifier: Modifier = Modifier) {
    InstrumentCard(shape = RoadSideShapes.enclosure, modifier = modifier.fillMaxWidth()) {
        Text(heading, style = RoadSideType.labelDataSm, color = RoadSideColors.onSurfaceVariant)
        Gap(Space.sm + Space.xs)
        findings.forEachIndexed { i, f ->
            if (i > 0) {
                Gap(Space.sm + Space.xs)
                SoftDivider()
                Gap(Space.sm + Space.xs)
            }
            StaggeredReveal(i) { FindingRow(f) }
        }
    }
}

/** One observation: tone icon, title, optional plain-language detail. */
@Composable
fun FindingRow(f: Finding) {
    val tint = f.tone.color()
    Row(verticalAlignment = Alignment.Top) {
        IconWell(tint = tint) {
            Icon(f.tone.icon(), null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Gap(Space.sm + Space.xs)
        Column(Modifier.weight(1f)) {
            Text(f.title, style = RoadSideType.headlineMd, color = RoadSideColors.onSurface)
            if (f.detail != null) {
                Gap(Space.xs)
                Text(f.detail, style = RoadSideType.bodyMd, color = RoadSideColors.onSurfaceVariant)
            }
        }
    }
}

/** Fades and lifts content in, delayed by [index] so siblings arrive in sequence. */
@Composable
fun StaggeredReveal(index: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(Motion.REVEAL_MS, delayMillis = index * 80)) +
            slideInVertically(
                tween(Motion.REVEAL_MS, delayMillis = index * 80, easing = Motion.decelerate)
            ) { it / 4 }
    ) { content() }
}
