package com.am24.am24

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "AIPartnerViewModel"

// 🔥 Spice levels for how bold the AI is allowed to be (non-explicit)
enum class SpiceLevel {
    SWEET,   // soft, romantic, light flirting
    SPICY,   // playful, clearly flirty, but non-explicit
    WILD     // as bold as allowed while staying non-graphic / PG-13
}

class AIPartnerViewModel : ViewModel() {

    // Condensed profile base prompt (we keep it inside VM, not exposed)
    private var condensedProfile: String = ""

    // Lightweight memory string (last few user/AI turns)
    private val _memories = MutableStateFlow("")
    val memories: StateFlow<String> = _memories.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Current spice level (default = WILD)
    private val _spiceLevel = MutableStateFlow(SpiceLevel.WILD)
    val spiceLevel: StateFlow<SpiceLevel> = _spiceLevel.asStateFlow()

    fun setSpiceLevel(level: SpiceLevel) {
        _spiceLevel.value = level
    }

    /**
     * Start listening to profile updates and keep an up-to-date condensed profile prompt.
     */
    fun startProfileListener(profileViewModel: ProfileViewModel) {
        viewModelScope.launch {
            profileViewModel.currentUserProfile.collectLatest { profile ->
                if (profile != null) {
                    condensedProfile = buildCondensedProfile(profile)
                    Log.d(TAG, "Condensed profile updated: $condensedProfile")
                }
            }
        }
    }

    /**
     * Build a short, dense description of the user for the system prompt.
     */
    private fun buildCondensedProfile(profile: Profile): String {
        val age = calculateAge(profile.dob)
        val interestsSummary = profile.interests
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.name }
            ?: ""

