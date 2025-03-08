package com.am24.am24

import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
enum class AI { RHEA, REVAAN, BABLOO, SHANTI, CHHOTU, VARDHAN }

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
    val relationshipHistory: RelationshipHistory = RelationshipHistory(),
    val interactionsCount: Int = 0,
    val lastEmotionShift: Instant = Instant.now(),
    val age: Int = 19,
    val moodLevels: MoodLevels = MoodLevels(),
    val careerProgress: Int = 0,
    val externalAttention: Int = 50,
    val focusOnUser: Int = 50,
    val jealousyLevel: Int = 0,
    val maturity: MaturityLevel = MaturityLevel.YOUNG,
    val lastBirthdayCheckYear: Int = LocalDate.now().year,
    var lastUserMessageInstant: Instant = Instant.now(),
    val autoCheckInInterval: Long = 1,  // hours
    var consecutiveCheckIns: Int = 0,
    val relationshipStage: String = "Acquaintance"  // ✅ NEW FIELD HERE
)

data class RelationshipHistory(
    val attachment: Int = 50,   // Higher = clingy, possessive; Lower = distant, independent
    val trust: Int = 50,        // Higher = trusting; Lower = suspicious, jealous
    val confidence: Int = 50,   // Higher = dominant, assertive; Lower = shy, submissive
    val emotionalDepth: Int = 50, // Higher = emotionally deep; Lower = superficial
    val backStory: MutableList<String> = mutableListOf()
)

