package com.roadside.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Stitch component vocabulary. Screens assemble these rather than styling raw Material
 * components, which is what keeps curvature, spacing and colour identical everywhere.
 */

// ── Surfaces ─────────────────────────────────────────────────────────────────

/**
 * An instrument card: a continuous surface that groups related readouts.
 *
 * Depth comes from the tonal step between [color] and the page behind it, not from a shadow.
 */
@Composable
fun InstrumentCard(
    modifier: Modifier = Modifier,
    color: Color = RoadSideColors.container,
    shape: Shape = RoadSideShapes.card,
    outlined: Boolean = false,
    padding: Dp = Space.md,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .then(
                if (outlined) Modifier.border(1.dp, RoadSideColors.outlineSubtle, shape)
                else Modifier
            )
            .padding(padding),
        content = content
    )
}

/** A recessed well — waveforms, viewports, inset readouts sit in these. */
@Composable
fun TelemetryWell(
    modifier: Modifier = Modifier,
    shape: Shape = RoadSideShapes.well,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(RoadSideColors.containerLowest),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ── Controls ─────────────────────────────────────────────────────────────────

/**
 * The primary operational trigger: Safety Amber, 56 dp, deep bold type.
 *
 * Press feedback is a spring-backed scale — the control yields to the finger.
 */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = rememberInteraction()
) {
    val scale by rememberPressScale(interactionSource)
    val pressedTint by animateColorAsState(
        if (scale < 0.995f) RoadSideColors.amberPressed else RoadSideColors.amber,
        label = "primaryTint"
    )
    val bg = if (enabled) pressedTint else RoadSideColors.containerHigh
    val fg = if (enabled) RoadSideColors.onAmber else RoadSideColors.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .heightIn(min = 56.dp)
            .clip(RoadSideShapes.control)
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.sm + Space.xs))
        }
        Text(
            text,
            style = RoadSideType.headlineMd,
            color = fg,
            modifier = Modifier.weight(1f, fill = false),
            textAlign = TextAlign.Center
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(Space.sm + Space.xs))
            Icon(trailingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        }
    }
}

/** Secondary instrument control — a touch surface, not an outline. */
@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    tint: Color = RoadSideColors.onSurface,
    container: Color = RoadSideColors.containerHigh,
    interactionSource: MutableInteractionSource = rememberInteraction()
) {
    val scale by rememberPressScale(interactionSource)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .heightIn(min = Space.touchTarget)
            .clip(RoadSideShapes.control)
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm + Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text, style = RoadSideType.bodyLg, color = tint, textAlign = TextAlign.Center)
    }
}

/** A selectable pill. Active state carries Safety Amber. */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = rememberInteraction()
    val scale by rememberPressScale(interaction, pressedScale = 0.95f)
    val bg by animateColorAsState(
        if (selected) RoadSideColors.amber else RoadSideColors.containerHigh,
        tween(Motion.FAST_MS), label = "chipBg"
    )
    val fg by animateColorAsState(
        if (selected) RoadSideColors.onAmber else RoadSideColors.onSurface,
        tween(Motion.FAST_MS), label = "chipFg"
    )
    Row(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 40.dp)
            .clip(RoadSideShapes.pill)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = RoadSideType.bodyMd, color = fg)
    }
}

// ── Status & labels ──────────────────────────────────────────────────────────

/** Semantic pill badge — safe / advisory / critical, per automotive convention. */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false
) {
    val alpha = if (pulsing) {
        val t = rememberInfiniteTransition(label = "badgePulse")
        t.animateFloat(
            initialValue = 0.45f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = Motion.standard), RepeatMode.Reverse),
            label = "badgeAlpha"
        ).value
    } else 1f

    Row(
        modifier = modifier
            .clip(RoadSideShapes.pill)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Space.sm + Space.xs, vertical = Space.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoadSideShapes.pill)
                .background(color.copy(alpha = alpha))
        )
        Spacer(Modifier.width(Space.sm - 2.dp))
        Text(text, style = RoadSideType.labelDataSm, color = color)
    }
}

/** Small uppercase section eyebrow in the accent colour. */
@Composable
fun Eyebrow(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = RoadSideColors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm - 2.dp))
        }
        Text(
            text.uppercase(),
            style = RoadSideType.labelDataSm,
            color = RoadSideColors.primary
        )
    }
}

/**
 * Step flow indicator — the progress slider only.
 *
 * Segments fill as the rider advances and the current one stretches, so moving between steps
 * reads as progress through one check. There is no "Step 2 of 4" text: the slider carries it,
 * and [label] is kept for screen readers.
 */
@Composable
fun StepIndicator(
    step: Int,
    total: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    // Compact rail at the end of the row, where it sat beside the old "Step n of 4" text.
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Step $step of $total: $label" },
        horizontalArrangement = Arrangement.End
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs + 2.dp)) {
            repeat(total) { i ->
                val w by androidx.compose.animation.core.animateDpAsState(
                    if (i == step - 1) 28.dp else 12.dp,
                    tween(Motion.SCREEN_MS, easing = Motion.standard), label = "railW"
                )
                val c by animateColorAsState(
                    if (i < step) RoadSideColors.amber else RoadSideColors.containerHighest,
                    tween(Motion.SCREEN_MS), label = "railC"
                )
                Box(
                    Modifier
                        .width(w)
                        .height(6.dp)
                        .clip(RoadSideShapes.pill)
                        .background(c)
                )
            }
        }
    }
}

/** Section title with an optional trailing hint, used above groups of controls. */
@Composable
fun SectionHeader(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = RoadSideType.headlineMd, color = RoadSideColors.onSurface)
        if (hint != null) {
            Text(hint, style = RoadSideType.bodySm, color = RoadSideColors.onSurfaceVariant)
        }
    }
}

/** A labelled row inside a card: human label left, technical value right. */
@Composable
fun ReadoutRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = RoadSideColors.onSurface
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = RoadSideType.bodyMd, color = RoadSideColors.onSurfaceVariant)
        Text(value, style = RoadSideType.labelDataMd, color = valueColor)
    }
}

/** Divider used inside nested surfaces — an inset tonal step, not a hard line. */
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RoadSideColors.outlineSubtle)
    )
}

@Composable
fun RowScope.Gap(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun ColumnScope.Gap(height: Dp) = Spacer(Modifier.height(height))

/** Rounded corner helper for one-off shapes that still belong to the system. */
fun roundedAll(dp: Dp) = RoundedCornerShape(dp)
