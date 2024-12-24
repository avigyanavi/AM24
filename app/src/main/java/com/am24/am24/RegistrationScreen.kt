// RegistrationActivity.kt
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterial3Api::class
)

package com.am24.am24

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
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
    // Voice Recording
    var voiceNoteUri by mutableStateOf<Uri?>(null) // To hold the voice recording URI
    var voiceNoteFilePath by mutableStateOf<String?>(null) // To hold the file path
    private var voiceRecorder: MediaRecorder? = null

    // Profile and Photos
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

    var height by mutableStateOf(169) // Height in centimeters
    var height2 by mutableStateOf(listOf(5, 7)) // Height in feet + inches (default example: 5'7")
    var isHeightInFeet by mutableStateOf(false) // Toggle for height unit preference (cm or feet+inches)
    var caste by mutableStateOf("") // User's caste

    // Hometown and Education
    var hometown by mutableStateOf("") // Treated as Locality
    var bio by mutableStateOf("")
    var gender by mutableStateOf("")
    var customHometown by mutableStateOf("")
    var religion by mutableStateOf("")
    var community by mutableStateOf("")
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

    // Lifestyle and Preferences
    var lifestyle by mutableStateOf(Lifestyle())   // Lifestyle information (smoking, drinking, etc.)
    var lookingFor by mutableStateOf("")           // What the user is looking for (Friendship, Relationship, etc.)
    var politics by mutableStateOf("")             // User's political views
    var socialCauses = mutableStateListOf<String>() // List of user's selected social causes

    // New fields for dating preferences
    var datingAgeStart by mutableStateOf(18)        // Starting age for preference
    var datingAgeEnd by mutableStateOf(30)          // Ending age for preference
    var datingDistancePreference by mutableStateOf(10) // Distance preference in kilometers
    var interestedIn = mutableStateListOf<String>() // List for "Men," "Women," "Other"

    // Voice Recording Methods
    fun startVoiceRecording(context: Context, filePath: String) {
        try {
            voiceRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(filePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                prepare()
                start()
            }
            voiceNoteFilePath = filePath
        } catch (e: Exception) {
            Log.e("RegistrationViewModel", "Error starting voice recording: ${e.message}")
        }
    }

    fun stopVoiceRecording() {
        try {
            voiceRecorder?.apply {
                stop()
                release()
            }
            voiceRecorder = null
        } catch (e: Exception) {
            Log.e("RegistrationViewModel", "Error stopping voice recording: ${e.message}")
        }
    }
}

@Composable
fun RegistrationScreen(onRegistrationComplete: () -> Unit) {
    val registrationViewModel: RegistrationViewModel = viewModel()
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 9  // Updated total steps
    val progress = currentStep.toFloat() / totalSteps.toFloat()

    val context = LocalContext.current

    val onNext = { currentStep += 1 }
    val onBack: () -> Unit = {
        if (currentStep > 1) {
            currentStep -= 1
        } else {
            (context as? ComponentActivity)?.finish()
        }
    }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(innerPadding)
            ) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Color(0xFFFFA500),
                    trackColor = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content based on the current step
                when (currentStep) {
                    1 -> EnterEmailAndPasswordScreen(registrationViewModel, onNext, onBack)
                    2 -> EnterNameScreen(registrationViewModel, onNext, onBack)
                    3 -> UploadMediaComposable(registrationViewModel, onNext, onBack)
                    4 -> EnterBirthDateAndInterestsScreen(registrationViewModel, onNext, onBack)
                    5 -> EnterLocationAndSchoolScreen(registrationViewModel, onNext, onBack)
                    6 -> EnterGenderCommunityReligionScreen(registrationViewModel, onNext, onBack)
                    7 -> EnterLifestyleScreen(registrationViewModel, onNext, onBack)  // New step
                    8 -> EnterProfileHeadlineScreen(registrationViewModel, onNext, onBack)
                    9 -> EnterUsernameScreen(registrationViewModel, onRegistrationComplete, onBack)
                }
            }
        }
    )
}


