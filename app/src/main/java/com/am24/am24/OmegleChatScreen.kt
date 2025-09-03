package com.am24.am24

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmegleChatScreen(navController: NavController, chatId: String, otherUserId: String) {
    val context = LocalContext.current
    val dbRef = remember { FirebaseDatabase.getInstance().getReference("omegleChats").child(chatId) }
    var otherName by remember { mutableStateOf("") }
    var otherPhoto by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<OmegleMessage>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(otherUserId) {
        val snap = FirebaseDatabase.getInstance().reference.child("users").child(otherUserId).get().await()
        otherName = snap.child("name").getValue(String::class.java) ?: ""
        otherPhoto = snap.child("profilepicUrl").getValue(String::class.java) ?: ""
    }

    DisposableEffect(chatId) {
        val msgListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(OmegleMessage::class.java) }
                messages.clear(); messages.addAll(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        dbRef.child("messages").addValueEventListener(msgListener)
        val endListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ended = snapshot.getValue(Boolean::class.java) == true
                if (ended) navController.popBackStack()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        dbRef.child("ended").addValueEventListener(endListener)
        onDispose {
            dbRef.child("messages").removeEventListener(msgListener)
            dbRef.child("ended").removeEventListener(endListener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            navController.navigate("previewUserProfile/$otherUserId")
                        }
                    ) {
                        if (otherPhoto.isNotBlank()) {
                            AsyncImage(
                                model = otherPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.local_placeholder),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(otherName)
                    }
                },
                actions = {
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages) { msg ->
                    val isMe = msg.senderId == uid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMe) Color(0xFFFF6F00) else Color.DarkGray,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                msg.text,
                                color = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = { dbRef.child("ended").setValue(true) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.end_chat))
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                IconButton(onClick = {
                    val text = input.text.trim()
                    if (text.isNotEmpty() && uid != null) {
                        val key = dbRef.child("messages").push().key ?: return@IconButton
                        val msg = OmegleMessage(id = key, senderId = uid, text = text, timestamp = System.currentTimeMillis())
                        dbRef.child("messages").child(key).setValue(msg)
                        input = TextFieldValue("")
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = "send")
                }
            }
        }
    }
}

data class OmegleMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)