// RegistrationActivity.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.am24.am24.ui.theme.AppTheme
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
import java.util.Calendar
class RegistrationActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN

        setContent {
            AppTheme {
                RegistrationScreen(onRegistrationComplete = {
                    // Navigate to LoginActivity after successful registration
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deleteIncompleteRegistration()
    }

    private fun deleteIncompleteRegistration() {
        val currentUser = auth.currentUser

        // Check if the user exists and has not completed the registration
        if (currentUser != null) {
            val userId = currentUser.uid
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(userId)
                .child("username")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        // If username does not exist, delete the unverified account
                        currentUser.delete()
                            .addOnSuccessListener {
                                Log.d("RegistrationActivity", "Unverified user account deleted successfully.")
                            }
                            .addOnFailureListener { exception ->
                                Log.e("RegistrationActivity", "Failed to delete unverified user: ${exception.message}")
                            }
                    }
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

    var height by mutableStateOf(169)            // Height in centimeters
    var height2 by mutableStateOf(listOf(5, 7))  // Height in feet + inches (default example: 5'7")
    var isHeightInFeet by mutableStateOf(false)  // Toggle for height unit preference (cm or feet+inches)
    var caste by mutableStateOf("")              // User's caste

    // Hometown and Education
    var hometown by mutableStateOf("")     // Treated as Locality
    var bio by mutableStateOf("")
    var gender by mutableStateOf("")
    var customHometown by mutableStateOf("")
    var religion by mutableStateOf("")
    var community by mutableStateOf("")
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
    var lifestyle by mutableStateOf(Lifestyle())     // Lifestyle info (smoking, drinking, etc.)
    var lookingFor by mutableStateOf("")             // What the user is looking for (Friendship, Relationship, etc.)
    var politics by mutableStateOf("")               // User's political views
    var socialCauses = mutableStateListOf<String>()  // List of user's selected social causes

    // New fields for dating preferences
    var datingAgeStart by mutableStateOf(18)         // Starting age for preference
    var datingAgeEnd by mutableStateOf(30)           // Ending age for preference
    var datingDistancePreference by mutableStateOf(10)   // Distance preference in kilometers
    var interestedIn = mutableStateListOf<String>()      // List for "Men," "Women," "Other"

    // ---------------------------------------------------
    // Voice Recording Methods
    // ---------------------------------------------------
    fun startVoiceRecording(context: Context, filePath: String) {
        try {
            voiceRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000) // e.g., 128 kbps for better quality
                setAudioSamplingRate(44100) // e.g., 44.1 kHz standard sampling rate
                setOutputFile(filePath)
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
                reset()
                release()
            }
            voiceRecorder = null
        } catch (e: Exception) {
            Log.e("RegistrationViewModel", "Error stopping voice recording: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .padding(innerPadding)
            ) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Color(0xFFFF6000),
                    trackColor = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content based on the current step
                when (currentStep) {
                    1 -> EnterEmailAndPasswordScreen(registrationViewModel, onNext, onBack)
                    2 -> EnterNameScreen(registrationViewModel, onNext)
                    3 -> UploadMediaComposable(registrationViewModel, onNext, onBack)
                    4 -> EnterBirthDateAndInterestsScreen(registrationViewModel, onNext, onBack)
                    5 -> EnterLocationAndSchoolScreen(registrationViewModel, onNext, onBack)
                    6 -> EnterGenderCommunityReligionScreen(registrationViewModel, onNext)
                    7 -> EnterLifestyleScreen(registrationViewModel, onNext)  // New step
                    8 -> EnterProfileHeadlineScreen(registrationViewModel, onNext)
                    9 -> EnterUsernameScreen(registrationViewModel, onRegistrationComplete, onBack)
                }
            }
        }
    )
}