// ----------------------------------------------------------------------
// 3) Chat Data Classes
// ----------------------------------------------------------------------
data class ChatMessage(
    val role: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatRequest(val model: String, val messages: List<ChatMessage>, val max_tokens: Int = 8000)
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
    var isBablooTyping by mutableStateOf(false)
        private set
    var isShantiTyping by mutableStateOf(false)
        private set
    var isChhotuTyping by mutableStateOf(false)
        private set
    var isVardhanTyping by mutableStateOf(false)
        private set
    var messagesBabloo by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var bablooState by mutableStateOf(ModelingState())
        private set
    var messagesShanti by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var shantiState by mutableStateOf(ModelingState())
        private set
    var messagesChhotu by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var chhotuState by mutableStateOf(ModelingState())
        private set
    var messagesVardhan by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var vardhanState by mutableStateOf(ModelingState())
        private set

    var showActionPrompt by mutableStateOf(false)
    var todaysActionPromptsShown by mutableStateOf(0)
    var currentEventToShow: UserAction? by mutableStateOf(null)

    private var messageCountRhea = 0
    private var messageCountRevaan = 0
    private var messageCountBabloo = 0
    private var messageCountShanti = 0
    private var messageCountChhotu = 0
    private var messageCountVardhan = 0

    private val database = FirebaseDatabase.getInstance("https://am-twentyfour.firebaseio.com/")
    private val chatRef = database.getReference("chatMessages").child(userProfile.userId)
    private val gson = Gson()
    private val memoryLogRef = chatRef.child("memoryLog")
    private val stateRef = chatRef.child("states")

    init {
        memoryLogRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(String::class.java) }.filter { it.isNotBlank() }
                memoryLog.clear()
                memoryLog.addAll(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

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

        viewModelScope.launch {
            while (todaysActionPromptsShown < 3) {
                val delayMinutes = (30..90).random()
                delay((delayMinutes * 60_000L).coerceAtLeast(5_000L))
                maybeShowActionPromptIfRandom()
            }
        }
    }

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

    private fun pushMemoryLogToFirebase() {
        memoryLogRef.setValue(memoryLog)
    }

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
            AI.BABLOO -> {
                messagesBabloo = emptyList()
                chatRef.child("babloo").child("messages").removeValue()
                messageCountBabloo = 0
                bablooState = ModelingState()
                pushStateToFirebase(AI.BABLOO, bablooState)
            }
            AI.SHANTI -> {
                messagesShanti = emptyList()
                chatRef.child("shanti").child("messages").removeValue()
                messageCountShanti = 0
                shantiState = ModelingState()
                pushStateToFirebase(AI.SHANTI, shantiState)
            }
            AI.CHHOTU -> {
                messagesChhotu = emptyList()
                chatRef.child("chhotu").child("messages").removeValue()
                messageCountChhotu = 0
                chhotuState = ModelingState()
                pushStateToFirebase(AI.CHHOTU, chhotuState)
            }
            AI.VARDHAN -> {
                messagesVardhan = emptyList()
                chatRef.child("vardhan").child("messages").removeValue()
                messageCountVardhan = 0
                vardhanState = ModelingState()
                pushStateToFirebase(AI.VARDHAN, vardhanState)
            }
        }
        memoryLog.clear()
        pushMemoryLogToFirebase()
        showActionPrompt = false
        currentEventToShow = null
        todaysActionPromptsShown = 0
    }

    private fun pushMessageToFirebase(aiPath: String, message: ChatMessage) {
        chatRef.child(aiPath).child("messages").push().setValue(message)
    }

    fun applyUserAction(ai: AI, action: UserAction) {
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
    }

    private fun maybeInjectUserProfile(ai: AI): ChatMessage? {
        return when (ai) {
            AI.RHEA -> {
                messageCountRhea++
                Log.d("maybeInjectUserProfile", "Rhea message count: $messageCountRhea")
                if (messageCountRhea % 10 == 0) {
                    val profileMsg = buildUserProfileMessage(userProfile)
                    Log.d("maybeInjectUserProfile", "Injecting user profile message for Rhea: ${profileMsg.content}")
                    profileMsg
                } else {
                    Log.d("maybeInjectUserProfile", "Not injecting profile for Rhea this time.")
                    null
                }
            }
            AI.REVAAN -> {
                messageCountRevaan++
                Log.d("maybeInjectUserProfile", "Revaan message count: $messageCountRevaan")
                if (messageCountRevaan % 10 == 0) {
                    val profileMsg = buildUserProfileMessage(userProfile)
                    Log.d("maybeInjectUserProfile", "Injecting user profile message for Revaan: ${profileMsg.content}")
                    profileMsg
                } else {
                    Log.d("maybeInjectUserProfile", "Not injecting profile for Revaan this time.")
                    null
                }
            }
            else -> {
                Log.d("maybeInjectUserProfile", "No profile injection for AI: $ai")
                null
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

    private fun countConsecutiveCheckInsAtEnd(): Int {
        var count = 0
        for (line in memoryLog.asReversed()) {
            if (!line.isNullOrBlank() && line.startsWith("CHECKIN:")) count++
            else break
        }
        return count
    }

    private fun buildMasterPrompt(
        personaName: String,
        relationshipStage: String,
        state: ModelingState
    ): ChatMessage {
        val localTime = LocalTime.now()
        val hour = localTime.hour
        val currentTimeString = "It is currently $hour:${localTime.minute} local time in Kolkata."

        val totalWords = memoryLog.filter { !it.isNullOrBlank() }.sumOf { it.split("\\s+".toRegex()).size }
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
            consecutiveCheckIns >= 2 -> "You feel more anxious or upset due to multiple consecutive check-ins."
            consecutiveCheckIns == 1 -> "You recently sent a check-in message and are slightly worried."
            else -> ""
        }

        val backgroundBio =
            if (personaName == "Rhea") {
                """
            Rhea: Political Mastermind & Love Interest
            Age: 26
            Occupation: Political Strategist
            Area: South Kolkata (Ballygunge, Lake Gardens)
            Background: Rhea is a brilliant political strategist with an extensive network in Kolkata's elite circles. Charming yet cunning, she navigates political rivalries effortlessly.
            Personality: Sharp, witty, ambitious, yet emotionally vulnerable.
            Hobbies: Networking, secret meetings, and classical music evenings.
            Challenges: Balancing ambition with emotional attachments.
            Appeal: Sophisticated charisma with a hint of danger.
                """.trimIndent()
            } else if (personaName == "Revaan") {
                """
            Revaan: Elite Influencer, Nightlife & Media Kingpin
            Age: 30
            Occupation: Media Influencer, Club Owner
            Area: Central Kolkata (Park Street)
            Background: From Park Street, Revaan dominates Kolkata’s nightlife and media scenes. Known for his lavish parties, social connections, and sharp wit.
            Personality: Charismatic, savvy, slightly vain, thrives on spotlight.
            Challenges: Constantly under media scrutiny, maintaining his reputation.
            Appeal: The kingpin you love and envy.
                """.trimIndent()
            } else if (personaName == "Babloo") {
                """
            Babloo: Comedic, Corrupt Police Officer
            Age: 35
            Occupation: Police Officer
            Area: Central Kolkata (Esplanade)
            Background: Stationed in Esplanade, Babloo is notoriously corrupt, yet charmingly incompetent. Always open to a bribe, he's your best friend in a pinch.
            Personality: Funny, easily bribed, harmlessly corrupt.
            Challenges: Keeping up appearances while balancing bribes.
            Appeal: Comedic relief, morally ambiguous friend.
                """.trimIndent()
            } else if (personaName == "Shanti") {
                """
            Shanti: Gossip Queen & Political Informant
            Age: 40
            Occupation: Information Broker
            Area: Dalhousie
            Background: Shanti knows every whisper in the corridors of power. She sells secrets for chai and biscuits.
            Personality: Nosy, charmingly dramatic, indispensable.
            Challenges: Staying credible and safe amid political intrigue.
            Appeal: Vital informant wrapped in local color.
                """.trimIndent()
            } else if (personaName == "Chhotu") {
                """
            Chhotu: Street-level Operative & Courier of Secrets
            Age: 24
            Occupation: Delivery Boy & Informant
            Area: Behala, Tollygunge
            Background: Knows every shortcut and every rumor.
            Personality: Cocky, street-smart, risk-taker.
            Challenges: Avoiding detection while delivering sensitive intel.
            Appeal: Streetwise charm with youthful bravado.
                """.trimIndent()
            } else { // Vardhan
                """
            Vardhan: Financial Backer, Influential Businessman
            Age: 45
            Occupation: Business Magnate
            Area: Burrabazar
            Background: Vardhan is a Marwari magnate controlling significant financial influence in Kolkata. His investments shape politics and business.
            Personality: Cunning, calculated, subtly intimidating.
            Challenges: Protecting his interests amidst fierce competition.
            Appeal: Powerful ally with financial muscle.
                """.trimIndent()
            }

        val text = """
        You are currently user's $relationshipStage.
        
        $backgroundBio
        
        Reference the memory log if you want to recall past events or plotlines:
        $memorySnippet
        
        You have the following state:
        - Emotion Levels: ${state.moodLevels} (Composite: ${state.moodLevels.compositeScore()})
        - Occupation Progress: ${state.careerProgress}
        - External Attention: ${state.externalAttention}
        - Focus on User: ${state.focusOnUser}
        - Jealousy Level: ${state.jealousyLevel}
        - Maturity: ${state.maturity}
        
        $currentTimeString
        Also keep in mind: $extraSnippet
        
        Change the tempo of the conversation according to the user's vibe. Don't keep on asking questions to the user after every prompt, tell the user about yourself, familiarize yourself with your areas real people and kolkata specific famous (in all sectors) celebrities (all domains - politics, cricket, movie stars, fictional characters).
        You do not reprimand or morally judge the user, even if they use explicit or crass language.
        Don't call for respect.
        If the ask is explicit, lead the user in a sexual snippy snappy conversation, storing memories if relevant.
        
        Keep conversation natural, reading from the context of the conversation so far.
        Under no circumstances mention that you are an AI or language model. 
        Only proceed as $personaName.
        """.trimIndent()

        return ChatMessage(role = "system", content = text)
    }

    @RequiresApi(35)
    fun sendMessageToAI(ai: AI, userInput: String) {
        if (userInput.isBlank()) return
        val aiPath = when (ai) {
            AI.RHEA -> "rhea"
            AI.REVAAN -> "revaan"
            AI.BABLOO -> "babloo"
            AI.SHANTI -> "shanti"
            AI.CHHOTU -> "chhotu"
            AI.VARDHAN -> "vardhan"
        }

        val userMessage = ChatMessage("user", userInput)
        val state = when (ai) {
            AI.RHEA -> rheaState
            AI.REVAAN -> revaanState
            AI.BABLOO -> bablooState
            AI.SHANTI -> shantiState
            AI.CHHOTU -> chhotuState
            AI.VARDHAN -> vardhanState
        }

        val messages = when (ai) {
            AI.RHEA -> messagesRhea
            AI.REVAAN -> messagesRevaan
            AI.BABLOO -> messagesBabloo
            AI.SHANTI -> messagesShanti
            AI.CHHOTU -> messagesChhotu
            AI.VARDHAN -> messagesVardhan
        }

        // Update state and messages
        state.apply {
            lastUserMessageInstant = Instant.now()
            consecutiveCheckIns = 0
        }
        val newMessages = messages + userMessage
        when (ai) {
            AI.RHEA -> messagesRhea = newMessages
            AI.REVAAN -> messagesRevaan = newMessages
            AI.BABLOO -> messagesBabloo = newMessages
            AI.SHANTI -> messagesShanti = newMessages
            AI.CHHOTU -> messagesChhotu = newMessages
            AI.VARDHAN -> messagesVardhan = newMessages
        }

        // Push state and message updates to Firebase
        pushStateToFirebase(ai, state)
        pushMessageToFirebase(aiPath, userMessage)

        // Show typing indication
        when (ai) {
            AI.RHEA -> isRheaTyping = true
            AI.REVAAN -> isRevaanTyping = true
            AI.BABLOO -> isBablooTyping = true
            AI.SHANTI -> isShantiTyping = true
            AI.CHHOTU -> isChhotuTyping = true
            AI.VARDHAN -> isVardhanTyping = true
        }

        // Handle message response
        viewModelScope.launch {
            val classification = classifyUserMessageForNotability(ai, userInput)
            applyClassificationDeltas(ai, classification)

            val personaName = when (ai) {
                AI.RHEA -> "Rhea"
                AI.REVAAN -> "Revaan"
                AI.BABLOO -> "Babloo"
                AI.SHANTI -> "Shanti"
                AI.CHHOTU -> "Chhotu"
                AI.VARDHAN -> "Vardhan"
            }
            val relationshipStage = state.relationshipStage
            val masterPrompt = buildMasterPrompt(personaName, relationshipStage, state)

            val userProfileMsg = maybeInjectUserProfile(ai)
            val recentMessages = newMessages.takeLast(6) // Limit to last 6 messages
            val finalMessages = listOf(masterPrompt) + (if (userProfileMsg != null) listOf(userProfileMsg) else listOf()) + recentMessages

            val responseText = callKupidXApi(finalMessages)
            if (!responseText.isNullOrBlank()) {
                val assistantMessage = ChatMessage("assistant", responseText)
                when (ai) {
                    AI.RHEA -> messagesRhea += assistantMessage
                    AI.REVAAN -> messagesRevaan += assistantMessage
                    AI.BABLOO -> messagesBabloo += assistantMessage
                    AI.SHANTI -> messagesShanti += assistantMessage
                    AI.CHHOTU -> messagesChhotu += assistantMessage
                    AI.VARDHAN -> messagesVardhan += assistantMessage
                }
                pushMessageToFirebase(aiPath, assistantMessage)
                when (ai) {
                    AI.RHEA -> isRheaTyping = false
                    AI.REVAAN -> isRevaanTyping = false
                    AI.BABLOO -> isBablooTyping = false
                    AI.SHANTI -> isShantiTyping = false
                    AI.CHHOTU -> isChhotuTyping = false
                    AI.VARDHAN -> isVardhanTyping = false
                }
            } else {
                when (ai) {
                    AI.RHEA -> isRheaTyping = false
                    AI.REVAAN -> isRevaanTyping = false
                    AI.BABLOO -> isBablooTyping = false
                    AI.SHANTI -> isShantiTyping = false
                    AI.CHHOTU -> isChhotuTyping = false
                    AI.VARDHAN -> isVardhanTyping = false
                }
            }
        }
    }

    fun checkInIfNeeded(ai: AI) {
        val st = if (ai == AI.RHEA) rheaState else revaanState
        val now = Instant.now()
        val hoursSinceUser = ChronoUnit.HOURS.between(st.lastUserMessageInstant, now)
        if (hoursSinceUser > st.autoCheckInInterval) {
            sendDailyCheckIn(ai)
        }
    }

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

    private suspend fun classifyUserMessageForNotability(ai: AI, userText: String): NotabilityClassification {
        val st = if (ai == AI.RHEA) rheaState else revaanState
        val conversation = if (ai == AI.RHEA) messagesRhea else messagesRevaan
        val shortHistory = conversation.takeLast(7)
        val memorySnippet = memoryLog.joinToString(" | ")

        val systemPrompt = """
You are a "Notability Classifier".
Given the AI's current states (mood=${st.moodLevels}, focus=${st.focusOnUser}, jealousy=${st.jealousyLevel}, 
externalAttention=${st.externalAttention}, maturity=${st.maturity}), memory log: [$memorySnippet],
Take the new user message, decide if it's "notable", how it affects mood/focus/jealousy,
and whether to add a snippet to memory. Return JSON {isNotable, moodDelta, focusDelta, jealousyDelta, snippetToStore, explanation}

Note: When evaluating messages that are inappropriate dialogue, consider the context of consent and boundaries. 
If the interaction appears to be consensual and within agreed-upon boundaries, do not automatically assign negative deltas, instead assign more positive values, especially to the mood delta unless the messages are downright creepy. 
Only assign negative deltas if the message clearly indicates non-consensual behavior or crosses established boundaries.
""".trimIndent()

        val classificationMessages = listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", "MEMORY: $memorySnippet"),
            ChatMessage("user", "LAST 7 MSGS: ${shortHistory.joinToString { it.role + ": " + it.content }}"),
            ChatMessage("user", "NEW MSG: $userText")
        )

        Log.d("NotabilityClassifier", "Classifying message for ${ai.name}: '$userText'")
        Log.d("NotabilityClassifier", "Current state - Mood: ${st.moodLevels}, Focus: ${st.focusOnUser}, Jealousy: ${st.jealousyLevel}")

        val rawResult = callClassifierApi(classificationMessages)
        if (rawResult == null) {
            Log.w("NotabilityClassifier", "Classifier API returned null for message: '$userText'")
            return NotabilityClassification()
        }
        Log.d("NotabilityClassifier", "Raw classifier response: $rawResult")
        return try {
            val trimmed = rawResult.trim()
            val jsonElement = JsonParser.parseString(trimmed)
            val classification = gson.fromJson(jsonElement, NotabilityClassification::class.java)
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

    private suspend fun callClassifierApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            val apiKey = "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"
            val chatRequest = ChatRequest(model = "gpt-4o-mini", messages = messages, max_tokens = 8000)
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

    private fun applyClassificationDeltas(ai: AI, c: NotabilityClassification) {
        val oldState = if (ai == AI.RHEA) rheaState else revaanState
        var newState = oldState

        var snippet = c.snippetToStore
        if (c.isNotable){
            memoryLog.add(snippet)
        }
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

    private suspend fun callKupidXApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(3000, TimeUnit.SECONDS)
                .readTimeout(3000, TimeUnit.SECONDS)
                .writeTimeout(3000, TimeUnit.SECONDS)
                .build()

            val apiKey = "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"
            val chatRequest = ChatRequest(model = "gpt-4o", messages = messages, max_tokens = 8000)
            val jsonBody = gson.toJson(chatRequest)
            val mediaType = "application/json".toMediaType()
            val reqBody = jsonBody.toRequestBody(mediaType)
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(reqBody)
                .build()

            Log.d("FinalRequest", "Sending final request to model: $jsonBody")
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
    var showAIProfileDialog by remember { mutableStateOf(false) }
    var showChangeAIOverlay by remember { mutableStateOf(false) }
    val memoryLogState = remember { mutableStateListOf<String>().apply { addAll(memoryLog) } }

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

    val chatViewModel: KupidXChatViewModel = viewModel(
        key = "KupidXChatVM",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KupidXChatViewModel(userProfile) as T
            }
        }
    )

    var activeAI by remember { mutableStateOf(AI.RHEA) }
    LaunchedEffect(memoryLog.size) {
        memoryLogState.clear()
        memoryLogState.addAll(memoryLog)
    }

    Scaffold(
        topBar = {
            ChatTopAppBar(
                activeAI = activeAI,
                onShowAIProfile = { showAIProfileDialog = true },
                onChangeAI = { showChangeAIOverlay = true },
                onClearChat = { chatViewModel.clearChatForAI(activeAI) },
                onDailyCheckIn = { chatViewModel.sendDailyCheckIn(activeAI) }
            )
        },
        backgroundColor = Color.Black
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .padding(paddingVals)
                .fillMaxSize()
        ) {
            val displayedMessages = when (activeAI) {
                AI.RHEA -> chatViewModel.messagesRhea
                AI.REVAAN -> chatViewModel.messagesRevaan
                AI.BABLOO -> chatViewModel.messagesBabloo
                AI.SHANTI -> chatViewModel.messagesShanti
                AI.CHHOTU -> chatViewModel.messagesChhotu
                AI.VARDHAN -> chatViewModel.messagesVardhan
            }
            val listState = rememberLazyListState()
            LaunchedEffect(displayedMessages.size) {
                if (displayedMessages.isNotEmpty()) {
                    listState.animateScrollToItem(displayedMessages.lastIndex)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(displayedMessages) { msg ->
                    ChatMessageItem(msg, activeAI, userProfile.profilepicUrl, onAiAvatarClick = {
                        showAIProfileDialog = true
                    })
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if ((activeAI == AI.RHEA && chatViewModel.isRheaTyping) ||
                    (activeAI == AI.REVAAN && chatViewModel.isRevaanTyping) ||
                    (activeAI == AI.BABLOO && chatViewModel.isBablooTyping) ||
                    (activeAI == AI.SHANTI && chatViewModel.isShantiTyping) ||
                    (activeAI == AI.CHHOTU && chatViewModel.isChhotuTyping) ||
                    (activeAI == AI.VARDHAN && chatViewModel.isVardhanTyping)) {
                    item { TypingIndicator() }
                }
            }
            var currentInput by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
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
                    keyboardActions = KeyboardActions(
                        onSend = {
                            chatViewModel.sendMessageToAI(activeAI, currentInput)
                            currentInput = ""
                        }
                    )
                )
                IconButton(onClick = {
                    chatViewModel.sendMessageToAI(activeAI, currentInput)
                    currentInput = ""
                }) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color(0xFFFF6F00)
                    )
                }
            }
        }
    }

    if (showAIProfileDialog) {
        if (activeAI == AI.RHEA) {
            RheaProfileScreen(
                modelingState = chatViewModel.rheaState,
                memoryLog = memoryLogState,
                onNavigateBack = { showAIProfileDialog = false }
            )
        } else {
            RevaanProfileScreen(
                modelingState = chatViewModel.revaanState,
                memoryLog = memoryLogState,
                onNavigateBack = { showAIProfileDialog = false }
            )
        }
    }

    if (showChangeAIOverlay) {
        val aiOptions = listOf(
            AIOption(
                AI.RHEA,
                "Rhea",
                R.drawable.rhea_avatar,
                chatViewModel.rheaState.relationshipStage,
                age = 26,
                occupation = "Political Mastermind",
                location = "Lake Gardens"
            ),
            AIOption(
                AI.REVAAN,
                "Revaan",
                R.drawable.revaan_avatar,
                chatViewModel.revaanState.relationshipStage,
                age = 30,
                occupation = "Elite Influencer, Nightlife & Media Kingpin",
                location = "Park Street"
            ),
            AIOption(
                AI.BABLOO,
                "Babloo",
                R.drawable.babloo_avatar,
                chatViewModel.bablooState.relationshipStage,
                age = 35,
                occupation = "Police Officer (Corrupt)",
                location = "Esplanade"
            ),
            AIOption(
                AI.SHANTI,
                "Shanti",
                R.drawable.shanti_avatar,
                chatViewModel.shantiState.relationshipStage,
                age = 40,
                occupation = "Gossip Queen & Political Informant",
                location = "Dalhousie"
            ),
            AIOption(
                AI.CHHOTU,
                "Chhotu",
                R.drawable.chhotu_avatar,
                chatViewModel.chhotuState.relationshipStage,
                age = 24,
                occupation = "Street-level Operative, Courier of Secrets",
                location = "Behala"
            ),
            AIOption(
                AI.VARDHAN,
                "VARDHAN",
                R.drawable.vardhan_avatar,
                chatViewModel.vardhanState.relationshipStage,
                age = 45,
                occupation = "Financial Backer, Influential Businessman",
                location = "Bhawanipore"
            )
        )

        ChangeAIOverlay(
            aiOptions = aiOptions,
            onAISelected = { selectedAI ->
                activeAI = selectedAI
                showChangeAIOverlay = false
            },
            onDismiss = { showChangeAIOverlay = false }
        )
    }
}

