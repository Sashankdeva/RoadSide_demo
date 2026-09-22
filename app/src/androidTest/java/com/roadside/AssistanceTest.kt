package com.roadside

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roadside.agent.VehicleContext
import com.roadside.assist.AskRoadSide
import com.roadside.assist.AskRoadSide.Topic
import com.roadside.assist.Assistance
import com.roadside.assist.FindingTone
import com.roadside.assist.Findings
import com.roadside.rules.DiagnosisRules
import com.roadside.sensing.AudioClassificationResult as A
import com.roadside.sensing.VisionClassificationResult as V
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards for the solution screen and "Ask RoadSide":
 *
 *   - no fabricated measurements (Hz, mm, torque, percentages, volts…) anywhere a rider reads
 *   - observations stay separate from conclusions; "normal" is never a fault
 *   - answers use the current context, and the four example questions are understood
 *
 * Contexts are built through the real [DiagnosisRules], so the assessment under test is the
 * one the app would actually produce for that evidence.
 */
@RunWith(AndroidJUnit4::class)
class AssistanceTest {

    companion object {
        const val TAG = "RoadSideAssist"

        val AUDIO = listOf(
            A.POSSIBLE_CHAIN_NOISE, A.BRAKE_SQUEAL, A.CLICKING_ELECTRICAL, A.ENGINE_NOISE,
            A.MECHANICAL_NOISE, A.AMBIENT_ONLY, A.UNKNOWN, null
        )

        /** Observation sets the v3 vision head can report, plus "no photo". */
        val PHOTOS: List<List<String>> = listOf(
            emptyList(),
            listOf(V.UNKNOWN),
            listOf(V.CHAIN_PRESENT),
            listOf(V.CHAIN_PRESENT, V.NORMAL),
            listOf(V.CHAIN_PRESENT, V.CHAIN_SOILED),
            listOf(V.CHAIN_PRESENT, V.CHAIN_APPEARS_DRY),
            listOf(V.CHAIN_PRESENT, V.SPROCKET_WEAR_VISIBLE),
            listOf(V.CHAIN_PRESENT, V.CHAIN_SOILED, V.CHAIN_APPEARS_DRY),
            listOf(V.CHAIN_PRESENT, V.CHAIN_SOILED, V.SPROCKET_WEAR_VISIBLE)
        )

        val QUESTIONS = listOf(
            "I don't have chain cleaner.", "The noise is still there.", "I don't understand this.",
            "I don't have these tools.", "Is it safe to ride?", "can I use WD-40?", "how tight should the chain be",
            "how much will a mechanic cost", "thanks", "asdf qwerty"
        )

        /** Any number followed by a unit, or a percentage. */
        val MEASUREMENT = Regex(
            "(?i)\\b\\d+(\\.\\d+)?\\s*(hz|khz|mm|cm|nm|n·m|psi|bar|v|volts?|kg|km/?h|mph|rpm|%)(\\b|$)|\\d+\\s*%|percent"
        )
        val INTERNAL_LABEL = Regex("\\b[a-z]+_[a-z_]+\\b")
    }

    private fun log(m: String) = Log.i(TAG, m)

    /** Same primary-label priority as ChainVisionClassifier. */
    private fun primary(obs: List<String>): String? = when {
        obs.isEmpty() -> null
        V.UNKNOWN in obs -> V.UNKNOWN
        V.SPROCKET_WEAR_VISIBLE in obs -> V.SPROCKET_WEAR_VISIBLE
        V.CHAIN_SOILED in obs -> V.CHAIN_SOILED
        V.CHAIN_APPEARS_DRY in obs -> V.CHAIN_APPEARS_DRY
        V.NORMAL in obs -> V.NORMAL
        else -> V.CHAIN_PRESENT
    }

    private fun ctx(audio: String?, obs: List<String>, vehicle: String = "Motorcycle"): VehicleContext {
        val p = primary(obs)
        return VehicleContext(
            vehicleType = vehicle,
            audioEvidence = audio,
            audioEvidenceLabel = audio?.let { "label for $it" },
            visionEvidence = p,
            visionEvidenceLabel = if (p == V.UNKNOWN) "The photo is not clear enough to confirm a drive chain" else p?.let { "photo" },
            visionObservations = obs,
            diagnosisCandidate = DiagnosisRules.evaluate(vehicle, "", audio, p)
        )
    }

    @Test
    fun noFabricatedMeasurementsOrInternalLabelsAnywhere() {
        val failures = mutableListOf<String>()
        var texts = 0
        for (vehicle in listOf("Motorcycle", "Scooter")) for (a in AUDIO) for (o in PHOTOS) {
            val c = ctx(a, o, vehicle)
            val plan = Assistance.plan(c)
            val shown = mutableListOf(plan.headline, plan.found, plan.meaning, plan.safety) +
                plan.actions + plan.tools +
                (Findings.sound(a, c.audioEvidenceLabel) + Findings.photo(o, c.visionEvidence, c.visionEvidenceLabel))
                    .flatMap { listOfNotNull(it.title, it.detail) } +
                QUESTIONS.map { AskRoadSide.answer(it, c, plan).text }
            for (s in shown) {
                texts++
                MEASUREMENT.find(s)?.let { failures += "measurement '${it.value}' in \"$s\" (audio=$a obs=$o)" }
                // `label for …` is this test's own placeholder, not app text.
                if (!s.startsWith("label for")) {
                    INTERNAL_LABEL.find(s)?.let { failures += "internal label '${it.value}' in \"$s\"" }
                }
            }
        }
        log("checked $texts rider-facing strings, ${failures.size} problems")
        failures.take(10).forEach { log("  FAIL $it") }
        assertTrue("Fabricated measurement or internal label shown: ${failures.take(5)}", failures.isEmpty())
    }

