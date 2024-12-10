@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import DatingViewModel
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

@Composable
fun FiltersScreen(
    datingViewModel: DatingViewModel,
    postViewModel: PostViewModel,
    initialTab: Int = 0 // Default to 0 (Dating Tab)
) {
    val context = LocalContext.current

    // State for selected tab
    var selectedTab by remember { mutableStateOf(initialTab) } // 0 for Dating Filters, 1 for Feed Filters

    // City selection
    val cities = listOf("All", "Kolkata", "Mumbai", "Delhi")
    var selectedCity by remember { mutableStateOf("All") }

    // Observe dating filters from the DatingViewModel
    val datingFilters by datingViewModel.datingFilters.collectAsState()

    // City-based data mappings
    val cityLocalitiesMap = mapOf(
        "Kolkata" to listOf("North Kolkata", "South Kolkata", "East Kolkata", "West Kolkata"),
        "Mumbai" to listOf("Andheri", "Bandra", "Juhu", "Colaba"),
        "Delhi" to listOf("South Delhi", "North Delhi", "East Delhi", "West Delhi")
    )
    val cityHighSchoolsMap = mapOf(
        "Kolkata" to listOf("Kolkata High School 1", "Kolkata High School 2"),
        "Mumbai" to listOf("Mumbai High School 1", "Mumbai High School 2"),
        "Delhi" to listOf("Delhi High School 1", "Delhi High School 2")
    )
    val cityCollegesMap = mapOf(
        "Kolkata" to listOf("Kolkata College 1", "Kolkata College 2"),
        "Mumbai" to listOf("Mumbai College 1", "Mumbai College 2"),
        "Delhi" to listOf("Delhi College 1", "Delhi College 2")
    )
    val cityPostGradMap = mapOf(
        "Kolkata" to listOf("IIM Calcutta", "Jadavpur University"),
        "Mumbai" to listOf("IIT Bombay", "Mumbai University"),
        "Delhi" to listOf("IIT Delhi", "Delhi University")
    )
    val cityWorkMap = mapOf(
        "Kolkata" to listOf("TCS", "Wipro"),
        "Mumbai" to listOf("Reliance", "Tata Motors"),
        "Delhi" to listOf("Infosys", "HCL")
    )

    // Dating Filters
    var datingLocalities by remember { mutableStateOf(setOf<String>()) }
    var datingHighSchool by remember { mutableStateOf("") }
    var datingCollege by remember { mutableStateOf("") }
    var datingPostGrad by remember { mutableStateOf("") }
    var datingWork by remember { mutableStateOf("") }
    var datingRating by remember { mutableStateOf("") }
    var datingAgeRange by remember { mutableStateOf(18..30) }
    var datingDistance by remember { mutableStateOf(10) }
    var datingGender by remember { mutableStateOf("") }
    var datingCity by remember { mutableStateOf("All") }

    // Feed Filters
    var feedLocalities by remember { mutableStateOf(setOf<String>()) }
    var feedHighSchool by remember { mutableStateOf("") }
    var feedCollege by remember { mutableStateOf("") }
    var feedPostGrad by remember { mutableStateOf("") }
    var feedWork by remember { mutableStateOf("") }
    var feedRating by remember { mutableStateOf("") }
    var feedAgeRange by remember { mutableStateOf(18..70) }
    var feedGender by remember { mutableStateOf("") }
    var feedCity by remember { mutableStateOf("All") }

    // Dynamic Localities based on selected city
    val allLocalities = if (selectedCity != "All") {
        cityLocalitiesMap[selectedCity] ?: emptyList()
    } else {
        emptyList()
    }
    var searchQueryLocalities by remember { mutableStateOf("") }
    val filteredLocalities = allLocalities.filter { it.contains(searchQueryLocalities, ignoreCase = true) }

    fun saveFilters(
        filterSettings: FilterSettings,
        context: android.content.Context
    ) {
        val updates = mapOf(
            "datingFilters" to mapOf(
                "localities" to filterSettings.datingFilters.localities,
                "city" to filterSettings.datingFilters.city,
                "highSchool" to filterSettings.datingFilters.highSchool,
                "college" to filterSettings.datingFilters.college,
                "postGrad" to filterSettings.datingFilters.postGrad,
                "work" to filterSettings.datingFilters.work,
                "ageStart" to filterSettings.datingFilters.ageStart,
                "ageEnd" to filterSettings.datingFilters.ageEnd,
                "distance" to filterSettings.datingFilters.distance,
                "gender" to filterSettings.datingFilters.gender,
                "rating" to filterSettings.datingFilters.rating
            ),
            "feedFilters" to mapOf(
                "localities" to filterSettings.feedFilters.localities,
                "city" to filterSettings.feedFilters.city,
                "highSchool" to filterSettings.feedFilters.highSchool,
                "college" to filterSettings.feedFilters.college,
                "postGrad" to filterSettings.feedFilters.postGrad,
                "work" to filterSettings.feedFilters.work,
                "ageStart" to filterSettings.feedFilters.ageStart,
                "ageEnd" to filterSettings.feedFilters.ageEnd,
                "gender" to filterSettings.feedFilters.gender,
                "rating" to filterSettings.feedFilters.rating
            )
        )

        FirebaseDatabase.getInstance().reference.child("users")
            .child(FirebaseAuth.getInstance().currentUser?.uid ?: return)
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Filters are saved!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to save filters.", Toast.LENGTH_SHORT).show()
            }
    }


    // Load Filters from Firebase
    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
            try {
                val snapshot = userRef.get().await()
                val filterSettings = snapshot.getValue(FilterSettings::class.java)
                if (filterSettings != null) {
                    // Update state variables with loaded filter settings

                    // Update selected city
                    selectedCity = filterSettings.datingFilters.city
                    datingCity = filterSettings.datingFilters.city
                    feedCity = filterSettings.feedFilters.city

                    // Update dating filters
                    datingLocalities = filterSettings.datingFilters.localities.toSet()
                    datingHighSchool = filterSettings.datingFilters.highSchool
                    datingCollege = filterSettings.datingFilters.college
                    datingPostGrad = filterSettings.datingFilters.postGrad
                    datingWork = filterSettings.datingFilters.work
                    datingAgeRange = filterSettings.datingFilters.ageStart..filterSettings.datingFilters.ageEnd
                    datingDistance = filterSettings.datingFilters.distance
                    datingGender = filterSettings.datingFilters.gender
                    datingRating = filterSettings.datingFilters.rating

                    // Update feed filters
                    feedLocalities = filterSettings.feedFilters.localities.toSet()
                    feedHighSchool = filterSettings.feedFilters.highSchool
                    feedCollege = filterSettings.feedFilters.college
                    feedPostGrad = filterSettings.feedFilters.postGrad
                    feedWork = filterSettings.feedFilters.work
                    feedAgeRange = filterSettings.feedFilters.ageStart..filterSettings.feedFilters.ageEnd
                    feedGender = filterSettings.feedFilters.gender
                    feedRating = filterSettings.feedFilters.rating
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load filters: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val filterSettings = FilterSettings(
                filterOption = "everyone",
                datingFilters = DatingFilterSettings(
                    localities = datingLocalities.toList(),
                    city = datingCity,
                    highSchool = datingHighSchool,
                    college = datingCollege,
                    postGrad = datingPostGrad,
                    work = datingWork,
                    ageStart = datingAgeRange.start,
                    ageEnd = datingAgeRange.endInclusive,
                    distance = datingDistance,
                    gender = datingGender,
                    rating = datingRating
                ),
                feedFilters = FeedFilterSettings(
                    localities = feedLocalities.toList(),
                    city = feedCity,
                    highSchool = feedHighSchool,
                    college = feedCollege,
                    postGrad = feedPostGrad,
                    work = feedWork,
                    ageStart = feedAgeRange.start,
                    ageEnd = feedAgeRange.endInclusive,
                    gender = feedGender,
                    rating = feedRating
                ),
                sortOption = "No Sort",
                searchQuery = ""
            )

            // Save to Firebase
            saveFilters(filterSettings, context)

            // Update ViewModel
            saveFeedFiltersToViewModel(
                postViewModel = postViewModel,
                feedLocalities = feedLocalities,
                feedHighSchool = feedHighSchool,
                feedCollege = feedCollege,
                feedPostGrad = feedPostGrad,
                feedWork = feedWork,
                feedRating = feedRating,
                feedAgeRange = feedAgeRange,
                feedGender = feedGender,
                feedCity = feedCity
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // City Dropdown
        Text(text = "Select City", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
        CityDropdown(
            cities = cities,
            selectedCity = selectedCity,
            onCitySelected = { city ->
                selectedCity = city
                datingCity = city
                feedCity = city
                // Reset localities and other selections when city changes
                datingLocalities = emptySet()
                feedLocalities = emptySet()
                searchQueryLocalities = ""
            }
        )

        // Display message if "All" is selected
        if (selectedCity == "All") {
            Text(
                text = "Select a city for its localities to show up",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Tab Selector
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Black,
            contentColor = Color(0xFFFFA500),
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFFFFA500)
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Dating Filters", color = if (selectedTab == 0) Color(0xFFFFA500) else Color.White) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Feed Filters", color = if (selectedTab == 1) Color(0xFFFFA500) else Color.White) }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val localities = if (selectedTab == 0) datingLocalities else feedLocalities
                val onLocalityChange: (String) -> Unit = { locality ->
                    if (selectedTab == 0) {
                        datingLocalities = datingLocalities.toggleImmutable(locality)
                    } else {
                        feedLocalities = feedLocalities.toggleImmutable(locality)
                    }
                }

                val highSchoolOptions = if (selectedCity != "All") cityHighSchoolsMap[selectedCity] ?: emptyList() else emptyList()
                val collegeOptions = if (selectedCity != "All") cityCollegesMap[selectedCity] ?: emptyList() else emptyList()
                val postGradOptions = if (selectedCity != "All") cityPostGradMap[selectedCity] ?: emptyList() else emptyList()
                val workOptions = if (selectedCity != "All") cityWorkMap[selectedCity] ?: emptyList() else emptyList()

                FilterFields(
                    localities = localities,
                    onLocalityChange = onLocalityChange,
                    searchQueryLocalities = searchQueryLocalities,
                    onSearchLocalitiesChange = { searchQueryLocalities = it },
                    filteredLocalities = filteredLocalities,
                    highSchool = if (selectedTab == 0) datingHighSchool else feedHighSchool,
                    onHighSchoolChange = { newHighSchool ->
                        if (selectedTab == 0) datingHighSchool = newHighSchool else feedHighSchool = newHighSchool
                    },
                    highSchoolOptions = highSchoolOptions,
                    college = if (selectedTab == 0) datingCollege else feedCollege,
                    onCollegeChange = { newCollege ->
                        if (selectedTab == 0) datingCollege = newCollege else feedCollege = newCollege
                    },
                    collegeOptions = collegeOptions,
                    postGrad = if (selectedTab == 0) datingPostGrad else feedPostGrad,
                    onPostGradChange = { newPostGrad ->
                        if (selectedTab == 0) datingPostGrad = newPostGrad else feedPostGrad = newPostGrad
                    },
                    postGradOptions = postGradOptions,
                    work = if (selectedTab == 0) datingWork else feedWork,
                    onWorkChange = { newWork ->
                        if (selectedTab == 0) datingWork = newWork else feedWork = newWork
                    },
                    workOptions = workOptions,
                    ageRange = if (selectedTab == 0) datingAgeRange else feedAgeRange,
                    onAgeRangeChange = { newRange ->
                        if (selectedTab == 0) datingAgeRange = newRange else feedAgeRange = newRange
                    },
                    rating = if (selectedTab == 0) datingRating else feedRating,
                    onRatingChange = { newRating ->
                        if (selectedTab == 0) datingRating = toggleSelection(datingRating, newRating)
                        else feedRating = toggleSelection(feedRating, newRating)
                    },
                    distance = if (selectedTab == 0) datingDistance else null, // Remove distance for feed filters
                    onDistanceChange = { newDistance ->
                        if (selectedTab == 0) datingDistance = newDistance
                    },
                    gender = if (selectedTab == 0) datingGender else feedGender,
                    onGenderChange = { newGender ->
                        if (selectedTab == 0) datingGender = toggleSelection(datingGender, newGender)
                        else feedGender = toggleSelection(feedGender, newGender)
                    }
                )
            }
        }
    }
}

