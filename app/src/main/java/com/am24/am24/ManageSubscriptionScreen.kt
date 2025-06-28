package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubscriptionScreen(navController: NavController) {
    val ctx = LocalContext.current
    val functions = Firebase.functions("asia-south1")
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRef = FirebaseRefs.db.getReference("users/$uid")

    var premiumTier   by remember { mutableStateOf("Loading...") }
    var expiry        by remember { mutableStateOf("Loading...") }
    var subscriptionId by remember { mutableStateOf<String?>(null) }
    var isPlus        by remember { mutableStateOf(false) }
    var isPremium     by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        val snap = userRef.get().await()
        isPlus        = snap.child("isPlus").getValue(Boolean::class.java)  ?: false
        isPremium     = snap.child("isPremium").getValue(Boolean::class.java) ?: false
        premiumTier   = when {
            isPremium -> "Premium"
            isPlus    -> "Plus"
            else      -> "Free"
        }
        expiry = snap.child("nextRenewal").getValue(Long::class.java)
            ?.let { DateFormat.getDateInstance().format(Date(it)) }
            ?: "N/A"
        subscriptionId = snap.child("subscription").child("id")
            .getValue(String::class.java)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage Plan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Membership: $premiumTier", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Expires on: $expiry",   fontSize = 16.sp)

            val featureList = when {
                isPremium -> PREMIUM_FEATURES
                isPlus    -> PLUS_FEATURES
                else      -> emptyList()
            }
            if (featureList.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Your Benefits:", fontWeight = FontWeight.SemiBold)
                featureList.forEach { bullet ->
                    Text(
                        "• $bullet",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            when {
                // ─── one-time PLUS ───
                subscriptionId.isNullOrBlank() && isPlus -> {
                    // 2) One-time
                    OutlinedButton(
                        onClick = { navController.navigate("upgradeLanding") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Plan", color = Color(0xFFFF6600))
                    }
                }

                // ─── one-time PREMIUM ───
                subscriptionId.isNullOrBlank() && isPremium -> {
                    // 2) One-time
                    OutlinedButton(
                        onClick = { navController.navigate("upgradeLanding") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Plan", color = Color(0xFFFF6600))
                    }
                }

                // ─── recurring subscription ───
                !subscriptionId.isNullOrBlank() -> {
                    Button(
                        onClick = {
                            functions
                                .getHttpsCallable("cancelKupidxPlusSub")
                                .call()
                                .addOnSuccessListener {
                                    Toast.makeText(ctx, "Subscription cancelled", Toast.LENGTH_LONG).show()
                                    navController.popBackStack()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(ctx, "Cancel failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6600))
                    ) {
                        Text("Cancel Subscription")
                    }
                }
            }
        }
    }
}
