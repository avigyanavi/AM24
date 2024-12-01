@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterial3Api::class
)

package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import kotlin.math.roundToInt

@Composable
fun FiltersScreen(
    postViewModel: PostViewModel,
    initialTab: Int = 0 // Default to 0 (Dating Tab)
) {
    val context = LocalContext.current

    // State for selected tab
    var selectedTab by remember { mutableStateOf(initialTab) } // 0 for Dating Filters, 1 for Feed Filters

    // City selection
    val cities = listOf("Kolkata", "Mumbai", "Delhi")
    var selectedCity by remember { mutableStateOf("") }

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
    // Add postgrad and work mappings similarly

    // Feed Filters
    var feedLocalities by remember { mutableStateOf(setOf<String>()) } // Immutable Set
    var feedHighSchool by remember { mutableStateOf("") }
    var feedCollege by remember { mutableStateOf("") }
    var feedRating by remember { mutableStateOf("") }
    var feedAgeRange by remember { mutableStateOf(18..70) }
    var feedDistance by remember { mutableStateOf(10) }
    var feedGender by remember { mutableStateOf("") }

    // Dating Filters
    var datingLocalities by remember { mutableStateOf(setOf<String>()) } // Immutable Set
    var datingHighSchool by remember { mutableStateOf("") }
    var datingCollege by remember { mutableStateOf("") }
    var datingRating by remember { mutableStateOf("") }
    var datingAgeRange by remember { mutableStateOf(18..30) }
    var datingDistance by remember { mutableStateOf(10) }
    var datingGender by remember { mutableStateOf("") }


    // Dynamic Localities based on selected city
    val allLocalities = cityLocalitiesMap[selectedCity] ?: emptyList()
    var searchQueryLocalities by remember { mutableStateOf("") }
    val filteredLocalities = allLocalities.filter { it.contains(searchQueryLocalities, ignoreCase = true) }

    // Save Filters Function
    fun saveFilters(
        filterSettings: FilterSettings,
        context: android.content.Context
    ) {
        val updates = mapOf(
            "filterOption" to filterSettings.filterOption,
            "datingFilters" to mapOf(
                "localities" to filterSettings.datingFilters.localities,
                "highSchool" to filterSettings.datingFilters.highSchool,
                "college" to filterSettings.datingFilters.college,
                "ageStart" to filterSettings.datingFilters.ageStart,
                "ageEnd" to filterSettings.datingFilters.ageEnd,
                "distance" to filterSettings.datingFilters.distance,
                "gender" to filterSettings.datingFilters.gender,
                "rating" to filterSettings.datingFilters.rating
            ),
            "feedFilters" to mapOf(
                "localities" to filterSettings.feedFilters.localities,
                "highSchool" to filterSettings.feedFilters.highSchool,
                "college" to filterSettings.feedFilters.college,
                "ageStart" to filterSettings.feedFilters.ageStart,
                "ageEnd" to filterSettings.feedFilters.ageEnd,
                "distance" to filterSettings.feedFilters.distance,
                "gender" to filterSettings.feedFilters.gender,
                "rating" to filterSettings.feedFilters.rating
            ),
            "sortOption" to filterSettings.sortOption,
            "searchQuery" to filterSettings.searchQuery
        )

        FirebaseDatabase.getInstance().reference.child("users")
            .child(FirebaseAuth.getInstance().currentUser?.uid ?: return)
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Filters saved successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to save filters.", Toast.LENGTH_SHORT).show()
            }
    }


    DisposableEffect(Unit) {
        onDispose {
            val filterSettings = FilterSettings(
                filterOption = "everyone",
                datingFilters = DatingFilterSettings(
                    localities = datingLocalities.toList(),
                    highSchool = datingHighSchool,
                    college = datingCollege,
                    ageStart = datingAgeRange.start,
                    ageEnd = datingAgeRange.endInclusive,
                    distance = datingDistance,
                    gender = datingGender,
                    rating = datingRating
                ),
                feedFilters = FeedFilterSettings(
                    localities = feedLocalities.toList(),
                    highSchool = feedHighSchool,
                    college = feedCollege,
                    ageStart = feedAgeRange.start,
                    ageEnd = feedAgeRange.endInclusive,
                    distance = feedDistance,
                    gender = feedGender,
                    rating = feedRating
                ),
                sortOption = "timestamp",
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
                feedRating = feedRating,
                feedAgeRange = feedAgeRange,
                feedDistance = feedDistance,
                feedGender = feedGender
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
                // Reset localities and other selections when city changes
                datingLocalities = emptySet()
                feedLocalities = emptySet()
                searchQueryLocalities = ""
            }
        )

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

                val highSchoolOptions = cityHighSchoolsMap[selectedCity] ?: emptyList()
                val collegeOptions = cityCollegesMap[selectedCity] ?: emptyList()
                // Add postgradOptions and workOptions similarly



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
                    ageRange = if (selectedTab == 0) datingAgeRange else feedAgeRange,
                    onAgeRangeChange = { newRange ->
                        if (selectedTab == 0) datingAgeRange = newRange else feedAgeRange = newRange
                    },
                    rating = if (selectedTab == 0) datingRating else feedRating,
                    onRatingChange = { newRating ->
                        if (selectedTab == 0) datingRating = toggleSelection(datingRating, newRating)
                        else feedRating = toggleSelection(feedRating, newRating)
                    },
                    distance = if (selectedTab == 0) datingDistance else feedDistance,
                    onDistanceChange = { newDistance ->
                        if (selectedTab == 0) datingDistance = newDistance else feedDistance = newDistance
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
    // Add postgrad and work parameters similarly
    ageRange: IntRange = 18..30,
    onAgeRangeChange: (IntRange) -> Unit = {},
    distance: Int = 10,
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

        // Distance Slider
        Text(text = "Distance: $distance km", color = Color.White, fontSize = 16.sp)
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

        // Rating Buttons
        Text(text = "Rating", color = Color.White, fontSize = 16.sp)
        SelectionButtons(
            options = listOf("0-1.9", "2-3.9", "4-5"),
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
        // Add postgrad and work fields similarly
    }
}

fun saveFeedFiltersToViewModel(
    postViewModel: PostViewModel,
    feedLocalities: Set<String>,
    feedHighSchool: String,
    feedCollege: String,
    feedRating: String,
    feedAgeRange: IntRange,
    feedDistance: Int,
    feedGender: String
) {
    postViewModel.setFeedFilters(
        FilterSettings(
            filterOption = "everyone", // Set appropriately based on screen selections
            feedFilters = FeedFilterSettings(
                localities = feedLocalities.toList(),
                highSchool = feedHighSchool,
                college = feedCollege,
                ageStart = feedAgeRange.start,
                ageEnd = feedAgeRange.endInclusive,
                distance = feedDistance,
                gender = feedGender,
                rating = feedRating
            ),
            sortOption = "Sort by Upvotes",
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