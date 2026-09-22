package com.roadside

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadside.rules.DiagnosisRules
import com.roadside.sensing.ChainConditionHead
import com.roadside.sensing.ChainVisionClassifier
import com.roadside.sensing.ImagePreprocessor
import com.roadside.sensing.MobileNetFeatureExtractor
import com.roadside.sensing.VisionClassificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Device-side verification for the real vision path.
 *
 *   adb shell am instrument -w -e class com.roadside.ChainVisionTest \
 *     com.roadside.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Validation images can be pushed to
 *   /sdcard/Android/data/com.roadside/files/vision_test/
 * and are then classified with their expected label taken from the filename prefix
 * (`chain_serviceable__*.jpg`, `chain_corroded__*.jpg`, `not_chain__*.jpg`). With no
 * images present the structural checks still run against a synthetic image.
 */
@RunWith(AndroidJUnit4::class)
class ChainVisionTest {

    companion object {
        const val TAG = "RoadSideVision"
        const val TEST_DIR = "vision_test"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(msg: String) = msg.lines().forEach { Log.i(TAG, it) }

    private fun usedKb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1024 + Debug.getNativeHeapAllocatedSize() / 1024
    }

    private fun testDir(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, TEST_DIR)
            .also { if (!it.exists()) it.mkdirs() }

