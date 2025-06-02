package com.am24.am24

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.am24.am24.ui.theme.AppTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import java.util.Locale

/* ──────────────────────────────────────────────────────────────────────────── */
/*                              ACTIVITY                                       */
/* ──────────────────────────────────────────────────────────────────────────── */

class LandingActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)
                Log.d("LandingActivity", "Google sign in successful: ${account?.email}")
                firebaseAuthWithGoogle(account?.idToken, account)
            } catch (e: ApiException) {
                Log.w("LandingActivity", "Google sign in failed", e)
                toast("Google sign-in failed: ${e.localizedMessage}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* Let content draw edge-to-edge so we can handle insets ourselves */
        WindowCompat.setDecorFitsSystemWindows(window, false)

        firebaseAuth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            AppTheme {
                LandingScreen(
                    onLoginClick = {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    },
                    onRegisterClick = {
                        startActivity(Intent(this, RegistrationActivity::class.java))
                        finish()
                    },
                    onGoogleSignIn = { signInWithGoogle() },
                    onFacebookSignIn = { /* TODO: add Facebook logic */ }
                )
            }
        }
    }

    private fun signInWithGoogle() {
        // Always sign out first to force a fresh account chooser
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    /**
     * Called immediately after GoogleSignInAccount is retrieved.
     * Exchanges the Google ID token for a Firebase credential, then either:
     *  - If this email/provider already exists with Firebase, simply sign in.
     *  - Otherwise, handle registration (email/password link or new user flow).
     */
    private fun firebaseAuthWithGoogle(idToken: String?, account: GoogleSignInAccount?) {
        Log.d("LandingActivity", "firebaseAuthWithGoogle called, idToken=$idToken")
        if (idToken.isNullOrEmpty()) {
            Log.e("LandingActivity", "ID token was null or empty. Aborting.")
            toast("ID token was null. Check your OAuth client / SHA-1.")
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Signed in successfully—now decide if this is a new user or an existing one
                    Log.d("LandingActivity", "Firebase signInWithCredential: succeeded.")
                    if (task.result?.additionalUserInfo?.isNewUser == true) {
                        // A brand-new Firebase user: run registration flow
                        startRegistrationFlow()
                    } else {
                        // Existing user: go directly to MainActivity
                        signInAndGoMain(idToken)
                    }
                } else {
                    // If sign-in fails because the email already exists with a different provider,
                    // we need to link or ask for a password.
                    val exception = task.exception
                    if (exception is FirebaseAuthUserCollisionException) {
                        // Email exists with a different credential.
                        val email = exception.email ?: ""
                        Log.w("LandingActivity", "Collision: email exists: $email")
                        promptForPasswordAndLink(email, idToken)
                    } else {
                        Log.e("LandingActivity", "Firebase signInWithCredential: failed", exception)
                        toast("Firebase authentication failed: ${exception?.localizedMessage}")
                    }
                }
            }
    }

    /**
     * If the email returned by Google already exists in Firebase, but is not yet linked to Google,
     * we prompt for the user’s password so we can sign in with email/password and then link the Google credential.
     */
    private fun promptForPasswordAndLink(email: String, idToken: String?) {
        // Show a simple AlertDialog with an EditText to collect password
        // Once password is collected, call collectPasswordFromUser() → link credential
        collectPasswordFromUser(email) { password ->
            val existingCredential = EmailAuthProvider.getCredential(email, password)
            firebaseAuth.signInWithCredential(existingCredential)
                .addOnCompleteListener(this) { signInTask ->
                    if (signInTask.isSuccessful) {
                        // Already signed in via email/password—now link Google credential
                        val googleCred = GoogleAuthProvider.getCredential(idToken, null)
                        firebaseAuth.currentUser
                            ?.linkWithCredential(googleCred)
                            ?.addOnCompleteListener(this) { linkTask ->
                                if (linkTask.isSuccessful) {
                                    Log.d("LandingActivity", "Successfully linked Google account.")
                                    toast("Accounts linked! Welcome back.")
                                    goToMain()
                                } else {
                                    Log.e(
                                        "LandingActivity",
                                        "Failed to link Google credential",
                                        linkTask.exception
                                    )
                                    toast("Failed to link accounts: ${linkTask.exception?.localizedMessage}")
                                }
                            }
                    } else {
                        Log.e(
                            "LandingActivity",
                            "Email/Password sign in failed when linking",
                            signInTask.exception
                        )
                        toast("Password incorrect or sign-in failed: ${signInTask.exception?.localizedMessage}")
                    }
                }
        }
    }

    /**
     * Called when Google-authenticated user is brand-new. Fire off any registration logic:
     * e.g. create a Firebase user profile entry, collect additional info, etc.
     */
    private fun startRegistrationFlow() {
        // You can collect additional info here (e.g. username, displayName, etc.).
        // For simplicity, we’ll just go straight to “Main” or finish registration.
        Log.d("LandingActivity", "Starting registration flow for new user.")
        // … insert any “extra registration” UI/dialog if needed …
        goToMain()
    }

    /** Called when an existing Google-linked Firebase user should be sent to MainActivity. */
    private fun signInAndGoMain(idToken: String?) {
        Log.d("LandingActivity", "Existing user authenticated; navigating to MainActivity.")
        goToMain()
    }

    /** Launches the main/home screen. */
    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /** Simple Toast helper. */
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    /**
     * Shows a password‐input dialog, then calls `onPassword(password)` once the user confirms.
     * (This was “unchanged” in your old code; we implement it via an AlertDialog here.)
     */
    private fun collectPasswordFromUser(email: String, onPassword: (String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Link accounts")
        builder.setMessage("Enter password for $email to link your Google account:")
        val input = android.widget.EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        builder.setView(input)
        builder.setPositiveButton("OK") { dialog, _ ->
            val password = input.text.toString()
            dialog.dismiss()
            onPassword(password)
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }
}

/* ──────────────────────────────────────────────────────────────────────────── */
/*                              COMPOSABLES                                    */
/* ──────────────────────────────────────────────────────────────────────────── */

fun updateLocale(context: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = context.resources.configuration
    config.setLocale(locale)
    return context.createConfigurationContext(config)
}

@Composable
fun LandingScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onFacebookSignIn: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var selectedLanguage by remember { mutableStateOf(prefs.getString("language", "en")!!) }
    var shouldRestart by remember { mutableStateOf(false) }

    if (shouldRestart) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(100)
            (context as? Activity)?.recreate()
        }
    }

    /* Box lets us pin the language row to the bottom while
       keeping the button stack perfectly centred.            */
    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black, Color(0xFF1A1A1A))
                )
            )
    ) {

        /* ───────  CENTRED WELCOME + BUTTONS  ─────── */
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.welcome_kupidx),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            SocialSignInButtons(onGoogleSignIn, onFacebookSignIn)

            Spacer(Modifier.height(24.dp)) // ← spacer between Meta + Register

            OutlinedButton(
                onClick = onRegisterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, Color(0xFFFF6600)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text(
                    stringResource(R.string.register),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, Color(0xFFFF6600)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text(
                    stringResource(R.string.login),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        /* ───────  BOTTOM-ANCHORED LANGUAGE ROW  ─────── */
        LanguageSelectionBar(
            selectedLanguage = selectedLanguage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding() // keeps row above gesture bar
        ) { lang ->
            prefs.edit().putString("language", lang).apply()
            updateLocale(context, lang)
            shouldRestart = true
        }
    }
}

/* ───────────────── Social sign-ins ───────────────── */

@Composable
fun SocialSignInButtons(
    onGoogleSignIn: () -> Unit,
    onFacebookSignIn: () -> Unit
) = Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

    SocialSignInButton(
        logo = R.drawable.ic_google_logo,
        text = stringResource(R.string.continue_with_google),
        contentColor = Color.Black,
        onClick = onGoogleSignIn
    )

    SocialSignInButton(
        logo = R.drawable.facebook_logo,
        text = stringResource(R.string.continue_with_facebook),
        contentColor = Color(0xFF1877F2),
        onClick = onFacebookSignIn
    )
}

