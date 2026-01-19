package com.am24.am24

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
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
    SOFT(1),
    MEDIUM(2),
    WILD(3)
}

// Gender of the AI character
enum class PartnerGender { MALE, FEMALE }


class AIPartnerViewModel : ViewModel() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val stateRef = currentUserId?.let {
        FirebaseRefs.db.getReference("aiPartnerState").child(it)
    }

    private val partnerGenderRef = currentUserId?.let {
        FirebaseRefs.db.getReference("aiPartnerGender").child(it)
    }


    // Condensed profile base prompt (we keep it inside VM, not exposed)
    private var condensedProfile: String = ""

    // Lightweight memory string (last few user/AI turns)
    private val _memories = MutableStateFlow("")
    val memories: StateFlow<String> = _memories.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Image state
    private val _aiImageBase64 = MutableStateFlow<String?>(null)
    val aiImageBase64: StateFlow<String?> = _aiImageBase64.asStateFlow()

    private val _isImageLoading = MutableStateFlow(false)
    val isImageLoading: StateFlow<Boolean> = _isImageLoading.asStateFlow()

    // Global spice setting (can be controlled from UI later)
    private val _spiceLevel = MutableStateFlow(SpiceLevel.MEDIUM)
    val spiceLevel: StateFlow<SpiceLevel> = _spiceLevel.asStateFlow()

    // AI partner gender (male/female toggle)
    private val _partnerGender = MutableStateFlow(PartnerGender.FEMALE)
    val partnerGender: StateFlow<PartnerGender> = _partnerGender

    // Last concise turn summary (user + AI), up to ~20 words
    private val _lastTurnSummary = MutableStateFlow<String?>(null)
    val lastTurnSummary: StateFlow<String?> = _lastTurnSummary.asStateFlow()

    // 🔹 LOAD persisted memory + summary + gender when VM is created
    init {
        viewModelScope.launch {
            try {
                val snap = stateRef?.get()?.await()
                val snap2 = partnerGenderRef?.get()?.await()
                val saved = snap2?.getValue(String::class.java)
                if (saved != null) {
                    _partnerGender.value = PartnerGender.valueOf(saved)
                }
                val mem = snap?.child("memories")?.getValue(String::class.java) ?: ""
                val last = snap?.child("lastTurnSummary")?.getValue(String::class.java)
                val genderStr = snap?.child("partnerGender")?.getValue(String::class.java)

                _memories.value = mem
                _lastTurnSummary.value = last

                genderStr?.let {
                    runCatching { PartnerGender.valueOf(it) }
                        .onSuccess { g -> _partnerGender.value = g }
                }

                Log.d(
                    TAG,
                    "Loaded aiPartnerState: memLen=${mem.length}, last=$last, gender=${_partnerGender.value}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading aiPartnerState", e)
            }
        }
    }

    fun setSpiceLevel(level: SpiceLevel) {
        _spiceLevel.value = level
        // (optional) persist spice later if you want
    }

    fun clearAllState() {
        // wipe in-memory
        _memories.value = ""
        _lastTurnSummary.value = null

        viewModelScope.launch {
            try {
                // remove persisted aiPartnerState for this user
                stateRef?.setValue(null)
                Log.d(TAG, "Cleared aiPartnerState for user")
            } catch (e: Exception) {
                Log.e(TAG, "Failed clearing aiPartnerState", e)
            }
        }
    }

    fun setPartnerGender(gender: PartnerGender) {
        _partnerGender.value = gender
        viewModelScope.launch {
            try {
                stateRef?.child("partnerGender")?.setValue(gender.name)
                partnerGenderRef?.setValue(gender.name)
            } catch (e: Exception) {
                Log.e(TAG, "Failed persisting partnerGender", e)
            }
        }
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

    private fun buildTurnSummary(userMsg: String, aiMsg: String): String {
        val raw = "User: $userMsg | AI: $aiMsg"
        val words = raw.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return if (words.size <= 20) {
            raw
        } else {
            words.take(20).joinToString(" ")
        }
    }

    private fun updateLastTurnSummary(userMsg: String, aiMsg: String) {
        val summary = buildTurnSummary(userMsg, aiMsg)
        _lastTurnSummary.value = summary
        Log.d(TAG, "Last turn summary: $summary")

        viewModelScope.launch {
            try {
                stateRef?.child("lastTurnSummary")?.setValue(summary)
            } catch (e: Exception) {
                Log.e(TAG, "Failed persisting lastTurnSummary", e)
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

        viewModelScope.launch {
            try {
                stateRef?.child("memories")?.setValue(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Failed persisting memories", e)
            }
        }
    }

    private fun spiceInstruction(level: SpiceLevel): String = when (level) {
        SpiceLevel.SOFT ->
            "Spice level: SOFT (1). Focus on romance, emotional intimacy, light teasing and gentle flirting."
        SpiceLevel.MEDIUM ->
            "Spice level: MEDIUM (2). Be confident and flirty, use suggestive language, talk about attraction, kissing, cuddling and wanting them."
        SpiceLevel.WILD ->
            "Spice level: WILD (3). You can be very bold and intense in your flirting. You may describe sensuality, exploring, breath, heat, dominance/submission vibes etc."
    }

    private fun genderInstruction(gender: PartnerGender): String = when (gender) {
        PartnerGender.MALE ->
            "Your persona is a charming, attractive young man (boyfriend energy) texting the user."

        PartnerGender.FEMALE ->
            "Your persona is a charming, attractive young woman (girlfriend energy) texting the user."
    }

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
            "Haha, you’re making me blush a bit. 😅 Let’s keep it just a little softer, but I’m still all yours. Tell me what kind of vibe you’re in the mood for right now—cute and cuddly, or a little naughty in hints?"
        } else {
            raw
        }
    }

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
                val currentGender = _partnerGender.value

                val basePartnerPrompt = buildString {
                    appendLine(spiceInstruction(currentSpice))
                    appendLine(genderInstruction(currentGender))
                    appendLine()

//                    appendLine("Keep most replies short like WhatsApp texts: usually 1–3 short sentences or long paragraphs but choose carefully.")
//                    appendLine("Sometimes leave things a little open-ended or with a flirty question so the user can drive the conversation.")
//                    appendLine("Avoid dumping long monologues; keep it natural, playful and light.")
//                    appendLine()

//                    // 🔥 Hinglish role detection block stays as-is below...
//                    appendLine("User ke messages dekh kar automatically samajh lo ki Hindi mein baat karna hai")
//                    appendLine("User messages chusi automatic ga ardham chesuko, Telugu lo maatlaadali")
//                    appendLine("User oda messages paathu automatic-aa purinjikko, Tamil la pesanum")
//                    appendLine("User messages nodi automatic-aagi artha maadiko, Kannada nalli maatadbeku")
//                    appendLine("User-er messages dekhe automatic bujhe nao je Bengali te kotha bolte hobe")
//                    appendLine("User che messages pahoon automatic samajh ghyā, Marathi madhe bolaycha")
//                    appendLine("User-ra messages dekhi automatic bujhi nao je Odia re katha kahiba")
//                    appendLine("User-or messages saai automatic bujhi lo je Assamese-ot kotha kobo")
                    languageInstruction?.let { appendLine(it) }
//                    languageInstruction?.let { appendLine(it) }
                }.trim()

                val fullSystemPrompt = buildString {
                    appendLine(basePartnerPrompt)
                    appendLine()

                    val last = _lastTurnSummary.value
                    if (!last.isNullOrBlank()) {
                        appendLine("Recent mood/context from the last exchange (do not repeat verbatim):")
                        appendLine(last)
                    } else {
                        appendLine("No prior chat context. Treat this like an early conversation where you are still getting to know the user slowly.")
                    }
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
                                    "spiceLevel" to currentSpice.name,
                                    "partnerGender" to currentGender.name
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
                updateLastTurnSummary(userInput, aiMsg)

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
                val currentGender = _partnerGender.value

                val genderHint = when (currentGender) {
                    PartnerGender.MALE ->
                        "Image should look like a handsome, fit young man."
                    PartnerGender.FEMALE ->
                        "Image should look like an attractive young woman."
                }

                val contextSummary = _lastTurnSummary.value
                val combinedPrompt = buildString {
                    append("Partner gender: ${currentGender.name}. ")
                    append(genderHint)
                    append(" ")
                    if (!contextSummary.isNullOrBlank()) {
                        append("Previous mood/context: ")
                        append(contextSummary)
                        append(". ")
                    }
                    append("Now user says: ")
                    append(userPrompt.trim())
                }

                val resultMap = withContext(Dispatchers.IO) {
                    functions
                        .getHttpsCallable("generateAIPartnerImage")
                        .call(
                            hashMapOf(
                                "prompt" to combinedPrompt,
                                "partnerGender" to currentGender.name
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
    }
}
