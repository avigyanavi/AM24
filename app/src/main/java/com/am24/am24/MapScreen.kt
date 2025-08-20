@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.am24.am24

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.asFlow
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.firebase.geofire.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.firebase.database.*
import com.google.maps.android.compose.*
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

/* ——— shared helper & model from your project ——— */
import com.am24.am24.searchPlacesRich
import com.am24.am24.PlaceResult
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.res.pluralStringResource



/* ======================================================================================= */
/*  Theme bits                                                                             */
/* ======================================================================================= */

val KupidxOrange = Color(0xFFFF6F00)

/* ======================================================================================= */
/*  Data models (existing)                                                                 */
/* ======================================================================================= */

data class MarkerData(val userId: String, val position: LatLng)

data class MatchProfile(
    val userId: String,
    val name: String,
    val age: Int,
    val hometown: String,
    val photoUrl: String?
)

enum class SortMode { NEARBY, ACTIVE }

enum class Region { LA, SF_BAY, NONE }

enum class GenderFilter { BOTH, WOMEN, MEN }

data class NearbyUser(
    val userId: String,
    val username: String,
    val age: Int,
    val photoUrl: String?,
    val lastActiveAt: Long,
    val latLng: LatLng?,
    val distanceMeters: Double,
    val gender: String,
    val sexualOrientation: String
)

// Leaderboard
data class LeaderboardEntry(
    val placeId: String,
    val placeName: String,
    val checkInCount: Int
)

/* ======================================================================================= */
/*  Place caching (from old code)                                                          */
/* ======================================================================================= */

private val latLngCache = mutableMapOf<String, LatLng?>()
private var hasShownLocationDialogThisSession = false


suspend fun getLatLngFromPlaceId(placeId: String, context: android.content.Context): LatLng? {
    latLngCache[placeId]?.let { return it }
    return try {
        val placesClient = Places.createClient(context)
        val request = FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.LAT_LNG))
        val response = placesClient.fetchPlace(request).await()
        val latLng = response.place.latLng
        latLngCache[placeId] = latLng
        latLng
    } catch (e: Exception) {
        Log.e("MapScreen", "Failed to fetch LatLng for placeId: $placeId", e)
        latLngCache[placeId] = null
        null
    }
}

suspend fun getPlaceNameFromPlaceId(placeId: String, context: android.content.Context): String? =
    try {
        val placesClient = Places.createClient(context)
        val request = FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.NAME))
        val response = placesClient.fetchPlace(request).await()
        response.place.name
    } catch (e: Exception) {
        Log.e("MapScreen", "Failed to fetch name for placeId: $placeId", e)
        null
    }

/* ======================================================================================= */
/*  Units helpers (NEW)                                                                    */
/* ======================================================================================= */
private const val KM_PER_MILE = 1.609344
private const val METERS_PER_MILE = 1609.344

private fun prettyDistance(meters: Double, useMiles: Boolean): String {
    if (!meters.isFinite()) return "—"

    return if (useMiles) {
        val feet = meters * 3.28084
        if (feet < 1000) "${feet.roundToInt()} ft"
        else {
            val mi = meters / METERS_PER_MILE
            val miRounded = (mi * 10).roundToInt() / 10.0
            "$miRounded mi"
        }
    } else {
        if (meters < 1000) "${meters.roundToInt()} m"
        else {
            val km = meters / 1000.0
            val kmRounded = (km * 10).roundToInt() / 10.0
            "$kmRounded km"
        }
    }
}
/* ======================================================================================= */
/*  Main screen                                                                            */
/* ======================================================================================= */

