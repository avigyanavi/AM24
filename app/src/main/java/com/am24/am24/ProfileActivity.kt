@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            var currentTab by rememberSaveable { mutableStateOf(2) } // Default to Profile tab

            UnifiedScaffold(
                currentTab = currentTab,
                onTabChange = { currentTab = it },
                navController = navController,
                titleProvider = { tabIndex ->
                    when (tabIndex) {
                        0 -> "DMs"
                        1 -> "Feed"
                        2 -> "Profile"
                        3 -> "Dating"
                        4 -> "Settings"
                        else -> "AM24"
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileScreen(
    navController: NavHostController,
    currentTab: Int,
    onTabChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val profile = remember { mutableStateOf(Profile()) }
    val posts = remember { mutableStateListOf<Post>() }
    val showEchoes = remember { mutableStateOf(true) }
    var currentlyPlayingPostId by remember { mutableStateOf<String?>(null) }

    // Load profile and posts using Firebase
    val firebaseDatabase = FirebaseDatabase.getInstance()
    LaunchedEffect(userId) {
        launch(Dispatchers.IO) {
            // Fetch user profile
            firebaseDatabase.getReference("users").child(userId)
                .get().addOnSuccessListener {
                    profile.value = it.getValue(Profile::class.java) ?: Profile()
                }.addOnFailureListener { e ->
                    Log.e("Firebase", "Failed to fetch user profile: ${e.message}")
                }

            // Fetch user posts
            firebaseDatabase.getReference("posts").orderByChild("userId")
                .equalTo(userId).get().addOnSuccessListener { snapshot ->
                    snapshot.children.mapNotNullTo(posts) {
                        it.getValue(Post::class.java)
                    }
                }.addOnFailureListener { e ->
                    Log.e("Firebase", "Failed to fetch user posts: ${e.message}")
                }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(3.dp, getLevelBorderColor(profile.value.level))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))

                // User Information (Basic)
                UserInfoSectionBasic(profile = profile.value)

                Spacer(modifier = Modifier.height(16.dp))

                // Photos/Videos Grid
                PhotosGrid(
                    photos = profile.value.optionalPhotoUrls,
                    onPhotoClick = { /* Handle fullscreen photo click */ }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Button for Echoes and Metrics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { showEchoes.value = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showEchoes.value) Color(0xFF00bf63) else Color.Gray
                        )
                    ) {
                        Text
