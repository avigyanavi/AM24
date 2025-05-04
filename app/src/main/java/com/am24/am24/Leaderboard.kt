// LeaderboardScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage

@Composable
fun LeaderboardScreen(
    navController: NavHostController,
    viewModel: LeaderboardViewModel = viewModel()
) {
    val leaderboard by viewModel.leaderboard.collectAsState()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Leaderboard", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)                     // ← apply it here
        ) {
// filter out AI users here:
            items(leaderboard.filterNot { profile ->
                profile.userId.endsWith("Ai")
            }) { profile ->
                LeaderboardRow(profile)
            }
        }
    }
}

@Composable
fun LeaderboardRow(profile: Profile) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "${profile.am24Ranking}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width( thirtyTwoDp )
        )

        Spacer(Modifier.width(8.dp))

        AsyncImage(
            model = profile.profilepicUrl ?: "",   // use your real URL field
            contentDescription = profile.name,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

// handy constant for consistent rank‐column width
private val thirtyTwoDp = 32.dp
