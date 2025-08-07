package com.am24.am24.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

import com.am24.am24.Period
import com.am24.am24.R
import com.am24.am24.Tier

private val PLUS_FEATURES = listOf(
    R.string.feature_no_ads,
    R.string.feature_priority_profile,
    R.string.feature_people_liked_me,
    R.string.feature_picture_voice_posts,
    R.string.feature_50_swipes,
    R.string.feature_3_compliments,
    R.string.feature_3_boosts
)

private val PREMIUM_FEATURES = listOf(
    R.string.feature_video_rank_section,
    R.string.feature_unlimited_swipes,
    R.string.feature_5_compliments,
    R.string.feature_5_boosts,
    R.string.feature_performance_metrics_rank,
    R.string.feature_everything_plus
)

@Composable
fun TierCard(
    tier: Tier,
    colour: Color,
    priceWeekly: Int,
    priceMonth: Int,
    priceYear: Int,
    onAuto: () -> Unit,
    onManual: (Period) -> Unit,
    showManual: Boolean
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

                if (showManual) {
                    var expanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val accent = if (tier == Tier.PLUS) Color(0xFFFF6F00) else Color.White
                            Text("Pay once with UPI", color = accent)
                        }

                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("₹$priceWeekly / week") },
                                onClick = { expanded = false; onManual(Period.WEEK) }
                            )
                            DropdownMenuItem(
                                text = { Text("₹$priceMonth / month") },
                                onClick = { expanded = false; onManual(Period.MONTH) }
                            )
                            DropdownMenuItem(
                                text = { Text("₹$priceYear / year") },
                                onClick = { expanded = false; onManual(Period.YEAR) }
                            )
                        }
                    }
                }
            }
        }
    }
}