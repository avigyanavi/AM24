@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.am24.am24

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.firebase.geofire.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.firebase.database.*
import com.google.maps.android.compose.*
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.*
import java.text.SimpleDateFormat
import java.util.*

/* ——— shared helper & model ——— */
import com.am24.am24.searchPlacesRich
import com.am24.am24.PlaceResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/*────────────────── utils ──────────────────*/
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

/* ────────── cache for LatLng ────────── */
val latLngCache = mutableMapOf<String, LatLng?>()

/* ────────── fetch LatLng from placeId ────────── */
suspend fun getLatLngFromPlaceId(placeId: String, context: android.content.Context): LatLng? {
    latLngCache[placeId]?.let { return it }
    return try {
        val placesClient = Places.createClient(context)
        val request = FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.LAT_LNG))
        val response = placesClient.fetchPlace(request).await()
        val latLng = response.place.latLng
        Log.d("MapScreen", "Fetched LatLng for placeId: $placeId -> $latLng")
        latLngCache[placeId] = latLng
        latLng
    } catch (e: Exception) {
        Log.e("MapScreen", "Failed to fetch LatLng for placeId: $placeId", e)
        latLngCache[placeId] = null
        null
    }
}

/* ────────── fetch place name from placeId ────────── */
suspend fun getPlaceNameFromPlaceId(placeId: String, context: android.content.Context): String? {
    return try {
        val placesClient = Places.createClient(context)
        val request = FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.NAME))
        val response = placesClient.fetchPlace(request).await()
        response.place.name
    } catch (e: Exception) {
        Log.e("MapScreen", "Failed to fetch name for placeId: $placeId", e)
        null
    }
}

/* ────────── cluster model ────────── */
private data class CheckInCluster(
    val placeId: String,
    val postIds: List<String>
)

private data class TagItem(val label: String, val query: String)

