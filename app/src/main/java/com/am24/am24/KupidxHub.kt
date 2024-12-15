// KupidxHub.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.roundToInt

@Composable
fun KupidxHubScreen(navController: NavController) {
    // Fetch all profiles (you need to load from Firebase or ViewModel)
    // For now, we create a placeholder list of online users
    val allProfiles = remember {
        listOf(
            // Sample data. Fill in with real data. Assume "online" means lastActive is recent.
            Profile(userId = "u1", username = "A", name="User A", dob="10/05/1995", averageRating=4.5, vibepoints=7.0, latitude=0.0, longitude=0.0),
            Profile(userId = "u2", username = "B", name="User B", dob="22/11/1990", averageRating=3.2, vibepoints=5.0, latitude=0.0, longitude=0.0),
            Profile(userId = "u3", username = "C", name="User C", dob="01/02/1998", averageRating=4.9, vibepoints=9.0, latitude=0.0, longitude=0.0)
        )
    }

    // Consider all users as online for demo. In reality, filter by lastActive time or a flag.
    val onlineUsers = allProfiles // Adjust to filter only those online

    // State for sorting
    var currentSort by remember { mutableStateOf("rank") }
    var ascending by remember { mutableStateOf(true) }

    val sortedUsers = remember(onlineUsers, currentSort, ascending) {
        // Compute age from dob
        val usersWithExtra = onlineUsers.map { profile ->
            val age = calculateAge(profile.dob)
            // Define "rank" as rating + vibepoints for this demo
            val rank = profile.averageRating + profile.vibepoints
            Triple(profile, age, rank)
        }

        val comparator = when (currentSort) {
            "age" -> compareBy<Triple<Profile,Int,Double>> { it.second }
            "rating" -> compareBy<Triple<Profile,Int,Double>> { it.first.averageRating }
            "engagement" -> compareBy<Triple<Profile,Int,Double>> { it.first.vibepoints }
            else -> compareBy<Triple<Profile,Int,Double>> { it.third } // rank
        }

        val sorted = usersWithExtra.sortedWith(comparator)
        val finalList = if (ascending) sorted else sorted.reversed()
        finalList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "KupidX Hub: Online Users",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Table Headers
        Row(modifier = Modifier.fillMaxWidth()) {
            TableHeader("Age", currentSort=="age", ascending) {
                if (currentSort=="age") ascending = !ascending
                currentSort="age"
            }
            TableHeader("Rating", currentSort=="rating", ascending) {
                if (currentSort=="rating") ascending = !ascending
                currentSort="rating"
            }
            TableHeader("Engagement", currentSort=="engagement", ascending) {
                if (currentSort=="engagement") ascending = !ascending
                currentSort="engagement"
            }
            TableHeader("Rank", currentSort=="rank", ascending) {
                if (currentSort=="rank") ascending = !ascending
                currentSort="rank"
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            for ((profile, age, rank) in sortedUsers) {
                UserRow(profile, age, rank) {
                    navController.navigate("ephemeral_chat/${profile.userId}")
                }
            }
        }
    }
}

@Composable
fun TableHeader(text:String, selected:Boolean, ascending:Boolean, onClick:()->Unit){
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Row {
            Text(text = text, color=Color.White, fontWeight= FontWeight.Bold, maxLines=1, overflow= TextOverflow.Ellipsis)
            if (selected) {
                Text(
                    text = if (ascending) "▲" else "▼",
                    color = Color.White,
                    modifier = Modifier.padding(start=4.dp)
                )
            }
        }
    }
}

@Composable
fun UserRow(profile: Profile, age: Int, rank: Double, onClick: ()->Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick=onClick)
            .padding(vertical=8.dp)
            .background(Color.DarkGray.copy(alpha=0.3f))
            .clip(RoundedCornerShape(4.dp))
            .padding(horizontal=8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Age
        Text(text=age.toString(), color=Color.White, modifier=Modifier.weight(1f))
        // Rating
        Text(text=String.format("%.1f", profile.averageRating), color=Color.White, modifier=Modifier.weight(1f))
        // Engagement (vibepoints)
        Text(text=String.format("%.1f", profile.vibepoints), color=Color.White, modifier=Modifier.weight(1f))
        // Rank (demo)
        Text(text=String.format("%.1f", rank), color=Color.White, modifier=Modifier.weight(1f))
    }
}

// Ephemeral Chat Screen
// A temporary chat that doesn't show username or profile pic, just messages.
// After leaving, no data saved. Just a dummy UI here.
@Composable
fun EphemeralChatScreen(navController: NavController, otherUserId: String) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    // We'll store messages in local state for this demo
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var messageText by remember { mutableStateOf("") }

    // In a real scenario, you'd listen to ephemeralChats/{chatId} in Firebase
    // chatId can be something like: ephemeral_${sorted(currentUserId, otherUserId)}
    // For now, we just keep it local

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("Ephemeral Chat", color=Color.White) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.Close, contentDescription="Close", tint=Color.White)
                }
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Black)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(8.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                EphemeralMessageBubble(msg, currentUserId)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color.DarkGray).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = messageText,
                onValueChange = { messageText=it },
                modifier = Modifier.weight(1f).padding(8.dp),
                textStyle = LocalTextStyle.current.copy(color=Color.White)
            )
            IconButton(onClick = {
                if (messageText.isNotBlank()) {
                    val newMsg = Message(
                        id= UUID.randomUUID().toString(),
                        senderId=currentUserId,
                        receiverId=otherUserId,
                        text=messageText,
                        timestamp=System.currentTimeMillis()
                    )
                    messages = messages + newMsg
                    messageText=""
                }
            }) {
                Icon(Icons.Default.Send, contentDescription="Send", tint=Color.White)
            }
        }
    }
}

@Composable
fun EphemeralMessageBubble(msg: Message, currentUserId:String) {
    val isCurrentUser = msg.senderId == currentUserId
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if(isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isCurrentUser) Color(0xFF444444) else Color(0xFF333333),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
                .widthIn(min=0.dp, max=250.dp)
        ) {
            Text(msg.text, color=Color.White)
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}