    /** A synthetic image so structural checks work with no pushed data. */
    private fun syntheticImage(): File {
        val f = File(context.cacheDir, "vision_probe.jpg")
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(60, 60, 64))
        val p = Paint().apply { color = android.graphics.Color.rgb(180, 180, 190) }
        for (i in 0 until 12) {
            c.drawCircle(60f + i * 45f, 240f, 16f, p)
        }
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return f
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Backbone + head load, shapes are what we expect, latency measured.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun visionModelLoadsAndReportsShapes() {
        val memBefore = usedKb()
        val t0 = System.nanoTime()
        val extractor = MobileNetFeatureExtractor(context)
        val backboneInitMs = (System.nanoTime() - t0) / 1_000_000L

        val t1 = System.nanoTime()
        val head = ChainConditionHead.load(context)
        val headInitMs = (System.nanoTime() - t1) / 1_000_000L
        val memAfter = usedKb()

        val img = syntheticImage()
        val input = ImagePreprocessor.toModelInput(img)

        val t2 = System.nanoTime()
        val features = extractor.extract(input)
        val firstMs = (System.nanoTime() - t2) / 1_000_000L
        val warm = (1..3).map {
            val s = System.nanoTime()
            extractor.extract(input)
            (System.nanoTime() - s) / 1_000_000L
        }

        log("")
        log("========== VISION MODEL ==========")
        log("BACKBONE:     " + MobileNetFeatureExtractor.MODEL_FILENAME)
        log("  input:      " + extractor.inputShape.toList())
        log("  output:     " + extractor.outputShape.toList())
        log("  init:       " + backboneInitMs + " ms")
        log("HEAD:         " + ChainConditionHead.ASSET_FILENAME)
        log("  classes:    " + head.classes)
        log("  featureDim: " + head.featureDim)
        log("  init:       " + headInitMs + " ms")
        head.metadata.forEach { (k, v) -> log("  " + k + " = " + v) }
        log("FEATURE VECTOR: dim=" + features.size)
        log("  mean=" + "%.4f".format(features.average()) +
            " min=" + "%.4f".format(features.min()) +
            " max=" + "%.4f".format(features.max()))
        log("LATENCY:      first=" + firstMs + " ms  warm=" + warm.joinToString("/") + " ms")
        log("MEMORY:       " + memBefore + " -> " + memAfter + " KB (delta " + (memAfter - memBefore) + ")")
        log("==================================")

        assertEquals("Backbone must emit a 1280-d vector",
            MobileNetFeatureExtractor.FEATURE_DIM, features.size)
        assertEquals("Head feature dim must match backbone",
            MobileNetFeatureExtractor.FEATURE_DIM, head.featureDim)
        assertTrue("Head must have at least 2 classes", head.classes.size >= 2)
        assertTrue("Features are all zero — backbone did not run",
            features.any { it != 0f })
        extractor.close()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Preprocessing: EXIF, centre crop, range.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun preprocessingProducesCorrectTensor() {
        val img = syntheticImage()
        val t = ImagePreprocessor.toModelInput(img)

        log("")
        log("========== PREPROCESSING ==========")
        log("tensor: [" + t.size + ", " + t[0].size + ", " + t[0][0].size + ", " + t[0][0][0].size + "]")
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (row in t[0]) for (px in row) for (v in px) {
            if (v < min) min = v
            if (v > max) max = v
        }
        log("value range: " + "%.4f".format(min) + " .. " + "%.4f".format(max))
        log("===================================")

        assertEquals(1, t.size)
        assertEquals(ImagePreprocessor.INPUT_SIZE, t[0].size)
        assertEquals(ImagePreprocessor.INPUT_SIZE, t[0][0].size)
        assertEquals(ImagePreprocessor.CHANNELS, t[0][0][0].size)
        assertTrue("Values must be normalised to [0,1], got $min..$max", min >= 0f && max <= 1f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Classify pushed validation images, if any, and score them.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun classifyValidationImages() {
        val dir = testDir()
        val images = dir.listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png")
        }?.sortedBy { it.name } ?: emptyList()

        val classifier = ChainVisionClassifier(context)
        log("")
        log("========== VISION VALIDATION ==========")
        log("model: " + classifier.modelInfo())
        log("test dir: " + dir.absolutePath)

        if (images.isEmpty()) {
            log("NO VALIDATION IMAGES PUSHED — running synthetic image only.")
            val d = classifier.classifyDetailed(syntheticImage())
            log("synthetic -> " + d.result.label + " (" + d.topClass + ") " + d.probabilities)
            log("  reason: " + d.reason)
            log("=======================================")
            assertNotNull(d.result.label)
            classifier.close()
            return
        }

        var correct = 0
        var scored = 0
        var unknowns = 0
        val falseChain = mutableListOf<String>()
        var chainHits = 0
        for (f in images) {
            val expected = f.name.substringBefore("__", "").ifEmpty { null }
            val d = classifier.classifyDetailed(f)
            if (d.result.label == VisionClassificationResult.UNKNOWN) unknowns++

            // Map the head's class back to the filename convention for scoring.
            val isChainDetected = d.result.label in setOf(
                VisionClassificationResult.CHAIN_VISIBLE,
                VisionClassificationResult.CHAIN_PRESENT,
                VisionClassificationResult.CHAIN_SOILED,
                VisionClassificationResult.CHAIN_APPEARS_DRY,
                VisionClassificationResult.SPROCKET_WEAR_VISIBLE,
                VisionClassificationResult.NORMAL
            )
            val predictedAsLabel = when {
                isChainDetected -> "chain_visible"
                d.topClass == ChainVisionClassifier.CLASS_NOT_CHAIN -> "not_chain"
                else -> "UNKNOWN"
            }
            if (expected != null) {
                scored++
                if (expected == predictedAsLabel) correct++
                if (expected == "not_chain" && predictedAsLabel == "chain_visible") falseChain += f.name
                if (expected == "chain_visible" && predictedAsLabel == "chain_visible") chainHits++
            }
            log("%-46s exp=%-18s got=%-18s p=%s obs=%s".format(
                f.name.take(46), expected ?: "?", predictedAsLabel,
                d.probabilities.entries.joinToString(",") {
                    "${it.key.take(12)}=${"%.2f".format(it.value)}"
                },
                d.result.observations
            ))
            log("    evidence=" + d.result.label + "  " + d.preprocessMs + "/" +
                d.featureMs + "/" + d.headMs + " ms (pre/feat/head)")
        }
        log("")
        log("images=" + images.size + " scored=" + scored + " correct=" + correct +
            " unknown=" + unknowns)
        if (scored > 0) {
            log("accuracy on pushed images: " + "%.1f".format(100.0 * correct / scored) + "%")
        }
        log("false chain on not_chain images: ${falseChain.size} $falseChain")
        log("=======================================")
        classifier.close()
        assertTrue("No images were classified", images.isNotEmpty())

        // Saying "chain" about a photo with no chain is the failure that matters: it is the
        // one that produces maintenance advice from nothing. On the 128 images verify.ps1
        // pushes (50 device_images + 78 held-out, deduplicated), the shipped v2 head does
        // this on exactly 2, and both are images that DO contain a drive chain — one a chain
        // sitting on a cassette, one boxed replacement chains — so they are label conventions
        // rather than hallucinations. The bound is a rate, not a count, so it stays meaningful
        // if the pushed set changes size; it fails if a future head genuinely inflates false
        // chains rather than letting that surface in a demo.
        val notChain = images.count { it.name.startsWith("not_chain__") }
        val fpRate = if (notChain > 0) falseChain.size.toDouble() / notChain else 0.0
        log("false-chain rate: %.3f (%d of %d not_chain images)".format(fpRate, falseChain.size, notChain))
        assertTrue(
            "False-chain rate ${"%.3f".format(fpRate)} exceeds 0.05 " +
                "(${falseChain.size} of $notChain): $falseChain",
            notChain == 0 || fpRate <= 0.05
        )

        // The other half: a chain photo must actually be detected. With the v3 head the
        // presence decision once read the top class across presence AND condition scores, so a
        // "normal" score of 0.99 outranked presence and every chain photo came back "not clear
        // enough" — 0 of 39 here — while the false-chain check above still passed. A floor on
        // detection is what makes that failure visible. Measured after the fix: ~0.64.
        val chains = images.count { it.name.startsWith("chain_visible__") }
        val recall = if (chains > 0) chainHits.toDouble() / chains else 1.0
        log("chain detection rate: %.3f (%d of %d chain images)".format(recall, chainHits, chains))
        assertTrue(
            "Only $chainHits of $chains chain photos were detected — the chain path is not working",
            chains == 0 || recall >= 0.40
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. The fusion rule this whole phase exists to fix.
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun chainVisibleCorroboratesWithoutFabricatingCondition() {
        log("")
        log("========== DIAGNOSIS FUSION ==========")

        // chain_visible corroborates but must not, on its own, assert a fault.
        val visionOnly = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "",
            audioPattern = null,
            visualPattern = VisionClassificationResult.CHAIN_VISIBLE
        )
        log("vision=chain_visible alone                  -> " + visionOnly.issueKey)

        // Audio + a confirmed chain in view: the chain route should hold.
        val both = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "strange chain noise",
            audioPattern = "possible_chain_noise",
            visualPattern = VisionClassificationResult.CHAIN_VISIBLE
        )
        log("audio=possible_chain_noise + chain_visible  -> " + both.issueKey)

        // Audio alone, no visual evidence.
        val audioOnly = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "",
            audioPattern = "possible_chain_noise",
            visualPattern = null
        )
        log("audio=possible_chain_noise, no vision       -> " + audioOnly.issueKey)

        // UNKNOWN vision must not act as evidence either way.
        val unknownVision = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "",
            audioPattern = "possible_chain_noise",
            visualPattern = VisionClassificationResult.UNKNOWN
        )
        log("audio=possible_chain_noise + vision=UNKNOWN -> " + unknownVision.issueKey)

        // A photo with no chain in it must not create chain evidence.
        val noChain = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "",
            audioPattern = null,
            visualPattern = VisionClassificationResult.UNKNOWN
        )
        log("no audio + vision=UNKNOWN                   -> " + noChain.issueKey)
        log("======================================")

