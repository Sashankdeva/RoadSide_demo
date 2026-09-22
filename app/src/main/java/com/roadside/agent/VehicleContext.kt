package com.roadside.agent

import java.io.File

enum class MessageSender {
    USER,
    ROADSIDE
}

data class ChatMessage(
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DiagnosisCandidate(
    val issueKey: String,
    val title: String,
    val summary: String,
    val audioEvidenceSummary: String,
    val visionEvidenceSummary: String,
    val requiredTools: List<String>,
    val safetyAdvisory: String,
    val guideSteps: List<String>
)

data class VehicleContext(
    val vehicleType: String = "Motorcycle",
    val userProblemDescription: String = "",
    val chatMessages: List<ChatMessage> = listOf(
        ChatMessage(
            sender = MessageSender.ROADSIDE,
            text = "Welcome to RoadSide. Describe the issue or record vehicle sounds to begin."
        )
    ),
    val audioRecordingFile: File? = null,
    val audioEvidence: String? = null,
    val audioEvidenceLabel: String? = null,
    val visionImageFile: File? = null,
    val visionEvidence: String? = null,
    val visionEvidenceLabel: String? = null,
    val diagnosisCandidate: DiagnosisCandidate? = null,
    val guideStepIndex: Int = 0,
    /** True while a recording is being classified off the main thread. */
    val audioAnalysisPending: Boolean = false,
    /** True while a photo is being classified off the main thread. */
    val visionAnalysisPending: Boolean = false
)
