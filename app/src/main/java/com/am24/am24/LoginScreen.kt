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
                LoginScreen(
                    onLoginClick = { email, password ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val trimmedEmail = email.trim()
                                val result = auth.signInWithEmailAndPassword(trimmedEmail, password).await()

                                val currentUser = result.user
                                if (currentUser != null && currentUser.isEmailVerified) {
                                    // Navigate to ExploreActivity on successful login
                                    withContext(Dispatchers.Main) {
                                        startActivity(Intent(this@LoginActivity, KupidXAppActivity::class.java))
                                        finish()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            this@LoginActivity,
                                            "Please verify your email before logging in.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        auth.signOut()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@LoginActivity,
                                        "Authentication failed: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginClick: (String, String) -> Unit) {
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }

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
            // Title
            Text(
                text = "Login",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = Color(0xFF00bf63)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00bf63),
                    focusedBorderColor = Color(0xFF00bf63),
                    unfocusedBorderColor = Color(0xFF00bf63),
                    focusedLabelColor = Color(0xFF00bf63),
                    unfocusedLabelColor = Color(0xFF00bf63)
                )
            )

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color(0xFF00bf63)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00bf63),
                    focusedBorderColor = Color(0xFF00bf63),
                    unfocusedBorderColor = Color(0xFF00bf63),
                    focusedLabelColor = Color(0xFF00bf63),
                    unfocusedLabelColor = Color(0xFF00bf63)
                )
            )

            // Login Button
            Button(
                onClick = { onLoginClick(email.text, password.text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63)),
                shape = CircleShape,
                elevation = ButtonDefaults.elevatedButtonElevation(8.dp)
            ) {
                Text(
                    text = "Login",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
    AppTheme {
        LoginScreen(onLoginClick = { _, _ -> })
    }
}