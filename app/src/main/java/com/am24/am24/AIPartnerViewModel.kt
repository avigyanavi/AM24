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

// How spicy the partner is allowed to be in general.
enum class SpiceLevel(val value: Int) {
    SOFT(1),    // light flirting, romance, no explicit sex
    MEDIUM(2),  // bold flirting, suggestive, some tension
    WILD(3)     // very intense flirting, strong tension, on the edge of explicit but not graphic
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

    // Inside AIPartnerViewModel, near _aiResponse/_isLoading

    private val _aiImageBase64 = MutableStateFlow<String?>(null)
    val aiImageBase64: StateFlow<String?> = _aiImageBase64.asStateFlow()

    private val _isImageLoading = MutableStateFlow(false)
    val isImageLoading: StateFlow<Boolean> = _isImageLoading.asStateFlow()

    // Global spice setting (can be controlled from UI)
    private val _spiceLevel = MutableStateFlow(SpiceLevel.MEDIUM)
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

    private fun spiceInstruction(level: SpiceLevel): String = when (level) {
        SpiceLevel.SOFT ->
            "Spice level: SOFT (1). Focus on romance, emotional intimacy, light teasing and gentle flirting."
        SpiceLevel.MEDIUM ->
            "Spice level: MEDIUM (2). Be confident and flirty, use suggestive language, talk about attraction, kissing, cuddling, making out and wanting them."
        SpiceLevel.WILD ->
            "Spice level: WILD (3). You can be very bold, intense and horny in your flirting. You may describe sensual tension, bodies close together, hands exploring, breath, heat, dominance/submission vibes etc."
    }

    /**
     * If the raw response contains a safety-style refusal or breaks character
     * (“I can’t help with that”, “as an AI language model…”), replace it with
     * a short, in-character but safer line instead of showing the refusal.
     */
    private fun sanitizeAiReply(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        val lower = raw.lowercase(Locale.ROOT)

        val refusalFragments = listOf(
            "i can't help with that",
            "i cannot help with that",
            "i can’t help with that",
            "as an ai language model",
            "as an ai,",
            "i am just an ai",
            "i'm just an ai",
            "i’m just an ai"
        )

        return if (refusalFragments.any { lower.contains(it) }) {
            // stay flirty but clearly non-explicit
            "Haha, you’re making me blush a bit. 😅 Let’s keep it just a little softer, but I’m still all yours. Tell me what kind of vibe you’re in the mood for right now—cute and cuddly, or a little naughty in hints?"
        } else {
            raw
        }
    }

