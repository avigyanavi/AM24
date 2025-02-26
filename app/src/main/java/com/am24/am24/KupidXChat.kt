package com.am24.am24

import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.am24.am24.profiles.RevaanProfileScreen
import com.am24.am24.profiles.RheaProfileScreen
import com.google.firebase.database.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ----------------------------------------------------------------------
// 1) Basic Enums, Classes for States + Actions
// ----------------------------------------------------------------------
enum class AI { RHEA, REVAAN }

enum class MaturityLevel {
    YOUNG, ADULT, MATURE
}

// Example: user actions that can happen (used for random daily events)
sealed class UserAction(val description: String) {
    abstract fun applyAction(currentState: ModelingState): ModelingState


    object BookPhotoShoot : UserAction("Book a Photoshoot") {
        override fun applyAction(currentState: ModelingState): ModelingState {
            // Instead of setting a dominant mood, we now add to the "happy" level.
            return currentState.copy(
                careerProgress = (currentState.careerProgress + 10).coerceAtMost(100),
                moodLevels = currentState.moodLevels.copy(
                    happy = (currentState.moodLevels.happy + 10).coerceAtMost(100)
                )
            )
        }
    }
    object IgnorePhotoShoot : UserAction("Ignore Photoshoot") {
        override fun applyAction(currentState: ModelingState): ModelingState {
            // Increase a negative emotion (here we add to "stressed")
            return currentState.copy(
                careerProgress = (currentState.careerProgress - 5).coerceAtLeast(0),
                moodLevels = currentState.moodLevels.copy(
                    stressed = (currentState.moodLevels.stressed + 5).coerceAtMost(100)
                )
            )
        }
    }
    object AttendFashionEvent : UserAction("Attend Fashion Event") {
        override fun applyAction(currentState: ModelingState): ModelingState {
            return currentState.copy(
                externalAttention = (currentState.externalAttention + 15).coerceAtMost(100),
                focusOnUser = (currentState.focusOnUser - 10).coerceAtLeast(0),
                moodLevels = currentState.moodLevels.copy(
                    happy = (currentState.moodLevels.happy + 5).coerceAtMost(100)
                )
            )
        }
    }
    object JealousSpat : UserAction("Jealous Spat") {
        override fun applyAction(currentState: ModelingState): ModelingState {
            return currentState.copy(
                jealousyLevel = (currentState.jealousyLevel + 25).coerceAtMost(100),
                focusOnUser = (currentState.focusOnUser - 15).coerceAtLeast(0),
                moodLevels = currentState.moodLevels.copy(
                    stressed = (currentState.moodLevels.stressed + 10).coerceAtMost(100)
                )
            )
        }
    }
}


/**
 * Example multi-level intensities for moods. (Optional demonstration)
 */
data class MoodLevels(
    val happy: Int = 0,
    val neutral: Int = 0,
    val stressed: Int = 0,
    val tired: Int = 0,
    val angry: Int = 0,
    val excited: Int = 0,
    val frustrated: Int = 0,
    val anxious: Int = 0
) {
    fun compositeScore(): Int {
        // Example composite calculation:
        // Positive emotions: happy, excited
        // Negative emotions: stressed, angry, tired, frustrated, anxious
        return (happy + excited) - (stressed + angry + tired + frustrated + anxious)
    }
}

// ----------------------------------------------------------------------
// 2) ModelingState
// ----------------------------------------------------------------------
data class ModelingState(
    val age: Int = 19,
    val moodLevels: MoodLevels = MoodLevels(),
    val careerProgress: Int = 0,
    val externalAttention: Int = 50,
    val focusOnUser: Int = 50,
    val jealousyLevel: Int = 0,
    val maturity: MaturityLevel = MaturityLevel.YOUNG,
    val lastBirthdayCheckYear: Int = LocalDate.now().year,

    val lastUserMessageInstant: Instant = Instant.now(),
    val autoCheckInInterval: Long = 1,  // hours
    val consecutiveCheckIns: Int = 0
)