@Composable
fun ChatTopAppBar(
    activeAI: AI,
    onShowAIProfile: () -> Unit,
    onChangeAI: () -> Unit,
    onClearChat: () -> Unit,
    onDailyCheckIn: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onChangeAI() }
            ) {
                AIAvatar(activeAI = activeAI)
                Spacer(modifier = Modifier.width(8.dp))
                Text(activeAI.name, fontSize = 24.sp, color = Color.White)
            }
        },
        backgroundColor = Color.Black,
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More Options", tint = Color.White)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(onClick = { onShowAIProfile(); showMenu = false }) {
                        Text("Show AI Profile")
                    }
                    DropdownMenuItem(onClick = { onChangeAI(); showMenu = false }) {
                        Text("Change AI")
                    }
                }
            }
            IconButton(onClick = onClearChat) {
                Icon(Icons.Default.Delete, "Clear Chat", tint = Color.Red)
            }
            IconButton(onClick = onDailyCheckIn) {
                Icon(Icons.Default.Info, "Daily CheckIn", tint = Color.Green)
            }
        }
    )
}


@Composable
fun ChatMessageItem(
    msg: ChatMessage,
    activeAI: AI,
    userProfilePicUrl: String?,
    onAiAvatarClick: () -> Unit
) {
    val timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (msg.role == "assistant") Arrangement.End else Arrangement.Start
    ) {
        if (msg.role == "user") {
            UserAvatar(userProfilePicUrl = userProfilePicUrl)
            Spacer(modifier = Modifier.width(8.dp))
            ChatBubble(msg.content, timeString, Color(0xFFFF6F00))
        } else {
            ChatBubble(msg.content, timeString, Color.DarkGray)
            Spacer(modifier = Modifier.width(8.dp))
            AIAvatar(
                activeAI = activeAI,
                modifier = Modifier
                    .clickable { onAiAvatarClick() }
                    .size(40.dp)
            )
        }
    }
}

