// SettingsScreen.kt  (drop-in replacement)

@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.am24.am24.AccountDeletion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

/* ───────────────────────────────────────────────  small helpers ── */

@Composable
fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    trailingText: String? = null,
    showChevron: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit = {}
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != {}, onClick = onClick),
        leadingContent = { CompositionLocalProvider(LocalContentColor provides tint, content = icon) },
        headlineContent = { Text(title, color = tint) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingText != null) {
                    Text(
                        trailingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showChevron) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

/* ───────────────────────────────────────────────  main screen ── */

@Composable
fun SettingsScreen(navController: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val uid = user.uid

    /* Firebase refs */
    val userRef = FirebaseRefs.db.getReference("users").child(uid)
    val blocksRef = FirebaseRefs.db.getReference("blocks").child(uid)

    /*  state  */
    var isPremium by remember { mutableStateOf(false) }
    var premiumTier by remember { mutableStateOf("Free") }           // "Free" / "Plus" / "Premium"
    var expiry by remember { mutableStateOf("N/A") }
    var boosts by remember { mutableStateOf(0) }
    var swipes by remember { mutableStateOf(0) }
    var compliments by remember { mutableStateOf(0) }
    var aiMessages    by remember { mutableStateOf(0) }       // ★ NEW ★

    var isPrivate by remember { mutableStateOf(false) }
    var preferredLang by remember { mutableStateOf("en") }
    var allowLoc by remember { mutableStateOf(true) }
    var isMatrimony by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(listOf<String>()) }

    // ── NEW STATE ──
    var country by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var locality by remember { mutableStateOf("") }

    var showLocationDialog by remember { mutableStateOf(false) }
    var subscriptionId by remember { mutableStateOf<String?>(null) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var feedbackText      by remember { mutableStateOf("") }
    var working           by remember { mutableStateOf(false) }

    /* load once */
    LaunchedEffect(Unit) {
        val s = userRef.get().await()
// pull the flat `isPremium` boolean and optional expiryDate
            val plusFlag    = s.child("isPlus").getValue(Boolean::class.java) ?: false
            val premiumFlag = s.child("isPremium").getValue(Boolean::class.java) ?: false
            premiumTier = when {
                  premiumFlag -> "Premium"
                  plusFlag    -> "Plus"
                  else         -> "Free"
                }
        expiry = s.child("nextRenewal").getValue(Long::class.java)
              ?.let { DateFormat.getDateInstance().format(Date(it)) }
              ?: "N/A"

        subscriptionId = s.child("subscription").child("id")
            .getValue(String::class.java)

        boosts      = s.child("availableBoosts").getValue(Int::class.java) ?: 0
        swipes      = s.child("swipesInfo/remainingSwipes").getValue(Int::class.java) ?: 0
        compliments = s.child("availableCompliments").getValue(Int::class.java) ?: 0
        aiMessages    = s.child("availableAiMessages").getValue(Int::class.java) ?: 0   // ← NEW

        isPrivate   = s.child("isPrivate").getValue(Boolean::class.java) ?: false
        preferredLang = s.child("preferredLanguage").getValue(String::class.java) ?: "en"
        allowLoc    = s.child("allowLocationForMatches").getValue(Boolean::class.java) ?: true
        isMatrimony = s.child("isMatrimonyMode").getValue(Boolean::class.java) ?: false

        // ── load the new fields too ──
        country  = s.child("country").getValue(String::class.java) ?: ""
        city     = s.child("city").getValue(String::class.java) ?: ""
        locality = s.child("hometown").getValue(String::class.java) ?: ""

        blocksRef.get().addOnSuccessListener { snap ->
            blocked = snap.children.mapNotNull { it.key }
        }
    }

    Scaffold { pads ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(bottom = pads.calculateBottomPadding())   // ✨ only bottom
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (isPremium) {
                    SettingsSection {
                        SettingsRow(
                            icon = { Icon(Icons.Default.Public, null, tint = Color(0xFFFF6F00)) },
                            title = "Change Location",
                            trailingText = listOf(country, city, locality)
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                                .ifBlank { "Not set" }
                        ) {
                            showLocationDialog = true
                        }
                        var ctx = LocalContext.current
                        if (showLocationDialog) {
                            // pull in your arrays; you can use stringArrayResource or any provider
                            val countryOptions = stringArrayResource(R.array.country_names).toList()
                            val cityOptions    = stringArrayResource(R.array.city_names).toList()
                            val localityResId  = remember(city) {
                                ctx.resources.getIdentifier(
                                    "localities_${city.replace(" ", "_").lowercase()}",
                                    "array",
                                    ctx.packageName
                                )
                            }
                            val localityOptions = if (localityResId != 0)
                                ctx.resources.getStringArray(localityResId).toList()
                            else emptyList()

                            AlertDialog(
                                onDismissRequest = { showLocationDialog = false },
                                title = { Text("Select your Location") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SearchableDropdown(
                                            label = "Country",
                                            options = countryOptions,
                                            selected = country,
                                            onSelectedChange = { country = it }
                                        )
                                        SearchableDropdown(
                                            label = "City",
                                            options = cityOptions,
                                            selected = city,
                                            onSelectedChange = { city = it }
                                        )
                                        SearchableDropdown(
                                            label = "Locality",
                                            options = localityOptions,
                                            selected = locality,
                                            onSelectedChange = { locality = it }
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        // write all three at once
                                        scope.launch {
                                            userRef.child("country").setValue(country)
                                            userRef.child("city").setValue(city)
                                            userRef.child("hometown").setValue(locality)
                                        }
                                        showLocationDialog = false
                                    }) { Text("Save") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLocationDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            /*──────────────── Premium / Subscription card ─────────────*/
            item {
                SettingsSection {
                    ListItem(
                        leadingContent = { Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700)) },
                        headlineContent = { Text("Membership", fontWeight = FontWeight.Bold) }
                    )
                    Divider(Modifier.padding(start = 56.dp))

                    /**  FREE  → go to the NEW UpgradeLandingScreen  */
                    if (premiumTier == "Free") {
                        SettingsRow(
                            icon  = { Icon(Icons.Default.StarOutline, null) },
                            title = "Free User",
                            trailingText = "Upgrade",
                            onClick = {                 // ⬅️ change only this line
                                navController.navigate("upgradeLanding")
                            }
                        )

                        /**  PLUS / PREMIUM  → keep old manage page  */
                    } else {
                        SettingsRow(
                            icon  = { Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700)) },
                            title = "$premiumTier Member",
                            trailingText = "Expires: $expiry",
                            onClick = { navController.navigate("manageSubscription") } // <-- UPDATED ROUTE
                        )
                    }

                    Divider(Modifier.padding(start = 56.dp))

                    /* STATIC BOOSTS ROW  */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.FlashOn, null) },
                        title        = "Boosts remaining",
                        trailingText = "$boosts",
                        onClick = { navController.navigate("buyBoosts") }
                    )
                    Divider(Modifier.padding(start = 56.dp))

                    /* STATIC SWIPES ROW  */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.Swipe, null) },
                        title        = "Swipes remaining",
                        trailingText = "$swipes",
                        onClick = { navController.navigate("buySwipes") }
                    )
                    Divider(Modifier.padding(start = 56.dp))

                    /* STATIC COMPLIMENTS ROW  */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.FavoriteBorder, null) },
                        title        = "Compliments remaining",
                        trailingText = "$compliments",
                        onClick = { navController.navigate("buyCompliments") }
                    )

                    /* AI messages  ★ NEW ★ */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.SmartToy, null) },
                        title        = "AI messages remaining",
                        trailingText = aiMessages.toString(),
                        onClick      = { navController.navigate("buyAiMessages") }
                    )
                }
            }

            /*──────────────── Account settings  (username / password) ─*/
            item { AccountCard(uid) }

            /*──────────────── Global preferences ──────────────────────*/
            item {
                GlobalPrefCard(
                    userRef = userRef,
                    lang = preferredLang,
                    onLangChange = { code ->
                        preferredLang = code
                        scope.launch {
                            userRef.child("preferredLanguage").setValue(code)
                            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                .edit().putString("language", code).apply()
                            updateLocale(ctx, code)
                            (ctx as? ComponentActivity)?.recreate()
                        }
                    },
                    isPrivate = isPrivate,
                    onPrivateChange = {
                        isPrivate = it
                        scope.launch { userRef.child("isPrivate").setValue(it) }
                    },
                    allowLoc = allowLoc,
                    onAllowLocChange = {
                        allowLoc = it
                        scope.launch { userRef.child("allowLocationForMatches").setValue(it) }
                    },
                    isMatrimony = isMatrimony,
                    onMatrimonyChange = {
                        isMatrimony = it
                        scope.launch { userRef.child("isMatrimonyMode").setValue(it) }
                    }
                )
            }

            /*──────────────── Blocked users ───────────────────────────*/
            item { BlockedUsersCard(blocksRef, blocked) }

            /*──────────────── Logout row ──────────────────────────────*/
            item {
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFFF5722)) },
                        title = "Logout",
                        showChevron = false,
                        tint = Color(0xFFFF5722)
                    ) {
                        FirebaseAuth.getInstance().signOut()
                        TokenStorageManager.clearToken(ctx)
                        ctx.startActivity(Intent(ctx, LandingActivity::class.java))
                        (ctx as? ComponentActivity)?.finish()
                    }
                }
            }

            item {
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(Icons.Default.Feedback, null, tint = Color(0xFFFF6F00)) },
                        title = "Give Feedback",
                        showChevron = false
                    ) { showFeedbackDialog = true }
                }
            }
            /* ───── Policies & Support ───── */
            item {
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(Icons.Default.Description, null, tint = Color(0xFFFF6F00)) },
                        title = "Policies & Support",
                        onClick = { navController.navigate("policies") }
                    )
                }
            }
            /* ───── Delete Account ───── */
            item {
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6F00)) },
                        title = "Delete Account",
                        showChevron = false,
                        onClick = { showDeleteDialog = true }
                    )
                }
            }


            /*──────────────── footer ─────────────────────────────────*/
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Kupidx™",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "v0.1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                )
            }
        }
        val kupidxOrange = Color(0xFFFF6F00)
        if (showFeedbackDialog) {
            AlertDialog(
                onDismissRequest = { if (!working) showFeedbackDialog = false },
                title = { Text("Send Feedback", color = kupidxOrange) },
                text = {
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("Your feedback") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            containerColor       = Color(0xFF1A1A1A),
                            cursorColor          = Color.White,
                            focusedBorderColor   = kupidxOrange,   // ← orange outline (focused)
                            unfocusedBorderColor = kupidxOrange.copy(alpha = 0.4f), // ← subtler outline (unfocused)
                            focusedLabelColor    = kupidxOrange
                        )
                    )
                },
                confirmButton = {
                    // button NEVER vanishes; just gets disabled
                    TextButton(
                        onClick = {
                            working = true
                            scope.launch {
                                try {
                                    submitFeedback(uid, feedbackText)
                                    Toast.makeText(ctx, "Feedback sent", Toast.LENGTH_SHORT).show()
                                    showFeedbackDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                } finally {
                                    working = false
                                    feedbackText = ""
                                }
                            }
                        },
                        enabled = feedbackText.isNotBlank() && !working
                    ) {
                        Text("Send", color = kupidxOrange)        // ← orange text
                    }
                },
                dismissButton = {
                    if (!working)
                        TextButton(onClick = { showFeedbackDialog = false }) {
                            Text("Cancel", color = kupidxOrange)
                        }
                }
            )
        }
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { if (!working) showDeleteDialog = false },
                title = { Text("Delete Account", color = kupidxOrange) },
                text = {
                    Text("Are you sure you want to delete your account? All data will be removed.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            working = true
                            scope.launch {
                                try {
                                    AccountDeletion.deleteAccount()
                                    Toast.makeText(ctx, "Account deleted", Toast.LENGTH_LONG).show()
                                    FirebaseAuth.getInstance().signOut()
                                    TokenStorageManager.clearToken(ctx)
                                    ctx.startActivity(Intent(ctx, LandingActivity::class.java))
                                    (ctx as? ComponentActivity)?.finish()
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                } finally {
                                    working = false
                                    showDeleteDialog = false
                                }
                            }
                        },
                        enabled = !working
                    ) {
                        Text("Delete", color = kupidxOrange)
                    }
                },
                dismissButton = {
                    if (!working)
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel", color = kupidxOrange)
                        }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelectedChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query    by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = if (expanded) query else selected,
            onValueChange = { query = it },
            readOnly = !expanded,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = ""
            }
        ) {
            // filter your options by query (case-insensitive)
            options
                .filter { it.contains(query, ignoreCase = true) }
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelectedChange(option)
                            query = ""
                            expanded = false
                        }
                    )
                }
        }
    }
}