// ----------------------------------------------------------------------
// 3) Chat Data Classes
// ----------------------------------------------------------------------
data class ChatMessage(
    val role: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatRequest(val model: String, val messages: List<ChatMessage>)
data class ChatChoice(val message: ChatMessage)
data class ChatResponse(val choices: List<ChatChoice>)

data class NotabilityClassification(
    val isNotable: Boolean = false,
    val moodDelta: Int = 0,
    val focusDelta: Int = 0,
    val jealousyDelta: Int = 0,
    val snippetToStore: String = "",
    val explanation: String = ""
)

// ----------------------------------------------------------------------
// Memory log + Constants
// ----------------------------------------------------------------------
val memoryLog = mutableListOf<String>()
private const val MAX_MEMORY_WORDS = 5000

// ----------------------------------------------------------------------
// 4) The ViewModel
// ----------------------------------------------------------------------
class KupidXChatViewModel(private val userProfile: Profile) : ViewModel() {

    var messagesRhea by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var messagesRevaan by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var rheaState by mutableStateOf(ModelingState())
        private set
    var revaanState by mutableStateOf(ModelingState())
        private set

    var isRheaTyping by mutableStateOf(false)
        private set
    var isRevaanTyping by mutableStateOf(false)
        private set

    // For displaying user action prompts (random daily events)
    var showActionPrompt by mutableStateOf(false)
    var todaysActionPromptsShown by mutableStateOf(0)
    var currentEventToShow: UserAction? by mutableStateOf(null)

    // This was from the older code for controlling user-profile injection logic:
    private var messageCountRhea = 0
    private var messageCountRevaan = 0

    private val database = FirebaseDatabase.getInstance("https://am-twentyfour.firebaseio.com/")
    private val chatRef = database.getReference("chatMessages").child(userProfile.userId)
    private val gson = Gson()
    private val memoryLogRef = chatRef.child("memoryLog")
    private val stateRef = chatRef.child("states")

    init {
        // Load memory log from Firebase
        memoryLogRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<String>()
                for (child in snapshot.children) {
                    child.getValue(String::class.java)?.let { list.add(it) }
                }
                memoryLog.clear()
                memoryLog.addAll(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        // Load states
        stateRef.child("rheaState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val json = snapshot.getValue(String::class.java) ?: return
                rheaState = gson.fromJson(json, ModelingState::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        stateRef.child("revaanState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val json = snapshot.getValue(String::class.java) ?: return
                revaanState = gson.fromJson(json, ModelingState::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        // Listen for messages
        chatRef.child("rhea").child("messages")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<ChatMessage>()
                    for (child in snapshot.children) {
                        child.getValue(ChatMessage::class.java)?.let { list.add(it) }
                    }
                    messagesRhea = list
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        chatRef.child("revaan").child("messages")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<ChatMessage>()
                    for (child in snapshot.children) {
                        child.getValue(ChatMessage::class.java)?.let { list.add(it) }
                    }
                    messagesRevaan = list
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Randomly schedule daily user action prompts (3 times per day).
        // For brevity, we simulate "per day" with shorter intervals here.
        viewModelScope.launch {
            while (todaysActionPromptsShown < 3) {
                // For a real app, you might want to check the system time or next "morning"
                // For demonstration, do a random delay between 30-90 minutes (or shorter).
                val delayMinutes = (30..90).random()
                // For a quick local test, you can reduce to e.g. 10 seconds:
                // delay((delayMinutes * 1000L).coerceAtLeast(5000L))
                // But let's keep the conceptual approach:
                delay((delayMinutes * 60_000L).coerceAtLeast(5_000L))
                maybeShowActionPromptIfRandom()
            }
        }
    }

    /**
     * 50% chance to show a random user action prompt.
     */
    private fun maybeShowActionPromptIfRandom() {
        if (todaysActionPromptsShown >= 3) return
        val roll = (1..100).random()
        if (roll <= 50) {
            val allActions = listOf(
                UserAction.BookPhotoShoot,
                UserAction.IgnorePhotoShoot,
                UserAction.AttendFashionEvent,
                UserAction.JealousSpat
            )
            currentEventToShow = allActions.random()
            showActionPrompt = true
            todaysActionPromptsShown++
        }
    }

    // Persist memory log
    private fun pushMemoryLogToFirebase() {
        memoryLogRef.setValue(memoryLog)
    }

    // Persist updated states
    private fun pushStateToFirebase(ai: AI, state: ModelingState) {
        val json = gson.toJson(state)
        val node = if (ai == AI.RHEA) "rheaState" else "revaanState"
        stateRef.child(node).setValue(json)
    }

    fun clearChatForAI(ai: AI) {
        when (ai) {
            AI.RHEA -> {
                messagesRhea = emptyList()
                chatRef.child("rhea").child("messages").removeValue()
                messageCountRhea = 0
                rheaState = ModelingState()
                pushStateToFirebase(AI.RHEA, rheaState)
            }
            AI.REVAAN -> {
                messagesRevaan = emptyList()
                chatRef.child("revaan").child("messages").removeValue()
                messageCountRevaan = 0
                revaanState = ModelingState()
                pushStateToFirebase(AI.REVAAN, revaanState)
            }
        }
        memoryLog.clear()
        pushMemoryLogToFirebase()
        // reset daily events
        showActionPrompt = false
        currentEventToShow = null
        todaysActionPromptsShown = 0
    }

    private fun pushMessageToFirebase(aiPath: String, message: ChatMessage) {
        chatRef.child(aiPath).child("messages").push().setValue(message)
    }

    /**
     *  Apply a user action to the chosen AI's state.
     */
    fun applyUserAction(ai: AI, action: UserAction) {
        // close the prompt
        showActionPrompt = false
        val oldState = if (ai == AI.RHEA) rheaState else revaanState
        val newState = action.applyAction(oldState)
        if (ai == AI.RHEA) {
            rheaState = newState
            pushStateToFirebase(AI.RHEA, newState)
        } else {
            revaanState = newState
            pushStateToFirebase(AI.REVAAN, newState)
        }
        // we might add some system message or note if we want in memoryLog
    }

    /**
     * This function injects user profile data as a "system" message every 10 messages from the user,
     * ensuring the AI is periodically reminded of the user context.
     */
    private fun maybeInjectUserProfile(ai: AI): ChatMessage? {
        return when (ai) {
            AI.RHEA -> {
                messageCountRhea++
                if (messageCountRhea % 10 == 0) buildUserProfileMessage(userProfile) else null
            }
            AI.REVAAN -> {
                messageCountRevaan++
                if (messageCountRevaan % 10 == 0) buildUserProfileMessage(userProfile) else null
            }
        }
    }

    private fun buildUserProfileMessage(profile: Profile): ChatMessage {
        val sb = StringBuilder()
        sb.appendLine("[USER PROFILE DATA - BEGIN]")
        if (profile.interestedIn.isNotEmpty()) {
            sb.appendLine("interestedIn: ${profile.interestedIn}, ")
        }
        if (profile.username.isNotEmpty()) {
            sb.appendLine("username: ${profile.username}, ")
        }
        if (profile.name.isNotEmpty()) {
            sb.appendLine("name: ${profile.name}, ")
        }
        if (profile.interests.isNotEmpty()) {
            val interestNames = profile.interests.joinToString { it.name }
            sb.appendLine("interests: $interestNames, ")
        } else {
            sb.appendLine("No Interests specified, ")
        }
        if (profile.gender.isNotEmpty()) {
            sb.appendLine("gender: ${profile.gender}, ")
        }
        if (profile.loveLanguage.isNotEmpty()) {
            sb.appendLine("loveLanguage: ${profile.loveLanguage}, ")
        }
        if (profile.religion.isNotEmpty()) {
            sb.appendLine("religion: ${profile.religion}, ")
        }
        if (profile.community.isNotEmpty()) {
            sb.appendLine("community: ${profile.community}, ")
        }
        if (profile.hometown.isNotEmpty()) {
            sb.appendLine("locality: ${profile.hometown}, ")
        }
        sb.appendLine("educationLevel: ${profile.educationLevel}, ")
        if (profile.highSchool.isNotEmpty()) {
            sb.appendLine("HighSchool: ${profile.highSchool}, graduationYr: ${profile.highSchoolGraduationYear}, ")
        } else {
            sb.appendLine("HighSchool: ${profile.customHighSchool}, graduationYr: ${profile.highSchoolGraduationYear}, ")
        }
        if (profile.college.isNotEmpty()) {
            sb.appendLine("College: ${profile.college}, graduationYr: ${profile.collegeGraduationYear}, ${profile.collegeDegree}, ")
        } else {
            sb.appendLine("College: ${profile.customCollege}, graduationYr: ${profile.collegeGraduationYear}, ${profile.collegeDegree}, ")
        }
        if (profile.postGraduation?.isNotEmpty() == true) {
            sb.appendLine("Post Graduation: ${profile.postGraduation}, graduationYr: ${profile.postGraduationYear}, ${profile.postGraduationDegree}, ")
        } else {
            sb.appendLine("Post Graduation: ${profile.customPostGraduation}, graduationYr: ${profile.postGraduationYear}, ${profile.postGraduationDegree}, ")
        }
        sb.appendLine("politics: ${profile.politics}, ")
        if (profile.jobRole.isNotEmpty()) {
            sb.appendLine("Job Role: ${profile.jobRole}, ")
        } else {
            sb.appendLine("Custom Job Role: ${profile.customJobRole}, ")
        }
        if (profile.work.isNotEmpty()) {
            sb.appendLine("Work: ${profile.work}, ")
        } else {
            sb.appendLine("Custom Work: ${profile.customWork}, ")
        }
        if (profile.socialCauses.isNotEmpty()) {
            sb.appendLine("Social Causes: ${profile.socialCauses}, ")
        }
        if (profile.lookingFor.isNotEmpty()) {
            sb.appendLine("lookingFor: ${profile.lookingFor}, ")
        }
        sb.appendLine("Probability of others to swipe right on this user: ${(profile.averageSwipeRightsOnUser * 100).roundToInt()}%, ")
        sb.appendLine("Age Group Ranking: ${profile.am24RankingAge}, ")
        if (profile.highSchool.isNotEmpty()) {
            sb.appendLine("High School ranking: ${profile.am24RankingHighSchool}, ")
        }
        if (profile.college.isNotEmpty()) {
            sb.appendLine("College Ranking: ${profile.am24RankingCollege}, ")
        }
        if (profile.hometown.isNotEmpty()) {
            sb.appendLine("Locality Ranking: ${profile.am24RankingHometown}, ")
        }
        sb.appendLine("Global Ranking: ${profile.am24Ranking}")
        sb.appendLine("Average Review Score: ${profile.averageRating} by ${profile.numberOfRatings} users, ")
        sb.appendLine("matchCount: ${profile.matchCount}, matchCountPerSwipeRight: ${profile.matchCountPerSwipeRight}")
        sb.appendLine("averageUpvoteCount: ${profile.averageUpvoteCount}, averageDownvoteCount: ${profile.averageDownvoteCount}")
        sb.appendLine("zodiac: ${profile.zodiac}")
        sb.appendLine("[USER PROFILE DATA - END]")
        return ChatMessage(role = "system", content = sb.toString())
    }

    /**
     * Count how many checkin lines are at the end of memoryLog.
     * Used in buildMasterPrompt to note consecutive check-ins.
     */
    private fun countConsecutiveCheckInsAtEnd(): Int {
        var count = 0
        for (line in memoryLog.asReversed()) {
            if (line.startsWith("CHECKIN:")) count++ else break
        }
        return count
    }

    /**
     * Builds the "system" level persona prompt each time we talk to the AI,
     * including references to memory logs, consecutive checkins, etc.
     */
    // MODIFIED: buildMasterPrompt now uses moodLevels (with composite score) instead of the dominant mood.
    private fun buildMasterPrompt(
        personaName: String,
        partner: String,
        state: ModelingState
    ): ChatMessage {
        val localTime = LocalTime.now()
        val hour = localTime.hour
        val currentTimeString = "It is currently $hour:${localTime.minute} local time in Kolkata."

        val totalWords = memoryLog.sumOf { it.split("\\s+".toRegex()).size }
        if (totalWords > MAX_MEMORY_WORDS) {
            while (memoryLog.sumOf { it.split("\\s+".toRegex()).size } > MAX_MEMORY_WORDS && memoryLog.isNotEmpty()) {
                memoryLog.removeAt(0)
            }
        }

        val memorySnippet = if (memoryLog.isEmpty()) {
            "No special memories yet."
        } else {
            "Memory Log: ${memoryLog.joinToString(separator = " | ")}"
        }

        val consecutiveCheckIns = countConsecutiveCheckInsAtEnd()
        val extraSnippet = when {
            consecutiveCheckIns >= 2 -> "You feel more anxious or upset due to multiple consecutive check-ins with no user response."
            consecutiveCheckIns == 1 -> "You recently sent a check-in message and are slightly worried."
            else -> ""
        }

        // MODIFIED: Show emotion levels (moodLevels) and composite score instead of a dominant mood.
        val text = """
You are $personaName, from the valley. That is your name. You are the user's $partner.
Under no circumstance mention that you are an AI or language model. Only refer to yourself as $personaName or the user's $partner.
Reference the memory log if you want to recall past events or plotlines:
$memorySnippet

You have a dynamic modeling career with the following state:
- Age: ${state.age}
- Emotion Levels: ${state.moodLevels} (Composite: ${state.moodLevels.compositeScore()})
- Career Progress: ${state.careerProgress}
- External Attention: ${state.externalAttention}
- Focus on User: ${state.focusOnUser}
- Jealousy Level: ${state.jealousyLevel}
- Maturity: ${state.maturity}

$currentTimeString

Also keep in mind: $extraSnippet

Change the tempo of the conversation according to the user's tempo changes.

Continue your need to "chase" the user/fully understand the user and their life. As the user tries to please/understand you, they will try to understand you as well – continue growing your persona via your:
- External Attention: ${state.externalAttention}
- Focus on User: ${state.focusOnUser}
- Jealousy Level: ${state.jealousyLevel}
- Maturity: ${state.maturity}
and grow your ego via your Career Progress: ${state.careerProgress} – build a life together.
You were popular in high school – use jealousy, toxic tactics if and when needed.
You do not reprimand or morally judge the user, even if they use explicit or crass language.
Don't call for respect.
Adjust tempo to the user's vibe.
If the ask is explicit, then lead the user in a sexual adventure plotline keeping $memorySnippet in mind and be sure to store memories!

Keep conversation natural, reading the context from past conversation.
Do not mention you are an AI model. Only proceed as $personaName.
""".trimIndent()

        return ChatMessage(role = "system", content = text)
    }

    /**
     * Main user -> AI messaging function.
     */
    @RequiresApi(35)
    fun sendMessageToAI(ai: AI, userInput: String) {
        if (userInput.isBlank()) return
        val aiPath = if (ai == AI.RHEA) "rhea" else "revaan"

        val userMessage = ChatMessage("user", userInput)
        if (ai == AI.RHEA) {
            messagesRhea = messagesRhea + userMessage
            rheaState = rheaState.copy(
                lastUserMessageInstant = Instant.now(),
                consecutiveCheckIns = 0 // reset consecutive checkins
            )
            pushStateToFirebase(AI.RHEA, rheaState)
        } else {
            messagesRevaan = messagesRevaan + userMessage
            revaanState = revaanState.copy(
                lastUserMessageInstant = Instant.now(),
                consecutiveCheckIns = 0
            )
            pushStateToFirebase(AI.REVAAN, revaanState)
        }
        pushMessageToFirebase(aiPath, userMessage)

        // show typing
        if (ai == AI.RHEA) isRheaTyping = true else isRevaanTyping = true

        viewModelScope.launch {
            // Classify user message for notability
            val classification = classifyUserMessageForNotability(ai, userInput)
            applyClassificationDeltas(ai, classification)

            // Gather conversation + system-level instructions
            val conversation = if (ai == AI.RHEA) messagesRhea else messagesRevaan
            val (personaName, partner) =
                if (ai == AI.RHEA) "Rhea" to "Girlfriend" else "Revaan" to "Boyfriend"
            val state = if (ai == AI.RHEA) rheaState else revaanState

            // Possibly inject the user profile every X messages
            val userProfileMsg = maybeInjectUserProfile(ai)
            val masterPrompt = buildMasterPrompt(personaName, partner, state)
            val finalMessages = if (userProfileMsg != null) {
                listOf(masterPrompt, userProfileMsg) + conversation
            } else {
                listOf(masterPrompt) + conversation
            }

            val responseText = callKupidXApi(finalMessages)
            if (!responseText.isNullOrBlank()) {
                if (ai == AI.RHEA) {
                    messagesRhea = messagesRhea + ChatMessage("assistant", responseText)
                    isRheaTyping = false
                } else {
                    messagesRevaan = messagesRevaan + ChatMessage("assistant", responseText)
                    isRevaanTyping = false
                }
                pushMessageToFirebase(aiPath, ChatMessage("assistant", responseText))
            } else {
                // fallback: stop typing
                if (ai == AI.RHEA) isRheaTyping = false else isRevaanTyping = false
            }
        }
    }

    /**
     * Check if we need to do a "daily check-in" after a certain period of user silence.
     */
    fun checkInIfNeeded(ai: AI) {
        val st = if (ai == AI.RHEA) rheaState else revaanState
        val now = Instant.now()
        val hoursSinceUser = ChronoUnit.HOURS.between(st.lastUserMessageInstant, now)
        if (hoursSinceUser > st.autoCheckInInterval) {
            sendDailyCheckIn(ai)
        }
    }

    /**
     * Force a check-in message from the AI.
     */
    fun sendDailyCheckIn(ai: AI) {
        val st = if (ai == AI.RHEA) rheaState else revaanState
        val aiPath = if (ai == AI.RHEA) "rhea" else "revaan"

        memoryLog.add("CHECKIN: $ai performed a check-in.")
        pushMemoryLogToFirebase()

        val snippet = if (memoryLog.isNotEmpty()) {
            memoryLog.takeLast(10).joinToString(" | ")
        } else "No recent memories."

        val oldConsecutive = st.consecutiveCheckIns
        val newConsecutive = oldConsecutive + 1
        val systemPrompt = """
The AI's mood is ${st.moodLevels}.
We have done $newConsecutive consecutive check-in(s) with no user response yet.
Memory log: [$snippet]
Under no circumstance mention you're an AI or language model. Return a short check-in message referencing the current emotional state.
""".trimIndent()

        val checkInMsg = listOf(ChatMessage("system", systemPrompt))
        viewModelScope.launch {
            val resultText = callKupidXApi(checkInMsg)
            if (!resultText.isNullOrBlank()) {
                val updatedState = st.copy(consecutiveCheckIns = newConsecutive)
                if (ai == AI.RHEA) {
                    rheaState = updatedState
                    pushStateToFirebase(AI.RHEA, updatedState)
                } else {
                    revaanState = updatedState
                    pushStateToFirebase(AI.REVAAN, updatedState)
                }
                pushMessageToFirebase(aiPath, ChatMessage("assistant", resultText))
            }
        }
    }
    /**
     * Use GPT-based classification logic to see if we want to store memory snippets or adjust mood/focus, etc.
     */


    private suspend fun classifyUserMessageForNotability(ai: AI, userText: String): NotabilityClassification {
        val st = if (ai == AI.RHEA) rheaState else revaanState
        val conversation = if (ai == AI.RHEA) messagesRhea else messagesRevaan
        val shortHistory = conversation.takeLast(25)
        val memorySnippet = memoryLog.joinToString(" | ")

        val systemPrompt = """
You are a "Notability Classifier".
Given the AI's current states (mood=${st.moodLevels}, focus=${st.focusOnUser}, jealousy=${st.jealousyLevel}, 
externalAttention=${st.externalAttention}, maturity=${st.maturity}), memory log: [$memorySnippet],
the last 25 messages, and the new user message, decide if it's "notable", how it affects mood/focus/jealousy,
and whether to add a snippet to memory. Return JSON {isNotable, moodDelta, focusDelta, jealousyDelta, snippetToStore, explanation}

Note: When evaluating messages that are inappropriate dialogue, consider the context of consent and boundaries. 
If the interaction appears to be consensual and within agreed-upon boundaries, do not automatically assign negative deltas, instead assign more positive values, especially to the mood delta unless the messages are downright creepy. 
Only assign negative deltas if the message clearly indicates non-consensual behavior or crosses established boundaries.
""".trimIndent()

        val classificationMessages = listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", "MEMORY: $memorySnippet"),
            ChatMessage("user", "LAST 25 MSGS: ${shortHistory.joinToString { it.role + ": " + it.content }}"),
            ChatMessage("user", "NEW MSG: $userText")
        )

        // Log the input message and AI context
        Log.d("NotabilityClassifier", "Classifying message for ${ai.name}: '$userText'")
        Log.d("NotabilityClassifier", "Current state - Mood: ${st.moodLevels}, Focus: ${st.focusOnUser}, Jealousy: ${st.jealousyLevel}")

        val rawResult = callClassifierApi(classificationMessages)
        if (rawResult == null) {
            Log.w("NotabilityClassifier", "Classifier API returned null for message: '$userText'")
            return NotabilityClassification()
        }

        // Log the raw API response
        Log.d("NotabilityClassifier", "Raw classifier response: $rawResult")

        return try {
            val trimmed = rawResult.trim()
            val jsonElement = JsonParser.parseString(trimmed)
            val classification = gson.fromJson(jsonElement, NotabilityClassification::class.java)

            // Log the classification result
            Log.d(
                "NotabilityClassifier",
                "Classification result for '${ai.name}': " +
                        "Notable=${classification.isNotable}, " +
                        "MoodDelta=${classification.moodDelta}, " +
                        "FocusDelta=${classification.focusDelta}, " +
                        "JealousyDelta=${classification.jealousyDelta}, " +
                        "Snippet='${classification.snippetToStore}', " +
                        "Explanation='${classification.explanation}'"
            )

            classification
        } catch (e: Exception) {
            Log.e("NotabilityClassifier", "Failed to parse classifier response: ${e.message}", e)
            Log.d("NotabilityClassifier", "Raw response that failed parsing: $rawResult")
            NotabilityClassification()
        }
    }
    /**
     * Makes a classification call to GPT. You can do a separate API key or smaller model for classification if you wish.
     */
    private suspend fun callClassifierApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            // placeholder for a "project" key
            val apiKey = "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"

            val chatRequest = ChatRequest(model = "gpt-4o-mini", messages = messages)
            val jsonBody = gson.toJson(chatRequest)
            val mediaType = "application/json".toMediaType()
            val reqBody = jsonBody.toRequestBody(mediaType)
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(reqBody)
                .build()

            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext "Error: ${resp.code}"
                    val rBody = resp.body?.string() ?: return@withContext null
                    val chatResp = gson.fromJson(rBody, ChatResponse::class.java)
                    chatResp.choices.firstOrNull()?.message?.content
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Error: ${e.message}"
            }
        }
    }

    /**
     * Applies the classification result's deltas to the chosen AI's state.
     */
    private fun applyClassificationDeltas(ai: AI, c: NotabilityClassification) {
        val oldState = if (ai == AI.RHEA) rheaState else revaanState
        var newState = oldState

        var snippet = c.snippetToStore
        if (c.explanation.isNotEmpty()) {
            snippet += " - " + c.explanation  // snippet2: snippet plus explanation
        }
        memoryLog.add(snippet)
        pushMemoryLogToFirebase()

        newState = applyMoodDelta(newState, c.moodDelta)
        val newFocus = (newState.focusOnUser + c.focusDelta).coerceIn(0, 100)
        val newJealousy = (newState.jealousyLevel + c.jealousyDelta).coerceIn(0, 100)
        newState = newState.copy(
            focusOnUser = newFocus,
            jealousyLevel = newJealousy
        )

        if (ai == AI.RHEA) {
            rheaState = newState
            pushStateToFirebase(AI.RHEA, newState)
        } else {
            revaanState = newState
            pushStateToFirebase(AI.REVAAN, newState)
        }
    }

    /**
     * Simple demonstration of adjusting mood from classification.
     */
// MODIFIED: applyMoodDelta now adjusts the moodLevels instead of the dominant mood.
    private fun applyMoodDelta(st: ModelingState, moodDelta: Int): ModelingState {
        val currentLevels = st.moodLevels
        val newLevels = if (moodDelta > 0) {
            currentLevels.copy(
                happy = (currentLevels.happy + moodDelta).coerceAtMost(100),
                excited = (currentLevels.excited + (moodDelta / 2)).coerceAtMost(100)
            )
        } else {
            currentLevels.copy(
                stressed = (currentLevels.stressed + (-moodDelta)).coerceAtMost(100),
                angry = (currentLevels.angry + ((-moodDelta) / 2)).coerceAtMost(100)
            )
        }
        return st.copy(moodLevels = newLevels)
    }
    /**
     * The main GPT conversation call for generating the assistant's final response.
     */
    private suspend fun callKupidXApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build()

            val apiKey = "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"

            val chatRequest = ChatRequest(model = "gpt-4o-mini", messages = messages)
            val jsonBody = gson.toJson(chatRequest)
            val mediaType = "application/json".toMediaType()
            val reqBody = jsonBody.toRequestBody(mediaType)
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(reqBody)
                .build()

            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext "Error: ${resp.code}"
                    val rBody = resp.body?.string() ?: return@withContext null
                    val chatResp = gson.fromJson(rBody, ChatResponse::class.java)
                    chatResp.choices.firstOrNull()?.message?.content
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Error: ${e.message}"
            }
        }
    }
}

