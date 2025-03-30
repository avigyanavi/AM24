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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.am24.am24.profiles.GenericProfileScreen
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

// Initialize GeoFire instance globally
val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geoFireLocations"))

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    postViewModel: PostViewModel
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
        val matchesRef = FirebaseDatabase.getInstance().getReference("matches").child(userId)
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
        composable("home") {
            HomeScreen(navController = navController, postViewModel = postViewModel)
        }
        composable("editProfile") {
            EditProfileScreen(navController = navController)
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
                initialQuery = initialQuery
            )
        }
        composable("dating") {
            DatingScreen(navController = navController, geoFire = geoFire)
        }
        composable("editPicAndVoiceBio") {
            EditPicAndVoiceBioScreen(navController, profileViewModel)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("peopleWhoLikedMe") {
            PeopleWhoLikeMeScreen(navController = navController)
        }

        composable(
            route = "aiProfile/{aiId}?scrollToMemoryLogs={scrollToMemoryLogs}",
            arguments = listOf(
                navArgument("aiId") { type = NavType.StringType },
                navArgument("scrollToMemoryLogs") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val aiId = backStackEntry.arguments?.getString("aiId") ?: return@composable
            val scrollToMemoryLogs = backStackEntry.arguments?.getBoolean("scrollToMemoryLogs") ?: false
            val chatAIViewModel: ChatAIViewModel = viewModel()
            val ai = when (aiId) {
                "zaraAi" -> AI.ZARA
                "kabirAi" -> AI.KABIR
                else -> return@composable
            }
            val avatarRes = when (ai) {
                AI.ZARA -> R.drawable.zara_avatar
                AI.KABIR -> R.drawable.kabir_avatar
                else -> R.drawable.zara_avatar // Fallback
            }
            GenericProfileScreen(
                title = ai.name,
                modelingState = chatAIViewModel.getModelingState(aiId),
                avatarRes = avatarRes,
                onNavigateBack = { navController.popBackStack() },
                ai = ai,
                userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                messageCount = chatAIViewModel.getMessageCount(aiId),
                scrollToMemoryLogs = scrollToMemoryLogs
            )
        }

        // ----- The AI route for KupidXChatScreen -----
        composable("aiProfile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            val chatAIViewModel: ChatAIViewModel = viewModel()
            val modelingState = chatAIViewModel.getModelingState(userId)
            val aiEnum = when (userId) {
                "zaraAi" -> AI.ZARA
                "kabirAi" -> AI.KABIR
                else -> return@composable
            }
            val avatarRes = when (aiEnum) {
                AI.ZARA -> R.drawable.zara_avatar
                AI.KABIR -> R.drawable.kabir_avatar
                else -> R.drawable.zara_avatar // Fallback
            }
            GenericProfileScreen(
                title = aiEnum.name,
                modelingState = modelingState,
                avatarRes = avatarRes,
                onNavigateBack = { navController.popBackStack() },
                ai = aiEnum,
                userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                messageCount = chatAIViewModel.getMessageCount(userId)
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
                geoFireDatabaseRef = FirebaseDatabase.getInstance().getReference("geoFireLocations"),
                navController = navController, // NEW parameter
                onProfileMarkerClicked = { profileId ->
                    // Replace 'matchesSet' with your available list of matched user IDs.
                    if (matchesSet.contains(profileId)) {
                        navController.navigate("matchedUserProfile/$profileId")
                    } else {
                        navController.navigate("dating_screen?initialQuery=$profileId")
                    }
                }
            )
        }

        // 2) A simple menu: city group chat OR neighborhood map
        composable(
            route = "cityMenu/{cityName}",
            arguments = listOf(navArgument("cityName") { type = NavType.StringType })
        ) { backStackEntry ->
            val cityName = backStackEntry.arguments?.getString("cityName") ?: return@composable
            CityMenuScreen(
                navController = navController,
                cityName = cityName
            )
        }

        // 3) City-level group chat
        composable(
            route = "cityGroupChat/{cityName}",
            arguments = listOf(navArgument("cityName") { type = NavType.StringType })
        ) { backStackEntry ->
            val cityName = backStackEntry.arguments?.getString("cityName") ?: return@composable
            CityGroupChatScreen(
                navController = navController,
                cityName = cityName,
                profileViewModel = profileViewModel
            )
        }

        // 4) City-level map for neighborhoods
        composable(
            route = "cityMap/{cityName}",
            arguments = listOf(navArgument("cityName") { type = NavType.StringType })
        ) { backStackEntry ->
            val cityName = backStackEntry.arguments?.getString("cityName") ?: return@composable
            CityNeighborhoodMapScreen(
                userId = userId,
                cityName = cityName,
                onNeighborhoodClicked = { hoodName ->
                    navController.navigate("neighborhoodGroupChat/$hoodName")
                }
            )
        }

        // 5) Neighborhood-based group chat
        composable(
            route = "neighborhoodGroupChat/{hoodName}",
            arguments = listOf(navArgument("hoodName") { type = NavType.StringType })
        ) { backStackEntry ->
            val hoodName = backStackEntry.arguments?.getString("hoodName") ?: return@composable
            NeighborhoodGroupChatScreen(
                navController = navController,
                neighborhoodName = hoodName,
                profileViewModel = profileViewModel
            )
        }

        composable("matchedUserProfile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            MatchedUserProfile(
                navController = navController,
                userId = userId,
                profileViewModel = profileViewModel
            )
        }
    }
}
