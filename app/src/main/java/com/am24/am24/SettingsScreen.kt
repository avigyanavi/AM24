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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.core.content.edit
import com.google.firebase.database.DatabaseReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val uid         = currentUser.uid
    val userRef     = FirebaseRefs.db.getReference("users").child(uid)
    val blocksRef   = FirebaseRefs.db.getReference("blocks").child(uid)
    val scope       = rememberCoroutineScope()

    // ─── STATE ───
    var isPremium            by remember { mutableStateOf(false) }
    var expiry               by remember { mutableStateOf("N/A") }
    var availableBoosts      by remember { mutableStateOf(0) }
    var availableSwipes      by remember { mutableStateOf(0) }
    var availableCompliments by remember { mutableStateOf(0) }
    var lastBoostTs          by remember { mutableStateOf(0L) }
    var isBoosted            by remember { mutableStateOf(false) }

    var isPrivate         by remember { mutableStateOf(false) }
    var preferredLang     by remember { mutableStateOf("en") }
    var allowLocation     by remember { mutableStateOf(true) }
    var isMatrimonyMode   by remember { mutableStateOf(false) }
    var blockedUsers      by remember { mutableStateOf(listOf<String>()) }

    // ─── LOAD ONCE ───
    LaunchedEffect(Unit) {
        val snap = userRef.get().await()
        // premium
        isPremium = snap.child("premiumStatus/isPremium")
            .getValue(Boolean::class.java) ?: false
        expiry = snap.child("premiumStatus/expiryDate")
            .getValue(String::class.java) ?: "N/A"

        // boosts/swipes/compliments
        availableBoosts = snap.child("availableBoosts")
            .getValue(Int::class.java) ?: 0
        lastBoostTs = snap.child("lastBoostTimestamp")
            .getValue(Long::class.java) ?: 0L
        // ← here’s the fix:
        availableSwipes = snap
            .child("swipesInfo")                 // your wrapper node
            .child("remainingSwipes")            // the exact key
            .getValue(Int::class.java) ?: 0
        availableCompliments = snap.child("availableCompliments")
            .getValue(Int::class.java) ?: 0
        val now = System.currentTimeMillis()
        isBoosted = availableBoosts > 0 && (now - lastBoostTs) < 6 * 60 * 60 * 1000L

        // global prefs
        isPrivate = snap.child("isPrivate")
            .getValue(Boolean::class.java) ?: false
        preferredLang = snap.child("preferredLanguage")
            .getValue(String::class.java) ?: "en"
        allowLocation = snap.child("allowLocationForMatches")
            .getValue(Boolean::class.java) ?: true
        isMatrimonyMode = snap.child("isMatrimonyMode")
            .getValue(Boolean::class.java) ?: false

        // blocked users
        blocksRef.get().addOnSuccessListener { bsnap ->
            blockedUsers = bsnap.children.mapNotNull { it.key }
        }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            item {
                Column(Modifier.background(Color.Black, RoundedCornerShape(8.dp))) {
                    PremiumStatusSection(
                        navController        = navController,
                        isPremiumUser        = isPremium,
                        premiumExpiryDate    = expiry,
                        isBoosted            = isBoosted,
                        boosts               = availableBoosts,
                        swipes               = availableSwipes,
                        compliments          = availableCompliments
                    )
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                    PurchaseOptionsSection(navController)
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                    AccountSettingsSection(
                        navController    = navController,
                        isPrivate        = isPrivate,
                        onPrivateChange  = { new ->
                            isPrivate = new
                            scope.launch { userRef.child("isPrivate").setValue(new) }
                        }
                    )
                }
            }

            item {
                GlobalPreferencesSection(
                    userRef                   = userRef,
                    isPrivate                 = isPrivate,
                    onPrivateChange           = { new ->
                        isPrivate = new
                        scope.launch { userRef.child("isPrivate").setValue(new) }
                    },
                    preferredLanguage         = preferredLang,
                    onPreferredLanguageChange = { code ->
                        preferredLang = code
                        scope.launch { userRef.child("preferredLanguage").setValue(code) }
                    },
                    allowLocationForMatches   = allowLocation,
                    onAllowLocationChange     = { allow ->
                        allowLocation = allow
                        scope.launch { userRef.child("allowLocationForMatches").setValue(allow) }
                    },
                    isMatrimonyMode           = isMatrimonyMode,
                    onMatrimonyModeChange     = { m ->
                        isMatrimonyMode = m
                        scope.launch { userRef.child("isMatrimonyMode").setValue(m) }
                    }
                )
            }

            item {
                BlockedUsersSection(blocksRef = blocksRef, blockedIds = blockedUsers)
            }
        }
    }
}

