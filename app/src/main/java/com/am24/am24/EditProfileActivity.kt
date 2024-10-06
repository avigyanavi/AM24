package com.am24.am24

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
            // User not logged in; redirect to login or show an error
            Toast.makeText(this, "User not authenticated. Please log in.", Toast.LENGTH_SHORT).show()
            finish() // Close the activity
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
        var hometown by remember { mutableStateOf(TextFieldValue("")) }
        var highSchool by remember { mutableStateOf(TextFieldValue("")) }
        var college by remember { mutableStateOf(TextFieldValue("")) }
        var gender by remember { mutableStateOf("") }

        var showEditInterestsScreen by remember { mutableStateOf(false) }

        // State variables for username validation
        var isUsernameValid by rememberSaveable { mutableStateOf(true) }
        var isCheckingUsername by rememberSaveable { mutableStateOf(false) }
        var usernameErrorMessage by rememberSaveable { mutableStateOf("") }

        // Load profile data
        LaunchedEffect(Unit) {
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    profile = snapshot.getValue(Profile::class.java) ?: Profile()
                    name = TextFieldValue(profile.name)
                    username = TextFieldValue(profile.username)
                    bio = TextFieldValue(profile.bio)
                    interests.clear()
                    interests.addAll(profile.interests)
                    hometown = TextFieldValue(profile.hometown)
                    highSchool = TextFieldValue(profile.highSchool)
                    college = TextFieldValue(profile.college)
                    gender = profile.gender
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
                                onValueChange = {
                                    username = it
                                    isUsernameValid = true
                                    usernameErrorMessage = ""
                                },
                                label = { Text("Username", color = Color(0xFF00bf63)) },
                                singleLine = true,
                                isError = !isUsernameValid,
                                supportingText = {
                                    if (!isUsernameValid) {
                                        Text(text = usernameErrorMessage, color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF00bf63),
                                    focusedBorderColor = if (isUsernameValid) Color(0xFF00bf63) else MaterialTheme.colorScheme.error,
                                    unfocusedBorderColor = if (isUsernameValid) Color(0xFF00bf63) else MaterialTheme.colorScheme.error
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

                            // Interests Section
                            Button(
                                onClick = {
                                    // Navigate to EditInterestsScreen
                                    showEditInterestsScreen = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "Edit Interests",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Display Selected Interests
                            Text(
                                text = "Selected Interests:",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp)
                            )

                            Text(
                                text = interests.joinToString(", ") { "${it.emoji} ${it.name}" },
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Hometown
                            OutlinedTextField(
                                value = hometown,
                                onValueChange = { hometown = it },
                                label = { Text("Hometown", color = Color(0xFF00bf63)) },
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

                            // High School
                            OutlinedTextField(
                                value = highSchool,
                                onValueChange = { highSchool = it },
                                label = { Text("High School", color = Color(0xFF00bf63)) },
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

                            // College
                            OutlinedTextField(
                                value = college,
                                onValueChange = { college = it },
                                label = { Text("College", color = Color(0xFF00bf63)) },
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

                            Spacer(modifier = Modifier.height(16.dp))

                            // Save Button
                            Button(
                                onClick = {
                                    // Validate and update profile
                                    scope.launch {
                                        if (username.text.trim().isEmpty()) {
                                            isUsernameValid = false
                                            usernameErrorMessage = "Username cannot be empty"
                                            return@launch
                                        }

                                        if (username.text.trim() != profile.username) {
                                            // Username changed, check uniqueness
                                            isCheckingUsername = true
                                            val database = FirebaseDatabase.getInstance().reference
                                            database.child("usernames").child(username.text.trim())
                                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                                    override fun onDataChange(snapshot: DataSnapshot) {
                                                        if (snapshot.exists()) {
                                                            // Username taken
                                                            isUsernameValid = false
                                                            usernameErrorMessage = "Username already taken"
                                                            isCheckingUsername = false
                                                        } else {
                                                            // Update username in usernames node
                                                            database.child("usernames").child(profile.username).removeValue()
                                                            database.child("usernames").child(username.text.trim()).setValue(true)
                                                            isCheckingUsername = false
                                                            val updatedProfile = profile.copy(
                                                                name = name.text.trim(),
                                                                username = username.text.trim(),
                                                                bio = bio.text.trim(),
                                                                interests = interests.toList(),
                                                                hometown = hometown.text.trim(),
                                                                highSchool = highSchool.text.trim(),
                                                                college = college.text.trim(),
                                                                gender = gender
                                                            )
                                                            updateProfile(updatedProfile)
                                                        }
                                                    }

                                                    override fun onCancelled(error: DatabaseError) {
                                                        isUsernameValid = false
                                                        usernameErrorMessage = "Error checking username"
                                                        isCheckingUsername = false
                                                    }
                                                })
                                        } else {
                                            val updatedProfile = profile.copy(
                                                name = name.text.trim(),
                                                username = username.text.trim(),
                                                bio = bio.text.trim(),
                                                interests = interests.toList(),
                                                hometown = hometown.text.trim(),
                                                highSchool = highSchool.text.trim(),
                                                college = college.text.trim(),
                                                gender = gender
                                            )
                                            updateProfile(updatedProfile)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                enabled = !isCheckingUsername,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (isCheckingUsername) "Checking..." else "Save",
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
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EditInterestsScreen(
        initialInterests: List<Interest>,
        onInterestsUpdated: (List<Interest>) -> Unit,
        onBack: () -> Unit
    ) {
        val context = LocalContext.current

        // Use a mutable list to track selected interests
        val selectedInterests = remember { mutableStateListOf<Interest>().apply { addAll(initialInterests) } }

        val interestCategories = listOf(
            InterestCategory(
                category = "Food & Culinary Arts",
                emoji = "🍽️",
                subcategories = listOf(
                    InterestSubcategory("Phuchka", "🥟"),
                    InterestSubcategory("Kathi Rolls", "🌯"),
                    InterestSubcategory("Momos", "🥟"),
                    InterestSubcategory("Roshogolla", "🍰"),
                    InterestSubcategory("Mishti Doi", "🍮"),
                    InterestSubcategory("Club Kachori", "🍘"),
                    InterestSubcategory("Kochuri", "🥠"),
                    InterestSubcategory("Paratha", "🥙"),
                    InterestSubcategory("Petai Porota", "🥞"),
                    InterestSubcategory("Jhalmuri", "🍿"),
                    InterestSubcategory("Chai", "☕"),
                    InterestSubcategory("Fish Curry", "🐟"),
                    InterestSubcategory("Biriyani", "🍛"),
                    InterestSubcategory("Sandesh", "🍥"),
                    InterestSubcategory("Luchi-Alur Dom", "🥔"),
                    InterestSubcategory("Chomchom", "🍮"),
                    InterestSubcategory("Telebhaja", "🍤"),
                    InterestSubcategory("Ghugni", "🥣")
                )
            ),
            InterestCategory(
                category = "Festivals & Culture",
                emoji = "🎉",
                subcategories = listOf(
                    InterestSubcategory("Durga Puja", "🙏"),
                    InterestSubcategory("Pandal Hopping", "🏮"),
                    InterestSubcategory("Saraswati Puja", "📚"),
                    InterestSubcategory("Poila Boishakh", "🎊"),
                    InterestSubcategory("Rabindra Jayanti", "🎼"),
                    InterestSubcategory("Book Fair", "📚"),
                    InterestSubcategory("Kolkata Film Festival", "🎬"),
                    InterestSubcategory("Mela Visits", "🎠"),
                    InterestSubcategory("Jagaddhatri Puja", "🕉️"),
                    InterestSubcategory("Christmas Park Street", "🎄"),
                    InterestSubcategory("Eid Celebrations", "🌙")
                )
            ),
            InterestCategory(
                category = "Music",
                emoji = "🎵",
                subcategories = listOf(
                    InterestSubcategory("Rabindra Sangeet", "🎼"),
                    InterestSubcategory("Nazrul Geeti", "🎶"),
                    InterestSubcategory("Bengali Folk Music", "🪕"),
                    InterestSubcategory("Baul Songs", "🎤"),
                    InterestSubcategory("Adhunik Bangla Gaan", "🎧"),
                    InterestSubcategory("Band Music", "🎸"),
                    InterestSubcategory("Classical Indian Music", "🎻"),
                    InterestSubcategory("Modern Bollywood", "🎬"),
                    InterestSubcategory("Western Classical", "🎹"),
                    InterestSubcategory("Rock", "🎸"),
                    InterestSubcategory("Jazz", "🎷"),
                    InterestSubcategory("K-Pop", "🎤")
                )
            ),
            InterestCategory(
                category = "Sports",
                emoji = "🏅",
                subcategories = listOf(
                    InterestSubcategory("Football", "⚽"),
                    InterestSubcategory("East Bengal Club", "🔴"),
                    InterestSubcategory("Mohun Bagan", "🟢"),
                    InterestSubcategory("Mohammedan Sporting", "⚫"),
                    InterestSubcategory("Cricket", "🏏"),
                    InterestSubcategory("Table Tennis", "🏓"),
                    InterestSubcategory("Badminton", "🏸"),
                    InterestSubcategory("Chess", "♟️"),
                    InterestSubcategory("Rowing", "🚣"),
                    InterestSubcategory("Running", "🏃"),
                    InterestSubcategory("Cycling", "🚴"),
                    InterestSubcategory("Esports", "🎮")
                )
            ),
            InterestCategory(
                category = "Movies & Theatre",
                emoji = "🎭",
                subcategories = listOf(
                    InterestSubcategory("Bengali Cinema", "🎥"),
                    InterestSubcategory("Satyajit Ray Films", "🎬"),
                    InterestSubcategory("Tollywood", "🎞️"),
                    InterestSubcategory("Theatre", "🎭"),
                    InterestSubcategory("Jatra", "🎪"),
                    InterestSubcategory("Indian Art Films", "🎬"),
                    InterestSubcategory("Documentaries", "📽️"),
                    InterestSubcategory("International Cinema", "🌐"),
                    InterestSubcategory("Film Festivals", "🎟️"),
                    InterestSubcategory("Drama", "🎭"),
                    InterestSubcategory("Netflix Binging", "📺")
                )
            ),
            InterestCategory(
                category = "Literature & Art",
                emoji = "📚",
                subcategories = listOf(
                    InterestSubcategory("Rabindranath Tagore", "📖"),
                    InterestSubcategory("Sarat Chandra Chattopadhyay", "📘"),
                    InterestSubcategory("Bankim Chandra Chatterjee", "📙"),
                    InterestSubcategory("Bengali Poetry", "📝"),
                    InterestSubcategory("Contemporary Bengali Writers", "📚"),
                    InterestSubcategory("Bengali Comics (Narayan Debnath)", "📖"),
                    InterestSubcategory("Art Galleries", "🖼️"),
                    InterestSubcategory("Painting", "🎨"),
                    InterestSubcategory("Sculpture", "🗿"),
                    InterestSubcategory("Photography", "📷"),
                    InterestSubcategory("Graphic Novels", "📓")
                )
            ),
            InterestCategory(
                category = "Outdoor Activities",
                emoji = "🌳",
                subcategories = listOf(
                    InterestSubcategory("Walks in Victoria Memorial", "🏛️"),
                    InterestSubcategory("Walks in Rabindra Sarobar (Lake)", "🚶‍♂️"),
                    InterestSubcategory("Boating in Ganges", "🚣"),
                    InterestSubcategory("Eco Park Visits", "🌲"),
                    InterestSubcategory("Prinsep Ghat Hangout", "🌉"),
                    InterestSubcategory("Botanical Garden Visits", "🌿"),
                    InterestSubcategory("Zoo Visits", "🦁"),
                    InterestSubcategory("Park Street Strolls", "🌆"),
                    InterestSubcategory("Heritage Walks", "🏘️"),
                    InterestSubcategory("Street Photography", "📷")
                )
            ),
            InterestCategory(
                category = "Socializing & Lifestyle",
                emoji = "☕",
                subcategories = listOf(
                    InterestSubcategory("Adda over Chai", "☕"),
                    InterestSubcategory("Coffee House Chats", "🍵"),
                    InterestSubcategory("Street Food Tours", "🌮"),
                    InterestSubcategory("Bookstore Hangouts", "📚"),
                    InterestSubcategory("Lazing Around", "😴"),
                    InterestSubcategory("Daydreaming", "💭"),
                    InterestSubcategory("Café Hopping", "🍰"),
                    InterestSubcategory("Shopping in New Market", "🛍️"),
                    InterestSubcategory("Nightlife in Kolkata", "🌃"),
                    InterestSubcategory("Fusion Cuisine Tasting", "🍱")
                )
            ),
            InterestCategory(
                category = "Technology & Innovation",
                emoji = "💻",
                subcategories = listOf(
                    InterestSubcategory("Programming", "💻"),
                    InterestSubcategory("Startup Culture", "🚀"),
                    InterestSubcategory("Hackathons", "👨‍💻"),
                    InterestSubcategory("Robotics", "🤖"),
                    InterestSubcategory("AI & Machine Learning", "🧠"),
                    InterestSubcategory("Gaming", "🎮"),
                    InterestSubcategory("Electronics", "🔌"),
                    InterestSubcategory("Blockchain", "⛓️"),
                    InterestSubcategory("Virtual Reality", "🎧")
                )
            ),
            InterestCategory(
                category = "Education & Learning",
                emoji = "🎓",
                subcategories = listOf(
                    InterestSubcategory("Debating", "🗣️"),
                    InterestSubcategory("Elocution", "🎤"),
                    InterestSubcategory("Quizzing", "❓"),
                    InterestSubcategory("Competitive Exams", "📚"),
                    InterestSubcategory("Workshops", "🛠️"),
                    InterestSubcategory("Language Learning", "🈵"),
                    InterestSubcategory("Book Clubs", "📖"),
                    InterestSubcategory("Science Exhibitions", "🔬")
                )
            ),
            InterestCategory(
                category = "Travel & Exploration",
                emoji = "✈️",
                subcategories = listOf(
                    InterestSubcategory("Darjeeling Trips", "⛰️"),
                    InterestSubcategory("Sundarbans Exploration", "🌳"),
                    InterestSubcategory("Digha Beach Visits", "🏖️"),
                    InterestSubcategory("Heritage Site Visits", "🏰"),
                    InterestSubcategory("Weekend Getaways", "🚗"),
                    InterestSubcategory("Adventure Sports", "🏄"),
                    InterestSubcategory("Cultural Tours", "🏛️"),
                    InterestSubcategory("International Travel", "🌍")
                )
            ),
            InterestCategory(
                category = "Fitness & Wellness",
                emoji = "💪",
                subcategories = listOf(
                    InterestSubcategory("Yoga", "🧘"),
                    InterestSubcategory("Gym", "🏋️"),
                    InterestSubcategory("Morning Walks", "🚶‍♂️"),
                    InterestSubcategory("Cycling", "🚴"),
                    InterestSubcategory("Meditation", "🧘‍♂️"),
                    InterestSubcategory("Cricket Matches", "🏏"),
                    InterestSubcategory("Swimming", "🏊"),
                    InterestSubcategory("Marathon Running", "🏃‍♂️")
                )
            ),
            InterestCategory(
                category = "Dance",
                emoji = "💃",
                subcategories = listOf(
                    InterestSubcategory("Rabindra Nritya", "🩰"),
                    InterestSubcategory("Kathak", "👣"),
                    InterestSubcategory("Bharatanatyam", "🙏"),
                    InterestSubcategory("Folk Dance", "💃"),
                    InterestSubcategory("Contemporary Dance", "🕺"),
                    InterestSubcategory("Bollywood Dance", "🎬"),
                    InterestSubcategory("Salsa", "💃"),
                    InterestSubcategory("Hip Hop", "🕺")
                )
            ),
            InterestCategory(
                category = "Art & Craft",
                emoji = "🎨",
                subcategories = listOf(
                    InterestSubcategory("Patachitra", "🖼️"),
                    InterestSubcategory("Terracotta Art", "🏺"),
                    InterestSubcategory("Pottery", "⚱️"),
                    InterestSubcategory("Handicrafts", "🧵"),
                    InterestSubcategory("Embroidery", "🧶"),
                    InterestSubcategory("Origami", "📄"),
                    InterestSubcategory("Graffiti Art", "🎨")
                )
            ),
            InterestCategory(
                category = "Pets & Animals",
                emoji = "🐾",
                subcategories = listOf(
                    InterestSubcategory("Dog Lover", "🐶"),
                    InterestSubcategory("Cat Lover", "🐱"),
                    InterestSubcategory("Bird Watching", "🐦"),
                    InterestSubcategory("Aquarium Fish", "🐠"),
                    InterestSubcategory("Horse Riding", "🐴"),
                    InterestSubcategory("Pet Adoption", "🏠")
                )
            ),
            InterestCategory(
                category = "Social Causes",
                emoji = "🤝",
                subcategories = listOf(
                    InterestSubcategory("Community Service", "🏘️"),
                    InterestSubcategory("Environmental Conservation", "🌿"),
                    InterestSubcategory("Education Initiatives", "🎓"),
                    InterestSubcategory("Healthcare Volunteering", "🏥"),
                    InterestSubcategory("Animal Welfare", "🐾"),
                    InterestSubcategory("Rural Development", "🌾"),
                    InterestSubcategory("Heritage Preservation", "🏛️"),
                    InterestSubcategory("Women's Rights", "👩")
                )
            ),
            InterestCategory(
                category = "Fashion & Lifestyle",
                emoji = "👗",
                subcategories = listOf(
                    InterestSubcategory("Traditional Bengali Attire", "👘"),
                    InterestSubcategory("Sustainable Fashion", "🌱"),
                    InterestSubcategory("Jewelry Design", "💍"),
                    InterestSubcategory("Styling", "💇‍♀️"),
                    InterestSubcategory("Modeling", "💃"),
                    InterestSubcategory("Blogging", "✍️"),
                    InterestSubcategory("Streetwear", "👕")
                )
            ),
            InterestCategory(
                category = "Photography",
                emoji = "📷",
                subcategories = listOf(
                    InterestSubcategory("Street Photography", "🚶"),
                    InterestSubcategory("Landscape", "🏞️"),
                    InterestSubcategory("Portrait", "🖼️"),
                    InterestSubcategory("Wildlife", "🦁"),
                    InterestSubcategory("Astrophotography", "🌌"),
                    InterestSubcategory("Wedding Photography", "💒"),
                    InterestSubcategory("Macro Photography", "🔍")
                )
            ),
            InterestCategory(
                category = "Environmental Activities",
                emoji = "🌍",
                subcategories = listOf(
                    InterestSubcategory("Tree Plantation", "🌳"),
                    InterestSubcategory("Beach Clean-ups", "🏖️"),
                    InterestSubcategory("Sustainable Living", "♻️"),
                    InterestSubcategory("Wildlife Conservation", "🐾"),
                    InterestSubcategory("Cycling Initiatives", "🚴")
                )
            ),
            InterestCategory(
                category = "Science & Technology",
                emoji = "🔬",
                subcategories = listOf(
                    InterestSubcategory("Astronomy", "🌌"),
                    InterestSubcategory("Physics", "🧪"),
                    InterestSubcategory("Chemistry", "⚗️"),
                    InterestSubcategory("Biology", "🧬"),
                    InterestSubcategory("Robotics", "🤖"),
                    InterestSubcategory("Gadgets", "📱"),
                    InterestSubcategory("Space Exploration", "🚀")
                )
            ),
            InterestCategory(
                category = "Language & Literature",
                emoji = "🈵",
                subcategories = listOf(
                    InterestSubcategory("Bengali Language", "🕌"),
                    InterestSubcategory("English Literature", "📖"),
                    InterestSubcategory("French Language", "🇫🇷"),
                    InterestSubcategory("Japanese Anime & Manga", "🇯🇵"),
                    InterestSubcategory("Hindi Poetry", "📜"),
                    InterestSubcategory("Regional Dialects", "🗣️")
                )
            ),
            InterestCategory(
                category = "Entertainment",
                emoji = "🎭",
                subcategories = listOf(
                    InterestSubcategory("Stand-up Comedy", "🎙️"),
                    InterestSubcategory("Theater Performances", "🎭"),
                    InterestSubcategory("TV Series", "📺"),
                    InterestSubcategory("Web Series", "💻"),
                    InterestSubcategory("Reality Shows", "🎤"),
                    InterestSubcategory("Acting Workshops", "🎬"),
                    InterestSubcategory("Playwriting", "✍️")
                )
            )
        )


        // State to add custom interests
        var customInterest by remember { mutableStateOf(TextFieldValue("")) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Interests", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
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
                        // Interests Selection
                        Text(
                            text = "Select Your Interests",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Interests with Dropdowns
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                                                    selectedInterests.add(
                                                        Interest(name = subcategory.name, emoji = subcategory.emoji)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Custom Interest Input
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
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
                                        selectedInterests.add(Interest(name = customInterest.text))
                                        customInterest = TextFieldValue("") // Clear input after adding
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                            ) {
                                Text(text = "Add Interest", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Save Interests Button
                        Button(
                            onClick = {
                                onInterestsUpdated(selectedInterests)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
                            shape = CircleShape
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


    private fun updateProfile(updatedProfile: Profile) {
        userRef.setValue(updatedProfile)
            .addOnSuccessListener {
                runOnUiThread {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    // Navigate back to the main profile screen
                    finish()
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
