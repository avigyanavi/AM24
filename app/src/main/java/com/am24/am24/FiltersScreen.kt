@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
fun FiltersScreen(navController: NavController) {
    val context = LocalContext.current

    // State for unified settings toggle
    var useUnifiedSettings by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0 for Dating, 1 for Feed

    // Dating Stack Filters
    var datingLocality by remember { mutableStateOf("") }
    var datingAgeRange by remember { mutableStateOf(18..30) }
    var datingRating by remember { mutableStateOf("") }
    var datingGender by remember { mutableStateOf("") }
    var datingHighSchool by remember { mutableStateOf("") }
    var datingCollege by remember { mutableStateOf("") }
    var datingDistance by remember { mutableStateOf(10) }

    // Feed Filters
    var feedLocality by remember { mutableStateOf("") }
    var feedAgeRange by remember { mutableStateOf(18..30) }
    var feedRating by remember { mutableStateOf("") }
    var feedGender by remember { mutableStateOf("") }
    var feedHighSchool by remember { mutableStateOf("") }
    var feedCollege by remember { mutableStateOf("") }
    var feedDistance by remember { mutableStateOf(10) }

    // Firebase references
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    // Load existing filters on launch
    LaunchedEffect(Unit) {
        database.get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(Profile::class.java)
            if (profile != null) {
                datingLocality = profile.datingLocality ?: ""
                datingAgeRange = (profile.datingAgeStart ?: 18)..(profile.datingAgeEnd ?: 30)
                datingRating = profile.datingRating ?: ""
                datingGender = profile.datingGender ?: ""
                datingHighSchool = profile.datingHighSchool ?: ""
                datingCollege = profile.datingCollege ?: ""
                datingDistance = (profile.datingDistancePreference ?: 10).toInt()

                feedLocality = profile.feedLocality ?: ""
                feedAgeRange = (profile.feedAgeStart ?: 18)..(profile.feedAgeEnd ?: 30)
                feedRating = profile.feedRating ?: ""
                feedGender = profile.feedGender ?: ""
                feedHighSchool = profile.feedHighSchool ?: ""
                feedCollege = profile.feedCollege ?: ""
                feedDistance = (profile.feedDistancePreference ?: 10).toInt()

                useUnifiedSettings = profile.useUnifiedFilters ?: true
            }
        }
    }

    // Save filters function
    fun saveFilters() {
        val updates = mutableMapOf<String, Any>()

        if (useUnifiedSettings) {
            updates["datingLocality"] = datingLocality
            updates["datingAgeStart"] = datingAgeRange.start
            updates["datingAgeEnd"] = datingAgeRange.endInclusive
            updates["datingRating"] = datingRating
            updates["datingGender"] = datingGender
            updates["datingHighSchool"] = datingHighSchool
            updates["datingCollege"] = datingCollege
            updates["datingDistancePreference"] = datingDistance

            updates["feedLocality"] = datingLocality
            updates["feedAgeStart"] = datingAgeRange.start
            updates["feedAgeEnd"] = datingAgeRange.endInclusive
            updates["feedRating"] = datingRating
            updates["feedGender"] = datingGender
            updates["feedHighSchool"] = datingHighSchool
            updates["feedCollege"] = datingCollege
            updates["feedDistancePreference"] = datingDistance
        } else {
            updates["datingLocality"] = datingLocality
            updates["datingAgeStart"] = datingAgeRange.start
            updates["datingAgeEnd"] = datingAgeRange.endInclusive
            updates["datingRating"] = datingRating
            updates["datingGender"] = datingGender
            updates["datingHighSchool"] = datingHighSchool
            updates["datingCollege"] = datingCollege
            updates["datingDistancePreference"] = datingDistance

            updates["feedLocality"] = feedLocality
            updates["feedAgeStart"] = feedAgeRange.start
            updates["feedAgeEnd"] = feedAgeRange.endInclusive
            updates["feedRating"] = feedRating
            updates["feedGender"] = feedGender
            updates["feedHighSchool"] = feedHighSchool
            updates["feedCollege"] = feedCollege
            updates["feedDistancePreference"] = feedDistance
        }

        updates["useUnifiedFilters"] = useUnifiedSettings

        database.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(context, "Filters saved successfully!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to save filters.", Toast.LENGTH_SHORT).show()
        }
    }

    // Save filters on dispose
    DisposableEffect(Unit) {
        onDispose {
            saveFilters()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp) // No extra padding
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Use one set of filters for both -------------->",
                color = Color(0xFFFFA500),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = useUnifiedSettings,
                onCheckedChange = { useUnifiedSettings = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFF4500),
                    uncheckedThumbColor = Color.Gray
                )
            )
        }

        if (!useUnifiedSettings) {
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
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (useUnifiedSettings || selectedTab == 0) {
                item {
                    FilterFields(
                        locality = datingLocality,
                        onLocalityChange = { datingLocality = it },
                        ageRange = datingAgeRange,
                        onAgeRangeChange = { datingAgeRange = it },
                        rating = datingRating,
                        onRatingChange = { datingRating = it },
                        gender = datingGender,
                        onGenderChange = { datingGender = it },
                        highSchool = datingHighSchool,
                        onHighSchoolChange = { datingHighSchool = it },
                        college = datingCollege,
                        onCollegeChange = { datingCollege = it },
                        distance = datingDistance,
                        onDistanceChange = { datingDistance = it }
                    )
                }
            }

            if (!useUnifiedSettings && selectedTab == 1) {
                item {
                    FilterFields(
                        locality = feedLocality,
                        onLocalityChange = { feedLocality = it },
                        ageRange = feedAgeRange,
                        onAgeRangeChange = { feedAgeRange = it },
                        rating = feedRating,
                        onRatingChange = { feedRating = it },
                        gender = feedGender,
                        onGenderChange = { feedGender = it },
                        highSchool = feedHighSchool,
                        onHighSchoolChange = { feedHighSchool = it },
                        college = feedCollege,
                        onCollegeChange = { feedCollege = it },
                        distance = feedDistance,
                        onDistanceChange = { feedDistance = it }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterFields(
    locality: String,
    onLocalityChange: (String) -> Unit,
    ageRange: IntRange,
    onAgeRangeChange: (IntRange) -> Unit,
    rating: String,
    onRatingChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    highSchool: String,
    onHighSchoolChange: (String) -> Unit,
    college: String,
    onCollegeChange: (String) -> Unit,
    distance: Int, // Added distance filter
    onDistanceChange: (Int) -> Unit, // Callback for distance filter
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        // Locality Buttons (Vertically Stacked)
        Text(text = "Locality", color = Color.White, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(6.dp))
        SelectionButtons(
            options = listOf(
                "North Kolkata", "South Kolkata", "East Kolkata",
                "West Kolkata", "Other Locality", "Outside Bengal"
            ),
            selectedOption = locality,
            onOptionSelected = onLocalityChange,
            vertical = true // Added vertical stacking
        )
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
            vertical = false // Keep horizontally aligned
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

        // High School Text Field
        TextFieldWithLabel(
            label = "High School",
            value = highSchool,
            onValueChange = onHighSchoolChange
        )
        Spacer(modifier = Modifier.height(16.dp))

        // College Text Field
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
    vertical: Boolean = false // Add flag for vertical stacking
) {
    if (vertical) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                Button(
                    onClick = { onOptionSelected(option) },
                    enabled = false, // Make buttons unselectable
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
                    enabled = false, // Make buttons unselectable
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
            unfocusedTextColor = Color.Black,
        )
    )
}