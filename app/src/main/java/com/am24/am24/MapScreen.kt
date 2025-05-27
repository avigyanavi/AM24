// MapScreen.kt  — com.am24.am24
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
import com.google.firebase.database.*
import com.google.maps.android.compose.*
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.launch
import kotlin.math.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/* ——— shared helper & model ——— */
import com.am24.am24.searchPlacesRich
import com.am24.am24.PlaceResult

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
private fun haversineKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLng - aLng)
    val h = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
            sin(dLon / 2).pow(2.0)
    return 2 * R * atan2(sqrt(h), sqrt(1 - h))
}
/*────────────────────────────────────────────*/

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
    val ctx             = LocalContext.current
    val scope           = rememberCoroutineScope()
    val focusManager    = LocalFocusManager.current

    /* ───── state ───── */
    var searchQuery       by remember { mutableStateOf("") }
    var showSearchBar     by remember { mutableStateOf(false) }
    val camera            = rememberCameraPositionState()
    val matchMarkers      = remember { mutableStateListOf<MarkerData>() }
    val searchResults     = remember { mutableStateListOf<PlaceResult>() }
    var selectedPlace     by remember { mutableStateOf<PlaceResult?>(null) }
    var selectedProfile   by remember { mutableStateOf<Profile?>(null) }
    val matchUids         = remember { mutableStateListOf<String>() }
    var navigateToProfile by remember { mutableStateOf<String?>(null) }
    var userLatLng        by remember { mutableStateOf<LatLng?>(null) }
    val heatPoints        = remember { mutableStateListOf<LatLng>() }

    var isLoadingSearch      by remember { mutableStateOf(false) }
    var isLoadingQuickSearch by remember { mutableStateOf(false) }
    var loadingTag           by remember { mutableStateOf<String?>(null) }
    var isLoadingMatches     by remember { mutableStateOf(false) }

    /* overlay: send place to match */
    var showSendOverlay      by remember { mutableStateOf(false) }
    var placeToSend          by remember { mutableStateOf<PlaceResult?>(null) }
    val matchProfiles        = remember { mutableStateListOf<MatchProfile>() }

    /* quick tags (trimmed list for brevity) */
    val quickTags = listOf(
        TagItem(ctx.getString(R.string.tag_cafes),        "cafes"),
        TagItem(ctx.getString(R.string.tag_bars),         "bars"),
        TagItem(ctx.getString(R.string.tag_restaurants),  "restaurants"),
        TagItem(ctx.getString(R.string.tag_hotels),       "hotels"),
        TagItem(ctx.getString(R.string.tag_oyo),          "OYO")
    )

    /*──────── fetch user location ────────*/
    LaunchedEffect(userId) {
        locationManager.getUserLocationFromGeoFire(userId) { lat, lng ->
            userLatLng = lat?.let { LatLng(it, lng ?: 0.0) }
        }
    }

    /*──────── load matches ────────*/
    LaunchedEffect(userId) {
        isLoadingMatches = true
        FirebaseRefs.db.getReference("matches").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    matchUids.clear(); snap.children.forEach { it.key?.let(matchUids::add) }
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

    /*──────── load nearby check-ins for heat-map ────────*/
    LaunchedEffect(userLatLng) {
        userLatLng?.let { me ->
            FirebaseRefs.db.getReference("posts").get().addOnSuccessListener { snap ->
                heatPoints.clear()
                snap.children.forEach { post ->
                    val ci = post.child("checkIn")
                    if (ci.exists()) {
                        val lat = ci.child("lat").getValue(Double::class.java) ?: return@forEach
                        val lng = ci.child("lng").getValue(Double::class.java) ?: return@forEach
                        if (haversineKm(me.latitude, me.longitude, lat, lng) <= 50.0)
                            heatPoints += LatLng(lat, lng)
                    }
                }
            }
        }
    }

    /*──────── nav to full profile ────────*/
    LaunchedEffect(navigateToProfile) {
        navigateToProfile?.let {
            navController.navigate("matchedUserProfile/$it")
            navigateToProfile = null; selectedProfile = null
        }
    }

    /*──────── helper to perform any search ────────*/
    suspend fun runSearch(query: String) {
        if (query.isBlank()) return
        val bias = userLatLng ?: run {
            Toast.makeText(ctx, R.string.error_location_not_available, Toast.LENGTH_SHORT).show(); return
        }
        val results = searchPlacesRich(query, bias)
        searchResults.apply { clear(); addAll(results) }
        selectedPlace = null
        if (results.isNotEmpty()) {
            val b = LatLngBounds.builder(); results.forEach { b.include(it.latLng) }
            camera.move(CameraUpdateFactory.newLatLngBounds(b.build(), 100))
        }
    }

    /*────────────────── UI ──────────────────*/
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (showSearchBar) { showSearchBar = false; focusManager.clearFocus() }
                }
        ) {
            /* TAG / SEARCH ROW */
            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                /* search capsule / icon */
                item {
                    if (showSearchBar) {
                        Box(
                            Modifier.height(36.dp)
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
                                    onSearch = { scope.launch { isLoadingSearch = true; runSearch(searchQuery); isLoadingSearch = false } }
                                )
                            )
                        }
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank())
                                    scope.launch { isLoadingSearch = true; runSearch(searchQuery); isLoadingSearch = false }
                                else { showSearchBar = false; focusManager.clearFocus() }
                            },
                            modifier = Modifier.size(36.dp).offset((-36).dp)
                        ) {
                            Icon(
                                if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.Close,
                                null, tint = if (searchQuery.isNotBlank()) Color(0xFFFF6F00) else Color.Gray
                            )
                        }
                    } else {
                        IconButton(onClick = { showSearchBar = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Search, null, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                /* quick tags */
                items(quickTags) { tag ->
                    Box(
                        Modifier.padding(end = 6.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, Color(0xFFFF6F00)), RoundedCornerShape(4.dp))
                            .clickable(enabled = !isLoadingQuickSearch) {
                                scope.launch {
                                    loadingTag = tag.label; isLoadingQuickSearch = true
                                    searchQuery = tag.query
                                    runSearch(tag.query)
                                    isLoadingQuickSearch = false; loadingTag = null
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        if (isLoadingQuickSearch && tag.label == loadingTag)
                            CircularProgressIndicator(
                                strokeWidth = 1.dp, modifier = Modifier.size(12.dp), color = Color.White
                            )
                        else
                            Text(tag.label, color = Color.LightGray, fontSize = 10.sp)
                    }
                }
            }

            /* MAP */
            Box(Modifier.weight(1f)) {
                val heatProvider = remember(heatPoints) {
                    if (heatPoints.isNotEmpty())
                        HeatmapTileProvider.Builder().data(heatPoints).radius(40).opacity(0.65).build()
                    else null
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
                            icon  = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE),
                            onClick = {
                                scope.launch {
                                    FirebaseRefs.db.getReference("users").child(m.userId)
                                        .get().addOnSuccessListener { snap ->
                                            snap.getValue(Profile::class.java)?.let { p ->
                                                if (matchUids.contains(p.userId) && p.allowLocationForMatches)
                                                    selectedProfile = p
                                            }
                                        }
                                }; true
                            }
                        )
                    }
                    /* place search markers */
                    searchResults.forEach { p ->
                        Marker(
                            state = MarkerState(p.latLng),
                            title = p.name,
                            onClick = { selectedPlace = p; true }
                        )
                    }
                    /* heat-map */
                    heatProvider?.let { TileOverlay(tileProvider = it, state = heatState) }
                }

                /* arrows */
                userLatLng?.let { me ->
                    DirectionalArrowsOverlay(
                        userLocation = me,
                        matchLocations = matchMarkers.map { it.position },
                        modifier = Modifier.fillMaxSize()
                    ) { loc -> scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(loc, 18f), 500) } }
                }

                /* pop-ups */
                selectedPlace?.let { place ->
                    PlaceDetailsPopup(
                        latLng = place.latLng,
                        name   = place.name,
                        onDismiss = { selectedPlace = null },
                        onSendToMatch = {
                            placeToSend = place; showSendOverlay = true
                        }
                    )
                }
                selectedProfile?.let { prof ->
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)), Alignment.Center) {
                        UserProfilePopup(
                            profile = prof,
                            onProfileClick = { navigateToProfile = it },
                            onCloseClick   = { selectedProfile = null }
                        )
                    }
                }
                if (showSendOverlay) {
                    /* fetch profiles once */
                    LaunchedEffect(Unit) {
                        if (matchProfiles.isEmpty())
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
                    MatchesListOverlay(
                        matches = matchProfiles,
                        onDismiss = { showSendOverlay = false },
                        onSend = { match ->
                            placeToSend?.let { pl ->
                                val msg = "Check out this place: ${pl.name}. Directions: https://maps.google" +
                                        ".com/?q=${pl.latLng.latitude},${pl.latLng.longitude}"
                                val chatId = getChatId2(userId, match.userId)
                                val ref = FirebaseRefs.db.getReference("messages/$chatId")
                                sendMessage2(userId, match.userId, chatId, msg, ref)
                                Toast.makeText(ctx, "Sent to ${match.name}", Toast.LENGTH_SHORT).show()
                                showSendOverlay = false; placeToSend = null
                            }
                        }
                    )
                }
            }
        }

        if (isLoadingMatches || isLoadingSearch || isLoadingQuickSearch) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/*──────── DirectionalArrowsOverlay / Pop-ups / Helpers — unchanged ───────*/
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DirectionalArrowsOverlay(
    userLocation: LatLng,
    matchLocations: List<LatLng>,
    modifier: Modifier = Modifier,
    onArrowClick: (LatLng) -> Unit
) {
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val radius = (min(w, h) / 2f) - with(LocalDensity.current) { 40.dp.toPx() }
        matchLocations.forEach { loc ->
            val bearing = getBearing(userLocation, loc); val rad = Math.toRadians(bearing.toDouble())
            val x = cx + radius * cos(rad).toFloat(); val y = cy - radius * sin(rad).toFloat()
            Icon(Icons.Default.ArrowUpward, null, tint = Color.Red,
                modifier = Modifier.size(40.dp)
                    .offset { IntOffset((x - 20).toInt(), (y - 20).toInt()) }
                    .graphicsLayer(rotationZ = bearing)
                    .clickable { onArrowClick(loc) })
        }
    }
}

