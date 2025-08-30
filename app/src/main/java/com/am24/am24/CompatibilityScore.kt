package com.am24.am24.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun CompactCompatBadge(
    percent: Double,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    val p = percent.roundToInt().coerceIn(0, 100)
    val emoji = when {
        p >= 90 -> "💞"
        p >= 75 -> "💖"
        p >= 60 -> "💘"
        p >= 45 -> "💛"
        p >= 30 -> "🧡"
        p >= 15 -> "💙"
        else    -> "🤍"
    }
    Text(
        text = "$emoji $p%",
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
fun CompatibilityMeter(
    percent: Double,
    modifier: Modifier = Modifier,
) {
    // clamp 0..100
    val pct = percent.coerceIn(0.0, 100.0)
    val progress = (pct / 100f).toFloat()

    // pick emoji + bar color based on ranges
    val (emoji, barColor) = when {
        pct >= 80  -> "🔥" to Color(0xFFEF5350) // red
        pct >= 60  -> "❤️" to Color(0xFFFF7043) // orange
        pct >= 40  -> "🙂" to Color(0xFFFFCA28) // amber
        pct >= 20  -> "😐" to Color(0xFF29B6F6) // light blue
        else       -> "💔" to Color(0xFF78909C) // grey blue
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Emoji + Percentage label
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = "${pct.roundToInt()}%",
                color = Color.White,
                fontSize = 18.sp,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = Color.DarkGray.copy(alpha = 0.3f)
        )
    }
}