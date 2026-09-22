package com.roadside.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.roadside.audio.WavDecoder
import com.roadside.sensing.EvidenceTranslator
import com.roadside.sensing.YamNetBatchValidator
import com.roadside.sensing.YamNetInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Developer debug screen for inspecting YAMNet model internals.
 *
 * Accessible via the "YAMNet Debug" button on [HomeScreen].
 * Does NOT alter the normal user flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YamNetDebugScreen(
    androidContext: Context,
    onBackClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // UI state
    var statusText by remember { mutableStateOf("Ready. Tap 'Run Inference' to test last recording.") }
    var inferenceResult by remember { mutableStateOf<DebugResult?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var batchReport by remember { mutableStateOf<YamNetBatchValidator.BatchReport?>(null) }

    // Model metadata loaded once
    var modelMeta by remember { mutableStateOf<ModelMeta?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val interp = YamNetInterpreter(androidContext)
                val inputShape  = interp.inputShape().toList()
                val outputShapes = interp.outputShapes().map { it.toList() }
                interp.close()
                modelMeta = ModelMeta(
                    modelFile = YamNetInterpreter.MODEL_FILENAME,
                    windowSamples = YamNetInterpreter.WINDOW_SAMPLES,
                    numClasses = YamNetInterpreter.NUM_CLASSES,
                    embeddingDim = YamNetInterpreter.EMBEDDING_DIM,
                    inputShape = inputShape,
                    outputShapes = outputShapes
                )
            } catch (e: Exception) {
                statusText = "Failed to load model: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor check") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Model metadata ────────────────────────────────────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Model Information", style = MaterialTheme.typography.titleSmall)
                        if (modelMeta != null) {
                            val m = modelMeta!!
                            MonoText("File: ${m.modelFile}")
                            MonoText("Window: ${m.windowSamples} samples (${m.windowSamples / 16000f}s @ 16kHz)")
                            MonoText("Classes: ${m.numClasses} AudioSet labels")
                            MonoText("Embedding dim: ${m.embeddingDim}")
                            MonoText("Input shape:  ${m.inputShape}")
                            m.outputShapes.forEachIndexed { i, s ->
                                MonoText("Output[$i] shape: $s")
                            }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Inference trigger ─────────────────────────────────────────────
            item {
                Button(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            statusText = "Loading last WAV..."
                            val result = withContext(Dispatchers.IO) {
                                runDebugInference(androidContext)
                            }
                            inferenceResult = result
                            statusText = result?.summary ?: "No result"
                            isRunning = false
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Run Inference on Last Recording")
                }
                Text(statusText, style = MaterialTheme.typography.bodySmall)
            }

            // ── Batch validation over pushed test recordings ───────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Batch Validation", style = MaterialTheme.typography.titleSmall)
                        MonoText("Push .wav files to:")
                        MonoText(YamNetBatchValidator.testDir(androidContext).absolutePath)
                        Button(
                            onClick = {
                                scope.launch {
                                    isRunning = true
                                    batchReport = null
                                    statusText = "Running batch validation..."
                                    val report = withContext(Dispatchers.IO) {
                                        YamNetBatchValidator.runBatch(androidContext)
                                    }
                                    batchReport = report
                                    statusText = if (report.filesFound == 0) {
                                        "No .wav files found in test dir — push recordings first."
                                    } else {
                                        "Batch done: ${report.filesFound} file(s). " +
                                            "Report: ${report.reportFile?.name ?: "not written"}"
                                    }
                                    isRunning = false
                                }
                            },
                            enabled = !isRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Run Batch Validation")
                        }
                    }
                }
            }

            val batch = batchReport
            if (batch != null && batch.fileReports.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Batch Results", style = MaterialTheme.typography.titleSmall)
                            MonoText("Model init: ${batch.modelInitMs} ms")
                        }
                    }
                }
                itemsIndexed(batch.fileReports) { _, fileReport ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            MonoText(YamNetBatchValidator.formatFileReport(fileReport))
                        }
                    }
                }
            }

            // ── Results ───────────────────────────────────────────────────────
            val result = inferenceResult
            if (result != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Inference Summary", style = MaterialTheme.typography.titleSmall)
                            MonoText("Windows processed: ${result.numWindows}")
                            MonoText("Total inference: ${result.totalInferenceMs}ms")
                            MonoText("Avg per window:  ${result.avgInferenceMs}ms")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("RoadSide Evidence", style = MaterialTheme.typography.labelMedium)
                            MonoText("Label:       ${result.evidenceLabel}")
                            MonoText("Score:       ${"%.3f".format(result.evidenceConfidence)} (uncalibrated)")
                            MonoText("Description: ${result.evidenceDescription}")
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Top-10 AudioSet Labels (mean score)", style = MaterialTheme.typography.titleSmall)
                            result.top10.forEachIndexed { rank, (label, score) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${rank + 1}. $label",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "%.4f".format(score),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Helper composable ─────────────────────────────────────────────────────────

@Composable
private fun MonoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}

// ── Data classes ──────────────────────────────────────────────────────────────

private data class ModelMeta(
    val modelFile: String,
    val windowSamples: Int,
    val numClasses: Int,
    val embeddingDim: Int,
    val inputShape: List<Int>,
    val outputShapes: List<List<Int>>
)

private data class DebugResult(
    val numWindows: Int,
    val totalInferenceMs: Long,
    val avgInferenceMs: Long,
    val top10: List<Pair<String, Float>>,
    val evidenceLabel: String,
    val evidenceConfidence: Float,
    val evidenceDescription: String,
    val summary: String
)

// ── Inference logic (runs on IO thread) ──────────────────────────────────────

/**
 * Finds the most recent WAV in the diagnostic cache and runs YAMNet on it.
 * Returns null if no recording is found.
 */
private fun runDebugInference(context: Context): DebugResult? {
    val cacheDir = File(context.cacheDir, "audio_diagnostics")
    val wavFile = cacheDir.listFiles { f -> f.extension == "wav" }
        ?.maxByOrNull { it.lastModified() }
        ?: return null

    val samples = WavDecoder.decode(wavFile)
    if (samples.isEmpty()) return null

    val interpreter = YamNetInterpreter(context)
    val classMap = loadClassMap(context)

    val meanScores = FloatArray(YamNetInterpreter.NUM_CLASSES)
    var totalMs = 0L
    var numWindows = 0
    var start = 0
    val hop = YamNetInterpreter.WINDOW_SAMPLES / 2

    while (start < samples.size) {
        val window = FloatArray(YamNetInterpreter.WINDOW_SAMPLES)
        val copyLen = minOf(YamNetInterpreter.WINDOW_SAMPLES, samples.size - start)
        System.arraycopy(samples, start, window, 0, copyLen)

        val result = interpreter.runWindow(window)
        totalMs += result.inferenceTimeMs
        for (i in meanScores.indices) meanScores[i] += result.scores[i]
        numWindows++
        start += hop
    }

    interpreter.close()

    if (numWindows == 0) return null
    for (i in meanScores.indices) meanScores[i] /= numWindows

    // Top-10 labels
    val top10 = meanScores.mapIndexed { idx, score -> idx to score }
        .sortedByDescending { it.second }
        .take(10)
        .map { (idx, score) -> (classMap[idx] ?: "class_$idx") to score }

    val evidence = EvidenceTranslator.translate(meanScores)

    return DebugResult(
        numWindows = numWindows,
        totalInferenceMs = totalMs,
        avgInferenceMs = if (numWindows > 0) totalMs / numWindows else 0L,
        top10 = top10,
        evidenceLabel = evidence.label,
        evidenceConfidence = evidence.evidenceScore,
        evidenceDescription = evidence.labelDescription,
        summary = "Done: $numWindows windows, ${totalMs}ms total"
    )
}

/** Loads class display names from yamnet_class_map.csv in assets. */
private fun loadClassMap(context: Context): Map<Int, String> {
    return try {
        context.assets.open("yamnet_class_map.csv").bufferedReader().useLines { lines ->
            lines.drop(1) // skip header
                .mapNotNull { line ->
                    val parts = line.split(",", limit = 3)
                    if (parts.size >= 3) parts[0].trim().toIntOrNull()?.let { it to parts[2].trim() }
                    else null
                }
                .toMap()
        }
    } catch (e: Exception) {
        emptyMap()
    }
}
