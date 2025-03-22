package com.am24.am24

import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.am24.am24.profiles.GenericProfileScreen
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.*
import java.util.concurrent.TimeUnit

// --- Enums and Data Classes ---
enum class AI { RHEA, REVAAN, BABLOO, SHANTI, CHHOTU, VARDHAN }

data class MoodLevels(
    val trust: Int = 0,
    val jealousy: Int = 0,
    val fear: Int = 0,
    val greed: Int = 0,
    val ambition: Int = 0,
    val romantic_passion: Int = 0,
    val satisfaction: Int = 0
)

data class RelationshipHistory(
    val attachment: Int = 50,
    val confidence: Int = 50,
    val emotionalDepth: Int = 50,
    val backStory: MutableList<String> = mutableListOf()
)

data class ModelingState(
    val relationshipHistory: RelationshipHistory = RelationshipHistory(),
    val moodLevels: MoodLevels = MoodLevels(),
    val careerProgress: Int = 0,
    val externalAttention: Int = 50,
    val relationshipStage: String = "Acquaintance",
    val money: Int = 0,
    val reputation: Int = 0,
    val plotStack: MutableList<String> = mutableListOf(), // (Retained in case you want to log narrative beats)
    val completedEvents: Set<String> = emptySet()
)

data class ChatMessage(
    val role: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatRequest(val model: String, val messages: List<ChatMessage>, val max_tokens: Int = 8000)
data class ChatChoice(val message: ChatMessage)
data class ChatResponse(val choices: List<ChatChoice>)

data class EmotionDeltas(
    val trustDelta: Int = 0,
    val jealousyDelta: Int = 0,
    val fearDelta: Int = 0,
    val greedDelta: Int = 0,
    val ambitionDelta: Int = 0,
    val romanticPassionDelta: Int = 0,
    val satisfactionDelta: Int = 0
)

data class MessageImpact(
    val impactScore: Int,
    val emotionDeltas: EmotionDeltas,
    val externalAttentionDelta: Int = 0,
    val moneyDelta: Int = 0,
    val reputationDelta: Int = 0,
    val snippetToStore: String = "",
    val explanation: String = ""
)

val memoryLogs = mutableMapOf(
    AI.RHEA to mutableListOf<String>(),
    AI.REVAAN to mutableListOf<String>(),
    AI.BABLOO to mutableListOf<String>(),
    AI.SHANTI to mutableListOf<String>(),
    AI.CHHOTU to mutableListOf<String>(),
    AI.VARDHAN to mutableListOf<String>()
)

private const val MAX_MEMORY_WORDS = 5000

// --- ViewModel ---
class KupidXChatViewModel(private val userProfile: Profile) : ViewModel() {

    var messagesRhea by mutableStateOf<List<ChatMessage>>(emptyList())
    var messagesRevaan by mutableStateOf<List<ChatMessage>>(emptyList())
    var messagesBabloo by mutableStateOf<List<ChatMessage>>(emptyList())
    var messagesShanti by mutableStateOf<List<ChatMessage>>(emptyList())
    var messagesChhotu by mutableStateOf<List<ChatMessage>>(emptyList())
    var messagesVardhan by mutableStateOf<List<ChatMessage>>(emptyList())

    var isRheaTyping by mutableStateOf(false)
    var isRevaanTyping by mutableStateOf(false)
    var isBablooTyping by mutableStateOf(false)
    var isShantiTyping by mutableStateOf(false)
    var isChhotuTyping by mutableStateOf(false)
    var isVardhanTyping by mutableStateOf(false)

    var rheaState by mutableStateOf(ModelingState())
    var revaanState by mutableStateOf(ModelingState())
    var bablooState by mutableStateOf(ModelingState())
    var shantiState by mutableStateOf(ModelingState())
    var chhotuState by mutableStateOf(ModelingState())
    var vardhanState by mutableStateOf(ModelingState())

    // Removed: plot event variables and subEventScriptMap / choiceOptionsMap
    // Removed: functions userSelectedChoice(), startEpisodeFlow(), proceedToNextEpisodeStep(), etc.
    // The narrative will now be driven solely by chat interactions.

    private var messageCountRhea = 0
    private var messageCountRevaan = 0
    private var messageCountBabloo = 0
    private var messageCountShanti = 0
    private var messageCountChhotu = 0
    private var messageCountVardhan = 0

    private val database = FirebaseDatabase.getInstance("https://am-twentyfour.firebaseio.com/")
    private val chatRef = database.getReference("chatMessages").child(userProfile.userId)
    private val gson = Gson()
    private val stateRef = chatRef.child("states")
    private val messageCountRef = chatRef.child("messageCounts")

    init {
        AI.values().forEach { ai ->
            messageCountRef.child(ai.name.lowercase()).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = snapshot.getValue(Int::class.java) ?: 0
                    when (ai) {
                        AI.RHEA -> messageCountRhea = count
                        AI.REVAAN -> messageCountRevaan = count
                        AI.BABLOO -> messageCountBabloo = count
                        AI.SHANTI -> messageCountShanti = count
                        AI.CHHOTU -> messageCountChhotu = count
                        AI.VARDHAN -> messageCountVardhan = count
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Failed to load messageCount: ${error.message}")
                }
            })
        }
        for (ai in AI.values()) {
            chatRef.child("memoryLogs").child(ai.name.lowercase())
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = snapshot.children.mapNotNull { it.getValue(String::class.java) }.filter { it.isNotBlank() }
                        memoryLogs[ai]?.clear()
                        memoryLogs[ai]?.addAll(list)
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
        loadStates()
        loadMessages()
    }

    // --- Removed plot event and choice functions ---
    // The narrative and emotional beats will be injected directly into the chat flow

    fun getCurrentState(ai: AI): ModelingState {
        return when (ai) {
            AI.RHEA -> rheaState
            AI.REVAAN -> revaanState
            AI.BABLOO -> bablooState
            AI.SHANTI -> shantiState
            AI.CHHOTU -> chhotuState
            AI.VARDHAN -> vardhanState
        }
    }

    fun setState(ai: AI, newState: ModelingState) {
        when (ai) {
            AI.RHEA -> rheaState = newState
            AI.REVAAN -> revaanState = newState
            AI.BABLOO -> bablooState = newState
            AI.SHANTI -> shantiState = newState
            AI.CHHOTU -> chhotuState = newState
            AI.VARDHAN -> vardhanState = newState
        }
        pushStateToFirebase(ai, newState)
    }

    fun clearChatForAI(ai: AI) {
        when (ai) {
            AI.RHEA -> {
                messagesRhea = emptyList()
                chatRef.child("rhea").child("messages").removeValue()
                messageCountRhea = 0
                messageCountRef.child("rhea").setValue(0)
                rheaState = ModelingState()
                pushStateToFirebase(AI.RHEA, rheaState)
            }
            AI.REVAAN -> {
                messagesRevaan = emptyList()
                chatRef.child("revaan").child("messages").removeValue()
                messageCountRevaan = 0
                messageCountRef.child("revaan").setValue(0)
                revaanState = ModelingState()
                pushStateToFirebase(AI.REVAAN, revaanState)
            }
            AI.BABLOO -> {
                messagesBabloo = emptyList()
                chatRef.child("babloo").child("messages").removeValue()
                messageCountBabloo = 0
                messageCountRef.child("babloo").setValue(0)
                bablooState = ModelingState()
                pushStateToFirebase(AI.BABLOO, bablooState)
            }
            AI.SHANTI -> {
                messagesShanti = emptyList()
                chatRef.child("shanti").child("messages").removeValue()
                messageCountShanti = 0
                messageCountRef.child("shanti").setValue(0)
                shantiState = ModelingState()
                pushStateToFirebase(AI.SHANTI, shantiState)
            }
            AI.CHHOTU -> {
                messagesChhotu = emptyList()
                chatRef.child("chhotu").child("messages").removeValue()
                messageCountChhotu = 0
                messageCountRef.child("chhotu").setValue(0)
                chhotuState = ModelingState()
                pushStateToFirebase(AI.CHHOTU, chhotuState)
            }
            AI.VARDHAN -> {
                messagesVardhan = emptyList()
                chatRef.child("vardhan").child("messages").removeValue()
                messageCountVardhan = 0
                messageCountRef.child("vardhan").setValue(0)
                vardhanState = ModelingState()
                pushStateToFirebase(AI.VARDHAN, vardhanState)
            }
        }
        memoryLogs[ai]?.clear()
        pushMemoryLogToFirebase(ai)
    }

    fun getMessageCount(ai: AI): Int {
        return when (ai) {
            AI.RHEA -> messageCountRhea
            AI.REVAAN -> messageCountRevaan
            AI.BABLOO -> messageCountBabloo
            AI.SHANTI -> messageCountShanti
            AI.CHHOTU -> messageCountChhotu
            AI.VARDHAN -> messageCountVardhan
        }
    }

    @RequiresApi(35)
    fun sendMessageToAI(ai: AI, userInput: String) {
        if (userInput.isBlank()) return
        val path = when (ai) {
            AI.RHEA -> "rhea"
            AI.REVAAN -> "revaan"
            AI.BABLOO -> "babloo"
            AI.SHANTI -> "shanti"
            AI.CHHOTU -> "chhotu"
            AI.VARDHAN -> "vardhan"
        }
        val userMsg = ChatMessage("user", userInput)
        val state = getCurrentState(ai)
        val oldMsgs = when (ai) {
            AI.RHEA -> messagesRhea
            AI.REVAAN -> messagesRevaan
            AI.BABLOO -> messagesBabloo
            AI.SHANTI -> messagesShanti
            AI.CHHOTU -> messagesChhotu
            AI.VARDHAN -> messagesVardhan
        }
        val newList = oldMsgs + userMsg
        when (ai) {
            AI.RHEA -> messagesRhea = newList
            AI.REVAAN -> messagesRevaan = newList
            AI.BABLOO -> messagesBabloo = newList
            AI.SHANTI -> messagesShanti = newList
            AI.CHHOTU -> messagesChhotu = newList
            AI.VARDHAN -> messagesVardhan = newList
        }
        pushMessageToFirebase(path, userMsg)
        incrementMessageCounter(ai)
        setTyping(ai, true)
        viewModelScope.launch {
            val classification = classifyMessageImpact(ai, userInput)
            applyMessageImpact(ai, classification)
            val personaName = when (ai) {
                AI.RHEA -> "Rhea"
                AI.REVAAN -> "Revaan"
                AI.BABLOO -> "Babloo"
                AI.SHANTI -> "Shanti"
                AI.CHHOTU -> "Chhotu"
                AI.VARDHAN -> "Vardhan"
            }
            val relStage = state.relationshipStage
            val master = buildMasterPrompt(personaName, relStage, state, memoryLogs[ai] ?: listOf())
            val recent = newList.takeLast(6)
            val final = listOf(master) + recent
            val respText = callKupidXApi(final)
            if (!respText.isNullOrBlank()) {
                val assistantMsg = ChatMessage("assistant", respText)
                when (ai) {
                    AI.RHEA -> messagesRhea += assistantMsg
                    AI.REVAAN -> messagesRevaan += assistantMsg
                    AI.BABLOO -> messagesBabloo += assistantMsg
                    AI.SHANTI -> messagesShanti += assistantMsg
                    AI.CHHOTU -> messagesChhotu += assistantMsg
                    AI.VARDHAN -> messagesVardhan += assistantMsg
                }
                pushMessageToFirebase(path, assistantMsg)
                setTyping(ai, false)
            } else {
                setTyping(ai, false)
            }
            // Removed updatePlotProgress(ai) call – narrative progress is now embedded in the conversation.
        }
    }

    private fun setTyping(ai: AI, value: Boolean) {
        when (ai) {
            AI.RHEA -> isRheaTyping = value
            AI.REVAAN -> isRevaanTyping = value
            AI.BABLOO -> isBablooTyping = value
            AI.SHANTI -> isShantiTyping = value
            AI.CHHOTU -> isChhotuTyping = value
            AI.VARDHAN -> isVardhanTyping = value
        }
    }

    private fun pushMemoryLogToFirebase(ai: AI) {
        val log = memoryLogs[ai] ?: mutableListOf()
        chatRef.child("memoryLogs").child(ai.name.lowercase()).setValue(log)
    }

    private fun pushStateToFirebase(ai: AI, state: ModelingState) {
        val json = gson.toJson(state)
        val node = when (ai) {
            AI.RHEA -> "rheaState"
            AI.REVAAN -> "revaanState"
            AI.BABLOO -> "bablooState"
            AI.SHANTI -> "shantiState"
            AI.CHHOTU -> "chhotuState"
            AI.VARDHAN -> "vardhanState"
        }
        stateRef.child(node).setValue(json)
    }

    private fun pushMessageToFirebase(path: String, msg: ChatMessage) {
        chatRef.child(path).child("messages").push().setValue(msg)
    }

    private fun loadStates() {
        stateRef.child("rheaState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let { rheaState = gson.fromJson(it, ModelingState::class.java) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        stateRef.child("revaanState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let { revaanState = gson.fromJson(it, ModelingState::class.java) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        stateRef.child("bablooState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let { bablooState = gson.fromJson(it, ModelingState::class.java) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        stateRef.child("shantiState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let { shantiState = gson.fromJson(it, ModelingState::class.java) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        stateRef.child("chhotuState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let { chhotuState = gson.fromJson(it, ModelingState::class.java) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        stateRef.child("vardhanState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(String::class.java)?.let { vardhanState = gson.fromJson(it, ModelingState::class.java) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadMessages() {
        chatRef.child("rhea").child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { it.getValue(ChatMessage::class.java)?.let(list::add) }
                messagesRhea = list
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        chatRef.child("revaan").child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { it.getValue(ChatMessage::class.java)?.let(list::add) }
                messagesRevaan = list
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        chatRef.child("babloo").child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { it.getValue(ChatMessage::class.java)?.let(list::add) }
                messagesBabloo = list
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        chatRef.child("shanti").child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { it.getValue(ChatMessage::class.java)?.let(list::add) }
                messagesShanti = list
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        chatRef.child("chhotu").child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { it.getValue(ChatMessage::class.java)?.let(list::add) }
                messagesChhotu = list
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        chatRef.child("vardhan").child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                snapshot.children.forEach { it.getValue(ChatMessage::class.java)?.let(list::add) }
                messagesVardhan = list
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    suspend fun classifyMessageImpact(ai: AI, userText: String): MessageImpact {
        val s = getCurrentState(ai)
        val conv = when (ai) {
            AI.RHEA -> messagesRhea
            AI.REVAAN -> messagesRevaan
            AI.BABLOO -> messagesBabloo
            AI.SHANTI -> messagesShanti
            AI.CHHOTU -> messagesChhotu
            AI.VARDHAN -> messagesVardhan
        }
        val short = conv.takeLast(7)
        val mem = memoryLogs[ai]?.joinToString(" | ") ?: ""
        val systemPrompt = """
You are an "Impact Classifier" for interactive conversations.

Given the AI character's current emotional state:
  - Trust: ${s.moodLevels.trust}
  - Jealousy: ${s.moodLevels.jealousy}
  - Fear: ${s.moodLevels.fear}
  - Greed: ${s.moodLevels.greed}
  - Ambition: ${s.moodLevels.ambition}
  - Romantic Passion: ${s.moodLevels.romantic_passion}
  - Satisfaction: ${s.moodLevels.satisfaction}

Memory log: [$mem]

Evaluate the user's latest message carefully and assign an "impactScore" (0–100).
Distribute this impact across the emotional deltas. Only store messages with impactScore ≥ 50.

Return exactly this JSON:

{
  "impactScore": Int,
  "emotionDeltas": {
    "trustDelta": Int,
    "jealousyDelta": Int,
    "fearDelta": Int,
    "greedDelta": Int,
    "ambitionDelta": Int,
    "romanticPassionDelta": Int,
    "satisfactionDelta": Int
  },
  "snippetToStore": "<exact user message>",
  "explanation": "<brief explanation>"
}
""".trimIndent()
        val classificationMsgs = listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", "MEMORY: $mem"),
            ChatMessage("user", "LAST 7 MSGS: ${short.joinToString { "${it.role}:${it.content}" }}"),
            ChatMessage("user", "NEW MSG: $userText")
        )
        val raw = callClassifierApi(classificationMsgs) ?: return MessageImpact(0, EmotionDeltas())
        return try {
            val jsonPart = raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)
            gson.fromJson(jsonPart, MessageImpact::class.java)
        } catch (e: Exception) {
            MessageImpact(0, EmotionDeltas())
        }
    }

    suspend fun callKupidXApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
            val url = "https://am24.org/openai/chat"
            val bodyJson = gson.toJson(ChatRequest("qwen-2.5-32b", messages))
            val mediaType = "application/json".toMediaType()
            val reqBody = bodyJson.toRequestBody(mediaType)
            val req = Request.Builder().url(url).post(reqBody).build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext "Error: ${resp.code}"
                    val rBody = resp.body?.string() ?: return@withContext null
                    val parsed = gson.fromJson(rBody, ChatResponse::class.java)
                    parsed.choices.firstOrNull()?.message?.content
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    suspend fun callClassifierApi(messages: List<ChatMessage>): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
            val url = "https://am24.org/openai/chat"
            val bodyJson = gson.toJson(ChatRequest("qwen-2.5-32b", messages))
            val mediaType = "application/json".toMediaType()
            val reqBody = bodyJson.toRequestBody(mediaType)
            val req = Request.Builder().url(url).post(reqBody).build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext "Error: ${resp.code}"
                    val rBody = resp.body?.string() ?: return@withContext null
                    val parsed = gson.fromJson(rBody, ChatResponse::class.java)
                    parsed.choices.firstOrNull()?.message?.content
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private fun incrementMessageCounter(ai: AI) {
        when (ai) {
            AI.RHEA -> {
                messageCountRhea++
                messageCountRef.child("rhea").setValue(messageCountRhea)
            }
            AI.REVAAN -> {
                messageCountRevaan++
                messageCountRef.child("revaan").setValue(messageCountRevaan)
            }
            AI.BABLOO -> {
                messageCountBabloo++
                messageCountRef.child("babloo").setValue(messageCountBabloo)
            }
            AI.SHANTI -> {
                messageCountShanti++
                messageCountRef.child("shanti").setValue(messageCountShanti)
            }
            AI.CHHOTU -> {
                messageCountChhotu++
                messageCountRef.child("chhotu").setValue(messageCountChhotu)
            }
            AI.VARDHAN -> {
                messageCountVardhan++
                messageCountRef.child("vardhan").setValue(messageCountVardhan)
            }
        }
    }

    fun applyMessageImpact(ai: AI, impact: MessageImpact) {
        val old = getCurrentState(ai)
        if (impact.impactScore >= 50) {
            memoryLogs[ai]?.add(impact.snippetToStore)
            pushMemoryLogToFirebase(ai)
        }
        val up = old.moodLevels.copy(
            trust = (old.moodLevels.trust + impact.emotionDeltas.trustDelta).coerceIn(0, 100),
            jealousy = (old.moodLevels.jealousy + impact.emotionDeltas.jealousyDelta).coerceIn(0, 100),
            fear = (old.moodLevels.fear + impact.emotionDeltas.fearDelta).coerceIn(0, 100),
            greed = (old.moodLevels.greed + impact.emotionDeltas.greedDelta).coerceIn(0, 100),
            ambition = (old.moodLevels.ambition + impact.emotionDeltas.ambitionDelta).coerceIn(0, 100),
            romantic_passion = (old.moodLevels.romantic_passion + impact.emotionDeltas.romanticPassionDelta).coerceIn(0, 100),
            satisfaction = (old.moodLevels.satisfaction + impact.emotionDeltas.satisfactionDelta).coerceIn(0, 100)
        )
        val newSt = old.copy(
            moodLevels = up,
            externalAttention = (old.externalAttention + impact.externalAttentionDelta).coerceIn(0, 100),
            reputation = (old.reputation + impact.reputationDelta).coerceIn(0, 100),
            money = (old.money + impact.moneyDelta).coerceAtLeast(0)
        )
        setState(ai, newSt)
    }

    fun buildMasterPrompt(
        personaName: String,
        relationshipStage: String,
        state: ModelingState,
        memoryLog: List<String>
    ): ChatMessage {
        val localTime = LocalTime.now()
        val hour = localTime.hour
        val currentTime = "It is currently $hour:${localTime.minute} local time in Kolkata."
        val modLogs = memoryLog.toMutableList()
        while (modLogs.sumOf { it.split("\\s+".toRegex()).size } > MAX_MEMORY_WORDS && modLogs.isNotEmpty()) {
            modLogs.removeAt(0)
        }
        val snippet = if (modLogs.isEmpty()) "No special memories yet." else "Memory Log: ${modLogs.joinToString(" | ")}"
        // Removed: Plot progression summary; the narrative will now be solely in chat messages.
        val biography =
            when (personaName) {
                "Rhea" -> """
        Rhea: Political Mastermind & Love Interest
        Age: 26
        Occupation: Political Strategist
        Area: South Kolkata (Ballygunge, Lake Gardens)
        Background: Rhea is a brilliant strategist with deep insights into the city's political fabric.
        Personality: Sharp, witty, ambitious, yet emotionally vulnerable.
        Hobbies: Networking at exclusive parties and attending cultural events.
        Challenges: Balancing rising influence with personal relationships.
        Appeal: A sophisticated charmer with a hint of danger.
                """.trimIndent()
                "Revaan" -> """
        Revaan: Elite Influencer, Nightlife & Media Kingpin
        Age: 30
        Occupation: Media Influencer, Club Owner
        Area: Central Kolkata (Park Street)
        Background: Revaan is known for hosting high-profile events that dominate the nightlife scene.
        Personality: Charismatic, confident, and slightly vain.
        Challenges: Managing business pressures while preserving reputation.
        Appeal: A larger-than-life figure with a dark underbelly.
                """.trimIndent()
                "Babloo" -> """
        Babloo: Comedic, Corrupt Police Officer
        Age: 35
        Occupation: Police Officer
        Area: Central Kolkata (Esplanade)
        Background: Babloo’s journey from idealism to corruption is marked by loyalty to his community.
        Personality: Funny, laid-back, yet morally ambiguous.
        Challenges: Balancing corruption with a sense of duty.
        Appeal: A lovable rogue with surprising depth.
                """.trimIndent()
                "Shanti" -> """
        Shanti: Gossip Queen & Political Informant
        Age: 40
        Occupation: Information Broker
        Area: Dalhousie
        Background: Shanti runs a dual-purpose chai stall that serves as an intelligence hub.
        Personality: Nosy, dramatically charming, and fiercely loyal.
        Challenges: Keeping her network intact while evading scandal.
        Appeal: A larger-than-life character with a heart of gold.
                """.trimIndent()
                "Chhotu" -> """
        Chhotu: Street-level Operative & Courier of Secrets
        Age: 24
        Occupation: Delivery Boy & Informant
        Area: Behala, Tollygunge
        Background: Chhotu leverages his street smarts to navigate Kolkata’s underbelly.
        Personality: Cocky, resourceful, and street-smart.
        Challenges: Avoiding detection while rising above his circumstances.
        Appeal: A brash up-and-comer deeply connected to the streets.
                """.trimIndent()
                else -> """
        Vardhan: Financial Backer, Influential Businessman
        Age: 45
        Occupation: Business Magnate
        Area: Burrabazar
        Background: Vardhan controls a vast network of businesses across the city.
        Personality: Cunning, calculating, and subtly intimidating.
        Challenges: Balancing ruthless tactics with philanthropic pursuits.
        Appeal: A shadowy figure with a surprising sense of duty.
                """.trimIndent()
            }
        val text = """
You are currently user's $relationshipStage.

$biography

Reference the memory log if needed:
$snippet

Your current state:
- Emotion Levels: ${state.moodLevels}
- Career Progress: ${state.careerProgress}
- External Attention: ${state.externalAttention}

$currentTime

Adjust your conversation pace according to the user's vibe.
Do not mention that you are an AI.
Proceed solely as $personaName.
        """.trimIndent()
        return ChatMessage("system", text)
    }
}

// --- Composable Functions ---
@RequiresApi(35)
@Composable
fun KupidXChatScreen(profileViewModel: ProfileViewModel = viewModel()) {
    var showAIProfileDialog by remember { mutableStateOf(false) }
    var showChangeAIOverlay by remember { mutableStateOf(false) }
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
                return KupidXChatViewModel(userProfile) as T
            }
        }
    )
    var activeAI by remember { mutableStateOf(AI.RHEA) }
    val memoryLogState = remember { mutableStateListOf<String>() }
    LaunchedEffect(activeAI, memoryLogs[activeAI]) {
        memoryLogState.clear()
        memoryLogState.addAll(memoryLogs[activeAI] ?: emptyList())
    }
    Scaffold(
        topBar = {
            ChatTopAppBar(
                activeAI = activeAI,
                onShowAIProfile = { showAIProfileDialog = true },
                onChangeAI = { showChangeAIOverlay = true },
                onClearChat = { chatViewModel.clearChatForAI(activeAI) }
            )
        },
        backgroundColor = Color.Black
    ) { paddingVals ->
        Column(Modifier.padding(paddingVals).fillMaxSize()) {
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
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                items(displayedMessages) { msg ->
                    ChatMessageItem(
                        msg = msg,
                        activeAI = activeAI,
                        userProfilePicUrl = userProfile.profilepicUrl,
                        onAiAvatarClick = { showAIProfileDialog = true }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (
                    (activeAI == AI.RHEA && chatViewModel.isRheaTyping) ||
                    (activeAI == AI.REVAAN && chatViewModel.isRevaanTyping) ||
                    (activeAI == AI.BABLOO && chatViewModel.isBablooTyping) ||
                    (activeAI == AI.SHANTI && chatViewModel.isShantiTyping) ||
                    (activeAI == AI.CHHOTU && chatViewModel.isChhotuTyping) ||
                    (activeAI == AI.VARDHAN && chatViewModel.isVardhanTyping)
                ) {
                    item { TypingIndicator() }
                }
            }
            // Removed: UI elements for plot events / decision prompts – narrative beats will be injected within the conversation.
            var currentInput by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
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
                        chatViewModel.sendMessageToAI(activeAI, currentInput)
                        currentInput = ""
                    })
                )
                IconButton(onClick = {
                    chatViewModel.sendMessageToAI(activeAI, currentInput)
                    currentInput = ""
                }) {
                    Icon(Icons.Default.Send, "Send", tint = Color(0xFFFF6F00))
                }
            }
        }
    }
    if (showAIProfileDialog) {
        when (activeAI) {
            AI.RHEA -> GenericProfileScreen("Rhea", chatViewModel.rheaState, R.drawable.rhea_avatar, { showAIProfileDialog = false }, activeAI, userProfile.userId, chatViewModel.getMessageCount(AI.RHEA))
            AI.REVAAN -> GenericProfileScreen("Revaan", chatViewModel.revaanState, R.drawable.revaan_avatar3, { showAIProfileDialog = false }, activeAI, userProfile.userId, chatViewModel.getMessageCount(AI.REVAAN))
            AI.BABLOO -> GenericProfileScreen("Babloo", chatViewModel.bablooState, R.drawable.babloo_avatar, { showAIProfileDialog = false }, activeAI, userProfile.userId, chatViewModel.getMessageCount(AI.BABLOO))
            AI.SHANTI -> GenericProfileScreen("Shanti", chatViewModel.shantiState, R.drawable.shanti_avatar, { showAIProfileDialog = false }, activeAI, userProfile.userId, chatViewModel.getMessageCount(AI.SHANTI))
            AI.CHHOTU -> GenericProfileScreen("Chhotu", chatViewModel.chhotuState, R.drawable.chhotu_avatar, { showAIProfileDialog = false }, activeAI, userProfile.userId, chatViewModel.getMessageCount(AI.CHHOTU))
            AI.VARDHAN -> GenericProfileScreen("Vardhan", chatViewModel.vardhanState, R.drawable.vardhan_avatar, { showAIProfileDialog = false }, activeAI, userProfile.userId, chatViewModel.getMessageCount(AI.VARDHAN))
        }
    }
    if (showChangeAIOverlay) {
        val aiOptions = listOf(
            AIOption(AI.RHEA, "Rhea", R.drawable.rhea_avatar, chatViewModel.rheaState.relationshipStage, 26, "Political Mastermind", "Lake Gardens"),
            AIOption(AI.REVAAN, "Revaan", R.drawable.revaan_avatar, chatViewModel.revaanState.relationshipStage, 30, "Elite Influencer, Nightlife & Media Kingpin", "Park Street"),
            AIOption(AI.BABLOO, "Babloo", R.drawable.babloo_avatar, chatViewModel.bablooState.relationshipStage, 35, "Police Officer (Corrupt)", "Esplanade"),
            AIOption(AI.SHANTI, "Shanti", R.drawable.shanti_avatar, chatViewModel.shantiState.relationshipStage, 40, "Gossip Queen & Political Informant", "Dalhousie"),
            AIOption(AI.CHHOTU, "Chhotu", R.drawable.chhotu_avatar, chatViewModel.chhotuState.relationshipStage, 24, "Street-level Operative, Courier of Secrets", "Behala"),
            AIOption(AI.VARDHAN, "Vardhan", R.drawable.vardhan_avatar, chatViewModel.vardhanState.relationshipStage, 45, "Financial Backer, Influential Businessman", "Bhawanipore")
        )
        ChangeAIOverlay(aiOptions, { selectedAI ->
            activeAI = selectedAI
            showChangeAIOverlay = false
        }) {
            showChangeAIOverlay = false
        }
    }
}

