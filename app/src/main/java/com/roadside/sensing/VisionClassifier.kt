package com.roadside.sensing

import java.io.File

interface VisionClassifier {
    suspend fun classifyImage(imageFile: File, userContextHint: String = ""): VisionClassificationResult
}

data class VisionClassificationResult(
    val label: String,
    val confidence: Float,
    val labelDescription: String
) {
    companion object {
        const val CHAIN_APPEARS_DRY = "chain_appears_dry"

        /**
         * A drive chain is the subject of the photo. **Condition is not assessed.**
         *
         * This is what the real vision model ([ChainVisionClassifier]) can actually
         * support: it was trained to separate "chain photo" from "not a chain photo".
         * It deliberately does NOT mean the chain looks healthy — treating it as such
         * would be the same fabricated-evidence problem the mock had, just inverted.
         *
         * Its value to diagnosis is corroboration: if the audio suggests chain-like
         * noise and the camera confirms a chain is actually in view, the chain
         * hypothesis is better supported than from audio alone.
         */
        const val CHAIN_VISIBLE = "chain_visible"

        const val BRAKE_ROTOR_WORN = "brake_rotor_worn"
        const val BATTERY_TERMINAL_CORRODED = "battery_terminal_corroded"
        const val UNKNOWN = "UNKNOWN"
    }
}

class MockVisionClassifier : VisionClassifier {
    override suspend fun classifyImage(imageFile: File, userContextHint: String): VisionClassificationResult {
        val hint = userContextHint.lowercase()
        return when {
            hint.contains("brake") || hint.contains("rotor") || hint.contains("disc") -> {
                VisionClassificationResult(
                    label = VisionClassificationResult.BRAKE_ROTOR_WORN,
                    confidence = 0.86f,
                    labelDescription = "Brake friction surface shows visual scoring or minimal pad clearance"
                )
            }
            hint.contains("battery") || hint.contains("terminal") || hint.contains("wire") -> {
                VisionClassificationResult(
                    label = VisionClassificationResult.BATTERY_TERMINAL_CORRODED,
                    confidence = 0.82f,
                    labelDescription = "Corrosion deposits or loose contacts near battery terminal"
                )
            }
            hint.contains("unknown") || hint.contains("clear") -> {
                VisionClassificationResult(
                    label = VisionClassificationResult.UNKNOWN,
                    confidence = 0.25f,
                    labelDescription = "No distinct mechanical failure detected visually"
                )
            }
            else -> {
                // Primary prototype motorcycle demo default: chain appears dry
                VisionClassificationResult(
                    label = VisionClassificationResult.CHAIN_APPEARS_DRY,
                    confidence = 0.89f,
                    labelDescription = "Drive chain rollers lack visible lubricant film with mild surface dryness"
                )
            }
        }
    }
}
