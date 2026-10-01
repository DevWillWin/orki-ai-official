package com.example.data.network

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GeneratedImageFile(
    val file: File,
    val engineName: String,
    val isFallback: Boolean = false
)

class ImageGenerationService(private val context: Context) {

    val perchanceEngine by lazy { PerchanceImageEngine(context) }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun warmUp() {
        perchanceEngine.warmUp()
    }

    suspend fun generateImage(
        prompt: String,
        workerUrl: String = "https://orki-img-gen.devmightwin.workers.dev",
        apiKey: String = "Orki-Image-7xP6-kQ9m-81vL",
        pollinationsKey: String = "",
        preferredEngine: String = "auto", // "auto" (Perchance -> Cloudflare -> Pollinations) or "azure_dalle"
        azureEndpoint: String = "",
        azureKey: String = "",
        azureDalleDeployment: String = "dall-e-3",
        onStatusUpdate: ((stage: String, isFallback: Boolean) -> Unit)? = null
    ): Result<GeneratedImageFile> = withContext(Dispatchers.IO) {
        val trimmedPrompt = prompt.trim()
        var perchanceError: String? = null
        var cloudflareError: String? = null
        var azureError: String? = null

        // 1. If user explicitly preferred Azure DALL-E 3 (or configured it)
        if (preferredEngine == "azure_dalle" && azureKey.isNotBlank()) {
            onStatusUpdate?.invoke("Synthesizing with Azure OpenAI DALL-E 3...", false)
            val azureResult = generateWithAzureDalle(
                prompt = trimmedPrompt,
                azureEndpoint = azureEndpoint,
                azureKey = azureKey,
                deployment = azureDalleDeployment,
                onStatusUpdate = { stage -> onStatusUpdate?.invoke(stage, false) }
            )
            if (azureResult.isSuccess) {
                return@withContext azureResult
            }
            azureError = azureResult.exceptionOrNull()?.localizedMessage ?: "Azure DALL-E failed"
            Log.w("ImageGenerationService", "Azure DALL-E 3 failed: $azureError. Falling back to Perchance/Cloudflare...")
            onStatusUpdate?.invoke("Azure DALL-E unavailable. Falling back to Perchance AI...", true)
        }

        // 2. PRIMARY ENGINE: Perchance AI (Background Free Unlimited)
        if (preferredEngine != "azure_dalle" && preferredEngine != "cloudflare") {
            try {
                onStatusUpdate?.invoke("Synthesizing with Perchance AI (Primary Free)...", false)
                val perchanceResult = perchanceEngine.generateImage(trimmedPrompt, timeoutSeconds = 28)
                if (perchanceResult.isSuccess) {
                    return@withContext perchanceResult
                }
                perchanceError = perchanceResult.exceptionOrNull()?.localizedMessage ?: "Perchance timeout"
                Log.w("ImageGenerationService", "Perchance failed: $perchanceError. Falling back to Cloudflare Workers AI...")
                onStatusUpdate?.invoke("Perchance busy. Routing to Cloudflare Workers AI...", true)
            } catch (e: Exception) {
                perchanceError = e.localizedMessage
                Log.w("ImageGenerationService", "Perchance exception: $perchanceError")
                onStatusUpdate?.invoke("Perchance busy. Routing to Cloudflare Workers AI...", true)
            }
        }

        // 3. SECONDARY ENGINE (FALLBACK 1): Cloudflare Workers AI
        try {
            onStatusUpdate?.invoke("Generating with Orki Cloudflare Worker...", false)

            val json = JSONObject()
                .put("prompt", trimmedPrompt)
                .toString()

            val effectiveUrl = if (workerUrl.isNotBlank() && workerUrl.contains("workers.dev")) {
                workerUrl
            } else {
                "https://orki-img-gen.devmightwin.workers.dev"
            }

            val normalizedUrl = if (effectiveUrl.startsWith("http://") || effectiveUrl.startsWith("https://")) {
                effectiveUrl
            } else {
                "https://$effectiveUrl"
            }

            val request = Request.Builder()
                .url(normalizedUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept", "image/png, image/*, */*")
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val cfResult: File? = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                    val cleanError = try {
                        val errorJson = JSONObject(errorBody)
                        val mainMsg = errorJson.optString("error", "")
                        val details = errorJson.optString("details", "")
                        when {
                            mainMsg.isNotBlank() && details.isNotBlank() -> "$mainMsg: $details"
                            mainMsg.isNotBlank() -> mainMsg
                            details.isNotBlank() -> details
                            else -> errorBody
                        }
                    } catch (_: Exception) {
                        errorBody
                    }
                    cloudflareError = "HTTP ${response.code} ($cleanError)"
                    return@use null
                }

                val imageBytes = response.body?.bytes()
                if (imageBytes == null || imageBytes.isEmpty()) {
                    cloudflareError = "Empty response body from Cloudflare Worker"
                    return@use null
                }

                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap == null) {
                    cloudflareError = "Failed to decode image bytes from Cloudflare Worker"
                    return@use null
                }

                saveToCache(imageBytes, "orki_cf")
            }

