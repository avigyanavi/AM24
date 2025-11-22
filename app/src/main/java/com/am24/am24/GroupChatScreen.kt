package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.placeholder
import com.google.accompanist.placeholder.material.shimmer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
/**
 * Simple group‑chat screen that stores messages under
 *   messages/{groupId}
 * and reuses the same visual style as one‑to‑one ChatScreen.
 */

data class GroupChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderUsername: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

private sealed interface ValidationResult {
    data class Valid(val message: GroupChatMessage) : ValidationResult
    data class Invalid(val messageId: String) : ValidationResult
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    navController: NavController,
    groupId: String
) {
    /* ---------- Firebase handles ---------- */
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database      = FirebaseRefs.db
    val usersRef      = database.getReference("users")
    val messagesRef   = database.getReference("messages").child(groupId)

    /* ---------- current user profile ---------- */
    var currentUserProfile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(currentUserId) {
        usersRef.child(currentUserId).get().addOnSuccessListener { snap ->
            currentUserProfile = snap.getValue(Profile::class.java)
        }
    }

    val matches = remember { mutableStateListOf<String>() }
    LaunchedEffect(currentUserId) {
        FirebaseRefs.db.getReference("matches")
            .child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    matches.clear()
                    snapshot.children.forEach { it.key?.let(matches::add) }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /* ---------- local UI state ---------- */
    val messages = remember { mutableStateListOf<GroupChatMessage>() }
    var messageText by remember { mutableStateOf("") }
    var isMessagesLoading by remember { mutableStateOf(true) }
    val userIdentityCache = remember { mutableMapOf<String, Pair<String, String>>() }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isMessagesLoading) {
        if (!isMessagesLoading && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    /* ---------- real‑time listener ---------- */
    DisposableEffect(groupId) {
        val processingJob = SupervisorJob()
        val processingScope = CoroutineScope(Dispatchers.Main.immediate + processingJob)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!processingJob.isActive) return
                processingScope.launch {
                    try {
                        val newList = snapshot.children.mapNotNull { it.getValue(GroupChatMessage::class.java) }
                        val validated = coroutineScope {
                            newList.map { message ->
                                async {
                                    val senderId = message.senderId.trim()
                                    if (senderId.isBlank()) {
                                        ValidationResult.Invalid(message.id)
                                    } else {
                                        val cached = userIdentityCache[senderId]
                                        val resolvedName = message.senderName.ifBlank { cached?.first ?: "" }
                                        val resolvedUsername = message.senderUsername.ifBlank { cached?.second ?: "" }

                                        val (name, username) = if (resolvedName.isBlank() || resolvedUsername.isBlank()) {
                                            try {
                                                val userSnap = withContext(Dispatchers.IO) {
                                                    usersRef.child(senderId).get().await()
                                                }
                                                val profile = userSnap.getValue(Profile::class.java)
                                                val fetchedName = resolvedName.ifBlank { profile?.name.orEmpty() }
                                                val fetchedUsername = resolvedUsername.ifBlank { profile?.username.orEmpty() }
                                                userIdentityCache[senderId] = fetchedName to fetchedUsername
                                                fetchedName to fetchedUsername
                                            } catch (_: Exception) {
                                                resolvedName to resolvedUsername
                                            }
                                        } else {
                                            resolvedName to resolvedUsername
                                        }

                                        val enrichedMessage = message.copy(
                                            senderName = name.ifBlank { "User" },
                                            senderUsername = username
                                        )
                                        try {
                                            if (UserDeletionCache.isDeleted(database, senderId)) {
                                                UserDeletionCache.markDeleted(senderId)
                                                ValidationResult.Invalid(message.id)
                                            } else {
                                                UserDeletionCache.markActive(senderId)
                                                ValidationResult.Valid(enrichedMessage)
                                            }
                                        } catch (ce: CancellationException) {
                                            throw ce
                                        } catch (_: Exception) {
                                            ValidationResult.Valid(enrichedMessage)
                                        }
                                    }
                                }
                            }.awaitAll()
                        }

                        val validMessages = mutableListOf<GroupChatMessage>()
                        val staleMessageIds = mutableListOf<String>()

                        validated.forEach { result ->
                            when (result) {
                                is ValidationResult.Valid -> validMessages += result.message
                                is ValidationResult.Invalid -> if (result.messageId.isNotBlank()) {
                                    staleMessageIds += result.messageId
                                }
                            }
                        }
                        messages.clear()
                        messages.addAll(validMessages.sortedBy { it.timestamp })

                        staleMessageIds.distinct().forEach { id ->
                            messagesRef.child(id).removeValue()
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        messages.clear()
                    } finally {
                        isMessagesLoading = false
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                if (!processingJob.isActive) return
                processingScope.launch {
                    messages.clear()
                    isMessagesLoading = false
                }
            }
        }
        messagesRef.addValueEventListener(listener)
        onDispose {
            messagesRef.removeEventListener(listener)
            processingJob.cancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        /* ----- messages list ----- */
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            state = listState,
            verticalArrangement = if (messages.isNotEmpty()) Arrangement.Bottom else Arrangement.Top
        ) {
            when {
                isMessagesLoading -> {
                    items(6) { index ->
                        GroupMessageSkeleton(isCurrentUser = index % 2 == 0)
                    }
                }

                else -> {
                    items(messages) { msg ->
                        GroupMessageBubble(
                            message = msg,
                            navController = navController,
                            currentUserId = currentUserId,
                            matches = matches
                        )
                    }
                }
            }
        }

        /* ----- input row ----- */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Type a message…", color = Color.Gray) },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.DarkGray, RoundedCornerShape(24.dp)),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.DarkGray,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    focusedPlaceholderColor = Color.Gray,
                    cursorColor             = KupidxOrange
                ),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        val displayName = currentUserProfile?.name ?: "User"
                        val username = currentUserProfile?.username.orEmpty()
                        sendGroupChatMessage(
                            userId = currentUserId,
                            userName = displayName,
                            userUsername = username,
                            text = messageText,
                            messagesRef = messagesRef
                        )
                        messageText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFFF4500), CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

/* ---------------- helper composables / functions ---------------- */

@Composable
fun GroupMessageBubble(
    message: GroupChatMessage,
    navController: NavController,
    currentUserId: String,
    matches: List<String>
) {
    val isCurrentUser = message.senderId == currentUserId
    val bubbleColor   = if (isCurrentUser) Color(0xFFFFDB00) else Color(0xFFFF6F00)
    val textColor     = if (isCurrentUser) Color.Black else Color.White
    val headerLabel = when {
        message.senderName.isNotBlank() && message.senderUsername.isNotBlank() ->
            "${message.senderName} (@${message.senderUsername})"
        message.senderName.isNotBlank() -> message.senderName
        message.senderUsername.isNotBlank() -> "@${message.senderUsername}"
        else -> "Unknown user"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = headerLabel,
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        Box(
            modifier = Modifier
                .clickable {
                    when {
                        message.senderId == currentUserId ->
                            navController.navigate("profile")
                        matches.contains(message.senderId) ->
                            navController.navigate("matchedUserProfile/${message.senderId}")
                        else ->
                            navController.navigate("previewUserProfile/${message.senderId}")
                    }
                }
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(message.text, color = textColor, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatRelativeTime(message.timestamp),
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GroupMessageSkeleton(isCurrentUser: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .height(12.dp)
                .width(90.dp)
                .placeholder(
                    visible = true,
                    color = Color.DarkGray,
                    highlight = PlaceholderHighlight.shimmer()
                )
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .fillMaxWidth(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .placeholder(
                    visible = true,
                    color = Color.DarkGray,
                    highlight = PlaceholderHighlight.shimmer()
                )
        )
    }
}

fun sendGroupChatMessage(
    userId: String,
    userName: String,
    userUsername: String,
    text: String,
    messagesRef: DatabaseReference
) {
    val newId = messagesRef.push().key ?: return
    val msg = GroupChatMessage(
        id = newId,
        senderId = userId,
        senderName = userName,
        senderUsername = userUsername,
        text = text,
        timestamp = System.currentTimeMillis()
    )
    messagesRef.child(newId).setValue(msg)
}

fun formatGroupTitle(raw: String): String = when (raw) {
    "group_wb" -> "West Bengal"
    "group_usa" -> "United States"
    "group_mexico" -> "Mexico"
    else -> raw.removePrefix("group_")
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() } + " Chat"
}
