package com.roadside

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.roadside.agent.RoadSideAgent
import com.roadside.audio.AudioRecorder
import com.roadside.camera.CameraManager
import com.roadside.speech.SpeechRecognizer
import com.roadside.ui.*
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    object Chat : Screen()
    data class Capture(val mode: CaptureMode) : Screen()
    object Diagnosis : Screen()
    object Guide : Screen()
    object YamNetDebug : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var agent: RoadSideAgent
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var cameraManager: CameraManager
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        agent = RoadSideAgent(applicationContext)
        audioRecorder = AudioRecorder(applicationContext)
        cameraManager = CameraManager()
        speechRecognizer = SpeechRecognizer(applicationContext)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RoadSideApp(
                        agent = agent,
                        audioRecorder = audioRecorder,
                        cameraManager = cameraManager,
                        speechRecognizer = speechRecognizer,
                        context = applicationContext
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.release()
        cameraManager.unbind()
        speechRecognizer.stopListening()
    }
}

@Composable
fun RoadSideApp(
    agent: RoadSideAgent,
    audioRecorder: AudioRecorder,
    cameraManager: CameraManager,
    speechRecognizer: SpeechRecognizer,
    context: android.content.Context
) {
    val contextState by agent.context.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val coroutineScope = rememberCoroutineScope()

    // Activity result launcher for speech recognition
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenMatches?.firstOrNull().orEmpty()
            if (spokenText.isNotBlank()) {
                agent.handleUserText(spokenText)
                currentScreen = Screen.Chat
            }
        }
    }

    val triggerSpeech = {
        try {
            val intent = SpeechRecognizer.createSpeechIntent()
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback to internal speech recognizer
            speechRecognizer.startListening(
                onResult = { text ->
                    if (text.isNotBlank()) {
                        agent.handleUserText(text)
                        currentScreen = Screen.Chat
                    }
                },
                onError = { /* fallback silent or toast handled */ }
            )
        }
    }

    // Handle system back navigation
    BackHandler(enabled = currentScreen !is Screen.Home) {
        currentScreen = when (currentScreen) {
            is Screen.Guide -> Screen.Diagnosis
            is Screen.Diagnosis -> Screen.Chat
            is Screen.Capture -> Screen.Chat
            is Screen.Chat -> Screen.Home
            is Screen.YamNetDebug -> Screen.Home
            is Screen.Home -> Screen.Home
        }
    }

    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                context = contextState,
                onVehicleTypeChanged = { agent.updateVehicleType(it) },
                onProblemDescriptionChanged = { /* tracked in state */ },
                onAskRoadSide = { problem ->
                    agent.handleUserText(problem)
                    currentScreen = Screen.Chat
                },
                onListenClicked = {
                    currentScreen = Screen.Capture(CaptureMode.AUDIO)
                },
                onInspectClicked = {
                    currentScreen = Screen.Capture(CaptureMode.CAMERA)
                },
                onVoiceInputRequested = triggerSpeech,
                onDebugClicked = { currentScreen = Screen.YamNetDebug }
            )
        }

        is Screen.Chat -> {
            ChatScreen(
                context = contextState,
                onSendMessage = { text ->
                    agent.handleUserText(text)
                },
                onRecordSoundClicked = {
                    currentScreen = Screen.Capture(CaptureMode.AUDIO)
                },
                onOpenCameraClicked = {
                    currentScreen = Screen.Capture(CaptureMode.CAMERA)
                },
                onViewDiagnosisClicked = {
                    agent.ensureDiagnosis()
                    currentScreen = Screen.Diagnosis
                },
                onVoiceInputRequested = triggerSpeech,
                onBackClicked = {
                    currentScreen = Screen.Home
                }
            )
        }

        is Screen.Capture -> {
            CaptureScreen(
                initialMode = screen.mode,
                context = contextState,
                audioRecorder = audioRecorder,
                cameraManager = cameraManager,
                onAudioFileSaved = { file ->
                    coroutineScope.launch {
                        agent.processAudioCapture(file)
                    }
                },
                onVisionPhotoSaved = { file ->
                    coroutineScope.launch {
                        agent.processVisionCapture(file)
                    }
                },
                onContinueToDiagnosis = {
                    agent.ensureDiagnosis()
                    currentScreen = Screen.Diagnosis
                },
                onSwitchToCamera = {
                    currentScreen = Screen.Capture(CaptureMode.CAMERA)
                },
                onSwitchToAudio = {
                    currentScreen = Screen.Capture(CaptureMode.AUDIO)
                },
                onBackClicked = {
                    currentScreen = Screen.Chat
                }
            )
        }

        is Screen.Diagnosis -> {
            DiagnosisScreen(
                context = contextState,
                onGuideMeClicked = {
                    agent.setGuideStep(0)
                    currentScreen = Screen.Guide
                },
                onBackClicked = {
                    currentScreen = Screen.Chat
                }
            )
        }

        is Screen.Guide -> {
            GuideScreen(
                context = contextState,
                onStepChanged = { step ->
                    agent.setGuideStep(step)
                },
                onFinishGuide = {
                    agent.resetSession()
                    currentScreen = Screen.Home
                },
                onBackClicked = {
                    currentScreen = Screen.Diagnosis
                }
            )
        }

        is Screen.YamNetDebug -> {
            YamNetDebugScreen(
                androidContext = context,
                onBackClicked = { currentScreen = Screen.Home }
            )
        }
    }
}
