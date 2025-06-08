package com.am24.am24

/* Android & Compose */
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

/* ─── Paywall UI ─── */
@Composable
fun SubscriptionScreen(navController: NavController) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text      = "Upgrade to Premium",
            color     = Color(0xFF00bf63),
            fontSize  = 24.sp,
            fontWeight= FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text     = "Subscribe once • auto-renew yearly",
            color    = Color.White,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = { navController.navigate("paypal_web") },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
        ) {
            Text(
                text     = "Pay with PayPal – \$3.00",
                color    = Color.White,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { navController.popBackStack() }) {
            Text(
                text     = "Cancel",
                color    = Color(0xFF00bf63),
                fontSize = 16.sp
            )
        }
    }
}

/* ─── WebView screen for Smart Button ─── */
@Composable
fun PayPalWebView(navController: NavController) {
    val ctx       = LocalContext.current
    val uriHandler= LocalUriHandler.current
    val pageUrl   = "https://kupidx.com/paypal_subscribe.html"

    AndroidView(
        factory = { c ->
            WebView(c).apply {
                settings.apply {
                    javaScriptEnabled    = true
                    domStorageEnabled    = true
                    mixedContentMode     = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false
                        if (uri.scheme == ctx.packageName) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            navController.popBackStack()
                            return true
                        }
                        // let HTTPS load inside WebView
                        if (uri.scheme == "http" || uri.scheme == "https") return false
                        // else, hand off to system
                        return try { uriHandler.openUri(uri.toString()); true }
                        catch (_:Exception){ false }
                    }
                }

                               loadUrl(pageUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
