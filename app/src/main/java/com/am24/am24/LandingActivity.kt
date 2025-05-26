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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.am24.am24.ui.theme.AppTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.util.Locale

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
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN

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
                    onFacebookSignIn = { /* TODO */ }
                )
            }
        }
    }

    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    /**
     * Check existing providers for this email before signing in with Google:
     * - If already has "google.com" → direct sign-in
     * - If has "password" only → prompt to link Google
     * - If none → new user → Google sign-up → registration
     */
    private fun firebaseAuthWithGoogle(idToken: String?, account: GoogleSignInAccount?) {
        val email = account?.email ?: return
        firebaseAuth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->
                val methods = result.signInMethods.orEmpty()
                when {
                    methods.contains(GoogleAuthProvider.GOOGLE_SIGN_IN_METHOD) -> {
                        // Already linked → sign in directly
                        signInAndGoMain(idToken)
                    }
                    methods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD) -> {
                        // E/P only → prompt to link Google credential
                        promptForPasswordAndLink(email, idToken)
                    }
                    else -> {
                        // No providers → new user flow
                        signInAndHandleRegistration(idToken)
                    }
                }
            }
            .addOnFailureListener {
                // fallback flow
                signInAndHandleRegistration(idToken)
            }
    }

    /**
     * Sign in with Google and if returning user go to Main, otherwise registration
     */
    private fun signInAndHandleRegistration(idToken: String?) {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(cred)
            .addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) {
                    Log.w("LandingActivity", "Firebase sign in failed", task.exception)
                    return@addOnCompleteListener
                }
                val isNew = task.result?.additionalUserInfo?.isNewUser == true
                if (isNew) startRegistrationFlow() else goToMain()
            }
    }

    /**
     * Sign in with Google and immediately go to Main
     */
    private fun signInAndGoMain(idToken: String?) {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(cred)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) goToMain()
                else Log.w("LandingActivity", "Google sign-in failed", task.exception)
            }
    }

    /**
     * Ask for the existing E/P password to then link Google credential into that account
     */
    private fun promptForPasswordAndLink(email: String, idToken: String?) {
        // Show your own dialog/UI to collect password securely
        collectPasswordFromUser(email) { password ->
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val cred = GoogleAuthProvider.getCredential(idToken, null)
                    authResult.user
                        ?.linkWithCredential(cred)
                        ?.addOnSuccessListener { goToMain() }
                        ?.addOnFailureListener { e -> toast("Could not link Google: ${e.message}") }
                }
                .addOnFailureListener { toast("Password incorrect; cannot link Google.") }
        }
    }

    private fun startRegistrationFlow() {
        Intent(this, RegistrationActivity::class.java).also { intent ->
            intent.putExtra("isGoogleSignUp", true)
            intent.putExtra("google_email", firebaseAuth.currentUser?.email)
            intent.putExtra("google_displayName", firebaseAuth.currentUser?.displayName)
            intent.putExtra("google_photoUrl", firebaseAuth.currentUser?.photoUrl?.toString())
            startActivity(intent)
        }
        finish()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // Stubbed: implement secure password prompt dialog
    private fun collectPasswordFromUser(email: String, onPassword: (String) -> Unit) {
        // e.g. AlertDialog with TextField, then invoke onPassword(input)
    }
}

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
    var showLanguagePicker by remember { mutableStateOf(false) }

    if (shouldRestart) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(100)
            (context as? Activity)?.recreate()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black, Color(0xFF1A1A1A))))
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.welcome_kupidx),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            SocialSignInButtons(onGoogleSignIn, onFacebookSignIn)
            Button(
                    onClick = onRegisterClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor   = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFFFF6600))
            ) {
            Text(
                text = stringResource(R.string.register),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
            Spacer(Modifier.height(16.dp))
            // Login button
            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor   = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFFF6600))
            ) {
                Text(
                    text = stringResource(R.string.login),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        LanguageSelectionBar(selectedLanguage) { lang ->
            prefs.edit().putString("language", lang).apply()
            updateLocale(context, lang)
            shouldRestart = true
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SocialSignInButtons(
    onGoogleSignIn: () -> Unit,
    onFacebookSignIn: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SocialSignInButton(
            logo = R.drawable.ic_google_logo,
            text = "Sign in with Google",
            contentColor = Color.Black,
            onClick = onGoogleSignIn
        )
        SocialSignInButton(
            logo = R.drawable.facebook_logo,
            text = "Sign in with Facebook",
            contentColor = Color(0xFF1877F2), // FB blue
            onClick = onFacebookSignIn
        )
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun LanguageSelectionBar(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    // display order
    val languages = listOf(
        "English" to "en",
        "हिन्दी"   to "hi",
        "বাংলা"    to "bn",
        // locked / coming-soon set
        "தமிழ்"    to "ta",
        "ଓଡ଼ିଆ"     to "or",   // ← NEW: Odia
        "తెలుగు"  to "te",
        "मराठी"     to "mr",
        "ગુજરાતી"  to "gu",
        "ಕನ್ನಡ"    to "kn",
        "മലയാളം"  to "ml",
        "অসমীয়া"  to "as",
        "ਪੰਜਾਬੀ"   to "pa"
    )

    val unlockedCodes = setOf("en", "hi", "bn")          // only these are live
    val scroll = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        languages.forEach { (label, code) ->
            val isUnlocked = code in unlockedCodes
            val isSelected = selectedLanguage == code

            // show label + optional lock badge in a Column so the 🔒 sits “above”
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 🔒 only for coming-soon languages
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
                    enabled = isUnlocked,             // greys out + blocks ripple
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isUnlocked && isSelected -> Color(0xFFFF6000)    // orange
                            isUnlocked               -> Color.DarkGray
                            else                     -> Color.Gray           // locked
                        },
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(label, fontSize = 14.sp)
                }
            }
        }
    }
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
            .height( 50.dp ),
        shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor   = contentColor
        ),
        border = BorderStroke(1.dp, Color.LightGray),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Icon(
            painter = painterResource(logo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(25.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLandingScreen() {
    AppTheme {
        LandingScreen({}, {}, {}, {})
    }
}
