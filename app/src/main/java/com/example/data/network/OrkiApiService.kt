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
            val response = call.execute()
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

            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("data: ")) {
                    val jsonStr = trimmed.substring(6).trim()
                    if (jsonStr.isNotEmpty()) {
                        try {
                            val parsed = JSONObject(jsonStr)
                            val candidates = parsed.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val content = candidates.getJSONObject(0).optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    val chunk = parts.getJSONObject(0).optString("text", "")
                                    if (chunk.isNotEmpty()) {
                                        trySend(chunk)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                line = reader.readLine()
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

    /**
     * Gemini Middleman Prompt Enhancer:
     * Transforms basic or ambiguous prompts into rich, visually descriptive, culturally accurate prompts.
     * Prevents image/video models from hallucinating on cultural attire (e.g. Dokhona, Aronai, Fasra)
     * and adds cinematic lighting, camera perspective, textures, and framing.
     */
    suspend fun enhanceVisualPrompt(rawPrompt: String, isVideo: Boolean): String = withContext(Dispatchers.IO) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) return@withContext trimmed

        try {
            val systemInstruction = if (isVideo) {
                "You are an expert AI video prompt engineer. Enhance the user's video prompt into a rich, photorealistic, cinematic visual description for an 8-second video. CRITICAL: Always output in English only, because video diffusion models require English. If cultural attire (such as Bodo traditional 'dokhona', 'aronai', 'fasra', cultural festivals, or scenery) is mentioned, translate and describe authentic visual details, fabrics, handwoven motifs, and vibrant colors (yellow, green, red) accurately so the generator does not hallucinate. Specify camera motion (smooth cinematic pan/zoom, 4k detail, atmospheric lighting). Keep it concise, under 60 words. Return ONLY the enhanced prompt in English, no quotes or preamble."
            } else {
                "You are an expert AI image prompt engineer. Enhance the user's image prompt into a rich, photorealistic, visually precise description. CRITICAL: Always output in English only, because image diffusion models require English. If cultural attire (such as Bodo traditional 'dokhona', 'aronai', 'fasra', cultural festivals, or scenery) is mentioned, translate and describe authentic visual details, fabrics, handwoven Agor motifs, and vibrant colors (yellow, green, red) accurately so the diffusion model does not hallucinate. Include camera lighting, framing, 8k resolution, photorealistic masterpiece details. Keep it concise, under 60 words. Return ONLY the enhanced prompt in English, no quotes or preamble."
            }

            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemInstruction\n\nUser request: \"$trimmed\"\n\nEnhanced prompt:")
                        })
                    })
                })
            }

            val payload = JSONObject().apply {
                put("plan", "Pro")
                put("model", "gemini-2.0-flash")
                put("script", "Latin")
                put("userPersona", "")
                put("contents", contents)
                put("isLiveMode", false)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 180)
                })
            }

            val request = Request.Builder()
                .url(STREAM_API_URL)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext trimmed

            val body = response.body ?: return@withContext trimmed
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            val enhancedBuilder = StringBuilder()

            var line: String? = reader.readLine()
            while (line != null) {
                val lineTrimmed = line.trim()
                if (lineTrimmed.startsWith("data: ")) {
                    val jsonStr = lineTrimmed.substring(6).trim()
                    if (jsonStr.isNotEmpty()) {
                        try {
                            val parsed = JSONObject(jsonStr)
                            val candidates = parsed.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val content = candidates.getJSONObject(0).optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    val chunk = parts.getJSONObject(0).optString("text", "")
                                    enhancedBuilder.append(chunk)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                line = reader.readLine()
            }

            val finalEnhanced = enhancedBuilder.toString()
                .replace(Regex("^(Enhanced prompt|Prompt):?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^\"|\"$"), "")
                .trim()

            if (finalEnhanced.isNotBlank()) finalEnhanced else trimmed
        } catch (e: Exception) {
            trimmed
        }
    }
}
