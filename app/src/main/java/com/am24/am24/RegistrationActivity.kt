// RegistrationActivity.kt
package com.am24.am24

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
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
import com.am24.am24.Profile
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

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
    var interests = mutableStateListOf<String>()
    var profilePictureUri by mutableStateOf<Uri?>(null)
    var optionalPhotoUris = mutableStateListOf<Uri>()
    var profilePicUrl by mutableStateOf<String?>(null)
    var optionalPhotoUrls = mutableStateListOf<String>()
    var promptsAndAnswers = mutableStateMapOf<String, String>()
    var hometown by mutableStateOf("")
    var bio by mutableStateOf("")
    var highSchool by mutableStateOf("")
    var college by mutableStateOf("")
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
        6 -> EnterPromptsAndAnswersScreen(registrationViewModel, onNext, onBack)
        7 -> EnterLocationAndSchoolScreen(registrationViewModel, onNext, onBack)
        8 -> EnterProfileHeadlineScreen(registrationViewModel, onRegistrationComplete, onBack)
    }
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

@Composable
fun EnterUsernameScreen(registrationViewModel: RegistrationViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    var username by remember { mutableStateOf(TextFieldValue(registrationViewModel.username)) }

    BasicInputScreen(
        title = "Enter Your Username",
        label = "Username",
        value = username,
        onValueChange = {
            username = it
            registrationViewModel.username = it.text
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

    val interestCategories = listOf(
        InterestCategory("Music", listOf("Rock", "Pop", "Classical", "Jazz", "Hip Hop", "Country", "Electronic")),
        InterestCategory("Sports", listOf("Football", "Basketball", "Tennis", "Swimming", "Running", "Cycling", "Gymnastics")),
        InterestCategory("Travel", listOf("Adventure", "Beaches", "Mountains", "Road Trips", "Cultural Tours", "Cruises")),
        InterestCategory("Movies", listOf("Action", "Comedy", "Drama", "Horror", "Sci-Fi", "Romance")),
        InterestCategory("Technology", listOf("AI", "Robotics", "Gadgets", "Programming", "Gaming")),
        InterestCategory("Art", listOf("Painting", "Sculpture", "Photography", "Digital Art")),
        InterestCategory("Reading", listOf("Fiction", "Non-fiction", "Fantasy", "Mystery", "Science")),
        InterestCategory("Food", listOf("Cooking", "Baking", "Vegan", "BBQ", "Seafood")),
        InterestCategory("Fitness", listOf("Yoga", "Gym", "Pilates", "Crossfit", "Martial Arts")),
        InterestCategory("Fashion", listOf("Design", "Modeling", "Styling", "Streetwear")),
        InterestCategory("Nature", listOf("Hiking", "Wildlife", "Conservation", "Gardening")),
        InterestCategory("Science", listOf("Physics", "Chemistry", "Biology", "Astronomy")),
        InterestCategory("History", listOf("Ancient", "Medieval", "Modern", "World Wars")),
        InterestCategory("Languages", listOf("English", "Spanish", "French", "Mandarin", "Japanese")),
        InterestCategory("Pets", listOf("Dogs", "Cats", "Birds", "Reptiles", "Aquatic")),
        InterestCategory("Volunteer Work", listOf("Community Service", "Environmental", "Education", "Healthcare")),
        InterestCategory("Photography", listOf("Landscape", "Portrait", "Wildlife", "Astrophotography")),
        InterestCategory("Dance", listOf("Ballet", "Hip Hop", "Salsa", "Contemporary", "Ballroom")),
        InterestCategory("Theater", listOf("Acting", "Directing", "Playwriting", "Musical Theater")),
        InterestCategory("Automobiles", listOf("Cars", "Motorcycles", "Classic Cars", "Racing"))
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
                                Text(text = category.category)
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black)
                            ) {
                                category.subcategories.forEach { subcategory ->
                                    val isSelected = registrationViewModel.interests.contains(subcategory)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = subcategory,
                                                color = if (isSelected) Color(0xFF00bf63) else Color.White
                                            )
                                        },
                                        onClick = {
                                            if (isSelected) {
                                                registrationViewModel.interests.remove(subcategory)
                                            } else {
                                                registrationViewModel.interests.add(subcategory)
                                            }
                                        }
                                    )
                                }
                            }
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

data class InterestCategory(val category: String, val subcategories: List<String>)

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
fun EnterPromptsAndAnswersScreen(registrationViewModel: RegistrationViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    val prompts = listOf("What inspires you?", "Favorite travel destination?", "Your proudest moment?")
    val selectedAnswers = registrationViewModel.promptsAndAnswers

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
                        text = "Enter Prompts & Answers",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Display prompts and text fields for answers
                    prompts.forEach { prompt ->
                        Text(
                            text = prompt,
                            color = Color(0xFF00bf63),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = selectedAnswers[prompt] ?: "",
                            onValueChange = { selectedAnswers[prompt] = it },
                            label = { Text("Answer", color = Color(0xFF00bf63)) },
                            singleLine = false,
                            maxLines = 3,
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

@Composable
fun EnterHometownScreen(registrationViewModel: RegistrationViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    var hometown by remember { mutableStateOf(TextFieldValue(registrationViewModel.hometown)) }

    BasicInputScreen(
        title = "Enter Your Hometown",
        label = "Location",
        value = hometown,
        onValueChange = {
            hometown = it
            registrationViewModel.hometown = it.text
        },
        onNext = onNext,
        onBack = onBack
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
                                        interests = registrationViewModel.interests.toList(),
                                        hometown = registrationViewModel.hometown,
                                        profilepicUrl = registrationViewModel.profilePicUrl,
                                        optionalPhotoUrls = registrationViewModel.optionalPhotoUrls.toList(),
                                        matches = emptyList()
                                        // Include other fields as needed
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