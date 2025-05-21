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
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/* ─────────────────────────── Activity ─────────────────────────── */

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private val isLoading = mutableStateOf(false)
    private val loginProgress = mutableStateOf(0f)

    // Override attachBaseContext to update the locale
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        super.attachBaseContext(updateLocale(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN

        setContent {
            AppTheme {
                LoginScreen(
                    isLoading     = isLoading.value,
                    progress      = loginProgress.value,
                    onLoginClick  = ::handleLogin,
                    onForgotPassword = ::handlePasswordReset
                )
            }
        }
    }

    /* Core Helper Functions */

    private fun toast(msg: String) =
        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()

    private fun handleLogin(userOrEmail: String, pwd: String) {
        isLoading.value = true
        loginProgress.value = 0f
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val email = if (userOrEmail.contains("@")) {
                    userOrEmail.trim()
                } else {
                    // 1) Sign in anonymously so we can look up “usernames”…
                    val anonAuth = FirebaseAuth.getInstance()
                    val anonResult = anonAuth.signInAnonymously().await()
                    val anonUser = anonResult.user
                    loginProgress.value = 0.33f

                    // 2) Resolve the real email address
                    val resolved = resolveToEmail(userOrEmail)
                    if (resolved == null) {
                        // clean up the anon user before bailing out
                        anonUser?.delete()?.await()
                        anonAuth.signOut()
                        return@launch withContext(Dispatchers.Main) {
                            toast("Username not found")
                        }
                    }
                    loginProgress.value = 0.66f

                    // 3) Immediately delete the anonymous account once we have the email
                    anonUser?.delete()?.await()
                    anonAuth.signOut()

                    resolved
                }

                // 4) Now sign in for real
                val res = auth
                    .signInWithEmailAndPassword(email, pwd)
                    .await()
                loginProgress.value = 1f

                withContext(Dispatchers.Main) {
                    val user = res.user
                    if (user != null && !user.isEmailVerified) {
                        toast("Welcome! Please verify your email later to unlock all features.")
                    }
                    isLoading.value = false
                    startActivity(Intent(this@LoginActivity, KupidXAppActivity::class.java))
                    finish()
                }
            }
                catch (e: Exception) {
                         withContext(Dispatchers.Main) {
                             isLoading.value = false
                             val fullMsg = "Auth failed: ${e.message}"
                                     // If it begins with the INVALID_LOGIN internal error, show the custom toast
                                     if (fullMsg.startsWith(
                                             "Auth failed: An internal error has occurred. [ INVALID_LOGIN"
                                         )
                                     ) {
                                         toast("Invalid manual log in. Try signing in with Google or Facebook.")
                                     } else {
                                                 // <-- all other errors still show the raw message
                                                 toast("Auth failed: ${e.message}")
                                             }
                                     }
                             }
                     }
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

    // Helper: convert a username to an email if needed.
    private suspend fun resolveToEmail(userOrEmail: String): String? {
        val trimmed = userOrEmail.trim()
        if (trimmed.contains("@")) return trimmed

        val uidSnap = FirebaseRefs.db
            .reference.child("usernames").child(trimmed).get().await()
        if (!uidSnap.exists()) return null
        val uid = uidSnap.getValue(String::class.java) ?: return null

        val emailSnap = FirebaseRefs.db
            .reference.child("users").child(uid).child("email").get().await()
        return emailSnap.getValue(String::class.java)
    }
}


/* ─────────────────────────── UI ─────────────────────────── */

@Composable
fun LoginScreen(
    isLoading: Boolean,
    progress: Float,
    onLoginClick: (String, String) -> Unit,
    onForgotPassword: (String) -> Unit
) {
    val context = LocalContext.current

    /* user inputs */
    var userOrEmail by remember { mutableStateOf(TextFieldValue("")) }
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