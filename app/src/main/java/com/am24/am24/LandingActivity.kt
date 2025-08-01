package com.am24.am24

/* ──────────────────────────  IMPORTS  ────────────────────────── */


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.am24.am24.KupidxOrange
import com.am24.am24.ui.theme.AppTheme
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import kotlinx.coroutines.delay
import java.util.Locale

/* ──────────────────────────  ACTIVITY  ────────────────────────── */

class LandingActivity : ComponentActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager   // Facebook
    var isSigningIn by mutableStateOf(false)

    /* Preserve chosen language */
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }
    // 1️⃣  Add a tiny util (inside LandingActivity)
    private fun currentProvider(): String {
        val p = FirebaseAuth.getInstance().currentUser?.providerData?.map { it.providerId } ?: return "unknown"
        return when {
            GoogleAuthProvider.PROVIDER_ID   in p -> "google"
            FacebookAuthProvider.PROVIDER_ID in p -> "facebook"
            PhoneAuthProvider.PROVIDER_ID    in p -> "phone"
            EmailAuthProvider.PROVIDER_ID    in p -> "emailPassword"
            else                                   -> "unknown"
        }
    }

    /* Google Activity-result launcher */
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    private fun continueIntoApp() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: run { goToMain(); return }

        PushService.updateLastActive()

        FirebaseRefs.db.reference.child("users/$uid/username").get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    goToMain()                              // profile already finished
                } else {
                    FirebaseRefs.db.reference
                        .child("users/$uid/registrationStep").get()
                        .addOnSuccessListener { stepSnap ->
                            /*  ─── THIS LINE CHANGED ───  */
                            val startAt = stepSnap.getValue(Long::class.java)?.toInt() ?: 1
                            launchRegistration(startAt, currentProvider())
                        }
                        .addOnFailureListener {
                            launchRegistration(1, currentProvider())
                        }
                }
            }
            .addOnFailureListener { goToMain() }
    }


    /* tiny one-liner that starts RegistrationActivity at a given step */
    /** Fire up RegistrationActivity with the right extras */
    private fun launchRegistration(startAt: Int, provider: String) {
        startActivity(
            Intent(this, RegistrationActivity::class.java)
                .putExtra("requestedStartStep", startAt)
                .putExtra("signInProvider",    provider)   //  ← NEW
        )
        finish()
    }

    /* ─────────  onCreate  ───────── */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        googleSignInLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val acct = task.getResult(ApiException::class.java)
                        firebaseAuthWithGoogle(acct)
                    } catch (e: ApiException) {
                        Log.w("LandingActivity", "Google sign-in failed", e)
                        toast("Google sign-in failed: ${e.localizedMessage}")
                        isSigningIn = false
                    }
                } else {
                    isSigningIn = false
                }
            }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        firebaseAuth = FirebaseAuth.getInstance()          // ➊ keep this first

        /* ── NEW: skip landing if cached user exists ── */
        if (firebaseAuth.currentUser != null) {            // <- user is already signed-in
            continueIntoApp()                              //    jump straight to next screen
            return                                         //    do NOT render LandingScreen
        }

        /* Google */
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        /* Facebook */
        FacebookSdk.sdkInitialize(applicationContext)
        callbackManager = CallbackManager.Factory.create()

        /* UI */
        setContent {
            AppTheme {
                LandingScreen(
                    isLoading = isSigningIn,
                    onLoginClick = {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    },
                    onRegisterClick = {
                        launchRegistration(startAt = 1, provider = "emailPassword")
                        finish()
                    },
                    onGoogleSignIn = { signInWithGoogle() },
                    onFacebookSignIn = { signInWithFacebook() }
                )
            }
        }
    }

    /* ───────── Google flow ───────── */

    private fun signInWithGoogle() {
        isSigningIn = true
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    /** Accepts the full Google account; fetches an ID token if missing. */
    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount?) {
        if (acct == null) {
            toast("Google account was null — try again.")
            return
        }

        val idToken: String? = acct.idToken ?: run {
            // rare: ask once more
            try {
                GoogleSignIn.getSignedInAccountFromIntent(
                    googleSignInClient.signInIntent
                ).result?.idToken
            } catch (e: Exception) { null }
        }

        if (idToken.isNullOrEmpty()) {
            toast("Could not obtain an ID token from Google.")
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        signInWithFirebaseCredential(credential, acct)   // pass acct for profile
    }

    /* ───────── Facebook flow ───────── */

    private fun signInWithFacebook() {
        isSigningIn = true
        LoginManager.getInstance().logOut()   // let user pick account
        LoginManager.getInstance()
            .logInWithReadPermissions(this, listOf("email", "public_profile"))

        LoginManager.getInstance()
            .registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
                override fun onSuccess(res: LoginResult) =
                    handleFacebookAccessToken(res.accessToken)

                override fun onCancel() {
                    isSigningIn = false
                    toast("Facebook sign-in cancelled")
                }

                override fun onError(e: FacebookException) {
                    Log.e("LandingActivity", "Facebook sign-in error", e)
                    toast("Facebook sign-in failed: ${e.localizedMessage}")
                    isSigningIn = false
                }
            })
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        val credential = FacebookAuthProvider.getCredential(token.token)
        signInWithFirebaseCredential(credential)         // acct not needed here
    }

    /* Deliver Activity results to FB SDK */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    /* ───────── Shared Firebase helper ───────── */
    private fun signInWithFirebaseCredential(
        credential: AuthCredential,
        acct: GoogleSignInAccount? = null
    ) {
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                isSigningIn = false
                if (task.isSuccessful) {
                    // ① grab the freshly-signed-in user
                    val user = firebaseAuth.currentUser!!
                    val uid  = user.uid
                    user.getIdToken(true)
                        .addOnSuccessListener { res ->
                            res.token?.let { TokenStorageManager.saveToken(this@LandingActivity, it) }
                        }

                    // ② pull their e-mail out of the FirebaseUser
                    val email = user.email
                        ?: acct?.email            // fallback in the rare case FirebaseUser.email is null
                        ?: ""

                    // ③ write it to your DB under users/$uid/email
                    FirebaseRefs.db.reference
                        .child("users/$uid/email")
                        .setValue(email)
                    if (task.result?.additionalUserInfo?.isNewUser == true) {
                        // brand-new social account → skip E-mail/Phone step
                        val prov = when (credential) {
                            is GoogleAuthCredential    -> "google"
                            is FacebookAuthCredential  -> "facebook"
                            else                       -> "unknown"
                        }
                        launchRegistration(startAt = 2, provider = prov)   //  ← NEW
                    } else {
                        continueIntoApp()
                    }
                } else {
                    val ex = task.exception
                    if (ex is FirebaseAuthUserCollisionException) {
                        promptForPasswordAndLink(ex.email ?: "", credential)
                    } else {
                        toast("Auth failed: ${ex?.localizedMessage}")
                    }
                }
            }
    }

    /* ───────── Collision-handling ───────── */
    private fun promptForPasswordAndLink(email: String, pending: AuthCredential) {
        collectPasswordFromUser(email) { password ->
            val emailCred = EmailAuthProvider.getCredential(email, password)
            firebaseAuth.signInWithCredential(emailCred)
                .addOnCompleteListener(this) { signInTask ->
                    if (signInTask.isSuccessful) {
                        firebaseAuth.currentUser
                            ?.linkWithCredential(pending)
                            ?.addOnCompleteListener(this) { linkTask ->
                                if (linkTask.isSuccessful) {
                                    toast("Accounts linked! Welcome back.")
                                    continueIntoApp()
                                } else {
                                    toast("Link failed: ${linkTask.exception?.localizedMessage}")
                                }
                            }
                    } else {
                        toast("Password incorrect: ${signInTask.exception?.localizedMessage}")
                    }
                }
        }
    }

    /* ───────── Misc helpers ───────── */

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun collectPasswordFromUser(email: String, onPassword: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Link accounts")
            .setMessage("Enter password for $email to link your account:")
            .setView(input)
            .setPositiveButton("OK") { d, _ ->
                onPassword(input.text.toString()); d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.cancel() }
            .show()
    }
}

