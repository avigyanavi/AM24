package com.am24.am24

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
            Button(
                onClick = onRegisterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                shape = CircleShape
            ) {
                Text(stringResource(R.string.register), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = CircleShape
            ) {
                Text(stringResource(R.string.login), color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            SocialSignInButtons(onGoogleSignIn, onFacebookSignIn)
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
fun LanguageSelectionBar(selectedLanguage: String, onLanguageSelected: (String) -> Unit) {
    val languages = listOf("English" to "en", "বাংলা" to "bn", "हिन्दी" to "hi")
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        languages.forEach { (label, code) ->
            Button(
                onClick = { onLanguageSelected(code) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedLanguage == code) Color(0xFFFF6000) else Color.Gray
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(label, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun SocialSignInButtons(onGoogleSignIn: () -> Unit, onFacebookSignIn: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onGoogleSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            shape = CircleShape
        ) {
            Icon(painterResource(R.drawable.google_logo), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.continue_with_google), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onFacebookSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            shape = CircleShape
        ) {
            Icon(painterResource(R.drawable.facebook_logo), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.continue_with_facebook), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLandingScreen() {
    AppTheme {
        LandingScreen({}, {}, {}, {})
    }
}
