package com.am24.am24

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliciesScreen(navController: NavController) {
    // which policy is currently open?
    var activePolicy by remember { mutableStateOf<Policy?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Policies & Support") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Policy.values().forEach { policy ->
                SettingsSection {
                    SettingsRow(
                        icon = { Icon(policy.icon, null, tint = Color(0xFFFF6F00)) },
                        title = policy.title,
                        showChevron = true
                    ) {
                        activePolicy = policy
                    }
                }
            }
        }
    }

// instead of if (activePolicy != null) { … }
    activePolicy?.let { policy ->            // capture non-null policy

        AlertDialog(
            onDismissRequest = { activePolicy = null },
            title = { Text(policy.title) },
            text = {
                Box(Modifier.height(500.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                loadUrl(policy.url)
                            }
                        },
                        update = { webView ->
                            // this lambda also closes over the captured `policy`, not the nullable state
                            webView.loadUrl(policy.url)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activePolicy = null }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

private enum class Policy(val title: String, val url: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ContactUs("Contact Us",      "https://kupidx.com/contact",     Icons.Default.Call),
    Privacy("Privacy Policy",     "https://kupidx.com/privacy-policy",     Icons.Default.Shield),
    Terms("Terms & Conditions",   "https://kupidx.com/terms-of-conditions",       Icons.Default.Article),
    Refunds("Refund Policy",      "https://kupidx.com/refund-policy",     Icons.Default.ShoppingBag),
    Shipping("Shipping & Delivery","https://kupidx.com/shipping-and-delivery",    Icons.Default.LocalShipping),
    DeleteAccount("Delete Account","https://kupidx.com/deleteme", Icons.Default.Delete)
}
