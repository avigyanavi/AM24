// PeopleWhoLikeMe.kt

package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun PeopleWhoLikeMeScreen(navController: NavController) {
    // Replace this with actual user subscription data
    val isPremiumUser by remember { mutableStateOf(false) } // Hardcoded for now

    if (isPremiumUser) {
        PremiumUserView()
    } else {
        NonPremiumUserView(navController)
    }
}

@Composable
fun PremiumUserView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "People Who Like You",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Add logic to display list of people who liked the user.
        Text(
            text = "Here will be the list of users who liked you.",
            color = Color.Gray,
            fontSize = 16.sp
        )
    }
}

@Composable
fun NonPremiumUserView(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Upgrade to Premium to See Who Likes You!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(
            onClick = {
                // Navigate to Payment Screen or handle the upgrade logic here.
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
        ) {
            Text(text = "Upgrade Now", color = Color.White)
        }
    }
}
