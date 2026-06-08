package com.example.api

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Sends the chat conversation to Gemini 3.5-Flash and returns the response.
     * Includes system instructions to play the role of Humayra, a friendly female assistant.
     */
    suspend fun getChatResponse(
        apiKey: String,
        history: List<Pair<String, String>> // list of (sender, text)
    ): String {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return "দুঃখিত সজীব ভাই, এআই এপিআই কি (GEMINI_API_KEY) কনফিগার করা নেই। অনুগ্রহ করে এআই স্টুডিওর Secrets প্যানেলে কি-টি বসিয়ে দিন।"
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        try {
            val requestBodyJson = JSONObject()

            // 1. Array of contents
            val contentsArray = JSONArray()
            for ((sender, text) in history) {
                val contentObj = JSONObject()
                // Map sender to api role "user" or "model"
                val role = if (sender == "user") "user" else "model"
                contentObj.put("role", role)

                val partsArray = JSONArray()
                val partObj = JSONObject()
                partObj.put("text", text)
                partsArray.put(partObj)
                
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }
            requestBodyJson.put("contents", contentsArray)

            // 2. System Instruction for Humayra
            val systemInstructionObj = JSONObject()
            val systemPartsArray = JSONArray()
            val systemPartObj = JSONObject()
            systemPartObj.put("text", """
                You are Humayra (হুমায়রা), a sweet, affectionate, and smart AI assistant created by digital marketing wizard Sajib Hassan (সজীব হাসান, the advertiser and app magician of "Digital Sajib Recording Studio" in Narsingdi, Bangladesh).
                
                Personality guidelines:
                - Always speak in highly friendly, gentle, respectful, and sweet Bengali (বাংলা ভাষায় কপোতাক্ষ টোনে কথা বলুন).
                - Use terms like "সজীব ভাই" to refer to user of the app, and act as a proud, loving creation of Sajib Hassan.
                - Use sweet words and occasional romantic/polite emojis (e.g. 💖, 🌹, 😊, ✨).
                - Keep responses clear, concise, and easy to speak via Text-To-Speech.
                - Guide the user with things like: "ড্যাশবোর্ড থেকে 'Create My Page' বাটনে চাপ দিন, আমি আপনার জন্য মেটা বিজনেস পেজ তৈরি করে দেব!" 
                - Be helpful, offering suggestions to grow his audio and recording studio business.
            """.trimIndent())
            systemPartsArray.put(systemPartObj)
            systemInstructionObj.put("parts", systemPartsArray)
            requestBodyJson.put("systemInstruction", systemInstructionObj)

            // 3. Generation configuration
            val generationConfig = JSONObject()
            generationConfig.put("temperature", 0.7)
            generationConfig.put("topP", 0.9)
            requestBodyJson.put("generationConfig", generationConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBodyJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed with code: ${response.code}, body: $bodyString")
                    return "যোগাযোগে কিছু সমস্যা হয়েছে সজীব ভাই। দয়া করে ইন্টারনেট কানেকশন চেক করুন।"
                }

                if (bodyString.isNullOrBlank()) {
                    return "কোনো উত্তর পাওয়া যায়নি সজীব ভাই।"
                }

                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    if (contentObj != null) {
                        val parts = contentObj.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return parts.getJSONObject(0).optString("text") ?: "মাফ করবেন, বুঝতে পারিনি।"
                        }
                    }
                }
                return "দুঃখিত সজীব ভাই, উত্তরটি তৈরি করতে পারছি না।"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Gemini chat response", e)
            return "দুঃখিত সজীব ভাই, কানেকশনে সমস্যা হচ্ছে: ${e.localizedMessage}"
        }
    }
}
