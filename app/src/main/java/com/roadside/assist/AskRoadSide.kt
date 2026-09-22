package com.roadside.assist

import com.roadside.agent.VehicleContext
import com.roadside.knowledge.VehicleKnowledge
import com.roadside.sensing.AudioClassificationResult
import com.roadside.sensing.VisionClassificationResult

/**
 * "Something wrong? Ask RoadSide…" — a local, rule-based responder for the solution screen.
 *
 * Offline by construction: keyword intents over the rider's question, answered from the
 * current vehicle, evidence, assessment and [SolutionPlan]. No model, no network.
 *
 * Two rules it never breaks:
 *   1. A question never changes the assessment. What the rider types is context, not evidence
 *      — the same principle `DiagnosisRules` applies to the problem description.
 *   2. Answers stay conservative. When something could be unsafe, the answer says stop and
 *      get it checked; it never invents a measurement or promises a fix.
 */
object AskRoadSide {

    enum class Topic {
        NO_CLEANER, NO_LUBE, NO_TOOLS, STILL_NOISY, DONT_UNDERSTAND,
        SAFE_TO_RIDE, MECHANIC, CHAIN_SLACK, THANKS, FALLBACK
    }

    data class Answer(val topic: Topic, val text: String)

    fun answer(question: String, c: VehicleContext, plan: SolutionPlan = Assistance.plan(c)): Answer {
        val q = question.lowercase().trim()
        val topic = classify(q)
        return Answer(topic, respond(topic, c, plan))
    }

    // Order matters: the more specific intents are checked first ("I don't have chain
    // cleaner" must not fall into the generic "I don't have these tools").
    internal fun classify(q: String): Topic = when {
        q.isBlank() -> Topic.FALLBACK
        q.has("thank", "thanks", "cheers", "great", "got it") && q.length < 30 -> Topic.THANKS
        q.has("cleaner", "degreaser", "clean it", "cleaning") -> Topic.NO_CLEANER
        q.has("lube", "lubricant", "wd40", "wd-40", "oil", "grease") -> Topic.NO_LUBE
        q.has("slack", "tension", "tight", "loose", "sag", "adjust") -> Topic.CHAIN_SLACK
        q.has("tool", "stand", "brush", "equipment", "spanner", "wrench") -> Topic.NO_TOOLS
        q.has("still", "again", "didn't work", "didnt work", "not fixed", "same noise", "keeps", "persist") -> Topic.STILL_NOISY
        q.has("safe", "ride", "riding", "drive", "dangerous") -> Topic.SAFE_TO_RIDE
        q.has("mechanic", "garage", "workshop", "cost", "price", "expensive", "repair shop") -> Topic.MECHANIC
        q.has("understand", "explain", "what does", "what do", "mean", "confus", "simple", "why") -> Topic.DONT_UNDERSTAND
        q.has("noise", "sound", "rattl", "click", "squeak") -> Topic.STILL_NOISY
        else -> Topic.FALLBACK
    }