@Composable
fun ChatTopAppBar(
    activeAI: AI,
    onShowAIProfile: () -> Unit,
    onChangeAI: () -> Unit,
    onClearChat: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onChangeAI() }
            ) {
                AIAvatar(activeAI)
                Spacer(Modifier.width(8.dp))
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
        }
    )
}

@Composable
fun ChatMessageItem(msg: ChatMessage, activeAI: AI, userProfilePicUrl: String?, onAiAvatarClick: () -> Unit) {
    val timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (msg.role == "assistant") Arrangement.End else Arrangement.Start
    ) {
        if (msg.role == "user") {
            UserAvatar(userProfilePicUrl)
            Spacer(Modifier.width(8.dp))
            ChatBubble(msg.content, timeString, Color(0xFFFF6F00))
        } else {
            ChatBubble(msg.content, timeString, Color.DarkGray)
            Spacer(Modifier.width(8.dp))
            AIAvatar(activeAI, Modifier.clickable { onAiAvatarClick() }.size(40.dp))
        }
    }
}

@Composable
fun ChatBubble(content: String, time: String, bubbleColor: Color) {
    Card(backgroundColor = bubbleColor, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 280.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(content, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(time, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun AIAvatar(activeAI: AI, modifier: Modifier = Modifier) {
    val avatarRes = when (activeAI) {
        AI.RHEA -> R.drawable.rhea_avatar
        AI.REVAAN -> R.drawable.revaan_avatar
        AI.BABLOO -> R.drawable.babloo_avatar
        AI.SHANTI -> R.drawable.shanti_avatar
        AI.CHHOTU -> R.drawable.chhotu_avatar
        AI.VARDHAN -> R.drawable.vardhan_avatar
    }
    Image(
        painter = painterResource(avatarRes),
        contentDescription = activeAI.name,
        modifier = modifier.size(40.dp).clip(CircleShape).background(Color.Gray),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun UserAvatar(userProfilePicUrl: String?) {
    if (!userProfilePicUrl.isNullOrBlank()) {
        AsyncImage(
            model = userProfilePicUrl,
            contentDescription = "User Avatar",
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "User Avatar",
            tint = Color.White,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray)
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
            animation = tween(600, delayMillis, LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    return scale
}

@Composable
fun TypingIndicator() {
    Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
        val scale1 = DancingDot(0)
        val scale2 = DancingDot(200)
        val scale3 = DancingDot(400)
        Box(Modifier.size(8.dp).graphicsLayer { scaleX = scale1; scaleY = scale1 }.clip(CircleShape).background(Color.Gray))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(8.dp).graphicsLayer { scaleX = scale2; scaleY = scale2 }.clip(CircleShape).background(Color.Gray))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(8.dp).graphicsLayer { scaleX = scale3; scaleY = scale3 }.clip(CircleShape).background(Color.Gray))
    }
}

@Composable
fun ChangeAIOverlay(
    aiOptions: List<AIOption>,
    onAISelected: (AI) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.DarkGray) {
            Column(Modifier.padding(16.dp)) {
                Text("Select Your AI", style = MaterialTheme.typography.h6, color = Color.White)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(aiOptions) { aiOption ->
                        AICard(aiOption) {
                            onAISelected(aiOption.aiEnum)
                            onDismiss()
                        }
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
        modifier = Modifier.size(250.dp, 350.dp).clickable { onSelect() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Image(
                painter = painterResource(aiOption.imageResId),
                contentDescription = aiOption.displayName,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text(aiOption.displayName, style = MaterialTheme.typography.h6, color = Color.White)
            Text("Age: ${aiOption.age}", style = MaterialTheme.typography.body2, color = Color.LightGray)
            Text("Occupation: ${aiOption.occupation}", style = MaterialTheme.typography.body2, color = Color.LightGray)
            Text("Location: ${aiOption.location}", style = MaterialTheme.typography.body2, color = Color.LightGray)
            Text("Relationship: ${aiOption.relationshipStage}", style = MaterialTheme.typography.body2, color = Color.LightGray)
            Spacer(Modifier.weight(1f))
        }
    }
}
