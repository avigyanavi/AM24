package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@Composable
fun PeopleWhoLikeMeScreen(
    navController: NavController,
    currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
) {
    val likesReceivedRef = FirebaseDatabase.getInstance().getReference("likesReceived/$currentUserId")
    val usersRef = FirebaseDatabase.getInstance().getReference("users")
    val likedUsers = remember { mutableStateListOf<Profile>() }

    LaunchedEffect(Unit) {
        likesReceivedRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userIds = snapshot.children.mapNotNull { it.key }
                userIds.forEach { userId ->
                    usersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val profile = userSnapshot.getValue(Profile::class.java)
                            if (profile != null) {
                                likedUsers.add(profile)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Handle error
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    // Display the list of liked users
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (likedUsers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No one has liked you yet.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            items(likedUsers) { profile ->
                // Display each profile in a card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            val username = profile.username
                            navController.navigate("dating_screen?initialQuery=$username")
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
                        // User Details
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
                        }
                    }
                }
            }
        }
    }
}
