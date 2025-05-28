// ----------------------------------
//  MainNavGraph.kt
// ----------------------------------
package com.am24.am24

import DatingViewModel
import EditPicAndVoiceBioScreen
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.am24.am24.ui.purchase.OneTimePurchaseScreen
import com.am24.am24.ui.purchase.PurchaseType
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await

// Initialize GeoFire instance globally
val geoFire = GeoFire(FirebaseRefs.db.getReference("geoFireLocations"))

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainNavGraph(
    navController: NavHostController,
    datingViewModel: DatingViewModel,
    modifier: Modifier = Modifier,
    postViewModel: PostViewModel,
    currentPrice  : String
) {
    // Re-initialize postViewModel
    val postViewModel: PostViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
    var userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Read in the current user's matches from Firebase
    val matchesSet = remember { mutableStateListOf<String>() }
    LaunchedEffect(userId) {
        val matchesRef = FirebaseRefs.db.getReference("matches").child(userId)
        matchesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                matchesSet.clear()
                for (child in snapshot.children) {
                    child.key?.let { matchesSet.add(it) }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error if needed
            }
        })
    }


    // Initialize `profileViewModel`
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )

    // Initialize LocationManager (safe inside Composable)
    val context = LocalContext.current
    val locationManager = remember { LocationManager(context) }

    NavHost(
        navController = navController,
        startDestination = "dating",
        modifier = modifier
    ) {
        composable("dms") {
            DMScreen(navController = navController)
        }
        composable("leaderboard") {
            LeaderboardScreen(navController)
        }
        composable("home") {
            HomeScreen(navController = navController, postViewModel = postViewModel)
        }
        composable("create_post") {
            CreatePostScreen(navController = navController, postViewModel = postViewModel)
        }
        composable("create_post/text") {
            TextPostComposable(navController = navController, postViewModel = postViewModel)
        }
        composable("create_post/voice") {
            VoicePostComposable(navController = navController, postViewModel = postViewModel)
        }
        composable("profile") {
            ProfileScreen(
                navController = navController,
                profileViewModel = profileViewModel,
                postViewModel = postViewModel
            )
        }
        composable("dating_screen?initialQuery={initialQuery}") { backStackEntry ->
            val initialQuery = backStackEntry.arguments?.getString("initialQuery") ?: ""
            DatingScreen(
                navController = navController,
                geoFire = geoFire,
                datingViewModel = datingViewModel,
                initialQuery = initialQuery
            )
        }
        composable("create_post/image") { ImagePostComposable(navController, postViewModel) }
        composable("create_post/video") { VideoPostComposable(navController, postViewModel) }
        composable("subscription") {
            SubscriptionScreen(navController)
        }
        composable("dating") {
            DatingScreen(
                navController = navController,
                geoFire = geoFire,
                datingViewModel = datingViewModel // Pass the ViewModel
            )
        }
        composable("editPicAndVoiceBio") {
            EditPicAndVoiceBioScreen(navController, profileViewModel)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        // new: compose a check-in feed screen, keyed by lat & lng
        composable(
            route = "checkinFeed/{placeId}",
            arguments = listOf(navArgument("placeId"){ type = NavType.StringType })
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments!!.getString("placeId")!!
            CheckInFeedScreen(
                placeId       = placeId,
                navController = navController
            )
        }
        composable("peopleWhoLikedMe") {
            PeopleWhoLikeMeScreen(navController = navController)
        }
        composable("govtIdVerification") {
            GovtIdVerificationScreen(
                navController     = navController,
                profileViewModel  = profileViewModel
            )
        }
        composable("notifications") {
            NotificationsScreen(navController = navController)
        }
        composable("chat/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId")
            if (otherUserId != null) {
                ChatScreen(navController, otherUserId)
            }
        }
        // 1) West Bengal top-level map
        composable("map") {
            // Pass references
            MapScreen(
                userId = userId,
                locationManager = locationManager,        // Or create it via DI
                geoFireDatabaseRef = FirebaseRefs.db
                    .getReference("geoFireLocations"),
                navController = navController, // NEW parameter
                onProfileMarkerClicked = { profileId ->
                    // Replace 'matchesSet' with your available list of matched user IDs.
                    if (matchesSet.contains(profileId)) {
                        navController.navigate("matchedUserProfile/$profileId")
                    } else {
                        navController.navigate("dating_screen?initialQuery=$profileId")
                    }
                },
                currentPrice = currentPrice
            )
        }

        composable(
            route = "groupChat/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            GroupChatScreen(navController = navController, groupId = groupId)
        }

        composable("buySwipes") { OneTimePurchaseScreen(
            type = PurchaseType.Swipes,
            onBack = { navController.popBackStack() }
        ) }
        composable("buyCompliments") { OneTimePurchaseScreen(
            type = PurchaseType.Compliments,
            onBack = { navController.popBackStack() }
        ) }
        composable("buyBoosts") { OneTimePurchaseScreen(
            type = PurchaseType.Boosts,
            onBack = { navController.popBackStack() }
        ) }
        composable("saved_posts") {
            SavedPostsScreen(
                navController = navController,
                postViewModel = postViewModel
            )
        }

        composable(
            route = "matchedUserProfile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            var matchedProfile by remember { mutableStateOf<Profile?>(null) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(userId) {
                profileViewModel.fetchCurrentUserProfile() // Ensure current profile is fetched
                try {
                    println("Fetching profile for userId: $userId")
                    val snapshot = FirebaseRefs.db
                        .getReference("users")
                        .child(userId)
                        .get()
                        .await()
                    if (snapshot.exists()) {
                        val profile = snapshot.getValue(Profile::class.java)
                        println("Profile fetched: $profile")
                        matchedProfile = profile
                        println("matchedProfile updated to: $matchedProfile")
                        if (profile == null) {
                            errorMessage = "Profile data could not be parsed"
                        }
                    } else {
                        errorMessage = "No profile found for userId: $userId"
                    }
                } catch (e: Exception) {
                    errorMessage = "Error fetching profile: ${e.message}"
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (matchedProfile != null) {
                    println("Rendering MatchedUserProfileScreen for: ${matchedProfile?.userId}")
                    MatchedUserProfileScreen(
                        profile = matchedProfile!!,
                        geoFire = geoFire,
                        postViewModel = postViewModel,
                        profileViewModel = profileViewModel,
                        navController = navController
                    )
                } else if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red, modifier = Modifier.padding(16.dp))
                } else {
                    Text("Loading profile...", color = Color.White)
                }
            }
        }
        composable(
            route = "previewUserProfile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val targetId   = backStackEntry.arguments?.getString("userId") ?: return@composable
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@composable

            PreviewUserProfileScreen(
                navController    = navController,
                targetUserId     = targetId,
                currentUserId    = currentUid,
                geoFire          = geoFire,          // ← add this line
                profileViewModel = profileViewModel,
                postViewModel    = postViewModel
            )
        }
    }
}