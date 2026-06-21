package com.msi.ai

import android.util.Log
import com.msi.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiMetadataHelper {
    private const val TAG = "GeminiMetadataHelper"
    private const val MODEL = "gemini-3.5-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class MetadataSuggestions(
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val year: String,
        val explanation: String
    )

    suspend fun fetchSuggestions(
        filePath: String,
        currentTitle: String,
        currentArtist: String,
        currentAlbum: String,
        currentGenre: String
    ): MetadataSuggestions? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "UNSPECIFIED" || apiKey == "null") {
            Log.e(TAG, "Gemini API key is not configured or is empty!")
            return null
        }

        val prompt = """
            You are an expert music metadata tagging assistant.
            Analyze the following audio file metadata details:
            - File Path: $filePath
            - Current Title: $currentTitle
            - Current Artist: $currentArtist
            - Current Album: $currentAlbum
            - Current Genre: $currentGenre

            Based on this information (using the file path or title to identify the exact track, artist, album, genre, release year if possible), automatically suggest highly accurate tags for:
            1. Title (properly formatted with correct capitalization, no extensions)
            2. Artist (correct performer name)
            3. Album (exact album name or 'Single' if uncategorized)
            4. Genre (standard genre classification, e.g., Synthwave, Chillwave, Rock, Pop, Classical, Electronic, Jazz, etc.)
            5. Year (correct release year, or 'Unknown Year' if not certifiable)

            Provide your response exclusively as a valid JSON object. Do not include any explanation text outside the JSON. Format the JSON with these exact string fields:
            - "title"
            - "artist"
            - "album"
            - "genre"
            - "year"
            - "explanation" (a brief 1-2 sentence explanation of why you made these suggestions)
        """.trimIndent()

        val jsonRequest = JSONObject()
        val contentsArray = org.json.JSONArray()
        val contentObject = JSONObject()
        val partsArray = org.json.JSONArray()
        val partObject = JSONObject()
        partObject.put("text", prompt)
        partsArray.put(partObject)
        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        jsonRequest.put("contents", contentsArray)

        // Set up generation config for application/json response format
        val generationConfig = JSONObject()
        generationConfig.put("responseMimeType", "application/json")
        jsonRequest.put("generationConfig", generationConfig)

        val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val url = "$ENDPOINT?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Request failed with code: ${response.code}")
                        return@withContext null
                    }
                    val bodyString = response.body?.string() ?: return@withContext null
                    Log.d(TAG, "Response: $bodyString")
                    
                    val responseJson = JSONObject(bodyString)
                    val candidatesJson = responseJson.optJSONArray("candidates")
                    val contentJson = candidatesJson?.optJSONObject(0)?.optJSONObject("content")
                    val partsJson = contentJson?.optJSONArray("parts")
                    val text = partsJson?.optJSONObject(0)?.optString("text") ?: ""
                    
                    if (text.isNotEmpty()) {
                        var cleanedText = text.trim()
                        if (cleanedText.startsWith("```json")) {
                            cleanedText = cleanedText.substringAfter("```json").substringBeforeLast("```").trim()
                        } else if (cleanedText.startsWith("```")) {
                            cleanedText = cleanedText.substringAfter("```").substringBeforeLast("```").trim()
                        }
                        
                        val suggestionJson = JSONObject(cleanedText)
                        MetadataSuggestions(
                            title = suggestionJson.optString("title", currentTitle),
                            artist = suggestionJson.optString("artist", currentArtist),
                            album = suggestionJson.optString("album", currentAlbum),
                            genre = suggestionJson.optString("genre", currentGenre),
                            year = suggestionJson.optString("year", "Unknown Year"),
                            explanation = suggestionJson.optString("explanation", "Suggested corrections based on file properties.")
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed during Gemini request: ${e.message}", e)
                null
            }
        }
    }
}
