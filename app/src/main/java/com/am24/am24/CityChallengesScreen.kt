@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.annotation.RequiresApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@RequiresApi(35)
@Composable
fun CityChallengesScreen() {
    val city = "Kolkata"

    // Firebase references
    val challengesDb = FirebaseDatabase.getInstance("https://am-twentyfour.firebaseio.com/")
    val challengesRef = challengesDb.getReference("challenges")
    val userResponsesRef = challengesDb.getReference("user_challenge_responses")

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }

    var challenges by remember { mutableStateOf<List<CityChallenge>>(emptyList()) }
    var userResponses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var completionCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val tabCategories = listOf("Unread", "Read")
    var selectedTabIndex by remember { mutableStateOf(0) }
    var selectedChallenge by remember { mutableStateOf<CityChallenge?>(null) }
    var currentStepId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val challengeSnap = challengesRef.get().await()
            val tmpChallenges = mutableListOf<CityChallenge>()
            challengeSnap.children.forEach { snap ->
                val challenge = snap.getValue(CityChallenge::class.java)
                if (challenge != null && challenge.city == city) {
                    tmpChallenges.add(challenge)
                }
            }

            val responseSnap = userResponsesRef.get().await()
            val tmpResponses = mutableMapOf<String, String>()
            val tmpCompletionCounts = mutableMapOf<String, Int>()

            responseSnap.children.forEach { snap ->
                val challengeId = snap.child("challengeId").getValue(String::class.java)
                val userId = snap.child("userId").getValue(String::class.java)
                val endingId = snap.child("endingId").getValue(String::class.java)
                if (challengeId != null && endingId != null) {
                    tmpCompletionCounts[challengeId] = tmpCompletionCounts.getOrDefault(challengeId, 0) + 1
                }
                if (userId == currentUserId && challengeId != null && endingId != null) {
                    tmpResponses[challengeId] = endingId
                }
            }

            challenges = tmpChallenges
            userResponses = tmpResponses
            completionCounts = tmpCompletionCounts
        } finally {
            loading = false
        }
    }

    val unreadChallenges = challenges.filter { it.id !in userResponses.keys }
    val readChallenges = challenges.filter { it.id in userResponses.keys }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFFF6F00)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab Row with elevation and divider
                Surface(
                    color = Color.Black,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .height(48.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        selectedTabIndex = 0
                                        selectedChallenge = null
                                        currentStepId = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Unread",
                                    color = if (selectedTabIndex == 0) Color(0xFFFF6F00) else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Divider(
                                color = Color.DarkGray,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        selectedTabIndex = 1
                                        selectedChallenge = null
                                        currentStepId = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Read",
                                    color = if (selectedTabIndex == 1) Color(0xFFFF6F00) else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                when (tabCategories[selectedTabIndex]) {
                    "Unread" -> ShowChallengesList(
                        challenges = unreadChallenges,
                        userResponses = userResponses, // ✅ Fixed: Passing userResponses
                        completionCounts = completionCounts,
                        onChallengeSelected = { challenge ->
                            selectedChallenge = challenge
                            currentStepId = challenge.steps.firstOrNull()?.id
                        }
                    )
                    "Read" -> ShowChallengesList(
                        challenges = readChallenges,
                        userResponses = userResponses, // ✅ Fixed: Passing userResponses
                        completionCounts = completionCounts,
                        onChallengeSelected = { challenge ->
                            selectedChallenge = challenge
                            currentStepId = null
                        }
                    )
                }
            }

            selectedChallenge?.let { challenge ->
                if (selectedTabIndex == 0) {
                    ShowChallengeDetail(
                        challenge = challenge,
                        currentStepId = currentStepId,
                        userResponsesRef = userResponsesRef,
                        currentUserId = currentUserId,
                        onStepChanged = { stepId -> currentStepId = stepId },
                        onEndingReached = { endingId ->
                            scope.launch {
                                saveUserChallengeResponse(userResponsesRef, challenge.id, endingId, currentUserId)
                                userResponses = userResponses.toMutableMap().apply { put(challenge.id, endingId) }
                                completionCounts = completionCounts.toMutableMap().apply {
                                    put(challenge.id, (completionCounts[challenge.id] ?: 0) + 1)
                                }
                                selectedChallenge = null
                                currentStepId = null
                            }
                        },
                        onClose = {
                            selectedChallenge = null
                            currentStepId = null
                        }
                    )
                } else {
                    ShowChallengeDetailForRead(
                        challenge = challenge,
                        userResponsesRef = userResponsesRef,
                        currentUserId = currentUserId,
                        userResponses = userResponses,
                        onReDoChallenge = {
                            scope.launch {
                                clearUserChallengeResponse(userResponsesRef, challenge.id, currentUserId)
                                userResponses = userResponses.toMutableMap().apply { remove(challenge.id) }
                                completionCounts = completionCounts.toMutableMap().apply {
                                    put(challenge.id, (completionCounts[challenge.id] ?: 1) - 1)
                                }
                                selectedTabIndex = 0
                                currentStepId = challenge.steps.firstOrNull()?.id
                            }
                        },
                        onClose = {
                            selectedChallenge = null
                        }
                    )
                }
            }
        }
    }
}