/* ──────────────────────────  COMPOSABLES  ────────────────────────── */

fun updateLocale(context: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val cfg = context.resources.configuration
    cfg.setLocale(locale)
    return context.createConfigurationContext(cfg)
}

/* ───────── LandingScreen ───────── */

@Composable
fun LandingScreen(
    isLoading: Boolean,
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
            delay(100)
            (context as? Activity)?.recreate()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(
                Brush.verticalGradient(listOf(Color.Black, Color(0xFF1A1A1A)))
            )
    ) {

        /* ─── 1. Top banner ─── */
        Column(
            modifier = Modifier                // top-centre, a bit of breathing room
                .align(Alignment.TopCenter)
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Image(                                   // your logo
                painter = painterResource(R.drawable.kupidx_logo),
                contentDescription = null,
                modifier = Modifier.size(110.dp)
            )
        }

        /* ─── 2. Main action stack ─── */
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = onRegisterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, KupidxOrange),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black,
                    contentColor = KupidxOrange
                )
            ) {
                Text(
                    stringResource(R.string.register),
                    color = KupidxOrange,
                    fontSize = 14.sp,
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
                border = BorderStroke(1.dp, KupidxOrange),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black,
                    contentColor = KupidxOrange
                )
            ) {
                Text(
                    stringResource(R.string.login),
                    color = KupidxOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(40.dp))

            SocialSignInButtons(onGoogleSignIn, onFacebookSignIn)
        }

        val isIndia = remember { CountryUtil.isProbablyInIndia(context) }
        if (!isIndia) {
            LanguageSelectionBar(
                selectedLanguage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) { lang ->
                if (lang != selectedLanguage) {
                    prefs.edit().putString("language", lang).apply()
                    shouldRestart = true
                }
            }
        }

        /* ─── 3. Full-screen loading overlay ─── */
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/* ───────── Social buttons ───────── */

