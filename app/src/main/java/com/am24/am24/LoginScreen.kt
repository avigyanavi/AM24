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
import com.am24.am24.ui.theme.AppTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/* ─────────────────────────── Activity ─────────────────────────── */

class LoginActivity : ComponentActivity() {
    private val GOOGLE_ONLY = "__GOOGLE_ONLY__"

    private lateinit var auth: FirebaseAuth
    private val isLoading = mutableStateOf(false)
    private val loginProgress = mutableStateOf(0f)

    // 1) Google Sign-In launcher
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
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
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN

        // 1️⃣ Check for a cached GoogleSignIn account
        val lastAccount = GoogleSignIn.getLastSignedInAccount(this)
        // Prefer a “username” string (you could also pull displayName if you like)
        val cachedUser = lastAccount?.email

        val prefill = intent.getStringExtra("prefill_email") ?: ""

        setContent {
            AppTheme {
                LoginScreen(
                    cachedUser        = cachedUser,           // ◀︎ pass it in
                    initialUserOrEmail = prefill,
                    isLoading         = isLoading.value,
                    progress          = loginProgress.value,
                    onLoginClick      = ::handleLogin,
                    onForgotPassword  = ::handlePasswordReset,
                    onGoogleSignIn    = ::startGoogleSignIn      // <-- pass it in
                )
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()

    private fun handleLogin(userOrEmail: String, pwd: String) {
        isLoading.value = true
        loginProgress.value = 0f

        lifecycleScope.launch(Dispatchers.IO) {
            val trimmed = userOrEmail.trim()

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
                GOOGLE_ONLY -> return@launch withContext(Dispatchers.Main) {
                    isLoading.value = false
                    startGoogleSignIn()
                }
            }

            // ── 2) now lookup is a real e-mail, continue with password login ──
            val email = lookup!!
            loginProgress.value = 0.5f

            try {
                auth.signInWithEmailAndPassword(email, pwd).await()
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
        lifecycleScope.launch(Dispatchers.IO) {
            val email = resolveToEmail(userOrEmail) ?: run {
                withContext(Dispatchers.Main) { toast("No account found for that entry.") }
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

    private suspend fun resolveToEmail(userOrEmail: String): String? {
        val trimmed = userOrEmail.trim()
        if (trimmed.contains("@")) return trimmed

        // ── 0) check the publicUsers node first ──
        val methodSnap = FirebaseRefs.db
            .reference
            .child("publicUsers")
            .child(trimmed.lowercase(Locale.getDefault()))
            .child("signInMethod")
            .get()
            .await()
        val signInMethod = methodSnap.getValue(String::class.java)
        if (signInMethod == "google") {
            return GOOGLE_ONLY
        }

        // ── 1) normal username → uid lookup ──
        val uidSnap = FirebaseRefs.db
            .reference
            .child("usernames")
            .child(trimmed.lowercase(Locale.getDefault()))
            .get()
            .await()
        val uid = uidSnap.getValue(String::class.java) ?: return null

        // ── 2) then fetch the e-mail from /users/{uid}/email ──
        val emailSnap = FirebaseRefs.db
            .reference
            .child("users")
            .child(uid)
            .child("email")
            .get()
            .await()
        val email = emailSnap.getValue(String::class.java)

        return when {
            email == null         -> null
            email.isBlank()       -> GOOGLE_ONLY   // should never happen now, but safe
            else                  -> email
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
    onLoginClick: (String, String) -> Unit,
    onForgotPassword: (String) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    // ▷ state to flip between “cached-user” view vs. full form
    var showFullForm by remember { mutableStateOf(false) }

    // ── 1) If we have a cached Google account *and* the user hasn’t tapped “Show login screen”:
    if (cachedUser != null && !showFullForm) {
        // ◁ lookup the username key whose value == currentUid
        val username by produceState<String?>(initialValue = null, cachedUser) {
            // this block runs once when cachedUser changes
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                val snap = FirebaseRefs.db.reference
                    .child("usernames")
                    .orderByValue()
                    .equalTo(uid)
                    .get()
                    .await()
                // first matching key is your “username”
                value = snap.children.firstOrNull()?.key
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ▷ Button #1: tap to sign in with Google immediately
                Button(
                    onClick = onGoogleSignIn,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6600)),
                    shape = CircleShape
                ) {
                    // show the username if we found one, else fallback to the email
                    Text(
                        text = "Sign in with " + (username ?: cachedUser),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ▷ Button #2: flip over to the normal login form
                OutlinedButton(
                    onClick = { showFullForm = true },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                    shape = CircleShape,
                ) {
                    Text("Or Show Login Screen Instead", color = Color.White)
                }
            }
        }
        return  // don’t render the rest until they’ve tapped “Show Login Screen”
    }


    val context = LocalContext.current

    var userOrEmail by remember { mutableStateOf(TextFieldValue(initialUserOrEmail)) }
    var password    by remember { mutableStateOf(TextFieldValue("")) }


    /* dialogs */
    var showPwdDialog   by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var dialogInput     by remember { mutableStateOf("") }

    /* results */
    var resultLabel by remember { mutableStateOf("") }
    var resultValue by remember { mutableStateOf("") }

    val db    = FirebaseRefs.db
    val scope = rememberCoroutineScope()

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
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = orangeOutlinedColors()
            )

            Button(
                onClick = { onLoginClick(userOrEmail.text, password.text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6600)),
                shape = CircleShape,
                elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.login),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

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
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(id = R.string.submit)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) } },
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