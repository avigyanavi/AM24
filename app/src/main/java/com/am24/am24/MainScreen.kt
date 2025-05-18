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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.am24.am24.util.LocaleUtils

@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainScreen(navController: NavHostController, onLogout: () -> Unit, postViewModel: PostViewModel) {
    val items = listOf(
        BottomNavItem(stringResource(R.string.date), Icons.Default.FavoriteBorder, "dating"),
        BottomNavItem(stringResource(R.string.map), Icons.Default.Map, "map"),
        BottomNavItem(stringResource(R.string.chat), Icons.Default.MailOutline, "dms"),
        BottomNavItem(stringResource(R.string.feed), Icons.Default.RssFeed, "home"),
        BottomNavItem(stringResource(R.string.profile), Icons.Default.PersonOutline, "profile")
    )

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val priceAll = stringResource(id = R.string.price_all)


    // ➋ only show the global Top/Bottom bars if NOT on leaderboard
    val showGlobalBars = currentRoute?.startsWith("chat/") == false &&
            currentRoute != "leaderboard"
    val priceTier = rememberSaveable { mutableStateOf(priceAll) }

    val profileViewModel: ProfileViewModel = viewModel()

    Scaffold(
        topBar = {
            if (showGlobalBars) {
                TopNavBar(
                    navController = navController,
                    profileViewModel = profileViewModel,
                    currentUserId = currentUserId,
                    postViewModel = postViewModel,   // ← pass it
                    onPriceChange = { priceTier.value = it },   //  ← update state
                            onLogout = onLogout
                )
            }
        },
        bottomBar = {
            if (showGlobalBars) {
                BottomNavigationBar(navController = navController, items = items)
            }
        }
    ) { innerPadding ->
        val paddingValues = if (showGlobalBars) innerPadding else PaddingValues(0.dp)
        MainNavGraph(
            navController   = navController,
            modifier        = Modifier.padding(paddingValues),
            postViewModel   = postViewModel,
            currentPrice  = priceTier.value
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    currentUserId: String,
    postViewModel: PostViewModel,   // ← pass it
    onLogout: () -> Unit,
    onPriceChange      : (String) -> Unit = {}   // ← NEW, default no-op
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

    /*  ─────────  STATE FOR PRICE FILTER  ────────── */
    val priceAll = stringResource(id = R.string.price_all)

    var priceMenuExpanded   by rememberSaveable { mutableStateOf(false) }
    var selectedPriceRange  by rememberSaveable { mutableStateOf(priceAll) }


    // New: language menu
    var pickLangMenu by remember { mutableStateOf(false) }
    val ctx      = LocalContext.current
    val activity = ctx as? ComponentActivity
    var appLang  by rememberSaveable { mutableStateOf(LocaleUtils.getSavedLang(ctx)) }
    val languages = listOf("English" to "en", "हिन्दी" to "hi", "বাংলা" to "bn")


    // Fetch premium status from Firebase
    DisposableEffect(currentUserId) {
        val profileRef = FirebaseRefs.db
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
        val notificationsRef = FirebaseRefs.db
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
    var showForYouDialog by remember { mutableStateOf(false) }

    // anywhere before TopAppBar:
    val isOnHome = currentDestination
        ?.hierarchy
        ?.any { it.route == "home" } == true

    TopAppBar(
        title = {
            Text(stringResource(R.string.app_name), color = Color(0xFFFF6F00))
        },
        navigationIcon = {
            Box(
                modifier = Modifier.size(40.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.kupidx_logo),
                    contentDescription = stringResource(R.string.logo_kupidx_desc),
                    modifier = Modifier.size(56.dp)
                )
            }
        },
        actions = {

            if (currentRoute == "dating" || currentRoute == "profile") {
                IconButton(onClick = { navController.navigate("leaderboard") }) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = stringResource(R.string.cd_leaderboard),
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

           // Location settings icon (map screen)
            if (currentRoute == "map") {
                /* 1) Price-Filter icon (new) – shows before the old Location icon */
                IconButton(onClick = { priceMenuExpanded = true }) {
                    Icon(
                        imageVector      = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.cd_price_filter),
                        tint              = if (selectedPriceRange != stringResource(R.string.price_all))
                            Color(0xFFFF6F00) else Color.White,
                        modifier          = Modifier.size(24.dp)
                    )
                }

                /* ▼ Dropdown for price tiers */
                DropdownMenu(
                    expanded          = priceMenuExpanded,
                    onDismissRequest  = { priceMenuExpanded = false }
                ) {
                    val tiers = listOf(
                        stringResource(R.string.price_all),
                        stringResource(R.string.price_1),
                        stringResource(R.string.price_2),
                        stringResource(R.string.price_3),
                        stringResource(R.string.price_4)
                    )
                    tiers.forEach { tier ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    tier,
                                    color = if (tier == selectedPriceRange) Color(0xFFFF6F00) else Color.White
                                )
                            },
                            onClick = {
                                priceMenuExpanded  = false
                                if (tier != selectedPriceRange) {
                                    selectedPriceRange = tier
                                    /* TODO : forward this to MapScreen / ViewModel */
                                    onPriceChange(tier)                 // ✅ fire the callback
                                }
                            }
                        )
                    }
                }

                /* 2) Existing location icon */
                IconButton(onClick = { showLocationPrefDialog = true }) {
                    Icon(
                        imageVector   = Icons.Default.LocationOn,
                        contentDescription = stringResource(R.string.cd_location_settings),
                        tint          = Color.White,
                        modifier      = Modifier.size(24.dp)
                    )
                }
            }
            if (isOnHome) {
                if (!isPremium.value) {
                    IconButton(onClick = { showForYouDialog = true }) {
                        Icon(
                            Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.cd_for_you),
                            tint = Color(0xFFFF6F00)
                        )
                    }
                }
                // Create Post
                IconButton(onClick = { navController.navigate("create_post") }) {
                    Icon(
                        imageVector    = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_create_post),
                        tint           = Color(0xFFFF6F00)
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
                            tint = if (isUserSettings) Color(0xFFFF6F00) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
               // Saved-Posts Icon (Profile screen only)
               if (isProfileScreen) {
                       IconButton(onClick = { navController.navigate("saved_posts") }) {
                               Icon(
                                       imageVector   = Icons.Default.BookmarkBorder,
                                       contentDescription = "Saved Posts",
                                       tint          = Color.White,
                                       modifier      = Modifier.size(24.dp)
                                           )
                           }
                   }

            // 5) **Language picker** on Dating, Map, DMs and Feed:
            if (currentRoute in listOf("dating", "map", "dms", "home")) {
                IconButton(
                    onClick    = { pickLangMenu = !pickLangMenu },
                    colors     = IconButtonDefaults.iconButtonColors(
                        contentColor = if (pickLangMenu) Color(0xFFFF6F00) else Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = stringResource(R.string.btn_language),
                        modifier = Modifier.size(24.dp)
                    )
                }
                DropdownMenu(
                    expanded        = pickLangMenu,
                    onDismissRequest = { pickLangMenu = false }
                ) {
                    languages.forEach { (label, code) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    color = if (appLang == code) Color(0xFFFF6F00) else Color.White
                                )
                            },
                            onClick = {
                                pickLangMenu = false
                                if (appLang != code) {
                                    appLang = code
                                    LocaleUtils.setAppLocale(ctx, code)
                                    activity?.recreate()
                                }
                            }
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
                        tint = if (isNotificationsSelected) Color(0xFFFF6F00) else Color.White,
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
            title = { Text(stringResource(R.string.dialog_location_title)) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.lbl_visible_to_matches))
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = allowLocationForMatches,
                            onCheckedChange = { allowLocationForMatches = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF6F00), // Orange when selected
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.White
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    FirebaseRefs.db.getReference("users").child(currentUserId)
                        .child("allowLocationForMatches").setValue(allowLocationForMatches)
                        .addOnSuccessListener {
                            Log.d("TopNavBar", "Location preference saved: $allowLocationForMatches")
                            showLocationPrefDialog = false
                        }
                        .addOnFailureListener { e ->
                            Log.e("TopNavBar", "Failed to save preference: ${e.message}")
                        }
                }) { Text(stringResource(R.string.btn_save)) }
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
                        tint = if (selected) Color(0xFFFF6F00) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        color = if (selected) Color(0xFFFF6F00) else Color.White,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFFF6F00),
                    unselectedIconColor = Color.White,
                    selectedTextColor = Color(0xFFFF6F00),
                    unselectedTextColor = Color.White,
                    indicatorColor = Color.DarkGray
                )
            )
        }
    }
}