@Composable
fun PlaceDetailsPopup(
    latLng: LatLng,
    name: String,
    onDismiss: () -> Unit,
    onSendToMatch: () -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().background(Color.White).padding(16.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.End) {
            Icon(Icons.Default.Close, null, tint = Color.Gray,
                modifier = Modifier.size(24.dp).clickable { onDismiss() })
        }
        Text(name, color = Color.Black)
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = {
                val gmm = Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}")
                ctx.startActivity(Intent(Intent.ACTION_VIEW, gmm).apply { setPackage("com.google.android.apps.maps") })
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select a Match", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))
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
                            modifier = Modifier
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(match.name, fontWeight = FontWeight.Bold)
                                Text("Age: ${match.age}")
                                Text("From: ${match.hometown}")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .width(260.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
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
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = profile.name,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(6.dp))
        RatingBar2(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
        Spacer(modifier = Modifier.height(16.dp))
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
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = String.format("%.2f (%d)", rating, ratingCount),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

//suspend fun searchPlacesWithOkHttp(query: String, userLocation: LatLng): List<Pair<LatLng, String>> = withContext(Dispatchers.IO) {
//    val client = OkHttpClient()
//    val apiKey = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"
//    val requestBody = JSONObject()
//        .put("textQuery", query)
//        .put(
//            "locationBias",
//            JSONObject()
//                .put(
//                    "circle",
//                    JSONObject()
//                        .put("center", JSONObject().put("latitude", userLocation.latitude).put("longitude", userLocation.longitude))
//                        .put("radius", 10000)
//                )
//        )
//        .toString()
//        .toRequestBody("application/json".toMediaType())
//
//    val request = Request.Builder()
//        .url("https://places.googleapis.com/v1/places:searchText")
//        .addHeader("Content-Type", "application/json")
//        .addHeader("X-Goog-Api-Key", apiKey)
//        .addHeader("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location")
//        .post(requestBody)
//        .build()
//
//    val results = mutableListOf<Pair<LatLng, String>>()
//    try {
//        client.newCall(request).execute().use { response ->
//            if (!response.isSuccessful) {
//                Log.e("PlacesSearch", "Error: ${response.code} - ${response.body?.string()}")
//                return@withContext emptyList()
//            }
//            val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
//            val places = json.getJSONArray("places")
//            for (i in 0 until places.length()) {
//                val place = places.getJSONObject(i)
//                val name = place.getJSONObject("displayName").getString("text")
//                val location = place.getJSONObject("location")
//                val lat = location.getDouble("latitude")
//                val lng = location.getDouble("longitude")
//                results.add(LatLng(lat, lng) to name)
//            }
//        }
//    } catch (e: Exception) {
//        Log.e("PlacesSearch", "Exception: ${e.message}", e)
//    }
//    return@withContext results
//}

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
                        markersState.replaceAll { if (it.userId == key) it.copy(position = LatLng(location.latitude, location.longitude)) else it }
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

/*────────────────── data classes ─────────────────*/
data class MarkerData(val userId: String, val position: LatLng)
data class MatchProfile(
    val userId: String,
    val name: String,
    val age: Int,
    val hometown: String,
    val photoUrl: String?
)