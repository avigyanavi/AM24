package com.am24.am24

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleWhoLikeMeScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel(),
    currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
) {
    val context = LocalContext.current
    var isPlus by remember { mutableStateOf<Boolean?>(null) }
    var isPremium by remember { mutableStateOf<Boolean?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val blockedRef = FirebaseRefs.db
        .getReference("blocks/$currentUserId")

    val blockedIds = remember { mutableStateListOf<String>() }

    val matchPopUpState by profileViewModel.matchPopUpState.collectAsState()

    val likesReceivedRef = FirebaseRefs.db
        .getReference("likesReceived/$currentUserId")
    val usersRef = FirebaseRefs.db.getReference("users")

    // We also fetch the current user's matches so we can exclude them
    val matchesRef = FirebaseRefs.db
        .getReference("matches/$currentUserId")

    // Will hold the final list of profiles who liked me
    val likedUsers = remember { mutableStateListOf<Profile>() }

    // We'll track the user's matched IDs so we can skip them
    val myMatchIds = remember { mutableStateListOf<String>() }

    // (1) Get the current user's matched user IDs
    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        try {
            val db = FirebaseRefs.db
            val userSnapshot = db.getReference("users")
                .child(currentUserId)
                .get()
                .await()

            val premiumFlag = userSnapshot.child("isPremium").getValue(Boolean::class.java) ?: false
            val plusFlag = userSnapshot.child("isPlus").getValue(Boolean::class.java) ?: false
            val loginPlusExpiryValue = userSnapshot.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L
            val entryFeePaidAtValue = userSnapshot.child("entryFeePaidAt").getValue(Long::class.java) ?: 0L
            val entryFeePaidFlag = userSnapshot.child("isEntryFeePaid").getValue(Boolean::class.java) ?: false
            val nextRenewalValue = userSnapshot.child("nextRenewal").getValue(Long::class.java) ?: 0L
            val premiumExpiryValue = userSnapshot.child("premiumExpiryDate").getValue(Long::class.java) ?: 0L
            val subscriptionStatusValue = userSnapshot.child("subscriptionStatus").getValue(String::class.java)

            val now = System.currentTimeMillis()
            val entryFeePaidExpiry = if (entryFeePaidAtValue > 0L) {
                entryFeePaidAtValue + TimeUnit.DAYS.toMillis(30)
            } else {
                0L
            }

            val hasActivePlus = plusFlag ||
                    premiumFlag ||
                    loginPlusExpiryValue > now ||
                    (entryFeePaidFlag && entryFeePaidExpiry > now) ||
                    nextRenewalValue > now ||
                    premiumExpiryValue > now ||
                    subscriptionStatusValue.equals("active", ignoreCase = true)

            isPremium = premiumFlag || subscriptionStatusValue.equals("active", ignoreCase = true)
            isPlus = hasActivePlus

            if (!hasActivePlus) {
                Toast
                    .makeText(context, "Upgrade to Plus to see who liked you", Toast.LENGTH_SHORT)
                    .show()
                likedUsers.clear()
                blockedIds.clear()
                myMatchIds.clear()
                isLoading = false
                navController.popBackStack()
                return@LaunchedEffect
            }
            val blockedSnapshot = blockedRef.get().await()
            blockedIds.clear()
            blockedSnapshot.children.forEach { it.key?.let(blockedIds::add) }

            val matchesSnapshot = matchesRef.get().await()
            myMatchIds.clear()
            matchesSnapshot.children.forEach { data -> data.key?.let(myMatchIds::add) }

            val likesSnapshot = likesReceivedRef.get().await()
            val userIds = likesSnapshot.children.mapNotNull { it.key }

            val fetchedProfiles = coroutineScope {
                userIds.map { userId ->
                    async {
                        val profileSnapshot = usersRef.child(userId).get().await()
                        if (UserDeletionCache.isDeleted(FirebaseRefs.db, userId, profileSnapshot)) {
                            return@async null
                        }
                        val profile = profileSnapshot.getValue(Profile::class.java)?.let { loaded ->
                            if (loaded.userId.isBlank()) loaded.copy(userId = userId) else loaded
                        }
                        if (profile != null && profile.username.isBlank()) {
                            runCatching { likesReceivedRef.child(userId).removeValue().await() }
                            UserDeletionCache.markDeleted(userId)
                            return@async null
                        }
                        profile?.let { UserDeletionCache.markActive(userId) }
                        profile
                    }
                }.awaitAll()
                    .filterNotNull()
                    .filter { profile ->
                        !myMatchIds.contains(profile.userId) &&
                                !profile.matches.contains(currentUserId) &&
                                !blockedIds.contains(profile.userId)
                    }
            }

            likedUsers.clear()
            likedUsers.addAll(fetchedProfiles)
        } catch (e: Exception) {
            isPremium = false
            isPlus = false
            likedUsers.clear()
        } finally {
            isLoading = false
        }
    }

    // For the scroll-to-top feature
    val listState = rememberLazyListState()
    val uiCoroutineScope = rememberCoroutineScope()

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFFF4500))
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.people_who_like_me_title), color = Color.White) },
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
            floatingActionButton = {
                if (likedUsers.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            uiCoroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = Color(0xFFFF4500)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Scroll to top",
                            tint = Color.White
                        )
                    }
                }
            },
            containerColor = Color.Black
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(innerPadding)
            ) {
                if (likedUsers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No one has liked you yet.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        state = listState
                    ) {
                        items(likedUsers) { profile ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable {
                                        navController.navigate("previewUserProfile/${profile.userId}")
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val placeholder = painterResource(R.drawable.local_placeholder)
                                    val localContext = LocalContext.current
                                    val model = run {
                                        val url = profile.profilepicThumbnailUrl ?: profile.profilepicUrl
                                        url?.let {
                                            val pathKey = Uri.parse(it).path
                                            ImageRequest.Builder(localContext)
                                                .data(it)
                                                .diskCacheKey(pathKey)
                                                .memoryCacheKey(pathKey)
                                                .build()
                                        }
                                    }
                                    AsyncImage(
                                        model = model,
                                        contentDescription = "Profile Picture",
                                        placeholder = placeholder,
                                        error = placeholder,
                                        modifier = Modifier.size(50.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column {
                                        val displayName = profile.name.ifBlank { profile.username }
                                        Text(
                                            text = displayName,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = profile.username,
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Likes: ${profile.numberOfUsersWhoSwiped.toInt()}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Show match pop-up if available
            matchPopUpState?.let { (you, them) ->
                MatchPopUp(
                    currentUserProfilePic = you.profilepicUrl.orEmpty(),
                    otherUserProfilePic   = them.profilepicUrl.orEmpty(),
                    onChatClick = {
                        profileViewModel.clearMatchPopUp()
                        navController.navigate("chat/${them.userId}")
                    },
                    onClose = { profileViewModel.clearMatchPopUp() }
                )
            }
        }
    }
}
