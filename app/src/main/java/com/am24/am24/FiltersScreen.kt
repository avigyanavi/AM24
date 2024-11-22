@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlin.math.roundToInt

@Composable
fun FiltersScreen() {
    val context = LocalContext.current

    // State for selected tab
    var selectedTab by remember { mutableStateOf(0) } // 0 for Dating Filters, 1 for Feed Filters

    // Dating Filters
    var datingLocalities by remember { mutableStateOf(setOf<String>()) } // Immutable Set
    var datingHighSchool by remember { mutableStateOf("") }
    var datingCollege by remember { mutableStateOf("") }
    var datingRating by remember { mutableStateOf("") }
    var datingAgeRange by remember { mutableStateOf(18..30) }
    var datingDistance by remember { mutableStateOf(10) }
    var datingGender by remember { mutableStateOf("") }

    // Feed Filters
    var feedLocalities by remember { mutableStateOf(setOf<String>()) } // Immutable Set
    var feedHighSchool by remember { mutableStateOf("") }
    var feedCollege by remember { mutableStateOf("") }
    var feedRating by remember { mutableStateOf("") }
    var feedAgeRange by remember { mutableStateOf(18..30) }
    var feedDistance by remember { mutableStateOf(10) }
    var feedGender by remember { mutableStateOf("") }

    // Sample Data
    val allLocalities = listOf("North Kolkata", "South Kolkata", "East Kolkata", "West Kolkata", "Locality 1", "Locality 2")
    var searchQueryLocalities by remember { mutableStateOf("") }
    val filteredLocalities = allLocalities.filter { it.contains(searchQueryLocalities, ignoreCase = true) }

    // Save Filters Function
    fun saveFilters() {
        val updates = mapOf(
            "datingLocalities" to datingLocalities.toList(),
            "feedLocalities" to feedLocalities.toList(),
            "datingRating" to datingRating,
            "feedRating" to feedRating,
            "datingGender" to datingGender,
            "feedGender" to feedGender,
            "datingHighSchool" to datingHighSchool,
            "feedHighSchool" to feedHighSchool,
            "datingCollege" to datingCollege,
            "feedCollege" to feedCollege,
            "datingAgeStart" to datingAgeRange.start,
            "datingAgeEnd" to datingAgeRange.endInclusive,
            "feedAgeStart" to feedAgeRange.start,
            "feedAgeEnd" to feedAgeRange.endInclusive,
            "datingDistancePreference" to datingDistance,
            "feedDistancePreference" to feedDistance
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
        onDispose { saveFilters() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Tab Selector
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Black,
            contentColor = Color(0xFFFFA500),
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFFFFA500) // Indicator color under the selected tab
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
                    college = if (selectedTab == 0) datingCollege else feedCollege,
                    onCollegeChange = { newCollege ->
                        if (selectedTab == 0) datingCollege = newCollege else feedCollege = newCollege
                    },
                    ageRange = if (selectedTab == 0) datingAgeRange else feedAgeRange,
                    onAgeRangeChange = { newRange ->
                        if (selectedTab == 0) datingAgeRange = newRange else feedAgeRange = newRange
                    },
                    rating = if (selectedTab == 0) datingRating else feedRating,
                    onRatingChange = { newRating ->
                        if (selectedTab == 0) datingRating = toggleSelection(datingRating, newRating) else feedRating = toggleSelection(feedRating, newRating)
                    },
                    distance = if (selectedTab == 0) datingDistance else feedDistance,
                    onDistanceChange = { newDistance ->
                        if (selectedTab == 0) datingDistance = newDistance else feedDistance = newDistance
                    },
                    gender = if (selectedTab == 0) datingGender else feedGender,
                    onGenderChange = { newGender ->
                        if (selectedTab == 0) datingGender = toggleSelection(datingGender, newGender) else feedGender = toggleSelection(feedGender, newGender)
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
    // If the new value is the same as the current value, deselect it; otherwise, select it
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
    college: String,
    onCollegeChange: (String) -> Unit,
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
                focusedTextColor = Color.White
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

        // High School Searchable Field
        TextFieldWithLabel(
            label = "High School",
            value = highSchool,
            onValueChange = onHighSchoolChange
        )
        Spacer(modifier = Modifier.height(16.dp))

        // College Searchable Field
        TextFieldWithLabel(
            label = "College",
            value = college,
            onValueChange = onCollegeChange
        )
    }
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
fun TextFieldWithLabel(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = Color.White) },
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = Color(0xFFFF4500),
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.Black
        )
    )
}
