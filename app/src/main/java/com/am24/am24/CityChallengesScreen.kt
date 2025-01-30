@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.annotation.RequiresApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
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

    // All challenges for the city
    var challenges by remember { mutableStateOf<List<CityChallenge>>(emptyList()) }
    // User's responses
    var userResponses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Completion counts
    var completionCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // Tabs
    val tabCategories = listOf("Unread", "Read")
    var selectedTabIndex by remember { mutableStateOf(0) }
    // For showing a specific challenge
    var selectedChallenge by remember { mutableStateOf<CityChallenge?>(null) }
    var currentStepId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            // Fetch challenges
            val challengeSnap = challengesRef.get().await()
            val tmpChallenges = mutableListOf<CityChallenge>()
            challengeSnap.children.forEach { snap ->
                val challenge = snap.getValue(CityChallenge::class.java)
                if (challenge != null && challenge.city == city) {
                    tmpChallenges.add(challenge)
                }
            }

            // Fetch user responses
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
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Black,
                    contentColor = Color(0xFFFF6F00)
                ) {
                    tabCategories.forEachIndexed { i, tabLabel ->
                        Tab(
                            selected = (selectedTabIndex == i),
                            onClick = {
                                selectedTabIndex = i
                                selectedChallenge = null
                                currentStepId = null
                            },
                            text = {
                                Text(
                                    text = tabLabel,
                                    color = if (selectedTabIndex == i) Color(0xFFFF6F00) else Color.White
                                )
                            }
                        )
                    }
                }

                when (tabCategories[selectedTabIndex]) {
                    "Unread" -> ShowChallengesList(
                        challenges = unreadChallenges,
                        userResponses = userResponses,
                        completionCounts = completionCounts,
                        onChallengeSelected = { challenge ->
                            selectedChallenge = challenge
                            currentStepId = challenge.steps.firstOrNull()?.id
                        }
                    )

                    "Read" -> ShowChallengesList(
                        challenges = readChallenges,
                        userResponses = userResponses,
                        completionCounts = completionCounts,
                        onChallengeSelected = { challenge ->
                            selectedChallenge = challenge
                            currentStepId = null // Read challenges won't replay the steps
                        }
                    )
                }
            }

            selectedChallenge?.let { challenge ->
                ShowChallengeDetail(
                    challenge = challenge,
                    currentStepId = currentStepId,
                    userResponsesRef = userResponsesRef,
                    currentUserId = currentUserId,
                    onStepChanged = { stepId -> currentStepId = stepId },
                    onEndingReached = { endingId ->
                        scope.launch {
                            saveUserChallengeResponse(userResponsesRef, challenge.id, endingId, currentUserId)
                            userResponses = userResponses.toMutableMap().apply {
                                put(challenge.id, endingId)
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
            }
        }
    }
}

@Composable
fun ShowChallengesList(
    challenges: List<CityChallenge>,
    userResponses: Map<String, String>,
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
    // Stack to keep track of navigation history for steps
    val navigationStack = remember { mutableStateListOf<String>() }

    // Push the current step ID onto the stack if it's not already there
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
                            // Pop the current step and navigate to the previous one
                            navigationStack.removeLast()
                            onStepChanged(navigationStack.last())
                        } else {
                            // If no more steps in the stack, close the detail screen
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
        val currentStep = challenge.steps.find { it.id == currentStepId }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .background(Color(0xFF1A1A1A))
        ) {
            Text(
                text = challenge.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                            onEndingReached(choice.endingId)
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
