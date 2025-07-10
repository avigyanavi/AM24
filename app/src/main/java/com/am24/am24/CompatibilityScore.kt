package com.am24.am24.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun CompatibilityMeter(
    percent: Double,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFFFF6F00)
) {
    val pct = percent.coerceIn(0.0, 100.0)
    val progress = (pct / 100f).toFloat()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${pct.roundToInt()}%",
            color = Color.White,
            fontSize = 12.sp,
            style = MaterialTheme.typography.bodySmall
        )
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = barColor,
            trackColor = Color.Gray
        )
    }
}