@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
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

    // Fetch notifications (both read and unread)
    LaunchedEffect(currentUserId) {
        profileViewModel.getNotifications(
            userId = currentUserId,
            onSuccess = {
                notifications.clear()
                notifications.addAll(it)
            },
            onFailure = {
                // Handle error if needed
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (notifications.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    var hasBeenSeen by remember { mutableStateOf(false) } // track if already marked as seen

    LaunchedEffect(notification.senderId) {
        if (notification.senderUsername.isEmpty()) {
            profileViewModel.fetchUsernameById(notification.senderId, { fetchedUsername ->
                dynamicUsername.value = fetchedUsername
            }, {
                dynamicUsername.value = "Unknown"
            })
        }
    }

    // Use the background color to show read vs unread notifications
    val backgroundColor = if (notification.isRead == "true") Color.Black else Color.DarkGray

    // Wrap the card in a Box to detect when it becomes visible
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                // Since LazyColumn only composes visible items, this indicates the notification is on screen.
                if (!hasBeenSeen) {
                    hasBeenSeen = true
                    if (notification.isRead != "true") {
                        onRead()
                    }
                }
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // Even if the notification is already marked as read when seen,
                    // you can still perform navigation or additional actions on click.
                    when (notification.type) {
                        "new_like" -> {
                            navController.navigate("peopleWhoLikedMe")
                        }
                        "new_match" -> {
                            navController.navigate("dms")
                        }
                        else -> { /* no navigation */ }
                    }
                    onAction() // If any additional action is needed on click
                },
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notification",
                    tint = Color(0xFF00bf63),
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.message.replace("senderUsername", dynamicUsername.value),
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
