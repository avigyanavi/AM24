@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.am24.am24

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider


@Composable
fun LeaderboardScreen(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    )

    val isPremium by profileViewModel.isPremium.collectAsState(false)
    val leaderboardVm: LeaderboardViewModel? =
        if (isPremium) viewModel() else null
    val profilesState = leaderboardVm?.leaderboard?.collectAsState(emptyList())
    val profiles = profilesState?.value ?: emptyList()

    var countryFilter    by remember { mutableStateOf("") }   // NEW
    // local UI filter state
    var selectedGender   by remember { mutableStateOf("") }
    var cityFilter       by remember { mutableStateOf("") }
    var localityFilter   by remember { mutableStateOf("") }
    var highSchoolFilter by remember { mutableStateOf("") }
    var collegeFilter    by remember { mutableStateOf("") }
    // for age range slider:
    var ageRange by remember { mutableStateOf(18f..100f) }
    // existing composite slider in percent 0–100:
    var minComposite by remember { mutableStateOf(0f) }
    var minSwipeRights by remember { mutableStateOf(0f) }
    // collapse/expand filters
    var filtersExpanded by remember { mutableStateOf(false) }

    // sync back into VM
    LaunchedEffect(selectedGender, leaderboardVm) {
        leaderboardVm?.setGenderFilter(selectedGender.ifBlank { null })
    }
    LaunchedEffect(countryFilter, leaderboardVm)  {
        leaderboardVm?.setCountryFilter(countryFilter.ifBlank { null })
    } // NEW
    LaunchedEffect(cityFilter, leaderboardVm)     {
        leaderboardVm?.setCityFilter(cityFilter.ifBlank { null })
    }
    LaunchedEffect(localityFilter, leaderboardVm) {
        leaderboardVm?.setLocalityFilter(localityFilter.ifBlank { null })
    }
    LaunchedEffect(highSchoolFilter, leaderboardVm) {
        leaderboardVm?.setHighSchoolFilter(highSchoolFilter.ifBlank { null })
    }
    LaunchedEffect(collegeFilter, leaderboardVm)  {
        leaderboardVm?.setCollegeFilter(collegeFilter.ifBlank { null })
    }
    LaunchedEffect(ageRange, leaderboardVm) {
        leaderboardVm?.setAgeRangeFilter(
            ageRange.start.roundToInt(),
            ageRange.endInclusive.roundToInt()
        )
    }
    LaunchedEffect(minComposite, leaderboardVm) {
        leaderboardVm?.setMinCompositePct(minComposite.toDouble())
    }
    LaunchedEffect(minSwipeRights, leaderboardVm) {
        leaderboardVm?.setMinSwipeRights(minSwipeRights.roundToInt())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Collapsible Filters card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(8.dp),
                    elevation = cardElevation(defaultElevation = 4.dp),
                    colors = cardColors(containerColor = Color(0xFF121212))
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { filtersExpanded = !filtersExpanded }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Filters",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (filtersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        if (filtersExpanded) {
                            Divider(color = Color.Gray.copy(alpha = 0.3f))
                            Spacer(Modifier.height(12.dp))
                            LeaderboardFilters(
                                selectedGender = selectedGender,
                                onGenderChange = { selectedGender = it },
                                countryFilter = countryFilter,       // NEW
                                onCountryChange = { countryFilter = it }, // NEW
                                cityFilter = cityFilter,
                                onCityChange = { cityFilter = it },
                                localityFilter = localityFilter,
                                onLocalityChange = { localityFilter = it },
                                highSchoolFilter = highSchoolFilter,
                                onHighSchoolChange = { highSchoolFilter = it },
                                collegeFilter = collegeFilter,
                                onCollegeChange = { collegeFilter = it },
                                ageRange = ageRange,
                                onAgeRangeChange = { ageRange = it },
                                minComposite = minComposite,
                                onMinCompositeChange = { minComposite = it },
                                minSwipeRights = minSwipeRights,
                                onMinSwipeRightsChange = { minSwipeRights = it }
                            )
                        }
                    }
                }
            }

            if (leaderboardVm == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Leaderboard is a Premium feature", color = Color.White)
                    }
                }
            } else if (profiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_significant_rated_users_yet),
                            color = Color.White
                        )
                    }
                }
            } else {
                itemsIndexed(profiles) { index, profile ->
                    LeaderboardRow(rank = index + 1, profile = profile)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LeaderboardFilters(
    selectedGender: String,
    onGenderChange: (String) -> Unit,
    countryFilter: String,                     // NEW
    onCountryChange: (String) -> Unit,         // NEW
    cityFilter: String,
    onCityChange: (String) -> Unit,
    localityFilter: String,
    onLocalityChange: (String) -> Unit,
    highSchoolFilter: String,
    onHighSchoolChange: (String) -> Unit,
    collegeFilter: String,
    onCollegeChange: (String) -> Unit,
    ageRange: ClosedFloatingPointRange<Float>,
    onAgeRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    minComposite: Float,
    onMinCompositeChange: (Float) -> Unit,
    minSwipeRights: Float,
    onMinSwipeRightsChange: (Float) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        // 1️⃣ Gender chips
        FlowRow(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("" to "All", "Male" to "Male", "Female" to "Female", "Either" to "Either")
                .forEach { (value, label) ->
                    FilterChip(
                        selected = selectedGender == value,
                        onClick  = { onGenderChange(value) },
                        label    = { Text(label) }
                    )
                }
        }

        Spacer(Modifier.height(16.dp))

        /* ------------------ Country (ExposedDropdownMenuBox) ------------------ */
        var countryExpanded by remember { mutableStateOf(false) }
        val countryOptions = stringArrayResource(R.array.country_names).toList()
        ExposedDropdownMenuBox(
            expanded = countryExpanded,
            onExpandedChange = { countryExpanded = it }
        ) {
            TextField(
                value = countryFilter,
                onValueChange = {},            // read-only – selection comes from menu
                readOnly = true,
                label = { Text("Country") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(countryExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = TextFieldDefaults.colors(cursorColor = KupidxOrange)
            )
            ExposedDropdownMenu(
                expanded = countryExpanded,
                onDismissRequest = { countryExpanded = false }
            ) {
                countryOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onCountryChange(option)
                            countryExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 2️⃣ Dropdowns instead of free-text
        // Example: City
        var cityExpanded by remember { mutableStateOf(false) }
        val cityOptions = stringArrayResource(R.array.city_names).toList()
        ExposedDropdownMenuBox(
            expanded = cityExpanded,
            onExpandedChange = { cityExpanded = it }
        ) {
            TextField(
                value = cityFilter,
                onValueChange = { /* no-op, handled by menu */ },
                readOnly = true,
                label = { Text("City") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(),
                colors = TextFieldDefaults.colors(cursorColor = KupidxOrange)
            )
            ExposedDropdownMenu(
                expanded = cityExpanded,
                onDismissRequest = { cityExpanded = false }
            ) {
                cityOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onCityChange(option)
                            cityExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Locality dropdown (same pattern)
        var localityExpanded by remember { mutableStateOf(false) }
        /* ------------------ Locality ------------------ */
        val ctx = LocalContext.current
        val localityArrayName =
            "localities_" + cityFilter
                .lowercase()
                .replace(' ', '_')        // “New Town” → “new_town”
                .replace('-', '_')        // “Secunderabad-Jubilee” → …
        val localityResId = remember(cityFilter) {
            ctx.resources.getIdentifier(localityArrayName, "array", ctx.packageName)
        }
        val localityOptions: List<String> =
            if (localityResId != 0)
                stringArrayResource(id = localityResId).toList()
            else
                listOf("Other")           // graceful fallback
        ExposedDropdownMenuBox(
            expanded = localityExpanded,
            onExpandedChange = { localityExpanded = it }
        ) {
            TextField(
                value = localityFilter,
                readOnly = true,
                onValueChange = {},
                label = { Text("Locality") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(localityExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(),
                colors = TextFieldDefaults.colors(cursorColor = KupidxOrange)
            )
            ExposedDropdownMenu(
                expanded = localityExpanded,
                onDismissRequest = { localityExpanded = false }
            ) {
                localityOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onLocalityChange(option)
                            localityExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // High School dropdown
        var hsExpanded by remember { mutableStateOf(false) }
        val hsOptions = listOf("Andrews HS", "Don Bosco", "Other")
        ExposedDropdownMenuBox(
            expanded = hsExpanded,
            onExpandedChange = { hsExpanded = it }
        ) {
            TextField(
                value = highSchoolFilter,
                readOnly = true,
                onValueChange = {},
                label = { Text("High School") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(hsExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(),
                colors = TextFieldDefaults.colors(cursorColor = KupidxOrange)
            )
            ExposedDropdownMenu(
                expanded = hsExpanded,
                onDismissRequest = { hsExpanded = false }
            ) {
                hsOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onHighSchoolChange(option)
                            hsExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // College dropdown
        var collExpanded by remember { mutableStateOf(false) }
        val collOptions = listOf("Calcutta Univ", "IIT Kharagpur", "Other")
        ExposedDropdownMenuBox(
            expanded = collExpanded,
            onExpandedChange = { collExpanded = it }
        ) {
            TextField(
                value = collegeFilter,
                readOnly = true,
                onValueChange = {},
                label = { Text("College") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(collExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(),
                colors = TextFieldDefaults.colors(cursorColor = KupidxOrange)
            )
            ExposedDropdownMenu(
                expanded = collExpanded,
                onDismissRequest = { collExpanded = false }
            ) {
                collOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onCollegeChange(option)
                            collExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 3️⃣ Age range slider
        Text("Age: ${ageRange.start.roundToInt()} – ${ageRange.endInclusive.roundToInt()}", color = Color.White)
        RangeSlider(
            value = ageRange,
            onValueChange = { onAgeRangeChange(it) },
            valueRange = 18f..100f,
            steps = 82,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6F00),
                activeTrackColor = Color(0xFFFF6F00)
            )
        )

        Spacer(Modifier.height(16.dp))

        // 4️⃣ Composite slider (unchanged)
        Text("Min composite: ${minComposite.roundToInt()}%", color = Color.White)
        Slider(
            value = minComposite / 100f,
            onValueChange = { onMinCompositeChange(it * 100f) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6000),
                activeTrackColor = Color(0xFFFF6000),
                inactiveTrackColor = Color.Gray
            )
        )
        Spacer(Modifier.height(16.dp))

        Text("Min likes: ${minSwipeRights.roundToInt()}", color = Color.White)
        Slider(
            value = minSwipeRights,
            onValueChange = onMinSwipeRightsChange,
            valueRange = 0f..200f,
            modifier = Modifier.fillMaxWidth(),
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6F00),
                activeTrackColor = Color(0xFFFF6F00),
                inactiveTrackColor = Color.Gray
            )
        )
    }
}

@Composable
fun LeaderboardRow(rank: Int, profile: Profile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = cardElevation(defaultElevation = 4.dp),
        colors = cardColors(containerColor = Color(0xFF121212))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.width(32.dp)
            )

            Spacer(Modifier.width(8.dp))

            val placeholder = painterResource(R.drawable.local_placeholder)
            val displayName = profile.name.ifBlank { profile.username }
            AsyncImage(
                model = profile.profilepicUrl.takeIf { !it.isNullOrBlank() },
                contentDescription = displayName,
                placeholder = placeholder,
                error = placeholder,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = "${profile.compositeScorePct.roundToInt()} %",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "Matches: ${profile.matchCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                val likes = profile.numberOfSwipeRights
                val totalSwipes = profile.numberOfUsersWhoSwiped.coerceAtLeast(0)
                val dislikes = (totalSwipes - likes).coerceAtLeast(0)

                RatingBarFromLikes(likes = likes, dislikes = dislikes)
            }
        }
    }
}
