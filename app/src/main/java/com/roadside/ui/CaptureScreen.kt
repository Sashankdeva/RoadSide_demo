package com.roadside.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.roadside.agent.VehicleContext
import com.roadside.audio.AudioRecorder
import com.roadside.camera.CameraManager
import kotlinx.coroutines.delay
import java.io.File

enum class CaptureMode {
    AUDIO,
    CAMERA
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var activeMode by remember { mutableStateOf(initialMode) }
    val androidContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permissions state
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                androidContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                androidContext,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Audio recording state
    // Start from the recorder's real state, not `false`: this screen can be recreated while
    // a recording object still exists, and a stale `false` offered "Start Recording" again.
    var isRecordingAudio by remember { mutableStateOf(audioRecorder.isRecording) }

    var audioRecordSeconds by remember { mutableStateOf(0) }
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

    // Camera capture state
    var isCameraCapturing by remember { mutableStateOf(false) }
    var cameraStatusMessage by remember { mutableStateOf<String?>(null) }
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (activeMode == CaptureMode.AUDIO) "Record Sound" else "Camera Inspection")
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeMode == CaptureMode.AUDIO,
                    onClick = {
                        activeMode = CaptureMode.AUDIO
                        onSwitchToAudio()
                    },
                    label = { Text("Audio Mode") },
                    leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = activeMode == CaptureMode.CAMERA,
                    onClick = {
                        activeMode = CaptureMode.CAMERA
                        onSwitchToCamera()
                    },
                    label = { Text("Camera Mode") },
                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeMode == CaptureMode.AUDIO) {
                // AUDIO MODE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Record vehicle sound",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Hold phone near engine, chain, or wheel while the sound occurs. Diagnostic audio is recorded unfiltered.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!hasAudioPermission) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Microphone Permission Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "RoadSide requires microphone access to record vehicle diagnostic audio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    } else {
                        if (isRecordingAudio) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(20.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Recording vehicle sound: ${audioRecordSeconds}s",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Capturing unfiltered diagnostic frequencies...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val result = audioRecorder.stopRecording()
                                    isRecordingAudio = false
                                    result.onSuccess { savedFile ->
                                        val kb = savedFile.length() / 1024
                                        audioStatusMessage = "Raw WAV recorded: ${savedFile.name} (${savedFile.length()} bytes, 16kHz Mono 16-bit)"
                                        onAudioFileSaved(savedFile)
                                    }.onFailure { err ->
                                        audioStatusMessage = "Recording error: ${err.message}"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val result = audioRecorder.startRecording()
                                    result.onSuccess {
                                        isRecordingAudio = true
                                        audioStatusMessage = null
                                    }.onFailure { err ->
                                        audioStatusMessage = "Failed to start: ${err.message}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Recording")
                            }
                        }
                    }

                    audioStatusMessage?.let { msg ->
                        Text(text = msg, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (context.audioAnalysisPending) {
                        AnalysingCard("Analysing the recording on device...")
                    } else if (context.audioEvidence != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Audio Evidence Extracted",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = context.audioEvidenceLabel.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Button(
                            onClick = {
                                activeMode = CaptureMode.CAMERA
                                onSwitchToCamera()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Next: Inspect with Camera")
                        }

                        OutlinedButton(
                            onClick = onContinueToDiagnosis,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go Directly to Diagnosis")
                        }
                    }
                }
            } else {
                // CAMERA MODE
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Show me the motorcycle",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Point camera at suspect component (chain, brake rotor, battery).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!hasCameraPermission) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Camera Permission Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "RoadSide requires camera access to inspect the vehicle.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Grant Camera Permission")
                                }
                            }
                        }
                    } else {
                        // Live Camera Preview box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            // Close the camera as soon as the preview leaves the screen
                            // (switching to Audio mode, Back, or finishing the flow).
                            DisposableEffect(Unit) {
                                onDispose { cameraManager.unbind() }
                            }
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    cameraManager.startCamera(
                                        context = ctx,
                                        lifecycleOwner = lifecycleOwner,
                                        previewView = previewView,
                                        onError = { err ->
                                            cameraStatusMessage = "Camera initialization issue: ${err.message}"
                                        }
                                    )
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (isCameraCapturing) {
                            CircularProgressIndicator()
                        } else {
                            Button(
                                onClick = {
                                    isCameraCapturing = true
                                    cameraManager.capturePhoto(
                                        context = androidContext,
                                        onSuccess = { photoFile ->
                                            isCameraCapturing = false
                                            capturedPhotoFile = photoFile
                                            cameraStatusMessage = "Photo captured (${photoFile.name})"
                                            onVisionPhotoSaved(photoFile)
                                        },
                                        onError = { err ->
                                            isCameraCapturing = false
                                            cameraStatusMessage = "Capture failed: ${err.message}"
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Capture Photo")
                            }
                        }
                    }

                    cameraStatusMessage?.let { msg ->
                        Text(text = msg, style = MaterialTheme.typography.bodySmall)
                    }

                    if (context.visionAnalysisPending) {
                        AnalysingCard("Analysing the photo on device...")
                    } else if (context.visionEvidence != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Visual Evidence Extracted",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = context.visionEvidenceLabel.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Button(
                            onClick = onContinueToDiagnosis,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show Diagnosis")
                        }
                    }
                }
            }
        }
    }
}

/** Shown while a capture is classified off the main thread, instead of stale or empty evidence. */
@Composable
private fun AnalysingCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
