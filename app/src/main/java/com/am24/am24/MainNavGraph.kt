// MainNavGraph.kt
package com.am24.am24

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.Modifier
import com.firebase.geofire.GeoFire
import com.google.firebase.database.FirebaseDatabase

// Assuming you're initializing GeoFire in your MainNavGraph or somewhere globally
val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("geoFireLocations"))

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
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