private fun deleteIncompleteRegistration() {
    val currentUser = FirebaseAuth.getInstance().currentUser
    if (currentUser != null && !currentUser.isEmailVerified) {
        currentUser.delete()
            .addOnSuccessListener {
                Log.d("RegistrationActivity", "Unverified user account deleted successfully.")
            }
            .addOnFailureListener { exception ->
                Log.e("RegistrationActivity", "Failed to delete unverified user: ${exception.message}")
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterLifestyleScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Lifestyle Preferences",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Smoking Slider
                item {
                    LifestyleSlider(
                        label = "Smoking",
                        value = registrationViewModel.lifestyle.smoking,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(smoking = it) },
                        nouns = listOf("Non-Smoker", "Rare Smoker", "Social Smoker", "Frequent Smoker", "Heavy Smoker")
                    )
                }

                // Drinking Slider
                item {
                    LifestyleSlider(
                        label = "Drinking",
                        value = registrationViewModel.lifestyle.drinking,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(drinking = it) },
                        nouns = listOf("Non-Drinker", "Rare Drinker", "Social Drinker", "Frequent Drinker", "Heavy Drinker")
                    )
                }


                // Cannabis Friendly Checkbox
                item {
                    CheckboxInput(
                        label = "Cannabis Friendly",
                        isChecked = registrationViewModel.lifestyle.cannabisFriendly,
                        onCheckedChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(cannabisFriendly = it) }
                    )
                }

                // Indoorsy to Outdoorsy Slider
                item {
                    LifestyleSlider(
                        label = "Indoorsy to Outdoorsy",
                        value = registrationViewModel.lifestyle.indoorsyToOutdoorsy,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(indoorsyToOutdoorsy = it) },
                        nouns = listOf("Indoorsy", "Mostly Indoorsy", "Balanced", "Mostly Outdoorsy", "Outdoorsy")
                    )
                }

                // Social Media Slider
                item {
                    LifestyleSlider(
                        label = "Social Media",
                        value = registrationViewModel.lifestyle.socialMedia,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(socialMedia = it) },
                        nouns = listOf("Invisible", "Watcher", "Casual Participant", "Engager", "Influencer")
                    )
                }

                // Diet Dropdown
                item {
                    DropdownWithStaticOptions(
                        label = "Diet",
                        options = listOf("Vegetarian", "Non-Veg", "Vegan", "Keto", "Eggetarian", "Paleo", "Fruitarian", "Carnivore"),
                        selectedOption = registrationViewModel.lifestyle.diet,
                        onOptionSelected = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(diet = it) }
                    )
                }

                // Sleep Cycle Slider
                item {
                    LifestyleSlider(
                        label = "Sleep Cycle",
                        value = registrationViewModel.lifestyle.sleepCycle,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(sleepCycle = it) },
                        nouns = listOf("Early Riser", "Morning Person", "Balanced", "Night Owl", "Late Night Enthusiast")
                    )
                }

                // Work-Life Balance Slider
                item {
                    LifestyleSlider(
                        label = "Work-Life Balance",
                        value = registrationViewModel.lifestyle.workLifeBalance,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(workLifeBalance = it) },
                        nouns = listOf("Workaholic", "More Work-Oriented", "Balanced", "More Life-Oriented", "Relaxed")
                    )
                }

                // Exercise Frequency Slider
                item {
                    LifestyleSlider(
                        label = "Exercise Frequency",
                        value = registrationViewModel.lifestyle.exerciseFrequency,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(exerciseFrequency = it) },
                        nouns = listOf("Inactive", "Rarely Active", "Moderately Active", "Active", "Very Active")
                    )
                }

                // Family-Oriented Slider
                item {
                    LifestyleSlider(
                        label = "Family-Oriented",
                        value = registrationViewModel.lifestyle.familyOriented,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(familyOriented = it) },
                        nouns = listOf("Independent", "Slightly Family-Oriented", "Balanced", "Family-Oriented", "Very Family-Oriented")
                    )
                }

                // Adventurous Slider
                item {
                    LifestyleSlider(
                        label = "Adventurous",
                        value = registrationViewModel.lifestyle.adventurous,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(adventurous = it) },
                        nouns = listOf("Cautious", "Slightly Adventurous", "Moderately Adventurous", "Adventurous", "Thrill Seeker")
                    )
                }

                // Intellectual Slider
                item {
                    LifestyleSlider(
                        label = "Intellectual",
                        value = registrationViewModel.lifestyle.intellectual,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(intellectual = it) },
                        nouns = listOf("Casual Thinker", "Inquisitive", "Knowledge Seeker", "Intellectual", "Philosopher")
                    )
                }

                // Creative/Artistic Slider
                item {
                    LifestyleSlider(
                        label = "Creative/Artistic",
                        value = registrationViewModel.lifestyle.creativeArtistic,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(creativeArtistic = it) },
                        nouns = listOf("Not Creative", "Somewhat Creative", "Creative", "Very Creative", "Artistic Genius")
                    )
                }

                // Fitness Level Slider
                item {
                    LifestyleSlider(
                        label = "Fitness Level",
                        value = registrationViewModel.lifestyle.fitnessLevel,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(fitnessLevel = it) },
                        nouns = listOf("Sedentary", "Somewhat Fit", "Fit", "Athletic", "Peak Fitness")
                    )
                }

                // Spiritual/Mindful Slider
                item {
                    LifestyleSlider(
                        label = "Spiritual/Mindful",
                        value = registrationViewModel.lifestyle.spiritualMindful,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(spiritualMindful = it) },
                        nouns = listOf("Not Spiritual", "Occasionally Mindful", "Balanced", "Spiritual", "Deeply Mindful")
                    )
                }

                // Humorous/Easygoing Slider
                item {
                    LifestyleSlider(
                        label = "Humorous/Easygoing",
                        value = registrationViewModel.lifestyle.humorousEasyGoing,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(humorousEasyGoing = it) },
                        nouns = listOf("Serious", "Somewhat Easygoing", "Balanced", "Humorous", "Life of the Party")
                    )
                }

                // Professional/Ambitious Slider
                item {
                    LifestyleSlider(
                        label = "Professional/Ambitious",
                        value = registrationViewModel.lifestyle.professionalAmbitious,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(professionalAmbitious = it) },
                        nouns = listOf("Relaxed", "Occasionally Driven", "Balanced", "Ambitious", "Highly Ambitious")
                    )
                }

                // Environmentally Conscious Slider
                item {
                    LifestyleSlider(
                        label = "Environmentally Conscious",
                        value = registrationViewModel.lifestyle.environmentallyConscious,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(environmentallyConscious = it) },
                        nouns = listOf("Not Conscious", "Occasionally Conscious", "Balanced", "Eco-Friendly", "Eco-Champion")
                    )
                }

                // Foodie/Culinary Enthusiast Slider
                item {
                    LifestyleSlider(
                        label = "Foodie/Culinary Enthusiast",
                        value = registrationViewModel.lifestyle.foodieCulinaryEnthusiast,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(foodieCulinaryEnthusiast = it) },
                        nouns = listOf("Basic", "Occasional Foodie", "Balanced", "Food Enthusiast", "Culinary Expert")
                    )
                }

                // Sports Enthusiast Slider (New Field)
                item {
                    LifestyleSlider(
                        label = "Sports Enthusiast",
                        value = registrationViewModel.lifestyle.sportsEnthusiast,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(sportsEnthusiast = it) },
                        nouns = listOf("Non-Sports", "Casual Viewer", "Occasional Player", "Sports Enthusiast", "Sports Fanatic")
                    )
                }

                // Sexually Active Slider (New Field)
                item {
                    LifestyleSlider(
                        label = "Sexual Activity Level",
                        value = registrationViewModel.lifestyle.sal,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(sal = it) },
                        nouns = listOf("Abstinent", "Rarely Active", "Moderately Active", "Active", "Highly Active")
                    )
                }
                // Politically Aware Slider
                item {
                    LifestyleSlider(
                        label = "Politically Aware",
                        value = registrationViewModel.lifestyle.politicallyAware,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(politicallyAware = it) },
                        nouns = listOf("Unaware", "Occasionally Aware", "Balanced", "Aware", "Politically Engaged")
                    )
                }

                // Introvert to Extrovert Slider
                item {
                    LifestyleSlider(
                        label = "Introvert to Extrovert",
                        value = registrationViewModel.lifestyle.IE, // Assuming this is the field for introversion/extroversion
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(IE = it) },
                        nouns = listOf("Highly Introverted", "Somewhat Introverted", "Ambivert", "Somewhat Extroverted", "Highly Extroverted")
                    )
                }


                // Community-Oriented Slider
                item {
                    LifestyleSlider(
                        label = "Community-Oriented",
                        value = registrationViewModel.lifestyle.communityOriented,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = { registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(communityOriented = it) },
                        nouns = listOf("Individualistic", "Occasionally Involved", "Balanced", "Community-Oriented", "Community Leader")
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onNext() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))
                    ) {
                        Text(text = "Next", color = Color.White)
                    }
                }
            }
        }
    )
}


