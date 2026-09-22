package com.roadside.agent

import android.content.Context
import com.roadside.rules.DiagnosisRules
import com.roadside.sensing.AudioClassifier
import com.roadside.sensing.ChainVisionClassifier
import com.roadside.sensing.VisionClassifier
import com.roadside.sensing.YamNetAudioClassifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class RoadSideAgent(
    context: Context,
    private val audioClassifier: AudioClassifier = YamNetAudioClassifier(context),
    // Real on-device vision for the chain scenario. If the model or head cannot be loaded,
    // ChainVisionClassifier returns UNKNOWN rather than inventing evidence.
    private val visionClassifier: VisionClassifier = ChainVisionClassifier(context),
    // Model loading, WAV decoding, image decoding and inference all block. The UI calls
    // processAudioCapture / processVisionCapture from a main-thread coroutine scope, so the
    // work is moved here, in one place, rather than relying on every caller to remember.
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _context = MutableStateFlow(VehicleContext())
    val context: StateFlow<VehicleContext> = _context.asStateFlow()

    // The TFLite interpreters are not thread-safe. Once inference is off the main thread,
    // two quick captures could otherwise run concurrently on the same interpreter.
    private val audioLock = Mutex()
    private val visionLock = Mutex()

    fun updateVehicleType(vehicleType: String) {
        _context.update { it.copy(vehicleType = vehicleType) }
    }

    fun handleUserText(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(sender = MessageSender.USER, text = text)
        val roadsideResponseText = when {
            text.contains("noise", ignoreCase = true) || text.contains("sound", ignoreCase = true) ->
                "Let's check the sound first. Please tap 'Record Sound' to capture the vehicle audio."
            text.contains("look", ignoreCase = true) || text.contains("inspect", ignoreCase = true) ->
                "Let's take a look. Please tap 'Open Camera' to inspect the vehicle."
            else ->
                "I understand: \"$text\". Let's record the vehicle sound or take a photo so I can evaluate the issue."
        }
        val roadsideMessage = ChatMessage(sender = MessageSender.ROADSIDE, text = roadsideResponseText)

        _context.update {
            it.copy(
                userProblemDescription = text,
                chatMessages = it.chatMessages + userMessage + roadsideMessage
            )
        }
    }

    suspend fun processAudioCapture(file: File) {
        val currentProblem = _context.value.userProblemDescription
        _context.update { it.copy(audioAnalysisPending = true) }

        val result = try {
            withContext(inferenceDispatcher) {
                audioLock.withLock { audioClassifier.classifyAudio(file, currentProblem) }
            }
        } finally {
            _context.update { it.copy(audioAnalysisPending = false) }
        }

        val botMsg = ChatMessage(
            sender = MessageSender.ROADSIDE,
            text = "Vehicle sound recorded. Acoustic pattern: ${result.labelDescription}. Next, let's inspect the bike with the camera."
        )

        _context.update { current ->
            val updated = current.copy(
                audioRecordingFile = file,
                audioEvidence = result.label,
                audioEvidenceLabel = result.labelDescription,
                chatMessages = current.chatMessages + botMsg
            )
            updated.copy(diagnosisCandidate = diagnose(updated))
        }
    }

    suspend fun processVisionCapture(file: File) {
        val currentProblem = _context.value.userProblemDescription
        _context.update { it.copy(visionAnalysisPending = true) }

        val result = try {
            withContext(inferenceDispatcher) {
                visionLock.withLock { visionClassifier.classifyImage(file, currentProblem) }
            }
        } finally {
            _context.update { it.copy(visionAnalysisPending = false) }
        }

        val botMsg = ChatMessage(
            sender = MessageSender.ROADSIDE,
            text = "Visual evidence captured. Visual assessment: ${result.labelDescription}. Ready for diagnosis."
        )

        _context.update { current ->
            val updated = current.copy(
                visionImageFile = file,
                visionEvidence = result.label,
                visionEvidenceLabel = result.labelDescription,
                chatMessages = current.chatMessages + botMsg
            )
            updated.copy(diagnosisCandidate = diagnose(updated))
        }
    }

    fun ensureDiagnosis() {
        _context.update { it.copy(diagnosisCandidate = diagnose(it)) }
    }

    fun setGuideStep(stepIndex: Int) {
        _context.update { it.copy(guideStepIndex = stepIndex) }
    }

    /**
     * Clears all evidence and the diagnosis, keeping only the selected vehicle. Called when
     * a guide is finished, so the next check never fuses a new recording with an old photo.
     */
    fun resetSession() {
        _context.update { VehicleContext(vehicleType = it.vehicleType) }
    }

    private fun diagnose(c: VehicleContext): DiagnosisCandidate {
        val d = DiagnosisRules.evaluate(
            vehicleType = c.vehicleType,
            userProblem = c.userProblemDescription,
            audioPattern = c.audioEvidence,
            visualPattern = c.visionEvidence
        )
        // UNKNOWN covers several different situations (no chain in the photo, photo too
        // unclear, model unavailable). The classifier's own plain-language description says
        // which, so show it instead of the generic "inconclusive" sentence.
        return d.copy(
            audioEvidenceSummary = specificIfUnknown(c.audioEvidence, c.audioEvidenceLabel, d.audioEvidenceSummary),
            visionEvidenceSummary = specificIfUnknown(c.visionEvidence, c.visionEvidenceLabel, d.visionEvidenceSummary)
        )
    }

    private fun specificIfUnknown(label: String?, description: String?, generic: String): String =
        if (label == "UNKNOWN" && !description.isNullOrBlank()) description.trimEnd('.') + "." else generic
}
