package com.roadside.assist

import com.roadside.agent.VehicleContext
import com.roadside.knowledge.VehicleKnowledge
import com.roadside.sensing.AudioClassificationResult
import com.roadside.sensing.VisionClassificationResult

/**
 * The solution a rider sees after an assessment: one screen, five short sections.
 *
 * It is built from the diagnosis the rules already made plus the observations behind it, and
 * adds no conclusion of its own. The wording is deliberately conservative:
 *   - "may", "often", "worth checking" — never "your chain is worn"
 *   - no measurements (slack, torque, pad thickness): RoadSide cannot measure them, and the
 *     correct values differ between bikes, so the owner's manual is referenced instead
 *   - anything that could be a safety problem (sprocket wear, brakes) points to a mechanic
 */
data class SolutionPlan(
    /** ASSESSMENT — the conclusion's title. */
    val headline: String,
    /** ASSESSMENT — what RoadSide found, in one or two sentences. */
    val found: String,
    /** WHAT IT MAY MEAN */
    val meaning: String,
    /** WHAT YOU CAN DO */
    val actions: List<String>,
    /** TOOLS YOU MAY NEED — only items relevant to [actions]. */
    val tools: List<String>,
    /** SAFETY — one short line. */
    val safety: String,
    /** True when the evidence did not support a specific issue. */
    val inconclusive: Boolean
)

object Assistance {

    private const val SAFETY_CHAIN =
        "Engine off and key out before you touch the chain. Turn the wheel by hand only — a moving chain and sprocket can trap fingers."
    private const val SAFETY_SPROCKET =
        "Ride slowly and only on smooth roads until the sprocket is checked. Engine off and key out before you touch the chain."
    private const val SAFETY_BRAKE =
        "Let the brakes cool first, and never put oil, lube or spray near the discs or pads."
    private const val SAFETY_ELECTRICAL =
        "Ignition off before touching the battery. Disconnect the black (negative) lead first and reconnect it last."
    private const val SAFETY_GENERAL =
        "Park somewhere safe, away from traffic, with the engine off before you inspect anything."

    fun plan(c: VehicleContext): SolutionPlan {
        val issue = c.diagnosisCandidate?.issueKey ?: VehicleKnowledge.UNKNOWN.id
        val obs = c.visionObservations.ifEmpty { listOfNotNull(c.visionEvidence) }
        val heard = c.audioEvidence
        val vehicle = c.vehicleType.lowercase()
        return when (issue) {
            VehicleKnowledge.CHAIN_MAINTENANCE.id -> chainPlan(obs, heard, vehicle)
            VehicleKnowledge.BRAKE_SOUND.id -> brakePlan()
            VehicleKnowledge.BATTERY_ELECTRICAL.id -> electricalPlan()
            else -> inconclusivePlan(c, obs, heard)
        }
    }

    // ── Chain ────────────────────────────────────────────────────────────────

    private fun chainPlan(obs: List<String>, heard: String?, vehicle: String): SolutionPlan {
        val soiled = VisionClassificationResult.CHAIN_SOILED in obs
        val dry = VisionClassificationResult.CHAIN_APPEARS_DRY in obs
        val wear = VisionClassificationResult.SPROCKET_WEAR_VISIBLE in obs
        val rattle = heard == AudioClassificationResult.POSSIBLE_CHAIN_NOISE

        val seen = buildList {
            if (soiled) add("a soiled chain")
            if (dry) add("a dry-looking chain")
            if (wear) add("sprocket teeth that don't look in good condition")
        }
        val found = when {
            rattle && seen.isNotEmpty() -> "RoadSide heard chain-like rattling, and the photo shows ${seen.joinWithAnd()}."
            rattle -> "RoadSide heard chain-like rattling from your $vehicle."
            else -> "The photo shows ${seen.joinWithAnd()}."
        }

        val meaning = when {
            wear -> "The teeth on the sprocket don't look in good condition. Worn teeth can let the chain slip " +
                "or jump and wear it out faster, so go easy on the bike until it's been looked at. Worn " +
                "sprockets are usually replaced together with the chain."
            soiled && dry -> "Dirt and grit on a chain that's short of lubricant wear it and the sprockets faster, " +
                "and can make it noisy. Cleaning and re-lubricating is usually the first thing to try."
            soiled -> "Dirt and grit on a chain wear it and the sprockets faster and can make it noisy. " +
                "A clean and re-lube is usually the first thing to try."
            dry -> "A chain without enough lubricant can rattle or squeak and wears faster. " +
                "Lubricating it is a simple first step."
            else -> "A rattle from the drive area often comes from a chain that needs cleaning, lubrication " +
                "or adjustment. The photo couldn't confirm the chain's condition, so start with the simple checks."
        }

        val actions = buildList {
            add("Put the $vehicle on its stand on level ground, engine off and key out.")
            if (wear) {
                add("Look at the sprocket: teeth that are hooked, pointed or uneven are a sign of wear.")
                add("Until it's checked, ride only at slow speeds and on smooth roads. Avoid rough roads, hard acceleration and carrying heavy loads.")
                add("Have a mechanic check the sprocket and chain as soon as you can.")
            }
            if (soiled || (!dry && !wear)) {
                add("Clean the chain with a brush and chain cleaner, turning the rear wheel by hand.")
                add("Wipe it dry with a clean cloth.")
            }
            if (dry || soiled || !wear) {
                add("Apply chain lube to the inner side of the chain while slowly turning the wheel, then wipe off the excess.")
                add("Give the lube time to settle before riding — the product label says how long.")
            }
            add("Check the chain isn't visibly sagging or tight. Your owner's manual gives the correct slack.")
            if (!wear) add("If the noise continues after this, have it checked by a mechanic.")
        }

        val tools = buildList {
            if (soiled || (!dry && !wear)) {
                add("Chain cleaner")
                add("Chain cleaning brush")
            }
            if (dry || soiled || !wear) add("Chain lubricant")
            add("Lint-free cloth")
            add("Protective gloves")
            if (wear) add("Flashlight")
            add("Owner's manual")
        }

        return SolutionPlan(
            headline = if (wear) "Chain and sprocket check" else "Chain maintenance",
            found = found,
            meaning = meaning,
            actions = actions,
            tools = tools,
            safety = if (wear) SAFETY_SPROCKET else SAFETY_CHAIN,
            inconclusive = false
        )
    }

