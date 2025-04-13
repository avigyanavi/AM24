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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
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

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN

        setContent {
            AppTheme {
                LoginScreen { userOrEmail, pwd ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val email = if (userOrEmail.contains("@")) {
                                userOrEmail.trim()
                            } else {
                                // ── username flow ────────────────────────────────
                                val uidSnap = FirebaseDatabase.getInstance()
                                    .reference.child("usernames")
                                    .child(userOrEmail.trim())
                                    .get().await()

                                if (!uidSnap.exists()) {
                                    withContext(Dispatchers.Main) {
                                        toast("Username not found")
                                    }
                                    return@launch
                                }

                                val uid = uidSnap.getValue(String::class.java) ?: ""
                                val emailSnap = FirebaseDatabase.getInstance()
                                    .reference.child("users")
                                    .child(uid)
                                    .child("email")
                                    .get().await()

                                emailSnap.getValue(String::class.java)?.trim().orEmpty()
                            }

                            if (email.isBlank()) {
                                withContext(Dispatchers.Main) {
                                    toast("Could not resolve email for that username")
                                }
                                return@launch
                            }

                            val result = auth.signInWithEmailAndPassword(email, pwd).await()
                            val currentUser = result.user

                            if (currentUser != null && currentUser.isEmailVerified) {
                                withContext(Dispatchers.Main) {
                                    startActivity(Intent(this@LoginActivity, KupidXAppActivity::class.java))
                                    finish()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    toast("Please verify your email before logging in.")
                                    auth.signOut()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { toast("Auth failed: ${e.message}") }
                        }
                    }
                }
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
}

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit
) {
    var userOrEmail by remember { mutableStateOf(TextFieldValue("")) }
    var password    by remember { mutableStateOf(TextFieldValue("")) }

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
                text = "Login",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Username *or* Email
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

            // Password
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

            // Login button
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = { /* TODO: Forgot‑username flow */ }) {
                    Text("Forgot Username?", color = Color.White)
                }
                TextButton(onClick = { /* TODO: Firebase password reset */ }) {
                    Text("Forgot Password?", color = Color.White)
                }
            }
        }
    }
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
