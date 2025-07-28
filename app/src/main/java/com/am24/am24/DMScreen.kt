@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.app.Activity
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
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
    var isLoadingProfile by remember { mutableStateOf(true) } // Track loading state
    var currentUserProfile by remember { mutableStateOf<Profile?>(null) }
    val activity = LocalContext.current as Activity
    val lotteryAdManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedLottery(activity)) }
    DisposableEffect(Unit) { onDispose { lotteryAdManager.clearCallbacks() } }

    var showLotteryDialog by remember { mutableStateOf(false) }
    var selectedLotteryGender by remember { mutableStateOf("Both") }
    LaunchedEffect(currentUserId) {
        usersRef.child(currentUserId).get()
            .addOnSuccessListener { snap ->
                currentUserProfile = snap.getValue(Profile::class.java)
                isLoadingProfile = false
            }
            .addOnFailureListener {
                isLoadingProfile = false // Handle error appropriately
                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
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
            Text(
                "Error loading profile",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    val isPremiumUser = profile.isPremium || profile.isPlus
    val todayDay = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }
    val lotteryAvailable = remember(profile.lastLotteryDayOfYear) { profile.lastLotteryDayOfYear != todayDay }

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


    /* ──────  LOCATION-SELECTOR STATE  ────── */
    var showLocationSelector by rememberSaveable { mutableStateOf(false) }

    /* string-array resources → Lists */
    val countryOptions = stringArrayResource(R.array.country_names).toList()
    val cityOptions    = stringArrayResource(R.array.city_names).toList()

    var selectedCountry  by rememberSaveable { mutableStateOf(countryOptions.first()) }
    var countryExpanded  by remember { mutableStateOf(false) }

    var selectedCity     by rememberSaveable { mutableStateOf(cityOptions.first()) }
    var cityExpanded     by remember { mutableStateOf(false) }

    /**  Dynamically look-up the correct `localities_<city>` array  */
    val localityResId = remember(selectedCity) {
        context.resources.getIdentifier(
            "localities_" + selectedCity.replace(" ", "_").lowercase(),
            "array",
            context.packageName
        )
    }
    val localityOptions = remember(localityResId) {
        if (localityResId != 0)
            context.resources.getStringArray(localityResId).toList()
        else emptyList()
    }

    var selectedLocality by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(localityOptions) {
        selectedLocality = localityOptions.firstOrNull() ?: ""
    }
    var localityExpanded by remember { mutableStateOf(false) }

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
    // — new: grab your blocks
    val blockedRef = database.getReference("blocks/$currentUserId")
    val blockedIds = remember { mutableStateListOf<String>() }

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
            /* ①  COUNTRY / CITY CHIP ROW  **OR**  DROPDOWNs */
            if (showLocationSelector) {
                LocationSelectorComposable(navController)
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showLocationSelector = false },
                    modifier = Modifier.align(Alignment.End).padding(8.dp)
                ) {
                    Text("Cancel", color = Color(0xFFFF4500))
                }
            }
            else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cts = LocalContext.current
                    Log.d("DMScreen", "Change Location clicked: isPremiumUser=$isPremiumUser")
                    GroupChatChip("Change Location") {
                        if (isPremiumUser) {
                            showLocationSelector = true     // 🟢 premium users see the selector
                        } else {
                            Toast
                                .makeText(
                                    cts,
                                    "Upgrade to Plus to change location",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    }

                    Spacer(Modifier.width(12.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(autoScrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 2️⃣  Map chip → chat-room ID
                        groupChatTitles.forEach { title ->
                            GroupChatChip(title) {
                                val id = when (title) {
                                    "India" -> "group_india"
                                    "United States" -> "group_usa"// ← new constant
                                    else -> "group_${title.replace(" ", "_").lowercase()}"
                                }
                                navController.navigate("groupChat/$id")
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Button(
                            onClick = { showLotteryDialog = true },
                            enabled = lotteryAvailable,
                            colors = ButtonDefaults.buttonColors(containerColor = if (lotteryAvailable) Color(0xFFFF4500) else Color.DarkGray)
                        ) {
                            Text("Lottery", color = Color.White, fontSize = 10.sp)
                        }
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
                        .defaultMinSize(minHeight = 56.dp),      // or just drop the size modifier
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
                                    Toast
                                        .makeText(
                                            context,
                                            "Upgrade to Plus to see who liked you.",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
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

                if (displayedUsers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No matches found",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                            "Your Rating: ${if (tempRating >= 0) String.format("%.1f", tempRating) else "N/A"}",
                            color = Color.Gray,
                            fontSize = 12.sp
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This will remove the match and delete your conversation history.",
                            color = Color.Gray,
                            fontSize = 12.sp
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

        if (showLotteryDialog) {
            AlertDialog(
                onDismissRequest = { showLotteryDialog = false },
                title = { Text("Lottery Match", color = Color(0xFFFF4500)) },
                text = {
                    Column {
                        Text("Select gender preference", color = Color.White, fontSize = 12.sp)
                        val opts = listOf("Male", "Female", "Both")
                        opts.forEach { opt ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedLotteryGender == opt,
                                    onClick = { selectedLotteryGender = opt },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF4500))
                                )
                                Text(opt, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLotteryDialog = false
                        lotteryAdManager.showWithDailyLimit(
                            userId = currentUserId,
                            onReward = {
                                handleLotteryResult(
                                    currentUserId,
                                    selectedLotteryGender,
                                    database,
                                    usersRef,
                                    matchIds,
                                    blockedIds,
                                    context
                                )
                            }
                        )
                    }) { Text("Watch Ad", color = Color(0xFFFF4500)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLotteryDialog = false }) { Text("Cancel", color = Color.Gray) }
                }
            )
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
fun LocationSelectorComposable(navController: NavController) {
    val context = LocalContext.current

    val countryOptions = stringArrayResource(R.array.country_names).toList()
    val cityOptions = stringArrayResource(R.array.city_names).toList()

    var selectedCountry by rememberSaveable { mutableStateOf("") }
    var selectedCity by rememberSaveable { mutableStateOf("") }
    var selectedLocality by rememberSaveable { mutableStateOf("") }

    var countryExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var localityExpanded by remember { mutableStateOf(false) }

    val citySelectable = selectedCountry.isNotBlank()
    val localitySelectable = selectedCity.isNotBlank()

    val localityResId = remember(selectedCity) {
        context.resources.getIdentifier(
            "localities_${selectedCity.replace(" ", "_").lowercase()}",
            "array",
            context.packageName
        )
    }

    val localityOptions = remember(localityResId) {
        if (localityResId != 0)
            context.resources.getStringArray(localityResId).toList()
        else emptyList()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        DropdownField(
            label = "Country",
            options = countryOptions,
            selected = selectedCountry,
            onSelectionChange = {
                selectedCountry = it
                selectedCity = ""
                selectedLocality = ""
            },
            expanded = countryExpanded,
            onExpandedChange = { countryExpanded = it }
        )

        Spacer(Modifier.height(8.dp))

        DropdownField(
            label = "City",
            options = cityOptions,
            selected = selectedCity,
            onSelectionChange = {
                selectedCity = it
                selectedLocality = ""
            },
            expanded = cityExpanded,
            onExpandedChange = { cityExpanded = it },
            enabled = citySelectable
        )

        Spacer(Modifier.height(8.dp))

        DropdownField(
            label = "Locality",
            options = localityOptions,
            selected = selectedLocality,
            onSelectionChange = { selectedLocality = it },
            expanded = localityExpanded,
            onExpandedChange = { localityExpanded = it },
            enabled = localitySelectable && localityOptions.isNotEmpty()
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            val destination = when {
                selectedLocality.isNotBlank() -> selectedLocality
                selectedCity.isNotBlank() -> selectedCity
                selectedCountry.isNotBlank() -> selectedCountry
                else -> ""
            }

            if (destination.isNotBlank()) {
                navController.navigate(
                    "groupChat/group_${destination.replace(" ", "_").lowercase()}"
                )
            } else {
                Toast.makeText(context, "Please select at least Country", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Save")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelectionChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val placeholder = "Not selected"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(!expanded) }
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selected.ifEmpty { placeholder },
            onValueChange = {},
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            colors = TextFieldDefaults.outlinedTextFieldColors(cursorColor = KupidxOrange)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectionChange(option)
                        onExpandedChange(false)
                    }
                )
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
                            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }
    ref.addValueEventListener(listener)
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
            .filter { gender == "Both" || it.gender.equals(gender, true) }
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

private fun handleLotteryResult(
    currentUserId: String,
    gender: String,
    database: FirebaseDatabase,
    usersRef: DatabaseReference,
    matchIds: List<String>,
    blockedIds: List<String>,
    context: android.content.Context
) {
    val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    database.getReference("users/$currentUserId/lastLotteryDayOfYear").setValue(today)

    if (Random.nextInt(100) < 25) {
        val excluded = matchIds.toSet() + blockedIds.toSet() + setOf(currentUserId)
        fetchRandomUserForLottery(usersRef, gender, excluded, currentUserId) { profile ->
            if (profile != null) {
                createMatch(database, currentUserId, profile.userId)
                Toast.makeText(context, "Matched with ${profile.username}!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No user found", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        Toast.makeText(context, "Better luck next time!", Toast.LENGTH_SHORT).show()
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