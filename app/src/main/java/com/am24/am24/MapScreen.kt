package com.am24.am24

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*
import com.google.maps.android.compose.*
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// Helper function to calculate bearing between two locations.
fun getBearing(from: LatLng, to: LatLng): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lon1 = Math.toRadians(from.longitude)
    val lat2 = Math.toRadians(to.latitude)
    val lon2 = Math.toRadians(to.longitude)
    val dLon = lon2 - lon1

    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}

/** UI label (locale-dependent) + English query term */
private data class TagItem(
    val label : String,   // what the user sees (#<label>)
    val query : String    // always English – what we send to Places
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapScreen(
    userId: String,
    locationManager: LocationManager,
    geoFireDatabaseRef: DatabaseReference,
    navController: NavController,
    onProfileMarkerClicked: (String) -> Unit,
    currentPrice: String        // ← add this param, pass from MainNavGraph
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // UI States
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState()
    val selectedPriceRange = currentPrice   // READ-ONLY
    val markersState = remember { mutableStateListOf<MarkerData>() }
    val searchResultsState = remember { mutableStateListOf<Pair<LatLng, String>>() }
    var selectedPlaceDetails by remember { mutableStateOf<Pair<LatLng, String>?>(null) }
    var selectedUserProfile by remember { mutableStateOf<Profile?>(null) }
    val errorlocation = stringResource(R.string.error_location_not_available)

    /* before: val quickSearchItems = listOf("OYO", "hotels", …) */
    val quickSearchItems = listOf(
        TagItem(stringResource(R.string.tag_cafes)        , "cafes"),
        TagItem(stringResource(R.string.tag_bars)         , "bars"),
        TagItem(stringResource(R.string.tag_malls)        , "malls"),
        TagItem(stringResource(R.string.tag_parks)        , "parks"),
        TagItem(stringResource(R.string.tag_cinemas)      , "cinemas"),
        TagItem(stringResource(R.string.tag_restaurants)  , "restaurants"),
        TagItem(stringResource(R.string.tag_hotels)       , "hotels"),
        TagItem(stringResource(R.string.tag_oyo)          , "OYO"),
        TagItem(stringResource(R.string.tag_street_food)  , "street food"),
        TagItem(stringResource(R.string.tag_clubs)        , "clubs"),
        TagItem(stringResource(R.string.tag_turf)             , "turf"),
        TagItem(stringResource(R.string.tag_bookstores)   , "bookstores"),
        TagItem(stringResource(R.string.tag_gaming_zones) , "gaming center"),
        TagItem(stringResource(R.string.tag_howrah_bridge)       , "howrah bridge"),
        TagItem(stringResource(R.string.tag_victoria_memorial)   , "victoria memorial"),
        TagItem(stringResource(R.string.tag_princep_ghat)        , "princep ghat"),
        TagItem(stringResource(R.string.tag_indian_museum)       , "indian museum"),
        TagItem(stringResource(R.string.tag_science_city)        , "science city"),
        TagItem(stringResource(R.string.tag_park_street)         , "park street"),
        TagItem(stringResource(R.string.tag_college_street)      , "college street"),
        TagItem(stringResource(R.string.tag_esplanade)           , "esplanade"),
        TagItem(stringResource(R.string.tag_dakshineswar_temple) , "dakshineswar temple"),
        TagItem(stringResource(R.string.tag_kalighat_temple)     , "kalighat temple"),
        TagItem(stringResource(R.string.tag_belur_math)          , "belur math"),
        TagItem(stringResource(R.string.tag_nicco_park)          , "nicco park"),
        TagItem(stringResource(R.string.tag_eco_park)            , "eco park"),
        TagItem(stringResource(R.string.tag_salt_lake_stadium)   , "salt lake stadium")
    )

    val matchesSet = remember { mutableStateListOf<String>() }
    var showPriceFilterDialog by remember { mutableStateOf(false) }
    var navigateToProfile by remember { mutableStateOf<String?>(null) }
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }

    // Loading States
    var isLoadingSearch by remember { mutableStateOf(false) }
    var isLoadingQuickSearch by remember { mutableStateOf(false) }
    var loadingTag by remember { mutableStateOf<String?>(null) }
    var isLoadingMatches by remember { mutableStateOf(false) }

    // Overlay states
    var showSendOverlay by remember { mutableStateOf(false) }
    var placeDetailsToSend by remember { mutableStateOf<Pair<LatLng, String>?>(null) }
    val matchProfiles = remember { mutableStateListOf<MatchProfile>() }

    // Fetch user location
    LaunchedEffect(userId) {
        locationManager.getUserLocationFromGeoFire(userId) { lat, lng ->
            if (lat != null && lng != null) {
                userLatLng = LatLng(lat, lng)
            }
            isLoadingMatches = false
        }
    }

    // Handle navigation
    LaunchedEffect(navigateToProfile) {
        navigateToProfile?.let { userId ->
            navController.navigate("matchedUserProfile/$userId")
            navigateToProfile = null
            selectedUserProfile = null
        }
    }

    // Load Matches
    LaunchedEffect(userId) {
        isLoadingMatches = true
        val matchesRef = FirebaseRefs.db.getReference("matches").child(userId)
        matchesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                matchesSet.clear()
                for (child in snapshot.children) {
                    child.key?.let { matchesSet.add(it) }
                }
                loadUserLocationAndMatches(
                    userId,
                    locationManager,
                    geoFireDatabaseRef,
                    matchesSet,
                    markersState,
                    cameraPositionState,
                    context
                )
                isLoadingMatches = false
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load matches: ${error.message}", Toast.LENGTH_LONG).show()
                isLoadingMatches = false
            }
        })
    }

    LaunchedEffect(showSendOverlay) {
        if (showSendOverlay && matchProfiles.isEmpty()) {
            matchesSet.forEach { matchUid ->
                val dbRef = FirebaseRefs.db.getReference("users").child(matchUid)
                dbRef.get().addOnSuccessListener { snapshot ->
                    val profile = snapshot.getValue(Profile::class.java)
                    if (profile != null) {
                        matchProfiles.add(
                            MatchProfile(
                                matchUid,
                                profile.name,
                                calculateAge(profile.dob),
                                profile.hometown,
                                profile.profilepicUrl
                            )
                        )
                    }
                }
            }
        }
    }

    // Main Layout
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (showSearchBar) {
                        focusManager.clearFocus()
                        showSearchBar = false
                    }
                }
        ) {
            // Top Row: Search and Filter Buttons
            /* ───────── SEARCH ICON  +  HASH-TAGS ROW ───────── */
            /* ───────── SEARCH ICON  +  TAGS  ───────── */
            val listState = rememberLazyListState()

            /* inside MapScreen(), just before LazyRow --------------------------------*/
            suspend fun performSearch(query: String) {
                if (query.isBlank()) return
                isLoadingSearch = true
                val q = if (selectedPriceRange != "All")
                    "$query, Price: $selectedPriceRange" else query
                userLatLng?.let { loc ->
                    val results = searchPlacesWithOkHttp(q, loc)
                    searchResultsState.apply { clear(); addAll(results) }
                    selectedPlaceDetails = null
                    if (checkNotEmpty(results)) {
                        val b = LatLngBounds.builder()
                        results.forEach { b.include(it.first) }
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngBounds(b.build(), 100)
                        )
                    }
                } ?: Toast.makeText(context,
                    "User location not available", Toast.LENGTH_SHORT).show()
                isLoadingSearch = false
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                state = listState,
                verticalAlignment = Alignment.CenterVertically
            ) {
                /* 1️⃣  Search capsule / icon --------------------------------------- */
                item {
                    // -- search capsule / icon ------------------------------------------
                    if (showSearchBar) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .background(Color.White, RoundedCornerShape(18.dp))
                                .padding(start = 12.dp, end = 40.dp, top = 8.dp, bottom = 8.dp)
                                .wrapContentWidth()
                        ) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle  = TextStyle(color = Color.Black, fontSize = 14.sp),
                                singleLine = true,
                                keyboardOptions  = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { scope.launch { performSearch(searchQuery) } }
                                )
                            )
                        }

                        /* 🔍 now *runs search* when bar is visible,
                           ❌ still closes if bar empty                               */
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    scope.launch { performSearch(searchQuery) }
                                } else {
                                    showSearchBar = false
                                    focusManager.clearFocus()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .offset(x = (-36).dp)
                        ) {
                            Icon(
                                if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.Close,
                                contentDescription = if (searchQuery.isNotBlank()) "Search" else "Close",
                                tint = if (searchQuery.isNotBlank()) Color(0xFFFF6F00) else Color.Gray
                            )
                        }
                    } else {
                        /* first tap -> open bar */
                        IconButton(
                            onClick  = { showSearchBar = true },
                            modifier = Modifier.size(36.dp)
                        ) { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White) }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                /* 2️⃣  Tags --------------------------------------------------------- */
                items(quickSearchItems) { tagItem ->
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .background(Color.Black, RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, Color(0xFFFF6F00)), RoundedCornerShape(4.dp))
                            .clickable(enabled = !isLoadingQuickSearch) {
                                scope.launch {
                                    loadingTag = tagItem.label          // start spinner on this tag
                                    isLoadingQuickSearch = true
                                    searchQuery = tagItem.query         // show English in the text-field
                                    val q = if (selectedPriceRange != "All")
                                        "${tagItem.query}, Price: $selectedPriceRange"
                                    else
                                        tagItem.query

                                    userLatLng?.let { loc ->
                                        val results = searchPlacesWithOkHttp(q, loc)
                                        searchResultsState.apply {
                                            clear()
                                            addAll(results)
                                        }
                                        selectedPlaceDetails = null
                                        if (checkNotEmpty(results)) {
                                            val b = LatLngBounds.builder()
                                            results.forEach { b.include(it.first) }
                                            cameraPositionState.move(
                                                CameraUpdateFactory.newLatLngBounds(b.build(), 100)
                                            )
                                        }
                                    } ?: Toast.makeText(
                                        context,
                                        errorlocation,
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    isLoadingQuickSearch = false
                                    loadingTag = null                   // remove spinner
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        if (isLoadingQuickSearch && tagItem.label == loadingTag) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.dp,
                                color = Color.White
                            )
                        } else {
                            Text("${tagItem.label}", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Map & Overlays
            Box(Modifier.weight(1f)) {
                val heatmapProvider = remember(markersState) {
                    if (checkNotEmpty(markersState)) {
                        HeatmapTileProvider.Builder()
                            .data(markersState.map { it.position })
                            .build()
                    } else null
                }
                val heatmapState = rememberTileOverlayState()
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        mapToolbarEnabled = false,
                        myLocationButtonEnabled = true
                    ),
                    properties = MapProperties(isMyLocationEnabled = true)
                ) {
                    markersState.forEach { markerData ->
                        Marker(
                            state = MarkerState(position = markerData.position),
                            title = markerData.userId,
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE),
                            onClick = {
                                scope.launch {
                                    FirebaseRefs.db.getReference("users").child(markerData.userId)
                                        .get().addOnSuccessListener { snapshot ->
                                            val profile = snapshot.getValue(Profile::class.java)
                                            if (profile != null && matchesSet.contains(markerData.userId) && profile.allowLocationForMatches) {
                                                selectedUserProfile = profile
                                            }
                                        }
                                }
                                true
                            }
                        )
                    }
                    searchResultsState.forEach { (position, name) ->
                        Marker(
                            state = MarkerState(position = position),
                            title = name,
                            onClick = {
                                selectedPlaceDetails = position to name
                                true
                            }
                        )
                    }
                    if (heatmapProvider != null) {
                        TileOverlay(tileProvider = heatmapProvider, state = heatmapState)
                    }
                }

                // Overlay: Directional Arrows are added as a sibling overlay on top of GoogleMap.
                userLatLng?.let { userLoc ->
                    // Collect all match locations from your markersState.
                    val matchLocations = markersState.map { it.position }
                    DirectionalArrowsOverlay(
                        userLocation = userLoc,
                        matchLocations = matchLocations,
                        modifier = Modifier.fillMaxSize(),
                        onArrowClick = { matchLoc ->
                            // Animate the camera to center on the tapped match location (e.g., zoom level 18).
                            scope.launch {
                                cameraPositionState.animate(
                                    update = CameraUpdateFactory.newLatLngZoom(matchLoc, 18f),
                                    durationMs = 500
                                )
                            }
                        }
                    )
                }

                // Popups and Overlays
                selectedPlaceDetails?.let { (latLng, name) ->
                    PlaceDetailsPopup(
                        latLng = latLng,
                        name = name,
                        onDismiss = { selectedPlaceDetails = null },
                        onSendToMatch = {
                            placeDetailsToSend = Pair(latLng, name)
                            showSendOverlay = true
                        }
                    )
                }
                selectedUserProfile?.let { profile ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        UserProfilePopup(
                            profile = profile,
                            onProfileClick = { userIdClicked ->
                                navigateToProfile = userIdClicked
                            },
                            onCloseClick = { selectedUserProfile = null }
                        )
                    }
                }
                if (showSendOverlay) {
                    MatchesListOverlay(
                        matches = matchProfiles,
                        onDismiss = { showSendOverlay = false },
                        onSend = { selectedMatch ->
                            placeDetailsToSend?.let { (latLng, placeName) ->
                                val messageText = "Check out this place: $placeName. Directions: https://maps.google.com/?q=${latLng.latitude},${latLng.longitude}"
                                val chatId = getChatId2(userId, selectedMatch.userId)
                                val messagesRef = FirebaseRefs.db.getReference("messages/$chatId")
                                sendMessage2(userId, selectedMatch.userId, chatId, messageText, messagesRef)
                                Toast.makeText(context, "Sent place details to ${selectedMatch.name}", Toast.LENGTH_LONG).show()
                                showSendOverlay = false
                                placeDetailsToSend = null
                            }
                        }
                    )
                }
            }
        }

        // Loading Overlay
        if (isLoadingMatches || isLoadingSearch || isLoadingQuickSearch) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DirectionalArrowsOverlay(
    userLocation: LatLng,
    matchLocations: List<LatLng>,
    onArrowClick: (LatLng) -> Unit, // Callback when an arrow is tapped
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val centerX = boxWidth / 2f
        val centerY = boxHeight / 2f

        // Radius from the center where arrows will be anchored.
        val radius = (min(boxWidth, boxHeight) / 2f) - with(LocalDensity.current) { 40.dp.toPx() }

        matchLocations.forEach { matchLoc ->
            val bearing = getBearing(userLocation, matchLoc)
            val angleRad = Math.toRadians(bearing.toDouble())
            // Calculate the arrow's x and y coordinates.
            val arrowX = centerX + (radius * cos(angleRad)).toFloat()
            val arrowY = centerY - (radius * sin(angleRad)).toFloat()

            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Match Arrow",
                tint = Color.Red,
                modifier = Modifier
                    .size(40.dp)
                    .offset { IntOffset((arrowX - 20).toInt(), (arrowY - 20).toInt()) }
                    .graphicsLayer(rotationZ = bearing)
                    .clickable { onArrowClick(matchLoc) }
            )
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
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
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
                    .clickable { onDismiss() }
            )
        }
        Text(text = name, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = {
                val gmmIntentUri = Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                context.startActivity(mapIntent)
            }) { Text("Directions") }
            Spacer(modifier = Modifier.width(8.dp))
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

