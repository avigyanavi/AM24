@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Main Settings Screen
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val currentUserId = currentUser.uid
    val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    // -----------------------
    // Existing Premium & Account States
    // -----------------------
    var isPremiumUser by remember { mutableStateOf(false) }
    var premiumExpiryDate by remember { mutableStateOf("") }
    var isBoosted by remember { mutableStateOf(false) }

    // -----------------------
    // New Global Preferences
    // -----------------------
    // Option (1): Show "name" or "username" on profile screen
    var displayPreference by remember { mutableStateOf("name") } // "name" or "username"
    // Option (6): Preferred language; default English
    var preferredLanguage by remember { mutableStateOf("English") }
    // Option (7): Allow location for matches
    var allowLocationForMatches by remember { mutableStateOf(true) }
    // Option (8): Matrimony mode toggle
    var isMatrimonyMode by remember { mutableStateOf(false) }

    // New: Blocked users list (assumed to be a list of user IDs or usernames)
    var blockedUsers by remember { mutableStateOf(listOf<String>()) }

    // Load all global settings from Firebase when the screen launches
    LaunchedEffect(Unit) {
        // Load payment details
        userRef.child("premiumStatus").get().addOnSuccessListener { snap ->
            isPremiumUser = snap.child("isPremium").getValue(Boolean::class.java) ?: false
            premiumExpiryDate = snap.child("expiryDate").getValue(String::class.java) ?: "N/A"
        }
        userRef.child("isBoosted").get().addOnSuccessListener { snap ->
            isBoosted = snap.getValue(Boolean::class.java) ?: false
        }
        // Load global preferences from user record
        userRef.get().addOnSuccessListener { snapshot ->
            displayPreference = snapshot.child("displayPreference").getValue(String::class.java) ?: "name"
            preferredLanguage = snapshot.child("preferredLanguage").getValue(String::class.java) ?: "English"
            allowLocationForMatches = snapshot.child("allowLocationForMatches").getValue(Boolean::class.java) ?: true
            isMatrimonyMode = snapshot.child("isMatrimonyMode").getValue(Boolean::class.java) ?: false
            // Blocked users list saved as a list in Firebase (or as a Map whose keys are user IDs)
            blockedUsers = snapshot.child("blockedUsers").children.mapNotNull { it.value as? String }
        }
    }

    // Scaffold wrapping all sections
    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Premium Status Section (existing)
            item { PremiumStatusSection(navController, isPremiumUser, premiumExpiryDate, isBoosted) }
            // Account Settings Section (existing)
            item { AccountSettingsSection(navController) }
            // Global Preferences Section (new)
            item {
                GlobalPreferencesSection(
                    displayPreference = displayPreference,
                    onDisplayPreferenceChange = { displayPreference = it },
                    preferredLanguage = preferredLanguage,
                    onPreferredLanguageChange = { preferredLanguage = it },
                    allowLocationForMatches = allowLocationForMatches,
                    onAllowLocationForMatchesChange = { allowLocationForMatches = it },
                    isMatrimonyMode = isMatrimonyMode,
                    onMatrimonyModeChange = { isMatrimonyMode = it }
                )
            }
            // Purchase Options Section (new)
            item { PurchaseOptionsSection(navController) }
            // Blocked Users Section (new)
            item { BlockedUsersSection(userRef, blockedUsers) }
            // Save Button for Global Preferences
            item {
                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                updateGlobalSettings(
                                    userRef = userRef,
                                    displayPreference = displayPreference,
                                    preferredLanguage = preferredLanguage,
                                    allowLocationForMatches = allowLocationForMatches,
                                    isMatrimonyMode = isMatrimonyMode
                                )
                                Toast.makeText(context, "Global settings updated.", Toast.LENGTH_SHORT).show()
                            } catch (ex: Exception) {
                                Toast.makeText(context, ex.message ?: "Failed to update.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Text("Save Global Settings", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

/** PREMIUM STATUS SECTION **/
@Composable
fun PremiumStatusSection(
    navController: NavController,
    isPremiumUser: Boolean,
    premiumExpiryDate: String,
    isBoosted: Boolean
) {
    Column {
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
            // Show plan type – you can extend to include "Super plan" if needed
            Text(
                text = if (isPremiumUser) "Premium Member" else "Free User",
                color = if (isPremiumUser) Color(0xFFFFD700) else Color.Gray,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isPremiumUser) "Expires on: $premiumExpiryDate" else "Upgrade to unlock premium features",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Subscription management/upgrading
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
            // Buy Boost (existing behavior)
            Button(
                onClick = {
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
}

/** ACCOUNT SETTINGS SECTION (existing) **/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsSection(navController: NavController) {
    // Use your existing AccountSettingsSection code here.
    // For brevity, the code below is the same as your provided code.
    // (See your original SettingsPage code for account editing, password changes, private toggle, etc.)
    // ──────────────
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val currentUserId = currentUser.uid
    val database = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var oldUsername by remember { mutableStateOf<String?>(null) }
    var isPrivate by remember { mutableStateOf(false) }

    var isEditingPassword by remember { mutableStateOf(false) }
    var isEditingUsername by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    var usernameStatus by remember { mutableStateOf("idle") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUserId) {
        database.get().addOnSuccessListener { snapshot ->
            val loadedEmail = snapshot.child("email").getValue(String::class.java)
            val loadedUsername = snapshot.child("username").getValue(String::class.java)
            val loadedPrivate = snapshot.child("isPrivate").getValue(Boolean::class.java) ?: false
            email = loadedEmail ?: currentUser.email ?: ""
            username = loadedUsername ?: ""
            oldUsername = loadedUsername
            isPrivate = loadedPrivate
        }
    }

    LaunchedEffect(username) {
        if (isEditingUsername && username.isNotBlank()) {
            usernameStatus = "checking"
            delay(500)
            val dbUsernames = FirebaseDatabase.getInstance().getReference("usernames")
            dbUsernames.child(username).get()
                .addOnSuccessListener { snap ->
                    if (snap.exists() && snap.value != currentUserId) {
                        usernameStatus = "not available"
                    } else {
                        usernameStatus = "available"
                    }
                }
                .addOnFailureListener { usernameStatus = "idle" }
        } else {
            usernameStatus = "idle"
        }
    }

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
        Text("Email: $email", color = Color.White, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
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
                        oldUsername = username
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
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Button(
                onClick = { deleteAccount(context) },
                modifier = Modifier.width(160.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Delete Account", color = Color.White, fontSize = 14.sp)
            }
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

/** GLOBAL PREFERENCES SECTION **/
@Composable
fun GlobalPreferencesSection(
    displayPreference: String,
    onDisplayPreferenceChange: (String) -> Unit,
    preferredLanguage: String,
    onPreferredLanguageChange: (String) -> Unit,
    allowLocationForMatches: Boolean,
    onAllowLocationForMatchesChange: (Boolean) -> Unit,
    isMatrimonyMode: Boolean,
    onMatrimonyModeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Global Preferences",
            color = Color(0xFF00bf63),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        // (1) Display preference: name vs username
        Text("Display on Profile:", color = Color.White, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = displayPreference == "name",
                onClick = { onDisplayPreferenceChange("name") },
                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF6F00))
            )
            Text("Name", color = Color.White)
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = displayPreference == "username",
                onClick = { onDisplayPreferenceChange("username") },
                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF6F00))
            )
            Text("Username", color = Color.White)
        }
        Spacer(modifier = Modifier.height(16.dp))
        // (6) Preferred Language dropdown
        Text("Preferred Language:", color = Color.White, fontSize = 16.sp)
        var languageExpanded by remember { mutableStateOf(false) }
        Box {
            Button(
                onClick = { languageExpanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Text(preferredLanguage, color = Color.White)
            }
            DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                listOf("English", "Hindi", "Bengali", "Other").forEach { lang ->
                    DropdownMenuItem(text = { Text(lang) }, onClick = {
                        onPreferredLanguageChange(lang)
                        languageExpanded = false
                    })
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // (7) Allow Location for Matches toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Allow Location for Matches", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = allowLocationForMatches,
                onCheckedChange = onAllowLocationForMatchesChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFF6F00),
                    uncheckedThumbColor = Color.Gray
                )
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // (8) Matrimony Mode toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Matrimony Mode", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = isMatrimonyMode,
                onCheckedChange = onMatrimonyModeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFF6F00),
                    uncheckedThumbColor = Color.Gray
                )
            )
        }
    }
}

