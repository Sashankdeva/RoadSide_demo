package com.roadside.rules

import com.roadside.agent.DiagnosisCandidate
import com.roadside.knowledge.VehicleKnowledge

object DiagnosisRules {

    /**
     * Picks a knowledge entry from the **sensor evidence only**.
     *
     * [userProblem] is accepted for context but deliberately does NOT influence the result.
     * An earlier version let words in the typed description ("chain", "brake", "battery",
     * "start") select a fault on their own, so a recording that YAMNet heard as background
     * noise still produced chain-maintenance advice because the rider typed "chain". Typed
     * text is a claim by the user, not evidence, and "it started rattling" is not a battery
     * fault.
     *
     * Anything that is not positive evidence for a rule — engine_noise, mechanical_noise,
     * ambient_only, UNKNOWN, chain_visible, or no capture at all — falls through to
     * [VehicleKnowledge.UNKNOWN].
     */
    fun evaluate(
        vehicleType: String,
        userProblem: String,
        audioPattern: String?,
        visualPattern: String?
    ): DiagnosisCandidate {
        // The real vision model reports only whether a chain is in view, not its condition,
        // so `chain_visible` CORROBORATES the chain hypothesis but never establishes a fault
        // by itself. `chain_appears_dry`, `brake_rotor_worn` and `battery_terminal_corroded`
        // are condition labels that no shipped model produces today; they are honoured here
        // so the rules stay correct if a condition model is added later.
        val entry = when {
            audioPattern == "possible_chain_noise" ||
            visualPattern == "chain_appears_dry" ||
            visualPattern == "chain_soiled" ||
            visualPattern == "sprocket_wear_visible" ->
                VehicleKnowledge.CHAIN_MAINTENANCE

            audioPattern == "brake_squeal" || visualPattern == "brake_rotor_worn" ->
                VehicleKnowledge.BRAKE_SOUND

            audioPattern == "clicking_electrical" || visualPattern == "battery_terminal_corroded" ->
                VehicleKnowledge.BATTERY_ELECTRICAL

            else -> VehicleKnowledge.UNKNOWN
        }

        val audioDesc = SafetyRules.formatAudioEvidence(audioPattern)
        val visionDesc = SafetyRules.formatVisionEvidence(visualPattern)
        val summary = SafetyRules.formatDiagnosisSummary(entry.id, entry.title, audioPattern, visualPattern)

        return DiagnosisCandidate(
            issueKey = entry.id,
            title = entry.title,
            summary = summary,
            audioEvidenceSummary = audioDesc,
            visionEvidenceSummary = visionDesc,
            requiredTools = entry.tools,
            safetyAdvisory = entry.safetyAdvisory,
            guideSteps = entry.guideSteps
        )
    }
}
