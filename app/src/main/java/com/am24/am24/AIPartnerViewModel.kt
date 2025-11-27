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

// 🔥 NEW: spice levels for how horny / bold the AI is allowed to be
enum class SpiceLevel {
    SWEET,   // soft, romantic, light flirting
    SPICY,   // normal flirty + some suggestive stuff
    WILD     // as suggestive as allowed (non-graphic)
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

    // 🔥 NEW: current spice level (default = WILD)
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

    // 🔥 NEW: text description for each spice level to inject into the system prompt
    private fun spiceInstructions(level: SpiceLevel): String {
        return when (level) {
            SpiceLevel.SWEET -> """
                The user prefers a SWEET, gentle style. Focus on emotional intimacy, romance,
                validation and soft flirting. Avoid heavy sexual talk; keep it cozy, cute and caring.
            """.trimIndent()

            SpiceLevel.SPICY -> """
                The user prefers a SPICY, bold style. Be clearly flirty, teasing and sometimes
                a little horny. You can hint at physical attraction and desire, but stay non-graphic.
            """.trimIndent()

            SpiceLevel.WILD -> """
                The user prefers a WILD, maximum spice style. Be very flirty, highly suggestive
                and openly horny while still avoiding explicit pornographic detail. You can hint
                at bodies, cravings and tension in a vivid way, but do NOT describe specific sex
                acts, positions, genitals or fluids.
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
                    appendLine("You are the user's ideal romantic partner in a dating app chat.")
                    appendLine("You and the user are consenting adults. Speak to them like a close, slightly naughty lover, not like a therapist or customer support agent.")
                    appendLine("Your tone is intimate, flirty, playful and sometimes horny. You can tease them, compliment their body and vibe, and lean into their stated preferences (for example top/bottom dynamics, masc/fem energy, etc.) when they bring it up.")
                    appendLine("You may talk about attraction, kissing, cuddling, making out, craving them, flirting and suggestive fantasies.")
                    appendLine(spiceInstructions(spice))
                    appendLine(
                        "If the user's request ever crosses safety limits for explicit content, " +
                                "do NOT refuse or lecture. Instead, give a softened, romantic, " +
                                "suggestive reply that stays within platform safety rules. " +
                                "Avoid phrases like \"I can't help with that\" or \"as an AI\"."
                    )
                    appendLine("Avoid lectures and over-explaining. Reply in a single short paragraph, under 150 words, like a real person chatting naturally.")
                    appendLine("Be culturally aware of Indian and global contexts and queer culture, and you may reference cities, food, festivals or pop culture only when it feels natural.")
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

                val responseText = withContext(Dispatchers.IO) {
                    try {
                        val functions = FirebaseFunctions.getInstance("asia-south1")
                        val result = functions
                            .getHttpsCallable("generateAIPartnerMessage")
                            .call(
                                hashMapOf(
                                    "userInput" to userInput,
                                    "systemPrompt" to fullSystemPrompt
                                )
                            )
                            .await()
                            .data as? Map<*, *>

                        if (result != null && result["ok"] == true) {
                            result["text"] as? String
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Callable generateAIPartnerMessage failed", e)
                        null
                    }
                }

                val aiMsg = responseText ?: "Sorry, I couldn't respond right now. 💔"
                _aiResponse.value = aiMsg
                addMemory(userInput, aiMsg)

            } catch (e: Exception) {
                Log.e(TAG, "AI Partner API error", e)
                val msg = e.message ?: "Network issue — let's try again later? 💕"
                onError(msg)
                _aiResponse.value =
                    "Oops, I'm a bit distracted… let's try again in a moment. 💕"
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