/* ───────────────────────────────────── account card ─ */

@Composable
private fun AccountCard(uid: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbUser = FirebaseRefs.db.getReference("users").child(uid)

    /* fields */
    var email by remember { mutableStateOf("") }

    /* username */
    var editingUname by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var oldUsername by remember { mutableStateOf("") }
    var unameStatus by remember { mutableStateOf("idle") } // idle/checking/ok/not

    /* password dialog */
    var showPassDialog by remember { mutableStateOf(false) }

    /* load user data once */
    LaunchedEffect(uid) {
        val snap = dbUser.get().await()
        email = snap.child("email").getValue(String::class.java) ?: ""
        username = snap.child("username").getValue(String::class.java) ?: ""
        oldUsername = username
    }

    /* validate username availability */
    LaunchedEffect(username, editingUname) {
        if (editingUname && username.isNotBlank()) {
            unameStatus = "checking"
            delay(500)
            val snap = FirebaseRefs.db.getReference("usernames").child(username).get().await()
            unameStatus = if (snap.exists() && snap.value != uid) "not" else "ok"
        }
    }

    SettingsSection {
        ListItem(
            leadingContent = { Icon(Icons.Default.Person, null, tint = Color(0xFFFF6F00)) },
            headlineContent = { Text("Account Settings", fontWeight = FontWeight.Bold) }
        )
        Divider(Modifier.padding(start = 56.dp))

        /* email (display only) */
        ListItem(
            leadingContent = { Icon(Icons.Default.Email, null) },
            headlineContent = { Text("Email: $email") }
        )
        Divider(Modifier.padding(start = 56.dp))

        /* password row */
        SettingsRow(
            icon = { Icon(Icons.Default.Lock, null) },
            title = "Change Password",
            showChevron = false
        ) { showPassDialog = true }
        Divider(Modifier.padding(start = 56.dp))

        /* username row */
        if (editingUname) {
            ListItem(
                leadingContent = { Icon(Icons.Default.Person, null) },
                headlineContent = {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                        label = { Text("Username", color = Color(0xFFFF6F00)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFFF6F00),
                            cursorColor = Color(0xFFFF6F00),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                trailingContent = {
                    val enabled = username.isNotBlank() && unameStatus == "ok"
                    TextButton(
                        onClick = {
                            editingUname = false
                            scope.launch {
                                updateAccountSettingsNoEmail("", username, oldUsername)
                                oldUsername = username
                                Toast.makeText(ctx, "Username updated", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = enabled
                    ) { Text("Done") }
                }
            )
        } else {
            ListItem(
                leadingContent = { Icon(Icons.Default.Person, null) },
                headlineContent = { Text("Username: $username") },
                trailingContent = {
                    TextButton(onClick = { editingUname = true }) { Text("Edit") }
                }
            )
        }
    }

    /* Password overlay dialog */
    if (showPassDialog) {
        var oldPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var working by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!working) showPassDialog = false },
            title = { Text("Change Password", color = Color(0xFFFF6F00)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val enabled = oldPass.isNotBlank() && newPass.isNotBlank() &&
                        confirm.isNotBlank() && newPass == confirm && !working
                TextButton(
                    onClick = {
                        working = true
                        scope.launch {
                            try {
                                val cred = EmailAuthProvider
                                    .getCredential(FirebaseAuth.getInstance().currentUser!!.email!!, oldPass)
                                FirebaseAuth.getInstance().currentUser!!.reauthenticate(cred).await()
                                FirebaseAuth.getInstance().currentUser!!.updatePassword(newPass).await()
                                Toast.makeText(ctx, "Password updated", Toast.LENGTH_SHORT).show()
                                showPassDialog = false
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                working = false
                                oldPass = ""; newPass = ""; confirm = ""
                            }
                        }
                    },
                    enabled = enabled
                ) { Text("Done") }
            },
            dismissButton = {
                if (!working)
                    TextButton(onClick = { showPassDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/* ───────────────────────────────────── global preferences ─ */

@Composable
private fun GlobalPrefCard(
    userRef: com.google.firebase.database.DatabaseReference,
    lang: String,
    onLangChange: (String) -> Unit,
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit,
    allowLoc: Boolean,
    onAllowLocChange: (Boolean) -> Unit,
    isMatrimony: Boolean,
    onMatrimonyChange: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val isIndia = CountryUtil.isProbablyInIndia(ctx)
    val langs = listOf(
        "English" to "en",
        "हिन्दी" to "hi",
        "বাংলা" to "bn",
        "தமிழ்" to "ta",
        "ಕನ್ನಡ" to "kn",
        "తెలుగు" to "te"
    )
    var exp by remember { mutableStateOf(false) }

    SettingsSection {
        ListItem(
            leadingContent = { Icon(Icons.Default.Tune, null, tint = Color(0xFFFF6F00)) },
            headlineContent = { Text("Global Preferences", fontWeight = FontWeight.Bold) }
        )
        Divider(Modifier.padding(start = 56.dp))

        /* private */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, null)
            Spacer(Modifier.width(16.dp))
            Text("Private Account", Modifier.weight(1f))
            Switch(
                checked = isPrivate,
                onCheckedChange = onPrivateChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF6F00))
            )
        }
        Text(
            "Be undiscoverable in card stack except those you swipe right on",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 72.dp, bottom = 12.dp)
        )

        Divider(Modifier.padding(start = 56.dp))

        /* language */
        if (isIndia) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { exp = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Language, null)
                Spacer(Modifier.width(16.dp))
                Text("Preferred Language", Modifier.weight(1f))
                Text(langs.first { it.second == lang }.first)
                Icon(Icons.Default.KeyboardArrowRight, null)
            }
            DropdownMenu(
                expanded = exp,
                onDismissRequest = { exp = false }
            ) {
                langs.forEach { (label, code) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            exp = false
                            onLangChange(code)
                        }
                    )
                }
            }
            Divider(Modifier.padding(start = 56.dp))
        }

        /* location */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MyLocation, null)
            Spacer(Modifier.width(16.dp))
            Text("Allow matches to view you in Maps", Modifier.weight(1f))
            Switch(
                checked = allowLoc,
                onCheckedChange = onAllowLocChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF6F00))
            )
        }
        Divider(Modifier.padding(start = 56.dp))

        /* matrimony */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Group, null)
            Spacer(Modifier.width(16.dp))
            Text("Matrimony Mode", Modifier.weight(1f))
            Switch(
                checked = isMatrimony,
                onCheckedChange = onMatrimonyChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF6F00))
            )
        }
    }
}

