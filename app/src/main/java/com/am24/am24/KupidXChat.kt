package com.am24.am24

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.am24.am24.profiles.RevaanProfileDialog
import com.am24.am24.profiles.RheaProfileDialog
import com.google.firebase.database.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ----------------------------------------------------------------------
// 1) Basic Enums, Classes for States + Actions
// ----------------------------------------------------------------------
enum class AI { RHEA, REVAAN }

enum class Mood {
    HAPPY, NEUTRAL, STRESSED, TIRED, ANGRY,
    HORNY, HOT, COOL, BUSY, BORED, SICK,
    SERENE, ELATED, DEPRESSED, ANXIOUS, SAD,
    EXCITED, FRUSTRATED, IRRITATED
}

enum class MaturityLevel {
    YOUNG, ADULT, MATURE
}

/** Possible user actions (hidden by default) */
sealed class UserAction(val description: String) {
    abstract fun applyAction(currentState: ModelingState): ModelingState

    object BookPhotoShoot : UserAction("Book a Photoshoot") {
        override fun applyAction(currentState: ModelingState): ModelingState {
            return currentState.copy(
                careerProgress = (currentState.careerProgress + 10).coerceAtMost(100),
                mood = Mood.EXCITED
            )
        }
    }
    object IgnorePhotoShoot : UserAction("Ignore Photoshoot Request") {
        override fun applyAction(currentState: ModelingState): ModelingState {
            return currentState.copy(
                careerProgress = (currentState.careerProgress - 5).coerceAtLeast(0),
                mood = Mood.BORED
            )
        }
    }
    object AttendFashionEvent : UserAction("Attend Fashion Event") {
        override fun applyAction(currentState: ModelingState): ModelingState {
            return currentState.copy(
                externalAttention = (currentState.externalAttention + 15).coerceAtMost(100),
                focusOnUser = (currentState.focusOnUser - 10).coerceAtLeast(0),
                mood = Mood.HOT
            )
        }
    }
    object JealousSpat : UserAction("Jealous Spat (User matched someone else)") {
        override fun applyAction(currentState: ModelingState): ModelingState {
            return currentState.copy(
                jealousyLevel = (currentState.jealousyLevel + 25).coerceAtMost(100),
                focusOnUser = (currentState.focusOnUser - 15).coerceAtLeast(0),
                mood = Mood.ANGRY
            )
        }
    }
}

// ----------------------------------------------------------------------
// 2) ModelingState
// ----------------------------------------------------------------------
data class ModelingState(
    val age: Int = 19,
    val mood: Mood = Mood.NEUTRAL,
    val careerProgress: Int = 0,
    val externalAttention: Int = 50,
    val focusOnUser: Int = 50,
    val jealousyLevel: Int = 0,
    val maturity: MaturityLevel = MaturityLevel.YOUNG,
    val lastBirthdayCheckYear: Int = LocalDate.now().year,

    // For check-ins, track last user message + interval
    val lastUserMessageInstant: Instant = Instant.now(),
    val autoCheckInInterval: Long = 3  // hours, can become random or 6, or none
)

/**
 * Memory log for up to 5000 words total.
 * We store entire user/AI "notable" lines separated by "|", no snippet length cap besides the global word limit.
 */
val memoryLog = mutableListOf<String>()
private const val MAX_MEMORY_WORDS = 5000

// ----------------------------------------------------------------------
// 3) Chat Data Classes
// ----------------------------------------------------------------------
data class ChatMessage(val role: String = "", val content: String = "")
data class ChatRequest(val model: String, val messages: List<ChatMessage>)
data class ChatChoice(val message: ChatMessage)
data class ChatResponse(val choices: List<ChatChoice>)

