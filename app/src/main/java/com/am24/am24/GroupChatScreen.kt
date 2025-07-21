package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple group‑chat screen that stores messages under
 *   messages/{groupId}
 * and reuses the same visual style as one‑to‑one ChatScreen.
 */

data class GroupChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

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

    /* ---------- real‑time listener ---------- */
    DisposableEffect(groupId) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = snapshot.children.mapNotNull { it.getValue(GroupChatMessage::class.java) }
                messages.clear()
                messages.addAll(newList.sortedBy { it.timestamp })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        messagesRef.addValueEventListener(listener)
        onDispose { messagesRef.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatGroupTitle(groupId), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            /* ----- messages list ----- */
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                items(messages) { msg ->
                    GroupMessageBubble(
                        message = msg,
                        navController = navController,
                        currentUserId = currentUserId,
                        matches = matches
                    )
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
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.DarkGray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        focusedPlaceholderColor = Color.Gray
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            val displayName = currentUserProfile?.name ?: "User"
                            sendGroupChatMessage(
                                userId = currentUserId,
                                userName = displayName,
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


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.senderName,
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

fun sendGroupChatMessage(
    userId: String,
    userName: String,
    text: String,
    messagesRef: DatabaseReference
) {
    val newId = messagesRef.push().key ?: return
    val msg = GroupChatMessage(
        id = newId,
        senderId = userId,
        senderName = userName,
        text = text,
        timestamp = System.currentTimeMillis()
    )
    messagesRef.child(newId).setValue(msg)
}

fun formatGroupTitle(raw: String): String = when (raw) {
    "group_wb" -> "West Bengal"
    else -> raw.removePrefix("group_").replaceFirstChar { it.uppercase() } + " Chat"
}
