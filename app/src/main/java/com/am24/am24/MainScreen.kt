@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.material3.*
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
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import android.util.Log
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainScreen(navController: NavHostController, onLogout: () -> Unit) {
    val items = listOf(
        BottomNavItem("DMs", Icons.Default.MailOutline, "dms"),
        BottomNavItem("Feed", Icons.Default.Home, "home"),
        BottomNavItem("Profile", Icons.Default.Person, "profile"),
        BottomNavItem("Dating", Icons.Default.Favorite, "dating"),
        BottomNavItem("Settings", Icons.Default.Settings, "settings")
    )

    // Obtain the current user ID
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // Obtain the ProfileViewModel instance
    val profileViewModel: ProfileViewModel = viewModel()

    Scaffold(
        topBar = {
            TopNavBar(
                navController = navController,
                profileViewModel = profileViewModel,
                currentUserId = currentUserId,
                onLogout = onLogout
            )
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
fun TopNavBar(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    currentUserId: String,
    onLogout: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isPeopleWhoLikeMeSelected = currentDestination?.route == "peopleWhoLikeMe"
    val isNotificationsSelected = currentDestination?.route == "notifications"
    val isSavedPostsSelected = currentDestination?.route == "savedPosts"

    val title = when (currentDestination?.route) {
        "dms" -> "DMs"
        "home" -> "Feed"
        "savedPosts" -> "Saved Posts"
        "profile" -> "Profile"
        "dating" -> "Dating"
        "settings" -> "Settings"
        "peopleWhoLikeMe" -> "People Who Like Me"
        "notifications" -> "Notifications"
        else -> ""
    }

    val unreadCount = remember { mutableStateOf(0) }

    // Observe unread notifications count
    DisposableEffect(currentUserId) {
        val notificationsRef = FirebaseDatabase.getInstance()
            .getReference("notifications")
            .child(currentUserId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.children.count { child ->
                    val notification = child.getValue(Notification::class.java)
                    notification?.isRead == "false"
                }
                unreadCount.value = count
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TopNavBar", "Failed to fetch unread notifications count: ${error.message}")
            }
        }
        notificationsRef.addValueEventListener(listener)
        onDispose {
            notificationsRef.removeEventListener(listener)
        }
    }

    TopAppBar(
        title = { Text(text = title, color = Color.White, fontSize = 18.sp) },
        navigationIcon = {
            IconButton(onClick = {
                navController.navigate("peopleWhoLikeMe")
            }) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "People Who Like Me",
                    tint = if (isPeopleWhoLikeMeSelected) Color(0xFF00bf63) else Color.White,
                    modifier = Modifier.size(18.dp) // Slightly smaller size for better alignment
                )
            }
        },
        actions = {
            IconButton(onClick = {
                navController.navigate("notifications")
            }) {
                BadgedBox(
                    badge = {
                        if (unreadCount.value > 0) {
                            Badge(
                                containerColor = Color.Red, // Set a background color for visibility
                                modifier = Modifier.size(15.dp) // Smaller badge size
                            ) {
                                Text(
                                    text = unreadCount.value.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp // Smaller font size
                                )
                            }
                        }
                    },
                    modifier = Modifier.size(20.dp) // Set size for the BadgedBox
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = if (isNotificationsSelected) Color(0xFF00bf63) else Color.White,
                        modifier = Modifier.size(18.dp) // Smaller icon size for balance
                    )
                }
            }
            IconButton(onClick = {
                navController.navigate("savedPosts") // New SavedPosts navigation
            }) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Saved Posts",
                    tint = if (isSavedPostsSelected) Color(0xFF00bf63) else Color.White,
                    modifier = Modifier.size(18.dp) // Smaller icon size for balance
                )
            }
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp) // Smaller icon size for balance
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