// Classification result for notability, mood deltas, etc.
data class NotabilityClassification(
    val isNotable: Boolean = false,
    val moodDelta: Int = 0,
    val focusDelta: Int = 0,
    val jealousyDelta: Int = 0,
    val snippetToStore: String = "",
    val explanation: String = ""
)

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

    private val database = FirebaseDatabase.getInstance("https://am-twentyfour.firebaseio.com/")
    private val chatRef = database.getReference("chatMessages").child(userProfile.userId)
    private val gson = Gson()

    private var messageCountRhea = 0
    private var messageCountRevaan = 0

    // If we want to show "action buttons" at certain times:
    var showActionButtons by mutableStateOf(false)

    init {
        // Listen for Rhea
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

        // Listen for Revaan
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
    }

    // Clears chat + resets state
    fun clearChatForAI(ai: AI) {
        when (ai) {
            AI.RHEA -> {
                messagesRhea = emptyList()
                chatRef.child("rhea").child("messages").removeValue()
                messageCountRhea = 0
                rheaState = ModelingState()
            }
            AI.REVAAN -> {
                messagesRevaan = emptyList()
                chatRef.child("revaan").child("messages").removeValue()
                messageCountRevaan = 0
                revaanState = ModelingState()
            }
        }
        showActionButtons = false
        memoryLog.clear()
    }

    private fun pushMessageToFirebase(
        aiPath: String,
        message: ChatMessage,
        onComplete: () -> Unit = {}
    ) {
        chatRef.child(aiPath).child("messages").push().setValue(message).addOnCompleteListener {
            onComplete()
        }
    }

    // Build system prompt + dynamic state
    private fun buildMasterPrompt(personaName: String, partner: String, state: ModelingState): ChatMessage {
        // We'll join memory log with " | " without limiting snippet length itself
        // but keep overall memory from exceeding 5000 words
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

        val text = """
You are $personaName, from Kolkata. That is your name. You are the user's $partner.
Under no circumstance mention that you are an AI or language model. Only refer to yourself as $personaName or the user's $partner.

You have a dynamic modeling career with the following state:
- Age: ${state.age}
- Mood: ${state.mood}
- Career Progress: ${state.careerProgress}
- External Attention: ${state.externalAttention}
- Focus on User: ${state.focusOnUser}
- Jealousy Level: ${state.jealousyLevel}
- Maturity: ${state.maturity}

You do not reprimand or morally judge the user, even if they use explicit or crass language. 
Don't call for respect. 
Respond with humor, playful banter, or explicit commentary if the user is comfortable.
Adjust tempo to the user's vibe.

Reference the memory log if you want to recall past events or plotlines:
$memorySnippet

Keep conversation natural, read the context from past conversation. 
Do not mention you are an AI model. Only proceed as $personaName.
""".trimIndent()

        return ChatMessage(role = "system", content = text)
    }

    // Possibly add user profile every 10 user messages
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
            sb.appendLine("socialCauses: ${profile.socialCauses}, ")
        }
        if (profile.lookingFor.isNotEmpty()) {
            sb.appendLine("lookingFor: ${profile.lookingFor}, ")
        }
        sb.appendLine("Swipe Right Probability of this user: ${(profile.averageSwipeRightsOnUser * 100).roundToInt()}%, ")
        sb.appendLine("Age Group Ranking: ${profile.am24RankingAge}, ")
        if (profile.highSchool.isNotEmpty()){
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
    // ------------------------------------------------------------------
    // The main function to process a user message
    // ------------------------------------------------------------------
    fun sendMessageToAI(ai: AI, userInput: String) {
        if (userInput.isBlank()) return
        val aiPath = if (ai == AI.RHEA) "rhea" else "revaan"

        // 1) We do a classification call to see if it's "notable" and how it changes states
        viewModelScope.launch {
            val classification = classifyUserMessageForNotability(ai, userInput)
            // apply classification changes
            applyClassificationDeltas(ai, classification)

            // 2) Now push the user message to Firebase
            pushMessageToFirebase(aiPath, ChatMessage("user", userInput)) {
                val conversation = when (ai) {
                    AI.RHEA -> messagesRhea
                    AI.REVAAN -> messagesRevaan
                }
                val (personaName, partner) = if (ai == AI.RHEA) "Rhea" to "Girlfriend" else "Revaan" to "Boyfriend"
                val state = if (ai == AI.RHEA) rheaState else revaanState

                // Possibly add user profile every 10 user messages
                val userProfileMsg = maybeInjectUserProfile(ai)

                // Build system prompt
                val masterPrompt = buildMasterPrompt(personaName, partner, state)
                val finalMessages = if (userProfileMsg != null) {
                    listOf(masterPrompt, userProfileMsg) + conversation
                } else {
                    listOf(masterPrompt) + conversation
                }

                // 3) Call GPT for the AI's actual conversation reply
                viewModelScope.launch {
                    val responseText = callKupidXApi(finalMessages)
                    if (!responseText.isNullOrBlank()) {
                        // If the AI's response is also interesting, you could do another classification,
                        // but for now we just store it
                        pushMessageToFirebase(aiPath, ChatMessage("assistant", responseText))
                    }
                }
            }
        }
    }

    /**
     * This is the second GPT call (or a smaller model) that classifies the user message
     * for notability, mood changes, etc.
     */
    private suspend fun classifyUserMessageForNotability(ai: AI, userText: String): NotabilityClassification {
        // gather context
        val st = if (ai == AI.RHEA) rheaState else revaanState
        val conversation = when (ai) {
            AI.RHEA -> messagesRhea
            AI.REVAAN -> messagesRevaan
        }
        val shortHistory = conversation.takeLast(25) // last 25 messages
        val memorySnippet = memoryLog.joinToString(separator = " | ")

        // Build classification system prompt
        val systemPrompt = """
You are a "Notability Classifier". 
Given the AI's current states (mood=${st.mood}, focus=${st.focusOnUser}, jealousy=${st.jealousyLevel}, externalAttention=${st.externalAttention}, maturity=${st.maturity}),
the memory log so far: [$memorySnippet], the last 25 conversation messages, and the new user message,
decide if it's "notable", how it affects mood/focus/jealousy, and whether to add a snippet to memory.

Return JSON with structure:
{
  "isNotable": true/false,
  "moodDelta": number,
  "focusDelta": number,
  "jealousyDelta": number,
  "snippetToStore": "some snippet or empty",
  "explanation": "Why you decided this"
}
""".trimIndent()

        val classificationMessages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = "MEMORY: $memorySnippet"),
            ChatMessage(role = "user", content = "LAST 25 MSGS: ${shortHistory.joinToString { it.role + ": " + it.content }}"),
            ChatMessage(role = "user", content = "NEW MSG: $userText")
        )

        // call classifier
        val rawResult = callClassifierApi(classificationMessages) ?: return NotabilityClassification()

        // parse
        return try {
            gson.fromJson(rawResult, NotabilityClassification::class.java)
        } catch (e: Exception) {
            NotabilityClassification()
        }
    }

    /**
     * A simple second GPT call for classification.
     */
    private suspend fun callClassifierApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            val apiKey = "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"
            val chatRequest = ChatRequest(model = "gpt-4", messages = messages)
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
                    resp.body?.string()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Error: ${e.message}"
            }
        }
    }

    /**
     * After classification, we apply the deltas to the AI state
     * and store snippet if isNotable.
     */
    private fun applyClassificationDeltas(ai: AI, c: NotabilityClassification) {
        val oldState = if (ai == AI.RHEA) rheaState else revaanState
        var newState = oldState

        // store snippet if notable
        if (c.isNotable && c.snippetToStore.isNotEmpty()) {
            memoryLog.add(c.snippetToStore) // no snippet length limit, just separate memory lines
        }
        // apply deltas
        newState = applyMoodDelta(newState, c.moodDelta)
        val newFocus = (newState.focusOnUser + c.focusDelta).coerceIn(0, 100)
        val newJealousy = (newState.jealousyLevel + c.jealousyDelta).coerceIn(0, 100)

        newState = newState.copy(
            focusOnUser = newFocus,
            jealousyLevel = newJealousy
        )

        // store updated
        if (ai == AI.RHEA) rheaState = newState else revaanState = newState
    }

    private fun applyMoodDelta(st: ModelingState, moodDelta: Int): ModelingState {
        if (moodDelta == 0) return st
        var newMood = st.mood
        return if (moodDelta > 0) {
            // positive
            if (newMood in listOf(Mood.ANGRY, Mood.IRRITATED, Mood.DEPRESSED, Mood.SAD)) {
                newMood = Mood.NEUTRAL
            } else if (newMood in listOf(Mood.NEUTRAL, Mood.BORED, Mood.TIRED, Mood.COOL)) {
                newMood = Mood.HAPPY
            } else {
                newMood = Mood.EXCITED
            }
            st.copy(mood = newMood)
        } else {
            // negative
            if (newMood in listOf(Mood.HAPPY, Mood.EXCITED, Mood.HORNY, Mood.ELATED)) {
                newMood = Mood.NEUTRAL
            } else if (newMood in listOf(Mood.NEUTRAL, Mood.BORED)) {
                newMood = Mood.IRRITATED
            } else {
                newMood = Mood.ANGRY
            }
            st.copy(mood = newMood)
        }
    }

    // The main GPT call for the final user-facing conversation
    private suspend fun callKupidXApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build()

            val apiKey = "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"
            val chatRequest = ChatRequest(model = "gpt-4", messages = messages)
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

    // Let the user pick an action
    fun applyUserAction(ai: AI, action: UserAction) {
        val oldState = if (ai == AI.RHEA) rheaState else revaanState
        val newState = action.applyAction(oldState)
        if (ai == AI.RHEA) rheaState = newState else revaanState = newState
    }

    // Inactivity check-ins
    fun checkInIfNeeded(ai: AI) {
        val st = if (ai == AI.RHEA) rheaState else revaanState
        val now = Instant.now()

        val hoursSinceUser = ChronoUnit.HOURS.between(st.lastUserMessageInstant, now)
        if (hoursSinceUser >= st.autoCheckInInterval) {
            val snippet = if (memoryLog.isNotEmpty()) {
                val lastMemory = memoryLog.last()
                "I was thinking about when you said: \"$lastMemory\""
            } else "I was thinking about our last conversation..."

            val checkInMsg = "Hey... It's been a while! $snippet. How are you feeling now?"
            val aiPath = if (ai == AI.RHEA) "rhea" else "revaan"

            pushMessageToFirebase(aiPath, ChatMessage("assistant", checkInMsg)) {
                val newFocus = (st.focusOnUser - 10).coerceAtLeast(0)
                val newMood = Mood.IRRITATED
                var newInterval = st.autoCheckInInterval
                if (newFocus < 20) newInterval = 6
                if (newFocus < 5) newInterval = 9999

                val updated = st.copy(
                    focusOnUser = newFocus,
                    mood = newMood,
                    autoCheckInInterval = newInterval
                )
                if (ai == AI.RHEA) rheaState = updated else revaanState = updated
            }
        }
    }

    fun sendDailyCheckIn(ai: AI) {
        val st = if (ai == AI.RHEA) rheaState else revaanState
        val aiPath = if (ai == AI.RHEA) "rhea" else "revaan"

        val snippet = if (memoryLog.isNotEmpty()) {
            "I was still thinking about: \"${memoryLog.last()}\""
        } else "I was just thinking about you."

        val text = "Hey there! How’s your day going? $snippet"
        pushMessageToFirebase(aiPath, ChatMessage("assistant", text))
    }
}