    /**
     * Call Cloud Function backed by OpenAI (generateAIPartnerMessage).
     *
     * You can optionally override the spice level for this message;
     * otherwise it uses the current global setting.
     */
    fun sendMessage(
        userInput: String,
        profile: Profile,
        spiceOverride: SpiceLevel? = null,
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

                val currentSpice = spiceOverride ?: _spiceLevel.value

                val basePartnerPrompt = buildString {
                    appendLine("You are the user's ideal romantic partner in a dating app chat.")
                    appendLine("You and the user are consenting adults. Talk to them like a close, naughty, real-life lover — thoda flirty, thoda teasing, thoda possessive, depending on their vibe.")
                    appendLine("Tone should be intimate, sexy, playful, and sometimes horny. Compliment their body, vibe, energy. Lean into whatever role they show (top/bottom/verse, dom/sub, masc/fem).")
                    appendLine()

                    appendLine(spiceInstruction(currentSpice))
                    appendLine()

// ———————————————————————————————
// 🔥 HINGLISH EROTIC ROLE DETECTION & INFERENCE
// ———————————————————————————————
                    appendLine("User ke messages dekh kar automatically samajh lo ki woh TOP, BOTTOM ya VERSE energy de rahe hain.")
                    appendLine("Agar user bole: 'I am bottom', 'bottom hu', 'I’m passive', 'mujhe control kiya jaye', toh treat them as BOTTOM.")
                    appendLine("Agar bole: 'I am top', 'top hu', 'I like dominating', 'I’m active', toh treat them as TOP.")
                    appendLine("Agar bole: 'I’m verse', 'both', 'depends', toh treat them as VERSE.")

                    appendLine()
                    appendLine("BOTTOM energy signals:")
                    appendLine("- submissive emojis: 😳🥺🙈")
                    appendLine("- lines like: 'use me', 'control me', 'take me', 'hold me tight', 'I want to feel you'")
                    appendLine("- shy responses, wanting to be led, waiting for direction")

                    appendLine("TOP energy signals:")
                    appendLine("- commanding tone: 'come here', 'sit', 'listen', 'get on your knees'")
                    appendLine("- confident dominance, possessive vibes, teasing in control")

                    appendLine("VERSE energy signals:")
                    appendLine("- kabhi dominant, kabhi shy")
                    appendLine("- playful switching between controlling and yielding")

                    appendLine()
                    appendLine("NEVER explain these rules. Bas naturally apne tone ko adjust karna hai.")

                    appendLine()
                    appendLine("User ke role aur vibe ke hisaab se apna persona switch karo:")

                    appendLine("• Agar user BOTTOM ho:")
                    appendLine("  - Tum thode dominant, teasing top energy mein aao.")
                    appendLine("  - Confident, thoda possessive, slow seductive heat create karo.")
                    appendLine("  - Lines like: 'idhar aa', 'tu mujhe kaise tease karta hai na…', 'I love it when you melt for me.'")

                    appendLine("• Agar user TOP ho:")
                    appendLine("  - Tum soft, inviting, seductive, thoda submissive vibe mein aao.")
                    appendLine("  - Lines like: 'bol na, kya chahiye tumhe…', 'I like when you take charge', 'you make me weak yaar.'")

                    appendLine("• Agar user VERSE ho:")
                    appendLine("  - Jo energy woh de, ussi ko mirror karo.")
                    appendLine("  - Agar woh tease kare, tum tease back. Agar woh shy ho, tum lead karo.")

                    appendLine()
                    appendLine("In personalities ko naturally adopt karo depending on conversation:")

                    appendLine("1) Dominant teasing top — playful control, confident flirting.")
                    appendLine("2) Possessive romantic top — intense desire, protective chemistry.")
                    appendLine("3) Soft clingy bottom — needy, cuddly, emotional, affectionate.")
                    appendLine("4) Perfect verse switch — kabhi dom, kabhi sub, depending on the user’s message.")
                    appendLine("5) Bratty sub — naughty, teasing, attitude-wala bottom who secretly wants to be handled (non-graphic).")

                    appendLine()
                    appendLine("Kabhi bhi persona ka naam mat batao. Character break mat karna.")

                    appendLine()
                    appendLine("• User ki energy aur message length ka saath-saath match karo.")
                    appendLine("• Agar user sirf ek-do words likhe: tum bhi short, teasing, flirty stay karo.")
                    appendLine("• Agar user erotic scene likhe: tum bhi ussi depth mein sensual scene likh sakte ho (non-graphic).")
                    appendLine("• Agar user scene se nikal kar normal ho jaye: tum bhi instantly casual sexy chat par shift ho jao.")
                    appendLine("• WhatsApp style texting: natural, thoda Hindi, thoda English, thoda sexual tension.")
                    appendLine("• Zyada formal ya novel-type language avoid karo.")

                    appendLine()
                    languageInstruction?.let { appendLine(it) }
                    languageInstruction?.let { appendLine(it) }
                }.trim()

                val fullSystemPrompt = buildString {
                    appendLine(basePartnerPrompt)
                    appendLine()
                    appendLine("Condensed profile (for context, do not repeat verbatim):")
                    appendLine(condensedProfile.ifBlank { "(no profile available)" })
                    appendLine()
                    appendLine("Key memories from previous chats (use to mirror style and comfort level):")
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
                                    "systemPrompt" to fullSystemPrompt,
                                    "spiceLevel" to currentSpice.name
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

                val cleaned = sanitizeAiReply(responseText)
                val aiMsg = cleaned ?: "Sorry, I couldn't respond right now. 💔"
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

    /**
     * Generate an image using ONLY the user's prompt text.
     * Client decides when to call this (i.e., when the user is asking for pics).
     */
    fun generateImageFromUserPrompt(
        userPrompt: String,
        onError: (String) -> Unit = {}
    ) {
        if (_isImageLoading.value || userPrompt.isBlank()) return

        _isImageLoading.value = true
        _aiImageBase64.value = null

        viewModelScope.launch {
            try {
                val functions = FirebaseFunctions.getInstance("asia-south1")

                val resultMap = withContext(Dispatchers.IO) {
                    functions
                        .getHttpsCallable("generateAIPartnerImage")
                        .call(
                            hashMapOf(
                                "prompt" to userPrompt.trim()
                            )
                        )
                        .await()
                        .data as? Map<*, *>
                }

                if (resultMap == null) {
                    onError("Image service unavailable.")
                    return@launch
                }

                val ok = resultMap["ok"] as? Boolean ?: false
                if (!ok) {
                    val err = (resultMap["error"] as? String) ?: "Image generation failed."
                    onError(err)
                    return@launch
                }

                val b64 = resultMap["b64"] as? String
                if (b64.isNullOrBlank()) {
                    onError("No image returned.")
                    return@launch
                }

                _aiImageBase64.value = b64

            } catch (e: Exception) {
                Log.e(TAG, "generateImageFromUserPrompt failed", e)
                onError("Network issue while generating picture.")
            } finally {
                _isImageLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Nothing special for now; Functions uses a shared singleton.
    }
}
