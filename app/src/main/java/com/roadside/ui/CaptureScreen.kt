package com.roadside.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.roadside.agent.VehicleContext
import com.roadside.audio.AudioRecorder
import com.roadside.camera.CameraManager
import com.roadside.assist.Findings
import com.roadside.ui.theme.AnalysisSweep
import com.roadside.ui.theme.Eyebrow
import com.roadside.ui.theme.ElapsedReadout
import com.roadside.ui.theme.Gap
import com.roadside.ui.theme.IconWell
import com.roadside.ui.theme.InstrumentCard
import com.roadside.ui.theme.ListeningWaveform
import com.roadside.ui.theme.Motion
import com.roadside.ui.theme.PrimaryAction
import com.roadside.ui.theme.PulseRing
import com.roadside.ui.theme.RoadSideColors
import com.roadside.ui.theme.RoadSideShapes
import com.roadside.ui.theme.RoadSideType
import com.roadside.ui.theme.SecondaryAction
import com.roadside.ui.theme.ShutterFlash
import com.roadside.ui.theme.Space
import com.roadside.ui.theme.StatusBadge
import com.roadside.ui.theme.StepIndicator
import com.roadside.ui.theme.TelemetryWell
import kotlinx.coroutines.delay
import java.io.File

enum class CaptureMode {
    AUDIO,
    CAMERA
}

/**
 * Step 2 — capture. Audio and camera share this screen.
 *
 * The recorder and camera lifecycle handling in here is load-bearing and was verified on
 * device; the UI pass around it changed presentation only. Specifically preserved:
 *   - recording state seeded from `audioRecorder.isRecording`, not `false`
 *   - ON_STOP and onDispose cancel an in-flight recording (background capture is silenced by
 *     Android, and an abandoned recorder keeps the microphone)
 *   - the camera unbinds the moment the preview leaves the screen
 */
@Composable
fun CaptureScreen(
    initialMode: CaptureMode,
    context: VehicleContext,
    audioRecorder: AudioRecorder,
    cameraManager: CameraManager,
    onAudioFileSaved: (File) -> Unit,
    onVisionPhotoSaved: (File) -> Unit,
    onContinueToDiagnosis: () -> Unit,
    onSwitchToCamera: () -> Unit,
    onSwitchToAudio: () -> Unit,
    onBackClicked: () -> Unit
) {
    val activeMode = initialMode
    val androidContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                androidContext, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                androidContext, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // Start from the recorder's real state, not `false`: this screen can be recreated while
    // a recording object still exists, and a stale `false` offered "Start Recording" again.
    var isRecordingAudio by remember { mutableStateOf(audioRecorder.isRecording) }
    var audioRecordSeconds by remember { mutableIntStateOf(0) }
    var audioStatusMessage by remember { mutableStateOf<String?>(null) }

    // A recording must never outlive this screen or continue in the background. Android
    // silences background microphone capture, and an abandoned recorder keeps the mic, so
    // leaving mid-recording discards it instead of leaking it.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && audioRecorder.isRecording) {
                audioRecorder.cancelRecording()
                isRecordingAudio = false
                audioStatusMessage = "Recording stopped because the app went to the background. Please record again."
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (audioRecorder.isRecording) audioRecorder.cancelRecording()
        }
    }

    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            audioRecordSeconds = 0
            while (isRecordingAudio) {
                delay(1000)
                audioRecordSeconds += 1
            }
        }
    }

    var isCameraCapturing by remember { mutableStateOf(false) }
    var cameraStatusMessage by remember { mutableStateOf<String?>(null) }
    var shutterTrigger by remember { mutableIntStateOf(0) }

    val listening = activeMode == CaptureMode.AUDIO
    ScreenScaffold(
        title = if (listening) "Listen" else "Inspect",
        vehicle = context.vehicleType,
        onBack = onBackClicked
    ) {
        StepIndicator(
            step = if (listening) 2 else 3,
            total = 4,
            label = if (listening) "Listen" else "Inspect"
        )
        Gap(Space.lg)

        // Listen → Inspect is a navigation (with its own screen transition), so the mode is
        // fixed for the life of this screen.
        Column {
                if (listening) {
                    AudioSection(
                        context = context,
                        hasPermission = hasAudioPermission,
                        onRequestPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        isRecording = isRecordingAudio,
                        seconds = audioRecordSeconds,
                        statusMessage = audioStatusMessage,
                        onStart = {
                            val result = audioRecorder.startRecording()
                            result.onSuccess {
                                isRecordingAudio = true
                                audioStatusMessage = null
                            }.onFailure { err ->
                                audioStatusMessage = "Could not start recording: ${err.message}"
                            }
                        },
                        onStop = {
                            val result = audioRecorder.stopRecording()
                            isRecordingAudio = false
                            result.onSuccess { savedFile ->
                                audioStatusMessage = null
                                onAudioFileSaved(savedFile)
                            }.onFailure { err ->
                                audioStatusMessage = "Recording problem: ${err.message}"
                            }
                        },
                        onNextCamera = onSwitchToCamera,
                        onSkipToDiagnosis = onContinueToDiagnosis
                    )
                } else {
                    CameraSection(
                        context = context,
                        hasPermission = hasCameraPermission,
                        onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        cameraManager = cameraManager,
                        isCapturing = isCameraCapturing,
                        shutterTrigger = shutterTrigger,
                        statusMessage = cameraStatusMessage,
                        onCapture = {
                            isCameraCapturing = true
                            shutterTrigger += 1
                            cameraManager.capturePhoto(
                                context = androidContext,
                                onSuccess = { photoFile ->
                                    isCameraCapturing = false
                                    cameraStatusMessage = null
                                    onVisionPhotoSaved(photoFile)
                                },
                                onError = { err ->
                                    isCameraCapturing = false
                                    cameraStatusMessage = "Capture failed: ${err.message}"
                                }
                            )
                        },
                        onCameraError = { err -> cameraStatusMessage = "Camera problem: ${err.message}" },
                        onShowAssessment = onContinueToDiagnosis
                    )
                }
        }
        Gap(Space.xl)
    }
}