/* ───────────────────────────────────── Blocked card ─ */

@Composable
private fun BlockedUsersCard(
    blocksRef: com.google.firebase.database.DatabaseReference,
    ids: List<String>
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var overlay by remember { mutableStateOf(false) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    /* fetch usernames */
    LaunchedEffect(ids) {
        val tmp = mutableMapOf<String, String>()
        ids.forEach {
            val snap = FirebaseRefs.db.getReference("users").child(it).child("username").get().await()
            tmp[it] = snap.getValue(String::class.java) ?: it
        }
        names = tmp
    }

    SettingsSection {
        ListItem(
            leadingContent = { Icon(Icons.Default.Block, null, tint = Color(0xFFFF6F00)) },
            headlineContent = { Text("Blocked Users", fontWeight = FontWeight.Bold) },
            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.clickable { overlay = true }
        )
        Divider(Modifier.padding(start = 56.dp))

        if (ids.isEmpty()) {
            ListItem(headlineContent = { Text("No users are blocked", color = MaterialTheme.colorScheme.onSurfaceVariant) })
        } else {
            ids.take(3).forEach {
                ListItem(headlineContent = { Text(names[it] ?: it) })
                Divider(Modifier.padding(start = 56.dp))
            }
            ListItem(
                headlineContent = { Text("View All Blocked Users", color = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { overlay = true }
            )
        }
    }

    /* dialog */
    if (overlay) {
        AlertDialog(
            onDismissRequest = { overlay = false },
            title = { Text("Blocked Users", color = Color(0xFFFF6F00)) },
            text = {
                if (ids.isEmpty()) {
                    Text("No users are currently blocked.")
                } else {
                    Column {
                        ids.forEach { uid ->
                            val uname = names[uid] ?: uid
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(uname, Modifier.weight(1f))
                                TextButton(onClick = {
                                    scope.launch {
                                        blocksRef.child(uid).removeValue().await()
                                        Toast.makeText(ctx, "Unblocked $uname", Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text("Unblock", color = Color(0xFFFF6F00)) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overlay = false }) { Text("Done", color = Color(0xFFFF6F00)) }
            }
        )
    }
}

/* ───────────────────────────────────── username/password helper ─ */

suspend fun updateAccountSettingsNoEmail(
    newPassword: String,
    newUsername: String,
    oldUsername: String
) {
    val user = FirebaseAuth.getInstance().currentUser ?: throw Exception("No user")
    val userId = user.uid
    if (newPassword.isNotBlank()) user.updatePassword(newPassword).await()

    val db = FirebaseRefs.db.reference
    val usernames = db.child("usernames")
    if (oldUsername.isNotBlank() && oldUsername != newUsername) {
        val snap = usernames.child(oldUsername).get().await()
        if (snap.exists() && snap.value == userId) usernames.child(oldUsername).removeValue().await()
    }
    val dup = usernames.child(newUsername).get().await()
    if (dup.exists() && dup.value != userId) throw Exception("Username taken")
    usernames.child(newUsername).setValue(userId).await()
    db.child("users").child(userId).child("username").setValue(newUsername).await()
}