@Composable
fun SocialSignInButton(
    @DrawableRes logo: Int,
    text: String,
    backgroundColor: Color = Color.White,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, Color.LightGray),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Icon(
            painter = painterResource(logo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(25.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            maxLines = 1, // keeps a single row
            overflow = TextOverflow.Ellipsis // fade if user pumps font scale
        )
    }
}

/* ───────────────── Languages row ───────────────── */

@Composable
fun LanguageSelectionBar(
    selectedLanguage: String,
    modifier: Modifier = Modifier,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf(
        "English" to "en",
        "हिन्दी" to "hi",
        "বাংলা" to "bn",
        "தமிழ்" to "ta",
        "ಕನ್ನಡ" to "kn",
        "తెలుగు" to "te",
        // locked
        "मराठी" to "mr",
        "ગુજરાતી" to "gu",
        "മലയാളം" to "ml",
        "অসমীয়া" to "as",
        "ਪੰਜਾਬੀ" to "pa",
        "ଓଡ଼ିଆ" to "or"
    )

    val unlockedCodes = setOf("en", "hi", "bn", "ta", "kn", "te")
    val scroll = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scroll)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        languages.forEach { (label, code) ->
            val isUnlocked = code in unlockedCodes
            val isSelected = selectedLanguage == code

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isUnlocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Button(
                    onClick = { if (isUnlocked) onLanguageSelected(code) },
                    enabled = isUnlocked,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp) // 36 dp minimum, but may grow
                        .padding(horizontal = 0.dp), // keep width logic unchanged
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isUnlocked && isSelected -> Color(0xFFFF6000)
                            isUnlocked -> Color.DarkGray
                            else -> Color.Gray
                        },
                        contentColor = Color.White
                    )
                ) {
                    Text(label, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

/* ───────────────── PREVIEW ───────────────── */

@Preview(showBackground = true)
@Composable
fun PreviewLandingScreen() {
    AppTheme {
        LandingScreen({}, {}, {}, {})
    }
}
