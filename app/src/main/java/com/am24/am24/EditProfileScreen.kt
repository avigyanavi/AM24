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
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Firebase setup
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = currentUser?.uid ?: ""
    val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

// State variables for fields
    var profile by remember { mutableStateOf(Profile()) }
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var username by remember { mutableStateOf(TextFieldValue("")) }
    var bio by remember { mutableStateOf(TextFieldValue("")) }
    val interests = remember { mutableStateListOf<Interest>() }
    var hometown by remember { mutableStateOf("") }
    var highSchool by remember { mutableStateOf("") }
    var college by remember { mutableStateOf("") }
    var postGraduation by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var community by remember { mutableStateOf("") }
    var religion by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var lookingFor by remember { mutableStateOf("") }
    var claimedIncomeLevel by remember { mutableStateOf("") }

    // Lifestyle fields
    var lifestyle by remember { mutableStateOf(Lifestyle()) }
    val smokingOptions = listOf("Non-smoker", "Social Smoker", "Regular Smoker")
    val drinkingOptions = listOf("Occasional", "Social Drinker", "Heavy Drinker")
    val dietOptions = listOf("Vegan", "Vegetarian", "Non-Vegetarian", "Keto")
    val sleepCycleOptions = listOf("Early Riser", "Night Owl", "Balanced")
    val workLifeBalanceOptions = listOf("Workaholic", "Balanced", "Relaxed")

    var showEditInterestsScreen by remember { mutableStateOf(false) }

    // Predefined lists
    val hometowns = listOf("Kolkata", "Chennai", "West Lafayette", "Lafayette", "Chicago")
    val highSchools = listOf("St. Xavier's High School", "Delhi Public School", "Modern High School")
    val colleges = listOf("IIT Delhi", "Jadavpur University", "St. Xavier's College")
    val postGraduationColleges = listOf("Calcutta University", "VIT University", "Delhi University")
    val genderOptions = listOf("Male", "Female", "Other")
    val communityOptions = listOf("Marwari", "Bengali", "Punjabi", "Tamil")
    val religionOptions = listOf("Hindu", "Muslim", "Christian", "Other")
    val countries = listOf("India", "USA", "UK", "Canada")
    val cities  = listOf("Kolkata", "Bangalore", "West Lafayette", "Chicago")
    val lookingForOptions = listOf("Friendship", "Dating", "Serious Relationship")
    val incomeLevels = listOf("Below 50K", "50K-100K", "100K-200K", "Above 200K")

    var hometownText by remember { mutableStateOf("") } // Resolved: Ensure it's declared
    var highSchoolText by remember { mutableStateOf("") }
    var collegeText by remember { mutableStateOf("") }
    var cityText by remember { mutableStateOf("")}
    var postGraduationCollegeText by remember { mutableStateOf("") }

    var customHometown by remember { mutableStateOf(false) } // Boolean variable
    var customHighSchool by remember { mutableStateOf(false) }
    var customCollege by remember { mutableStateOf(false) }
    var customPostGraduation by remember { mutableStateOf(false) }
    var customCity by remember { mutableStateOf(false) }

    var expandedHometown by remember { mutableStateOf(false) }
    var expandedHighSchool by remember { mutableStateOf(false) }
    var expandedCollege by remember { mutableStateOf(false) }
    var expandedPostGraduationCollege by remember { mutableStateOf(false) }
    var expandedCity by remember { mutableStateOf(false) }

    var hometownSearch by remember { mutableStateOf("") }
    var highSchoolSearch by remember { mutableStateOf("") }
    var collegeSearch by remember { mutableStateOf("") }
    var postGraduationCollegeSearch by remember { mutableStateOf("") }
    var citySearch by remember { mutableStateOf("") }

    val filteredHometowns = hometowns.filter { it.contains(hometownSearch, ignoreCase = true) }
    val filteredHighSchools = highSchools.filter { it.contains(highSchoolSearch, ignoreCase = true) }
    val filteredColleges = colleges.filter { it.contains(collegeSearch, ignoreCase = true) }
    val filteredPostGraduationColleges = postGraduationColleges.filter { it.contains(postGraduationCollegeSearch, ignoreCase = true) }
    val filteredCities = cities.filter { it.contains(citySearch, ignoreCase = true)}

    // Load profile data
    LaunchedEffect(Unit) {
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                profile = snapshot.getValue(Profile::class.java) ?: Profile()
                name = TextFieldValue(profile.name)
                hometownText = profile.hometown
                highSchoolText = profile.highSchool
                collegeText = profile.college
                cityText = profile.city
                postGraduationCollegeText = profile.postGraduation.toString()

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
                hometown = profile.hometown
                highSchool = profile.highSchool
                college = profile.college
                postGraduation = profile.postGraduation ?: ""
                gender = profile.gender
                community = profile.community
                religion = profile.religion
                country = profile.country
                city = profile.city
                lookingFor = profile.lookingFor
                claimedIncomeLevel = profile.claimedIncomeLevel ?: ""
                lifestyle = profile.lifestyle ?: Lifestyle()
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


                    // Hometown Dropdown with Search and Custom Input
                    Text("Enter Your Hometown", color = Color.White, fontSize = 18.sp)
                    if (customHometown) {
                        OutlinedTextField(
                            value = hometownText,
                            onValueChange = {
                                hometownText = it
                                customHometown = true // Boolean to track custom input mode
                            },
                            label = { Text("Custom Hometown", color = Color(0xFF00bf63)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedLabelColor = Color(0xFF00bf63),
                                focusedBorderColor = Color(0xFF00bf63),
                                cursorColor = Color(0xFF00bf63),
                                focusedTextColor = Color.White
                            )
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = expandedHometown,
                            onExpandedChange = { expandedHometown = !expandedHometown }
                        ) {
                            OutlinedTextField(
                                value = hometownText,
                                onValueChange = {
                                    hometownText = it
                                    expandedHometown = true
                                },
                                label = { Text("Search Hometown", color = Color(0xFF00bf63)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedLabelColor = Color(0xFF00bf63),
                                    focusedBorderColor = Color(0xFF00bf63),
                                    cursorColor = Color(0xFF00bf63),
                                    focusedTextColor = Color.White
                                )
                            )
                            DropdownMenu(
                                expanded = expandedHometown,
                                onDismissRequest = { expandedHometown = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black)
                            ) {
                                filteredHometowns.forEach { option ->
                                    DropdownMenuItem(
                                        text = {Text(option, color = Color.White)},
                                        onClick = {
                                            hometownText = option
                                            expandedHometown = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Didn't find your Hometown? Add", color = Color.White)},
                                    onClick = {
                                        customHometown = true
                                        expandedHometown = false
                                    }
                                )
                            }
                        }
                    }


                    Spacer(modifier = Modifier.height(8.dp))

                    // High School Dropdown with Search and Custom Input
                    Text("Enter Your High School", color = Color.White, fontSize = 18.sp)
                    if (customHighSchool) {
                        OutlinedTextField(
                            value = highSchoolText,
                            onValueChange = {
                                highSchoolText = it
                                customHighSchool = true // Boolean to track custom input mode
                            },
                            label = { Text("Custom High School", color = Color(0xFF00bf63)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedLabelColor = Color(0xFF00bf63),
                                focusedBorderColor = Color(0xFF00bf63),
                                cursorColor = Color(0xFF00bf63),
                                focusedTextColor = Color.White
                            )
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = expandedHighSchool,
                            onExpandedChange = { expandedHighSchool = !expandedHighSchool }
                        ) {
                            OutlinedTextField(
                                value = highSchoolText,
                                onValueChange = {
                                    highSchoolText = it
                                    expandedHighSchool = true
                                },
                                label = { Text("Search High School", color = Color(0xFF00bf63)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedLabelColor = Color(0xFF00bf63),
                                    focusedBorderColor = Color(0xFF00bf63),
                                    cursorColor = Color(0xFF00bf63),
                                    focusedTextColor = Color.White
                                )
                            )
                            DropdownMenu(
                                expanded = expandedHighSchool,
                                onDismissRequest = { expandedHighSchool = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black)
                            ) {
                                filteredHighSchools.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = Color.White) },
                                        onClick = {
                                            highSchoolText = option
                                            expandedHighSchool = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {Text("Didn't find your High School? Add", color = Color.White)
                                    },
                                    onClick = {
                                        customHighSchool = true
                                        expandedHighSchool = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // College Dropdown with Search and Custom Input
                    Text("Enter Your College", color = Color.White, fontSize = 18.sp)
                    if (customCollege) {
                        OutlinedTextField(
                            value = collegeText,
                            onValueChange = {
                                collegeText = it
                                customCollege = true // Boolean to track custom input mode
                            },
                            label = { Text("Custom College", color = Color(0xFF00bf63)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedLabelColor = Color(0xFF00bf63),
                                focusedBorderColor = Color(0xFF00bf63),
                                cursorColor = Color(0xFF00bf63),
                                focusedTextColor = Color.White
                            )
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = expandedCollege,
                            onExpandedChange = { expandedCollege = !expandedCollege }
                        ) {
                            OutlinedTextField(
                                value = collegeText,
                                onValueChange = {
                                    collegeText = it
                                    expandedCollege = true
                                },
                                label = { Text("Search College", color = Color(0xFF00bf63)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedLabelColor = Color(0xFF00bf63),
                                    focusedBorderColor = Color(0xFF00bf63),
                                    cursorColor = Color(0xFF00bf63),
                                    focusedTextColor = Color.White
                                )
                            )
                            DropdownMenu(
                                expanded = expandedCollege,
                                onDismissRequest = { expandedCollege = false },
                                modifier = Modifier.fillMaxWidth().background(Color.Black)
                            ) {
                                filteredColleges.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = Color.White)},
                                        onClick = {
                                            collegeText = option
                                            expandedCollege = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Didn't find your College? Add", color = Color.White)},
                                    onClick = {
                                        customCollege = true
                                        expandedCollege = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    // Country Dropdown
                DropdownWithSearch(
                    title = "Select Country",
                    options = countries,
                    selectedOption = country,
                    onOptionSelected = { country = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                    // City Dropdown with Search and Custom Input
                    Text("Enter Your City", color = Color.White, fontSize = 18.sp)
                    if (customCity) {
                        OutlinedTextField(
                            value = cityText,
                            onValueChange = {
                                cityText = it
                                customCity = true // Boolean to track custom input mode
                            },
                            label = { Text("Custom City", color = Color(0xFF00bf63)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedLabelColor = Color(0xFF00bf63),
                                focusedBorderColor = Color(0xFF00bf63),
                                cursorColor = Color(0xFF00bf63),
                                focusedTextColor = Color.White
                            )
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = expandedCity,
                            onExpandedChange = { expandedCity = !expandedCity }
                        ) {
                            OutlinedTextField(
                                value = cityText,
                                onValueChange = {
                                    cityText = it
                                    expandedCity = true
                                },
                                label = { Text("Search City", color = Color(0xFF00bf63)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedLabelColor = Color(0xFF00bf63),
                                    focusedBorderColor = Color(0xFF00bf63),
                                    cursorColor = Color(0xFF00bf63),
                                    focusedTextColor = Color.White
                                )
                            )
                            DropdownMenu(
                                expanded = expandedCity,
                                onDismissRequest = { expandedCity = false },
                                modifier = Modifier.fillMaxWidth().background(Color.Black)
                            ) {
                                filteredCities.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = Color.White)},
                                        onClick = {
                                            cityText = option
                                            expandedCity = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Didn't find your City? Add", color = Color.White)},
                                    onClick = {
                                        customCity = true
                                        expandedCity = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    //Post Graduate dropdown:
                    Text("Enter Your Post Graduate College", color = Color.White, fontSize = 18.sp)
                    if (customPostGraduation) {
                        OutlinedTextField(
                            value = postGraduationCollegeText,
                            onValueChange = {
                                postGraduationCollegeText = it
                                customPostGraduation = true // Boolean to track custom input mode
                            },
                            label = { Text("Custom Post Graduation College", color = Color(0xFF00bf63)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedLabelColor = Color(0xFF00bf63),
                                focusedBorderColor = Color(0xFF00bf63),
                                cursorColor = Color(0xFF00bf63),
                                focusedTextColor = Color.White
                            )
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = expandedPostGraduationCollege,
                            onExpandedChange = { expandedPostGraduationCollege = !expandedPostGraduationCollege }
                        ) {
                            OutlinedTextField(
                                value = postGraduationCollegeText,
                                onValueChange = {
                                    postGraduationCollegeText = it
                                    expandedPostGraduationCollege = true
                                },
                                label = { Text("Search Post Graduation College", color = Color(0xFF00bf63)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedLabelColor = Color(0xFF00bf63),
                                    focusedBorderColor = Color(0xFF00bf63),
                                    cursorColor = Color(0xFF00bf63),
                                    focusedTextColor = Color.White
                                )
                            )
                            DropdownMenu(
                                expanded = expandedPostGraduationCollege,
                                onDismissRequest = { expandedPostGraduationCollege = false },
                                modifier = Modifier.fillMaxWidth().background(Color.Black)
                            ) {
                                filteredPostGraduationColleges.forEach { option ->
                                    DropdownMenuItem(
                                        text = {Text(option, color = Color.White)},
                                        onClick = {
                                            postGraduationCollegeText = option
                                            expandedPostGraduationCollege = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {Text("Didn't find your College? Add", color = Color.White)},
                                            onClick = {
                                        customPostGraduation = true
                                        expandedPostGraduationCollege = false
                                    }
                                )
                            }
                        }
                    }

                Spacer(modifier = Modifier.height(16.dp))

                // Looking For Dropdown
                DropdownWithSearch(
                    title = "Looking For",
                    options = lookingForOptions,
                    selectedOption = lookingFor,
                    onOptionSelected = { lookingFor = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Claimed Income Level Dropdown
                DropdownWithSearch(
                    title = "Claimed Income Level",
                    options = incomeLevels,
                    selectedOption = claimedIncomeLevel,
                    onOptionSelected = { claimedIncomeLevel = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                        // Lifestyle Section
                        Text("Lifestyle", color = Color(0xFF00bf63), fontSize = 18.sp, fontWeight = FontWeight.Bold)

                        // Smoking Dropdown
                        DropdownWithSearch(
                            title = "Smoking",
                            options = smokingOptions,
                            selectedOption = lifestyle.smoking,
                            onOptionSelected = { lifestyle = lifestyle.copy(smoking = it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Drinking Dropdown
                        DropdownWithSearch(
                            title = "Drinking",
                            options = drinkingOptions,
                            selectedOption = lifestyle.drinking,
                            onOptionSelected = { lifestyle = lifestyle.copy(drinking = it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Diet Dropdown
                        DropdownWithSearch(
                            title = "Diet",
                            options = dietOptions,
                            selectedOption = lifestyle.diet,
                            onOptionSelected = { lifestyle = lifestyle.copy(diet = it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sleep Cycle Dropdown
                        DropdownWithSearch(
                            title = "Sleep Cycle",
                            options = sleepCycleOptions,
                            selectedOption = lifestyle.sleepCycle,
                            onOptionSelected = { lifestyle = lifestyle.copy(sleepCycle = it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Work-Life Balance Dropdown
                        DropdownWithSearch(
                            title = "Work-Life Balance",
                            options = workLifeBalanceOptions,
                            selectedOption = lifestyle.workLifeBalance,
                            onOptionSelected = { lifestyle = lifestyle.copy(workLifeBalance = it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                    // Interests
                    Button(
                        onClick = { showEditInterestsScreen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                    ) {
                        Text(
                            "Edit Interests",
                            color = Color.White,
                            fontSize = 16.sp
                        )
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
                                    if (customHometown) hometownText else hometown,
                                    if (customHighSchool) highSchoolText else highSchool,
                                    if (customCollege) collegeText else college,
                                    if (customPostGraduation) postGraduationCollegeText else postGraduation,
                                    gender,
                                    community,
                                    religion,
                                    country,
                                    if (customCity) cityText else city,
                                    lookingFor,
                                    claimedIncomeLevel,
                                    interests,
                                    lifestyle
                                )
                                Toast.makeText(context, "Profile Updated", Toast.LENGTH_SHORT).show()
                                navController?.navigateUp()
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
    hometown: String,
    highSchool: String,
    college: String,
    postGraduation: String,
    gender: String,
    community: String,
    religion: String,
    country: String,
    city: String,
    lookingFor: String,
    claimedIncomeLevel: String,
    interests: List<Interest>,
    lifestyle: Lifestyle,
) {
    val updates = mapOf(
        "name" to name,
        "username" to username,
        "bio" to bio,
        "hometown" to hometown,
        "highSchool" to highSchool,
        "college" to college,
        "postGraduation" to postGraduation,
        "gender" to gender,
        "community" to community,
        "religion" to religion,
        "country" to country,
        "city" to city,
        "lookingFor" to lookingFor,
        "claimedIncomeLevel" to claimedIncomeLevel,
        "interests" to interests.map { mapOf("name" to it.name, "emoji" to it.emoji) },
        "lifestyle" to lifestyle
    )

    FirebaseDatabase.getInstance().getReference("users").child(FirebaseAuth.getInstance().currentUser?.uid ?: "")
        .updateChildren(updates).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                // Handle error
            }
        }
}




fun extractEmojiAndText(input: String): Pair<String, String> {
    val emojiRegex = Regex("[\\p{So}\\p{Sc}\\p{Sk}\\p{Sm}\\p{Cs}]+") // Matches emojis
    val emojis =
        emojiRegex.findAll(input).map { it.value }.joinToString("") // Find and join all emojis
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
                InterestSubcategory("Kathi Rolls", "🌯")
            )
        ),
        InterestCategory(
            category = "Festivals & Culture",
            emoji = "🎉",
            subcategories = listOf(
                InterestSubcategory("Durga Puja", "🙏"),
                InterestSubcategory("Saraswati Puja", "📚")
            )
        )
    )

    val selectedInterests =
        remember { mutableStateListOf<Interest>().apply { addAll(initialInterests) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Interests", color = Color.White) },
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
                                val isSelected =
                                    selectedInterests.any { it.name == subcategory.name }
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
                                                Interest(
                                                    name = subcategory.name,
                                                    emoji = subcategory.emoji
                                                )
                                            )
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
                                selectedInterests.add(
                                    Interest(
                                        name = name,
                                        emoji = emojis
                                    )
                                ) // Add all found emojis
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


