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

private const val TAG = "AIPartnerViewModel"

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
                val basePartnerPrompt = """
                    You are the user's ideal romantic partner in a dating app.
                    Be empathetic, flirty, supportive, and culturally aware of Indian and global contexts.
                    Reference Indian festivals, cities, food or pop-culture only when relevant (not always).
                    Responses must be under 150 words. Respond in the user's preferred language.
                """.trimIndent()

                val fullSystemPrompt = buildString {
                    appendLine(basePartnerPrompt)
                    appendLine()
                    appendLine("Profile:")
                    appendLine(condensedProfile)
                    appendLine()
                    appendLine("Key memories from previous chats:")
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
