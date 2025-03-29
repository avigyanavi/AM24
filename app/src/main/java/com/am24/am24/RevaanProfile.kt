package com.am24.am24.profiles

import AI
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.am24.am24.ModelingState
import com.am24.am24.ui.theme.White
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Displays the modeling state using linear progress indicators.
 */
@Composable
fun ModelingStateSliders(modelingState: ModelingState) {
    Card(
        backgroundColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(12.dp),
        elevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Mood sliders
            EmotionSliderRow(Icons.Default.Favorite, "Trust", modelingState.moodLevels.trust)
            Spacer(modifier = Modifier.height(8.dp))
            EmotionSliderRow(Icons.Default.Warning, "Jealousy", modelingState.moodLevels.jealousy)
            Spacer(modifier = Modifier.height(8.dp))
            EmotionSliderRow(Icons.Default.Liquor, "Romantic Passion", modelingState.moodLevels.romantic_passion)
            Spacer(modifier = Modifier.height(8.dp))
            EmotionSliderRow(Icons.Default.SentimentSatisfied, "Satisfaction", modelingState.moodLevels.satisfaction)

            Spacer(modifier = Modifier.height(16.dp))

            // Relationship History sliders
            SliderRowWithIcon(
                icon = Icons.Default.Star,
                label = "Attachment",
                valueText = "${modelingState.relationshipHistory.attachment}",
                progress = modelingState.relationshipHistory.attachment / 100f,
                trackColor = Color(0xFFFF6F00)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SliderRowWithIcon(
                icon = Icons.Default.Visibility,
                label = "Confidence",
                valueText = "${modelingState.relationshipHistory.confidence}",
                progress = modelingState.relationshipHistory.confidence / 100f,
                trackColor = Color(0xFFFF6F00)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SliderRowWithIcon(
                icon = Icons.Default.Work,
                label = "Emotional Depth",
                valueText = "${modelingState.relationshipHistory.emotionalDepth}",
                progress = modelingState.relationshipHistory.emotionalDepth / 100f,
                trackColor = Color(0xFFFF6F00)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Relationship Stage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Relationship",
                    tint = Color(0xFFFF6F00),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Relationship: ${modelingState.relationshipStage}",
                    color = White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun EmotionSliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Int
) {
    val clampedValue = value.coerceIn(0, 100)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFFF6F00),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$label: $clampedValue", color = White, fontWeight = FontWeight.SemiBold)
    }
    Spacer(modifier = Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = clampedValue / 100f,
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = Color(0xFFFF6F00),
        backgroundColor = Color.DarkGray
    )
}

@Composable
fun SliderRowWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    valueText: String,
    progress: Float,
    trackColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = trackColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$label: $valueText", color = White, fontWeight = FontWeight.SemiBold)
    }
    Spacer(modifier = Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = trackColor,
        backgroundColor = Color.DarkGray
    )
}

@Composable
fun FullScreenImageOverlay(
    imageRes: Int,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable { onDismiss() }
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = "Full screen avatar",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
        }
    }
}

/**
 * GenericProfileScreen displays an AI character’s profile.
 * All references to plot events have been removed.
 */
@Composable
fun GenericProfileScreen(
    title: String,
    modelingState: ModelingState,
    avatarRes: Int,
    onNavigateBack: () -> Unit,
    ai: AI,
    userId: String,
    messageCount: Int,
    scrollToMemoryLogs: Boolean = false
) {
    var showFullScreenImage by remember { mutableStateOf(false) }
    val memoryLogState = remember { mutableStateListOf<String>() }
    val lazyListState = rememberLazyListState()

    // Fetch memory logs from aiMemoryLogs/{aiId}
    LaunchedEffect(ai) {
        val dbRef = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/")
            .getReference("aiMemoryLogs")
            .child(ai.name.lowercase() + "Ai") // Match aiId format (e.g., "zaraAi", "kabirAi")

        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                memoryLogState.clear()
                snapshot.children.mapNotNullTo(memoryLogState) { it.getValue(String::class.java) }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GenericProfileScreen", "Failed to load memory logs: ${error.message}")
            }
        })
    }

    // Scroll to memory logs if requested
    LaunchedEffect(scrollToMemoryLogs, memoryLogState) {
        if (scrollToMemoryLogs && memoryLogState.isNotEmpty()) {
            lazyListState.scrollToItem(2)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title's Profile", color = Color.White) },
                backgroundColor = Color.Black,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        },
        backgroundColor = Color.Black
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = avatarRes),
                        contentDescription = "$title Avatar",
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .clickable { showFullScreenImage = true },
                        contentScale = ContentScale.Crop
                    )
                }
            }
            item { ModelingStateSliders(modelingState) }
            item {
                Text(
                    "Recent Memories:",
                    color = Color.White,
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (memoryLogState.isEmpty()) {
                    Text("No memories yet.", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        memoryLogState.forEach { memory ->
                            Text("• $memory", color = Color.LightGray)
                        }
                    }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Messages Sent",
                        tint = Color(0xFFFF6F00),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Messages Sent: $messageCount",
                        color = White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showFullScreenImage) {
        FullScreenImageOverlay(
            imageRes = avatarRes,
            onDismiss = { showFullScreenImage = false }
        )
    }
}