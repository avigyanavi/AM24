@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

/* Android & Compose */
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ServerValue.increment
import java.util.Locale
import androidx.core.content.getSystemService

/* ───────── Subscription screen ───────── */

@Composable
fun SubscriptionScreen(navController: NavController) {

    /* ───────── Early country split ───────── */
    val ctx      = LocalContext.current
    val inIndia  = remember { isProbablyInIndia(ctx) }

    if (!inIndia) {
        // 👉 Foreign user: jump straight into the Pay-Pal WebView route
        LaunchedEffect(Unit) {
            navController.navigate("paypal")        // ← make sure this route exists
        }
        // We return so no UPI UI is even composed
        return
    }
    /* Firebase handles */
    val uid       = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db        = FirebaseDatabase.getInstance()
    val userRoot  = db.getReference("users/$uid")
    val plusRef     = userRoot.child("isPlus")
    val premiumRef  = userRoot.child("isPremium")

    /* 1️⃣  Listen for either flag so we leave once subscribed */
    var isPlusState    by remember { mutableStateOf<Boolean?>(null) }
    var isPremiumState by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(plusRef, premiumRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                isPlusState    = s.child("isPlus").getValue(Boolean::class.java)
                isPremiumState = s.child("isPremium").getValue(Boolean::class.java)
            }
            override fun onCancelled(error: DatabaseError) { /* ignore */ }
        }
        userRoot.addValueEventListener(listener)
        onDispose { userRoot.removeEventListener(listener) }
    }

    /* 2️⃣  Auto-exit if already subscribed */
    when {
        isPremiumState == null || isPlusState == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00BF63))
            }
            return
        }
        isPremiumState == true || isPlusState == true -> {
            LaunchedEffect(Unit) { navController.popBackStack() }
            return
        }
    }

    /* 3️⃣  Deep links */
    val PLUS_UPI_LINK = "upi://pay?ver=01&mode=19" +
            "&pa=mukherjeeallian718511.rzp@icici" +
            "&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED" +
            "&tr=RZPQgdtoAmnwI2kJSqrv2" +
            "&cu=INR&mc=7372&qrMedium=04" +
            "&tn=PaymenttoMUKHERJEEALLIANCESINFOTECHPRIVATELIMITED" +
            "&am=500.00"

    val PREMIUM_UPI_LINK = "upi://pay?ver=01&mode=19" +
            "&pa=mukherjeeallian718511.rzp@icici" +
            "&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED" +
            "&tr=RZPQgj6iMP00wbDP9qrv2" +          // ← new transaction ID
            "&cu=INR&mc=7372&qrMedium=04" +
            "&tn=PaymenttoMUKHERJEEALLIANCESINFOTECHPRIVATELIMITED" +
            "&am=1000.00"

    val plusIntent     = remember { Intent(Intent.ACTION_VIEW, Uri.parse(PLUS_UPI_LINK)) }
    val premiumIntent  = remember { Intent(Intent.ACTION_VIEW, Uri.parse(PREMIUM_UPI_LINK)) }

    /* 4️⃣  Launchers */
    val plusLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resp = result.data?.getStringExtra("response")
            if (parseUpiStatus(resp) in listOf("SUCCESS", "SUBMITTED")) {
                userRoot.updateChildren(
                    mapOf(
                        "isPlus"              to true,
                        "availableBoosts"     to increment(3L),
                        "availableCompliments" to increment(3L)
                    )
                )
            } else {
                Toast.makeText(ctx, "Payment failed or cancelled", Toast.LENGTH_LONG).show()
            }
        } else Toast.makeText(ctx, "Payment cancelled", Toast.LENGTH_SHORT).show()
    }

    val premiumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resp = result.data?.getStringExtra("response")
            if (parseUpiStatus(resp) in listOf("SUCCESS", "SUBMITTED")) {
                userRoot.updateChildren(
                    mapOf(
                        "isPremium"           to true,
                        "availableBoosts"     to increment(5L),
                        "availableCompliments" to increment(5L)
                    )
                )
            } else {
                Toast.makeText(ctx, "Payment failed or cancelled", Toast.LENGTH_LONG).show()
            }
        } else Toast.makeText(ctx, "Payment cancelled", Toast.LENGTH_SHORT).show()
    }

    /* 5️⃣  UI */
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Text("Choose your plan", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { plusLauncher.launch(plusIntent) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Get Plus — ₹500", fontSize = 16.sp, color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { premiumLauncher.launch(premiumIntent) },
            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BF63)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Get Premium — ₹1 000", fontSize = 16.sp, color = Color.White)
        }

        Spacer(Modifier.height(24.dp))

        TextButton(onClick = { navController.popBackStack() }) {
            Text("Cancel", color = Color(0xFF00BF63))
        }
    }
}

/* Helper – parses the UPI callback string */
fun parseUpiStatus(raw: String?): String =
    raw
        ?.split('&')
        ?.mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0].uppercase() to parts[1] else null
        }
        ?.toMap()
        ?.get("STATUS")
        ?: "UNKNOWN"


fun isProbablyInIndia(ctx: Context): Boolean {
    // ① SIM / network country if available
    val telephony = ctx.getSystemService<TelephonyManager>()
    val simIso    = telephony?.simCountryIso ?: ""
    val netIso    = telephony?.networkCountryIso ?: ""

    // ② Device UI locale fallback
    val localeIso = Locale.getDefault().country

    return listOf(simIso, netIso, localeIso).any { it.equals("IN", true) }
}


/* ───────── PayPal Smart-Button WebView (unchanged) ───────── */

@Composable
fun PayPalWebView(navController: NavController) {
    val ctx        = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val pageUrl    = "https://kupidx.com/paypal_subscribe.html"

    AndroidView(
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode  = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webChromeClient = WebChromeClient()
                webViewClient   = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false
                        if (uri.scheme == ctx.packageName) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            navController.popBackStack()
                            return true
                        }
                        if (uri.scheme == "http" || uri.scheme == "https") return false
                        return try {
                            uriHandler.openUri(uri.toString())
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
                loadUrl(pageUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