// Extension to toggle items in Immutable Set
fun Set<String>.toggleImmutable(item: String): Set<String> {
    return if (contains(item)) this - item else this + item
}

fun toggleSelection(currentValue: String, newValue: String): String {
    return if (currentValue == newValue) "" else newValue
}

@Composable
fun FilterFields(
    localities: Set<String>,
    onLocalityChange: (String) -> Unit,
    searchQueryLocalities: String,
    onSearchLocalitiesChange: (String) -> Unit,
    filteredLocalities: List<String>,
    highSchool: String,
    onHighSchoolChange: (String) -> Unit,
    highSchoolOptions: List<String>,
    college: String,
    onCollegeChange: (String) -> Unit,
    collegeOptions: List<String>,
    postGrad: String,
    onPostGradChange: (String) -> Unit,
    postGradOptions: List<String>,
    work: String,
    onWorkChange: (String) -> Unit,
    workOptions: List<String>,
    ageRange: IntRange = 18..30,
    onAgeRangeChange: (IntRange) -> Unit = {},
    distance: Int? = null, // Nullable to remove distance for feed filters
    onDistanceChange: (Int) -> Unit = {},
    rating: String = "",
    onRatingChange: (String) -> Unit = {},
    gender: String = "",
    onGenderChange: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        // Show localities only if a city is selected
        if (filteredLocalities.isNotEmpty()) {
            // Searchable Localities
            Text(
                text = "Search Localities",
                color = Color.White,
                fontSize = 16.sp
            )
            OutlinedTextField(
                value = searchQueryLocalities,
                onValueChange = onSearchLocalitiesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Type locality name", color = Color.White) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF4500),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.White,
                    focusedTextColor = Color.White,
                    cursorColor = Color(0xFFFF4500),
                    containerColor = Color.Black
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.Black)
            ) {
                items(items = filteredLocalities) { locality ->
                    Button(
                        onClick = { onLocalityChange(locality) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (localities.contains(locality)) Color(0xFFFFA500) else Color.Transparent,
                            contentColor = if (localities.contains(locality)) Color.Black else Color.White
                        ),
                        border = if (localities.contains(locality)) null else BorderStroke(1.dp, Color(0xFFFF4500)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .height(40.dp)
                    ) {
                        Text(text = locality, fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Age Range Slider
        Text(text = "Age Range: ${ageRange.start} - ${ageRange.endInclusive}", color = Color.White, fontSize = 16.sp)
        RangeSlider(
            value = ageRange.start.toFloat()..ageRange.endInclusive.toFloat(),
            onValueChange = { range -> onAgeRangeChange(range.start.roundToInt()..range.endInclusive.roundToInt()) },
            valueRange = 18f..100f,
            steps = 82,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFA500),
                activeTrackColor = Color(0xFFFFA500),
                inactiveTrackColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

// Distance Slider (only for dating filters)
        if (distance != null) {
            val displayedDistanceText = if (distance == 100) "Global" else "$distance km"
            Text(text = "Distance: $displayedDistanceText", color = Color.White, fontSize = 16.sp)
            Slider(
                value = distance.toFloat(),
                onValueChange = { newDistance -> onDistanceChange(newDistance.roundToInt()) },
                valueRange = 0f..100f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFA500),
                    activeTrackColor = Color(0xFFFFA500),
                    inactiveTrackColor = Color.Gray
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Rating Buttons
        Text(text = "Rating", color = Color.White, fontSize = 16.sp)
        SelectionButtons(
            options = listOf("0-2", "2-4", "4-5"),
            selectedOption = rating,
            onOptionSelected = onRatingChange,
            vertical = false
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Gender Buttons
        Text(text = "Gender", color = Color.White, fontSize = 16.sp)
        SelectionButtons(
            options = listOf("Male", "Female", "Other"),
            selectedOption = gender,
            onOptionSelected = onGenderChange,
            vertical = false
        )
        Spacer(modifier = Modifier.height(16.dp))

        // High School Searchable Dropdown
        SearchableDropdown(
            title = "High School",
            options = highSchoolOptions,
            selectedOption = highSchool,
            onOptionSelected = onHighSchoolChange
        )
        Spacer(modifier = Modifier.height(16.dp))

        // College Searchable Dropdown
        SearchableDropdown(
            title = "College",
            options = collegeOptions,
            selectedOption = college,
            onOptionSelected = onCollegeChange
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Post Graduation Searchable Dropdown
        SearchableDropdown(
            title = "Post Graduation",
            options = postGradOptions,
            selectedOption = postGrad,
            onOptionSelected = onPostGradChange
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Work Searchable Dropdown
        SearchableDropdown(
            title = "Work",
            options = workOptions,
            selectedOption = work,
            onOptionSelected = onWorkChange
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

fun saveFeedFiltersToViewModel(
    postViewModel: PostViewModel,
    feedLocalities: Set<String>,
    feedHighSchool: String,
    feedCollege: String,
    feedPostGrad: String,
    feedWork: String,
    feedRating: String,
    feedAgeRange: IntRange,
    feedGender: String,
    feedCity: String
) {
    val currentSortOption = postViewModel.filterSettings.value.sortOption
    postViewModel.setFeedFilters(
        FilterSettings(
            filterOption = "everyone",
            feedFilters = FeedFilterSettings(
                localities = feedLocalities.toList(),
                city = feedCity,
                highSchool = feedHighSchool,
                college = feedCollege,
                postGrad = feedPostGrad,
                work = feedWork,
                ageStart = feedAgeRange.start,
                ageEnd = feedAgeRange.endInclusive,
                gender = feedGender,
                rating = feedRating
            ),
            sortOption = currentSortOption,
            searchQuery = ""
        )
    )
}

@Composable
fun SelectionButtons(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    vertical: Boolean = false
) {
    if (vertical) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                Button(
                    onClick = { onOptionSelected(option) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (option == selectedOption) Color(0xFFFFA500) else Color.Transparent,
                        contentColor = if (option == selectedOption) Color.Black else Color.White
                    ),
                    border = if (option == selectedOption) null else BorderStroke(1.dp, Color(0xFFFF4500)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(text = option, fontSize = 14.sp)
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                Button(
                    onClick = { onOptionSelected(option) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (option == selectedOption) Color(0xFFFFA500) else Color.Transparent,
                        contentColor = if (option == selectedOption) Color.Black else Color.White
                    ),
                    border = if (option == selectedOption) null else BorderStroke(1.dp, Color(0xFFFF4500)),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text(text = option, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun CityDropdown(
    cities: List<String>,
    selectedCity: String,
    onCitySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFFFF4500)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA500))
        ) {
            Text(text = selectedCity.ifEmpty { "Select City" }, color = Color(0xFFFFA500))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city, color = Color.White) },
                    onClick = {
                        onCitySelected(city)
                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
fun SearchableDropdown(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, color = Color.White, fontSize = 16.sp)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFFFF4500)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA500))
        ) {
            Text(
                text = selectedOption.ifEmpty { "Select or type" },
                color = Color(0xFFFFA500)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Search", color = Color(0xFFFFA500)) },
                textStyle = TextStyle(color = Color.White),
                colors = TextFieldDefaults.textFieldColors(
                    cursorColor = Color(0xFFFFA500),
                    focusedIndicatorColor = Color(0xFFFFA500),
                    focusedLabelColor = Color(0xFFFFA500),
                    containerColor = Color.Black
                )
            )
            options.filter { it.contains(searchText, ignoreCase = true) }
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onOptionSelected(option)
                            searchText = ""
                            expanded = false
                        }
                    )
                }
        }
    }
}
