@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    // Firebase references
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val database = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)

    // State variables
    var isPremiumUser by remember { mutableStateOf(false) }
    var premiumExpiryDate by remember { mutableStateOf("") }

    // Fetch premium status
    LaunchedEffect(Unit) {
        database.child("premiumStatus").get().addOnSuccessListener { snapshot ->
            isPremiumUser = snapshot.child("isPremium").getValue(Boolean::class.java) ?: false
            premiumExpiryDate = snapshot.child("expiryDate").getValue(String::class.java) ?: "N/A"
        }
    }

    // Save settings function
    fun deleteAccount() {
        database.removeValue()
        FirebaseAuth.getInstance().currentUser?.delete()
        navController.navigate("login") {
            popUpTo("settings") { inclusive = true }
        }
        Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        content = { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF121212))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Premium Status Section
                item {
                    Text(
                        text = "Premium Status",
                        color = Color(0xFF00bf63),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (isPremiumUser) "Premium Member" else "Free User",
                            color = if (isPremiumUser) Color(0xFFFFD700) else Color.Gray,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isPremiumUser) "Expires on: $premiumExpiryDate" else "Upgrade to unlock premium features",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                // Navigate to payment or subscription screen
                                navController.navigate("subscription")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPremiumUser) Color.Gray else Color(0xFF00bf63)
                            )
                        ) {
                            Text(
                                text = if (isPremiumUser) "Manage Subscription" else "Upgrade to Premium",
                                color = Color.White
                            )
                        }
                    }
                }

                // Account Settings Section
                item {
                    Text(
                        text = "Account Settings",
                        color = Color(0xFF00bf63),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        // Private Account Toggle
                        var privateAccount by remember { mutableStateOf(false) }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Private Account",
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = privateAccount,
                                onCheckedChange = { privateAccount = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00bf63),
                                    uncheckedThumbColor = Color.Gray
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Logout Button
                        Button(
                            onClick = {
                                FirebaseAuth.getInstance().signOut()
                                navController.navigate("login") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(text = "Logout", color = Color.White)
                        }
                    }
                }

                // Danger Zone Section
                item {
                    Text(
                        text = "Danger Zone",
                        color = Color.Red,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        // Delete Account Button
                        Button(
                            onClick = { deleteAccount() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(text = "Delete Account", color = Color.White)
                        }
                    }
                }
            }
        }
    )
}
