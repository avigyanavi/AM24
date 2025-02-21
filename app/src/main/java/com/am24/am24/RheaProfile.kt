package com.am24.am24.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.am24.am24.ModelingState

@Composable
fun RheaProfileScreen(
    modelingState: ModelingState,
    memoryLog: List<String>,
    onNavigateBack: () -> Unit
) {
    // Full-screen scaffold with a top bar
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rhea's Profile", color = Color.White) },
                backgroundColor = Color.Black,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        backgroundColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("Age: ${modelingState.age}", color = Color.White)
            Text("Mood: ${modelingState.mood}", color = Color.White)
            Text("Career Progress: ${modelingState.careerProgress}", color = Color.White)
            Text("External Attention: ${modelingState.externalAttention}", color = Color.White)
            Text("Focus on User: ${modelingState.focusOnUser}", color = Color.White)
            Text("Jealousy Level: ${modelingState.jealousyLevel}", color = Color.White)
            Text("Maturity: ${modelingState.maturity}", color = Color.White)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Recent Memories:",
                color = Color.White,
                style = MaterialTheme.typography.h6
            )
            LazyColumn(modifier = Modifier.height(120.dp)) {
                items(memoryLog) { memory ->
                    Text("• $memory", color = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = """
                    Mood and focus evolve based on your chats.
                    More engagement leads to positive growth, while ignorance can cause emotional shifts.
                """.trimIndent(),
                color = Color.Gray,
                style = MaterialTheme.typography.body2
            )
        }
    }
}
