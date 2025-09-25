package com.am24.am24

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.VisualTransformation
import com.am24.am24.ui.theme.AppTheme
import com.am24.am24.ui.theme.DarkGrayBackground
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import java.util.concurrent.TimeUnit

/* ─────────────────────────── Activity ─────────────────────────── */

class LoginActivity : ComponentActivity() {
    private val NO_EMAIL = "__NO_EMAIL__"

    private lateinit var auth: FirebaseAuth
    private val isLoading = mutableStateOf(false)
    private val loginProgress = mutableStateOf(0f)
    private var storedVerificationId: String? = null
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    // 1) Google Sign-In launcher
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java) ?: run {
                    Toast.makeText(this, "Google sign-in failed.", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        auth.currentUser?.getIdToken(true)
                            ?.addOnSuccessListener { res ->
                                res.token?.let { TokenStorageManager.saveToken(this@LoginActivity, it) }
                            }
                        // success → go to main
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: ApiException) {
                // user cancelled or error
                Toast.makeText(this, "Google sign-in cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultLang = if (Locale.getDefault().country.equals("MX", true)) "es" else "en"
        val languageCode = prefs.getString("language", defaultLang) ?: defaultLang
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN

        val prefill = intent.getStringExtra("prefill_email") ?: ""
        val deviceAllowsPhone = CountryUtil.isProbablyInIndia(this)
        val allowPhoneAuthState = mutableStateOf(deviceAllowsPhone)

        setContent {
            AppTheme {
                val allowPhoneAuth = allowPhoneAuthState.value
                LoginScreen(
                    cachedUser        = null,           // ◀︎ pass it in
                    initialUserOrEmail = prefill,
                    isLoading         = isLoading.value,
                    progress          = loginProgress.value,
                    allowPhoneAuth    = allowPhoneAuth,
                    onLoginClick      = ::handleLogin,
                    onForgotPassword  = ::handlePasswordReset,
                    onPhoneLogin       = ::handlePhoneLogin,     // Add this
                    onBackToLanding   = ::onBackToLanding                )
            }
        }
    }

    private fun handlePhoneLogin(input: String) {
        // If it's a phone number, start OTP
        if (input.all { it.isDigit() } && input.length >= 10) {
            startPhoneNumberVerification("+91$input")
        }
        // If it's an OTP, verify
        else if (input.length == 6 && storedVerificationId != null) {
            verifyPhoneNumberWithCode(storedVerificationId!!, input)
        } else {
            toast("Invalid input")
        }
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        isLoading.value = true
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(phoneAuthCallbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val phoneAuthCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) { // <- FIX: FirebaseException, not Exception
            isLoading.value = false
            toast("Verification failed: ${e.message}")
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            storedVerificationId = verificationId
            resendToken = token
            isLoading.value = false
            toast("OTP sent to your phone.")
        }
    }

    private fun verifyPhoneNumberWithCode(verificationId: String, code: String) {
        isLoading.value = true
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                isLoading.value = false
                if (task.isSuccessful) {
                    auth.currentUser?.getIdToken(true)
                        ?.addOnSuccessListener { res ->
                            res.token?.let { TokenStorageManager.saveToken(this@LoginActivity, it) }
                        }
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    toast("Sign-in failed: ${task.exception?.message}")
                }
            }
    }

    private fun toast(msg: String) =
        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()

    private fun handleLogin(userOrEmail: String, pwd: String) {
        isLoading.value = true
        loginProgress.value = 0f
        val trimmed = userOrEmail.trim()

        if (trimmed.isBlank()) {        // 🆕 guard ①
            toast("Please enter your username or e-mail.")
            return
        }
        if (pwd.isBlank()) {            // optional, but nice
            toast("Password can’t be empty.")
            return
        }

        val invalidChars = Regex("[.#$\\[\\]]")
        if (!trimmed.contains("@") && invalidChars.containsMatchIn(trimmed)) {
            toast("Username can’t contain .  #  $  [  ]")
            isLoading.value = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {

            // ── 0) if it’s a “username” (no @), see if publicUsers says “google” ──
            if (!trimmed.contains("@")) {
                val publicRef = FirebaseRefs.db.reference
                    .child("publicUsers")
                    .child(trimmed.lowercase())
                    .child("signInMethod")
                    .get()
                    .await()
                val signInMethod = publicRef.getValue(String::class.java)
                if (signInMethod == "google") {
                    // kick off Google flow and bail
                    withContext(Dispatchers.Main) {
                        isLoading.value = false
                        startGoogleSignIn()
                    }
                    return@launch
                }
            }

            // ── 1) fall back to your old resolveToEmail / lookup logic ──
            val lookup = if (trimmed.contains("@")) trimmed
            else            resolveToEmail(trimmed)

            when (lookup) {
                null -> return@launch withContext(Dispatchers.Main) {
                    isLoading.value = false
                    toast("Username not found")
                }
                NO_EMAIL -> return@launch withContext(Dispatchers.Main) {
                    isLoading.value = false
                    toast("This account was created with phone-OTP or Social Sign-In. Please log in using that method.")
                }
            }

            // ── 2) now lookup is a real e-mail, continue with password login ──
            val email = lookup!!
            loginProgress.value = 0.5f

            try {
                auth.signInWithEmailAndPassword(email, pwd).await()
                auth.currentUser?.getIdToken(true)?.await()?.token?.let {
                    TokenStorageManager.saveToken(this@LoginActivity, it)
                }
                loginProgress.value = 1f
                withContext(Dispatchers.Main) {
                    isLoading.value = false
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                // … your existing catch + fetchSignInMethods logic …
            }
        }
    }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    private fun handlePasswordReset(userOrEmail: String) {
        val trimmed = userOrEmail.trim()

        if (trimmed.isBlank()) {        // 🆕 guard ②
            toast("Please enter your username or e-mail.")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val email = resolveToEmail(trimmed) ?: run {
                withContext(Dispatchers.Main) { toast("No account found.") }
                return@launch
            }

            try {
                auth.sendPasswordResetEmail(email).await()
                withContext(Dispatchers.Main) { toast("Reset link sent to $email") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("Error: ${e.message}") }
            }
        }
    }

    private fun onBackToLanding() {
        val intent = Intent(this, LandingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private suspend fun resolveToEmail(userOrEmail: String): String? {
        val trimmed = userOrEmail.trim()
        if (trimmed.contains("@")) return trimmed

        // 0) publicUsers → sign-in method
        val methodSnap = FirebaseRefs.db.reference
            .child("publicUsers")
            .child(trimmed.lowercase(Locale.getDefault()))
            .child("signInMethod")
            .get()
            .await()
        if (methodSnap.getValue(String::class.java) == "google") return NO_EMAIL

        // 1) username → uid (handle both schemas)
// 1) username → uid
        val uidSnap = FirebaseRefs.db.reference
            .child("usernames")
            .child(trimmed.lowercase(Locale.getDefault()))
            .get()
            .await()

        val uid: String? = when (val raw = uidSnap.value) {
            is String    -> raw
            is Map<*, *> -> raw["uid"] as? String
            else         -> null
        }

// ⬇️ Stop right here if we still don't have a uid
        val uidNonNull = uid ?: return null          // uidNonNull is now String (not String?)

// 2) uid → email
        val emailSnap = FirebaseRefs.db.reference
            .child("users")
            .child(uidNonNull)                        // ✅ no more mismatch
            .child("email")
            .get()
            .await()
        val email = emailSnap.getValue(String::class.java)


        return when {
            email == null       -> null
            email.isBlank()     -> NO_EMAIL
            else                -> email
        }
    }
}


/* ─────────────────────────── UI ─────────────────────────── */

@Composable
fun LoginScreen(
    cachedUser: String? = null,                 // ▶︎ new
    initialUserOrEmail: String = "",
    isLoading: Boolean,
    progress: Float,
    allowPhoneAuth: Boolean,
    onLoginClick: (String, String) -> Unit,
    onForgotPassword: (String) -> Unit,
    onPhoneLogin: (String) -> Unit,           // ◀︎ New param for phone OTP login
    onBackToLanding: () -> Unit
) {
    // OTP dialog state
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpInput     by remember { mutableStateOf("") }
    var phoneForOtp  by remember { mutableStateOf("") }

    var phoneNumber by remember { mutableStateOf(TextFieldValue("")) }

    var userOrEmail by remember { mutableStateOf(TextFieldValue(initialUserOrEmail)) }
    var password    by remember { mutableStateOf(TextFieldValue("")) }
    var passwordVisible by remember { mutableStateOf(false) }   // ⬅ NEW

    val context = LocalContext.current

    /* dialogs */
    var showPwdDialog   by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var dialogInput     by remember { mutableStateOf("") }

    /* results */
    var resultLabel by remember { mutableStateOf("") }
    var resultValue by remember { mutableStateOf("") }

    val db    = FirebaseRefs.db
    val scope = rememberCoroutineScope()

    // OTP Dialog
    if (showOtpDialog) {
        SimpleInputDialog(
            title = "Enter OTP",
            hint  = "6-digit OTP",
            input = otpInput,
            onInputChange = { otpInput = it },
            onDismiss = { showOtpDialog = false },
            onConfirm = {
                // You’ll handle OTP verification in Activity, pass to callback
                onPhoneLogin(otpInput)
                showOtpDialog = false
                otpInput = ""
            }
        )
    }

    /* ---------- Dialog builders ---------- */

    if (showPwdDialog) {
        SimpleInputDialog(
            title = stringResource(id = R.string.reset_password),
            hint  = stringResource(id = R.string.username_or_email),
            input = dialogInput,
            onInputChange = { dialogInput = it },
            onDismiss = { showPwdDialog = false },
            onConfirm = {
                onForgotPassword(dialogInput)
                showPwdDialog = false
                dialogInput = ""
            }
        )
    }

    if (showEmailDialog) {
        SimpleInputDialog(
            title = stringResource(id = R.string.recover_email),
            hint  = stringResource(id = R.string.username),
            input = dialogInput,
            onInputChange = { dialogInput = it },
            onDismiss = { showEmailDialog = false },
            onConfirm = {
                val username = dialogInput.trim()
                if (username.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.enter_username), Toast.LENGTH_LONG).show()
                } else {
                    scope.launch {
                        val uidSnap = db.reference.child("usernames")
                            .child(username).get().await()
                        if (!uidSnap.exists()) {
                            Toast.makeText(context, "Username not found", Toast.LENGTH_LONG).show()
                        } else {
                            val uid = uidSnap.getValue(String::class.java) ?: ""
                            val emailSnap = db.reference.child("users")
                                .child(uid).child("email").get().await()
                            resultLabel = context.getString(R.string.your_email)
                            resultValue = emailSnap.getValue(String::class.java) ?: "(none)"
                        }
                    }
                }
                showEmailDialog = false
                dialogInput = ""
            }
        )
    }

    // ── Loading overlay ──
    if (isLoading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x88000000)),  // semi-transparent black
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // determinate circular with progress [0f..1f]
                CircularProgressIndicator(progress = progress)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    /* ---------- Main layout ---------- */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        IconButton(
            onClick = onBackToLanding,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = stringResource(id = R.string.login),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = userOrEmail,
                onValueChange = { userOrEmail = it },
                label = { Text(stringResource(id = R.string.username_or_email), color = Color(0xFFFF6600)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = orangeOutlinedColors()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(id = R.string.password), color = Color(0xFFFF6600)) },
                singleLine = true,
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None        // 👁 show plain text
                else
                    PasswordVisualTransformation(),  // •••••••
                trailingIcon = {                     // 👁 toggle
                    val icon =
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(icon, contentDescription = null, tint = Color(0xFFFF6600))
                    }
                },
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                colors = orangeOutlinedColors()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onLoginClick(userOrEmail.text, password.text) },
                modifier = Modifier
                    .height(46.dp),
                border = BorderStroke(1.dp, KupidxOrange),   // <- fixed line
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DarkGrayBackground,
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.login),
                    color = KupidxOrange,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))

            if (allowPhoneAuth) {
                // Phone OTP Section
                // Phone OTP Section
                Text(
                    text = "OR",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number", color = Color(0xFFFF6600)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = orangeOutlinedColors()
                )
                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        // You can validate and start OTP flow here
                        if (phoneNumber.text.length >= 10) {
                            onPhoneLogin(phoneNumber.text)
                            phoneForOtp = phoneNumber.text
                            showOtpDialog = true   // Show OTP dialog for user input
                        } else {
                            Toast.makeText(context, "Enter valid phone number", Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        "Login with OTP",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = { showPwdDialog = true }) {
                    Text(stringResource(id = R.string.forgot_password), color = Color.White)
                }
                TextButton(onClick = { showEmailDialog = true }) {
                    Text(stringResource(id = R.string.forgot_email), color = Color.White)
                }
            }

            if (resultValue.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = resultValue,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(resultLabel, color = Color(0xFFFF6600)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = orangeOutlinedColors()
                )
            }
        }
    }
}

/* ---------- reusable bits ---------- */

@Composable
private fun SimpleInputDialog(
    title: String,
    hint: String,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(id = R.string.submit), color = Color(0xFFFF6600)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel), color = Color(0xFFFF6600)) } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = orangeOutlinedColors()
            )
        },
        containerColor = Color(0xFF1A1A1A),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
private fun orangeOutlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFFFF6600),
    focusedBorderColor = Color(0xFFFF6600),
    unfocusedBorderColor = Color(0xFFFF6600),
    focusedLabelColor = Color(0xFFFF6600),
    unfocusedLabelColor = Color(0xFFFF6600)
)