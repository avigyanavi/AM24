package com.am24.am24

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.razorpay.Checkout
import org.json.JSONObject
/* ───────── project helpers ───────── */
import com.am24.am24.FirebaseRefs             // your existing wrapper
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


/** visible to all files */
enum class Period(val label: String) { WEEK("Weekly"), MONTH("Monthly"), YEAR("Yearly") }

/** public key-id used by Razorpay’s Checkout SDK */
const val RZP_KEY_ID_PUBLIC = "rzp_live_DsoxJLeiCw940M"

private val PLUS_FEATURES = listOf(
    "No ads",
    "Unlock People Who Liked Me and Change Location",
    "Unlock Picture and Voice posts",
    "3 compliments per week",
    "3 boosts per week"
)
private val PREMIUM_FEATURES = listOf(
    "Unlock Video posts",
    "Priority Profile in the dating stack",
    "Unlimited Swipes",
    "5 compliments per week",
    "5 boosts per week",
    "Unlocked Performance Metrics per profile",
    "Everything in Plus"
)

/* ╔════════════════════════════════════════════════════════════╗ */
/* ║                        ENTRY SCREEN                        ║ */
/* ╚════════════════════════════════════════════════════════════╝ */
@Composable
fun UpgradeLandingScreen(nav: NavController) {

    /* ── local handles (captured by launchOneTimeUpi) ── */
    val ctx    = LocalContext.current
    val scope  = rememberCoroutineScope()
    val host   = ctx as? KupidXAppActivity
    val fx     = FirebaseFunctions.getInstance("asia-south1")
    val co     = remember { Checkout().apply { setKeyID(RZP_KEY_ID_PUBLIC) } }
    val db     = FirebaseRefs.db
    val uid    = FirebaseAuth.getInstance().currentUser?.uid ?: return

    /* ── helper moved INSIDE so it sees the locals ── */
    fun launchOneTimeUpi(tier: Tier, period: Period) = scope.launch {
        val price = when (tier to period) {
            Tier.PLUS to Period.WEEK -> 9
            Tier.PLUS to Period.MONTH -> 39
            Tier.PLUS to Period.YEAR -> 399
            Tier.PREMIUM to Period.WEEK -> 29
            Tier.PREMIUM to Period.MONTH -> 99
            else -> 999
        }

        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token
                ?: throw Exception("User not authenticated")

            val res = fx.getHttpsCallable("createManualSubscriptionOrder")
                .call(hashMapOf(
                    "amount" to price,
                    "label" to "${tier.name}_${period.name.lowercase()}"
                )).await().data as Map<*, *>

            val orderId = res["id"] as String
            val keyId = res["key"] as String

            co.open(ctx as Activity, JSONObject().apply {
                put("key", keyId)
                put("order_id", orderId)
                put("name", "Kupidx ${tier.name.lowercase().replaceFirstChar(Char::uppercase)}")
            })

            host?.setPaymentCallbacks(
                onSuccess = {
                    val validityMs = when (period) {
                        Period.WEEK  -> 7L  * 24 * 60 * 60 * 1_000
                        Period.MONTH -> 30L * 24 * 60 * 60 * 1_000
                        Period.YEAR  -> 365L* 24 * 60 * 60 * 1_000
                    }
                    val now = System.currentTimeMillis()
                    val updates = mutableMapOf<String, Any>(
                        "isPlus"       to (tier == Tier.PLUS),
                        "isPremium"    to (tier == Tier.PREMIUM),
                        "nextRenewal"  to (now + validityMs)
                    ).apply {
                        val boosts      = if (tier == Tier.PREMIUM) 5 else 3
                        val compliments = if (tier == Tier.PREMIUM) 5 else 3
                        val swipes      = if (tier == Tier.PREMIUM) Int.MAX_VALUE else 50
                        put("availableBoosts",      boosts)
                        put("availableCompliments", compliments)
                        put("swipesInfo/remainingSwipes", swipes)
                        if (tier == Tier.PREMIUM) put("availableAiMessages", 2)
                    }
                    FirebaseRefs.db.getReference("users/$uid")
                        .updateChildren(updates)

                    Toast.makeText(ctx, "Thanks! Enjoy your perks.", Toast.LENGTH_LONG).show()

                    /* ← NEW: jump to Settings and clear this screen */
                    nav.navigate("settings") {
                        popUpTo("upgradeLanding") { inclusive = true }
                    }
                },
                onError = { msg ->
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                }
            )

        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
        }
    }
    /* ─────────────────────────────────────────────── */

    /* ── UI ───────────────────────────────────────── */
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())      // ← enable scrolling
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Choose your upgrade",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

        TierCard(
            tier = Tier.PLUS,
            colour = Color(0xFF1E1E1E),
            priceWeekly = 9,  priceMonth = 39, priceYear = 399,
            onAuto   = { nav.navigate("subscription") },
            onManual = { p -> launchOneTimeUpi(Tier.PLUS, p) }
        )

        TierCard(
            tier = Tier.PREMIUM,
            colour =             Color(0xFFFF6F00),
            priceWeekly = 29, priceMonth = 99, priceYear = 999,
            onAuto   = { nav.navigate("subscription") },
            onManual = { p -> launchOneTimeUpi(Tier.PREMIUM, p) }
        )
    }
}

/* ╔════════════════════════════════════════════════════════════╗ */
/* ║                      REUSABLE TILE                         ║ */
/* ╚════════════════════════════════════════════════════════════╝ */
@Composable
private fun TierCard(
    tier:        Tier,
    colour:      Color,
    priceWeekly: Int,
    priceMonth:  Int,
    priceYear:   Int,
    onAuto:      ()       -> Unit,
    onManual:    (Period) -> Unit
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = colour),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {

            Text(
                tier.name.lowercase().replaceFirstChar(Char::uppercase),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            val features = when (tier) {
                Tier.PREMIUM -> PREMIUM_FEATURES
                else -> PLUS_FEATURES
            }
            features.forEach { bullet ->
                Text(
                    "• $bullet",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            /* ── buttons row ── */
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                Button(onClick = onAuto, modifier = Modifier.weight(1f)) {
                    Text("Subscribe with Card")
                }

                /* 2 ▸ one-time-payment button + its menu */
                var expanded by remember { mutableStateOf(false) }

                Box(                               // <- this is now the anchor
                    modifier = Modifier.weight(1f) // keep the 50-50 width split
                ) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        val accent = if (tier == Tier.PLUS) Color(0xFFFF6F00) else Color.White
                        Text("Pay once", color = accent)
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