@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24.ui.purchase

/* Android & Compose */
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue.increment

/* ────────────────────────────────────── */
/* 1.  UPI links (ALL pasted verbatim)    */
/* ────────────────────────────────────── */
enum class PurchaseType(val displayName: String, val links: Map<Int, String>) {
    Swipes("Swipes", mapOf(
        5  to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgjYGAINicGve6qrv2&cu=INR&mc=7372&qrMedium=04&tn=Swipes-5&am=10.00",
        10 to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgjaNILnj4LKQqqrv2&cu=INR&mc=7372&qrMedium=04&tn=Swipes-10&am=18.00",
        20 to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgjcrCLtNjHCtqqrv2&cu=INR&mc=7372&qrMedium=04&tn=Swipes-20&am=32.00"
    )),
    Compliments("Compliments", mapOf(
        5  to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgjdzL2lBW0SMDqrv2&cu=INR&mc=7372&qrMedium=04&tn=Compliments-5&am=125.00",
        10 to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgjfwgPpggRKm4qrv2&cu=INR&mc=7372&qrMedium=04&tn=Compliments-10&am=225.00",
        20 to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgji6EACLzWXIiqrv2&cu=INR&mc=7372&qrMedium=04&tn=Compliments-20&am=400.00"
    )),
    Boosts("Boosts", mapOf(
        5  to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgjkz6ziUHsURoqrv2&cu=INR&mc=7372&qrMedium=04&tn=Boosts-5&am=750.00",
        10 to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgjmyHzduzC7n8qrv2&cu=INR&mc=7372&qrMedium=04&tn=Boosts-10&am=1350.00",
        20 to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgjp52HudCEZvzqrv2&cu=INR&mc=7372&qrMedium=04&tn=Boosts-20&am=2400.00"
    )),
    AiMessages("AI messages", mapOf(
        5  to "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPQgkLDAKUFLMdguqrv2&cu=INR&mc=7372&qrMedium=04&tn=AIMsg-5&am=50.00",
        10 to "upi://pay?ver=01&mode=19&pa=rzpzlgmukherjeealliancesinfotechprivatelimited@yesbank&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPYQgkNTHrsRqHYOmqrv2&cu=INR&mc=7372&qrMedium=04&tn=AIMsg-10&am=100.00",
        20 to "upi://pay?ver=01&mode=19&pa=rzpzlgmukherjeealliancesinfotechprivatelimited@yesbank&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED&tr=RZPYQgkOKjPbDGPfRCqrv2&cu=INR&mc=7372&qrMedium=04&tn=AIMsg-20&am=200.00"
    ));

    fun link(qty: Int) = links[qty]
        ?: error("Unsupported qty $qty for $displayName")
}

/* ─────────────────────────── */
/* 2.  simple UI state         */
/* ─────────────────────────── */
private data class UiState(val selectedQty: Int = 5, val isProcessing: Boolean = false)

/* ─────────────────────────── */
/* 3.  Purchase screen         */
/* ─────────────────────────── */
@Composable
fun OneTimePurchaseScreen(
    type: PurchaseType,
    navController: NavController,
    onBack: () -> Unit
) {
    val ctx      = LocalContext.current
    val uid      = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val userRoot = FirebaseDatabase.getInstance().getReference("users/$uid")

    var ui by remember { mutableStateOf(UiState()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        ui = ui.copy(isProcessing = false)
        if (res.resultCode != Activity.RESULT_OK) {
            Toast.makeText(ctx, "Payment cancelled", Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult
        }
        val status = parseUpiStatus(res.data?.getStringExtra("response"))
        if (status !in listOf("SUCCESS", "SUBMITTED")) {
            Toast.makeText(ctx, "Payment failed: $status", Toast.LENGTH_LONG).show(); return@rememberLauncherForActivityResult
        }

        val qty = ui.selectedQty.toLong()
        val updates = when (type) {
            PurchaseType.Swipes      -> mapOf("swipesInfo/remainingSwipes" to increment(qty))
            PurchaseType.Compliments -> mapOf("complimentsLeft"            to increment(qty))
            PurchaseType.Boosts      -> mapOf("availableBoosts"            to increment(qty))
            PurchaseType.AiMessages  -> mapOf("availableAiMessages"        to increment(qty))
        }
        userRoot.updateChildren(updates).addOnCompleteListener {
            Toast.makeText(ctx, "Added $qty ${type.displayName}", Toast.LENGTH_LONG).show()
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Buy ${type.displayName}") },
                colors = centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Select quantity", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(5, 10, 20).forEach { qty ->
                    FilterChip(
                        selected = ui.selectedQty == qty,
                        onClick  = { ui = ui.copy(selectedQty = qty) },
                        label    = { Text(qty.toString()) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF6F00),
                            selectedLabelColor     = Color.White,
                            containerColor         = Color(0xFF2A2A2A),
                            labelColor             = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    ui = ui.copy(isProcessing = true)
                    launcher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(type.link(ui.selectedQty))))
                },
                enabled = !ui.isProcessing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6F00),
                    contentColor   = Color.White
                )
            ) {
                if (ui.isProcessing)
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else
                    Text("Pay via UPI", fontSize = 16.sp)
            }
        }
    }
}

/* ───────── helper ───────── */
fun parseUpiStatus(raw: String?): String =
    raw?.split('&')
        ?.mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0].uppercase() to p[1] } }
        ?.toMap()
        ?.get("STATUS") ?: "UNKNOWN"
