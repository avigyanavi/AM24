// SettingsScreen.kt

package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    // State variables
    var selectedCity by remember { mutableStateOf("Kolkata") } // Default city
    var selectedLocality by remember { mutableStateOf("") }
    val cityLocalities = mapOf(
        "Kolkata" to listOf("Salt Lake", "Garia", "Dumdum", "Park Street", "Behala"),
        "Mumbai" to listOf("Andheri", "Bandra", "Dadar", "Borivali", "Colaba"),
        "Delhi" to listOf("Connaught Place", "Dwarka", "Saket", "Karol Bagh", "Lajpat Nagar"),
        "Bangalore" to listOf("Whitefield", "Koramangala", "Indiranagar", "Jayanagar", "Marathahalli"),
        "Hyderabad" to listOf("Banjara Hills", "Begumpet", "Hitech City", "Kukatpally", "Gachibowli"),
        "Chennai" to listOf("Adyar", "T Nagar", "Velachery", "Anna Nagar", "Besant Nagar"),
        "Ahmedabad" to listOf("Satellite", "Navrangpura", "Vastrapur", "Bopal", "Paldi")
    )
    var availableLocalities by remember { mutableStateOf(cityLocalities[selectedCity] ?: listOf()) }
    var distance by remember { mutableStateOf(10) }
    var enableFriendsToDM by remember { mutableStateOf(false) }
    var privateAccount by remember { mutableStateOf(false) }
    var ageRange by remember { mutableStateOf(18..30) }

    // Firebase references
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    // Load user profile to prefill settings
    LaunchedEffect(Unit) {
        database.get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(Profile::class.java)
            if (profile != null) {
                selectedCity = profile.city ?: "Kolkata"
                availableLocalities = cityLocalities[selectedCity] ?: listOf()
                selectedLocality = profile.localityFilter ?: ""
                distance = profile.distancePreference.toInt()
                ageRange = (profile.ageRangeStart ?: 18)..(profile.ageRangeEnd ?: 30)
                enableFriendsToDM = profile.enableFriendsToDM ?: false
                privateAccount = profile.privateAccount ?: false
            }
        }
    }

    // Save settings function
    fun saveSettings() {
        database.child("distancePreference").setValue(distance)
        database.child("localityFilter").setValue(selectedLocality)
        database.child("ageRangeStart").setValue(ageRange.start)
        database.child("ageRangeEnd").setValue(ageRange.endInclusive)
        database.child("enableFriendsToDM").setValue(enableFriendsToDM)
        database.child("privateAccount").setValue(privateAccount)
        Toast.makeText(context, "Settings saved.", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        content = { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF121212))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Dating Filters Section
                item {
                    Text(
                        text = "Dating Filters",
                        color = Color(0xFF00bf63),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        // Age Range Slider
                        Text(
                            text = "Age Range: ${ageRange.start} - ${ageRange.endInclusive}",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        RangeSlider(
                            value = ageRange.start.toFloat()..ageRange.endInclusive.toFloat(),
                            onValueChange = { range ->
                                ageRange = range.start.roundToInt()..range.endInclusive.roundToInt()
                            },
                            valueRange = 18f..100f,
                            steps = 82,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00bf63),
                                activeTrackColor = Color(0xFF00bf63),
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Distance Slider
                        Text(
                            text = "Maximum Distance: ${distance} km",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Slider(
                            value = distance.toFloat(),
                            onValueChange = { newDistance -> distance = newDistance.roundToInt() },
                            valueRange = 0f..100f,
                            steps = 100,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00bf63),
                                activeTrackColor = Color(0xFF00bf63),
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // City Dropdown
                        Text(
                            text = "Select City",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        var expandedCity by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCity,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("City") },
                                trailingIcon = {
                                    IconButton(onClick = { expandedCity = true }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Dropdown"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = expandedCity,
                                onDismissRequest = { expandedCity = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                cityLocalities.keys.forEach { city ->
                                    DropdownMenuItem(
                                        text = { Text(text = city) },
                                        onClick = {
                                            selectedCity = city
                                            availableLocalities = cityLocalities[city] ?: listOf()
                                            expandedCity = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Locality Dropdown
                        Text(
                            text = "Select Locality",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        var expandedLocality by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedLocality,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Locality") },
                                trailingIcon = {
                                    IconButton(onClick = { expandedLocality = true }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Dropdown"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = expandedLocality,
                                onDismissRequest = { expandedLocality = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                availableLocalities.forEach { locality ->
                                    DropdownMenuItem(
                                        text = { Text(text = locality) },
                                        onClick = {
                                            selectedLocality = locality
                                            expandedLocality = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Save Filters Button
                        Button(
                            onClick = { saveSettings() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                        ) {
                            Text(text = "Save Filters", color = Color.White)
                        }
                    }
                }

                // Additional Settings Section
                item {
                    Text(
                        text = "Additional Settings",
                        color = Color(0xFF00bf63),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        // Enable Friends to DM
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enable Friends to DM",
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = enableFriendsToDM,
                                onCheckedChange = { enableFriendsToDM = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00bf63),
                                    uncheckedThumbColor = Color.Gray
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Private Account
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Private Account",
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = privateAccount,
                                onCheckedChange = { privateAccount = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00bf63),
                                    uncheckedThumbColor = Color.Gray
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Logout Button
                        Button(
                            onClick = {
                                FirebaseAuth.getInstance().signOut()
                                navController.navigate("login") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(text = "Logout", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Delete Account Button
                        Button(
                            onClick = {
                                database.removeValue()
                                FirebaseAuth.getInstance().currentUser?.delete()
                                navController.navigate("login") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(text = "Delete Account", color = Color.White)
                        }
                    }
                }
            }
        }
    )
}
