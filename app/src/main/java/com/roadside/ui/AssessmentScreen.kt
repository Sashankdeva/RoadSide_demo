package com.roadside.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.roadside.agent.VehicleContext
import com.roadside.assist.Assistance
import com.roadside.assist.Finding
import com.roadside.assist.FindingTone
import com.roadside.assist.Findings
import com.roadside.ui.theme.Eyebrow
import com.roadside.ui.theme.Gap
import com.roadside.ui.theme.InstrumentCard
import com.roadside.ui.theme.PrimaryAction
import com.roadside.ui.theme.RoadSideColors
import com.roadside.ui.theme.RoadSideShapes
import com.roadside.ui.theme.RoadSideType
import com.roadside.ui.theme.SecondaryAction
import com.roadside.ui.theme.Space
import com.roadside.ui.theme.StatusBadge
import com.roadside.ui.theme.StepIndicator

/**
 * Step 4 — Assessment.
 *
 * Two clearly separate parts: WHAT WE OBSERVED (the sensors' findings, verbatim from the
 * classifiers) and WHAT ROADSIDE CONCLUDES (the rules' verdict). Keeping them apart is the
 * point — "the chain appears dry" is an observation, "chain maintenance" is a conclusion, and a
 * rider should be able to see which is which.
 */
@Composable
fun AssessmentScreen(
    context: VehicleContext,
    onSeeSolution: () -> Unit,
    onCheckAgain: () -> Unit,
    onBack: () -> Unit
) {
    val plan = Assistance.plan(context)

    val sound = Findings.sound(context.audioEvidence, context.audioEvidenceLabel).ifEmpty {
        listOf(Finding("Sound not recorded", "No recording was analysed for this check.", FindingTone.NEUTRAL))
    }
    val photo = Findings.photo(context.visionObservations, context.visionEvidence, context.visionEvidenceLabel).ifEmpty {
        listOf(Finding("No photo taken", "No photo was analysed for this check.", FindingTone.NEUTRAL))
    }

    ScreenScaffold(
        title = "Assessment",
        vehicle = context.vehicleType,
        onBack = onBack
    ) {
        StepIndicator(step = 4, total = 4, label = "Assess")
        Gap(Space.lg)

        // ── Observations ─────────────────────────────────────────────────────
        Eyebrow("What we observed")
        Gap(Space.sm + Space.xs)
        StaggeredReveal(0) { FindingsCard("SOUND", sound) }
        Gap(Space.sm + Space.xs)
        StaggeredReveal(1) { FindingsCard("PHOTO", photo) }
        Gap(Space.lg)

        // ── Conclusion ───────────────────────────────────────────────────────
        Eyebrow("What RoadSide concludes")
        Gap(Space.sm + Space.xs)
        StaggeredReveal(2) {
            InstrumentCard(
                color = RoadSideColors.containerLow,
                shape = RoadSideShapes.enclosure,
                padding = Space.lg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        plan.headline,
                        style = RoadSideType.headlineLg,
                        color = RoadSideColors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Gap(Space.sm)
                    StatusBadge(
                        if (plan.inconclusive) "NO CLEAR CAUSE" else "ADVISORY",
                        if (plan.inconclusive) RoadSideColors.onSurfaceVariant else RoadSideColors.advisory
                    )
                }
                Gap(Space.sm)
                // plan.found links the observations to this conclusion in one plain sentence.
                // The rules' own summary is more formal ("available sensory inputs"), so it
                // stays out of the rider-facing card.
                Text(plan.found, style = RoadSideType.bodyLg, color = RoadSideColors.onSurface)
                Gap(Space.sm)
                Text(
                    "A preliminary check, not a professional inspection.",
                    style = RoadSideType.bodySm,
                    color = RoadSideColors.onSurfaceVariant
                )
            }
        }
        Gap(Space.lg)

        PrimaryAction(
            text = "See what you can do",
            onClick = onSeeSolution,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
        )
        Gap(Space.sm + Space.xs)
        SecondaryAction("Record or photograph again", onCheckAgain, leadingIcon = Icons.Default.Refresh)
        Gap(Space.xl)
    }
}
