package com.roadside.llm

interface LocalLlm {
    suspend fun generateResponse(prompt: String): String
    val isReady: Boolean
}

class StubLocalLlm : LocalLlm {
    override val isReady: Boolean = false

    override suspend fun generateResponse(prompt: String): String {
        return "Offline local LLM stub (reserved for future integration phase)."
    }
}
