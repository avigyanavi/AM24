// RegistrationActivity.kt
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.input.ImeAction
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
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.firebase.geofire.GeoFire
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
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
    private var authListener: FirebaseAuth.AuthStateListener? = null    // ≤ NEW


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowPhoneAuth = CountryUtil.isProbablyInIndia(this)

        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check if this registration was initiated via Google sign-up.
        val provider      = intent.getStringExtra("signInProvider") ?: "emailPassword"
        val requested     = intent.getIntExtra("requestedStartStep", 1)
        val initialStep   = if (provider != "emailPassword" && requested == 1) 2 else requested


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
                                this@RegistrationActivity,
                                registrationViewModel,
                                getString(R.string.college_other),
                                allowPhoneAuth
                                ) {
                                // ② only once that’s done, mirror under /publicUsers/{username}
                                val auth = FirebaseAuth.getInstance()
                                val db   = FirebaseRefs.db.reference
                                auth.currentUser?.uid?.let { uid ->
                                    db.child("users").child(uid).child("username").get()
                                        .addOnSuccessListener { snap ->
                                            val username = snap.getValue(String::class.java) ?: return@addOnSuccessListener
                                            val signInMethod = provider          // already one of the three strings
                                            db.child("publicUsers")
                                                .child(username)
                                                .setValue(mapOf(
                                                    "email"                to auth.currentUser?.email,
                                                    "signInMethod"         to signInMethod,
                                                    "registrationFinished" to true
                                                ))
                                            db.child("users")
                                                .child(uid)
                                                .child("registrationFinished")
                                                .setValue(true)
                                            FirebaseRefs.db.reference.child("users/$uid/registrationStep").removeValue()
                                                .addOnSuccessListener {
                                                    auth.currentUser?.sendEmailVerification()
                                                    startActivity(Intent(this@RegistrationActivity, MainActivity::class.java))
                                                    finish()
                                                }
                                        }
                                }
                            }
                        }
                    },
                    allowPhoneAuth   = allowPhoneAuth,
                    fusedLocationClient = fusedLocationClient,
                    initialStep = initialStep
                )
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Retrieve the language code from SharedPreferences (default "en")
        val prefs = newBase.getSharedPreferences("settings", MODE_PRIVATE)
        val defaultLang = if (Locale.getDefault().country.equals("MX", true)) "es" else "en"
        val languageCode = prefs.getString("language", defaultLang) ?: defaultLang
        val updatedContext = updateLocale(newBase, languageCode)
        super.attachBaseContext(updatedContext)
    }
}

suspend fun claimPhoneNumber(
    db: DatabaseReference,
    e164: String,      // “+919876543210”
    uid: String
): Boolean = suspendCancellableCoroutine { cont ->
    db.child("phoneNumbers").child(e164)
        .runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                return if (current.value == null) {
                    current.value = uid                 // reserve it
                    Transaction.success(current)
                } else {
                    Transaction.abort()                 // someone else has it
                }
            }
            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                cont.resume(committed) {}               // true = success
            }
        })
}

private fun saveStep(step: Int) {
    FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
        FirebaseRefs.db.reference.child("users/$uid/registrationStep")
            .setValue(step)
    }
}

class RegistrationViewModel : ViewModel() {

    var nextEnabled by mutableStateOf(false)
    var selectedLanguage by mutableStateOf("en") // Options: "en", "bn", "hi"
    var city by mutableStateOf("")
    var customCity by mutableStateOf("")
    var phoneNumber by mutableStateOf("")   // <── add this line
    // RegistrationViewModel
    var country       by mutableStateOf("")
    var customCountry by mutableStateOf("")
    var allowLocationForMatches by mutableStateOf(true)
    var allowLocationPublic by mutableStateOf(true)
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
    var honeypot by mutableStateOf("")
    var name by mutableStateOf("")
    var username by mutableStateOf("")
    var dob by mutableStateOf("")
    var interests = mutableStateListOf<Interest>()
    var profilePictureUri by mutableStateOf<Uri?>(null)
    var optionalPhotoUris = mutableStateListOf<Uri>()
    var profilePicUrl by mutableStateOf<String?>(null)
    var voiceNoteUrl by mutableStateOf<String?>(null)
    var optionalPhotoUrls = mutableStateListOf<String>()
    var privateAlbumUris = mutableStateListOf<Uri>()
    var privateAlbumUrls = mutableStateListOf<String>()

    var height by mutableStateOf(0)            // Height in centimeters
    var height2 by mutableStateOf(listOf(0, 0))  // Height in feet + inches (default example: 5'7")
    var isHeightInFeet by mutableStateOf(false)  // Toggle for height unit preference (cm or feet+inches)

    // Hometown and Education
    var hometown by mutableStateOf("")     // Treated as Locality
    var bio by mutableStateOf("")
    var gender by mutableStateOf("")
    var customHometown by mutableStateOf("")
    var religion by mutableStateOf("")
    var community by mutableStateOf("")
    var ethnicity by mutableStateOf("")
    var incomeLevel by mutableStateOf("")
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

