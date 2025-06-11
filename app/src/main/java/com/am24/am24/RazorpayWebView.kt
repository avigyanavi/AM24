package com.am24.am24

import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController

@Composable
fun RazorpayWebView(navController: NavController, razorpayUrl: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled    = true
                settings.domStorageEnabled    = true
                settings.mixedContentMode     = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        // intercept your deep-link
                        if (url.startsWith("kupidx://payment_callback")) {
                            val status = Uri.parse(url)
                                .getQueryParameter("razorpay_payment_link_status") ?: "unknown"
                            // navigate into PaymentResultScreen
                            navController.navigate("payment_callback?status=$status") {
                                popUpTo("subscription")
                            }
                            return true
                        }
                        return false
                    }
                }
                loadUrl(razorpayUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
