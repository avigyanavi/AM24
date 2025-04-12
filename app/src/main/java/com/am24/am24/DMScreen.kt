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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
/* ----------  extra imports  ---------- */
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch


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
    val likesRef = database.getReference("likesReceived/$currentUserId") // Added for likes
    val usersRef = database.getReference("users")
    val messagesRootRef = database.getReference("messages")
    val ratingsRef = database.getReference("ratings") // Add this, similar to ChatScreenContent

    var showRatingOverlay by remember { mutableStateOf(false) }
    var profileToRate by remember { mutableStateOf<Profile?>(null) }
    var tempRating by remember { mutableStateOf(-1.0) }


    /* ───────── Profiles for AI coaches (unchanged) ───────── */
    val zaraProfile = Profile(
        email = "zara@am24.org",
        password = "ZaraPassword123!",
        interestedIn = listOf("Male"),
        preferredLanguage = "English",
        userId = "zaraAi",
        username = "ZaraAI",
        name = "Zara",
        dob = "12/04/1999",
        bio = "Kolkata sports star turned dating coach, once a jock in high school!",
        gender = "Female",
        lastActive = System.currentTimeMillis(),
        badges = listOf("CricketChampion", "SchoolJock"),
        profilepicUrl = "",
        voiceNoteUrl = "",
        loveLanguage = "Words of Affirmation",
        optionalPhotoUrls = emptyList(),
        matches = emptyList(),
        religion = "Hindu",
        community = "Bengali",
        city = "Kolkata",
        customCity = null,
        hometown = "Kolkata",
        customHometown = null,
        educationLevel = "Masters",
        highSchool = "Kolkata HS",
        customHighSchool = null,
        highSchoolGraduationYear = "2014",
        college = "Kolkata University",
        customCollege = null,
        collegeGraduationYear = "2018",
        collegeDegree = "B.A. in Sociology",
        postGraduation = "Kolkata University PG",
        customPostGraduation = null,
        postGraduationYear = "2020",
        postGraduationDegree = "M.A. in Psychology",
        lifestyle = Lifestyle(
            smoking_habit = 0,
            drinking_habit = 1,
            indoor_outdoor_orientation = 2,
            sexual_activity_level = 1,
            sociability = 2,
            social_media_engagement = 2,
            dietary_preferences = "Non-Veg",
            sleep_pattern = 2,
            work_life_balance = 3,
            exercise_frequency = 4,
            adventurousness = 4,
            family_orientated = 3,
            intellectual_curiosity = 3,
            creative_expression = 4,
            physical_fitness = 4,
            spirituality_mindfulness = 2,
            easy_goingness = 3,
            professional_ambition = 3,
            environmental_awareness = 2,
            culinary_enthusiasm = 3,
            political_awareness = 3,
            community_engagement = 3,
            sports_enthusiasm = 5,
        ),
        politics = "Liberal",
        jobRole = "Dating Coach",
        customJobRole = null,
        work = "Self-Employed",
        customWork = null,
        socialCauses = listOf("Women Empowerment"),
        lookingFor = "Connections",
        likedUsers = mutableMapOf(),
        numberOfUsersWhoSwiped = 0.0,
        UsersWhoLikeMe = mutableMapOf(),
        isBoosted = false,
        isPremium = false,
        isPrivate = false,
        am24RankingAge = 5,
        am24RankingHighSchool = 5,
        am24RankingCollege = 5,
        am24RankingHometown = 5,
        am24Ranking = 20,
        numberOfRatings = 35,
        numberOfSwipeRights = 0,
        matchCount = 0,
        matchCountPerSwipeRight = 0.0,
        cumulativeUpvotes = 0,
        cumulativeDownvotes = 0,
        averageUpvoteCount = 0.0,
        averageDownvoteCount = 0.0,
        reportUsers = mutableMapOf(),
        blockedUsers = mutableMapOf(),
        upvoteCount = 0,
        downvoteCount = 0,
        userTags = emptyList(),
        zodiac = null,
        dateOfJoin = System.currentTimeMillis(),
        am24RankingCompositeScore = 100.0,
        latitude = 0.0,
        longitude = 0.0,
        averageRating = 4.2,
        isMatrimonyMode = false,
        marriageTimeline = null,
        relocationPreference = null,
        postMarriageCareerPlan = null,
        traditionalVsLiberal = null,
        fatherOccupation = null,
        motherOccupation = null,
        numberOfSiblings = null,
        elderSiblings = null,
        youngerSiblings = null,
        isConsultantVerified = false,
        datingAgeStart = 18,
        datingAgeEnd = 30,
        datingDistancePreference = 10,
        height = 163,
        height2 = emptyList(),
        caste = "",
        relationship = null,
        averageSwipeRightsOnUser = 0.0,
        ratingsGiven = emptyMap(),
        ratingsReceived = emptyMap()
    )

    val kabirProfile = Profile(
        email = "kabir@am24.org",
        password = "KabirPassword456!",
        interestedIn = listOf("Female"),
        preferredLanguage = "English",
        userId = "kabirAi",
        username = "KabirAI",
        name = "Kabir",
        dob = "07/02/1998",
        bio = "Ex-college athlete turned edgy approach mentor, jock vibes all the way!",
        gender = "Male",
        lastActive = System.currentTimeMillis(),
        badges = listOf("SchoolJock", "FootballStar"),
        profilepicUrl = "",
        voiceNoteUrl = "",
        loveLanguage = "Physical Touch",
        optionalPhotoUrls = emptyList(),
        matches = emptyList(),
        religion = "Muslim",
        community = "Bengali",
        city = "Kolkata",
        customCity = null,
        hometown = "Kolkata",
        customHometown = null,
        educationLevel = "Bachelors",
        highSchool = "Kolkata HS",
        customHighSchool = null,
        highSchoolGraduationYear = "2013",
        college = "Kolkata College",
        customCollege = null,
        collegeGraduationYear = "2017",
        collegeDegree = "B.Com",
        postGraduation = "",
        customPostGraduation = null,
        postGraduationYear = "",
        postGraduationDegree = null,
        lifestyle = Lifestyle(
            smoking_habit = 2,
            drinking_habit = 2,
            indoor_outdoor_orientation = 4,
            sexual_activity_level = 2,
            sociability = 2,
            social_media_engagement = 3,
            dietary_preferences = "Non-Veg",
            sleep_pattern = 2,
            work_life_balance = 3,
            exercise_frequency = 5,
            adventurousness = 5,
            family_orientated = 3,
            intellectual_curiosity = 2,
            creative_expression = 2,
            physical_fitness = 5,
            spirituality_mindfulness = 2,
            easy_goingness = 3,
            professional_ambition = 2,
            environmental_awareness = 2,
            culinary_enthusiasm = 2,
            political_awareness = 2,
            community_engagement = 2,
            sports_enthusiasm = 5,
        ),
        politics = "Moderate",
        jobRole = "Approach Mentor",
        customJobRole = null,
        work = "Freelancer",
        customWork = null,
        socialCauses = listOf("Youth Empowerment"),
        lookingFor = "Fun & Flirting",
        likedUsers = mutableMapOf(),
        numberOfUsersWhoSwiped = 0.0,
        UsersWhoLikeMe = mutableMapOf(),
        isBoosted = false,
        isPremium = false,
        isPrivate = false,
        am24RankingAge = 5,
        am24RankingHighSchool = 5,
        am24RankingCollege = 3,
        am24RankingHometown = 5,
        am24Ranking = 18,
        numberOfRatings = 20,
        numberOfSwipeRights = 0,
        matchCount = 0,
        matchCountPerSwipeRight = 0.0,
        cumulativeUpvotes = 0,
        cumulativeDownvotes = 0,
        averageUpvoteCount = 0.0,
        averageDownvoteCount = 0.0,
        reportUsers = mutableMapOf(),
        blockedUsers = mutableMapOf(),
        upvoteCount = 0,
        downvoteCount = 0,
        userTags = emptyList(),
        zodiac = null,
        dateOfJoin = System.currentTimeMillis(),
        am24RankingCompositeScore = 80.0,
        latitude = 0.0,
        longitude = 0.0,
        averageRating = 3.8,
        isMatrimonyMode = false,
        marriageTimeline = null,
        relocationPreference = null,
        postMarriageCareerPlan = null,
        traditionalVsLiberal = null,
        fatherOccupation = null,
        motherOccupation = null,
        numberOfSiblings = null,
        elderSiblings = null,
        youngerSiblings = null,
        isConsultantVerified = false,
        datingAgeStart = 18,
        datingAgeEnd = 30,
        datingDistancePreference = 10,
        height = 178,
        height2 = emptyList(),
        caste = "",
        relationship = null,
        averageSwipeRightsOnUser = 0.0,
        ratingsGiven = emptyMap(),
        ratingsReceived = emptyMap()
    )

    var currentUserProfile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(currentUserId) {
        usersRef.child(currentUserId).get()
            .addOnSuccessListener { snap ->
                currentUserProfile = snap.getValue(Profile::class.java)
            }
    }

    val groupChatTitles = remember(currentUserProfile) {
        buildList {
            add("West Bengal")
            currentUserProfile?.city
                ?.takeIf { it.isNotBlank() }?.let { add(it) }
            currentUserProfile?.hometown
                ?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()
    }

    var showAIChats by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var likedCount by remember { mutableStateOf(0) }
    val matchedUsers = remember { mutableStateListOf<Profile>() }
    val nonInitiatedMatches = remember { mutableStateListOf<Profile>() }
    val lastMessages = remember { mutableStateMapOf<String, Triple<String, Boolean, Boolean>>() }

    /* ---- Lists to compute likedCount (likes – matches) ---- */
    val matchIds = remember { mutableStateListOf<String>() }
    val likeIds = remember { mutableStateListOf<String>() }
    fun recomputeLiked() { likedCount = likeIds.count { !matchIds.contains(it) } }

    /* ---- Scroll-to-top helpers ---- */
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current // Add FocusManager

    /* ───────── Realtime listeners for matches & likes ───────── */
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

    /* ───────── Fetch matched user objects ───────── */
    LaunchedEffect(currentUserId) {
        fetchUsersFromNode(matchesRef, usersRef, matchedUsers, context) {
            // Add AI profiles
            zaraProfile?.let { if (matchedUsers.none { it.userId == "zaraAi" }) matchedUsers.add(it) }
            kabirProfile?.let { if (matchedUsers.none { it.userId == "kabirAi" }) matchedUsers.add(it) }

            // Check non-initiated conversations
            checkNonInitiatedConversations(matchedUsers, messagesRootRef, currentUserId) { nonInitiated ->
                nonInitiatedMatches.clear()
                nonInitiatedMatches.addAll(nonInitiated)
            }

            // Preload profile pictures
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

            // Fetch last messages
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

    // Wrap the content in a Box to handle clicks outside the text field
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                onClick = { focusManager.clearFocus() }, // Clear focus when clicking outside
                indication = null, // Remove ripple effect for better UX
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Single Row for group chat buttons and AI toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scrollable Row for group chat buttons
                Row(
                    modifier = Modifier
                        .weight(1f) // Takes available space on the left
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    groupChatTitles.forEach { title ->
                        GroupChatChip(title) {
                            val id = when (title) {
                                "West Bengal" -> "group_wb"
                                else -> "group_${title.replace(" ", "_").lowercase()}"
                            }
                            navController.navigate("groupChat/$id")
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }

                // "Show AI Chats" toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI Chats", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = showAIChats,
                        onCheckedChange = { showAIChats = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFF4500),
                            uncheckedThumbColor = Color.Gray
                        )
                    )
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
                    .filter { showAIChats || !it.userId.endsWith("Ai") }
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

            val displayedUsers = matchedUsers.filter {
                (showAIChats || !it.userId.endsWith("Ai")) &&
                        (it.username.contains(searchQuery, true) || it.name.contains(searchQuery, true))
            }

            if (displayedUsers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matches found", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    state = listState, // Use listState for scroll control
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
                                // Fetch the current rating for this profile and update the state
                                fetchUserRating(ratingsRef, selectedProfile.userId) { fetchedRating ->
                                    tempRating = fetchedRating
                                    profileToRate = selectedProfile
                                    showRatingOverlay = true
                                }
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
                        // Show the rated user's current rating info
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
                                // Update rating on Firebase for this matched user
                                updateUserRating(ratingsRef, usersRef, profileToRate!!.userId, tempRating, context)
                                // Hide overlay once done
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


        /* ───── Scroll-to-top FAB ───── */
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
    onRateClick: (Profile) -> Unit  // New parameter
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .border(BorderStroke(2.dp, getLevelBorderColor(profile.averageRating)), shape = RoundedCornerShape(8.dp))
            .clickable { navController.navigate("chat/${profile.userId}") }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AIOrProfileImage(
                    profile = profile,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.username,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Display locality and age info
                    val age = profile.dob?.let { calculateAge(it) }
                    val locality = profile.hometown
                    if (profile.hometown.isNotBlank()) {
                        Text(
                            text = "$locality, ${profile.jobRole}, Age: ${age ?: ""}",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "${locality ?: ""}, Age: ${age ?: ""}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    // Display last message with ticks (same as before)
                    val messageText = when {
                        lastMessage.isEmpty() -> "No messages yet"
                        lastMessageFromCurrentUser -> "Sent: $lastMessage"
                        else -> lastMessage
                    }
                    val ticks = if (lastMessageFromCurrentUser && lastMessage.isNotEmpty()) {
                        if (lastMessageRead) " ✔✔ Seen" else " ✔ Delivered"
                    } else ""
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
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // NEW: "Rate" button row. You can position it as desired.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onRateClick(profile) }
                ) {
                    Text("Rate", color = Color(0xFFFF4500))
                }
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
        rating in 0.0..1.0 -> Color(0xFF444444)
        rating in 1.1..2.1 -> Color(0xFF555555)
        rating in 2.1..3.6 -> Color(0xFF886633)
        rating in 3.6..4.7 -> Color(0xFFAA6633)
        rating in 4.7..5.0 -> Color(0xFFFF6F00)
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