@Composable
fun PremiumStatusSection(
    navController: NavController,
    isPremiumUser: Boolean,
    premiumExpiryDate: String,
    isBoosted: Boolean,
    boosts: Int,
    swipes: Int,
    compliments: Int
) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "   Premium Status",
            color = Color(0xFFFF6F00),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = if (isPremiumUser) "Premium Member" else "Free User",
                color = if (isPremiumUser) Color(0xFFFFD700) else Color.Gray,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isPremiumUser)
                    "Expires on: $premiumExpiryDate"
                else
                    "Upgrade to unlock premium features",
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // display remaining counts
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Boosts remaining: $boosts", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Swipes remaining: $swipes", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Compliments remaining: $compliments", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // still allow subscription/manage
            Button(
                onClick = { navController.navigate("subscription") },
                modifier = Modifier.width(160.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPremiumUser) Color.Gray else Color(0xFFFF6F00)
                )
            ) {
                Text(
                    text = if (isPremiumUser) "Manage Subs." else "Upgrade",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsSection(
    navController: NavController,
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit
) {
    val context     = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser ?: return
    val userId      = currentUser.uid
    val database    = FirebaseRefs.db.getReference("users").child(userId)
    val scope       = rememberCoroutineScope()

    var email          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }
    var username       by remember { mutableStateOf("") }
    var oldUsername    by remember { mutableStateOf<String?>(null) }
    var isEditingPass  by remember { mutableStateOf(false) }
    var isEditingUname by remember { mutableStateOf(false) }
    var passVisible    by remember { mutableStateOf(false) }
    var unameStatus    by remember { mutableStateOf("idle") }

    // Load existing user data once
    LaunchedEffect(userId) {
        val snap = database.get().await()
        email       = snap.child("email").getValue(String::class.java)
            ?: currentUser.email.orEmpty()
        username    = snap.child("username").getValue(String::class.java).orEmpty()
        oldUsername = username
    }

    // Check username availability whenever it's being edited
    LaunchedEffect(username, isEditingUname) {
        if (isEditingUname && username.isNotBlank()) {
            unameStatus = "checking"
            delay(500)
            val dbUsernames = FirebaseRefs.db.getReference("usernames")
            val nameSnap = dbUsernames.child(username).get().await()
            unameStatus = if (nameSnap.exists() && nameSnap.value != userId) {
                "not available"
            } else {
                "available"
            }
        } else {
            unameStatus = "idle"
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
            color = Color(0xFFFF6F00),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        // Email display
        Text("Email: $email", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        // Password editing
        if (isEditingPass) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("New Password", color = Color(0xFFFF6F00)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { passVisible = !passVisible }
                    ) {
                        Icon(
                            imageVector = if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility",
                            tint = Color(0xFFFF6F00)
                        )
                    }
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFFFF6F00),
                    cursorColor = Color(0xFFFF6F00),
                    focusedTextColor = Color.White
                )
            )
            TextButton(onClick = { isEditingPass = false }) {
                Text("Done", color = Color(0xFFFF6F00))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Password: ********",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { isEditingPass = true }) {
                    Text("Edit", color = Color(0xFFFF6F00))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Username editing
        if (isEditingUname) {
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
            when (unameStatus) {
                "checking" -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFFF6F00),
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
            TextButton(onClick = { isEditingUname = false }) {
                Text("Done", color = Color(0xFFFF6F00))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Username: $username",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { isEditingUname = true }) {
                    Text("Edit", color = Color(0xFFFF6F00))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Save & Logout buttons
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        updateAccountSettingsNoEmail(
                            newPassword = if (password == "********") "" else password,
                            newUsername = username,
                            oldUsername = oldUsername
                        )
                        Toast.makeText(context, "Settings updated.", Toast.LENGTH_SHORT).show()
                        oldUsername = username
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Text("Save", color = Color.White)
            }
            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    context.startActivity(Intent(context, LandingActivity::class.java))
                    (context as? ComponentActivity)?.finish()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Logout", color = Color.White)
            }
        }
    }
}

