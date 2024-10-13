// ChatScreen.kt

@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@Composable
fun ChatScreen(navController: NavController, otherUserId: String) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val messages = remember { mutableStateListOf<Message>() }
    val focusManager = LocalFocusManager.current

    var messageText by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0f) }

    // Fetch messages
    LaunchedEffect(Unit) {
        val database = FirebaseDatabase.getInstance()
        val messagesRef = database.getReference("messages/${getChatId(currentUserId, otherUserId)}")

        messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)
                if (message != null) {
                    messages.add(message)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }

            // Other methods omitted for brevity
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Bar with Username, DP, and Options
        TopAppBar(
            title = {
                Text(text = "Chat with ${otherUserId}", color = Color.White)
            },
            actions = {
                IconButton(onClick = { /* Show options menu */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
        )

        // Rating Bar and Clear Rating Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Your Rating:", color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            RatingBar(
                rating = rating,
                onRatingChanged = { newRating ->
                    rating = newRating
                    // Update rating in database
                    updateRating(currentUserId, otherUserId, rating)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                rating = 0f
                updateRating(currentUserId, otherUserId, rating)
            }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear Rating", tint = Color.White)
            }
        }

        // Messages List
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(messages) { message ->
                MessageItem(message = message, isCurrentUser = message.senderId == currentUserId)
            }
        }

        // Message Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = "Type a message", color = Color.Gray) },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color.White,
                    focusedTextColor = Color.Black,
                    focusedPlaceholderColor = Color.Gray
                ),
                keyboardActions = KeyboardActions(onDone = {
                    sendMessage(currentUserId, otherUserId, messageText)
                    messageText = ""
                    focusManager.clearFocus()
                })
            )
            IconButton(onClick = {
                sendMessage(currentUserId, otherUserId, messageText)
                messageText = ""
                focusManager.clearFocus()
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

fun getChatId(userId1: String, userId2: String): String {
    return if (userId1 < userId2) "$userId1-$userId2" else "$userId2-$userId1"
}

fun sendMessage(currentUserId: String, otherUserId: String, messageText: String) {
    val database = FirebaseDatabase.getInstance()
    val chatId = getChatId(currentUserId, otherUserId)
    val messagesRef = database.getReference("messages/$chatId")

    val messageId = messagesRef.push().key ?: return
    val message = Message(
        id = messageId,
        senderId = currentUserId,
        receiverId = otherUserId,
        text = messageText,
        timestamp = System.currentTimeMillis()
    )
    messagesRef.child(messageId).setValue(message)
}

fun updateRating(currentUserId: String, otherUserId: String, rating: Float) {
    val database = FirebaseDatabase.getInstance()
    val ratingsRef = database.getReference("ratings/$otherUserId/$currentUserId")
    ratingsRef.setValue(rating)
}

@Composable
fun RatingBar(
    rating: Float,
    onRatingChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    stars: Int = 5,
    starSize: Dp = 24.dp,
    starColor: Color = Color.Yellow,
    starBackgroundColor: Color = Color.Gray
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..stars) {
            val filled = i <= rating.toInt()
            IconButton(onClick = { onRatingChanged(i.toFloat()) }) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Star",
                    tint = if (filled) starColor else starBackgroundColor,
                    modifier = Modifier.size(starSize)
                )
            }
        }
    }
}

@Composable
fun MessageItem(message: Message, isCurrentUser: Boolean) {
    // Display message bubbles
    // ...
}
