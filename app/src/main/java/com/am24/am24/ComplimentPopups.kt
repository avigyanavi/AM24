package com.am24.am24

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Small stack of compliment popups that can be displayed in the DM screen or on the map.
 * The stack is kept intentionally lightweight – callers own the list and simply provide
 * callbacks for the action buttons.
 */
@Composable
fun ComplimentPopupStack(
    items: List<ComplimentWithProfile>,
    modifier: Modifier = Modifier,
    onAccept: (ComplimentWithProfile) -> Unit,
    onReject: (ComplimentWithProfile) -> Unit,
    onOpenProfile: (ComplimentWithProfile) -> Unit
) {
    if (items.isEmpty()) return

    // keep a local copy so we can animate entries gracefully even if the caller swaps lists
    val queue = remember { mutableStateListOf<ComplimentWithProfile>() }
    LaunchedEffect(items) {
        queue.clear()
        queue.addAll(items.take(3)) // only show up to 3 stacked cards at once
    }

    Box(modifier = modifier) {
        queue.forEachIndexed { index, item ->
            val offsetModifier = Modifier
                .padding(bottom = (index * 12).dp)
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = offsetModifier
            ) {
                ComplimentPopupCard(
                    complimentWithProfile = item,
                    onAccept = { onAccept(item) },
                    onReject = { onReject(item) },
                    onOpenProfile = { onOpenProfile(item) }
                )
            }
        }
    }
}

@Composable
fun ComplimentPopupCard(
    complimentWithProfile: ComplimentWithProfile,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val profile = complimentWithProfile.profile
    val compliment = complimentWithProfile.compliment

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = profile.profilepicThumbnailUrl ?: profile.profilepicUrl,
                    contentDescription = profile.username,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name.ifBlank { profile.username },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = compliment.text.ifBlank { "Sent you a compliment" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFA64D),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33222222))
                        .clickable(onClick = onOpenProfile)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("View", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFFF6F00), Color(0xFFFF4500)))
                        )
                        .clickable(onClick = onAccept)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Match", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}