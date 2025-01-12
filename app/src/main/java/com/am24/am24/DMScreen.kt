@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@Composable
fun DMScreen(navController: NavController) {
    DMScreenContent(navController = navController)
}

@Composable
fun DMScreenContent(navController: NavController) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val context = LocalContext.current

    val database = FirebaseDatabase.getInstance()
    val matchesRef = database.getReference("matches/$currentUserId")
    val usersRef = database.getReference("users")
    val messagesRootRef = database.getReference("messages")

    val matchedUsers = remember { mutableStateListOf<Profile>() }
    val nonInitiatedMatches = remember { mutableStateListOf<Profile>() }
    var searchQuery by remember { mutableStateOf("") }

    var likedCount by remember { mutableStateOf(0) }
    LaunchedEffect(currentUserId) {
        val likesReceivedRef = database.getReference("likesReceived/$currentUserId")
        likesReceivedRef.get().addOnSuccessListener {
            likedCount = it.childrenCount.toInt()
        }
    }

    // lastMessages map: Key = userId, Value = Triple(msg, fromCurrentUser, read)
    val lastMessages = remember { mutableStateMapOf<String, Triple<String, Boolean, Boolean>>() }

    LaunchedEffect(currentUserId) {
        fetchUsersFromNode(matchesRef, usersRef, matchedUsers, context) {
            checkNonInitiatedConversations(matchedUsers, messagesRootRef, currentUserId) { nonInitiated ->
                nonInitiatedMatches.clear()
                nonInitiatedMatches.addAll(nonInitiated)
            }

            // Listen for last message updates in real-time
            matchedUsers.forEach { profile ->
                val chatId = getChatId(currentUserId, profile.userId)
                messagesRootRef.child(chatId)
                    .orderByChild("timestamp")
                    .limitToLast(1)
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (!snapshot.exists()) {
                                lastMessages[profile.userId] = Triple("", false, true)
                                return
                            }
                            for (msgSnap in snapshot.children) {
                                val text = msgSnap.child("text").getValue(String::class.java) ?: ""
                                val senderId = msgSnap.child("senderId").getValue(String::class.java) ?: ""
                                // Default to false if not present
                                val read = msgSnap.child("read").getValue(Boolean::class.java) ?: false
                                val fromCurrentUser = (senderId == currentUserId)
                                lastMessages[profile.userId] = Triple(text, fromCurrentUser, read)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("DMScreen", "Failed to listen last message: ${error.message}")
                        }
                    })
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search matches", color = Color.Gray) },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFFFF4500),
                unfocusedBorderColor = Color.Gray,
                cursorColor = Color(0xFFFF4500),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.Gray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp, start = 10.dp, end = 10.dp)
        )

        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Instead of toast, navigate to peopleWhoLikedMe
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .clickable {
                        navController.navigate("peopleWhoLikedMe")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$likedCount",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            for (profile in nonInitiatedMatches) {
                AsyncImage(
                    model = profile.profilepicUrl,
                    contentDescription = profile.username,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                        .clickable {
                            navController.navigate("chat/${profile.userId}")
                        },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        val displayedUsers = matchedUsers.filter {
            it.username.contains(searchQuery, ignoreCase = true) || it.name.contains(searchQuery, ignoreCase = true)
        }

        if (displayedUsers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matches found",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(displayedUsers) { profile ->
                    val lastMsgState = lastMessages[profile.userId] ?: Triple("", false, true)
                    DMUserCard(
                        profile = profile,
                        navController = navController,
                        lastMessage = lastMsgState.first,
                        lastMessageFromCurrentUser = lastMsgState.second,
                        lastMessageRead = lastMsgState.third
                    )
                }
            }
        }
    }
}

