// RegistrationActivity.kt
package com.am24.am24

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Calendar

class RegistrationActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN

        setContent {
            AppTheme {
                RegistrationScreen(onRegistrationComplete = {
                    // After registration, navigate to LoginActivity
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                })
            }
        }
    }
}

class RegistrationViewModel : ViewModel() {
    var voiceNoteUri by mutableStateOf<Uri?>(null)  // To hold the voice recording URI
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var name by mutableStateOf("")
    var username by mutableStateOf("")
    var dob by mutableStateOf("")
    var interests = mutableStateListOf<Interest>()
    var profilePictureUri by mutableStateOf<Uri?>(null)
    var optionalPhotoUris = mutableStateListOf<Uri>()
    var profilePicUrl by mutableStateOf<String?>(null)
    var voiceNoteUrl by mutableStateOf<String?>(null)
    var optionalPhotoUrls = mutableStateListOf<String>()
    var hometown by mutableStateOf("")
    var bio by mutableStateOf("")
    var gender by mutableStateOf("")
    var customHometown by mutableStateOf("")
    var religion by mutableStateOf("")
    var community by mutableStateOf("")

    // Newly added fields
    var city by mutableStateOf("")            // Current city
    var customCity by mutableStateOf("")      // For custom city input if not in dropdown
    var educationLevel by mutableStateOf("")  // For user's highest education level

    var highSchool by mutableStateOf("")
    var customHighSchool by mutableStateOf("")
    var highSchoolGraduationYear by mutableStateOf("")

    var college by mutableStateOf("")
    var customCollege by mutableStateOf("")
    var collegeGraduationYear by mutableStateOf("")

    var postGraduation by mutableStateOf("")
    var customPostGraduation by mutableStateOf("")
    var postGraduationYear by mutableStateOf("")

    var work by mutableStateOf("")
    var customWork by mutableStateOf("")

    // Added fields for new Profile components
    var lifestyle by mutableStateOf(Lifestyle())   // Lifestyle information (smoking, drinking, etc.)
    var lookingFor by mutableStateOf("")           // What the user is looking for (Friendship, Relationship, etc.)
    var politics by mutableStateOf("")             // User's political views
    var fitnessLevel by mutableStateOf("")         // User's fitness activity level
    var socialCauses = mutableStateListOf<String>() // List of user's selected social causes
}

