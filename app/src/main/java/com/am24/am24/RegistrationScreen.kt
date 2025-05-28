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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
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
import com.firebase.geofire.GeoFire
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import com.google.firebase.auth.PhoneAuthCredential

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
                // grab your ViewModel so you can pass it in:
                val registrationViewModel: RegistrationViewModel = viewModel()

                RegistrationScreen(
                    onRegistrationComplete = {
                        // ① first save the whole profile under /users/{uid}
                        lifecycleScope.launch {
                            saveProfileToFirebase(
                                registrationViewModel,
                                /* other-string = */ getString(R.string.college_other)
                            ) {
                                // ② only once that’s done, mirror under /publicUsers/{username}
                                val auth = FirebaseAuth.getInstance()
                                val db   = FirebaseRefs.db.reference
                                auth.currentUser?.uid?.let { uid ->
                                    db.child("users").child(uid).child("username").get()
                                        .addOnSuccessListener { snap ->
                                            val username = snap.getValue(String::class.java) ?: return@addOnSuccessListener
                                            val signInMethod = if (isGoogleSignUp) "google" else "emailPassword"
                                            db.child("publicUsers")
                                                .child(username)
                                                .setValue(mapOf(
                                                    "email"                to auth.currentUser?.email,
                                                    "signInMethod"         to signInMethod,
                                                    "registrationFinished" to true
                                                ))
                                                .addOnSuccessListener {
                                                    startActivity(Intent(this@RegistrationActivity, MainActivity::class.java))
                                                    finish()
                                                }
                                        }
                                }
                            }
                        }
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
    var phoneNumber by mutableStateOf("")   // <── add this line
    // RegistrationViewModel
    var country       by mutableStateOf("")
    var customCountry by mutableStateOf("")
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
    var customEducationLevel by mutableStateOf("")  // ← NEW

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

    /** Convert cm → (feet, inches) */
    fun cmToFeetInches(cm: Int): Pair<Int,Int> {
        val totalInches = cm / 2.54
        val feet = (totalInches / 12).toInt()
        val inches = ((totalInches - feet * 12).roundToInt())
        return feet to inches
    }

    /** Convert (feet, inches) → cm */
    fun feetInchesToCm(feet: Int, inches: Int): Int {
        return ((feet * 12 + inches) * 2.54).roundToInt()
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
        when {
            // BACK from Step 2 → Step 1: delete half-baked account, clear email/password, go to step 1
            currentStep == 2 -> {
                cleanupIncompleteUser(
                    FirebaseAuth.getInstance(),
                    FirebaseDatabase.getInstance(),
                    FirebaseStorage.getInstance()
                )
                registrationViewModel.email = ""
                registrationViewModel.password = ""
                currentStep = 1
            }
            // any other back (steps > 2) just go back a step
            currentStep > 2 -> {
                currentStep -= 1
            }
            // BACK from Step 1 → exit: also delete incomplete auth right here, then finish
            else -> {
                cleanupIncompleteUser(
                    FirebaseAuth.getInstance(),
                    FirebaseDatabase.getInstance(),
                    FirebaseStorage.getInstance()
                )
                (context as? ComponentActivity)?.finish()
            }
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

private fun tryRegister(
    typedEmail: String,
    typedPassword: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db   = FirebaseDatabase
        .getInstance("https://kupidxdefault.asia-southeast1.firebasedatabase.app/")
        .getReference()
    val storage = FirebaseStorage.getInstance()

    // 1) See if an email/password account already exists for this email
    auth.fetchSignInMethodsForEmail(typedEmail)
        .addOnSuccessListener { result ->
            val methods = result.signInMethods ?: emptyList()
            if (methods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD)) {
                // → there *is* an existing E/P account: sign in to inspect it
                auth.signInWithEmailAndPassword(typedEmail, typedPassword)
                    .addOnSuccessListener {
                        val uid = auth.currentUser!!.uid
                        // 2) Check for a username in the DB
                        db.child(uid).child("username").get()
                            .addOnSuccessListener { snap ->
                                if (!snap.exists()) {
                                    // 🗑️  Incomplete!  Wipe it:
                                    cleanupIncompleteUser(auth, FirebaseDatabase.getInstance("https://kupidxdefault.asia-southeast1.firebasedatabase.app/"),
                                        storage)
                                    // after it’s deleted, create the new one:
                                    createFreshAccount(typedEmail, typedPassword, onSuccess, onError)
                                } else {
                                    onError("An account with that email is already fully registered.")
                                }
                            }
                            .addOnFailureListener { e ->
                                onError("Error checking existing profile: ${e.message}")
                            }
                    }
                    .addOnFailureListener {
                        onError("Wrong password for existing account.")
                    }
            } else {
                // → no existing E/P account: just make a brand-new one
                createFreshAccount(typedEmail, typedPassword, onSuccess, onError)
            }
        }
        .addOnFailureListener { e ->
            onError("Error checking sign-in methods: ${e.message}")
        }
}



/**
 * Wipes out any half-baked Firebase user data (RTDB, GeoFire, Storage)
 * and then deletes the Auth user.
 */
fun cleanupIncompleteUser(
    auth: FirebaseAuth,
    db: FirebaseDatabase,
    storage: FirebaseStorage
) {
    val user = auth.currentUser ?: return
    val uid = user.uid

    // 1) Remove any partial Realtime-DB node:
    db.reference
        .child("users")
        .child(uid)
        .removeValue()
        .addOnSuccessListener {
            Log.d("cleanup", "Realtime-DB node removed for $uid")
        }
        .addOnFailureListener { e ->
            Log.e("cleanup", "Failed to remove RTDB node: ${e.message}")
        }

    // 2) Remove the GeoFire location for this user:
    val geoRef = db.reference.child("geofire")
    GeoFire(geoRef).removeLocation(uid) { key, error ->
        if (error != null) {
            Log.e("cleanup", "GeoFire removeLocation error for $key: ${error.message}")
        } else {
            Log.d("cleanup", "GeoFire location removed for $key")
        }
    }

    // 3) Remove storage blobs under /users/{uid}/
    val userStorage = storage.reference.child("users").child(uid)
    userStorage.child("profile_pic.jpg").delete()
        .addOnSuccessListener { Log.d("cleanup", "Deleted profile_pic.jpg for $uid") }
        .addOnFailureListener { e ->
            Log.e("cleanup", "Failed to delete profile_pic.jpg: ${e.message}")
        }
    userStorage.child("voice_note.mp3").delete()
        .addOnSuccessListener { Log.d("cleanup", "Deleted voice_note.mp3 for $uid") }
        .addOnFailureListener { e ->
            Log.e("cleanup", "Failed to delete voice_note.mp3: ${e.message}")
        }
    userStorage.child("photos")
        .listAll()
        .addOnSuccessListener { listResult ->
            listResult.items.forEach { fileRef ->
                fileRef.delete()
                    .addOnSuccessListener {
                        Log.d("cleanup", "Deleted ${fileRef.name} for $uid")
                    }
                    .addOnFailureListener { e ->
                        Log.e("cleanup", "Failed to delete ${fileRef.name}: ${e.message}")
                    }
            }
        }
        .addOnFailureListener { e ->
            Log.e("cleanup", "Failed to list photos for deletion: ${e.message}")
        }

    // 4) Finally delete the Auth user itself:
    user.delete()
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("cleanup", "Auth user $uid deleted successfully.")
            } else {
                Log.e("cleanup", "Failed to delete user auth: ${task.exception?.message}")
            }
        }
}

private fun createFreshAccount(
    email: String,
    password: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    FirebaseAuth.getInstance()
        .createUserWithEmailAndPassword(email, password)
        .addOnSuccessListener {
            it.user?.sendEmailVerification()
            onSuccess()
        }
        .addOnFailureListener { e ->
            onError("Registration failed: ${e.message}")
        }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnterPersonalDetailsScreen(
    viewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val lookingForOptions = listOf(
        stringResource(R.string.looking_for_not_selected),
        stringResource(R.string.looking_for_romance),
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
    // ── Social Causes ──
    val allCauses = stringArrayResource(R.array.social_causes_list).toList()
    val selectedCauses = remember { mutableStateListOf<String>().apply { addAll(viewModel.socialCauses) } }
    val maxSelections = 5

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
                        Text(
                            stringResource(R.string.social_causes),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allCauses.forEach { cause ->
                                val isSelected = selectedCauses.contains(cause)
                                val canSelectMore = selectedCauses.size < maxSelections

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) {
                                            selectedCauses.remove(cause)
                                        } else if (canSelectMore) {
                                            selectedCauses.add(cause)
                                        }
                                        // ✅ mutate the SnapshotStateList directly:
                                        viewModel.socialCauses.apply {
                                            clear()
                                            addAll(selectedCauses)
                                        }
                                              },
                                    enabled = isSelected || canSelectMore,
                                    label = { Text(cause) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFF6F00),
                                        selectedLabelColor = Color.White,
                                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                                        disabledLabelColor = Color.LightGray
                                    )
                                )
                            }
                        }

                        if (selectedCauses.size > maxSelections) {
                            Text(
                                text = stringResource(
                                    R.string.max_social_causes_error,
                                    maxSelections
                                ),
                                color = Color.Red,
                                modifier = Modifier.padding(top = 8.dp)
                            )
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    .background(Color(0xFF1A1A1A))
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

                // 1. Exercise Frequency
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_exercise),
                        value = registrationViewModel.lifestyle.exercise_frequency,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(exercise_frequency = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.inactive),
                            stringResource(R.string.rarely_active),
                            stringResource(R.string.moderately_active),
                            stringResource(R.string.active),
                            stringResource(R.string.very_active)
                        )
                    )
                }

                // 2. Adventurousness
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_adventurousness),
                        value = registrationViewModel.lifestyle.adventurousness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(adventurousness = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.cautious),
                            stringResource(R.string.slightly_adventurous),
                            stringResource(R.string.moderately_adventurous),
                            stringResource(R.string.adventurous),
                            stringResource(R.string.thrill_seeker)
                        )
                    )
                }

                // 3. Intellectual Curiosity
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_intellectual_curiosity),
                        value = registrationViewModel.lifestyle.intellectual_curiosity,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(intellectual_curiosity = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.casual_thinker),
                            stringResource(R.string.inquisitive),
                            stringResource(R.string.knowledge_seeker),
                            stringResource(R.string.intellectual),
                            stringResource(R.string.philosopher)
                        )
                    )
                }

                // 4. Smoking Habit
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_smoking),
                        value = registrationViewModel.lifestyle.smoking_habit,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(smoking_habit = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.non_smoker),
                            stringResource(R.string.rare_smoker),
                            stringResource(R.string.social_smoker),
                            stringResource(R.string.frequent_smoker),
                            stringResource(R.string.heavy_smoker)
                        )
                    )
                }

                // 5. Drinking Habit
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_drinking),
                        value = registrationViewModel.lifestyle.drinking_habit,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(drinking_habit = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.non_drinker),
                            stringResource(R.string.rare_drinker),
                            stringResource(R.string.social_drinker),
                            stringResource(R.string.frequent_drinker),
                            stringResource(R.string.heavy_drinker)
                        )
                    )
                }

                // 6. Work–Life Balance
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_work_life_balance),
                        value = registrationViewModel.lifestyle.work_life_balance,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(work_life_balance = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.workaholic),
                            stringResource(R.string.more_work_oriented),
                            stringResource(R.string.balanced),
                            stringResource(R.string.more_life_oriented),
                            stringResource(R.string.relaxed)
                        )
                    )
                }

                // 7. Sleep Pattern
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_sleep),
                        value = registrationViewModel.lifestyle.sleep_pattern,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(sleep_pattern = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.early_riser),
                            stringResource(R.string.morning_person),
                            stringResource(R.string.balanced),
                            stringResource(R.string.night_owl),
                            stringResource(R.string.late_night_enthusiast)
                        )
                    )
                }

                // 8. Creative Expression
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_creative_expression),
                        value = registrationViewModel.lifestyle.creative_expression,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(creative_expression = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.not_creative),
                            stringResource(R.string.somewhat_creative),
                            stringResource(R.string.creative),
                            stringResource(R.string.very_creative),
                            stringResource(R.string.artistic_genius)
                        )
                    )
                }

                // 9. Physical Fitness
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_physical_fitness),
                        value = registrationViewModel.lifestyle.physical_fitness,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(physical_fitness = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.sedentary),
                            stringResource(R.string.somewhat_fit),
                            stringResource(R.string.fit),
                            stringResource(R.string.athletic),
                            stringResource(R.string.peak_fitness)
                        )
                    )
                }

                // 10. Professional Ambition
                item {
                    LifestyleSlider(
                        label = stringResource(R.string.lifestyle_professional_ambition),
                        value = registrationViewModel.lifestyle.professional_ambition,
                        valueRangeStart = 0,
                        valueRangeEnd = 4,
                        onValueChange = {
                            registrationViewModel.lifestyle = registrationViewModel.lifestyle.copy(professional_ambition = it)
                        },
                        nouns = listOf(
                            stringResource(R.string.relaxed),
                            stringResource(R.string.occasionally_driven),
                            stringResource(R.string.balanced),
                            stringResource(R.string.ambitious),
                            stringResource(R.string.high_ambitious)
                        )
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
    val educationLevels = listOf(stringResource(R.string.no_education_label),
        stringResource(R.string.high_school_label), stringResource(R.string.college_label), stringResource(R.string.post_graduation_label))
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
                        onCustomInputChange = { registrationViewModel.customHighSchool = it!! }
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
                        onCustomInputChange = { registrationViewModel.customCollege = it!! }
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
                        onCustomInputChange = { registrationViewModel.customPostGraduation = it!! }
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
    customInput: String? = null,
    onCustomInputChange: (String?) -> Unit = {}
) {
    val other = stringResource(R.string.college_other)
    // recompute when selectedOption changes
    var showCustomInput by remember(selectedOption) { mutableStateOf(selectedOption == other) }
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Text(text = title, color = Color.White, fontSize = 9.sp)

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
                text = if (showCustomInput) customInput.orEmpty()
                else selectedOption.ifEmpty { stringResource(R.string.select_or_type) },
                fontSize = 9.sp,
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
                onValueChange = {
                    searchText = it
                    showCustomInput = false
                },
                label = {
                    Text(stringResource(R.string.search_label), fontSize = 9.sp, color = Color.White)
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedLabelColor = Color(0xFFFF4500),
                    focusedBorderColor = Color(0xFFFF4500),
                    cursorColor       = Color(0xFFFF4500),
                    focusedTextColor  = Color.White
                )
            )

            Spacer(Modifier.height(8.dp))

            options
                .filter { it.contains(searchText, ignoreCase = true) }
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 9.sp, color = Color.White) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                            showCustomInput = (option == other)
                        }
                    )
                }
        }

        if (showCustomInput) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customInput.orEmpty(),
                onValueChange = { newText ->
                    onCustomInputChange(newText)
                },
                label = {
                    Text(
                        stringResource(R.string.enter_custom_value),
                        fontSize = 9.sp,
                        color = Color.White
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedLabelColor = Color(0xFFFF4500),
                    focusedBorderColor = Color(0xFFFF4500),
                    cursorColor       = Color(0xFFFF4500),
                    focusedTextColor  = Color.White
                )
            )
        }
    }
}

