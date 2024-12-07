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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
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

@Composable
fun ChatScreen(navController: NavController, otherUserId: String) {
    ChatScreenContent(navController = navController, otherUserId = otherUserId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(navController: NavController, otherUserId: String) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val database = FirebaseDatabase.getInstance()
    val usersRef = database.getReference("users")
    val chatId = getChatId(currentUserId, otherUserId)
    val messagesRef = database.getReference("messages/$chatId")
    val ratingsRef = database.getReference("ratings")

    var yourRating by rememberSaveable(otherUserId) { mutableStateOf(-1.0) }
    var averageRating by remember { mutableStateOf(0.0) }
    var otherUserProfile by remember { mutableStateOf<Profile?>(null) }
    val messages = remember { mutableStateListOf<Message>() }
    var messageText by remember { mutableStateOf("") }

    // Fetch other user's profile and rating info
    LaunchedEffect(otherUserId) {
        usersRef.child(otherUserId).get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(Profile::class.java)
            if (profile != null) {
                otherUserProfile = profile
                averageRating = profile.averageRating
            }
        }.addOnFailureListener { exception ->
            Log.e("ChatScreen", "Failed to fetch user: ${exception.message}")
            Toast.makeText(context, "Failed to load user", Toast.LENGTH_SHORT).show()
        }

        fetchUserRating(ratingsRef, otherUserId) { rating ->
            yourRating = rating
        }

        fetchAverageRating(ratingsRef, otherUserId) { avg ->
            averageRating = avg
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
                newMessages.sortBy { it.timestamp }
                messages.clear()
                messages.addAll(newMessages)

                // Mark messages as read if current user is the receiver
                markMessagesAsRead(messagesRef, messages, currentUserId)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatScreen", "DatabaseError: ${error.message}")
                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // TopAppBar
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
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black),
            modifier = Modifier
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets(0, 0)) // Removes padding for status bar
        )

        // Content Section
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f) // Ensures the content section takes remaining space
        ) {
            // Rating Info and Slider
            if (otherUserProfile != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Avg Rating: ${String.format("%.1f", averageRating)}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Your Rating: ${if (yourRating >= 0) String.format("%.1f", yourRating) else "N/A"}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = if (yourRating >= 0) yourRating.toFloat() else 0f,
                        onValueChange = { yourRating = it.toDouble() },
                        onValueChangeFinished = {
                            if (yourRating >= 0) {
                                updateUserRating(ratingsRef, usersRef, otherUserId, yourRating, context)
                            }
                        },
                        valueRange = 0f..5f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF4500),
                            activeTrackColor = Color(0xFFFF4500)
                        )
                    )
                }
            }

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
                    .background(Color(0xFFFF4500), shape = CircleShape)
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

@Composable
fun MessageBubble(message: Message, currentUserId: String) {
    val isCurrentUser = message.senderId == currentUserId
    // Determine ticks
    val ticks = if (isCurrentUser) {
        if (message.read) "✔✔" else "✔"
    } else {
        ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                if (ticks.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = ticks,
                        color = Color(0xFFFF4500), // Ticks are Blue
                        fontSize = 12.sp
                    )
                }
            }
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
        timestamp = timestamp,
        read = false // Not read at the time of sending
    )
    messagesRef.child(messageId).setValue(message)
}

fun markMessagesAsRead(messagesRef: DatabaseReference, messages: List<Message>, currentUserId: String) {
    // Mark messages as read if the current user is the receiver of these messages
    val unreadMessages = messages.filter { it.receiverId == currentUserId && !it.read }
    for (msg in unreadMessages) {
        messagesRef.child(msg.id).child("read").setValue(true)
    }
}

// Fetch rating and average rating functions
fun fetchAverageRating(
    ratingsRef: DatabaseReference,
    userId: String,
    onAverageFetched: (Double) -> Unit
) {
    ratingsRef.child(userId).child("averageRating").get().addOnSuccessListener { snapshot ->
        val average = snapshot.getValue(Double::class.java) ?: 0.0
        onAverageFetched(average)
    }.addOnFailureListener {
        onAverageFetched(0.0)
    }
}

fun fetchUserRating(
    ratingsRef: DatabaseReference,
    userId: String,
    onRatingFetched: (Double) -> Unit
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    ratingsRef.child(userId).child("ratings").child(currentUserId).get()
        .addOnSuccessListener { snapshot ->
            val yourRating = snapshot.getValue(Double::class.java) ?: 0.0
            onRatingFetched(yourRating)
        }
        .addOnFailureListener {
            onRatingFetched(0.0)
        }
}

fun updateUserRating(
    ratingsRef: DatabaseReference,
    usersRef: DatabaseReference,
    userId: String,
    rating: Double,
    context: android.content.Context
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRatingRef = ratingsRef.child(userId)
    userRatingRef.get().addOnSuccessListener { snapshot ->
        val ratingsMap = if (snapshot.exists() && snapshot.child("ratings").value is Map<*, *>) {
            snapshot.child("ratings")
                .getValue(object : GenericTypeIndicator<MutableMap<String, Double>>() {}) ?: mutableMapOf()
        } else {
            mutableMapOf()
        }

        ratingsMap[currentUserId] = rating
        val averageRating = if (ratingsMap.isNotEmpty()) ratingsMap.values.average() else 0.0

        val updates = mapOf(
            "ratings" to ratingsMap,
            "averageRating" to averageRating
        )
        userRatingRef.updateChildren(updates).addOnSuccessListener {
            usersRef.child(userId).child("averageRating").setValue(averageRating)
                .addOnSuccessListener {
                    Toast.makeText(context, "Rating updated!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to update average rating in profile.", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to update ratings.", Toast.LENGTH_SHORT).show()
        }
    }.addOnFailureListener {
        Toast.makeText(context, "Failed to fetch current ratings.", Toast.LENGTH_SHORT).show()
    }
}