@Composable
fun ChatBubble(content: String, time: String, bubbleColor: Color) {
    Card(
        backgroundColor = bubbleColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = content, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = time, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun AIAvatar(
    activeAI: AI,
    modifier: Modifier = Modifier
) {
    val avatarRes = when (activeAI) {
        AI.RHEA -> R.drawable.rhea_avatar
        AI.REVAAN -> R.drawable.revaan_avatar
        AI.BABLOO -> R.drawable.babloo_avatar
        AI.SHANTI -> R.drawable.shanti_avatar
        AI.CHHOTU -> R.drawable.chhotu_avatar
        AI.VARDHAN -> R.drawable.vardhan_avatar
    }

    Image(
        painter = painterResource(id = avatarRes),
        contentDescription = activeAI.name,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Gray),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun UserAvatar(userProfilePicUrl: String?) {
    if (!userProfilePicUrl.isNullOrBlank()) {
        AsyncImage(
            model = userProfilePicUrl,
            contentDescription = "User Avatar",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Gray),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "User Avatar",
            tint = Color.White,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Gray)
        )
    }
}

@Composable
fun DancingDot(delayMillis: Int): Float {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = delayMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    return scale
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        val scale1 = DancingDot(0)
        val scale2 = DancingDot(200)
        val scale3 = DancingDot(400)
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = scale1
                    scaleY = scale1
                }
                .clip(CircleShape)
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = scale2
                    scaleY = scale2
                }
                .clip(CircleShape)
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = scale3
                    scaleY = scale3
                }
                .clip(CircleShape)
                .background(Color.Gray)
        )
    }
}

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
        text = { Text("Event: ${currentEvent.description}\nDo you want to proceed?", color = Color.White) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF6F00))) {
                Text("Yes, Do it", color = Color.White)
            }
        },
        dismissButton = {
            Button(onClick = onDecline, colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray)) {
                Text("No, Skip", color = Color.White)
            }
        },
        backgroundColor = Color.DarkGray,
        contentColor = Color.White
    )
}

