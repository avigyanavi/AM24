@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import ComplimentData
import DatingViewModel
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringArrayResource
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
import com.am24.am24.ui.theme.DarkGrayBackground
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.tasks.await


private fun canonicalLocationId(name: String): String =
    name.replace("\\s".toRegex(), "").lowercase()

data class ComplimentWithProfile(
    val profile: Profile,
    val compliment: ComplimentData
)

@Composable
fun DMScreen(
    navController: NavController,
    nearbyViewModel: NearbyViewModel,
) {
    DMScreenContent(navController, nearbyViewModel)
}

@Composable
fun DMScreenContent(
    navController: NavController,
    nearbyViewModel: NearbyViewModel,
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val context = LocalContext.current
    val database = FirebaseRefs.db
    val matchesRef = database.getReference("matches/$currentUserId")
    val likesRef = database.getReference("likesReceived/$currentUserId")
    val usersRef = database.getReference("users")
    val messagesRootRef = database.getReference("messages")
    val ratingsRef = database.getReference("ratings")
    val datingViewModel: DatingViewModel = viewModel()
    val compliments by datingViewModel.complimentsReceived.collectAsState()

    var showRatingOverlay by remember { mutableStateOf(false) }
    var profileToRate by remember { mutableStateOf<Profile?>(null) }
    var tempRating by remember { mutableStateOf(-1.0) }
    var showUnmatchDialog by remember { mutableStateOf(false) }
    var profileToUnmatch by remember { mutableStateOf<Profile?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) } // Track loading state
    var currentUserProfile by remember { mutableStateOf<Profile?>(null) }
    var showSmartMatchDialog by remember { mutableStateOf(false) }
    var selectedSmartMatchGender by remember { mutableStateOf("Both") }

    LaunchedEffect(currentUserId) {
        usersRef.child(currentUserId).get()
            .addOnSuccessListener { snap ->
                currentUserProfile = snap.getValue(Profile::class.java)
                isLoadingProfile = false
            }
            .addOnFailureListener {
                isLoadingProfile = false // Handle error appropriately
                Toast.makeText(context, context.getString(R.string.dm_failed_load_profile), Toast.LENGTH_SHORT).show()
            }
    }

    // Show loading UI while profile is being fetched
    if (isLoadingProfile) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFFF4500))
        }
        return
    }

    // If profile is null after loading, handle error
    val profile = currentUserProfile ?: run {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.dm_error_loading_profile), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    val isPremiumUser = profile.isPremium || profile.isPlus
    val todayWeek = remember { Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) }
    val smartMatchAvailable = remember(profile.lastSmartMatchWeekOfYear) { profile.lastSmartMatchWeekOfYear != todayWeek }

    // instead of   rememberScrollState()
    val autoScrollState = rememberScrollState()

    // kick off an endless back-and-forth animation whenever there's overflow
    LaunchedEffect(autoScrollState.maxValue) {
        // wait for the scroll to measure
        snapshotFlow { autoScrollState.maxValue }
            .filter { it > 0 }       // only once it's actually overflowed
            .first()                 // suspend until >0
        while (true) {
            autoScrollState.animateScrollTo(autoScrollState.maxValue)
            delay(2000)              // pause at end
            autoScrollState.animateScrollTo(0)
            delay(2000)              // pause at start
        }
    }


