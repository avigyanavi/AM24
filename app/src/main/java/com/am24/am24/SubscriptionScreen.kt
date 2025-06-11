package com.am24.am24

/* Android & Compose */
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import kotlinx.coroutines.tasks.await
import java.util.Locale


@Composable
fun SubscriptionScreen(navController: NavController) {
    val ctx   = LocalContext.current
    val user  = FirebaseAuth.getInstance().currentUser ?: return
    val uid   = user.uid

    // 0) Live‐listen for isPremium
    var isPremium by remember { mutableStateOf<Boolean?>(null) }
    val premiumRef = FirebaseRefs.db
        .getReference("users")
        .child(uid)
        .child("isPremium")

    DisposableEffect(premiumRef) {
        val listener = object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                isPremium = snap.getValue(Boolean::class.java) ?: false
            }
            override fun onCancelled(err: DatabaseError) {
                isPremium = false
            }
        }
        premiumRef.addValueEventListener(listener)
        onDispose { premiumRef.removeEventListener(listener) }
    }

    // 1) Already premium? pop back
    if (isPremium == true) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
        return
    } else if (isPremium == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00bf63))
        }
        return
    }

    // 2) Not premium → show payment buttons
    val tm             = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val simCountry     = tm.simCountryIso
    val networkCountry = tm.networkCountryIso
    val localeCountry  = Locale.getDefault().country
    val countryCode = when {
        simCountry.isNotBlank()     -> simCountry
        networkCountry.isNotBlank() -> networkCountry
        else                         -> localeCountry
    }.uppercase(Locale.ROOT)
    val usePaypal = countryCode in setOf("US","CA","AU","GB")

    // razorpay short link (no callback)
    val razorpayUrl = "https://rzp.io/rzp/x1zwA1qz"

    // UPI deep-link unchanged
    val upiLink = "upi://pay?ver=01&mode=19&pa=mukherjeeallian718511.rzp@icici" +
            "&pn=MUKHERJEEALLIANCESINFOTECHPRIVATELIMITED" +
            "&tr=RZPQf5yHb5NdEjERFqrv2&cu=INR&mc=7372&qrMedium=04" +
            "&tn=PaymenttoMUKHERJEEALLIANCESINFOTECHPRIVATELIMITED" +
            "&am=29.00"

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Text(
            "Upgrade to Premium",
            color      = Color(0xFF00bf63),
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text("Subscribe once • auto-renew yearly", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))

        if (usePaypal) {
            Button(
                onClick  = { navController.navigate("paypal_web") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Text("Pay with PayPal – \$3.00", color = Color.White, fontSize = 16.sp)
            }
        } else {
            Button(
                onClick  = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(razorpayUrl))
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Text("Pay with Razorpay – ₹29.00", color = Color.White, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick  = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(upiLink))
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF005CDB))
            ) {
                Text("Pay via UPI – ₹29.00", color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { navController.popBackStack() }) {
            Text("Cancel", color = Color(0xFF00bf63), fontSize = 16.sp)
        }
    }
}
/* ─── WebView screen for Smart Button ─── */
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