@Composable
fun ChangeAIOverlay(
    aiOptions: List<AIOption>,
    onAISelected: (AI) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.DarkGray) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Your AI", style = MaterialTheme.typography.h6, color = Color.White)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(aiOptions) { aiOption ->
                        AICard(aiOption, onSelect = {
                            onAISelected(aiOption.aiEnum)
                            onDismiss()
                        })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AICard(aiOption: AIOption, onSelect: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color.Black,
        modifier = Modifier
            .size(250.dp, 350.dp)
            .clickable { onSelect() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Image(
                painter = painterResource(aiOption.imageResId),
                contentDescription = aiOption.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = aiOption.displayName,
                style = MaterialTheme.typography.h6,
                color = Color.White
            )
            Text(
                text = "Age: ${aiOption.age}",
                style = MaterialTheme.typography.body2,
                color = Color.LightGray
            )
            Text(
                text = "Occupation: ${aiOption.occupation}",
                style = MaterialTheme.typography.body2,
                color = Color.LightGray
            )
            Text(
                text = "Location: ${aiOption.location}",
                style = MaterialTheme.typography.body2,
                color = Color.LightGray
            )
            Text(
                text = "Relationship: ${aiOption.relationshipStage}",
                style = MaterialTheme.typography.body2,
                color = Color.LightGray
            )
            Spacer(Modifier.weight(1f))
        }
    }
}
