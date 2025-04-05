@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
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
        profilepicUrl = "", // local drawables used in your ChatScreen anyway
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
            cannabis_friendly = false,
            indoor_outdoor_orientation = 2,
            sexual_activity_level = 1,
            sociability = 2,
            social_media_engagement = 2,
            dietary_preferences = "Non-Veg",
            sleep_pattern = 2,
            work_life_balance = 3,
            exercise_frequency = 4,
            adventurousness = 4,
            pet_affinity = true,
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
            sports_enthusiasm = 5, // She’s a jock
            preferred_alcohol_type = "Wine"
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
            cannabis_friendly = false,
            indoor_outdoor_orientation = 4,
            sexual_activity_level = 2,
            sociability = 2,
            social_media_engagement = 3,
            dietary_preferences = "Non-Veg",
            sleep_pattern = 2,
            work_life_balance = 3,
            exercise_frequency = 5,
            adventurousness = 5,
            pet_affinity = false,
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
            sports_enthusiasm = 5, // He’s big on sports
            preferred_alcohol_type = "Beer"
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

    val lastMessages = remember { mutableStateMapOf<String, Triple<String, Boolean, Boolean>>() }

    LaunchedEffect(currentUserId) {
        // 2) Fetch real matches, then insert Zara/Kabir
        fetchUsersFromNode(matchesRef, usersRef, matchedUsers, context) {
            injectAIProfiles(matchedUsers, zaraProfile, kabirProfile)

            checkNonInitiatedConversations(matchedUsers, messagesRootRef, currentUserId) { nonInitiated ->
                nonInitiatedMatches.clear()
                nonInitiatedMatches.addAll(nonInitiated)
            }

            // Listen for last messages
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
        // Search bar
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

        // Row with likes + nonInitiated
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            nonInitiatedMatches.forEach { profile ->
                AIOrProfileImage(
                    profile = profile,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                        .clickable {
                            navController.navigate("chat/${profile.userId}")
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        // Filter list
        val displayedUsers = matchedUsers.filter {
            it.username.contains(searchQuery, ignoreCase = true) ||
                    it.name.contains(searchQuery, ignoreCase = true)
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

// Helper function to show either local resource for Zara/Kabir or the real user's pic
@Composable
fun AIOrProfileImage(profile: Profile, modifier: Modifier = Modifier) {
    // If userId = "zaraAi" => load R.drawable.zara_avatar
    // If userId = "kabirAi" => load R.drawable.kabir_avatar
    // else => load profilepicUrl with AsyncImage
    when (profile.userId) {
        "zaraAi" -> {
            Image(
                painter = painterResource(R.drawable.zara_avatar),
                contentDescription = "Zara",
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
        "kabirAi" -> {
            Image(
                painter = painterResource(R.drawable.kabir_avatar),
                contentDescription = "Kabir",
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
        else -> {
            AsyncImage(
                model = profile.profilepicUrl,
                contentDescription = profile.username,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
    }
}

fun injectAIProfiles(
    matchedUsers: MutableList<Profile>,
    zaraProfile: Profile,
    kabirProfile: Profile
) {
    if (matchedUsers.none { it.userId == "zaraAi" }) {
        matchedUsers.add(zaraProfile)
    }
    if (matchedUsers.none { it.userId == "kabirAi" }) {
        matchedUsers.add(kabirProfile)
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
            // Use the same approach for the row image
            AIOrProfileImage(
                profile = profile,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.username,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                val age = profile.dob?.let { calculateAge(it) }
                val locality = profile.hometown
                if (profile.hometown.isNotBlank()) {
                    Text(
                        text = "$locality, ${profile.jobRole}, Age: ${age ?: ""}",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "${locality ?: ""}, Age: ${age ?: ""}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                val messageText = when {
                    lastMessage.isEmpty() -> "No messages yet"
                    lastMessageFromCurrentUser -> "Sent: $lastMessage"
                    else -> lastMessage
                }

                val ticks = if (lastMessageFromCurrentUser && lastMessage.isNotEmpty()) {
                    if (lastMessageRead) " ✔✔ Seen" else " ✔ Delivered"
                } else {
                    ""
                }

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
