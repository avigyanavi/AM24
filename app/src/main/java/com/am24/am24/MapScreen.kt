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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.alpha
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
import androidx.lifecycle.viewmodel.compose.viewModel
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

import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import java.text.Normalizer


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

enum class GenderFilter { BOTH, WOMEN, MEN, OTHER }

data class NearbyUser(
    val userId: String,
    val username: String,
    val age: Int,
    val photoUrl: String?,
    val lastActiveAt: Long,
    val latLng: LatLng?,
    val distanceMeters: Double,
    val gender: String,
    val sexualOrientation: String,
    val interests: List<Interest> = emptyList(),
    val compatibilityPct: Int? = null,
    val randomDetail: String? = null
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
private const val HAS_SHOWN_LOCATION_DIALOG = "has_shown_location_dialog"
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
    nearbyViewModel: NearbyViewModel,
    radiusKmDefault: Double = 100.0
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

    val mapVisibility = remember { mutableStateMapOf<String, Boolean>() } // uid -> allowed on map

    /* ---------------- People / grid state ---------------- */
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }
    val people = nearbyViewModel.people
    var sortMode by nearbyViewModel::sortMode
    var radiusKm by nearbyViewModel::radiusKm
    var lastActiveHours by nearbyViewModel::lastActiveHours
    var selectedTab by rememberSaveable { mutableStateOf(1) } // 0: People, 1: Cards, 2: Map
    var genderFilter by nearbyViewModel::genderFilter
    var datingFilters by nearbyViewModel::datingFilters
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFiltersDialog by remember { mutableStateOf(false) }
    var isPlus by remember { mutableStateOf(false) }
    var isPremium by remember { mutableStateOf(false) }
    var dailyLoginInfo by remember { mutableStateOf<DailyLoginInfo?>(null) }
    var omegleInvite by remember { mutableStateOf<OmegleMatch?>(null) }
    var remainingSwipes by remember { mutableStateOf(0) }
    var swipesLoaded by remember { mutableStateOf(false) }
    var showSwipeLimitOverlay by remember { mutableStateOf(false) }
    var isIndian by remember { mutableStateOf(false) }
    val profileViewModel: ProfileViewModel = viewModel()
    val orientationFilter by navController.currentBackStackEntry?.savedStateHandle
        ?.getStateFlow("mapOrientationFilter", prefs.getString("map_orientation_filter", "") ?: "")?.collectAsState()
        ?: remember { mutableStateOf("") }

    val showAds = !isPremium && !isPlus

    LaunchedEffect(orientationFilter) {
        prefs.edit().putString("map_orientation_filter", orientationFilter).apply()
    }

    LaunchedEffect(selectedTab) {
        navController.currentBackStackEntry?.savedStateHandle?.set("mapSelectedTab", selectedTab)
    }

    LaunchedEffect(isPremium) {
        if (isPremium) {
            val hasShownDialog = prefs.getBoolean(HAS_SHOWN_LOCATION_DIALOG, false)
            if (!hasShownLocationDialogThisSession && !hasShownDialog) {
                navController.currentBackStackEntry?.savedStateHandle?.set("showLocationPrefDialog", true)
                hasShownLocationDialogThisSession = true
                prefs.edit().putBoolean(HAS_SHOWN_LOCATION_DIALOG, true).apply()
            }
        }
        sortMode = prefs.getString("map_sort_mode", null)?.let { SortMode.valueOf(it) } ?: SortMode.NEARBY
        radiusKm = prefs.getFloat("map_radius_km", radiusKmDefault.toFloat()).toDouble()
        lastActiveHours = prefs.getFloat("map_last_active_hours", 168f).toDouble()
        genderFilter = prefs.getString("map_gender_filter", null)
            ?.let { runCatching { GenderFilter.valueOf(it) }.getOrNull() }
            ?: GenderFilter.BOTH
    }
    LaunchedEffect(isPlus || isPremium) {
        if (isPlus || isPremium) {
            prefs.getString("map_dating_filters", null)?.let {
                runCatching { nearbyViewModel.datingFilters = Gson().fromJson(it, DatingFilterSettings::class.java) }
            }
        }
    }

    LaunchedEffect(isPremium) {
        if (!(isPremium) && selectedTab == 2) {
            selectedTab = 0
        }
    }

    LaunchedEffect(userId) {
        val snap = FirebaseRefs.db.getReference("users").child(userId).get().await()
        val profile = snap.getValue(Profile::class.java)
        isPlus = snap.child("isPlus").getValue(Boolean::class.java) ?: false
        isPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
        nearbyViewModel.setTier(isPlus, isPremium)
        nearbyViewModel.setCurrentUserProfile(profile)
        val country = snap.child("country").getValue(String::class.java) ?: ""
        isIndian = canonicalCountry(country) == "India"
        remainingSwipes = loadAndResetSwipesDaily(userId)
        swipesLoaded = true
        nearbyViewModel.setExcluded(fetchExcludedUsers(userId))
    }

    LaunchedEffect(isPremium, isPlus) {
        if (!isPremium && !isPlus) {
            dailyLoginInfo = checkDailyLoginReward(ctx)
        } else {
            dailyLoginInfo = null
        }
    }

    DisposableEffect(userId) {
        val ref = FirebaseDatabase.getInstance().reference
            .child("omegleInvites").child(userId)
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
    LaunchedEffect(userLatLng, radiusKm, isPlus, isPremium) {
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

    // react to country changes from TopNavBar
    LaunchedEffect(navController) {
        navController.currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("mapCountryChanged")
            ?.asFlow()
            ?.collect { changed ->
                if (changed == true) {
                    locationManager.getUserLocationFromGeoFire(userId) { lat, lng ->
                        userLatLng = lat?.let { LatLng(it, lng ?: 0.0) }
                        userLatLng?.let { loc ->
                            nearbyViewModel.refreshNearbyUsers(userId, loc, geoFireDatabaseRef)
                        }
                    }
                    navController.currentBackStackEntry?.savedStateHandle?.set("mapCountryChanged", false)
                }
            }
    }

    // react to global exclude events (likes/compliments/dislikes elsewhere)
    LaunchedEffect(Unit) {
        ExclusionEventBus.events.collect { uid ->
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
        people, genderFilter, sortMode, lastActiveHours, orientationFilter, isPlus, isPremium
    ) {
        derivedStateOf {
            var list = when (genderFilter) {
                GenderFilter.BOTH  -> people
                GenderFilter.WOMEN -> people.filter { it.gender.toGenderCode() == Gender.FEMALE }
                GenderFilter.MEN   -> people.filter { it.gender.toGenderCode() == Gender.MALE }
                GenderFilter.OTHER -> people.filter { it.gender.toGenderCode() == Gender.OTHER }
            }
            if (orientationFilter.isNotBlank()) {
                list = list.filter { it.sexualOrientation.toOrientationCode()?.name == orientationFilter }
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

    /* 👇 Add this debug effect right here */
    LaunchedEffect(people, genderFilter) {
        if (genderFilter != GenderFilter.BOTH) {
            val misses = people.filter {
                val c = canonicalGender(it.gender)
                (genderFilter == GenderFilter.WOMEN && c != "female") ||
                        (genderFilter == GenderFilter.MEN   && c != "male") ||
                        (genderFilter == GenderFilter.OTHER && c != "other")
            }.take(20)
            if (misses.isNotEmpty()) {
                Log.d("GenderDebug", "Unmatched (${misses.size}): " +
                        misses.joinToString { "${it.userId}:${it.gender}" })
            }
        }
    }

    LaunchedEffect(selectedTab, sortedPeople, matchUids) {
        if (selectedTab != 2) return@LaunchedEffect

        val currentIds = sortedPeople.map { it.userId }.toSet()

        // keep only users still present
        mapVisibility.keys.retainAll(currentIds)

        // IMPORTANT: force recompute for current users whenever matches set changes
        // (ensures we re-evaluate allowForMatches vs allowPublic correctly)
        currentIds.forEach { mapVisibility.remove(it) }

        sortedPeople.forEach { u ->
            if (!mapVisibility.containsKey(u.userId)) {
                try {
                    val snap = FirebaseRefs.db.getReference("users")
                        .child(u.userId).get().await()
                    val allowForMatches = snap.child("allowLocationForMatches")
                        .getValue(Boolean::class.java) ?: false
                    val allowPublic = snap.child("allowLocationPublic")
                        .getValue(Boolean::class.java) ?: true
                    val isMatch = matchUids.contains(u.userId)
                    mapVisibility[u.userId] = if (isMatch) (allowForMatches || allowPublic) else allowPublic
                } catch (_: Exception) {
                    mapVisibility[u.userId] = false
                }
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
                TopAppBar(
                    title = {
                        if (sortMode == SortMode.NEARBY) {
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
                                modifier = Modifier.scale(0.9f)
                            )
                        } else {
                            LastActiveChip(
                                hours = lastActiveHours,
                                onChange = {
                                    lastActiveHours = it
                                    prefs.edit().putFloat("map_last_active_hours", it.toFloat()).apply()
                                },
                                modifier = Modifier.scale(0.9f)
                            )
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = {
                                sortMode = if (sortMode == SortMode.NEARBY) SortMode.ACTIVE else SortMode.NEARBY
                                prefs.edit().putString("map_sort_mode", sortMode.name).apply()
                                userLatLng?.let { nearbyViewModel.refreshNearbyUsers(userId, it, geoFireDatabaseRef) }
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
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filters)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showFiltersDialog = true
                                    }
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.padding(padding)) {

            if (!(isPremium) && selectedTab == 2) {
                selectedTab = 0
            }

            // Tabs: People | Cards | Map (Map only for Plus/Premium)
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
                    selectedContentColor = KupidxOrange,
                    unselectedContentColor = Color.Gray,
                    text = { Text(stringResource(R.string.tab_cards)) }
                )
                if (isPremium) {
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        selectedContentColor = KupidxOrange,
                        unselectedContentColor = Color.Gray,
                        text = { Text(stringResource(R.string.tab_map)) }
                    )
                }
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
                                useMiles = useMiles,
                                onRemove = { uid ->
                                    scope.launch {
                                        nearbyViewModel.addExcluded(uid)
                                        FirebaseRefs.db.getReference("users/$userId/permanentExcludes/$uid").setValue(true)
                                    }
                                },
                                showAds = showAds,
                                onBlock = { uid ->
                                    scope.launch {
                                        FirebaseRefs.db.getReference("blocks/$userId/$uid").setValue(true)
                                        nearbyViewModel.addExcluded(uid)
                                    }
                                }
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
                    }
                }

                /* ======================= CARDS TAB ======================= */
                1 -> {
                    Box(Modifier.fillMaxSize()) {
                        CardsList(
                            users = sortedPeople,
                            showAds = showAds,
                            useMiles = useMiles,          // <-- pass through
                            onLike = { user ->
                                if (swipesLoaded && remainingSwipes <= 0) {
                                    showSwipeLimitOverlay = true
                                } else {
                                    handleSwipeRight(userId, user.userId, profileViewModel)
                                    nearbyViewModel.addExcluded(user.userId)
                                    if (swipesLoaded) {
                                        remainingSwipes--
                                        updateSwipesInFirebase(remainingSwipes)
                                    }
                                }
                            },
                            onDislike = { user ->
                                if (swipesLoaded && remainingSwipes <= 0) {
                                    showSwipeLimitOverlay = true
                                } else {
                                    handleSwipeLeft(userId, user.userId)
                                    nearbyViewModel.addExcluded(user.userId)
                                    if (swipesLoaded) {
                                        remainingSwipes--
                                        updateSwipesInFirebase(remainingSwipes)
                                    }
                                }
                            },
                            onCardClick = { user ->
                                if (swipesLoaded && remainingSwipes <= 0) {
                                    showSwipeLimitOverlay = true
                                } else {
                                    navController.navigate("previewUserProfile/${user.userId}")
                                }
                            },
                            onRemove = { uid ->
                                scope.launch {
                                    nearbyViewModel.addExcluded(uid)
                                    FirebaseRefs.db.getReference("users/$userId/permanentExcludes/$uid").setValue(true)
                                }
                            },
                            onBlock = { uid ->
                                scope.launch {
                                    FirebaseRefs.db.getReference("blocks/$userId/$uid").setValue(true)
                                    nearbyViewModel.addExcluded(uid)
                                }
                            }
                    )
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
                    }
                }

                /* ======================= MAP TAB (old map restored) ======================= */
                2 -> {
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
                                    val visibleMapPeople = sortedPeople.filter { u ->
                                        u.latLng != null && (mapVisibility[u.userId] == true)
                                    }
                                    visibleMapPeople.forEach { u ->
                                        val ll = u.latLng!!
                                        Marker(
                                            state = MarkerState(ll),
                                            title = u.username,
                                            onClick = {
                                                onProfileMarkerClicked(u.userId)
                                                true
                                            }
                                        )
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

                        // Controls row: leaderboard, zoom, gender filter
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 8.dp, bottom = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FloatingActionButton(
                                onClick = { showLeaderboard = true },
                                containerColor = KupidxOrange,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Leaderboard,
                                    contentDescription = "Leaderboard",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            FloatingActionButton(
                                onClick = {
                                    val points = sortedPeople.mapNotNull { it.latLng } + listOfNotNull(userLatLng)
                                    if (points.isNotEmpty()) {
                                        val builder = LatLngBounds.builder()
                                        points.forEach { builder.include(it) }
                                        val bounds = try { builder.build() } catch (e: Exception) { null }
                                        bounds?.let {
                                            scope.launch {
                                                camera.animate(
                                                    CameraUpdateFactory.newLatLngBounds(it, 80)
                                                )
                                            }
                                        }
                                    }
                        },
                        containerColor = Color.Black,
                        modifier = Modifier.size(40.dp)
                        ) {
                        Icon(
                            Icons.Default.ZoomOutMap,
                            contentDescription = "Zoom to results",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                        GenderFilterChip(
                            selected = genderFilter,
                            onChange = {
                                genderFilter = it
                                prefs.edit().putString("map_gender_filter", it.name).apply()
                            },
                            modifier = Modifier.scale(0.8f)
                        )
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

    if (!isPremium && !isPlus) {
        dailyLoginInfo?.let { info ->
            val kupidxOrange = Color(0xFFFF6F00)
            AlertDialog(
                onDismissRequest = { dailyLoginInfo = null },
                confirmButton = {
                    TextButton(onClick = { dailyLoginInfo = null }) {
                        Text("OK", color = kupidxOrange)
                    }
                },
                title = { Text(stringResource(R.string.daily_login_title)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (i in 1..5) {
                                val checked = i <= info.streak
                                Icon(
                                    imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (checked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.daily_login_message, info.streak))
                        if (info.rewardHours > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.daily_login_plus_award, info.rewardHours))
                        }
                    }
                }
            )
        }
    }

    if (omegleInvite != null) {
        AlertDialog(
            onDismissRequest = { /* keep dialog until user acts */ },
            text = { Text(stringResource(R.string.join_random_chat), color = KupidxOrange) },
            confirmButton = {
                TextButton(onClick = {
                    val match = omegleInvite!!
                    val ref = FirebaseDatabase.getInstance().reference
                    ref.child("omegleChats").child(match.chatId).child("status").setValue("accepted")
                    ref.child("omegleInvites").child(userId).child(match.chatId).removeValue()
                    navController.navigate("omegleChat/${match.chatId}/${match.otherUserId}")
                    omegleInvite = null
                }) { Text(stringResource(R.string.join_chat), color = KupidxOrange) }
                            },
            dismissButton = {
                TextButton(onClick = {
                    omegleInvite?.let { match ->
                        val ref = FirebaseDatabase.getInstance().reference
                        ref.child("omegleChats").child(match.chatId).child("status").setValue("rejected")
                        ref.child("omegleInvites").child(userId).child(match.chatId).removeValue()
                    }
                    omegleInvite = null
                }) { Text(stringResource(R.string.ignore_chat), color = KupidxOrange) }
            }
        )
    }
    if (showFiltersDialog) {
        DatingFilterDialog(
            initial = datingFilters,
            onDismiss = { showFiltersDialog = false },
            onApply = { filters ->
                showFiltersDialog = false
                nearbyViewModel.datingFilters = filters
                prefs.edit().putString("map_dating_filters", Gson().toJson(filters)).apply()
                userLatLng?.let {
                    nearbyViewModel.refreshNearbyUsers(userId, it, geoFireDatabaseRef, forceRefresh = true)
                }
            }
        )
    }
}

/* ======================================================================================= */
/*  Cards list + profile card (new tab)                                                   */
/* ======================================================================================= */

/** Thin grey film-wrapped text used on images */
@Composable
private fun FilmText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier
            .background(Color(0x66000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CardsList(
    users: List<NearbyUser>,
    showAds: Boolean,
    useMiles: Boolean,
    onLike: (NearbyUser) -> Unit,
    onDislike: (NearbyUser) -> Unit,
    onCardClick: (NearbyUser) -> Unit,
    onRemove: (String) -> Unit,
    onBlock: (String) -> Unit
) {
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_one_nearby_yet), color = Color.Gray)
        }
        return
    }
    val context = LocalContext.current
    val items = remember(users, showAds) {
        val list = mutableListOf<Any>()
        users.forEachIndexed { index, u ->
            list += u
            if (showAds && (index + 1) % 5 == 0) {
                list += "ad_$index"
            }
        }
        list
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .visibleScrollbar(listState),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items,
            key = { idx, item -> if (item is NearbyUser) item.userId else "ad_$idx" }
        ) { _, item ->
            if (item is NearbyUser) {
                ProfileCard(
                    user = item,
                    useMiles = useMiles,    // <---
                    onLike = { onLike(item) },
                    onDislike = { onDislike(item) },
                    onClick = { onCardClick(item) },
                    onRemove = { onRemove(item.userId) },
                    onBlock = { onBlock(item.userId) }
                )
            } else {
                ComposeNativeAd(
                    adUnitId = AdUnitIds.native(context),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    user: NearbyUser,
    useMiles: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit
) {
    val photos = remember(user.photoUrl) { listOfNotNull(user.photoUrl) }
    var currentIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex) {
        scope.launch { listState.animateScrollToItem(currentIndex) }
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            }

            if (photos.size > 1) {
                IconButton(
                    onClick = { if (currentIndex > 0) currentIndex-- },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .alpha(0.5f)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.White)
                }
                IconButton(
                    onClick = { if (currentIndex < photos.lastIndex) currentIndex++ },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .alpha(0.5f)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
                }
            }

            // Top readability gradient
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                            startY = 0f,
                            endY = 260f
                        )
                    )
            ) {
                // ⬆️ Top-left: Name + Compatibility with film
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    FilmText(
                        text = "${user.username}, ${user.age}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    user.compatibilityPct?.let {
                        FilmText(text = "Compatibility: $it%")
                    }
                    if (user.interests.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            user.interests.take(3).forEach { interest ->
                                val text = buildString {
                                    interest.emoji?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
                                    append(interest.name)
                                }
                                TagBox(text)
                            }
                        }
                    }
                }

                // ⬇️ Bottom-left: Distance (replaces randomDetail visually)
                val distanceLabel = if (user.distanceMeters.isFinite())
                    prettyDistance(user.distanceMeters, useMiles) else null

                distanceLabel?.let { dist ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        FilmText(text = dist)
                    }
                }
            }

            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = Color.White
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_stack)) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.block)) },
                    onClick = {
                        menuExpanded = false
                        onBlock()
                    }
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                FloatingActionButton(
                    onClick = onDislike,
                    shape = CircleShape,
                    containerColor = Color.DarkGray
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
                FloatingActionButton(
                    onClick = onLike,
                    shape = CircleShape,
                    containerColor = Color(0xFFFF6F00)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}
/* ======================================================================================= */
/*  People grid + cards                                                                    */
/* ======================================================================================= */

@Composable
private fun PeopleGrid(
    users: List<NearbyUser>,
    onClick: (NearbyUser) -> Unit,
    useMiles: Boolean,
    onRemove: (String) -> Unit,
    onBlock: (String) -> Unit,
    showAds: Boolean
) {
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_one_nearby_yet), color = Color.Gray)
        }
        return
    }
    val context = LocalContext.current
    val gridItems = remember(users, showAds) {
        val items = mutableListOf<Any>()
        users.forEachIndexed { index, user ->
            items.add(user)
            if (showAds && (index + 1) % 15 == 0) {
                items.add("ad_$index")
            }
        }
        items
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(
            gridItems,
            key = { index, item -> if (item is NearbyUser) item.userId else "ad_$index" },
            span = { _, item ->
                if (item is NearbyUser) GridItemSpan(1) else GridItemSpan(maxLineSpan)
            }
        ) { _, item ->
            if (item is NearbyUser) {
                NearbyCard(
                    user = item,
                    onClick = { onClick(item) },
                    useMiles = useMiles,
                    onRemove = { onRemove(item.userId) },
                    onBlock = { onBlock(item.userId) }
                )
            } else {
                ComposeNativeAd(
                    adUnitId = AdUnitIds.native(context),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NearbyCard(
    user: NearbyUser,
    onClick: () -> Unit,
    useMiles: Boolean,
    onRemove: () -> Unit,
    onBlock: () -> Unit
) {
    val placeholder = painterResource(R.drawable.local_placeholder)
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
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
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                            startY = 0f,
                            endY = 260f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentSize()
            ){
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_stack)) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.block)) },
                        onClick = {
                            menuExpanded = false
                            onBlock()
                        }
                    )
                }
            }
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
                GenderFilter.BOTH to stringResource(R.string.gender_all),
                GenderFilter.WOMEN to stringResource(R.string.gender_women),
                GenderFilter.MEN   to stringResource(R.string.gender_men),
                GenderFilter.OTHER to stringResource(R.string.gender_other)
            ).forEach { (type, label) ->
                FilterChip(
                    selected = selected == type,
                    onClick = {
                        onChange(
                            if (type == GenderFilter.BOTH) GenderFilter.BOTH
                            else if (selected == type) GenderFilter.BOTH else type
                        )
                    },
                    label = { Text(label) }
                )
            }
        }
    }
}
private fun deaccent(s: String): String {
    // NFD = split accents into separate code points, then strip marks (\p{Mn})
    val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
    return nfd.replace(Regex("\\p{Mn}+"), "")
}