        assertEquals("Seeing a chain is not a fault on its own",
            "unknown", visionOnly.issueKey)
        assertTrue("Visual-only diagnosis must clearly preserve the detected-chain evidence",
            visionOnly.visionEvidenceSummary.contains("Drive chain detected", ignoreCase = true))
        assertTrue("Visual-only diagnosis must not claim that the chain is faulty",
            visionOnly.summary.contains("condition cannot be determined", ignoreCase = true))
        assertEquals("Audio chain noise plus a confirmed chain must reach the chain rule",
            "chain_maintenance", both.issueKey)
        assertTrue("The fused path must retain the chain-detected visual evidence",
            both.visionEvidenceSummary.contains("Drive chain detected", ignoreCase = true))
        assertEquals("Audio-only chain noise must still reach the chain rule",
            "chain_maintenance", audioOnly.issueKey)
        assertEquals("UNKNOWN vision must not suppress the audio route",
            "chain_maintenance", unknownVision.issueKey)
        assertEquals("No evidence at all must stay unknown",
            "unknown", noChain.issueKey)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Multi-observation condition evidence and abstention verification
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun multiObservationConditionEvidenceAndAbstention() {
        log("")
        log("========== CONDITION EVIDENCE & ABSTENTION ==========")
        val classifier = ChainVisionClassifier(context)
        log("model info: " + classifier.modelInfo())

        // 1. Synthetic image (dark/generic) should safely abstain as UNKNOWN
        val syn = syntheticImage()
        val dSyn = classifier.classifyDetailed(syn)
        log("synthetic image -> label=${dSyn.result.label} obs=${dSyn.result.observations}")
        log("  reason: ${dSyn.reason}")
        assertEquals("Synthetic image must abstain as UNKNOWN",
            VisionClassificationResult.UNKNOWN, dSyn.result.label)
        assertTrue("Synthetic image observations must contain UNKNOWN",
            dSyn.result.observations.contains(VisionClassificationResult.UNKNOWN))

        // 2. Fusion rules with condition observations
        val soiledDiag = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "",
            audioPattern = "possible_chain_noise",
            visualPattern = VisionClassificationResult.CHAIN_SOILED
        )
        log("audio=possible_chain_noise + vision=chain_soiled -> " + soiledDiag.issueKey)
        assertEquals("chain_maintenance", soiledDiag.issueKey)
        assertTrue("Summary must note soiled chain",
            soiledDiag.summary.contains("soiled", ignoreCase = true))

