package com.roadside.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.roadside.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roadside.agent.VehicleContext
import com.roadside.ui.theme.Eyebrow
import com.roadside.ui.theme.Gap
import com.roadside.ui.theme.IconWell
import com.roadside.ui.theme.InstrumentCard
import com.roadside.ui.theme.PrimaryAction
import com.roadside.ui.theme.RoadSideColors
import com.roadside.ui.theme.RoadSideShapes
import com.roadside.ui.theme.RoadSideType
import com.roadside.ui.theme.SecondaryAction
import com.roadside.ui.theme.SelectChip
import com.roadside.ui.theme.SectionHeader
import com.roadside.ui.theme.Space
import com.roadside.ui.theme.StatusBadge
import com.roadside.ui.theme.StepIndicator
import com.roadside.ui.theme.rememberInteraction
import com.roadside.ui.theme.rememberPressScale

/**
 * Shared page chrome: emblem, title, vehicle pill, and the scrolling content column.
 *
 * Every screen uses this so the header geometry, gutters and scroll behaviour are identical
 * across the app rather than re-specified per screen.
 */
@Composable
fun ScreenScaffold(
    title: String,
    vehicle: String? = null,
    onVehicleChanged: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    /** Pass one in to scroll programmatically, e.g. to reveal a new answer. */
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(RoadSideColors.canvas)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.margin)
                .padding(top = Space.md, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                val interaction = rememberInteraction()
                val s by rememberPressScale(interaction, 0.92f)
                Box(
                    Modifier
                        .scale(s)
                        .size(44.dp)
                        .clip(RoadSideShapes.pill)
                        .background(RoadSideColors.container)
                        .clickable(interactionSource = interaction, indication = null, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = RoadSideColors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Gap(Space.sm + Space.xs)
            } else {
                // Top-level screen: the RoadSide emblem sits where the back button would be.
                Image(
                    painter = painterResource(R.drawable.roadside_logo),
                    contentDescription = "RoadSide",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Gap(Space.sm + Space.xs)
            }
            Column(Modifier.weight(1f)) {
                Text("ROADSIDE", style = RoadSideType.labelDataSm, color = RoadSideColors.primary)
                // Up to two lines: with the back button and vehicle pill beside it, a long title
                // like "Describe the problem" would otherwise be cut to "Describe the …".
                Text(
                    title,
                    style = RoadSideType.headlineMd,
                    color = RoadSideColors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (vehicle != null) {
                VehiclePill(vehicle, onVehicleChanged)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = Space.margin),
            content = content
        )
    }
}

/** Vehicle selector, shown as a pill in the header. */
@Composable
private fun VehiclePill(vehicle: String, onChanged: ((String) -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    val vehicles = listOf("Motorcycle", "Scooter")
    Box {
        Row(
            Modifier
                .clip(RoadSideShapes.pill)
                .background(RoadSideColors.containerHigh)
                .clickable(enabled = onChanged != null) { open = true }
                .padding(horizontal = Space.sm + Space.xs, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.TwoWheeler, null, tint = RoadSideColors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Space.xs + 2.dp))
            Text(vehicle, style = RoadSideType.labelDataSm, color = RoadSideColors.onSurface)
            if (onChanged != null) {
                Icon(
                    Icons.Default.ExpandMore, null,
                    tint = RoadSideColors.onSurfaceVariant, modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(RoadSideColors.containerHigh)
        ) {
            vehicles.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v, style = RoadSideType.bodyMd, color = RoadSideColors.onSurface) },
                    onClick = { onChanged?.invoke(v); open = false }
                )
            }
        }
    }
}
