@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun MatchedUserProfile(
    navController: NavController,
    userId: String,
    profileViewModel: ProfileViewModel,
    modifier: Modifier = Modifier
) {
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDetailedView by remember { mutableStateOf(false) }

    // Fetch user profile from ProfileViewModel
    LaunchedEffect(userId) {
        profileViewModel.fetchUserProfile(
            userId = userId,
            onSuccess = { profile ->
                userProfile = profile
                isLoading = false
            },
            onFailure = {
                Log.e("MatchedUserProfile", "Failed to load profile: $it")
                isLoading = false
            }
        )
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFF6F00))
        }
    } else if (userProfile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Failed to load profile.",
                color = Color.White,
                fontSize = 18.sp
            )
        }
    } else {
        if (isDetailedView) {
            MatchedUserDetailsTabs(
                profile = userProfile!!,
                onCloseClick = { isDetailedView = false }
            )
        } else {
            MatchedUserContent(
                profile = userProfile!!,
                onBackClick = { navController.popBackStack() },
                onInfoClick = { isDetailedView = true }
            )
        }
    }
}

@Composable
fun MatchedUserContent(
    profile: Profile,
    onBackClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    var currentPhotoIndex by remember(profile) { mutableStateOf(0) }
    val photoUrls = listOfNotNull(profile.profilepicUrl) + profile.optionalPhotoUrls

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = photoUrls[currentPhotoIndex],
            contentDescription = "Profile Photo",
            placeholder = painterResource(R.drawable.local_placeholder),
            error = painterResource(R.drawable.local_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(7 / 10f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val tapX = offset.x
                            val photoCount = photoUrls.size
                            if (photoCount > 1) {
                                currentPhotoIndex = if (tapX > size.width / 2) {
                                    (currentPhotoIndex + 1) % photoCount
                                } else {
                                    (currentPhotoIndex - 1 + photoCount) % photoCount
                                }
                            }
                        }
                    )
                },
            contentScale = ContentScale.Crop
        )

        // Back and Info Icons
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFFFFFFFF)
            )
        }

        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "View More Info",
                tint = Color.White
            )
        }

        // Profile Details Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "${profile.name}, ${calculateAge(profile.dob)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.White
                )
                if (profile.hometown.isNotEmpty()) {
                    Text(
                        text = "From ${profile.hometown}",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                RatingBar(
                    rating = profile.averageRating,
                    ratingCount = profile.numberOfRatings
                )
                Text(
                    text = "Vibe Score: ${profile.vibepoints}",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }

        // Dots Indicator for Photos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            photoUrls.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(horizontal = 2.dp)
                        .clip(CircleShape)
                        .background(if (index == currentPhotoIndex) Color.White else Color.Gray)
                )
            }
        }
    }
}

@Composable
fun MatchedUserDetailsTabs(profile: Profile, onCloseClick: () -> Unit) {
    val tabTitles = listOf("Profile", "Posts", "Comparison")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Tab Row with Close Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.weight(1f)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, color = Color.White) }
                    )
                }
            }

            // Close Button
            IconButton(
                onClick = { onCloseClick() },
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFFFF6F00)
                )
            }
        }

        // Tab Content
        when (selectedTabIndex) {
//            0 -> ProfileDetails(profile)
            1 -> ProfilePosts(profile)
            2 -> ComparisonSection(profile)
        }
    }
}

@Composable
fun ComparisonSection(profile: Profile) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Comparison for ${profile.name}",
            color = Color.White,
            fontSize = 16.sp
        )
        // Dynamically fetch and display comparison data
    }
}