        val wearDiag = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "",
            audioPattern = "possible_chain_noise",
            visualPattern = VisionClassificationResult.SPROCKET_WEAR_VISIBLE
        )
        log("audio=possible_chain_noise + vision=sprocket_wear_visible -> " + wearDiag.issueKey)
        assertEquals("chain_maintenance", wearDiag.issueKey)
        assertTrue("Summary must note sprocket wear",
            wearDiag.summary.contains("sprocket wear", ignoreCase = true))

        val dryDiag = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "",
            audioPattern = null,
            visualPattern = VisionClassificationResult.CHAIN_APPEARS_DRY
        )
        log("vision=chain_appears_dry alone -> " + dryDiag.issueKey)
        assertEquals("chain_maintenance", dryDiag.issueKey)

        val normalDiag = DiagnosisRules.evaluate(
            vehicleType = "motorcycle",
            userProblem = "",
            audioPattern = null,
            visualPattern = VisionClassificationResult.NORMAL
        )
        log("vision=normal alone -> " + normalDiag.issueKey)
        assertEquals("unknown", normalDiag.issueKey)
        assertTrue("Normal vision summary must note no obvious defect",
            normalDiag.visionEvidenceSummary.contains("no obvious visual defect", ignoreCase = true))

        classifier.close()
        log("=====================================================")
    }
}