@Composable
fun SocialSignInButtons(
    onGoogleSignIn: () -> Unit,
    onFacebookSignIn: () -> Unit
) = Row(
    modifier = Modifier
    .fillMaxWidth()                                   // 1️⃣
    .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp)   // 3️⃣
){
        SocialSignInButton(
            modifier   = Modifier.weight(1f),
            logo = R.drawable.ic_google_logo,
        text = stringResource(R.string.continue_with_google),
            contentColor = KupidxOrange,
        onClick = onGoogleSignIn
    )
    SocialSignInButton(
        modifier   = Modifier.weight(1f),
        logo = R.drawable.facebook_logo,
        text = stringResource(R.string.continue_with_facebook),
        contentColor = KupidxOrange,
        onClick = onFacebookSignIn
    )
}

@Composable
fun SocialSignInButton(
    modifier: Modifier = Modifier,           // ← NEW
    @DrawableRes logo: Int,
    text: String,
    backgroundColor: Color = Color.Black,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, KupidxOrange),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(2.dp)
    ) {
        Icon(
            painter = painterResource(logo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(25.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ───────── Languages row ───────── */

@Composable
fun LanguageSelectionBar(
    selectedLanguage: String,
    modifier: Modifier = Modifier,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf("English" to "en", "Español" to "es")
    val unlockedCodes = setOf("en", "es")
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
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Button(
                    onClick = { if (isUnlocked) onLanguageSelected(code) },
                    enabled = isUnlocked,
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isUnlocked && isSelected -> Color(0xFFFF6000)
                            isUnlocked -> Color.DarkGray
                            else -> Color.Gray
                        },
                        contentColor = Color.White
                    )
                ) {
                    Text(label, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

/* ───────── Preview ───────── */

@Preview(showBackground = true)
@Composable
fun PreviewLandingScreen() {
    AppTheme { LandingScreen(isLoading = false, {}, {}, {}, {}) }
}