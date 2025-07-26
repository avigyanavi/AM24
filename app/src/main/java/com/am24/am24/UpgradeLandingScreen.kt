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
import androidx.compose.runtime.LaunchedEffect
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
import com.am24.am24.ui.TierCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


/** visible to all files */
enum class Period(val label: String) { WEEK("Weekly"), MONTH("Monthly"), YEAR("Yearly") }

/** public key-id used by Razorpay’s Checkout SDK */
const val RZP_KEY_ID_PUBLIC = "rzp_live_DsoxJLeiCw940M"

/* ╔════════════════════════════════════════════════════════════╗ */
/* ║                        ENTRY SCREEN                        ║ */
/* ╚════════════════════════════════════════════════════════════╝ */
@Composable
fun UpgradeLandingScreen(nav: NavController) {

    /* ── local handles (captured by launchOneTimeUpi) ── */
    val ctx    = LocalContext.current
    val uid    = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRoot = FirebaseRefs.db.getReference("users/$uid")
    var userCountry by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uid) {
        userCountry = userRoot.child("country").get().await().getValue(String::class.java)
    }
    val isIndia = CountryUtil.useRazorpay(ctx, userCountry)

    val scope  = rememberCoroutineScope()
    val host   = ctx as? KupidXAppActivity
    val fx     = FirebaseFunctions.getInstance("asia-south1")
    val co     = remember { Checkout().apply { setKeyID(RZP_KEY_ID_PUBLIC) } }
    val db     = FirebaseRefs.db

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
            onAuto = { nav.navigate("subscription") },
            onManual = { p -> launchOneTimeUpi(Tier.PLUS, p) },
            showManual = isIndia                    // 👈 new arg
        )

        TierCard(
            tier = Tier.PREMIUM,
            colour = Color(0xFFFF6F00),
            priceWeekly = 29, priceMonth = 99, priceYear = 999,
            onAuto = { nav.navigate("subscription") },
            onManual = { p -> launchOneTimeUpi(Tier.PREMIUM, p) },
            showManual = isIndia                    // 👈
        )
    }
}