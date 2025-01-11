@file:OptIn(ExperimentalMaterial3Api::class)
package com.am24.am24

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun PollsScreen() {
    val city = "Kolkata"

    // DB references
    val quizDb = FirebaseDatabase.getInstance("https://am-twentyfour.firebaseio.com/")
    val quizRef = quizDb.getReference("quizzes")
    val userResponsesRef = quizDb.getReference("user_responses")

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }

    var quizzes by remember { mutableStateOf<List<Quiz>>(emptyList()) }
    var allResponses by remember { mutableStateOf<List<UserResponse>>(emptyList()) }
    var userResponsesMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    val tabCategories = listOf("Popular", "New", "Completed")
    var selectedTabIndex by remember { mutableStateOf(0) }
    var selectedQuiz by remember { mutableStateOf<Quiz?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            // 1) fetch all quizzes for city
            val quizSnap = quizRef.get().await()
            val tmpQuizzes = mutableListOf<Quiz>()
            for (child in quizSnap.children) {
                val q = child.getValue(Quiz::class.java)
                if (q != null && q.city == city) {
                    tmpQuizzes.add(q)
                }
            }
            // 2) fetch all user responses for city
            val respSnap = userResponsesRef.get().await()
            val tmpAllResponses = mutableListOf<UserResponse>()
            val tmpUserMap = mutableMapOf<String, List<String>>()

            for (respChild in respSnap.children) {
                val response = respChild.getValue(UserResponse::class.java) ?: continue
                if (response.city == city) {
                    tmpAllResponses.add(response)
                    if (response.userId == currentUserId) {
                        tmpUserMap[response.quizId] = response.selectedOptions
                    }
                }
            }

            quizzes = tmpQuizzes
            allResponses = tmpAllResponses
            userResponsesMap = tmpUserMap
        } finally {
            loading = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFFF6F00)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // The tab row
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
                                selectedQuiz = null
                            },
                            text = {
                                Text(
                                    text = tabLabel,
                                    color = if (selectedTabIndex == i) Color(0xFFFF6F00) else Color.Gray
                                )
                            }
                        )
                    }
                }

                when (tabCategories[selectedTabIndex]) {
                    "Popular" -> ShowPopularTabAccordions(
                        quizzes = quizzes,
                        allResponses = allResponses,
                        userResponsesMap = userResponsesMap,
                        selectedQuiz = selectedQuiz,
                        onQuizSelected = { selectedQuiz = it },
                        onQuizClosed = { selectedQuiz = null },
                        onSaveResponse = { chosen ->
                            scope.launch {
                                selectedQuiz?.let { q ->
                                    saveUserResponse(
                                        userResponsesRef, q.id, chosen, city, currentUserId
                                    )
                                    userResponsesMap = userResponsesMap.toMutableMap().apply {
                                        put(q.id, chosen)
                                    }
                                    allResponses = allResponses + UserResponse(
                                        quizId = q.id,
                                        selectedOptions = chosen,
                                        userId = currentUserId,
                                        city = city,
                                        timestamp = System.currentTimeMillis()
                                    )
                                }
                                selectedQuiz = null
                            }
                        }
                    )

                    "New" -> ShowNewTabAccordions(
                        quizzes = quizzes,
                        allResponses = allResponses,
                        userResponsesMap = userResponsesMap,
                        selectedQuiz = selectedQuiz,
                        onQuizSelected = { selectedQuiz = it },
                        onQuizClosed = { selectedQuiz = null },
                        onSaveResponse = { chosen ->
                            scope.launch {
                                selectedQuiz?.let { q ->
                                    saveUserResponse(
                                        userResponsesRef, q.id, chosen, city, currentUserId
                                    )
                                    userResponsesMap = userResponsesMap.toMutableMap().apply {
                                        put(q.id, chosen)
                                    }
                                    allResponses = allResponses + UserResponse(
                                        quizId = q.id,
                                        selectedOptions = chosen,
                                        userId = currentUserId,
                                        city = city,
                                        timestamp = System.currentTimeMillis()
                                    )
                                }
                                selectedQuiz = null
                            }
                        }
                    )

                    "Completed" -> ShowCompletedTabAccordions(
                        quizzes = quizzes,
                        allResponses = allResponses,
                        userResponsesMap = userResponsesMap,
                        selectedQuiz = selectedQuiz,
                        onQuizSelected = { selectedQuiz = it },
                        onQuizClosed = { selectedQuiz = null }
                    )
                }
            }
        }
    }
}

