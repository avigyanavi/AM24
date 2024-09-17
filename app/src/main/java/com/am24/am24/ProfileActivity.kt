package com.am24.am24

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.am24.am24.ui.theme.AppTheme

class ProfileActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            AppTheme {
                ProfileScreen(auth, db, onNavigateToConnections = {
                    startActivity(Intent(this, ConnectionsActivity::class.java))  // Navigates to ConnectionsActivity
                })
            }
        }
    }
}

@Composable
fun ProfileScreen(auth: FirebaseAuth, db: DatabaseReference, onNavigateToConnections: () -> Unit) {
    var profile by remember { mutableStateOf(Profile()) }

    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid ?: return@LaunchedEffect
        db.child("users").child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                profile = snapshot.getValue(Profile::class.java) ?: Profile()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Basic profile info
        Text(text = "Username: ${profile.username}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Name: ${profile.name}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // Connections
        Text(text = "Connections: ${profile.connectionsDeemedByMe.size}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onNavigateToConnections) {
            Text(text = "View Connections")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Avg. Rating per Post: ${profile.avgRating}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Quality Metric: ${profile.qualityMetric}", style = MaterialTheme.typography.bodyMedium)
    }
}
