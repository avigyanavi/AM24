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

class OtherUserProfileActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        setContent {
            AppTheme {
                OtherUserProfileScreen(auth, db)
            }
        }
    }
}

@Composable
fun OtherUserProfileScreen(auth: FirebaseAuth, db: DatabaseReference) {
    var otherUserProfile by remember { mutableStateOf(Profile()) }
    var currentUserProfile by remember { mutableStateOf(Profile()) }
    var selectedConnectionLevel by remember { mutableStateOf("Acquaintance") }

    val connectionLevels = listOf("Acquaintance", "Friend", "Best Friend", "Crush", "Hookup", "Handler", "Wife", "Husband", "Ex", "Sneaky Link")

    // Fetch the current user's profile and the other user's profile
    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid ?: return@LaunchedEffect
        db.child("users").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentUserProfile = snapshot.getValue(Profile::class.java) ?: Profile()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })

        // Simulate getting the other user's profile based on an ID
        val otherUserId = "someOtherUserId"  // Replace with the actual user ID
        db.child("users").child(otherUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                otherUserProfile = snapshot.getValue(Profile::class.java) ?: Profile()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Username: ${otherUserProfile.username}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // Exposed Dropdown for selecting the connection level
        CustomExposedDropdownMenuOne(
            selectedItem = selectedConnectionLevel,
            items = connectionLevels,
            label = "Connection Level",
            onItemSelected = { selectedConnectionLevel = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            // Update connection level in Firebase
            val userId = auth.currentUser?.uid ?: return@Button
            db.child("users").child(userId).child("connectionsDeemedByMe").child(otherUserProfile.uid).setValue(selectedConnectionLevel)
        }) {
            Text(text = "Set Connection Level")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExposedDropdownMenuOne(
    selectedItem: String,
    items: List<String>,
    label: String,
    onItemSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOptionText by remember { mutableStateOf(selectedItem) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            readOnly = true,
            value = selectedOptionText,
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor() // Ensures correct placement of the dropdown
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption) },
                    onClick = {
                        selectedOptionText = selectionOption
                        onItemSelected(selectionOption)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
