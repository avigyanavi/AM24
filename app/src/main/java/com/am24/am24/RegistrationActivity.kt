// RegistrationActivity.kt
package com.am24.am24

import android.content.Intent
import com.am24.am24.Profile
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import coil.compose.AsyncImage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar
import java.net.URLEncoder
import java.net.URLDecoder

class RegistrationActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private val storage = FirebaseStorage.getInstance().reference

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
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var name by mutableStateOf("")
    var username by mutableStateOf("")
    var dob by mutableStateOf("")
    var interests = mutableStateListOf<Interest>()
    var profilePictureUri by mutableStateOf<Uri?>(null)
    var optionalPhotoUris = mutableStateListOf<Uri>()
    var profilePicUrl by mutableStateOf<String?>(null)
    var optionalPhotoUrls = mutableStateListOf<String>()
    var hometown by mutableStateOf("")
    var bio by mutableStateOf("")
    var highSchool by mutableStateOf("")
    var college by mutableStateOf("")
    var gender by mutableStateOf("")
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
        3 -> EnterUsernameScreen(registrationViewModel, onNext, onBack)
        4 -> EnterBirthDateAndInterestsScreen(registrationViewModel, onNext, onBack)
        5 -> UploadPhotosScreen(registrationViewModel, onNext, onBack)
        6 -> EnterLocationAndSchoolScreen(registrationViewModel, onNext, onBack)
        7 -> EnterProfileHeadlineScreen(registrationViewModel, onRegistrationComplete, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterUsernameScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf(TextFieldValue(registrationViewModel.username)) }
    var gender by remember { mutableStateOf(registrationViewModel.gender) }
    val genderOptions = listOf("M", "F", "T")
    var expanded by remember { mutableStateOf(false) }
    var isUsernameValid by remember { mutableStateOf(true) }
    var isCheckingUsername by remember { mutableStateOf(false) }
    var usernameErrorMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                        text = "Enter Your Username",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Username Input Field
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            registrationViewModel.username = it.text
                            isUsernameValid = true // Reset validation on change
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00bf63),
                            focusedBorderColor = if (isUsernameValid) Color(0xFF00bf63) else MaterialTheme.colorScheme.error,
                            unfocusedBorderColor = if (isUsernameValid) Color(0xFF00bf63) else MaterialTheme.colorScheme.error
                        )
                    )

                    // Gender Selection
                    Text(
                        text = "Select Your Gender",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFF00bf63)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00bf63))
                        ) {
                            Text(
                                text = if (gender.isNotEmpty()) gender else "Select Gender",
                                color = Color(0xFF00bf63)
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                        ) {
                            genderOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = Color.White) },
                                    onClick = {
                                        gender = option
                                        registrationViewModel.gender = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Next Button
                    Button(
                        onClick = {
                            scope.launch {
                                val trimmedUsername = username.text.trim()
                                if (trimmedUsername.isEmpty()) {
                                    isUsernameValid = false
                                    usernameErrorMessage = "Username cannot be empty"
                                    return@launch
                                }
                                if (gender.isEmpty()) {
                                    Toast.makeText(context, "Please select your gender", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                isCheckingUsername = true
                                val database = FirebaseDatabase.getInstance().reference
                                val encodedUsername = URLEncoder.encode(trimmedUsername, "UTF-8")
                                database.child("usernames").child(encodedUsername)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(snapshot: DataSnapshot) {
                                            if (snapshot.exists()) {
                                                // Username taken
                                                isUsernameValid = false
                                                usernameErrorMessage = "Username already taken"
                                                isCheckingUsername = false
                                            } else {
                                                // Username is unique, reserve it
                                                database.child("usernames").child(encodedUsername).setValue(true)
                                                isCheckingUsername = false
                                                // Proceed to next screen
                                                onNext()
                                            }
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            isUsernameValid = false
                                            usernameErrorMessage = "Error checking username: ${error.message}"
                                            isCheckingUsername = false
                                            Log.e("EnterUsernameScreen", "DatabaseError: ${error.message}", error.toException())
                                        }
                                    })
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isCheckingUsername,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        Text(
                            text = if (isCheckingUsername) "Checking..." else "Next",
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
fun EnterLocationAndSchoolScreen(registrationViewModel: RegistrationViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    val highSchools = listOf("St. Xavier's High School", "Delhi Public School", "Modern High School")
    val colleges = listOf("IIT Delhi", "Jadavpur University", "St. Xavier's College")

    var city by remember { mutableStateOf(registrationViewModel.hometown) }
    var highSchool by remember { mutableStateOf(registrationViewModel.highSchool) }
    var college by remember { mutableStateOf(registrationViewModel.college) }

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
                    // City Input
                    Text("Enter Your City", color = Color.White, fontSize = 18.sp)
                    OutlinedTextField(
                        value = city,
                        onValueChange = {
                            city = it
                            registrationViewModel.hometown = it
                        },
                        label = { Text("City", color = Color(0xFF00bf63)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00bf63),
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color(0xFF00bf63)
                        )
                    )

                    // High School Dropdown and Custom Input
                    Text("Select or Enter Your High School", color = Color.White, fontSize = 18.sp)
                    OutlinedTextField(
                        value = highSchool,
                        onValueChange = {
                            highSchool = it
                            registrationViewModel.highSchool = it
                        },
                        label = { Text("High School", color = Color(0xFF00bf63)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00bf63),
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color(0xFF00bf63)
                        )
                    )

                    // College Dropdown and Custom Input
                    Text("Select or Enter Your College", color = Color.White, fontSize = 18.sp)
                    OutlinedTextField(
                        value = college,
                        onValueChange = {
                            college = it
                            registrationViewModel.college = it
                        },
                        label = { Text("College", color = Color(0xFF00bf63)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00bf63),
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color(0xFF00bf63)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Next Button
                    Button(
                        onClick = { onNext() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
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
fun EnterEmailAndPasswordScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf(TextFieldValue(registrationViewModel.email)) }
    var password by remember { mutableStateOf(TextFieldValue(registrationViewModel.password)) }
    val context = LocalContext.current

    var isCreatingAccount by remember { mutableStateOf(false) }

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
                if (isCreatingAccount) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xAA000000)), // semi-transparent background
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00bf63))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
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
                        label = { Text("Email", color = Color(0xFF00bf63)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00bf63),
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color(0xFF00bf63),
                            focusedLabelColor = Color(0xFF00bf63),
                            unfocusedLabelColor = Color(0xFF00bf63)
                        )
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            registrationViewModel.password = it.text
                        },
                        label = { Text("Password", color = Color(0xFF00bf63)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00bf63),
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color(0xFF00bf63),
                            focusedLabelColor = Color(0xFF00bf63),
                            unfocusedLabelColor = Color(0xFF00bf63)
                        )
                    )

                    // Next Button
                    Button(
                        onClick = {
                            if (email.text.isNotEmpty() && password.text.isNotEmpty()) {
                                isCreatingAccount = true
                                (context as? ComponentActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                                    try {
                                        // Create user account
                                        val authResult = FirebaseAuth.getInstance()
                                            .createUserWithEmailAndPassword(
                                                email.text.trim(),
                                                password.text.trim()
                                            )
                                            .await()

                                        // Send verification email
                                        val user = authResult.user
                                        user?.sendEmailVerification()?.await()

                                        withContext(Dispatchers.Main) {
                                            registrationViewModel.email = email.text.trim()
                                            registrationViewModel.password = password.text.trim()
                                            Toast.makeText(context, "Account created successfully. Verification email sent.", Toast.LENGTH_LONG).show()
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
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isCreatingAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
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
                        label = { Text(label, color = Color(0xFF00bf63)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00bf63),
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color(0xFF00bf63),
                            focusedLabelColor = Color(0xFF00bf63),
                            unfocusedLabelColor = Color(0xFF00bf63)
                        )
                    )

                    // Next Button
                    Button(
                        onClick = { onNext() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
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


    // Adjusted DatePicker logic
    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = "$dayOfMonth/${month + 1}/$year"
                registrationViewModel.dob = selectedDate
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
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
                            contentColor = Color(0xFF00bf63)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF00bf63))
                    ) {
                        Text(
                            text = if (selectedDate.isNotEmpty()) selectedDate else "Select Birth Date",
                            color = Color(0xFF00bf63)
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
                                    val isSelected = registrationViewModel.interests.any { it.name == subcategory.name }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${subcategory.emoji} ${subcategory.name}",
                                                color = if (isSelected) Color(0xFF00bf63) else Color.White
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
                                    registrationViewModel.interests.add(Interest(name = customInterest.text))
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

                    // Next Button
                    Button(
                        onClick = { onNext() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadPhotosScreen(registrationViewModel: RegistrationViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current

    // State for profile picture
    var profilePictureUri by remember { mutableStateOf(registrationViewModel.profilePictureUri) }

    // State for optional photos
    val images = remember { mutableStateListOf<Uri>().apply { addAll(registrationViewModel.optionalPhotoUris) } }

    var isUploading by remember { mutableStateOf(false) }

    val profilePicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                profilePictureUri = uri
                registrationViewModel.profilePictureUri = uri
            }
        }
    )

    val photosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> ->
            if (images.size + uris.size <= 9) {
                images.addAll(uris)
                registrationViewModel.optionalPhotoUris.addAll(uris)
            } else {
                Toast.makeText(context, "You can upload up to 9 photos", Toast.LENGTH_SHORT).show()
            }
        }
    )

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
                    // Title
                    Text(
                        text = "Upload Photos",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Profile Picture Section
                    Text(
                        text = "Profile Picture",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
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
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Optional Photos Section
                    Text(
                        text = "Optional Photos (up to 9)",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Display Selected Photos
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        items(images) { uri ->
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .padding(4.dp)
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Uploaded Image",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Add Photos Button
                    OutlinedButton(
                        onClick = { photosLauncher.launch("image/*") },
                        modifier = Modifier.padding(top = 8.dp),
                        border = BorderStroke(1.dp, Color(0xFF00bf63)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00bf63))
                    ) {
                        Text(text = "Add Photos", color = Color(0xFF00bf63))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isUploading) {
                        // Show loading indicator
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xAA000000)), // semi-transparent background
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00bf63))
                        }
                    }

                    // Next Button
                    Button(
                        onClick = {
                            if (profilePictureUri != null) {
                                isUploading = true
                                (context as? ComponentActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                                    try {
                                        val auth = FirebaseAuth.getInstance()
                                        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

                                        val storageRef = FirebaseStorage.getInstance().reference.child("users/$userId")

                                        // Upload Profile Picture
                                        val profilePicRef = storageRef.child("profile_picture")
                                        profilePicRef.putFile(profilePictureUri!!).await()
                                        val profilePicUrl = profilePicRef.downloadUrl.await().toString()
                                        registrationViewModel.profilePicUrl = profilePicUrl

                                        // Upload Optional Photos
                                        val photoUrls = mutableListOf<String>()
                                        images.forEachIndexed { index, uri ->
                                            val imageRef = storageRef.child("optional_photos/photo_$index")
                                            imageRef.putFile(uri).await()
                                            val url = imageRef.downloadUrl.await().toString()
                                            photoUrls.add(url)
                                        }
                                        registrationViewModel.optionalPhotoUrls.clear()
                                        registrationViewModel.optionalPhotoUrls.addAll(photoUrls)

                                        withContext(Dispatchers.Main) {
                                            isUploading = false
                                            onNext()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isUploading = false
                                            Toast.makeText(context, "Failed to upload photos: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please add a profile picture", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isUploading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
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
fun EnterProfileHeadlineScreen(
    registrationViewModel: RegistrationViewModel,
    onRegistrationComplete: () -> Unit,
    onBack: () -> Unit
) {
    var headline by remember { mutableStateOf(TextFieldValue(registrationViewModel.bio)) }
    val context = LocalContext.current

    var isSavingProfile by remember { mutableStateOf(false) }

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
                if (isSavingProfile) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xAA000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00bf63))
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = "Enter a Profile Headline",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Input Field
                    OutlinedTextField(
                        value = headline,
                        onValueChange = {
                            headline = it
                            registrationViewModel.bio = it.text
                        },
                        label = { Text("One-liner Bio", color = Color(0xFF00bf63)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00bf63),
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color(0xFF00bf63),
                            focusedLabelColor = Color(0xFF00bf63),
                            unfocusedLabelColor = Color(0xFF00bf63)
                        )
                    )

                    // Finish Button
                    Button(
                        onClick = {
                            isSavingProfile = true
                            (context as? ComponentActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                                try {
                                    val auth = FirebaseAuth.getInstance()
                                    val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

                                    val profile = Profile(
                                        userId = userId,
                                        username = registrationViewModel.username,
                                        name = registrationViewModel.name,
                                        dob = registrationViewModel.dob,
                                        bio = registrationViewModel.bio,
                                        gender = registrationViewModel.gender,
                                        interests = registrationViewModel.interests.toList(),
                                        hometown = registrationViewModel.hometown,
                                        highSchool = registrationViewModel.highSchool,
                                        college = registrationViewModel.college,
                                        profilepicUrl = registrationViewModel.profilePicUrl,
                                        optionalPhotoUrls = registrationViewModel.optionalPhotoUrls.toList(),
                                        matches = emptyList()
                                    )

                                    val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
                                    userRef.setValue(profile).await()

                                    withContext(Dispatchers.Main) {
                                        isSavingProfile = false
                                        Toast.makeText(context, "Profile saved successfully.", Toast.LENGTH_SHORT).show()
                                        onRegistrationComplete()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSavingProfile = false
                                        Toast.makeText(context, "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isSavingProfile,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
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