@Composable
fun GlobalPreferencesSection(
    userRef: DatabaseReference,
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit,
    preferredLanguage: String,
    onPreferredLanguageChange: (String) -> Unit,
    allowLocationForMatches: Boolean,
    onAllowLocationChange: (Boolean) -> Unit,
    isMatrimonyMode: Boolean,
    onMatrimonyModeChange: (Boolean) -> Unit
) {
    val context  = LocalContext.current
    val activity = (context as? ComponentActivity)
    val langs    = listOf("English" to "en", "हिन्दी" to "hi", "বাংলা" to "bn")

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(
            "Global Preferences",
            color = Color(0xFFFF6F00),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        // PRIVATE ACCOUNT
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Private Account",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isPrivate,
                onCheckedChange = onPrivateChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color(0xFFFF6F00),
                    uncheckedThumbColor = Color.Gray
                )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Be undiscoverable in card stack by everyone except those you swipe right on",
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp)
        )

        Spacer(Modifier.height(16.dp))

        // PREFERRED LANGUAGE
        Text("Preferred Language:", color = Color.White, fontSize = 16.sp)
        var expanded by remember { mutableStateOf(false) }
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Text(preferredLanguage, color = Color.White)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                langs.forEach { (label, code) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onPreferredLanguageChange(code)
                            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                .edit { putString("language", code) }
                            userRef.child("preferredLanguage")
                                .setValue(code)
                                .addOnCompleteListener {
                                    updateLocale(context, code)
                                    activity?.recreate()
                                }
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ALLOW LOCATION
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Allow Location for Matches",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = allowLocationForMatches,
                onCheckedChange = onAllowLocationChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color(0xFFFF6F00),
                    uncheckedThumbColor = Color.Gray
                )
            )
        }

        Spacer(Modifier.height(16.dp))

        // MATRIMONY MODE
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Matrimony Mode",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isMatrimonyMode,
                onCheckedChange = onMatrimonyModeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color(0xFFFF6F00),
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
            text = "Swipes/Compliments/Boosts",
            color = Color(0xFFFF6F00),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { navController.navigate("buySwipes") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text("Buy Swipes", color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.navigate("buyCompliments") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text("Buy Compliments", color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { navController.navigate("buyBoosts") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text("Buy Boosts", color = Color.White)
        }
    }
}

/** BLOCKED USERS SECTION **/
@Composable
fun BlockedUsersSection(
    blocksRef: DatabaseReference,          // points at /blocks/{currentUserId}
    blockedIds: List<String>               // list of UIDs
) {
    var showBlockedOverlay by remember { mutableStateOf(false) }
    var namesById        by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope            = rememberCoroutineScope()
    val context          = LocalContext.current

    // 1) Fetch all usernames whenever the blockedIds list changes
    LaunchedEffect(blockedIds) {
        val tmp = mutableMapOf<String, String>()
        blockedIds.forEach { uid ->
            try {
                val snap = FirebaseRefs.db
                    .getReference("users")
                    .child(uid)
                    .child("username")
                    .get()
                    .await()
                tmp[uid] = snap.getValue(String::class.java) ?: uid
            } catch (_: Exception) {
                tmp[uid] = uid
            }
        }
        namesById = tmp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, shape = RoundedCornerShape(8.dp))
            .clickable { showBlockedOverlay = true }
            .padding(16.dp)
    ) {
        Text(
            text = "Blocked Users",
            color = Color(0xFFFF6F00),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (blockedIds.isEmpty()) {
            Text("No users are blocked.", color = Color.White, fontSize = 16.sp)
        } else {
            // Show up to 3 usernames
            blockedIds.take(3).forEach { uid ->
                val name = namesById[uid] ?: uid
                Text(name, color = Color.White, fontSize = 16.sp)
            }
        }
    }

    if (showBlockedOverlay) {
        AlertDialog(
            onDismissRequest = { showBlockedOverlay = false },
            title = { Text("Blocked Users", color = Color(0xFFFF6F00)) },
            text = {
                if (blockedIds.isEmpty()) {
                    Text("No users are currently blocked.", color = Color.White)
                } else {
                    Column {
                        blockedIds.forEach { uid ->
                            val name = namesById[uid] ?: uid
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(name, color = Color.White, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    scope.launch {
                                        // remove from /blocks/{currentUserId}/{uid}
                                        blocksRef.child(uid)
                                            .removeValue()
                                            .await()
                                        Toast.makeText(context, "Unblocked $name", Toast.LENGTH_SHORT).show()
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
                    Text("Done", color = Color(0xFFFF6F00))
                }
            }
        )
    }
}

/** UPDATE ACCOUNT SETTINGS FUNCTION **/
suspend fun updateAccountSettingsNoEmail(
    newPassword: String,
    newUsername: String,
    oldUsername: String?,
) {
    val user = FirebaseAuth.getInstance().currentUser ?: throw Exception("No user is signed in.")
    val userId = user.uid

    if (newPassword.isNotBlank()) {
        user.updatePassword(newPassword).await()
    }
    checkAndUpdateUsernameAwait(newUsername, oldUsername, userId)
}

suspend fun checkAndUpdateUsernameAwait(
    newUsername: String,
    oldUsername: String?,
    userId: String
) {
    val db = FirebaseRefs.db.reference
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