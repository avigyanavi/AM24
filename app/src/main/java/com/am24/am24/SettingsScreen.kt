@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val currentUserId = currentUser.uid
    val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    // Premium + Boost states
    var isPremiumUser by remember { mutableStateOf(false) }
    var premiumExpiryDate by remember { mutableStateOf("") }
    var isBoosted by remember { mutableStateOf(false) }

    // Load premium + boosted status
    LaunchedEffect(Unit) {
        userRef.child("premiumStatus").get().addOnSuccessListener { snap ->
            isPremiumUser = snap.child("isPremium").getValue(Boolean::class.java) ?: false
            premiumExpiryDate = snap.child("expiryDate").getValue(String::class.java) ?: "N/A"
        }
        userRef.child("isBoosted").get().addOnSuccessListener { snap ->
            isBoosted = snap.getValue(Boolean::class.java) ?: false
        }
    }

    // Scaffold with two main items: (1) Premium Status, (2) Account Settings
    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // (1) Premium Status
            item {
                Text(
                    text = "Premium Status",
                    color = Color(0xFF00bf63),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black, shape = RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    // Premium / Free
                    Text(
                        text = if (isPremiumUser) "Premium Member" else "Free User",
                        color = if (isPremiumUser) Color(0xFFFFD700) else Color.Gray,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Expiry or prompt
                    Text(
                        text = if (isPremiumUser) "Expires on: $premiumExpiryDate"
                        else "Upgrade to unlock premium features",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Smaller button for subscription
                    Button(
                        onClick = { navController.navigate("subscription") },
                        modifier = Modifier.width(160.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPremiumUser) Color.Gray else Color(0xFF00bf63)
                        )
                    ) {
                        Text(
                            text = if (isPremiumUser) "Manage Subs." else "Upgrade",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    // "Buy Boost" or show "Boost Active" if isBoosted
                    Button(
                        onClick = {
                            // Navigate to your "buyBoosts" screen or handle logic
                            if (!isBoosted) {
                                navController.navigate("buyBoosts")
                            }
                        },
                        modifier = Modifier.width(160.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBoosted) Color.Gray else Color(0xFFFF6F00)
                        )
                    ) {
                        Text(
                            text = if (isBoosted) "Boost Active" else "Buy Boost",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // (2) Account Settings
            item {
                AccountSettingsSection(navController = navController)
            }
        }
    }
}

/**
 * This section includes:
 *  - Password editing
 *  - Username editing
 *  - Private account toggle
 *  - "Save" button
 *  - "Delete Account" and "Logout" within the same card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsSection(navController: NavController) {
    val context = LocalContext.current

    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val currentUserId = currentUser.uid
    val database = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    // Local state for account fields
    var email by remember { mutableStateOf("") }        // SHOWN ONLY
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var oldUsername by remember { mutableStateOf<String?>(null) }

    var isPrivate by remember { mutableStateOf(false) }

    // Toggles
    var isEditingPassword by remember { mutableStateOf(false) }
    var isEditingUsername by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Username uniqueness check
    var usernameStatus by remember { mutableStateOf("idle") }

    // We'll do final updates in a coroutine
    val scope = rememberCoroutineScope()

    // On load, read data
    LaunchedEffect(currentUserId) {
        database.get().addOnSuccessListener { snapshot ->
            val loadedEmail = snapshot.child("email").getValue(String::class.java)
            val loadedUsername = snapshot.child("username").getValue(String::class.java)
            val loadedPrivate = snapshot.child("isPrivate").getValue(Boolean::class.java) ?: false

            // fallback: if 'email' is null, use currentUser.email
            email = loadedEmail ?: currentUser.email ?: ""
            username = loadedUsername ?: ""
            oldUsername = loadedUsername
            isPrivate = loadedPrivate
        }
    }

    // Real-time uniqueness check for username
    LaunchedEffect(username) {
        if (isEditingUsername && username.isNotBlank()) {
            usernameStatus = "checking"
            delay(500) // Debounce
            val db = FirebaseDatabase.getInstance().getReference("usernames")
            db.child(username).get()
                .addOnSuccessListener { snap ->
                    if (snap.exists() && snap.value != currentUserId) {
                        usernameStatus = "not available"
                    } else {
                        usernameStatus = "available"
                    }
                }
                .addOnFailureListener {
                    usernameStatus = "idle"
                }
        } else {
            usernameStatus = "idle"
        }
    }

    // For Delete + Logout (within account settings)
    fun logoutUser() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(context, LandingActivity::class.java)
        context.startActivity(intent)
        (context as? ComponentActivity)?.finish()
    }

    fun deleteAccount(context: Context) {
        val usr = FirebaseAuth.getInstance().currentUser
        if (usr == null) {
            Toast.makeText(context, "No user is logged in.", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = usr.uid
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        userRef.removeValue().addOnCompleteListener { rmTask ->
            if (rmTask.isSuccessful) {
                usr.delete().addOnCompleteListener { delTask ->
                    if (delTask.isSuccessful) {
                        Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                        navController.navigate("login") {
                            popUpTo("settings") { inclusive = true }
                        }
                    } else {
                        Toast.makeText(context, "Failed to delete account: ${delTask.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Failed to remove account data: ${rmTask.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // UI card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Account Settings",
            color = Color(0xFF00bf63),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Show email only
        Text("Email: $email", color = Color.White, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // Password
        if (isEditingPassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("New Password", color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Password",
                            tint = Color(0xFF00bf63)
                        )
                    }
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    cursorColor = Color(0xFFFF6F00),
                    focusedTextColor = Color.White
                )
            )
            TextButton(onClick = { isEditingPassword = false }) {
                Text("Done", color = Color(0xFF00bf63))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Password: ********", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { isEditingPassword = true }) {
                    Text("Edit", color = Color(0xFF00bf63))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Username
        if (isEditingUsername) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username", color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    cursorColor = Color(0xFFFF6F00),
                    focusedTextColor = Color.White
                )
            )
            when (usernameStatus) {
                "checking" -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF00bf63),
                        strokeWidth = 2.dp
                    )
                }
                "available" -> {
                    Text("✓", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                "not available" -> {
                    Text("✗", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                else -> {}
            }
            TextButton(onClick = { isEditingUsername = false }) {
                Text("Done", color = Color(0xFF00bf63))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Username: $username", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { isEditingUsername = true }) {
                    Text("Edit", color = Color(0xFF00bf63))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Private Account
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Private Account", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = isPrivate,
                onCheckedChange = { isPrivate = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00bf63),
                    uncheckedThumbColor = Color.Gray
                )
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                scope.launch {
                    try {
                        updateAccountSettingsNoEmail(
                            newPassword = if (password == "********") "" else password,
                            newUsername = username,
                            oldUsername = oldUsername,
                            isPrivate = isPrivate
                        )
                        Toast.makeText(context, "Settings updated.", Toast.LENGTH_SHORT).show()
                        oldUsername = username // track for next time
                    } catch (ex: Exception) {
                        Toast.makeText(context, ex.message ?: "Failed to update settings.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.width(160.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
        ) {
            Text("Save", color = Color.White, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Row for "Delete Account" & "Logout" side-by-side or stacked
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            // Delete Account
            Button(
                onClick = { deleteAccount(context) },
                modifier = Modifier.width(160.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Delete Account", color = Color.White, fontSize = 14.sp)
            }
            // Logout
            Button(
                onClick = { logoutUser() },
                modifier = Modifier.width(160.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Logout", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Simplified version that does NOT edit email. Only password, username, private-flag.
 */
suspend fun updateAccountSettingsNoEmail(
    newPassword: String,
    newUsername: String,
    oldUsername: String?,
    isPrivate: Boolean
) {
    val user = FirebaseAuth.getInstance().currentUser ?: throw Exception("No user is signed in.")
    val userId = user.uid
    val db = FirebaseDatabase.getInstance().getReference("users").child(userId)

    // 1) If user typed a new password
    if (newPassword.isNotBlank()) {
        user.updatePassword(newPassword).await()
    }
    // 2) Update the username in /usernames
    checkAndUpdateUsernameAwait(newUsername, oldUsername, userId)

    // 3) Update isPrivate
    db.child("isPrivate").setValue(isPrivate).await()
}

/** Same logic for username updating, but in suspend form. */
suspend fun checkAndUpdateUsernameAwait(
    newUsername: String,
    oldUsername: String?,
    userId: String
) {
    val db = FirebaseDatabase.getInstance().reference
    val usernamesRef = db.child("usernames")

    // Remove old
    if (!oldUsername.isNullOrBlank() && oldUsername != newUsername) {
        val oldSnap = usernamesRef.child(oldUsername).get().await()
        if (oldSnap.exists() && oldSnap.value == userId) {
            usernamesRef.child(oldUsername).removeValue().await()
        }
    }

    // Check if newUsername is taken
    val newSnap = usernamesRef.child(newUsername).get().await()
    if (newSnap.exists() && newSnap.value != userId) {
        throw Exception("Username already taken. Please choose another.")
    } else {
        // Write new => userId
        usernamesRef.child(newUsername).setValue(userId).await()
        // Also store in /users
        db.child("users").child(userId).child("username")
            .setValue(newUsername).await()
    }
}
