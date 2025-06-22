package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubscriptionScreen(navController: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRef = FirebaseRefs.db.getReference("users/$uid")

    var premiumTier by remember { mutableStateOf("Loading...") }
    var expiry by remember { mutableStateOf("Loading...") }

    LaunchedEffect(uid) {
        val snap = userRef.get().await()
        premiumTier = when {
            snap.child("isPremium").getValue(Boolean::class.java) == true -> "Premium"
            snap.child("isPlus").getValue(Boolean::class.java) == true -> "Plus"
            else -> "Free"
        }
        expiry = snap.child("nextRenewal").getValue(Long::class.java)
            ?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "N/A"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage Subscription") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Membership: $premiumTier", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Expires on: $expiry", fontSize = 16.sp)

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    userRef.updateChildren(mapOf("isPlus" to false, "isPremium" to false))
                    Toast.makeText(ctx, "Subscription cancelled", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            ) {
                Text("Cancel Subscription")
            }
        }
    }
}