// ─── replace your old EnterEmailAndPasswordScreen with this ───
enum class AuthTab { PHONE, EMAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterEmailAndPasswordScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    /* ───────────────────────── TAB STATE ───────────────────────── */
    var selectedTab by remember { mutableStateOf(AuthTab.PHONE) }   // ← default = Phone

    /* ─────────────── EMAIL/PASSWORD local state ────────────────── */
    var email           by remember { mutableStateOf(TextFieldValue(registrationViewModel.email)) }
    var password        by remember { mutableStateOf(TextFieldValue(registrationViewModel.password)) }
    var confirmPassword by remember { mutableStateOf(TextFieldValue("")) }
    var passwordError   by remember { mutableStateOf(false) }

    /* ─────────────── PHONE/OTP local state ─────────────────────── */
    var phoneNumber        by remember { mutableStateOf(TextFieldValue("")) }
    var resendToken        by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) } // NEW
    var otpCode            by remember { mutableStateOf(TextFieldValue("")) }
    var verificationId     by remember { mutableStateOf<String?>(null) }
    var otpSent            by remember { mutableStateOf(false) }
    var cooldownSeconds    by remember { mutableStateOf(0) }           // NEW
    var isSubmitting       by remember { mutableStateOf(false) }

    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth  = FirebaseAuth.getInstance()

    /* ───────────────── Firebase helpers ────────────────────────── */
    fun startPhoneVerification(number: String) {
        Log.d("OTP", "Starting verification for: $number")
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(ctx as ComponentActivity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(cred: PhoneAuthCredential) {
                    Log.d("OTP", "Verification completed automatically")
                    auth.signInWithCredential(cred).addOnSuccessListener { onNext() }
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e("OTP", "Verification failed", e)
                    Toast.makeText(ctx, "OTP failed: ${e.message}", Toast.LENGTH_LONG).show()
                    isSubmitting = false
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    Log.d("OTP", "Code sent successfully, verificationId: $id")
                    verificationId = id
                    resendToken = token
                    otpSent = true
                    cooldownSeconds = 60
                    isSubmitting = false
                }
            })
            .build()
        try {
            PhoneAuthProvider.verifyPhoneNumber(options)
            Log.d("OTP", "verifyPhoneNumber called successfully")
        } catch (e: Exception) {
            Log.e("OTP", "Error calling verifyPhoneNumber", e)
            Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            isSubmitting = false
        }
    }
    /* Count-down after every (re)send */
    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            kotlinx.coroutines.delay(1_000)
            cooldownSeconds--
        }
    }

    fun verifyOtpAndContinue(code: String) {
        val id = verificationId ?: return
        val cred = PhoneAuthProvider.getCredential(id, code)  // returns PhoneAuthCredential
        auth.signInWithCredential(cred)
            .addOnSuccessListener {    registrationViewModel.phoneNumber =
                FirebaseAuth.getInstance().currentUser?.phoneNumber ?: registrationViewModel.phoneNumber
                onNext() }
            .addOnFailureListener { e ->
                Toast.makeText(ctx, "OTP error: ${e.message}", Toast.LENGTH_LONG).show()
                isSubmitting = false
            }
    }
    fun resendOtp(number: String) {
        val token = resendToken ?: return                                 // no token yet
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(ctx as ComponentActivity)
            .setForceResendingToken(token)                                // ★ key line
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(cred: PhoneAuthCredential) {   // ← fixed type
                    auth.signInWithCredential(cred).addOnSuccessListener { onNext() }
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(ctx, "OTP failed: ${e.message}", Toast.LENGTH_LONG).show()
                    isSubmitting = false
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    resendToken    = token                            // ← keep it
                    otpSent = true
                    cooldownSeconds = 60                              // ← 60-s timer
                    isSubmitting = false
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        cooldownSeconds = 60                                              // restart timer
    }

    /* ───────────────────────  UI  ──────────────────────────────── */
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
                .padding(pad)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            /* ─── TAB STRIP ─────────────────────────────────────── */
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFF262626),
                contentColor   = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Color(0xFFFF6000)
                    )
                }
            ) {
                AuthTab.values().forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick  = { selectedTab = tab },
                        text     = { Text(if (tab == AuthTab.PHONE) "Phone" else "Email") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            when (selectedTab) {
                /* ──────────────────── PHONE TAB ─────────────────── */
                AuthTab.PHONE -> {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { tfValue ->
                            phoneNumber = tfValue
                            registrationViewModel.phoneNumber = tfValue.text
                        },
                        label = { Text("Mobile (+91…)", color = Color.White) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(16.dp))
                    if (otpSent) {
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            label = { Text("OTP", color = Color.White) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors()
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Button(
                        enabled = !isSubmitting && (if (otpSent) otpCode.text.length >= 6 else phoneNumber.text.length >= 10),
                        onClick = {
                            isSubmitting = true
                            if (!otpSent) {
                                val number = formatPhoneNumber(phoneNumber.text)
                                Log.d("OTP", "Formatted number: $number")
                                startPhoneVerification(number)
                            } else {
                                verifyOtpAndContinue(otpCode.text.trim())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000)),
                        shape = CircleShape
                    ) {
                        Text(if (otpSent) "Verify OTP" else "Send OTP", color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (otpSent) {
                        OutlinedButton(
                            onClick = {
                                val number = formatPhoneNumber(phoneNumber.text)
                                resendOtp(number)
                            },
                            enabled = cooldownSeconds == 0 && !isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFFF6000)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (cooldownSeconds == 0) Color(0xFFFF6000) else Color.Gray
                            )
                        ) {
                            Text(
                                text = if (cooldownSeconds == 0) "Resend OTP" else "Resend in ${cooldownSeconds}s"
                            )
                        }
                    }
                }
                /* ─────────────────── EMAIL TAB ──────────────────── */
                AuthTab.EMAIL -> {
                    OutlinedTextField(
                        value = email, onValueChange = {
                            email = it; registrationViewModel.email = it.text
                        },
                        label = { Text("Email", color = Color.White) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password, onValueChange = {
                            password = it; registrationViewModel.password = it.text
                        },
                        label = { Text("Password", color = Color.White) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it },
                        label = { Text("Confirm password", color = Color.White) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    if (passwordError) {
                        Text("Passwords don’t match", color = Color.Red)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        enabled = !isSubmitting,
                        onClick = {
                            val mail = email.text.trim()
                            val pwd  = password.text.trim()
                            val pwd2 = confirmPassword.text.trim()

                            if (mail.isEmpty() || pwd.isEmpty()) return@Button
                            if (pwd != pwd2) { passwordError = true; return@Button }
                            passwordError = false
                            isSubmitting  = true

                            tryRegister(
                                mail, pwd,
                                onSuccess = { isSubmitting = false; onNext() },
                                onError   = { msg ->
                                    isSubmitting = false
                                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000)),
                        shape  = CircleShape
                    ) {
                        Text("Next", color = Color.White)
                    }
                }
            }
        }
    }
}

fun formatPhoneNumber(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("+")) trimmed else "+91$trimmed"
}
/* ---------- tiny helper for terse field-colors ---------- */
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFFF6000),
    unfocusedBorderColor = Color.White,
    cursorColor = Color.White,
    focusedLabelColor = Color(0xFFFF6000),
    unfocusedLabelColor = Color.White,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

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
    val isNextEnabled = registrationViewModel.gender.isNotEmpty()

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
                    .verticalScroll(rememberScrollState())
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
                DropdownWithSearch(
                    title = stringResource(R.string.select_gender),
                    options = genderOptions,
                    selectedOption = registrationViewModel.gender,
                    onOptionSelected = { registrationViewModel.gender = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Religion Dropdown
                DropdownWithSearch(
                    title = stringResource(R.string.select_religion),
                    options = religionOptions,
                    selectedOption = registrationViewModel.religion,
                    onOptionSelected = { registrationViewModel.religion = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Community Dropdown
                DropdownWithSearch(
                    title = stringResource(R.string.select_community),
                    options = communityOptions,
                    selectedOption = registrationViewModel.community,
                    onOptionSelected = { registrationViewModel.community = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ---- Caste ----
                val other = stringResource(R.string.college_other)
                SearchableDropdownWithCustomOption(
                    title = stringResource(R.string.caste_title),
                    options = listOf(
                        stringResource(R.string.caste_kulin_brahmin),
                        stringResource(R.string.caste_non_kulin_brahmin),
                        stringResource(R.string.caste_kulin_kayastha),
                        stringResource(R.string.caste_non_kulin_kayastha),
                        stringResource(R.string.caste_kshatriya),
                        stringResource(R.string.caste_baidya),
                        stringResource(R.string.caste_mahishya),
                        stringResource(R.string.caste_sadgop),
                        stringResource(R.string.caste_vaishya),
                        stringResource(R.string.caste_obc),
                        stringResource(R.string.caste_scheduled_caste),
                        stringResource(R.string.caste_scheduled_tribe),
                        stringResource(R.string.caste_rajbonshi),
                        stringResource(R.string.caste_general),
                        stringResource(R.string.caste_other)
                    ),
                    selectedOption = registrationViewModel.caste,
                    onOptionSelected = { selectedOption ->
                        if (selectedOption != other) {
                            registrationViewModel.caste = selectedOption
                        }
                    },
                    customInput = registrationViewModel.caste,
                    onCustomInputChange = { registrationViewModel.caste = it!! }
                )

                Spacer(modifier = Modifier.height(16.dp))

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

@Composable
fun DropdownWithSearch(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    customInput: String? = null,
    onCustomInputChange: (String?) -> Unit = {},
    onDropdownClicked: () -> Unit = {}
) {
    SearchableDropdownWithCustomOption(
        title               = title,
        options             = options,
        selectedOption      = selectedOption,
        onOptionSelected    = onOptionSelected,
        customInput         = customInput,
        onCustomInputChange = onCustomInputChange
    )
    onDropdownClicked()  // preserve the side-effect
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterUsernameScreen(
    registrationViewModel: RegistrationViewModel,
    onRegistrationComplete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val auth    = FirebaseAuth.getInstance()
    val db      = FirebaseDatabase
        .getInstance("https://kupidxdefault.asia-southeast1.firebasedatabase.app/")
        .getReference()

    var usernameTf by remember { mutableStateOf(TextFieldValue(registrationViewModel.username)) }
    var isValid    by remember { mutableStateOf(true) }
    var errorMsg   by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        containerColor = Color(0xFF1A1A1A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = usernameTf,
                onValueChange = {
                    usernameTf = it
                    isValid    = true
                },
                label = { Text("Username", color = Color.White) },
                singleLine = true,
                isError    = !isValid,
                supportingText = {
                    if (!isValid) Text(errorMsg, color = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color(0xFFFF6000),
                    unfocusedBorderColor = Color.White,
                    cursorColor          = Color.White,
                    focusedLabelColor    = Color(0xFFFF6000),
                    unfocusedLabelColor  = Color.White
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val raw = usernameTf.text.trim()
                    if (raw.isEmpty()) {
                        isValid  = false
                        errorMsg = "Username cannot be empty."
                        return@Button
                    }

                    // normalize for lookup
                    val key = raw.lowercase(Locale.getDefault())
                    val uid = auth.currentUser?.uid ?: run {
                        Toast.makeText(context, "No signed-in user", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    // 1) check availability
                    db.child("usernames").child(key).get()
                        .addOnSuccessListener { snap ->
                            if (snap.exists()) {
                                isValid  = false
                                errorMsg = "That username is taken."
                            } else {
                                // 2) reserve under /usernames/{key}
                                db.child("usernames").child(key).setValue(uid)
                                    .addOnSuccessListener {
                                        // 3) write into /users/{uid}/username
                                        db.child("users").child(uid)
                                            .child("username")
                                            .setValue(raw)
                                            .addOnSuccessListener {
                                                registrationViewModel.username = raw
                                                // 4) call back to finish registration
                                                onRegistrationComplete()
                                            }
                                    }
                                    .addOnFailureListener {
                                        isValid  = false
                                        errorMsg = "Failed to reserve username: ${it.message}"
                                    }
                            }
                        }
                        .addOnFailureListener {
                            isValid  = false
                            errorMsg = "Error checking username: ${it.message}"
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000)),
                shape = CircleShape
            ) {
                Text("Finish", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

suspend fun saveProfileToFirebase(
    registrationViewModel: RegistrationViewModel,
    other: String,
    onRegistrationComplete: () -> Unit
) {
    try {
        val database = FirebaseRefs.db.reference
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val finalHeightCm = if (registrationViewModel.isHeightInFeet) {
            registrationViewModel.feetInchesToCm(
                registrationViewModel.height2.getOrNull(0) ?: 0,
                registrationViewModel.height2.getOrNull(1) ?: 0
            )
        } else {
            registrationViewModel.height
        }

        val profile = Profile(
            phoneNumber = registrationViewModel.phoneNumber          // <── use VM first
                .ifBlank { FirebaseAuth.getInstance().currentUser?.phoneNumber },
            userId = userId,
            country       = if (registrationViewModel.country == other) registrationViewModel.customCountry else registrationViewModel.country,
            customCountry = registrationViewModel.customCountry,
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
            height = finalHeightCm,
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

suspend fun compressImage(
    context: Context,
    uri: Uri,
    maxWidth: Int = 1080,      // down-scale if wider than this
    quality: Int = 75          // JPEG quality 0‒100
): ByteArray = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri) ?: error("No stream")
    val original = BitmapFactory.decodeStream(input)
    input.close()

    // scale if needed
    val ratio = maxWidth.toFloat() / original.width.toFloat()
    val scaled = if (ratio < 1f) {
        Bitmap.createScaledBitmap(
            original,
            (original.width * ratio).toInt(),
            (original.height * ratio).toInt(),
            true
        )
    } else original

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    out.toByteArray()
}

fun uploadProfilePicToFirebase(
    context: Context,
    storageRef: StorageReference,
    uri: Uri,
    registrationViewModel: RegistrationViewModel
) {
    (context as? ComponentActivity)?.lifecycleScope?.launch {
        val jpegBytes = compressImage(context, uri)           // ← compress first
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
        val ref = storageRef.child("users/$userId/profile_pic.jpg")

        ref.putBytes(jpegBytes)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    registrationViewModel.profilePicUrl = downloadUri.toString()
                    Log.d("UploadMedia", "Profile picture uploaded: $downloadUri")
                }
            }
            .addOnFailureListener { e ->
                Log.e("UploadMedia", "Profile-pic upload failed: ${e.message}")
            }
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
    val maleOption = stringResource(R.string.male_option)
    val femaleOption = stringResource(R.string.female_option)
    val interestedOptions = listOf(maleOption, femaleOption)

    var heightText by remember { mutableStateOf(registrationViewModel.height.toString()) }
    var feetText   by remember {
        mutableStateOf(registrationViewModel.height2.getOrNull(0)?.toString() ?: "")
    }
    var inchText   by remember {
        mutableStateOf(registrationViewModel.height2.getOrNull(1)?.toString() ?: "")
    }

    // State to determine if the "Next" button can be enabled
    val canProceed = registrationViewModel.name.isNotEmpty() &&
            registrationViewModel.height > 0 &&
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
                        // ---- Full Name ----
                        TextFieldWithLabel(
                            label = stringResource(R.string.full_name_label),
                            value = registrationViewModel.name,
                            onValueChange = { registrationViewModel.name = it }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // ---- Height ----
                        Text(
                            text = stringResource(R.string.height_label),
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = registrationViewModel.isHeightInFeet,
                                onCheckedChange = { useFeet ->
                                    // update the unit
                                    registrationViewModel.isHeightInFeet = useFeet

                                    if (useFeet) {
                                        // cm → ft/in
                                        val (f, i) = registrationViewModel.cmToFeetInches(registrationViewModel.height)
                                        feetText = f.toString()
                                        inchText = i.toString()
                                    } else {
                                        // ft/in → cm
                                        val f = feetText.toIntOrNull() ?: 0
                                        val i = inchText.toIntOrNull() ?: 0
                                        heightText = registrationViewModel.feetInchesToCm(f, i).toString()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor   = Color(0xFFFF6000),
                                    uncheckedThumbColor = Color.White
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
                            // make sure the Row itself fills the width:
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Feet field
                                OutlinedTextField(
                                    value = feetText,
                                    onValueChange = { newFeet ->
                                        feetText = newFeet
                                        registrationViewModel.height2 = listOf(
                                            newFeet.toIntOrNull() ?: 0,
                                            registrationViewModel.height2.getOrNull(1) ?: 0
                                        )
                                    },
                                    label = { Text(stringResource(R.string.feet_label), color = Color.White) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(56.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = Color(0xFFFF6000),
                                        unfocusedBorderColor = Color.White,
                                        cursorColor = Color.White,
                                        focusedLabelColor = Color(0xFFFF6000),
                                        unfocusedLabelColor = Color.White
                                    )
                                )

                                // Inches field
                                OutlinedTextField(
                                    value = inchText,
                                    onValueChange = { newInch ->
                                        inchText = newInch
                                        registrationViewModel.height2 = listOf(
                                            registrationViewModel.height2.getOrNull(0) ?: 0,
                                            newInch.toIntOrNull() ?: 0
                                        )
                                    },
                                    label = { Text(stringResource(R.string.inches_label), color = Color.White) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(56.dp),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = Color(0xFFFF6000),
                                        unfocusedBorderColor = Color.White,
                                        cursorColor = Color.White,
                                        focusedLabelColor = Color(0xFFFF6000),
                                        unfocusedLabelColor = Color.White
                                    )
                                )
                            }
                        } else {
                            // Centimeters field
                            TextFieldWithLabel(
                                label = stringResource(R.string.height_cm_label),
                                value = heightText,
                                onValueChange = { newCm ->
                                    heightText = newCm
                                    // only parse when we have a number—otherwise leave the last valid
                                    registrationViewModel.height = newCm.toIntOrNull()
                                        ?: registrationViewModel.height
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ---- Interested In (improved) ----
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF6000)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.interested_in_header),
                                style = MaterialTheme.typography.titleMedium
                                    .copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            interestedOptions.forEach { option ->
                                val isSelected = registrationViewModel.interestedIn.contains(option)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) registrationViewModel.interestedIn.remove(option)
                                        else registrationViewModel.interestedIn.add(option)
                                    },
                                    label = { Text(option) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (option) {
                                                maleOption -> Icons.Default.Male
                                                else            -> Icons.Default.Female
                                            },
                                            contentDescription = null
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFF6000),
                                        selectedLabelColor     = Color.White,
                                        containerColor         = Color(0xFF1A1A1A),
                                        labelColor             = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        // ————————————————————————————————————————————————————

                        Spacer(modifier = Modifier.height(24.dp))

                        // ---- Next Button ----
                        Button(
                            onClick = { if (canProceed) onNext() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canProceed) Color(0xFFFF6000) else Color.DarkGray
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
    val countries = remember { resources.getStringArray(R.array.country_names).toList() }
    var selectedCountry  by remember { mutableStateOf(countries.first()) } // default “Other”
    var customCountry    by remember { mutableStateOf(registrationViewModel.customCountry) }

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

    // City Selection
    val cities = remember { resources.getStringArray(R.array.city_names).toList() }
    var selectedCity by remember { mutableStateOf(cities.firstOrNull() ?: "") }
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
    var customLocality by remember { mutableStateOf(registrationViewModel.customHometown) }

    // Location Permission Handling
    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted.values.all { it }
        if (ok) {
            isLocating = true
            fetchLocation(
                fusedLocationClient, context, countries, other,
                /* city constants */ kolkata, howrah, durgapur, asansol, siliguri, darjeeling, malda,
                jalpaiguri, coochbehar, alipurduar, bankura, purulia, kharagpur, midnapore,
                bardhaman, hooghly, murshidabad, baharampur, haldia, ranaghat, kalyani, chandannagar
            ) { country, city, locality ->
                selectedCountry  = country
                selectedCity     = city
                selectedLocality = locality

                registrationViewModel.country  = if (country  == other) customCountry  else country
                registrationViewModel.city     = if (city     == other) customCity     else city
                registrationViewModel.hometown = if (locality == other) customLocality else locality
                isLocating = false
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    /* auto‑locate on first entry */
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            isLocating = true
            fetchLocation(
                fusedLocationClient, context, countries, other,
                kolkata, howrah, durgapur, asansol, siliguri, darjeeling, malda,
                jalpaiguri, coochbehar, alipurduar, bankura, purulia, kharagpur, midnapore,
                bardhaman, hooghly, murshidabad, baharampur, haldia, ranaghat, kalyani, chandannagar
            ) { country, city, locality ->
                selectedCountry  = country
                selectedCity     = city
                selectedLocality = locality

                registrationViewModel.country  = if (country  == other) customCountry  else country
                registrationViewModel.city     = if (city     == other) customCity     else city
                registrationViewModel.hometown = if (locality == other) customLocality else locality
                isLocating = false
            }
        }
    }


    // Validation
    val isCountryOther  = selectedCountry == other
    val isCityOther     = selectedCity    == other
    val isLocalityOther = selectedLocality== other

    val isNextEnabled = registrationViewModel.dob.isNotBlank() &&
            ((isCountryOther  && customCountry .isNotBlank()) || (!isCountryOther  && selectedCountry .isNotBlank())) &&
            ((isCityOther     && customCity    .isNotBlank()) || (!isCityOther     && selectedCity    .isNotBlank())) &&
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
                        DropdownWithSearch(
                            title          = stringResource(R.string.select_day_label),
                            options        = dayRange.map { it.toString() },
                            selectedOption = selectedDay.toString(),
                            onOptionSelected = { sel ->
                                sel.toIntOrNull()?.let {
                                    selectedDay = it
                                    updateDob()
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownWithSearch(
                            title           = stringResource(R.string.select_month_label),
                            options         = monthNames,
                            selectedOption  = monthNames[selectedMonthIndex],
                            onOptionSelected = { sel ->
                                monthNames.indexOf(sel).takeIf { it >= 0 }?.let { idx ->
                                    selectedMonthIndex = idx
                                    updateDob()
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownWithSearch(
                            title           = stringResource(R.string.select_year_label),
                            options         = yearRange,
                            selectedOption  = selectedYear.toString(),
                            onOptionSelected = { sel ->
                                sel.toIntOrNull()?.let { yyyy ->
                                    selectedYear = yyyy
                                    updateDob()
                                }
                            }
                        )
                    }
                }
                DropdownWithSearch(
                    title               = stringResource(R.string.select_country),
                    options             = countries,
                    selectedOption      = selectedCountry,
                    onOptionSelected    = { c ->
                        selectedCountry = c
                        registrationViewModel.country = if (c == other) customCountry else c
                    },
                    customInput         = customCountry,
                    onCustomInputChange = { new ->
                        customCountry = new ?: ""
                        registrationViewModel.customCountry = new ?: ""
                    }
                )

                // City Section
                Text(stringResource(R.string.city_label), color = Color.White, fontSize = 18.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownWithSearch(
                            title               = stringResource(R.string.select_city_default),
                            options             = cities,
                            selectedOption      = selectedCity,
                            onOptionSelected    = { cityName ->
                                selectedCity = cityName
                                registrationViewModel.city = if (cityName == other) customCity else cityName
                            },
                            customInput         = customCity,
                            onCustomInputChange = { new ->
                                customCity = new ?: ""
                                registrationViewModel.customCity = new ?: ""
                            }
                        )
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

                // Locality Section
                Text(stringResource(R.string.locality_label), color = Color.White, fontSize = 18.sp)
                Box {
                    DropdownWithSearch(
                        title               = stringResource(R.string.select_locality_default),
                        options             = localities,
                        selectedOption      = selectedLocality,
                        onOptionSelected    = { loc ->
                            selectedLocality = loc
                            registrationViewModel.hometown =
                                if (loc == other) customLocality else loc
                        },
                        customInput         = customLocality,
                        onCustomInputChange = { new ->
                            customLocality = new ?: ""
                            registrationViewModel.customHometown = new ?: ""
                        }
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
    fused: FusedLocationProviderClient,
    ctx: Context,
    countries: List<String>,
    other: String,
    kolkata: String, howrah: String, durgapur: String, asansol: String, siliguri: String,
    darjeeling: String, malda: String, jalpaiguri: String, coochbehar: String, alipurduar: String,
    bankura: String, purulia: String, kharagpur: String, midnapore: String, bardhaman: String,
    hooghly: String, murshidabad: String, baharampur: String, haldia: String, ranaghat: String,
    kalyani: String, chandannagar: String,
    onLocationFound: (String, String, String) -> Unit // ← country, city, locality
) {
    val scope = (ctx as? ComponentActivity)?.lifecycleScope ?: return
    scope.launch {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                onLocationFound(other, other, other); return@launch
            }
            val loc = fused.lastLocation.await() ?: run {
                onLocationFound(other, other, other); return@launch
            }
            val geo = Geocoder(ctx, Locale.getDefault())
            val addr = geo.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            if (addr == null) { onLocationFound(other, other, other); return@launch }

            val detectedCountry  = addr.countryName ?: other
            val detectedCity     = addr.locality ?: addr.subAdminArea ?: other
            val detectedLocality = addr.subLocality ?: other

            val matchedCountry  = countries.find { it.equals(detectedCountry, true) } ?: other
            val matchedCityList = ctx.resources.getStringArray(R.array.city_names).toList()
            val matchedCity     = matchedCityList.find { it.equals(detectedCity, true) } ?: other

            val localities = when (matchedCity) {
                kolkata       -> ctx.resources.getStringArray(R.array.localities_kolkata).toList()
                howrah        -> ctx.resources.getStringArray(R.array.localities_howrah).toList()
                durgapur      -> ctx.resources.getStringArray(R.array.localities_durgapur).toList()
                asansol       -> ctx.resources.getStringArray(R.array.localities_asansol).toList()
                siliguri      -> ctx.resources.getStringArray(R.array.localities_siliguri).toList()
                darjeeling    -> ctx.resources.getStringArray(R.array.localities_darjeeling).toList()
                malda         -> ctx.resources.getStringArray(R.array.localities_malda).toList()
                jalpaiguri    -> ctx.resources.getStringArray(R.array.localities_jalpaiguri).toList()
                coochbehar    -> ctx.resources.getStringArray(R.array.localities_cooch_behar).toList()
                alipurduar    -> ctx.resources.getStringArray(R.array.localities_alipurduar).toList()
                bankura       -> ctx.resources.getStringArray(R.array.localities_bankura).toList()
                purulia       -> ctx.resources.getStringArray(R.array.localities_purulia).toList()
                kharagpur     -> ctx.resources.getStringArray(R.array.localities_kharagpur).toList()
                midnapore     -> ctx.resources.getStringArray(R.array.localities_midnapore).toList()
                bardhaman     -> ctx.resources.getStringArray(R.array.localities_bardhaman).toList()
                hooghly       -> ctx.resources.getStringArray(R.array.localities_hooghly).toList()
                murshidabad   -> ctx.resources.getStringArray(R.array.localities_murshidabad).toList()
                baharampur    -> ctx.resources.getStringArray(R.array.localities_baharampur).toList()
                haldia        -> ctx.resources.getStringArray(R.array.localities_haldia).toList()
                ranaghat      -> ctx.resources.getStringArray(R.array.localities_ranaghat).toList()
                kalyani       -> ctx.resources.getStringArray(R.array.localities_kalyani).toList()
                chandannagar  -> ctx.resources.getStringArray(R.array.localities_chandannagar).toList()
                other         -> ctx.resources.getStringArray(R.array.localities_other).toList()
                else          -> emptyList()
            }
            val matchedLocality = localities.find { it.equals(detectedLocality, true) } ?: other
            withContext(Dispatchers.Main) { onLocationFound(matchedCountry, matchedCity, matchedLocality) }
        } catch (e: Exception) {
            Log.e("fetchLocation", "${e.message}")
            withContext(Dispatchers.Main) { onLocationFound(other, other, other) }
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
        Interest(stringResource(R.string.interest_pets), "🐾"),
        Interest(stringResource(R.string.interest_food), "🍔"),
        Interest(stringResource(R.string.interest_nature), "🌳"),
        Interest(stringResource(R.string.interest_charity), "❤️"),
        Interest(stringResource(R.string.interest_community), "👥"),
        Interest(stringResource(R.string.interest_networking), "🤝"),
        Interest(stringResource(R.string.interest_public_speaking), "🎤"),
        Interest(stringResource(R.string.interest_writing), "✍️"),
        Interest(stringResource(R.string.interest_blogging), "📝"),
        Interest(stringResource(R.string.interest_podcasting), "🎙️"),
        Interest(stringResource(R.string.interest_social_media), "📱"),
        Interest(stringResource(R.string.interest_online_communities), "💬"),
        Interest(stringResource(R.string.interest_skydiving), "🪂"),
        Interest(stringResource(R.string.interest_scuba_diving), "🤿"),
        Interest(stringResource(R.string.interest_rock_climbing), "🧗"),
        Interest(stringResource(R.string.interest_surfing), "🏄"),
        Interest(stringResource(R.string.interest_skiing), "⛷️"),
        Interest(stringResource(R.string.interest_snowboarding), "🏂"),
        Interest(stringResource(R.string.interest_mountain_biking), "🚵"),
        Interest(stringResource(R.string.interest_motorcycling), "🏍️"),
        Interest(stringResource(R.string.interest_car_racing), "🏎️"),
        Interest(stringResource(R.string.interest_extreme_sports), "🏂"),
        Interest(stringResource(R.string.interest_puzzles), "🧩"),
        Interest(stringResource(R.string.interest_board_games), "🎲"),
        Interest(stringResource(R.string.interest_video_games), "🎮"),
        Interest(stringResource(R.string.interest_watching_tv), "📺"),
        Interest(stringResource(R.string.interest_napping), "😴"),
        Interest(stringResource(R.string.interest_spa_days), "💆"),
        Interest(stringResource(R.string.interest_beach_days), "🏖️"),
        Interest(stringResource(R.string.interest_picnics), "🧺"),
        Interest(stringResource(R.string.interest_coding), "⌨️"),
        Interest(stringResource(R.string.interest_robotics), "🤖"),
        Interest(stringResource(R.string.interest_space), "🚀"),
        Interest(stringResource(R.string.interest_environmentalism), "🌍"),
        Interest(stringResource(R.string.interest_baking), "🍰"),
        Interest(stringResource(R.string.interest_wine_tasting), "🍷"),
        Interest(stringResource(R.string.interest_craft_beer), "🍺"),
        Interest(stringResource(R.string.interest_coffee), "☕"),
        Interest(stringResource(R.string.interest_yoga), "🧘"),
        Interest(stringResource(R.string.interest_meditation), "🧘‍♂️"),
        Interest(stringResource(R.string.interest_astrology), "♈"),
        Interest(stringResource(R.string.interest_romance), "💋"), // Example emoji; choose based on app tone
        Interest(stringResource(R.string.interest_crystals), "💎"),
        Interest(stringResource(R.string.interest_vintage_clothing), "🧥"),
        Interest(stringResource(R.string.interest_thrift_shopping), "🛍️"),
        Interest(stringResource(R.string.interest_diy), "🛠️"),
        Interest(stringResource(R.string.interest_home_improvement), "🏠"),
        Interest(stringResource(R.string.interest_interior_design), "🛋️"),
        Interest(stringResource(R.string.interest_history), "📜"),
        Interest(stringResource(R.string.interest_science), "🔬"),
        Interest(stringResource(R.string.interest_philosophy), "🧠"),
        Interest(stringResource(R.string.interest_politics), "🗳️"),
        Interest(stringResource(R.string.interest_economics), "💰"),
        Interest(stringResource(R.string.interest_hiking), "🥾"),
        Interest(stringResource(R.string.interest_camping), "⛺"),
        Interest(stringResource(R.string.interest_fishing), "🎣"),
        Interest(stringResource(R.string.interest_hunting), "🏹"),
        Interest(stringResource(R.string.interest_traveling), "🧳")
    )

    // Combine global and locality-based interests and remove duplicates (by name)
    val allInterests = (globalInterests).distinctBy { it.name }

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
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageRef = FirebaseRefs.storage.reference

    var isRecording by remember { mutableStateOf(false) }
    var isPlaying  by remember { mutableStateOf(false) }
    var isVoiceBioValid by remember { mutableStateOf(true) }

    val voiceFile = remember { File(context.filesDir, "voice_note.mp3") }
    val voiceFilePath = voiceFile.absolutePath
    var voiceProgress by remember { mutableStateOf(0f) }
    var voiceDuration by remember { mutableStateOf(0L) }

    val mediaPlayer = remember { MediaPlayer() }

    /* --------------------------------------------------------------------- */
    /*  Voice bio is now OPTIONAL – so it’s removed from the 'canProceed'    */
    /* --------------------------------------------------------------------- */
    val canProceed = registrationViewModel.profilePictureUri != null

    /* ─────────────────── HELPER: check image with OpenAI ─────────────────── */
    suspend fun isExplicit(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        // ① read the (already-compressed) JPEG bytes
        val jpeg = compressImage(ctx, uri)
        // ② Base-64 encode for moderation endpoint
        val b64  = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        // ③ call the existing suspend helper (true == unsafe)
        moderateImages(listOf(b64))
    }

    /* ─────────────────── PROFILE PIC PICKER ─────────────────── */
    val profilePicPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (isExplicit(uri)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        ctx,
                        "That photo looks explicit – please retake another one.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch                                           // 🚫  block upload
            }
            registrationViewModel.profilePictureUri = uri
            uploadProfilePicToFirebase(ctx, storageRef, uri, registrationViewModel)
        }
    }

    /* ─────────────────── OPTIONAL PHOTOS PICKER ─────────────────── */
    val optionalPhotoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (isExplicit(uri)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        ctx,
                        "That photo looks explicit – please choose another.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch                                           // 🚫  block upload
            }
            registrationViewModel.optionalPhotoUris.add(uri)
            uploadOptionalPhoto(ctx, storageRef, uri, registrationViewModel)
        }
    }

    /* ---------- Voice-bio helpers ---------- */
    fun validateVoiceBio() {
        try {
            val temp = MediaPlayer().apply {
                setDataSource(voiceFilePath)
                prepare()
            }
            voiceDuration = temp.duration.toLong()
            temp.release()
            isVoiceBioValid = voiceDuration <= 60_000          // ≤ 60 s allowed
        } catch (e: Exception) {
            isVoiceBioValid = false
            Log.e("VoiceValidation", "Could not validate: ${e.message}")
        }
    }

    val audioPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            isRecording = true
            registrationViewModel.startVoiceRecording(context, voiceFilePath)
        } else {
            Toast.makeText(context, "Mic permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            isRecording = false
            registrationViewModel.stopVoiceRecording()
            registrationViewModel.voiceNoteUri = Uri.fromFile(voiceFile)
            validateVoiceBio()

            if (isVoiceBioValid) {
                uploadVoiceToFirebase(
                    storageRef,
                    registrationViewModel.voiceNoteUri!!,
                    registrationViewModel
                )
            }
        } else {
            permissionLauncher.launch(audioPermissions)
        }
    }

    fun togglePlayback() {
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
                Log.e("MediaPlayer", "Playback error: ${e.message}")
            }
        }
    }

    /* ---------- Observe playback progress ---------- */
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer.isPlaying) {
            voiceProgress = mediaPlayer.currentPosition / voiceDuration.toFloat()
            delay(300)
        }
        if (!mediaPlayer.isPlaying) {
            isPlaying = false
            voiceProgress = 0f
        }
    }

    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

    /* ============================  UI  ============================ */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_media_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        containerColor = Color(0xFF1A1A1A)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            /* ---------------- Profile picture ---------------- */
            item {
                Text(stringResource(R.string.profile_picture_label),
                    color = Color.White, fontSize = 18.sp)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .border(2.dp, Color(0xFFFF6000), CircleShape)
                        .clickable { profilePicPickerLauncher.launch("image/*") }
                ) {
                    registrationViewModel.profilePictureUri?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                        )
                    } ?: Text("Tap", color = Color.White, fontSize = 14.sp)
                }
            }

            /* ---------------- Optional photos ---------------- */
            item {
                Text(stringResource(R.string.optional_photos_label),
                    color = Color.White, fontSize = 18.sp)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                        }
                    }
                }
                Button(
                    onClick = { optionalPhotoPickerLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
                ) {
                    Text(stringResource(R.string.add_photos_button), color = Color.White)
                }
            }

            /* ---------------- Voice bio (optional) ---------------- */
            item {
                Text(stringResource(R.string.voice_bio_label) + "  •  " +
                        stringResource(R.string.optional_voice),
                    color = Color.White, fontSize = 18.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::toggleRecording) {
                        Icon(
                            if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                            null,
                            tint = if (isRecording) Color.Red else Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    registrationViewModel.voiceNoteUri?.let {
                        IconButton(onClick = ::togglePlayback) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null,
                                tint = Color.White
                            )
                        }
                        Slider(
                            value = voiceProgress,
                            onValueChange = {},
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!isVoiceBioValid) {
                    Text(
                        stringResource(R.string.voice_bio_duration_error),
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }
            }

            /* ---------------- Next button ---------------- */
            item {
                Button(
                    onClick = { if (canProceed) onNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = canProceed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canProceed) Color(0xFFFF6000) else Color.DarkGray
                    )
                ) {
                    Text(stringResource(R.string.next_button), color = Color.White)
                }
            }
        }
    }
}


fun uploadOptionalPhoto(
    context: Context,
    storageRef: StorageReference,
    uri: Uri,
    registrationViewModel: RegistrationViewModel
) {
    (context as? ComponentActivity)?.lifecycleScope?.launch {
        val jpegBytes = compressImage(context, uri)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
        val ref = storageRef.child("users/$userId/${uri.lastPathSegment ?: System.currentTimeMillis()}.jpg")

        ref.putBytes(jpegBytes)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    registrationViewModel.optionalPhotoUrls.add(downloadUri.toString())
                }
            }
            .addOnFailureListener { e ->
                Log.e("UploadMedia", "Optional-photo upload failed: ${e.message}")
            }
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
