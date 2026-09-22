package com.roadside.sensing

import java.io.File

interface VisionClassifier {
    suspend fun classifyImage(imageFile: File, userContextHint: String = ""): VisionClassificationResult
}

data class VisionClassificationResult(
    val label: String,
    val confidence: Float,
    val labelDescription: String,
    val observations: List<String> = emptyList(),
    val rawScores: Map<String, Float> = emptyMap()
) {
    companion object {
        const val CHAIN_PRESENT = "chain_present"
        const val CHAIN_VISIBLE = "chain_visible"
        const val CHAIN_SOILED = "chain_soiled"
        const val CHAIN_APPEARS_DRY = "chain_appears_dry"
        const val SPROCKET_WEAR_VISIBLE = "sprocket_wear_visible"
        const val NORMAL = "normal"
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