            if (cfResult != null) {
                return@withContext Result.success(
                    GeneratedImageFile(
                        file = cfResult,
                        engineName = "Orki AI (Cloudflare)",
                        isFallback = false
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cloudflareError = e.localizedMessage ?: "Cloudflare network connection error"
        }

        // 3. Fallback to Azure DALL-E 3 if available and not yet tried
        if (preferredEngine != "azure_dalle" && azureKey.isNotBlank()) {
            try {
                onStatusUpdate?.invoke("Cloudflare unavailable. Engaging Azure DALL-E 3...", true)
                val azureFallbackResult = generateWithAzureDalle(
                    prompt = trimmedPrompt,
                    azureEndpoint = azureEndpoint,
                    azureKey = azureKey,
                    deployment = azureDalleDeployment,
                    onStatusUpdate = { stage -> onStatusUpdate?.invoke(stage, true) }
                )
                if (azureFallbackResult.isSuccess) {
                    return@withContext Result.success(
                        GeneratedImageFile(
                            file = azureFallbackResult.getOrThrow().file,
                            engineName = "Azure OpenAI DALL-E 3 (Backup)",
                            isFallback = true
                        )
                    )
                }
            } catch (e: Exception) {
                azureError = e.localizedMessage
            }
        }

        // 4. Primary & Secondary Engine Failed -> Automatic Fallback to Pollinations AI
        Log.w("ImageGenerationService", "Primary engines failed (CF: $cloudflareError, Azure: $azureError). Switching to Pollinations AI fallback...")
        onStatusUpdate?.invoke("Engaging Pollinations AI backup engine...", true)

        try {
            val encodedPrompt = URLEncoder.encode(trimmedPrompt, "UTF-8")
            val pollinationsUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&seed=${System.currentTimeMillis() % 100000}"

            val pollRequestBuilder = Request.Builder()
                .url(pollinationsUrl)

            if (pollinationsKey.isNotBlank()) {
                pollRequestBuilder.addHeader("Authorization", "Bearer $pollinationsKey")
            }

            val fallbackFile: File = httpClient.newCall(pollRequestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val pollError = response.body?.string() ?: "HTTP ${response.code}"
                    throw Exception("Fallback Pollinations AI failed: HTTP ${response.code} ($pollError). Primary Cloudflare error: $cloudflareError")
                }

                val imageBytes = response.body?.bytes()
                    ?: throw Exception("Empty image response from fallback Pollinations AI")

                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw Exception("Could not decode image bytes from Pollinations AI")

                saveToCache(imageBytes, "orki_pollinations")
            }

            return@withContext Result.success(
                GeneratedImageFile(
                    file = fallbackFile,
                    engineName = "Pollinations AI (Backup)",
                    isFallback = true
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val finalMsg = "Image generation failed on all services:\n• Cloudflare: ${cloudflareError ?: "Unavailable"}\n• Azure DALL-E 3: ${azureError ?: "Unavailable"}\n• Pollinations AI: ${e.localizedMessage ?: "Network error"}"
            return@withContext Result.failure(Exception(finalMsg))
        }
    }

    suspend fun generateWithAzureDalle(
        prompt: String,
        azureEndpoint: String,
        azureKey: String,
        deployment: String = "dall-e-3",
        onStatusUpdate: ((stage: String) -> Unit)? = null
    ): Result<GeneratedImageFile> = withContext(Dispatchers.IO) {
        try {
            val baseEndpoint = azureEndpoint.trim().trimEnd('/')
            if (baseEndpoint.isEmpty() || azureKey.trim().isEmpty()) {
                return@withContext Result.failure(Exception("Azure Endpoint and API Key are required for DALL-E 3"))
            }

            val deploy = deployment.trim().ifEmpty { "dall-e-3" }
            val dalleUrl = "$baseEndpoint/openai/deployments/$deploy/images/generations?api-version=2024-02-01"

            onStatusUpdate?.invoke("Sending prompt to Azure DALL-E 3 ($deploy)...")

            val payload = JSONObject().apply {
                put("prompt", prompt.trim())
                put("n", 1)
                put("size", "1024x1024")
                put("quality", "standard")
                put("style", "vivid")
            }

            val request = Request.Builder()
                .url(dalleUrl)
                .addHeader("api-key", azureKey.trim())
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val dalleResult: File = httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val cleanMsg = try {
                        val errObj = JSONObject(bodyStr).optJSONObject("error")
                        errObj?.optString("message") ?: bodyStr
                    } catch (_: Exception) {
                        bodyStr
                    }
                    throw Exception("Azure DALL-E 3 HTTP ${response.code}: $cleanMsg")
                }

                val json = JSONObject(bodyStr)
                val dataArr = json.optJSONArray("data")
                    ?: throw Exception("No 'data' array returned by Azure DALL-E 3")
                if (dataArr.length() == 0) {
                    throw Exception("Azure DALL-E 3 returned empty data list")
                }

                val item = dataArr.getJSONObject(0)
                val imageUrl = item.optString("url", "")
                val b64 = item.optString("b64_json", "")

                val imageBytes: ByteArray = when {
                    imageUrl.isNotBlank() -> {
                        onStatusUpdate?.invoke("Downloading high-res artwork from Azure...")
                        val dlRequest = Request.Builder().url(imageUrl).build()
                        httpClient.newCall(dlRequest).execute().use { dlResp ->
                            if (!dlResp.isSuccessful) {
                                throw Exception("Failed to download image URL from Azure: HTTP ${dlResp.code}")
                            }
                            dlResp.body?.bytes() ?: throw Exception("Empty image payload from Azure URL")
                        }
                    }
                    b64.isNotBlank() -> {
                        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    }
                    else -> throw Exception("Azure DALL-E 3 response contained neither 'url' nor 'b64_json'")
                }

                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw Exception("Could not decode image bytes from Azure DALL-E 3")

                saveToCache(imageBytes, "orki_azure_dalle")
            }

            Result.success(
                GeneratedImageFile(
                    file = dalleResult,
                    engineName = "Azure OpenAI (DALL-E 3)",
                    isFallback = false
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun saveToCache(imageBytes: ByteArray, prefix: String): File {
        val imageDir = File(context.cacheDir, "generated_images")
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        val fileName = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png"
        val imageFile = File(imageDir, fileName)
        FileOutputStream(imageFile).use { out ->
            out.write(imageBytes)
            out.flush()
        }
        return imageFile
    }

    suspend fun saveImageToGallery(imageFile: File, title: String = "Orki AI Generated Image"): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Orki_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Orki AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Could not create MediaStore entry"))

            resolver.openOutputStream(imageUri)?.use { outStream ->
                imageFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            Result.success(imageUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
