// MainNavGraph.kt
package com.am24.am24

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

// Initialize GeoFire instance globally in your MainNavGraph
val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geoFireLocations"))

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    val postViewModel: PostViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )

    // Get the current user's ID and username
    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid
    val currentUserName = currentUser?.displayName ?: "defaultName" // Replace with actual username retrieval logic if needed

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier // The padding is already applied via the modifier
    ) {
        composable("dms") {
            DMScreen(navController = navController)
        }
        composable("home") {
            HomeScreen(navController = navController)
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
            ProfileScreen(navController = navController)
        }
        composable("profile/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId")
            if (otherUserId != null && currentUserId != null) {
                OtherUserProfileScreen(
                    navController = navController,
                    otherUserId = otherUserId,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    geoFire = geoFire // Pass the geoFire instance here
                )
            }
        }
        composable("dating_screen?initialQuery={initialQuery}") { backStackEntry ->
            val initialQuery = backStackEntry.arguments?.getString("initialQuery")
            DatingScreen(
                navController = navController,
                geoFire = geoFire
            )
        }
        composable("dating") {
            // Navigate to DatingScreen without startUserId
            DatingScreen(navController = navController, geoFire = geoFire)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("peopleWhoLikedMe") {
            PeopleWhoLikeMeScreen(navController = navController)
        }
        composable("notifications") {
            NotificationsScreen(navController = navController)
        }
        composable("filters") {
            FiltersScreen(postViewModel = postViewModel)
        }
        composable("chat/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId")
            if (otherUserId != null) {
                ChatScreen(navController, otherUserId)
            }
        }
    }
}
