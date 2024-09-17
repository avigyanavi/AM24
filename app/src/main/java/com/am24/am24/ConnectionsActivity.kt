package com.am24.am24

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

class ConnectionsActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            AppTheme {
                ConnectionsScreen(auth, db)
            }
        }
    }
}

@Composable
fun ConnectionsScreen(auth: FirebaseAuth, db: DatabaseReference) {
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
        Text(text = "Your Connections", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // List of people you deem as connections
        Text(text = "How You Deem Others", style = MaterialTheme.typography.headlineMedium)
        profile.connectionsDeemedByMe.forEach { (uid, connectionLevel) ->
            Text(text = "$uid: $connectionLevel", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // List of people and how they see you
        Text(text = "How Others Deem You", style = MaterialTheme.typography.headlineMedium)
        profile.connectionsDeemedByOthers.forEach { (uid, connectionLevel) ->
            Text(text = "$uid sees you as $connectionLevel", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