@Composable
fun RegistrationScreen(onRegistrationComplete: () -> Unit) {
    val registrationViewModel: RegistrationViewModel = viewModel()
    var currentStep by remember { mutableStateOf(1) }

    val context = LocalContext.current

    val onNext = { currentStep += 1 }
    val onBack: () -> Unit = {
        if (currentStep > 1) {
            currentStep -= 1
        } else {
            (context as? ComponentActivity)?.finish()
        }
    }

    when (currentStep) {
        1 -> EnterEmailAndPasswordScreen(registrationViewModel, onNext, onBack)
        2 -> EnterNameScreen(registrationViewModel, onNext, onBack)
        3 -> UploadMediaComposable(registrationViewModel, onNext, onBack)
        4 -> EnterBirthDateAndInterestsScreen(registrationViewModel, onNext, onBack)
        5 -> EnterLocationAndSchoolScreen(registrationViewModel, onNext, onBack)
        6 -> EnterGenderCommunityReligionScreen(registrationViewModel, onNext, onBack)
        7 -> EnterProfileHeadlineScreen(registrationViewModel, onNext, onBack)
        8 -> EnterUsernameScreen(registrationViewModel, onRegistrationComplete, onBack) // Username screen comes last
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterLocationAndSchoolScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Predefined lists for dropdowns
    val cities = listOf("Kolkata", "Mumbai", "Delhi", "Bangalore", "Hyderabad", "Chennai", "Ahmedabad")
    val cityLocalities = mapOf(
        "Kolkata" to listOf("Salt Lake", "Garia", "Dumdum", "Park Street", "Behala"),
        "Mumbai" to listOf("Andheri", "Bandra", "Dadar", "Borivali", "Colaba"),
        "Delhi" to listOf("Connaught Place", "Dwarka", "Saket", "Karol Bagh", "Lajpat Nagar"),
        "Bangalore" to listOf("Whitefield", "Koramangala", "Indiranagar", "Jayanagar", "Marathahalli"),
        "Hyderabad" to listOf("Banjara Hills", "Begumpet", "Hitech City", "Kukatpally", "Gachibowli"),
        "Chennai" to listOf("Adyar", "T Nagar", "Velachery", "Anna Nagar", "Besant Nagar"),
        "Ahmedabad" to listOf("Satellite", "Navrangpura", "Vastrapur", "Bopal", "Paldi")
    )
    val highSchools = listOf("St. Xavier's", "La Martiniere", "South Point", "Modern High School")
    val colleges = listOf("IIT Kharagpur", "Jadavpur University", "Presidency University")
    val postGraduations = listOf("IIM Calcutta", "ISB Hyderabad")
    val companies = listOf("Google", "Microsoft", "Amazon", "Facebook", "Apple")

    // Default city setup
    if (registrationViewModel.city.isEmpty()) {
        registrationViewModel.city = "Kolkata"
    }
    var localities by remember { mutableStateOf(cityLocalities[registrationViewModel.city] ?: emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text("City", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SearchableDropdownWithCustomOption(
                            title = "Select or type your city",
                            options = cities,
                            selectedOption = registrationViewModel.city,
                            onOptionSelected = { city ->
                                registrationViewModel.city = city
                                localities = cityLocalities[city] ?: emptyList()
                                registrationViewModel.hometown = "" // Reset locality
                            },
                            customInput = registrationViewModel.customCity,
                            onCustomInputChange = { registrationViewModel.customCity = it }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        Text("Locality", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SearchableDropdownWithCustomOption(
                            title = "Select or type your locality",
                            options = localities,
                            selectedOption = registrationViewModel.hometown,
                            onOptionSelected = { locality ->
                                registrationViewModel.hometown = locality
                            },
                            customInput = registrationViewModel.customHometown,
                            onCustomInputChange = { registrationViewModel.customHometown = it }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        Text("High School", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SearchableDropdownWithCustomOption(
                            title = "Select or type your high school",
                            options = highSchools,
                            selectedOption = registrationViewModel.highSchool,
                            onOptionSelected = { registrationViewModel.highSchool = it },
                            customInput = registrationViewModel.customHighSchool,
                            onCustomInputChange = { registrationViewModel.customHighSchool = it }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        Text("College", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SearchableDropdownWithCustomOption(
                            title = "Select or type your college",
                            options = colleges,
                            selectedOption = registrationViewModel.college,
                            onOptionSelected = { registrationViewModel.college = it },
                            customInput = registrationViewModel.customCollege,
                            onCustomInputChange = { registrationViewModel.customCollege = it }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        Text("Post Graduation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SearchableDropdownWithCustomOption(
                            title = "Select or type your post-graduation institute",
                            options = postGraduations,
                            selectedOption = registrationViewModel.postGraduation,
                            onOptionSelected = { registrationViewModel.postGraduation = it },
                            customInput = registrationViewModel.customPostGraduation,
                            onCustomInputChange = { registrationViewModel.customPostGraduation = it }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    item {
                        Text("Work", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        SearchableDropdownWithCustomOption(
                            title = "Select or type your workplace",
                            options = companies,
                            selectedOption = registrationViewModel.work,
                            onOptionSelected = { registrationViewModel.work = it },
                            customInput = registrationViewModel.customWork,
                            onCustomInputChange = { registrationViewModel.customWork = it }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }

                    item {
                        Button(
                            onClick = {
                                if (registrationViewModel.city.isEmpty()) {
                                    Toast.makeText(context, "City cannot be empty", Toast.LENGTH_SHORT).show()
                                } else {
                                    onNext()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                            shape = CircleShape,
                            elevation = ButtonDefaults.buttonElevation(8.dp)
                        ) {
                            Text(
                                text = "Next",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableDropdownWithCustomOption(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    customInput: String,
    onCustomInputChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, color = Color.White, fontSize = 18.sp)

        // Main button to toggle dropdown
        OutlinedButton(
            onClick = {
                expanded = !expanded
                searchText = ""
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFFFF4500)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA500))
        ) {
            Text(
                text = if (showCustomInput) customInput else selectedOption.ifEmpty { "Select or type" },
                color = Color(0xFFFFA500)
            )
        }

        // Dropdown menu with search functionality
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            TextField(
                value = searchText,
                onValueChange = { input ->
                    searchText = input
                    // Show custom input if no match is found
                    showCustomInput = options.none { it.equals(input, ignoreCase = true) }
                },
                label = { Text("Search", color = Color(0xFFFFA500)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedLabelColor = Color(0xFFFF4500),
                    focusedBorderColor = Color(0xFFFF4500),
                    cursorColor = Color(0xFFFF4500),
                    focusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Filtered options
            options.filter { it.contains(searchText, ignoreCase = true) }
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onOptionSelected(option)
                            searchText = "" // Clear search text
                            expanded = false
                            showCustomInput = false
                        }
                    )
                }
        }

        // Show custom input field only if the user enters a unique value
        if (showCustomInput) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customInput,
                onValueChange = {
                    onCustomInputChange(it)
                    onOptionSelected("") // Clear selected dropdown value when custom input is typed
                },
                label = { Text("Enter custom value", color = Color(0xFFFFA500)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedLabelColor = Color(0xFFFF4500),
                    focusedBorderColor = Color(0xFFFF4500),
                    cursorColor = Color(0xFFFF4500),
                    focusedTextColor = Color.White
                )
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterEmailAndPasswordScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf(TextFieldValue(registrationViewModel.email)) }
    var password by remember { mutableStateOf(TextFieldValue(registrationViewModel.password)) }
    var confirmPassword by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current

    var isCreatingAccount by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (isCreatingAccount) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xAA000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF4500))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create Account",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Email Input
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            registrationViewModel.email = it.text
                        },
                        label = { Text("Email", color = Color(0xFFFF4500)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF4500),
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFFFFA500),
                            focusedLabelColor = Color(0xFFFF4500),
                            unfocusedLabelColor = Color(0xFFFFA500)
                        )
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            registrationViewModel.password = it.text
                        },
                        label = { Text("Password", color = Color(0xFFFF4500)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF4500),
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFFFFA500),
                            focusedLabelColor = Color(0xFFFF4500),
                            unfocusedLabelColor = Color(0xFFFFA500)
                        )
                    )

                    // Confirm Password Input
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password", color = Color(0xFFFF4500)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF4500),
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFFFFA500),
                            focusedLabelColor = Color(0xFFFF4500),
                            unfocusedLabelColor = Color(0xFFFFA500)
                        )
                    )

                    if (passwordError) {
                        Text("Passwords do not match", color = Color.Red)
                    }

                    Button(
                        onClick = {
                            if (email.text.isNotEmpty() && password.text.isNotEmpty()) {
                                if (password.text == confirmPassword.text) {
                                    isCreatingAccount = true
                                    passwordError = false
                                    (context as? ComponentActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                                        try {
                                            val authResult = FirebaseAuth.getInstance()
                                                .createUserWithEmailAndPassword(
                                                    email.text.trim(),
                                                    password.text.trim()
                                                )
                                                .await()

                                            val user = authResult.user
                                            user?.sendEmailVerification()?.await()

                                            withContext(Dispatchers.Main) {
                                                registrationViewModel.email = email.text.trim()
                                                registrationViewModel.password = password.text.trim()
                                                Toast.makeText(context, "Account created. Verification email sent.", Toast.LENGTH_LONG).show()
                                                isCreatingAccount = false
                                                onNext()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                isCreatingAccount = false
                                            }
                                        }
                                    }
                                } else {
                                    passwordError = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isCreatingAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        Text(
                            text = "Next",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterGenderCommunityReligionScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    // Sample predefined lists
    val genderOptions = listOf("Male", "Female", "Non Binary")
    val communityOptions = listOf("Marwari", "Bengali", "Punjabi", "Tamil")
    val religionOptions = listOf("Hindu", "Muslim", "Christian", "Other")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                    // Gender Dropdown
                    DropdownWithSearch(
                        title = "Select Gender",
                        options = genderOptions,
                        selectedOption = registrationViewModel.gender,
                        onOptionSelected = { registrationViewModel.gender = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Community Dropdown
                    DropdownWithSearch(
                        title = "Select Community",
                        options = communityOptions,
                        selectedOption = registrationViewModel.community,
                        onOptionSelected = { registrationViewModel.community = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Religion Dropdown
                    DropdownWithSearch(
                        title = "Select Religion",
                        options = religionOptions,
                        selectedOption = registrationViewModel.religion,
                        onOptionSelected = { registrationViewModel.religion = it }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Next Button
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        Text(
                            text = "Next",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownWithSearch(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDropdownClicked: () -> Unit = {} // Optional callback for dropdown click
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, color = Color.White, fontSize = 18.sp)
        OutlinedButton(
            onClick = {
                onDropdownClicked() // Invoke the callback on dropdown click
                expanded = !expanded
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFFFF4500)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4500))
        ) {
            Text(text = selectedOption.ifEmpty { "Select" }, color = Color(0xFFFFA500))
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
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedLabelColor = Color(0xFFFF4500),
                    focusedBorderColor = Color(0xFFFF4500),
                    cursorColor = Color(0xFFFF4500),
                    focusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            options.filter { it.contains(searchText, ignoreCase = true) }
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterUsernameScreen(
    registrationViewModel: RegistrationViewModel,
    onRegistrationComplete: () -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf(TextFieldValue(registrationViewModel.username)) }
    val database = FirebaseDatabase.getInstance().reference
    var isUsernameValid by remember { mutableStateOf(true) }
    var usernameErrorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            isUsernameValid = true
                            usernameErrorMessage = ""
                        },
                        label = { Text("Username", color = Color(0xFFFF4500)) },
                        singleLine = true,
                        isError = !isUsernameValid,
                        supportingText = {
                            if (!isUsernameValid) {
                                Text(text = usernameErrorMessage, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF4500),
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFFFFA500)
                        )
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                val trimmedUsername = username.text.trim()
                                val invalidChars = listOf('.', '#', '$', '[', ']')

                                if (trimmedUsername.isEmpty()) {
                                    isUsernameValid = false
                                    usernameErrorMessage = "Username cannot be empty"
                                    return@launch
                                }
                                if (invalidChars.any { trimmedUsername.contains(it) }) {
                                    isUsernameValid = false
                                    usernameErrorMessage = "Username contains invalid characters (., #, $, [, ])"
                                    return@launch
                                }

                                isUsernameValid = true

                                val encodedUsername = URLEncoder.encode(trimmedUsername, "UTF-8")
                                database.child("usernames").child(encodedUsername)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(snapshot: DataSnapshot) {
                                            if (snapshot.exists()) {
                                                isUsernameValid = false
                                                usernameErrorMessage = "Username already taken"
                                            } else {
                                                // Save the username both globally and in user's profile
                                                scope.launch {
                                                    try {
                                                        // Save to global usernames node for future validation
                                                        database.child("usernames").child(encodedUsername).setValue(true)
                                                        registrationViewModel.username = trimmedUsername

                                                        // Save the profile to Firebase
                                                        saveProfileToFirebase(registrationViewModel, onRegistrationComplete)
                                                    } catch (e: Exception) {
                                                        Log.e("EnterUsernameScreen", "Error saving username: ${e.message}")
                                                    }
                                                }
                                            }
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            isUsernameValid = false
                                            usernameErrorMessage = "Error: ${error.message}"
                                        }
                                    })
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        Text(
                            text = "Finish",
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

suspend fun saveProfileToFirebase(
    registrationViewModel: RegistrationViewModel,
    onRegistrationComplete: () -> Unit
) {
    try {
        val database = FirebaseDatabase.getInstance().reference
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Create a Profile object with the new fields
        val profile = Profile(
            userId = userId,
            username = registrationViewModel.username,
            name = registrationViewModel.name,
            dob = registrationViewModel.dob,
            bio = registrationViewModel.bio,
            gender = registrationViewModel.gender,
            interests = registrationViewModel.interests.toList(),
            hometown = registrationViewModel.hometown.ifEmpty { registrationViewModel.customHometown },
            highSchool = if (registrationViewModel.highSchool == "Other") registrationViewModel.customHighSchool else registrationViewModel.highSchool,
            college = if (registrationViewModel.college == "Other") registrationViewModel.customCollege else registrationViewModel.college,
            postGraduation = if (registrationViewModel.postGraduation == "Other") registrationViewModel.customPostGraduation else registrationViewModel.postGraduation,
            work = if (registrationViewModel.work == "Other") registrationViewModel.customWork else registrationViewModel.work,
            profilepicUrl = registrationViewModel.profilePicUrl,
            optionalPhotoUrls = registrationViewModel.optionalPhotoUrls.toList(),
            religion = registrationViewModel.religion,
            community = registrationViewModel.community,

            // New fields
            city = registrationViewModel.city.ifEmpty { registrationViewModel.customCity }, // New: current city
            educationLevel = registrationViewModel.educationLevel,   // New: education level
            lifestyle = registrationViewModel.lifestyle,             // New: lifestyle preferences
            lookingFor = registrationViewModel.lookingFor,           // New: what the user is looking for
            politics = registrationViewModel.politics,               // New: political views
            fitnessLevel = registrationViewModel.fitnessLevel,       // New: fitness level
            socialCauses = registrationViewModel.socialCauses.toList(), // New: social causes
            voiceNoteUrl = registrationViewModel.voiceNoteUrl   // Add the voice URL
        )

        // Save the profile to Firebase Realtime Database
        database.child("users").child(userId).setValue(profile).await()

        withContext(Dispatchers.Main) {
            onRegistrationComplete()
        }
    } catch (e: Exception) {
        Log.e("Registration", "Failed to save profile: ${e.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicInputScreen(
    title: String,
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Input Field
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = { Text(label, color = Color(0xFFFF4500)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF4500),
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFFFFA500),
                            focusedLabelColor = Color(0xFFFF4500),
                            unfocusedLabelColor = Color(0xFFFFA500)
                        )
                    )

                    // Next Button
                    Button(
                        onClick = { onNext() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        Text(
                            text = "Next",
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

@Composable
fun EnterNameScreen(registrationViewModel: RegistrationViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(registrationViewModel.name)) }

    BasicInputScreen(
        title = "Enter Your Name",
        label = "Full Name",
        value = name,
        onValueChange = {
            name = it
            registrationViewModel.name = it.text
        },
        onNext = onNext,
        onBack = onBack
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterBirthDateAndInterestsScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(registrationViewModel.dob) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customInterest by remember { mutableStateOf(TextFieldValue("")) }

    // Interest categories and subcategories
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

    // DatePicker logic
    val calendar = Calendar.getInstance()
    if (showDatePicker) {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }

                // Prevent selecting a date in the future
                if (selectedCalendar.timeInMillis <= calendar.timeInMillis) {
                    selectedDate = "$dayOfMonth/${month + 1}/$year"
                    registrationViewModel.dob = selectedDate
                } else {
                    Toast.makeText(context, "Birth date cannot be in the future.", Toast.LENGTH_SHORT).show()
                }
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // Set the maximum date to today
            datePicker.maxDate = calendar.timeInMillis
        }.show()
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                    // Title
                    Text(
                        text = "Enter Birth Date and Interests",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Birth Date Picker Button
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Black,
                            contentColor = Color(0xFFFFA500)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFF4500))
                    ) {
                        Text(
                            text = if (selectedDate.isNotEmpty()) selectedDate else "Select Birth Date",
                            color = Color(0xFFFFA500)
                        )
                    }

                    // Interests Selection
                    Text(
                        text = "Select Interests",
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
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
                                    contentColor = Color(0xFFFFA500)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFF4500))
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
                                    val isSelected = registrationViewModel.interests.any { it.name == subcategory.name }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${subcategory.emoji} ${subcategory.name}",
                                                color = if (isSelected) Color(0xFFFF4500) else Color.White
                                            )
                                        },
                                        onClick = {
                                            if (isSelected) {
                                                registrationViewModel.interests.removeIf { it.name == subcategory.name }
                                            } else {
                                                registrationViewModel.interests.add(
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
                        TextField(
                            value = customInterest,
                            onValueChange = { customInterest = it },
                            label = { Text("Add Custom Interest", color = Color(0xFFFF4500)) },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedLabelColor = Color(0xFFFF4500),
                                focusedBorderColor = Color(0xFFFFA500),
                                cursorColor = Color(0xFFFF4500),
                                focusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        Button(
                            onClick = {
                                if (customInterest.text.isNotBlank()) {
                                    registrationViewModel.interests.add(Interest(name = customInterest.text))
                                    customInterest = TextFieldValue("") // Clear input after adding
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))
                        ) {
                            Text(text = "Add Interest", color = Color.White)
                        }
                    }

                    // Display Added Interests
                    Text("Your Interests", color = Color.White, fontSize = 18.sp)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        items(registrationViewModel.interests) { interest ->
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color.Black, shape = CircleShape)
                                    .clickable {
                                        registrationViewModel.interests.remove(interest)
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


                    // Check if the birth date is selected to enable "Next" button
                    val isNextEnabled = selectedDate.isNotEmpty()

                    Spacer(modifier = Modifier.height(24.dp))

                    // Next Button
                    Button(
                        onClick = { onNext() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = isNextEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isNextEnabled) Color(0xFFFFA500) else Color.Gray),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        Text(
                            text = "Next",
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

data class InterestSubcategory(val name: String, val emoji: String)
data class InterestCategory(val category: String, val emoji: String, val subcategories: List<InterestSubcategory>)

//------------------edit media---------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadMediaComposable(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storageRef = FirebaseStorage.getInstance().reference
    var profilePictureUri by remember { mutableStateOf(registrationViewModel.profilePictureUri) }
    val images = remember { mutableStateListOf<Uri>().apply { addAll(registrationViewModel.optionalPhotoUris) } }

    val profilePicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                profilePictureUri = uri
                registrationViewModel.profilePictureUri = uri
                uploadProfilePicToFirebase(storageRef, uri, registrationViewModel)
            }
        }
    )

    val placeholderUrl = "https://firebasestorage.googleapis.com/v0/b/am-twentyfour.appspot.com/o/photos%2Fplaceholder.jpg?alt=media&token=7eec2ce4-094d-4b4a-be04-0d988dda242c"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Media", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tap to upload Profile Picture", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(99.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(2.dp, Color(0xFFFF4500), CircleShape)
                        .clickable { profilePicLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePictureUri != null) {
                        AsyncImage(
                            model = profilePictureUri,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Add Profile Picture",
                            tint = Color(0xFFFF4500),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Next Button
                Button(
                    onClick = {
                        if (profilePictureUri == null) {
                            // Assign placeholder URL if no profile picture is provided
                            registrationViewModel.profilePicUrl = placeholderUrl
                        }
                        onNext()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))
                ) {
                    Text(text = "Next", color = Color.White)
                }
            }
        }
    )
}


// Functions to upload media to Firebase Storage
fun uploadProfilePicToFirebase(storageRef: StorageReference, uri: Uri, registrationViewModel: RegistrationViewModel) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val profilePicRef = storageRef.child("users/${userId}/profile_pic.jpg")
    profilePicRef.putFile(uri).addOnSuccessListener {
        profilePicRef.downloadUrl.addOnSuccessListener { downloadUri ->
            registrationViewModel.profilePicUrl = downloadUri.toString()
        }
    }.addOnFailureListener {
        Log.e("UploadMedia", "Failed to upload profile picture: ${it.message}")
    }
}

fun uploadOptionalPhotosToFirebase(storageRef: StorageReference, uris: List<Uri>, registrationViewModel: RegistrationViewModel) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    uris.forEachIndexed { index, uri ->
        val photoRef = storageRef.child("users/${userId}/optional_photo_$index.jpg")
        photoRef.putFile(uri).addOnSuccessListener {
            photoRef.downloadUrl.addOnSuccessListener { downloadUri ->
                registrationViewModel.optionalPhotoUrls.add(downloadUri.toString())
            }
        }.addOnFailureListener {
            Log.e("UploadMedia", "Failed to upload optional photo: ${it.message}")
        }
    }
}

fun uploadVoiceToFirebase(storageRef: StorageReference, uri: Uri, registrationViewModel: RegistrationViewModel) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val voiceRef = storageRef.child("users/${userId}/voice_note.aac") // Use the correct extension
    voiceRef.putFile(uri).addOnSuccessListener {
        voiceRef.downloadUrl.addOnSuccessListener { downloadUri ->
            registrationViewModel.voiceNoteUrl = downloadUri.toString()
            Log.d("UploadMedia", "Voice note uploaded successfully: $downloadUri")
        }
    }.addOnFailureListener {
        Log.e("UploadMedia", "Failed to upload voice note: ${it.message}")
    }
}

//-----------------edit media-----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterProfileHeadlineScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit, // Triggered after the user proceeds to the next screen
    onBack: () -> Unit  // Triggered when the user wants to go back
) {
    var headline by remember { mutableStateOf(TextFieldValue(registrationViewModel.bio)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Headline Input (Bio)
                    OutlinedTextField(
                        value = headline,
                        onValueChange = {
                            headline = it
                            registrationViewModel.bio = it.text
                        },
                        label = { Text("One-liner Bio (optional)", color = Color(0xFFFFA500)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF4500),
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFFFFA500)
                        )
                    )

                    // Button to move to the next step
                    Button(
                        onClick = {
                            // Save the bio to the ViewModel
                            registrationViewModel.bio = headline.text
                            // Proceed to the next screen
                            onNext()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        Text(
                            text = "Next",
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