// NavigationUtils.kt

package com.am24.am24

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedScaffold(
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    navController: NavHostController,
    titleProvider: (Int) -> String
) {
    Scaffold(
        topBar = {
            TopNavBar(
                title = titleProvider(currentTab),
                onProfileClick = {
                    navController.navigate("peopleWhoLikeMe") // Navigate to PeopleWhoLikeMe screen
                },
                onNotificationClick = {
                    navController.navigate("notifications")
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedIndex = currentTab,
                onItemSelected = { index ->
                    onTabChange(index)
                    when (index) {
                        0 -> navController.navigate("dms")
                        1 -> navController.navigate("home")
                        2 -> navController.navigate("profile")
                        3 -> navController.navigate("dating")
                        4 -> navController.navigate("settings")
                    }
                }
            )
        },
        content = { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("home") {
                    HomeScreen(
                        navController = navController,
                        currentTab = currentTab,
                        onTabChange = onTabChange
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        navController = navController,
                        currentTab = currentTab,
                        onTabChange = onTabChange
                    )
                }
                composable("dating") {
                    // Your Dating screen content here
                }
                composable("settings") {
                    // Your Settings screen content here
                }
                composable("dms") {
                    // Your DMs screen content here
                }
                composable("notifications") {
                    // Your Notifications screen content here
                }
                composable("peopleWhoLikeMe") {
                    PeopleWhoLikeMeScreen(navController = navController)
                }
                composable("leaderboard") {
                    LeaderboardScreen(navController = navController)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar(
    title: String,
    onProfileClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    TopAppBar(
        title = { Text(text = title, color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onProfileClick) {
                Icon(imageVector = Icons.Default.Person, contentDescription = "People Who Like Me", tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = onNotificationClick) {
                Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
    )
}

@Composable
fun BottomNavBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color.Black
    ) {
        val items = listOf(Icons.Default.Message, Icons.Default.Feed, Icons.Default.Person, Icons.Default.Favorite, Icons.Default.Settings)
        val labels = listOf("DMs", "Feed", "Profile", "Dating", "Settings")
        items.forEachIndexed { index, icon ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = labels[index],
                        tint = if (selectedIndex == index) Color(0xFF00bf63) else Color.Gray
                    )
                },
                label = { Text(labels[index], color = if (selectedIndex == index) Color(0xFF00bf63) else Color.Gray) },
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
