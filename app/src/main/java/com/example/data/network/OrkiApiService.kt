package com.example.data.network

import com.example.data.audio.BodoTtsNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ParsedDualResponse(
    val displayText: String,
    val ttsText: String
)

class OrkiApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val STREAM_API_URL = "https://aqhedgyiworhbfkrzreb.supabase.co/functions/v1/quick-processor"
        const val TTS_API_URL = "https://aqhedgyiworhbfkrzreb.supabase.co/functions/v1/swift-worker"

        fun parseDualResponse(rawText: String): ParsedDualResponse {
            if (!rawText.contains("---TTS---")) {
                val trimmed = rawText.trim()
                return ParsedDualResponse(trimmed, trimmed)
            }
            val parts = rawText.split("---TTS---")
            val displayText = parts[0].trim()
            val ttsText = parts.drop(1).joinToString("---TTS---").trim()
            return ParsedDualResponse(
                displayText = if (displayText.isNotEmpty()) displayText else rawText.trim(),
                ttsText = if (ttsText.isNotEmpty()) ttsText else displayText
            )
        }

        /** Gemini may split one event into multiple text parts; ignore internal thought parts. */
        fun streamChunks(jsonStr: String): List<String> {
            if (jsonStr.isBlank() || jsonStr == "[DONE]") return emptyList()
            val parsed = JSONObject(jsonStr)
            parsed.optJSONObject("error")?.let { error ->
                throw IllegalStateException(error.optString("message", "Upstream stream error"))
            }
            val parts = parsed.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: return emptyList()
            return (0 until parts.length()).mapNotNull { index ->
                val part = parts.optJSONObject(index) ?: return@mapNotNull null
                if (part.optBoolean("thought", false)) null else part.optString("text", "").takeIf { it.isNotEmpty() }
            }
        }

        fun ensureDevanagariForTTS(text: String): String {
            return BodoTtsNormalizer.normalizeForTts(text)
        }

        fun cleanTextForTTS(rawText: String): String {
            return BodoTtsNormalizer.normalizeForTts(rawText)
        }
    }

    fun streamChat(
        plan: String,
        model: String,
        script: String,
        userPersona: String,
        contentsJson: JSONArray,
        isLiveMode: Boolean = false
    ): Flow<String> = callbackFlow {
        val payload = JSONObject().apply {
            put("plan", plan)
            put("model", model)
            put("script", script)
            put("userPersona", userPersona)
            put("contents", contentsJson)
            put("isLiveMode", isLiveMode)
            put("generationConfig", JSONObject().apply {
                put("temperature", if (isLiveMode) 0.25 else 0.35)
                put("maxOutputTokens", if (isLiveMode) 180 else if (model == "okafwr-2.1") 4000 else 1500)
            })
        }

        val request = Request.Builder()
            .url(STREAM_API_URL)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val call = client.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                    close(Exception("Server returned ${response.code}: $errorBody"))
                    return@callbackFlow
                }
                val body = response.body
                if (body == null) {
                    close(Exception("Empty response body"))
                    return@callbackFlow
                }
                BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("data:")) {
                            try {
                                streamChunks(trimmed.substring(5).trim()).forEach { trySend(it) }
                            } catch (_: org.json.JSONException) {
                                // Ignore non-JSON heartbeat frames, not explicit upstream errors.
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
            close()
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchTtsAudioUrl(text: String, voice: String = "female_mainao"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanText = cleanTextForTTS(text)
            if (cleanText.isEmpty()) {
                return@withContext Result.failure(Exception("Text for TTS is empty"))
            }

            // Map voice ID to gender expected by swift-worker and EC2 endpoint
            val gender = if (voice.contains("female", ignoreCase = true)) "female" else "male"

            val payload = JSONObject().apply {
                put("text", cleanText)
                put("gender", gender)
                put("voice", voice)
            }

            val request = Request.Builder()
                .url(TTS_API_URL)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                return@withContext Result.failure(Exception("TTS failed: $err"))
            }

            val resBody = response.body?.string() ?: ""
            val json = JSONObject(resBody)
            val audioUrl = json.optString("audioUrl", "")
            if (audioUrl.isNotEmpty()) {
                Result.success(audioUrl)
            } else {
                Result.failure(Exception("No audioUrl returned by TTS server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun warmUpEdgeFunctions() = withContext(Dispatchers.IO) {
        try {
            val payload = "{\"ping\":true,\"warmup\":true}".toRequestBody(jsonMediaType)
            val req1 = Request.Builder().url(STREAM_API_URL).post(payload).build()
            val req2 = Request.Builder().url(TTS_API_URL).post(payload).build()
            client.newCall(req1).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
            })
            client.newCall(req2).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
            })
        } catch (_: Exception) {}
    }

    /** Uses the dedicated visual route; chat personas must never enhance an image prompt. */
    suspend fun enhanceVisualPrompt(rawPrompt: String, isVideo: Boolean, script: String = "roman"): String = withContext(Dispatchers.IO) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) return@withContext trimmed
        try {
            val request = Request.Builder()
                .url(STREAM_API_URL)
                .post(VisualPromptProtocol.request(trimmed, isVideo, script).toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext trimmed
                val body = response.body?.string() ?: return@withContext trimmed
                VisualPromptProtocol.parse(body, trimmed)
            }
        } catch (e: VisualPromptClarificationException) {
            // The caller must show this question and stop, not render an invented subject.
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Old deployments, network or malformed output: preserve the original request.
            trimmed
        }
    }
}
