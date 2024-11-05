// ChatScreen.kt

@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*


@Composable
fun ChatScreen(navController: NavController, otherUserId: String) {
    ChatScreenContent(navController = navController, otherUserId = otherUserId)
}

@Composable
fun ChatScreenContent(navController: NavController, otherUserId: String) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val database = FirebaseDatabase.getInstance()
    val usersRef = database.getReference("users")
    val chatId = getChatId(currentUserId, otherUserId)
    val messagesRef = database.getReference("messages/$chatId")

    var otherUserProfile by remember { mutableStateOf<Profile?>(null) }
    val messages = remember { mutableStateListOf<Message>() }
    var messageText by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    // Fetch other user's profile
    LaunchedEffect(otherUserId) {
        usersRef.child(otherUserId).get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(Profile::class.java)
            if (profile != null) {
                otherUserProfile = profile
            }
        }.addOnFailureListener { exception ->
            Log.e("ChatScreen", "Failed to fetch user: ${exception.message}")
            Toast.makeText(context, "Failed to load user", Toast.LENGTH_SHORT).show()
        }
    }

    // Listen for messages
    LaunchedEffect(chatId) {
        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newMessages = mutableListOf<Message>()
                for (messageSnapshot in snapshot.children) {
                    val message = messageSnapshot.getValue(Message::class.java)
                    if (message != null) {
                        newMessages.add(message)
                    }
                }
                // Sort messages by timestamp
                newMessages.sortBy { it.timestamp }
                messages.clear()
                messages.addAll(newMessages)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatScreen", "DatabaseError: ${error.message}")
                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (otherUserProfile != null && otherUserProfile!!.profilepicUrl?.isNotBlank() == true) {
                            AsyncImage(
                                model = otherUserProfile!!.profilepicUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = otherUserProfile!!.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Chat",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .padding(paddingValues)
            ) {
                // Messages List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    items(messages.reversed()) { message ->
                        MessageBubble(message = message, currentUserId = currentUserId)
                    }
                }

                // Input Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message...", color = Color.Gray) },
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.DarkGray, shape = RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.textFieldColors(
                            containerColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            focusedPlaceholderColor = Color.Gray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (messageText.isNotBlank()) {
                                    sendMessage(
                                        currentUserId = currentUserId,
                                        otherUserId = otherUserId,
                                        chatId = chatId,
                                        messageText = messageText,
                                        messagesRef = messagesRef
                                    )
                                    messageText = ""
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                sendMessage(
                                    currentUserId = currentUserId,
                                    otherUserId = otherUserId,
                                    chatId = chatId,
                                    messageText = messageText,
                                    messagesRef = messagesRef
                                )
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF00bf63), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun MessageBubble(message: Message, currentUserId: String) {
    val isCurrentUser = message.senderId == currentUserId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = if (isCurrentUser) Color(0xFF00bf63) else Color.DarkGray,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimestamp(message.timestamp),
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}

fun getChatId(userId1: String, userId2: String): String {
    return if (userId1 < userId2) {
        "${userId1}_$userId2"
    } else {
        "${userId2}_$userId1"
    }
}

fun sendMessage(
    currentUserId: String,
    otherUserId: String,
    chatId: String,
    messageText: String,
    messagesRef: DatabaseReference
) {
    val timestamp = System.currentTimeMillis()
    val messageId = messagesRef.push().key ?: return
    val message = Message(
        id = messageId,
        senderId = currentUserId,
        receiverId = otherUserId,
        text = messageText,
        timestamp = timestamp
    )
    messagesRef.child(messageId).setValue(message)
}
