// DMScreen.kt

package com.am24.am24

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@Composable
fun DMScreen(navController: NavController) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val matches = remember { mutableStateListOf<Profile>() }

    LaunchedEffect(Unit) {
        val database = FirebaseDatabase.getInstance()
        val matchesRef = database.getReference("matches/$currentUserId")
        val usersRef = database.getReference("users")

        matchesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val matchedUserIds = snapshot.children.map { it.key }.filterNotNull()
                usersRef.get().addOnSuccessListener { usersSnapshot ->
                    matches.clear()
                    for (userSnapshot in usersSnapshot.children) {
                        val profile = userSnapshot.getValue(Profile::class.java)
                        if (profile != null && matchedUserIds.contains(profile.userId)) {
                            matches.add(profile)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Text(
            text = "Matches",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(16.dp)
        )
        if (matches.isEmpty()) {
            Text(
                text = "You have no matches yet.",
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(matches) { profile ->
                    MatchListItem(profile = profile, navController = navController)
                }
            }
        }
    }
}

@Composable
fun MatchListItem(profile: Profile, navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Navigate to Chat Screen
                navController.navigate("chat/${profile.userId}")
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = profile.profilepicUrl,
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = profile.username,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            // Optionally, display last message preview
        }
    }
}
