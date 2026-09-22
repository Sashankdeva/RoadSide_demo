package com.roadside.sensing

import android.content.Context
import android.os.Debug
import android.util.Log
import java.io.File

/**
 * Offline validation harness: runs a folder of WAV recordings through the exact
 * production YAMNet path and prints raw model output next to the RoadSide evidence
 * category derived from it.
 *
 * This is a measurement tool, not part of the app flow. It does not modify
 * [YamNetInterpreter], [EvidenceTranslator] or [YamNetAudioClassifier] — it only
 * observes them, so what it reports is what the app would actually do.
 *
 * Usage:
 *   adb push clip.wav /sdcard/Android/data/com.roadside/files/roadside_test/
 *   (tap "Run Batch Validation" on the YAMNet Debug screen)
 *   adb pull /sdcard/Android/data/com.roadside/files/roadside_test/validation_report.txt
 *
 * Interpretation rule: YAMNet is a general AudioSet sound-event model. Its raw
 * predictions are acoustic observations ("Motorcycle", "Rattle"), never fault
 * verdicts. Any mechanical meaning is introduced downstream by [EvidenceTranslator],
 * and is only as good as that mapping.
 */
object YamNetBatchValidator {

    private const val TAG = "YamNetBatchValidator"
    const val TEST_DIR_NAME = "roadside_test"
    const val REPORT_FILENAME = "validation_report.txt"
    private const val TOP_N = 5

    /** Hop between consecutive windows — mirrors YamNetAudioClassifier (50% overlap). */
    private val HOP_SAMPLES = YamNetInterpreter.WINDOW_SAMPLES / 2

    data class FileReport(
        val fileName: String,
        val ok: Boolean,
        val message: String,
        val durationSeconds: Float = 0f,
        val numWindows: Int = 0,
        val topEvents: List<Pair<String, Float>> = emptyList(),
        val evidenceLabel: String = "",
        val evidenceScore: Float = 0f,
        val evidenceReason: String = "",
        val evidenceTopContributor: String = "",
        val groupLines: List<String> = emptyList(),
        val firstInferenceMs: Long = 0L,
        val warmAvgMs: Long = 0L,
        val totalInferenceMs: Long = 0L,
        val wallClockMs: Long = 0L,
        val sourceInfo: String = ""
    )

    data class BatchReport(
        val text: String,
        val reportFile: File?,
        val fileReports: List<FileReport>,
        val modelInitMs: Long,
        val filesFound: Int
    )

