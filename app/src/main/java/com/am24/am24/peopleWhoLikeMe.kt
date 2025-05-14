package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch

@Composable
fun PeopleWhoLikeMeScreen(
    navController: NavController,
    currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
) {
    val blockedRef = FirebaseRefs.db
        .getReference("blocks/$currentUserId")

    val blockedIds = remember { mutableStateListOf<String>() }

    // Load blocked IDs
    LaunchedEffect(currentUserId) {
        blockedRef.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                blockedIds.clear()
                s.children.forEach { it.key?.let(blockedIds::add) }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    val likesReceivedRef = FirebaseRefs.db
        .getReference("likesReceived/$currentUserId")
    val usersRef = FirebaseRefs.db.getReference("users")

    // We also fetch the current user's matches so we can exclude them
    val matchesRef = FirebaseRefs.db
        .getReference("matches/$currentUserId")

    // Will hold the final list of profiles who liked me
    val likedUsers = remember { mutableStateListOf<Profile>() }

    // We'll track the user's matched IDs so we can skip them
    val myMatchIds = remember { mutableStateListOf<String>() }

    // (1) Get the current user's matched user IDs
    LaunchedEffect(currentUserId) {
        matchesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                myMatchIds.clear()
                snapshot.children.forEach { data ->
                    data.key?.let { myMatchIds.add(it) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // (2) Load the IDs of people who liked me, then fetch each of their profiles
    LaunchedEffect(currentUserId) {
        likesReceivedRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userIds = snapshot.children.mapNotNull { it.key }
                // Now fetch each user's full Profile
                userIds.forEach { userId ->
                    usersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val profile = userSnapshot.getValue(Profile::class.java)
                            if (profile != null) {
                                // We'll only add them if NOT matched with the current user
                                // i.e. if "myMatchIds" does not contain their userId.
                                // Also we can check if they have matched you in their "matches".
                                if (!myMatchIds.contains(profile.userId)
                                    && !profile.matches.contains(currentUserId)
                                    && !blockedIds.contains(profile.userId)
                                ) {
                                    likedUsers.add(profile)
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // For the scroll-to-top feature
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scaffold with a floating action button
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Scroll to index 0
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                },
                containerColor = Color(0xFFFF4500)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Scroll to Top",
                    tint = Color.White
                )
            }
        },
        containerColor = Color.Black
    ) { innerPadding ->
        // Actual list area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
        ) {
            if (likedUsers.isEmpty()) {
                // Show an empty state if no one liked user
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No one has liked you yet.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    state = listState
                ) {
                    items(likedUsers) { profile ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable {
                                    // (4) Navigate with userId as the query param
                                    navController.navigate(
                                        "dating_screen?initialQuery=${profile.userId}"
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Profile Picture
                                AsyncImage(
                                    model = profile.profilepicUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.size(50.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))

                                // (2) Show user's name, username, and numberOfUsersWhoSwiped
                                Column {
                                    Text(
                                        text = profile.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = profile.username,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    // Display how many likes that user has
                                    Text(
                                        text = "Likes: ${profile.numberOfUsersWhoSwiped.toInt()}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
