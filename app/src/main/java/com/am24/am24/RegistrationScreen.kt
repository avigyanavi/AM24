// RegistrationActivity.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.yalantis.ucrop.UCrop
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
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
import kotlinx.coroutines.suspendCancellableCoroutine

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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    var jobRole by remember { mutableStateOf(viewModel.jobRole) }
    var work by remember { mutableStateOf(viewModel.work) }

    // Work search state
    var workQuery by remember { mutableStateOf(viewModel.work) }
    var workResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var workSearching by remember { mutableStateOf(false) }
    var isWorkFieldFocused by remember { mutableStateOf(false) } // Track focus state
    val workMenuExpanded = workResults.isNotEmpty() && isWorkFieldFocused // Only expand if focused

    // Social Causes
    val allCauses = stringArrayResource(R.array.social_causes_list).toList()
    val maxSelections = 5

    // Focus management
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        // Clear focus on screen entry to prevent automatic focus on the text field
        focusManager.clearFocus()
    }

    // Work search with debouncing
    LaunchedEffect(workQuery) {
        if (workQuery.length < 3) {
            workResults = emptyList()
            return@LaunchedEffect
        } else {
            delay(400) // Debounce
            workSearching = true
            workResults = searchPlacesRich(workQuery) // Use "establishment" for workplaces
            workSearching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tell_us_more_about_yourself), color = Color.White) },
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
                    ExposedDropdownMenuBox(
                        expanded = workMenuExpanded,
                        onExpandedChange = { /* Controlled by results and focus */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        var workother = stringResource(R.string.work_option_other)
                        val focusRequester = remember { FocusRequester() } // For focus tracking
                        OutlinedTextField(
                            value = workQuery,
                            onValueChange = { query ->
                                workQuery = query
                                viewModel.work = workother
                                viewModel.customWork = query
                                work = viewModel.work
                            },
                            label = { Text(stringResource(R.string.select_work)) },
                            singleLine = true,
                            trailingIcon = {
                                if (workSearching)
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                else
                                    Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFFF6000),
                                unfocusedBorderColor = Color.White,
                                cursorColor = Color.White,
                                focusedLabelColor = Color(0xFFFF6000),
                                unfocusedLabelColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isWorkFieldFocused = focusState.isFocused
                                    if (!focusState.isFocused) {
                                        workResults = emptyList() // Clear results when focus is lost
                                    }
                                }
                        )
                        ExposedDropdownMenu(
                            expanded = workMenuExpanded,
                            onDismissRequest = {
                                workResults = emptyList()
                                focusManager.clearFocus() // Clear focus when dismissing the dropdown
                            },
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
                        ) {
                            workResults.forEach { res ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(res.name, color = Color.Black)
                                            if (res.address.isNotBlank())
                                                Text(res.address, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500)) },
                                    onClick = {
                                        viewModel.work = res.name
                                        viewModel.customWork = ""
                                        workQuery = res.name
                                        workResults = emptyList()
                                        work = viewModel.work
                                        focusManager.clearFocus() // Clear focus after selection
                                    }
                                )
                            }
                        }
                    }
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allCauses.forEach { cause ->
                                val isSelected = viewModel.socialCauses.contains(cause)
                                val canSelectMore = viewModel.socialCauses.size < maxSelections
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) {
                                            viewModel.socialCauses.remove(cause)
                                        } else if (canSelectMore) {
                                            viewModel.socialCauses.add(cause)
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
                        if (viewModel.socialCauses.size > maxSelections) {
                            Text(
                                text = stringResource(R.string.max_social_causes_error, maxSelections),
                                color = Color.Red,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
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
    val educationLevels = listOf(
        stringResource(R.string.no_education_label),
        stringResource(R.string.high_school_label),
        stringResource(R.string.college_label),
        stringResource(R.string.post_graduation_label)
    )

    // State for place search queries and results
    var highSchoolQuery by remember { mutableStateOf(registrationViewModel.highSchool) }
    var collegeQuery by remember { mutableStateOf(registrationViewModel.college) }
    var postGradQuery by remember { mutableStateOf(registrationViewModel.postGraduation) }
    var highSchoolResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var collegeResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var postGradResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var highSchoolSearching by remember { mutableStateOf(false) }
    var collegeSearching by remember { mutableStateOf(false) }
    var postGradSearching by remember { mutableStateOf(false) }
    var isHighSchoolFieldFocused by remember { mutableStateOf(false) } // Track focus state for high school
    var isCollegeFieldFocused by remember { mutableStateOf(false) } // Track focus state for college
    var isPostGradFieldFocused by remember { mutableStateOf(false) } // Track focus state for post-grad
    val highSchoolMenuExpanded = highSchoolResults.isNotEmpty() && isHighSchoolFieldFocused // Only expand if focused
    val collegeMenuExpanded = collegeResults.isNotEmpty() && isCollegeFieldFocused // Only expand if focused
    val postGradMenuExpanded = postGradResults.isNotEmpty() && isPostGradFieldFocused // Only expand if focused

    // Enable Next button only if education level is selected and required fields are filled
    val isNextEnabled = registrationViewModel.educationLevel.isNotEmpty() &&
            (registrationViewModel.educationLevel == stringResource(R.string.no_education_label) ||
                    registrationViewModel.highSchool.isNotEmpty()) &&
            (registrationViewModel.educationLevel != stringResource(R.string.college_label) ||
                    registrationViewModel.college.isNotEmpty()) &&
            (registrationViewModel.educationLevel != stringResource(R.string.post_graduation_label) ||
                    registrationViewModel.postGraduation.isNotEmpty())

    // Focus management
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        // Clear focus on screen entry to prevent automatic focus on any text field
        focusManager.clearFocus()
    }

    // Place search side-effects with debouncing
    LaunchedEffect(highSchoolQuery) {
        if (highSchoolQuery.length < 3) {
            highSchoolResults = emptyList()
            return@LaunchedEffect
        } else {
            delay(400) // Debounce to prevent excessive API calls
            highSchoolSearching = true
            highSchoolResults = searchPlacesRich(highSchoolQuery)
            highSchoolSearching = false
        }
    }

    LaunchedEffect(collegeQuery) {
        if (collegeQuery.length < 3) {
            collegeResults = emptyList()
            return@LaunchedEffect
        } else {
            delay(400)
            collegeSearching = true
            collegeResults = searchPlacesRich(collegeQuery)
            collegeSearching = false
        }
    }

    LaunchedEffect(postGradQuery) {
        if (postGradQuery.length < 3) {
            postGradResults = emptyList()
            return@LaunchedEffect
        } else {
            delay(400)
            postGradSearching = true
            postGradResults = searchPlacesRich(postGradQuery)
            postGradSearching = false
        }
    }

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
                if (registrationViewModel.educationLevel in listOf(
                        stringResource(R.string.high_school_label),
                        stringResource(R.string.college_label),
                        stringResource(R.string.post_graduation_label)
                    )) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.high_school_label),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ExposedDropdownMenuBox(
                        expanded = highSchoolMenuExpanded,
                        onExpandedChange = { /* Controlled by results and focus */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val focusRequester = remember { FocusRequester() } // For focus tracking
                        OutlinedTextField(
                            value = highSchoolQuery,
                            onValueChange = { query ->
                                highSchoolQuery = query
                                registrationViewModel.highSchool = query
                                registrationViewModel.customHighSchool = query
                            },
                            label = { Text(stringResource(R.string.select_or_type_high_school)) },
                            singleLine = true,
                            trailingIcon = {
                                if (highSchoolSearching)
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                else
                                    Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFFF6000),
                                unfocusedBorderColor = Color.White,
                                cursorColor = Color.White,
                                focusedLabelColor = Color(0xFFFF6000),
                                unfocusedLabelColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isHighSchoolFieldFocused = focusState.isFocused
                                    if (!focusState.isFocused) {
                                        highSchoolResults = emptyList() // Clear results when focus is lost
                                    }
                                }
                        )
                        ExposedDropdownMenu(
                            expanded = highSchoolMenuExpanded,
                            onDismissRequest = {
                                highSchoolResults = emptyList()
                                focusManager.clearFocus() // Clear focus when dismissing the dropdown
                            },
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
                        ) {
                            highSchoolResults.forEach { res ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(res.name, color = Color.Black)
                                            if (res.address.isNotBlank())
                                                Text(
                                                    res.address,
                                                    color = Color.DarkGray,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500))
                                    },
                                    onClick = {
                                        registrationViewModel.highSchool = res.name
                                        registrationViewModel.customHighSchool = ""
                                        highSchoolQuery = res.name
                                        highSchoolResults = emptyList()
                                        focusManager.clearFocus() // Clear focus after selection
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GraduationYearDropdown(
                        year = registrationViewModel.highSchoolGraduationYear,
                        onYearSelected = { registrationViewModel.highSchoolGraduationYear = it }
                    )
                }

                // College Section
                if (registrationViewModel.educationLevel in listOf(
                        stringResource(R.string.college_label),
                        stringResource(R.string.post_graduation_label)
                    )) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.college_label),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ExposedDropdownMenuBox(
                        expanded = collegeMenuExpanded,
                        onExpandedChange = { /* Controlled by results and focus */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val focusRequester = remember { FocusRequester() } // For focus tracking
                        OutlinedTextField(
                            value = collegeQuery,
                            onValueChange = { query ->
                                collegeQuery = query
                                registrationViewModel.college = query
                                registrationViewModel.customCollege = query
                            },
                            label = { Text(stringResource(R.string.select_or_type_college)) },
                            singleLine = true,
                            trailingIcon = {
                                if (collegeSearching)
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                else
                                    Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFFF6000),
                                unfocusedBorderColor = Color.White,
                                cursorColor = Color.White,
                                focusedLabelColor = Color(0xFFFF6000),
                                unfocusedLabelColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isCollegeFieldFocused = focusState.isFocused
                                    if (!focusState.isFocused) {
                                        collegeResults = emptyList() // Clear results when focus is lost
                                    }
                                }
                        )
                        ExposedDropdownMenu(
                            expanded = collegeMenuExpanded,
                            onDismissRequest = {
                                collegeResults = emptyList()
                                focusManager.clearFocus() // Clear focus when dismissing the dropdown
                            },
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
                        ) {
                            collegeResults.forEach { res ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(res.name, color = Color.Black)
                                            if (res.address.isNotBlank())
                                                Text(
                                                    res.address,
                                                    color = Color.DarkGray,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500))
                                    },
                                    onClick = {
                                        registrationViewModel.college = res.name
                                        registrationViewModel.customCollege = ""
                                        collegeQuery = res.name
                                        collegeResults = emptyList()
                                        focusManager.clearFocus() // Clear focus after selection
                                    }
                                )
                            }
                        }
                    }
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
                    ExposedDropdownMenuBox(
                        expanded = postGradMenuExpanded,
                        onExpandedChange = { /* Controlled by results and focus */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val focusRequester = remember { FocusRequester() } // For focus tracking
                        OutlinedTextField(
                            value = postGradQuery,
                            onValueChange = { query ->
                                postGradQuery = query
                                registrationViewModel.postGraduation = query
                                registrationViewModel.customPostGraduation = query
                            },
                            label = { Text(stringResource(R.string.select_or_type_post_grad)) },
                            singleLine = true,
                            trailingIcon = {
                                if (postGradSearching)
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                else
                                    Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFFF6000),
                                unfocusedBorderColor = Color.White,
                                cursorColor = Color.White,
                                focusedLabelColor = Color(0xFFFF6000),
                                unfocusedLabelColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isPostGradFieldFocused = focusState.isFocused
                                    if (!focusState.isFocused) {
                                        postGradResults = emptyList() // Clear results when focus is lost
                                    }
                                }
                        )
                        ExposedDropdownMenu(
                            expanded = postGradMenuExpanded,
                            onDismissRequest = {
                                postGradResults = emptyList()
                                focusManager.clearFocus() // Clear focus when dismissing the dropdown
                            },
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
                        ) {
                            postGradResults.forEach { res ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(res.name, color = Color.Black)
                                            if (res.address.isNotBlank())
                                                Text(
                                                    res.address,
                                                    color = Color.DarkGray,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500))
                                    },
                                    onClick = {
                                        registrationViewModel.postGraduation = res.name
                                        registrationViewModel.customPostGraduation = ""
                                        postGradQuery = res.name
                                        postGradResults = emptyList()
                                        focusManager.clearFocus() // Clear focus after selection
                                    }
                                )
                            }
                        }
                    }
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
    var showCustomInput by remember(selectedOption) { mutableStateOf(selectedOption == other) }
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Text(text = title, color = Color.White, fontSize = 11.sp)

        OutlinedButton(
            onClick = {
                expanded = !expanded
                searchText = ""
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFFFF6000)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6000))
        ) {
            val displayText = if (showCustomInput) {
                if (!customInput.isNullOrEmpty()) customInput else "Custom"
            } else {
                selectedOption.ifEmpty { stringResource(R.string.select_or_type) }
            }
            Text(
                text = displayText,
                fontSize = 11.sp,
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
                    Text(stringResource(R.string.search_label), fontSize = 11.sp, color = Color.White)
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
                        text = { Text(option, fontSize = 11.sp, color = Color.White) },
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
                        fontSize = 11.sp,
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
    /* ───── visibility toggles ───── */
    var pwdVisible        by remember { mutableStateOf(false) }
    var confirmPwdVisible by remember { mutableStateOf(false) }


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
                    /* -------- Password field -------- */
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            registrationViewModel.password = it.text
                        },
                        label = { Text("Password", color = Color.White) },
                        singleLine = true,
                        visualTransformation =
                            if (pwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (pwdVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            IconButton(onClick = { pwdVisible = !pwdVisible }) {
                                Icon(icon, contentDescription = null, tint = Color.White)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors  = fieldColors()
                    )

                    /* ---- Confirm-password field ---- */
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm password", color = Color.White) },
                        singleLine = true,
                        visualTransformation =
                            if (confirmPwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (confirmPwdVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            IconButton(onClick = { confirmPwdVisible = !confirmPwdVisible }) {
                                Icon(icon, contentDescription = null, tint = Color.White)
                            }
                        },
                        isError = passwordError,
                        modifier = Modifier.fillMaxWidth(),
                        colors  = fieldColors()
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
        stringResource(R.string.community_gujarati),
        stringResource(R.string.community_kannadiga),
        stringResource(R.string.community_tamil),
        stringResource(R.string.community_malayali),
        stringResource(R.string.community_odia),
        stringResource(R.string.community_telugu),
        stringResource(R.string.community_nepali),
        stringResource(R.string.community_munda),
        stringResource(R.string.community_oraon),
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
                        stringResource(R.string.caste_brahmin),
                        stringResource(R.string.caste_kshatriya),
                        stringResource(R.string.caste_baidya),
                        stringResource(R.string.caste_mahishya),
                        stringResource(R.string.caste_sadgop),
                        stringResource(R.string.caste_vaishya),
                        stringResource(R.string.caste_obc),
                        stringResource(R.string.caste_scheduled_caste),
                        stringResource(R.string.caste_scheduled_tribe),
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

    // Local state for name & height to mirror registrationViewModel
    var heightText by remember { mutableStateOf(registrationViewModel.height.toString()) }
    var feetText   by remember {
        mutableStateOf(registrationViewModel.height2.getOrNull(0)?.toString() ?: "")
    }
    var inchText   by remember {
        mutableStateOf(registrationViewModel.height2.getOrNull(1)?.toString() ?: "")
    }
    var isLoading  by remember { mutableStateOf(false) }

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
            // ---- Username (mandatory) ----
            OutlinedTextField(
                value = usernameTf,
                onValueChange = {
                    usernameTf = it
                    isValid = true
                },
                label = { Text("Username", color = Color.White) },
                singleLine = true,
                isError = !isValid,
                supportingText = {
                    if (!isValid) Text(errorMsg, color = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6000),
                    unfocusedBorderColor = Color.White,
                    cursorColor = Color.White,
                    focusedLabelColor = Color(0xFFFF6000),
                    unfocusedLabelColor = Color.White
                )
            )

            Spacer(Modifier.height(24.dp))

            // ---- Full Name (optional) ----
            TextFieldWithLabel(
                label = stringResource(R.string.full_name_label),
                value = registrationViewModel.name,
                onValueChange = { registrationViewModel.name = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Height (optional) ----
            Text(
                text = stringResource(R.string.height_label),
                color = Color.White,
                fontSize = 18.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = registrationViewModel.isHeightInFeet,
                    onCheckedChange = { useFeet ->
                        registrationViewModel.isHeightInFeet = useFeet
                        if (useFeet) {
                            val (f, i) = registrationViewModel.cmToFeetInches(registrationViewModel.height)
                            feetText = f.toString()
                            inchText = i.toString()
                        } else {
                            val f = feetText.toIntOrNull() ?: 0
                            val i = inchText.toIntOrNull() ?: 0
                            heightText = registrationViewModel.feetInchesToCm(f, i).toString()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFF6000),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    OutlinedTextField(
                        value = inchText,
                        onValueChange = { newInch ->
                            inchText = newInch
                            registrationViewModel.height2 = listOf(
                                registrationViewModel.height2.getOrNull(0) ?: 0,
                                newInch.toIntOrNull() ?: 0
                            )
                        },
                        label = {
                            Text(
                                stringResource(R.string.inches_label),
                                color = Color.White
                            )
                        },
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
                TextFieldWithLabel(
                    label = stringResource(R.string.height_cm_label),
                    value = heightText,
                    onValueChange = { newCm ->
                        heightText = newCm
                        registrationViewModel.height = newCm.toIntOrNull()
                            ?: registrationViewModel.height
                    }
                )
            }

            // Add a spacer after height ✔
            Spacer(modifier = Modifier.height(24.dp))

            // ---- Finish Button ----
            Button(
                onClick = {
                    val raw = usernameTf.text.trim()
                    if (raw.isEmpty()) {
                        isValid = false
                        errorMsg = "Username cannot be empty."
                        return@Button
                    }
                    isLoading = true

                    val key = raw.lowercase(Locale.getDefault())
                    val uid = auth.currentUser?.uid ?: run {
                        Toast.makeText(context, "No signed-in user", Toast.LENGTH_LONG).show()
                        isLoading = false
                        return@Button
                    }

                    // 1) check availability
                    db.child("usernames").child(key).get()
                        .addOnSuccessListener { snap ->
                            if (snap.exists()) {
                                isValid = false
                                errorMsg = "That username is taken."
                                isLoading = false
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
                                        isValid = false
                                        errorMsg = "Failed to reserve username: ${it.message}"
                                        isLoading = false
                                    }
                            }
                        }
                        .addOnFailureListener {
                            isValid = false
                            errorMsg = "Error checking username: ${it.message}"
                            isLoading = false
                        }
                },
                enabled = usernameTf.text.trim().isNotEmpty() && !isLoading, // disable if blank
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6000),
                    disabledContainerColor = Color(0x88FF6000)
                ), // optional: lighter tint when disabled
                shape = CircleShape
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        "Finish",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
            city = if (registrationViewModel.city == other) registrationViewModel.customCity.trim() else registrationViewModel.city.trim(),
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
            zodiac = registrationViewModel.zodiac, // Include zodiac in the profile
            interestedIn = registrationViewModel.interestedIn.toList() // Include "interested in" data
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

    // Now only “Interested In” is required on this screen
    val canProceed = registrationViewModel.interestedIn.isNotEmpty()

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
                        // ---- Interested In (font made larger) ----
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF6000)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.interested_in_header),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
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
                                    label = { Text(option, color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (option) {
                                                maleOption -> Icons.Default.Male
                                                else       -> Icons.Default.Female
                                            },
                                            contentDescription = null,
                                            tint = Color.White
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

// ──────────────────────────────────────────────────────────────────────────────
// ↓ FULL composable (replace the old one entirely)
// ──────────────────────────────────────────────────────────────────────────────
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

    var agartala = stringResource(R.string.city_agartala)
    var ahmedabad = stringResource(R.string.city_ahmedabad)
    var aizawl = stringResource(R.string.city_aizawl)
    var amaravati = stringResource(R.string.city_amaravati)
    var amritsar = stringResource(R.string.city_amritsar)
    var asansol = stringResource(R.string.city_asansol)
    var bengaluru = stringResource(R.string.city_bengaluru)
    var bhilai = stringResource(R.string.city_bhilai)
    var bhopal = stringResource(R.string.city_bhopal)
    var bhubaneswar = stringResource(R.string.city_bhubaneswar)
    var bilaspur = stringResource(R.string.city_bilaspur)
    var chandigarh = stringResource(R.string.city_chandigarh)
    var chennai = stringResource(R.string.city_chennai)
    var coimbatore = stringResource(R.string.city_coimbatore)
    var cuttack = stringResource(R.string.city_cuttack)
    var daman = stringResource(R.string.city_daman)
    var darjeeling = stringResource(R.string.city_darjeeling)
    var dehradun = stringResource(R.string.city_dehradun)
    var dibrugarh = stringResource(R.string.city_dibrugarh)
    var dharamshala = stringResource(R.string.city_dharamshala)
    var durgapur = stringResource(R.string.city_durgapur)
    var faridabad = stringResource(R.string.city_faridabad)
    var gangtok = stringResource(R.string.city_gangtok)
    var gaya = stringResource(R.string.city_gaya)
    var gandhinagar = stringResource(R.string.city_gandhinagar)
    var ghaziabad = stringResource(R.string.city_ghaziabad)
    var gwalior = stringResource(R.string.city_gwalior)
    var gyalshing = stringResource(R.string.city_gyalshing)
    var guwahati = stringResource(R.string.city_guwahati)
    var gurugram = stringResource(R.string.city_gurugram)
    var haridwar = stringResource(R.string.city_haridwar)
    var hisar = stringResource(R.string.city_hisar)
    var howrah = stringResource(R.string.city_howrah)
    var hyderabad = stringResource(R.string.city_hyderabad)
    var imphal = stringResource(R.string.city_imphal)
    var indore = stringResource(R.string.city_indore)
    var itanagar = stringResource(R.string.city_itanagar)
    var jaipur = stringResource(R.string.city_jaipur)
    var jamshedpur = stringResource(R.string.city_jamshedpur)
    var jodhpur = stringResource(R.string.city_jodhpur)
    var kancheepuram = stringResource(R.string.city_kancheepuram)
    var kanpur = stringResource(R.string.city_kanpur)
    var kargil = stringResource(R.string.city_kargil)
    var kavaratti = stringResource(R.string.city_kavaratti)
    var kharagpur = stringResource(R.string.city_kharagpur)
    var kochi = stringResource(R.string.city_kochi)
    var kohima = stringResource(R.string.city_kohima)
    var kolkata = stringResource(R.string.city_kolkata)
    var leh = stringResource(R.string.city_leh)
    var ludhiana = stringResource(R.string.city_ludhiana)
    var lucknow = stringResource(R.string.city_lucknow)
    var madurai = stringResource(R.string.city_madurai)
    var mumbai = stringResource(R.string.city_mumbai)
    var mangaluru = stringResource(R.string.city_mangaluru)
    var mysuru = stringResource(R.string.city_mysuru)
    var nainital = stringResource(R.string.city_nainital)
    var nagpur = stringResource(R.string.city_nagpur)
    var namchi = stringResource(R.string.city_namchi)
    var navi_mumbai = stringResource(R.string.city_navi_mumbai)
    var nct_of_delhi = stringResource(R.string.city_nct_of_delhi)
    var noida = stringResource(R.string.city_noida)
    var panaji = stringResource(R.string.city_panaji)
    var pasighat = stringResource(R.string.city_pasighat)
    var patna = stringResource(R.string.city_patna)
    var prayagraj = stringResource(R.string.city_prayagraj)
    var pune = stringResource(R.string.city_pune)
    var port_blair = stringResource(R.string.city_port_blair)
    var puducherry = stringResource(R.string.city_puducherry)
    var raipur = stringResource(R.string.city_raipur)
    var ranchi = stringResource(R.string.city_ranchi)
    var rourkela = stringResource(R.string.city_rourkela)
    var rohtak = stringResource(R.string.city_rohtak)
    var shillong = stringResource(R.string.city_shillong)
    var shimla = stringResource(R.string.city_shimla)
    var silchar = stringResource(R.string.city_silchar)
    var siliguri = stringResource(R.string.city_siliguri)
    var sonipat = stringResource(R.string.city_sonipat)
    var surat = stringResource(R.string.city_surat)
    var secunderabad = stringResource(R.string.city_secunderabad)
    var tawang = stringResource(R.string.city_tawang)
    var thane = stringResource(R.string.city_thane)
    var thiruvananthapuram = stringResource(R.string.city_thiruvananthapuram)
    var udaipur = stringResource(R.string.city_udaipur)
    var vadodara = stringResource(R.string.city_vadodara)
    var varanasi = stringResource(R.string.city_varanasi)
    var vellore = stringResource(R.string.city_vellore)
    var vijayawada = stringResource(R.string.city_vijayawada)
    var visakhapatnam = stringResource(R.string.city_visakhapatnam)
    var warangal = stringResource(R.string.city_warangal)

    val localities = remember(selectedCity) {
        when (selectedCity) {
            agartala -> resources.getStringArray(R.array.localities_agartala).toList()
            ahmedabad -> resources.getStringArray(R.array.localities_ahmedabad).toList()
            aizawl -> resources.getStringArray(R.array.localities_aizawl).toList()
            amaravati -> resources.getStringArray(R.array.localities_amaravati).toList()
            amritsar -> resources.getStringArray(R.array.localities_amritsar).toList()
            asansol -> resources.getStringArray(R.array.localities_asansol).toList()
            bengaluru -> resources.getStringArray(R.array.localities_bengaluru).toList()
            bhilai -> resources.getStringArray(R.array.localities_bhilai).toList()
            bhopal -> resources.getStringArray(R.array.localities_bhopal).toList()
            bhubaneswar -> resources.getStringArray(R.array.localities_bhubaneswar).toList()
            bilaspur -> resources.getStringArray(R.array.localities_bilaspur).toList()
            chandigarh -> resources.getStringArray(R.array.localities_chandigarh).toList()
            chennai -> resources.getStringArray(R.array.localities_chennai).toList()
            coimbatore -> resources.getStringArray(R.array.localities_coimbatore).toList()
            cuttack -> resources.getStringArray(R.array.localities_cuttack).toList()
            daman -> resources.getStringArray(R.array.localities_daman).toList()
            darjeeling -> resources.getStringArray(R.array.localities_darjeeling).toList()
            dehradun -> resources.getStringArray(R.array.localities_dehradun).toList()
            dibrugarh -> resources.getStringArray(R.array.localities_dibrugarh).toList()
            dharamshala -> resources.getStringArray(R.array.localities_dharamshala).toList()
            durgapur -> resources.getStringArray(R.array.localities_durgapur).toList()
            faridabad -> resources.getStringArray(R.array.localities_faridabad).toList()
            gangtok -> resources.getStringArray(R.array.localities_gangtok).toList()
            gaya -> resources.getStringArray(R.array.localities_gaya).toList()
            gandhinagar -> resources.getStringArray(R.array.localities_gandhinagar).toList()
            ghaziabad -> resources.getStringArray(R.array.localities_ghaziabad).toList()
            gwalior -> resources.getStringArray(R.array.localities_gwalior).toList()
            gyalshing -> resources.getStringArray(R.array.localities_gyalshing).toList()
            guwahati -> resources.getStringArray(R.array.localities_guwahati).toList()
            gurugram -> resources.getStringArray(R.array.localities_gurugram).toList()
            haridwar -> resources.getStringArray(R.array.localities_haridwar).toList()
            hisar -> resources.getStringArray(R.array.localities_hisar).toList()
            howrah -> resources.getStringArray(R.array.localities_howrah).toList()
            hyderabad -> resources.getStringArray(R.array.localities_hyderabad).toList()
            imphal -> resources.getStringArray(R.array.localities_imphal).toList()
            indore -> resources.getStringArray(R.array.localities_indore).toList()
            itanagar -> resources.getStringArray(R.array.localities_itanagar).toList()
            jaipur -> resources.getStringArray(R.array.localities_jaipur).toList()
            jamshedpur -> resources.getStringArray(R.array.localities_jamshedpur).toList()
            jodhpur -> resources.getStringArray(R.array.localities_jodhpur).toList()
            kancheepuram -> resources.getStringArray(R.array.localities_kancheepuram).toList()
            kanpur -> resources.getStringArray(R.array.localities_kanpur).toList()
            kargil -> resources.getStringArray(R.array.localities_kargil).toList()
            kavaratti -> resources.getStringArray(R.array.localities_kavaratti).toList()
            kharagpur -> resources.getStringArray(R.array.localities_kharagpur).toList()
            kochi -> resources.getStringArray(R.array.localities_kochi).toList()
            kohima -> resources.getStringArray(R.array.localities_kohima).toList()
            kolkata -> resources.getStringArray(R.array.localities_kolkata).toList()
            leh -> resources.getStringArray(R.array.localities_leh).toList()
            ludhiana -> resources.getStringArray(R.array.localities_ludhiana).toList()
            lucknow -> resources.getStringArray(R.array.localities_lucknow).toList()
            madurai -> resources.getStringArray(R.array.localities_madurai).toList()
            mumbai -> resources.getStringArray(R.array.localities_mumbai).toList()
            mangaluru -> resources.getStringArray(R.array.localities_mangaluru).toList()
            mysuru -> resources.getStringArray(R.array.localities_mysuru).toList()
            nainital -> resources.getStringArray(R.array.localities_nainital).toList()
            nagpur -> resources.getStringArray(R.array.localities_nagpur).toList()
            namchi -> resources.getStringArray(R.array.localities_namchi).toList()
            navi_mumbai -> resources.getStringArray(R.array.localities_navi_mumbai).toList()
            nct_of_delhi -> resources.getStringArray(R.array.localities_delhi_nct).toList()
            noida -> resources.getStringArray(R.array.localities_noida).toList()
            panaji -> resources.getStringArray(R.array.localities_panaji).toList()
            pasighat -> resources.getStringArray(R.array.localities_pasighat).toList()
            patna -> resources.getStringArray(R.array.localities_patna).toList()
            prayagraj -> resources.getStringArray(R.array.localities_prayagraj).toList()
            pune -> resources.getStringArray(R.array.localities_pune).toList()
            port_blair -> resources.getStringArray(R.array.localities_port_blair).toList()
            puducherry -> resources.getStringArray(R.array.localities_puducherry).toList()
            raipur -> resources.getStringArray(R.array.localities_raipur).toList()
            ranchi -> resources.getStringArray(R.array.localities_ranchi).toList()
            rourkela -> resources.getStringArray(R.array.localities_rourkela).toList()
            rohtak -> resources.getStringArray(R.array.localities_rohtak).toList()
            shillong -> resources.getStringArray(R.array.localities_shillong).toList()
            shimla -> resources.getStringArray(R.array.localities_shimla).toList()
            silchar -> resources.getStringArray(R.array.localities_silchar).toList()
            siliguri -> resources.getStringArray(R.array.localities_siliguri).toList()
            sonipat -> resources.getStringArray(R.array.localities_sonipat).toList()
            surat -> resources.getStringArray(R.array.localities_surat).toList()
            secunderabad -> resources.getStringArray(R.array.localities_secunderabad).toList()
            tawang -> resources.getStringArray(R.array.localities_tawang).toList()
            thane -> resources.getStringArray(R.array.localities_thane).toList()
            thiruvananthapuram -> resources.getStringArray(R.array.localities_thiruvananthapuram).toList()
            udaipur -> resources.getStringArray(R.array.localities_udaipur).toList()
            vadodara -> resources.getStringArray(R.array.localities_vadodara).toList()
            varanasi -> resources.getStringArray(R.array.localities_varanasi).toList()
            vellore -> resources.getStringArray(R.array.localities_vellore).toList()
            vijayawada -> resources.getStringArray(R.array.localities_vijayawada).toList()
            visakhapatnam -> resources.getStringArray(R.array.localities_visakhapatnam).toList()
            warangal -> resources.getStringArray(R.array.localities_warangal).toList()
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
                /* city constants */
                agartala, ahmedabad, aizawl, amaravati, amritsar, asansol, bengaluru, bhilai, bhopal,
                bhubaneswar, bilaspur, chandigarh, chennai, coimbatore, cuttack, daman, darjeeling,
                dehradun, dibrugarh, dharamshala, durgapur, faridabad, gangtok, gaya, gandhinagar,
                ghaziabad, gwalior, gyalshing, guwahati, gurugram, haridwar, hisar, howrah, hyderabad,
                imphal, indore, itanagar, jaipur, jamshedpur, jodhpur, kancheepuram, kanpur, kargil,
                kavaratti, kharagpur, kochi, kohima, kolkata, leh, ludhiana, lucknow, madurai, mumbai,
                mangaluru, mysuru, nainital, nagpur, namchi, navi_mumbai, nct_of_delhi, noida, panaji,
                pasighat, patna, prayagraj, pune, port_blair, puducherry, raipur, ranchi, rourkela,
                rohtak, shillong, shimla, silchar, siliguri, sonipat, surat, secunderabad, tawang,
                thane, thiruvananthapuram, udaipur, vadodara, varanasi, vellore, vijayawada,
                visakhapatnam, warangal
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
                /* city constants */
                agartala, ahmedabad, aizawl, amaravati, amritsar, asansol, bengaluru, bhilai, bhopal,
                bhubaneswar, bilaspur, chandigarh, chennai, coimbatore, cuttack, daman, darjeeling,
                dehradun, dibrugarh, dharamshala, durgapur, faridabad, gangtok, gaya, gandhinagar,
                ghaziabad, gwalior, gyalshing, guwahati, gurugram, haridwar, hisar, howrah, hyderabad,
                imphal, indore, itanagar, jaipur, jamshedpur, jodhpur, kancheepuram, kanpur, kargil,
                kavaratti, kharagpur, kochi, kohima, kolkata, leh, ludhiana, lucknow, madurai, mumbai,
                mangaluru, mysuru, nainital, nagpur, namchi, navi_mumbai, nct_of_delhi, noida, panaji,
                pasighat, patna, prayagraj, pune, port_blair, puducherry, raipur, ranchi, rourkela,
                rohtak, shillong, shimla, silchar, siliguri, sonipat, surat, secunderabad, tawang,
                thane, thiruvananthapuram, udaipur, vadodara, varanasi, vellore, vijayawada,
                visakhapatnam, warangal
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
                                registrationViewModel.city = customCity
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
    agartala: String,
    ahmedabad: String,
    aizawl: String,
    amaravati: String,
    amritsar: String,
    asansol: String,
    bengaluru: String,
    bhilai: String,
    bhopal: String,
    bhubaneswar: String,
    bilaspur: String,
    chandigarh: String,
    chennai: String,
    coimbatore: String,
    cuttack: String,
    daman: String,
    darjeeling: String,
    dehradun: String,
    dibrugarh: String,
    dharamshala: String,
    durgapur: String,
    faridabad: String,
    gangtok: String,
    gaya: String,
    gandhinagar: String,
    ghaziabad: String,
    gwalior: String,
    gyalshing: String,
    guwahati: String,
    gurugram: String,
    haridwar: String,
    hisar: String,
    howrah: String,
    hyderabad: String,
    imphal: String,
    indore: String,
    itanagar: String,
    jaipur: String,
    jamshedpur: String,
    jodhpur: String,
    kancheepuram: String,
    kanpur: String,
    kargil: String,
    kavaratti: String,
    kharagpur: String,
    kochi: String,
    kohima: String,
    kolkata: String,
    leh: String,
    ludhiana: String,
    lucknow: String,
    madurai: String,
    mumbai: String,
    mangaluru: String,
    mysuru: String,
    nainital: String,
    nagpur: String,
    namchi: String,
    navi_mumbai: String,
    nct_of_delhi: String,
    noida: String,
    panaji: String,
    pasighat: String,
    patna: String,
    prayagraj: String,
    pune: String,
    port_blair: String,
    puducherry: String,
    raipur: String,
    ranchi: String,
    rourkela: String,
    rohtak: String,
    shillong: String,
    shimla: String,
    silchar: String,
    siliguri: String,
    sonipat: String,
    surat: String,
    secunderabad: String,
    tawang: String,
    thane: String,
    thiruvananthapuram: String,
    udaipur: String,
    vadodara: String,
    varanasi: String,
    vellore: String,
    vijayawada: String,
    visakhapatnam: String,
    warangal: String,
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
                agartala -> ctx.resources.getStringArray(R.array.localities_agartala).toList()
                ahmedabad -> ctx.resources.getStringArray(R.array.localities_ahmedabad).toList()
                aizawl -> ctx.resources.getStringArray(R.array.localities_aizawl).toList()
                amaravati -> ctx.resources.getStringArray(R.array.localities_amaravati).toList()
                amritsar -> ctx.resources.getStringArray(R.array.localities_amritsar).toList()
                asansol -> ctx.resources.getStringArray(R.array.localities_asansol).toList()
                bengaluru -> ctx.resources.getStringArray(R.array.localities_bengaluru).toList()
                bhilai -> ctx.resources.getStringArray(R.array.localities_bhilai).toList()
                bhopal -> ctx.resources.getStringArray(R.array.localities_bhopal).toList()
                bhubaneswar -> ctx.resources.getStringArray(R.array.localities_bhubaneswar).toList()
                bilaspur -> ctx.resources.getStringArray(R.array.localities_bilaspur).toList()
                chandigarh -> ctx.resources.getStringArray(R.array.localities_chandigarh).toList()
                chennai -> ctx.resources.getStringArray(R.array.localities_chennai).toList()
                coimbatore -> ctx.resources.getStringArray(R.array.localities_coimbatore).toList()
                cuttack -> ctx.resources.getStringArray(R.array.localities_cuttack).toList()
                daman -> ctx.resources.getStringArray(R.array.localities_daman).toList()
                darjeeling -> ctx.resources.getStringArray(R.array.localities_darjeeling).toList()
                dehradun -> ctx.resources.getStringArray(R.array.localities_dehradun).toList()
                dibrugarh -> ctx.resources.getStringArray(R.array.localities_dibrugarh).toList()
                dharamshala -> ctx.resources.getStringArray(R.array.localities_dharamshala).toList()
                durgapur -> ctx.resources.getStringArray(R.array.localities_durgapur).toList()
                faridabad -> ctx.resources.getStringArray(R.array.localities_faridabad).toList()
                gangtok -> ctx.resources.getStringArray(R.array.localities_gangtok).toList()
                gaya -> ctx.resources.getStringArray(R.array.localities_gaya).toList()
                gandhinagar -> ctx.resources.getStringArray(R.array.localities_gandhinagar).toList()
                ghaziabad -> ctx.resources.getStringArray(R.array.localities_ghaziabad).toList()
                gwalior -> ctx.resources.getStringArray(R.array.localities_gwalior).toList()
                gyalshing -> ctx.resources.getStringArray(R.array.localities_gyalshing).toList()
                guwahati -> ctx.resources.getStringArray(R.array.localities_guwahati).toList()
                gurugram -> ctx.resources.getStringArray(R.array.localities_gurugram).toList()
                haridwar -> ctx.resources.getStringArray(R.array.localities_haridwar).toList()
                hisar -> ctx.resources.getStringArray(R.array.localities_hisar).toList()
                howrah -> ctx.resources.getStringArray(R.array.localities_howrah).toList()
                hyderabad -> ctx.resources.getStringArray(R.array.localities_hyderabad).toList()
                imphal -> ctx.resources.getStringArray(R.array.localities_imphal).toList()
                indore -> ctx.resources.getStringArray(R.array.localities_indore).toList()
                itanagar -> ctx.resources.getStringArray(R.array.localities_itanagar).toList()
                jaipur -> ctx.resources.getStringArray(R.array.localities_jaipur).toList()
                jamshedpur -> ctx.resources.getStringArray(R.array.localities_jamshedpur).toList()
                jodhpur -> ctx.resources.getStringArray(R.array.localities_jodhpur).toList()
                kancheepuram -> ctx.resources.getStringArray(R.array.localities_kancheepuram).toList()
                kanpur -> ctx.resources.getStringArray(R.array.localities_kanpur).toList()
                kargil -> ctx.resources.getStringArray(R.array.localities_kargil).toList()
                kavaratti -> ctx.resources.getStringArray(R.array.localities_kavaratti).toList()
                kharagpur -> ctx.resources.getStringArray(R.array.localities_kharagpur).toList()
                kochi -> ctx.resources.getStringArray(R.array.localities_kochi).toList()
                kohima -> ctx.resources.getStringArray(R.array.localities_kohima).toList()
                kolkata -> ctx.resources.getStringArray(R.array.localities_kolkata).toList()
                leh -> ctx.resources.getStringArray(R.array.localities_leh).toList()
                ludhiana -> ctx.resources.getStringArray(R.array.localities_ludhiana).toList()
                lucknow -> ctx.resources.getStringArray(R.array.localities_lucknow).toList()
                madurai -> ctx.resources.getStringArray(R.array.localities_madurai).toList()
                mumbai -> ctx.resources.getStringArray(R.array.localities_mumbai).toList()
                mangaluru -> ctx.resources.getStringArray(R.array.localities_mangaluru).toList()
                mysuru -> ctx.resources.getStringArray(R.array.localities_mysuru).toList()
                nainital -> ctx.resources.getStringArray(R.array.localities_nainital).toList()
                nagpur -> ctx.resources.getStringArray(R.array.localities_nagpur).toList()
                namchi -> ctx.resources.getStringArray(R.array.localities_namchi).toList()
                navi_mumbai -> ctx.resources.getStringArray(R.array.localities_navi_mumbai).toList()
                nct_of_delhi -> ctx.resources.getStringArray(R.array.localities_delhi_nct).toList()
                noida -> ctx.resources.getStringArray(R.array.localities_noida).toList()
                panaji -> ctx.resources.getStringArray(R.array.localities_panaji).toList()
                pasighat -> ctx.resources.getStringArray(R.array.localities_pasighat).toList()
                patna -> ctx.resources.getStringArray(R.array.localities_patna).toList()
                prayagraj -> ctx.resources.getStringArray(R.array.localities_prayagraj).toList()
                pune -> ctx.resources.getStringArray(R.array.localities_pune).toList()
                port_blair -> ctx.resources.getStringArray(R.array.localities_port_blair).toList()
                puducherry -> ctx.resources.getStringArray(R.array.localities_puducherry).toList()
                raipur -> ctx.resources.getStringArray(R.array.localities_raipur).toList()
                ranchi -> ctx.resources.getStringArray(R.array.localities_ranchi).toList()
                rourkela -> ctx.resources.getStringArray(R.array.localities_rourkela).toList()
                rohtak -> ctx.resources.getStringArray(R.array.localities_rohtak).toList()
                shillong -> ctx.resources.getStringArray(R.array.localities_shillong).toList()
                shimla -> ctx.resources.getStringArray(R.array.localities_shimla).toList()
                silchar -> ctx.resources.getStringArray(R.array.localities_silchar).toList()
                siliguri -> ctx.resources.getStringArray(R.array.localities_siliguri).toList()
                sonipat -> ctx.resources.getStringArray(R.array.localities_sonipat).toList()
                surat -> ctx.resources.getStringArray(R.array.localities_surat).toList()
                secunderabad -> ctx.resources.getStringArray(R.array.localities_secunderabad).toList()
                tawang -> ctx.resources.getStringArray(R.array.localities_tawang).toList()
                thane -> ctx.resources.getStringArray(R.array.localities_thane).toList()
                thiruvananthapuram -> ctx.resources.getStringArray(R.array.localities_thiruvananthapuram).toList()
                udaipur -> ctx.resources.getStringArray(R.array.localities_udaipur).toList()
                vadodara -> ctx.resources.getStringArray(R.array.localities_vadodara).toList()
                varanasi -> ctx.resources.getStringArray(R.array.localities_varanasi).toList()
                vellore -> ctx.resources.getStringArray(R.array.localities_vellore).toList()
                vijayawada -> ctx.resources.getStringArray(R.array.localities_vijayawada).toList()
                visakhapatnam -> ctx.resources.getStringArray(R.array.localities_visakhapatnam).toList()
                warangal -> ctx.resources.getStringArray(R.array.localities_warangal).toList()
                other -> ctx.resources.getStringArray(R.array.localities_other).toList()
                else -> emptyList()
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

    // Helper: combined list of URIs (first = profile, rest = optional)
    val combinedPhotoUris: List<Uri> = listOfNotNull(registrationViewModel.profilePictureUri) +
            registrationViewModel.optionalPhotoUris

    // Only allow “Next” once at least one photo exists
    val canProceed = registrationViewModel.profilePictureUri != null

    suspend fun isExplicit(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val jpeg = compressImage(context, uri)
        val b64  = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        moderateImages(listOf(b64))
    }

    // 1) Crop launcher
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val outUri = UCrop.getOutput(result.data!!) ?: return@rememberLauncherForActivityResult
            scope.launch {
                if (isExplicit(outUri)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "That photo looks explicit – please choose another.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                if (registrationViewModel.profilePictureUri == null) {
                    registrationViewModel.profilePictureUri = outUri
                    uploadProfilePicToFirebase(context, storageRef, outUri, registrationViewModel)
                } else {
                    registrationViewModel.optionalPhotoUris.add(outUri)
                    uploadOptionalPhoto(context, storageRef, outUri, registrationViewModel)
                }
            }
        }
    }

    // 2) Photo picker → crop
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val destUri = Uri.fromFile(File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg"))
        val uCropIntent = UCrop.of(uri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(800, 800)
            .getIntent(context)
        cropLauncher.launch(uCropIntent)
    }

    // Voice-bio helpers
    fun validateVoiceBio() {
        try {
            val temp = MediaPlayer().apply {
                setDataSource(voiceFilePath)
                prepare()
            }
            voiceDuration = temp.duration.toLong()
            temp.release()
            isVoiceBioValid = voiceDuration <= 60_000
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_media_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
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
            /* ---------------- Upload Photos ---------------- */
            item {
                Text(
                    text = stringResource(R.string.upload_media_title),
                    color = Color.White,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(combinedPhotoUris) { index, uri ->
                        Box(modifier = Modifier.size(100.dp)) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.dp,
                                        color = if (index == 0) Color(0xFFFF6000) else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                            IconButton(
                                onClick = {
                                    if (index == 0) {
                                        registrationViewModel.profilePictureUri = null
                                        if (registrationViewModel.optionalPhotoUris.isNotEmpty()) {
                                            val newProfile =
                                                registrationViewModel.optionalPhotoUris.removeAt(0)
                                            registrationViewModel.profilePictureUri = newProfile
                                            uploadProfilePicToFirebase(
                                                context,
                                                storageRef,
                                                newProfile,
                                                registrationViewModel
                                            )
                                        }
                                    } else {
                                        val optIndex = index - 1
                                        registrationViewModel.optionalPhotoUris.removeAt(optIndex)
                                        registrationViewModel.optionalPhotoUrls.removeAt(optIndex)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                    .padding(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(100.dp)
                                .border(2.dp, Color(0xFFFF6000), CircleShape)
                                .clickable { photoPickerLauncher.launch("image/*") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = stringResource(R.string.add_photos_button),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            /* ---------------- Voice bio (optional) ---------------- */
            item {
                Text(
                    text = stringResource(R.string.voice_bio_label) + "  •  " +
                            stringResource(R.string.optional_voice),
                    color = Color.White,
                    fontSize = 18.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::toggleRecording) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isRecording) Color.Red else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    registrationViewModel.voiceNoteUri?.let {
                        IconButton(onClick = ::togglePlayback) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Slider(
                            value = voiceProgress,
                            onValueChange = {},
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (isPlaying) togglePlayback()
                            registrationViewModel.voiceNoteUri = null
                            File(voiceFilePath).delete()
                            isVoiceBioValid = true
                            voiceProgress = 0f
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete voice",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                if (!isVoiceBioValid) {
                    Text(
                        text = stringResource(R.string.voice_bio_duration_error),
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


/**
 * Uploads an optional photo to Firebase Storage, then adds its URL to ViewModel.
 * (Unchanged from your original helper.)
 */
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
