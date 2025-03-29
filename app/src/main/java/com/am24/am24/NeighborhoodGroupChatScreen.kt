package com.am24.am24

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeighborhoodGroupChatScreen(
    navController: NavHostController,
    neighborhoodName: String,
    profileViewModel: ProfileViewModel
) {
    val (profiles, setProfiles) = remember { mutableStateOf(emptyList<Profile>()) }

    // Filter by hometown
    LaunchedEffect(neighborhoodName) {
        profileViewModel.fetchProfilesByHometown(neighborhoodName) { result ->
            setProfiles(result)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neighborhood Chat: $neighborhoodName") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (profiles.isEmpty()) {
                Text(
                    text = "No profiles found for $neighborhoodName",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(profiles) { profile ->
                        ProfileRow(profile)
                    }
                }
            }
        }
    }
}
