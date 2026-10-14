package com.example.data.network

import org.json.JSONObject

class VisualPromptClarificationException(message: String) : Exception(message)

/** Contract with quick-processor's dedicated non-streaming enhancement route. */
object VisualPromptProtocol {
    fun request(prompt: String, isVideo: Boolean, script: String = "roman"): JSONObject = JSONObject().apply {
        put("task", "image_prompt_enhancement")
        put("prompt", prompt.trim())
        put("mediaType", if (isVideo) "video" else "image")
        put("script", if (script == "deva") "deva" else "roman")
        // Preserve the existing standard route; a model label is not an upstream model ID.
        put("plan", "Guest")
        put("model", "orki")
    }

    fun parse(body: String, originalPrompt: String): String {
        val json = JSONObject(body)
        require(json.optString("task") == "image_prompt_enhancement") { "Unexpected enhancement task" }
        require(json.optString("originalPrompt") == originalPrompt.trim()) { "Enhancement request mismatch" }
        require(json.has("needsClarification") && json.get("needsClarification") is Boolean) {
            "Missing visual interpretation status"
        }
        if (json.getBoolean("needsClarification")) {
            val question = json.getString("clarificationQuestion").trim()
            require(question.isNotEmpty()) { "Missing clarification question" }
            throw VisualPromptClarificationException(question)
        }
        val prompt = json.getString("enhancedPrompt").trim()
        require(prompt.isNotEmpty() && !prompt.contains("---TTS---") &&
            !prompt.startsWith("RESPONSE:") && !prompt.startsWith("TRANSCRIPTION:")) {
            "Invalid visual prompt"
        }
        return prompt
    }
}
