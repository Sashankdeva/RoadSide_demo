package com.roadside.rules

import com.roadside.knowledge.VehicleKnowledge

/**
 * User-facing wording for evidence and diagnoses.
 *
 * Every branch returns plain language. Internal labels such as `ambient_only` or
 * `engine_noise` must never reach the screen, so unrecognised labels fall back to a generic
 * sentence rather than being interpolated.
 */
object SafetyRules {

    fun formatPossibleIssueTitle(issueTitle: String): String {
        return "Possible issue: $issueTitle"
    }

    fun formatAudioEvidence(audioPattern: String?): String {
        return when (audioPattern) {
            "possible_chain_noise" -> "Recorded audio contains rattling alongside vehicle sounds, which can come from the drive chain."
            "brake_squeal" -> "Recorded audio contains a high-pitched squeal or friction sound, which can come from the brakes."
            "clicking_electrical" -> "Recorded audio contains clicking or buzzing, which can come from a starter relay or electrical fault."
            "engine_noise" -> "Recorded audio sounds like a running engine or vehicle. Nothing in it points to a specific fault."
            "mechanical_noise" -> "Recorded audio contains general mechanical sounds that do not point to a specific fault."
            "ambient_only" -> "Recorded audio contains only background sound. No vehicle sound was picked up."
            "UNKNOWN", null -> "Audio evidence is inconclusive or insufficient."
            else -> "Audio evidence is inconclusive or insufficient."
        }
    }

    fun formatVisionEvidence(visualPattern: String?): String {
        return when (visualPattern) {
            "chain_appears_dry" -> "Visual inspection suggests chain appears dry with minimal visible lubrication."
            "chain_visible" -> "Drive chain detected in the photo. Its condition cannot be assessed from this image."
            "chain_loose" -> "Visual inspection suggests drive chain may have excessive slack."
            "brake_rotor_worn" -> "Visual inspection shows scoring or uneven wear pattern on brake rotor."
            "battery_terminal_corroded" -> "Visual inspection reveals potential oxidation or loose contact on battery terminals."
            "UNKNOWN", null -> "Visual evidence is inconclusive or insufficient."
            else -> "Visual evidence is inconclusive or insufficient."
        }
    }

    fun formatDiagnosisSummary(
        issueId: String,
        issueTitle: String,
        audioEvidence: String?,
        visualEvidence: String?
    ): String {
        if (issueId == VehicleKnowledge.UNKNOWN.id) {
            return if (audioEvidence == null && visualEvidence == null) {
                "No sound recording or photo has been analysed yet, so there is no evidence to assess. " +
                    "Record the sound or take a photo of the suspected area."
            } else if (visualEvidence == "chain_visible") {
                "A drive chain was detected in the photo, but its condition cannot be determined from an image alone. " +
                    "No sound evidence currently points to a specific drivetrain issue."
            } else {
                "The recorded evidence does not point to a specific issue. " +
                    "This does not mean the vehicle is fault-free; if the problem persists, have it inspected by a mechanic."
            }
        }

        // When both sensors converge on the chain, note the corroboration.
        if (issueId == VehicleKnowledge.CHAIN_MAINTENANCE.id &&
            audioEvidence == "possible_chain_noise" && visualEvidence == "chain_visible") {
            return "Both the recorded sound and the photo point to the drive chain: " +
                "rattling was detected in the audio, and a chain is visible in the photo. " +
                "This is preliminary evidence suggesting the chain may benefit from inspection and lubrication. " +
                "This advisory does not replace professional mechanical inspection."
        }

        return "Preliminary evidence suggests this vehicle may benefit from attention to ${issueTitle.lowercase()}. " +
            "This assessment is an advisory guideline based on available sensory inputs and does not replace professional mechanical inspection."
    }
}
