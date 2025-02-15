package com.am24.am24

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit


enum class AI {
    RHEA, REVAAN
}

data class ChatMessage(val role: String, val content: String)
data class ChatRequest(val model: String, val messages: List<ChatMessage>)
data class ChatChoice(val message: ChatMessage)
data class ChatResponse(val choices: List<ChatChoice>)

class KupidXChatViewModel(
    private val userProfile: Profile
) : ViewModel() {
    var messagesRhea by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var messagesRevaan by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    private var sessionActive by mutableStateOf(false)

    private fun buildMasterPrompt(personaName: String): ChatMessage {
        val masterPromptText = """
            [MASTER PROMPT: ALPHA PERSONA]
            
            You are $personaName, from Kolkata. 
            Keep your response under 200 words maximum.
            
            You speak confidently, sometimes teasing. 
            Always remain in character.
        """.trimIndent()
        return ChatMessage(role = "system", content = masterPromptText)
    }

    private fun buildUserProfileMessage(profile: Profile): ChatMessage {
        val sb = StringBuilder()
        sb.appendLine("[USER PROFILE DATA - BEGIN]")
        sb.appendLine("interestedIn: ${profile.interestedIn}")
        sb.appendLine("username: ${profile.username}")
        sb.appendLine("name: ${profile.name}")
        if (profile.interests.isNotEmpty()) {
            val interestNames = profile.interests.joinToString { it.name }
            sb.appendLine("interests: $interestNames")
        } else {
            sb.appendLine("interests: []")
        }
        sb.appendLine("gender: ${profile.gender}")
        sb.appendLine("lastActive: ${profile.lastActive}")
        sb.appendLine("badges: ${profile.badges}")
        sb.appendLine("loveLanguage: ${profile.loveLanguage}")
        sb.appendLine("matches: ${profile.matches}")
        sb.appendLine("religion: ${profile.religion}")
        sb.appendLine("community: ${profile.community}")
        sb.appendLine("hometown: ${profile.hometown}")
        sb.appendLine("educationLevel: ${profile.educationLevel}")
        sb.appendLine("highSchool: ${profile.highSchool}, customHighSchool: ${profile.customHighSchool}, graduationYr: ${profile.highSchoolGraduationYear}")
        sb.appendLine("college: ${profile.college}, customCollege: ${profile.customCollege}, graduationYr: ${profile.collegeGraduationYear}, collegeDegree: ${profile.collegeDegree}")
        sb.appendLine("postGraduation: ${profile.postGraduation}, customPostGraduation: ${profile.customPostGraduation}, postGraduationYear: ${profile.postGraduationYear}, postGraduationDegree: ${profile.postGraduationDegree}")
        sb.appendLine("politics: ${profile.politics}")
        sb.appendLine("jobRole: ${profile.jobRole}, customJobRole: ${profile.customJobRole}")
        sb.appendLine("work: ${profile.work}, customWork: ${profile.customWork}")
        sb.appendLine("socialCauses: ${profile.socialCauses}")
        sb.appendLine("lookingFor: ${profile.lookingFor}")
        sb.appendLine("numberOfUsersWhoSwiped: ${profile.numberOfUsersWhoSwiped}")
        sb.appendLine("isBoosted: ${profile.isBoosted}, isPremium: ${profile.isPremium}, isPrivate: ${profile.isPrivate}")
        sb.appendLine("am24RankingAge: ${profile.am24RankingAge}, am24RankingHighSchool: ${profile.am24RankingHighSchool}, am24RankingCollege: ${profile.am24RankingCollege}, am24RankingHometown: ${profile.am24RankingHometown}, am24Ranking: ${profile.am24Ranking}")
        sb.appendLine("numberOfRatings: ${profile.numberOfRatings}, numberOfSwipeRights: ${profile.numberOfSwipeRights}")
        sb.appendLine("matchCount: ${profile.matchCount}, matchCountPerSwipeRight: ${profile.matchCountPerSwipeRight}")
        sb.appendLine("cumulativeUpvotes: ${profile.cumulativeUpvotes}, cumulativeDownvotes: ${profile.cumulativeDownvotes}")
        sb.appendLine("averageUpvoteCount: ${profile.averageUpvoteCount}, averageDownvoteCount: ${profile.averageDownvoteCount}")
        sb.appendLine("reportUsers: ${profile.reportUsers}, blockedUsers: ${profile.blockedUsers}")
        sb.appendLine("upvoteCount: ${profile.upvoteCount}, downvoteCount: ${profile.downvoteCount}")
        sb.appendLine("userTags: ${profile.userTags}")
        sb.appendLine("zodiac: ${profile.zodiac}")
        sb.appendLine("dateOfJoin: ${profile.dateOfJoin}")
        sb.appendLine("am24RankingCompositeScore: ${profile.am24RankingCompositeScore}")
        sb.appendLine("vibepoints: ${profile.vibepoints}")
        sb.appendLine("averageRating: ${profile.averageRating}")
        sb.appendLine("isMatrimonyMode: ${profile.isMatrimonyMode}")
        sb.appendLine("marriageTimeline: ${profile.marriageTimeline}")
        sb.appendLine("relocationPreference: ${profile.relocationPreference}")
        sb.appendLine("postMarriageCareerPlan: ${profile.postMarriageCareerPlan}")
        sb.appendLine("traditionalVsLiberal: ${profile.traditionalVsLiberal}")
        sb.appendLine("fatherOccupation: ${profile.fatherOccupation}, motherOccupation: ${profile.motherOccupation}")
        sb.appendLine("numberOfSiblings: ${profile.numberOfSiblings}, elderSiblings: ${profile.elderSiblings}, youngerSiblings: ${profile.youngerSiblings}")
        sb.appendLine("datingAgeStart: ${profile.datingAgeStart}, datingAgeEnd: ${profile.datingAgeEnd}, datingDistancePreference: ${profile.datingDistancePreference}")
        sb.appendLine("height: ${profile.height}, height2: ${profile.height2}")
        sb.appendLine("caste: ${profile.caste}, relationship: ${profile.relationship}")
        sb.appendLine("averageSwipeRightsOnUser: ${profile.averageSwipeRightsOnUser}")
        sb.appendLine("\n[LIFESTYLE SECTION]")
        val life = profile.lifestyle
        if (life == null) {
            sb.appendLine("No lifestyle info provided.")
        } else {
            sb.appendLine("smoking: ${life.smoking}, drinking: ${life.drinking}, cannabisFriendly: ${life.cannabisFriendly}")
            sb.appendLine("indoorsyToOutdoorsy: ${life.indoorsyToOutdoorsy}, sal: ${life.sal}, IE: ${life.IE}, socialMedia: ${life.socialMedia}, diet: ${life.diet}")
            sb.appendLine("sleepCycle: ${life.sleepCycle}, workLifeBalance: ${life.workLifeBalance}, exerciseFrequency: ${life.exerciseFrequency}, adventurous: ${life.adventurous}")
            sb.appendLine("petFriendly: ${life.petFriendly}, familyOriented: ${life.familyOriented}, intellectual: ${life.intellectual}, creativeArtistic: ${life.creativeArtistic}")
            sb.appendLine("fitnessLevel: ${life.fitnessLevel}, spiritualMindful: ${life.spiritualMindful}, humorousEasyGoing: ${life.humorousEasyGoing}")
            sb.appendLine("professionalAmbitious: ${life.professionalAmbitious}, environmentallyConscious: ${life.environmentallyConscious}")
            sb.appendLine("foodieCulinaryEnthusiast: ${life.foodieCulinaryEnthusiast}, politicallyAware: ${life.politicallyAware}, communityOriented: ${life.communityOriented}")
            sb.appendLine("sportsEnthusiast: ${life.sportsEnthusiast}, alcoholType: ${life.alcoholType}")
        }
        sb.appendLine("\nCalculated Profile Completion: ${profile.profileCompletionPercentage}%")
        sb.appendLine("[USER PROFILE DATA - END]")
        return ChatMessage(role = "system", content = sb.toString())
    }

    private fun buildSummaryMessage(conversation: List<ChatMessage>): ChatMessage {
        val userMessages = conversation.filter { it.role == "user" }
        val last25 = userMessages.takeLast(25)
        val summaryText = buildString {
            appendLine("[SUMMARY OF LAST 25 USER MESSAGES]")
            last25.forEachIndexed { i, msg ->
                appendLine("${i + 1}. ${msg.content}")
            }
            appendLine("[END SUMMARY]")
        }
        return ChatMessage(role = "system", content = summaryText)
    }

    fun startNewSession() {
        sessionActive = false
        messagesRhea = emptyList()
        messagesRevaan = emptyList()
    }

    fun sendMessageToAI(ai: AI, userInput: String) {
        if (userInput.isBlank()) return
        when (ai) {
            AI.RHEA -> messagesRhea = messagesRhea + ChatMessage("user", userInput)
            AI.REVAAN -> messagesRevaan = messagesRevaan + ChatMessage("user", userInput)
        }
        viewModelScope.launch {
            val conversation = when (ai) {
                AI.RHEA -> messagesRhea
                AI.REVAAN -> messagesRevaan
            }
            val masterPrompt = buildMasterPrompt(if (ai == AI.RHEA) "AI Rhea" else "AI Revaan")
            val profilePrompt = buildUserProfileMessage(userProfile)
            val summaryPrompt = buildSummaryMessage(conversation)
            val finalMessages = if (!sessionActive) {
                sessionActive = true
                listOf(masterPrompt, profilePrompt, summaryPrompt) + conversation
            } else {
                listOf(masterPrompt, summaryPrompt) + conversation
            }
            val responseText = callKupidXApi(finalMessages)
            if (!responseText.isNullOrBlank()) {
                val assistantMsg = ChatMessage("assistant", responseText)
                when (ai) {
                    AI.RHEA -> messagesRhea = messagesRhea + assistantMsg
                    AI.REVAAN -> messagesRevaan = messagesRevaan + assistantMsg
                }
            }
        }
    }

    private suspend fun callKupidXApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build()
            val gson = Gson()
            // Use BuildConfig to hide your secret key
            val apiKey = BuildConfig.OPENAI_API_KEY
            val chatRequest = ChatRequest(
                model = "gpt-4o", // or "gpt-3.5-turbo"
                messages = messages
            )
            val jsonBody = gson.toJson(chatRequest)
            val mediaType = "application/json".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext "Error: ${response.code}"
                    }
                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) return@withContext null
                    val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                    chatResponse.choices.firstOrNull()?.message?.content
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Error: ${e.message}"
            }
        }
    }
}