suspend fun searchPlacesWithOkHttp(query: String, userLocation: LatLng): List<Pair<LatLng, String>> = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val apiKey = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ"
    val requestBody = JSONObject()
        .put("textQuery", query)
        .put(
            "locationBias",
            JSONObject()
                .put(
                    "circle",
                    JSONObject()
                        .put("center", JSONObject().put("latitude", userLocation.latitude).put("longitude", userLocation.longitude))
                        .put("radius", 10000)
                )
        )
        .toString()
        .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://places.googleapis.com/v1/places:searchText")
        .addHeader("Content-Type", "application/json")
        .addHeader("X-Goog-Api-Key", apiKey)
        .addHeader("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location")
        .post(requestBody)
        .build()

    val results = mutableListOf<Pair<LatLng, String>>()
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("PlacesSearch", "Error: ${response.code} - ${response.body?.string()}")
                return@withContext emptyList()
            }
            val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
            val places = json.getJSONArray("places")
            for (i in 0 until places.length()) {
                val place = places.getJSONObject(i)
                val name = place.getJSONObject("displayName").getString("text")
                val location = place.getJSONObject("location")
                val lat = location.getDouble("latitude")
                val lng = location.getDouble("longitude")
                results.add(LatLng(lat, lng) to name)
            }
        }
    } catch (e: Exception) {
        Log.e("PlacesSearch", "Exception: ${e.message}", e)
    }
    return@withContext results
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

fun <T> checkNotEmpty(list: List<T>): Boolean = list.isNotEmpty()

data class MarkerData(val userId: String, val position: LatLng)

data class MatchProfile(
    val userId: String,
    val name: String,
    val age: Int,
    val hometown: String,
    val photoUrl: String?
)