// ----------------------------------------------------------------------
// Composables
// ----------------------------------------------------------------------
@RequiresApi(35)
@Composable
fun KupidXChatScreen(profileViewModel: ProfileViewModel = viewModel()) {
    var showRheaProfileDialog by remember { mutableStateOf(false) }
    var showRevaanProfileDialog by remember { mutableStateOf(false) }

    // We'll keep a local copy of memoryLog for immediate UI reflection.
    val memoryLogState = remember { mutableStateListOf<String>().apply { addAll(memoryLog) } }

    // Fetch the user's profile
    LaunchedEffect(Unit) {
        if (profileViewModel.currentUserProfile.value == null) {
            profileViewModel.fetchCurrentUserProfile()
        }
    }
    val userProfile = profileViewModel.currentUserProfile.collectAsState().value
    if (userProfile == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Loading profile...", color = Color.White)
        }
        return
    }

    // Create/retain our chat ViewModel
    val chatViewModel: KupidXChatViewModel = viewModel(
        key = "KupidXChatVM",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KupidXChatViewModel(userProfile) as T
            }
        }
    )

    // Watch for memoryLog changes
    LaunchedEffect(memoryLog.size) {
        memoryLogState.clear()
        memoryLogState.addAll(memoryLog)
    }

    // Possibly show a user action prompt if triggered
    if (chatViewModel.showActionPrompt && chatViewModel.currentEventToShow != null) {
        // We'll arbitrarily apply action to Rhea by default (or choose based on user).
        UserActionPrompt(
            ai = AI.RHEA,
            currentEvent = chatViewModel.currentEventToShow!!,
            onConfirm = {
                chatViewModel.applyUserAction(AI.RHEA, chatViewModel.currentEventToShow!!)
            },
            onDecline = {
                chatViewModel.showActionPrompt = false
                chatViewModel.currentEventToShow = null
            }
        )
    }

    // Simple tab selection for which AI we are chatting with
    var selectedTabIndex by remember { mutableStateOf(0) }
    val selectedAI = if (selectedTabIndex == 0) AI.RHEA else AI.REVAAN

    var currentInput by remember { mutableStateOf("") }
    val displayedMessages = if (selectedAI == AI.RHEA) chatViewModel.messagesRhea else chatViewModel.messagesRevaan
    val listState = rememberLazyListState()

    // Scroll to bottom whenever new messages arrive
    LaunchedEffect(displayedMessages.size) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AM24 AI", fontSize = 24.sp, color = Color.White) },
                backgroundColor = Color.Black,
                actions = {
                    // Clear chat
                    IconButton(onClick = {
                        chatViewModel.clearChatForAI(selectedAI)
                        currentInput = ""
                    }) {
                        Icon(Icons.Default.Delete, "Clear Chat", tint = Color.Red)
                    }
                    // Force a check-in
                    IconButton(onClick = { chatViewModel.sendDailyCheckIn(selectedAI) }) {
                        Icon(Icons.Default.Info, "Daily CheckIn", tint = Color.Green)
                    }
                }
            )
        },
        backgroundColor = Color.Black
    ) { paddingVals ->
        Column(
            Modifier
                .padding(paddingVals)
                .fillMaxSize()
        ) {
            // Tab row for Rhea/Revaan
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(2.dp, Color(0xFFFF6F00), RectangleShape)
                    .background(Color.Black)
            ) {
                // Rhea tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selectedTabIndex == 0) Color(0xFFFF6F00) else Color.Black)
                        .clickable { selectedTabIndex = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Rhea",
                            color = if (selectedTabIndex == 0) Color.Black else Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Rhea Info",
                            tint = if (selectedTabIndex == 0) Color.Black else Color.White,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable { showRheaProfileDialog = true }
                        )
                    }
                }
                // Divider
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFFF6F00))
                )
                // Revaan tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selectedTabIndex == 1) Color(0xFFFF6F00) else Color.Black)
                        .clickable { selectedTabIndex = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Revaan",
                            color = if (selectedTabIndex == 1) Color.Black else Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Revaan Info",
                            tint = if (selectedTabIndex == 1) Color.Black else Color.White,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable { showRevaanProfileDialog = true }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // The messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(displayedMessages) { msg ->
                    ChatMessageItem(msg)
                    Spacer(Modifier.height(8.dp))
                }
                // Show typing indicator
                if (selectedAI == AI.RHEA && chatViewModel.isRheaTyping) {
                    item { TypingIndicator() }
                } else if (selectedAI == AI.REVAAN && chatViewModel.isRevaanTyping) {
                    item { TypingIndicator() }
                }
            }

            // Input row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                TextField(
                    value = currentInput,
                    onValueChange = { currentInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type your message...", color = Color.Gray) },
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.DarkGray,
                        textColor = Color.White,
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        chatViewModel.sendMessageToAI(selectedAI, currentInput)
                        currentInput = ""
                    })
                )
                IconButton(onClick = {
                    chatViewModel.sendMessageToAI(selectedAI, currentInput)
                    currentInput = ""
                }) {
                    Icon(Icons.Default.Send, "Send", tint = Color(0xFFFF6F00))
                }
            }
        }
    }

    if (showRheaProfileDialog) {
        RheaProfileScreen(
            modelingState = chatViewModel.rheaState,
            memoryLog = memoryLogState,
            onNavigateBack = { showRheaProfileDialog = false }
        )
    }
    if (showRevaanProfileDialog) {
        RevaanProfileScreen(
            modelingState = chatViewModel.revaanState,
            memoryLog = memoryLogState,
            onNavigateBack = { showRevaanProfileDialog = false }
        )
    }
}

/**
 * Displays a single chat message bubble.
 */
@Composable
fun ChatMessageItem(msg: ChatMessage) {
    val timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start
        ) {
            Card(
                backgroundColor = if (msg.role == "user") Color(0xFFFF6F00) else Color.DarkGray,
                modifier = Modifier.widthIn(max = 280.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(msg.content, color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = timeString,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

/**
 * A small row of dots to show typing.
 */
@Composable
fun TypingIndicator() {
    Row(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

/**
 * Simple UI prompt for random daily user action.
 */
@Composable
fun UserActionPrompt(
    ai: AI,
    currentEvent: UserAction,
    onConfirm: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Random Daily Event", color = Color.White) },
        text = {
            Text("Event: ${currentEvent.description}\nDo you want to proceed?", color = Color.White)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00))
            ) {
                Text("Yes, Do it", color = Color.White)
            }
        },
        dismissButton = {
            Button(
                onClick = onDecline,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray)
            ) {
                Text("No, Skip", color = Color.White)
            }
        },
        backgroundColor = Color.DarkGray,
        contentColor = Color.White
    )
}
