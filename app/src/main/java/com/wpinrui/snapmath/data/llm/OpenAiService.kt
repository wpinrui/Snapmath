package com.wpinrui.snapmath.data.llm

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

private const val TAG = "Snapmath.LLM"

class OpenAiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
        private const val MAX_IMAGE_DIMENSION = 1024
        private const val JPEG_QUALITY = 80
    }

    /**
     * Sends an image to GPT-4o with a prompt and returns the response text.
     */
    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resizedBitmap = resizeBitmapIfNeeded(bitmap)
            val base64Image = bitmapToBase64(resizedBitmap)
            val requestBody = createRequestBody(prompt, base64Image)

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API Error ${response.code}: $errorBody")
                return@withContext Result.failure(Exception("API error ${response.code}: $errorBody"))
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val parsedResponse = json.decodeFromString<ChatCompletionResponse>(responseBody)
            val content = parsedResponse.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("No content in response"))

            Result.success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        val scale = min(
            MAX_IMAGE_DIMENSION.toFloat() / width,
            MAX_IMAGE_DIMENSION.toFloat() / height
        )

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun createRequestBody(prompt: String, base64Image: String, stream: Boolean = false): String {
        val textContent = buildJsonObject {
            put("type", "text")
            put("text", prompt)
        }

        val imageContent = buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject {
                put("url", "data:image/jpeg;base64,$base64Image")
            })
        }

        val message = buildJsonObject {
            put("role", "user")
            put("content", JsonArray(listOf(textContent, imageContent)))
        }

        val requestJson = buildJsonObject {
            put("model", MODEL)
            put("messages", JsonArray(listOf(message)))
            put("max_tokens", 4096)
            if (stream) {
                put("stream", true)
            }
        }

        return requestJson.toString()
    }

    /**
     * Streams an image analysis response, emitting text chunks as they arrive.
     */
    fun analyzeImageStreaming(bitmap: Bitmap, prompt: String): Flow<String> = flow {
        val resizedBitmap = resizeBitmapIfNeeded(bitmap)
        val base64Image = bitmapToBase64(resizedBitmap)
        val requestBody = createRequestBody(prompt, base64Image, stream = true)

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "API Error ${response.code}: $errorBody")
            throw Exception("API error ${response.code}: $errorBody")
        }

        val reader = response.body?.byteStream()?.bufferedReader()
            ?: throw Exception("Empty response body")

        reader.useLines { lines ->
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = json.decodeFromString<StreamChunk>(data)
                        val content = chunk.choices.firstOrNull()?.delta?.content
                        if (content != null) {
                            emit(content)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping malformed chunk: ${e.message}")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}

// Response models for non-streaming
@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice>
)

@Serializable
private data class Choice(
    val message: ResponseMessage
)

@Serializable
private data class ResponseMessage(
    val content: String?
)

// Response models for streaming
@Serializable
private data class StreamChunk(
    val choices: List<StreamChoice>
)

@Serializable
private data class StreamChoice(
    val delta: StreamDelta
)

@Serializable
private data class StreamDelta(
    val content: String? = null
)