private fun canonicalGender(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val t = deaccent(raw).trim().lowercase(Locale.ROOT)

    return when (t) {
        // English
        "male", "m", "man" -> "male"
        "female", "f", "woman" -> "female"
        "other", "others", "non-binary", "nonbinary", "nb" -> "other"

        // Spanish (lowercase; include common forms)
        "hombre", "hombres", "masculino" -> "male"
        "mujer", "mujeres", "femenino" -> "female"
        "otro", "otra", "otros", "otras",
        "no binario", "no-binario", "no_binario", "nobinario" -> "other"

        // Stored resource keys
        "male_option" -> "male"
        "female_option" -> "female"
        "gender_other" -> "other"

        else -> t
    }
}

@Composable
private fun RadiusChip(
    radiusKm: Double,                  // keep km internally for GeoFire
    useMiles: Boolean,
    onChange: (Double) -> Unit,        // expects km
    modifier: Modifier = Modifier
) {
    val minKm = 10.0
    val maxKm = 8000.0


    // Slider displays miles when needed but converts back to km for state
    val sliderPos = remember(radiusKm) {
        (ln(radiusKm / minKm) / ln(maxKm / minKm)).toFloat()
    }

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
                value = sliderPos,
                onValueChange = {
                    val newKm = minKm * (maxKm / minKm).pow(it.toDouble())
                    onChange(newKm.coerceIn(minKm, maxKm))
                },
                valueRange = 0f..1f,
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
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
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

@Composable
private fun DatingFilterDialog(
    initial: DatingFilterSettings,
    onDismiss: () -> Unit,
    onApply: (DatingFilterSettings) -> Unit
) {
    var ageRange by remember { mutableStateOf(initial.ageStart to initial.ageEnd) }
    var highSchool by remember { mutableStateOf(initial.highSchool) }
    var college by remember { mutableStateOf(initial.college) }
    var work by remember { mutableStateOf(initial.work) }
    var religion by remember { mutableStateOf(initial.religion) }
    var ethnicity by remember { mutableStateOf(initial.ethnicity) }
    var income by remember { mutableStateOf(initial.incomeLevel) }
    val defaultAgeRange = DatingFilterSettings().let { it.ageStart to it.ageEnd }

    val ageOptions = listOf(
        18 to 25,
        26 to 35,
        36 to 45,
        46 to 55,
        56 to 100
    )
    val religionResourceIds = listOf(
        R.string.religion_other,
        R.string.religion_buddhist,
        R.string.religion_christian,
        R.string.religion_christian_catholic,
        R.string.religion_christian_protestant_mainline,
        R.string.religion_christian_evangelical,
        R.string.religion_christian_orthodox,
        R.string.religion_christian_latter_day_saint,
        R.string.religion_christian_jehovahs_witness,
        R.string.religion_christian_other,
        R.string.religion_hindu,
        R.string.religion_jain,
        R.string.religion_jewish,
        R.string.religion_muslim,
        R.string.religion_muslim_sunni,
        R.string.religion_muslim_shia,
        R.string.religion_muslim_ahmadiyya,
        R.string.religion_muslim_sufi,
        R.string.religion_muslim_other,
        R.string.religion_no_religion,
        R.string.religion_parsi,
        R.string.religion_sikh,
        R.string.religion_indigenous_tribal,
        R.string.religion_santeria,
        R.string.religion_voodou,
        R.string.religion_candomble,
        R.string.religion_umbanda,
        R.string.religion_palo_mayombe,
        R.string.religion_native_traditional,
        R.string.religion_native_church,
        R.string.religion_vision_quest,
        R.string.religion_african_traditional,
        R.string.religion_obeah,
        R.string.religion_hoodoo,
        R.string.religion_rastafari,
        R.string.religion_black_protestant
    )
    val religionOptions = religionResourceIds.map { it to religionResToCanonical.getValue(it) }
    val ethnicityOptions = listOf(
        R.string.ethnicity_option_indian to "Bharatiya",
        R.string.ethnicity_option_white to "White (Caucasian)",
        R.string.ethnicity_option_black to "Black / African American",
        R.string.ethnicity_option_hispanic to "Hispanic / Latino",
        R.string.ethnicity_option_asian to "Asian",
        R.string.ethnicity_option_native_american to "Native American",
        R.string.ethnicity_option_middle_eastern to "Middle Eastern",
        R.string.ethnicity_option_pacific_islander to "Pacific Islander",
        R.string.ethnicity_option_mixed_other to "Mixed / Other"
    )
    val incomeOptions = listOf(
        R.string.income_level_under_25k to "Under $25k",
        R.string.income_level_25k_50k to "$25k–$50k",
        R.string.income_level_50k_75k to "$50k–$75k",
        R.string.income_level_75k_100k to "$75k–$100k",
        R.string.income_level_100k_150k to "$100k–$150k",
        R.string.income_level_over_150k to "Over $150k"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filters)) },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    DatingFilterSettings(
                        highSchool = highSchool,
                        college = college,
                        work = work,
                        ageStart = ageRange.first,
                        ageEnd = ageRange.second,
                        religion = religion,
                        ethnicity = ethnicity,
                        incomeLevel = income
                    )
                )
            }) { Text(stringResource(R.string.apply), color = KupidxOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = KupidxOrange) }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.label_age), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ageOptions.forEach { (start, end) ->
                        val label = if (end >= 100) "$start+" else "$start-$end"
                        FilterChip(
                            selected = ageRange.first == start && ageRange.second == end,
                            onClick = {
                                ageRange = if (ageRange.first == start && ageRange.second == end) {
                                    defaultAgeRange
                                } else {
                                    start to end
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = highSchool, onValueChange = { highSchool = it }, label = { Text(stringResource(R.string.label_high_school)) })
                OutlinedTextField(value = college, onValueChange = { college = it }, label = { Text(stringResource(R.string.label_college)) })
                OutlinedTextField(value = work, onValueChange = { work = it }, label = { Text(stringResource(R.string.label_work)) })
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.label_religion), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    religionOptions.forEach { (resId, value) ->
                        FilterChip(
                            selected = religion == value,
                            onClick = { religion = if (religion == value) "" else value },
                            label = { Text(stringResource(resId)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ethnicity_label), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ethnicityOptions.forEach { (resId, value) ->
                        FilterChip(
                            selected = ethnicity == value,
                            onClick = { ethnicity = if (ethnicity == value) "" else value },
                            label = { Text(stringResource(resId)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.income_level_label), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    incomeOptions.forEach { (resId, value) ->
                        FilterChip(
                            selected = income == value,
                            onClick = { income = if (income == value) "" else value },
                            label = { Text(stringResource(resId)) }
                        )
                    }
                }
            }
        }
    )
}