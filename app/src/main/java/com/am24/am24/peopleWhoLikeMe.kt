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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleWhoLikeMeScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel(),
    currentUserId: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var upgradePromptShown by remember { mutableStateOf(false) }

    val matchPopUpState by profileViewModel.matchPopUpState.collectAsState()
    val sessionReady by SessionDataRepository.sessionReady.collectAsState()
    val blockedIds by SessionDataRepository.blockedUserIds.collectAsState()
    val likesMap by SessionDataRepository.likesReceived.collectAsState()
    val matchIds by SessionDataRepository.matchIds.collectAsState()
    val plusFlag by profileViewModel.isPlus.collectAsState()
    val premiumFlag by profileViewModel.isPremium.collectAsState()
    val loginPlusExpiry by profileViewModel.loginPlusExpiry.collectAsState()
    val entryFeePaidAt by profileViewModel.entryFeePaidAt.collectAsState()
    val entryFeePaid by profileViewModel.isEntryFeePaid.collectAsState()
    val premiumExpiry by profileViewModel.premiumExpiryDate.collectAsState()
    val subscriptionStatus by profileViewModel.subscriptionStatus.collectAsState()
    val nextRenewal by profileViewModel.nextRenewal.collectAsState()

    val likedUsers = remember { mutableStateListOf<UserSummary>() }

    LaunchedEffect(Unit) {
        if (currentUserId.isNotBlank()) {
            SessionDataRepository.start(currentUserId)
            profileViewModel.fetchCurrentUserProfile()
        }
    }

    LaunchedEffect(
        sessionReady,
        likesMap,
        blockedIds,
        matchIds,
        plusFlag,
        premiumFlag,
        loginPlusExpiry,
        entryFeePaid,
        entryFeePaidAt,
        premiumExpiry,
        subscriptionStatus,
        nextRenewal
    ) {
        if (currentUserId.isBlank() || !sessionReady) {
            isLoading = currentUserId.isNotBlank()
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        val entryFeePaidExpiry = if (entryFeePaid && entryFeePaidAt > 0L) {
            entryFeePaidAt + TimeUnit.DAYS.toMillis(30)
        } else {
            0L
        }
        val hasActivePlus = premiumFlag ||
                plusFlag ||
                loginPlusExpiry > now ||
                (entryFeePaid && entryFeePaidExpiry > now) ||
                (nextRenewal ?: 0L) > now ||
                (premiumExpiry ?: 0L) > now ||
                subscriptionStatus?.equals("active", ignoreCase = true) == true

        if (!hasActivePlus) {
            if (!upgradePromptShown) {
                upgradePromptShown = true
                Toast
                    .makeText(context, "Upgrade to Plus to see who liked you", Toast.LENGTH_SHORT)
                    .show()
                likedUsers.clear()
                navController.popBackStack()
            }
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true

        if (likesMap.isEmpty()) {
            likedUsers.clear()
            isLoading = false
            return@LaunchedEffect
        }

        val sortedProfiles = withContext(Dispatchers.IO) {
            val activeLikeIds = mutableListOf<String>()
            likesMap.keys.forEach { userId ->
                if (userId.isBlank()) return@forEach

                val isDeleted = runCatching {
                    UserDeletionCache.isDeleted(FirebaseRefs.db, userId)
                }.getOrElse { false }

                if (isDeleted) {
                    runCatching {
                        FirebaseRefs.db
                            .getReference("likesReceived/$currentUserId/$userId")
                            .removeValue()
                            .await()
                    }
                    UserDeletionCache.markDeleted(userId)
                } else {
                    activeLikeIds += userId
                }
            }

            if (activeLikeIds.isEmpty()) {
                return@withContext emptyList<UserSummary>()
            }

            val fetched = UserSummaryCache.getSummaries(activeLikeIds)
            val keepers = mutableListOf<UserSummary>()
            fetched.forEach { (userId, summary) ->
                if (summary.username.isBlank()) {
                    runCatching {
                        FirebaseRefs.db.getReference("likesReceived/$currentUserId/$userId").removeValue().await()
                    }
                    UserDeletionCache.markDeleted(userId)
                } else {
                    UserDeletionCache.markActive(userId)
                    if (!matchIds.contains(userId) && !blockedIds.contains(userId)) {
                        keepers += summary
                    }
                }
            }
            keepers.sortedWith(
                compareByDescending<UserSummary> { profile ->
                    profile.likesReceivedCount
                }.thenByDescending { profile ->
                    likesMap[profile.userId] ?: 0L
                }
            )
        }
        likedUsers.clear()
        likedUsers.addAll(sortedProfiles)
        isLoading = false
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
                                        val displayName = profile.displayName
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
                                            text = "Total likes: ${profile.likesReceivedCount}",
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
