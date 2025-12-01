@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.res.stringResource

@Composable
fun NotificationsScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // Single ActiveNotificationsView to display all notifications with visual distinction for read/unread
    ActiveNotificationsView(currentUserId, profileViewModel, navController)
}

@Composable
fun ActiveNotificationsView(
    currentUserId: String,
    profileViewModel: ProfileViewModel,
    navController: NavController
) {
    val notifications = remember { mutableStateListOf<Notification>() }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch notifications (both read and unread)
    LaunchedEffect(currentUserId) {
        isLoading = true
        profileViewModel.getNotifications(
            userId = currentUserId,
            onSuccess = { fetched ->
                notifications.clear()
                notifications.addAll(fetched)
                isLoading = false

                // NEW: reset unread counter AND mark each notification row as read
                try {
                    val updates = HashMap<String, Any?>()

                    // reset badge
                    updates["users/$currentUserId/notifUnreadCount"] = 0

                    // mark each notification row as read in DB (only if not already read)
                    fetched.forEach { n ->
                        if (n.isRead != "true") {
                            updates["notifications/$currentUserId/${n.id}/isRead"] = "true"
                        }
                    }

                    if (updates.isNotEmpty()) {
                        FirebaseRefs.db.reference.updateChildren(updates)
                            .addOnSuccessListener {
                                // optionally update local model so UI updates immediately
                                fetched.forEach { n -> n.isRead = "true" }
                                // reflect in local list
                                notifications.clear()
                                notifications.addAll(fetched)
                            }
                            .addOnFailureListener { e ->
                                // log but don't crash the UI
                                android.util.Log.w("Notifications", "mark-read failed: ${e.message}")
                            }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("Notifications", "mark-read exception: ${e.message}")
                }
            },
            onFailure = {
                // Handle error if needed
                isLoading = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title), color = Color.White) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = KupidxOrange)
                }
            }
        } else if (notifications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No new notifications",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            items(notifications) { notification ->
                NotificationCard(
                    notification = notification,
                    currentUserId = currentUserId,
                    profileViewModel = profileViewModel,
                    navController = navController, // Pass navController
                    onRead = {
                        profileViewModel.markNotificationAsRead(
                            userId = currentUserId,
                            notificationId = notification.id,
                            onSuccess = {
                                // Reflect the change in the UI by updating the background color
                                notification.isRead = "true"
                            },
                            onFailure = { error ->
                                // Handle mark as read failure if needed
                            }
                        )
                    },
                    onAction = {
                        // Define specific action for notification click if needed
                    }
                )
            }
        }
        }
    }
}

@Composable
fun NotificationCard(
    notification: Notification,
    currentUserId: String,
    profileViewModel: ProfileViewModel,
    navController: NavController,
    onRead: () -> Unit,
    onAction: () -> Unit
) {
    val dynamicUsername = remember { mutableStateOf(notification.senderUsername) }
    var seen by remember { mutableStateOf(false) }

    /* 1️⃣  fetch senderUsername, if blank */
    LaunchedEffect(notification.senderId) {
        if (notification.senderUsername.isEmpty()) {
            profileViewModel.fetchUsernameById(
                notification.senderId,
                onSuccess = { dynamicUsername.value = it },
                onFailure = { dynamicUsername.value = "Unknown" }
            )
        }
    }

    /* 2️⃣  icon & nav target per type */
    val (icon, onClickRoute) = when (notification.type) {
        /* ——— POSTS ——— */
        "match_post", "match_checkin" ->
            Icons.Default.Notifications to
                    notification.postId?.let { "post/$it" }          // deep-link

        /* ——— COMMENTS ——— */
        "post_comment" ->
            Icons.Default.ChatBubbleOutline to
                    notification.postId?.let { "post/$it" }

        "comment_upvote", "comment_downvote" ->
            Icons.Default.ChatBubbleOutline to
                    notification.postId?.let { post ->
                        val c  = notification.commentId             // may be null
                        if (c != null) "post/$post/comment/$c" else "post/$post"
                    }
        /* ─── POST-related ─── */
        "chat_message" -> Icons.Default.ChatBubbleOutline to "chat/${notification.senderId}"
        "new_like"       -> Icons.Default.Favorite      to "peopleWhoLikedMe"
        "new_compliment" -> Icons.Default.EmojiEmotions to "peopleWhoLikedMe"          // or a “compliments” inbox
        "streak_plus"    -> Icons.Default.Favorite      to "peopleWhoLikedMe"
        "new_match"      -> Icons.Default.People        to "dms"
        else             -> Icons.Default.Notifications to null                        // fallback
    }

    /* 3️⃣  card visuals */
    val bgColor = if (notification.isRead == "true") Color.Black else Color.DarkGray

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                if (!seen) {
                    seen = true
                    if (notification.isRead != "true") onRead()
                }
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onClickRoute?.let { navController.navigate(it) }
                    onAction()
                },
            colors = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Yellow,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = notification.message
                            .replace("senderUsername", dynamicUsername.value),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Received at: ${formatTimestamp(notification.timestamp)}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
