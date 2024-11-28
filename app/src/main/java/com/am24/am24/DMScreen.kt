// DMScreen.kt

package com.am24.am24

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@Composable
fun DMScreen(navController: NavController) {
    DMScreenContent(navController = navController)
}

@Composable
fun DMScreenContent(navController: NavController) {
    var selectedTab by remember { mutableStateOf("Matches") } // "Matches", "Friends", or "ListView"
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val context = LocalContext.current

    val database = FirebaseDatabase.getInstance()
    val matchesRef = database.getReference("matches/$currentUserId")
    val friendsRef = database.getReference("friends/$currentUserId")
    val usersRef = database.getReference("users")
    val ratingsRef = database.getReference("ratings/$currentUserId")

    val matchedUsers = remember { mutableStateListOf<Profile>() }

    // Fetch matched users and friends
    LaunchedEffect(currentUserId) {
        fetchUsersFromNode(matchesRef, usersRef, matchedUsers, context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top toggle buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { selectedTab = "Matches" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "Matches") Color(0xFFFF4500) else Color.Gray
                )
            ) {
                Text(text = "Matches", color = Color.White)
            }
            Button(
                onClick = { selectedTab = "Friends" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "Friends") Color(0xFFFF4500) else Color.Gray
                )
            ) {
                Text(text = "Friends", color = Color.White)
            }
            Button(
                onClick = { selectedTab = "ListView" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "ListView") Color(0xFFFF4500) else Color.Gray
                )
            ) {
                Text(text = "List View", color = Color.White)
            }
        }

        // Show content based on selectedTab
        when (selectedTab) {
            "Matches" -> UserListSection(
                users = matchedUsers,
                navController = navController,
                emptyText = "No matches yet",
                ratingsRef = ratingsRef,
                usersRef = usersRef
            )
            "ListView" -> RatingListView(
                matchedUsers = matchedUsers,
                ratingsRef = ratingsRef,
                usersRef = usersRef
            )
        }
    }
}


@Composable
fun UserListSection(
    users: List<Profile>,
    navController: NavController,
    emptyText: String,
    ratingsRef: DatabaseReference,
    usersRef: DatabaseReference // Add usersRef here
) {
    if (users.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyText,
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
            items(users) { profile ->
                UserCard(profile = profile, navController = navController, ratingsRef = ratingsRef, usersRef = usersRef)
            }
        }
    }
}

@Composable
fun RatingListView(
    matchedUsers: List<Profile>,
    ratingsRef: DatabaseReference,
    usersRef: DatabaseReference
) {
    // Merge lists, avoid duplicates, and sort by am24Ranking
    val combinedUsers = (matchedUsers)
        .distinctBy { it.userId } // Remove duplicates
        .sortedWith(compareByDescending<Profile> { it.am24Ranking } // Sort by am24Ranking (higher is better)
            .thenByDescending { it.averageRating } // Tie-break by averageRating
            .thenByDescending { it.vibepoints }) // Further tie-break by vibepoints

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(combinedUsers) { index, profile ->
            // Determine connection type
            val connectionType = when {
                matchedUsers.any { it.userId == profile.userId } -> "Match"
                else -> "Exception"
            }

            // Render user card with ranking
            UserCardWithRanking(
                profile = profile,
                navController = null,
                ratingsRef = ratingsRef,
                usersRef = usersRef,
                ranking = index + 1, // Global ranking starts from 1
                connectionType = connectionType
            )
        }
    }
}