@Composable
fun DMUserCard(
    profile: Profile,
    navController: NavController,
    lastMessage: String,
    lastMessageFromCurrentUser: Boolean,
    lastMessageRead: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .border(BorderStroke(2.dp, getLevelBorderColor(profile.averageRating)), shape = RoundedCornerShape(8.dp))
            .clickable { navController.navigate("chat/${profile.userId}") }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            AsyncImage(
                model = profile.profilepicUrl,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Gray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Display username
                Text(
                    text = profile.username,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                // Calculate age
                val age = profile.dob?.let { calculateAge(it) }
                // Location info
                val locality = profile.hometown
                val city = profile.city

                if(profile.hometown != "") {
                    Text(
                        text = "${locality}, ${profile.city}, Age: ${age ?: ""}",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                } else {
                    // Show username, location, age
                    Text(
                        text = "${city ?: ""}, Age: ${age ?: ""}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                val messageText = when {
                    lastMessage.isEmpty() -> "No messages yet"
                    lastMessageFromCurrentUser -> "Sent: $lastMessage"
                    else -> lastMessage
                }

                // Determine ticks
                val ticks = if (lastMessageFromCurrentUser && lastMessage.isNotEmpty()) {
                    if (lastMessageRead) " ✔✔ Seen" else " ✔ Delivered"
                } else {
                    ""
                }

                // Apply color to ticks
                val fullText = messageText + ticks
                val styledText = buildAnnotatedString {
                    val tickIndex = fullText.indexOf('✔')
                    if (tickIndex != -1) {
                        append(fullText.substring(0, tickIndex))
                        withStyle(SpanStyle(color = Color(0xFFFF4500))) {
                            append(fullText.substring(tickIndex))
                        }
                    } else {
                        append(fullText)
                    }
                }

                Text(
                    text = styledText,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}

private fun checkNonInitiatedConversations(
    matchedUsers: List<Profile>,
    messagesRootRef: DatabaseReference,
    currentUserId: String,
    onResult: (List<Profile>) -> Unit
) {
    val nonInitiated = mutableListOf<Profile>()
    var remaining = matchedUsers.size
    if (remaining == 0) {
        onResult(nonInitiated)
        return
    }

    // Check if there's at least one message
    matchedUsers.forEach { profile ->
        val chatId = getChatId(currentUserId, profile.userId)
        messagesRootRef.child(chatId).limitToFirst(1).get().addOnSuccessListener {
            if (!it.exists()) {
                nonInitiated.add(profile)
            }
            remaining--
            if (remaining == 0) onResult(nonInitiated)
        }.addOnFailureListener {
            remaining--
            if (remaining == 0) onResult(nonInitiated)
        }
    }
}

private fun fetchUsersFromNode(
    ref: DatabaseReference,
    usersRef: DatabaseReference,
    usersList: MutableList<Profile>,
    context: android.content.Context,
    onComplete: (() -> Unit)? = null
) {
    ref.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val userIdsToFetch = snapshot.children.mapNotNull { it.key }

            if (userIdsToFetch.isNotEmpty()) {
                usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(userSnapshot: DataSnapshot) {
                        val newUsers = mutableListOf<Profile>()
                        for (user in userSnapshot.children) {
                            val profile = user.getValue(Profile::class.java)
                            if (profile != null && userIdsToFetch.contains(profile.userId)) {
                                newUsers.add(profile)
                            }
                        }
                        usersList.clear()
                        usersList.addAll(newUsers)
                        onComplete?.invoke()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("DMScreen", "DBError: ${error.message}")
                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                usersList.clear()
                onComplete?.invoke()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("DMScreen", "DatabaseError: ${error.message}")
            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    })
}
fun getLevelBorderColor(rating: Double): Color {
    return when {
        rating in 0.0..1.0 -> Color(0xFF444444)    // 0 to 1 Rating
        rating in 1.1..2.1 -> Color(0xFF555555)    // 1.1 to 2.0 Rating
        rating in 2.1..3.6 -> Color(0xFF886633)    // 2.1 to 3.0 Rating
        rating in 3.6..4.7 -> Color(0xFFAA6633)    // 3.1 to 4.0 Rating (same color as 2.1 to 3.0)
        rating in 4.7..5.0 -> Color(0xFFFF6F00)    // 4.1 to 5.0 Rating
        else -> Color.Gray                         // Default color if rating is out of range
    }
}
