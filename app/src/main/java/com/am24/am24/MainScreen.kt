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
import androidx.compose.foundation.Image
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainScreen(navController: NavHostController, onLogout: () -> Unit, postViewModel: PostViewModel) {
    val items = listOf(
        BottomNavItem("Profile", Icons.Default.PersonOutline, "profile"),
        BottomNavItem("Polls", Icons.Default.InsertEmoticon, "quiz"),
        BottomNavItem("Date", Icons.Default.FavoriteBorder, "dating"),
        BottomNavItem("Feed", Icons.Default.RssFeed, "home"),
        BottomNavItem("Chat", Icons.Default.MailOutline, "dms"),
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

    val currentRoute = currentDestination?.route

    val isNotificationsSelected = currentRoute == "notifications"
    val isUserSettings = currentRoute == "settings"
    val isProfileScreen = currentRoute == "profile"

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
        title = {
            // No textual title
        },
        navigationIcon = {
            // Logo is now in a Box (not clickable)
            Box(
                modifier = Modifier.size(40.dp) // Adjust as needed
            ) {
                Image(
                    painter = painterResource(id = R.drawable.kupidx_logo),
                    contentDescription = "KupidX Logo",
                    modifier = Modifier.size(56.dp)
                )
            }
        },
        actions = {
            // User Settings Icon (only on Profile screen)
            if (isProfileScreen) {
                IconButton(onClick = {
                    navController.navigate("settings") // New route for user settings
                }) {
                    Icon(
                        imageVector = Icons.Default.ManageAccounts,
                        contentDescription = "User Settings",
                        tint = if (isUserSettings) Color(0xFFFF6F00) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // Show Filters Icon on Home, Dating and Filters screens
                val onFiltersScreen = currentRoute?.startsWith("filters") == true
                val showFiltersIcon = (currentRoute == "home") || (currentRoute == "dating") || onFiltersScreen

                if (showFiltersIcon) {
                    IconButton(onClick = {
                        val initialTab = if (currentRoute == "home") 1 else 0
                        if (onFiltersScreen) {
                            // If already on filters, go back
                            navController.popBackStack()
                        } else {
                            navController.navigate("filters?initialTab=$initialTab")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Filters",
                            tint = if (onFiltersScreen) Color(0xFFFF6F00) else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            // Notifications Icon
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
                        tint = if (isNotificationsSelected) Color(0xFFFF6F00) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
                        tint = if (selected) Color(0xFFFF6F00) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        color = if (selected) Color(0xFFFF6F00) else Color.Gray,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFFF6F00),
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = Color(0xFFFF6F00),
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.DarkGray
                )
            )
        }
    }
}