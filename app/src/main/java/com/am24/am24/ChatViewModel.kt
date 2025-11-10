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

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val database: FirebaseDatabase = FirebaseRefs.db
    private val usersRef: DatabaseReference = database.getReference("users")
    private val ratingsRef: DatabaseReference = database.getReference("ratings")

    private var messagesRef: DatabaseReference? = null
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

        loadOtherUserProfile(otherUid)
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

    private fun loadOtherUserProfile(otherUid: String) {
        _uiState.update { it.copy(isLoadingProfiles = true) }
        usersRef.child(otherUid).get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.getValue(Profile::class.java)
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
                            timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            read = map["read"] as? Boolean ?: false,
                            mediaType = map["mediaType"] as? String,
                            mediaUrl = map["mediaUrl"] as? String,
                            processed = map["processed"] as? Boolean ?: false,
                            isPost = map["isPost"] as? Boolean ?: false
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
        ref.addValueEventListener(listener)
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

    private fun clearListeners() {
        messagesListener?.let { listener -> messagesRef?.removeEventListener(listener) }
        typingListener?.let { listener -> typingRef?.removeEventListener(listener) }
        messagesListener = null
        typingListener = null
        messagesRef = null
        typingRef = null
    }

    override fun onCleared() {
        super.onCleared()
        clearListeners()
    }
}