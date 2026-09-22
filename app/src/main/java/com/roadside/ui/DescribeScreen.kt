package com.roadside.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
 * Step 1 — Describe the problem.
 *
 * The typed description and the suggestion chips are context for the rider only: the
 * diagnosis comes from the microphone and camera, never from these words. That is enforced
 * in `DiagnosisRules`, and the copy here is careful not to promise otherwise.
 */
@Composable
fun DescribeScreen(
    context: VehicleContext,
    onVehicleTypeChanged: (String) -> Unit,
    onProblemDescriptionChanged: (String) -> Unit,
    onAskRoadSide: (String) -> Unit,
    onListenClicked: () -> Unit,
    onInspectClicked: () -> Unit,
    onVoiceInputRequested: () -> Unit,
    onBack: () -> Unit
) {
    var problemText by remember(context.userProblemDescription) {
        mutableStateOf(context.userProblemDescription)
    }
    var selectedSound by remember { mutableStateOf<String?>(null) }
    var selectedTiming by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = "Describe the problem",
        vehicle = context.vehicleType,
        onVehicleChanged = onVehicleTypeChanged,
        onBack = onBack
    ) {
        StepIndicator(step = 1, total = 4, label = "Describe")
        Gap(Space.lg)

        Eyebrow("Where it starts", Icons.Default.Hearing)
        Gap(Space.sm)
        Text(
            "What are you hearing?",
            style = RoadSideType.headlineXlMobile,
            color = RoadSideColors.onSurface
        )
        Gap(Space.sm)
        Text(
            "Tell us what sounds different. RoadSide listens to the bike itself before " +
                "suggesting anything.",
            style = RoadSideType.bodyMd,
            color = RoadSideColors.onSurfaceVariant
        )
        Gap(Space.lg)

        // ── Description input ────────────────────────────────────────────────
        InstrumentCard(color = RoadSideColors.containerLow, shape = RoadSideShapes.enclosure, padding = Space.md) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, null, tint = RoadSideColors.primary, modifier = Modifier.size(18.dp))
                    Gap(Space.sm)
                    Text("Describe the sound", style = RoadSideType.bodyMd, color = RoadSideColors.onSurface)
                }
                StatusBadge("READY", RoadSideColors.safe, pulsing = true)
            }
            Gap(Space.sm + Space.xs)

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp)
                    .clip(RoadSideShapes.control)
                    .background(RoadSideColors.containerLowest)
                    .padding(Space.md)
            ) {
                if (problemText.isEmpty()) {
                    Text(
                        "e.g. a metallic clicking near the rear wheel that speeds up as I ride",
                        style = RoadSideType.bodyMd,
                        color = RoadSideColors.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                BasicTextField(
                    value = problemText,
                    onValueChange = {
                        problemText = it
                        onProblemDescriptionChanged(it)
                    },
                    textStyle = RoadSideType.bodyMd.copy(color = RoadSideColors.onSurface),
                    cursorBrush = SolidColor(RoadSideColors.amber),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Default
                    )
                )
            }
            Gap(Space.sm + Space.xs)

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock, null,
                        tint = RoadSideColors.onSurfaceVariant, modifier = Modifier.size(14.dp)
                    )
                    Gap(Space.xs + 2.dp)
                    Text(
                        "Stays on your phone",
                        style = RoadSideType.bodySm,
                        color = RoadSideColors.onSurfaceVariant
                    )
                }
                DictateButton(onVoiceInputRequested)
            }
        }
        Gap(Space.lg)

        // ── Suggestion chips ─────────────────────────────────────────────────
        SectionHeader("Sound type", "Optional")
        Gap(Space.sm + Space.xs)
        ChipFlow(
            options = listOf("Metallic clicking", "Dry scraping", "High-pitched whine", "Dull knock", "Rhythmic"),
            selected = selectedSound,
            onSelect = { selectedSound = if (selectedSound == it) null else it }
        )
        Gap(Space.lg)

        SectionHeader("When does it happen?", "Optional")
        Gap(Space.sm + Space.xs)
        ChipFlow(
            options = listOf("With wheel speed", "At idle", "Accelerating", "Slowing down"),
            selected = selectedTiming,
            onSelect = { selectedTiming = if (selectedTiming == it) null else it }
        )
        Gap(Space.lg)

        // ── What happens next ────────────────────────────────────────────────
        InstrumentCard(color = RoadSideColors.containerLow, shape = RoadSideShapes.enclosure) {
            Row(verticalAlignment = Alignment.Top) {
                IconWell {
                    Icon(
                        Icons.Default.Hearing, null,
                        tint = RoadSideColors.primary, modifier = Modifier.size(22.dp)
                    )
                }
                Gap(Space.sm + Space.xs)
                Column(Modifier.weight(1f)) {
                    Text("What happens next", style = RoadSideType.headlineMd, color = RoadSideColors.onSurface)
                    Gap(Space.xs)
                    Text(
                        "RoadSide records the sound, then looks at a photo of the chain, and tells " +
                            "you what the two together support — and says so plainly when they " +
                            "support nothing. What you type here helps you, but doesn't change the result.",
                        style = RoadSideType.bodyMd,
                        color = RoadSideColors.onSurfaceVariant
                    )
                }
            }
        }
        Gap(Space.lg)

        // ── Actions ──────────────────────────────────────────────────────────
        PrimaryAction(
            text = "Listen to the bike",
            onClick = {
                if (problemText.isNotBlank()) onAskRoadSide(problemText)
                onListenClicked()
            },
            leadingIcon = Icons.Default.Hearing
        )
        Gap(Space.sm + Space.xs)
        SecondaryAction(
            text = "Take a photo instead",
            onClick = {
                if (problemText.isNotBlank()) onAskRoadSide(problemText)
                onInspectClicked()
            },
            leadingIcon = Icons.Default.CameraAlt
        )
        Gap(Space.xl)
    }
}

/** Voice dictation trigger. Amber pill, press-scaled like every other control. */
@Composable
private fun DictateButton(onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val s by rememberPressScale(interaction, pressedScale = 0.94f)
    Row(
        Modifier
            .scale(s)
            .clip(RoadSideShapes.pill)
            .background(RoadSideColors.amber)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Mic, null, tint = RoadSideColors.onAmber, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.xs + 2.dp))
        Text("Dictate", style = RoadSideType.labelDataSm, color = RoadSideColors.onAmber)
    }
}

/** Wrapping row of selectable pills. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ChipFlow(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
            SelectChip(option, selected == option, { onSelect(option) })
        }
    }
}