    // Roles, Tribes, Body type and Kinks
    var roles = mutableStateListOf<String>()
    var showRolesOnProfile by mutableStateOf(true)
    var tribes = mutableStateListOf<String>()
    var showTribesOnProfile by mutableStateOf(true)
    var bodyType by mutableStateOf("")
    var showBodyTypeOnProfile by mutableStateOf(true)
    var kinks = mutableStateListOf<String>()
    var showKinksOnProfile by mutableStateOf(false)

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
    allowPhoneAuth: Boolean,
    fusedLocationClient: FusedLocationProviderClient,
    initialStep: Int
) {
    val registrationViewModel: RegistrationViewModel = viewModel()
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(initialStep) }
    val totalSteps = 8 // now includes orientation screen
    val progress = currentStep.toFloat() / totalSteps.toFloat()
    val displayProgress = when (currentStep) {
        1           -> 0f      // Step-1 should read 0 %
        totalSteps  -> 0.99f   // Keep the 99 % cap on the last step
        else        -> progress
    }
    val onNext = {
        registrationViewModel.nextEnabled = false
        currentStep += 1
        saveStep(currentStep)
    }
    val onBack: () -> Unit = {
        when {
            // Back from step 1 -> return to landing
            currentStep == 1 -> {
                val activity = context as? Activity
                activity?.startActivity(Intent(context, LandingActivity::class.java))
                activity?.finish()
            }
            // BACK from Step 2 → Step 1: delete half-baked account, clear email/password, go to step 1
            currentStep == 2 -> {
                cleanupIncompleteUser(
                    FirebaseAuth.getInstance(),
                    FirebaseRefs.db,       //  ✨ NO “.getInstance()” here
                    FirebaseRefs.storage   // instead of FirebaseStorage.getInstance()
                )
                registrationViewModel.email = ""
                registrationViewModel.password = ""
                currentStep = 1
            }
            // any other back (steps > 2) just go back a step
            currentStep > 2 -> {
                currentStep -= 1
                saveStep(currentStep)
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
                /* NEW → one global “Next” icon, enabled ⇔ viewModel.nextEnabled */
                /* NEW → one global “Next” icon */
                actions = {
                    IconButton(
                        onClick = {
                            if (registrationViewModel.nextEnabled) {
                                onNext()
                            } else {
                                when (currentStep) {
                                    2 -> {
                                        when {
                                            registrationViewModel.dob.isBlank() ->
                                                Toast.makeText(context, "Please select your birth date", Toast.LENGTH_LONG).show()
                                            calculateAge(registrationViewModel.dob) < 14 ->
                                                Toast.makeText(context, "You must be at least 14 years old", Toast.LENGTH_LONG).show()
                                            else -> {}
                                        }
                                    }
                                    8 -> {
                                        Toast.makeText(context, "Pick a valid username and tap Finish", Toast.LENGTH_LONG).show()
                                    }
                                    else -> {
                                        Toast.makeText(context, "Please complete the required fields", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next",
                            tint = Color.White
                        )
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
                    progress = displayProgress,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color(0xFFFF6000),
                    trackColor = Color.Gray
                )
                 Spacer(Modifier.height(4.dp))
                 Text(
                     text = "${(displayProgress * 100).roundToInt()}% completed",
                     color = Color.White,
                     fontSize = 12.sp,
                     modifier = Modifier.align(Alignment.End)
                 )
                Spacer(modifier = Modifier.height(16.dp))
                when (currentStep) {
                    1 -> EnterEmailAndPasswordScreen(registrationViewModel, allowPhoneAuth, onNext, onBack)
                    2 -> EnterGenderCommunityReligionScreen(registrationViewModel, onNext)   // now includes DOB
                    3 -> UploadMediaComposable(registrationViewModel, onNext, onBack)
                    4 -> EnterBirthdateCityHometownScreen(registrationViewModel, onNext, fusedLocationClient)
//                    5 -> EnterInterestsScreen(registrationViewModel, onNext)
                    5 -> EnterOrientationScreen(registrationViewModel, onNext)
//                    7 -> EnterLifestyleScreen(registrationViewModel, onNext)
                    6 -> EnterUsernameScreen(registrationViewModel, onRegistrationComplete, onBack)
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
//                                    cleanupIncompleteUser(auth, FirebaseDatabase.getInstance("https://kupidxdefault.asia-southeast1.firebasedatabase.app/"),
//                                        storage)
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
            val logger = AppEventsLogger.newLogger(MyApp.instance)
            logger.logEvent(AppEventsConstants.EVENT_NAME_ACTIVATED_APP)
            logger.logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION)
            onSuccess()
        }
        .addOnFailureListener { e ->
            onError("Registration failed: ${e.message}")
        }
}

//@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
//@Composable
//fun EnterPersonalDetailsScreen(
//    viewModel: RegistrationViewModel,
//    onNext: () -> Unit,
//    onBack: () -> Unit
//) {
//    val lookingForOptions = listOf(
//        stringResource(R.string.looking_for_not_selected),
//        stringResource(R.string.looking_for_romance),
//        stringResource(R.string.looking_for_connection),
//        stringResource(R.string.looking_for_partner),
//        stringResource(R.string.looking_for_marriage)
//    )
//    val loveLanguageOptions = listOf(
//        stringResource(R.string.love_language_option_not_selected),
//        stringResource(R.string.love_language_option_words_of_affirmation),
//        stringResource(R.string.love_language_option_acts_of_service),
//        stringResource(R.string.love_language_option_receiving_gifts),
//        stringResource(R.string.love_language_option_quality_time),
//        stringResource(R.string.love_language_option_physical_touch),
//        stringResource(R.string.love_language_option_other),
//    )
//    val politicsOptions = listOf(
//        stringResource(R.string.politics_option_far_left),
//        stringResource(R.string.politics_option_left),
//        stringResource(R.string.politics_option_centre_left),
//        stringResource(R.string.politics_option_centre),
//        stringResource(R.string.politics_option_centre_right),
//        stringResource(R.string.politics_option_right),
//        stringResource(R.string.politics_option_far_right),
//        stringResource(R.string.politics_option_liberal),
//        stringResource(R.string.politics_option_conservative),
//        stringResource(R.string.politics_option_moderate),
//        stringResource(R.string.politics_option_socialist),
//        stringResource(R.string.politics_option_communist),
//        stringResource(R.string.politics_option_other)
//    )
//
//    var lookingFor by remember { mutableStateOf(viewModel.lookingFor) }
//    var loveLanguage by remember { mutableStateOf(viewModel.loveLanguage) }
//    var politics by remember { mutableStateOf(viewModel.politics) }
//
//    // Social Causes
//    val allCauses = stringArrayResource(R.array.social_causes_list).toList()
//    val maxSelections = 5
//
//    // Focus management
//    val focusManager = LocalFocusManager.current
//    LaunchedEffect(Unit) {
//        // Clear focus on screen entry to prevent automatic focus on the text field
//        focusManager.clearFocus()
//    }
//
//    Scaffold(
//        content = { innerPadding ->
//            LazyColumn(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(Color(0xFF1A1A1A))
//                    .padding(innerPadding)
//                    .padding(horizontal = 16.dp, vertical = 8.dp),
//                verticalArrangement = Arrangement.spacedBy(16.dp)
//            ) {
//                item {
//                    DropdownWithStaticOptions(
//                        label = stringResource(R.string.looking_for_label),
//                        options = lookingForOptions,
//                        selectedOption = lookingFor,
//                        onOptionSelected = {
//                            lookingFor = it
//                            viewModel.lookingFor = it
//                        }
//                    )
//                }
//
//                item {
//                    DropdownWithStaticOptions(
//                        label = stringResource(R.string.love_language_label),
//                        options = loveLanguageOptions,
//                        selectedOption = loveLanguage,
//                        onOptionSelected = {
//                            loveLanguage = it
//                            viewModel.loveLanguage = it
//                        }
//                    )
//                }
//
//                item {
//                    DropdownWithStaticOptions(
//                        label = stringResource(R.string.label_politics),
//                        options = politicsOptions,
//                        selectedOption = politics,
//                        onOptionSelected = {
//                            politics = it
//                            viewModel.politics = it
//                        }
//                    )
//                }
//
//                item {
//                    Column {
//                        Text(
//                            stringResource(R.string.social_causes),
//                            color = Color.White,
//                            fontSize = 16.sp,
//                            fontWeight = FontWeight.Bold
//                        )
//                        Spacer(Modifier.height(8.dp))
//                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//                            allCauses.forEach { cause ->
//                                val isSelected = viewModel.socialCauses.contains(cause)
//                                val canSelectMore = viewModel.socialCauses.size < maxSelections
//                                FilterChip(
//                                    selected = isSelected,
//                                    onClick = {
//                                        if (isSelected) {
//                                            viewModel.socialCauses.remove(cause)
//                                        } else if (canSelectMore) {
//                                            viewModel.socialCauses.add(cause)
//                                        }
//                                    },
//                                    enabled = isSelected || canSelectMore,
//                                    label = { Text(cause) },
//                                    colors = FilterChipDefaults.filterChipColors(
//                                        selectedContainerColor = Color(0xFFFF6F00),
//                                        selectedLabelColor = Color.White,
//                                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
//                                        disabledLabelColor = Color.LightGray
//                                    )
//                                )
//                            }
//                        }
//                        if (viewModel.socialCauses.size > maxSelections) {
//                            Text(
//                                text = stringResource(R.string.max_social_causes_error, maxSelections),
//                                color = Color.Red,
//                                modifier = Modifier.padding(top = 8.dp)
//                            )
//                        }
//                    }
//                }
//
//                item {
//                    Button(
//                        onClick = onNext,
//                        modifier = Modifier.fillMaxWidth(),
//                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
//                    ) {
//                        Text(stringResource(R.string.next_button), color = Color.White)
//                    }
//                }
//            }
//        }
//    )
//}

@Composable
fun RegistrationAccordion(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1A1A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White
            )
        }
        if (expanded) {
            Divider(color = Color(0xFFFF6F00))
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterLifestyleScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit
) {
    LaunchedEffect(Unit) { registrationViewModel.nextEnabled = true }
    Scaffold(
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
                    .padding(innerPadding)
            ) {
                // 1. Exercise Frequency
                RegistrationAccordion(title = stringResource(R.string.section_lifestyle_attributes)) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_exercise),
                            value = registrationViewModel.lifestyle.exercise_frequency,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(exercise_frequency = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.inactive),
                                stringResource(R.string.rarely_active),
                                stringResource(R.string.moderately_active),
                                stringResource(R.string.active),
                                stringResource(R.string.very_active)
                            )
                        )


                        // 2. Adventurousness

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_adventurousness),
                            value = registrationViewModel.lifestyle.adventurousness,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(adventurousness = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.cautious),
                                stringResource(R.string.slightly_adventurous),
                                stringResource(R.string.moderately_adventurous),
                                stringResource(R.string.adventurous),
                                stringResource(R.string.thrill_seeker)
                            )
                        )


                        // 3. Intellectual Curiosity

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_intellectual_curiosity),
                            value = registrationViewModel.lifestyle.intellectual_curiosity,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(intellectual_curiosity = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.casual_thinker),
                                stringResource(R.string.inquisitive),
                                stringResource(R.string.knowledge_seeker),
                                stringResource(R.string.intellectual),
                                stringResource(R.string.philosopher)
                            )
                        )


                        // 4. Smoking Habit

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_smoking),
                            value = registrationViewModel.lifestyle.smoking_habit,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(smoking_habit = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.non_smoker),
                                stringResource(R.string.rare_smoker),
                                stringResource(R.string.social_smoker),
                                stringResource(R.string.frequent_smoker),
                                stringResource(R.string.heavy_smoker)
                            )
                        )


                        // 5. Drinking Habit

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_drinking),
                            value = registrationViewModel.lifestyle.drinking_habit,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(drinking_habit = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.non_drinker),
                                stringResource(R.string.rare_drinker),
                                stringResource(R.string.social_drinker),
                                stringResource(R.string.frequent_drinker),
                                stringResource(R.string.heavy_drinker)
                            )
                        )


                        // 6. Work–Life Balance

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_work_life_balance),
                            value = registrationViewModel.lifestyle.work_life_balance,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(work_life_balance = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.workaholic),
                                stringResource(R.string.more_work_oriented),
                                stringResource(R.string.balanced),
                                stringResource(R.string.more_life_oriented),
                                stringResource(R.string.relaxed)
                            )
                        )


                        // 7. Sleep Pattern

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_sleep),
                            value = registrationViewModel.lifestyle.sleep_pattern,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(sleep_pattern = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.early_riser),
                                stringResource(R.string.morning_person),
                                stringResource(R.string.balanced),
                                stringResource(R.string.night_owl),
                                stringResource(R.string.late_night_enthusiast)
                            )
                        )


                        // 8. Creative Expression

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_creative_expression),
                            value = registrationViewModel.lifestyle.creative_expression,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(creative_expression = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.not_creative),
                                stringResource(R.string.somewhat_creative),
                                stringResource(R.string.creative),
                                stringResource(R.string.very_creative),
                                stringResource(R.string.artistic_genius)
                            )
                        )


                        // 9. Physical Fitness

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_physical_fitness),
                            value = registrationViewModel.lifestyle.physical_fitness,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(physical_fitness = it)
                            },
                            nouns = listOf(
                                stringResource(R.string.sedentary),
                                stringResource(R.string.somewhat_fit),
                                stringResource(R.string.fit),
                                stringResource(R.string.athletic),
                                stringResource(R.string.peak_fitness)
                            )
                        )


                        // 10. Professional Ambition

                        LifestyleSlider(
                            label = stringResource(R.string.lifestyle_professional_ambition),
                            value = registrationViewModel.lifestyle.professional_ambition,
                            valueRangeStart = 0,
                            valueRangeEnd = 4,
                            onValueChange = {
                                registrationViewModel.lifestyle =
                                    registrationViewModel.lifestyle.copy(professional_ambition = it)
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
                }

//                item {
//                    Spacer(modifier = Modifier.height(24.dp))
//                    Button(
//                        onClick = { onNext() },
//                        modifier = Modifier.fillMaxWidth(),
//                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
//                    ) {
//                        Text(text = stringResource(R.string.next_button), color = Color.White)
//                    }
//                }
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

//@Composable
//fun DropdownWithStaticOptions(
//    label: String,
//    options: List<String>,
//    selectedOption: String,
//    onOptionSelected: (String) -> Unit
//) {
//    var expanded by remember { mutableStateOf(false) }
//
//    Column {
//        Text(text = label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
//        OutlinedButton(
//            onClick = { expanded = !expanded },
//            modifier = Modifier.fillMaxWidth(),
//            border = BorderStroke(1.dp, Color(0xFFFF6000)),
//            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6000))
//        ) {
//            Text(text = selectedOption.ifEmpty { stringResource(R.string.select_default) }, fontSize = 12.sp, color = Color.White)
//        }
//
//        DropdownMenu(
//            expanded = expanded,
//            onDismissRequest = { expanded = false },
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(Color(0xFF1A1A1A))
//        ) {
//            options.forEach { option ->
//                DropdownMenuItem(
//                    text = { Text(option, color = Color.White) },
//                    onClick = {
//                        onOptionSelected(option)
//                        expanded = false
//                    }
//                )
//            }
//        }
//    }
//}

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun EnterLocationAndSchoolScreen(
//    registrationViewModel: RegistrationViewModel,
//    onNext: () -> Unit,
//    onBack: () -> Unit
//) {
//    val educationLevels = listOf(
//        stringResource(R.string.no_education_label),
//        stringResource(R.string.high_school_label),
//        stringResource(R.string.college_label),
//        stringResource(R.string.post_graduation_label)
//    )
//
//    val other = stringResource(R.string.job_role_option_other)
//
//    val jobRoleOptions = listOf(
//        stringResource(R.string.job_role_option_software_developer),
//        stringResource(R.string.job_role_option_data_scientist),
//        stringResource(R.string.job_role_option_ux_ui_designer),
//        stringResource(R.string.job_role_option_civil_engineer),
//        stringResource(R.string.job_role_option_mechanical_engineer),
//        stringResource(R.string.job_role_option_electrical_engineer),
//        stringResource(R.string.job_role_option_project_manager),
//        stringResource(R.string.job_role_option_product_manager),
//        stringResource(R.string.job_role_option_business_analyst),
//        stringResource(R.string.job_role_option_accountant),
//        stringResource(R.string.job_role_option_chartered_accountant),
//        stringResource(R.string.job_role_option_hr_manager),
//        stringResource(R.string.job_role_option_marketing_manager),
//        stringResource(R.string.job_role_option_sales_executive),
//        stringResource(R.string.job_role_option_director),
//        stringResource(R.string.job_role_option_ceo),
//        stringResource(R.string.job_role_option_teacher),
//        stringResource(R.string.job_role_option_professor),
//        stringResource(R.string.job_role_option_researcher),
//        stringResource(R.string.job_role_option_scientist),
//        stringResource(R.string.job_role_option_doctor),
//        stringResource(R.string.job_role_option_surgeon),
//        stringResource(R.string.job_role_option_nurse),
//        stringResource(R.string.job_role_option_pharmacist),
//        stringResource(R.string.job_role_option_lawyer),
//        stringResource(R.string.job_role_option_advocate),
//        stringResource(R.string.job_role_option_legal_consultant),
//        stringResource(R.string.job_role_option_graphic_designer),
//        stringResource(R.string.job_role_option_content_writer),
//        stringResource(R.string.job_role_option_photographer),
//        stringResource(R.string.job_role_option_journalist),
//        stringResource(R.string.job_role_option_editor),
//        stringResource(R.string.job_role_option_chef),
//        stringResource(R.string.job_role_option_barista),
//        stringResource(R.string.job_role_option_pilot),
//        stringResource(R.string.job_role_option_flight_attendant),
//        stringResource(R.string.job_role_option_police_officer),
//        stringResource(R.string.job_role_option_firefighter),
//        stringResource(R.string.job_role_option_army_officer),
//        stringResource(R.string.job_role_option_electrician),
//        stringResource(R.string.job_role_option_plumber),
//        stringResource(R.string.job_role_option_carpenter),
//        stringResource(R.string.job_role_option_mechanic),
//        stringResource(R.string.job_role_option_entrepreneur),
//        stringResource(R.string.job_role_option_intern),
//        other
//    )
//
//    var work by remember { mutableStateOf(registrationViewModel.work) }
//
//    // Work search state
//    var workQuery by remember { mutableStateOf(registrationViewModel.work) }
//    var workResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
//    var workSearching by remember { mutableStateOf(false) }
//    var isWorkFieldFocused by remember { mutableStateOf(false) } // Track focus state
//    val workMenuExpanded = workResults.isNotEmpty() && isWorkFieldFocused // Only expand if focused
//
//
//    // State for place search queries and results
//    var highSchoolQuery by remember { mutableStateOf(registrationViewModel.highSchool) }
//    var collegeQuery by remember { mutableStateOf(registrationViewModel.college) }
//    var postGradQuery by remember { mutableStateOf(registrationViewModel.postGraduation) }
//    var highSchoolResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
//    var collegeResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
//    var postGradResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
//    var highSchoolSearching by remember { mutableStateOf(false) }
//    var collegeSearching by remember { mutableStateOf(false) }
//    var postGradSearching by remember { mutableStateOf(false) }
//    var isHighSchoolFieldFocused by remember { mutableStateOf(false) } // Track focus state for high school
//    var isCollegeFieldFocused by remember { mutableStateOf(false) } // Track focus state for college
//    var isPostGradFieldFocused by remember { mutableStateOf(false) } // Track focus state for post-grad
//    val highSchoolMenuExpanded = highSchoolResults.isNotEmpty() && isHighSchoolFieldFocused // Only expand if focused
//    val collegeMenuExpanded = collegeResults.isNotEmpty() && isCollegeFieldFocused // Only expand if focused
//    val postGradMenuExpanded = postGradResults.isNotEmpty() && isPostGradFieldFocused // Only expand if focused
//
//    // Enable Next button only if education level is selected and required fields are filled
//    val isNextEnabled = registrationViewModel.educationLevel.isNotEmpty() &&
//            (registrationViewModel.educationLevel == stringResource(R.string.no_education_label) ||
//                    registrationViewModel.highSchool.isNotEmpty()) &&
//            (registrationViewModel.educationLevel != stringResource(R.string.college_label) ||
//                    registrationViewModel.college.isNotEmpty()) &&
//            (registrationViewModel.educationLevel != stringResource(R.string.post_graduation_label) ||
//                    registrationViewModel.postGraduation.isNotEmpty())
//
//    // Focus management
//    val focusManager = LocalFocusManager.current
//    LaunchedEffect(Unit) {
//        // Clear focus on screen entry to prevent automatic focus on any text field
//        focusManager.clearFocus()
//    }
//
//    // Place search side-effects with debouncing
//    LaunchedEffect(highSchoolQuery) {
//        if (highSchoolQuery.length < 3) {
//            highSchoolResults = emptyList()
//            return@LaunchedEffect
//        } else {
//            delay(400) // Debounce to prevent excessive API calls
//            highSchoolSearching = true
//            highSchoolResults = searchPlacesRich(highSchoolQuery)
//            highSchoolSearching = false
//        }
//    }
//
//    LaunchedEffect(collegeQuery) {
//        if (collegeQuery.length < 3) {
//            collegeResults = emptyList()
//            return@LaunchedEffect
//        } else {
//            delay(400)
//            collegeSearching = true
//            collegeResults = searchPlacesRich(collegeQuery)
//            collegeSearching = false
//        }
//    }
//
//    LaunchedEffect(postGradQuery) {
//        if (postGradQuery.length < 3) {
//            postGradResults = emptyList()
//            return@LaunchedEffect
//        } else {
//            delay(400)
//            postGradSearching = true
//            postGradResults = searchPlacesRich(postGradQuery)
//            postGradSearching = false
//        }
//    }
//
//
//    // Work search with debouncing
//    LaunchedEffect(workQuery) {
//        if (workQuery.length < 3) {
//            workResults = emptyList()
//            return@LaunchedEffect
//        } else {
//            delay(400) // Debounce
//            workSearching = true
//            workResults = searchPlacesRich(workQuery) // Use "establishment" for workplaces
//            workSearching = false
//        }
//    }
//
//
//    Scaffold(
//        content = { innerPadding ->
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(Color(0xFF1A1A1A))
//                    .padding(innerPadding)
//                    .verticalScroll(rememberScrollState())
//                    .padding(horizontal = 32.dp, vertical = 16.dp),
//                verticalArrangement = Arrangement.Top,
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                // Section Title
////                Text(
////                    text = stringResource(R.string.education_and_location_title),
////                    color = Color.White,
////                    fontSize = 24.sp,
////                    fontWeight = FontWeight.Bold,
////                    modifier = Modifier.padding(bottom = 24.dp)
////                )
//
////                // Education Level Dropdown
////                Text(
////                    text = stringResource(R.string.education_level_label),
////                    color = Color.White,
////                    fontSize = 18.sp,
////                    fontWeight = FontWeight.Bold
////                )
//                DropdownWithSearch(
//                    title = stringResource(R.string.select_education_level),
//                    options = educationLevels,
//                    selectedOption = registrationViewModel.educationLevel,
//                    onOptionSelected = { registrationViewModel.educationLevel = it }
//                )
//
//                // High School Section
//                if (registrationViewModel.educationLevel in listOf(
//                        stringResource(R.string.high_school_label),
//                        stringResource(R.string.college_label),
//                        stringResource(R.string.post_graduation_label)
//                    )) {
//                    Spacer(modifier = Modifier.height(16.dp))
//                    Text(
//                        text = stringResource(R.string.high_school_label),
//                        color = Color.White,
//                        fontSize = 18.sp,
//                        fontWeight = FontWeight.Bold
//                    )
//                    ExposedDropdownMenuBox(
//                        expanded = highSchoolMenuExpanded,
//                        onExpandedChange = { /* Controlled by results and focus */ },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        val focusRequester = remember { FocusRequester() } // For focus tracking
//                        OutlinedTextField(
//                            value = highSchoolQuery,
//                            onValueChange = { query ->
//                                highSchoolQuery = query
//                                registrationViewModel.highSchool = query
//                                registrationViewModel.customHighSchool = query
//                            },
//                            label = { Text(stringResource(R.string.select_or_type_high_school)) },
//                            singleLine = true,
//                            trailingIcon = {
//                                if (highSchoolSearching)
//                                    CircularProgressIndicator(
//                                        strokeWidth = 2.dp,
//                                        modifier = Modifier.size(18.dp)
//                                    )
//                                else
//                                    Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
//                            },
//                            colors = TextFieldDefaults.outlinedTextFieldColors(
//                                focusedBorderColor = Color(0xFFFF6000),
//                                unfocusedBorderColor = Color.White,
//                                cursorColor = Color.White,
//                                focusedLabelColor = Color(0xFFFF6000),
//                                unfocusedLabelColor = Color.White,
//                                focusedTextColor = Color.White,
//                                unfocusedTextColor = Color.White
//                            ),
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .menuAnchor()
//                                .focusRequester(focusRequester)
//                                .onFocusChanged { focusState ->
//                                    isHighSchoolFieldFocused = focusState.isFocused
//                                    if (!focusState.isFocused) {
//                                        highSchoolResults = emptyList() // Clear results when focus is lost
//                                    }
//                                }
//                        )
//                        ExposedDropdownMenu(
//                            expanded = highSchoolMenuExpanded,
//                            onDismissRequest = {
//                                highSchoolResults = emptyList()
//                                focusManager.clearFocus() // Clear focus when dismissing the dropdown
//                            },
//                            modifier = Modifier
//                                .background(Color.White, RoundedCornerShape(6.dp))
//                                .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
//                        ) {
//                            highSchoolResults.forEach { res ->
//                                DropdownMenuItem(
//                                    text = {
//                                        Column {
//                                            Text(res.name, color = Color.Black)
//                                            if (res.address.isNotBlank())
//                                                Text(
//                                                    res.address,
//                                                    color = Color.DarkGray,
//                                                    style = MaterialTheme.typography.bodySmall
//                                                )
//                                        }
//                                    },
//                                    leadingIcon = {
//                                        Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500))
//                                    },
//                                    onClick = {
//                                        registrationViewModel.highSchool = res.name
//                                        registrationViewModel.customHighSchool = ""
//                                        highSchoolQuery = res.name
//                                        highSchoolResults = emptyList()
//                                        focusManager.clearFocus() // Clear focus after selection
//                                    }
//                                )
//                            }
//                        }
//                    }
//                    Spacer(modifier = Modifier.height(8.dp))
//                    GraduationYearDropdown(
//                        year = registrationViewModel.highSchoolGraduationYear,
//                        onYearSelected = { registrationViewModel.highSchoolGraduationYear = it }
//                    )
//                }
//
//                // College Section
//                if (registrationViewModel.educationLevel in listOf(
//                        stringResource(R.string.college_label),
//                        stringResource(R.string.post_graduation_label)
//                    )) {
//                    Spacer(modifier = Modifier.height(16.dp))
//                    Text(
//                        text = stringResource(R.string.college_label),
//                        color = Color.White,
//                        fontSize = 18.sp,
//                        fontWeight = FontWeight.Bold
//                    )
//                    ExposedDropdownMenuBox(
//                        expanded = collegeMenuExpanded,
//                        onExpandedChange = { /* Controlled by results and focus */ },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        val focusRequester = remember { FocusRequester() } // For focus tracking
//                        OutlinedTextField(
//                            value = collegeQuery,
//                            onValueChange = { query ->
//                                collegeQuery = query
//                                registrationViewModel.college = query
//                                registrationViewModel.customCollege = query
//                            },
//                            label = { Text(stringResource(R.string.select_or_type_college)) },
//                            singleLine = true,
//                            trailingIcon = {
//                                if (collegeSearching)
//                                    CircularProgressIndicator(
//                                        strokeWidth = 2.dp,
//                                        modifier = Modifier.size(18.dp)
//                                    )
//                                else
//                                    Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
//                            },
//                            colors = TextFieldDefaults.outlinedTextFieldColors(
//                                focusedBorderColor = Color(0xFFFF6000),
//                                unfocusedBorderColor = Color.White,
//                                cursorColor = Color.White,
//                                focusedLabelColor = Color(0xFFFF6000),
//                                unfocusedLabelColor = Color.White,
//                                focusedTextColor = Color.White,
//                                unfocusedTextColor = Color.White
//                            ),
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .menuAnchor()
//                                .focusRequester(focusRequester)
//                                .onFocusChanged { focusState ->
//                                    isCollegeFieldFocused = focusState.isFocused
//                                    if (!focusState.isFocused) {
//                                        collegeResults = emptyList() // Clear results when focus is lost
//                                    }
//                                }
//                        )
//                        ExposedDropdownMenu(
//                            expanded = collegeMenuExpanded,
//                            onDismissRequest = {
//                                collegeResults = emptyList()
//                                focusManager.clearFocus() // Clear focus when dismissing the dropdown
//                            },
//                            modifier = Modifier
//                                .background(Color.White, RoundedCornerShape(6.dp))
//                                .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
//                        ) {
//                            collegeResults.forEach { res ->
//                                DropdownMenuItem(
//                                    text = {
//                                        Column {
//                                            Text(res.name, color = Color.Black)
//                                            if (res.address.isNotBlank())
//                                                Text(
//                                                    res.address,
//                                                    color = Color.DarkGray,
//                                                    style = MaterialTheme.typography.bodySmall
//                                                )
//                                        }
//                                    },
//                                    leadingIcon = {
//                                        Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500))
//                                    },
//                                    onClick = {
//                                        registrationViewModel.college = res.name
//                                        registrationViewModel.customCollege = ""
//                                        collegeQuery = res.name
//                                        collegeResults = emptyList()
//                                        focusManager.clearFocus() // Clear focus after selection
//                                    }
//                                )
//                            }
//                        }
//                    }
//                    Spacer(modifier = Modifier.height(8.dp))
//                    GraduationYearDropdown(
//                        year = registrationViewModel.collegeGraduationYear,
//                        onYearSelected = { registrationViewModel.collegeGraduationYear = it }
//                    )
//                }
//
//                // Post-Graduation Section
//                if (registrationViewModel.educationLevel == stringResource(R.string.post_graduation_label)) {
//                    Spacer(modifier = Modifier.height(16.dp))
//                    Text(
//                        text = stringResource(R.string.post_graduation_label),
//                        color = Color.White,
//                        fontSize = 18.sp,
//                        fontWeight = FontWeight.Bold
//                    )
//                    ExposedDropdownMenuBox(
//                        expanded = postGradMenuExpanded,
//                        onExpandedChange = { /* Controlled by results and focus */ },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        val focusRequester = remember { FocusRequester() } // For focus tracking
//                        OutlinedTextField(
//                            value = postGradQuery,
//                            onValueChange = { query ->
//                                postGradQuery = query
//                                registrationViewModel.postGraduation = query
//                                registrationViewModel.customPostGraduation = query
//                            },
//                            label = { Text(stringResource(R.string.select_or_type_post_grad)) },
//                            singleLine = true,
//                            trailingIcon = {
//                                if (postGradSearching)
//                                    CircularProgressIndicator(
//                                        strokeWidth = 2.dp,
//                                        modifier = Modifier.size(18.dp)
//                                    )
//                                else
//                                    Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
//                            },
//                            colors = TextFieldDefaults.outlinedTextFieldColors(
//                                focusedBorderColor = Color(0xFFFF6000),
//                                unfocusedBorderColor = Color.White,
//                                cursorColor = Color.White,
//                                focusedLabelColor = Color(0xFFFF6000),
//                                unfocusedLabelColor = Color.White,
//                                focusedTextColor = Color.White,
//                                unfocusedTextColor = Color.White
//                            ),
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .menuAnchor()
//                                .focusRequester(focusRequester)
//                                .onFocusChanged { focusState ->
//                                    isPostGradFieldFocused = focusState.isFocused
//                                    if (!focusState.isFocused) {
//                                        postGradResults = emptyList() // Clear results when focus is lost
//                                    }
//                                }
//                        )
//                        ExposedDropdownMenu(
//                            expanded = postGradMenuExpanded,
//                            onDismissRequest = {
//                                postGradResults = emptyList()
//                                focusManager.clearFocus() // Clear focus when dismissing the dropdown
//                            },
//                            modifier = Modifier
//                                .background(Color.White, RoundedCornerShape(6.dp))
//                                .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
//                        ) {
//                            postGradResults.forEach { res ->
//                                DropdownMenuItem(
//                                    text = {
//                                        Column {
//                                            Text(res.name, color = Color.Black)
//                                            if (res.address.isNotBlank())
//                                                Text(
//                                                    res.address,
//                                                    color = Color.DarkGray,
//                                                    style = MaterialTheme.typography.bodySmall
//                                                )
//                                        }
//                                    },
//                                    leadingIcon = {
//                                        Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500))
//                                    },
//                                    onClick = {
//                                        registrationViewModel.postGraduation = res.name
//                                        registrationViewModel.customPostGraduation = ""
//                                        postGradQuery = res.name
//                                        postGradResults = emptyList()
//                                        focusManager.clearFocus() // Clear focus after selection
//                                    }
//                                )
//                            }
//                        }
//                    }
//                    Spacer(modifier = Modifier.height(8.dp))
//                    GraduationYearDropdown(
//                        year = registrationViewModel.postGraduationYear,
//                        onYearSelected = { registrationViewModel.postGraduationYear = it }
//                    )
//                }
//                Spacer(modifier = Modifier.height(8.dp))
//
//                SearchableDropdownWithCustomOption(
//                    title            = stringResource(R.string.job_role_label),
//                    options          = jobRoleOptions,
//                    selectedOption   = registrationViewModel.jobRole,
//                    onOptionSelected = { sel -> registrationViewModel.jobRole = sel },
//                    customInput      = if (
//                        registrationViewModel.jobRole != other &&
//                        !jobRoleOptions.contains(registrationViewModel.jobRole)
//                    ) registrationViewModel.jobRole else null,
//                    onCustomInputChange = { typed -> registrationViewModel.jobRole = typed ?: "" }
//                )
//
//                    Spacer(modifier = Modifier.height(16.dp))
//
//                    ExposedDropdownMenuBox(
//                        expanded = workMenuExpanded,
//                        onExpandedChange = { /* Controlled by results and focus */ },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        var workother = stringResource(R.string.work_option_other)
//                        val focusRequester = remember { FocusRequester() } // For focus tracking
//                        OutlinedTextField(
//                            value = workQuery,
//                            onValueChange = { query ->
//                                workQuery = query
//                                registrationViewModel.work = workother
//                                registrationViewModel.customWork = query
//                                work = registrationViewModel.work
//                            },
//                            label = { Text(stringResource(R.string.select_work)) },
//                            singleLine = true,
//                            trailingIcon = {
//                                if (workSearching)
//                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
//                                else
//                                    Icon(Icons.Default.Search, null, tint = Color(0xFFFFA500))
//                            },
//                            colors = TextFieldDefaults.outlinedTextFieldColors(
//                                focusedBorderColor = Color(0xFFFF6000),
//                                unfocusedBorderColor = Color(0xFFFF6000),
//                                cursorColor = Color.White,
//                                focusedLabelColor = Color(0xFFFF6000),
//                                unfocusedLabelColor = Color.White,
//                                focusedTextColor = Color.White,
//                                unfocusedTextColor = Color.White
//                            ),
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .menuAnchor()
//                                .focusRequester(focusRequester)
//                                .onFocusChanged { focusState ->
//                                    isWorkFieldFocused = focusState.isFocused
//                                    if (!focusState.isFocused) {
//                                        workResults = emptyList() // Clear results when focus is lost
//                                    }
//                                }
//                        )
//                        ExposedDropdownMenu(
//                            expanded = workMenuExpanded,
//                            onDismissRequest = {
//                                workResults = emptyList()
//                                focusManager.clearFocus() // Clear focus when dismissing the dropdown
//                            },
//                            modifier = Modifier
//                                .background(Color.White, RoundedCornerShape(6.dp))
//                                .border(BorderStroke(1.dp, Color(0x33000000)), RoundedCornerShape(6.dp))
//                        ) {
//                            workResults.forEach { res ->
//                                DropdownMenuItem(
//                                    text = {
//                                        Column {
//                                            Text(res.name, color = Color.Black)
//                                            if (res.address.isNotBlank())
//                                                Text(res.address, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
//                                        }
//                                    },
//                                    leadingIcon = { Icon(Icons.Default.Place, null, tint = Color(0xFFFFA500)) },
//                                    onClick = {
//                                        registrationViewModel.work = res.name
//                                        registrationViewModel.customWork = ""
//                                        workQuery = res.name
//                                        workResults = emptyList()
//                                        work = registrationViewModel.work
//                                        focusManager.clearFocus() // Clear focus after selection
//                                    }
//                                )
//                            }
//                        }
//                    }
//
//
//                // Next Button
//                Spacer(modifier = Modifier.height(24.dp))
//                Button(
//                    onClick = { if (isNextEnabled) onNext() },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(56.dp),
//                    enabled = isNextEnabled,
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = if (isNextEnabled) Color(0xFFFF6000) else Color.Gray
//                    ),
//                    shape = CircleShape
//                ) {
//                    Text(
//                        text = stringResource(R.string.next_button),
//                        color = Color.White,
//                        fontSize = 18.sp,
//                        fontWeight = FontWeight.Bold
//                    )
//                }
//            }
//        }
//    )
//}


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

    // ── state ───────────────────────────────────────────────────────────────
    var showCustomInput by remember(selectedOption) {
        mutableStateOf(selectedOption == other)
    }
    var expanded    by remember { mutableStateOf(false) }
    var searchText  by rememberSaveable { mutableStateOf("") }

    // local copy of the custom text
    var customText  by rememberSaveable(selectedOption) {
        mutableStateOf(customInput.orEmpty())
    }

    // ── UI ──────────────────────────────────────────────────────────────────
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontSize = 14.sp, color = Color.White)

        /* ---------- Button that opens the menu ---------- */
        OutlinedButton(
            onClick = { expanded = !expanded; searchText = "" },
            modifier = Modifier.fillMaxWidth(),
            border   = BorderStroke(1.dp, Color(0xFFFF6000)),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6000))
        ) {
            val display = if (showCustomInput) {
                if (customText.isNotBlank()) customText else "Custom"
            } else {
                selectedOption.ifEmpty { stringResource(R.string.select_or_type) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(display, fontSize = 11.sp, color = Color.White)
                if (selectedOption.isNotEmpty() || customText.isNotBlank()) {
                    IconButton(onClick = {
                        onOptionSelected("")
                        customText = ""
                        onCustomInputChange(null)
                        showCustomInput = false
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_selection),
                            tint = Color.Red
                        )
                    }
                }
            }
        }

        /* ---------- Drop-down ---------- */
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
        ) {
            TextField(
                value         = searchText,
                onValueChange = { searchText = it },
                label         = { Text(stringResource(R.string.search_label), fontSize = 11.sp) },
                colors        = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF4500),
                    cursorColor        = Color(0xFFFF4500),
                    focusedTextColor          = Color.White,
                    focusedLabelColor  = Color(0xFFFF4500)
                ),
                singleLine    = true
            )

            Spacer(Modifier.height(8.dp))

            options.filter { it.contains(searchText, true) }.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 11.sp) },
                    onClick = {
                        onOptionSelected(option)
                        expanded        = false
                        showCustomInput = option == other
                    }
                )
            }
        }

        /* ---------- Custom value ---------- */
        if (showCustomInput) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = customText,
                onValueChange = { new ->
                    customText = new               // update instantly for smooth typing
                    onCustomInputChange(new.ifBlank { null })
                },
                label         = {
                    Text(stringResource(R.string.enter_custom_value), fontSize = 11.sp)
                },
                singleLine    = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                modifier      = Modifier.fillMaxWidth(),
                colors        = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF4500),
                    cursorColor        = Color(0xFFFF4500),
                    focusedTextColor          = Color.White,
                    focusedLabelColor  = Color(0xFFFF4500)
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
    allowPhoneAuth: Boolean,          // ← NEW
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    /* ───────────────────────── TAB STATE ───────────────────────── */
    var selectedTab by remember {
        mutableStateOf(
            if (allowPhoneAuth) AuthTab.PHONE else AuthTab.EMAIL
        )
    }
    /* When we build the TabRow we only include PHONE if allowed */
    val visibleTabs = if (allowPhoneAuth)
        listOf(AuthTab.PHONE, AuthTab.EMAIL) else listOf(AuthTab.EMAIL)

    /* ─────────────── EMAIL/PASSWORD local state ────────────────── */
    var email           by remember { mutableStateOf(TextFieldValue(registrationViewModel.email)) }
    var password        by remember { mutableStateOf(TextFieldValue(registrationViewModel.password)) }
    var confirmPassword by remember { mutableStateOf(TextFieldValue("")) }
    var passwordError   by remember { mutableStateOf(false) }
    var botField        by remember { mutableStateOf(TextFieldValue(registrationViewModel.honeypot)) }
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
            val selectedIndex = visibleTabs.indexOf(selectedTab)

            /* ─── TAB STRIP ─────────────────────────────────────── */
            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor = Color(0xFF262626),
                contentColor   = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = Color(0xFFFF6000)
                    )
                }
            ) {
                visibleTabs.forEach { tab ->
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
                    OutlinedTextField(
                        value = botField,
                        onValueChange = {
                            botField = it
                            registrationViewModel.honeypot = it.text
                        },
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0f),
                        singleLine = true,
                        colors = fieldColors()
                    )
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
                            if (botField.text.isNotBlank()) {
                                Toast.makeText(ctx, "Invalid form", Toast.LENGTH_LONG).show()
                                return@Button
                            }
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
    // ─── DOB pickers ────────────────────────────────────────────────────────
    val dayRange       = (1..31).toList()
    val monthNames     = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val currentYear    = Calendar.getInstance().get(Calendar.YEAR)
    val yearRange      = (1950..currentYear).map { it.toString() }.reversed()
    var selectedDay    by remember { mutableStateOf(dayRange.first()) }
    var selectedMonth  by remember { mutableStateOf(0) }
    var selectedYear   by remember { mutableStateOf(yearRange.first().toInt()) }

    fun updateDob() {
        registrationViewModel.dob = "$selectedDay/${selectedMonth+1}/$selectedYear"
        registrationViewModel.zodiac = deriveZodiac(registrationViewModel.dob)
    }
    LaunchedEffect(selectedDay, selectedMonth, selectedYear) { updateDob() }

    var other = stringResource(R.string.college_other)
    // Predefined lists for dropdown options
    val isIndian = canonicalCountry(registrationViewModel.country) == "India"
    LaunchedEffect(isIndian) {
        if (!isIndian) registrationViewModel.community = ""
    }
    val communityOptions = listOf(
        stringResource(R.string.community_other),
        stringResource(R.string.community_adi),
        stringResource(R.string.community_anglo_indian),
        stringResource(R.string.community_andamanese),
        stringResource(R.string.community_assamese),
        stringResource(R.string.community_awadhi),
        stringResource(R.string.community_banjara),
        stringResource(R.string.community_bengali),
        stringResource(R.string.community_bhil),
        stringResource(R.string.community_bhojpuri),
        stringResource(R.string.community_bihari),
        stringResource(R.string.community_bodo),
        stringResource(R.string.community_chhattisgarhi),
        stringResource(R.string.community_coorgi),
        stringResource(R.string.community_dogra),
        stringResource(R.string.community_garhwali),
        stringResource(R.string.community_goan),      // “Goan”
        stringResource(R.string.community_gond),
        stringResource(R.string.community_gujarati),
        stringResource(R.string.community_haryanvi),
        stringResource(R.string.community_himachali),
        stringResource(R.string.community_kannadiga),
        stringResource(R.string.community_kashmiri),
        stringResource(R.string.community_khasi),
        stringResource(R.string.community_konkani),
        stringResource(R.string.community_kumaoni),
        stringResource(R.string.community_ladakhi),
        stringResource(R.string.community_lakhadweepi),
        stringResource(R.string.community_lepcha),
        stringResource(R.string.community_madhya_pradeshi),
        stringResource(R.string.community_malayali),
        stringResource(R.string.community_malayali_mappila),
        stringResource(R.string.community_manipuri),
        stringResource(R.string.community_marathi),
        stringResource(R.string.community_marwari),
        stringResource(R.string.community_mizo),
        stringResource(R.string.community_munda),
        stringResource(R.string.community_naga),
        stringResource(R.string.community_nepali),
        stringResource(R.string.community_nyishi),
        stringResource(R.string.community_odia),
        stringResource(R.string.community_oraon),
        stringResource(R.string.community_parsi),
        stringResource(R.string.community_punjabi),
        stringResource(R.string.community_rajasthani),
        stringResource(R.string.community_santhal),
        stringResource(R.string.community_sikkimese),
        stringResource(R.string.community_sindhi),
        stringResource(R.string.community_tamil),
        stringResource(R.string.community_telugu),
        stringResource(R.string.community_tibetan),
        stringResource(R.string.community_tripuri),
        stringResource(R.string.community_urdu_speaker),
    )

    // Validation for enabling the "Next" button
    val userAge = calculateAge(registrationViewModel.dob)
    val isNextEnabled =
            registrationViewModel.dob.isNotBlank() &&
            userAge >= 14
    LaunchedEffect(isNextEnabled) { registrationViewModel.nextEnabled = isNextEnabled }

    Scaffold(
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
/* ---------- Date-of-Birth (old layout) ---------- */
                                Text(
                                        text = stringResource(R.string.select_birth_date_label),
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        modifier = Modifier.align(Alignment.Start)
                                            )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                        /* Day */
                                        Box(Modifier.weight(1f)) {
                                                DropdownWithSearch(                 // <- unchanged
                                                        title            = stringResource(R.string.select_day_label),
                                                        options          = dayRange.map { it.toString() },
                                                        selectedOption   = selectedDay.toString(),
                                                        onOptionSelected = { it.toIntOrNull()?.let { d -> selectedDay = d } }
                                                            )
                                            }
                                        /* Month */
                                        Box(Modifier.weight(1f)) {
                                                DropdownWithSearch(
                                                        title            = stringResource(R.string.select_month_label),
                                                        options          = monthNames,
                                                        selectedOption   = monthNames[selectedMonth],
                                                        onOptionSelected = {
                                                                monthNames.indexOf(it)
                                                                    .takeIf { idx -> idx >= 0 }
                                                                    ?.let { m -> selectedMonth = m }
                                                            }
                                                            )
                                            }
                                        /* Year */
                                        Box(Modifier.weight(1f)) {
                                                DropdownWithSearch(
                                                        title            = stringResource(R.string.select_year_label),
                                                        options          = yearRange,
                                                        selectedOption   = selectedYear.toString(),
                                                        onOptionSelected = { it.toIntOrNull()?.let { y -> selectedYear = y } }
                                                            )
                                            }
                                   }

                Spacer(Modifier.height(24.dp))
//                // Title
//                Text(
//                    text = stringResource(R.string.enter_gender_community_religion_title),
//                    color = Color.White,
//                    fontSize = 24.sp,
//                    fontWeight = FontWeight.Bold,
//                    modifier = Modifier.padding(bottom = 24.dp)
//                )

                if (isIndian) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Community Dropdown
                    DropdownWithSearch(
                        title = stringResource(R.string.select_community),
                        options = communityOptions,
                        selectedOption = registrationViewModel.community,
                        onOptionSelected = { registrationViewModel.community = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

//                // Next Button
//                Button(
//                    onClick = { if (isNextEnabled) onNext() },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(56.dp),
//                    enabled = isNextEnabled,
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = if (isNextEnabled) Color(0xFFFF6000) else Color.Gray
//                    ),
//                    shape = CircleShape
//                ) {
//                    Text(
//                        text = stringResource(R.string.next_button),
//                        color = Color.White,
//                        fontSize = 18.sp,
//                        fontWeight = FontWeight.Bold
//                    )
//                }
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
    LaunchedEffect(Unit) { registrationViewModel.nextEnabled = false }

    val invalidChars = remember { Regex("[.#$\\[\\]]") }
    val context = LocalContext.current
    val auth    = FirebaseAuth.getInstance()
    val db      = FirebaseDatabase
        .getInstance("https://kupidxdefault.asia-southeast1.firebasedatabase.app/")
        .getReference()

    var usernameTf by remember { mutableStateOf(TextFieldValue(registrationViewModel.username)) }
    var isValid    by remember { mutableStateOf(true) }
    var errorMsg   by remember { mutableStateOf("") }

    var isLoading  by remember { mutableStateOf(false) }

    Scaffold(
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
                onValueChange = { tf ->
                    usernameTf = tf

                    val raw = tf.text.trim()
                    when {
                        raw.isEmpty() -> {
                            isValid  = false
                            errorMsg = "Username cannot be empty."
                        }
                        invalidChars.containsMatchIn(raw) -> {
                            isValid  = false
                            errorMsg = "Username can’t contain .  #  \$  [  ]"
                        }
                        else -> {
                            isValid  = true
                            errorMsg = ""
                        }
                    }
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
                    unfocusedBorderColor = Color(0xFFFF6000),
                    cursorColor = Color.White,
                    focusedLabelColor = Color(0xFFFF6000),
                    unfocusedLabelColor = Color.White
                )
            )

            Spacer(Modifier.height(24.dp))

            // ---- Finish Button ----
            Button(
                onClick = {
                    val raw = usernameTf.text.trim()

                    // final guard – never reaches Firebase if invalid
                    if (raw.isEmpty()) {
                        isValid  = false
                        errorMsg = "Username cannot be empty."
                        return@Button
                    }
                    if (invalidChars.containsMatchIn(raw)) {
                        isValid  = false
                        errorMsg = "Username can’t contain .  #  \$  [  ]"
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
                                isValid  = false
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
                                        isValid  = false
                                        errorMsg = "Failed to reserve username: ${it.message}"
                                        isLoading = false
                                    }
                            }
                        }
                        .addOnFailureListener {
                            isValid  = false
                            errorMsg = "Error checking username: ${it.message}"
                            isLoading = false
                        }
                },
                // ★ Button stays disabled while the text is invalid or blank
                enabled = usernameTf.text.trim().isNotEmpty() && isValid && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6000),
                    disabledContainerColor = Color(0x88FF6000)
                ),
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
    context: Context,                          // ★ new
    registrationViewModel: RegistrationViewModel,
    other: String,
    allowPhoneAuth: Boolean,                    // ← add
    onRegistrationComplete: () -> Unit
) {
    try {
        val database = FirebaseRefs.db.reference
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val raw = registrationViewModel.phoneNumber
            .ifBlank { FirebaseAuth.getInstance().currentUser?.phoneNumber }
        val e164 = raw?.let { formatPhoneNumber(it) } ?: ""

        if (allowPhoneAuth && e164.isNotBlank()) {          // ← gate by country
            val ok = claimPhoneNumber(database, e164, userId)
            if (!ok) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context,
                        "That mobile number is already linked to another account.",
                        Toast.LENGTH_LONG).show()
                }
                return                                       // abort save
            }
        }

        val finalHeightCm = if (registrationViewModel.isHeightInFeet) {
            registrationViewModel.feetInchesToCm(
                registrationViewModel.height2.getOrNull(0) ?: 0,
                registrationViewModel.height2.getOrNull(1) ?: 0
            )
        } else {
            registrationViewModel.height
        }

        val profile = Profile(
            phoneNumber = if (allowPhoneAuth) raw else null, // ← not stored abroad
            userId = userId,
            country       = canonicalCountry(
                if (registrationViewModel.country == other)
                    registrationViewModel.customCountry
                else registrationViewModel.country
            ),
            customCountry = registrationViewModel.customCountry,

            username = registrationViewModel.username,
            name = registrationViewModel.name,
            dob = registrationViewModel.dob,
            email = registrationViewModel.email,
            bio = registrationViewModel.bio,
            gender = registrationViewModel.gender.toGenderCode()?.name
                ?: registrationViewModel.gender,
            interests = registrationViewModel.interests.toList(),
            // Save the city using customCity if "Other" is selected
            city = canonicalCity(
                if (registrationViewModel.city == other)
                    registrationViewModel.customCity.trim()
                else registrationViewModel.city.trim()
            ),
            hometown = if (registrationViewModel.hometown == other)
                registrationViewModel.customHometown.trim()
            else registrationViewModel.hometown.trim(),
            highSchool = if (registrationViewModel.highSchool == other) registrationViewModel.customHighSchool else registrationViewModel.highSchool,
            college = if (registrationViewModel.college == other) registrationViewModel.customCollege else registrationViewModel.college,
            postGraduation = if (registrationViewModel.postGraduation == other) registrationViewModel.customPostGraduation else registrationViewModel.postGraduation,
            work = if (registrationViewModel.work == other) registrationViewModel.customWork else registrationViewModel.work,
            profilepicUrl = registrationViewModel.profilePicUrl,
            optionalPhotoUrls = registrationViewModel.optionalPhotoUrls.toList(),
            privateAlbumUrls = registrationViewModel.privateAlbumUrls.toList(),
            religion = registrationViewModel.religion,
            community = registrationViewModel.community,
            ethnicity = registrationViewModel.ethnicity,
            incomeLevel = registrationViewModel.incomeLevel,
            educationLevel = registrationViewModel.educationLevel,
            lifestyle = registrationViewModel.lifestyle,
            lookingFor = registrationViewModel.lookingFor,
            roles = registrationViewModel.roles.toList(),
            showRolesOnProfile = registrationViewModel.showRolesOnProfile,
            tribes = registrationViewModel.tribes.toList(),
            showTribesOnProfile = registrationViewModel.showTribesOnProfile,
            bodyType = registrationViewModel.bodyType,
            showBodyTypeOnProfile = registrationViewModel.showBodyTypeOnProfile,
            kinks = registrationViewModel.kinks.toList(),
            showKinksOnProfile = registrationViewModel.showKinksOnProfile,
            politics = registrationViewModel.politics,
            socialCauses = registrationViewModel.socialCauses.toList(),
            height = finalHeightCm,
            height2 = registrationViewModel.height2,
            voiceNoteUrl = registrationViewModel.voiceNoteUrl,
            datingAgeStart = registrationViewModel.datingAgeStart,
            datingAgeEnd = registrationViewModel.datingAgeEnd,
            datingDistancePreference = registrationViewModel.datingDistancePreference,
            loveLanguage = registrationViewModel.loveLanguage,
            jobRole = registrationViewModel.jobRole,
            allowLocationForMatches = registrationViewModel.allowLocationForMatches,
            allowLocationPublic = registrationViewModel.allowLocationPublic,
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
): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val original = BitmapFactory.decodeStream(input) ?: return@withContext null
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
    } catch (e: Exception) {
        Log.e("CompressImage", "Failed to compress image: ${e.message}")
        null
    }
}