@Composable
fun LifestyleSlider(
    label: String,
    value: Int,
    valueRangeStart: Int,
    valueRangeEnd: Int,
    onValueChange: (Int) -> Unit,
    nouns: List<String>
) {
    val clampedValue = value.coerceIn(valueRangeStart, valueRangeEnd) // Clamp value to avoid out-of-bounds errors
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Slider(
            value = clampedValue.toFloat(),
            onValueChange = { newValue ->
                onValueChange(newValue.toInt().coerceIn(valueRangeStart, valueRangeEnd))
            },
            valueRange = valueRangeStart.toFloat()..valueRangeEnd.toFloat(),
            steps = (valueRangeEnd - valueRangeStart - 1), // Correct number of steps for the slider
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFA500),
                activeTrackColor = Color(0xFFFFA500)
            )
        )
        // Display the corresponding noun safely
        Text(
            text = nouns.getOrElse(clampedValue) { "Unknown" }, // Fallback to "Unknown" if out-of-bounds
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}



@Composable
fun DropdownWithStaticOptions(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(text = label, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFFFFA500)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA500))
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
            options.forEach { option ->
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

@Composable
fun CheckboxInput(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkmarkColor = Color.Black,
                checkedColor = Color(0xFFFFA500),
                uncheckedColor = Color.Gray
            )
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterLocationAndSchoolScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val educationLevels = listOf("High School", "College", "Post-Graduation")

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
                        Text("Education Level", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        DropdownWithSearch(
                            title = "Select Education Level",
                            options = educationLevels,
                            selectedOption = registrationViewModel.educationLevel,
                            onOptionSelected = { registrationViewModel.educationLevel = it }
                        )
                    }

                    if (registrationViewModel.educationLevel in listOf("High School", "College", "Post-Graduation")) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("High School", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            SearchableDropdownWithCustomOption(
                                title = "Select or type your high school",
                                options = listOf("St. Xavier's", "La Martiniere", "Other"),
                                selectedOption = registrationViewModel.highSchool,
                                onOptionSelected = { registrationViewModel.highSchool = it },
                                customInput = registrationViewModel.customHighSchool,
                                onCustomInputChange = { registrationViewModel.customHighSchool = it }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GraduationYearDropdown(
                                    year = registrationViewModel.highSchoolGraduationYear,
                            onYearSelected = { registrationViewModel.highSchoolGraduationYear = it }
                            )
                        }
                    }

                    if (registrationViewModel.educationLevel in listOf("College", "Post-Graduation")) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("College", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            SearchableDropdownWithCustomOption(
                                title = "Select or type your college",
                                options = listOf("IIT Kharagpur", "Jadavpur University", "Other"),
                                selectedOption = registrationViewModel.college,
                                onOptionSelected = { registrationViewModel.college = it },
                                customInput = registrationViewModel.customCollege,
                                onCustomInputChange = { registrationViewModel.customCollege = it }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GraduationYearDropdown(
                                year = registrationViewModel.collegeGraduationYear,
                                onYearSelected = { registrationViewModel.collegeGraduationYear = it }
                            )
                        }
                    }

                    if (registrationViewModel.educationLevel == "Post-Graduation") {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Post Graduation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            SearchableDropdownWithCustomOption(
                                title = "Select or type your post-graduation institute",
                                options = listOf("IIM Calcutta", "ISB Hyderabad", "Other"),
                                selectedOption = registrationViewModel.postGraduation,
                                onOptionSelected = { registrationViewModel.postGraduation = it },
                                customInput = registrationViewModel.customPostGraduation,
                                onCustomInputChange = { registrationViewModel.customPostGraduation = it }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GraduationYearDropdown(
                                year = registrationViewModel.postGraduationYear,
                                onYearSelected = { registrationViewModel.postGraduationYear = it }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (registrationViewModel.educationLevel.isEmpty()) {
                                } else {
                                    onNext()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))
                        ) {
                            Text(text = "Next", color = Color.White)
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraduationYearDropdown(year: String, onYearSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = (1950..currentYear).map { it.toString() }.reversed() // Generate a reversed list of years
    var searchText by remember { mutableStateOf("") }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA500)),
            border = BorderStroke(1.dp, Color(0xFFFF4500))
        ) {
            Text(
                text = year.ifEmpty { "Select Graduation Year" },
                color = Color(0xFFFFA500)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            // Search Box
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Search Year", color = Color(0xFFFFA500)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedLabelColor = Color(0xFFFFA500),
                    cursorColor = Color(0xFFFFA500),
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color(0xFFFFBF00)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            // Filtered Years
            years.filter { it.contains(searchText, ignoreCase = true) }
                .forEach { filteredYear ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = filteredYear,
                                color = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        },
                        onClick = {
                            onYearSelected(filteredYear) // Set only the selected field's year
                            searchText = ""
                            expanded = false
                        }
                    )
                }
        }
    }
}