@Composable
fun MapScreen(
    userId: String,
    locationManager: LocationManager,
    geoFireDatabaseRef: DatabaseReference,
    navController: NavController,
    onProfileMarkerClicked: (String) -> Unit,
    currentPrice: String
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    /* ───── state ───── */
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    val camera = rememberCameraPositionState()
    val matchMarkers = remember { mutableStateListOf<MarkerData>() }
    val searchResults = remember { mutableStateListOf<PlaceResult>() }
    var selectedPlace by remember { mutableStateOf<PlaceResult?>(null) }
    var selectedProfile by remember { mutableStateOf<Profile?>(null) }
    val matchUids = remember { mutableStateListOf<String>() }
    var navigateToProfile by remember { mutableStateOf<String?>(null) }
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }
    val clusters = remember { mutableStateListOf<CheckInCluster>() }
    val heatPoints = remember { mutableStateListOf<LatLng>() }
    var isLoadingSearch by remember { mutableStateOf(false) }
    var isLoadingQuickSearch by remember { mutableStateOf(false) }
    var loadingTag by remember { mutableStateOf<String?>(null) }
    var isLoadingMatches by remember { mutableStateOf(false) }
    var showSendOverlay by remember { mutableStateOf(false) }
    var placeToSend by remember { mutableStateOf<PlaceResult?>(null) }
    val matchProfiles = remember { mutableStateListOf<MatchProfile>() }
    val clusterLatLngs = remember { mutableStateMapOf<String, LatLng?>() }

    /* quick tags */
    val quickTags = listOf(
        TagItem(ctx.getString(R.string.tag_budget_hotels), "budget_hotels"),
        TagItem(ctx.getString(R.string.tag_hotels), "hotels"),
        TagItem(ctx.getString(R.string.tag_cafes), "cafes"),
        TagItem(ctx.getString(R.string.tag_bars), "bars"),
        TagItem(ctx.getString(R.string.tag_malls), "malls"),
        TagItem(ctx.getString(R.string.tag_parks), "parks"),
        TagItem(ctx.getString(R.string.tag_cinemas), "cinemas"),
        TagItem(ctx.getString(R.string.tag_restaurants), "restaurants"),
        TagItem(ctx.getString(R.string.tag_street_food), "street_food"),
        TagItem(ctx.getString(R.string.tag_clubs), "clubs"),
        TagItem(ctx.getString(R.string.tag_bookstores), "bookstores"),
        TagItem(ctx.getString(R.string.tag_gaming_centers), "gaming_centers"),
        TagItem(ctx.getString(R.string.tag_amusement_parks), "amusement_parks"),
        TagItem(ctx.getString(R.string.tag_beaches), "beaches"),
        TagItem(ctx.getString(R.string.tag_forts), "forts"),
        TagItem(ctx.getString(R.string.tag_temples), "temples"),
        TagItem(ctx.getString(R.string.tag_mosques), "mosques"),
        TagItem(ctx.getString(R.string.tag_monuments), "monuments"),
        TagItem(ctx.getString(R.string.tag_markets), "markets"),
        TagItem(ctx.getString(R.string.tag_art_galleries), "art_galleries"),
        TagItem(ctx.getString(R.string.tag_lakes), "lakes"),
        TagItem(ctx.getString(R.string.tag_sports_centers), "sports_centers"),
        TagItem(ctx.getString(R.string.tag_night_markets), "night_markets"),
        TagItem(ctx.getString(R.string.tag_food_courts), "food_courts")
    )

    /* fetch user location */
    LaunchedEffect(userId) {
        locationManager.getUserLocationFromGeoFire(userId) { lat, lng ->
            userLatLng = lat?.let { LatLng(it, lng ?: 0.0) }
        }
    }

    /* load matches */
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

    /* load nearby check-ins for heat-map */
    /*──────── load nearby check-ins for heat-map ────────*/
    LaunchedEffect(userLatLng) {
        if (userLatLng == null) return@LaunchedEffect     // just a trigger

        try {
            val snap = FirebaseRefs.db.getReference("posts").get().await()

            clusters       .clear()
            heatPoints     .clear()
            clusterLatLngs .clear()

            /* ---- 1) group posts by placeId and grab lat/lng when present ---- */
            val grouped = mutableMapOf<String, MutableList<String>>()   // placeId → postIds

            snap.children.forEach { postSnap ->
                val postId  = postSnap.key ?: return@forEach
                val ci      = postSnap.child("checkIn")
                val placeId = ci.child("placeId").getValue(String::class.java) ?: return@forEach

                grouped.getOrPut(placeId) { mutableListOf() }.add(postId)

                // fast path: we already have coordinates
                val lat = ci.child("lat").getValue(Double::class.java)
                val lng = ci.child("lng").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    val ll = LatLng(lat, lng)
                    clusterLatLngs[placeId] = ll
                    heatPoints            += ll
                    return@forEach                                // done with this post
                }
                // else -> will resolve via Places SDK below
            }

            grouped.forEach { (pid, postIds) ->
                clusters += CheckInCluster(pid, postIds)
            }

            /* ---- 2) resolve the *missing* ones in parallel ------------------ */
            val toResolve = grouped.keys.filter { !clusterLatLngs.containsKey(it) }
            if (toResolve.isNotEmpty()) {
                val resolvedPairs = toResolve.map { pid ->
                    async { pid to getLatLngFromPlaceId(pid, ctx) }
                }.awaitAll()

                resolvedPairs.forEach { (pid, ll) ->
                    ll?.let {
                        clusterLatLngs[pid] = it
                        heatPoints        += it
                    }
                }
            }

            Log.d("MapScreen", "🌡  heatPoints = ${heatPoints.size}, clusters = ${clusters.size}")
        } catch (e: Exception) {
            Log.e("MapScreen", "Check-in load failed: ${e.message}", e)
        }
    }
    /* nav to full profile */
    LaunchedEffect(navigateToProfile) {
        navigateToProfile?.let {
            navController.navigate("matchedUserProfile/$it")
            navigateToProfile = null
            selectedProfile = null
        }
    }

    /* helper to perform any search */
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
            camera.move(CameraUpdateFactory.newLatLngBounds(b.build(), 100))
        }
    }

    /* UI */
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (showSearchBar) {
                        showSearchBar = false
                        focusManager.clearFocus()
                    }
                }
        ) {
            /* TAG / SEARCH ROW */
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
                                .padding(start = 12.dp, end = 40.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(Color.Black, fontSize = 14.sp),
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        scope.launch {
                                            isLoadingSearch = true
                                            runSearch(searchQuery)
                                            isLoadingSearch = false
                                        }
                                    }
                                )
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
                                tint = if (searchQuery.isNotBlank()) Color(0xFFFF6F00) else Color.Gray
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { showSearchBar = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                items(quickTags) { tag ->
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, Color(0xFFFF6F00)), RoundedCornerShape(4.dp))
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
                            Text(tag.label, color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                }
            }

            /* MAP */
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
                    properties = MapProperties(isMyLocationEnabled = true),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = true,
                        mapToolbarEnabled = false
                    )
                ) {
                    /* match markers */
                    matchMarkers.forEach { m ->
                        Marker(
                            state = MarkerState(m.position),
                            title = m.userId,
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE),
                            onClick = {
                                scope.launch {
                                    FirebaseRefs.db.getReference("users").child(m.userId)
                                        .get().addOnSuccessListener { snap ->
                                            snap.getValue(Profile::class.java)?.let { p ->
                                                if (matchUids.contains(p.userId) && p.allowLocationForMatches) {
                                                    selectedProfile = p
                                                }
                                            }
                                        }
                                }
                                true
                            }
                        )
                    }

                    /* place search markers */
                    searchResults.forEach { p ->
                        Marker(
                            state = MarkerState(p.latLng),
                            title = p.name,
                            onClick = {
                                selectedPlace = p
                                true
                            }
                        )
                    }

                    /* heat‐map overlay */
                    heatProvider?.let {
                        TileOverlay(tileProvider = it, state = heatState)
                    }

                    /* clusters */
                    clusters.forEach { cluster ->
                        clusterLatLngs[cluster.placeId]?.let { latLng ->
                            Marker(
                                state = MarkerState(latLng),
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                                alpha = 0f,
                                onClick = {
                                    Log.d("MapScreen", "Navigating to checkinFeed with placeId: ${cluster.placeId}")
                                    navController.navigate("checkinFeed/${cluster.placeId}")
                                    true
                                }
                            )
                        }
                    }
                }

                /* directional arrows toward matches */
                userLatLng?.let { me ->
                    DirectionalArrowsOverlay(
                        userLocation = me,
                        matchLocations = matchMarkers.map { it.position },
                        modifier = Modifier.fillMaxSize()
                    ) { loc ->
                        scope.launch {
                            camera.animate(CameraUpdateFactory.newLatLngZoom(loc, 18f), 500)
                        }
                    }
                }

                /* place details popup */
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

                /* profile popup */
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

                /* send-to-match overlay */
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
                                val msg =
                                    "Check out this place: ${pl.name}. Directions: https://maps.google.com/?q=place_id:${pl.placeId}"
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
        }

        /* global loading overlay */
        if (isLoadingMatches || isLoadingSearch || isLoadingQuickSearch) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .3f)),
                Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/* Supporting Composables and Functions (unchanged unless noted) */
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

