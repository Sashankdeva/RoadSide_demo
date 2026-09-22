package com.roadside.assist

import com.roadside.sensing.AudioClassificationResult
import com.roadside.sensing.VisionClassificationResult

/**
 * Plain-language presentation of what the sensors observed.
 *
 * These are OBSERVATIONS, kept separate from the mechanical conclusion in [Assistance]: "the
 * chain appears soiled" is something the camera saw; "the chain may benefit from cleaning" is
 * what that could mean. The UI shows the two in different places so a rider never mistakes one
 * for the other.
 *
 * Nothing here carries a number. The classifiers report uncalibrated scores, and a percentage
 * next to "chain appears dry" would read as a measured quantity, which it is not.
 */
enum class FindingTone {
    /** Something was found that may need attention. */
    ADVISORY,

    /** A confirmation: a chain is in view, or nothing obvious is wrong with what was seen. */
    CONFIRMED,

    /** Inconclusive, or nothing relevant heard/seen. Not a fault, not an all-clear. */
    NEUTRAL
}

data class Finding(val title: String, val detail: String?, val tone: FindingTone)

object Findings {

    /** What the microphone picked up. Empty when no recording was analysed. */
    fun sound(label: String?, description: String?): List<Finding> = when (label) {
        null -> emptyList()
        AudioClassificationResult.POSSIBLE_CHAIN_NOISE -> listOf(
            Finding(
                "Chain-like rattling heard",
                "A rattling sound resembling drive-chain noise was picked up.",
                FindingTone.ADVISORY
            )
        )
        AudioClassificationResult.BRAKE_SQUEAL -> listOf(
            Finding("High-pitched squeal heard", "A squeal or friction sound was picked up.", FindingTone.ADVISORY)
        )
        AudioClassificationResult.CLICKING_ELECTRICAL -> listOf(
            Finding("Clicking or buzzing heard", "This kind of sound can come from the starter or battery.", FindingTone.ADVISORY)
        )
        AudioClassificationResult.ENGINE_NOISE -> listOf(
            Finding("Engine sound only", "Nothing in it points to a specific fault.", FindingTone.NEUTRAL)
        )
        AudioClassificationResult.MECHANICAL_NOISE -> listOf(
            Finding("General mechanical sound", "It doesn't point to a specific fault.", FindingTone.NEUTRAL)
        )
        AudioClassificationResult.AMBIENT_ONLY -> listOf(
            Finding("Only background sound", "No vehicle sound was picked up.", FindingTone.NEUTRAL)
        )
        else -> listOf(
            Finding("Sound not clear", description?.trimEnd('.')?.plus(".") ?: "The recording was inconclusive.", FindingTone.NEUTRAL)
        )
    }

    /**
     * What the camera saw, one finding per observation the classifier reported.
     *
     * @param observations every observation from the last photo (`VehicleContext.visionObservations`)
     * @param primary the primary label the rules used — used when an older result carries no list
     * @param description the classifier's own sentence, used for UNKNOWN so the rider sees *why*
     */
    fun photo(observations: List<String>, primary: String?, description: String?): List<Finding> {
        if (primary == null && observations.isEmpty()) return emptyList()
        val obs = observations.ifEmpty { listOfNotNull(primary) }

        if (VisionClassificationResult.UNKNOWN in obs || primary == VisionClassificationResult.UNKNOWN) {
            val noChain = description?.contains("No drive chain", ignoreCase = true) == true
            return listOf(
                Finding(
                    if (noChain) "No chain in view" else "Photo not clear enough",
                    description?.trimEnd('.')?.plus(".") ?: "The photo couldn't be assessed.",
                    FindingTone.NEUTRAL
                )
            )
        }

        val conditions = obs.filter { it in CONDITION_LABELS }
        val out = mutableListOf<Finding>()
        if (obs.any { it == VisionClassificationResult.CHAIN_PRESENT || it == VisionClassificationResult.CHAIN_VISIBLE } ||
            conditions.isNotEmpty()) {
            out += Finding(
                "Drive chain detected",
                // Only say the condition was not judged when nothing else was reported about it.
                if (conditions.isEmpty() && VisionClassificationResult.NORMAL !in obs)
                    "Its condition couldn't be judged from this photo." else null,
                FindingTone.CONFIRMED
            )
        }
        conditions.forEach { c ->
            out += when (c) {
                VisionClassificationResult.CHAIN_SOILED ->
                    Finding("Chain appears soiled or muddy", "Dirt or grit is visible on the chain.", FindingTone.ADVISORY)
                VisionClassificationResult.CHAIN_APPEARS_DRY ->
                    Finding("Chain appears dry", "It looks poorly lubricated in the photo.", FindingTone.ADVISORY)
                VisionClassificationResult.SPROCKET_WEAR_VISIBLE ->
                    Finding(
                        "Sprocket teeth look worn",
                        "The teeth on the sprocket don't look in good condition.",
                        FindingTone.ADVISORY
                    )
                else -> Finding("Visible issue", null, FindingTone.ADVISORY)
            }
        }
        if (VisionClassificationResult.NORMAL in obs && conditions.isEmpty()) {
            out += Finding(
                "No obvious issue visible",
                "On the part of the chain and sprocket in the photo.",
                FindingTone.CONFIRMED
            )
        }
        return out
    }

    val CONDITION_LABELS = setOf(
        VisionClassificationResult.CHAIN_SOILED,
        VisionClassificationResult.CHAIN_APPEARS_DRY,
        VisionClassificationResult.SPROCKET_WEAR_VISIBLE
    )
}