// ── Audio ────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.AudioSection(
    context: VehicleContext,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    isRecording: Boolean,
    seconds: Int,
    statusMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNextCamera: () -> Unit,
    onSkipToDiagnosis: () -> Unit
) {
    Eyebrow("Sound check", Icons.Default.Hearing)
    Gap(Space.sm)
    Text(
        if (isRecording) "Listening…" else "Hold the phone near the chain",
        style = RoadSideType.headlineXlMobile,
        color = RoadSideColors.onSurface
    )
    Gap(Space.sm)
    Text(
        "Record while the noise is happening. Ten seconds is plenty. The sound is analysed " +
            "on this phone and never leaves it.",
        style = RoadSideType.bodyMd,
        color = RoadSideColors.onSurfaceVariant
    )
    Gap(Space.lg)

    if (!hasPermission) {
        PermissionCard(
            title = "Microphone access needed",
            body = "RoadSide needs the microphone to hear the bike.",
            action = "Allow microphone",
            onClick = onRequestPermission
        )
        return
    }

    // The listening instrument: pulse ring, travelling waveform, elapsed time.
    InstrumentCard(
        color = RoadSideColors.containerLow,
        shape = RoadSideShapes.enclosure,
        padding = Space.md,
        modifier = Modifier.fillMaxWidth()
    ) {
        TelemetryWell(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            // Either the waveform or the idle label — never both, or they overlap.
            Crossfade(targetState = isRecording, label = "waveIdle") { listening ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (listening) {
                        ListeningWaveform(
                            active = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.md)
                                .height(88.dp)
                        )
                    } else {
                        Text(
                            "Ready to listen",
                            style = RoadSideType.labelDataSm,
                            color = RoadSideColors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Gap(Space.md)

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isRecording) {
                StatusBadge("RECORDING", RoadSideColors.amber, pulsing = true)
                ElapsedReadout(seconds)
            } else {
                StatusBadge("READY", RoadSideColors.safe)
                Text(
                    "16 kHz · unfiltered",
                    style = RoadSideType.labelDataSm,
                    color = RoadSideColors.onSurfaceVariant
                )
            }
        }
    }
    Gap(Space.md)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        PulseRing(
            active = isRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        )
        if (isRecording) {
            PrimaryAction("Stop recording", onStop, leadingIcon = Icons.Default.Stop)
        } else {
            PrimaryAction("Start recording", onStart, leadingIcon = Icons.Default.Mic)
        }
    }

    if (statusMessage != null) {
        Gap(Space.sm + Space.xs)
        Text(statusMessage, style = RoadSideType.bodyMd, color = RoadSideColors.error)
    }

    // recording → analysis → result
    Gap(Space.md)
    AnimatedContent(
        targetState = when {
            context.audioAnalysisPending -> AudioStage.ANALYSING
            context.audioEvidence != null -> AudioStage.RESULT
            else -> AudioStage.IDLE
        },
        transitionSpec = { Motion.revealTransform() },
        label = "audioStage"
    ) { stage ->
        when (stage) {
            AudioStage.IDLE -> Spacer(Modifier.height(0.dp))
            AudioStage.ANALYSING -> AnalysingCard("Listening back to the recording…")
            AudioStage.RESULT -> Column {
                FindingsCard(
                    heading = "WHAT WE HEARD",
                    findings = Findings.sound(context.audioEvidence, context.audioEvidenceLabel)
                )
                Gap(Space.md)
                PrimaryAction(
                    "Next: inspect the chain",
                    onNextCamera,
                    leadingIcon = Icons.Default.PhotoCamera
                )
                Gap(Space.sm + Space.xs)
                SecondaryAction("Skip to assessment", onSkipToDiagnosis)
            }
        }
    }
}