// Helper Composable for TextField with Label
@Composable
fun TextFieldWithLabel(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = Color(0xFFFF4500)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedLabelColor = Color(0xFFFF4500),
                cursorColor = Color(0xFFFF4500),
                focusedTextColor = Color.White
            )
        )
    }
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
    var showCustomInput by remember { mutableStateOf(selectedOption == "Other") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, color = Color.White, fontSize = 18.sp)

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
                    showCustomInput = false
                },
                label = { Text("Search", color = Color(0xFFFFA500)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedLabelColor = Color(0xFFFF4500),
                    focusedBorderColor = Color(0xFFFF4500),
                    cursorColor = Color(0xFFFF4500),
                    focusedTextColor = Color.White
                )
            )

            options.filter { it.contains(searchText, ignoreCase = true) }
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                            showCustomInput = option == "Other"
                        }
                    )
                }
        }

        // Custom Input Field
        if (showCustomInput) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customInput,
                onValueChange = {
                    onCustomInputChange(it)
                    onOptionSelected("Other")
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
    var datingAgeStart by remember { mutableStateOf(registrationViewModel.datingAgeStart) }
    var datingAgeEnd by remember { mutableStateOf(registrationViewModel.datingAgeEnd) }
    var datingDistancePreference by remember { mutableStateOf(registrationViewModel.datingDistancePreference) }
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
                    // Username Input
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

                    // Dating Age Preferences
                    Text("Preferred Age Range", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = datingAgeStart.toString(),
                            onValueChange = {
                                datingAgeStart = it.toIntOrNull() ?: datingAgeStart
                                registrationViewModel.datingAgeStart = datingAgeStart
                            },
                            label = { Text("Start Age", color = Color(0xFFFF4500)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFFF4500),
                                focusedBorderColor = Color(0xFFFF4500),
                                unfocusedBorderColor = Color(0xFFFFA500)
                            )
                        )

                        OutlinedTextField(
                            value = datingAgeEnd.toString(),
                            onValueChange = {
                                datingAgeEnd = it.toIntOrNull() ?: datingAgeEnd
                                registrationViewModel.datingAgeEnd = datingAgeEnd
                            },
                            label = { Text("End Age", color = Color(0xFFFF4500)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFFF4500),
                                focusedBorderColor = Color(0xFFFF4500),
                                unfocusedBorderColor = Color(0xFFFFA500)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dating Distance Preference
                    OutlinedTextField(
                        value = datingDistancePreference.toString(),
                        onValueChange = {
                            datingDistancePreference = it.toIntOrNull() ?: datingDistancePreference
                            registrationViewModel.datingDistancePreference = datingDistancePreference
                        },
                        label = { Text("Distance Preference (km)", color = Color(0xFFFF4500)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF4500),
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFFFFA500)
                        )
                    )

                    // Finish Button
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
                                                // Save the username and preferences to Firebase
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
            profilepicUrl = registrationViewModel.profilePicUrl,  // Use the uploaded profile picture URL
            optionalPhotoUrls = registrationViewModel.optionalPhotoUrls.toList(),
            religion = registrationViewModel.religion,
            community = registrationViewModel.community,
            city = registrationViewModel.city.ifEmpty { registrationViewModel.customCity },
            educationLevel = registrationViewModel.educationLevel,
            lifestyle = registrationViewModel.lifestyle,
            lookingFor = registrationViewModel.lookingFor,
            politics = registrationViewModel.politics,
            socialCauses = registrationViewModel.socialCauses.toList(),
            height = registrationViewModel.height,
            height2 = registrationViewModel.height2,
            caste = registrationViewModel.caste,
            voiceNoteUrl = registrationViewModel.voiceNoteUrl,  // Use the uploaded voice note URL
            datingAgeStart = registrationViewModel.datingAgeStart,
            datingAgeEnd = registrationViewModel.datingAgeEnd,
            datingDistancePreference = registrationViewModel.datingDistancePreference
        )

        database.child("users").child(userId).setValue(profile).await()

        withContext(Dispatchers.Main) {
            onRegistrationComplete()
        }
    } catch (e: Exception) {
        Log.e("Registration", "Failed to save profile: ${e.message}")
    }
}

