package com.am24.am24

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.functions.FirebaseFunctions
import java.util.concurrent.TimeUnit
import com.google.firebase.database.Query
data class ChatUiState(
    val otherUserProfile: Profile? = null,
    val messages: List<Message> = emptyList(),
    val isOtherUserTyping: Boolean = false,
    val isLoadingProfiles: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val averageRating: Double = 0.0,
    val yourRating: Double = -1.0,
    val aiMatchResult: AiMatchCheckResult? = null
)

class ChatViewModel : ViewModel() {
    companion object {
        private const val MESSAGE_HISTORY_LIMIT = 300
    }
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val database: FirebaseDatabase = FirebaseRefs.db
    private val usersRef: DatabaseReference = database.getReference("users")
    private val ratingsRef: DatabaseReference = database.getReference("ratings")
    private val functions = FirebaseFunctions.getInstance("asia-south1")

    private var messagesRef: DatabaseReference? = null
    private var messagesQuery: Query? = null
    private var typingRef: DatabaseReference? = null
    private var messagesListener: ValueEventListener? = null
    private var typingListener: ValueEventListener? = null

    private var started = false
    private var currentUserId: String? = null
    private var otherUserId: String? = null
    private var chatId: String? = null

    fun startSession(currentUid: String, otherUid: String) {
        if (started && currentUid == currentUserId && otherUid == otherUserId) {
            return
        }
        clearListeners()
        started = true
        currentUserId = currentUid
        otherUserId = otherUid
        chatId = getChatId(currentUid, otherUid)
        _uiState.value = ChatUiState(isLoadingMessages = true, isLoadingProfiles = true)

        val chatPath = chatId ?: return
        messagesRef = database.getReference("messages/$chatPath")
        typingRef = database.getReference("typing/$chatPath/$otherUid")

        fetchChatBootstrap(currentUid, otherUid)
        observeMessages()
        observeTyping()
        fetchRatings(otherUid)
        fetchExistingAiMatch(currentUid, otherUid)
    }

    val messagesReference: DatabaseReference?
        get() = messagesRef

    val chatIdentifier: String?
        get() = chatId

    fun setCurrentUserTyping(isTyping: Boolean) {
        val chatPath = chatId ?: return
        val uid = currentUserId ?: return
        database.getReference("typing/$chatPath/$uid").setValue(isTyping)
    }

    fun updateYourRating(rating: Double) {
        _uiState.update { it.copy(yourRating = rating) }
    }

    fun updateAverageRating(value: Double) {
        _uiState.update { it.copy(averageRating = value) }
    }

    fun updateAiMatch(result: AiMatchCheckResult) {
        _uiState.update { it.copy(aiMatchResult = result) }
    }

    fun clearConversation(onComplete: (() -> Unit)? = null) {
        messagesRef?.setValue(null)?.addOnCompleteListener { onComplete?.invoke() }
    }