    private fun respond(t: Topic, c: VehicleContext, p: SolutionPlan): String {
        val vehicle = c.vehicleType.lowercase()
        val issue = c.diagnosisCandidate?.issueKey ?: VehicleKnowledge.UNKNOWN.id
        val chain = issue == VehicleKnowledge.CHAIN_MAINTENANCE.id
        val obs = c.visionObservations.ifEmpty { listOfNotNull(c.visionEvidence) }
        val wear = VisionClassificationResult.SPROCKET_WEAR_VISIBLE in obs
        val rattle = c.audioEvidence == AudioClassificationResult.POSSIBLE_CHAIN_NOISE

        return when (t) {
            Topic.NO_CLEANER -> if (chain) {
                "No chain cleaner is fine for now. A stiff brush and a dry cloth will shift most of the grit — " +
                    "turn the wheel by hand as you go. Avoid petrol, harsh solvents and pressure washers, which can " +
                    "damage the chain's seals. Lubricate it afterwards."
            } else {
                "RoadSide didn't find a chain problem this time (${p.headline.lowercase()}), so cleaning the chain " +
                    "isn't something it's recommending right now."
            }

            Topic.NO_LUBE -> if (chain) {
                "A proper chain lube is best. WD-40 is mainly a cleaner and water displacer, so it won't keep the " +
                    "chain lubricated for long. If you have to use something else to get home, re-lube with chain " +
                    "lube as soon as you can — and keep any oil or spray well away from the brakes."
            } else {
                "RoadSide isn't suggesting lubrication for this result. Whatever you use on the $vehicle, keep oil " +
                    "and spray well away from the brake discs and pads."
            }

            Topic.NO_TOOLS -> buildString {
                append("You can still do a useful check without tools: engine off, key out, and look along the chain ")
                append("for dirt, dry or rusty links, and whether it sags a lot. ")
                if (chain) {
                    append("For cleaning you only really need a brush, a cloth and chain lube. ")
                    append("Without a stand it's harder to turn the rear wheel — a mechanic or a friend with a stand is the easier option.")
                } else {
                    append("If you're not sure what you're looking at, a mechanic can check it properly.")
                }
            }

            Topic.STILL_NOISY -> when {
                chain && wear ->
                    "With the sprocket teeth looking worn, a noise that won't go away needs a mechanic to look at the " +
                        "chain and sprocket. Until then, ride slowly and stick to smooth roads."
                chain ->
                    "If the noise is still there after cleaning and lubricating, the chain may need adjusting, or the " +
                        "chain or sprockets may be worn — that needs a mechanic to judge. You can also record it again, " +
                        "close to the chain, so RoadSide can check what it hears now."
                else ->
                    "RoadSide couldn't identify the noise from the last recording. Try recording again while it's " +
                        "happening, with the phone close to where it comes from. If it continues or gets worse, have the " +
                        "$vehicle checked by a mechanic."
            }

            Topic.DONT_UNDERSTAND -> buildString {
                append("In short: ")
                append(simpleSummary(issue, rattle, obs))
                append(" ")
                // Skip the "put it on the stand" preamble: the useful first step is the one after it.
                val first = p.actions.firstOrNull { !it.startsWith("Put the") } ?: p.actions.firstOrNull()
                first?.let { append("The first thing to do: ${it.replaceFirstChar { ch -> ch.lowercase() }}") }
            }

            Topic.SAFE_TO_RIDE -> when {
                issue == VehicleKnowledge.BRAKE_SOUND.id ->
                    "If braking feels or sounds different, don't ride until the brakes have been checked. Brakes are " +
                        "not something to take a chance on."
                chain && wear ->
                    "The sprocket teeth don't look in good condition, so ride carefully: slow speeds, smooth roads " +
                        "only, no hard acceleration. That's fine for getting it to a mechanic, but avoid long or fast " +
                        "rides until the chain and sprocket have been checked."
                chain ->
                    "A dirty or dry chain usually isn't an immediate danger, but ride gently until you've sorted it. " +
                        "If the chain slaps, jumps or the noise gets worse, stop and have it checked."
                else ->
                    "RoadSide couldn't identify a specific issue, so it can't say it's safe. If anything feels different " +
                        "— a noise getting worse, vibration, or the brakes — don't ride; have it checked."
            }

            Topic.MECHANIC ->
                "A mechanic can measure chain wear and tension properly, which RoadSide can't do from a recording " +
                    "or photo. RoadSide doesn't estimate repair costs. Showing them this result can help explain what " +
                    "you heard."

            Topic.CHAIN_SLACK ->
                "Chain slack is how far the chain moves up and down midway between the two sprockets. The right " +
                    "amount depends on the $vehicle — it's in the owner's manual, often on a sticker on the swingarm. " +
                    "RoadSide can't measure slack from a photo."

            Topic.THANKS -> "You're welcome. Ride safe."

            Topic.FALLBACK ->
                "I can help with this result: ${p.headline.lowercase()}. Try asking what it means, what to do without " +
                    "the tools, or what to do if the noise continues. If anything feels unsafe, stop and have the " +
                    "$vehicle checked by a mechanic."
        }
    }

    private fun simpleSummary(issue: String, rattle: Boolean, obs: List<String>): String = when (issue) {
        VehicleKnowledge.CHAIN_MAINTENANCE.id -> when {
            VisionClassificationResult.SPROCKET_WEAR_VISIBLE in obs ->
                "the teeth on the sprocket don't look in good condition, so ride slowly on smooth roads and get it checked."
            VisionClassificationResult.CHAIN_SOILED in obs -> "the chain looks dirty, so clean it and oil it."
            VisionClassificationResult.CHAIN_APPEARS_DRY in obs -> "the chain looks dry, so it needs chain lube."
            rattle -> "the rattle sounds like it's coming from the chain, which often just needs cleaning and oiling."
            else -> "the chain may need some care."
        }
        VehicleKnowledge.BRAKE_SOUND.id -> "the brakes are making a noise and should be looked at."
        VehicleKnowledge.BATTERY_ELECTRICAL.id -> "the clicking may be a weak battery or a loose connection."
        else -> "RoadSide didn't find a clear cause, so it's worth trying again or asking a mechanic."
    }

    /**
     * True if any of [words] starts a word in this string. Plain `contains` misfires on
     * substrings: "under*stand*" would read as a question about a bike stand, "mes*sag*e" as
     * chain sag, "t*oil*et" as oil. Word-start matching still lets "rattl" match "rattling".
     */
    private fun String.has(vararg words: String) =
        words.any { Regex("(^|[^a-z0-9])" + Regex.escape(it)).containsMatchIn(this) }
}
