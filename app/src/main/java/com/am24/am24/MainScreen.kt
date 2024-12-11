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
fun MainScreen(navController: NavHostController, onLogout: () -> Unit, postViewModel: PostViewModel) {
    val items = listOf(
        BottomNavItem("Profile", Icons.Default.PersonOutline, "profile"),
        BottomNavItem("Leaderboard", Icons.Default.Leaderboard, "leaderboard"),
        BottomNavItem("Dating", Icons.Default.FavoriteBorder, "dating"),
        BottomNavItem("Feed", Icons.Default.RssFeed, "home"),
        BottomNavItem("DMs", Icons.Default.MailOutline, "dms"),
    )

    // Obtain the current user ID
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // Get the current destination
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine if TopNavBar should be visible
    val isTopNavBarVisible = currentRoute != "chat/{otherUserId}" // Hide on ChatScreen

    // Obtain the ProfileViewModel instance
    val profileViewModel: ProfileViewModel = viewModel()

    Scaffold(
        topBar = {
            if (isTopNavBarVisible) {
                TopNavBar(
                    navController = navController,
                    profileViewModel = profileViewModel,
                    currentUserId = currentUserId,
                    onLogout = onLogout
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(navController = navController, items = items)
        }
    ) { innerPadding ->
        // Pass the innerPadding to MainNavGraph via the modifier
        MainNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            postViewModel = postViewModel // Pass it here
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    currentUserId: String,
    onLogout: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isNotificationsSelected = currentDestination?.route == "notifications"
    val isPeopleWhoLikeMeSelected = currentDestination?.route == "peopleWhoLikedMe"
    val isProfileScreen = currentDestination?.route == "profile"

    val title = when (currentDestination?.route) {
        "dms" -> "Chat"
        "home" -> "KupidX"
        "profile" -> "Profile"
        "dating" -> "Dating"
        "leaderboard" -> "Rankings"
        "peopleWhoLikedMe" -> "People Who Like Me"
        "notifications" -> "Notifications"
        "filters?initialTab={initialTab}" -> "Filters"
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
        title = { Text(text = title, color = Color(0xFFFF4500), fontSize = 18.sp) }, // Title in light orange
        navigationIcon = {
            IconButton(onClick = {
                if (isPeopleWhoLikeMeSelected) {
                    navController.popBackStack()
                } else {
                    navController.navigate("peopleWhoLikedMe")
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "People Who Like Me",
                    tint = if (isPeopleWhoLikeMeSelected) Color(0xFFFF4500) else Color.Gray, // Button in dark orange
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = {
                if (isNotificationsSelected) {
                    navController.popBackStack()
                } else {
                    navController.navigate("notifications")
                }
            }) {
                BadgedBox(
                    badge = {
                        if (unreadCount.value > 0) {
                            Badge(
                                containerColor = Color.Red,
                                modifier = Modifier.size(15.dp)
                            ) {
                                Text(
                                    text = unreadCount.value.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = if (isNotificationsSelected) Color(0xFFFF4500) else Color.Gray, // Button in dark orange
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (isProfileScreen) {
                IconButton(onClick = {
                    navController.navigate("settings")
                }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFFFFA500),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(onClick = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    val initialTab = if (currentRoute == "home") 1 else 0 // 1 for Feed, 0 for Dating
                    if (currentDestination?.route?.startsWith("filters") == true) {
                        navController.popBackStack() // Return to previous screen
                    } else {
                        navController.navigate("filters?initialTab=$initialTab") // Navigate to FiltersScreen
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filters",
                        tint = if (currentDestination?.route?.startsWith("filters") == true) Color(0xFFFF4500) else Color(0xFFFFA500), // Preserve tint color on selection
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
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
                        tint = if (selected) Color(0xFFFF4500) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        color = if (selected) Color(0xFFFF4500) else Color.Gray,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFFF4500),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFFFF4500),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.DarkGray
                )
            )
        }
    }
}