    private fun fetchChatBootstrap(currentUid: String, otherUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = hashMapOf(
                    "otherUid" to otherUid,
                    "limit" to 50
                )
                val callable = functions.getHttpsCallable("fetchChatBootstrap").apply {
                    setTimeout(30, TimeUnit.SECONDS)
                }
                val data = callable.call(payload).await().data as? Map<*, *>
                if (data == null) {
                    loadOtherUserProfileFallback(otherUid)
                    _uiState.update { it.copy(isLoadingProfiles = false, isLoadingMessages = false) }
                    return@launch
                }
                val profile = (data["profile"] as? Map<*, *>)?.safeMapToProfile("chatBootstrap/profile")
                val messages = (data["messages"] as? List<*>)
                    ?.mapNotNull { (it as? Map<*, *>)?.toMessage() }
                    ?: emptyList()

                _uiState.update { state ->
                    state.copy(
                        otherUserProfile = profile ?: state.otherUserProfile,
                        messages = if (messages.isNotEmpty()) messages else state.messages,
                        isLoadingProfiles = false,
                        isLoadingMessages = false,
                        averageRating = profile?.averageRating ?: state.averageRating
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "fetchChatBootstrap failed: ${e.message}", e)
                _uiState.update { it.copy(isLoadingProfiles = false, isLoadingMessages = false) }
                loadOtherUserProfileFallback(otherUid)
            }
        }
    }

    private fun loadOtherUserProfileFallback(otherUid: String) {
        usersRef.child(otherUid).get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.safeGetProfile("chatProfileFallback/$otherUid")
                _uiState.update {
                    it.copy(
                        otherUserProfile = profile,
                        isLoadingProfiles = false,
                        averageRating = profile?.averageRating ?: it.averageRating
                    )
                }
            }
            .addOnFailureListener { error ->
                Log.e("ChatViewModel", "Failed loading profile for $otherUid: ${error.message}")
                _uiState.update { it.copy(isLoadingProfiles = false) }
            }
    }

    private fun observeTyping() {
        val ref = typingRef ?: return
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isTyping = when (val raw = snapshot.value) {
                    is Boolean -> raw
                    is Number -> raw.toInt() != 0
                    is String -> raw.equals("true", ignoreCase = true) || raw == "1"
                    else -> false
                }
                _uiState.update { it.copy(isOtherUserTyping = isTyping) }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Typing listener cancelled: ${error.message}")
            }
        }
        typingListener = listener
        ref.addValueEventListener(listener)
    }

    private fun observeMessages() {
        val ref = messagesRef ?: return
        val query = ref.limitToLast(MESSAGE_HISTORY_LIMIT)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    if (child.key == "participants") return@mapNotNull null
                    try {
                        val map = child.value as? Map<*, *> ?: return@mapNotNull null
                        Message(
                            id = map["id"] as? String ?: "",
                            senderId = map["senderId"] as? String ?: "",
                            receiverId = map["receiverId"] as? String ?: "",
                            text = map["text"] as? String ?: "",
                            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            read = map["read"] as? Boolean ?: false,
                            mediaType = map["mediaType"] as? String,
                            mediaUrl = map["mediaUrl"] as? String,
                            processed = map["processed"] as? Boolean ?: false,
                            isPost = map["isPost"] as? Boolean ?: false,
                            reaction   = map["reaction"] as? String    // ✅ ADD THIS
                        )
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed parsing message ${child.key}: ${e.message}")
                        null
                    }
                }
                Log.d("ChatViewModel", "Loaded ${list.size} messages for chatId=$chatId")
                _uiState.update { it.copy(messages = list, isLoadingMessages = false) }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Messages listener cancelled: ${error.message}")
                _uiState.update { it.copy(isLoadingMessages = false) }
            }
        }
        messagesListener = listener
        messagesQuery = query
        query.addValueEventListener(listener)
    }

    private fun fetchRatings(otherUid: String) {
        fetchUserRating(ratingsRef, otherUid) { rating ->
            _uiState.update { it.copy(yourRating = rating) }
        }
        fetchAverageRating(ratingsRef, otherUid) { average ->
            _uiState.update { it.copy(averageRating = average) }
        }
    }

    private fun fetchExistingAiMatch(currentUid: String, otherUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = database
                    .getReference("aiMatchCheck/$currentUid/$otherUid")
                    .get()
                    .await()
                val existing = snapshot.getValue(AiMatchCheckResult::class.java)
                if (existing != null) {
                    _uiState.update { it.copy(aiMatchResult = existing) }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed loading AI match result: ${e.message}")
            }
        }
    }

    private fun Map<*, *>.toMessage(): Message = Message(
        id = this["id"] as? String ?: "",
        senderId = this["senderId"] as? String ?: "",
        receiverId = this["receiverId"] as? String ?: "",
        text = this["text"] as? String ?: "",
        timestamp = (this["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        read = this["read"] as? Boolean ?: false,
        mediaType = this["mediaType"] as? String,
        mediaUrl = this["mediaUrl"] as? String,
        processed = this["processed"] as? Boolean ?: false,
        isPost = this["isPost"] as? Boolean ?: false,
        reaction = this["reaction"] as? String
    )

    private fun clearListeners() {
        messagesListener?.let { listener -> messagesQuery?.removeEventListener(listener) }
        typingListener?.let { listener -> typingRef?.removeEventListener(listener) }
        messagesListener = null
        typingListener = null
        messagesRef = null
        messagesQuery = null
        typingRef = null
    }

    override fun onCleared() {
        super.onCleared()
        clearListeners()
    }
}