/** PURCHASE OPTIONS SECTION **/
@Composable
fun PurchaseOptionsSection(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Purchase Options",
            color = Color(0xFF00bf63),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Add buttons for various in-app purchases:
        Button(
            onClick = { navController.navigate("buySwipes") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text("Buy Swipes", color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.navigate("buyForceMatches") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text("Buy Force Matches", color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.navigate("buySuperSwipes") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text("Buy Super Swipes", color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // The "Buy Boost" option is already in PremiumStatusSection.
    }
}

/** BLOCKED USERS SECTION **/
@Composable
fun BlockedUsersSection(userRef: com.google.firebase.database.DatabaseReference, blockedUsers: List<String>) {
    var showBlockedOverlay by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Button to view blocked users
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, shape = RoundedCornerShape(8.dp))
            .clickable { showBlockedOverlay = true }
            .padding(16.dp)
    ) {
        Text(
            text = "Blocked Users",
            color = Color(0xFF00bf63),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (blockedUsers.isEmpty()) {
            Text("No users are blocked.", color = Color.White, fontSize = 16.sp)
        } else {
            // Show up to 3 blocked users as preview
            blockedUsers.take(3).forEach { user ->
                Text(user, color = Color.White, fontSize = 16.sp)
            }
        }
    }
    // Overlay: an AlertDialog listing blocked users with an unblock option
    if (showBlockedOverlay) {
        AlertDialog(
            onDismissRequest = { showBlockedOverlay = false },
            title = { Text("Blocked Users", color = Color(0xFF00bf63)) },
            text = {
                if (blockedUsers.isEmpty()) {
                    Text("No users are currently blocked.", color = Color.White)
                } else {
                    Column {
                        blockedUsers.forEach { user ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(user, color = Color.White, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    scope.launch {
                                        // Remove user from blocked list in Firebase
                                        userRef.child("blockedUsers").child(user).removeValue().await()
                                        Toast.makeText(context, "Unblocked $user", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text("Unblock", color = Color(0xFFFF6F00))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedOverlay = false }) {
                    Text("Done", color = Color(0xFF00bf63))
                }
            }
        )
    }
}

/** UPDATE GLOBAL SETTINGS FUNCTION **/
suspend fun updateGlobalSettings(
    userRef: com.google.firebase.database.DatabaseReference,
    displayPreference: String,
    preferredLanguage: String,
    allowLocationForMatches: Boolean,
    isMatrimonyMode: Boolean
) {
    val updates = mapOf(
        "displayPreference" to displayPreference,
        "preferredLanguage" to preferredLanguage,
        "allowLocationForMatches" to allowLocationForMatches,
        "isMatrimonyMode" to isMatrimonyMode
    )
    userRef.updateChildren(updates).await()
}

/** UPDATE ACCOUNT SETTINGS (Same as your current implementation) **/
suspend fun updateAccountSettingsNoEmail(
    newPassword: String,
    newUsername: String,
    oldUsername: String?,
    isPrivate: Boolean
) {
    val user = FirebaseAuth.getInstance().currentUser ?: throw Exception("No user is signed in.")
    val userId = user.uid
    val db = FirebaseDatabase.getInstance().getReference("users").child(userId)

    if (newPassword.isNotBlank()) {
        user.updatePassword(newPassword).await()
    }
    checkAndUpdateUsernameAwait(newUsername, oldUsername, userId)
    db.child("isPrivate").setValue(isPrivate).await()
}

suspend fun checkAndUpdateUsernameAwait(
    newUsername: String,
    oldUsername: String?,
    userId: String
) {
    val db = FirebaseDatabase.getInstance().reference
    val usernamesRef = db.child("usernames")
    if (!oldUsername.isNullOrBlank() && oldUsername != newUsername) {
        val oldSnap = usernamesRef.child(oldUsername).get().await()
        if (oldSnap.exists() && oldSnap.value == userId) {
            usernamesRef.child(oldUsername).removeValue().await()
        }
    }
    val newSnap = usernamesRef.child(newUsername).get().await()
    if (newSnap.exists() && newSnap.value != userId) {
        throw Exception("Username already taken. Please choose another.")
    } else {
        usernamesRef.child(newUsername).setValue(userId).await()
        db.child("users").child(userId).child("username").setValue(newUsername).await()
    }
}
