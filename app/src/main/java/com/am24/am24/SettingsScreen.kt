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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.am24.am24.AccountDeletion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.am24.am24.ui.theme.ThemeManager
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max


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

    val isDarkTheme by ThemeManager.isDarkTheme.collectAsState()

    /* Firebase refs */
    val userRef = FirebaseRefs.db.getReference("users").child(uid)
    val blocksRef = FirebaseRefs.db.getReference("blocks").child(uid)

    var premiumTier by remember { mutableStateOf("Free") }           // "Free" / "Plus" / "Premium"
    var expiry by remember { mutableStateOf(ctx.getString(R.string.na)) }
    var swipes by remember { mutableStateOf(0) }
    var compliments by remember { mutableStateOf(0) }
    var aiMessages    by remember { mutableStateOf(0) }       // ★ NEW ★
    var freeTrialExpiry by remember { mutableStateOf<Long?>(null) }
    var hasUsedFreeTrial by remember { mutableStateOf(false) }
    var freeTrialCompleted by remember { mutableStateOf(false) }
    var loginPlusExpiry by remember { mutableStateOf(0L) }
    var entryFeeOfferExpiry by remember { mutableStateOf(0L) }
    var entryFeePaid by remember { mutableStateOf(false) }
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

    val isIndian = remember(country) { canonicalCountry(country) == "India" }

    /* load once */
    LaunchedEffect(Unit) {
        val s = userRef.get().await()
// pull the flat `isPremium` boolean and optional expiryDate
        val plusFlag = s.child("isPlus").getValue(Boolean::class.java) ?: false
        val premiumFlag = s.child("isPremium").getValue(Boolean::class.java) ?: false
        val loginPlusExpiryVal = s.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L
        val entryFeePaidFlag = s.child("isEntryFeePaid").getValue(Boolean::class.java) ?: false
        val entryFeePaidAt = s.child("entryFeePaidAt").getValue(Long::class.java) ?: 0L
        val entryFeeOfferExpiryVal = s.child("entryFeeOfferExpiry").getValue(Long::class.java) ?: 0L
        val now = System.currentTimeMillis()
        val entryFeeExpiry = if (entryFeePaidAt > 0L) {
            entryFeePaidAt + TimeUnit.DAYS.toMillis(30)
        } else 0L
        val entryFeeActive = entryFeePaidFlag && ((loginPlusExpiryVal > now) || (entryFeeExpiry > now && entryFeeExpiry > 0L))
        val plusExpired = plusFlag && entryFeePaidFlag && !entryFeeActive && !premiumFlag

        if (plusExpired) {
            userRef.child("isPlus").setValue(false)
            if (entryFeePaidFlag) {
                userRef.child("isEntryFeePaid").setValue(false)
            }
        }

        entryFeePaid = entryFeeActive
        entryFeeOfferExpiry = entryFeeOfferExpiryVal
        if (entryFeeOfferExpiryVal > 0L && entryFeeOfferExpiryVal < now) {
            userRef.child("entryFeeOfferExpiry").removeValue()
            entryFeeOfferExpiry = 0L
        }

        hasUsedFreeTrial = s.child("hasUsedFreeTrial").getValue(Boolean::class.java) ?: false
        freeTrialCompleted = s.child("freeTrialCompleted").getValue(Boolean::class.java) ?: false
        freeTrialExpiry = s.child("freeTrialExpiry").getValue(Long::class.java)

        premiumTier = when {
            premiumFlag -> "Premium"
            plusFlag && !plusExpired -> "Plus"
            entryFeeActive -> "Plus"
            else -> "Free"
        }
        val subscriptionIdValue = s.child("subscription").child("id")
            .getValue(String::class.java)
        val nextRenewalValue = s.child("nextRenewal").getValue(Long::class.java)
        val activeEntryFeeExpiry = entryFeeExpiry.takeIf { entryFeeActive && it > now }
        val activeLoginPlusExpiry = loginPlusExpiryVal.takeIf { it > now }

        loginPlusExpiry = if (plusExpired) 0L else loginPlusExpiryVal
        expiry = when {
            activeEntryFeeExpiry != null -> DateFormat.getDateInstance().format(Date(activeEntryFeeExpiry))
            !subscriptionIdValue.isNullOrBlank() -> "Never"
            nextRenewalValue != null -> DateFormat.getDateInstance().format(Date(nextRenewalValue))
            activeLoginPlusExpiry != null -> DateFormat.getDateInstance().format(Date(activeLoginPlusExpiry))
            else -> ctx.getString(R.string.na)
        }

        subscriptionId = subscriptionIdValue
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

        loginPlusExpiry = s.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L

        blocksRef.get().addOnSuccessListener { snap ->
            blocked = snap.children.mapNotNull { it.key }
        }
    }

    val listState = rememberLazyListState()
    Scaffold { pads ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = pads.calculateBottomPadding())   // ✨ only bottom
                .padding(horizontal = 16.dp)
                .visibleScrollbar(listState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (premiumTier == "Free") {
                item {
                    val now = System.currentTimeMillis()
                    val expiryValue = freeTrialExpiry
                    val trialMessage = when {
                        !hasUsedFreeTrial && !freeTrialCompleted ->
                            stringResource(R.string.settings_trial_not_started)
                        expiryValue != null && expiryValue > now -> {
                            val daysLeft = max(
                                1,
                                ceil((expiryValue - now).toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
                            )
                            val dateLabel = DateFormat.getDateInstance().format(Date(expiryValue))
                            val daysText = pluralStringResource(
                                R.plurals.settings_trial_days_left,
                                daysLeft,
                                daysLeft
                            )
                            stringResource(R.string.settings_trial_active, dateLabel, daysText)
                        }
                        expiryValue != null && expiryValue > 0L -> {
                            val dateLabel = DateFormat.getDateInstance().format(Date(expiryValue))
                            stringResource(R.string.settings_trial_expired, dateLabel)
                        }
                        freeTrialCompleted -> stringResource(R.string.settings_trial_expired_generic)
                        else -> stringResource(R.string.settings_trial_not_started)
                    }
                    val plusSuffix = if (loginPlusExpiry > now)
                        stringResource(R.string.plus_time_left, ((loginPlusExpiry - now) / 3600000).toInt())
                    else null
                    val message = plusSuffix?.let {
                        stringResource(R.string.settings_trial_message_with_plus, trialMessage, it)
                    } ?: trialMessage

                    Text(
                        message,
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
                        val now = System.currentTimeMillis()
                        val offerActive = entryFeeOfferExpiry > now && !entryFeePaid
                        val offerHoursLeft = if (offerActive) {
                            max(1, ceil((entryFeeOfferExpiry - now) / 3600000.0).toInt())
                        } else 0
                        SettingsRow(
                            icon  = { Icon(Icons.Default.StarOutline, null) },
                            title = stringResource(R.string.settings_free_user),
                            trailingText = stringResource(R.string.upgrade),
                            onClick = {
                                navController.navigate("subscription")
                            }
                        )
                        if (offerActive) {
                            Text(
                                stringResource(R.string.settings_limited_offer_message, offerHoursLeft),
                                modifier = Modifier
                                    .padding(start = 72.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { navController.navigate("entryFeePlus") },
                                modifier = Modifier.padding(start = 60.dp, bottom = 8.dp)
                            ) {
                                Text(stringResource(R.string.settings_view_offer_cta))
                            }
                        }

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
                            trailingText = stringResource(R.string.expires_prefix, expiry),
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

//                    if (premiumTier != "Free") {
//                        SettingsRow(
//                            icon  = { Icon(Icons.Default.Swipe, null) },
//                            title = stringResource(R.string.paid_user_stack),
//                            onClick = { navController.navigate("dating") }
//                        )
//                        Divider(Modifier.padding(start = 56.dp))
//                    }

                    /* STATIC SWIPES ROW  */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.Swipe, null) },
                        title        = stringResource(R.string.settings_get_more_swipes),
                        trailingText = "$swipes",
                        onClick = {
                            if (isIndian) {
                                navController.navigate("buySwipes")
                            } else {
                                navController.navigate("subscription?allowIfSubscribed=true&force=false")
                            }
                        }
                    )
                    Divider(Modifier.padding(start = 56.dp))

                    /* STATIC COMPLIMENTS ROW  */
                    SettingsRow(
                        icon         = { Icon(Icons.Default.FavoriteBorder, null) },
                        title        = stringResource(R.string.settings_get_more_compliments),
                        trailingText = "$compliments",
                        onClick = {
                            if (isIndian) {
                                navController.navigate("buyCompliments")
                            } else {
                                navController.navigate("subscription?allowIfSubscribed=true&force=false")
                            }
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
                    isDarkTheme = isDarkTheme,
                    onThemeChange = { dark -> ThemeManager.setDarkTheme(ctx, dark) },
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
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor       = Color(0xFF1A1A1A),
                            cursorColor          = Color.White,
                            focusedIndicatorColor   = kupidxOrange,   // ← orange outline (focused)
                            unfocusedIndicatorColor = kupidxOrange.copy(alpha = 0.4f), // ← subtler outline (unfocused)
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
                        Text(stringResource(R.string.send), color = kupidxOrange)        // ← orange text
                    }
                },
                dismissButton = {
                    if (!working)
                        TextButton(onClick = { showFeedbackDialog = false }) {
                            Text(stringResource(R.string.cancel), color = kupidxOrange)
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
                        Text(stringResource(R.string.reset), color = kupidxOrange)
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
                    Text(stringResource(R.string.account_delete_prompt))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            working = true
                            scope.launch {
                                try {
                                    AccountDeletion.deleteAccount()
                                    Toast.makeText(ctx, R.string.account_deleted, Toast.LENGTH_LONG).show()
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
                        Text(stringResource(R.string.delete), color = kupidxOrange)
                    }
                },
                dismissButton = {
                    if (!working) {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(R.string.cancel), color = kupidxOrange)
                        }
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
    var unameStatus by remember { mutableStateOf("idle") } // idle/checking/ok/not/invalid

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
        if (!editingUname) {
            unameStatus = "idle"
            return@LaunchedEffect
        }
        val trimmed = username.trim()
        if (trimmed.isBlank()) {
            unameStatus = "idle"
            return@LaunchedEffect
        }
        if (!isFirebaseKeyValid(trimmed)) {
            unameStatus = "invalid"
            return@LaunchedEffect
        }

        unameStatus = "checking"
        delay(500)
        val snap = FirebaseRefs.db.getReference("usernames").child(trimmed).get().await()
        unameStatus = if (snap.exists() && snap.value != uid) "❌" else "✅"
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
                    val invalidUsernameMessage = stringResource(R.string.username_invalid_chars)
                    Column {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.username), color = Color(0xFFFF6F00)) },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color(0xFFFF6F00),
                                cursorColor = Color(0xFFFF6F00),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (unameStatus == "invalid") {
                            Text(
                                text = invalidUsernameMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                trailingContent = {
                    val trimmedUsername = username.trim()
                    val enabled = trimmedUsername.isNotBlank() && unameStatus == "ok"
                    TextButton(
                        onClick = {
                            editingUname = false
                            scope.launch {
                                val finalUsername = trimmedUsername
                                username = finalUsername
                                updateAccountSettingsNoEmail("", finalUsername, oldUsername)
                                oldUsername = finalUsername
                                Toast.makeText(ctx, R.string.username_updated, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = enabled
                    ) { Text(stringResource(R.string.done)) }
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
                text = { Text(stringResource(R.string.verify_email)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isSendingEmail = true
                            scope.launch {
                                try {
                                    FirebaseAuth.getInstance().currentUser?.sendEmailVerification()?.await()
                                    Toast.makeText(ctx, R.string.email_verification_sent, Toast.LENGTH_LONG).show()
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
                                    Toast.makeText(ctx, R.string.email_verified_enjoy, Toast.LENGTH_LONG).show()
                                    needsVerification = false
                                    showVerifyDialog = false
                                    try {
                                        FirebaseFunctions.getInstance("asia-south1")
                                            .getHttpsCallable("flipGovtIdOnEmailVerify")
                                            .call()
                                    } catch (_: Exception) {}
                                } else {
                                    Toast.makeText(ctx, R.string.email_not_verified_yet, Toast.LENGTH_LONG).show()
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
                        label = { Text(stringResource(R.string.current_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(cursorColor = KupidxOrange)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text(stringResource(R.string.new_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(cursorColor = KupidxOrange)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.confirm_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(cursorColor = KupidxOrange)
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
                            val auth = FirebaseAuth.getInstance()
                            val user = auth.currentUser
                            val email = user?.email
                            if (user == null || email.isNullOrEmpty()) {
                                Toast.makeText(
                                    ctx,
                                    "Password changes require an email/password account.",
                                    Toast.LENGTH_LONG
                                ).show()
                                working = false
                                return@launch
                            }
                            try {
                                val cred = EmailAuthProvider.getCredential(email, oldPass)
                                user.reauthenticate(cred).await()
                                user.updatePassword(newPass).await()
                                Toast.makeText(ctx, R.string.password_updated, Toast.LENGTH_SHORT).show()
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
                ) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                if (!working)
                    TextButton(onClick = { showPassDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
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
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
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
    )
    var exp by remember { mutableStateOf(false) }

    SettingsSection {
        ListItem(
            leadingContent = { Icon(Icons.Default.Tune, null, tint = Color(0xFFFF6F00)) },
            headlineContent = { Text(stringResource(R.string.global_preferences), fontWeight = FontWeight.Bold) }
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
            Text(stringResource(R.string.private_account), Modifier.weight(1f))
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

        /* theme */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LightMode, null)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.settings_day_theme), Modifier.weight(1f))
            Switch(
                checked = !isDarkTheme,
                onCheckedChange = { enableDay -> onThemeChange(!enableDay) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF6F00))
            )
        }

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
                                }) { Text(stringResource(R.string.unblock), color = Color(0xFFFF6F00)) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overlay = false }) { Text(stringResource(R.string.done), color = Color(0xFFFF6F00)) }
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
    val trimmedOld = oldUsername.trim()
    val trimmedNew = newUsername.trim()

    val validOld = if (trimmedOld.isNotEmpty() && isFirebaseKeyValid(trimmedOld)) trimmedOld else null
    val validNew = if (trimmedNew.isNotEmpty()) {
        if (!isFirebaseKeyValid(trimmedNew)) {
            throw IllegalArgumentException("Username contains invalid characters")
        }
        trimmedNew
    } else null

    if (validOld != null && validOld != validNew) {
        val snap = usernames.child(validOld).get().await()
        if (snap.exists() && snap.value == userId) usernames.child(validOld).removeValue().await()
    }
    if (validNew != null) {
        val dup = usernames.child(validNew).get().await()
        if (dup.exists() && dup.value != userId) throw Exception("Username taken")
        usernames.child(validNew).setValue(userId).await()
    }

    db.child("users").child(userId).child("username").setValue(trimmedNew).await()
}