@Composable
fun KupidXChatScreen(
    profileViewModel: ProfileViewModel = viewModel()
) {
    // Ensure profile is fetched
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
    // Use rememberSaveable to preserve chat input and messages if possible.
    // Also, ensure that your ViewModel is scoped to a higher level so it isn’t recreated.
    val chatViewModel: KupidXChatViewModel = viewModel(
        key = "KupidXChatVM",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return KupidXChatViewModel(userProfile) as T
            }
        }
    )

    var selectedAI by rememberSaveable { mutableStateOf(AI.RHEA) }
    var currentInput by rememberSaveable { mutableStateOf("") }
    val displayedMessages = when (selectedAI) {
        AI.RHEA -> chatViewModel.messagesRhea
        AI.REVAAN -> chatViewModel.messagesRevaan
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KupidX AI", fontSize = 24.sp, color = Color.White) },
                backgroundColor = Color.Black
            )
        },
        backgroundColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { selectedAI = AI.RHEA },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (selectedAI == AI.RHEA) Color(0xFFFF6F00) else Color.DarkGray
                    )
                ) {
                    Text("Rhea", color = Color.White)
                }
                Button(
                    onClick = { selectedAI = AI.REVAAN },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (selectedAI == AI.REVAAN) Color(0xFFFF6F00) else Color.DarkGray
                    )
                ) {
                    Text("AI Revaan", color = Color.White)
                }
                Button(
                    onClick = {
                        chatViewModel.startNewSession()
                        currentInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                ) {
                    Text("Clear Chat", color = Color.White)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(displayedMessages) { msg ->
                    ChatMessageItem(msg)
                    Spacer(Modifier.height(8.dp))
                }
            }

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
                        cursorColor = Color.White
                    ),
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
}

@Composable
fun ChatMessageItem(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start
    ) {
        Card(
            backgroundColor = if (msg.role == "user") Color(0xFFFF6F00) else Color.DarkGray,
            modifier = Modifier.widthIn(max = 280.dp)
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
