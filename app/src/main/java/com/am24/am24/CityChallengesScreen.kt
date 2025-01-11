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
import kotlin.math.roundToInt

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

    // We'll store all quizzes
    var quizzes by remember { mutableStateOf<List<Quiz>>(emptyList()) }
    // We'll store all responses for city
    var allResponses by remember { mutableStateOf<List<UserResponse>>(emptyList()) }

    // Map: quizId -> UserResponse (with possible responseKey)
    var userResponsesMap by remember { mutableStateOf<Map<String, UserResponse>>(emptyMap()) }

    // Tabs
    val tabCategories = listOf("Popular", "New", "Completed")
    var selectedTabIndex by remember { mutableStateOf(0) }
    // If user taps for detail
    var selectedQuiz by remember { mutableStateOf<Quiz?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            // 1) fetch quizzes for city
            val quizSnap = quizRef.get().await()
            val tmpQuizzes = mutableListOf<Quiz>()
            quizSnap.children.forEach { snap ->
                val q = snap.getValue(Quiz::class.java)
                if (q != null && q.city == city) {
                    tmpQuizzes.add(q)
                }
            }

            // 2) fetch user responses for city
            val respSnap = userResponsesRef.get().await()
            val tmpAllResponses = mutableListOf<UserResponse>()
            val tmpUserMap = mutableMapOf<String, UserResponse>()

            respSnap.children.forEach { snap ->
                val response = snap.getValue(UserResponse::class.java)?.copy(
                    responseKey = snap.key ?: ""
                ) ?: return@forEach
                if (response.city == city) {
                    tmpAllResponses.add(response)
                    // If currentUser's response:
                    if (response.userId == currentUserId) {
                        tmpUserMap[response.quizId] = response
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

    // Compute "popularity" = total unique user count for that quiz
    val quizPopularityMap = remember(quizzes, allResponses) {
        val map = mutableMapOf<String, Int>()
        for (quiz in quizzes) {
            val userIds = allResponses.filter { it.quizId == quiz.id }.map { it.userId }.toSet()
            map[quiz.id] = userIds.size
        }
        map
    }

    // Sort quizzes by popularity desc, top 30% => "popular"
    val sortedByPopularity = quizzes.sortedByDescending { quizPopularityMap[it.id] ?: 0 }
    val cutoffIndex = (sortedByPopularity.size * 0.3).roundToInt()
    val popularQuizIds = sortedByPopularity.take(cutoffIndex).map { it.id }.toSet()

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
                                    color = if (selectedTabIndex == i) Color(0xFFFF6F00) else Color.White
                                )
                            }
                        )
                    }
                }

                when (tabCategories[selectedTabIndex]) {
                    "Popular" -> ShowPopularAccordions(
                        quizzes = quizzes,
                        quizPopularityMap = quizPopularityMap,
                        popularQuizIds = popularQuizIds,
                        allResponses = allResponses,
                        userResponsesMap = userResponsesMap,
                        selectedQuiz = selectedQuiz,
                        onQuizSelected = { q -> selectedQuiz = q },
                        onQuizClosed = { selectedQuiz = null },
                        onSaveResponse = { chosenOpts ->
                            scope.launch {
                                selectedQuiz?.let { quiz ->
                                    handleUserResponseEditOrCreate(
                                        userResponsesRef,
                                        quiz,
                                        chosenOpts,
                                        currentUserId,
                                        city,
                                        userResponsesMap
                                    )
                                    // Update local data
                                    userResponsesMap = userResponsesMap.toMutableMap().apply {
                                        put(
                                            quiz.id,
                                            this[quiz.id]?.copy(selectedOptions = chosenOpts)
                                                ?: UserResponse(
                                                    quizId = quiz.id,
                                                    selectedOptions = chosenOpts,
                                                    userId = currentUserId,
                                                    city = city
                                                )
                                        )
                                    }
                                }
                                selectedQuiz = null
                            }
                        }
                    )

                    "New" -> ShowNewAccordions(
                        quizzes = quizzes,
                        quizPopularityMap = quizPopularityMap,
                        popularQuizIds = popularQuizIds,
                        allResponses = allResponses,
                        userResponsesMap = userResponsesMap,
                        selectedQuiz = selectedQuiz,
                        onQuizSelected = { q -> selectedQuiz = q },
                        onQuizClosed = { selectedQuiz = null },
                        onSaveResponse = { chosenOpts ->
                            scope.launch {
                                selectedQuiz?.let { quiz ->
                                    handleUserResponseEditOrCreate(
                                        userResponsesRef,
                                        quiz,
                                        chosenOpts,
                                        currentUserId,
                                        city,
                                        userResponsesMap
                                    )
                                    userResponsesMap = userResponsesMap.toMutableMap().apply {
                                        put(
                                            quiz.id,
                                            this[quiz.id]?.copy(selectedOptions = chosenOpts)
                                                ?: UserResponse(
                                                    quizId = quiz.id,
                                                    selectedOptions = chosenOpts,
                                                    userId = currentUserId,
                                                    city = city
                                                )
                                        )
                                    }
                                }
                                selectedQuiz = null
                            }
                        }
                    )

                    "Completed" -> ShowCompletedAccordions(
                        quizzes = quizzes,
                        allResponses = allResponses,
                        userResponsesMap = userResponsesMap,
                        selectedQuiz = selectedQuiz,
                        onQuizSelected = { q -> selectedQuiz = q },
                        onQuizClosed = { selectedQuiz = null },
                        onSaveEdit = { chosenOpts ->
                            scope.launch {
                                selectedQuiz?.let { quiz ->
                                    // user is editing => do not increment popularity again
                                    // just update the existing record
                                    handleUserResponseEditOrCreate(
                                        userResponsesRef,
                                        quiz,
                                        chosenOpts,
                                        currentUserId,
                                        city,
                                        userResponsesMap,
                                        isEdit = true
                                    )
                                    userResponsesMap = userResponsesMap.toMutableMap().apply {
                                        put(
                                            quiz.id,
                                            this[quiz.id]?.copy(selectedOptions = chosenOpts)
                                                ?: UserResponse(
                                                    quizId = quiz.id,
                                                    selectedOptions = chosenOpts,
                                                    userId = currentUserId,
                                                    city = city
                                                )
                                        )
                                    }
                                }
                                selectedQuiz = null
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Creates or edits a user response.
 * If there's an existing record in `userResponsesMap`, we update that record in DB.
 * Otherwise we create a new one (which implicitly increments the quiz popularity).
 */
suspend fun handleUserResponseEditOrCreate(
    userResponsesRef: DatabaseReference,
    quiz: Quiz,
    chosenOpts: List<String>,
    currentUserId: String,
    city: String,
    userResponsesMap: Map<String, UserResponse>,
    isEdit: Boolean = false
) {
    val existingResp = userResponsesMap[quiz.id]
    if (existingResp != null) {
        // we are editing an existing response
        val responseKey = existingResp.responseKey
        if (responseKey.isBlank()) return
        val updated = existingResp.copy(
            selectedOptions = chosenOpts,
            timestamp = System.currentTimeMillis()
        )
        userResponsesRef.child(responseKey).setValue(updated).await()
    } else {
        // brand new record => only increment popularity if !isEdit
        val ref = userResponsesRef.push()
        val newResp = UserResponse(
            quizId = quiz.id,
            selectedOptions = chosenOpts,
            timestamp = System.currentTimeMillis(),
            userId = currentUserId,
            city = city,
            responseKey = ref.key ?: ""
        )
        ref.setValue(newResp).await()
    }
}

// -----------------------------------------------------------------
// ACCORDIONS: "Popular", "New", "Completed"
// -----------------------------------------------------------------

@Composable
fun ShowPopularAccordions(
    quizzes: List<Quiz>,
    quizPopularityMap: Map<String, Int>,
    popularQuizIds: Set<String>,  // top 30%
    allResponses: List<UserResponse>,
    userResponsesMap: Map<String, UserResponse>,
    selectedQuiz: Quiz?,
    onQuizSelected: (Quiz) -> Unit,
    onQuizClosed: () -> Unit,
    onSaveResponse: (List<String>) -> Unit
) {
    if (selectedQuiz != null) {
        ShowQuizDetail(
            quiz = selectedQuiz,
            allResponses = allResponses,
            existingResponses = userResponsesMap.mapValues { it.value.selectedOptions },
            onBack = onQuizClosed,
            // user can pick
            allowSelection = true,
            onSubmit = onSaveResponse
        )
        return
    }

    val uncompleted = quizzes.filter { it.id !in userResponsesMap.keys && it.id in popularQuizIds }
    val grouped = uncompleted.groupBy { it.pollGrouping.ifBlank { "Misc" } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (grouped.isEmpty()) {
            Text("No popular polls found (or all completed).", color = Color.White)
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

@Composable
fun ShowNewAccordions(
    quizzes: List<Quiz>,
    quizPopularityMap: Map<String, Int>,
    popularQuizIds: Set<String>,
    allResponses: List<UserResponse>,
    userResponsesMap: Map<String, UserResponse>,
    selectedQuiz: Quiz?,
    onQuizSelected: (Quiz) -> Unit,
    onQuizClosed: () -> Unit,
    onSaveResponse: (List<String>) -> Unit
) {
    if (selectedQuiz != null) {
        ShowQuizDetail(
            quiz = selectedQuiz,
            allResponses = allResponses,
            existingResponses = userResponsesMap.mapValues { it.value.selectedOptions },
            onBack = onQuizClosed,
            allowSelection = true,
            onSubmit = onSaveResponse
        )
        return
    }

    // "New" => not completed, and not in popular
    val uncompleted = quizzes.filter { it.id !in userResponsesMap.keys }
    val newList = uncompleted.filter { it.id !in popularQuizIds }

    val grouped = newList.groupBy { it.pollGrouping.ifBlank { "Misc" } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (grouped.isEmpty()) {
            Text("No new polls or all completed.", color = Color.White)
        } else {
            for ((groupName, quizGroup) in grouped) {
                var expanded by remember { mutableStateOf(false) }

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

@Composable
fun ShowCompletedAccordions(
    quizzes: List<Quiz>,
    allResponses: List<UserResponse>,
    userResponsesMap: Map<String, UserResponse>,
    selectedQuiz: Quiz?,
    onQuizSelected: (Quiz) -> Unit,
    onQuizClosed: () -> Unit,
    onSaveEdit: (List<String>) -> Unit
) {
    if (selectedQuiz != null) {
        // In completed => let's allow editing but still show distribution
        ShowQuizDetail(
            quiz = selectedQuiz,
            allResponses = allResponses,
            existingResponses = userResponsesMap.mapValues { it.value.selectedOptions },
            onBack = onQuizClosed,
            allowSelection = true,  // user can re-edit
            onSubmit = onSaveEdit
        )
        return
    }

    val completedIds = userResponsesMap.keys
    val completedQuizzes = quizzes.filter { it.id in completedIds }

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
                        val userResp = userResponsesMap[quiz.id]
                        val userSelected = userResp?.selectedOptions.orEmpty()

                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                // show detail with distribution + re-edit
                                onQuizSelected(quiz)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF6F00)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color.White
                            )
                        ) {
                            Column {
                                Text(
                                    quiz.question,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Your Pick: $userSelected",
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
 * A detail screen that **always** shows the distribution for each option (with %),
 * but if `allowSelection=true`, user can pick/unpick as well.
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
    val userAlready = existingResponses[quiz.id].orEmpty()
    var localPicks by remember { mutableStateOf<List<String>>(emptyList()) }

    // If read-only => show user picks
    LaunchedEffect(quiz.id) {
        if (!allowSelection) {
            localPicks = userAlready
        } else {
            // If you want the user to see their existing picks as a starting point (for editing)
            localPicks = userAlready
        }
    }

    // Build distribution
    val distMap = remember(quiz.id, allResponses) {
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
            counts.mapValues { (_, n) -> n.toFloat() / total.toFloat() * 100f }
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

        // For each option, always show distribution & highlight user picks
        quiz.options.forEach { opt ->
            val isPicked = localPicks.contains(opt)
            val distPct = distMap[opt] ?: 0f
            val displayPct = String.format("%.1f%%", distPct)

            // We'll unify the UI: we always show the % and color the user’s pick in orange
            // If allowSelection => tapping toggles
            val txtColor = if (isPicked) Color(0xFFFF6F00) else Color.White
            val bgColor = if (isPicked) Color.Black else Color(0xFF1A1A1A)

            if (allowSelection) {
                // The user can still pick/unpick, but we'll also show distribution in the button text
                Button(
                    onClick = {
                        localPicks = if (isPicked) localPicks - opt else localPicks + opt
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, Color(0xFFFF6F00), RoundedCornerShape(6.dp)),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = bgColor)
                ) {
                    // e.g. "Pantua (33.3%)"
                    Text("$opt  ($displayPct)", color = txtColor)
                }
            } else {
                // read-only
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
                    Text("$opt  ($displayPct)", color = txtColor)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Buttons row
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Back", color = Color.White)
            }

            if (allowSelection) {
                Button(
                    onClick = { onSubmit(localPicks) },
                    shape = RoundedCornerShape(6.dp),
                    enabled = localPicks.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text("Submit", color = Color.White)
                }
            }
        }
    }
}
