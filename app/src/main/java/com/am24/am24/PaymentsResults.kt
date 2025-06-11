package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun PaymentResultScreen(
    status: String,
    navController: NavController,
    onPaymentSuccess: () -> Unit = {}
) {
    // If payment succeeded, invoke success callback (e.g. update Firestore)
    LaunchedEffect(status) {
        if (status == "paid") {
            onPaymentSuccess()
        }
    }

    val isSuccess = status == "paid"
    val icon = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Error
    val tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336)
    val message = if (isSuccess) "🎉 Payment successful!" else "❌ Payment failed. Please try again."
    val buttonText = if (isSuccess) "Continue" else "Try Again"
    val targetRoute = if (isSuccess) "settings" else "subscription"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                navController.navigate(targetRoute) {
                    popUpTo(targetRoute) { inclusive = true }
                }
            }
        ) {
            Text(text = buttonText)
        }
    }
}
