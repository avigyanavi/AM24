// SettingsScreen.kt  (drop-in replacement)

@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.app.Activity
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
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.am24.am24.AccountDeletion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.am24.am24.ui.purchase.PurchaseType
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date
import java.util.Locale

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
        elevation = CardDefaults.cardElevation(8.dp)
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

    var premiumTier by remember { mutableStateOf("Free") }           // "Free" / "Plus" / "Premium"
    var expiry by remember { mutableStateOf("N/A") }
    var swipes by remember { mutableStateOf(0) }
    var compliments by remember { mutableStateOf(0) }
    var aiMessages    by remember { mutableStateOf(0) }       // ★ NEW ★
    var loginStreak   by remember { mutableStateOf(0) }
    var loginPlusExpiry by remember { mutableStateOf(0L) }
    var isPrivate by remember { mutableStateOf(false) }
    val defaultLang = if (Locale.getDefault().country.equals("MX", true)) "es" else "en"
    var preferredLang by remember { mutableStateOf(defaultLang) }
    var allowLoc by remember { mutableStateOf(false) }
    var allowPublic by remember { mutableStateOf(false) }
    var isMatrimony by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(listOf<String>()) }
    var subscriptionStatus by remember { mutableStateOf<String?>(null) }
    // ── NEW STATE ──
    var country by remember { mutableStateOf("") }

    var subscriptionId by remember { mutableStateOf<String?>(null) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showResetExcludesDialog by remember { mutableStateOf(false) }
    var feedbackText      by remember { mutableStateOf("") }
    var working           by remember { mutableStateOf(false) }

    var rewardDialogFor   by remember { mutableStateOf<PurchaseType?>(null) }
    val isIndian = remember(country) { country.equals("India", ignoreCase = true) }
    val activity = LocalContext.current as Activity
    val rewardedComplimentManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedCompliment(activity)) }
    val rewardedSwipeManager = remember { RewardedAdManager(activity, AdUnitIds.rewardedSwipe(activity)) }

    DisposableEffect(Unit) {
        onDispose {
            rewardedComplimentManager.clearCallbacks()
            rewardedSwipeManager.clearCallbacks()
        }
    }

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
        expiry = if (!subscriptionId.isNullOrBlank()) "Never" else
            s.child("nextRenewal").getValue(Long::class.java)
                ?.let { DateFormat.getDateInstance().format(Date(it)) }
                ?: "N/A"

        subscriptionId = s.child("subscription").child("id")
            .getValue(String::class.java)
        subscriptionStatus = s.child("subscriptionStatus").getValue(String::class.java)

        swipes      = s.child("swipesInfo/remainingSwipes").getValue(Int::class.java) ?: 0
        compliments = s.child("availableCompliments").getValue(Int::class.java) ?: 0
        aiMessages    = s.child("availableAiMessages").getValue(Int::class.java) ?: 0   // ← NEW

        isPrivate   = s.child("isPrivate").getValue(Boolean::class.java) ?: false
        preferredLang = s.child("preferredLanguage").getValue(String::class.java) ?: defaultLang
        allowLoc    = s.child("allowLocationForMatches").getValue(Boolean::class.java) ?: false
        allowPublic = s.child("allowLocationPublic").getValue(Boolean::class.java) ?: false
        isMatrimony = s.child("isMatrimonyMode").getValue(Boolean::class.java) ?: false

        // ── load the new fields too ──
        country  = s.child("country").getValue(String::class.java) ?: ""

        loginStreak = s.child("loginStreak").getValue(Int::class.java) ?: 0
        loginPlusExpiry = s.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L

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
            if (premiumTier == "Free") {
                item {
                    val now = System.currentTimeMillis()
                    val plusText = if (loginPlusExpiry > now)
                        stringResource(R.string.plus_time_left, ((loginPlusExpiry - now) / 3600000).toInt())
                    else stringResource(R.string.no_plus)

                    Text(
                        stringResource(R.string.settings_streak_line, loginStreak, plusText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            /*──────────────── Premium / Subscription card ─────────────*/
            item {
                SettingsSection {
                    ListItem(
                        leadingContent = { Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700)) },
                        headlineContent = { Text(stringResource(R.string.settings_membership), fontWeight = FontWeight.Bold) }
                    )
                    Divider(Modifier.padding(start = 56.dp))

                    /**  FREE  → go to the NEW UpgradeLandingScreen  */
                    if (premiumTier == "Free") {
                        SettingsRow(
                            icon  = { Icon(Icons.Default.StarOutline, null) },
                            title = stringResource(R.string.settings_free_user),
                            trailingText = stringResource(R.string.upgrade),
                                onClick = {
                                    if (premiumTier == "Plus") {
                                        navController.navigate("manageSubscription")
                                    } else {
                                        if (CountryUtil.useRazorpay(ctx, country)) {
                                            navController.navigate("upgradeLanding")
                                        } else {
                                            navController.navigate("subscription")
                                        }
                                    }
                                }
                        )

                        /**  PLUS / PREMIUM  → keep old manage page  */
                    } else {
                        val statusLabel = when (subscriptionStatus) {
                            "active" -> stringResource(R.string.subscription_active)
                            "inactive" -> stringResource(R.string.subscription_inactive)
                            "completed" -> stringResource(R.string.subscription_completed)
                            "cancelled" -> stringResource(R.string.subscription_cancelled)
                            "suspended" -> stringResource(R.string.subscription_suspended)
                            "expired" -> stringResource(R.string.subscription_expired)
                            null -> "N/A"
                            else -> subscriptionStatus ?: "N/A"
                        }
                        SettingsRow(
                            icon  = { Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700)) },
                            title = "$premiumTier Member",
                            trailingText = "Expires: $expiry",
                            onClick = {
                                if (CountryUtil.useRazorpay(ctx, country)) {
                                    navController.navigate("manageSubscription")
                                } else {
                                    navController.navigate("manageSubscription")
                                }
                            }
                        )
                        if (subscriptionStatus != null) {
                            val reason = if (subscriptionStatus == "inactive")
                                " \u2013 " + stringResource(R.string.payment_failed) else ""
                            Text(
                                "Status: $statusLabel$reason",
                                modifier = Modifier.padding(start = 72.dp, bottom = 4.dp),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Divider(Modifier.padding(start = 56.dp))

                    if (premiumTier == "Premium") {
                        SettingsRow(
                            icon  = { Icon(Icons.Outlined.Leaderboard, null) },
                            title = stringResource(R.string.leaderboard),
                            onClick = { navController.navigate("leaderboard") }
                        )
                        Divider(Modifier.padding(start = 56.dp))
//                        SettingsRow(
//                            icon  = { Icon(Icons.Default.RssFeed, null) },
//                            title = stringResource(R.string.feed),
//                            onClick = { navController.navigate("home") }
//                        )
//                        Divider(Modifier.padding(start = 56.dp))
                    }

                    /* STATIC SWIPES ROW  */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.Swipe, null) },
                        title        = stringResource(R.string.settings_get_more_swipes),
                        trailingText = "$swipes",
                        onClick = {
                            if (isIndian) navController.navigate("buySwipes")
                            else rewardDialogFor = PurchaseType.Swipes
                        }
                    )
                    Divider(Modifier.padding(start = 56.dp))

                    /* STATIC COMPLIMENTS ROW  */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.FavoriteBorder, null) },
                        title        = stringResource(R.string.settings_get_more_compliments),
                        trailingText = "$compliments",
                        onClick = {
                            if (isIndian) navController.navigate("buyCompliments")
                            else rewardDialogFor = PurchaseType.Compliments
                        }
                    )

                    /* AI messages  ★ NEW ★ */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.SmartToy, null) },
                        stringResource(R.string.settings_ai_messages_remaining),
                        trailingText = aiMessages.toString(),
                        onClick = {
                            navController.navigate("buyAiMessages")
                        }
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
                    allowPublic = allowPublic,
                    onAllowPublicChange = {
                        allowPublic = it
                        scope.launch { userRef.child("allowLocationPublic").setValue(it) }
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

            /*──────────────── Reset swipe exclusions ──────────────────*/
            item {
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(Icons.Default.Refresh, null, tint = Color(0xFFFF6F00)) },
                        title = stringResource(R.string.settings_reset_swipe_history),
                        showChevron = false
                    ) { showResetExcludesDialog = true }
                }
            }

            /*──────────────── Logout row ──────────────────────────────*/
            item {
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFFF5722)) },
                        title = stringResource(R.string.cd_logout),
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
                        title = stringResource(R.string.settings_give_feedback),
                        showChevron = false
                    ) { showFeedbackDialog = true }
                }
            }
            /* ───── Policies & Support ───── */
            item {
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(Icons.Default.Description, null, tint = Color(0xFFFF6F00)) },
                        title = stringResource(R.string.settings_policies_support),
                        onClick = { navController.navigate("policies") }
                    )
                }
            }
            /* ───── Delete Account ───── */
            item {
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6F00)) },
                        title = stringResource(R.string.settings_delete_account),
                        showChevron = false,
                        onClick = { showDeleteDialog = true }
                    )
                }
            }


            /*──────────────── footer ─────────────────────────────────*/
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }
        }
        val kupidxOrange = Color(0xFFFF6F00)

        if (!isIndian) rewardDialogFor?.let { type ->
            val msg = when (type) {
                PurchaseType.Compliments -> stringResource(R.string.watch_ad_compliment)
                PurchaseType.Swipes -> stringResource(R.string.watch_ad_swipes)
                else -> ""
            }
            AlertDialog(
                onDismissRequest = { rewardDialogFor = null },
                title = { Text(msg, color = kupidxOrange) },
                confirmButton = {
                    TextButton(onClick = {
                        rewardDialogFor = null
                        val manager = when (type) {
                            PurchaseType.Compliments -> rewardedComplimentManager
                            PurchaseType.Swipes -> rewardedSwipeManager
                            else -> null
                        }
                        manager?.showWithDailyLimit(
                            userId = uid,
                            onReward = {
                            when (type) {
                                PurchaseType.Compliments -> {
                                    compliments += 1
                                    scope.launch { userRef.child("availableCompliments").setValue(compliments) }
                                }
                                PurchaseType.Swipes -> {
                                    swipes += 5
                                    scope.launch { userRef.child("swipesInfo/remainingSwipes").setValue(swipes) }
                                }
                                else -> {}
                            }
                            }
                        )
                    }) { Text(stringResource(R.string.watch), color = kupidxOrange) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        rewardDialogFor?.let {
                            val route = when (it) {
                                PurchaseType.Compliments -> "buyCompliments"
                                PurchaseType.Swipes -> "buySwipes"
                                else -> null
                            }
                            rewardDialogFor = null
                            route?.let { r -> navController.navigate(r) }
                        }
                    }) { Text(stringResource(R.string.pay_instead), color = kupidxOrange) }
                }
            )
        }

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
        if (showResetExcludesDialog) {
            AlertDialog(
                onDismissRequest = { showResetExcludesDialog = false },
                title = { Text(stringResource(R.string.settings_reset_swipe_history), color = kupidxOrange) },
                text = { Text(stringResource(R.string.confirm_reset_swipe_history)) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetExcludesDialog = false
                        scope.launch {
                            try {
                                clearExcludedUsers(uid)
                                Toast.makeText(ctx, "Swipe exclusions cleared", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Text("Reset", color = kupidxOrange)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetExcludesDialog = false }) {
                        Text(stringResource(R.string.cancel), color = kupidxOrange)
                    }
                }
            )
        }
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { if (!working) showDeleteDialog = false },
                title = { Text(stringResource(R.string.settings_delete_account), color = kupidxOrange) },
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
    var showVerifyDialog by remember { mutableStateOf(false) }
    var isSendingEmail by remember { mutableStateOf(false) }
    var needsVerification by remember {
        mutableStateOf(FirebaseAuth.getInstance().currentUser?.isEmailVerified == false)
    }

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
            headlineContent = { Text(stringResource(R.string.settings_account_settings), fontWeight = FontWeight.Bold) }
        )
        Divider(Modifier.padding(start = 56.dp))

        /* email (display only) */
        ListItem(
            leadingContent = { Icon(Icons.Default.Email, null) },
            headlineContent = { Text("${stringResource(R.string.email_label)}: $email") }
        )
        if (needsVerification) {
            SettingsRow(
                icon = { Icon(Icons.Default.VerifiedUser, null) },
                title = stringResource(R.string.verify_email),
                showChevron = false
            ) { showVerifyDialog = true }
            Divider(Modifier.padding(start = 56.dp))
        } else {
            Divider(Modifier.padding(start = 56.dp))
        }

        /* password row */
        SettingsRow(
            icon = { Icon(Icons.Default.Lock, null) },
            title = stringResource(R.string.settings_change_password),
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
                    TextButton(onClick = { editingUname = true }) {
                        Text(stringResource(R.string.edit), color = KupidxOrange)
                    }
                }
            )
        }
        if (showVerifyDialog) {
            AlertDialog(
                onDismissRequest = { showVerifyDialog = false },
                title = { Text(stringResource(R.string.verify_email), color = Color(0xFFFF6F00)) },
                text = { Text("Please verify your email address to use the app.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isSendingEmail = true
                            scope.launch {
                                try {
                                    FirebaseAuth.getInstance().currentUser?.sendEmailVerification()?.await()
                                    Toast.makeText(ctx, "Verification email sent!", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, e.localizedMessage ?: "Error", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSendingEmail = false
                                }
                            }
                        },
                        enabled = !isSendingEmail
                    ) {
                        if (isSendingEmail) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.resend_link), color = KupidxOrange)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                FirebaseAuth.getInstance().currentUser?.reload()?.await()
                                val refreshed = FirebaseAuth.getInstance().currentUser
                                if (refreshed?.isEmailVerified == true) {
                                    Toast.makeText(ctx, "Email verified – enjoy the app!", Toast.LENGTH_LONG).show()
                                    needsVerification = false
                                    showVerifyDialog = false
                                    try {
                                        FirebaseFunctions.getInstance("asia-south1")
                                            .getHttpsCallable("flipGovtIdOnEmailVerify")
                                            .call()
                                    } catch (_: Exception) {}
                                } else {
                                    Toast.makeText(ctx, "Still not verified — please confirm the link first.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(ctx, e.localizedMessage ?: "Error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text(stringResource(R.string.ive_verified), color = KupidxOrange) }
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
            title = { Text(stringResource(R.string.settings_change_password), color = Color(0xFFFF6F00)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(cursorColor = KupidxOrange)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(cursorColor = KupidxOrange)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(cursorColor = KupidxOrange)
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
    allowPublic: Boolean,
    onAllowPublicChange: (Boolean) -> Unit,
    isMatrimony: Boolean,
    onMatrimonyChange: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val langs = listOf(
        "English" to "en",
        "Español" to "es",
        "हिन्दी" to "hi",
        "বাংলা" to "bn",
        "தமிழ்" to "ta",
        "తెలుగు" to "te",
        "ಕನ್ನಡ" to "kn"
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
            stringResource(R.string.private_account_desc),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 72.dp, bottom = 12.dp)
        )

        Divider(Modifier.padding(start = 56.dp))

        /* language */
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { exp = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.settings_preferred_language), Modifier.weight(1f))
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
            }
        Divider(Modifier.padding(start = 56.dp))


        /* location */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MyLocation, null)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.settings_allow_matches_maps), Modifier.weight(1f))
            Switch(
                checked = allowLoc,
                onCheckedChange = onAllowLocChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF6F00))
            )
        }
        Divider(Modifier.padding(start = 56.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Public, null)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.settings_allow_public_maps), Modifier.weight(1f))
            Switch(
                checked = allowPublic,
                onCheckedChange = onAllowPublicChange,
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
            Text(stringResource(R.string.settings_matrimony_mode), Modifier.weight(1f))
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
            headlineContent = { Text(stringResource(R.string.settings_blocked_users), fontWeight = FontWeight.Bold) },
            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.clickable { overlay = true }
        )
        Divider(Modifier.padding(start = 56.dp))

        if (ids.isEmpty()) {
            ListItem(headlineContent = { Text(stringResource(R.string.settings_no_users_blocked), color = MaterialTheme.colorScheme.onSurfaceVariant) })
        } else {
            ids.take(3).forEach {
                ListItem(headlineContent = { Text(names[it] ?: it) })
                Divider(Modifier.padding(start = 56.dp))
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_view_all_blocked_users), color = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { overlay = true }
            )
        }
    }

    /* dialog */
    if (overlay) {
        AlertDialog(
            onDismissRequest = { overlay = false },
            title = { Text(stringResource(R.string.settings_blocked_users), color = Color(0xFFFF6F00)) },
            text = {
                if (ids.isEmpty()) {
                    Text(stringResource(R.string.settings_no_users_currently_blocked))
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