    // ── Brakes / electrical (audio can produce these; not the demo focus) ─────

    private fun brakePlan() = SolutionPlan(
        headline = "Brake inspection",
        found = "RoadSide heard a high-pitched squeal or friction sound.",
        meaning = "Squealing brakes often come from dust, glazed pads or worn pads. " +
            "Brakes are safety-critical, so if anything feels different when braking, have them checked.",
        actions = listOf(
            "Park safely and let the brakes cool down.",
            "Shine a torch into the caliper and look at how much pad material is left.",
            "If the pads look very thin, you hear grinding, or the lever feels different, don't ride — have a mechanic check them."
        ),
        tools = listOf("Flashlight", "Owner's manual"),
        safety = SAFETY_BRAKE,
        inconclusive = false
    )

    private fun electricalPlan() = SolutionPlan(
        headline = "Battery / electrical check",
        found = "RoadSide heard clicking or buzzing.",
        meaning = "Rapid clicking when you press the starter often means the battery is low or a connection is loose.",
        actions = listOf(
            "Turn the ignition off and remove the key.",
            "Check the battery terminals are tight and free of white or green deposits.",
            "If the battery is old or the bike won't crank, charge it or have it tested."
        ),
        tools = listOf("Screwdriver or spanner for the terminals", "Owner's manual"),
        safety = SAFETY_ELECTRICAL,
        inconclusive = false
    )

    // ── Inconclusive ─────────────────────────────────────────────────────────

    private fun inconclusivePlan(c: VehicleContext, obs: List<String>, heard: String?): SolutionPlan {
        val normal = VisionClassificationResult.NORMAL in obs
        val chainSeen = obs.any {
            it == VisionClassificationResult.CHAIN_PRESENT || it == VisionClassificationResult.CHAIN_VISIBLE
        }
        val nothingCaptured = heard == null && c.visionEvidence == null

        val (headline, found, meaning) = when {
            nothingCaptured -> Triple(
                "Nothing checked yet",
                "No sound or photo has been analysed yet.",
                "RoadSide needs a recording or a photo before it can suggest anything."
            )
            normal -> Triple(
                "Nothing obvious found",
                "The photo shows the chain and sprocket with no obvious visible issue.",
                "That's a good sign for the parts in view, but it doesn't rule out a problem elsewhere or one a photo can't show."
            )
            chainSeen -> Triple(
                "No clear finding",
                "RoadSide could see the chain, but couldn't judge its condition, and the sound didn't point to a specific issue.",
                "There isn't enough evidence to say what's wrong. A clearer recording or photo may help."
            )
            else -> Triple(
                "No clear finding",
                "The sound and photo didn't point to a specific issue.",
                "This doesn't mean the bike is fault-free — only that RoadSide couldn't identify a cause."
            )
        }

        val actions = buildList {
            if (nothingCaptured) {
                add("Record the sound while it's happening, with the phone close to where it comes from.")
                add("Take a close, steady photo of the chain that fills the frame.")
            } else {
                if (heard == null || heard == AudioClassificationResult.UNKNOWN ||
                    heard == AudioClassificationResult.AMBIENT_ONLY) {
                    add("Record again while the noise is happening, with the phone close to where it comes from.")
                }
                if (!normal) add("Take a closer, steady photo of the chain that fills the frame.")
                add("Look over the bike for anything loose, leaking or rubbing.")
                add("If the noise continues or gets worse, have the bike checked by a mechanic.")
            }
        }

        return SolutionPlan(
            headline = headline,
            found = found,
            meaning = meaning,
            actions = actions,
            tools = listOf("Flashlight", "Owner's manual"),
            safety = SAFETY_GENERAL,
            inconclusive = true
        )
    }

    private fun List<String>.joinWithAnd(): String = when (size) {
        0 -> ""
        1 -> this[0]
        else -> dropLast(1).joinToString(", ") + " and " + last()
    }
}
