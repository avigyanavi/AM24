// DMScreen.kt

package com.am24.am24

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import coil.compose.AsyncImage

@Composable
fun DMScreen(navController: NavController) {
    DMScreenContent(navController = navController)
}

@Composable
fun DMScreenContent(navController: NavController) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val database = FirebaseDatabase.getInstance()
    val matchesRef = database.getReference("matches/$currentUserId")
    val usersRef = database.getReference("users")

    val matchedUsers = remember { mutableStateListOf<Profile>() }

    // Fetch matches and corresponding profiles
    LaunchedEffect(currentUserId) {
        // Listen to matches node for real-time updates
        matchesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newMatchedUsers = mutableListOf<Profile>()
                val userIdsToFetch = mutableListOf<String>()

                for (matchSnapshot in snapshot.children) {
                    val matchedUserId = matchSnapshot.key
                    if (matchedUserId != null) {
                        userIdsToFetch.add(matchedUserId)
                    }
                }

                if (userIdsToFetch.isNotEmpty()) {
                    usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            for (user in userSnapshot.children) {
                                val profile = user.getValue(Profile::class.java)
                                if (profile != null && userIdsToFetch.contains(profile.userId)) {
                                    newMatchedUsers.add(profile)
                                }
                            }
                            // Update the matchedUsers list
                            matchedUsers.clear()
                            matchedUsers.addAll(newMatchedUsers)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("DMScreen", "DatabaseError: ${error.message}")
                            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    matchedUsers.clear()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DMScreen", "DatabaseError: ${error.message}")
                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    if (matchedUsers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No matches yet",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(matchedUsers) { profile ->
                MatchedUserCard(profile = profile, navController = navController)
            }
        }
    }
}

@Composable
fun MatchedUserCard(profile: Profile, navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Navigate to ChatScreen with matched user's ID
                navController.navigate("chat/${profile.userId}")
            },
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            // Profile picture
            if (profile.profilepicUrl != null && profile.profilepicUrl.isNotBlank()) {
                AsyncImage(
                    model = profile.profilepicUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default Profile",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.username,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Age: ${calculateAge(profile.dob)}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = "Location: ${profile.city}, ${profile.country}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            // Chat Icon
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "Chat",
                tint = Color(0xFF00bf63),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

