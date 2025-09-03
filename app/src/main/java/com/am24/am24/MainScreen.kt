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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.RssFeed
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch


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

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val priceAll = stringResource(id = R.string.price_all)

    // ➋ only show the global Top/Bottom bars if NOT on leaderboard
    val showGlobalBars = currentRoute?.startsWith("chat/") == false &&
            currentRoute != "leaderboard"
    val showTopBar    = showGlobalBars
    val priceTier = rememberSaveable { mutableStateOf(priceAll) }

    val profileViewModel: ProfileViewModel = viewModel()
    // ─── collect both flags ───────────────────────────
    val isPremium by profileViewModel.isPremium.collectAsState(initial = false)
    val isPlus    by profileViewModel.isPlus   .collectAsState(initial = false)
    // ───────────────────────────────────────────────────

    // 🔸 NEW – one manager for the whole screen
    val context = LocalContext.current as Activity
    val interstitial = remember(isPremium, isPlus) {
        if (!isPremium && !isPlus) {
            InterstitialAdManager(
                context,
                AdUnitIds.interstitial(context)
            )
        } else null
    }

    // Listen for incoming omegle invites
    var omegleInvite by remember { mutableStateOf<OmegleMatch?>(null) }
    DisposableEffect(currentUserId) {
        val ref = FirebaseDatabase.getInstance().reference
            .child("omegleInvites").child(currentUserId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val first = snapshot.children.firstOrNull()
                if (first != null) {
                    val chatId = first.key ?: return
                    val otherUid = first.getValue(String::class.java) ?: return
                    omegleInvite = OmegleMatch(chatId, otherUid)
                } else {
                    omegleInvite = null
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopNavBar(
                    navController = navController,
                    profileViewModel = profileViewModel,
                    currentUserId = currentUserId,
                    postViewModel = postViewModel,
                    onPriceChange = { priceTier.value = it },
                    onLogout = onLogout,
                    isPremium = isPremium,
                    isPlus = isPlus,
                    locationManager = locationManager
                )
            }
        },
        bottomBar = {
            if (showGlobalBars) {
                BottomNavigationBar(
                    navController = navController,
                    items         = items,
                    interstitial  = interstitial,
                    isPremium     = isPremium,
                    isPlus        = isPlus
                )
            }
        }
    ) { innerPadding ->
        val paddingValues = if (showGlobalBars) innerPadding else PaddingValues(0.dp)
        MainNavGraph(
            navController = navController,
            modifier = Modifier.padding(paddingValues),
            postViewModel = postViewModel,
            currentPrice = priceTier.value,
            locationManager = locationManager
        )
        if (omegleInvite != null) {
            AlertDialog(
                onDismissRequest = { /* keep dialog until user acts */ },
                text = { Text("Join random chat?") },
                confirmButton = {
                    TextButton(onClick = {
                        val match = omegleInvite!!
                        FirebaseDatabase.getInstance().reference
                            .child("omegleInvites").child(currentUserId)
                            .child(match.chatId).removeValue()
                        navController.navigate("omegleChat/${match.chatId}/${match.otherUserId}")
                        omegleInvite = null
                    }) { Text("Join") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        omegleInvite?.let { match ->
                            FirebaseDatabase.getInstance().reference
                                .child("omegleInvites").child(currentUserId)
                                .child(match.chatId).removeValue()
                        }
                        omegleInvite = null
                    }) { Text("Ignore") }
                }
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
    onPriceChange: (String) -> Unit = {},
    isPremium: Boolean,
    isPlus: Boolean,
    locationManager: LocationManager
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val isNotificationsSelected = currentRoute == "notifications"
    val isUserSettings = currentRoute == "settings"
    val isProfileScreen = currentRoute == "profile"
    val isDMScreen = currentRoute == "dms"

    val unreadCount = remember { mutableStateOf(0) }
    var showLocationPrefDialog by remember { mutableStateOf(false) }
    var allowLocationForMatches by remember { mutableStateOf(false) }
    var allowLocationPublic by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(false) }

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val mapSelectedTab by savedStateHandle?.getStateFlow("mapSelectedTab", 0)?.collectAsState()
        ?: remember { mutableStateOf(0) }
    val triggerLocationDialog by savedStateHandle?.getStateFlow("showLocationPrefDialog", false)
        ?.collectAsState() ?: remember { mutableStateOf(false) }
    val orientationFilter by savedStateHandle?.getStateFlow(
        "mapOrientationFilter",
        LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("map_orientation_filter", "") ?: ""
    )?.collectAsState() ?: remember { mutableStateOf("") }

    LaunchedEffect(triggerLocationDialog) {
        if (triggerLocationDialog) {
            showLocationPrefDialog = true
            savedStateHandle?.set("showLocationPrefDialog", false)
        }
    }

    /*  ─────────  STATE FOR PRICE FILTER  ────────── */
    val priceAll = stringResource(id = R.string.price_all)
    var priceMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedPriceRange by rememberSaveable { mutableStateOf(priceAll) }
    var orientationMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var selectedCountry by rememberSaveable { mutableStateOf("") }
    var cityMenuExpanded by remember { mutableStateOf(false) }
    var selectedCity by rememberSaveable { mutableStateOf("") }
    val feedFilterState by postViewModel.feedFilters.collectAsState()
    val homeSelectedCountry = feedFilterState.feedFilters.country

    // Fetch premium status from Firebase
    DisposableEffect(currentUserId) {
        val profileRef = FirebaseRefs.db
            .getReference("users")
            .child(currentUserId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Fetch allowLocationForMatches
                val savedPref = snapshot.child("allowLocationForMatches").getValue(Boolean::class.java)
                if (savedPref == null) {
                    profileRef.child("allowLocationForMatches").setValue(false)
                    allowLocationForMatches = false
                } else {
                    allowLocationForMatches = savedPref
                }

                // Fetch allowLocationPublic
                val savedPublicPref = snapshot.child("allowLocationPublic").getValue(Boolean::class.java)
                if (savedPublicPref == null) {
                    profileRef.child("allowLocationPublic").setValue(false)
                    allowLocationPublic = false
                } else {
                    allowLocationPublic = savedPublicPref
                }

                // Fetch isPrivate
                val savedPrivatePref = snapshot.child("isPrivate").getValue(Boolean::class.java)
                if (savedPrivatePref == null) {
                    profileRef.child("isPrivate").setValue(false)
                    isPrivate = false
                } else {
                    isPrivate = savedPrivatePref
                }
                // Track spoofed country
                val spoofed = snapshot.child("isLocationSpoofed").getValue(Boolean::class.java) == true
                val country = snapshot.child("country").getValue(String::class.java) ?: ""
                val city = snapshot.child("city").getValue(String::class.java) ?: ""
                selectedCountry = if (spoofed) country else ""
                selectedCity = if (spoofed) city else ""
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
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        val savedOrientation = prefs.getString("map_orientation_filter", "") ?: ""
        savedStateHandle?.set("mapOrientationFilter", savedOrientation)
    }

    LaunchedEffect(homeSelectedCountry) {
        prefs.edit().putString("home_country_filter", homeSelectedCountry).apply()
    }

    LaunchedEffect(orientationFilter) {
        prefs.edit().putString("map_orientation_filter", orientationFilter).apply()
    }
    // anywhere before TopAppBar:
    val isOnHome = currentDestination
        ?.hierarchy
        ?.any { it.route == "home" } == true
    val isAdmin by profileViewModel.isAdmin.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val ctx = LocalContext.current

    TopAppBar(
        title = {
//            Text(stringResource(R.string.app_name), color = Color(0xFFFF6F00))
        },
        navigationIcon = {
            Box(
                modifier = Modifier.size(40.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.kupidx_logo1),
                    contentDescription = stringResource(R.string.logo_kupidx_desc),
                    modifier = Modifier.size(56.dp)
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

                    IconButton(onClick = {
                        if (currentRoute == "dating") {
                            navController.popBackStack()
                        } else {
                            navController.navigate("dating")
                        }
                    }) {
                    Icon(
                        imageVector = Icons.Default.Swipe,
                        contentDescription = stringResource(R.string.cd_dating_shortcut),
                        tint = if (currentRoute == "dating") Color(0xFFFF6F00) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

            if (isOnHome || isDMScreen) {
                IconButton(onClick = {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.searching),
                        Toast.LENGTH_SHORT
                    ).show()
                    coroutineScope.launch {
                        val match = matchRandomOmegleUser()
                        if (match != null) {
                            navController.navigate("omegleChat/${match.chatId}/${match.otherUserId}")
                        } else {
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.no_users_online),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = stringResource(R.string.cd_omegle)
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
                if (mapSelectedTab == 0) {
                    val orientationOptions = stringArrayResource(R.array.sexual_orientation_options).toList()
                    IconButton(onClick = {
//                        if (isPlus || isPremium) {
                        orientationMenuExpanded = true
//                    }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Wc,
                            contentDescription = stringResource(R.string.sexual_orientation_label),
                            tint = if (orientationFilter.isNotBlank()) Color(0xFFFF6F00) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = orientationMenuExpanded,
                        onDismissRequest = { orientationMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.price_all)) },
                            onClick = {
                                orientationMenuExpanded = false
                                savedStateHandle?.set("mapOrientationFilter", "")
                            }
                        )
                        orientationOptions.forEach { opt ->
                            val code = opt.toOrientationCode()?.name
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        opt,
                                        color = if (code == orientationFilter) Color(0xFFFF6F00) else Color.White
                                    )
                                },
                                onClick = {
                                    orientationMenuExpanded = false
                                    savedStateHandle?.set("mapOrientationFilter", code ?: "")
                                }
                            )
                        }
                    }
                }
                /* 1) Price-Filter icon (new) – shows before the old Location icon */
                if (mapSelectedTab == 1) {
                    /* 1) Price-Filter icon (new) – shows before the old Location icon */
                    IconButton(onClick = { priceMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.cd_price_filter),
                            tint = if (selectedPriceRange != stringResource(R.string.price_all))
                                Color(0xFFFF6F00) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    /* ▼ Dropdown for price tiers */
                    DropdownMenu(
                        expanded = priceMenuExpanded,
                        onDismissRequest = { priceMenuExpanded = false }
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
                                    priceMenuExpanded = false
                                    if (tier != selectedPriceRange) {
                                        selectedPriceRange = tier
                                        onPriceChange(tier)
                                    }
                                }
                            )
                        }
                    }
                }

                if (mapSelectedTab == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { countryMenuExpanded = !countryMenuExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = stringResource(R.string.cd_country_filter),
                                        tint = if (selectedCountry.isNotBlank()) Color(0xFFFF6F00) else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                if (selectedCountry.isNotBlank()) {
                                    Text(selectedCountry, color = Color(0xFFFF6F00))
                                }
                            }
                            DropdownMenu(
                                expanded = countryMenuExpanded,
                                onDismissRequest = { countryMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clear_country_filter)) },
                                    onClick = {
                                        countryMenuExpanded = false
                                        val profileRef = FirebaseRefs.db.getReference("users").child(currentUserId)
                                        profileRef.updateChildren(
                                            mapOf(
                                                "isLocationSpoofed" to false,
                                                "country" to "",
                                            )
                                        )
                                        selectedCountry = ""
                                        selectedCity = ""
                                        locationManager.resumeUpdates()
                                    }
                                )
                                val countryOptions = stringArrayResource(R.array.country_names).toList()
                                countryOptions.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c) },
                                        onClick = {
                                            countryMenuExpanded = false
                                            val profileRef = FirebaseRefs.db.getReference("users").child(currentUserId)
                                            val latLng = CountryLatLngMap.getLatLng(c)
                                            if (latLng != null) {
                                                locationManager.pauseUpdates()
                                                locationManager.setCustomLocation(currentUserId, latLng.first, latLng.second)
                                                profileRef.updateChildren(
                                                    mapOf(
                                                        "country" to c,
                                                        "city" to "",
                                                        "isLocationSpoofed" to true
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.country_coords_unavailable, c),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                profileRef.updateChildren(
                                                    mapOf(
                                                        "country" to c,
                                                        "city" to "",
                                                        "isLocationSpoofed" to false
                                                    )
                                                )
                                            }
                                            selectedCountry = c
                                            selectedCity = ""
                                            savedStateHandle?.set("mapCountryChanged", true)
                                        }
                                    )
                                }
                            }
                        }

                        if (CountryUtil.isMexico(context, selectedCountry.takeIf { it.isNotBlank() })) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { cityMenuExpanded = !cityMenuExpanded }) {
                                        Icon(
                                            imageVector = Icons.Default.LocationCity,
                                            contentDescription = stringResource(R.string.cd_city_filter),
                                            tint = if (selectedCity.isNotBlank()) Color(0xFFFF6F00) else Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    if (selectedCity.isNotBlank()) {
                                        Text(selectedCity, color = Color(0xFFFF6F00))
                                    }
                                }
                                DropdownMenu(
                                    expanded = cityMenuExpanded,
                                    onDismissRequest = { cityMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.clear_city_filter)) },
                                        onClick = {
                                            cityMenuExpanded = false
                                            val profileRef = FirebaseRefs.db.getReference("users").child(currentUserId)
                                            if (selectedCountry.isBlank()) {
                                                profileRef.updateChildren(
                                                    mapOf(
                                                        "city" to "",
                                                        "country" to "Mexico",
                                                        "isLocationSpoofed" to false
                                                    )
                                                )
                                                locationManager.resumeUpdates()
                                            } else {
                                                profileRef.updateChildren(
                                                    mapOf(
                                                        "city" to "",
                                                        "country" to selectedCountry,
                                                        "isLocationSpoofed" to true
                                                    )
                                                )
                                                val countryLatLng = CountryLatLngMap.getLatLng(selectedCountry)
                                                if (countryLatLng != null) {
                                                    locationManager.pauseUpdates()
                                                    locationManager.setCustomLocation(currentUserId, countryLatLng.first, countryLatLng.second)
                                                }
                                            }
                                            selectedCity = ""
                                            savedStateHandle?.set("mapCountryChanged", true)
                                        }
                                    )
                                    val cityOptions = stringArrayResource(R.array.mexico_cities).toList()
                                    cityOptions.forEach { city ->
                                        DropdownMenuItem(
                                            text = { Text(city) },
                                            onClick = {
                                                cityMenuExpanded = false
                                                val profileRef = FirebaseRefs.db.getReference("users").child(currentUserId)
                                                val latLng = MexicoCityLatLngMap.getLatLng(city)
                                                if (latLng != null) {
                                                    locationManager.pauseUpdates()
                                                    locationManager.setCustomLocation(currentUserId, latLng.first, latLng.second)
                                                    profileRef.updateChildren(
                                                        mapOf(
                                                            "country" to "Mexico",
                                                            "city" to city,
                                                            "isLocationSpoofed" to true
                                                        )
                                                    )
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.city_coords_unavailable, city),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                selectedCountry = "Mexico"
                                                selectedCity = city
                                                savedStateHandle?.set("mapCountryChanged", true)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                /* 2) Existing location icon */
                IconButton(onClick = { showLocationPrefDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = stringResource(R.string.cd_location_settings),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (isOnHome) {
                // Create Post with a subtle pulsing animation
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                IconButton(
                    onClick = { navController.navigate("create_post") },
                    modifier = Modifier
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            transformOrigin = TransformOrigin.Center
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_create_post),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
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
                            .size(24.dp),
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
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Saved Posts",
                        tint = Color.White,
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
                            tint = if (isNotificationsSelected) Color(0xFFFF6F00) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
    )

    if (showLocationPrefDialog) {
        LocationPrivacyDialog(
            allowLocationForMatches = allowLocationForMatches,
            allowLocationPublic = allowLocationPublic,
            isPrivate = isPrivate,
            onDismiss = { showLocationPrefDialog = false },
            onConfirm = { matches, public, private ->
                allowLocationForMatches = matches
                allowLocationPublic = public
                isPrivate = private
                val userRef = FirebaseRefs.db.getReference("users").child(currentUserId)
                userRef.child("allowLocationForMatches").setValue(matches)
                userRef.child("allowLocationPublic").setValue(public)
                userRef.child("isPrivate").setValue(private)
                    .addOnFailureListener { e ->
                        Log.e("TopNavBar", "Failed to save preference: ${e.message}")
                    }
                showLocationPrefDialog = false
            },
        )
    }
}

data class BottomNavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun BottomNavigationBar(
    navController: NavController,
    items: List<BottomNavItem>,
    interstitial: InterstitialAdManager?,  // now nullable
    isPremium: Boolean,
    isPlus: Boolean
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    NavigationBar(containerColor = Color.Black) {
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
                onClick = {
                    if (isPremium || isPlus) {
                        // Plus or Premium → go straight
                        navController.navigate(item.route) {
                            launchSingleTop = true
                            restoreState    = true
                        }
                    } else {
                        // Regular → show ad then navigate
                        interstitial?.show {
                            navController.navigate(item.route) {
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
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
                        fontSize = 10.sp
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