@Composable
fun MapScreen(
    userId: String,
    locationManager: LocationManager,
    geoFireDatabaseRef: DatabaseReference,
    navController: NavController,
    onProfileMarkerClicked: (String) -> Unit,
    currentPrice: String,
    nearbyViewModel: NearbyViewModel,
    radiusKmDefault: Double = 50.0
) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val useMiles = remember { !CountryUtil.usesKilometers(ctx) } // decide unit once
    val activity = LocalContext.current as Activity
    val rewardedSwipeManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedSwipe(activity)) }

    DisposableEffect(Unit) {
        onDispose { rewardedSwipeManager.clearCallbacks() }
    }

    val isLocationGranted =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /* ---------------- People / grid state ---------------- */
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }
    val people = nearbyViewModel.people
    var sortMode by nearbyViewModel::sortMode
    var radiusKm by nearbyViewModel::radiusKm
    var lastActiveHours by nearbyViewModel::lastActiveHours
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: People, 1: Map
    var genderFilter by nearbyViewModel::genderFilter
    var isPlus by nearbyViewModel::isPlus
    var isPremium by nearbyViewModel::isPremium
    var remainingSwipes by remember { mutableStateOf(0) }
    var swipesLoaded by remember { mutableStateOf(false) }
    var showSwipeLimitOverlay by remember { mutableStateOf(false) }
    var isIndian by remember { mutableStateOf(false) }
    val orientationFilter by navController.currentBackStackEntry?.savedStateHandle
        ?.getStateFlow("mapOrientationFilter", "")?.collectAsState()
        ?: remember { mutableStateOf("") }

    LaunchedEffect(selectedTab) {
        navController.currentBackStackEntry?.savedStateHandle?.set("mapSelectedTab", selectedTab)
    }

    LaunchedEffect(Unit) {
        if (!hasShownLocationDialogThisSession) {
            navController.currentBackStackEntry?.savedStateHandle?.set("showLocationPrefDialog", true)
            hasShownLocationDialogThisSession = true
        }
        sortMode = prefs.getString("map_sort_mode", null)?.let { SortMode.valueOf(it) } ?: SortMode.NEARBY
        radiusKm = prefs.getFloat("map_radius_km", radiusKmDefault.toFloat()).toDouble()
        lastActiveHours = prefs.getFloat("map_last_active_hours", 168f).toDouble()
        genderFilter = prefs.getString("map_gender_filter", null)?.let { GenderFilter.valueOf(it) }
            ?: GenderFilter.BOTH
    }

    LaunchedEffect(userId) {
        val snap = FirebaseRefs.db.getReference("users").child(userId).get().await()
        isPlus = snap.child("isPlus").getValue(Boolean::class.java) ?: false
        isPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
        val country = snap.child("country").getValue(String::class.java) ?: ""
        isIndian = country.equals("India", true)
        remainingSwipes = loadAndResetSwipesDaily(userId)
        swipesLoaded = true
        nearbyViewModel.setExcluded(fetchExcludedUsers(userId))
    }

    /* ---------------- Matches (markers, popup) ---------------- */
    val matchMarkers = remember { mutableStateListOf<MarkerData>() }
    val matchUids = remember { mutableStateListOf<String>() }
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }
    var navigateToProfile by remember { mutableStateOf<String?>(null) }
    var isLoadingMatches by remember { mutableStateOf(false) }

    /* ---------------- Map + old features state ---------------- */
    val camera = rememberCameraPositionState()

    // search + tags
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var isLoadingSearch by remember { mutableStateOf(false) }
    var isLoadingQuickSearch by remember { mutableStateOf(false) }
    var loadingTag by remember { mutableStateOf<String?>(null) }
    val searchResults = remember { mutableStateListOf<PlaceResult>() }

    // heatmap / clusters / leaderboard
    val clusters = remember { mutableStateListOf<CheckInCluster>() }
    val heatPoints = remember { mutableStateListOf<LatLng>() }
    val clusterLatLngs = remember { mutableStateMapOf<String, LatLng?>() }
    var showLeaderboard by remember { mutableStateOf(false) }

    // place sheet + send-to-match
    var selectedPlace by remember { mutableStateOf<PlaceResult?>(null) }
    var showSendOverlay by remember { mutableStateOf(false) }
    var placeToSend by remember { mutableStateOf<PlaceResult?>(null) }
    val matchProfiles = remember { mutableStateListOf<MatchProfile>() }

    /* ---------------- Region & curated tags ---------------- */
    var region by remember { mutableStateOf(Region.NONE) }
    val isUserInLA = region == Region.LA
    val isUserInBay = region == Region.SF_BAY

    val baseQuickTags = listOf(
        TagItem(ctx.getString(R.string.tag_cafes), "cafes"),
        TagItem(ctx.getString(R.string.tag_bars), "bars"),
        TagItem(ctx.getString(R.string.tag_malls), "malls"),
        TagItem(ctx.getString(R.string.tag_parks), "parks"),
        TagItem(ctx.getString(R.string.tag_cinemas), "cinemas"),
        TagItem(ctx.getString(R.string.tag_restaurants), "restaurants"),
        TagItem(ctx.getString(R.string.tag_street_food), "street_food"),
        TagItem(ctx.getString(R.string.tag_clubs), "clubs"),
        TagItem(ctx.getString(R.string.tag_bookstores), "bookstores"),
        TagItem(ctx.getString(R.string.tag_hotels), "hotels"),
        TagItem(ctx.getString(R.string.tag_gaming_centers), "gaming_centers"),
        TagItem(ctx.getString(R.string.tag_amusement_parks), "amusement_parks"),
        TagItem(ctx.getString(R.string.tag_beaches), "beaches"),
        TagItem(ctx.getString(R.string.tag_museums), "museums"),
        TagItem(ctx.getString(R.string.tag_national_parks), "national parks"),
        TagItem(ctx.getString(R.string.tag_zoos), "zoos"),
        TagItem(ctx.getString(R.string.tag_libraries), "libraries"),
        TagItem(ctx.getString(R.string.tag_markets), "markets"),
        TagItem(ctx.getString(R.string.tag_art_galleries), "art_galleries"),
        TagItem(ctx.getString(R.string.tag_lakes), "lakes"),
        TagItem(ctx.getString(R.string.tag_sports_centers), "sports_centers"),
        TagItem(ctx.getString(R.string.tag_night_markets), "night_markets"),
        TagItem(ctx.getString(R.string.tag_food_courts), "food_courts")
    )
    val laQuickTags = if (isUserInLA) listOf(
        TagItem(ctx.getString(R.string.la_tag_dtla_rooftops_label), ctx.getString(R.string.la_tag_dtla_rooftops_query)),
        TagItem(ctx.getString(R.string.la_tag_ktown_bbq_label), ctx.getString(R.string.la_tag_ktown_bbq_query)),
        TagItem(ctx.getString(R.string.la_tag_beach_sunset_label), ctx.getString(R.string.la_tag_beach_sunset_query)),
        TagItem(ctx.getString(R.string.la_tag_runyon_hikes_label), ctx.getString(R.string.la_tag_runyon_hikes_query)),
        TagItem(ctx.getString(R.string.la_tag_live_music_label), ctx.getString(R.string.la_tag_live_music_query)),
        TagItem(ctx.getString(R.string.la_tag_brunch_westside_label), ctx.getString(R.string.la_tag_brunch_westside_query)),
        TagItem(ctx.getString(R.string.la_tag_arts_district_label), ctx.getString(R.string.la_tag_arts_district_query)),
        TagItem(ctx.getString(R.string.la_tag_museums_label), ctx.getString(R.string.la_tag_museums_query)),
        TagItem(ctx.getString(R.string.la_tag_malibu_beaches_label), ctx.getString(R.string.la_tag_malibu_beaches_query)),
        TagItem(ctx.getString(R.string.la_tag_dockweiler_bonfire_label), ctx.getString(R.string.la_tag_dockweiler_bonfire_query)),
        TagItem(ctx.getString(R.string.la_tag_surf_rental_label), ctx.getString(R.string.la_tag_surf_rental_query)),
        TagItem(ctx.getString(R.string.la_tag_manhattan_volleyball_label), ctx.getString(R.string.la_tag_manhattan_volleyball_query)),
        TagItem(ctx.getString(R.string.la_tag_speakeasy_label), ctx.getString(R.string.la_tag_speakeasy_query)),
        TagItem(ctx.getString(R.string.la_tag_karaoke_ktown_label), ctx.getString(R.string.la_tag_karaoke_ktown_query)),
        TagItem(ctx.getString(R.string.la_tag_comedy_label), ctx.getString(R.string.la_tag_comedy_query)),
        TagItem(ctx.getString(R.string.la_tag_food_trucks_label), ctx.getString(R.string.la_tag_food_trucks_query))
    ) else emptyList()
    val bayQuickTags = if (isUserInBay) listOf(
        TagItem(ctx.getString(R.string.ba_tag_ocean_beach_sunset_label), ctx.getString(R.string.ba_tag_ocean_beach_sunset_query)),
        TagItem(ctx.getString(R.string.ba_tag_gg_viewpoints_label), ctx.getString(R.string.ba_tag_gg_viewpoints_query)),
        TagItem(ctx.getString(R.string.ba_tag_mission_tacos_label), ctx.getString(R.string.ba_tag_mission_tacos_query)),
        TagItem(ctx.getString(R.string.ba_tag_north_beach_pizza_label), ctx.getString(R.string.ba_tag_north_beach_pizza_query)),
        TagItem(ctx.getString(R.string.ba_tag_soma_coffee_label), ctx.getString(R.string.ba_tag_soma_coffee_query)),
        TagItem(ctx.getString(R.string.ba_tag_marin_hikes_label), ctx.getString(R.string.ba_tag_marin_hikes_query)),
        TagItem(ctx.getString(R.string.ba_tag_napa_wineries_label), ctx.getString(R.string.ba_tag_napa_wineries_query)),
        TagItem(ctx.getString(R.string.ba_tag_berkeley_bookstores_label), ctx.getString(R.string.ba_tag_berkeley_bookstores_query)),
        TagItem(ctx.getString(R.string.ba_tag_paloalto_coffee_label), ctx.getString(R.string.ba_tag_paloalto_coffee_query)),
        TagItem(ctx.getString(R.string.ba_tag_oakland_music_label), ctx.getString(R.string.ba_tag_oakland_music_query)),
        TagItem(ctx.getString(R.string.ba_tag_ferry_market_label), ctx.getString(R.string.ba_tag_ferry_market_query)),
        TagItem(ctx.getString(R.string.ba_tag_sf_museums_label), ctx.getString(R.string.ba_tag_sf_museums_query))
    ) else emptyList()
    val quickTags = laQuickTags + bayQuickTags + baseQuickTags

    /* ---------------- Effects ---------------- */

    // fetch user location + set region
    LaunchedEffect(userId) {
        locationManager.getUserLocationFromGeoFire(userId) { lat, lng ->
            userLatLng = lat?.let { LatLng(it, lng ?: 0.0) }
            userLatLng?.let {
                camera.position = CameraPosition.fromLatLngZoom(it, 15f)
                region = detectRegion(it)
            } ?: run {
                val kol = LatLng(22.5726, 88.3639)
                userLatLng = kol
                region = detectRegion(kol)
                camera.position = CameraPosition.fromLatLngZoom(kol, 14f)
            }
        }
    }

    // load matches and keep markers updated (unchanged logic)
    LaunchedEffect(userId) {
        isLoadingMatches = true
        FirebaseRefs.db.getReference("matches").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    matchUids.clear()
                    snap.children.forEach { it.key?.let(matchUids::add) }
                    loadUserLocationAndMatches(
                        userId, locationManager, geoFireDatabaseRef,
                        matchUids, matchMarkers, camera, ctx
                    )
                    isLoadingMatches = false
                }
                override fun onCancelled(err: DatabaseError) {
                    Toast.makeText(ctx, err.message, Toast.LENGTH_LONG).show()
                    isLoadingMatches = false
                }
            })
    }

    // listen for nearby users (grid)
    LaunchedEffect(userLatLng, radiusKm) {
        val me = userLatLng ?: return@LaunchedEffect
        nearbyViewModel.refreshNearbyUsers(userId, me, geoFireDatabaseRef)
    }

    // react to new excludes coming back from PreviewUserProfile
    LaunchedEffect(navController) {
        navController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>("exclude_uid")
            ?.asFlow()
            ?.collect { uid ->
                nearbyViewModel.addExcluded(uid)
            }
    }

    // heatmap data
    LaunchedEffect(userLatLng) {
        if (userLatLng == null) return@LaunchedEffect
        try {
            val snap = FirebaseRefs.db.getReference("posts")
                .orderByChild("checkIn/placeId")
                .limitToLast(50)
                .get().await()

            clusters.clear()
            heatPoints.clear()
            clusterLatLngs.clear()

            val grouped = mutableMapOf<String, MutableList<String>>()   // placeId → postIds
            val nameCache = mutableMapOf<String, String>()              // placeId → placeName
            val profileCache = mutableMapOf<String, Profile?>()

            snap.children.forEach { postSnap ->
                val postId   = postSnap.key ?: return@forEach
                val authorId = postSnap.child("userId").getValue(String::class.java) ?: return@forEach

                // Load profile once per author to check location visibility toggles
                var profile = profileCache[authorId]
                if (profile == null) {
                    profile = FirebaseRefs.db.getReference("users").child(authorId)
                        .get().await().getValue(Profile::class.java)
                    profileCache[authorId] = profile
                }

                val isMatch = matchUids.contains(authorId)
                val allowed = profile?.let {
                    (isMatch && it.allowLocationForMatches) || (!isMatch && it.allowLocationPublic)
                } ?: false
                if (!allowed) return@forEach
                val ci      = postSnap.child("checkIn")
                val placeId = ci.child("placeId").getValue(String::class.java) ?: return@forEach
                val placeNm = ci.child("name").getValue(String::class.java) ?: ctx.getString(R.string.unknown_place)

                nameCache[placeId] = placeNm
                grouped.getOrPut(placeId) { mutableListOf() }.add(postId)

                val lat = ci.child("lat").getValue(Double::class.java)
                val lng = ci.child("lng").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    val ll = LatLng(lat, lng)
                    clusterLatLngs[placeId] = ll
                    heatPoints += ll
                    return@forEach
                }
            }

            grouped.forEach { (pid, postIds) ->
                val name = nameCache[pid] ?: "Unknown"
                clusters += CheckInCluster(pid, name, postIds)
            }

            val toResolve = grouped.keys.filter { !clusterLatLngs.containsKey(it) }
            if (toResolve.isNotEmpty()) {
                val resolvedPairs = toResolve.map { pid -> async { pid to getLatLngFromPlaceId(pid, ctx) } }.awaitAll()
                resolvedPairs.forEach { (pid, ll) -> ll?.let { clusterLatLngs[pid] = it; heatPoints += it } }
            }
        } catch (e: Exception) {
            Log.e("MapScreen", "Check-in load failed: ${e.message}", e)
        }
    }

    // navigate to preview profile
    LaunchedEffect(navigateToProfile) {
        navigateToProfile?.let {
            navController.navigate("previewUserProfile/$it")
            navigateToProfile = null
            selectedProfile = null
        }
    }

    // filtering + sorting (wrapped in remember)
    val filteredPeople by remember(
        people,
        genderFilter,
        sortMode,
        lastActiveHours,
        orientationFilter,
        isPlus,
        isPremium
    ) {
        derivedStateOf {
            var list = when (genderFilter) {
                GenderFilter.BOTH -> people
                GenderFilter.WOMEN -> people.filter { it.gender.equals("Female", true) }
                GenderFilter.MEN -> people.filter { it.gender.equals("Male", true) }
            }
            if (orientationFilter.isNotBlank() && (isPlus || isPremium)) {
                list = list.filter { it.sexualOrientation.equals(orientationFilter, true) }
            }
            if (sortMode == SortMode.ACTIVE) {
                val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(lastActiveHours.toLong())
                list = list.filter { it.lastActiveAt >= cutoff }
            }
            list
        }
    }

    val sortedPeople by remember(sortMode, filteredPeople) {
        derivedStateOf {
            when (sortMode) {
                SortMode.NEARBY -> filteredPeople.sortedBy { it.distanceMeters }
                SortMode.ACTIVE -> filteredPeople.sortedByDescending { it.lastActiveAt }
            }
        }
    }

    /* ---------------- search helper ---------------- */
    suspend fun runSearch(query: String) {
        if (query.isBlank()) return
        val bias = userLatLng ?: run {
            Toast.makeText(ctx, R.string.error_location_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        val results = searchPlacesRich(query, bias)
        searchResults.apply { clear(); addAll(results) }
        selectedPlace = null
        if (results.isNotEmpty()) {
            val b = LatLngBounds.builder().apply { results.forEach { include(it.latLng) } }
            camera.move(CameraUpdateFactory.newLatLngBounds(b.build(), 80))
        }
    }

    /* ---------------- UI ---------------- */

    Scaffold(
        topBar = {
            if (selectedTab == 0) {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_nearby), fontWeight = FontWeight.SemiBold) },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            if (isPlus || isPremium) {
                                sortMode = if (sortMode == SortMode.NEARBY) SortMode.ACTIVE else SortMode.NEARBY
                                prefs.edit().putString("map_sort_mode", sortMode.name).apply()
                            } else {
                                navController.navigate("paywall")
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = KupidxOrange.copy(alpha = 0.20f),
                            contentColor = KupidxOrange
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = if (sortMode == SortMode.NEARBY) Icons.Default.MyLocation else Icons.Default.Schedule,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                if (sortMode == SortMode.NEARBY) R.string.sort_nearby
                                else R.string.sort_last_active
                            )
                        )
                    }
                } ,
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
                }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.padding(padding)) {

            // Tabs: People | Map  (orange selected)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = KupidxOrange,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = KupidxOrange
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    selectedContentColor = KupidxOrange,
                    unselectedContentColor = Color.Gray,
                    text = { Text(stringResource(R.string.tab_people)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    selectedContentColor = KupidxOrange,   // ← Map tab in orange when selected
                    unselectedContentColor = Color.Gray,
                    text = { Text(stringResource(R.string.tab_map)) }
                )
            }

            when (selectedTab) {
                /* ======================= PEOPLE TAB (grid + mini map) ======================= */
                0 -> {
                    Box(Modifier.fillMaxSize()) {
                        val refreshState = rememberSwipeRefreshState(nearbyViewModel.isRefreshing)
                        SwipeRefresh(
                            state = refreshState,
                            onRefresh = {
                                userLatLng?.let { nearbyViewModel.refreshNearbyUsers(userId, it, geoFireDatabaseRef) }
                            }
                        ) {
                            PeopleGrid(
                                users = sortedPeople,
                                onClick = {
                                    if (swipesLoaded && remainingSwipes <= 0) {
                                        showSwipeLimitOverlay = true
                                    } else {
                                        navController.navigate("previewUserProfile/${it.userId}")
                                    }
                                },
                                useMiles = useMiles // NEW
                            )
                        }

                        GenderFilterChip(
                            selected = genderFilter,
                            onChange = {
                                genderFilter = it
                                prefs.edit().putString("map_gender_filter", it.name).apply()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .scale(0.9f)
                        )

                        if (sortMode == SortMode.NEARBY) {
                            if (isPlus || isPremium) {
                                RadiusChip(
                                    radiusKm = radiusKm,
                                    onChange = {
                                        radiusKm = it
                                        prefs.edit().putFloat("map_radius_km", it.toFloat()).apply()
                                        userLatLng?.let { center ->
                                            nearbyViewModel.refreshNearbyUsers(userId, center, geoFireDatabaseRef)
                                        }
                                    },
                                    useMiles = useMiles,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .scale(0.9f)
                                )
                            } else {
                                LockedChip(
                                    label = stringResource(R.string.label_radius),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .scale(0.9f)
                                ) { navController.navigate("paywall") }
                            }
                        } else {
                            if (isPlus || isPremium) {
                                LastActiveChip(
                                    hours = lastActiveHours,
                                    onChange = {
                                        lastActiveHours = it
                                        prefs.edit().putFloat("map_last_active_hours", it.toFloat()).apply()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .scale(0.9f)
                                )
                            } else {
                                LockedChip(
                                    label = stringResource(R.string.sort_last_active),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .scale(0.9f)
                                ) { }
                            }
                        }
                    }
                }

                /* ======================= MAP TAB (old map restored) ======================= */
                1 -> {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {

                            // SEARCH + TAG ROW
                            val listState = rememberLazyListState()
                            LazyRow(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    if (showSearchBar) {
                                        Box(
                                            Modifier
                                                .height(36.dp)
                                                .background(Color.White, RoundedCornerShape(18.dp))
                                                .padding(
                                                    start = 12.dp,
                                                    end = 40.dp,
                                                    top = 8.dp,
                                                    bottom = 8.dp
                                                )
                                        ) {
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    Color.Black,
                                                    fontSize = 14.sp
                                                ),
                                                keyboardOptions = KeyboardOptions.Default.copy(
                                                    imeAction = ImeAction.Search
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onSearch = {
                                                        scope.launch {
                                                            isLoadingSearch = true
                                                            runSearch(searchQuery)
                                                            isLoadingSearch = false
                                                        }
                                                    },
                                                ),
                                                cursorBrush = SolidColor(KupidxOrange)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (searchQuery.isNotBlank()) {
                                                    scope.launch {
                                                        isLoadingSearch = true
                                                        runSearch(searchQuery)
                                                        isLoadingSearch = false
                                                    }
                                                } else {
                                                    showSearchBar = false
                                                    focusManager.clearFocus()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .offset((-36).dp)
                                        ) {
                                            Icon(
                                                if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.Close,
                                                contentDescription = null,
                                                tint = if (searchQuery.isNotBlank()) KupidxOrange else Color.Gray
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { showSearchBar = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                items(quickTags) { tag ->
                                    Box(
                                        Modifier
                                            .padding(end = 6.dp)
                                            .background(Color.Black, RoundedCornerShape(4.dp))
                                            .border(
                                                BorderStroke(1.dp, KupidxOrange),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable(enabled = !isLoadingQuickSearch) {
                                                scope.launch {
                                                    loadingTag = tag.label
                                                    isLoadingQuickSearch = true
                                                    searchQuery = tag.query
                                                    runSearch(tag.query)
                                                    isLoadingQuickSearch = false
                                                    loadingTag = null
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        if (isLoadingQuickSearch && tag.label == loadingTag) {
                                            CircularProgressIndicator(
                                                strokeWidth = 1.dp,
                                                modifier = Modifier.size(12.dp),
                                                color = Color.White
                                            )
                                        } else {
                                            Text(
                                                tag.label,
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (isUserInLA) {
                                val laCollections = listOf(
                                    TagItem(
                                        ctx.getString(R.string.la_col_date_westside_label),
                                        ctx.getString(R.string.la_col_date_westside_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.la_col_beach_day_label),
                                        ctx.getString(R.string.la_col_beach_day_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.la_col_studio_city_night_label),
                                        ctx.getString(R.string.la_col_studio_city_night_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.la_col_views_griffith_hollywood_label),
                                        ctx.getString(R.string.la_col_views_griffith_hollywood_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.la_col_weho_label),
                                        ctx.getString(R.string.la_col_weho_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.la_col_little_tokyo_label),
                                        ctx.getString(R.string.la_col_little_tokyo_query)
                                    )
                                )
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(laCollections) { tag ->
                                        Box(
                                            Modifier
                                                .padding(end = 6.dp)
                                                .background(
                                                    Color(0xFF121212),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .border(
                                                    BorderStroke(1.dp, KupidxOrange),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .clickable(enabled = !isLoadingQuickSearch) {
                                                    scope.launch {
                                                        loadingTag = tag.label
                                                        isLoadingQuickSearch = true
                                                        searchQuery = tag.query
                                                        runSearch(tag.query)
                                                        isLoadingQuickSearch = false
                                                        loadingTag = null
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                tag.label,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (isUserInBay) {
                                val baCollections = listOf(
                                    TagItem(
                                        ctx.getString(R.string.ba_col_date_sf_label),
                                        ctx.getString(R.string.ba_col_date_sf_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.ba_col_ocean_beach_evening_label),
                                        ctx.getString(R.string.ba_col_ocean_beach_evening_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.ba_col_south_bay_night_label),
                                        ctx.getString(R.string.ba_col_south_bay_night_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.ba_col_wine_day_napa_label),
                                        ctx.getString(R.string.ba_col_wine_day_napa_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.ba_col_berkeley_vintage_label),
                                        ctx.getString(R.string.ba_col_berkeley_vintage_query)
                                    ),
                                    TagItem(
                                        ctx.getString(R.string.ba_col_marin_headlands_label),
                                        ctx.getString(R.string.ba_col_marin_headlands_query)
                                    )
                                )
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(baCollections) { tag ->
                                        Box(
                                            Modifier
                                                .padding(end = 6.dp)
                                                .background(
                                                    Color(0xFF121212),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .border(
                                                    BorderStroke(1.dp, KupidxOrange),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .clickable(enabled = !isLoadingQuickSearch) {
                                                    scope.launch {
                                                        loadingTag = tag.label
                                                        isLoadingQuickSearch = true
                                                        searchQuery = tag.query
                                                        runSearch(tag.query)
                                                        isLoadingQuickSearch = false
                                                        loadingTag = null
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                tag.label,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // ---------- MAP BOX ----------
                            Box(Modifier.weight(1f)) {
                                val heatProvider = remember(heatPoints.size) {
                                    if (heatPoints.isNotEmpty()) {
                                        HeatmapTileProvider.Builder()
                                            .data(heatPoints)
                                            .radius(40)
                                            .opacity(0.65)
                                            .build()
                                    } else null
                                }
                                val heatState = rememberTileOverlayState()

                                GoogleMap(
                                    cameraPositionState = camera,
                                    modifier = Modifier.fillMaxSize(),
                                    properties = MapProperties(
                                        isMyLocationEnabled = isLocationGranted,
                                        isTrafficEnabled = isUserInLA || isUserInBay
                                    ),
                                    uiSettings = MapUiSettings(
                                        zoomControlsEnabled = true,
                                        myLocationButtonEnabled = isLocationGranted,
                                        mapToolbarEnabled = false
                                    )
                                ) {
                                    // People markers (neutral)
                                    sortedPeople.forEach { u ->
                                        u.latLng?.let { ll ->
                                            Marker(
                                                state = MarkerState(ll),
                                                title = u.username
                                            )
                                        }
                                    }

                                    // Match markers (blue) — click shows profile popup
                                    matchMarkers.forEach { m ->
                                        Marker(
                                            state = MarkerState(m.position),
                                            title = m.userId,
                                            icon = BitmapDescriptorFactory.defaultMarker(
                                                BitmapDescriptorFactory.HUE_BLUE
                                            ),
                                            onClick = {
                                                FirebaseRefs.db.getReference("users")
                                                    .child(m.userId)
                                                    .get().addOnSuccessListener { snap ->
                                                        snap.getValue(Profile::class.java)
                                                            ?.let { p ->
                                                                if (matchUids.contains(p.userId) && p.allowLocationForMatches) {
                                                                    selectedProfile = p
                                                                }
                                                            }
                                                    }
                                                true
                                            }
                                        )
                                    }

                                    // Search results markers
                                    searchResults.forEach { p ->
                                        Marker(
                                            state = MarkerState(p.latLng),
                                            title = p.name,
                                            onClick = { selectedPlace = p; true }
                                        )
                                    }

                                    // Heatmap overlay
                                    heatProvider?.let {
                                        TileOverlay(
                                            tileProvider = it,
                                            state = heatState
                                        )
                                    }

                                    // Invisible cluster pins (tap → feed)
                                    clusters.forEach { cluster ->
                                        clusterLatLngs[cluster.placeId]?.let { latLng ->
                                            Marker(
                                                state = MarkerState(latLng),
                                                icon = BitmapDescriptorFactory.defaultMarker(
                                                    BitmapDescriptorFactory.HUE_RED
                                                ),
                                                alpha = 0f,
                                                onClick = {
                                                    navController.navigate("checkinFeed/${cluster.placeId}")
                                                    true
                                                }
                                            )
                                        }
                                    }
                                }

                                // Directional arrows toward matches
                                userLatLng?.let { me ->
                                    DirectionalArrowsOverlay(
                                        userLocation = me,
                                        matchLocations = matchMarkers.map { it.position },
                                        modifier = Modifier.fillMaxSize()
                                    ) { loc ->
                                        scope.launch {
                                            camera.animate(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    loc,
                                                    18f
                                                ), 500
                                            )
                                        }
                                    }
                                }

                                // Place details popup
                                selectedPlace?.let { place ->
                                    PlaceDetailsPopup(
                                        placeId = place.placeId,
                                        name = place.name,
                                        onDismiss = { selectedPlace = null },
                                        onSendToMatch = {
                                            placeToSend = place
                                            showSendOverlay = true
                                        }
                                    )
                                }

                                // Profile popup (from match marker)
                                selectedProfile?.let { prof ->
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = .3f)),
                                        Alignment.Center
                                    ) {
                                        UserProfilePopup(
                                            profile = prof,
                                            onProfileClick = { navigateToProfile = it },
                                            onCloseClick = { selectedProfile = null }
                                        )
                                    }
                                }
                            }
                        }

                        // Leaderboard FAB
                        FloatingActionButton(
                            onClick = { showLeaderboard = true },
                            containerColor = KupidxOrange,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 32.dp)
                        ) { Icon(Icons.Outlined.Leaderboard, contentDescription = "Leaderboard") }

                        // Region recenter FABs
                        when {
                            isUserInLA -> {
                                FloatingActionButton(
                                    onClick = {
                                        scope.launch {
                                            camera.animate(
                                                CameraUpdateFactory.newLatLngBounds(
                                                    GREATER_LA_BOUNDS,
                                                    80
                                                )
                                            )
                                        }
                                    },
                                    containerColor = Color.Black,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 96.dp, bottom = 32.dp)
                                ) {
                                    Text(
                                        ctx.getString(R.string.la_fab_recenter_text),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            isUserInBay -> {
                                FloatingActionButton(
                                    onClick = {
                                        scope.launch {
                                            camera.animate(
                                                CameraUpdateFactory.newLatLngBounds(
                                                    SF_BAY_BOUNDS,
                                                    80
                                                )
                                            )
                                        }
                                    },
                                    containerColor = Color.Black,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 96.dp, bottom = 32.dp)
                                ) {
                                    Text(
                                        ctx.getString(R.string.ba_fab_recenter_text),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Leaderboard overlay
                        if (showLeaderboard) {
                            val leaderboardEntries = remember(clusters) {
                                clusters.sortedByDescending { it.postIds.size }
                                    .map {
                                        LeaderboardEntry(
                                            it.placeId,
                                            it.placeName,
                                            it.postIds.size
                                        )
                                    }
                            }
                            LeaderboardOverlay(
                                entries = leaderboardEntries,
                                onDismiss = { showLeaderboard = false },
                                onEntryClick = { entry ->
                                    showLeaderboard = false
                                    clusterLatLngs[entry.placeId]?.let { ll ->
                                        scope.launch {
                                            camera.animate(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    ll,
                                                    18f
                                                )
                                            )
                                        }
                                    }
                                    navController.navigate("checkinFeed/${entry.placeId}")
                                }
                            )
                        }
                    }
                }
            }
        }

        // global loading overlay
        if (isLoadingMatches || isLoadingSearch || isLoadingQuickSearch) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .3f)),
                Alignment.Center
            ) { CircularProgressIndicator(color = Color.White) }
        }
        if (showSwipeLimitOverlay) {
            SwipeLimitOverlay(
                remainingSwipes = remainingSwipes,
                isPlus = isPlus,
                isPremium = isPremium,
                isIndian = isIndian,
                onWatchAd = {
                    if (isIndian) {
                        navController.navigate("buySwipes")
                        showSwipeLimitOverlay = false
                    } else {
                        rewardedSwipeManager.showWithDailyLimit(
                            userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@SwipeLimitOverlay,
                            onReward = {
                                remainingSwipes += 5
                                updateSwipesInFirebase(remainingSwipes)
                            },
                            afterAd = { showSwipeLimitOverlay = false }
                        )
                    }
                }
            )
        }
    }

    // send-to-match overlay (outside of Tab when shown)
    if (showSendOverlay) {
        LaunchedEffect(Unit) {
            if (matchProfiles.isEmpty()) {
                matchUids.forEach { uid ->
                    FirebaseRefs.db.getReference("users").child(uid).get()
                        .addOnSuccessListener { snap ->
                            snap.getValue(Profile::class.java)?.let { p ->
                                matchProfiles += MatchProfile(
                                    userId = uid,
                                    name = p.name,
                                    age = calculateAge(p.dob),
                                    hometown = p.hometown,
                                    photoUrl = p.profilepicUrl
                                )
                            }
                        }
                }
            }
        }
        MatchesListOverlay(
            matches = matchProfiles,
            onDismiss = { showSendOverlay = false },
            onSend = { match ->
                placeToSend?.let { pl ->
                    val msg = "Check out this place: ${pl.name}. Directions: https://maps.google.com/?q=place_id:${pl.placeId}"
                    val chatId = getChatId2(userId, match.userId)
                    val ref = FirebaseRefs.db.getReference("messages/$chatId")
                    sendMessage2(userId, match.userId, chatId, msg, ref)
                    Toast.makeText(ctx, "Sent to ${match.name}", Toast.LENGTH_SHORT).show()
                    showSendOverlay = false
                    placeToSend = null
                }
            }
        )
    }
}

/* ======================================================================================= */
/*  People grid + cards                                                                    */
/* ======================================================================================= */

@Composable
private fun PeopleGrid(
    users: List<NearbyUser>,
    onClick: (NearbyUser) -> Unit,
    useMiles: Boolean
) {
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_one_nearby_yet), color = Color.Gray)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(users, key = { it.userId }) { u ->
            NearbyCard(user = u, onClick = { onClick(u) }, useMiles = useMiles)
        }
    }
}

@Composable
private fun NearbyCard(user: NearbyUser, onClick: () -> Unit, useMiles: Boolean) {
    val placeholder = painterResource(R.drawable.local_placeholder)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = user.photoUrl,
                placeholder = placeholder,
                error = placeholder,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            startY = 200f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${user.username} · ${user.age}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    if (System.currentTimeMillis() - user.lastActiveAt < TimeUnit.MINUTES.toMillis(5)) {
                        Box(Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2ECC71)))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(timeAgoShort(user.lastActiveAt))
                        if (user.distanceMeters.isFinite()) {
                            append(" · "); append(prettyDistance(user.distanceMeters, useMiles))
                        }
                    },
                    color = Color(0xFFE0E0E0),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GenderFilterChip(
    selected: GenderFilter,
    onChange: (GenderFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show only Women / Men chips
            listOf(
                GenderFilter.WOMEN to stringResource(R.string.gender_women),
                GenderFilter.MEN   to stringResource(R.string.gender_men)
            ).forEach { (type, label) ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onChange(if (selected == type) GenderFilter.BOTH else type) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun RadiusChip(
    radiusKm: Double,                  // keep km internally for GeoFire
    useMiles: Boolean,
    onChange: (Double) -> Unit,        // expects km
    modifier: Modifier = Modifier
) {
    val minKm = 1.0
    val maxKm = 50.0

    // Slider displays miles when needed but converts back to km for state
    val sliderValue = if (useMiles) (radiusKm / KM_PER_MILE).toFloat() else radiusKm.toFloat()
    val sliderRange = if (useMiles)
        (minKm / KM_PER_MILE).toFloat()..(maxKm / KM_PER_MILE).toFloat()
    else
        minKm.toFloat()..maxKm.toFloat()

    val label = if (useMiles) {
        val mi = (radiusKm / KM_PER_MILE)
        "${(mi * 10).roundToInt() / 10.0} mi"
    } else {
        "${radiusKm.roundToInt()} km"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Radar, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label)
            Spacer(Modifier.width(6.dp))
            Slider(
                value = sliderValue,
                onValueChange = {
                    val newKm = if (useMiles) it.toDouble() * KM_PER_MILE else it.toDouble()
                    onChange(newKm.coerceIn(minKm, maxKm))
                },
                valueRange = sliderRange,
                modifier = Modifier.width(100.dp)
            )
        }
    }
}

@Composable
private fun LastActiveChip(
    hours: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minHours = 24.0
    val maxHours = 24.0 * 30
    val days = (hours / 24).roundToInt()
    val label = if (days >= 30) "1 m" else "${days} d"

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label)
            Spacer(Modifier.width(6.dp))
            Slider(
                value = hours.toFloat(),
                onValueChange = { onChange(it.toDouble().coerceIn(minHours, maxHours)) },
                valueRange = minHours.toFloat()..maxHours.toFloat(),
                modifier = Modifier.width(100.dp)
            )
        }
    }
}

@Composable
private fun LockedChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

/* ======================================================================================= */
/*  Overlays & sheets (unchanged from old)                                                 */
/* ======================================================================================= */

@Composable
fun PlaceDetailsPopup(
    placeId: String,
    name: String? = null,
    onDismiss: () -> Unit,
    onSendToMatch: () -> Unit
) {
    val ctx = LocalContext.current
    var placeName by remember { mutableStateOf(name) }

    LaunchedEffect(placeId) {
        if (name == null) {
            placeName = getPlaceNameFromPlaceId(placeId, ctx) ?: ctx.getString(R.string.unknown_place)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }) { },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.End) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDismiss() })
        }
        Text(placeName ?: stringResource(R.string.loading_ellipsis), color = KupidxOrange)
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = {
                val gmm = Uri.parse("google.navigation:q=place_id:$placeId")
                val intent = Intent(Intent.ACTION_VIEW, gmm).apply {
                    setPackage("com.google.android.apps.maps")
                    // addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // uncomment if ctx might not be an Activity
                }
                try {
                    ctx.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: open without forcing the Maps package
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, gmm))
                }
            }) { Text(stringResource(R.string.btn_directions)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSendToMatch) { Text(stringResource(R.string.btn_send_to_match)) }
        }
    }
}

@Composable
fun MatchesListOverlay(
    matches: List<MatchProfile>,
    onDismiss: () -> Unit,
    onSend: (MatchProfile) -> Unit
) {
    var selectedMatch by remember { mutableStateOf<MatchProfile?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.dialog_select_match_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            LazyRow {
                items(matches) { match ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable { selectedMatch = match },
                        border = if (selectedMatch?.userId == match.userId) BorderStroke(2.dp, Color.Green) else null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier
                            .padding(8.dp)
                            .width(200.dp), verticalAlignment = Alignment.CenterVertically) {
                            match.photoUrl?.let { url ->
                                Image(
                                    painter = rememberAsyncImagePainter(model = url),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(match.name, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.match_age, match.age))
                                Text(stringResource(R.string.match_from, match.hometown))

                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (selectedMatch != null) {
                Button(onClick = { onSend(selectedMatch!!) }) { Text(stringResource(R.string.btn_send)) }
            }
        }
    }
}

@Composable
fun LeaderboardOverlay(
    entries: List<LeaderboardEntry>,
    onDismiss: () -> Unit,
    onEntryClick: (LeaderboardEntry) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.6f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.leaderboard_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Divider()
            LazyColumn(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                itemsIndexed(entries) { index, entry ->
                    LeaderboardRow(rank = index + 1, entry = entry, onClick = { onEntryClick(entry) })
                    Divider()
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$rank.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.placeName, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = KupidxOrange, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text("${entry.checkInCount} posts", fontSize = 12.sp, color = Color.Gray)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.cd_go_to_feed),
            tint = Color.Gray
        )
    }
}

/* ======================================================================================= */
/*  Helpers & math                                                                         */
/* ======================================================================================= */

private data class CheckInCluster(val placeId: String, val placeName: String, val postIds: List<String>)
private data class TagItem(val label: String, val query: String)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DirectionalArrowsOverlay(
    userLocation: LatLng,
    matchLocations: List<LatLng>,
    modifier: Modifier = Modifier,
    onArrowClick: (LatLng) -> Unit
) {
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = (min(w, h) / 2f) - with(LocalDensity.current) { 40.dp.toPx() }
        matchLocations.forEach { loc ->
            val bearing = getBearing(userLocation, loc)
            val rad = Math.toRadians(bearing.toDouble())
            val x = cx + radius * cos(rad).toFloat()
            val y = cy - radius * sin(rad).toFloat()
            Icon(
                Icons.Default.ArrowUpward, contentDescription = null, tint = Color.Red,
                modifier = Modifier
                    .size(40.dp)
                    .offset { IntOffset((x - 20).toInt(), (y - 20).toInt()) }
                    .graphicsLayer(rotationZ = bearing)
                    .clickable { onArrowClick(loc) }
            )
        }
    }
}

private fun getBearing(from: LatLng, to: LatLng): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lon1 = Math.toRadians(from.longitude)
    val lat2 = Math.toRadians(to.latitude)
    val lon2 = Math.toRadians(to.longitude)
    val dLon = lon2 - lon1
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}

private fun timeAgoShort(ts: Long): String {
    if (ts <= 0) return "—"
    val diff = System.currentTimeMillis() - ts
    val m = TimeUnit.MILLISECONDS.toMinutes(diff)
    val h = TimeUnit.MILLISECONDS.toHours(diff)
    val d = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "now"
        m < 60 -> "$m min"
        h < 24 -> "$h hr"
        d < 7 -> "$d d"
        else -> "${d / 7} wk"
    }
}

private fun distanceMeters(a: LatLng, b: LatLng): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val sinDLat = kotlin.math.sin(dLat / 2)
    val sinDLon = kotlin.math.sin(dLon / 2)
    val h = sinDLat * sinDLat + kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinDLon * sinDLon
    return 2 * R * kotlin.math.asin(kotlin.math.min(1.0, kotlin.math.sqrt(h)))
}

fun calculateAge(dob: String): Int {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val birthDate = try { sdf.parse(dob) } catch (_: Exception) { return 0 }
    val birthCalendar = Calendar.getInstance().apply { time = birthDate }
    val today = Calendar.getInstance()
    var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) age--
    return age
}

/* ======================================================================================= */
/*  Firebase wiring                                                                        */
/* ======================================================================================= */

//private fun upsert(list: MutableList<NearbyUser>, item: NearbyUser) {
//    val idx = list.indexOfFirst { it.userId == item.userId }
//    if (idx >= 0) list[idx] = item else list.add(item)
//}
//
//private fun observeNearbyUsers(
//    currentUserId: String,
//    center: LatLng,
//    radiusKm: Double,
//    geoFireDatabaseRef: DatabaseReference,
//    onEnterOrMove: (NearbyUser) -> Unit,
//    onExit: (String) -> Unit
//) {
//    val geoFire = GeoFire(geoFireDatabaseRef)
//    val query: GeoQuery = geoFire.queryAtLocation(GeoLocation(center.latitude, center.longitude), radiusKm)
//
//    fun buildUser(uid: String, loc: GeoLocation?) {
//        val usersRef = FirebaseRefs.db.getReference("users").child(uid)
//        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                val p = snapshot.getValue(Profile::class.java) ?: return
//                if (uid == currentUserId) return
//
//                val username = p.username.ifBlank { p.name }
//                val age = calculateAge(p.dob)
//                val lastActive = snapshot.child("lastActive").getValue(Long::class.java) ?: p.lastActive
//                val latLng = if (loc != null) LatLng(loc.latitude, loc.longitude) else null
//                val distM = if (latLng != null) distanceMeters(center, latLng) else Double.POSITIVE_INFINITY
//
//                onEnterOrMove(
//                    NearbyUser(
//                        userId = uid,
//                        username = username,
//                        age = age,
//                        photoUrl = p.profilepicUrl,
//                        lastActiveAt = lastActive,
//                        latLng = latLng,
//                        distanceMeters = distM,
//                        gender = p.gender,
//                        sexualOrientation = p.sexualOrientation
//                    )
//                )
//            }
//            override fun onCancelled(error: DatabaseError) {
//                Log.e("MapScreenV2", "User fetch cancelled $uid: ${error.message}")
//            }
//        })
//    }
//
//    query.addGeoQueryEventListener(object : GeoQueryEventListener {
//        override fun onKeyEntered(key: String, location: GeoLocation) = buildUser(key, location)
//        override fun onKeyExited(key: String) = onExit(key)
//        override fun onKeyMoved(key: String, location: GeoLocation) = buildUser(key, location)
//        override fun onGeoQueryReady() {}
//        override fun onGeoQueryError(error: DatabaseError) { Log.e("MapScreenV2", "GeoQuery error: ${error.message}") }
//    })
//}

/* ==== existing helpers from old file ==== */

fun loadUserLocationAndMatches(
    userId: String,
    locationManager: LocationManager,
    geoFireDatabaseRef: DatabaseReference,
    matchesSet: List<String>,
    markersState: MutableList<MarkerData>,
    cameraPositionState: CameraPositionState,
    context: android.content.Context
) {
    locationManager.getUserLocationFromGeoFire(userId) { lat, lng ->
        if (lat != null && lng != null) {
            val userLatLng = LatLng(lat, lng)
            cameraPositionState.position = CameraPosition.fromLatLngZoom(userLatLng, 18f)
            val geoFire = GeoFire(geoFireDatabaseRef)
            val query: GeoQuery = geoFire.queryAtLocation(GeoLocation(lat, lng), 10.0)
            query.addGeoQueryEventListener(object : GeoQueryEventListener {
                override fun onKeyEntered(key: String, location: GeoLocation) {
                    FirebaseRefs.db.getReference("users").child(key).get().addOnSuccessListener { snapshot ->
                        val profile = snapshot.getValue(Profile::class.java)
                        if (profile != null && matchesSet.contains(key) && profile.allowLocationForMatches) {
                            markersState.add(MarkerData(key, LatLng(location.latitude, location.longitude)))
                        }
                    }
                }
                override fun onKeyExited(key: String) { markersState.removeAll { it.userId == key } }
                override fun onKeyMoved(key: String, location: GeoLocation) {
                    if (matchesSet.contains(key)) {
                        markersState.replaceAll {
                            if (it.userId == key) it.copy(position = LatLng(location.latitude, location.longitude)) else it
                        }
                    }
                }
                override fun onGeoQueryReady() {}
                override fun onGeoQueryError(error: DatabaseError) {
                    Toast.makeText(context, "GeoQuery error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(22.5726, 88.3639), 16f)
        }
    }
}

fun getChatId2(userId1: String, userId2: String): String =
    if (userId1 < userId2) "${userId1}_$userId2" else "${userId2}_$userId1"

fun sendMessage2(
    currentUserId: String,
    otherUserId: String,
    chatId: String,
    messageText: String,
    messagesRef: DatabaseReference
) {
    val messageId = messagesRef.push().key ?: return
    val message = Message(
        id = messageId,
        senderId = currentUserId,
        receiverId = otherUserId,
        text = messageText,
        timestamp = System.currentTimeMillis(),
        read = false,
        processed = false
    )
    messagesRef.child(messageId).setValue(message)
}

/* ======================================================================================= */
/*  Region detection & bounds (from old)                                                   */
/* ======================================================================================= */

private val LA_CITY_HALL = LatLng(34.0536909, -118.242766)
private val SF_CITY_HALL = LatLng(37.7793, -122.4193)

private val GREATER_LA_BOUNDS = LatLngBounds(
    LatLng(33.35, -119.10), // SW
    LatLng(34.65, -117.35)  // NE
)

private val SF_BAY_BOUNDS = LatLngBounds(
    LatLng(36.80, -123.20), // SW
    LatLng(38.30, -121.50)  // NE
)

fun detectRegion(latLng: LatLng?): Region {
    latLng ?: return Region.NONE
    return when {
        GREATER_LA_BOUNDS.contains(latLng) || distanceMeters(latLng, LA_CITY_HALL) <= 100_000.0 -> Region.LA
        SF_BAY_BOUNDS.contains(latLng) || distanceMeters(latLng, SF_CITY_HALL) <= 80_000.0 -> Region.SF_BAY
        else -> Region.NONE
    }
}

@Composable
fun UserProfilePopup(
    profile: Profile,
    onProfileClick: (String) -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .width(260.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onCloseClick() }
            )
        }
        val placeholder = painterResource(R.drawable.local_placeholder)
        val url = profile.profilepicUrl
        AsyncImage(
            model = url.takeIf { !it.isNullOrBlank() },
            contentDescription = null,
            placeholder = placeholder,
            error = placeholder,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(12.dp))
        val displayName = profile.name.ifBlank { profile.username }
        Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = KupidxOrange)
        Spacer(Modifier.height(6.dp))
        RatingBar2(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onProfileClick(profile.userId) }) {     Text(stringResource(R.string.btn_view_full_profile))
        }
    }
}

@Composable
fun RatingBar2(rating: Double, ratingCount: Int) {
    val starSize = 25.dp
    val fullStars = floor(rating).toInt()
    val fraction = rating - fullStars
    val orange = Color(0xFFFF6F00)
    val backgroundColor = Color.White
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(fullStars) {
            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = orange, modifier = Modifier.size(starSize))
        }
        if (fraction > 0) {
            Box(modifier = Modifier.size(starSize)) {
                Icon(imageVector = Icons.Default.StarBorder, contentDescription = null, tint = orange, modifier = Modifier.fillMaxSize())
                Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = orange, modifier = Modifier.fillMaxSize())
                val fractionUnfilled = 1 - fraction
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(starSize * fractionUnfilled.toFloat())
                        .align(Alignment.CenterEnd)
                        .background(backgroundColor)
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(text = String.format("%.2f (%d)", rating, ratingCount), color = KupidxOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
