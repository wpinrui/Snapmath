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
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "Snapmath.LLM"

class OpenAiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
    }

    /**
     * Sends an image to GPT-4o with a prompt and returns the response text.
     */
    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val totalStartTime = System.currentTimeMillis()
        Log.d(TAG, "[LLM] ========== Starting OpenAI API Request ==========")
        Log.d(TAG, "[LLM] Model: $MODEL")
        Log.d(TAG, "[LLM] Image size: ${bitmap.width}x${bitmap.height}")
        Log.d(TAG, "[LLM] Prompt: ${prompt.take(100)}${if (prompt.length > 100) "..." else ""}")

        try {
            val encodeStartTime = System.currentTimeMillis()
            val base64Image = bitmapToBase64(bitmap)
            val encodeTime = System.currentTimeMillis() - encodeStartTime
            Log.d(TAG, "[LLM] Image encoded to base64 in ${encodeTime}ms (${base64Image.length} chars)")

            val requestBody = createRequestBody(prompt, base64Image)
            Log.d(TAG, "[LLM] Request body created, size: ${requestBody.length} bytes")

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "[LLM] Sending request to OpenAI...")
            val networkStartTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val networkTime = System.currentTimeMillis() - networkStartTime
            Log.d(TAG, "[LLM] Response received in ${networkTime}ms, status: ${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "[LLM] API Error ${response.code}: $errorBody")
                return@withContext Result.failure(Exception("API error ${response.code}: $errorBody"))
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            Log.d(TAG, "[LLM] Response body size: ${responseBody.length} bytes")

            val parsedResponse = json.decodeFromString<ChatCompletionResponse>(responseBody)
            val content = parsedResponse.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("No content in response"))

            val totalTime = System.currentTimeMillis() - totalStartTime
            Log.d(TAG, "[LLM] ========== OpenAI Response ==========")
            Log.d(TAG, "[LLM] Total time: ${totalTime}ms")
            Log.d(TAG, "[LLM] Response length: ${content.length} chars")
            Log.d(TAG, "[LLM] Response content:\n$content")
            Log.d(TAG, "[LLM] ========================================")

            Result.success(content)
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - totalStartTime
            Log.e(TAG, "[LLM] Request failed after ${totalTime}ms: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        Log.d(TAG, "[LLM] Compressed image to ${bytes.size} bytes")
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
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
        val totalStartTime = System.currentTimeMillis()
        Log.d(TAG, "[LLM] ========== Starting Streaming OpenAI API Request ==========")
        Log.d(TAG, "[LLM] Model: $MODEL")
        Log.d(TAG, "[LLM] Image size: ${bitmap.width}x${bitmap.height}")

        val base64Image = bitmapToBase64(bitmap)
        Log.d(TAG, "[LLM] Image encoded to base64 (${base64Image.length} chars)")

        val requestBody = createRequestBody(prompt, base64Image, stream = true)
        Log.d(TAG, "[LLM] Request body created, size: ${requestBody.length} bytes")

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "[LLM] Sending streaming request to OpenAI...")
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "[LLM] API Error ${response.code}: $errorBody")
            throw Exception("API error ${response.code}: $errorBody")
        }

        val reader = response.body?.byteStream()?.bufferedReader()
            ?: throw Exception("Empty response body")

        var fullContent = StringBuilder()

        reader.useLines { lines ->
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        Log.d(TAG, "[LLM] Stream complete")
                        break
                    }
                    try {
                        val chunk = json.decodeFromString<StreamChunk>(data)
                        val content = chunk.choices.firstOrNull()?.delta?.content
                        if (content != null) {
                            fullContent.append(content)
                            emit(content)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[LLM] Skipping malformed chunk: ${e.message}")
                    }
                }
            }
        }

        val totalTime = System.currentTimeMillis() - totalStartTime
        Log.d(TAG, "[LLM] ========== Streaming Complete ==========")
        Log.d(TAG, "[LLM] Total time: ${totalTime}ms")
        Log.d(TAG, "[LLM] Total content length: ${fullContent.length} chars")
        Log.d(TAG, "[LLM] ==========================================")
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