// 1️⃣  Build the chip list
    val groupChatTitles = remember(currentUserProfile) {
        buildList {
            currentUserProfile?.country
                ?.takeIf { it.isNotBlank() }
                ?.let { add(it) }
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
    val prefetchedUrls = remember { mutableStateSetOf<String>() }
    val complimentProfiles = remember { mutableStateListOf<ComplimentWithProfile>() }
    // — new: grab your blocks
    val blockedRef = database.getReference("blocks/$currentUserId")
    val blockedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(compliments) {
        complimentProfiles.clear()
        compliments.forEach { (senderId, compliment) ->
            try {
                val snap = usersRef.child(senderId).get().await()
                val profile = snap.getValue(Profile::class.java) ?: return@forEach
                complimentProfiles.add(ComplimentWithProfile(profile, compliment))
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(currentUserId) {
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                blockedIds.clear()
                s.children.mapNotNull { it.key }
                    .also(blockedIds::addAll)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        blockedRef.addListenerForSingleValueEvent(listener)
        onDispose { blockedRef.removeEventListener(listener) }
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

    DisposableEffect(Unit) {
        val matchesListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                matchIds.clear()
                s.children.forEach { it.key?.let(matchIds::add) }
                recomputeLiked()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val likesListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                likeIds.clear()
                s.children.forEach { it.key?.let(likeIds::add) }
                recomputeLiked()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        matchesRef.addValueEventListener(matchesListener)
        likesRef.addValueEventListener(likesListener)
        onDispose {
            matchesRef.removeEventListener(matchesListener)
            likesRef.removeEventListener(likesListener)
        }
    }

    val messageListeners = remember { mutableMapOf<String, ValueEventListener>() }

    DisposableEffect(currentUserId) {
        val fetchListener = fetchUsersFromNode(matchesRef, usersRef, matchedUsers, context) {
            checkNonInitiatedConversations(matchedUsers, messagesRootRef, currentUserId) { nonInitiated ->
                nonInitiatedMatches.clear()
                nonInitiatedMatches.addAll(nonInitiated)
            }

            matchedUsers.forEach { profile ->
                val url = profile.profilepicThumbnailUrl ?: profile.profilepicUrl
                url?.let {
                    if (prefetchedUrls.add(it)) {
                        val pathKey = Uri.parse(it).path
                        val request = ImageRequest.Builder(context)
                            .data(it)
                            .diskCacheKey(pathKey)
                            .memoryCacheKey(pathKey)
                            .crossfade(true)
                            .build()
                        context.imageLoader.enqueue(request)
                    }
                }
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@fetchUsersFromNode
            matchedUsers.forEach { profile ->
                val chatId = getChatId(uid, profile.userId)
                messageListeners[chatId]?.let { old ->
                    messagesRootRef.child(chatId).removeEventListener(old)
                }
                val listener = object : ValueEventListener {
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
                            val fromCurrentUser = (senderId == uid)
                            val displayText = if (text.length > 30) "${text.take(30)}..." else text
                            lastMessages[profile.userId] = Triple(displayText, fromCurrentUser, read)
                            Log.d("DMScreen", "Last message for ${profile.userId}: $displayText")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("DMScreen", "Failed to fetch last message for ${profile.userId}: ${error.message}")
                    }
                }
                messagesRootRef.child(chatId)
                    .orderByChild("timestamp")
                    .limitToLast(1)
                    .addValueEventListener(listener)
                messageListeners[chatId] = listener
            }
        }
        onDispose {
            matchesRef.removeEventListener(fetchListener)
            messageListeners.forEach { (chatId, l) ->
                messagesRootRef.child(chatId).removeEventListener(l)
            }
            messageListeners.clear()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGrayBackground)
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
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .horizontalScroll(autoScrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 2️⃣  Map chip → chat-room ID
                groupChatTitles.forEach { title ->
                    GroupChatChip(title) {
                        val id = when (title) {
                            "India" -> "group_india"
                            "United States" -> "group_usa"
                            else -> "group_${canonicalLocationId(title)}"
                        }
                        navController.navigate("groupChat/$id")
                    }

                    Spacer(Modifier.width(6.dp))
                }
                if (isPremiumUser) {
                    val now = Calendar.getInstance()
                    val nextReset = Calendar.getInstance().apply {
                        firstDayOfWeek = now.firstDayOfWeek
                        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                        add(Calendar.WEEK_OF_YEAR, 1)
                    }
                    val millisInDay = 24 * 60 * 60 * 1000L
                    val daysLeft =
                        ((nextReset.timeInMillis - now.timeInMillis) / millisInDay).toInt()
                    val msg = pluralStringResource(R.plurals.dm_next_available_in_days, daysLeft, daysLeft)
                    Button(
                        onClick = {
                            if (smartMatchAvailable) {
                                showSmartMatchDialog = true
                            } else {
                                msg
                            }
                        },
                        enabled = smartMatchAvailable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (smartMatchAvailable) Color(
                                0xFFFF4500,
                            ) else Color.DarkGray,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.action_smart_match),
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.dm_search_matches_hint), color = Color.Gray, fontSize = 12.sp) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = KupidxOrange,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFFFF4500),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .defaultMinSize(minHeight = dimensionResource(id = R.dimen.btn_height)),      // or just drop the size modifier
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
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
                        .clickable {
                            if (isPremiumUser) {
                                navController.navigate("peopleWhoLikedMe")
                            } else {
                                Toast.makeText(context, context.getString(R.string.dm_upgrade_plus_see_likes), Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+$likedCount",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
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
            val complimentItems = complimentProfiles.filter { cp ->
                !matchIds.contains(cp.profile.userId) && !blockedIds.contains(cp.profile.userId)
            }

            if (displayedUsers.isEmpty() && complimentItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.dm_no_matches), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkGrayBackground)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(complimentItems) { item ->
                        ComplimentCard(
                            profile = item.profile,
                            compliment = item.compliment,
                            onAccept = {
                                createMatch(database, currentUserId, item.profile.userId)
                                val updates = mapOf(
                                    "compliments/${item.profile.userId}/$currentUserId" to null,
                                    "complimentsReceived/$currentUserId/${item.profile.userId}" to null
                                )
                                database.reference.updateChildren(updates)
                            },
                            onReject = {
                                val updates = mapOf(
                                    "compliments/${item.profile.userId}/$currentUserId" to null,
                                    "complimentsReceived/$currentUserId/${item.profile.userId}" to null
                                )
                                database.reference.updateChildren(updates)
                            },
                            onClick = {
                                navController.navigate("previewUserProfile/${item.profile.userId}")
                            }
                        )
                    }
                    items(displayedUsers) { profile ->
                        val lastMsg = lastMessages[profile.userId] ?: Triple("", false, true)
                        DMUserCard(
                            profile = profile,
                            navController = navController,
                            lastMessage = lastMsg.first,
                            lastMessageFromCurrentUser = lastMsg.second,
                            lastMessageRead = lastMsg.third,
                            onRateClick = { selectedProfile ->
                                fetchUserRating(
                                    ratingsRef,
                                    selectedProfile.userId
                                ) { fetchedRating ->
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
                            stringResource(
                                R.string.dm_your_rating,
                                if (tempRating >= 0) String.format("%.1f", tempRating) else "N/A"
                            ),
                            color = Color.Gray, fontSize = 12.sp
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
                        Text(stringResource(R.string.dm_unmatch_with, profileToUnmatch!!.username), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.dm_unmatch_warning), color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                showUnmatchDialog = false
                                profileToUnmatch = null
                            }) {
                                Text(stringResource(R.string.action_cancel), color = Color.White)
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
                                Text(stringResource(R.string.action_unmatch), color = Color.Red)
                            }
                        }
                    }
                }
            }
        }

        if (showSmartMatchDialog) {
            AlertDialog(
                onDismissRequest = { showSmartMatchDialog = false },
                title = { Text(stringResource(R.string.smart_match_title), color = Color(0xFFFF4500)) },
                text = {
                    Column {
                        Text(stringResource(R.string.smart_match_select_gender), color = Color.White, fontSize = 12.sp)
                        // keep internal keys "Male"/"Female"/"Both" for backend matching
                        val opts = listOf("Male", "Female", "Both")
                        val labels = mapOf(
                            "Male" to stringResource(R.string.gender_male),
                            "Female" to stringResource(R.string.gender_female),
                            "Both" to stringResource(R.string.gender_both)
                        )
                        opts.forEach { key ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedSmartMatchGender == key,
                                    onClick = { selectedSmartMatchGender = key },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF4500))
                                )
                                Text(labels[key]!!, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSmartMatchDialog = false
                        handleSmartMatch(
                            currentUserId,
                            selectedSmartMatchGender,
                            database,
                            usersRef,
                            matchIds,
                            blockedIds,
                            context
                        )
                    }) {         Text(stringResource(R.string.action_smart_match), color = Color(0xFFFF4500)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSmartMatchDialog = false }) { Text("Cancel", color = Color.Gray) }
                }
            )
        }

        FloatingActionButton(
            onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
            containerColor = Color(0xFFFF4500),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.content_scroll_to_top), tint = Color.White)
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
            .background(DarkGrayBackground)
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
                    val displayName = profile.name.ifBlank { profile.username }
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 20.sp,
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
                    Text(stringResource(R.string.action_unmatch), color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun ComplimentCard(
    profile: Profile,
    compliment: ComplimentData,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(DarkGrayBackground)
            .border(
                BorderStroke(2.dp, getLevelBorderColor(profile.averageRating)),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
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
                        text = profile.name.ifBlank { profile.username },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val text = if (compliment.text.isNotBlank()) {
                        compliment.text
                    } else {
                        stringResource(R.string.dm_compliment_label)
                    }
                    Text(
                        text = text,
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
                TextButton(onClick = onAccept) { Text(stringResource(R.string.action_accept), color = Color(0xFFFF4500)) }
                TextButton(onClick = onReject) { Text(stringResource(R.string.action_reject), color = Color.Red) }

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
            Toast.makeText(context, context.getString(R.string.toast_unmatched_success), Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener { error ->
            Toast.makeText(context, context.getString(R.string.toast_unmatched_failed, error.message ?: "" ), Toast.LENGTH_SHORT).show()
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
): ValueEventListener {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val userIdsToFetch = snapshot.children.mapNotNull { it.key }
            Log.d("DMScreen", "Fetched user IDs from matches: $userIdsToFetch")

            if (userIdsToFetch.isNotEmpty()) {
                val newUsers = mutableListOf<Profile>()
                var remaining = userIdsToFetch.size

                userIdsToFetch.forEach { id ->
                    usersRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            userSnapshot.getValue(Profile::class.java)?.let { newUsers.add(it) }
                            remaining--
                            if (remaining == 0) {
                                usersList.clear()
                                usersList.addAll(newUsers)
                                Log.d(
                                    "DMScreen",
                                    "Populated matchedUsers with ${usersList.size} profiles: ${usersList.map { it.userId }}"
                                )
                                onComplete?.invoke()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("DMScreen", "DBError in fetchUsersFromNode: ${error.message}")
                            Toast.makeText(context, context.getString(R.string.toast_error_generic, error.message ?: "" ), Toast.LENGTH_SHORT).show()
                            remaining--
                            if (remaining == 0) {
                                usersList.clear()
                                usersList.addAll(newUsers)
                                onComplete?.invoke()
                            }
                        }
                    })
                }
            } else {
                usersList.clear()
                Log.d("DMScreen", "No user IDs to fetch, cleared matchedUsers")
                onComplete?.invoke()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("DMScreen", "DatabaseError in fetchUsersFromNode: ${error.message}")
            Toast.makeText(context, context.getString(R.string.toast_error_generic, error.message ?: "" ), Toast.LENGTH_SHORT).show()
        }
    }
    ref.addListenerForSingleValueEvent(listener)
    return listener
}

fun getLevelBorderColor(rating: Double): Color {
    return when {
        rating in 0.0..1.1 -> Color(0xFFFF6F00)
        rating in 1.1..2.1 -> Color.Green
        rating in 2.1..3.6 -> Color.Blue
        rating in 3.6..4.7 -> Color.Yellow
        rating in 4.7..5.0 -> Color(0xFFE91E63)
        else -> Color.Gray
    }
}

private fun fetchRandomUserForLottery(
    usersRef: DatabaseReference,
    gender: String,
    excludedIds: Set<String>,
    currentUserId: String,
    onResult: (Profile?) -> Unit
) {
    usersRef.get().addOnSuccessListener { snap ->
        val list = snap.children.mapNotNull { it.getValue(Profile::class.java) }
            .filter { it.userId != currentUserId && !excludedIds.contains(it.userId) }
            .filter {
                gender == "Both" || it.gender.toGenderCode() == gender.toGenderCode()
            }
        onResult(list.randomOrNull())
    }.addOnFailureListener { onResult(null) }
}

private fun createMatch(
    database: FirebaseDatabase,
    currentUserId: String,
    otherUserId: String
) {
    val ts = System.currentTimeMillis()
    val updates = mapOf(
        "matches/$currentUserId/$otherUserId" to ts,
        "matches/$otherUserId/$currentUserId" to ts
    )
    database.reference.updateChildren(updates)
}

private fun handleSmartMatch(
    currentUserId: String,
    gender: String,
    database: FirebaseDatabase,
    usersRef: DatabaseReference,
    matchIds: List<String>,
    blockedIds: List<String>,
    context: android.content.Context
) {
    val week = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    database.getReference("users/$currentUserId/lastSmartMatchWeekOfYear").setValue(week)

    val excluded = matchIds.toSet() + blockedIds.toSet() + setOf(currentUserId)
    fetchRandomUserForLottery(usersRef, gender, excluded, currentUserId) { profile ->
        if (profile != null) {
            createMatch(database, currentUserId, profile.userId)
            Toast.makeText(context, context.getString(R.string.dm_matched_with, profile.username), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.dm_no_match_available), Toast.LENGTH_SHORT).show()
        }
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
        Text(title, color = Color.White, fontSize = 11.sp)
    }
}