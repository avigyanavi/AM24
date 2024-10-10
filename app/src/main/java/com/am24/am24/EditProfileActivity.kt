package com.am24.am24

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch

class EditProfileActivity : ComponentActivity() {
    private lateinit var userId: String
    private lateinit var userRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated. Please log in.", Toast.LENGTH_SHORT).show()
            finish()
            return
        } else {
            userId = currentUser.uid
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        }

        setContent {
            EditProfileScreen()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EditProfileScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // State variables for fields
        var profile by remember { mutableStateOf(Profile()) }
        var name by remember { mutableStateOf(TextFieldValue("")) }
        var username by remember { mutableStateOf(TextFieldValue("")) }
        var bio by remember { mutableStateOf(TextFieldValue("")) }
        val interests = remember { mutableStateListOf<Interest>() }
        var locality by remember { mutableStateOf("") }
        var customLocality by remember { mutableStateOf("") }
        var highSchool by remember { mutableStateOf("") }
        var customHighSchool by remember { mutableStateOf("") }
        var college by remember { mutableStateOf("") }
        var customCollege by remember { mutableStateOf("") }
        var gender by remember { mutableStateOf("") }
        var community by remember { mutableStateOf("") }
        var religion by remember { mutableStateOf("") }

        var showEditInterestsScreen by remember { mutableStateOf(false) }

        // Predefined lists (similar to RegistrationActivity)
        val localities = listOf("Garia", "Chingrighata", "Ballygunge", "Tangra", "Alipore")
        val highSchools = listOf("St. Xavier's High School", "Delhi Public School", "Modern High School")
        val colleges = listOf("IIT Delhi", "Jadavpur University", "St. Xavier's College")
        val genderOptions = listOf("Male", "Female", "Other")
        val communityOptions = listOf("Marwari", "Bengali", "Punjabi", "Tamil")
        val religionOptions = listOf("Hindu", "Muslim", "Christian", "Other")

        // Load profile data
        LaunchedEffect(Unit) {
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    profile = snapshot.getValue(Profile::class.java) ?: Profile()

                    // Deserialize interests correctly with name and emoji
                    val interestsSnapshot = snapshot.child("interests").children
                    val interestList = interestsSnapshot.mapNotNull { childSnapshot ->
                        val name = childSnapshot.child("name").getValue(String::class.java)
                        val emoji = childSnapshot.child("emoji").getValue(String::class.java)
                        if (name != null) {
                            Interest(name = name, emoji = emoji)
                        } else {
                            null
                        }
                    }

                    name = TextFieldValue(profile.name)
                    username = TextFieldValue(profile.username)
                    bio = TextFieldValue(profile.bio)
                    interests.clear()
                    interests.addAll(interestList) // Add deserialized interests
                    locality = profile.locality
                    highSchool = profile.highSchool
                    college = profile.college
                    gender = profile.gender
                    community = profile.community
                    religion = profile.religion
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            })
        }



        if (showEditInterestsScreen) {
            EditInterestsScreen(
                initialInterests = interests,
                onInterestsUpdated = { updatedInterests ->
                    interests.clear()
                    interests.addAll(updatedInterests)
                    showEditInterestsScreen = false
                },
                onBack = { showEditInterestsScreen = false }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Edit Profile", color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = { (context as? Activity)?.finish() }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                    )
                },
                content = { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Name
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name", color = Color(0xFF00bf63)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF00bf63),
                                    focusedBorderColor = Color(0xFF00bf63),
                                    unfocusedBorderColor = Color(0xFF00bf63)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Username
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username", color = Color(0xFF00bf63)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF00bf63),
                                    focusedBorderColor = Color(0xFF00bf63),
                                    unfocusedBorderColor = Color(0xFF00bf63)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bio
                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("Bio", color = Color(0xFF00bf63)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF00bf63),
                                    focusedBorderColor = Color(0xFF00bf63),
                                    unfocusedBorderColor = Color(0xFF00bf63)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Gender Dropdown
                            DropdownWithSearch(
                                title = "Select Gender",
                                options = genderOptions,
                                selectedOption = gender,
                                onOptionSelected = { gender = it }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Community Dropdown
                            DropdownWithSearch(
                                title = "Select Community",
                                options = communityOptions,
                                selectedOption = community,
                                onOptionSelected = { community = it }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Religion Dropdown
                            DropdownWithSearch(
                                title = "Select Religion",
                                options = religionOptions,
                                selectedOption = religion,
                                onOptionSelected = { religion = it }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Locality Dropdown with Custom Input
                            if (customLocality.isNotEmpty()) {
                                OutlinedTextField(
                                    value = TextFieldValue(customLocality),
                                    onValueChange = { customLocality = it.text },
                                    label = { Text("Custom Locality", color = Color(0xFF00bf63)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        cursorColor = Color(0xFF00bf63),
                                        focusedBorderColor = Color(0xFF00bf63),
                                        unfocusedBorderColor = Color(0xFF00bf63)
                                    )
                                )
                            } else {
                                DropdownWithSearch(
                                    title = "Select Locality",
                                    options = localities,
                                    selectedOption = locality,
                                    onOptionSelected = { locality = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // High School Dropdown with Custom Input
                            if (customHighSchool.isNotEmpty()) {
                                OutlinedTextField(
                                    value = TextFieldValue(customHighSchool),
                                    onValueChange = { customHighSchool = it.text },
                                    label = { Text("Custom High School", color = Color(0xFF00bf63)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        cursorColor = Color(0xFF00bf63),
                                        focusedBorderColor = Color(0xFF00bf63),
                                        unfocusedBorderColor = Color(0xFF00bf63)
                                    )
                                )
                            } else {
                                DropdownWithSearch(
                                    title = "Select High School",
                                    options = highSchools,
                                    selectedOption = highSchool,
                                    onOptionSelected = { highSchool = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // College Dropdown with Custom Input
                            if (customCollege.isNotEmpty()) {
                                OutlinedTextField(
                                    value = TextFieldValue(customCollege),
                                    onValueChange = { customCollege = it.text },
                                    label = { Text("Custom College", color = Color(0xFF00bf63)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        cursorColor = Color(0xFF00bf63),
                                        focusedBorderColor = Color(0xFF00bf63),
                                        unfocusedBorderColor = Color(0xFF00bf63)
                                    )
                                )
                            } else {
                                DropdownWithSearch(
                                    title = "Select College",
                                    options = colleges,
                                    selectedOption = college,
                                    onOptionSelected = { college = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Place this below the "Bio" field or wherever appropriate in your form

                            Button(
                                onClick = { showEditInterestsScreen = true }, // This will trigger the interests screen to show
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                            ) {
                                Text("Edit Interests", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Save Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        saveProfileData(
                                            name.text,
                                            username.text,
                                            bio.text,
                                            locality.ifEmpty { customLocality },
                                            highSchool.ifEmpty { customHighSchool },
                                            college.ifEmpty { customCollege },
                                            gender,
                                            community,
                                            religion,
                                            interests
                                        )
                                        Toast.makeText(context, "Profile Updated", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                            ) {
                                Text("Save Changes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
        }
    }


    private suspend fun saveProfileData(
        name: String,
        username: String,
        bio: String,
        locality: String,
        highSchool: String,
        college: String,
        gender: String,
        community: String,
        religion: String,
        interests: List<Interest>
    ) {
        val updates = mapOf(
            "name" to name,
            "username" to username,
            "bio" to bio,
            "locality" to locality,
            "highSchool" to highSchool,
            "college" to college,
            "gender" to gender,
            "community" to community,
            "religion" to religion,
            // Save interests with both name and emoji
            "interests" to interests.map { mapOf("name" to it.name, "emoji" to it.emoji) }
        )

        userRef.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Profile update failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

}

fun extractEmojiAndText(input: String): Pair<String, String> {
    val emojiRegex = Regex("[\\p{So}\\p{Sc}\\p{Sk}\\p{Sm}\\p{Cs}]+") // Matches emojis
    val emojis = emojiRegex.findAll(input).map { it.value }.joinToString("") // Find and join all emojis
    val text = input.replace(emojiRegex, "").trim() // Remove all emojis from the string

    return Pair(emojis, text)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInterestsScreen(
    initialInterests: List<Interest>,
    onInterestsUpdated: (List<Interest>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var customInterest by remember { mutableStateOf(TextFieldValue("")) }
    val interestCategories = listOf(
        InterestCategory(
            category = "Food & Culinary Arts",
            emoji = "🍽️",
            subcategories = listOf(
                InterestSubcategory("Phuchka", "🥟"),
                InterestSubcategory("Kathi Rolls", "🌯"),
                // Other food interests
            )
        ),
        InterestCategory(
            category = "Festivals & Culture",
            emoji = "🎉",
            subcategories = listOf(
                InterestSubcategory("Durga Puja", "🙏"),
                InterestSubcategory("Saraswati Puja", "📚"),
                // Other festival interests
            )
        ),
        // Add other categories here
    )

    val selectedInterests = remember { mutableStateListOf<Interest>().apply { addAll(initialInterests) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Interests", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Select Interests", color = Color.White, fontSize = 18.sp)

                    interestCategories.forEach { category ->
                        var expanded by remember { mutableStateOf(false) }

                        OutlinedButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Black,
                                contentColor = Color(0xFF00bf63)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF00bf63))
                        ) {
                            Text(text = "${category.emoji} ${category.category}")
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                        ) {
                            category.subcategories.forEach { subcategory ->
                                val isSelected = selectedInterests.any { it.name == subcategory.name }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${subcategory.emoji} ${subcategory.name}",
                                            color = if (isSelected) Color(0xFF00bf63) else Color.White
                                        )
                                    },
                                    onClick = {
                                        if (isSelected) {
                                            selectedInterests.removeIf { it.name == subcategory.name }
                                        } else {
                                            selectedInterests.add(Interest(name = subcategory.name, emoji = subcategory.emoji))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Custom Interest Input
                    TextField(
                        value = customInterest,
                        onValueChange = { customInterest = it },
                        label = { Text("Add Custom Interest", color = Color(0xFF00bf63)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedLabelColor = Color(0xFF00bf63),
                            focusedBorderColor = Color(0xFF00bf63),
                            cursorColor = Color(0xFF00bf63),
                            focusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    Button(
                        onClick = {
                            if (customInterest.text.isNotBlank()) {
                                val (emojis, name) = extractEmojiAndText(customInterest.text)
                                selectedInterests.add(Interest(name = name, emoji = emojis)) // Add all found emojis
                                customInterest = TextFieldValue("") // Clear input after adding
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                    ) {
                        Text(text = "Add Interest", color = Color.White)
                    }


                    // Display Added Interests
                    Text("Your Interests", color = Color.White, fontSize = 18.sp)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        items(selectedInterests) { interest ->
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color.Black, shape = CircleShape)
                                    .clickable {
                                        selectedInterests.remove(interest)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = interest.name, color = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Interest",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            onInterestsUpdated(selectedInterests.toList())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        Text(
                            text = "Save Interests",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
}
