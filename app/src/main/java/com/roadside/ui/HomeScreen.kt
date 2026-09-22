package com.roadside.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.roadside.ui.theme.SoftDivider
import com.roadside.ui.theme.Space
import com.roadside.ui.theme.StatusBadge

/**
 * Home — the master visual reference for the rest of the app.
 *
 * Everything on it is a statement the app can back: it works offline (no INTERNET
 * permission), it listens and looks, and it says what the evidence supports. There are no
 * status readouts here, because a landing screen has nothing real to measure yet.
 */
@Composable
fun HomeScreen(
    context: VehicleContext,
    onVehicleTypeChanged: (String) -> Unit,
    onStartCheck: () -> Unit,
    onContinueCheck: () -> Unit,
    onSensorCheck: () -> Unit
) {
    val checkUnderway = context.audioEvidence != null || context.visionEvidence != null

    ScreenScaffold(
        title = "Roadside check",
        vehicle = context.vehicleType,
        onVehicleChanged = onVehicleTypeChanged,
        onBack = null
    ) {
        Gap(Space.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatusBadge("WORKS OFFLINE", RoadSideColors.safe)
            StatusBadge("STAYS ON THIS PHONE", RoadSideColors.onSurfaceVariant)
        }
        Gap(Space.lg)

        Eyebrow("Chain & drive check")
        Gap(Space.sm)
        Text(
            "Something sound wrong with your bike?",
            style = RoadSideType.headlineXlMobile,
            color = RoadSideColors.onSurface
        )
        Gap(Space.sm)
        Text(
            "RoadSide listens to the noise and looks at the chain, then tells you plainly what " +
                "that could mean and what you can do about it.",
            style = RoadSideType.bodyLg,
            color = RoadSideColors.onSurfaceVariant
        )
        Gap(Space.lg)

        // ── How it works ─────────────────────────────────────────────────────
        InstrumentCard(
            color = RoadSideColors.containerLow,
            shape = RoadSideShapes.enclosure,
            padding = Space.lg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("HOW IT WORKS", style = RoadSideType.labelDataSm, color = RoadSideColors.onSurfaceVariant)
            Gap(Space.md)
            HowRow(Icons.Default.Hearing, "Listen", "Record the noise while it's happening.")
            Gap(Space.sm + Space.xs)
            SoftDivider()
            Gap(Space.sm + Space.xs)
            HowRow(Icons.Default.CameraAlt, "Inspect", "Take a close photo of the chain.")
            Gap(Space.sm + Space.xs)
            SoftDivider()
            Gap(Space.sm + Space.xs)
            HowRow(Icons.Default.Build, "Get help", "See what it may mean and what to do next.")
        }
        Gap(Space.lg)

        PrimaryAction(
            text = "Start a check",
            onClick = onStartCheck,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
        )
        if (checkUnderway) {
            Gap(Space.sm + Space.xs)
            SecondaryAction("Continue current check", onContinueCheck, leadingIcon = Icons.Default.Refresh)
        }
        Gap(Space.lg)

        Text(
            "RoadSide gives a preliminary check, not a professional inspection.",
            style = RoadSideType.bodySm,
            color = RoadSideColors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Gap(Space.md)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                "Sensor check",
                style = RoadSideType.labelDataSm,
                color = RoadSideColors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .clip(RoadSideShapes.pill)
                    .clickable(onClick = onSensorCheck)
                    .padding(horizontal = Space.md, vertical = Space.sm)
            )
        }
        Gap(Space.xl)
    }
}

@Composable
private fun HowRow(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconWell {
            Icon(icon, null, tint = RoadSideColors.primary, modifier = Modifier.size(22.dp))
        }
        Gap(Space.md)
        Column(Modifier.weight(1f)) {
            Text(title, style = RoadSideType.headlineMd, color = RoadSideColors.onSurface)
            Text(body, style = RoadSideType.bodyMd, color = RoadSideColors.onSurfaceVariant)
        }
    }
}
