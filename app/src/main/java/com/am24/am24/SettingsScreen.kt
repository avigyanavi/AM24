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

    // State for Dating Filters
    var ageRange by remember { mutableStateOf(18f..30f) }
    var distance by remember { mutableStateOf(10f) }
    var selectedGender by remember { mutableStateOf("Any") }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    // State for Additional Settings
    var enableMessagesFromFollowers by remember { mutableStateOf(false) }
    var postOnlyForFollowers by remember { mutableStateOf(false) }
    var age by remember { mutableStateOf(18f) }


    // Fetch the current user's distance preference from Firebase
    LaunchedEffect(Unit) {
        database.get().addOnSuccessListener { snapshot ->
            val profile = snapshot.getValue(Profile::class.java)
            if (profile != null) {
                distance = profile.distancePreference // Load saved distance from profile
            }
        }
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
                // 1. Dating Filters Section
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
                            text = "Age Range: ${ageRange.start.roundToInt()} - ${ageRange.endInclusive.roundToInt()}",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        RangeSlider(
                            value = ageRange,
                            onValueChange = { newRange ->
                                ageRange = newRange
                                // TODO: Handle the age range change (e.g., update settings or filters)
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
                            text = "Maximum Distance: ${distance.roundToInt()} km",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Slider(
                            value = distance,
                            onValueChange = { newDistance ->
                                distance = newDistance
                            },
                            valueRange = 0f..100f,
                            steps = 100,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00bf63),
                                activeTrackColor = Color(0xFF00bf63),
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // "People You Want to See" Dropdown
                        Text(
                            text = "People You Want to See",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        var expanded by remember { mutableStateOf(false) }
                        val genders = listOf("Any", "Male (M)", "Female (F)", "Non-Binary (NB)", "Everyone (E)")
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedGender,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Gender") },
                                trailingIcon = {
                                    IconButton(onClick = { expanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Dropdown"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                genders.forEach { gender ->
                                    DropdownMenuItem(
                                        text = { Text(text = gender) },
                                        onClick = {
                                            selectedGender = gender
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        // Save button to store filters
                        Button(
                            onClick = {
                                // Save the distance preference to Firebase
                                database.child("distancePreference").setValue(distance)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Filters saved.", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, "Failed to save filters.", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                        ) {
                            Text(text = "Save & Apply Filters", color = Color.White)
                        }
                    }
                }

                // 2. Premium Filters Section (Under Paywall)


                // 2. Premium Filters Section (Under Paywall)
                item {
                    Text(
                        text = "Premium Filters",
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
                        // Rating Minimum
                        Text(
                            text = "Minimum Rating",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Rating") },
                            enabled = false,
                            placeholder = { Text("Premium Feature") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.DarkGray,
                                focusedTextColor = Color.Gray,
                                focusedPlaceholderColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Level Minimum
                        Text(
                            text = "Minimum Level",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Level") },
                            enabled = false,
                            placeholder = { Text("Premium Feature") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.DarkGray,
                                focusedTextColor = Color.Gray,
                                focusedPlaceholderColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // High School
                        Text(
                            text = "High School",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("High School") },
                            enabled = false,
                            placeholder = { Text("Premium Feature") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.DarkGray,
                                focusedTextColor = Color.Gray,
                                focusedPlaceholderColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // College
                        Text(
                            text = "College",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("College") },
                            enabled = false,
                            placeholder = { Text("Premium Feature") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.DarkGray,
                                focusedTextColor = Color.Gray,
                                focusedPlaceholderColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Job Role (New Premium Filter)
                        Text(
                            text = "Job Role",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Job Role") },
                            enabled = false,
                            placeholder = { Text("Premium Feature") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.DarkGray,
                                focusedTextColor = Color.Gray,
                                focusedPlaceholderColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Work (Company) (New Premium Filter)
                        Text(
                            text = "Work (Company)",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Company") },
                            enabled = false,
                            placeholder = { Text("Premium Feature") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.DarkGray,
                                focusedTextColor = Color.Gray,
                                focusedPlaceholderColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Claimed Income Level (New Premium Filter)
                        Text(
                            text = "Claimed Income Level",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Income Level") },
                            enabled = false,
                            placeholder = { Text("Premium Feature") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.DarkGray,
                                focusedTextColor = Color.Gray,
                                focusedPlaceholderColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // 3. Enable Messages from Followers
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable messages from followers",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Allow your followers to send you messages",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                            Switch(
                                checked = enableMessagesFromFollowers,
                                onCheckedChange = { enabled ->
                                    enableMessagesFromFollowers = enabled
                                    Toast.makeText(
                                        context,
                                        "Enable messages from followers: $enabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00bf63),
                                    uncheckedThumbColor = Color.Gray
                                )
                            )
                        }
                    }
                }

                // 4. View Composite Score History
                item {
                    Button(
                        onClick = {
                            Toast.makeText(
                                context,
                                "Composite Score History - Coming Soon",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Composite Score History",
                            tint = Color(0xFF00bf63),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "View Composite Score History",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 5. Post Only for Followers
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Post only for followers",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Only your followers will see your posts",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                            Switch(
                                checked = postOnlyForFollowers,
                                onCheckedChange = { enabled ->
                                    postOnlyForFollowers = enabled
                                    Toast.makeText(
                                        context,
                                        "Post only for followers: $enabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00bf63),
                                    uncheckedThumbColor = Color.Gray
                                )
                            )
                        }
                    }
                }

                // 6. View Leaderboard
                item {
                    Button(
                        onClick = {
                            navController.navigate("leaderboard")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = "View Leaderboard",
                            tint = Color(0xFF00bf63),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "View Leaderboard",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 7. Send Feedback
                item {
                    Button(
                        onClick = {
                            Toast.makeText(
                                context,
                                "Send Feedback - Coming Soon",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Feedback",
                            tint = Color(0xFF00bf63),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Send Feedback",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    )
}

