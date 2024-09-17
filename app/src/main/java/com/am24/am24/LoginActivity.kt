package com.am24.am24

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.am24.am24.ui.theme.AppTheme

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            AppTheme {
                LoginScreen(
                    onLoginClick = { email, password ->
                        val trimmedEmail = email.trim()
                        auth.signInWithEmailAndPassword(trimmedEmail, password)
                            .addOnCompleteListener(this) { task ->
                                if (task.isSuccessful) {
                                    val currentUser = auth.currentUser
                                    // Check if the email is verified
                                    if (currentUser != null && currentUser.isEmailVerified) {
                                        // Navigate to ExploreActivity after successful login
                                        startActivity(Intent(this, ExploreActivity::class.java))
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                                        auth.signOut()  // Sign out the user if not verified
                                    }
                                } else {
                                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit
) {
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Login", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        EmailTextField(email = email.text, onValueChange = { email = TextFieldValue(it) })
        Spacer(modifier = Modifier.height(16.dp))

        PasswordTextField(password = password.text, onValueChange = { password = TextFieldValue(it) })
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onLoginClick(email.text, password.text) }) {
            Text("Login")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
    AppTheme {
        LoginScreen(
            onLoginClick = { email, password -> /* Do nothing, it's just a preview */ }
        )
    }
}