    /**
     * Folder that test clips are pushed into. Created on demand.
     *
     * Falls back to internal storage if external storage is unavailable — note that adb push
     * cannot reach the internal fallback, so that case needs `adb shell run-as`.
     */
    fun testDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, TEST_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Runs every `.wav` in [testDir] through YAMNet and writes a report file.
     * Call from a background thread.
     */
    fun runBatch(context: Context): BatchReport {
        val dir = testDir(context)
        val wavFiles = dir.listFiles { f -> f.isFile && f.extension.equals("wav", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        val sb = StringBuilder()
        sb.appendLine("RoadSide — YAMNet validation report")
        sb.appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("Test dir: ${dir.absolutePath}")
        sb.appendLine("Window: ${YamNetInterpreter.WINDOW_SAMPLES} samples (${YamNetInterpreter.WINDOW_SAMPLES / 16000f}s) | Hop: $HOP_SAMPLES samples (${HOP_SAMPLES / 16000f}s)")
        sb.appendLine("=".repeat(72))

        if (wavFiles.isEmpty()) {
            sb.appendLine()
            sb.appendLine("No .wav files found. Push recordings with:")
            sb.appendLine("  adb push <clip>.wav ${dir.absolutePath}/")
            val text = sb.toString()
            Log.w(TAG, "No WAV files in ${dir.absolutePath}")
            return BatchReport(text, writeReport(dir, text), emptyList(), 0L, 0)
        }

        // ── Model initialisation (cold) ──────────────────────────────────────
        val memBefore = usedHeapKb()
        val initStart = System.nanoTime()
        val interpreter = try {
            YamNetInterpreter(context)
        } catch (e: Exception) {
            val text = sb.appendLine().appendLine("FATAL: model failed to load: ${e.message}").toString()
            Log.e(TAG, "Model init failed", e)
            return BatchReport(text, writeReport(dir, text), emptyList(), 0L, wavFiles.size)
        }
        val initMs = (System.nanoTime() - initStart) / 1_000_000
        val memAfterInit = usedHeapKb()

        sb.appendLine()
        sb.appendLine("Model init: ${initMs} ms  (heap ${memBefore} KB -> ${memAfterInit} KB, +${memAfterInit - memBefore} KB)")
        sb.appendLine("Input shape:  ${interpreter.inputShape().toList()}")
        interpreter.outputShapes().forEachIndexed { i, s -> sb.appendLine("Output[$i] shape: ${s.toList()}") }

        val classMap = loadClassMap(context)
        val reports = mutableListOf<FileReport>()

        for (wav in wavFiles) {
            val report = processFile(wav, interpreter, classMap)
            reports.add(report)
            sb.appendLine()
            sb.appendLine("-".repeat(72))
            sb.append(formatFileReport(report))
        }

        val memEnd = usedHeapKb()
        interpreter.close()

        sb.appendLine()
        sb.appendLine("=".repeat(72))
        sb.appendLine("Heap after ${reports.size} file(s): ${memEnd} KB (delta since init ${memEnd - memAfterInit} KB)")
        sb.appendLine()
        sb.appendLine("NOTE: RAW YAMNET is what the model emitted — AudioSet acoustic observations only.")
        sb.appendLine("      ROADSIDE INTERPRETATION is the EvidenceTranslator mapping applied on top of them.")
        sb.appendLine("      Scores are uncalibrated (max per group of independent sigmoids), not probabilities.")
        sb.appendLine("      Neither section constitutes a mechanical fault diagnosis.")
        sb.appendLine("      Thresholds are PROVISIONAL placeholders — tune only against real N160 recordings.")

        val text = sb.toString()
        Log.i(TAG, text)
        return BatchReport(text, writeReport(dir, text), reports, initMs, wavFiles.size)
    }

    private fun processFile(
        wav: File,
        interpreter: YamNetInterpreter,
        classMap: Map<Int, String>
    ): FileReport {
        val wallStart = System.nanoTime()

        val decoded = try {
            ValidationWavReader.decode(wav)
        } catch (e: Exception) {
            return FileReport(wav.name, false, "DECODE FAILED: ${e.message}")
        }

        if (decoded.samples.isEmpty()) {
            return FileReport(wav.name, false, "DECODE FAILED: no samples")
        }

        val sourceInfo = buildString {
            append("${decoded.sourceSampleRate} Hz, ${decoded.sourceChannels} ch, ${decoded.sourceBitsPerSample}-bit")
            if (decoded.resampled) append(" [resampled to 16 kHz]")
            if (decoded.downmixed) append(" [downmixed to mono]")
        }

        // Windowing — identical to YamNetAudioClassifier.buildWindows
        val samples = decoded.samples
        val meanScores = FloatArray(YamNetInterpreter.NUM_CLASSES)
        var numWindows = 0
        var totalMs = 0L
        var firstMs = 0L
        var warmTotalMs = 0L
        var start = 0

        while (start < samples.size) {
            val window = FloatArray(YamNetInterpreter.WINDOW_SAMPLES)
            val copyLen = minOf(YamNetInterpreter.WINDOW_SAMPLES, samples.size - start)
            System.arraycopy(samples, start, window, 0, copyLen)

            val t0 = System.nanoTime()
            val result = interpreter.runWindow(window)
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000

            if (numWindows == 0) firstMs = elapsedMs else warmTotalMs += elapsedMs
            totalMs += elapsedMs

            for (i in meanScores.indices) meanScores[i] += result.scores[i]
            numWindows++
            start += HOP_SAMPLES
        }

        if (numWindows == 0) return FileReport(wav.name, false, "No windows produced", sourceInfo = sourceInfo)
        for (i in meanScores.indices) meanScores[i] /= numWindows

        val topEvents = meanScores.mapIndexed { idx, s -> idx to s }
            .sortedByDescending { it.second }
            .take(TOP_N)
            .map { (idx, s) -> (classMap[idx] ?: "class_$idx") to s }

        val evidence = EvidenceTranslator.translate(meanScores)
        val wallMs = (System.nanoTime() - wallStart) / 1_000_000

        return FileReport(
            fileName = wav.name,
            ok = true,
            message = "OK",
            durationSeconds = decoded.durationSeconds,
            numWindows = numWindows,
            topEvents = topEvents,
            evidenceLabel = evidence.label,
            evidenceScore = evidence.evidenceScore,
            evidenceReason = evidence.reason,
            evidenceTopContributor = evidence.topContributor,
            groupLines = EvidenceTranslator.breakdown(meanScores).lines(),
            firstInferenceMs = firstMs,
            warmAvgMs = if (numWindows > 1) warmTotalMs / (numWindows - 1) else 0L,
            totalInferenceMs = totalMs,
            wallClockMs = wallMs,
            sourceInfo = sourceInfo
        )
    }

    /**
     * Renders one file's result.
     *
     * RAW YAMNET and ROADSIDE INTERPRETATION are kept strictly separate: the first is what
     * the model actually emitted, the second is what our mapping made of it. Conflating them
     * is how "YAMNet detected a chain fault" gets said out loud.
     */
    fun formatFileReport(r: FileReport): String = buildString {
        appendLine("File:     ${r.fileName}")
        if (!r.ok) {
            appendLine("Status:   ${r.message}")
            if (r.sourceInfo.isNotEmpty()) appendLine("Source:   ${r.sourceInfo}")
            return@buildString
        }
        appendLine("Source:   ${r.sourceInfo}")
        appendLine("Duration: ${"%.2f".format(r.durationSeconds)} s")
        appendLine("Windows:  ${r.numWindows}")
        appendLine()

        appendLine("RAW YAMNET")
        r.topEvents.forEachIndexed { i, (label, score) ->
            appendLine("  ${i + 1}. ${label.padEnd(34)} — ${"%.4f".format(score)}")
        }
        appendLine()

        appendLine("ROADSIDE INTERPRETATION")
        appendLine("  category: ${r.evidenceLabel}")
        appendLine("  evidence: ${r.evidenceTopContributor}  (uncalibrated score ${"%.4f".format(r.evidenceScore)})")
        appendLine("  reason:   ${r.evidenceReason}")
        if (r.groupLines.isNotEmpty()) {
            appendLine("  group scores (max per group, uncalibrated):")
            r.groupLines.forEach { appendLine("    $it") }
        }
        appendLine()

        appendLine("Inference time:")
        appendLine("  first window: ${r.firstInferenceMs} ms")
        appendLine("  warm avg:     ${r.warmAvgMs} ms/window")
        appendLine("  total infer:  ${r.totalInferenceMs} ms")
        appendLine("  clip total:   ${r.wallClockMs} ms (decode + windowing + inference)")
    }

    private fun writeReport(dir: File, text: String): File? = try {
        val f = File(dir, REPORT_FILENAME)
        f.writeText(text)
        f
    } catch (e: Exception) {
        Log.e(TAG, "Could not write report", e)
        null
    }

    private fun usedHeapKb(): Long {
        val rt = Runtime.getRuntime()
        val javaHeap = (rt.totalMemory() - rt.freeMemory()) / 1024
        val native = Debug.getNativeHeapAllocatedSize() / 1024
        return javaHeap + native
    }

    private fun loadClassMap(context: Context): Map<Int, String> = try {
        context.assets.open("yamnet_class_map.csv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val parts = line.split(",", limit = 3)
                if (parts.size >= 3) {
                    parts[0].trim().toIntOrNull()?.let { it to parts[2].trim().trim('"') }
                } else null
            }.toMap()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not load class map", e)
        emptyMap()
    }
}