private enum class AudioStage { IDLE, ANALYSING, RESULT }

// ── Camera ───────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.CameraSection(
    context: VehicleContext,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    cameraManager: CameraManager,
    isCapturing: Boolean,
    shutterTrigger: Int,
    statusMessage: String?,
    onCapture: () -> Unit,
    onCameraError: (Throwable) -> Unit,
    onShowAssessment: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Eyebrow("Photo check", Icons.Default.PhotoCamera)
    Gap(Space.sm)
    Text(
        "Fill the frame with the chain",
        style = RoadSideType.headlineXlMobile,
        color = RoadSideColors.onSurface
    )
    Gap(Space.sm)
    Text(
        "Get close and steady so the chain and sprocket fill the frame. RoadSide looks for dirt, " +
            "dryness and worn sprocket teeth, and says so when the photo isn't clear enough to tell.",
        style = RoadSideType.bodyMd,
        color = RoadSideColors.onSurfaceVariant
    )
    Gap(Space.lg)

    if (!hasPermission) {
        PermissionCard(
            title = "Camera access needed",
            body = "RoadSide needs the camera to look at the chain.",
            action = "Allow camera",
            onClick = onRequestPermission
        )
        return
    }

    // Viewport nudges inward on capture — the physical shutter feedback.
    val viewportScale by animateFloatAsState(
        if (isCapturing) 0.985f else 1f,
        tween(Motion.PRESS_MS), label = "viewport"
    )

    TelemetryWell(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .scale(viewportScale),
        shape = RoadSideShapes.enclosure
    ) {
        // Close the camera as soon as the preview leaves the screen (switching to Sound,
        // Back, or finishing the flow).
        DisposableEffect(Unit) {
            onDispose { cameraManager.unbind() }
        }
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    // Fill the rounded well; the default FIT_CENTER letterboxes inside it.
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                cameraManager.startCamera(
                    context = ctx,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    onError = onCameraError
                )
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        FramingGuide()
        ShutterFlash(shutterTrigger)
    }
    Gap(Space.md)

    PrimaryAction(
        text = if (isCapturing) "Capturing…" else "Capture photo",
        onClick = onCapture,
        enabled = !isCapturing,
        leadingIcon = Icons.Default.PhotoCamera
    )

    if (statusMessage != null) {
        Gap(Space.sm + Space.xs)
        Text(statusMessage, style = RoadSideType.bodyMd, color = RoadSideColors.error)
    }

    Gap(Space.md)
    AnimatedContent(
        targetState = when {
            context.visionAnalysisPending -> VisionStage.ANALYSING
            context.visionEvidence != null -> VisionStage.RESULT
            else -> VisionStage.IDLE
        },
        transitionSpec = { Motion.revealTransform() },
        label = "visionStage"
    ) { stage ->
        when (stage) {
            VisionStage.IDLE -> Spacer(Modifier.height(0.dp))
            VisionStage.ANALYSING -> AnalysingCard("Looking at the photo…")
            VisionStage.RESULT -> Column {
                FindingsCard(
                    heading = "WHAT WE SAW",
                    findings = Findings.photo(
                        context.visionObservations, context.visionEvidence, context.visionEvidenceLabel
                    )
                )
                Gap(Space.md)
                PrimaryAction(
                    "See the assessment",
                    onShowAssessment,
                    leadingIcon = Icons.Default.CheckCircle
                )
            }
        }
    }
}

private enum class VisionStage { IDLE, ANALYSING, RESULT }

/** Framing reticle: shows how much of the frame the chain should fill. */
@Composable
private fun FramingGuide() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(1f)
                .border(2.dp, RoadSideColors.amber.copy(alpha = 0.5f), RoadSideShapes.card)
        )
    }
}

// ── Shared pieces ────────────────────────────────────────────────────────────

/** Shown while a capture is classified off the main thread, instead of stale or empty evidence. */
@Composable
private fun AnalysingCard(message: String) {
    InstrumentCard(shape = RoadSideShapes.enclosure, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnalysisSweep(Modifier.size(28.dp))
            Gap(Space.md)
            Column {
                Text(message, style = RoadSideType.bodyLg, color = RoadSideColors.onSurface)
                Gap(Space.xs)
                Text(
                    "Running on this device",
                    style = RoadSideType.labelDataSm,
                    color = RoadSideColors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, action: String, onClick: () -> Unit) {
    InstrumentCard(shape = RoadSideShapes.enclosure, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = RoadSideType.headlineMd, color = RoadSideColors.onSurface)
        Gap(Space.xs)
        Text(body, style = RoadSideType.bodyMd, color = RoadSideColors.onSurfaceVariant)
        Gap(Space.md)
        PrimaryAction(action, onClick)
    }
}
