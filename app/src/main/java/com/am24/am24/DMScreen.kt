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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import java.util.Calendar
import kotlin.random.Random
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import com.google.firebase.functions.FirebaseFunctions

private fun canonicalLocationId(name: String): String {
    val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")              // strip accents/diacritics

    val cleaned = normalized
        .lowercase()
        .replace("[^a-z0-9]+".toRegex(), "_")       // collapse to word characters
        .trim('_')

    return cleaned.ifBlank { "general" }
}

data class ComplimentWithProfile(
    val profile: Profile,
    val compliment: ComplimentData
)

@Composable
fun DMScreen(
    navController: NavController,
    nearbyViewModel: NearbyViewModel,
    profileViewModel: ProfileViewModel,
    ) {
    DMScreenContent(navController, nearbyViewModel, profileViewModel)
}

@Composable
fun DMScreenContent(
    navController: NavController,
    nearbyViewModel: NearbyViewModel,
    profileViewModel: ProfileViewModel,
    ) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val context = LocalContext.current
    val database = remember { FirebaseRefs.db }
    val matchesRef = remember(currentUserId) { database.getReference("matches/$currentUserId") }
    val likesRef = remember(currentUserId) { database.getReference("likesReceived/$currentUserId") }
    val usersRef = remember { database.getReference("users") }
    val messagesRootRef = remember { database.getReference("messages") }
    val ratingsRef = remember { database.getReference("ratings") }
    val datingViewModel: DatingViewModel = viewModel()
    val compliments by datingViewModel.complimentsReceived.collectAsState()
    val sessionReady by SessionDataRepository.sessionReady.collectAsState(initial = false)
    val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()

    var showRatingOverlay by remember { mutableStateOf(false) }
    var profileToRate by remember { mutableStateOf<Profile?>(null) }
    var tempRating by remember { mutableStateOf(-1.0) }
    var showUnmatchDialog by remember { mutableStateOf(false) }
    var profileToUnmatch by remember { mutableStateOf<Profile?>(null) }
    var showSmartMatchDialog by remember { mutableStateOf(false) }
    var selectedSmartMatchGender by remember { mutableStateOf("Both") }

    LaunchedEffect(currentUserId) {
        profileViewModel.fetchCurrentUserProfile()
    }

    // Show loading UI while profile is being fetched
    if (!sessionReady) {
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
    LaunchedEffect(autoScrollState) {
        // wait for the scroll to measure
        val hasOverflow = snapshotFlow { autoScrollState.maxValue }

            .filter { it > 0 }       // only once it's actually overflowed
            .firstOrNull()           // suspend until >0
        if (hasOverflow == null) {
            autoScrollState.scrollTo(0)
            return@LaunchedEffect
        }
        while (isActive) {
            autoScrollState.animateScrollTo(autoScrollState.maxValue)
            delay(2000)              // pause at end
            autoScrollState.animateScrollTo(0)
            delay(2000)              // pause at start
        }
    }


// 1️⃣  Build the chip list
    val groupChatTitles = remember(profile) {
        buildList {
            profile.country
                .takeIf { it.isNotBlank() }
                ?.let { add(it) }
            profile.city
                .takeIf { it.isNotBlank() }?.let { add(it) }
            profile.hometown
                .takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()
    }


    var searchQuery by remember { mutableStateOf("") }
    var likedCount by remember { mutableStateOf(0) }
    val matchedUsers = remember { mutableStateListOf<Profile>() }
    val nonInitiatedMatches = remember { mutableStateListOf<Profile>() }
    val lastMessages = remember { mutableStateMapOf<String, Triple<String, Boolean, Boolean>>() }
    val prefetchedUrls = remember { mutableStateOf(mutableSetOf<String>()) }
    val complimentProfiles = remember { mutableStateListOf<ComplimentWithProfile>() }
    val complimentQueue = remember { mutableStateListOf<ComplimentWithProfile>() }
    var calledSweepOnce by remember { mutableStateOf(false) }
    val functions = remember { FirebaseFunctions.getInstance("asia-south1") }
    // — new: grab your blocks
    val blockedRef = database.getReference("blocks/$currentUserId")
    val blockedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(compliments) {
        complimentProfiles.clear()
        if (compliments.isEmpty()) {
            complimentQueue.clear()
            return@LaunchedEffect
        }
        val resolved = coroutineScope {
            compliments.map { (senderId, compliment) ->
                async {
                    try {
                        val snap = usersRef.child(senderId).get().await()
                        if (UserDeletionCache.isDeleted(FirebaseRefs.db, senderId, snap)) {
                            return@async null
                        }
                        val profile = snap.getValue(Profile::class.java) ?: return@async null
                        if (profile.username.isNullOrBlank()) {
                            UserDeletionCache.markDeleted(senderId)
                            return@async null
                        }
                        UserDeletionCache.markActive(senderId)
                        ComplimentWithProfile(profile, compliment)
                    } catch (e: Exception) {
                        Log.e("DMScreen", "Failed to load compliment sender $senderId", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull().sortedByDescending { it.compliment.timestamp }
        }
        complimentProfiles.addAll(resolved)
        complimentQueue.clear()
        complimentQueue.addAll(resolved)
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

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) return@LaunchedEffect
        try {
            val snapshot = matchesRef.get().await()
            val userIdsToFetch = snapshot.children.mapNotNull { it.key }
            val fetchedProfiles = fetchProfiles(usersRef, userIdsToFetch)
            matchedUsers.clear()
            matchedUsers.addAll(fetchedProfiles)

            val nonInitiated = fetchNonInitiatedConversations(fetchedProfiles, messagesRootRef, currentUserId)
            nonInitiatedMatches.clear()
            nonInitiatedMatches.addAll(nonInitiated)

            matchedUsers.forEach { profile ->
                val url = profile.profilepicThumbnailUrl ?: profile.profilepicUrl
                url?.let {
                    if (prefetchedUrls.value.add(it)) {
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

            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            val activeChatIds = mutableSetOf<String>()
            matchedUsers.forEach { profile ->
                val chatId = getChatId(uid, profile.userId)
                activeChatIds.add(chatId)
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
            val staleChatIds = messageListeners.keys - activeChatIds
            staleChatIds.forEach { chatId ->
                messageListeners.remove(chatId)?.let { listener ->
                    messagesRootRef.child(chatId).removeEventListener(listener)
                }
            }
        } catch (e: Exception) {
            Log.e("DMScreen", "Failed to load matches", e)
            Toast.makeText(
                context,
                context.getString(R.string.toast_error_generic, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
            matchedUsers.clear()
            nonInitiatedMatches.clear()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            messageListeners.forEach { (chatId, listener) ->
                messagesRootRef.child(chatId).removeEventListener(listener)
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
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor  = KupidxOrange,
                    unfocusedIndicatorColor  = Color.Gray,
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
                Text(
                    text = stringResource(R.string.dm_likes_label),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .clickable {
                        // 🔸 Always attempt the sweep once, even if not Plus
                                                        if (!calledSweepOnce) {
                                                        calledSweepOnce = true
                                                        try {
                                                                val data = hashMapOf("source" to "peopleWhoLikedMe_chip")
                                                                FirebaseFunctions.getInstance("asia-south1")
                                                                .getHttpsCallable("loginEntitlementSweep")
                                                                    .call(data)
                                                                    .addOnSuccessListener {
                                                                            Log.d("DMScreen", "loginEntitlementSweep ok")
                                                                        }
                                                                    .addOnFailureListener { e ->
                                                                            Log.w("DMScreen", "loginEntitlementSweep failed", e)
                                                                        }
                                                            } catch (e: Exception) {
                                                               Log.w("DMScreen", "loginEntitlementSweep invoke error", e)
                                                            }
                                                    }
                                                        if (isPremiumUser) {
                                                        navController.navigate("peopleWhoLikedMe")
                                                    } else {
                                                        Toast.makeText(
                                                                context,
                                                                context.getString(R.string.dm_upgrade_plus_see_likes),
                                                                Toast.LENGTH_SHORT
                                                                    ).show()
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
            val complimentItems = complimentQueue.filter { cp ->
                !matchIds.contains(cp.profile.userId) && !blockedIds.contains(cp.profile.userId)
            }

            val hasAnyContent = displayedUsers.isNotEmpty() || complimentItems.isNotEmpty()

            if (!hasAnyContent) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.dm_no_matches), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkGrayBackground)
                        .padding(12.dp)
                        .visibleScrollbar(listState),
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
        val complimentItems = complimentQueue.filter { cp ->
            !matchIds.contains(cp.profile.userId) && !blockedIds.contains(cp.profile.userId)
        }
        if (complimentItems.isNotEmpty()) {
            ComplimentPopupStack(
                items = complimentItems,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                onAccept = { item ->
                    createMatch(database, currentUserId, item.profile.userId)
                    val updates = mapOf(
                        "compliments/${item.profile.userId}/$currentUserId" to null,
                        "complimentsReceived/$currentUserId/${item.profile.userId}" to null
                    )
                    complimentQueue.remove(item)
                    complimentProfiles.removeAll { it.profile.userId == item.profile.userId }
                    database.reference.updateChildren(updates)
                },
                onReject = { item ->
                    val updates = mapOf(
                        "compliments/${item.profile.userId}/$currentUserId" to null,
                        "complimentsReceived/$currentUserId/${item.profile.userId}" to null
                    )
                    complimentQueue.remove(item)
                    complimentProfiles.removeAll { it.profile.userId == item.profile.userId }
                    database.reference.updateChildren(updates)
                },
                onOpenProfile = { item ->
                    navController.navigate("previewUserProfile/${item.profile.userId}")
                }
            )
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
                        coroutineScope.launch {
                            handleSmartMatch(
                                currentUserId,
                                selectedSmartMatchGender,
                                database,
                                usersRef,
                                matchIds,
                                blockedIds,
                                context
                            )
                        }
                    }) {
                        Text(stringResource(R.string.action_smart_match), color = Color(0xFFFF4500))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSmartMatchDialog = false }) {
                        Text(stringResource(R.string.cancel), color = Color.Gray)
                    }
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

@Composable
private fun ComplimentPopupStack(
    items: List<ComplimentWithProfile>,
    modifier: Modifier = Modifier,
    onAccept: (ComplimentWithProfile) -> Unit,
    onReject: (ComplimentWithProfile) -> Unit,
    onOpenProfile: (ComplimentWithProfile) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        items.take(3).forEach { item ->
            ComplimentPopupCard(
                complimentWithProfile = item,
                onAccept = { onAccept(item) },
                onReject = { onReject(item) },
                onOpenProfile = { onOpenProfile(item) }
            )
        }
    }
}

@Composable
private fun ComplimentPopupCard(
    complimentWithProfile: ComplimentWithProfile,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val profile = complimentWithProfile.profile
    val compliment = complimentWithProfile.compliment
    Surface(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = Color(0xCC1F1F1F),
        border = BorderStroke(1.dp, getLevelBorderColor(profile.averageRating))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AIOrProfileImage(
                    profile = profile,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.name.ifBlank { profile.username },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val complimentText = if (compliment.text.isNotBlank()) {
                        compliment.text
                    } else {
                        stringResource(R.string.dm_compliment_label)
                    }
                    Text(
                        text = complimentText,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenProfile) {
                    Text(
                        text = stringResource(R.string.dm_compliment_view_profile),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onReject) {
                    Text(
                        text = stringResource(R.string.action_reject),
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onAccept) {
                    Text(
                        text = stringResource(R.string.action_accept),
                        color = Color(0xFFFF4500),
                        fontSize = 12.sp
                    )
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
            Toast.makeText(context, context.getString(R.string.toast_unmatched_success), Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener { error ->
            Toast.makeText(context, context.getString(R.string.toast_unmatched_failed, error.message ?: "" ), Toast.LENGTH_SHORT).show()
            Log.e("DMScreen", "Unmatch failed: ${error.message}")
        }
}

private suspend fun fetchNonInitiatedConversations(
    matchedUsers: List<Profile>,
    messagesRootRef: DatabaseReference,
    currentUserId: String
): List<Profile> = coroutineScope {
    if (matchedUsers.isEmpty()) {
        return@coroutineScope emptyList<Profile>()
    }

    matchedUsers.map { profile ->
        async {
            try {
                val chatId = getChatId(currentUserId, profile.userId)
                val snapshot = messagesRootRef.child(chatId).limitToFirst(1).get().await()
                if (!snapshot.exists()) profile else null
            } catch (e: Exception) {
                Log.e("DMScreen", "Failed to inspect conversation for ${profile.userId}", e)
                null
            }
        }
        }.awaitAll().filterNotNull()
    }

private suspend fun fetchProfiles(
    usersRef: DatabaseReference,
    userIds: List<String>
): List<Profile> = coroutineScope {
    if (userIds.isEmpty()) {
        return@coroutineScope emptyList<Profile>()
    }
    userIds.map { id ->
        async {
            try {
                val snapshot = usersRef.child(id).get().await()
                if (UserDeletionCache.isDeleted(FirebaseRefs.db, id, snapshot)) {
                    return@async null
                }
                val profile = snapshot.getValue(Profile::class.java)
                if (profile != null && profile.username.isNullOrBlank()) {
                    UserDeletionCache.markDeleted(id)
                    return@async null
                }
                UserDeletionCache.markActive(id)
                profile
            } catch (e: Exception) {
                Log.e("DMScreen", "Failed to fetch profile for $id", e)
                null
            }
        }
    }.awaitAll().filterNotNull()
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

private suspend fun fetchRandomUserForLottery(
    usersRef: DatabaseReference,
    gender: String,
    excludedIds: Set<String>,
    currentUserId: String
): Profile? {
    val snap = usersRef.get().await()

    val list = snap.children.mapNotNull { child ->
        val uid = child.key ?: return@mapNotNull null

        // ✅ OK now: we're inside a suspend function
        if (UserDeletionCache.isDeleted(FirebaseRefs.db, uid, child)) return@mapNotNull null

        val profile = child.getValue(Profile::class.java) ?: return@mapNotNull null
        if (profile.username.isNullOrBlank()) {
            UserDeletionCache.markDeleted(uid)
            return@mapNotNull null
        }
        UserDeletionCache.markActive(uid)
        profile
    }
        .filter { it.userId != currentUserId && !excludedIds.contains(it.userId) }
        .filter { gender == "Both" || it.gender.toGenderCode() == gender.toGenderCode() }

    return list.randomOrNull()
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

private suspend fun handleSmartMatch(
    currentUserId: String,
    gender: String,
    database: FirebaseDatabase,
    usersRef: DatabaseReference,
    matchIds: List<String>,
    blockedIds: List<String>,
    context: android.content.Context
) {
    val week = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    database.getReference("users/$currentUserId/lastSmartMatchWeekOfYear").setValue(week).await()

    val excluded = matchIds.toSet() + blockedIds.toSet() + setOf(currentUserId)
    val profile = fetchRandomUserForLottery(usersRef, gender, excluded, currentUserId)

    if (profile != null) {
        createMatch(database, currentUserId, profile.userId)
        Toast.makeText(context, context.getString(R.string.dm_matched_with, profile.username), Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, context.getString(R.string.dm_no_match_available), Toast.LENGTH_SHORT).show()
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