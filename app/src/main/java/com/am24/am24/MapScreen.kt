package com.am24.am24

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
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
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapScreen(
    userId: String,
    locationManager: LocationManager,
    geoFireDatabaseRef: DatabaseReference,
    navController: NavController,
    onProfileMarkerClicked: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // UI States
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState()
    val markersState = remember { mutableStateListOf<MarkerData>() }
    val searchResultsState = remember { mutableStateListOf<Pair<LatLng, String>>() }
    var selectedPlaceDetails by remember { mutableStateOf<Pair<LatLng, String>?>(null) }
    var selectedUserProfile by remember { mutableStateOf<Profile?>(null) }
    val quickSearchItems = listOf("OYO", "hotels", "cafes", "bars", "malls")
    val matchesSet = remember { mutableStateListOf<String>() }
    var showPriceFilterDialog by remember { mutableStateOf(false) }
    var selectedPriceRange by remember { mutableStateOf("All") }
    var showAllUsers by remember { mutableStateOf(false) }
    var showMapTypeDialog by remember { mutableStateOf(false) }
    var selectedMapType by remember { mutableStateOf("West Bengal") }

    // Overlay states
    var showSendOverlay by remember { mutableStateOf(false) }
    var placeDetailsToSend by remember { mutableStateOf<Pair<LatLng, String>?>(null) }
    val matchProfiles = remember { mutableStateListOf<MatchProfile>() }
    var showLocationPrefOverlay by remember { mutableStateOf(true) }
    var allowLocationForMatches by remember { mutableStateOf(false) }
    var allowLocationForPublic by remember { mutableStateOf(false) }

    // Load Matches
    LaunchedEffect(userId) {
        val matchesRef = FirebaseDatabase.getInstance().getReference("matches").child(userId)
        matchesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                matchesSet.clear()
                for (child in snapshot.children) {
                    child.key?.let { matchesSet.add(it) }
                }
                Log.d("MapScreen", "Loaded matches: $matchesSet")
                loadUserLocationAndMatches(
                    userId,
                    locationManager,
                    geoFireDatabaseRef,
                    matchesSet,
                    markersState,
                    cameraPositionState,
                    context,
                    showAllUsers
                )
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load matches: ${error.message}", Toast.LENGTH_LONG).show()
                Log.e("MapScreen", "Matches load failed: ${error.message}")
            }
        })
    }

    LaunchedEffect(showAllUsers) {
        markersState.clear()
        loadUserLocationAndMatches(
            userId,
            locationManager,
            geoFireDatabaseRef,
            matchesSet,
            markersState,
            cameraPositionState,
            context,
            showAllUsers
        )
    }

    LaunchedEffect(showSendOverlay) {
        if (showSendOverlay && matchProfiles.isEmpty()) {
            matchesSet.forEach { matchUid ->
                val dbRef = FirebaseDatabase.getInstance().getReference("users").child(matchUid)
                dbRef.get().addOnSuccessListener { snapshot ->
                    val profile = snapshot.getValue(Profile::class.java)
                    if (profile != null) {
                        val name = profile.name
                        val age = calculateAge(profile.dob)
                        val hometown = profile.hometown
                        val photoUrl = profile.profilepicUrl
                        matchProfiles.add(MatchProfile(matchUid, name, age, hometown, photoUrl))
                    }
                }
            }
        }
    }

    // Location Preference Overlay
    if (showLocationPrefOverlay) {
        AlertDialog(
            onDismissRequest = { /* Force user to choose */ },
            title = { Text("Location Visibility Settings") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Visible to Matches")
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = allowLocationForMatches, onCheckedChange = { allowLocationForMatches = it })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Visible to Public")
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = allowLocationForPublic, onCheckedChange = { allowLocationForPublic = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showLocationPrefOverlay = false
                }) { Text("Save") }
            }
        )
    }

    // Main Layout with Tap to Unfocus
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
        // Top Row: Search, Filters
        if (showSearchBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch {
                            val queryWithFilter = if (selectedPriceRange != "All")
                                "$searchQuery, Price: $selectedPriceRange" else searchQuery
                            val results = searchPlacesWithOkHttp(queryWithFilter)
                            searchResultsState.clear()
                            searchResultsState.addAll(results)
                            selectedPlaceDetails = null
                            if (checkNotEmpty(results)) {
                                val boundsBuilder = LatLngBounds.builder()
                                results.forEach { boundsBuilder.include(it.first) }
                                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        searchQuery = ""
                        searchResultsState.clear()
                        selectedPlaceDetails = null
                        showSearchBar = false
                        focusManager.clearFocus()
                    }) { Text("Clear", color = Color.White) }
                }
                IconButton(onClick = { showPriceFilterDialog = true }) {
                    Icon(imageVector = Icons.Filled.FilterList, contentDescription = "Filter by Price")
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { showSearchBar = true }) {
                    Text("Search", color = Color.White)
                }
                Row {
                    IconButton(onClick = { showPriceFilterDialog = true }) {
                        Icon(imageVector = Icons.Filled.FilterList, contentDescription = "Filter by Price")
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showMapTypeDialog = true }) {
                        Icon(imageVector = Icons.Filled.Map, contentDescription = "Map Type")
                    }
                }
            }
        }

        // Price Filter Indicator
        if (selectedPriceRange != "All") {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, bottom = 4.dp)
                    .background(Color(0xFFFFEB3B), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Price Filter: $selectedPriceRange", color = Color.Black, fontSize = 14.sp)
            }
        }

        // Price Filter Dialog
        if (showPriceFilterDialog) {
            AlertDialog(
                onDismissRequest = { showPriceFilterDialog = false },
                title = { Text("Select Price Range") },
                text = {
                    Column {
                        listOf("All", "$", "$$", "$$$", "$$$$").forEach { price ->
                            Text(
                                text = price,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPriceRange = price
                                        showPriceFilterDialog = false
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Map Type Dialog
        if (showMapTypeDialog) {
            AlertDialog(
                onDismissRequest = { showMapTypeDialog = false },
                title = { Text("Select Map Type") },
                text = {
                    Column {
                        listOf("West Bengal", "City Map", "Neighborhood Map").forEach { mapType ->
                            Text(
                                text = mapType,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMapType = mapType
                                        showMapTypeDialog = false
                                        when (mapType) {
                                            "City Map" -> navController.navigate("cityMap/Kolkata") // Example city
                                            "Neighborhood Map" -> navController.navigate("cityMap/Kolkata") // Placeholder
                                            "West Bengal" -> navController.navigate("map")
                                        }
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Quick Search Tags
        QuickSearchTags(
            tags = quickSearchItems,
            onTagSelected = { tag ->
                scope.launch {
                    searchQuery = tag
                    val queryWithFilter = if (selectedPriceRange != "All") "$tag, Price: $selectedPriceRange" else tag
                    val results = searchPlacesWithOkHttp(queryWithFilter)
                    searchResultsState.clear()
                    searchResultsState.addAll(results)
                    selectedPlaceDetails = null
                    if (checkNotEmpty(results)) {
                        val boundsBuilder = LatLngBounds.builder()
                        results.forEach { boundsBuilder.include(it.first) }
                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
                    }
                }
            }
        )

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
                                FirebaseDatabase.getInstance().getReference("users").child(markerData.userId)
                                    .get().addOnSuccessListener { snapshot ->
                                        val profile = snapshot.getValue(Profile::class.java)
                                        if (profile != null) {
                                            val allowMatches = profile.allowLocationForMatches
                                            val allowPublic = profile.allowLocationForPublic
                                            val shouldShow = if (matchesSet.contains(markerData.userId)) allowMatches else allowPublic
                                            if (shouldShow) {
                                                selectedUserProfile = profile
                                            }
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
                        .background(Color.Black.copy(alpha = 0.3f))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                awaitFirstDown(false)
                                selectedUserProfile = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
                        contentAlignment = Alignment.Center
                    ) {
                        UserProfilePopup(
                            profile = profile,
                            onProfileClick = { userIdClicked ->
                                onProfileMarkerClicked(userIdClicked)
                                selectedUserProfile = null
                            },
                            onCloseClick = { selectedUserProfile = null }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show All Users", color = Color.Black)
                Spacer(Modifier.width(8.dp))
                Switch(checked = showAllUsers, onCheckedChange = { showAllUsers = it })
            }

            if (showSendOverlay) {
                MatchesListOverlay(
                    matches = matchProfiles,
                    onDismiss = { showSendOverlay = false },
                    onSend = { selectedMatch ->
                        placeDetailsToSend?.let { (latLng, placeName) ->
                            val messageText = "Check out this place: $placeName. Directions: https://maps.google.com/?q=${latLng.latitude},${latLng.longitude}"
                            val chatId = getChatId2(userId, selectedMatch.userId)
                            val messagesRef = FirebaseDatabase.getInstance().getReference("messages/$chatId")
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
}

// Supporting Composables and Functions

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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp).clickable { onDismiss() }
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
                                    modifier = Modifier.size(48.dp).clip(CircleShape),
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
    Column(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .width(260.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp).clickable { onCloseClick() }
            )
        }
        profile.profilepicUrl?.let { url ->
            Image(
                painter = rememberAsyncImagePainter(model = url),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(6.dp))
        RatingBar2(rating = profile.averageRating, ratingCount = profile.numberOfRatings)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onProfileClick(profile.userId) }) { Text("View Full Profile") }
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
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = String.format("%.2f (%d)", rating, ratingCount), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSearchTags(
    tags: List<String>,
    onTagSelected: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        tags.forEach { tag ->
            Box(
                Modifier
                    .padding(4.dp)
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .border(BorderStroke(1.dp, Color(0xFFFF6F00)), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable { onTagSelected(tag) }
            ) {
                Text("#$tag", color = Color.LightGray, fontSize = 10.sp)
            }
        }
    }
}

suspend fun searchPlacesWithOkHttp(query: String): List<Pair<LatLng, String>> = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val apiKey = "AIzaSyBJej3hxm7i7Nvd638k4OSMBQLjrueE9aQ" // Replace with your actual Places API key
    val requestBody = JSONObject().put("textQuery", query).toString().toRequestBody("application/json".toMediaType())
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
    context: android.content.Context,
    displayAllUsers: Boolean
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
                    FirebaseDatabase.getInstance().getReference("users").child(key).get().addOnSuccessListener { snapshot ->
                        val profile = snapshot.getValue(Profile::class.java)
                        if (profile != null) {
                            val allowMatches = profile.allowLocationForMatches
                            val allowPublic = profile.allowLocationForPublic
                            Log.d("MapScreen", "Profile for $key: allowMatches=$allowMatches, matchesSet.contains=${matchesSet.contains(key)}")
                            if (matchesSet.contains(key)) {
                                if (allowMatches) {
                                    markersState.add(MarkerData(key, LatLng(location.latitude, location.longitude)))
                                    Log.d("MapScreen", "Added match marker for $key. Markers state size: ${markersState.size}")
                                }
                            } else if (displayAllUsers) {
                                if (allowPublic) {
                                    markersState.add(MarkerData(key, LatLng(location.latitude, location.longitude)))
                                    Log.d("MapScreen", "Added public marker for $key. Markers state size: ${markersState.size}")
                                }
                            }
                        } else {
                            Log.e("MapScreen", "Failed to parse profile for $key")
                        }
                    }
                }
                override fun onKeyExited(key: String) {
                    markersState.removeAll { it.userId == key }
                    Log.d("MapScreen", "Key exited: $key. Markers state size: ${markersState.size}")
                }
                override fun onKeyMoved(key: String, location: GeoLocation) {
                    if (displayAllUsers || matchesSet.contains(key)) {
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

fun <T> checkNotEmpty(list: List<T>): Boolean {
    return list.isNotEmpty()
}

data class MarkerData(val userId: String, val position: LatLng)

data class MatchProfile(
    val userId: String,
    val name: String,
    val age: Int,
    val hometown: String,
    val photoUrl: String?
)