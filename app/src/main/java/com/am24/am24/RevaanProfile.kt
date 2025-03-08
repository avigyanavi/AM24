package com.am24.am24.profiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Visibility
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
import com.am24.am24.R
import com.am24.am24.ui.theme.White

/**
 * Displays the modeling state of the AI using linear progress indicators,
 * each with an icon, label, and progress bar.
 *
 * Note: For "Overall Mood", the raw composite score (which normally ranges from -200 to +200)
 * is clamped so that 0 maps to 0% progress and 200 maps to 100%.
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

            // 1) Overall Mood (Composite)
            val composite = modelingState.moodLevels.compositeScore()
            // Clamp negative values to 0 and positive above 200 to 200
            val clampedMood = composite.coerceIn(0, 200)
            val normalizedMood = clampedMood / 200f

            SliderRowWithIcon(
                icon = Icons.Default.Mood,
                label = "Overall Mood",
                valueText = "$composite",
                progress = normalizedMood,
                trackColor = Color(0xFFFF6F00)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 2) Career Progress
            val careerProgress = modelingState.careerProgress.coerceIn(0, 100)
            SliderRowWithIcon(
                icon = Icons.Default.Work,
                label = "Career Progress",
                valueText = "$careerProgress",
                progress = careerProgress / 100f,
                trackColor = Color(0xFFFF6F00)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 3) External Attention
            val externalAttention = modelingState.externalAttention.coerceIn(0, 100)
            SliderRowWithIcon(
                icon = Icons.Default.Visibility,
                label = "External Attention",
                valueText = "$externalAttention",
                progress = externalAttention / 100f,
                trackColor = Color(0xFFFF6F00)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 4) Focus on User
            val focus = modelingState.focusOnUser.coerceIn(0, 100)
            SliderRowWithIcon(
                icon = Icons.Default.Person,
                label = "Focus on User",
                valueText = "$focus",
                progress = focus / 100f,
                trackColor = Color(0xFFFF6F00)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 5) Jealousy
            val jealousy = modelingState.jealousyLevel.coerceIn(0, 100)
            SliderRowWithIcon(
                icon = Icons.Default.Favorite,
                label = "Jealousy",
                valueText = "$jealousy",
                progress = jealousy / 100f,
                trackColor = Color(0xFFFF6F00)
            )
        }
    }
}

/**
 * A helper composable that displays an icon, a label with a numeric value,
 * and a linear progress indicator below.
 */
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
        Text(
            text = "$label: $valueText",
            color = White,
            fontWeight = FontWeight.SemiBold
        )
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

/**
 * A full‑screen overlay for displaying an image.
 * Tapping anywhere dismisses the overlay.
 */
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
 * Rhea Profile Screen: Displays Rhea's circular profile picture (tappable for full‑screen view),
 * the modeling state sliders, and the recent memories.
 * (AI posts logic has been removed.)
 */
@Composable
fun RheaProfileScreen(
    modelingState: ModelingState,
    memoryLog: List<String>,
    onNavigateBack: () -> Unit,
    // Use the local drawable resource for Rhea's avatar
    rheaAvatarRes: Int = R.drawable.rhea_avatar
) {
    var showFullScreenImage by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rhea's Profile", color = Color.White) },
                backgroundColor = Color.Black,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        backgroundColor = Color.Black
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1) AI Profile Picture as a circular image
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = rheaAvatarRes),
                        contentDescription = "Rhea Avatar",
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .clickable { showFullScreenImage = true },
                        contentScale = ContentScale.Crop
                    )
                }
            }
            // 2) Modeling State Sliders
            item {
                ModelingStateSliders(modelingState)
            }
            // 3) Memory Log
            item {
                Text(
                    text = "Recent Memories:",
                    color = Color.White,
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (memoryLog.isEmpty()) {
                    Text(
                        text = "No memories yet.",
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        memoryLog.forEach { memory ->
                            Text("• $memory", color = Color.LightGray)
                        }
                    }
                }
            }
            // 4) Rhea's Description (added AFTER the memories)
            item {
                // Example realistic background for Rhea
                val rheaDescription = """
                    Rhea is a 22-year-old cricket player from Lake Gardens in South Kolkata. 
                    She studied at Modern High School for Girls before pursuing Sports Management 
                    at the University of Calcutta. Known for her fierce competitiveness on the pitch, 
                    she also has a lively social circle off the field. Although quick to show jealousy, 
                    Rhea is deeply passionate about her teammates and thrives on the rush of intense matches.
                """.trimIndent()

                Text(
                    text = rheaDescription,
                    color = Color.White,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
    if (showFullScreenImage) {
        // Full screen overlay for avatar image
        FullScreenImageOverlay(
            imageRes = rheaAvatarRes,
            onDismiss = { showFullScreenImage = false }
        )
    }
}
/**
 * Revaan Profile Screen: Displays Revaan's circular profile picture (tappable for full‑screen view),
 * the modeling state sliders, and the recent memories.
 */
@Composable
fun RevaanProfileScreen(
    modelingState: ModelingState,
    memoryLog: List<String>,
    onNavigateBack: () -> Unit,
    // Use the local drawable resource for Revaan's avatar
    revaanAvatarRes: Int = R.drawable.revaan_avatar3
) {
    var showFullScreenImage by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Revaan's Profile", color = Color.White) },
                backgroundColor = Color.Black,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        backgroundColor = Color.Black
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1) AI Profile Picture as a circular image
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = revaanAvatarRes),
                        contentDescription = "Revaan Avatar",
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .clickable { showFullScreenImage = true },
                        contentScale = ContentScale.Crop
                    )
                }
            }
            // 2) Modeling State Sliders
            item {
                ModelingStateSliders(modelingState)
            }
            // 3) Memory Log
            item {
                Text(
                    text = "Recent Memories:",
                    color = Color.White,
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (memoryLog.isEmpty()) {
                    Text(
                        text = "No memories yet.",
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        memoryLog.forEach { memory ->
                            Text("• $memory", color = Color.LightGray)
                        }
                    }
                }
            }
            // 4) Rhea's Description (added AFTER the memories)
            item {
                // Example realistic background for Rhea
                val rheaDescription = """
        Revaan is a 27-year-old entrepreneur who runs his own nightclub 
        in the bustling Park Street area of Kolkata. Born and raised in Ballygunge, 
        he studied Commerce at St. Xavier’s College, where he discovered his knack 
        for socializing and event planning. After college, he launched 
        his first lounge—now one of the city's popular nightlife spots. 
        Behind his charming, laid-back demeanor lies an ambitious streak, 
        a hint of jealousy if overshadowed, and an endless appetite for excitement. 
        Revaan’s life is a mix of business dealings, late-night parties, 
        and nurturing a deeper connection with those who dare to keep up with his pace.
                """.trimIndent()

                Text(
                    text = rheaDescription,
                    color = Color.White,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
    if (showFullScreenImage) {
        FullScreenImageOverlay(
            imageRes = revaanAvatarRes,
            onDismiss = { showFullScreenImage = false }
        )
    }
}