@Composable
fun EnterLifestyleScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit
) {
    Scaffold(
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))  // Overall background
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(smoking = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(drinking = it)
                        },
                        nouns = listOf("Non-Drinker", "Rare Drinker", "Social Drinker", "Frequent Drinker", "Heavy Drinker")
                    )
                }

                // Cannabis Friendly Checkbox
                item {
                    CheckboxInput(
                        label = "Cannabis Friendly",
                        isChecked = registrationViewModel.lifestyle.cannabisFriendly,
                        onCheckedChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(cannabisFriendly = it)
                        }
                    )
                }

                // Indoorsy to Outdoorsy Slider
                item {
                    LifestyleSlider(
                        label = "Indoor<->Outdoor",
                        value = registrationViewModel.lifestyle.indoorsyToOutdoorsy,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(indoorsyToOutdoorsy = it)
                        },
                        nouns = listOf("Very Indoorsy", "Mostly Indoorsy", "Balanced", "Mostly Outdoorsy", "Very Outdoorsy")
                    )
                }

                // Social Media Slider
                item {
                    LifestyleSlider(
                        label = "Social Media",
                        value = registrationViewModel.lifestyle.socialMedia,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(socialMedia = it)
                        },
                        nouns = listOf("Invisible", "Watcher", "Casual Participant", "Engager", "Influencer")
                    )
                }

                // Diet Dropdown
                item {
                    DropdownWithStaticOptions(
                        label = "Diet",
                        options = listOf(
                            "Vegetarian", "Non-Veg", "Vegan", "Keto",
                            "Eggetarian", "Paleo", "Fruitarian", "Carnivore"
                        ),
                        selectedOption = registrationViewModel.lifestyle.diet,
                        onOptionSelected = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(diet = it)
                        }
                    )
                }

                // Sleep Cycle Slider
                item {
                    LifestyleSlider(
                        label = "Sleep Cycle",
                        value = registrationViewModel.lifestyle.sleepCycle,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sleepCycle = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(workLifeBalance = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(exerciseFrequency = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(familyOriented = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(adventurous = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(intellectual = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(creativeArtistic = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(fitnessLevel = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(spiritualMindful = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(humorousEasyGoing = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(professionalAmbitious = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(environmentallyConscious = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(foodieCulinaryEnthusiast = it)
                        },
                        nouns = listOf("Basic", "Occasional Foodie", "Balanced", "Food Enthusiast", "Culinary Expert")
                    )
                }

                // Sports Enthusiast Slider
                item {
                    LifestyleSlider(
                        label = "Sports Enthusiast",
                        value = registrationViewModel.lifestyle.sportsEnthusiast,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sportsEnthusiast = it)
                        },
                        nouns = listOf("Non-Sports", "Casual Viewer", "Occasional Player", "Sports Enthusiast", "Sports Fanatic")
                    )
                }

                // Sexual Activity Level Slider
                item {
                    LifestyleSlider(
                        label = "Sexual Activity Level",
                        value = registrationViewModel.lifestyle.sal,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sal = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(politicallyAware = it)
                        },
                        nouns = listOf("Unaware", "Occasionally Aware", "Balanced", "Aware", "Politically Engaged")
                    )
                }

                // Introvert to Extrovert Slider
                item {
                    LifestyleSlider(
                        label = "Introvert to Extrovert",
                        value = registrationViewModel.lifestyle.IE,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(IE = it)
                        },
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
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(communityOriented = it)
                        },
                        nouns = listOf("Individualistic", "Occasionally Involved", "Balanced", "Community-Oriented", "Community Leader")
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onNext() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
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
    val adjustedValue = if (value == -1) -1 else value.coerceIn(valueRangeStart, valueRangeEnd) // Adjust value to include "Not Selected"

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
            value = if (adjustedValue == -1) 0f else adjustedValue.toFloat(), // Default position for "Not Selected"
            onValueChange = { newValue ->
                onValueChange(newValue.toInt().coerceIn(valueRangeStart, valueRangeEnd))
            },
            valueRange = valueRangeStart.toFloat()..valueRangeEnd.toFloat(),
            steps = (valueRangeEnd - valueRangeStart - 1), // Correct number of steps for the slider
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6000),
                activeTrackColor = Color(0xFFFF6000)
            )
        )
        // Display the corresponding noun or "Not Selected"
        Text(
            text = if (adjustedValue == -1) "Not Selected" else nouns.getOrElse(adjustedValue) { "Unknown" },
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
            border = BorderStroke(1.dp, Color(0xFFFF6000)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6000))
        ) {
            Text(text = selectedOption.ifEmpty { "Select" }, color = Color(0xFFFF6000))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
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
                checkmarkColor = Color(0xFF1A1A1A),
                checkedColor = Color(0xFFFF6000),
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
    val highSchoolOptions = listOf("St. Xavier's", "La Martiniere", "Other")
    val collegeOptions = listOf("IIT Kharagpur", "Jadavpur University", "Other")
    val postGraduationOptions = listOf("IIM Calcutta", "ISB Hyderabad", "Other")

    val isNextEnabled = registrationViewModel.educationLevel.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Section Title
                Text(
                    text = "Enter Education and Location",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Education Level Dropdown
                Text(
                    text = "Education Level",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownWithSearch(
                    title = "Select Education Level",
                    options = educationLevels,
                    selectedOption = registrationViewModel.educationLevel,
                    onOptionSelected = { registrationViewModel.educationLevel = it }
                )

                // High School Section
                if (registrationViewModel.educationLevel in listOf("High School", "College", "Post-Graduation")) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "High School",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SearchableDropdownWithCustomOption(
                        title = "Select or type your high school",
                        options = highSchoolOptions,
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

                // College Section
                if (registrationViewModel.educationLevel in listOf("College", "Post-Graduation")) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "College",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SearchableDropdownWithCustomOption(
                        title = "Select or type your college",
                        options = collegeOptions,
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

                // Post-Graduation Section
                if (registrationViewModel.educationLevel == "Post-Graduation") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Post-Graduation",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SearchableDropdownWithCustomOption(
                        title = "Select or type your post-graduation institute",
                        options = postGraduationOptions,
                        selectedOption = registrationViewModel.postGraduation ?: "",
                        onOptionSelected = { registrationViewModel.postGraduation = it },
                        customInput = registrationViewModel.customPostGraduation ?: "",
                        onCustomInputChange = { registrationViewModel.customPostGraduation = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GraduationYearDropdown(
                        year = registrationViewModel.postGraduationYear,
                        onYearSelected = { registrationViewModel.postGraduationYear = it }
                    )
                }

                // Next Button
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { if (isNextEnabled) onNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = isNextEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNextEnabled) Color(0xFFFF6000) else Color.Gray
                    ),
                    shape = CircleShape
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
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6000)),
            border = BorderStroke(1.dp, Color(0xFFFF6000))
        ) {
            Text(
                text = year.ifEmpty { "Select Graduation Year" },
                color = Color(0xFFFF6000)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
        ) {
            // Search Box
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Search Year", color = Color(0xFFFF6000)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedLabelColor = Color(0xFFFF6000),
                    cursorColor = Color(0xFFFF6000),
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color(0xFFFF6000)
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextFieldWithLabel(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = Color.White) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color(0xFFFF6000),
                unfocusedBorderColor = Color.White,
                focusedLabelColor = Color(0xFFFF6000),
                unfocusedLabelColor = Color.White
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
            border = BorderStroke(1.dp, Color(0xFFFF6000)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6000))
        ) {
            Text(
                text = if (showCustomInput) customInput else selectedOption.ifEmpty { "Select or type" },
                color = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
        ) {
            TextField(
                value = searchText,
                onValueChange = { input ->
                    searchText = input
                    showCustomInput = false
                },
                label = { Text("Search", color = Color.White) },
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
                label = { Text("Enter custom value", color = Color.White) },
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var email by remember { mutableStateOf(TextFieldValue(registrationViewModel.email)) }
    var password by remember { mutableStateOf(TextFieldValue(registrationViewModel.password)) }
    var confirmPassword by remember { mutableStateOf(TextFieldValue("")) }

    var isCreatingAccount by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    /**
     * Suspend function to check for incomplete accounts and delete them if necessary.
     */
    suspend fun checkAndDeleteIncompleteIfNeeded(email: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        try {
            val snapshot = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userId)
                .child("username")
                .get()
                .await()

            if (!snapshot.exists()) {
                // Incomplete -> delete user, sign out
                currentUser.delete().await()
                FirebaseAuth.getInstance().signOut()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Removed incomplete account for this email",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Create a new user account with typedEmail & typedPassword, and send verification email.
     */
    fun createUserAccount(typedEmail: String, typedPassword: String) {
        (context as? ComponentActivity)?.lifecycleScope?.launch {
            try {
                // Call the suspend function before creating the account
                checkAndDeleteIncompleteIfNeeded(typedEmail)

                // Proceed to create the user account
                val authResult = FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(typedEmail, typedPassword)
                    .await()

                val user = authResult.user
                user?.sendEmailVerification()?.await()

                withContext(Dispatchers.Main) {
                    isCreatingAccount = false
                    onNext()
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${ex.message}", Toast.LENGTH_LONG).show()
                    isCreatingAccount = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
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
                        CircularProgressIndicator(color = Color(0xFFFF6000))
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
                        label = { Text("Email", color = Color.White) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFFFF6000),
                            unfocusedBorderColor = Color.White,
                            focusedLabelColor = Color(0xFFFF6000),
                            unfocusedLabelColor = Color.White
                        )
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            registrationViewModel.password = it.text
                        },
                        label = { Text("Password", color = Color.White) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFFFF6000),
                            unfocusedBorderColor = Color.White,
                            focusedLabelColor = Color(0xFFFF6000),
                            unfocusedLabelColor = Color.White
                        )
                    )

                    // Confirm Password Input
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password", color = Color.White) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFFFF6000),
                            unfocusedBorderColor = Color.White,
                            focusedLabelColor = Color(0xFFFF6000),
                            unfocusedLabelColor = Color.White
                        )
                    )

                    if (passwordError) {
                        Text("Passwords do not match", color = Color.Red)
                    }

                    Button(
                        onClick = {
                            val typedEmail = email.text.trim()
                            val typedPassword = password.text.trim()
                            val typedConfirm = confirmPassword.text.trim()

                            if (typedEmail.isNotEmpty() && typedPassword.isNotEmpty()) {
                                if (typedPassword == typedConfirm) {
                                    isCreatingAccount = true
                                    passwordError = false

                                    // Simply create the user account in Firebase
                                    createUserAccount(typedEmail, typedPassword)
                                } else {
                                    passwordError = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isCreatingAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000)),
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
    onNext: () -> Unit
) {
    // Predefined lists for dropdown options
    val genderOptions = listOf("Male", "Female", "Non-Binary", "Other")
    val communityOptions = listOf("Marwari", "Bengali", "Punjabi", "Tamil", "Other")
    val religionOptions = listOf("Hindu", "Muslim", "Christian", "Sikh", "Buddhist", "Jain", "Other")

    // Validation for enabling the "Next" button
    val isNextEnabled = registrationViewModel.gender.isNotEmpty() &&
            registrationViewModel.community.isNotEmpty() &&
            registrationViewModel.religion.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Enter Your Gender, Community, and Religion",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Gender Dropdown
                Text(
                    text = "Gender",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownWithSearch(
                    title = "Select Gender",
                    options = genderOptions,
                    selectedOption = registrationViewModel.gender,
                    onOptionSelected = { registrationViewModel.gender = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Community Dropdown
                Text(
                    text = "Community",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownWithSearch(
                    title = "Select Community",
                    options = communityOptions,
                    selectedOption = registrationViewModel.community,
                    onOptionSelected = { registrationViewModel.community = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Religion Dropdown
                Text(
                    text = "Religion",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownWithSearch(
                    title = "Select Religion",
                    options = religionOptions,
                    selectedOption = registrationViewModel.religion,
                    onOptionSelected = { registrationViewModel.religion = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Next Button
                Button(
                    onClick = { if (isNextEnabled) onNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = isNextEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNextEnabled) Color(0xFFFF6000) else Color.Gray
                    ),
                    shape = CircleShape
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
            Text(text = selectedOption.ifEmpty { "Select" }, color = Color.White)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
        ) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Search", color = Color.White) },
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
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference

    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(TextFieldValue(registrationViewModel.username)) }
    var isUsernameValid by remember { mutableStateOf(true) }
    var usernameErrorMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
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
                    // Username TextField
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            isUsernameValid = true
                            usernameErrorMessage = ""
                        },
                        label = { Text("Username", color = Color.White) },
                        singleLine = true,
                        isError = !isUsernameValid,
                        supportingText = {
                            if (!isUsernameValid) {
                                Text(
                                    text = usernameErrorMessage,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF6000),
                            focusedBorderColor = Color(0xFFFF6000),
                            unfocusedBorderColor = Color(0xFFFFDB00)
                        )
                    )

                    // Finish Button
                    Button(
                        onClick = {
                            // Inside the Finish Button onClick in EnterUsernameScreen:
                            val trimmedUsername = username.text.trim()
                            if (trimmedUsername.isEmpty()) {
                                isUsernameValid = false
                                usernameErrorMessage = "Username cannot be empty"
                                return@Button
                            }
                            val currentUser = auth.currentUser
                            if (currentUser != null) {
                                val userId = currentUser.uid
                                // 1) Store username in both the user's node and in the "usernames" node.
                                checkAndStoreUsernameForRegistration(trimmedUsername, userId, onSuccess = {
                                    // If successful, update the user's profile node:
                                    database.child("users").child(userId).child("username")
                                        .setValue(trimmedUsername)
                                        .addOnSuccessListener {
                                            registrationViewModel.username = trimmedUsername
                                            // 2) Save the full profile in background.
                                            scope.launch {
                                                try {
                                                    saveProfileToFirebase(registrationViewModel) {
                                                        onRegistrationComplete()
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("EnterUsernameScreen", "Error saving profile: ${e.message}")
                                                }
                                            }
                                        }
                                        .addOnFailureListener { exception ->
                                            Log.e("EnterUsernameScreen", "Error saving username in profile: ${exception.message}")
                                        }
                                }, onFailure = { errorMsg ->
                                    Log.e("EnterUsernameScreen", errorMsg)
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
            email = registrationViewModel.email,
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
    val voiceNoteRef = storageRef.child("users/$userId/voice_note.mp3") // Adjust file extension if necessary
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
    onNext: () -> Unit
) {
    val interestedOptions = listOf("Male", "Female")

    // State to determine if the "Next" button can be enabled
    val canProceed = registrationViewModel.name.isNotEmpty() &&
            registrationViewModel.height > 0 &&
            registrationViewModel.caste.isNotEmpty() &&
            registrationViewModel.interestedIn.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
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
                            Switch(
                                checked = registrationViewModel.isHeightInFeet,
                                onCheckedChange = {
                                    registrationViewModel.isHeightInFeet = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF6000),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedBorderColor = Color.White,
                                    checkedBorderColor = Color(0xFFFF6000)
                                )
                            )
                            Text(
                                text = if (registrationViewModel.isHeightInFeet)
                                    " Feet + Inches"
                                else
                                    " Centimeters",
                                color = Color.White
                            )
                        }

                        if (registrationViewModel.isHeightInFeet) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Feet Input
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                ) {
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
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                ) {
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
                        }
                         else {
                            TextFieldWithLabel(
                                label = "Height (cm)",
                                value = registrationViewModel.height.toString(),
                                onValueChange = { newHeight ->
                                    registrationViewModel.height =
                                        newHeight.toIntOrNull() ?: 0
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Caste Input using SearchableDropdownWithCustomOption
                        SearchableDropdownWithCustomOption(
                            title = "Caste",
                            options = listOf("Brahmin", "Kshatriya", "Vaishya", "Shudra"),
                            selectedOption = registrationViewModel.caste,
                            onOptionSelected = { selectedOption ->
                                if (selectedOption != "Other") {
                                    registrationViewModel.caste = selectedOption
                                }
                            },
                            customInput = registrationViewModel.caste,
                            onCustomInputChange = { customInput ->
                                registrationViewModel.caste = customInput
                            }
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
                                val isSelected = option in registrationViewModel.interestedIn

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected)
                                                Color(0xFFFF6000)
                                            else
                                                Color(0xFF1A1A1A)
                                        )
                                        .border(1.dp, Color(0xFFFF6000), CircleShape)
                                ) {
                                    Button(
                                        onClick = {
                                            if (isSelected) {
                                                registrationViewModel
                                                    .interestedIn.remove(option)
                                            } else {
                                                registrationViewModel
                                                    .interestedIn.add(option)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = CircleShape,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = option,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Next Button
                        Button(
                            onClick = { if (canProceed) onNext() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canProceed)
                                    Color(0xFFFF6000)
                                else
                                    Color.DarkGray
                            ),
                            enabled = canProceed
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
    // 1) Birth Date Setup
    val dayRange = (1..31).toList()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val yearRange = (1950..currentYear).toList().reversed()
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    var selectedDay by remember { mutableStateOf(dayRange[0]) }
    var selectedMonthIndex by remember { mutableStateOf(0) }
    var selectedYear by remember { mutableStateOf(yearRange[0]) }

    fun updateDobInViewModel() {
        val monthNumber = selectedMonthIndex + 1
        registrationViewModel.dob = "$selectedDay/$monthNumber/$selectedYear"
    }

    // Restore existing DOB if previously set
    LaunchedEffect(Unit) {
        val storedDob = registrationViewModel.dob
        if (storedDob.isNotBlank()) {
            val parts = storedDob.split("/")
            if (parts.size == 3) {
                val parsedDay = parts[0].toIntOrNull()
                val parsedMonth = parts[1].toIntOrNull()
                val parsedYear = parts[2].toIntOrNull()

                if (parsedDay != null && parsedDay in dayRange) selectedDay = parsedDay
                if (parsedMonth != null && parsedMonth in 1..12) selectedMonthIndex = parsedMonth - 1
                if (parsedYear != null && parsedYear in yearRange) selectedYear = parsedYear
            }
        }
        updateDobInViewModel()
    }

    // Expand states
    var expandedDay by remember { mutableStateOf(false) }
    var expandedMonth by remember { mutableStateOf(false) }
    var expandedYear by remember { mutableStateOf(false) }
    val isDateSelected = registrationViewModel.dob.isNotBlank()

    // 2) Kolkata Localities Only
    val kolkataLocalities = listOf("Salt Lake", "New Town", "Dum Dum", "Behala", "Park Street")

    // We store the user’s selected locality in "hometown."
    var selectedLocality by rememberSaveable { mutableStateOf(registrationViewModel.hometown) }

    // Update registrationViewModel.hometown when locality changes
    LaunchedEffect(selectedLocality) {
        registrationViewModel.hometown = selectedLocality
    }

    // For the dropdown
    var localityDropdownExpanded by remember { mutableStateOf(false) }

    // 3) Interests
    val globalInterests = listOf(
        Interest("Music", "🎵"),
        Interest("Movies", "🎥"),
        Interest("Sports", "⚽"),
        Interest("Books", "📚"),
        Interest("Travel", "✈️"),
        Interest("Fitness", "💪"),
        Interest("Art", "🎨"),
        Interest("Gaming", "🎮")
    )

    // Locality-Specific Interests (Only for Kolkata)
    val kolkataLocalitiesInterests = mapOf(
        "Salt Lake" to listOf(
            Interest("CC Block Market", "🛒"),
            Interest("Sector V IT Hub", "💻")
        ),
        "New Town" to listOf(
            Interest("Eco Park", "🌳"),
            Interest("City Centre 2", "🛍️")
        ),
        "Dum Dum" to listOf(
            Interest("Airport Area", "✈️"),
            Interest("Local Market", "🛍️")
        ),
        "Behala" to listOf(
            Interest("Old Market", "🏪"),
            Interest("Local Eateries", "🍴")
        ),
        "Park Street" to listOf(
            Interest("Nightlife", "🌃"),
            Interest("Park Street Cafes", "☕")
        )
    )

    // Additional Interests for entire Kolkata
    val kolkataCityInterests = listOf(
        Interest("Durga Puja", "🙏"),
        Interest("Roshogolla", "⚪"),
        Interest("Jhalmuri", "🍿"),
        Interest("Victoria Memorial", "🏛️")
    )

    // 4) "Next" Enabled only if DOB + Locality chosen
    val isNextEnabled = isDateSelected && selectedLocality.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Enter Birth Date and Interests",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Birth Date Section
                Text("Select Birth Date", color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Day
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { expandedDay = true },
                            border = BorderStroke(1.dp, Color(0xFFFF6000)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$selectedDay", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = expandedDay,
                            onDismissRequest = { expandedDay = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            dayRange.forEachIndexed { index, day ->
                                DropdownMenuItem(
                                    text = { Text("$day", color = Color.White) },
                                    onClick = {
                                        selectedDay = day
                                        expandedDay = false
                                        updateDobInViewModel()
                                    }
                                )
                                if (index != dayRange.lastIndex) {
                                    Divider(color = Color(0xFFFF6000), thickness = 1.dp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Month
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { expandedMonth = true },
                            border = BorderStroke(1.dp, Color(0xFFFF6000)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(monthNames[selectedMonthIndex], color = Color.White)
                        }
                        DropdownMenu(
                            expanded = expandedMonth,
                            onDismissRequest = { expandedMonth = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            monthNames.forEachIndexed { index, monthName ->
                                DropdownMenuItem(
                                    text = { Text(monthName, color = Color.White) },
                                    onClick = {
                                        selectedMonthIndex = index
                                        expandedMonth = false
                                        updateDobInViewModel()
                                    }
                                )
                                if (index != monthNames.lastIndex) {
                                    Divider(color = Color(0xFFFF6000), thickness = 1.dp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Year
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { expandedYear = true },
                            border = BorderStroke(1.dp, Color(0xFFFF6000)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1A1A1A),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$selectedYear", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = expandedYear,
                            onDismissRequest = { expandedYear = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            yearRange.forEachIndexed { index, yr ->
                                DropdownMenuItem(
                                    text = { Text("$yr", color = Color.White) },
                                    onClick = {
                                        selectedYear = yr
                                        expandedYear = false
                                        updateDobInViewModel()
                                    }
                                )
                                if (index != yearRange.lastIndex) {
                                    Divider(color = Color(0xFFFF6000), thickness = 1.dp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Kolkata Locality
                Text("Select Your Locality in Kolkata", color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Box {
                    OutlinedButton(
                        onClick = { localityDropdownExpanded = true },
                        border = BorderStroke(1.dp, Color(0xFFFF6000)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1A1A1A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedLocality.isNotEmpty()) selectedLocality else "Select Locality",
                            color = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = localityDropdownExpanded,
                        onDismissRequest = { localityDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF1A1A1A))
                    ) {
                        kolkataLocalities.forEachIndexed { index, locality ->
                            DropdownMenuItem(
                                text = { Text(locality, color = Color.White) },
                                onClick = {
                                    selectedLocality = locality
                                    localityDropdownExpanded = false
                                }
                            )
                            if (index != kolkataLocalities.lastIndex) {
                                Divider(color = Color(0xFFFF6000), thickness = 1.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Global Interests
                Text("Global Interests", color = Color.White, fontSize = 18.sp)
                Column(modifier = Modifier.fillMaxWidth()) {
                    globalInterests.forEach { interest ->
                        val isSelected = registrationViewModel.interests.contains(interest)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (isSelected) {
                                        registrationViewModel.interests.remove(interest)
                                    } else {
                                        registrationViewModel.interests.add(interest)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFFF6000),
                                    uncheckedColor = Color.White
                                )
                            )
                            Text(
                                text = "${interest.emoji} ${interest.name}",
                                color = if (isSelected) Color(0xFFFF6000) else Color.White
                            )
                        }
                    }
                }

                // Locality-Specific Interests (Within Kolkata)
                Spacer(modifier = Modifier.height(16.dp))
                if (selectedLocality.isNotBlank()) {
                    Text("Interests in $selectedLocality", color = Color.White, fontSize = 18.sp)
                    // Hardcode a map of localities to interests if you want more detail:
                    val kolkataLocalitiesInterestsMap = mapOf(
                        "Salt Lake" to listOf(
                            Interest("CC Block Market", "🛒"),
                            Interest("Sector V IT Hub", "💻")
                        ),
                        "New Town" to listOf(
                            Interest("Eco Park", "🌳"),
                            Interest("City Centre 2", "🛍️")
                        ),
                        "Dum Dum" to listOf(
                            Interest("Airport Area", "✈️"),
                            Interest("Local Market", "🛍️")
                        ),
                        "Behala" to listOf(
                            Interest("Old Market", "🏪"),
                            Interest("Local Eateries", "🍴")
                        ),
                        "Park Street" to listOf(
                            Interest("Nightlife", "🌃"),
                            Interest("Park Street Cafes", "☕")
                        )
                    )

                    val localInterests = kolkataLocalitiesInterestsMap[selectedLocality] ?: emptyList()

                    Column(modifier = Modifier.fillMaxWidth()) {
                        localInterests.forEach { interest ->
                            val isSelected = registrationViewModel.interests.contains(interest)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        if (isSelected) {
                                            registrationViewModel.interests.remove(interest)
                                        } else {
                                            registrationViewModel.interests.add(interest)
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFFF6000),
                                        uncheckedColor = Color.White
                                    )
                                )
                                Text(
                                    text = "${interest.emoji} ${interest.name}",
                                    color = if (isSelected) Color(0xFFFF6000) else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Next Button
                Button(
                    onClick = { if (isNextEnabled) onNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = isNextEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNextEnabled) Color(0xFFFF6000) else Color.DarkGray
                    ),
                    shape = CircleShape
                ) {
                    Text("Next", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    var isVoiceBioValid by remember { mutableStateOf(true) }
    var voiceFilePath by remember { mutableStateOf(File(context.filesDir, "voice_note.mp3").absolutePath) }
    var voiceProgress by remember { mutableStateOf(0f) }
    var voiceDuration by remember { mutableStateOf(0L) }

    val mediaPlayer = remember { MediaPlayer() }

    val canProceed = registrationViewModel.profilePictureUri != null &&
            isVoiceBioValid &&
            registrationViewModel.voiceNoteUri != null

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

    fun validateVoiceBio() {
        try {
            val tempPlayer = MediaPlayer()
            tempPlayer.setDataSource(voiceFilePath)
            tempPlayer.prepare()
            voiceDuration = tempPlayer.duration.toLong()
            tempPlayer.release()
            isVoiceBioValid = voiceDuration <= 60000
        } catch (e: Exception) {
            Log.e("VoiceValidation", "Error validating voice duration: ${e.message}")
            isVoiceBioValid = false
        }
    }

    val permissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )
    // 1) Declare a lateinit variable (no initializer yet)
    lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

// 2) Assign it with rememberLauncherForActivityResult
    permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val allGranted = permissionsResult.values.all { it }

        if (!allGranted) {
            // Re‐request
            permissionLauncher.launch(permissions)
        } else {
            isRecording = true
            registrationViewModel.startVoiceRecording(context, voiceFilePath)
        }
    }

    val toggleRecording: () -> Unit = {
        if (isRecording) {
            isRecording = false
            registrationViewModel.stopVoiceRecording()
            registrationViewModel.voiceNoteUri = Uri.fromFile(File(voiceFilePath))
            validateVoiceBio()
            if (isVoiceBioValid) {
                uploadVoiceToFirebase(storageRef, registrationViewModel.voiceNoteUri!!, registrationViewModel)
            }
        } else {
            permissionLauncher.launch(permissions) // Request permissions before recording
        }
    }


    val togglePlayback: () -> Unit = {
        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        } else {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(
                    registrationViewModel.voiceNoteUri?.path ?: voiceFilePath
                )
                mediaPlayer.prepare()
                mediaPlayer.start()
                isPlaying = true
                voiceDuration = mediaPlayer.duration.toLong().coerceAtLeast(1L)
            } catch (e: IOException) {
                Log.e("MediaPlayer", "Playback Error: ${e.message}")
            }
        }
    }

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

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Media", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
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
                        // Profile Picture Section
                        Text("Profile Picture", color = Color.White, fontSize = 18.sp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(110.dp)
                                .border(2.dp, Color(0xFFFF6000), CircleShape)
                                .clickable { profilePicPickerLauncher.launch("image/*") }
                        ) {
                            if (registrationViewModel.profilePictureUri != null) {
                                AsyncImage(
                                    model = registrationViewModel.profilePictureUri,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Text("Tap", color = Color.White, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Optional Photos Section
                        Text("Optional Photos", color = Color.White, fontSize = 18.sp)
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
                        Button(
                            onClick = { optionalPhotoPickerLauncher.launch("image/*") },
                            modifier = Modifier.padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
                        ) {
                            Text("Add Photos", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Voice Bio Section
                        Text("Voice Bio [Tap icon to record/re-record]", color = Color.White, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = toggleRecording) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isRecording) Color.Red else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isVoiceBioValid) {
                            Text(
                                text = "Voice Bio exceeds 60 seconds. Please record a shorter one.",
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                        }

                        registrationViewModel.voiceNoteUri?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = togglePlayback) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White
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

                        // Next Button
                        Button(
                            onClick = { if (canProceed) onNext() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canProceed) Color(0xFFFF6000) else Color.DarkGray
                            ),
                            enabled = canProceed
                        ) {
                            Text("Next", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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

fun checkAndStoreUsernameForRegistration(
    newUsername: String,
    userId: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    if(newUsername.isBlank()){
        onFailure("Username cannot be empty.")
        return
    }
    val db = FirebaseDatabase.getInstance().reference
    val usernamesRef = db.child("usernames")
    // Check if the username already exists.
    usernamesRef.child(newUsername).get().addOnSuccessListener { snapshot ->
        if (snapshot.exists()) {
            onFailure("Username already taken. Please choose another.")
        } else {
            // Set the new mapping: username -> userId.
            usernamesRef.child(newUsername).setValue(userId)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e ->
                    onFailure("Failed to store username mapping: ${e.message}")
                }
        }
    }.addOnFailureListener { error ->
        onFailure("Error checking username uniqueness: ${error.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterProfileHeadlineScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit
) {
    var headline by remember { mutableStateOf(TextFieldValue(registrationViewModel.bio)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
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
                    // Headline/Bio TextField
                    OutlinedTextField(
                        value = headline,
                        onValueChange = {
                            headline = it
                            registrationViewModel.bio = it.text
                        },
                        label = { Text("Bio (optional)", color = Color(0xFFFF6000)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF4500),
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFFFF6000)
                        )
                    )

                    // Next Button
                    Button(
                        onClick = {
                            registrationViewModel.bio = headline.text
                            onNext()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000)),
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
