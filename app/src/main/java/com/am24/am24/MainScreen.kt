@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.app.Activity
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.am24.am24.ui.theme.LocalThreadsIconSizes
import com.am24.am24.ui.theme.LocalThreadsSpacing
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination


@RequiresApi(Build.VERSION_CODES.O_MR1)
@Composable
fun MainScreen(navController: NavHostController, onLogout: () -> Unit, postViewModel: PostViewModel, locationManager: LocationManager) {
    val items = listOf(
        BottomNavItem(stringResource(R.string.profile), Icons.Default.PersonOutline, "profile"),
        BottomNavItem(stringResource(R.string.feed), Icons.Outlined.RssFeed, "home"),
        BottomNavItem(stringResource(R.string.tab_nearby), Icons.Default.Favorite, "map"),
        BottomNavItem(stringResource(R.string.chat), Icons.Default.MailOutline, "dms"),
        BottomNavItem(stringResource(R.string.settings), Icons.Default.Settings, "settings")
    )
    val spacing = LocalThreadsSpacing.current

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val mainViewModel: MainViewModel = viewModel()
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    mainViewModel.ensureListeners()
    mainViewModel.onRouteChanged(currentRoute)

    // ➋ only show the global Top/Bottom bars if NOT on leaderboard

    val profileViewModel: ProfileViewModel = viewModel()
    val currentProfile by profileViewModel.currentUserProfile.collectAsState()

    // ─── collect both flags ───────────────────────────
    val isPremium by profileViewModel.isPremium.collectAsState(initial = false)
    val isPlus    by profileViewModel.isPlus   .collectAsState(initial = false)
    val shouldForceSubscription = mainUiState.shouldForceSubscription
    val showTopBar = mainUiState.showTopBar
    val showBottomBar = mainUiState.showBottomBar
    // ───────────────────────────────────────────────────
    val context = LocalContext.current
    val activity = context as? Activity

    BackHandler(enabled = navController.previousBackStackEntry == null) {
        activity?.finish()
    }
    LaunchedEffect(currentUserId) {
        profileViewModel.fetchCurrentUserProfile()
    }

    LaunchedEffect(shouldForceSubscription, currentRoute) {
        if (shouldForceSubscription && currentRoute?.startsWith("subscription") != true) {
            navController.navigate("subscription?allowIfSubscribed=false&force=true") {
                launchSingleTop = true
            }
        }
    }


    var showOnlineUsers by remember { mutableStateOf(false) }
    LaunchedEffect(navBackStackEntry?.destination?.route) {
        showOnlineUsers = navBackStackEntry?.destination?.route == "omegleUsers"
    }
    Scaffold(
        topBar = {
            if (showTopBar) {
                TopNavBar(
                    navController = navController,
                    profileViewModel = profileViewModel,
                    currentUserId = currentUserId,
                    postViewModel = postViewModel,
                    onLogout = onLogout,
                    isPremium = isPremium,
                    isPlus = isPlus,
                    locationManager = locationManager,
                    showOnlineUsers = showOnlineUsers,
                    onToggleOnlineUsers = {
                        if (showOnlineUsers) {
                            navController.popBackStack()
                        } else {
                            navController.navigate("omegleUsers")
                        }
                    },
                    mainUiState = mainUiState,
                    onLocationPreferencesClick = { mainViewModel.showLocationDialog() },
                    onLocationDialogDismiss = { mainViewModel.hideLocationDialog() },
                    onLocationDialogConfirm = { matches, public, private ->
                        mainViewModel.updateLocationPreferences(matches, public, private)
                    },
                    onClearLocationSpoofing = {
                        mainViewModel.clearLocationSpoofing()
                    },
                    onSetLocationSpoofing = { country, city ->
                        mainViewModel.setSpoofedLocation(country, city)
                    }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    navController = navController,
                    items = items,
                    onItemSelected = { route, alreadySelected ->
                        if (alreadySelected) return@BottomNavigationBar

                        val isBlockedByTrial = shouldForceSubscription && route != "settings"
                        if (isBlockedByTrial) return@BottomNavigationBar

                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val paddingValues = if (showBottomBar) innerPadding else PaddingValues(spacing.xs)
        MainNavGraph(
            navController = navController,
            modifier = Modifier.padding(paddingValues),
            postViewModel = postViewModel,
            locationManager = locationManager
        )
        val pendingInvite = mainUiState.omegleInvite
        if (pendingInvite != null) {
            AlertDialog(
                onDismissRequest = { /* keep dialog until user acts */ },
                text = { Text(stringResource(R.string.join_random_chat), color = KupidxOrange) },
                confirmButton = {
                    TextButton(onClick = {
                        mainViewModel.acceptOmegleInvite(pendingInvite)
                        navController.navigate("omegleChat/${pendingInvite.chatId}/${pendingInvite.otherUserId}")
                    }) { Text(stringResource(R.string.join_chat), color = KupidxOrange) }
                },
                dismissButton = {
                    mainViewModel.rejectOmegleInvite(pendingInvite)
                    TextButton(onClick = {
                    }) { Text(stringResource(R.string.ignore_chat), color = KupidxOrange) }
                }
            )
        }
        val inviteMessage = mainUiState.inviteStatusMessage
        if (inviteMessage != null) {
            AlertDialog(
                onDismissRequest = { mainViewModel.clearInviteStatusMessage() },
                confirmButton = {
                    TextButton(onClick = { mainViewModel.clearInviteStatusMessage() }) {
                        Text(stringResource(id = android.R.string.ok), color = KupidxOrange)
                    }
                },
                text = { Text(stringResource(inviteMessage), color = KupidxOrange) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar(
    navController: NavController,
    profileViewModel: ProfileViewModel,
    currentUserId: String,
    postViewModel: PostViewModel,
    onLogout: () -> Unit,
    isPremium: Boolean,
    isPlus: Boolean,
    locationManager: LocationManager,
    showOnlineUsers: Boolean,
    onToggleOnlineUsers: () -> Unit,
    mainUiState: MainUiState,
    onLocationPreferencesClick: () -> Unit,
    onLocationDialogDismiss: () -> Unit,
    onLocationDialogConfirm: (Boolean, Boolean, Boolean) -> Unit,
    onClearLocationSpoofing: () -> Unit,
    onSetLocationSpoofing: (String, String) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val isNotificationsSelected = currentRoute == "notifications"
    val isUserSettings = currentRoute == "settings"
    val isProfileScreen = currentRoute == "profile"
    val isDMScreen = currentRoute == "dms"

    val unreadCount = mainUiState.unreadNotifications
    val allowLocationForMatches = mainUiState.allowLocationForMatches
    val allowLocationPublic = mainUiState.allowLocationPublic
    val isPrivate = mainUiState.isPrivateProfile
    val showLocationPrefDialog = mainUiState.showLocationPrefDialog

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val mapSelectedTab by savedStateHandle?.getStateFlow("mapSelectedTab", 0)?.collectAsState()
        ?: remember { mutableStateOf(0) }
    val triggerLocationDialog by savedStateHandle?.getStateFlow("showLocationPrefDialog", false)
        ?.collectAsState() ?: remember { mutableStateOf(false) }

    LaunchedEffect(triggerLocationDialog) {
        if (triggerLocationDialog) {
            onLocationPreferencesClick()
            savedStateHandle?.set("showLocationPrefDialog", false)
        }
    }

    val selectedCountry = mainUiState.selectedCountry
    var cityMenuExpanded by remember { mutableStateOf(false) }
    val selectedCity = mainUiState.selectedCity
    val feedFilterState by postViewModel.feedFilters.collectAsState()
    val homeSelectedCountry = feedFilterState.feedFilters.country

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val hasLocationSpoofAccess = mainUiState.hasLocationSpoofAccess

    LaunchedEffect(Unit) {
        val savedOrientation = prefs.getString("map_orientation_filter", "") ?: ""
        savedStateHandle?.set("mapOrientationFilter", savedOrientation)
    }

    LaunchedEffect(homeSelectedCountry) {
        prefs.edit().putString("home_country_filter", homeSelectedCountry).apply()
    }

    // anywhere before TopAppBar:
    val isOnHome = currentDestination
        ?.hierarchy
        ?.any { it.route == "home" } == true
    val isOnMap = currentDestination
        ?.hierarchy
        ?.any { it.route == "map" } == true
    val isOnOnlineScreen = currentDestination
        ?.hierarchy
        ?.any { it.route == "omegleUsers" } == true
    val isAdmin by profileViewModel.isAdmin.collectAsState()
    val isFeedSearchVisible by postViewModel.isFeedSearchVisible.collectAsState()
    val spacing = LocalThreadsSpacing.current
    val iconSizes = LocalThreadsIconSizes.current

    TopAppBar(
        modifier = Modifier.shadow(spacing.lg),
        title = {
//            Text(stringResource(R.string.app_name), color = Color(0xFFFF6F00))
        },
        navigationIcon = {
            Box(
                modifier = Modifier.size(iconSizes.xl + spacing.xs)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.kupidx_logo1),
                    contentDescription = stringResource(R.string.logo_kupidx_desc),
                    modifier = Modifier.size(iconSizes.xl + spacing.md)
                )
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
            if (isAdmin) {
                IconButton(onClick = { navController.navigate("verifications_review") }) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = "Review IDs")
                }
                IconButton(onClick = { navController.navigate("feedback_list") }) {
                    Icon(Icons.Default.Feedback, contentDescription = "View Feedback")
                }
            }


            if (isOnMap || isOnOnlineScreen) {
                IconButton(onClick = { onToggleOnlineUsers() }) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = stringResource(R.string.cd_omegle),
                        tint = if (showOnlineUsers) KupidxOrange else Color.White,
                        modifier = Modifier.size(iconSizes.lg)
                    )
                }
            }

//            if (isPlus && currentRoute == "map" && mapSelectedTab == 0) {
//                Box {
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        IconButton(
//                            onClick = { countryMenuExpanded = !countryMenuExpanded },
//                        ) {
//                            Icon(
//                                imageVector = Icons.Default.Public,
//                                contentDescription = stringResource(R.string.cd_country_filter),
//                                tint = if (selectedCountry.isNotBlank()) Color(0xFFFF6F00) else Color.White,
//                                modifier = Modifier.size(24.dp)
//                            )
//                        }
//                        if (selectedCountry.isNotBlank()) {
//                            Text(selectedCountry, color = Color(0xFFFF6F00))
//                        }
//                    }
//                    DropdownMenu(
//                        expanded = countryMenuExpanded,
//                        onDismissRequest = { countryMenuExpanded = false }
//                    ) {
//                        DropdownMenuItem(
//                            text = { Text(stringResource(R.string.clear_country_filter)) },
//                            onClick = {
//                                countryMenuExpanded = false
//                                val profileRef =
//                                    FirebaseRefs.db.getReference("users").child(currentUserId)
//                                profileRef.updateChildren(
//                                    mapOf(
//                                        "isLocationSpoofed" to false,
//                                        "country" to ""
//                                    )
//                                )
//                                selectedCountry = ""
//                                locationManager.resumeUpdates()
//                            }
//                        )
//                        val countryOptions = stringArrayResource(R.array.country_names).toList()
//                        countryOptions.forEach { c ->
//                            DropdownMenuItem(
//                                text = { Text(c) },
//                                onClick = {
//                                    countryMenuExpanded = false
//                                    val profileRef =
//                                        FirebaseRefs.db.getReference("users").child(currentUserId)
//                                    val latLng = CountryLatLngMap.getLatLng(c)
//                                    if (latLng != null) {
//                                        locationManager.pauseUpdates()
//                                        locationManager.setCustomLocation(
//                                            currentUserId,
//                                            latLng.first,
//                                            latLng.second
//                                        )
//                                        profileRef.updateChildren(
//                                            mapOf(
//                                                "country" to c,
//                                                "city" to "",
//                                                "isLocationSpoofed" to true
//                                            )
//                                        )
//                                    } else {
//                                        Toast.makeText(
//                                            context,
//                                            context.getString(
//                                                R.string.country_coords_unavailable,
//                                                c
//                                            ),
//                                            Toast.LENGTH_SHORT
//                                        ).show()
//                                        profileRef.updateChildren(
//                                            mapOf(
//                                                "country" to c,
//                                                "city" to "",
//                                                "isLocationSpoofed" to false
//                                            )
//                                        )
//                                    }
//                                    selectedCountry = c
//                                    savedStateHandle?.set("mapCountryChanged", true)
//                                }
//                            )
//                        }
//                    }
//                }
//            }

//            // ← Leaderboard button in place of the old Map button
//            if (currentRoute != "map" || currentRoute != "home" || currentRoute != "dms") {
//                TextButton(
//                    onClick = { navController.navigate("leaderboard") },
//                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
//                ) {
//                    Icon(
//                        imageVector = Icons.Outlined.EmojiEvents,
//                        contentDescription = stringResource(R.string.leaderboard)
//                    )
//                    Spacer(modifier = Modifier.width(4.dp))
//                }
//            }

            // Location settings icon (map screen)
            if (currentRoute == "map") {
                // --- NEW ORDER & LAYOUT ---------------------------------------------------
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp)  // subtle separation from the Wc icon
                ) {
                    // 1) City selector icon (only meaningful if country = Mexico)
                    IconButton(
                        onClick = {
                            if (hasLocationSpoofAccess) {
                                cityMenuExpanded = !cityMenuExpanded
                            } else {
                                cityMenuExpanded = false
                                navController.navigate("subscription")
                            }
//                        },
//                        enabled = if (hasLocationSpoofAccess) {
//                            CountryUtil.isMexico(context, selectedCountry)
//                        } else {
//                            true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationCity,
                            contentDescription = stringResource(R.string.cd_city_filter),
                            tint = when {
                                !hasLocationSpoofAccess -> Color(0x66FFFFFF)
                                selectedCity.isNotBlank() -> Color(0xFFFF6F00)
//                                CountryUtil.isMexico(context, selectedCountry) -> Color.White
//                                else -> Color(0x66FFFFFF)
                                else -> Color.White
                            },
                            modifier = Modifier.size(iconSizes.lg)
                        )
                    }

                    // City dropdown (Mexico only)
                    if (hasLocationSpoofAccess) {
                        val canonicalSpoofCountry = canonicalCountry(
                            selectedCountry.takeIf { it.isNotBlank() }
                                ?: homeSelectedCountry?.takeIf { it.isNotBlank() }
                                ?: "India"
                        )
                        val cityOptions = when (canonicalSpoofCountry) {
                            "Mexico" -> stringArrayResource(R.array.mexico_cities).toList()
                            "United States" -> stringArrayResource(R.array.usa_cities).toList()
                            else -> stringArrayResource(R.array.india_cities).toList()
                        }
                        val resolvedSpoofCountry = when (canonicalSpoofCountry) {
                            "Mexico" -> "Mexico"
                            "United States" -> "United States"
                            else -> "India"
                        }
                        val cityLatLngLookup: (String) -> Pair<Double, Double>? = when (resolvedSpoofCountry) {
                            "Mexico" -> { city -> MexicoCityLatLngMap.getLatLng(city) }
                            "United States" -> { city -> UsaCityLatLngMap.getLatLng(city) }
                            else -> { city -> IndiaCityLatLngMap.getLatLng(city) }
                        }
                        DropdownMenu(
                            expanded = cityMenuExpanded,
                            onDismissRequest = { cityMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_city_filter)) },
                                onClick = {
                                    cityMenuExpanded = false
                                    if (selectedCountry.isBlank()) {
                                        // No fixed country → back to GPS
                                        onClearLocationSpoofing()
                                        locationManager.resumeUpdates()
                                    } else {
                                        // Keep the country, clear city
                                        val countryLatLng = CountryLatLngMap.getLatLng(selectedCountry)
                                        if (countryLatLng != null) {
                                            locationManager.pauseUpdates()
                                            locationManager.setCustomLocation(
                                                currentUserId,
                                                countryLatLng.first,
                                                countryLatLng.second
                                            )
                                        }
                                        onSetLocationSpoofing(selectedCountry, "")
                                    }
                                    savedStateHandle?.set("mapCountryChanged", true)
                                }
                            )
                            cityOptions.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city) },
                                    onClick = {
                                        cityMenuExpanded = false
                                        val latLng = cityLatLngLookup(city)
                                        if (latLng != null) {
                                            locationManager.pauseUpdates()
                                            locationManager.setCustomLocation(
                                                currentUserId,
                                                latLng.first,
                                                latLng.second
                                            )
                                            onSetLocationSpoofing(resolvedSpoofCountry, city)
                                            savedStateHandle?.set("mapCountryChanged", true)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.city_coords_unavailable, city),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // Show selected city label, right after the city icon
                    if (
                        hasLocationSpoofAccess &&
                        selectedCity.isNotBlank()
                    ) {
                        Text(
                            selectedCity,
                            color = Color(0xFFFF6F00),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }

                /* 2) Existing location icon */
                if (isPremium) {
                    IconButton(onClick = onLocationPreferencesClick) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = stringResource(R.string.cd_location_settings),
                            tint = Color.White,
                            modifier = Modifier.size(iconSizes.lg)
                        )
                    }
                }
            }
            if (isOnHome) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (isFeedSearchVisible) {
                            postViewModel.hideFeedSearch()
                        } else {
                            postViewModel.showFeedSearchBar()
                        }
                    }) {
                        Icon(
                            imageVector = if (isFeedSearchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isFeedSearchVisible) {
                                stringResource(R.string.cd_close_search)
                            } else {
                                stringResource(R.string.cd_search_feed)
                            },
                            tint = if (isFeedSearchVisible) KupidxOrange else Color.White,
                            modifier = Modifier.size(iconSizes.lg)
                        )
                    }

                    IconButton(
                        onClick = { navController.navigate("create_post") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_create_post),
                            tint = KupidxOrange,
                            modifier = Modifier.size(iconSizes.lg + spacing.xs)
                        )
                    }
                }
            }

            if (isUserSettings || isProfileScreen || isDMScreen) {
                IconButton(onClick = {
                    if (isUserSettings) {
                        navController.popBackStack()
                    } else {
                        navController.navigate("settings")
                    }
                }) {
                    Box(
                        modifier = Modifier
                            .size(iconSizes.lg),
                        contentAlignment = Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "User Settings",
                            tint = if (isUserSettings) Color(0xFFFF6F00) else Color.White,
                            modifier = Modifier.size(iconSizes.lg)
                        )
                    }
                }
            }
            // Saved-Posts Icon (Profile screen only)
            if (isProfileScreen) {
                IconButton(onClick = { navController.navigate("saved_posts") }) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Saved Posts",
                        tint = Color.White,
                        modifier = Modifier.size(iconSizes.lg)
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
                            if (unreadCount > 0) {
                                Badge(
                                    containerColor = Color.Red,
                                    modifier = Modifier.size(iconSizes.sm + spacing.xxs)
                                ) {
                                    Text(
                                        text = unreadCount.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        },
                        modifier = Modifier.size(iconSizes.lg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = if (isNotificationsSelected) Color(0xFFFF6F00) else Color.White,
                            modifier = Modifier.size(iconSizes.lg)
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
    )

    if (showLocationPrefDialog && isPremium) {
        LocationPrivacyDialog(
            allowLocationForMatches = allowLocationForMatches,
            allowLocationPublic = allowLocationPublic,
            isPrivate = isPrivate,
            onDismiss = onLocationDialogDismiss,
            onConfirm = onLocationDialogConfirm,
        )
    }
}

data class BottomNavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun BottomNavigationBar(
    navController: NavController,
    items: List<BottomNavItem>,
    onItemSelected: (route: String, alreadySelected: Boolean) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val spacing = LocalThreadsSpacing.current
    val iconSizes = LocalThreadsIconSizes.current

    NavigationBar(
        containerColor = Color.Black,
        tonalElevation = spacing.lg,
        modifier = Modifier.shadow(spacing.lg)
    ) {
        items.forEach { item ->
            val selected = when (item.route) {
                "dms" -> {
                    currentRoute?.startsWith("dms") == true ||
                            currentRoute?.startsWith("chat/") == true ||
                            currentRoute == "peopleWhoLikedMe" ||
                            currentRoute?.startsWith("matchedUserProfile/") == true || // Add this line
                            (currentRoute?.startsWith("previewUserProfile/") == true &&
                                    navController.previousBackStackEntry?.destination?.route == "peopleWhoLikedMe")
                }
                "profile" -> {
                    currentRoute == "profile" ||
                            currentRoute == "govtIdVerification" ||
                            currentRoute == "saved_posts" ||
                            currentRoute == "editPicAndVoiceBio" ||
                            (currentRoute?.startsWith("previewUserProfile/") == true &&
                                    navController.previousBackStackEntry?.destination?.route == "profile")
                }
                "settings" -> {
                    currentRoute == "settings" ||
                            currentRoute == "manageSubscription" ||
                            currentRoute == "buySwipes" ||
                            currentRoute == "buyCompliments" ||
                            currentRoute == "buyAiMessages" ||
                            currentRoute == "subscription" ||
                            currentRoute == "upgradeLanding" ||
                            currentRoute == "policies"
                }
                else -> {
                    currentDestination?.hierarchy?.any { it.route == item.route } == true
                }
            }

            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(item.route, selected) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) Color(0xFFFF6F00) else Color.White,
                        modifier = Modifier.size(iconSizes.md)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        color = if (selected) Color(0xFFFF6F00) else Color.White,
                        style = MaterialTheme.typography.labelSmall
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