@Composable
fun UserCardWithRanking(
    profile: Profile,
    navController: NavController?,
    ratingsRef: DatabaseReference,
    usersRef: DatabaseReference,
    ranking: Int,
    connectionType: String
) {
    var yourRating by rememberSaveable(profile.userId) { mutableStateOf(-1.0) }
    var averageRating by remember { mutableStateOf(profile.averageRating) }
    val context = LocalContext.current

    // Fetch ratings
    LaunchedEffect(profile.userId) {
        if (yourRating == -1.0) {
            fetchUserRating(ratingsRef, profile.userId) { rating ->
                yourRating = rating
            }
        }
        fetchAverageRating(ratingsRef, profile.userId) { avg ->
            averageRating = avg
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                navController?.navigate("chat/${profile.userId}")
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
            if (profile.profilepicUrl?.isNotBlank() == true) {
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

            // User details and ratings
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#$ranking ${profile.username} ($connectionType)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Average Rating: ${String.format("%.1f", averageRating)}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                RatingBar(rating = averageRating)
                Text(
                    text = "Your Rating: ${if (yourRating >= 0) String.format("%.1f", yourRating) else "N/A"}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                RatingBar(rating = yourRating)
            }
        }
    }
}


@Composable
fun UserCard(
    profile: Profile,
    navController: NavController?, // Make navController nullable
    ratingsRef: DatabaseReference,
    usersRef: DatabaseReference
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var yourRating by rememberSaveable(profile.userId) { mutableStateOf(-1.0) } // Use -1.0 as an unset marker
    var averageRating by remember { mutableStateOf(profile.averageRating) }
    val context = LocalContext.current

    // Fetch your rating and average rating only if yourRating is unset
    LaunchedEffect(profile.userId) {
        if (yourRating == -1.0) { // Fetch only if not already set
            fetchUserRating(ratingsRef, profile.userId) { rating ->
                yourRating = rating
            }
        }
        fetchAverageRating(ratingsRef, profile.userId) { avg ->
            averageRating = avg
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                navController?.navigate("chat/${profile.userId}") // Only navigate if navController is not null
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
            if (profile.profilepicUrl?.isNotBlank() == true) {
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

            // User details and rating
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.username,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Average Rating: ${String.format("%.1f", averageRating)}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = "Your Rating: ${if (yourRating >= 0) String.format("%.1f", yourRating) else "N/A"}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                // Rating Slider
                Slider(
                    value = if (yourRating >= 0) yourRating.toFloat() else 0f, // Default slider position
                    onValueChange = { yourRating = it.toDouble() },
                    onValueChangeFinished = {
                        if (yourRating >= 0) {
                            updateUserRating(ratingsRef, usersRef, profile.userId, yourRating, context)
                        }
                    },
                    valueRange = 0f..5f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF4500),
                        activeTrackColor = Color(0xFFFF4500)
                    )
                )
            }
        }
    }
}


private fun fetchAverageRating(
    ratingsRef: DatabaseReference,
    userId: String,
    onAverageFetched: (Double) -> Unit
) {
    ratingsRef.child(userId).child("averageRating").get().addOnSuccessListener { snapshot ->
        val average = snapshot.getValue(Double::class.java) ?: 0.0
        onAverageFetched(average)
    }
}


private fun fetchUserRating(
    ratingsRef: DatabaseReference,
    userId: String,
    onRatingFetched: (Double) -> Unit
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    ratingsRef.child(userId).child("ratings").child(currentUserId).get().addOnSuccessListener { snapshot ->
        val yourRating = snapshot.getValue(Double::class.java) ?: 0.0
        onRatingFetched(yourRating)
    }.addOnFailureListener {
        onRatingFetched(0.0) // Default to 0.0 on failure
    }
}


private fun updateUserRating(
    ratingsRef: DatabaseReference,
    usersRef: DatabaseReference,
    userId: String,
    rating: Double,
    context: android.content.Context
) {
    val userRatingRef = ratingsRef.child(userId)
    userRatingRef.get().addOnSuccessListener { snapshot ->
        val ratingsMap = if (snapshot.exists() && snapshot.child("ratings").value is Map<*, *>) {
            snapshot.child("ratings").getValue(object : GenericTypeIndicator<MutableMap<String, Double>>() {})
                ?: mutableMapOf()
        } else {
            mutableMapOf()
        }

        // Get the current user's ID
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener

        // Update the current user's rating
        ratingsMap[currentUserId] = rating

        // Recalculate the average rating
        val averageRating = if (ratingsMap.isNotEmpty()) {
            ratingsMap.values.average()
        } else 0.0

        // Update the ratings map and average rating in the `ratings` node
        val updates = mapOf(
            "ratings" to ratingsMap,
            "averageRating" to averageRating
        )
        userRatingRef.updateChildren(updates).addOnSuccessListener {
            // Update the average rating in the user's profile
            usersRef.child(userId).child("averageRating").setValue(averageRating).addOnSuccessListener {
                Toast.makeText(context, "Rating updated!", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(context, "Failed to update average rating in profile.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to update ratings.", Toast.LENGTH_SHORT).show()
        }
    }.addOnFailureListener {
        Toast.makeText(context, "Failed to fetch current ratings.", Toast.LENGTH_SHORT).show()
    }
}

private fun fetchUsersFromNode(
    ref: DatabaseReference,
    usersRef: DatabaseReference,
    usersList: MutableList<Profile>,
    context: android.content.Context
) {
    ref.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val newUsers = mutableListOf<Profile>()
            val userIdsToFetch = mutableListOf<String>()

            for (userSnapshot in snapshot.children) {
                val userId = userSnapshot.key
                if (userId != null) {
                    userIdsToFetch.add(userId)
                }
            }

            if (userIdsToFetch.isNotEmpty()) {
                usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(userSnapshot: DataSnapshot) {
                        for (user in userSnapshot.children) {
                            val profile = user.getValue(Profile::class.java)
                            if (profile != null && userIdsToFetch.contains(profile.userId)) {
                                newUsers.add(profile)
                            }
                        }
                        // Update the users list
                        usersList.clear()
                        usersList.addAll(newUsers)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("DMScreen", "DatabaseError: ${error.message}")
                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                usersList.clear()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("DMScreen", "DatabaseError: ${error.message}")
            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    })
}