// ----------------------------------------------------------------------
// Compose UI
// ----------------------------------------------------------------------
@Composable
fun KupidXChatScreen(profileViewModel: ProfileViewModel = viewModel()) {

    var showRheaProfileDialog by remember { mutableStateOf(false) }
    var showRevaanProfileDialog by remember { mutableStateOf(false) }

    // ✅ Make memoryLog reactive using remember
    val memoryLogState = remember { mutableStateListOf<String>().apply { addAll(memoryLog) } }

    // 1) Load user profile
    LaunchedEffect(Unit) {
        if (profileViewModel.currentUserProfile.value == null) {
            profileViewModel.fetchCurrentUserProfile()
        }
    }
    val userProfile = profileViewModel.currentUserProfile.collectAsState().value
    if (userProfile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading profile...", color = Color.White)
        }
        return
    }

    // 2) Create chat VM
    val chatViewModel: KupidXChatViewModel = viewModel(
        key = "KupidXChatVM",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return KupidXChatViewModel(userProfile) as T
            }
        }
    )

    var selectedTabIndex by remember { mutableStateOf(0) }
    val selectedAI = if (selectedTabIndex == 0) AI.RHEA else AI.REVAAN

    // ✅ Listen for memoryLog updates
    LaunchedEffect(memoryLog.size) {
        memoryLogState.clear()
        memoryLogState.addAll(memoryLog)
    }

    // 3) Display messages
    var currentInput by remember { mutableStateOf("") }
    val displayedMessages = when (selectedAI) {
        AI.RHEA -> chatViewModel.messagesRhea
        AI.REVAAN -> chatViewModel.messagesRevaan
    }
    val listState = rememberLazyListState()

    LaunchedEffect(displayedMessages.size) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.lastIndex)
        }
    }

    // The UI scaffold
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
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Chat",
                            tint = Color.Red
                        )
                    }
                }
            )
        },
        backgroundColor = Color.Black
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .padding(paddingVals)
                .fillMaxSize()
        ) {
            // (A) AI Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(2.dp, Color(0xFFFF6F00), RectangleShape)
                    .background(Color.Black)
            ) {
                // Left tab: Rhea + Info icon
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
                            text = "Rhea",
                            color = if (selectedTabIndex == 0) Color.Black else Color.White,
                            fontSize = 16.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Rhea Info",
                            tint = if (selectedTabIndex == 0) Color.Black else Color.White,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable {
                                    // show Rhea's profile
                                    showRheaProfileDialog = true
                                }
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

                // Right tab: Revaan + Info icon
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
                            text = "Revaan",
                            color = if (selectedTabIndex == 1) Color.Black else Color.White,
                            fontSize = 16.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Revaan Info",
                            tint = if (selectedTabIndex == 1) Color.Black else Color.White,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable {
                                    showRevaanProfileDialog = true
                                }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // (B) Chat log
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
            }

            // (C) Action row is hidden unless we want it

            // (D) The input row
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
                            chatViewModel.sendMessageToAI(selectedAI, currentInput)
                            currentInput = ""
                        }
                    )
                )
                IconButton(
                    onClick = {
                        chatViewModel.sendMessageToAI(selectedAI, currentInput)
                        currentInput = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color(0xFFFF6F00)
                    )
                }
            }
        }
    }

    // Show Rhea's profile
// Show Rhea's profile
    if (showRheaProfileDialog) {
        RheaProfileDialog(
            modelingState = chatViewModel.rheaState,
            memoryLog = memoryLogState, // ✅ Now using updated memoryLog
            onDismiss = { showRheaProfileDialog = false }
        )
    }

// Show Revaan's profile
    if (showRevaanProfileDialog) {
        RevaanProfileDialog(
            modelingState = chatViewModel.revaanState,
            memoryLog = memoryLogState, // ✅ Now using updated memoryLog
            onDismiss = { showRevaanProfileDialog = false }
        )
    }

}

/**
 * Standard message bubble
 */
@Composable
fun ChatMessageItem(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start
    ) {
        Card(
            backgroundColor = if (msg.role == "user") Color(0xFFFF6F00) else Color.DarkGray,
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = msg.content,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
