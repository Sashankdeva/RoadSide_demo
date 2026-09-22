package com.roadside.agent

sealed class RoadSideIntent {
    data class ReportProblem(val text: String) : RoadSideIntent()
    object RecordAudio : RoadSideIntent()
    object InspectCamera : RoadSideIntent()
    object RequestDiagnosis : RoadSideIntent()
    object OpenGuide : RoadSideIntent()
    data class SelectVehicle(val vehicle: String) : RoadSideIntent()
    object Unknown : RoadSideIntent()

    companion object {
        fun parseFromText(input: String): RoadSideIntent {
            val text = input.trim().lowercase()
            return when {
                text.contains("sound") || text.contains("noise") || text.contains("listen") || text.contains("hear") -> {
                    ReportProblem(input)
                }
                text.contains("camera") || text.contains("inspect") || text.contains("look") || text.contains("photo") -> {
                    InspectCamera
                }
                text.contains("diagnos") || text.contains("what is wrong") -> {
                    RequestDiagnosis
                }
                text.contains("guide") || text.contains("fix") || text.contains("step") -> {
                    OpenGuide
                }
                input.isNotBlank() -> {
                    ReportProblem(input)
                }
                else -> Unknown
            }
        }
    }
}