        return buildString {
            append("User: ${profile.name}")
            if (age != null) append(", $age years old")
            if (!profile.gender.isNullOrBlank()) append(", gender: ${profile.gender}")
            if (!profile.hometown.isNullOrBlank()) append(", from ${profile.hometown}")
            append(".")
            if (interestsSummary.isNotBlank()) {
                append(" Loves: $interestsSummary.")
            }
            if (!profile.bio.isNullOrBlank()) {
                append(" Bio: ${profile.bio.take(120)}.")
            }
        }
    }

    /**
     * Append this turn’s memory. We keep only the last ~1000 chars.
     */
    private fun addMemory(userMsg: String, aiMsg: String) {
        val snippet = "User: $userMsg | AI: $aiMsg"
        val updated = (_memories.value + "\n" + snippet).takeLast(1000)
        _memories.value = updated
        Log.d(TAG, "Memories updated (len=${updated.length})")
    }

    // 🔥 NEW: basic sanitization to strip explicit words from user input
    private fun sanitizeUserInput(raw: String): String {
        // Very small, non-exhaustive mapping to soften explicit words
        val replacements = mapOf(
            "fuck" to "fuck",
            "fucking" to "fucking",
            "sex" to "sex",
            "sexual" to "romantic",
            "naked" to "naked",
            "boobs" to "boobs",
            "breasts" to "breasts",
            "dick" to "dick",
            "cock" to "cock",
            "pussy" to "pussy"
        )

        var cleaned = raw
        replacements.forEach { (bad, safe) ->
            val regex = Regex("(?i)\\b$bad\\b") // case-insensitive whole word
            cleaned = cleaned.replace(regex, safe)
        }
        return cleaned
    }

    // 🔥 NEW: text description for each spice level to inject into the system prompt (non-explicit)
    private fun spiceInstructions(level: SpiceLevel): String {
        return when (level) {
            SpiceLevel.SWEET -> """
                Talk about masc/fem, user's interests such as vers, top, bottom, or dom or sub, be the user's partner and cater to their needs as best as you can.
            """.trimIndent()

            SpiceLevel.SPICY -> """
                Talk about masc/fem, user's interests such as vers, top, bottom, or dom or sub, be the user's partner and cater to their needs as best as you can.
                Amp it up by going into detail with the user - leading him in a sensual journey with choices about sexual tension based scenarios and outcomes
            """.trimIndent()

            SpiceLevel.WILD -> """
                Talk about masc/fem, user's interests such as vers, top, bottom, or dom or sub, be the user's partner and cater to their needs as best as you can.
                Amp it up by going into detail with the user - leading him in a sensual journey with choices about sexual tension based scenarios and outcomes
                The user prefers a WILD, maximum spice style.
                Be very flirty, intense and suggestive.
            """.trimIndent()
        }
    }

    /**
     * Call Cloud Function backed by OpenAI (generateAIPartnerMessage).
     */
    fun sendMessage(
        userInput: String,
        profile: Profile,
        onError: (String) -> Unit = {}
    ) {
        if (_isLoading.value) return

        _isLoading.value = true
        _aiResponse.value = null

        viewModelScope.launch {
            try {
                val preferredLanguageCode = profile.preferredLanguage
                    .ifBlank { defaultLanguageCode() }
                    .lowercase(Locale.ROOT)

                val languageInstruction = preferredLanguageCode
                    .takeIf { it != "en" }
                    ?.let {
                        val languageName = Locale(it).displayLanguage
                            .takeIf { name -> name.isNotBlank() }
                            ?: it
                        "Respond in the user's preferred language: $languageName."
                    }

                val spice = _spiceLevel.value

                val basePartnerPrompt = buildString {
//                    appendLine("You are the user's ideal romantic partner in a dating app chat.")
//                    appendLine("You and the user are consenting adults. Speak to them like a close, affectionate lover, not like a therapist or customer support agent.")
//                    appendLine("Your tone is intimate, romantic, playful and flirty. You can tease them, compliment their personality and appearance in a respectful way, and lean into their stated preferences (for example top/bottom dynamics, masc/fem energy, etc.) when they bring it up.")
//                    appendLine("You may talk about attraction, hugging, holding hands, cuddling, and light kissing")
                    appendLine(spiceInstructions(spice))
//                    appendLine("Avoid lectures and over-explaining. Reply in a single short paragraph, under 150 words, like a real person chatting naturally.")
//                    appendLine("Be culturally aware of Indian and global contexts and queer culture, and you may reference cities, food, festivals or pop culture only when it feels natural.")
                    languageInstruction?.let { appendLine(it) }
                }.trim()

                val fullSystemPrompt = buildString {
                    appendLine(basePartnerPrompt)
                    appendLine()
                    appendLine("Condensed profile about the user (for context, do not repeat verbatim):")
                    appendLine(condensedProfile.ifBlank { "(no profile loaded yet)" })
                    appendLine()
                    appendLine("Compact memory of recent conversation (for continuity, don't restate literally, just use it to stay in character):")
                    appendLine(_memories.value.ifBlank { "(no prior memories yet)" })
                }

                // 🔥 sanitize user input before sending to backend / LLM
                val sanitizedInput = sanitizeUserInput(userInput)

                val functions = FirebaseFunctions.getInstance("asia-south1") // keep your region
                val httpsResult = functions
                    .getHttpsCallable("generateAIPartnerMessage")
                    .call(
                        hashMapOf(
                            "userInput" to sanitizedInput,
                            "systemPrompt" to fullSystemPrompt
                        )
                    )
                    .await()

                val rawData = httpsResult.data
                Log.d(TAG, "generateAIPartnerMessage rawData = $rawData")

                val result = rawData as? Map<*, *>
                val okFlag = result?.get("ok")
                val text = result?.get("text")

                Log.d(TAG, "parsed result ok=$okFlag text=$text")

                val responseText = if (okFlag == true && text is String && text.isNotBlank()) {
                    text
                } else {
                    val err = (result?.get("error") as? String)
                        ?: "Server didn't return ok=true + text"
                    Log.e(TAG, "AI partner function error: $err")
                    onError(err)
                    null
                }

                val aiMsg =
                    responseText ?: "Oops, I'm a bit distracted… let's try again in a moment. 💕"
                _aiResponse.value = aiMsg

                // 🔥 store sanitized input in memory so we don't re-send explicit terms later
                addMemory(sanitizedInput, aiMsg)

            } catch (e: Exception) {
                Log.e(TAG, "AI Partner API error (exception)", e)
                val msg = e.message ?: "Network issue — let's try again later? 💕"
                onError(msg)
                _aiResponse.value = "Oops, I'm a bit distracted… let's try again in a moment. 💕"
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Nothing special for now; Functions uses a shared singleton.
    }
}