/** Saves a user response into Firebase */
suspend fun saveUserResponse(
    userResponsesRef: DatabaseReference,
    quizId: String,
    chosen: List<String>,
    city: String,
    currentUserId: String
) {
    val userResp = UserResponse(
        quizId = quizId,
        selectedOptions = chosen,
        timestamp = System.currentTimeMillis(),
        userId = currentUserId,
        city = city
    )
    userResponsesRef.push().setValue(userResp).await()
}

// --------------------------------------------------------------------------
// ACCORDION Versions of each tab
// --------------------------------------------------------------------------

/**
 * "Popular" means >10 responses in last 24h, uncompleted by current user.
 * We group them by quiz.pollGrouping, and show an expand/collapse (accordion) for each group.
 */
@Composable
fun ShowPopularTabAccordions(
    quizzes: List<Quiz>,
    allResponses: List<UserResponse>,
    userResponsesMap: Map<String, List<String>>,
    selectedQuiz: Quiz?,
    onQuizSelected: (Quiz) -> Unit,
    onQuizClosed: () -> Unit,
    onSaveResponse: (List<String>) -> Unit
) {
    // 1 day ago
    val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000

    // Filter out user-completed
    val uncompleted = quizzes.filter { it.id !in userResponsesMap.keys }

    // Among these, “popular” means >10 responses last 24h
    val popularList = uncompleted.filter { quiz ->
        val rcount = allResponses.count { it.quizId == quiz.id && it.timestamp >= oneDayAgo }
        rcount > 10
    }

    if (selectedQuiz != null) {
        // Show detail
        ShowQuizDetail(
            quiz = selectedQuiz,
            allResponses = allResponses,
            existingResponses = userResponsesMap,
            onBack = onQuizClosed,
            allowSelection = true,
            onSubmit = onSaveResponse
        )
        return
    }

    // If no selected quiz, show the grouped expansions
    val grouped = popularList.groupBy { it.pollGrouping.ifBlank { "Misc" } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (grouped.isEmpty()) {
            Text("No popular polls (or all completed).", color = Color.White)
        } else {
            for ((groupName, quizGroup) in grouped) {
                // A local expand/collapse state
                var expanded by remember { mutableStateOf(false) }

                // The "header" for the group
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .border(1.dp, Color(0xFFFF6F00), RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Text(groupName, color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                }

                if (expanded) {
                    // Show quizzes in that grouping
                    quizGroup.forEach { q ->
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { onQuizSelected(q) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFF6F00), RoundedCornerShape(6.dp)),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            // Left align text by default
                            Text(q.question, color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * "New" means <=10 responses in last 24h, uncompleted by user.
 * Also grouped by quiz.pollGrouping.
 */
@Composable
fun ShowNewTabAccordions(
    quizzes: List<Quiz>,
    allResponses: List<UserResponse>,
    userResponsesMap: Map<String, List<String>>,
    selectedQuiz: Quiz?,
    onQuizSelected: (Quiz) -> Unit,
    onQuizClosed: () -> Unit,
    onSaveResponse: (List<String>) -> Unit
) {
    val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
    val uncompleted = quizzes.filter { it.id !in userResponsesMap.keys }

    val newList = uncompleted.filter { quiz ->
        val rcount = allResponses.count { it.quizId == quiz.id && it.timestamp >= oneDayAgo }
        rcount <= 10
    }

    if (selectedQuiz != null) {
        ShowQuizDetail(
            quiz = selectedQuiz,
            allResponses = allResponses,
            existingResponses = userResponsesMap,
            onBack = onQuizClosed,
            allowSelection = true,
            onSubmit = onSaveResponse
        )
        return
    }

    // Group by pollGrouping
    val grouped = newList.groupBy { it.pollGrouping.ifBlank { "Misc" } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (grouped.isEmpty()) {
            Text("No new polls (or all completed).", color = Color.White)
        } else {
            for ((groupName, quizGroup) in grouped) {
                var expanded by remember { mutableStateOf(false) }

                // Accordion header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .border(1.dp, Color(0xFFFF6F00), RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Text(groupName, color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                }

                if (expanded) {
                    quizGroup.forEach { q ->
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { onQuizSelected(q) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFF6F00), RoundedCornerShape(6.dp)),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Text(q.question, color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * "Completed" => user has answered, so we show them read-only in grouped expansions.
 * Tapping a quiz inside the group => see distribution detail (ShowQuizDetail read-only).
 */
@Composable
fun ShowCompletedTabAccordions(
    quizzes: List<Quiz>,
    allResponses: List<UserResponse>,
    userResponsesMap: Map<String, List<String>>,
    selectedQuiz: Quiz?,
    onQuizSelected: (Quiz) -> Unit,
    onQuizClosed: () -> Unit
) {
    val completedIds = userResponsesMap.keys
    val completedQuizzes = quizzes.filter { it.id in completedIds }

    if (selectedQuiz != null) {
        // Show read-only detail
        ShowQuizDetail(
            quiz = selectedQuiz,
            allResponses = allResponses,
            existingResponses = userResponsesMap,
            onBack = onQuizClosed,
            allowSelection = false
        )
        return
    }

    // Group by pollGrouping
    val grouped = completedQuizzes.groupBy { it.pollGrouping.ifBlank { "Misc" } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (grouped.isEmpty()) {
            Text("No completed polls yet.", color = Color.White)
        } else {
            for ((groupName, quizGroup) in grouped) {
                var expanded by remember { mutableStateOf(false) }

                // Accordion header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .border(1.dp, Color(0xFFFF6F00), RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Text(groupName, color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                }

                if (expanded) {
                    quizGroup.forEach { quiz ->
                        val userSelected = userResponsesMap[quiz.id].orEmpty()
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                // show detail
                                onQuizSelected(quiz)
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF6F00)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color.White
                            )
                        ) {
                            // Left align
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = quiz.question,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Your Pick: $userSelected",
                                    color = Color(0xFFFF6F00)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * The detail screen (same as before), but ensuring text is left-aligned
 * and using 1dp border only once.
 */
@Composable
fun ShowQuizDetail(
    quiz: Quiz,
    allResponses: List<UserResponse>,
    existingResponses: Map<String, List<String>>,
    onBack: () -> Unit,
    allowSelection: Boolean,
    onSubmit: (List<String>) -> Unit = {}
) {
    val userPicks = existingResponses[quiz.id].orEmpty()
    var localSelectedOptions by remember { mutableStateOf<List<String>>(emptyList()) }

    // If read-only => show user picks
    LaunchedEffect(quiz.id) {
        if (!allowSelection) {
            localSelectedOptions = userPicks
        }
    }

    // Build distribution
    val distribution = remember(quiz.id, allResponses) {
        val relevant = allResponses.filter { it.quizId == quiz.id }
        val counts = quiz.options.associateWith { 0 }.toMutableMap()
        var total = 0
        for (resp in relevant) {
            resp.selectedOptions.forEach { opt ->
                if (counts.containsKey(opt)) {
                    counts[opt] = counts[opt]!! + 1
                    total++
                }
            }
        }
        if (total == 0) {
            counts.mapValues { 0f }
        } else {
            counts.mapValues { (_, c) -> c.toFloat() / total.toFloat() * 100f }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(quiz.question, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))

        quiz.options.forEach { opt ->
            val isPicked = localSelectedOptions.contains(opt)
            val pct = distribution[opt] ?: 0f
            val display = String.format("%.1f%%", pct)

            if (allowSelection) {
                // user can pick
                val bgColor = if (isPicked) Color.Black else Color(0xFF1A1A1A)
                Button(
                    onClick = {
                        if (!allowSelection) return@Button
                        localSelectedOptions = if (isPicked) {
                            localSelectedOptions - opt
                        } else {
                            localSelectedOptions + opt
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, Color(0xFFFF6F00), RoundedCornerShape(6.dp)),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = bgColor)
                ) {
                    Text(opt, color = Color.White)
                }
            } else {
                // read-only distribution
                val txtColor = if (isPicked) Color(0xFFFF6F00) else Color.White
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF6F00)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF1A1A1A),
                        contentColor = txtColor
                    )
                ) {
                    Text("$opt  ($display)", color = txtColor)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Back", color = Color.White)
            }
            if (allowSelection) {
                Button(
                    onClick = { onSubmit(localSelectedOptions) },
                    shape = RoundedCornerShape(6.dp),
                    enabled = localSelectedOptions.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text("Submit", color = Color.White)
                }
            }
        }
    }
}