fun uploadProfilePicToFirebase(
    context: Context,
    storageRef: StorageReference,
    uri: Uri,
    registrationViewModel: RegistrationViewModel
) {
    (context as? ComponentActivity)?.lifecycleScope?.launch {
        val jpegBytes = compressImage(context, uri) ?: return@launch
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

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun EnterNameScreen(
//    registrationViewModel: RegistrationViewModel,
//    onNext: () -> Unit
//) {
//    val maleOption = stringResource(R.string.male_option)
//    val femaleOption = stringResource(R.string.female_option)
//    val interestedOptions = listOf(maleOption, femaleOption)
//
//    var heightText by remember { mutableStateOf(registrationViewModel.height.toString()) }
//    var feetText   by remember { mutableStateOf(registrationViewModel.height2.getOrNull(0)?.toString() ?: "") }
//    var inchText   by remember { mutableStateOf(registrationViewModel.height2.getOrNull(1)?.toString() ?: "") }
//    var headline   by remember { mutableStateOf(TextFieldValue(registrationViewModel.bio)) }
//
//    // Now only “Interested In” is required on this screen
//    val canProceed = registrationViewModel.interestedIn.isNotEmpty()
//
//    Scaffold(
//        content = { innerPadding ->
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(Color(0xFF1A1A1A))
//                    .padding(innerPadding),
//                contentAlignment = Alignment.TopCenter
//            ) {
//                LazyColumn(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(horizontal = 32.dp, vertical = 16.dp),
//                    verticalArrangement = Arrangement.Top,
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    item {
//                        // ---- Interested In (font made larger) ----
//                        Row(verticalAlignment = Alignment.CenterVertically) {
//                            Icon(
//                                imageVector = Icons.Default.Favorite,
//                                contentDescription = null,
//                                tint = Color(0xFFFF6000)
//                            )
//                            Spacer(Modifier.width(8.dp))
//                            Text(
//                                text = stringResource(R.string.interested_in_header),
//                                color = Color.White,
//                                fontSize = 20.sp,
//                                fontWeight = FontWeight.Bold
//                            )
//                        }
//
//                        Spacer(modifier = Modifier.height(8.dp))
//
//                        Row(
//                            horizontalArrangement = Arrangement.spacedBy(12.dp),
//                            modifier = Modifier.fillMaxWidth()
//                        ) {
//                            interestedOptions.forEach { option ->
//                                val isSelected = registrationViewModel.interestedIn.contains(option)
//                                FilterChip(
//                                    selected = isSelected,
//                                    onClick = {
//                                        if (isSelected) registrationViewModel.interestedIn.remove(option)
//                                        else registrationViewModel.interestedIn.add(option)
//                                    },
//                                    label = { Text(option, color = Color.White) },
//                                    leadingIcon = {
//                                        Icon(
//                                            imageVector = when (option) {
//                                                maleOption -> Icons.Default.Male
//                                                else       -> Icons.Default.Female
//                                            },
//                                            contentDescription = null,
//                                            tint = Color.White
//                                        )
//                                    },
//                                    colors = FilterChipDefaults.filterChipColors(
//                                        selectedContainerColor = Color(0xFFFF6000),
//                                        selectedLabelColor     = Color.White,
//                                        containerColor         = Color(0xFF1A1A1A),
//                                        labelColor             = Color.White
//                                    ),
//                                    modifier = Modifier.weight(1f)
//                                )
//                            }
//                        }
//
//                        Spacer(modifier = Modifier.height(24.dp))
//                        // ---- Full Name (optional) ----
//                        TextFieldWithLabel(
//                            label = stringResource(R.string.full_name_label),
//                            value = registrationViewModel.name,
//                            onValueChange = { registrationViewModel.name = it }
//                        )
//
//                        Spacer(modifier = Modifier.height(16.dp))
//
//                        // ---- Height (optional) ----
//                        Text(
//                            text = stringResource(R.string.height_label),
//                            color = Color.White,
//                            fontSize = 16.sp
//                        )
//                        Row(verticalAlignment = Alignment.CenterVertically) {
//                            Switch(
//                                checked = registrationViewModel.isHeightInFeet,
//                                onCheckedChange = { useFeet ->
//                                    registrationViewModel.isHeightInFeet = useFeet
//                                    if (useFeet) {
//                                        val (f, i) = registrationViewModel.cmToFeetInches(registrationViewModel.height)
//                                        feetText = f.toString()
//                                        inchText = i.toString()
//                                    } else {
//                                        val f = feetText.toIntOrNull() ?: 0
//                                        val i = inchText.toIntOrNull() ?: 0
//                                        heightText = registrationViewModel.feetInchesToCm(f, i).toString()
//                                    }
//                                },
//                                colors = SwitchDefaults.colors(
//                                    checkedThumbColor = Color(0xFFFF6000),
//                                    uncheckedThumbColor = Color.White
//                                )
//                            )
//                            Text(
//                                text = if (registrationViewModel.isHeightInFeet)
//                                    stringResource(R.string.feet_inches_label)
//                                else
//                                    stringResource(R.string.centimeters_label),
//                                color = Color.White
//                            )
//                        }
//
//                        if (registrationViewModel.isHeightInFeet) {
//                            Row(
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .padding(vertical = 8.dp),
//                                horizontalArrangement = Arrangement.spacedBy(16.dp),
//                                verticalAlignment = Alignment.CenterVertically
//                            ) {
//                                OutlinedTextField(
//                                    value = feetText,
//                                    onValueChange = { newFeet ->
//                                        feetText = newFeet
//                                        registrationViewModel.height2 = listOf(
//                                            newFeet.toIntOrNull() ?: 0,
//                                            registrationViewModel.height2.getOrNull(1) ?: 0
//                                        )
//                                    },
//                                    label = { Text(stringResource(R.string.feet_label), color = Color.White) },
//                                    singleLine = true,
//                                    modifier = Modifier
//                                        .weight(1f)
//                                        .heightIn(56.dp),
//                                    colors = TextFieldDefaults.outlinedTextFieldColors(
//                                        focusedBorderColor = Color(0xFFFF6000),
//                                        unfocusedBorderColor = Color.White,
//                                        cursorColor = Color.White,
//                                        focusedLabelColor = Color(0xFFFF6000),
//                                        unfocusedLabelColor = Color.White
//                                    )
//                                )
//
//                                OutlinedTextField(
//                                    value = inchText,
//                                    onValueChange = { newInch ->
//                                        inchText = newInch
//                                        registrationViewModel.height2 = listOf(
//                                            registrationViewModel.height2.getOrNull(0) ?: 0,
//                                            newInch.toIntOrNull() ?: 0
//                                        )
//                                    },
//                                    label = { Text(stringResource(R.string.inches_label), color = Color.White) },
//                                    singleLine = true,
//                                    modifier = Modifier
//                                        .weight(1f)
//                                        .heightIn(56.dp),
//                                    colors = TextFieldDefaults.outlinedTextFieldColors(
//                                        focusedBorderColor = Color(0xFFFF6000),
//                                        unfocusedBorderColor = Color.White,
//                                        cursorColor = Color.White,
//                                        focusedLabelColor = Color(0xFFFF6000),
//                                        unfocusedLabelColor = Color.White
//                                    )
//                                )
//                            }
//                        } else {
//                            TextFieldWithLabel(
//                                label = stringResource(R.string.height_cm_label),
//                                value = heightText,
//                                onValueChange = { newCm ->
//                                    heightText = newCm
//                                    registrationViewModel.height = newCm.toIntOrNull() ?: registrationViewModel.height
//                                }
//                            )
//                        }
//
//                        Spacer(modifier = Modifier.height(24.dp))
//
//                        // Headline/Bio TextField
//                        OutlinedTextField(
//                            value = headline,
//                            onValueChange = {
//                                headline = it
//                                registrationViewModel.bio = it.text
//                            },
//                            label = { Text("Bio (optional)", color = Color.White) },
//                            singleLine = true,
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(bottom = 16.dp),
//                            colors = OutlinedTextFieldDefaults.colors(
//                                focusedTextColor = Color.White,
//                                unfocusedTextColor = Color.White,
//                                cursorColor = Color.White,
//                                focusedBorderColor = Color(0xFFFF6000),
//                                unfocusedBorderColor = Color.White
//                            )
//                        )
//
//                        // ---- Next Button ----
//                        Button(
//                            onClick = { if (canProceed) onNext() },
//                            modifier = Modifier.fillMaxWidth(),
//                            colors = ButtonDefaults.buttonColors(
//                                containerColor = if (canProceed) Color(0xFFFF6000) else Color.DarkGray
//                            ),
//                            enabled = canProceed
//                        ) {
//                            Text(stringResource(R.string.next_button), color = Color.White)
//                        }
//                    }
//                }
//            }
//        }
//    )
//}

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
    val isIndian = canonicalCountry(registrationViewModel.country) == "India"
    val context = LocalContext.current
    val resources = context.resources
    val countries = remember { resources.getStringArray(R.array.country_names).toList() }
    var selectedCountry  by remember { mutableStateOf(countries.first()) } // default “Other”
    var customCountry    by remember { mutableStateOf(registrationViewModel.customCountry) }
    var other = stringResource(R.string.college_other)


    // City Selection
    val cities = remember { resources.getStringArray(R.array.city_names).toList() }
    var selectedCity by remember { mutableStateOf(cities.firstOrNull() ?: "") }
    var customCity by remember { mutableStateOf(registrationViewModel.customCity) }
    var isLocating by remember { mutableStateOf(false) }
    var dontShowLocation by remember {
        mutableStateOf(
            !registrationViewModel.allowLocationPublic &&
                    !registrationViewModel.allowLocationForMatches
        )
    }

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
            ) { country, city, locality, rawCity, rawLocality ->
                selectedCountry = country
                registrationViewModel.country = if (country == other) customCountry else country

                if (canonicalCountry(country) == "India") {
                    selectedCity     = city
                    selectedLocality = locality
                    if (city == other) {
                        customCity = rawCity
                        registrationViewModel.customCity = rawCity
                        registrationViewModel.city = other
                    } else {
                        registrationViewModel.city = city
                    }
                    if (locality == other) {
                        customLocality = rawLocality
                        registrationViewModel.customHometown = rawLocality
                        registrationViewModel.hometown = other
                    } else {
                        registrationViewModel.hometown = locality
                    }
                } else {
                    selectedCity        = other
                    selectedLocality    = other
                    customCity          = rawCity
                    customLocality      = rawLocality
                    registrationViewModel.customCity     = rawCity
                    registrationViewModel.customHometown = rawLocality
                    registrationViewModel.city           = other
                    registrationViewModel.hometown       = other
                }
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
            ) { country, city, locality, rawCity, rawLocality ->
                selectedCountry = country
                registrationViewModel.country = if (country == other) customCountry else country

                if (canonicalCountry(country) == "India") {
                    selectedCity     = city
                    selectedLocality = locality
                    if (city == other) {
                        customCity = rawCity
                        registrationViewModel.customCity = rawCity
                        registrationViewModel.city = other
                    } else {
                        registrationViewModel.city = city
                    }
                    if (locality == other) {
                        customLocality = rawLocality
                        registrationViewModel.customHometown = rawLocality
                        registrationViewModel.hometown = other
                    } else {
                        registrationViewModel.hometown = locality
                    }
                } else {
                    selectedCity        = other
                    selectedLocality    = other
                    customCity          = rawCity
                    customLocality      = rawLocality
                    registrationViewModel.customCity     = rawCity
                    registrationViewModel.customHometown = rawLocality
                    registrationViewModel.city           = other
                    registrationViewModel.hometown       = other
                }
                isLocating = false
            }
        }
    }
    val isNextEnabled = true   // always skippable
    LaunchedEffect(Unit) { registrationViewModel.nextEnabled = true }
    Scaffold(
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
//                Text(stringResource(R.string.city_label), color = Color.White, fontSize = 18.sp)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isIndian) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
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
                    } else {
                        OutlinedTextField(
                            value = customCity,
                            onValueChange = { new ->
                                customCity = new
                                registrationViewModel.customCity = new
                                registrationViewModel.city = other
                            },
                            label = { Text(stringResource(R.string.city_label), color = Color.White) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        modifier = Modifier.fillMaxHeight(),
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
//                Text(stringResource(R.string.locality_label), color = Color.White, fontSize = 18.sp)
                if (isIndian) {
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
                } else {
                    OutlinedTextField(
                        value = customLocality,
                        onValueChange = { new ->
                            customLocality = new
                            registrationViewModel.customHometown = new
                            registrationViewModel.hometown = other
                        },
                        label = { Text(stringResource(R.string.locality_label), color = Color.White) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = dontShowLocation,
                        onCheckedChange = { checked ->
                            dontShowLocation = checked
                            registrationViewModel.allowLocationForMatches = !checked
                            registrationViewModel.allowLocationPublic = !checked
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dont_show_location), color = Color.White)
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
internal fun fetchLocation(
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
    onLocationFound: (
        String,
        String,
        String,
        String,
        String
    ) -> Unit // ← country, city, locality, rawCity, rawLocality
) {
    val TAG = "fetchLocation"                       // <- new tag
    val activityScope = (ctx as? ComponentActivity)?.lifecycleScope ?: return
    activityScope.launch(Dispatchers.IO) {
        try {
            // 1) Permission check
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "No location permission → defaulting to OTHER/OTHER/OTHER")
                withContext(Dispatchers.Main) { onLocationFound(other, other, other, other, other) }
                return@launch
            }

            // 2) Try the cached “last” fix
            var loc = fused.lastLocation.await()
            Log.d(TAG, "lastLocation.await() returned → $loc")

            // 3) If that was null, fall back to a fresh one‐shot request
            if (loc == null) {
                Log.d(TAG, "lastLocation was null → calling getCurrentLocation()")
                loc = fused.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()
                Log.d(TAG, "getCurrentLocation() returned → $loc")
            }

            // 4) If still null, give up
            if (loc == null) {
                Log.d(TAG, "Both location calls null → defaulting to OTHER/OTHER/OTHER")
                withContext(Dispatchers.Main) { onLocationFound(other, other, other, other, other) }
                return@launch
            }

            // 5) We have a valid Location!
            Log.d(TAG, "Have Location → lat=${loc.latitude}, lon=${loc.longitude}")

            val geo = Geocoder(ctx, Locale.getDefault())
            val addr = withContext(Dispatchers.IO) {
                geo.getFromLocation(loc.latitude, loc.longitude, 1)
            }?.firstOrNull()
            if (addr == null) {                 Log.d(TAG, "Geocoder returned no address, defaulting to OTHER/OTHER/OTHER")
                onLocationFound(other, other, other, other, other); return@launch }

            val detectedCountry  = addr.countryName ?: other
            val detectedCity     = addr.locality ?: addr.subAdminArea ?: other
            val detectedLocality = addr.subLocality ?: other
            Log.d(TAG, "Geocoder → $detectedCountry / $detectedCity / $detectedLocality")

            val matchedCountry = countries.find { it.equals(detectedCountry, ignoreCase = true) } ?: other
            val matchedCityList = ctx.resources.getStringArray(R.array.city_names).toList()
            val matchedCity     = matchedCityList.find { it.equals(detectedCity, ignoreCase = true) } ?: other
            Log.d(TAG, "Matched country='$matchedCountry'")

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
            val matchedLocality = localities.find { it.equals(detectedLocality, ignoreCase = true) } ?: other
            withContext(Dispatchers.Main) {
                onLocationFound(
                    matchedCountry,
                    matchedCity,
                    matchedLocality,
                    detectedCity,
                    detectedLocality
                )
            }
        } catch (e: Exception) {
            Log.e("fetchLocation", "${e.message}")
            withContext(Dispatchers.Main) { onLocationFound(other, other, other, other, other) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterInterestsScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit
) {
    val ready = true
    LaunchedEffect(ready) { registrationViewModel.nextEnabled = ready }
    val maxInterests = 20

    /* ───── 1) Bucket your interests here ───── */
    val categorized = mapOf(

        // ─────────── Arts & Entertainment ───────────
        "Arts & Entertainment" to listOf(
            Interest(stringResource(R.string.interest_art),            "🎨"),
            Interest(stringResource(R.string.interest_blogging),       "📝"),
            Interest(stringResource(R.string.interest_books),          "📚"),
            Interest(stringResource(R.string.interest_dancing),        "💃"),
            Interest(stringResource(R.string.interest_fashion),        "👗"),
            Interest(stringResource(R.string.interest_movies),         "🎥"),
            Interest(stringResource(R.string.interest_music),          "🎵"),
            Interest(stringResource(R.string.interest_photography),    "📷"),
            Interest(stringResource(R.string.interest_podcasting),     "🎙️"),
            Interest(stringResource(R.string.interest_watching_tv),    "📺"),
            Interest(stringResource(R.string.interest_writing),        "✍️")
        ).sortedBy { it.name.lowercase() },

        // ─────────── Food & Drink ───────────
        "Food & Drink" to listOf(
            Interest(stringResource(R.string.interest_baking),         "🍰"),
            Interest(stringResource(R.string.interest_coffee),         "☕"),
            Interest(stringResource(R.string.interest_cooking),        "🍳"),
            Interest(stringResource(R.string.interest_craft_beer),     "🍺"),
            Interest(stringResource(R.string.interest_food),           "🍔"),
            Interest(stringResource(R.string.interest_wine_tasting),   "🍷")
        ).sortedBy { it.name.lowercase() },

        // ─────────── Games & Puzzles ───────────
        "Games & Puzzles" to listOf(
            Interest(stringResource(R.string.interest_board_games),    "🎲"),
            Interest(stringResource(R.string.interest_gaming),         "🎮"),
            Interest(stringResource(R.string.interest_puzzles),        "🧩"),
            Interest(stringResource(R.string.interest_video_games),    "🎮")
        ).sortedBy { it.name.lowercase() },

        // ─────────── Home & DIY ───────────
        "Home & DIY" to listOf(
            Interest(stringResource(R.string.interest_diy),            "🛠️"),
            Interest(stringResource(R.string.interest_gardening),      "🌱"),
            Interest(stringResource(R.string.interest_home_improvement),"🏠"),
            Interest(stringResource(R.string.interest_interior_design),"🛋️"),
            Interest(stringResource(R.string.interest_thrift_shopping),"🛍️"),
            Interest(stringResource(R.string.interest_vintage_clothing),"🧥")
        ).sortedBy { it.name.lowercase() },

        // ─────────── Mind & Knowledge ───────────
        "Mind & Knowledge" to listOf(
            Interest(stringResource(R.string.interest_economics),      "💰"),
            Interest(stringResource(R.string.interest_history),        "📜"),
            Interest(stringResource(R.string.interest_philosophy),     "🧠"),
            Interest(stringResource(R.string.interest_politics),       "🗳️"),
            Interest(stringResource(R.string.interest_science),        "🔬")
        ).sortedBy { it.name.lowercase() },

        // ─────────── Nature & Animals ───────────
        "Nature & Animals" to listOf(
            Interest(stringResource(R.string.interest_gardening),      "🌱"),
            Interest(stringResource(R.string.interest_nature),         "🌳"),
            Interest(stringResource(R.string.interest_pets),           "🐾")
        ).sortedBy { it.name.lowercase() },

        // ─────────── Sports & Outdoors ───────────
        "Sports & Outdoors" to listOf(
            Interest(stringResource(R.string.interest_beach_days),     "🏖️"),
            Interest(stringResource(R.string.interest_camping),        "⛺"),
            Interest(stringResource(R.string.interest_car_racing),     "🏎️"),
            Interest(stringResource(R.string.interest_extreme_sports), "🏂"),
            Interest(stringResource(R.string.interest_fishing),        "🎣"),
            Interest(stringResource(R.string.interest_hiking),         "🥾"),
            Interest(stringResource(R.string.interest_hunting),        "🏹"),
            Interest(stringResource(R.string.interest_mountain_biking),"🚵"),
            Interest(stringResource(R.string.interest_motorcycling),   "🏍️"),
            Interest(stringResource(R.string.interest_rock_climbing),  "🧗"),
            Interest(stringResource(R.string.interest_scuba_diving),   "🤿"),
            Interest(stringResource(R.string.interest_skiing),         "⛷️"),
            Interest(stringResource(R.string.interest_skydiving),      "🪂"),
            Interest(stringResource(R.string.interest_snowboarding),   "🏂"),
            Interest(stringResource(R.string.interest_sports),         "⚽"),
            Interest(stringResource(R.string.interest_surfing),        "🏄"),
            Interest(stringResource(R.string.interest_travel),         "✈️"),
            Interest(stringResource(R.string.interest_traveling),      "🧳")
        ).sortedBy { it.name.lowercase() },

        // ─────────── Tech & Science ───────────
        "Tech & Innovation" to listOf(
            Interest(stringResource(R.string.interest_coding),         "⌨️"),
            Interest(stringResource(R.string.interest_robotics),       "🤖"),
            Interest(stringResource(R.string.interest_space),          "🚀"),
            Interest(stringResource(R.string.interest_technology),     "💻")
        ).sortedBy { it.name.lowercase() },

        // ─────────── Wellness & Lifestyle ───────────
        "Wellness & Lifestyle" to listOf(
            Interest(stringResource(R.string.interest_astrology),      "♈"),
            Interest(stringResource(R.string.interest_charity),        "❤️"),
            Interest(stringResource(R.string.interest_crystals),       "💎"),
            Interest(stringResource(R.string.interest_environmentalism),"🌍"),
            Interest(stringResource(R.string.interest_fitness),        "💪"),
            Interest(stringResource(R.string.interest_meditation),     "🧘‍♂️"),
            Interest(stringResource(R.string.interest_napping),        "😴"),
            Interest(stringResource(R.string.interest_networking),     "🤝"),
            Interest(stringResource(R.string.interest_picnics),        "🧺"),
            Interest(stringResource(R.string.interest_public_speaking),"🎤"),
            Interest(stringResource(R.string.interest_romance),        "💋"),
            Interest(stringResource(R.string.interest_social_media),   "📱"),
            Interest(stringResource(R.string.interest_online_communities),"💬"),
            Interest(stringResource(R.string.interest_spa_days),       "💆"),
            Interest(stringResource(R.string.interest_volunteering),   "🤝"),
            Interest(stringResource(R.string.interest_yoga),           "🧘")
        ).sortedBy { it.name.lowercase() }
    )

    /* ───── 2) Flatten once for validation ───── */
    val allInterests = remember { categorized.values.flatten() }
    val interestsOverLimit = registrationViewModel.interests.size > maxInterests

    /* ───── 3) UI ───── */
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
                .padding(innerPadding)
        ) {
            RegistrationAccordion(title = stringResource(R.string.section_interests)) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    categorized.forEach { (header, list) ->
                        /* section header */
                        Text(
                            text = header,
                            color = Color(0xFFFF6000),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))

                        /* interests inside the section */
                        list.forEach { interest ->
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
                                    text = "${interest.emoji}  ${interest.name}",
                                    color = if (isSelected) Color(0xFFFF6000) else Color.White
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (interestsOverLimit) {
                        Text(
                            text = stringResource(R.string.max_interests_error, maxInterests),
                            color = Color.Red
                        )
                    }

                    Spacer(Modifier.height(24.dp))


//            /* Next button */
//            Button(
//                onClick  = onNext,
//                enabled  = registrationViewModel.interests.isNotEmpty() && !interestsOverLimit,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(56.dp),
//                colors = ButtonDefaults.buttonColors(
//                    containerColor =
//                        if (registrationViewModel.interests.isNotEmpty() && !interestsOverLimit)
//                            Color(0xFFFF6000) else Color.DarkGray
//                ),
//                shape = CircleShape
//            ) {
//                Text(
//                    text = stringResource(R.string.next_button),
//                    color = Color.White,
//                    fontSize = 18.sp,
//                    fontWeight = FontWeight.Bold
//                )
//            }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterOrientationScreen(
    registrationViewModel: RegistrationViewModel,
    onNext: () -> Unit
) {
    LaunchedEffect(Unit) { registrationViewModel.nextEnabled = true }
    // Religion options
    val religionOptions = listOf(
        stringResource(R.string.religion_other),
        stringResource(R.string.religion_buddhist),
        stringResource(R.string.religion_christian),
        stringResource(R.string.religion_christian_catholic),
        stringResource(R.string.religion_christian_protestant_mainline),
        stringResource(R.string.religion_christian_evangelical),
        stringResource(R.string.religion_christian_orthodox),
        stringResource(R.string.religion_christian_latter_day_saint),
        stringResource(R.string.religion_christian_jehovahs_witness),
        stringResource(R.string.religion_christian_other),
        stringResource(R.string.religion_hindu),
        stringResource(R.string.religion_jain),
        stringResource(R.string.religion_jewish),
        stringResource(R.string.religion_muslim),
        stringResource(R.string.religion_muslim_sunni),
        stringResource(R.string.religion_muslim_shia),
        stringResource(R.string.religion_muslim_ahmadiyya),
        stringResource(R.string.religion_muslim_sufi),
        stringResource(R.string.religion_muslim_other),
        stringResource(R.string.religion_no_religion),
        stringResource(R.string.religion_parsi),
        stringResource(R.string.religion_sikh),
        stringResource(R.string.religion_indigenous_tribal),
        stringResource(R.string.religion_santeria),
        stringResource(R.string.religion_voodou),
        stringResource(R.string.religion_candomble),
        stringResource(R.string.religion_umbanda),
        stringResource(R.string.religion_palo_mayombe),
        stringResource(R.string.religion_native_traditional),
        stringResource(R.string.religion_native_church),
        stringResource(R.string.religion_vision_quest),
        stringResource(R.string.religion_african_traditional),
        stringResource(R.string.religion_obeah),
        stringResource(R.string.religion_hoodoo),
        stringResource(R.string.religion_rastafari),
        stringResource(R.string.religion_black_protestant),
    )

    val ethnicityOptions = listOf(
        stringResource(R.string.ethnicity_option_not_selected),
        stringResource(R.string.ethnicity_option_white),
        stringResource(R.string.ethnicity_option_black),
        stringResource(R.string.ethnicity_option_hispanic),
        stringResource(R.string.ethnicity_option_asian),
        stringResource(R.string.ethnicity_option_native_american),
        stringResource(R.string.ethnicity_option_middle_eastern),
        stringResource(R.string.ethnicity_option_pacific_islander),
        stringResource(R.string.ethnicity_option_mixed_other)
    )

    val incomeLevelOptions = listOf(
        stringResource(R.string.income_level_option_not_selected),
        stringResource(R.string.income_level_under_25k),
        stringResource(R.string.income_level_25k_50k),
        stringResource(R.string.income_level_50k_75k),
        stringResource(R.string.income_level_75k_100k),
        stringResource(R.string.income_level_100k_150k),
        stringResource(R.string.income_level_over_150k)
    )
    val lookingForOptions = listOf(
        stringResource(R.string.looking_for_long_term),
        stringResource(R.string.looking_for_short_to_long),
        stringResource(R.string.looking_for_casual),
        stringResource(R.string.looking_for_dating),
        stringResource(R.string.looking_for_exclusive),
        stringResource(R.string.looking_for_romance),
        stringResource(R.string.looking_for_connection),
        stringResource(R.string.looking_for_partner),
        stringResource(R.string.looking_for_marriage)
    )
    val loveLanguageOptions = listOf(
        stringResource(R.string.love_language_option_words_of_affirmation),
        stringResource(R.string.love_language_option_acts_of_service),
        stringResource(R.string.love_language_option_receiving_gifts),
        stringResource(R.string.love_language_option_quality_time),
        stringResource(R.string.love_language_option_physical_touch),
        stringResource(R.string.love_language_option_other)
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
    val roleOptions = stringArrayResource(R.array.roles_options).toList()
    val tribeOptions = stringArrayResource(R.array.tribes_options).toList()
    val bodyTypeOptions = stringArrayResource(R.array.body_type_options).toList()
    val kinkOptions = stringArrayResource(R.array.kink_options).toList()

    var selectedRoles = remember { mutableStateListOf<String>().apply { addAll(registrationViewModel.roles) } }
    var showRolesOnProfile by remember { mutableStateOf(registrationViewModel.showRolesOnProfile) }
    var selectedTribes = remember { mutableStateListOf<String>().apply { addAll(registrationViewModel.tribes) } }
    var showTribesOnProfile by remember { mutableStateOf(registrationViewModel.showTribesOnProfile) }
    var selectedBodyType by remember { mutableStateOf(registrationViewModel.bodyType) }
    var showBodyTypeOnProfile by remember { mutableStateOf(registrationViewModel.showBodyTypeOnProfile) }
    var selectedKinks = remember { mutableStateListOf<String>().apply { addAll(registrationViewModel.kinks) } }
    var showKinksOnProfile by remember { mutableStateOf(registrationViewModel.showKinksOnProfile) }
    Scaffold(
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFF1A1A1A))
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RegistrationAccordion(title = stringResource(R.string.advanced_compatibility)) {
                    Text(text = stringResource(R.string.roles_label), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.show_on_profile), color = Color.White)
                        Switch(checked = showRolesOnProfile, onCheckedChange = { showRolesOnProfile = it })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        roleOptions.forEach { option ->
                            FilterChip(
                                selected = option in selectedRoles,
                                onClick = {
                                    if (option in selectedRoles) selectedRoles.remove(option) else selectedRoles.add(option)
                                },
                                label = { Text(option, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF6000),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1A1A1A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = stringResource(R.string.tribes_label), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.show_on_profile), color = Color.White)
                        Switch(checked = showTribesOnProfile, onCheckedChange = { showTribesOnProfile = it })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tribeOptions.forEach { option ->
                            FilterChip(
                                selected = option in selectedTribes,
                                onClick = {
                                    if (option in selectedTribes) selectedTribes.remove(option) else selectedTribes.add(option)
                                },
                                label = { Text(option, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF6000),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1A1A1A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = stringResource(R.string.body_type_label), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.show_on_profile), color = Color.White)
                        Switch(checked = showBodyTypeOnProfile, onCheckedChange = { showBodyTypeOnProfile = it })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bodyTypeOptions.forEach { option ->
                            FilterChip(
                                selected = selectedBodyType == option,
                                onClick = {
                                    selectedBodyType = if (selectedBodyType == option) "" else option
                                },
                                label = { Text(option, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF6000),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1A1A1A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = stringResource(R.string.kinks_label), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.show_on_profile), color = Color.White)
                        Switch(checked = showKinksOnProfile, onCheckedChange = { showKinksOnProfile = it })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        kinkOptions.forEach { option ->
                            FilterChip(
                                selected = option in selectedKinks,
                                onClick = {
                                    if (option in selectedKinks) selectedKinks.remove(option) else selectedKinks.add(option)
                                },
                                label = { Text(option, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF6000),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1A1A1A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.looking_for_label),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lookingForOptions.forEach { option ->
                            FilterChip(
                                selected = registrationViewModel.lookingFor == option,
                                onClick = {
                                    registrationViewModel.lookingFor =
                                        if (registrationViewModel.lookingFor == option) "" else option
                                },
                                label = { Text(option, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF6000),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1A1A1A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.love_language_label),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        loveLanguageOptions.forEach { option ->
                            FilterChip(
                                selected = registrationViewModel.loveLanguage == option,
                                onClick = {
                                    registrationViewModel.loveLanguage =
                                        if (registrationViewModel.loveLanguage == option) "" else option
                                },
                                label = { Text(option, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF6000),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1A1A1A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.select_politics),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        politicsOptions.forEach { option ->
                            FilterChip(
                                selected = registrationViewModel.politics == option,
                                onClick = {
                                    registrationViewModel.politics =
                                        if (registrationViewModel.politics == option) "" else option
                                },
                                label = { Text(option, color = Color.White) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF6000),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1A1A1A),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        registrationViewModel.roles.clear()
                        registrationViewModel.roles.addAll(selectedRoles)
                        registrationViewModel.showRolesOnProfile = showRolesOnProfile
                        registrationViewModel.tribes.clear()
                        registrationViewModel.tribes.addAll(selectedTribes)
                        registrationViewModel.showTribesOnProfile = showTribesOnProfile
                        registrationViewModel.bodyType = selectedBodyType
                        registrationViewModel.showBodyTypeOnProfile = showBodyTypeOnProfile
                        registrationViewModel.kinks.clear()
                        registrationViewModel.kinks.addAll(selectedKinks)
                        registrationViewModel.showKinksOnProfile = showKinksOnProfile
                        onNext()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000))
                ) {
                    Text(stringResource(R.string.next_button), color = Color.White)
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

    LaunchedEffect(Unit) { registrationViewModel.nextEnabled = true }

    var isRecording by remember { mutableStateOf(false) }
    var isPlaying  by remember { mutableStateOf(false) }
    var isVoiceBioValid by remember { mutableStateOf(true) }

    val voiceFile = remember { File(context.filesDir, "voice_note.mp3") }
    val voiceFilePath = voiceFile.absolutePath
    var voiceProgress by remember { mutableStateOf(0f) }
    var voiceDuration by remember { mutableStateOf(0L) }

    val mediaPlayer = remember { MediaPlayer() }

    // Helper: combined list of URIs (first = profile or placeholder)
    val placeholderUriString =
        "android.resource://${context.packageName}/drawable/local_placeholder"
    val combinedPhotoUris: List<Uri> =
        (registrationViewModel.profilePictureUri ?: Uri.parse(placeholderUriString))
            .let { listOf(it) + registrationViewModel.optionalPhotoUris }

    suspend fun isExplicit(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val jpeg = compressImage(context, uri) ?: return@withContext false
        val b64  = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        moderateImages(listOf(b64))
    }

    // 1) Crop launcher
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultData = result.data ?: return@rememberLauncherForActivityResult
            val outUri = UCrop.getOutput(resultData) ?: return@rememberLauncherForActivityResult
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

    val privateAlbumPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (registrationViewModel.privateAlbumUris.size >= 10) {
            Toast.makeText(context, "Maximum 10 items", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        registrationViewModel.privateAlbumUris.add(uri)
        uploadPrivateAlbumMedia(context, storageRef, uri, registrationViewModel)
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
                            if (index == 0 && registrationViewModel.profilePictureUri == null) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                        .clickable { photoPickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = stringResource(R.string.add_photos_button),
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (index == 0 && registrationViewModel.profilePictureUri != null) {
                                            registrationViewModel.profilePictureUri = null
                                            if (registrationViewModel.optionalPhotoUris.isNotEmpty()) {
                                                val newProfile = registrationViewModel.optionalPhotoUris.removeAt(0)
                                                registrationViewModel.profilePictureUri = newProfile
                                                if (registrationViewModel.optionalPhotoUrls.isNotEmpty()) {
                                                    registrationViewModel.optionalPhotoUrls.removeAt(0)
                                                }
                                                uploadProfilePicToFirebase(
                                                    context,
                                                    storageRef,
                                                    newProfile,
                                                    registrationViewModel
                                                )
                                            }
                                        } else {
                                            val optIndex = if (registrationViewModel.profilePictureUri != null) index - 1 else index
                                            if (optIndex < registrationViewModel.optionalPhotoUrls.size) {
                                                registrationViewModel.optionalPhotoUrls.removeAt(optIndex)
                                            }
                                            registrationViewModel.optionalPhotoUris.removeAt(optIndex)
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .size(24.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A2A2A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.private_album), color = Color.White, modifier = Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    if (expanded) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(registrationViewModel.privateAlbumUris) { index, uri ->
                                Box(modifier = Modifier.size(100.dp)) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    IconButton(
                                        onClick = {
                                            registrationViewModel.privateAlbumUris.removeAt(index)
                                            if (index < registrationViewModel.privateAlbumUrls.size) {
                                                registrationViewModel.privateAlbumUrls.removeAt(index)
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
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
                            if (registrationViewModel.privateAlbumUris.size < 10) {
                                item {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(100.dp)
                                            .border(2.dp, Color(0xFFFF6000), RoundedCornerShape(8.dp))
                                            .clickable { privateAlbumPickerLauncher.launch("*/*") }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            /* ---------------- Voice bio (optional) ---------------- */
            item {
                Text(
                    text = stringResource(R.string.voice_bio_label),
                    color = Color.White,
                    fontSize = 16.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::toggleRecording) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isRecording) Color.Red else Color(0xFFFF6000),
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

//            /* ---------------- Next button ---------------- */
//            item {
//                Button(
//                    onClick = {
//                        if (registrationViewModel.profilePictureUri == null && registrationViewModel.profilePicUrl.isNullOrBlank()) {
//                            registrationViewModel.profilePicUrl =
//                                "android.resource://${context.packageName}/drawable/local_placeholder"
//                        }
//                        if (canProceed) onNext()
//                    },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(54.dp),
//                    enabled = canProceed,
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = if (canProceed) Color(0xFFFF6000) else Color.DarkGray
//                    )
//                ) {
//                    Text(stringResource(R.string.next_button), color = Color.White)
//                }
//            }
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
        val jpegBytes = compressImage(context, uri) ?: return@launch
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

fun uploadPrivateAlbumMedia(
    context: Context,
    storageRef: StorageReference,
    uri: Uri,
    registrationViewModel: RegistrationViewModel
) {
    (context as? ComponentActivity)?.lifecycleScope?.launch {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
        val ref = storageRef.child("users/$userId/private/${uri.lastPathSegment ?: System.currentTimeMillis()}")
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    registrationViewModel.privateAlbumUrls.add(downloadUri.toString())
                }
            }
            .addOnFailureListener { e ->
                Log.e("UploadMedia", "Private-media upload failed: ${e.message}")
            }
    }
}

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun EnterProfileHeadlineScreen(
//    registrationViewModel: RegistrationViewModel,
//    onNext: () -> Unit
//) {
//    // Local state for name & height to mirror registrationViewModel
//    var heightText by remember { mutableStateOf(registrationViewModel.height.toString()) }
//    var feetText   by remember {
//        mutableStateOf(registrationViewModel.height2.getOrNull(0)?.toString() ?: "")
//    }
//    var inchText   by remember {
//        mutableStateOf(registrationViewModel.height2.getOrNull(1)?.toString() ?: "")
//    }
//    var headline by remember { mutableStateOf(TextFieldValue(registrationViewModel.bio)) }
//
//    Scaffold(
//        content = { innerPadding ->
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(Color(0xFF1A1A1A))
//                    .padding(innerPadding),
//                contentAlignment = Alignment.Center
//            ) {
//                Column(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(horizontal = 32.dp),
//                    verticalArrangement = Arrangement.Center,
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    // ---- Full Name (optional) ----
//                    TextFieldWithLabel(
//                        label = stringResource(R.string.full_name_label),
//                        value = registrationViewModel.name,
//                        onValueChange = { registrationViewModel.name = it }
//                    )
//
//                    Spacer(modifier = Modifier.height(16.dp))
//
//                    // ---- Height (optional) ----
//                    Text(
//                        text = stringResource(R.string.height_label),
//                        color = Color.White,
//                        fontSize = 18.sp
//                    )
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Switch(
//                            checked = registrationViewModel.isHeightInFeet,
//                            onCheckedChange = { useFeet ->
//                                registrationViewModel.isHeightInFeet = useFeet
//                                if (useFeet) {
//                                    val (f, i) = registrationViewModel.cmToFeetInches(registrationViewModel.height)
//                                    feetText = f.toString()
//                                    inchText = i.toString()
//                                } else {
//                                    val f = feetText.toIntOrNull() ?: 0
//                                    val i = inchText.toIntOrNull() ?: 0
//                                    heightText = registrationViewModel.feetInchesToCm(f, i).toString()
//                                }
//                            },
//                            colors = SwitchDefaults.colors(
//                                checkedThumbColor = Color(0xFFFF6000),
//                                uncheckedThumbColor = Color.White
//                            )
//                        )
//                        Text(
//                            text = if (registrationViewModel.isHeightInFeet)
//                                stringResource(R.string.feet_inches_label)
//                            else
//                                stringResource(R.string.centimeters_label),
//                            color = Color.White
//                        )
//                    }
//
//                    if (registrationViewModel.isHeightInFeet) {
//                        Row(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(vertical = 8.dp),
//                            horizontalArrangement = Arrangement.spacedBy(16.dp),
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            OutlinedTextField(
//                                value = feetText,
//                                onValueChange = { newFeet ->
//                                    feetText = newFeet
//                                    registrationViewModel.height2 = listOf(
//                                        newFeet.toIntOrNull() ?: 0,
//                                        registrationViewModel.height2.getOrNull(1) ?: 0
//                                    )
//                                },
//                                label = { Text(stringResource(R.string.feet_label), color = Color.White) },
//                                singleLine = true,
//                                modifier = Modifier
//                                    .weight(1f)
//                                    .heightIn(56.dp),
//                                colors = TextFieldDefaults.outlinedTextFieldColors(
//                                    focusedBorderColor = Color(0xFFFF6000),
//                                    unfocusedBorderColor = Color.White,
//                                    cursorColor = Color.White,
//                                    focusedLabelColor = Color(0xFFFF6000),
//                                    unfocusedLabelColor = Color.White
//                                )
//                            )
//
//                            OutlinedTextField(
//                                value = inchText,
//                                onValueChange = { newInch ->
//                                    inchText = newInch
//                                    registrationViewModel.height2 = listOf(
//                                        registrationViewModel.height2.getOrNull(0) ?: 0,
//                                        newInch.toIntOrNull() ?: 0
//                                    )
//                                },
//                                label = {
//                                    Text(
//                                        stringResource(R.string.inches_label),
//                                        color = Color.White
//                                    )
//                                },
//                                singleLine = true,
//                                modifier = Modifier
//                                    .weight(1f)
//                                    .heightIn(56.dp),
//                                colors = TextFieldDefaults.outlinedTextFieldColors(
//                                    focusedBorderColor = Color(0xFFFF6000),
//                                    unfocusedBorderColor = Color.White,
//                                    cursorColor = Color.White,
//                                    focusedLabelColor = Color(0xFFFF6000),
//                                    unfocusedLabelColor = Color.White
//                                )
//                            )
//                        }
//                    } else {
//                        TextFieldWithLabel(
//                            label = stringResource(R.string.height_cm_label),
//                            value = heightText,
//                            onValueChange = { newCm ->
//                                heightText = newCm
//                                registrationViewModel.height = newCm.toIntOrNull()
//                                    ?: registrationViewModel.height
//                            }
//                        )
//                    }
//
//                    // Add a spacer after height ✔
//                    Spacer(modifier = Modifier.height(24.dp))
//                    // Headline/Bio TextField
//                    OutlinedTextField(
//                        value = headline,
//                        onValueChange = {
//                            headline = it
//                            registrationViewModel.bio = it.text
//                        },
//                        label = { Text("Bio (optional)", color = Color(0xFFFF6000)) },
//                        singleLine = true,
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(bottom = 16.dp),
//                        colors = OutlinedTextFieldDefaults.colors(
//                            focusedTextColor = Color.White,
//                            unfocusedTextColor = Color.White,
//                            cursorColor = Color(0xFFFF4500),
//                            focusedBorderColor = Color(0xFFFF4500),
//                            unfocusedBorderColor = Color(0xFFFF6000)
//                        )
//                    )
//
//                    // Next Button
//                    Button(
//                        onClick = {
//                            registrationViewModel.bio = headline.text
//                            onNext()
//                        },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .height(56.dp),
//                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6000)),
//                        shape = CircleShape,
//                        elevation = ButtonDefaults.buttonElevation(8.dp)
//                    ) {
//                        Text(
//                            text = stringResource(R.string.next_button),
//                            color = Color.White,
//                            fontSize = 18.sp,
//                            fontWeight = FontWeight.Bold
//                        )
//                    }
//                }
//            }
//        }
//    )
//}
