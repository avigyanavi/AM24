// RegistrationActivity.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.am24.am24.ui.theme.AppTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
import java.util.Locale

class RegistrationActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        setContent {
            AppTheme {
                RegistrationScreen(
                    onRegistrationComplete = {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    },
                    fusedLocationClient = fusedLocationClient
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deleteIncompleteRegistration()
    }

    override fun attachBaseContext(newBase: Context) {
        // Retrieve the language code from SharedPreferences (default "en")
        val prefs = newBase.getSharedPreferences("settings", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val updatedContext = updateLocale(newBase, languageCode)
        super.attachBaseContext(updatedContext)
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

    var selectedLanguage by mutableStateOf("en") // Options: "en", "bn", "hi"
    var city by mutableStateOf("")
    var customCity by mutableStateOf("")

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
fun RegistrationScreen(
    onRegistrationComplete: () -> Unit,
    fusedLocationClient: FusedLocationProviderClient
) {
    val registrationViewModel: RegistrationViewModel = viewModel()
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 11
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
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Color(0xFFFF6000),
                    trackColor = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                when (currentStep) {
                    1 -> ChooseLanguageScreen(registrationViewModel, onNext)
                    2 -> EnterEmailAndPasswordScreen(registrationViewModel, onNext, onBack)
                    3 -> EnterNameScreen(registrationViewModel, onNext)
                    4 -> UploadMediaComposable(registrationViewModel, onNext, onBack)
                    5 -> EnterBirthdateCityHometownScreen(registrationViewModel, onNext, fusedLocationClient)
                    6 -> EnterInterestsScreen(registrationViewModel, onNext)
                    7 -> EnterLocationAndSchoolScreen(registrationViewModel, onNext, onBack)
                    8 -> EnterGenderCommunityReligionScreen(registrationViewModel, onNext)
                    9 -> EnterLifestyleScreen(registrationViewModel, onNext)
                    10 -> EnterProfileHeadlineScreen(registrationViewModel, onNext)
                    11 -> EnterUsernameScreen(registrationViewModel, onRegistrationComplete, onBack)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseLanguageScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val currentLocaleLanguage = Locale.getDefault().language

    var shouldRestart by remember { mutableStateOf(false) }
    if (shouldRestart) {
        LanguageRestartScreen()
        return
    }

    val options = listOf(
        "English" to "en",
        "বাংলা (Bengali)" to "bn",
        "हिन्दी (Hindi)" to "hi"
    )

    // Optionally update the locale immediately for preview (won't fully reload the context)
    LaunchedEffect(registrationViewModel.selectedLanguage) {
        updateLocale(context, registrationViewModel.selectedLanguage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_language_title), color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp, vertical = 48.dp)
                    .background(Color(0xFF1A1A1A)),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.select_preferred_language),
                    color = Color.White,
                    fontSize = 20.sp
                )
                options.forEach { (label, langCode) ->
                    val isSelected = registrationViewModel.selectedLanguage == langCode
                    Button(
                        onClick = {
                            registrationViewModel.selectedLanguage = langCode
                            // Save selected language persistently
                            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                                .putString("language", langCode)
                                .apply()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFFFF6000) else Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (registrationViewModel.selectedLanguage != currentLocaleLanguage) {
                            shouldRestart = true
                        } else {
                            onNext()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = registrationViewModel.selectedLanguage.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
                ) {
                    Text(stringResource(R.string.next_button), color = Color.White)
                }
            }
        }
    )
}

// Add this new composable for restarting the activity
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageRestartScreen() {
    // Get the current context as an Activity.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(2000) // Wait 2 seconds before restarting.
        (context as? ComponentActivity)?.recreate()
    }
    // Display a simple pause UI.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFFF6000))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Restarting to apply language changes...", color = Color.White, fontSize = 18.sp)
        }
    }
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
                        value = registrationViewModel.lifestyle.smoking_habit,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(smoking_habit = it)
                        },
                        nouns = listOf("Non-Smoker", "Rare Smoker", "Social Smoker", "Frequent Smoker", "Heavy Smoker")
                    )
                }

                // Drinking Slider
                item {
                    LifestyleSlider(
                        label = "Drinking",
                        value = registrationViewModel.lifestyle.drinking_habit,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(drinking_habit = it)
                        },
                        nouns = listOf("Non-Drinker", "Rare Drinker", "Social Drinker", "Frequent Drinker", "Heavy Drinker")
                    )
                }

                // Cannabis Friendly Checkbox
                item {
                    CheckboxInput(
                        label = "Cannabis Friendly",
                        isChecked = registrationViewModel.lifestyle.cannabis_friendly,
                        onCheckedChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(cannabis_friendly = it)
                        }
                    )
                }

                // Indoorsy to Outdoorsy Slider
                item {
                    LifestyleSlider(
                        label = "Indoor<->Outdoor",
                        value = registrationViewModel.lifestyle.indoor_outdoor_orientation,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(indoor_outdoor_orientation = it)
                        },
                        nouns = listOf("Very Indoorsy", "Mostly Indoorsy", "Balanced", "Mostly Outdoorsy", "Very Outdoorsy")
                    )
                }

                // Social Media Slider
                item {
                    LifestyleSlider(
                        label = "Social Media",
                        value = registrationViewModel.lifestyle.social_media_engagement,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(social_media_engagement = it)
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
                        selectedOption = registrationViewModel.lifestyle.dietary_preferences,
                        onOptionSelected = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(dietary_preferences = it)
                        }
                    )
                }

                // Sleep Cycle Slider
                item {
                    LifestyleSlider(
                        label = "Sleep Cycle",
                        value = registrationViewModel.lifestyle.sleep_pattern,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sleep_pattern = it)
                        },
                        nouns = listOf("Early Riser", "Morning Person", "Balanced", "Night Owl", "Late Night Enthusiast")
                    )
                }

                // Work-Life Balance Slider
                item {
                    LifestyleSlider(
                        label = "Work-Life Balance",
                        value = registrationViewModel.lifestyle.work_life_balance,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(work_life_balance = it)
                        },
                        nouns = listOf("Workaholic", "More Work-Oriented", "Balanced", "More Life-Oriented", "Relaxed")
                    )
                }

                // Exercise Frequency Slider
                item {
                    LifestyleSlider(
                        label = "Exercise Frequency",
                        value = registrationViewModel.lifestyle.exercise_frequency,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(exercise_frequency = it)
                        },
                        nouns = listOf("Inactive", "Rarely Active", "Moderately Active", "Active", "Very Active")
                    )
                }

                // Family-Oriented Slider
                item {
                    LifestyleSlider(
                        label = "Family-Oriented",
                        value = registrationViewModel.lifestyle.family_orientated,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(family_orientated = it)
                        },
                        nouns = listOf("Independent", "Slightly Family-Oriented", "Balanced", "Family-Oriented", "Very Family-Oriented")
                    )
                }

                // Adventurous Slider
                item {
                    LifestyleSlider(
                        label = "Adventurous",
                        value = registrationViewModel.lifestyle.adventurousness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(adventurousness = it)
                        },
                        nouns = listOf("Cautious", "Slightly Adventurous", "Moderately Adventurous", "Adventurous", "Thrill Seeker")
                    )
                }

                // Intellectual Slider
                item {
                    LifestyleSlider(
                        label = "Intellectual",
                        value = registrationViewModel.lifestyle.intellectual_curiosity,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(intellectual_curiosity = it)
                        },
                        nouns = listOf("Casual Thinker", "Inquisitive", "Knowledge Seeker", "Intellectual", "Philosopher")
                    )
                }

                // Creative/Artistic Slider
                item {
                    LifestyleSlider(
                        label = "Creative/Artistic",
                        value = registrationViewModel.lifestyle.creative_expression,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(creative_expression = it)
                        },
                        nouns = listOf("Not Creative", "Somewhat Creative", "Creative", "Very Creative", "Artistic Genius")
                    )
                }

                // Fitness Level Slider
                item {
                    LifestyleSlider(
                        label = "Fitness Level",
                        value = registrationViewModel.lifestyle.physical_fitness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(physical_fitness = it)
                        },
                        nouns = listOf("Sedentary", "Somewhat Fit", "Fit", "Athletic", "Peak Fitness")
                    )
                }

                // Spiritual/Mindful Slider
                item {
                    LifestyleSlider(
                        label = "Spiritual/Mindful",
                        value = registrationViewModel.lifestyle.spirituality_mindfulness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(spirituality_mindfulness = it)
                        },
                        nouns = listOf("Not Spiritual", "Occasionally Mindful", "Balanced", "Spiritual", "Deeply Mindful")
                    )
                }

                // Humorous/Easygoing Slider
                item {
                    LifestyleSlider(
                        label = "Humorous/Easygoing",
                        value = registrationViewModel.lifestyle.easy_goingness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(easy_goingness = it)
                        },
                        nouns = listOf("Serious", "Somewhat Easygoing", "Balanced", "Humorous", "Life of the Party")
                    )
                }

                // Professional/Ambitious Slider
                item {
                    LifestyleSlider(
                        label = "Professional/Ambitious",
                        value = registrationViewModel.lifestyle.professional_ambition,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(professional_ambition = it)
                        },
                        nouns = listOf("Relaxed", "Occasionally Driven", "Balanced", "Ambitious", "Highly Ambitious")
                    )
                }

                // Environmentally Conscious Slider
                item {
                    LifestyleSlider(
                        label = "Environmentally Conscious",
                        value = registrationViewModel.lifestyle.environmental_awareness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(environmental_awareness = it)
                        },
                        nouns = listOf("Not Conscious", "Occasionally Conscious", "Balanced", "Eco-Friendly", "Eco-Champion")
                    )
                }

                // Foodie/Culinary Enthusiast Slider
                item {
                    LifestyleSlider(
                        label = "Foodie/Culinary Enthusiast",
                        value = registrationViewModel.lifestyle.culinary_enthusiasm,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(culinary_enthusiasm = it)
                        },
                        nouns = listOf("Non Foodie", "Occasional Foodie", "Balanced", "Food Enthusiast", "Culinary Expert")
                    )
                }

                // Sports Enthusiast Slider
                item {
                    LifestyleSlider(
                        label = "Sports Enthusiast",
                        value = registrationViewModel.lifestyle.sports_enthusiasm,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sports_enthusiasm = it)
                        },
                        nouns = listOf("Non-Sports", "Casual Viewer", "Occasional Player", "Sports Enthusiast", "Sports Fanatic")
                    )
                }

                // Sexual Activity Level Slider
                item {
                    LifestyleSlider(
                        label = "Sexual Activity Level",
                        value = registrationViewModel.lifestyle.sexual_activity_level,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sexual_activity_level = it)
                        },
                        nouns = listOf("Abstinent", "Rarely Active", "Moderately Active", "Active", "Highly Active")
                    )
                }

                // Politically Aware Slider
                item {
                    LifestyleSlider(
                        label = "Politically Aware",
                        value = registrationViewModel.lifestyle.political_awareness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(political_awareness = it)
                        },
                        nouns = listOf("Unaware", "Occasionally Aware", "Balanced", "Aware", "Politically Engaged")
                    )
                }

                // Introvert to Extrovert Slider
                item {
                    LifestyleSlider(
                        label = "Introvert to Extrovert",
                        value = registrationViewModel.lifestyle.sociability,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sociability = it)
                        },
                        nouns = listOf("Highly Introverted", "Somewhat Introverted", "Ambivert", "Somewhat Extroverted", "Highly Extroverted")
                    )
                }

                // Community-Oriented Slider
                item {
                    LifestyleSlider(
                        label = "Community-Oriented",
                        value = registrationViewModel.lifestyle.community_engagement,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(community_engagement = it)
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
                        Text(text = stringResource(R.string.next_button), color = Color.White)
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
            Text(text = selectedOption.ifEmpty { stringResource(R.string.select_default) }, color = Color(0xFFFF6000))
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
    val highSchoolOptions = listOf("St. Xavier's", "La Martinière", "Other")
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
                    text = stringResource(R.string.education_and_location_title),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Education Level Dropdown
                Text(
                    text = stringResource(R.string.education_level_label),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownWithSearch(
                    title = stringResource(R.string.select_education_level),
                    options = educationLevels,
                    selectedOption = registrationViewModel.educationLevel,
                    onOptionSelected = { registrationViewModel.educationLevel = it }
                )

                // High School Section
                if (registrationViewModel.educationLevel in listOf("High School", "College", "Post-Graduation")) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.high_school_label),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SearchableDropdownWithCustomOption(
                        title = stringResource(R.string.select_or_type_high_school),
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
                if (registrationViewModel.educationLevel in listOf(stringResource(R.string.college_label), stringResource(R.string.post_graduation_label))) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.college_label),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SearchableDropdownWithCustomOption(
                        title = stringResource(R.string.select_or_type_college),
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
                if (registrationViewModel.educationLevel == stringResource(R.string.post_graduation_label)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.post_graduation_label),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SearchableDropdownWithCustomOption(
                        title = stringResource(R.string.select_or_type_post_grad),
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
                        text = stringResource(R.string.next_button),
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
                text = year.ifEmpty { stringResource(R.string.select_graduation_year_label) },
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
                label = { Text(stringResource(R.string.search_year_label), color = Color(0xFFFF6000)) },
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
                text = if (showCustomInput) customInput else selectedOption.ifEmpty { stringResource(R.string.select_or_type) },
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
                label = { Text(stringResource(R.string.search_label), color = Color.White) },
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
                        text = stringResource(R.string.create_account_title),
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
                        label = { Text(stringResource(R.string.email_label), color = Color.White) },
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
                        label = { Text(stringResource(R.string.password_label), color = Color.White) },
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
                        label = { Text(stringResource(R.string.confirm_password_label), color = Color.White) },
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
                        Text(stringResource(R.string.password_mismatch_error), color = Color.Red)
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
                            text = stringResource(R.string.next_button),
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
    val genderOptions = listOf(stringResource(R.string.male_option), stringResource(R.string.female_option), "Other")
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
                    text = stringResource(R.string.enter_gender_community_religion_title),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Gender Dropdown
                Text(
                    text = stringResource(R.string.gender_label),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownWithSearch(
                    title = stringResource(R.string.select_gender),
                    options = genderOptions,
                    selectedOption = registrationViewModel.gender,
                    onOptionSelected = { registrationViewModel.gender = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Community Dropdown
                Text(
                    text = stringResource(R.string.community_label),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownWithSearch(
                    title = stringResource(R.string.select_community),
                    options = communityOptions,
                    selectedOption = registrationViewModel.community,
                    onOptionSelected = { registrationViewModel.community = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Religion Dropdown
                Text(
                    text = stringResource(R.string.religion_label),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                DropdownWithSearch(
                    title = stringResource(R.string.select_religion),
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
                        text = stringResource(R.string.next_button),
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
            Text(text = selectedOption.ifEmpty { stringResource(R.string.select_default) }, color = Color.White)
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
                label = { Text(stringResource(R.string.search_label), color = Color.White) },
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
                        label = { Text(stringResource(R.string.username_label), color = Color.White) },
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
                            text = stringResource(R.string.finish_button),
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
            // Save the city using customCity if "Other" is selected
            city = if (registrationViewModel.city == "Other") registrationViewModel.customCity else registrationViewModel.city,
            // Hometown represents locality; if empty, fallback to a custom value if provided
            hometown = registrationViewModel.hometown.ifEmpty { registrationViewModel.customHometown },
            highSchool = if (registrationViewModel.highSchool == "Other") registrationViewModel.customHighSchool else registrationViewModel.highSchool,
            college = if (registrationViewModel.college == "Other") registrationViewModel.customCollege else registrationViewModel.college,
            postGraduation = if (registrationViewModel.postGraduation == "Other") registrationViewModel.customPostGraduation else registrationViewModel.postGraduation,
            work = if (registrationViewModel.work == "Other") registrationViewModel.customWork else registrationViewModel.work,
            profilepicUrl = registrationViewModel.profilePicUrl,
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
            voiceNoteUrl = registrationViewModel.voiceNoteUrl,
            datingAgeStart = registrationViewModel.datingAgeStart,
            datingAgeEnd = registrationViewModel.datingAgeEnd,
            datingDistancePreference = registrationViewModel.datingDistancePreference,
            preferredLanguage = registrationViewModel.selectedLanguage // NEW: Save language choice
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
                            label = stringResource(R.string.full_name_label),
                            value = registrationViewModel.name,
                            onValueChange = { registrationViewModel.name = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Height Section
                        Text(stringResource(R.string.height_label), color = Color.White, fontSize = 18.sp)
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
                                    stringResource(R.string.feet_inches_label)
                                else
                                    stringResource(R.string.centimeters_label),
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
                                        label = stringResource(R.string.feet_label),
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
                                        label = stringResource(R.string.inches_label),
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
                                label = stringResource(R.string.height_cm_label),
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
                            title = stringResource(R.string.caste_title),
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
                        Text(stringResource(R.string.interested_in_title), color = Color.White, fontSize = 18.sp)
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
                            Text(stringResource(R.string.next_button), color = Color.White)
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterBirthdateCityHometownScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    fusedLocationClient: FusedLocationProviderClient
) {
    val context = LocalContext.current
    val resources = context.resources

    // Birthdate Setup
    val dayRange = (1..31).toList()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val yearRange = (1950..currentYear).map { it.toString() }.reversed()
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    var selectedDay by remember { mutableStateOf(dayRange.first()) }
    var selectedMonthIndex by remember { mutableStateOf(0) }
    var selectedYear by remember { mutableStateOf(yearRange.first().toInt()) }

    fun updateDob() {
        registrationViewModel.dob = "$selectedDay/${selectedMonthIndex + 1}/$selectedYear"
    }
    LaunchedEffect(Unit) {
        if (registrationViewModel.dob.isNotBlank()) {
            val parts = registrationViewModel.dob.split("/")
            if (parts.size == 3) {
                parts[0].toIntOrNull()?.let { selectedDay = it }
                parts[1].toIntOrNull()?.let { selectedMonthIndex = it - 1 }
                parts[2].toIntOrNull()?.let { selectedYear = it }
            }
        }
        updateDob()
    }
    var dayExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    // City Selection
    val cities = remember { resources.getStringArray(R.array.city_names).toList() }
    var selectedCity by remember { mutableStateOf(cities.firstOrNull() ?: "") }
    var cityExpanded by remember { mutableStateOf(false) }
    var customCity by remember { mutableStateOf(registrationViewModel.customCity) }
    var isLocating by remember { mutableStateOf(false) }

    // Locality Selection
    val localities = remember(selectedCity) {
        when (selectedCity) {
            "Kolkata" -> resources.getStringArray(R.array.localities_kolkata).toList()
            "Howrah" -> resources.getStringArray(R.array.localities_howrah).toList()
            "Durgapur" -> resources.getStringArray(R.array.localities_durgapur).toList()
            "Asansol" -> resources.getStringArray(R.array.localities_asansol).toList()
            "Siliguri" -> resources.getStringArray(R.array.localities_siliguri).toList()
            "Darjeeling" -> resources.getStringArray(R.array.localities_darjeeling).toList()
            "Malda" -> resources.getStringArray(R.array.localities_malda).toList()
            "Jalpaiguri" -> resources.getStringArray(R.array.localities_jalpaiguri).toList()
            "Cooch Behar" -> resources.getStringArray(R.array.localities_cooch_behar).toList()
            "Alipurduar" -> resources.getStringArray(R.array.localities_alipurduar).toList()
            "Bankura" -> resources.getStringArray(R.array.localities_bankura).toList()
            "Purulia" -> resources.getStringArray(R.array.localities_purulia).toList()
            "Kharagpur" -> resources.getStringArray(R.array.localities_kharagpur).toList()
            "Midnapore" -> resources.getStringArray(R.array.localities_midnapore).toList()
            "Bardhaman" -> resources.getStringArray(R.array.localities_bardhaman).toList()
            "Hooghly" -> resources.getStringArray(R.array.localities_hooghly).toList()
            "Nadia" -> resources.getStringArray(R.array.localities_nadia).toList()
            "Murshidabad" -> resources.getStringArray(R.array.localities_murshidabad).toList()
            "Baharampur" -> resources.getStringArray(R.array.localities_baharampur).toList()
            "Haldia" -> resources.getStringArray(R.array.localities_haldia).toList()
            "Ranaghat" -> resources.getStringArray(R.array.localities_ranaghat).toList()
            "Kalyani" -> resources.getStringArray(R.array.localities_kalyani).toList()
            "Chandannagar" -> resources.getStringArray(R.array.localities_chandannagar).toList()
            "Other" -> resources.getStringArray(R.array.localities_other).toList()
            else -> emptyList()
        }
    }
    var selectedLocality by remember { mutableStateOf(if (localities.isNotEmpty()) localities.first() else "") }
    var localityExpanded by remember { mutableStateOf(false) }
    var customLocality by remember { mutableStateOf(registrationViewModel.customHometown) }

    // Location Permission Handling
    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val allGranted = permissionsResult.values.all { it }
        if (allGranted) {
            isLocating = true
            fetchLocation(fusedLocationClient, context) { city, locality ->
                selectedCity = city
                selectedLocality = locality
                registrationViewModel.city = if (city == "Other") customCity else city
                registrationViewModel.hometown = if (locality == "Other") customLocality else locality
                isLocating = false
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            isLocating = false
        }
    }

    // Auto-fetch location on screen load if permissions are granted
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            isLocating = true
            fetchLocation(fusedLocationClient, context) { city, locality ->
                selectedCity = city
                selectedLocality = locality
                registrationViewModel.city = if (city == "Other") customCity else city
                registrationViewModel.hometown = if (locality == "Other") customLocality else locality
                isLocating = false
            }
        }
    }

    // Validation
    val isCityOther = selectedCity == "Other"
    val isLocalityOther = selectedLocality == "Other"
    val isNextEnabled = registrationViewModel.dob.isNotBlank() &&
            ((isCityOther && customCity.isNotBlank()) || (!isCityOther && selectedCity.isNotBlank())) &&
            ((isLocalityOther && customLocality.isNotBlank()) || (!isLocalityOther && selectedLocality.isNotBlank()))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.enter_birthdate_city_hometown_title), color = Color.White) },
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Birthdate Section
                Text(stringResource(R.string.select_birth_date_label), color = Color.White, fontSize = 18.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { dayExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFFF6000)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Text("$selectedDay", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = dayExpanded,
                            onDismissRequest = { dayExpanded = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            dayRange.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text("$day", color = Color.White) },
                                    onClick = {
                                        selectedDay = day
                                        dayExpanded = false
                                        updateDob()
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { monthExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFFF6000)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Text(monthNames[selectedMonthIndex], color = Color.White)
                        }
                        DropdownMenu(
                            expanded = monthExpanded,
                            onDismissRequest = { monthExpanded = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            monthNames.forEachIndexed { index, monthName ->
                                DropdownMenuItem(
                                    text = { Text(monthName, color = Color.White) },
                                    onClick = {
                                        selectedMonthIndex = index
                                        monthExpanded = false
                                        updateDob()
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { yearExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFFF6000)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Text("$selectedYear", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = yearExpanded,
                            onDismissRequest = { yearExpanded = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            yearRange.forEach { yr ->
                                DropdownMenuItem(
                                    text = { Text(yr, color = Color.White) },
                                    onClick = {
                                        selectedYear = yr.toInt()
                                        yearExpanded = false
                                        updateDob()
                                    }
                                )
                            }
                        }
                    }
                }

                // City Section
                Text(stringResource(R.string.city_label), color = Color.White, fontSize = 18.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { cityExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFFF6000)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Text(
                                text = if (selectedCity.isNotEmpty()) selectedCity else stringResource(R.string.select_city_default),
                                color = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = cityExpanded,
                            onDismissRequest = { cityExpanded = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            cities.forEach { cityName ->
                                DropdownMenuItem(
                                    text = { Text(cityName, color = Color.White) },
                                    onClick = {
                                        selectedCity = cityName
                                        registrationViewModel.city = if (cityName == "Other") customCity else cityName
                                        cityExpanded = false
                                        selectedLocality = if (localities.isNotEmpty()) localities.first() else ""
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isLocating = true
                            permissionLauncher.launch(permissions)
                        },
                        enabled = !isLocating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Locate", color = Color.White)
                        }
                    }
                }
                if (selectedCity == "Other") {
                    OutlinedTextField(
                        value = customCity,
                        onValueChange = {
                            customCity = it
                            registrationViewModel.customCity = it
                            registrationViewModel.city = it
                        },
                        label = { Text(stringResource(R.string.city_label), color = Color.White) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF6000),
                            focusedBorderColor = Color(0xFFFF6000),
                            unfocusedBorderColor = Color.White,
                            focusedLabelColor = Color(0xFFFF6000),
                            unfocusedLabelColor = Color.White
                        )
                    )
                }

                // Locality Section
                Text(stringResource(R.string.locality_label), color = Color.White, fontSize = 18.sp)
                Box {
                    OutlinedButton(
                        onClick = { localityExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFFFF6000)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1A1A1A))
                    ) {
                        Text(
                            text = if (selectedLocality.isNotEmpty()) selectedLocality else stringResource(R.string.select_locality_default),
                            color = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = localityExpanded,
                        onDismissRequest = { localityExpanded = false },
                        modifier = Modifier.background(Color(0xFF1A1A1A))
                    ) {
                        localities.forEach { loc ->
                            DropdownMenuItem(
                                text = { Text(loc, color = Color.White) },
                                onClick = {
                                    selectedLocality = loc
                                    registrationViewModel.hometown = if (loc == "Other") customLocality else loc
                                    localityExpanded = false
                                }
                            )
                        }
                    }
                }
                if (selectedLocality == "Other") {
                    OutlinedTextField(
                        value = customLocality,
                        onValueChange = {
                            customLocality = it
                            registrationViewModel.customHometown = it
                            registrationViewModel.hometown = it
                        },
                        label = { Text(stringResource(R.string.locality_label), color = Color.White) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF6000),
                            focusedBorderColor = Color(0xFFFF6000),
                            unfocusedBorderColor = Color.White,
                            focusedLabelColor = Color(0xFFFF6000),
                            unfocusedLabelColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
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
                    Text(stringResource(R.string.next_button), color = Color.White)
                }
            }
        }
    )
}

// Helper function to fetch location and map to city/locality
private fun fetchLocation(
    fusedLocationClient: FusedLocationProviderClient,
    context: Context,
    onLocationFound: (String, String) -> Unit
) {
    val scope = (context as? ComponentActivity)?.lifecycleScope ?: return
    scope.launch {
        try {
            // Explicitly check permission before accessing location
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
                    onLocationFound("Other", "Other")
                }
                return@launch
            }

            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0]
                    val detectedCity = address.locality ?: address.subAdminArea ?: "Other"
                    val detectedLocality = address.subLocality ?: "Other"

                    val cities = context.resources.getStringArray(R.array.city_names).toList()
                    val matchedCity = cities.find { it.equals(detectedCity, ignoreCase = true) } ?: "Other"
                    val localities = when (matchedCity) {
                        "Kolkata" -> context.resources.getStringArray(R.array.localities_kolkata).toList()
                        "Howrah" -> context.resources.getStringArray(R.array.localities_howrah).toList()
                        "Durgapur" -> context.resources.getStringArray(R.array.localities_durgapur).toList()
                        "Asansol" -> context.resources.getStringArray(R.array.localities_asansol).toList()
                        "Siliguri" -> context.resources.getStringArray(R.array.localities_siliguri).toList()
                        "Darjeeling" -> context.resources.getStringArray(R.array.localities_darjeeling).toList()
                        "Malda" -> context.resources.getStringArray(R.array.localities_malda).toList()
                        "Jalpaiguri" -> context.resources.getStringArray(R.array.localities_jalpaiguri).toList()
                        "Cooch Behar" -> context.resources.getStringArray(R.array.localities_cooch_behar).toList()
                        "Alipurduar" -> context.resources.getStringArray(R.array.localities_alipurduar).toList()
                        "Bankura" -> context.resources.getStringArray(R.array.localities_bankura).toList()
                        "Purulia" -> context.resources.getStringArray(R.array.localities_purulia).toList()
                        "Kharagpur" -> context.resources.getStringArray(R.array.localities_kharagpur).toList()
                        "Midnapore" -> context.resources.getStringArray(R.array.localities_midnapore).toList()
                        "Bardhaman" -> context.resources.getStringArray(R.array.localities_bardhaman).toList()
                        "Hooghly" -> context.resources.getStringArray(R.array.localities_hooghly).toList()
                        "Nadia" -> context.resources.getStringArray(R.array.localities_nadia).toList()
                        "Murshidabad" -> context.resources.getStringArray(R.array.localities_murshidabad).toList()
                        "Baharampur" -> context.resources.getStringArray(R.array.localities_baharampur).toList()
                        "Haldia" -> context.resources.getStringArray(R.array.localities_haldia).toList()
                        "Ranaghat" -> context.resources.getStringArray(R.array.localities_ranaghat).toList()
                        "Kalyani" -> context.resources.getStringArray(R.array.localities_kalyani).toList()
                        "Chandannagar" -> context.resources.getStringArray(R.array.localities_chandannagar).toList()
                        "Other" -> context.resources.getStringArray(R.array.localities_other).toList()
                        else -> emptyList()
                    }
                    val matchedLocality = localities.find { it.equals(detectedLocality, ignoreCase = true) } ?: "Other"

                    withContext(Dispatchers.Main) {
                        onLocationFound(matchedCity, matchedLocality)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Unable to determine location", Toast.LENGTH_SHORT).show()
                        onLocationFound("Other", "Other")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
                    onLocationFound("Other", "Other")
                }
            }
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Location access denied", Toast.LENGTH_SHORT).show()
                onLocationFound("Other", "Other")
            }
        } catch (e: Exception) {
            Log.e("Location", "Error fetching location: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error fetching location", Toast.LENGTH_SHORT).show()
                onLocationFound("Other", "Other")
            }
        }
    }
}

fun updateLocale(context: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = context.resources.configuration
    config.setLocale(locale)
    return context.createConfigurationContext(config)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterInterestsScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit
) {
    // Global interests (static list)
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

    // Locality-based interests for all localities (union of all values)
    val localityInterestsMap = mapOf(
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
        // Add additional mappings as needed.
    )
    // Instead of showing only the interests corresponding to the selected hometown,
    // we now take the union of all locality interests.
    val localityInterests = localityInterestsMap.values.flatten()

    // Combine global and locality-based interests and remove duplicates (by name)
    val allInterests = (globalInterests + localityInterests).distinctBy { it.name }

    val maxInterests = 15
    val interestsOverLimit = registrationViewModel.interests.size > maxInterests

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.enter_interests_title), color = Color.White) },
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.global_interests_label),
                    color = Color.White,
                    fontSize = 18.sp
                )

                // Render all the interests (both global and locality-based)
                allInterests.forEach { interest ->
                    val isSelected = registrationViewModel.interests.contains(interest)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) {
                                    registrationViewModel.interests.remove(interest)
                                } else if (registrationViewModel.interests.size < maxInterests) {
                                    registrationViewModel.interests.add(interest)
                                }
                            }
                            .padding(vertical = 4.dp),
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

                if (interestsOverLimit) {
                    Text(
                        stringResource(R.string.max_interests_error),
                        color = Color.Red
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = registrationViewModel.interests.isNotEmpty() &&
                            registrationViewModel.interests.size <= maxInterests,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (registrationViewModel.interests.isNotEmpty() &&
                            registrationViewModel.interests.size <= maxInterests)
                            Color(0xFFFF6000) else Color.DarkGray
                    ),
                    shape = CircleShape
                ) {
                    Text(
                        stringResource(R.string.next_button),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
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
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
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
                title = { Text(stringResource(R.string.upload_media_title), color = Color.White) },
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
                        Text(stringResource(R.string.profile_picture_label), color = Color.White, fontSize = 18.sp)
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
                                Text(stringResource(R.string.tap_placeholder), color = Color.White, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Optional Photos Section
                        Text(stringResource(R.string.optional_photos_label), color = Color.White, fontSize = 18.sp)
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
                            Text(stringResource(R.string.add_photos_button), color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Voice Bio Section
                        Text(stringResource(R.string.voice_bio_label), color = Color.White, fontSize = 18.sp)
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
                                text = stringResource(R.string.voice_bio_duration_error),
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
                            Text(stringResource(R.string.next_button), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}


fun uploadOptionalPhoto(
    storageRef: StorageReference,
    uri: Uri,
    registrationViewModel: RegistrationViewModel
) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
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
                            text = stringResource(R.string.next_button),
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
