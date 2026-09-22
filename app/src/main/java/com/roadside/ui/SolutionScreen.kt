package com.roadside.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadside.agent.VehicleContext
import com.roadside.assist.AskRoadSide
import com.roadside.assist.Assistance
import com.roadside.assist.SolutionPlan
import com.roadside.ui.theme.CompletionMark
import com.roadside.ui.theme.Eyebrow
import com.roadside.ui.theme.Gap
import com.roadside.ui.theme.IconWell
import com.roadside.ui.theme.InstrumentCard
import com.roadside.ui.theme.Motion
import com.roadside.ui.theme.PrimaryAction
import com.roadside.ui.theme.RoadSideColors
import com.roadside.ui.theme.RoadSideShapes
import com.roadside.ui.theme.RoadSideType
import com.roadside.ui.theme.Space
import com.roadside.ui.theme.StatusBadge
import com.roadside.ui.theme.rememberInteraction
import com.roadside.ui.theme.rememberPressScale
import kotlinx.coroutines.launch

/**
 * Solution / assistance — one screen, replacing the old four-step maintenance guide.
 *
 * ASSESSMENT · WHAT IT MAY MEAN · WHAT YOU CAN DO · TOOLS YOU MAY NEED · SAFETY, then an
 * "Ask RoadSide" box for follow-up questions. All content comes from [Assistance] and
 * [AskRoadSide], which build on the assessment the rules already made.
 */
