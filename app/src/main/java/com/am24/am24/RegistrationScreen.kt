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
import androidx.compose.material.icons.filled.Add
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

        // Check if this registration was initiated via Google sign-up.
        val isGoogleSignUp = intent.getBooleanExtra("isGoogleSignUp", false)
        val initialStep = if (isGoogleSignUp) 2 else 1
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        setContent {
            AppTheme {
                val registrationViewModel: RegistrationViewModel = viewModel()
                // If coming via Google, pre-populate email, name, and profile picture.
                if (isGoogleSignUp) {
                    registrationViewModel.email = intent?.getStringExtra("google_email") ?: ""
                    registrationViewModel.name = intent?.getStringExtra("google_displayName") ?: ""
                    registrationViewModel.profilePicUrl = intent?.getStringExtra("google_photoUrl")
                }
                RegistrationScreen(
                    onRegistrationComplete = {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    },
                    fusedLocationClient = fusedLocationClient,
                    initialStep = initialStep
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
            FirebaseRefs.db.reference
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

    // Add new fields
    var loveLanguage by mutableStateOf("")
    var jobRole by mutableStateOf("")

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
    var zodiac: String = "" // Add this to hold the zodiac sign

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

@Composable
fun RegistrationScreen(
    onRegistrationComplete: () -> Unit,
    fusedLocationClient: FusedLocationProviderClient,
    initialStep: Int
) {
    val registrationViewModel: RegistrationViewModel = viewModel()
    var currentStep by remember { mutableStateOf(initialStep) }
    val totalSteps = 11 // Updated total steps (language screen removed)
    val progress = currentStep.toFloat() / totalSteps.toFloat()

    val context = LocalContext.current
    val onNext = { currentStep += 1 }
    val onBack: () -> Unit = {
        if (currentStep > 1) currentStep -= 1 else (context as? ComponentActivity)?.finish()
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
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color(0xFFFF6000),
                    trackColor = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                when (currentStep) {
                    1 -> EnterEmailAndPasswordScreen(registrationViewModel, onNext, onBack)
                    2 -> EnterNameScreen(registrationViewModel, onNext)
                    3 -> UploadMediaComposable(registrationViewModel, onNext, onBack)
                    4 -> EnterBirthdateCityHometownScreen(registrationViewModel, onNext, fusedLocationClient)
                    5 -> EnterInterestsScreen(registrationViewModel, onNext)
                    6 -> EnterLocationAndSchoolScreen(registrationViewModel, onNext, onBack)
                    7 -> EnterGenderCommunityReligionScreen(registrationViewModel, onNext)
                    8 -> EnterLifestyleScreen(registrationViewModel, onNext)
                    9 -> EnterPersonalDetailsScreen(registrationViewModel, onNext, onBack)
                    10 -> EnterProfileHeadlineScreen(registrationViewModel, onNext)
                    11 -> EnterUsernameScreen(registrationViewModel, onRegistrationComplete, onBack)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterPersonalDetailsScreen(
    viewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val lookingForOptions = listOf(
        stringResource(R.string.looking_for_not_selected),
        stringResource(R.string.looking_for_casual_sex),
        stringResource(R.string.looking_for_connection),
        stringResource(R.string.looking_for_partner),
        stringResource(R.string.looking_for_marriage)
    )
    val loveLanguageOptions = listOf(
        stringResource(R.string.love_language_option_not_selected),
        stringResource(R.string.love_language_option_words_of_affirmation),
        stringResource(R.string.love_language_option_acts_of_service),
        stringResource(R.string.love_language_option_receiving_gifts),
        stringResource(R.string.love_language_option_quality_time),
        stringResource(R.string.love_language_option_physical_touch),
        stringResource(R.string.love_language_option_other),
    )

// ── Politics options ──
    val politicsOptions = listOf(
        stringResource(R.string.politics_option_far_left),
        stringResource(R.string.politics_option_left),
        stringResource(R.string.politics_option_centre_left),
        stringResource(R.string.politics_option_centre),
        stringResource(R.string.politics_option_centre_right),
        stringResource(R.string.politics_option_right),
        stringResource(R.string.politics_option_far_right),
        stringResource(R.string.politics_option_liberal),
        stringResource(R.string.politics_option_conservative),
        stringResource(R.string.politics_option_moderate),
        stringResource(R.string.politics_option_socialist),
        stringResource(R.string.politics_option_communist),
        stringResource(R.string.politics_option_other)
    )

    // ── Work/Industry options ──
    val workOptions = listOf(
        stringResource(R.string.work_option_private_sector),
        stringResource(R.string.work_option_government),
        stringResource(R.string.work_option_information_technology),
        stringResource(R.string.work_option_healthcare),
        stringResource(R.string.work_option_education),
        stringResource(R.string.work_option_construction),
        stringResource(R.string.work_option_manufacturing),
        stringResource(R.string.work_option_agriculture),
        stringResource(R.string.work_option_pharmaceuticals),
        stringResource(R.string.work_option_banking),
        stringResource(R.string.work_option_insurance),
        stringResource(R.string.work_option_real_estate),
        stringResource(R.string.work_option_retail),
        stringResource(R.string.work_option_e_commerce),
        stringResource(R.string.work_option_telecom),
        stringResource(R.string.work_option_automobile),
        stringResource(R.string.work_option_mining),
        stringResource(R.string.work_option_media_entertainment),
        stringResource(R.string.work_option_hospitality),
        stringResource(R.string.work_option_logistics),
        stringResource(R.string.work_option_non_profit),
        stringResource(R.string.work_option_startup),
        stringResource(R.string.work_option_freelance),
        stringResource(R.string.work_option_unemployed),
        stringResource(R.string.work_option_other)
    )

    // ── Job‐role options ──
    val jobRoleOptions = listOf(
        stringResource(R.string.job_role_option_software_developer),
        stringResource(R.string.job_role_option_data_scientist),
        stringResource(R.string.job_role_option_ux_ui_designer),
        stringResource(R.string.job_role_option_civil_engineer),
        stringResource(R.string.job_role_option_mechanical_engineer),
        stringResource(R.string.job_role_option_electrical_engineer),
        stringResource(R.string.job_role_option_project_manager),
        stringResource(R.string.job_role_option_product_manager),
        stringResource(R.string.job_role_option_business_analyst),
        stringResource(R.string.job_role_option_accountant),
        stringResource(R.string.job_role_option_chartered_accountant),
        stringResource(R.string.job_role_option_hr_manager),
        stringResource(R.string.job_role_option_marketing_manager),
        stringResource(R.string.job_role_option_sales_executive),
        stringResource(R.string.job_role_option_director),
        stringResource(R.string.job_role_option_ceo),
        stringResource(R.string.job_role_option_teacher),
        stringResource(R.string.job_role_option_professor),
        stringResource(R.string.job_role_option_researcher),
        stringResource(R.string.job_role_option_scientist),
        stringResource(R.string.job_role_option_doctor),
        stringResource(R.string.job_role_option_surgeon),
        stringResource(R.string.job_role_option_nurse),
        stringResource(R.string.job_role_option_pharmacist),
        stringResource(R.string.job_role_option_lawyer),
        stringResource(R.string.job_role_option_advocate),
        stringResource(R.string.job_role_option_legal_consultant),
        stringResource(R.string.job_role_option_graphic_designer),
        stringResource(R.string.job_role_option_content_writer),
        stringResource(R.string.job_role_option_photographer),
        stringResource(R.string.job_role_option_journalist),
        stringResource(R.string.job_role_option_editor),
        stringResource(R.string.job_role_option_chef),
        stringResource(R.string.job_role_option_barista),
        stringResource(R.string.job_role_option_pilot),
        stringResource(R.string.job_role_option_flight_attendant),
        stringResource(R.string.job_role_option_police_officer),
        stringResource(R.string.job_role_option_firefighter),
        stringResource(R.string.job_role_option_army_officer),
        stringResource(R.string.job_role_option_electrician),
        stringResource(R.string.job_role_option_plumber),
        stringResource(R.string.job_role_option_carpenter),
        stringResource(R.string.job_role_option_mechanic),
        stringResource(R.string.job_role_option_entrepreneur),
        stringResource(R.string.job_role_option_intern),
        stringResource(R.string.job_role_option_other)
    )
    var lookingFor by remember { mutableStateOf(viewModel.lookingFor) }
    var loveLanguage by remember { mutableStateOf(viewModel.loveLanguage) }
    var politics by remember { mutableStateOf(viewModel.politics) }
    val socialCauses = remember { mutableStateListOf<String>().apply { addAll(viewModel.socialCauses) } }
    var newSocialCause by remember { mutableStateOf("") }
    var jobRole by remember { mutableStateOf(viewModel.jobRole) }
    var work by remember { mutableStateOf(viewModel.work) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.enter_your_personal_details), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(stringResource(R.string.tell_us_more_about_yourself), color = Color.White, fontSize = 20.sp)
                }

                item {
                    DropdownWithStaticOptions(
                        label = stringResource(R.string.looking_for_label),
                        options = lookingForOptions,
                        selectedOption = lookingFor,
                        onOptionSelected = {
                            lookingFor = it
                            viewModel.lookingFor = it
                        }
                    )
                }

                item {
                    DropdownWithStaticOptions(
                        label = stringResource(R.string.love_language_label),
                        options = loveLanguageOptions,
                        selectedOption = loveLanguage,
                        onOptionSelected = {
                            loveLanguage = it
                            viewModel.loveLanguage = it
                        }
                    )
                }

                item {
                    DropdownWithStaticOptions(
                        label = stringResource(R.string.label_politics),
                        options = politicsOptions,
                        selectedOption = politics,
                        onOptionSelected = {
                            politics = it
                            viewModel.politics = it
                        }
                    )
                }

                item {
                    Column {
                        Text(stringResource(R.string.social_causes), color = Color.White, fontSize = 16.sp)
                        OutlinedTextField(
                            value = newSocialCause,
                            onValueChange = { newSocialCause = it },
                            label = { Text(stringResource(R.string.add_cause), color = Color.White) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFFF6000),
                                focusedBorderColor = Color(0xFFFF6000),
                                unfocusedBorderColor = Color.White,
                                focusedLabelColor = Color(0xFFFF6000),
                                unfocusedLabelColor = Color.White
                            ),
                            trailingIcon = {
                                if (newSocialCause.isNotEmpty()) {
                                    IconButton(onClick = {
                                        socialCauses.add(newSocialCause)
                                        viewModel.socialCauses.add(newSocialCause)
                                        newSocialCause = ""
                                    }) {
                                        Icon(Icons.Default.Add, stringResource(R.string.add), tint = Color.White)
                                    }
                                }
                            }
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            items(socialCauses) { cause ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        socialCauses.remove(cause)
                                        viewModel.socialCauses.remove(cause)
                                    },
                                    label = { Text(cause, color = Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFFF6000))
                                )
                            }
                        }
                    }
                }

                item {
                    DropdownWithStaticOptions(
                        label = stringResource(R.string.job_role_label),
                        options = jobRoleOptions,
                        selectedOption = jobRole,
                        onOptionSelected = {
                            jobRole = it
                            viewModel.jobRole = it
                        }
                    )
                }

                item {
                    DropdownWithStaticOptions(
                        label = stringResource(R.string.workplace_label),
                        options = workOptions,
                        selectedOption = work,
                        onOptionSelected = {
                            work = it
                            viewModel.work = it
                        }
                    )
                }

                item {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
                    ) {
                        Text(stringResource(R.string.next_button), color = Color.White)
                    }
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
                        text = stringResource(R.string.section_lifestyle_attributes),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Smoking Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_smoking),
                        value = registrationViewModel.lifestyle.smoking_habit,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(smoking_habit = it)
                        },
                        nouns = listOf(stringResource(R.string.non_smoker), stringResource(R.string.rare_smoker), stringResource(R.string.social_smoker), stringResource(R.string.frequent_smoker), stringResource(R.string.heavy_smoker))
                    )
                }

                // Drinking Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_drinking),
                        value = registrationViewModel.lifestyle.drinking_habit,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(drinking_habit = it)
                        },
                        nouns = listOf(stringResource(R.string.non_drinker), stringResource(R.string.rare_drinker), stringResource(R.string.social_drinker), stringResource(R.string.frequent_drinker), stringResource(R.string.heavy_drinker))
                    )
                }


                // Indoorsy to Outdoorsy Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_indoor_outdoor),
                        value = registrationViewModel.lifestyle.indoor_outdoor_orientation,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(indoor_outdoor_orientation = it)
                        },
                        nouns = listOf(stringResource(R.string.very_indoorsy), stringResource(R.string.mostly_indoorsy), stringResource(R.string.balanced), stringResource(R.string.mostly_outdoorsy), stringResource(R.string.very_outdoorsy))
                    )
                }

                // Social Media Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_social_media),
                        value = registrationViewModel.lifestyle.social_media_engagement,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(social_media_engagement = it)
                        },
                        nouns = listOf(stringResource(R.string.invisible), stringResource(R.string.watcher), stringResource(R.string.casual_participant), stringResource(R.string.engager), stringResource(R.string.influencer))
                    )
                }

                // Sleep Cycle Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_sleep),
                        value = registrationViewModel.lifestyle.sleep_pattern,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sleep_pattern = it)
                        },
                        nouns = listOf(stringResource(R.string.early_riser), stringResource(R.string.morning_person), stringResource(R.string.balanced), stringResource(R.string.night_owl), stringResource(R.string.late_night_enthusiast))
                    )
                }

                // Work-Life Balance Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_work_life_balance),
                        value = registrationViewModel.lifestyle.work_life_balance,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(work_life_balance = it)
                        },
                        nouns = listOf(stringResource(R.string.workaholic), stringResource(R.string.more_work_oriented), stringResource(R.string.balanced), stringResource(R.string.more_life_oriented), stringResource(R.string.relaxed))
                    )
                }

                // Exercise Frequency Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_exercise),
                        value = registrationViewModel.lifestyle.exercise_frequency,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(exercise_frequency = it)
                        },
                        nouns = listOf(stringResource(R.string.inactive), stringResource(R.string.rarely_active), stringResource(R.string.moderately_active), stringResource(R.string.active), stringResource(R.string.very_active))
                    )
                }

                // Family-Oriented Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_family_oriented),
                        value = registrationViewModel.lifestyle.family_orientated,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(family_orientated = it)
                        },
                        nouns = listOf(stringResource(R.string.independent), stringResource(R.string.slightly_family_oriented), stringResource(R.string.balanced), stringResource(R.string.more_family_oriented), stringResource(R.string.very_family_oriented))
                    )
                }

                // Adventurous Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_adventurousness),
                        value = registrationViewModel.lifestyle.adventurousness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(adventurousness = it)
                        },
                        nouns = listOf(stringResource(R.string.cautious), stringResource(R.string.slightly_adventurous), stringResource(R.string.moderately_adventurous), stringResource(R.string.adventurous), stringResource(R.string.thrill_seeker))
                    )
                }

                // Intellectual Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_intellectual_curiosity),
                        value = registrationViewModel.lifestyle.intellectual_curiosity,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(intellectual_curiosity = it)
                        },
                        nouns = listOf(stringResource(R.string.casual_thinker), stringResource(R.string.inquisitive), stringResource(R.string.knowledge_seeker), stringResource(R.string.intellectual), stringResource(R.string.philosopher))
                    )
                }

                // Creative/Artistic Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_creative_expression),
                        value = registrationViewModel.lifestyle.creative_expression,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(creative_expression = it)
                        },
                        nouns = listOf(stringResource(R.string.not_creative), stringResource(R.string.somewhat_creative), stringResource(R.string.creative), stringResource(R.string.very_creative), stringResource(R.string.artistic_genius))
                    )
                }

                // Fitness Level Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_physical_fitness),
                        value = registrationViewModel.lifestyle.physical_fitness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(physical_fitness = it)
                        },
                        nouns = listOf(stringResource(R.string.sedentary), stringResource(R.string.somewhat_fit), stringResource(R.string.fit), stringResource(R.string.athletic), stringResource(R.string.peak_fitness))
                    )
                }

                // Spiritual/Mindful Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_spirituality),
                        value = registrationViewModel.lifestyle.spirituality_mindfulness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(spirituality_mindfulness = it)
                        },
                        nouns = listOf(stringResource(R.string.not_spiritual), stringResource(R.string.occasionally_mindful), stringResource(R.string.balanced), stringResource(R.string.spiritual), stringResource(R.string.deeply_mindful))
                    )
                }

                // Humorous/Easygoing Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_humor),
                        value = registrationViewModel.lifestyle.easy_goingness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(easy_goingness = it)
                        },
                        nouns = listOf(stringResource(R.string.serious), stringResource(R.string.somewhat_easygoing), stringResource(R.string.balanced), stringResource(R.string.humorous), stringResource(R.string.life_of_the_party))
                    )
                }

                // Professional/Ambitious Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_professional_ambition),
                        value = registrationViewModel.lifestyle.professional_ambition,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(professional_ambition = it)
                        },
                        nouns = listOf(stringResource(R.string.relaxed), stringResource(R.string.occasionally_driven), stringResource(R.string.balanced), stringResource(R.string.ambitious), stringResource(R.string.high_ambitious))
                    )
                }

                // Environmentally Conscious Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_environmental_awareness),
                        value = registrationViewModel.lifestyle.environmental_awareness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(environmental_awareness = it)
                        },
                        nouns = listOf(stringResource(R.string.not_conscious), stringResource(R.string.occasionally_conscious), stringResource(R.string.balanced), stringResource(R.string.eco_friendly), stringResource(R.string.eco_champion))
                    )
                }

                // Foodie/Culinary Enthusiast Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_culinary_enthusiasm),
                        value = registrationViewModel.lifestyle.culinary_enthusiasm,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(culinary_enthusiasm = it)
                        },
                        nouns = listOf(stringResource(R.string.not_a_foodie), stringResource(R.string.occasional_foodie), stringResource(R.string.balanced), stringResource(R.string.foodie), stringResource(R.string.passionate_foodie))
                    )
                }

                // Sports Enthusiast Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.sports_enthusiast),
                        value = registrationViewModel.lifestyle.sports_enthusiasm,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sports_enthusiasm = it)
                        },
                        nouns = listOf(stringResource(R.string.non_sports), stringResource(R.string.casual_viewer), stringResource(R.string.occasional_player), stringResource(R.string.sports_enthusiast), stringResource(R.string.sports_fanatic))
                    )
                }

                // Sexual Activity Level Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_sexual_activity),
                        value = registrationViewModel.lifestyle.sexual_activity_level,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sexual_activity_level = it)
                        },
                        nouns = listOf(stringResource(R.string.inactive), stringResource(R.string.rarely_active), stringResource(R.string.moderately_active), stringResource(R.string.active), stringResource(R.string.very_active))
                    )
                }

                // Politically Aware Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_political_awareness),
                        value = registrationViewModel.lifestyle.political_awareness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(political_awareness = it)
                        },
                        nouns = listOf(stringResource(R.string.unaware), stringResource(R.string.occasionally_aware), stringResource(R.string.balanced), stringResource(R.string.aware), stringResource(R.string.politically_engaged))
                    )
                }

                // Introvert to Extrovert Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_sociability),
                        value = registrationViewModel.lifestyle.sociability,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(sociability = it)
                        },
                        nouns = listOf(stringResource(R.string.extremely_introverted), stringResource(R.string.very_introverted), stringResource(R.string.moderately_introverted), stringResource(R.string.slightly_introverted), stringResource(R.string.not_introverted))
                    )
                }

                // Community-Oriented Slider
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_community_engagement),
                        value = registrationViewModel.lifestyle.community_engagement,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle =
                                registrationViewModel.lifestyle.copy(community_engagement = it)
                        },
                        nouns = listOf(stringResource(R.string.individualistic), stringResource(R.string.occasionally_involved), stringResource(R.string.balanced), stringResource(R.string.community_oriented), stringResource(R.string.community_leader))
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
            text = if (adjustedValue == -1) stringResource(R.string.not_selected) else nouns.getOrElse(adjustedValue) { "Unknown" },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterLocationAndSchoolScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val educationLevels = listOf(stringResource(R.string.high_school_label), stringResource(R.string.college_label), stringResource(R.string.post_graduation_label))
    val highSchoolOptions = listOf(
        stringResource(R.string.high_school_andrews_high_school),
        stringResource(R.string.high_school_assembly_of_god_church_school),
        stringResource(R.string.high_school_bdm_international),
        stringResource(R.string.high_school_ballygunge_government_high_school),
        stringResource(R.string.high_school_baranagar_ramakrishna_mission),
        stringResource(R.string.high_school_barasat_mgm_high_school),
        stringResource(R.string.high_school_barasat_peary_charan),
        stringResource(R.string.high_school_barrackpore_government_high_school),
        stringResource(R.string.high_school_bethune_collegiate),
        stringResource(R.string.high_school_bidhannagar_government_high_school),
        stringResource(R.string.high_school_birla_high_school),
        stringResource(R.string.high_school_burdwan_cms_high_school),
        stringResource(R.string.high_school_calcutta_boys_school),
        stringResource(R.string.high_school_calcutta_girls_high_school),
        stringResource(R.string.high_school_darjeeling_government_high_school),
        stringResource(R.string.high_school_dps_durgapur),
        stringResource(R.string.high_school_dps_newtown),
        stringResource(R.string.high_school_dps_ruby_park),
        stringResource(R.string.high_school_don_bosco_park_circus),
        stringResource(R.string.high_school_goethals_memorial),
        stringResource(R.string.high_school_hare_school),
        stringResource(R.string.high_school_hindu_school),
        stringResource(R.string.high_school_howrah_zilla_school),
        stringResource(R.string.high_school_jadavpur_vidyapith),
        stringResource(R.string.high_school_jenkins_school),
        stringResource(R.string.high_school_kalyani_university_experimental),
        stringResource(R.string.high_school_kendriya_vidyalaya_ballygunge),
        stringResource(R.string.high_school_la_martiniere_boys),
        stringResource(R.string.high_school_la_martiniere_girls),
        stringResource(R.string.high_school_loreto_house),
        stringResource(R.string.high_school_mahadevi_birla_world_academy),
        stringResource(R.string.high_school_mitra_institution_main),
        stringResource(R.string.high_school_modern_high_school_girls),
        stringResource(R.string.high_school_nava_nalanda_high_school),
        stringResource(R.string.high_school_north_point_darjeeling),
        stringResource(R.string.high_school_patha_bhavan),
        stringResource(R.string.high_school_purwanchal_vidya_mandir),
        stringResource(R.string.high_school_rahara_ramakrishna_mission),
        stringResource(R.string.high_school_ramakrishna_mission_narendrapur),
        stringResource(R.string.high_school_rani_birla_girls_school),
        stringResource(R.string.high_school_sakhawat_memorial_girls),
        stringResource(R.string.high_school_scottish_church_collegiate),
        stringResource(R.string.high_school_siliguri_boys_high_school),
        stringResource(R.string.high_school_south_point_high_school),
        stringResource(R.string.high_school_st_james_school),
        stringResource(R.string.high_school_st_josephs_north_point),
        stringResource(R.string.high_school_st_lawrence_high_school),
        stringResource(R.string.high_school_st_pauls_mission_school),
        stringResource(R.string.high_school_st_thomas_kidderpore),
        stringResource(R.string.high_school_st_xaviers_collegiate),
        stringResource(R.string.high_school_the_heritage_school),
        stringResource(R.string.high_school_uttarpara_government_high_school),
        stringResource(R.string.high_school_asansol_st_anthonys),
        stringResource(R.string.high_school_bankura_christian_school),
        stringResource(R.string.high_school_berhampore_girls_high_school),
        stringResource(R.string.high_school_contai_high_school),
        stringResource(R.string.high_school_hooghly_collegiate_school),
        stringResource(R.string.high_school_krishnanagar_collegiate_school),
        stringResource(R.string.high_school_malda_zilla_school),
        stringResource(R.string.high_school_midnapore_collegiate_school),
        stringResource(R.string.high_school_ashok_hall),
        stringResource(R.string.high_school_mahadevi_birla_shishu_vihar),
        stringResource(R.string.high_school_jewish_girls),
        stringResource(R.string.high_school_cathedral_john_connon),
        stringResource(R.string.high_school_dhirubhai_ambani),
        stringResource(R.string.high_school_doon_school),
        stringResource(R.string.high_school_mayo_college),
        stringResource(R.string.high_school_modern_school_barakhamba),
        stringResource(R.string.high_school_rishi_valley),
        stringResource(R.string.high_school_scindia_school),
        stringResource(R.string.high_school_shri_ram_vasant_vihar),
        stringResource(R.string.high_school_lawrence_sanawar),
        stringResource(R.string.high_school_welham_girls),
        stringResource(R.string.high_school_other)
    )

    val collegeOptions = listOf(
        stringResource(R.string.college_other),
        stringResource(R.string.college_acharya_jagadish_chandra_bose_college),
        stringResource(R.string.college_asutosh_college),
        stringResource(R.string.college_bangabasi_college),
        stringResource(R.string.college_barasat_government_college),
        stringResource(R.string.college_barrackpore_rastraguru_surendranath_college),
        stringResource(R.string.college_behala_college),
        stringResource(R.string.college_bethune_college),
        stringResource(R.string.college_bidhannagar_college),
        stringResource(R.string.college_city_college),
        stringResource(R.string.college_derozio_memorial_college),
        stringResource(R.string.college_dinabandhu_andrews_college),
        stringResource(R.string.college_dum_dum_motijheel_college),
        stringResource(R.string.college_goenka_college),
        stringResource(R.string.college_heramba_chandra_college),
        stringResource(R.string.college_hooghly_mohsin_college),
        stringResource(R.string.college_iit_kharagpur),
        stringResource(R.string.college_iem_kolkata),
        stringResource(R.string.college_jadavpur_university),
        stringResource(R.string.college_jogamaya_devi_college),
        stringResource(R.string.college_kalyani_mahavidyalaya),
        stringResource(R.string.college_kazi_nazrul_islam_mahavidyalaya),
        stringResource(R.string.college_krishnanagar_government_college),
        stringResource(R.string.college_lady_brabourne_college),
        stringResource(R.string.college_loreto_college),
        stringResource(R.string.college_maulana_azad_college),
        stringResource(R.string.college_nit_durgapur),
        stringResource(R.string.college_presidency_university),
        stringResource(R.string.college_ramakrishna_mission_narendrapur),
        stringResource(R.string.college_ramakrishna_mission_vidyamandira),
        stringResource(R.string.college_rishi_bankim_chandra_college),
        stringResource(R.string.college_techno_india),
        stringResource(R.string.college_scottish_church_college),
        stringResource(R.string.college_serampore_college),
        stringResource(R.string.college_seth_anandram_jaipuria_college),
        stringResource(R.string.college_shri_shikshayatan_college),
        stringResource(R.string.college_siliguri_college),
        stringResource(R.string.college_southfield_college),
        stringResource(R.string.college_st_xaviers_college),
        stringResource(R.string.college_surendranath_college),
        stringResource(R.string.college_university_of_calcutta),
        stringResource(R.string.college_vidyasagar_college),
        stringResource(R.string.college_west_bengal_state_university),
        stringResource(R.string.college_basanti_devi_college),
        stringResource(R.string.college_gokhale_memorial_girls_college),
        stringResource(R.string.college_gurudas_college),
        stringResource(R.string.college_narasinha_dutt_college),
        stringResource(R.string.college_sivanath_sastri_college),
        stringResource(R.string.college_christ_university),
        stringResource(R.string.college_fergusson_college),
        stringResource(R.string.college_hindu_college),
        stringResource(R.string.college_iisc_bangalore),
        stringResource(R.string.college_iit_kanpur),
        stringResource(R.string.college_iit_roorkee),
        stringResource(R.string.college_lady_shri_ram_college),
        stringResource(R.string.college_loyola_college),
        stringResource(R.string.college_miranda_house),
        stringResource(R.string.college_st_stephens_college),
        stringResource(R.string.college_hansraj_college),
        stringResource(R.string.college_mount_carmel_college),
        stringResource(R.string.college_australian_national_university),
        stringResource(R.string.college_carnegie_mellon_university),
        stringResource(R.string.college_eth_zurich),
        stringResource(R.string.college_harvard_university),
        stringResource(R.string.college_imperial_college_london),
        stringResource(R.string.college_london_school_of_economics),
        stringResource(R.string.college_mcgill_university),
        stringResource(R.string.college_mit),
        stringResource(R.string.college_national_university_singapore),
        stringResource(R.string.college_purdue_university),
        stringResource(R.string.college_sorbonne_university),
        stringResource(R.string.college_stanford_university),
        stringResource(R.string.college_tu_delft),
        stringResource(R.string.college_university_college_london),
        stringResource(R.string.college_university_of_amsterdam),
        stringResource(R.string.college_university_of_british_columbia),
        stringResource(R.string.college_university_of_california_berkeley),
        stringResource(R.string.college_university_of_california_san_diego),
        stringResource(R.string.college_university_of_cambridge),
        stringResource(R.string.college_university_of_edinburgh),
        stringResource(R.string.college_university_of_melbourne),
        stringResource(R.string.college_university_of_michigan),
        stringResource(R.string.college_university_of_oxford),
        stringResource(R.string.college_university_of_queensland),
        stringResource(R.string.college_university_of_sydney),
        stringResource(R.string.college_university_of_toronto),
        // — New engineering colleges —
        stringResource(R.string.college_srm_institute_of_science_and_technology),
        stringResource(R.string.college_vellore_institute_of_technology),
        stringResource(R.string.college_bits_pilani),
        stringResource(R.string.college_manipal_institute_of_technology),
        stringResource(R.string.college_iiit_hyderabad),

        // — New private Arts/Science —
        stringResource(R.string.college_op_jindal_global_university),
        stringResource(R.string.college_ashoka_university),

        // — New Design institutes —
        stringResource(R.string.college_nid_ahmedabad),
        stringResource(R.string.college_nid_kurukshetra),
        stringResource(R.string.college_nid_gandhinagar),
        stringResource(R.string.college_nid_bengaluru),
        stringResource(R.string.college_nid_bhopal),
        stringResource(R.string.college_nid_jorhat),
        stringResource(R.string.college_nid_vijayawada),
        stringResource(R.string.college_nift),
        stringResource(R.string.college_srishti_manipal),
        stringResource(R.string.college_pearl_academy),
        stringResource(R.string.college_symbiosis_institute_of_design),
        stringResource(R.string.college_mit_institute_of_design),
        stringResource(R.string.college_iiad),
        stringResource(R.string.college_world_university_of_design),
        stringResource(R.string.college_amity_school_of_fashion_technology),
        stringResource(R.string.college_jd_institute_of_fashion_technology),
        stringResource(R.string.college_arch_academy_of_design),
        stringResource(R.string.college_daiict),

        // — New Law schools —
        stringResource(R.string.college_nlsiu_bangalore),
        stringResource(R.string.college_nalsar_hyderabad),
        stringResource(R.string.college_nlu_delhi),
        stringResource(R.string.college_wbnujs_kolkata),
        stringResource(R.string.college_nliu_bhopal),
        stringResource(R.string.college_gnlu_gandhinagar),
        stringResource(R.string.college_hnlu_raipur),
        stringResource(R.string.college_rmlnlu_lucknow),
        stringResource(R.string.college_rgnul_patiala),
        stringResource(R.string.college_cnlu_patna),
        stringResource(R.string.college_nuals_kochi),
        stringResource(R.string.college_nluo_cuttack),
        stringResource(R.string.college_nusr_law_ranchi),
        stringResource(R.string.college_nluja_guwahati),
        stringResource(R.string.college_tnnlu_tiruchirappalli),
        stringResource(R.string.college_mnlu_mumbai),
        stringResource(R.string.college_mnlu_nagpur),
        stringResource(R.string.college_mnlu_aurangabad),
        stringResource(R.string.college_hpnlu_shimla),
        stringResource(R.string.college_dnlu_jabalpur),
        stringResource(R.string.college_dbranlu_sonipat),
        stringResource(R.string.college_faculty_of_law_du),
        stringResource(R.string.college_symbiosis_law_school),
        stringResource(R.string.college_glc_mumbai),
        stringResource(R.string.college_ils_law_pune),
        stringResource(R.string.college_amity_law_school_noida),
        stringResource(R.string.college_christ_univ_law),
        stringResource(R.string.college_bhu_faculty_of_law),
        stringResource(R.string.college_amu_faculty_of_law),
        stringResource(R.string.college_jamia_law),
        stringResource(R.string.college_op_jindal_law_school),
        stringResource(R.string.college_army_institute_of_law_mohali),
        stringResource(R.string.college_kerala_law_academy),
        stringResource(R.string.college_school_of_law_calcutta),

        // — New Arts colleges —
        stringResource(R.string.college_college_of_art_du),
        stringResource(R.string.college_sir_jj_school_of_art),
        stringResource(R.string.college_faculty_visual_arts_bhu),
        stringResource(R.string.college_msu_fine_arts_vadodara),
        stringResource(R.string.college_govt_college_art_craft_kolkata),
        stringResource(R.string.college_visva_bharati_kala_bhavana),
        stringResource(R.string.college_chennai_govt_fine_arts),
        stringResource(R.string.college_rachana_sansad),
        stringResource(R.string.college_goa_college_of_art),
        stringResource(R.string.college_amity_school_fine_arts),
        stringResource(R.string.college_kalakshetra_foundation),
        stringResource(R.string.college_bharatiya_kala_kendra),
        stringResource(R.string.college_gandharva_mahavidyalaya),
        stringResource(R.string.college_nsd),
        stringResource(R.string.college_ftii_pune),
        stringResource(R.string.college_srfti_kolkata),
        stringResource(R.string.college_kathak_kendra),
        stringResource(R.string.college_drama_thrissur),
        stringResource(R.string.college_st_stephens),
        stringResource(R.string.college_lsr_college),
        stringResource(R.string.college_loyola_chennai),
        stringResource(R.string.college_christ_univ),
        stringResource(R.string.college_miranda_house),
        stringResource(R.string.college_presidency_university),
        stringResource(R.string.college_jadavpur_university),
        stringResource(R.string.college_ashoka_university),
        stringResource(R.string.college_flame_university),
        stringResource(R.string.college_symbiosis_liberal_arts),
        stringResource(R.string.college_krea_university),
        stringResource(R.string.college_hindu_college),
        stringResource(R.string.college_ramjas_college),
        stringResource(R.string.college_fergusson_college),
        stringResource(R.string.college_st_xaviers_mumbai),
        stringResource(R.string.college_mcc_chennai)
    )

    val postGraduationOptions = listOf(
        stringResource(R.string.postgrad_other),
        stringResource(R.string.postgrad_adamas_university),
        stringResource(R.string.postgrad_aliah_university),
        stringResource(R.string.postgrad_amity_university_kolkata),
        stringResource(R.string.postgrad_bankura_university),
        stringResource(R.string.postgrad_bidhan_chandra_krishi_viswavidyalaya),
        stringResource(R.string.postgrad_brainware_university),
        stringResource(R.string.postgrad_cooch_behar_panchanan_barma_university),
        stringResource(R.string.postgrad_darjeeling_hills_university),
        stringResource(R.string.postgrad_diamond_harbour_womens_university),
        stringResource(R.string.postgrad_iacs),
        stringResource(R.string.postgrad_jadavpur_university),
        stringResource(R.string.postgrad_jis_university),
        stringResource(R.string.postgrad_kazi_nazrul_university),
        stringResource(R.string.postgrad_maulana_abul_kalam_azad_university_of_technology),
        stringResource(R.string.postgrad_netaji_subhash_open_university),
        stringResource(R.string.postgrad_north_bengal_university),
        stringResource(R.string.postgrad_presidency_university),
        stringResource(R.string.postgrad_rabindra_bharati_university),
        stringResource(R.string.postgrad_raiganj_university),
        stringResource(R.string.postgrad_ramakrishna_mission_vivekananda),
        stringResource(R.string.postgrad_techno_india),
        stringResource(R.string.postgrad_seacom_skills_university),
        stringResource(R.string.postgrad_sidho_kanho_birsha_university),
        stringResource(R.string.postgrad_sister_nivedita_university),
        stringResource(R.string.postgrad_university_of_burdwan),
        stringResource(R.string.postgrad_university_of_calcutta),
        stringResource(R.string.postgrad_university_of_engineering_and_management),
        stringResource(R.string.postgrad_university_of_kalyani),
        stringResource(R.string.postgrad_uttar_banga_krishi_vishwavidyalaya),
        stringResource(R.string.postgrad_vidyasagar_university),
        stringResource(R.string.postgrad_visva_bharati_university),
        stringResource(R.string.postgrad_west_bengal_state_university),
        stringResource(R.string.postgrad_west_bengal_university_of_animal_and_fishery_sciences),
        stringResource(R.string.postgrad_west_bengal_university_of_health_sciences),
        stringResource(R.string.postgrad_west_bengal_university_of_teachers_training),
        stringResource(R.string.postgrad_iit_bombay),
        stringResource(R.string.postgrad_iit_delhi),
        stringResource(R.string.postgrad_iit_kanpur),
        stringResource(R.string.postgrad_iit_kharagpur),
        stringResource(R.string.postgrad_iit_madras),
        stringResource(R.string.postgrad_iim_ahmedabad),
        stringResource(R.string.postgrad_iim_bangalore),
        stringResource(R.string.postgrad_iim_calcutta),
        stringResource(R.string.postgrad_iisc_bangalore),
        stringResource(R.string.postgrad_jnu),
        stringResource(R.string.postgrad_university_of_delhi),
        stringResource(R.string.postgrad_harvard_university),
        stringResource(R.string.postgrad_stanford_university),
        stringResource(R.string.postgrad_mit),
        stringResource(R.string.postgrad_ucsd),
        stringResource(R.string.postgrad_purdue_university),
        stringResource(R.string.postgrad_uc_berkeley),
        stringResource(R.string.postgrad_university_of_michigan),
        stringResource(R.string.postgrad_university_of_oxford),
        stringResource(R.string.postgrad_university_of_cambridge),
        stringResource(R.string.postgrad_imperial_college_london),
        stringResource(R.string.postgrad_london_school_of_economics),
        stringResource(R.string.postgrad_university_of_toronto),
        stringResource(R.string.postgrad_university_of_british_columbia),
        stringResource(R.string.postgrad_mcgill_university),
        stringResource(R.string.postgrad_university_of_melbourne),
        stringResource(R.string.postgrad_university_of_sydney),
        stringResource(R.string.postgrad_australian_national_university),
        stringResource(R.string.postgrad_tu_delft),
        stringResource(R.string.postgrad_eth_zurich),
        stringResource(R.string.postgrad_university_college_london),
        stringResource(R.string.postgrad_university_of_amsterdam),
        stringResource(R.string.postgrad_sorbonne_university),
                // — New engineering colleges —
        stringResource(R.string.college_srm_institute_of_science_and_technology),
        stringResource(R.string.college_vellore_institute_of_technology),
        stringResource(R.string.college_bits_pilani),
        stringResource(R.string.college_manipal_institute_of_technology),
        stringResource(R.string.college_iiit_hyderabad),

        // — New private Arts/Science —
        stringResource(R.string.college_op_jindal_global_university),
        stringResource(R.string.college_ashoka_university),

        // — New Design institutes —
        stringResource(R.string.college_nid_ahmedabad),
        stringResource(R.string.college_nid_kurukshetra),
        stringResource(R.string.college_nid_gandhinagar),
        stringResource(R.string.college_nid_bengaluru),
        stringResource(R.string.college_nid_bhopal),
        stringResource(R.string.college_nid_jorhat),
        stringResource(R.string.college_nid_vijayawada),
        stringResource(R.string.college_nift),
        stringResource(R.string.college_srishti_manipal),
        stringResource(R.string.college_pearl_academy),
        stringResource(R.string.college_symbiosis_institute_of_design),
        stringResource(R.string.college_mit_institute_of_design),
        stringResource(R.string.college_iiad),
        stringResource(R.string.college_world_university_of_design),
        stringResource(R.string.college_amity_school_of_fashion_technology),
        stringResource(R.string.college_jd_institute_of_fashion_technology),
        stringResource(R.string.college_arch_academy_of_design),
        stringResource(R.string.college_daiict),

        // — New Law schools —
        stringResource(R.string.college_nlsiu_bangalore),
        stringResource(R.string.college_nalsar_hyderabad),
        stringResource(R.string.college_nlu_delhi),
        stringResource(R.string.college_wbnujs_kolkata),
        stringResource(R.string.college_nliu_bhopal),
        stringResource(R.string.college_gnlu_gandhinagar),
        stringResource(R.string.college_hnlu_raipur),
        stringResource(R.string.college_rmlnlu_lucknow),
        stringResource(R.string.college_rgnul_patiala),
        stringResource(R.string.college_cnlu_patna),
        stringResource(R.string.college_nuals_kochi),
        stringResource(R.string.college_nluo_cuttack),
        stringResource(R.string.college_nusr_law_ranchi),
        stringResource(R.string.college_nluja_guwahati),
        stringResource(R.string.college_tnnlu_tiruchirappalli),
        stringResource(R.string.college_mnlu_mumbai),
        stringResource(R.string.college_mnlu_nagpur),
        stringResource(R.string.college_mnlu_aurangabad),
        stringResource(R.string.college_hpnlu_shimla),
        stringResource(R.string.college_dnlu_jabalpur),
        stringResource(R.string.college_dbranlu_sonipat),
        stringResource(R.string.college_faculty_of_law_du),
        stringResource(R.string.college_symbiosis_law_school),
        stringResource(R.string.college_glc_mumbai),
        stringResource(R.string.college_ils_law_pune),
        stringResource(R.string.college_amity_law_school_noida),
        stringResource(R.string.college_christ_univ_law),
        stringResource(R.string.college_bhu_faculty_of_law),
        stringResource(R.string.college_amu_faculty_of_law),
        stringResource(R.string.college_jamia_law),
        stringResource(R.string.college_op_jindal_law_school),
        stringResource(R.string.college_army_institute_of_law_mohali),
        stringResource(R.string.college_kerala_law_academy),
        stringResource(R.string.college_school_of_law_calcutta),

        // — New Arts colleges —
        stringResource(R.string.college_college_of_art_du),
        stringResource(R.string.college_sir_jj_school_of_art),
        stringResource(R.string.college_faculty_visual_arts_bhu),
        stringResource(R.string.college_msu_fine_arts_vadodara),
        stringResource(R.string.college_govt_college_art_craft_kolkata),
        stringResource(R.string.college_visva_bharati_kala_bhavana),
        stringResource(R.string.college_chennai_govt_fine_arts),
        stringResource(R.string.college_rachana_sansad),
        stringResource(R.string.college_goa_college_of_art),
        stringResource(R.string.college_amity_school_fine_arts),
        stringResource(R.string.college_kalakshetra_foundation),
        stringResource(R.string.college_bharatiya_kala_kendra),
        stringResource(R.string.college_gandharva_mahavidyalaya),
        stringResource(R.string.college_nsd),
        stringResource(R.string.college_ftii_pune),
        stringResource(R.string.college_srfti_kolkata),
        stringResource(R.string.college_kathak_kendra),
        stringResource(R.string.college_drama_thrissur),
        stringResource(R.string.college_st_stephens),
        stringResource(R.string.college_lsr_college),
        stringResource(R.string.college_loyola_chennai),
        stringResource(R.string.college_christ_univ),
        stringResource(R.string.college_miranda_house),
        stringResource(R.string.college_presidency_university),
        stringResource(R.string.college_jadavpur_university),
        stringResource(R.string.college_ashoka_university),
        stringResource(R.string.college_flame_university),
        stringResource(R.string.college_symbiosis_liberal_arts),
        stringResource(R.string.college_krea_university),
        stringResource(R.string.college_hindu_college),
        stringResource(R.string.college_ramjas_college),
        stringResource(R.string.college_fergusson_college),
        stringResource(R.string.college_st_xaviers_mumbai),
        stringResource(R.string.college_mcc_chennai)
    )

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
                if (registrationViewModel.educationLevel in listOf(stringResource(R.string.high_school), stringResource(R.string.college), stringResource(R.string.post_graduation_label))) {
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
    var other = stringResource(R.string.college_other)
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(selectedOption == other) }
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
                            showCustomInput = option == other
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
                    onOptionSelected(other)
                },
                label = { Text(stringResource(R.string.enter_custom_value), color = Color.White) },
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
            val snapshot = FirebaseRefs.db
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
    var other = stringResource(R.string.college_other)
    // Predefined lists for dropdown options
    val genderOptions = listOf(stringResource(R.string.male_option), stringResource(R.string.female_option), other)
    val communityOptions = listOf(
        /* ——— Fallback / custom entry ——— */
        other,
        /* ——— Mainstream Bengal & pan‑India ——— */
        stringResource(R.string.community_bengali),
        stringResource(R.string.community_marwari),
        stringResource(R.string.community_bihari),
        stringResource(R.string.community_punjabi),
        stringResource(R.string.community_santhal),
        stringResource(R.string.community_bangal),
        stringResource(R.string.community_ghoti),
        stringResource(R.string.community_gujarati),
        stringResource(R.string.community_kannadiga),
        stringResource(R.string.community_tamil),
        stringResource(R.string.community_malayali),
        stringResource(R.string.community_odia),
        stringResource(R.string.community_telugu),
        stringResource(R.string.community_nepali),
        stringResource(R.string.community_munda),
        stringResource(R.string.community_oraon),

        /* ——— Himalayan neighbours ——— */
        stringResource(R.string.community_bhutanese),
        stringResource(R.string.community_sikkimese),

        /* ——— Arunachal Pradesh ——— */
        stringResource(R.string.community_arunachali),   // ← NEW

        /* ——— Assam plains tribes ——— */
        stringResource(R.string.community_assamese),
        stringResource(R.string.community_sonowal_kachari)
    )
    val religionOptions = listOf(stringResource(R.string.religion_hindu), stringResource(R.string.religion_muslim), stringResource(R.string.religion_christian), stringResource(R.string.religion_sikh), stringResource(R.string.religion_buddhist), stringResource(R.string.religion_jain), stringResource(R.string.religion_no_religion), stringResource(R.string.religion_indigenous_tribal), stringResource(R.string.religion_other))

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
    val database = FirebaseRefs.db.reference

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
                    var usernameempty = stringResource(R.string.username_empty_error)
                    var other = stringResource(R.string.college_other)
                    // Finish Button
                    Button(
                        onClick = {
                            // Inside the Finish Button onClick in EnterUsernameScreen:
                            val trimmedUsername = username.text.trim()
                            if (trimmedUsername.isEmpty()) {
                                isUsernameValid = false
                                usernameErrorMessage = usernameempty
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
                                                    saveProfileToFirebase(registrationViewModel, other) {
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
    other: String,
    onRegistrationComplete: () -> Unit
) {
    try {
        val database = FirebaseRefs.db.reference
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
            city = if (registrationViewModel.city == other) registrationViewModel.customCity else registrationViewModel.city,
            // Hometown represents locality; if empty, fallback to a custom value if provided
            hometown = registrationViewModel.hometown.ifEmpty { registrationViewModel.customHometown },
            highSchool = if (registrationViewModel.highSchool == other) registrationViewModel.customHighSchool else registrationViewModel.highSchool,
            college = if (registrationViewModel.college == other) registrationViewModel.customCollege else registrationViewModel.college,
            postGraduation = if (registrationViewModel.postGraduation == other) registrationViewModel.customPostGraduation else registrationViewModel.postGraduation,
            work = if (registrationViewModel.work == other) registrationViewModel.customWork else registrationViewModel.work,
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
            loveLanguage = registrationViewModel.loveLanguage,
            jobRole = registrationViewModel.jobRole,
            preferredLanguage = registrationViewModel.selectedLanguage, // NEW: Save language choice
            zodiac = registrationViewModel.zodiac // Include zodiac in the profile
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
    val interestedOptions = listOf(stringResource(R.string.male_option), stringResource(R.string.female_option))

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
                        var other = stringResource(R.string.college_other)

                        // Caste Input using SearchableDropdownWithCustomOption
                        SearchableDropdownWithCustomOption(
                            title = stringResource(R.string.caste_title),
                            options = listOf(stringResource(R.string.caste_kulin_brahmin), stringResource(R.string.caste_non_kulin_brahmin), stringResource(R.string.caste_kulin_kayastha), stringResource(R.string.caste_non_kulin_kayastha), stringResource(R.string.caste_kshatriya), stringResource(R.string.caste_baidya), stringResource(R.string.caste_mahishya), stringResource(R.string.caste_sadgop), stringResource(R.string.caste_vaishya), stringResource(R.string.caste_obc), stringResource(R.string.caste_scheduled_caste), stringResource(R.string.caste_scheduled_tribe), stringResource(R.string.caste_rajbonshi), stringResource(R.string.caste_general), stringResource(R.string.caste_other)),
                            selectedOption = registrationViewModel.caste,
                            onOptionSelected = { selectedOption ->
                                if (selectedOption != other) {
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
    val monthNames = listOf(stringResource(R.string.jan), stringResource(R.string.feb), stringResource(R.string.mar), stringResource(R.string.apr), stringResource(R.string.may), stringResource(R.string.jun), stringResource(R.string.jul), stringResource(R.string.aug), stringResource(R.string.sep), stringResource(R.string.oct), stringResource(R.string.nov), stringResource(R.string.dec))
    var other = stringResource(R.string.college_other)

    var selectedDay by remember { mutableStateOf(dayRange.first()) }
    var selectedMonthIndex by remember { mutableStateOf(0) }
    var selectedYear by remember { mutableStateOf(yearRange.first().toInt()) }

    fun updateDob() {
        registrationViewModel.dob = "$selectedDay/${selectedMonthIndex + 1}/$selectedYear"
        registrationViewModel.zodiac = deriveZodiac(registrationViewModel.dob) // Compute and set zodiac
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

    var kolkata = stringResource(R.string.city_kolkata)
    var howrah = stringResource(R.string.city_howrah)
    var durgapur = stringResource(R.string.city_durgapur)
    var asansol = stringResource(R.string.city_asansol)
    var siliguri = stringResource(R.string.city_siliguri)
    var darjeeling = stringResource(R.string.city_darjeeling)
    var malda = stringResource(R.string.city_malda)
    var jalpaiguri = stringResource(R.string.city_jalpaiguri)
    var coochbehar = stringResource(R.string.city_cooch_behar)
    var alipurduar = stringResource(R.string.city_alipurduar)
    var bankura = stringResource(R.string.city_bankura)
    var purulia = stringResource(R.string.city_purulia)
    var kharagpur = stringResource(R.string.city_kharagpur)
    var midnapore = stringResource(R.string.city_midnapore)
    var bardhaman = stringResource(R.string.city_bardhaman)
    var hooghly = stringResource(R.string.city_hooghly)
    var murshidabad = stringResource(R.string.city_murshidabad)
    var baharampur = stringResource(R.string.city_baharampur)
    var haldia = stringResource(R.string.city_haldia)
    var ranaghat = stringResource(R.string.city_ranaghat)
    var kalyani = stringResource(R.string.city_kalyani)
    var chandannagar = stringResource(R.string.city_chandannagar)

    // Locality Selection
    val localities = remember(selectedCity) {
        when (selectedCity) {
            kolkata -> resources.getStringArray(R.array.localities_kolkata).toList()
            howrah -> resources.getStringArray(R.array.localities_howrah).toList()
            durgapur -> resources.getStringArray(R.array.localities_durgapur).toList()
            asansol -> resources.getStringArray(R.array.localities_asansol).toList()
            siliguri -> resources.getStringArray(R.array.localities_siliguri).toList()
            darjeeling -> resources.getStringArray(R.array.localities_darjeeling).toList()
            malda -> resources.getStringArray(R.array.localities_malda).toList()
            jalpaiguri -> resources.getStringArray(R.array.localities_jalpaiguri).toList()
            coochbehar -> resources.getStringArray(R.array.localities_cooch_behar).toList()
            alipurduar -> resources.getStringArray(R.array.localities_alipurduar).toList()
            bankura -> resources.getStringArray(R.array.localities_bankura).toList()
            purulia -> resources.getStringArray(R.array.localities_purulia).toList()
            kharagpur -> resources.getStringArray(R.array.localities_kharagpur).toList()
            midnapore -> resources.getStringArray(R.array.localities_midnapore).toList()
            bardhaman -> resources.getStringArray(R.array.localities_bardhaman).toList()
            hooghly -> resources.getStringArray(R.array.localities_hooghly).toList()
            murshidabad -> resources.getStringArray(R.array.localities_murshidabad).toList()
            baharampur -> resources.getStringArray(R.array.localities_baharampur).toList()
            haldia -> resources.getStringArray(R.array.localities_haldia).toList()
            ranaghat -> resources.getStringArray(R.array.localities_ranaghat).toList()
            kalyani -> resources.getStringArray(R.array.localities_kalyani).toList()
            chandannagar -> resources.getStringArray(R.array.localities_chandannagar).toList()
            other -> resources.getStringArray(R.array.localities_other).toList()
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
            fetchLocation(fusedLocationClient, context, other, kolkata, howrah, durgapur, asansol, siliguri, darjeeling, malda, jalpaiguri, coochbehar, alipurduar, bankura, purulia, kharagpur, midnapore, bardhaman, hooghly, murshidabad, baharampur, haldia, ranaghat, kalyani, chandannagar) { city, locality ->
                selectedCity = city
                selectedLocality = locality
                registrationViewModel.city = if (city == other) customCity else city
                registrationViewModel.hometown = if (locality == other) customLocality else locality
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
            fetchLocation(fusedLocationClient, context, other, kolkata, howrah, durgapur, asansol, siliguri, darjeeling, malda, jalpaiguri, coochbehar, alipurduar, bankura, purulia, kharagpur, midnapore, bardhaman, hooghly, murshidabad, baharampur, haldia, ranaghat, kalyani, chandannagar) { city, locality ->
                selectedCity = city
                selectedLocality = locality
                registrationViewModel.city = if (city == other) customCity else city
                registrationViewModel.hometown = if (locality == other) customLocality else locality
                isLocating = false
            }
        }
    }

    // Validation
    val isCityOther = selectedCity == other
    val isLocalityOther = selectedLocality == other
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
                                        registrationViewModel.city = if (cityName == other) customCity else cityName
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
                            Text(stringResource(R.string.locate), color = Color.White)
                        }
                    }
                }
                if (selectedCity == other) {
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
                                    registrationViewModel.hometown = if (loc == other) customLocality else loc
                                    localityExpanded = false
                                }
                            )
                        }
                    }
                }
                if (selectedLocality == other) {
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
    other: String,
    kolkata: String, howrah: String, durgapur: String, asansol: String, siliguri: String, darjeeling: String, malda: String, jalpaiguri: String, coochbehar: String, alipurduar: String, bankura: String, purulia: String, kharagpur: String, midnapore: String, bardhaman: String, hooghly: String, murshidabad: String, baharampur: String, haldia: String, ranaghat: String, kalyani: String, chandannagar: String,
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
                    onLocationFound(other, other)
                }
                return@launch
            }


            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0]
                    val detectedCity = address.locality ?: address.subAdminArea ?: other
                    val detectedLocality = address.subLocality ?: other

                    val cities = context.resources.getStringArray(R.array.city_names).toList()
                    val matchedCity = cities.find { it.equals(detectedCity, ignoreCase = true) } ?: other
                    val localities = when (matchedCity) {
                        kolkata -> context.resources.getStringArray(R.array.localities_kolkata).toList()
                        howrah -> context.resources.getStringArray(R.array.localities_howrah).toList()
                        durgapur -> context.resources.getStringArray(R.array.localities_durgapur).toList()
                        asansol -> context.resources.getStringArray(R.array.localities_asansol).toList()
                        siliguri -> context.resources.getStringArray(R.array.localities_siliguri).toList()
                        darjeeling -> context.resources.getStringArray(R.array.localities_darjeeling).toList()
                        malda -> context.resources.getStringArray(R.array.localities_malda).toList()
                        jalpaiguri -> context.resources.getStringArray(R.array.localities_jalpaiguri).toList()
                        coochbehar -> context.resources.getStringArray(R.array.localities_cooch_behar).toList()
                        alipurduar -> context.resources.getStringArray(R.array.localities_alipurduar).toList()
                        bankura -> context.resources.getStringArray(R.array.localities_bankura).toList()
                        purulia -> context.resources.getStringArray(R.array.localities_purulia).toList()
                        kharagpur -> context.resources.getStringArray(R.array.localities_kharagpur).toList()
                        midnapore -> context.resources.getStringArray(R.array.localities_midnapore).toList()
                        bardhaman -> context.resources.getStringArray(R.array.localities_bardhaman).toList()
                        hooghly -> context.resources.getStringArray(R.array.localities_hooghly).toList()
                        murshidabad -> context.resources.getStringArray(R.array.localities_murshidabad).toList()
                        baharampur -> context.resources.getStringArray(R.array.localities_baharampur).toList()
                        haldia -> context.resources.getStringArray(R.array.localities_haldia).toList()
                        ranaghat -> context.resources.getStringArray(R.array.localities_ranaghat).toList()
                        kalyani -> context.resources.getStringArray(R.array.localities_kalyani).toList()
                        chandannagar -> context.resources.getStringArray(R.array.localities_chandannagar).toList()
                        other -> context.resources.getStringArray(R.array.localities_other).toList()
                        else -> emptyList()
                    }
                    val matchedLocality = localities.find { it.equals(detectedLocality, ignoreCase = true) } ?: other

                    withContext(Dispatchers.Main) {
                        onLocationFound(matchedCity, matchedLocality)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Unable to determine location", Toast.LENGTH_SHORT).show()
                        onLocationFound(other, other)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
                    onLocationFound(other, other)
                }
            }
        } catch (e: SecurityException) {
            Log.e("Location", "SecurityException: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Location access denied", Toast.LENGTH_SHORT).show()
                onLocationFound(other, other)
            }
        } catch (e: Exception) {
            Log.e("Location", "Error fetching location: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error fetching location", Toast.LENGTH_SHORT).show()
                onLocationFound(other, other)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterInterestsScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit
) {
    // Global interests (static list)
    val globalInterests = listOf(
        Interest(stringResource(R.string.interest_music), "🎵"),
        Interest(stringResource(R.string.interest_movies), "🎥"),
        Interest(stringResource(R.string.interest_sports), "⚽"),
        Interest(stringResource(R.string.interest_books), "📚"),
        Interest(stringResource(R.string.interest_travel), "✈️"),
        Interest(stringResource(R.string.interest_fitness), "💪"),
        Interest(stringResource(R.string.interest_art), "🎨"),
        Interest(stringResource(R.string.interest_gaming), "🎮"),
        Interest(stringResource(R.string.interest_photography), "📷"),
        Interest(stringResource(R.string.interest_cooking), "🍳"),
        Interest(stringResource(R.string.interest_dancing), "💃"),
        Interest(stringResource(R.string.interest_gardening), "🌱"),
        Interest(stringResource(R.string.interest_technology), "💻"),
        Interest(stringResource(R.string.interest_fashion), "👗"),
        Interest(stringResource(R.string.interest_volunteering), "🤝"),
        Interest(stringResource(R.string.interest_pets), "🐾")
    )

    // Locality-based interests for all localities (union of all values)
    val localityInterestsMap = mapOf(
        "Salt Lake" to listOf(
            Interest(stringResource(R.string.interest_cc_block_market), "🛒"),
            Interest(stringResource(R.string.interest_sector_v_it_hub), "💻")
        ),
        "New Town" to listOf(
            Interest(stringResource(R.string.interest_eco_park), "🌳"),
            Interest(stringResource(R.string.interest_city_centre_2), "🛍️")
        ),
        "Park Street"     to listOf(
            Interest(stringResource(R.string.interest_nightlife),"🌃"),
            Interest(stringResource(R.string.interest_park_street_cafes),"☕")
        ),
        "Dum Dum" to listOf(
            Interest(stringResource(R.string.interest_airport_area), "✈️"),
            Interest(stringResource(R.string.interest_local_market), "🛍️")
        ),
        "Behala" to listOf(
            Interest(stringResource(R.string.interest_old_market), "🏪"),
            Interest(stringResource(R.string.interest_local_eateries), "🍴"),
            ),
        // Hooghly district
        "Chandannagar"    to listOf( Interest(stringResource(R.string.interest_chandannagar_strand),"🌉"),
            Interest(stringResource(R.string.interest_french_heritage),"🏛️") ),
        "Serampore"       to listOf( Interest(stringResource(R.string.interest_riverside_ghats),"🚣"),
            Interest(stringResource(R.string.interest_heritage_walks),"🚶") ),

        // Howrah
        "Howrah"          to listOf( Interest(stringResource(R.string.interest_belur_math),"🕌"),
            Interest(stringResource(R.string.interest_avani_mall),"🛍️") ),

        // Industrial belt
        "Durgapur"        to listOf( Interest(stringResource(R.string.interest_city_centre_plaza),"🛍️"),
            Interest(stringResource(R.string.interest_steel_plant_tour),"🏭") ),
        "Asansol"         to listOf( Interest(stringResource(R.string.interest_burnpur_riverside),"🌅"),
            Interest(stringResource(R.string.interest_chittaranjan_park),"🌳") ),

        // North‑Bengal
        "Siliguri"        to listOf( Interest(stringResource(R.string.interest_hongkong_market),"🛍️"),
            Interest(stringResource(R.string.interest_mahananda_wls),"🐘") ),
        "Darjeeling"      to listOf( Interest(stringResource(R.string.interest_toy_train),"🚂"),
            Interest(stringResource(R.string.interest_tea_estate_walks),"🍃") ),
        "Jalpaiguri"      to listOf( Interest(stringResource(R.string.interest_gorumara_safari),"🦏"),
            Interest(stringResource(R.string.interest_rafting_teesta),"🚣") ),
        "Cooch Behar"     to listOf( Interest(stringResource(R.string.interest_rajbari_palace),"🏰"),
            Interest(stringResource(R.string.interest_sagar_dighi),"🦆") ),
        "Alipurduar"      to listOf( Interest(stringResource(R.string.interest_buxa_fort_trek),"🥾"),
            Interest(stringResource(R.string.interest_jayanti_picnic),"🏞️") ),

        // South‑West
        "Kharagpur"       to listOf( Interest(stringResource(R.string.interest_iit_campus_walk),"🎓"),
            Interest(stringResource(R.string.interest_gol_bazaar_food),"🍲") ),
        "Midnapore"       to listOf( Interest(stringResource(R.string.interest_vidyasagar_lake),"🌳"),
            Interest(stringResource(R.string.interest_khudiram_park),"🌺") ),
        "Haldia"          to listOf( Interest(stringResource(R.string.interest_river_cruise),"🚢"),
            Interest(stringResource(R.string.interest_marine_drive),"🌊") ),

        // Central WB
        "Bardhaman"       to listOf( Interest(stringResource(R.string.interest_curzon_gate_photo),"📸"),
            Interest(stringResource(R.string.interest_sitabhog_mihidana),"🍮") ),
        "Bankura"         to listOf( Interest(stringResource(R.string.interest_terracotta_art),"🏺"),
            Interest(stringResource(R.string.interest_susunia_trek),"🥾") ),
        "Purulia"         to listOf( Interest(stringResource(R.string.interest_ayodhya_hills),"⛰️"),
            Interest(stringResource(R.string.interest_chhau_dance),"🕺") ),

        // Nadia zone
        "Krishnanagar"    to listOf( Interest(stringResource(R.string.interest_clay_doll_lane),"🪆"),
            Interest(stringResource(R.string.interest_ghurni_artists),"🎭") ),
        "Kalyani"         to listOf( Interest(stringResource(R.string.interest_university_campus_walk),"🎓"),
            Interest(stringResource(R.string.interest_kalyani_lake),"🚣") ),
        "Ranaghat"        to listOf( Interest(stringResource(R.string.interest_boutique_sarees),"👗"),
            Interest(stringResource(R.string.interest_churni_riverbank),"🏞️") ),

        // Others
        "Malda"           to listOf( Interest(stringResource(R.string.interest_mango_festival),"🥭"),
            Interest(stringResource(R.string.interest_gour_ruins),"🏯") ),
        "Murshidabad"     to listOf( Interest(stringResource(R.string.interest_hazar_duari_museum),"🏰"),
            Interest(stringResource(R.string.interest_khusbagh_gardens),"🌳") ),
        "Baharampur"      to listOf( Interest(stringResource(R.string.interest_berhampore_silk),"🧣"),
            Interest(stringResource(R.string.interest_cossimbazar_rajbari),"🏛️") )
    )
    // Instead of showing only the interests corresponding to the selected hometown,
    // we now take the union of all locality interests.
    val localityInterests = localityInterestsMap.values.flatten()

    // Combine global and locality-based interests and remove duplicates (by name)
    val allInterests = (globalInterests + localityInterests).distinctBy { it.name }

    val maxInterests = 9
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

@Composable
fun UploadMediaComposable(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storageRef = FirebaseRefs.storage.reference

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
    val db = FirebaseRefs.db.reference
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