fun uploadProfilePicToFirebase(
    storageRef: StorageReference,
    uri: Uri,
    registrationViewModel: RegistrationViewModel
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val profilePicRef = storageRef.child("users/$userId/profile_pic.jpg")
    profilePicRef.putFile(uri)
        .addOnSuccessListener {
            profilePicRef.downloadUrl.addOnSuccessListener { downloadUri ->
                registrationViewModel.profilePicUrl = downloadUri.toString()
                Log.d("UploadMedia", "Profile picture uploaded successfully: $downloadUri")
            }
        }
        .addOnFailureListener { exception ->
            Log.e("UploadMedia", "Failed to upload profile picture: ${exception.message}")
        }
}

fun uploadVoiceToFirebase(
    storageRef: StorageReference,
    uri: Uri,
    registrationViewModel: RegistrationViewModel
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val voiceNoteRef = storageRef.child("users/$userId/voice_note.3gp") // Adjust file extension if necessary
    voiceNoteRef.putFile(uri)
        .addOnSuccessListener {
            voiceNoteRef.downloadUrl.addOnSuccessListener { downloadUri ->
                registrationViewModel.voiceNoteUrl = downloadUri.toString()
                Log.d("UploadMedia", "Voice note uploaded successfully: $downloadUri")
            }
        }
        .addOnFailureListener { exception ->
            Log.e("UploadMedia", "Failed to upload voice note: ${exception.message}")
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterNameScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val interestedOptions = listOf("Male", "Female", "Beyond Binary")

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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        // Name Input
                        TextFieldWithLabel(
                            label = "Full Name",
                            value = registrationViewModel.name,
                            onValueChange = { registrationViewModel.name = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Height Section
                        Text("Height", color = Color.White, fontSize = 18.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text("Units: ", color = Color.White)
                            Switch(
                                checked = registrationViewModel.isHeightInFeet,
                                onCheckedChange = { registrationViewModel.isHeightInFeet = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFFA500),
                                    uncheckedThumbColor = Color.Gray
                                )
                            )
                            Text(
                                text = if (registrationViewModel.isHeightInFeet) "Feet + Inches" else "Centimeters",
                                color = Color.White
                            )
                        }

                        if (registrationViewModel.isHeightInFeet) {
                            // Height in Feet + Inches
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Feet Input
                                Box(modifier = Modifier.weight(1f)) {
                                    TextFieldWithLabel(
                                        label = "Feet",
                                        value = registrationViewModel.height2.getOrNull(0)?.toString() ?: "",
                                        onValueChange = { newFeet ->
                                            registrationViewModel.height2 = listOf(
                                                newFeet.toIntOrNull() ?: 0,
                                                registrationViewModel.height2.getOrNull(1) ?: 0
                                            )
                                        }
                                    )
                                }

                                // Inches Input
                                Box(modifier = Modifier.weight(1f)) {
                                    TextFieldWithLabel(
                                        label = "Inches",
                                        value = registrationViewModel.height2.getOrNull(1)?.toString() ?: "",
                                        onValueChange = { newInches ->
                                            registrationViewModel.height2 = listOf(
                                                registrationViewModel.height2.getOrNull(0) ?: 0,
                                                newInches.toIntOrNull() ?: 0
                                            )
                                        }
                                    )
                                }
                            }
                        } else {
                            // Height in Centimeters
                            TextFieldWithLabel(
                                label = "Height (cm)",
                                value = registrationViewModel.height.toString(),
                                onValueChange = { newHeight ->
                                    registrationViewModel.height = newHeight.toIntOrNull() ?: 0
                                }
                            )
                        }





                        Spacer(modifier = Modifier.height(16.dp))

                        // Caste Input
                        TextFieldWithLabel(
                            label = "Caste",
                            value = registrationViewModel.caste,
                            onValueChange = { registrationViewModel.caste = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Interested In Options
                        Text("Interested In", color = Color.White, fontSize = 18.sp)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            interestedOptions.forEach { option ->
                                val isSelected = registrationViewModel.interestedIn.contains(option)
                                Button(
                                    onClick = {
                                        if (isSelected) {
                                            registrationViewModel.interestedIn.remove(option)
                                        } else {
                                            registrationViewModel.interestedIn.add(option)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFFFFA500) else Color.Gray
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(text = option, color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onNext,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500))
                        ) {
                            Text("Next", color = Color.White)
                        }
                    }
                }
            }
        }
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

@Composable
fun UploadMediaComposable(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storageRef = FirebaseStorage.getInstance().reference

    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isVoiceBioValid by remember { mutableStateOf(true) } // Validates if the voice bio is under 60 seconds
    var voiceFilePath by remember { mutableStateOf(File(context.filesDir, "voice_note.mp3").absolutePath) }
    var voiceProgress by remember { mutableStateOf(0f) }
    var voiceDuration by remember { mutableStateOf(0L) } // Length of the voice recording in milliseconds

    val mediaPlayer = remember { MediaPlayer() }

    // Profile Picture Picker
    val profilePicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                registrationViewModel.profilePictureUri = it
                uploadProfilePicToFirebase(storageRef, it, registrationViewModel)
            }
        }
    )

    // Optional Photos Picker
    val optionalPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                registrationViewModel.optionalPhotoUris.add(it)
                uploadOptionalPhoto(storageRef, it, registrationViewModel)
            }
        }
    )

    // Request Permissions
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
        }
    }

    // Function to validate voice bio duration
    fun validateVoiceBio() {
        try {
            val tempPlayer = MediaPlayer()
            tempPlayer.setDataSource(voiceFilePath)
            tempPlayer.prepare()
            voiceDuration = tempPlayer.duration.toLong() // Duration in milliseconds
            tempPlayer.release()
            isVoiceBioValid = voiceDuration <= 60000 // Check if under 60 seconds
        } catch (e: Exception) {
            Log.e("VoiceValidation", "Error validating voice duration: ${e.message}")
            isVoiceBioValid = false
        }
    }

    // Toggle recording state
    val toggleRecording: () -> Unit = {
        if (isRecording) {
            // Stop recording
            try {
                isRecording = false
                registrationViewModel.stopVoiceRecording()
                registrationViewModel.voiceNoteUri = Uri.fromFile(File(voiceFilePath))
                validateVoiceBio() // Validate the length of the recording
                if (isVoiceBioValid) {
                    uploadVoiceToFirebase(storageRef, registrationViewModel.voiceNoteUri!!, registrationViewModel)
                }
            } catch (e: Exception) {
                Log.e("Recording", "Failed to stop recording: ${e.message}")
            }
        } else {
            // Start recording
            try {
                isRecording = true
                registrationViewModel.startVoiceRecording(context, voiceFilePath)
            } catch (e: Exception) {
                Log.e("Recording", "Failed to start recording: ${e.message}")
            }
        }
    }

    // Toggle playback state
    val togglePlayback: () -> Unit = {
        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        } else {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(voiceFilePath)
                mediaPlayer.prepare()
                mediaPlayer.start()
                isPlaying = true
                voiceDuration = mediaPlayer.duration.toLong().coerceAtLeast(1L) // Ensure duration is valid
            } catch (e: IOException) {
                Log.e("MediaPlayer", "Playback Error: ${e.message}")
            }
        }
    }

    // Slider progress update
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer.isPlaying) {
            voiceProgress = (mediaPlayer.currentPosition / voiceDuration.toFloat()).coerceIn(0f, 1f)
            delay(500)
        }
        if (!mediaPlayer.isPlaying) {
            isPlaying = false
            voiceProgress = 0f
        }
    }

    // Dispose media player resources
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Media", color = Color(0xFFFFBF00)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFFFBF00))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .background(Color.Black),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture Section
                Text("Profile Picture", color = Color(0xFFFFBF00), fontSize = 18.sp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .border(2.dp, Color(0xFFFFBF00), CircleShape)
                        .clickable {
                            profilePicPickerLauncher.launch("image/*")
                        }
                ) {
                    AsyncImage(
                        model = registrationViewModel.profilePictureUri,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.size(100.dp).clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Optional Photos Section
                Text("Optional Photos", color = Color(0xFFFFBF00), fontSize = 18.sp)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(registrationViewModel.optionalPhotoUris) { uri ->
                        Box(modifier = Modifier.size(100.dp)) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(CircleShape)
                            )
                            IconButton(
                                onClick = { registrationViewModel.optionalPhotoUris.remove(uri) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Voice Bio Section
                Text("Voice Bio [Tap icon to record/re-record]", color = Color(0xFFFFBF00), fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = toggleRecording) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isRecording) Color.Red else Color(0xFFFFBF00)
                    )
                }
                if (!isVoiceBioValid) {
                    Text(
                        text = "Voice Bio exceeds 60 seconds. Please record a shorter one.",
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                registrationViewModel.voiceNoteUri?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = togglePlayback) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFFFBF00)
                            )
                        }
                        Slider(
                            value = voiceProgress,
                            onValueChange = {},
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNext,
                    enabled = isVoiceBioValid,
                    colors = ButtonDefaults.buttonColors(Color(0xFFFFBF00))
                ) {
                    Text("Next", color = Color.Black)
                }
            }
        }
    )
}

fun uploadOptionalPhoto(storageRef: StorageReference, uri: Uri, registrationViewModel: RegistrationViewModel) {
    val userId = "testUser" // Replace with actual user ID
    val ref = storageRef.child("users/$userId/${uri.lastPathSegment}")
    ref.putFile(uri).addOnSuccessListener {
        ref.downloadUrl.addOnSuccessListener { downloadUri ->
            registrationViewModel.optionalPhotoUrls.add(downloadUri.toString())
        }
    }.addOnFailureListener {
        Log.e("UploadMedia", "Failed to upload: ${it.message}")
    }
}

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