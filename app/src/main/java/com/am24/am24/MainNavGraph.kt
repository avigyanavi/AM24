// MainNavGraph.kt
package com.am24.am24

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.Modifier

@Composable
fun MainNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier // The padding is already applied via the modifier
    ) {
        composable("dms") { /* DMs Screen */ }
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("profile") {
            ProfileScreen(navController = navController)
        }
        composable("dating") {
            DatingScreen(navController = navController)
        }
        composable("dating/{startUserId}") { backStackEntry ->
            val startUserId = backStackEntry.arguments?.getString("startUserId")
            DatingScreen(navController = navController, startUserId = startUserId)
        }
        composable("settings") { /* Settings Screen */ }
        // Additional destinations can be added here
        composable("peopleWhoLikeMe") {
            PeopleWhoLikeMeScreen(navController = navController)
        }
        composable("notifications") {
            NotificationsScreen(navController = navController)
        }
    }
}
