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
fun CityGroupChatScreen(
    navController: NavHostController,
    cityName: String,
    profileViewModel: ProfileViewModel
) {
    // We'll store the profiles that belong to this city
    val (cityProfiles, setCityProfiles) = remember { mutableStateOf<List<Profile>>(emptyList()) }

    // Fetch city profiles once on entry
    LaunchedEffect(cityName) {
        profileViewModel.fetchProfilesByCity(cityName) { result ->
            setCityProfiles(result)
        }
    }

    // Simple UI to show the profiles
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Group Chat: $cityName") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (cityProfiles.isEmpty()) {
                Text(
                    text = "No profiles found for $cityName",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(cityProfiles) { profile ->
                        ProfileRow(profile)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileRow(profile: Profile) {
    // A simple row showing the profile name
    // You could expand this with images, chat UI, etc.
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = profile.name.ifBlank { "Unnamed" },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(8.dp)
        )
    }
}
