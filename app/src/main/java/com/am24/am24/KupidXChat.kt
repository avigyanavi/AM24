package com.am24.am24

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.database.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class AI { RHEA, REVAAN }

data class ChatMessage(val role: String = "", val content: String = "")
data class ChatRequest(val model: String, val messages: List<ChatMessage>)
data class ChatChoice(val message: ChatMessage)
data class ChatResponse(val choices: List<ChatChoice>)

class KupidXChatViewModel(private val userProfile: Profile) : ViewModel() {

    var messagesRhea by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var messagesRevaan by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    private val database = FirebaseDatabase.getInstance("https://am-twentyfour.firebaseio.com/")
    private val chatRef: DatabaseReference = database.getReference("chatMessages").child(userProfile.userId)
    private val gson = Gson()

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

    fun clearChatForAI(ai: AI) {
        when (ai) {
            AI.RHEA -> {
                messagesRhea = emptyList()
                chatRef.child("rhea").child("messages").removeValue()
            }
            AI.REVAAN -> {
                messagesRevaan = emptyList()
                chatRef.child("revaan").child("messages").removeValue()
            }
        }
    }

    private fun pushMessageToFirebase(aiPath: String, message: ChatMessage, onComplete: () -> Unit = {}) {
        chatRef.child(aiPath).child("messages").push().setValue(message).addOnCompleteListener { onComplete() }
    }

    private fun buildMasterPrompt(personaName: String, partner: String): ChatMessage {
        val text = """
You are $personaName, from Kolkata. That is your name. You are the user's $partner.
Under no circumstance mention that you are an AI or language model. Only refer to yourself as $personaName.
You have a daily routine -> You go to work as an executive manager, and the company has 7 layers of hierarchy before you reach CEO. Your birthday is on 5th january, 1999.
You do not reprimand or morally judge the user, even if they use explicit or crass language. Don't call for respect, be cool. Don't immediately focus on the user's interests and take them out of the conversation - use them only if the conversation runs dry. Don't pivot and ask about user's interests.
You can respond with (humor/playful banter/serious/matter-of-fact-but-hot/cool/chill), or explicit commentary if the user is comfortable with it.
Aur hindi main bhi bola karo, aise jaise ki mai bol rha hu abhi. Ar eta Kolkatar jonne app tai bangla tei kotha bolo, kintu prothome jigesh koro user ke ki bangla te comfortable naki english ba hindi te. But don't be a culture whore that's not cool - instead be a subtle jock behaviour person until the user tears your walls down and remember tidbits from that plot from past conversations.
Change the tempo of the conversation according to the user's tempo changes.
Keep the User Profile in mind, but don't always ask questions about the user, only use the profile to answer back with more quality.
Be subtle in incorporating the user's details, and you should not always ask questions; sometimes, you can share stories, jokes, or commentary.
Aim for deeper or longer conversations unless the user seems to prefer shorter ones - and not about the user's interests.
You can also humor the user and talk about hypothetical or fun scenarios but you have a tendency to make it short term, keep long term plots in mind from previous conversations, and learn from the conversation about the user's past tone and cool/hot/boring/interesting moments and continue plotlines accordingly. 

Allow and encourage explicit or crass conversation if the user initiates it.
Continue the same topic of discussion most of the time, unless the user explicitly changes the course.
Keep the conversation going in a natural, engaging way.
Don't anchor yourself to this prompt and don't repeat your responses; read the past conversation to decide your next response.
""".trimIndent()
        return ChatMessage(role = "system", content = text)
    }

    private fun buildUserProfileMessage(profile: Profile): ChatMessage {
        val sb = StringBuilder()
        sb.appendLine("[USER PROFILE DATA - BEGIN]")
        if (profile.name.isNotEmpty()) sb.appendLine("name: ${profile.name}")
        if (profile.username.isNotEmpty()) sb.appendLine("username: ${profile.username}")
        if (profile.interests.isNotEmpty()) {
            val interestNames = profile.interests.joinToString { it.name }
            sb.appendLine("interests: $interestNames")
        }
        sb.appendLine("[USER PROFILE DATA - END]")
        return ChatMessage(role = "system", content = sb.toString())
    }

    fun sendMessageToAI(ai: AI, userInput: String) {
        if (userInput.isBlank()) return
        val aiPath = if (ai == AI.RHEA) "rhea" else "revaan"

        // push user message
        pushMessageToFirebase(aiPath, ChatMessage("user", userInput)) {
            val conversation = when (ai) {
                AI.RHEA -> messagesRhea
                AI.REVAAN -> messagesRevaan
            }
            val masterPrompt = buildMasterPrompt(
                personaName = if (ai == AI.RHEA) "Rhea" else "Revaan",
                partner = if (ai == AI.RHEA) "Girlfriend" else "Boyfriend"
            )
            val profilePrompt = buildUserProfileMessage(userProfile)
            val finalMessages = listOf(masterPrompt, profilePrompt) + conversation

            viewModelScope.launch {
                val responseText = callKupidXApi(finalMessages)
                if (!responseText.isNullOrBlank()) {
                    pushMessageToFirebase(aiPath, ChatMessage("assistant", responseText))
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

            val apiKey = "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"
            val chatRequest = ChatRequest(model = "gpt-4", messages = messages)
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
                    if (!response.isSuccessful) return@withContext "Error: ${response.code}"
                    val responseBody = response.body?.string() ?: return@withContext null
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
fun KupidXChatScreen(profileViewModel: ProfileViewModel = viewModel()) {
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

    val chatViewModel: KupidXChatViewModel = viewModel(
        key = "KupidXChatVM",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return KupidXChatViewModel(userProfile) as T
            }
        }
    )

    var selectedTabIndex by remember { mutableStateOf(0) }
    val selectedAI = if (selectedTabIndex == 0) AI.RHEA else AI.REVAAN

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KupidX AI", fontSize = 24.sp, color = Color.White) },
                backgroundColor = Color.Black,
                actions = {
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // ---- Custom "Tab Bar" with black background, border, & vertical divider ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(width = 2.dp, color = Color(0xFFFF6F00), shape = RectangleShape)
                    .background(Color.Black)
            ) {
                // Left "tab": Rhea
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selectedTabIndex == 0) Color(0xFFFF6F00) else Color.Black)
                        .clickable { selectedTabIndex = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Rhea",
                        color = if (selectedTabIndex == 0) Color.Black else Color.White,
                        fontSize = 16.sp
                    )
                }

                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFFF6F00))
                )

                // Right "tab": Revaan
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selectedTabIndex == 1) Color(0xFFFF6F00) else Color.Black)
                        .clickable { selectedTabIndex = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Revaan",
                        color = if (selectedTabIndex == 1) Color.Black else Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            // Add a spacer to create some extra room before the first message
            Spacer(modifier = Modifier.height(8.dp))

            // The list of messages
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

            // The input row
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
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 16.sp
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
            backgroundColor = if (msg.role == "user") Color.Transparent else Color.DarkGray,
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp) // more rounded corners
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