    @Test
    fun observationsStaySeparateAndNormalIsNotAFault() {
        // Normal on its own: a confirmation, not a fault — and not an all-clear either.
        val normal = ctx(null, listOf(V.CHAIN_PRESENT, V.NORMAL))
        val normalPlan = Assistance.plan(normal)
        assertEquals("unknown", normal.diagnosisCandidate!!.issueKey)
        assertTrue(normalPlan.inconclusive)
        assertTrue(Findings.photo(normal.visionObservations, V.NORMAL, "").any { it.title == "No obvious issue visible" && it.tone == FindingTone.CONFIRMED })
        assertFalse("Normal must not ask for chain cleaner", normalPlan.tools.any { it.contains("cleaner", true) })

        // Soiled: cleaning appears in both actions and tools.
        val soiled = Assistance.plan(ctx(null, listOf(V.CHAIN_PRESENT, V.CHAIN_SOILED)))
        assertFalse(soiled.inconclusive)
        assertTrue(soiled.actions.any { it.contains("Clean the chain") })
        assertTrue(soiled.tools.contains("Chain cleaner"))

        // Dry only: lube, but no cleaner — tools stay relevant.
        val dry = Assistance.plan(ctx(null, listOf(V.CHAIN_PRESENT, V.CHAIN_APPEARS_DRY)))
        assertTrue(dry.tools.contains("Chain lubricant"))
        assertFalse(dry.tools.contains("Chain cleaner"))

        // Sprocket wear: routed to a mechanic, conclusion names the sprocket.
        val wear = Assistance.plan(ctx(A.POSSIBLE_CHAIN_NOISE, listOf(V.CHAIN_PRESENT, V.SPROCKET_WEAR_VISIBLE)))
        assertEquals("Chain and sprocket check", wear.headline)
        assertTrue(wear.actions.any { it.contains("mechanic", true) })

        // A chain merely in view never becomes a finding about its condition.
        val present = Findings.photo(listOf(V.CHAIN_PRESENT), V.CHAIN_PRESENT, "")
        assertEquals(1, present.size)
        assertEquals("Its condition couldn't be judged from this photo.", present[0].detail)

        // Several observations are all shown, not collapsed to the primary.
        val both = Findings.photo(listOf(V.CHAIN_PRESENT, V.CHAIN_SOILED, V.CHAIN_APPEARS_DRY), V.CHAIN_SOILED, "")
        log("soiled+dry findings: ${both.map { it.title }}")
        assertEquals(3, both.size)
    }

    @Test
    fun askRoadSideUnderstandsTheExamplesAndUsesContext() {
        val chain = ctx(A.POSSIBLE_CHAIN_NOISE, listOf(V.CHAIN_PRESENT, V.CHAIN_SOILED))
        val unknown = ctx(A.AMBIENT_ONLY, listOf(V.UNKNOWN))

        val expected = mapOf(
            "I don't have chain cleaner." to Topic.NO_CLEANER,
            "The noise is still there." to Topic.STILL_NOISY,
            "I don't understand this." to Topic.DONT_UNDERSTAND,
            "I don't have these tools." to Topic.NO_TOOLS,
            "Is it safe to ride?" to Topic.SAFE_TO_RIDE
        )
        log("")
        log("========== ASK ROADSIDE ==========")
        for ((q, topic) in expected) {
            val a = AskRoadSide.answer(q, chain)
            log("Q: $q -> ${a.topic}")
            log("A: ${a.text}")
            assertEquals("\"$q\" misunderstood", topic, a.topic)
            assertTrue(a.text.length > 40)
        }

        // Context matters: with no chain finding, RoadSide must not recommend cleaning it.
        val noChain = AskRoadSide.answer("I don't have chain cleaner.", unknown)
        log("unknown context, no cleaner -> ${noChain.text}")
        assertTrue(noChain.text.contains("didn't find a chain problem"))

        // Unknown + "is it safe" must not reassure.
        val safe = AskRoadSide.answer("Is it safe to ride?", unknown)
        assertTrue(safe.text.contains("can't say it's safe"))

        // The vehicle the rider picked is the one named.
        val scooter = AskRoadSide.answer("how tight should the chain be", ctx(A.POSSIBLE_CHAIN_NOISE, emptyList(), "Scooter"))
        assertTrue(scooter.text.contains("scooter"))

        // Asking never changes the assessment.
        val before = Assistance.plan(chain)
        QUESTIONS.forEach { AskRoadSide.answer(it, chain) }
        assertEquals(before, Assistance.plan(chain))
        log("==================================")
    }
}
