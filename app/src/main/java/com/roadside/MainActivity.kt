package com.roadside

import android.app.Activity
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.roadside.agent.RoadSideAgent
import com.roadside.audio.AudioRecorder
import com.roadside.camera.CameraManager
import com.roadside.speech.SpeechRecognizer
import com.roadside.ui.*
import com.roadside.ui.theme.RoadSideColors
import com.roadside.ui.theme.RoadSideTheme
import com.roadside.ui.theme.screenTransition
import kotlinx.coroutines.launch

/**
 * The flow: Home → Describe → Listen → Inspect → Assessment → Solution.
 *
 * Listen and Inspect are both [Capture] (one composable owns the recorder and camera
 * lifecycle); the mode decides which step it is.
 */
sealed class Screen {
    object Home : Screen()
    object Describe : Screen()
    data class Capture(val mode: CaptureMode) : Screen()
    object Assessment : Screen()
    object Solution : Screen()
    object SensorCheck : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var agent: RoadSideAgent
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var cameraManager: CameraManager
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        agent = RoadSideAgent(applicationContext)
        audioRecorder = AudioRecorder(applicationContext)
        cameraManager = CameraManager()
        speechRecognizer = SpeechRecognizer(applicationContext)

        setContent {
            RoadSideTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(RoadSideColors.canvas)
                        .navigationBarsPadding()
                ) {
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
    // A real back stack: Back returns to where the rider actually came from (Inspect may be
    // reached from Listen or straight from Describe), and the transition direction follows.
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var forward by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val current = stack.last()

    val navigate: (Screen) -> Unit = { target ->
        forward = true
        stack.add(target)
    }
    val back: () -> Unit = {
        if (stack.size > 1) {
            forward = false
            stack.removeAt(stack.lastIndex)
        }
    }
    val restart: () -> Unit = {
        agent.resetSession()
        forward = false
        stack.clear()
        stack.add(Screen.Home)
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            if (spokenText.isNotBlank()) agent.handleUserText(spokenText)
        }
    }

    val triggerSpeech = {
        try {
            speechLauncher.launch(SpeechRecognizer.createSpeechIntent())
        } catch (e: Exception) {
            // Fallback to the in-process recogniser.
            speechRecognizer.startListening(
                onResult = { text -> if (text.isNotBlank()) agent.handleUserText(text) },
                onError = { /* the screen keeps its typed text; nothing to surface */ }
            )
        }
    }

    BackHandler(enabled = stack.size > 1) { back() }

    AnimatedContent(
        targetState = current,
        transitionSpec = { screenTransition(forward) },
        label = "screen"
    ) { screen ->
        when (screen) {
            is Screen.Home -> HomeScreen(
                context = contextState,
                onVehicleTypeChanged = { agent.updateVehicleType(it) },
                onStartCheck = { navigate(Screen.Describe) },
                onContinueCheck = {
                    agent.ensureDiagnosis()
                    navigate(Screen.Assessment)
                },
                onSensorCheck = { navigate(Screen.SensorCheck) }
            )

            is Screen.Describe -> DescribeScreen(
                context = contextState,
                onVehicleTypeChanged = { agent.updateVehicleType(it) },
                onProblemDescriptionChanged = { },
                onAskRoadSide = { problem -> agent.handleUserText(problem) },
                onListenClicked = { navigate(Screen.Capture(CaptureMode.AUDIO)) },
                onInspectClicked = { navigate(Screen.Capture(CaptureMode.CAMERA)) },
                onVoiceInputRequested = triggerSpeech,
                onBack = back
            )

            is Screen.Capture -> CaptureScreen(
                initialMode = screen.mode,
                context = contextState,
                audioRecorder = audioRecorder,
                cameraManager = cameraManager,
                onAudioFileSaved = { file ->
                    coroutineScope.launch { agent.processAudioCapture(file) }
                },
                onVisionPhotoSaved = { file ->
                    coroutineScope.launch { agent.processVisionCapture(file) }
                },
                onContinueToDiagnosis = {
                    agent.ensureDiagnosis()
                    navigate(Screen.Assessment)
                },
                onSwitchToCamera = { navigate(Screen.Capture(CaptureMode.CAMERA)) },
                onSwitchToAudio = { navigate(Screen.Capture(CaptureMode.AUDIO)) },
                onBackClicked = back
            )

            is Screen.Assessment -> AssessmentScreen(
                context = contextState,
                onSeeSolution = { navigate(Screen.Solution) },
                onCheckAgain = { navigate(Screen.Capture(CaptureMode.AUDIO)) },
                onBack = back
            )

            is Screen.Solution -> SolutionScreen(
                context = contextState,
                onBack = back,
                onFinished = restart
            )

            is Screen.SensorCheck -> YamNetDebugScreen(
                androidContext = context,
                onBackClicked = back
            )
        }
    }
}
