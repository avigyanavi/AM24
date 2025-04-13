package com.am24.am24

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.am24.am24.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/* ─────────────────────────── Activity ─────────────────────────── */

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN

        setContent {
            AppTheme {
                LoginScreen(
                    onLoginClick     = ::handleLogin,
                    onForgotPassword = ::handlePasswordReset
                )
            }
        }
    }

    /* ------------ core helpers ------------ */

    private fun toast(msg: String) =
        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()

    private fun handleLogin(userOrEmail: String, pwd: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val email = resolveToEmail(userOrEmail) ?: run {
                withContext(Dispatchers.Main) { toast("Username / email not found") }
                return@launch
            }

            try {
                val res = auth.signInWithEmailAndPassword(email, pwd).await()
                val user = res.user
                if (user != null && user.isEmailVerified) {
                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@LoginActivity, KupidXAppActivity::class.java))
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) { toast("Verify your email first.") }
                    auth.signOut()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("Auth failed: ${e.message}") }
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

    /* username ⇢ email helper */
    private suspend fun resolveToEmail(userOrEmail: String): String? {
        val trimmed = userOrEmail.trim()
        if (trimmed.contains("@")) return trimmed

        val uidSnap = FirebaseDatabase.getInstance()
            .reference.child("usernames").child(trimmed).get().await()
        if (!uidSnap.exists()) return null
        val uid = uidSnap.getValue(String::class.java) ?: return null

        val emailSnap = FirebaseDatabase.getInstance()
            .reference.child("users").child(uid).child("email").get().await()
        return emailSnap.getValue(String::class.java)
    }
}

/* ─────────────────────────── UI ─────────────────────────── */

@Composable
fun LoginScreen(
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

    val db    = FirebaseDatabase.getInstance()
    val scope = rememberCoroutineScope()

    /* ---------- Dialog builders ---------- */

    if (showPwdDialog) {
        SimpleInputDialog(
            title = "Reset Password",
            hint  = "Username or Email",
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
            title = "Recover Email",
            hint  = "Username",
            input = dialogInput,
            onInputChange = { dialogInput = it },
            onDismiss = { showEmailDialog = false },
            onConfirm = {
                val username = dialogInput.trim()
                if (username.isBlank()) {
                    Toast.makeText(context, "Enter a username", Toast.LENGTH_LONG).show()
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
                            resultLabel = "Your e‑mail"
                            resultValue = emailSnap.getValue(String::class.java) ?: "(none)"
                        }
                    }
                }
                showEmailDialog = false
                dialogInput = ""
            }
        )
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

            Text("Login", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = userOrEmail,
                onValueChange = { userOrEmail = it },
                label = { Text("Username or Email", color = Color(0xFFFF6600)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = orangeOutlinedColors()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color(0xFFFF6600)) },
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
                Text("Login", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = { showPwdDialog = true }) {
                    Text("Forgot Password?", color = Color.White)
                }
                TextButton(onClick = { showEmailDialog = true }) {
                    Text("Forgot Email?", color = Color.White)
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
        confirmButton = { TextButton(onClick = onConfirm) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
