package com.am24.am24.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.am24.am24.R
import com.am24.am24.Tier

private val PLUS_FEATURES = listOf(
    R.string.feature_no_ads,
    R.string.feature_people_liked_me,
    R.string.feature_ai_messages_plus,
    R.string.feature_3_compliments,
)

private val PREMIUM_FEATURES = listOf(
    R.string.feature_ai_messages_premium,
    R.string.feature_unlock_maps,
    R.string.feature_unlimited_swipes,
    R.string.feature_5_compliments,
    R.string.feature_everything_plus
)

@Composable
fun TierCard(
    tier: Tier,
    colour: Color,
    onAuto: () -> Unit

) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colour),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {

            Text(
                tier.name.lowercase().replaceFirstChar(Char::uppercase),
                fontSize = 20.sp,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            val features = when (tier) {
                Tier.PREMIUM -> PREMIUM_FEATURES
                else -> PLUS_FEATURES
            }
            features.forEach { bulletResId ->
                Text(
                    text = "• ${stringResource(bulletResId)}",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAuto, modifier = Modifier.weight(1f)) {
                    Text("Subscribe")
                }
            }
        }
    }
}