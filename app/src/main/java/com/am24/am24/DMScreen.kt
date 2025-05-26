@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.imageLoader
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch

@Composable
fun DMScreen(navController: NavController) {
    DMScreenContent(navController = navController)
}

@Composable
fun DMScreenContent(navController: NavController) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val context = LocalContext.current
    val database = FirebaseRefs.db
    val matchesRef = database.getReference("matches/$currentUserId")
    val likesRef = database.getReference("likesReceived/$currentUserId")
    val usersRef = database.getReference("users")
    val messagesRootRef = database.getReference("messages")
    val ratingsRef = database.getReference("ratings")

    var showRatingOverlay by remember { mutableStateOf(false) }
    var profileToRate by remember { mutableStateOf<Profile?>(null) }
    var tempRating by remember { mutableStateOf(-1.0) }
    var showUnmatchDialog by remember { mutableStateOf(false) }
    var profileToUnmatch by remember { mutableStateOf<Profile?>(null) }

    var currentUserProfile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(currentUserId) {
        usersRef.child(currentUserId).get()
            .addOnSuccessListener { snap ->
                currentUserProfile = snap.getValue(Profile::class.java)
            }
    }

// 1️⃣  Build the chip list
    val groupChatTitles = remember(currentUserProfile) {
        buildList {
            add("India")           // ← global room (was “West Bengal”)
            currentUserProfile?.city
                ?.takeIf { it.isNotBlank() }?.let { add(it) }
            currentUserProfile?.hometown
                ?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()
    }


    var searchQuery by remember { mutableStateOf("") }
    var likedCount by remember { mutableStateOf(0) }
    val matchedUsers = remember { mutableStateListOf<Profile>() }
    val nonInitiatedMatches = remember { mutableStateListOf<Profile>() }
    val lastMessages = remember { mutableStateMapOf<String, Triple<String, Boolean, Boolean>>() }
    // — new: grab your blocks
    val blockedRef = database.getReference("blocks/$currentUserId")
    val blockedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(currentUserId) {
        blockedRef.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                blockedIds.clear()
                s.children.mapNotNull { it.key }
                    .also(blockedIds::addAll)
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    val matchIds = remember { mutableStateListOf<String>() }
    val likeIds = remember { mutableStateListOf<String>() }
    fun recomputeLiked() {
        likedCount = likeIds.count { id ->
            // only count if NOT matched *and* NOT blocked
            !matchIds.contains(id) &&
                    !blockedIds.contains(id)
        }
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        matchesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                matchIds.clear()
                s.children.forEach { it.key?.let(matchIds::add) }
                recomputeLiked()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        likesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                likeIds.clear()
                s.children.forEach { it.key?.let(likeIds::add) }
                recomputeLiked()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    LaunchedEffect(currentUserId) {
        fetchUsersFromNode(matchesRef, usersRef, matchedUsers, context) {
            checkNonInitiatedConversations(matchedUsers, messagesRootRef, currentUserId) { nonInitiated ->
                nonInitiatedMatches.clear()
                nonInitiatedMatches.addAll(nonInitiated)
            }

            matchedUsers.forEach { profile ->
                profile.profilepicUrl?.let { url ->
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .diskCacheKey(url)
                        .memoryCacheKey(url)
                        .crossfade(true)
                        .build()
                    context.imageLoader.enqueue(request)
                }
            }

            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@fetchUsersFromNode
            matchedUsers.forEach { profile ->
                val chatId = getChatId(currentUserId, profile.userId)
                messagesRootRef.child(chatId)
                    .orderByChild("timestamp")
                    .limitToLast(1)
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (!snapshot.exists()) {
                                lastMessages[profile.userId] = Triple("", false, true)
                                Log.d("DMScreen", "No messages for ${profile.userId}")
                                return
                            }
                            for (msgSnap in snapshot.children) {
                                val text = msgSnap.child("text").getValue(String::class.java) ?: ""
                                val senderId = msgSnap.child("senderId").getValue(String::class.java) ?: ""
                                val read = msgSnap.child("read").getValue(Boolean::class.java) ?: false
                                val fromCurrentUser = (senderId == currentUserId)
                                val displayText = if (text.length > 30) "${text.take(30)}..." else text
                                lastMessages[profile.userId] = Triple(displayText, fromCurrentUser, read)
                                Log.d("DMScreen", "Last message for ${profile.userId}: $displayText")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("DMScreen", "Failed to fetch last message for ${profile.userId}: ${error.message}")
                        }
                    })
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                onClick = { focusManager.clearFocus() },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 2️⃣  Map chip → chat-room ID
                    groupChatTitles.forEach { title ->
                            GroupChatChip(title) {
                            val id = when (title) {
                                "India" -> "group_india"                      // ← new constant
                                else -> "group_${title.replace(" ", "_").lowercase()}"
                            }
                            navController.navigate("groupChat/$id")
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search matches", color = Color.Gray, fontSize = 12.sp) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF4500),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFFFF4500),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(50.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .clickable { navController.navigate("peopleWhoLikedMe") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+$likedCount", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.width(6.dp))
                nonInitiatedMatches
                    .forEach { profile ->
                        AIOrProfileImage(
                            profile,
                            Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                                .clickable { navController.navigate("chat/${profile.userId}") }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
            }

            val displayedUsers = matchedUsers

            if (displayedUsers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matches found", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedUsers) { profile ->
                        val lastMsg = lastMessages[profile.userId] ?: Triple("", false, true)
                        DMUserCard(
                            profile = profile,
                            navController = navController,
                            lastMessage = lastMsg.first,
                            lastMessageFromCurrentUser = lastMsg.second,
                            lastMessageRead = lastMsg.third,
                            onRateClick = { selectedProfile ->
                                fetchUserRating(ratingsRef, selectedProfile.userId) { fetchedRating ->
                                    tempRating = fetchedRating
                                    profileToRate = selectedProfile
                                    showRatingOverlay = true
                                }
                            },
                            onUnmatchClick = { selectedProfile ->
                                profileToUnmatch = selectedProfile
                                showUnmatchDialog = true
                            }
                        )
                    }
                }
            }
        }
        if (showRatingOverlay && profileToRate != null) {
            Dialog(onDismissRequest = {
                showRatingOverlay = false
                profileToRate = null
            }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black,
                    border = BorderStroke(2.dp, Color(0xFFFF4500))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        RatingBar(rating = profileToRate!!.averageRating, ratingCount = profileToRate!!.numberOfRatings)
                        Text(
                            "Your Rating: ${if (tempRating >= 0) String.format("%.1f", tempRating) else "N/A"}",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Slider(
                            value = if (tempRating >= 0) tempRating.toFloat() else 0f,
                            onValueChange = { tempRating = it.toDouble() },
                            onValueChangeFinished = {
                                updateUserRating(ratingsRef, usersRef, profileToRate!!.userId, tempRating, context)
                                showRatingOverlay = false
                                profileToRate = null
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
            }
        }

        if (showUnmatchDialog && profileToUnmatch != null) {
            Dialog(onDismissRequest = {
                showUnmatchDialog = false
                profileToUnmatch = null
            }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black,
                    border = BorderStroke(2.dp, Color(0xFFFF4500))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Unmatch with ${profileToUnmatch!!.username}?",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This will remove the match and delete your conversation history.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                showUnmatchDialog = false
                                profileToUnmatch = null
                            }) {
                                Text("Cancel", color = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                unmatchUser(
                                    currentUserId = currentUserId,
                                    otherUserId = profileToUnmatch!!.userId,
                                    database = database,
                                    context = context
                                )
                                showUnmatchDialog = false
                                profileToUnmatch = null
                            }) {
                                Text("Unmatch", color = Color.Red)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
            containerColor = Color(0xFFFF4500),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, "Scroll to Top", tint = Color.White)
        }
    }
}

@Composable
fun DMUserCard(
    profile: Profile,
    navController: NavController,
    lastMessage: String,
    lastMessageFromCurrentUser: Boolean,
    lastMessageRead: Boolean,
    onRateClick: (Profile) -> Unit,
    onUnmatchClick: (Profile) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .border(
                BorderStroke(2.dp, getLevelBorderColor(profile.averageRating)),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { navController.navigate("chat/${profile.userId}") }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AIOrProfileImage(
                    profile,
                    Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.username,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val age = profile.dob?.let { calculateAge(it) } ?: ""
                    val localeInfo = if (profile.hometown.isNotBlank()) {
                        "${profile.hometown}, ${profile.jobRole}, ${stringResource(R.string.age_format, age)}"
                    } else {
                        stringResource(R.string.age_only_format, age)
                    }
                    Text(localeInfo, fontSize = 14.sp, color = Color.White)

                    val messageText = when {
                        lastMessage.isEmpty() -> stringResource(R.string.no_messages_yet)
                        lastMessageFromCurrentUser -> stringResource(R.string.sent_message, lastMessage)
                        else -> lastMessage
                    }
                    val ticks = if (lastMessageFromCurrentUser && lastMessage.isNotEmpty()) {
                        if (lastMessageRead) stringResource(R.string.seen_status)
                        else stringResource(R.string.delivered_status)
                    } else ""
                    val fullText = messageText + ticks
                    val styled = buildAnnotatedString {
                        val tickAt = fullText.indexOf('✔')
                        if (tickAt >= 0) {
                            append(fullText.substring(0, tickAt))
                            withStyle(SpanStyle(color = Color(0xFFFF4500))) {
                                append(fullText.substring(tickAt))
                            }
                        } else append(fullText)
                    }
                    Text(
                        text = styled,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onRateClick(profile) }) {
                    Text(stringResource(R.string.rate), color = Color(0xFFFF4500))
                }
                TextButton(onClick = { onUnmatchClick(profile) }) {
                    Text("Unmatch", color = Color.Red)
                }
            }
        }
    }
}

private fun unmatchUser(
    currentUserId: String,
    otherUserId: String,
    database: FirebaseDatabase,
    context: android.content.Context
) {
    val chatId = getChatId(currentUserId, otherUserId)
    val updates = mapOf(
        "matches/$currentUserId/$otherUserId" to null,
        "matches/$otherUserId/$currentUserId" to null,
        "messages/$chatId" to null
    )

    database.reference.updateChildren(updates)
        .addOnSuccessListener {
            Toast.makeText(context, "Unmatched successfully", Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener { error ->
            Toast.makeText(context, "Failed to unmatch: ${error.message}", Toast.LENGTH_SHORT).show()
            Log.e("DMScreen", "Unmatch failed: ${error.message}")
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
            Log.d("DMScreen", "Fetched user IDs from matches: $userIdsToFetch")

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
                        Log.d("DMScreen", "Populated matchedUsers with ${usersList.size} profiles: ${usersList.map { it.userId }}")
                        onComplete?.invoke()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("DMScreen", "DBError in fetchUsersFromNode: ${error.message}")
                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                usersList.clear()
                Log.d("DMScreen", "No user IDs to fetch, cleared matchedUsers")
                onComplete?.invoke()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("DMScreen", "DatabaseError in fetchUsersFromNode: ${error.message}")
            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    })
}

fun getLevelBorderColor(rating: Double): Color {
    return when {
        rating in 0.0..1.0 -> Color(0xFF1A1A1A)
        rating in 1.1..2.1 -> Color(0x88FFFF00)
        rating in 2.1..3.6 -> Color.White
        rating in 3.6..4.7 -> Color(0xFFFF6F00)
        rating in 4.7..5.0 -> Color(0xFFE91E63)
        else -> Color.Gray
    }
}

@Composable
fun GroupChatChip(
    title: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .border(BorderStroke(1.dp, Color.Gray), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, color = Color.White, fontSize = 14.sp)
    }
}