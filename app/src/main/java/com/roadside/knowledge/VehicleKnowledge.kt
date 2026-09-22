package com.roadside.knowledge

data class KnowledgeEntry(
    val id: String,
    val title: String,
    val supportedVehicle: String = "Motorcycle",
    val symptoms: List<String>,
    val audioPatterns: List<String>,
    val visualPatterns: List<String>,
    val tools: List<String>,
    val guideSteps: List<String>,
    val safetyAdvisory: String
)

object VehicleKnowledge {

    val CHAIN_MAINTENANCE = KnowledgeEntry(
        id = "chain_maintenance",
        title = "Chain maintenance",
        supportedVehicle = "Motorcycle",
        symptoms = listOf(
            "Dry drivetrain rattling",
            "Chirping noise under acceleration or deceleration",
            "Slack chain vibration"
        ),
        audioPatterns = listOf("possible_chain_noise", "drivetrain_rattle"),
        visualPatterns = listOf("chain_appears_dry", "chain_loose", "sprocket_worn"),
        tools = listOf(
            "Motorcycle chain lubricant",
            "Lint-free cloth",
            "Chain cleaning brush",
            "Protective gloves"
        ),
        guideSteps = listOf(
            "Turn the motorcycle engine off completely, remove the key, and place the bike on its center stand or paddock stand on firm, level ground.",
            "Inspect the drive chain and rear sprocket visually. Rotate the rear wheel by hand (NEVER with engine running) to check for dry rollers, rust, or tight links.",
            "Clean accumulated grit and old lubricant from the chain using a dedicated brush and cloth.",
            "Evenly apply motorcycle chain lube to the inner side of the chain rollers while turning the rear wheel slowly by hand. Allow 15 minutes to penetrate before riding."
        ),
        safetyAdvisory = "CRITICAL SAFETY: Never clean, lubricate, or touch the drive chain while the engine is running or in gear. Moving sprockets can cause severe finger injury."
    )

    val BRAKE_SOUND = KnowledgeEntry(
        id = "brake_sound",
        title = "Brake inspection",
        supportedVehicle = "Motorcycle",
        symptoms = listOf(
            "High-pitched squealing when braking",
            "Metallic grinding noise",
            "Spongy or vibrating brake lever"
        ),
        audioPatterns = listOf("brake_squeal", "metallic_grinding"),
        visualPatterns = listOf("brake_rotor_worn", "pad_lining_thin"),
        tools = listOf(
            "Flashlight",
            "Brake cleaner spray",
            "Ruler or depth gauge",
            "Safety goggles"
        ),
        guideSteps = listOf(
            "Ensure the motorcycle is parked securely and the brake discs have cooled down completely before touching them.",
            "Shine a flashlight into the brake caliper to inspect the pad friction material. Check that the pad thickness is at least 2mm above the metal backing plate.",
            "Examine the brake disc rotor surface for deep groove scoring, cracks, or excessive lip wear.",
            "If pads are contaminated with road dust, spray with motorcycle brake cleaner. If pad material is worn below 2mm, professional replacement is required before riding."
        ),
        safetyAdvisory = "SAFETY WARNING: Never apply lubricants, grease, or WD-40 anywhere near brake pads or discs. Severely worn brake pads cause sudden braking loss."
    )

    val BATTERY_ELECTRICAL = KnowledgeEntry(
        id = "battery_electrical",
        title = "Battery / electrical issue",
        supportedVehicle = "Motorcycle",
        symptoms = listOf(
            "Rapid clicking when pressing starter switch",
            "Headlight or instrument cluster dims when starting",
            "Engine fails to crank or cranks very slowly"
        ),
        audioPatterns = listOf("clicking_electrical", "starter_solenoid_click"),
        visualPatterns = listOf("battery_terminal_corroded", "loose_battery_cable"),
        tools = listOf(
            "Digital multimeter",
            "10mm wrench or socket",
            "Wire cleaning brush",
            "Dielectric terminal grease"
        ),
        guideSteps = listOf(
            "Turn ignition switch to OFF and remove the key from the motorcycle.",
            "Remove seat or side panel to access the motorcycle battery terminals.",
            "Check both battery terminal connections for tightness and white/blue corrosion deposits.",
            "Use a multimeter to measure resting voltage across terminals. A healthy battery shows 12.6V or above. If under 11.8V, the battery requires trickle charging."
        ),
        safetyAdvisory = "ELECTRICAL SAFETY: Always disconnect the negative (-) black cable first and reconnect it last to prevent electrical short-circuits against the frame."
    )

    val UNKNOWN = KnowledgeEntry(
        id = "unknown",
        title = "No clear finding",
        supportedVehicle = "Motorcycle",
        symptoms = listOf("Unidentified noise or condition"),
        audioPatterns = listOf("UNKNOWN"),
        visualPatterns = listOf("UNKNOWN"),
        tools = listOf(
            "Owner manual",
            "Flashlight",
            "Tire pressure gauge"
        ),
        guideSteps = listOf(
            "Park the motorcycle in a safe, well-lit location away from traffic and turn off the engine.",
            "Perform a general 360-degree walkaround inspection: check tire pressures, loose fasteners, fluid leaks, and exhaust mounts.",
            "Attempt another focused audio or camera recording near the suspected source of the noise.",
            "If the noise persists or involves engine internal knocking or suspension instability, consult a certified motorcycle mechanic before operating."
        ),
        safetyAdvisory = "CAUTION: The app cannot conclusively identify this problem. Do not operate the vehicle at high speeds if there is any suspicion of mechanical or brake failure."
    )

    val ALL_ENTRIES = listOf(CHAIN_MAINTENANCE, BRAKE_SOUND, BATTERY_ELECTRICAL, UNKNOWN)

    fun findEntryById(id: String): KnowledgeEntry {
        return ALL_ENTRIES.firstOrNull { it.id == id } ?: UNKNOWN
    }
}
