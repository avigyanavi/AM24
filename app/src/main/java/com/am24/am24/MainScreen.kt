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
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainScreen(navController: NavHostController, onLogout: () -> Unit, postViewModel: PostViewModel) {
    val items = listOf(
        BottomNavItem("Date", Icons.Default.FavoriteBorder, "dating"),
        BottomNavItem("Map", Icons.Default.Map, "map"),
        BottomNavItem("Chat", Icons.Default.MailOutline, "dms"),
        BottomNavItem("Feed", Icons.Default.RssFeed, "home"),
        BottomNavItem("Profile", Icons.Default.PersonOutline, "profile")
    )

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopNavBarVisible = currentRoute != "chat/{otherUserId}" && currentRoute != "dating" && currentRoute != "home"
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
        MainNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            postViewModel = postViewModel
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
    val isPremium = remember { mutableStateOf(false) }
    var showLocationPrefDialog by remember { mutableStateOf(false) }
    var allowLocationForMatches by remember { mutableStateOf(false) }

    // Fetch premium status from Firebase
    DisposableEffect(currentUserId) {
        val profileRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(currentUserId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(Profile::class.java)
                isPremium.value = profile?.isPremium ?: false
                // Fetch allowLocationForMatches
                val savedPref = snapshot.child("allowLocationForMatches").getValue(Boolean::class.java)
                if (savedPref != null) {
                    allowLocationForMatches = savedPref
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TopNavBar", "Failed to fetch profile: ${error.message}")
            }
        }
        profileRef.addValueEventListener(listener)
        onDispose {
            profileRef.removeEventListener(listener)
        }
    }

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
            Text("Kupidx", color = Color(0xFFFF6F00))
        },
        navigationIcon = {
            Box(
                modifier = Modifier.size(40.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.kupidx_logo),
                    contentDescription = "KupidX Logo",
                    modifier = Modifier.size(56.dp)
                )
            }
        },
        actions = {
            // Location settings icon (map screen)
            if (currentRoute == "map") {
                IconButton(onClick = { showLocationPrefDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location Settings",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            // User Settings Icon (Profile or Settings screen)
            if (isProfileScreen || isUserSettings) {
                val shouldAnimate = isProfileScreen && !isPremium.value
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (shouldAnimate) 1.2f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 500,
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                IconButton(onClick = {
                    if (isUserSettings) {
                        navController.popBackStack()
                    } else {
                        navController.navigate("settings")
                    }
                }) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                transformOrigin = TransformOrigin.Center
                            ),
                        contentAlignment = Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "User Settings",
                            tint = if (isUserSettings) Color(0xFFFF6F00) else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            // Logout Icon (Profile screen only)
            if (isProfileScreen) {
                IconButton(onClick = {
                    FirebaseAuth.getInstance().signOut()
                    onLogout()
                }) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Logout",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
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

    if (showLocationPrefDialog) {
        AlertDialog(
            onDismissRequest = { showLocationPrefDialog = false },
            title = { Text("Location Visibility Settings") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Visible to Matches")
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = allowLocationForMatches,
                            onCheckedChange = { allowLocationForMatches = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF6F00), // Orange when selected
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Gray
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
                        .child("allowLocationForMatches").setValue(allowLocationForMatches)
                        .addOnSuccessListener {
                            Log.d("TopNavBar", "Location preference saved: $allowLocationForMatches")
                            showLocationPrefDialog = false
                        }
                        .addOnFailureListener { e ->
                            Log.e("TopNavBar", "Failed to save preference: ${e.message}")
                        }
                }) { Text("Save") }
            }
        )
    }
}

data class BottomNavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun BottomNavigationBar(navController: NavController, items: List<BottomNavItem>) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(containerColor = Color.Black) {
        items.forEach { item ->
            val selected = if (item.route == "dms") {
                val route = currentDestination?.route ?: ""
                route.startsWith("dms") || route.startsWith("chat/") || route.startsWith("matchedUserProfile/")
            } else {
                currentDestination?.hierarchy?.any { it.route == item.route } == true
            }

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        launchSingleTop = true
                        restoreState = true
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