@Composable
fun PlaceDetailsPopup(
    placeId: String,
    name: String? = null,
    onDismiss: () -> Unit,
    onSendToMatch: () -> Unit
) {
    val ctx = LocalContext.current
    var placeName by remember { mutableStateOf(name ?: "Loading...") }

    LaunchedEffect(placeId) {
        if (name == null) {
            placeName = getPlaceNameFromPlaceId(placeId, ctx) ?: "Unknown Place"
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.End) {
            Icon(
                Icons.Default.Close, contentDescription = null, tint = Color.Gray,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDismiss() }
            )
        }
        Text(placeName, color = Color.Black)
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = {
                val gmm = Uri.parse("google.navigation:q=place_id:$placeId")
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, gmm)
                        .apply { setPackage("com.google.android.apps.maps") }
                )
            }) { Text("Directions") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSendToMatch) { Text("Send to Match") }
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
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select a Match", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                        Row(
                            Modifier
                                .padding(8.dp)
                                .width(200.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                Text("Age: ${match.age}")
                                Text("From: ${match.hometown}")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (selectedMatch != null) {
                Button(onClick = { onSend(selectedMatch!!) }) { Text("Send") }
            }
        }
    }
}

@Composable
fun UserProfilePopup(
    profile: Profile,
    onProfileClick: (String) -> Unit,
    onCloseClick: () -> Unit
) {
    val context = LocalContext.current
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
        profile.profilepicUrl?.let { url ->
            LaunchedEffect(url) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .diskCacheKey(url)
                    .memoryCacheKey(url)
                    .crossfade(true)
                    .build()
                context.imageLoader.enqueue(request)
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .diskCacheKey(url)
                    .memoryCacheKey(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = profile.name,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black
        )
        Spacer(Modifier.height(6.dp))
        RatingBar2(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            Log.d("MapScreen", "View Full Profile clicked for ${profile.userId}")
            onProfileClick(profile.userId)
        }) {
            Text("View Full Profile")
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
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = orange,
                modifier = Modifier.size(starSize)
            )
        }
        if (fraction > 0) {
            Box(modifier = Modifier.size(starSize)) {
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = orange,
                    modifier = Modifier.fillMaxSize()
                )
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
        Text(
            text = String.format("%.2f (%d)", rating, ratingCount),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
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
                    Log.d("MapScreen", "GeoFire key entered: $key at ${location.latitude}, ${location.longitude}")
                    FirebaseRefs.db.getReference("users").child(key).get().addOnSuccessListener { snapshot ->
                        val profile = snapshot.getValue(Profile::class.java)
                        if (profile != null && matchesSet.contains(key) && profile.allowLocationForMatches) {
                            markersState.add(MarkerData(key, LatLng(location.latitude, location.longitude)))
                            Log.d("MapScreen", "Added match marker for $key. Markers state size: ${markersState.size}")
                        }
                    }
                }

                override fun onKeyExited(key: String) {
                    markersState.removeAll { it.userId == key }
                    Log.d("MapScreen", "Key exited: $key. Markers state size: ${markersState.size}")
                }

                override fun onKeyMoved(key: String, location: GeoLocation) {
                    if (matchesSet.contains(key)) {
                        markersState.replaceAll {
                            if (it.userId == key) it.copy(position = LatLng(location.latitude, location.longitude))
                            else it
                        }
                        Log.d("MapScreen", "Key moved: $key to ${location.latitude}, ${location.longitude}")
                    }
                }

                override fun onGeoQueryReady() {
                    Log.d("MapScreen", "GeoQuery ready. Markers state: ${markersState.size}")
                }

                override fun onGeoQueryError(error: DatabaseError) {
                    Toast.makeText(context, "GeoQuery error: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e("MapScreen", "GeoQuery error: ${error.message}")
                }
            })
        } else {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(22.5726, 88.3639), 16f)
            Log.d("MapScreen", "User location not found, using default Kolkata position")
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

fun calculateAge(dob: String): Int {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val birthDate = try {
        sdf.parse(dob)
    } catch (e: Exception) {
        return 0
    }
    val birthCalendar = Calendar.getInstance().apply { time = birthDate }
    val today = Calendar.getInstance()
    var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
        age--
    }
    return age
}

/* data classes */
data class MarkerData(val userId: String, val position: LatLng)
data class MatchProfile(
    val userId: String,
    val name: String,
    val age: Int,
    val hometown: String,
    val photoUrl: String?
)