suspend fun clearUserChallengeResponse(
    userResponsesRef: DatabaseReference,
    challengeId: String,
    currentUserId: String
) {
    val snapshot = userResponsesRef.get().await()
    snapshot.children.forEach { snap ->
        val uid = snap.child("userId").getValue(String::class.java)
        val cid = snap.child("challengeId").getValue(String::class.java)
        if (uid == currentUserId && cid == challengeId) {
            snap.ref.removeValue().await()
        }
    }
}

@Composable
fun ShowChallengesList(
    challenges: List<CityChallenge>,
    userResponses: Map<String, String>, // ✅ Ensure userResponses is included
    completionCounts: Map<String, Int>,
    onChallengeSelected: (CityChallenge) -> Unit
) {
    val sortedChallenges = challenges.sortedByDescending { completionCounts[it.id] ?: 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (sortedChallenges.isEmpty()) {
            Text(
                text = "No challenges available.",
                color = Color.White,
                fontSize = 16.sp
            )
        } else {
            sortedChallenges.forEach { challenge ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onChallengeSelected(challenge) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Completion count column
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(50.dp)
                            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (completionCounts[challenge.id] ?: 0).toString(),
                            color = Color(0xFFFF6F00),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    // Challenge details
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = challenge.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = challenge.description,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        userResponses[challenge.id]?.let { endingId ->
                            Text(
                                text = "Ending: $endingId",
                                color = Color(0xFFFF6F00),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


@RequiresApi(35)
@Composable
fun ShowChallengeDetail(
    challenge: CityChallenge,
    currentStepId: String?,
    userResponsesRef: DatabaseReference,
    currentUserId: String,
    onStepChanged: (String) -> Unit,
    onEndingReached: (String) -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showEndingDialog by remember { mutableStateOf(false) }
    var endingToShow by remember { mutableStateOf<Ending?>(null) }
    val navigationStack = remember { mutableStateListOf<String>() }
    if (currentStepId != null && (navigationStack.isEmpty() || navigationStack.last() != currentStepId)) {
        navigationStack.add(currentStepId)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Challenge Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navigationStack.size > 1) {
                            navigationStack.removeLast()
                            onStepChanged(navigationStack.last())
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF1A1A1A))
        ) {
            Text(
                text = challenge.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            val currentStep = challenge.steps.find { it.id == currentStepId }
            Text(
                text = currentStep?.text.orEmpty(),
                color = Color.White,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            currentStep?.choices?.forEach { choice ->
                Button(
                    onClick = {
                        if (choice.nextStepId != null) {
                            onStepChanged(choice.nextStepId)
                        } else if (choice.endingId != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            endingToShow = challenge.endings.find { it.id == choice.endingId }
                            if (endingToShow != null) {
                                showEndingDialog = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Text(text = choice.text, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Close", color = Color.White)
            }
        }
    }
    if (showEndingDialog && endingToShow != null) {
        AlertDialog(
            onDismissRequest = { showEndingDialog = false },
            title = { Text("Quiz Completed!") },
            text = { Text("${endingToShow!!.title}\n\n${endingToShow!!.description}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndingDialog = false
                        onEndingReached(endingToShow!!.id)
                    }
                ) {
                    Text("OK", color = Color(0xFFFF6F00))
                }
            }
        )
    }
}

@Composable
fun ShowChallengeDetailForRead(
    challenge: CityChallenge,
    userResponsesRef: DatabaseReference,
    currentUserId: String,
    userResponses: Map<String, String>,
    onClose: () -> Unit,
    onReDoChallenge: () -> Unit
) {
    var computedEndingPercentages by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(challenge, refreshKey) {
        val responseSnap = userResponsesRef.get().await()
        val endingCounts = mutableMapOf<String, Int>()
        var totalEndingResponses = 0
        responseSnap.children.forEach { snap ->
            val cid = snap.child("challengeId").getValue(String::class.java)
            if (cid == challenge.id) {
                val endingId = snap.child("endingId").getValue(String::class.java)
                if (endingId != null) {
                    endingCounts[endingId] = endingCounts.getOrDefault(endingId, 0) + 1
                    totalEndingResponses++
                }
            }
        }
        computedEndingPercentages = if (totalEndingResponses > 0) {
            endingCounts.mapValues { (eid, count) -> (count * 100) / totalEndingResponses }
        } else {
            emptyMap()
        }
    }

    Scaffold(
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(scrollState)
                    .background(Color(0xFF1A1A1A))
            ) {
                Text(
                    text = challenge.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Endings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                challenge.endings.forEach { ending ->
                    val percentage = computedEndingPercentages[ending.id] ?: 0
                    val isUserEnding = userResponses[challenge.id] == ending.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = ending.title,
                            color = if (isUserEnding) Color(0xFFFFDB00) else Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "$percentage%",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            clearUserChallengeResponse(userResponsesRef, challenge.id, currentUserId)
                            refreshKey++
                            onReDoChallenge()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                ) {
                    Text("Re‑do Quiz", color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Close", color = Color.White)
                }
            }
            FloatingActionButton(
                onClick = {
                    scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Color(0xFFFF6F00)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Scroll",
                    tint = Color.White
                )
            }
        }
    }
}

suspend fun saveUserChallengeResponse(
    userResponsesRef: DatabaseReference,
    challengeId: String,
    endingId: String,
    currentUserId: String
) {
    val newResponse = mapOf(
        "userId" to currentUserId,
        "challengeId" to challengeId,
        "endingId" to endingId
    )
    userResponsesRef.push().setValue(newResponse).await()
}