@Composable
fun SolutionScreen(
    context: VehicleContext,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val plan = remember(context.diagnosisCandidate, context.visionObservations, context.audioEvidence) {
        Assistance.plan(context)
    }
    var done by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    ScreenScaffold(
        title = "What you can do",
        vehicle = context.vehicleType,
        onBack = onBack,
        scrollState = scroll
    ) {
        // Completion replaces the page content in place — the completion interaction.
        AnimatedContent(
            targetState = done,
            transitionSpec = {
                (fadeIn(tween(Motion.REVEAL_MS)) + scaleIn(tween(Motion.REVEAL_MS), initialScale = 0.96f)) togetherWith
                    fadeOut(tween(Motion.FAST_MS))
            },
            label = "solutionDone"
        ) { finished ->
            Column {
                if (finished) {
                    CompletionPanel(onFinished)
                } else {
                    SolutionBody(context, plan, scroll, onDone = { done = true })
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SolutionBody(
    context: VehicleContext,
    plan: SolutionPlan,
    scroll: androidx.compose.foundation.ScrollState,
    onDone: () -> Unit
) {
    Gap(Space.sm)

    // ── ASSESSMENT ───────────────────────────────────────────────────────────
    StaggeredReveal(0) {
        InstrumentCard(
            color = RoadSideColors.containerLow,
            shape = RoadSideShapes.enclosure,
            padding = Space.lg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Assessment", modifier = Modifier.weight(1f))
                StatusBadge(
                    if (plan.inconclusive) "NO CLEAR CAUSE" else "ADVISORY",
                    if (plan.inconclusive) RoadSideColors.onSurfaceVariant else RoadSideColors.advisory
                )
            }
            Gap(Space.sm)
            Text(plan.headline, style = RoadSideType.headlineXlMobile, color = RoadSideColors.onSurface)
            Gap(Space.sm)
            Text(plan.found, style = RoadSideType.bodyLg, color = RoadSideColors.onSurfaceVariant)
        }
    }
    Gap(Space.md)

    // ── WHAT IT MAY MEAN ─────────────────────────────────────────────────────
    StaggeredReveal(1) {
        Section("What it may mean") {
            Text(plan.meaning, style = RoadSideType.bodyLg, color = RoadSideColors.onSurface)
        }
    }
    Gap(Space.md)

    // ── WHAT YOU CAN DO ──────────────────────────────────────────────────────
    StaggeredReveal(2) {
        Section("What you can do") {
            plan.actions.forEachIndexed { i, action ->
                if (i > 0) Gap(Space.sm + Space.xs)
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoadSideShapes.pill)
                            .background(RoadSideColors.amber.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", style = RoadSideType.labelDataSm, color = RoadSideColors.primary)
                    }
                    Gap(Space.sm + Space.xs)
                    Text(
                        action,
                        style = RoadSideType.bodyMd,
                        color = RoadSideColors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    Gap(Space.md)

    // ── TOOLS YOU MAY NEED ───────────────────────────────────────────────────
    StaggeredReveal(3) {
        Section("Tools you may need") { ToolPills(plan.tools) }
    }
    Gap(Space.md)

    // ── SAFETY ───────────────────────────────────────────────────────────────
    StaggeredReveal(4) {
        InstrumentCard(
            color = RoadSideColors.critical.copy(alpha = 0.10f),
            shape = RoadSideShapes.enclosure,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.Top) {
                IconWell(tint = RoadSideColors.critical) {
                    Icon(Icons.Default.Warning, null, tint = RoadSideColors.critical, modifier = Modifier.size(22.dp))
                }
                Gap(Space.sm + Space.xs)
                Column(Modifier.weight(1f)) {
                    Text("SAFETY", style = RoadSideType.labelDataSm, color = RoadSideColors.critical)
                    Gap(Space.xs)
                    Text(plan.safety, style = RoadSideType.bodyMd, color = RoadSideColors.onSurface)
                }
            }
        }
    }
    Gap(Space.lg)

    // ── ASK ROADSIDE ─────────────────────────────────────────────────────────
    AskBox(context, plan, onAnswered = {
        // Keep the newest answer in view above the keyboard.
        it.launch { scroll.animateScrollTo(scroll.maxValue) }
    })
    Gap(Space.lg)

    PrimaryAction("I'm done", onDone, trailingIcon = Icons.Default.CheckCircle)
    Gap(Space.xl)
}

/** A titled section card — same enclosure, same eyebrow, every time. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    InstrumentCard(shape = RoadSideShapes.enclosure, padding = Space.lg, modifier = Modifier.fillMaxWidth()) {
        Eyebrow(title)
        Gap(Space.sm + Space.xs)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolPills(tools: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        tools.forEach { tool ->
            Text(
                tool,
                style = RoadSideType.bodyMd,
                color = RoadSideColors.onSurface,
                modifier = Modifier
                    .clip(RoadSideShapes.pill)
                    .background(RoadSideColors.containerHigh)
                    .padding(horizontal = Space.md, vertical = Space.sm)
            )
        }
    }
}

// ── Ask RoadSide ─────────────────────────────────────────────────────────────

private data class Exchange(val question: String, val answer: String)

/**
 * "Something wrong? Ask RoadSide…" — local, contextual follow-up. Suggested questions are
 * one-tap; answers appear beneath with a short reveal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskBox(
    context: VehicleContext,
    plan: SolutionPlan,
    onAnswered: (kotlinx.coroutines.CoroutineScope) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<Exchange>() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun ask(q: String) {
        val question = q.trim()
        if (question.isEmpty()) return
        history += Exchange(question, AskRoadSide.answer(question, context, plan).text)
        text = ""
        focus.clearFocus()
        onAnswered(scope)
    }

    InstrumentCard(
        color = RoadSideColors.containerLow,
        shape = RoadSideShapes.enclosure,
        padding = Space.md,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(Motion.REVEAL_MS, easing = Motion.decelerate))
    ) {
        Eyebrow("Something wrong?")
        Gap(Space.sm + Space.xs)

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoadSideShapes.control)
                .background(RoadSideColors.containerLowest)
                .padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        "Ask RoadSide…",
                        style = RoadSideType.bodyMd,
                        color = RoadSideColors.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = RoadSideType.bodyMd.copy(color = RoadSideColors.onSurface),
                    cursorBrush = SolidColor(RoadSideColors.amber),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { ask(text) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SendButton(enabled = text.isNotBlank()) { ask(text) }
        }
        Gap(Space.sm + Space.xs)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            SUGGESTIONS.forEach { s ->
                Text(
                    s,
                    style = RoadSideType.bodySm,
                    color = RoadSideColors.onSurface,
                    modifier = Modifier
                        .clip(RoadSideShapes.pill)
                        .background(RoadSideColors.containerHigh)
                        .clickable { ask(s) }
                        .padding(horizontal = Space.sm + Space.xs, vertical = Space.sm)
                )
            }
        }

        history.forEachIndexed { i, ex ->
            Gap(Space.md)
            StaggeredReveal(0) {
                Column {
                    // The rider's question, right-aligned in amber like a sent message.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            ex.question,
                            style = RoadSideType.bodyMd,
                            color = RoadSideColors.onAmber,
                            modifier = Modifier
                                .clip(RoadSideShapes.control)
                                .background(RoadSideColors.amber)
                                .padding(horizontal = Space.md, vertical = Space.sm + Space.xs)
                        )
                    }
                    Gap(Space.sm)
                    Text(
                        ex.answer,
                        style = RoadSideType.bodyMd,
                        color = RoadSideColors.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoadSideShapes.control)
                            .background(RoadSideColors.container)
                            .padding(Space.md)
                    )
                }
            }
            if (i == history.lastIndex) Gap(Space.xs)
        }
    }
}

private val SUGGESTIONS = listOf(
    "I don't have chain cleaner",
    "The noise is still there",
    "I don't understand this",
    "I don't have these tools",
    "Is it safe to ride?"
)

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val s by rememberPressScale(interaction, 0.9f)
    Box(
        Modifier
            .scale(s)
            .size(44.dp)
            .clip(RoadSideShapes.pill)
            .background(if (enabled) RoadSideColors.amber else RoadSideColors.containerHigh)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = "Ask",
            tint = if (enabled) RoadSideColors.onAmber else RoadSideColors.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Completion ───────────────────────────────────────────────────────────────

/** The completion interaction: the mark draws itself, then the closing line and next step. */
@Composable
private fun ColumnScope.CompletionPanel(onFinish: () -> Unit) {
    Gap(Space.xl)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CompletionMark(Modifier.size(120.dp))
    }
    Gap(Space.lg)
    Text(
        "Check complete",
        style = RoadSideType.headlineXlMobile,
        color = RoadSideColors.onSurface,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Gap(Space.sm)
    Text(
        "If the noise comes back or anything still feels wrong, have the bike looked at by a " +
            "mechanic. RoadSide gives a preliminary check, not a professional inspection.",
        style = RoadSideType.bodyMd,
        color = RoadSideColors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Gap(Space.xl)
    PrimaryAction("Start a new check", onFinish)
    Gap(Space.xl)
}
