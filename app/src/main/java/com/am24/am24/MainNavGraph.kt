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
import com.google.firebase.database.FirebaseDatabase

// Assuming you're initializing GeoFire in your MainNavGraph or somewhere globally
val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geoFireLocations"))

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    val postViewModel: PostViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )

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
        composable("create_post") {
            CreatePostScreen(navController = navController, postViewModel = postViewModel)
        }
        composable("create_post/text") {
            TextPostComposable(navController = navController, postViewModel = postViewModel)
        }
        composable("create_post/photo") {
            PhotoPostComposable(navController = navController, postViewModel = postViewModel)
        }
        composable("create_post/video") {
            VideoPostComposable(navController = navController, postViewModel = postViewModel)
        }
        composable("explore") { // New Explore route
            ExploreScreen(navController = navController, geoFire = geoFire)
        }
        composable("profile") {
            ProfileScreen(navController = navController)
        }
        composable("profile/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId")
            ProfileScreen(navController = navController, otherUserId = otherUserId)
        }
        composable("dating") {
            // Navigate to DatingScreen without startUserId
            DatingScreen(navController = navController, geoFire = geoFire)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("peopleWhoLikeMe") {
            PeopleWhoLikeMeScreen(navController = navController)
        }
        composable("notifications") {
            NotificationsScreen(navController = navController)
        }
        composable("savedPosts") { // New SavedPosts route
            SavedPostsScreen(navController = navController)
        }
        composable("chat/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId")
            if (otherUserId != null) {
                ChatScreen(navController, otherUserId)
            }
        }
    }
}
