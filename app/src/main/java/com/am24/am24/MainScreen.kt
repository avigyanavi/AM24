// MainScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainScreen(navController: NavHostController, onLogout: () -> Unit) {
    val items = listOf(
        BottomNavItem("DMs", Icons.Default.MailOutline, "dms"),
        BottomNavItem("Feed", Icons.Default.Home, "home"),
        BottomNavItem("Stories", Icons.Default.ManageHistory, "explore"),
        BottomNavItem("Profile", Icons.Default.Person, "profile"),
        BottomNavItem("Dating", Icons.Default.Favorite, "dating"),
        BottomNavItem("Settings", Icons.Default.Settings, "settings")
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        topBar = {
            TopNavBar(navController = navController, onLogout = onLogout)
        },
        bottomBar = {
            BottomNavigationBar(navController = navController, items = items)
        }
    ) { innerPadding ->
        // Pass the innerPadding to MainNavGraph via the modifier
        MainNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun TopNavBar(navController: NavController, onLogout: () -> Unit) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isPeopleWhoLikeMeSelected = currentDestination?.route == "peopleWhoLikeMe"
    val isNotificationsSelected = currentDestination?.route == "notifications"
    val isSavedPostsSelected = currentDestination?.route == "savedPosts"

    val title = when (currentDestination?.route) {
        "dms" -> "DMs"
        "home" -> "Feed"
        "explore" -> "Stories"
        "savedPosts" -> "Saved Posts"
        "profile" -> "Profile"
        "dating" -> "Dating"
        "settings" -> "Settings"
        "peopleWhoLikeMe" -> "People Who Like Me"
        "notifications" -> "Notifications"
        else -> "KupidXApp"
    }

    TopAppBar(
        title = { Text(text = title, color = Color.White) },
        navigationIcon = {
            IconButton(onClick = {
                navController.navigate("peopleWhoLikeMe")
            }) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "People Who Like Me",
                    tint = if (isPeopleWhoLikeMeSelected) Color(0xFF00bf63) else Color.White
                )
            }
        },
        actions = {
            IconButton(onClick = {
                navController.navigate("notifications")
            }) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = if (isNotificationsSelected) Color(0xFF00bf63) else Color.White
                )
            }
            IconButton(onClick = {
                navController.navigate("savedPosts") // New SavedPosts navigation
            }) {
                Icon(
                    imageVector = Icons.Default.Bookmark, // Use a suitable icon
                    contentDescription = "Saved Posts",
                    tint = if (isSavedPostsSelected) Color(0xFF00bf63) else Color.White
                )
            }
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
    )
}

data class BottomNavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun BottomNavigationBar(navController: NavController, items: List<BottomNavItem>) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(containerColor = Color.Black) {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        launchSingleTop = true
                        restoreState = true
                        // Remove popUpTo to allow normal back stack behavior
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) Color(0xFF00bf63) else Color.Gray
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        color = if (selected) Color(0xFF00bf63) else Color.Gray
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF00bf63),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFF00bf63),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.DarkGray
                )
            )
        }
    }
}