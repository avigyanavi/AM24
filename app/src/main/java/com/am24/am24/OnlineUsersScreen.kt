@file:OptIn(ExperimentalFoundationApi::class)

package com.am24.am24

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.min
import androidx.compose.material.icons.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineUsersScreen(navController: NavController) {
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseRefs.db.reference

    var users by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // --- Listen to presence and load profiles (concurrently) ---
    DisposableEffect(Unit) {
        val presenceRef = db.child("presence")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }.filter { it != currentUid }
                loading = true
                scope.launch {
                    try {
                        val profiles = ids.map { id ->
                            async {
                                try {
                                    val snap = db.child("users").child(id).get().await()
                                    if (UserDeletionCache.isDeleted(FirebaseRefs.db, id, snap)) {
                                        return@async null
                                    }
                                    val profile = snap.getValue(Profile::class.java)?.copy(userId = id)
                                    if (profile != null && profile.username.isNullOrBlank()) {
                                        UserDeletionCache.markDeleted(id)
                                        return@async null
                                    }
                                    UserDeletionCache.markActive(id)
                                    profile
                                } catch (e: Exception) {
                                    Log.e("OnlineUsersScreen", "Failed to load profile $id", e)
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull()
                            .sortedBy { it.username?.lowercase() ?: "" }

                        users = profiles
                        errorMessage = null
                    } catch (e: Exception) {
                        Log.e("OnlineUsersScreen", "Error loading users", e)
                        errorMessage = e.message ?: "Unknown error"
                    } finally {
                        loading = false
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("OnlineUsersScreen", "Presence listener canceled: ${error.message}")
                errorMessage = error.message
                loading = false
            }
        }
        presenceRef.addValueEventListener(listener)
        onDispose { presenceRef.removeEventListener(listener) }
    }

    // --- Grouped lists (derived for perf) ---
    val pageSize = 20
    var visibleCount by remember { mutableStateOf(pageSize) }

    LaunchedEffect(users.size) {
        visibleCount = min(visibleCount.coerceAtLeast(pageSize), users.size)
    }

    val visibleUsers = remember(users, visibleCount) {
        if (visibleCount >= users.size) users else users.take(visibleCount)
    }

    val hasMoreUsers = visibleUsers.size < users.size

    val (maleUsers, femaleUsers, otherUsers) = remember(visibleUsers) {
        val males = visibleUsers.filter { it.gender.equals("male", true) }
        val females = visibleUsers.filter { it.gender.equals("female", true) }
        val others = visibleUsers.filter { !it.gender.equals("male", true) && !it.gender.equals("female", true) }
        Triple(males, females, others)
    }

    // --- Omegle invite state (unchanged behavior, just tidied) ---
    var waitingMatch by remember { mutableStateOf<OmegleMatch?>(null) }
    var waitingStatus by remember { mutableStateOf("pending") }
    var infoMessage by remember { mutableStateOf<Int?>(null) }

    fun cancelInvite() {
        waitingMatch?.let { match ->
            db.child("omegleChats").child(match.chatId).child("status").setValue("canceled")
            db.child("omegleInvites").child(match.otherUserId).child(match.chatId).removeValue()
            db.child("omegleChats").child(match.chatId).removeValue()
        }
        waitingMatch = null
        waitingStatus = "pending"
    }

    waitingMatch?.let { match ->
        DisposableEffect(match.chatId) {
            val statusRef = db.child("omegleChats").child(match.chatId).child("status")
            val statusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    waitingStatus = snapshot.getValue(String::class.java) ?: "pending"
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            statusRef.addValueEventListener(statusListener)

            val otherPresenceRef = db.child("presence").child(match.otherUserId)
            val presenceListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists() && waitingStatus == "pending") {
                        cancelInvite()
                        infoMessage = R.string.request_canceled
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            otherPresenceRef.addValueEventListener(presenceListener)

            onDispose {
                statusRef.removeEventListener(statusListener)
                otherPresenceRef.removeEventListener(presenceListener)
            }
        }
    }

    LaunchedEffect(waitingStatus) {
        if (waitingStatus == "accepted" && waitingMatch != null) {
            val match = waitingMatch!!
            waitingMatch = null
            waitingStatus = "pending"
            navController.safePopBackStack("omegleUsers", inclusive = true)
            navController.navigate("omegleChat/${match.chatId}/${match.otherUserId}")
        } else if (waitingStatus == "rejected" && waitingMatch != null) {
            cancelInvite()
            infoMessage = R.string.request_rejected
        }
    }

    if (waitingMatch != null && waitingStatus == "pending") {
        AlertDialog(
            onDismissRequest = { },
            text = { Text(stringResource(R.string.request_sent_waiting)) },
            confirmButton = {
                TextButton(onClick = { cancelInvite() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    infoMessage?.let { resId ->
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            text = { Text(stringResource(resId)) },
            confirmButton = {
                TextButton(onClick = { infoMessage = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    BackHandler {
        if (waitingMatch != null) cancelInvite()
        navController.popBackStack()
    }

    // --- UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.online_users), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            stickyHeader {
                ScreenTitleHeader(stringResource(R.string.online_users))
            }

            if (!loading && users.isEmpty() && errorMessage == null) {
                item {
                    EmptyState(
                        text = stringResource(R.string.no_users_online)
                    )
                }
            }

            if (maleUsers.isNotEmpty()) {
                stickyHeader { SectionHeader(stringResource(R.string.male_header), maleUsers.size) }
                items(maleUsers, key = { it.userId ?: it.username ?: it.hashCode().toString() }) { prof ->
                    OnlineUserRow(
                        profile = prof,
                        onClick = {
                            scope.launch {
                                val match = inviteOmegleUser(prof.userId)
                                if (match != null) waitingMatch = match
                            }
                        }
                    )
                }
            }

            if (femaleUsers.isNotEmpty()) {
                stickyHeader { SectionHeader(stringResource(R.string.female_header), femaleUsers.size) }
                items(femaleUsers, key = { it.userId ?: it.username ?: it.hashCode().toString() }) { prof ->
                    OnlineUserRow(
                        profile = prof,
                        onClick = {
                            scope.launch {
                                val match = inviteOmegleUser(prof.userId)
                                if (match != null) waitingMatch = match
                            }
                        }
                    )
                }
            }

            if (otherUsers.isNotEmpty()) {
                stickyHeader { SectionHeader(stringResource(R.string.other_header), otherUsers.size) }
                items(otherUsers, key = { it.userId ?: it.username ?: it.hashCode().toString() }) { prof ->
                    OnlineUserRow(
                        profile = prof,
                        onClick = {
                            scope.launch {
                                val match = inviteOmegleUser(prof.userId)
                                if (match != null) waitingMatch = match
                            }
                        }
                    )
                }
            }
            if (hasMoreUsers) {
                item {
                    Button(
                        onClick = {
                            visibleCount = min(visibleCount + pageSize, users.size)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(stringResource(R.string.next_page))
                    }
                }
            }
            if (errorMessage != null) {
                item {
                    ErrorState(
                        message = errorMessage!!,
                        onRetry = {
                            // Force a refresh by toggling loading; presence listener will repopulate
                            loading = true
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (waitingMatch != null) cancelInvite() }
    }
}

@Composable
private fun ScreenTitleHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Text(
            title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$label ($count)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 2.dp)
            )
            Divider(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .weight(1f),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Transgender, // neutral placeholder
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun OnlineUserRow(profile: Profile, onClick: () -> Unit) {
    val age = remember(profile.dob) { calculateAge(profile.dob) }

    val (genderIcon, tint) = when (profile.gender?.lowercase()) {
        "male" -> Icons.Filled.Male to Color(0xFF2196F3)
        "female" -> Icons.Filled.Female to Color(0xFFE91E63)
        else -> Icons.Filled.Transgender to MaterialTheme.colorScheme.primary
    }

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(profile)

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listOfNotNull(profile.username, age?.let { "$it" }).joinToString(", "),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        genderIcon, contentDescription = null, tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                profile.hometown?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Avatar(profile: Profile) {
    val size = 48.dp
    val dot = with(LocalDensity.current) { 10.dp }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
    ) {
        if (!profile.profilepicUrl.isNullOrBlank()) {
            AsyncImage(
                model = profile.profilepicUrl,
                contentDescription = profile.username,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.local_placeholder),
                contentDescription = profile.username,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Online dot
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(dot)
                .clip(CircleShape)
                .background(Color(0xFF22C55E)) // green dot
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}
