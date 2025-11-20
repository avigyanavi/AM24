@file:OptIn(ExperimentalMaterial3Api::class)

package com.am24.am24

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

data class AIPartnerChatMessage(
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun AIPartnerScreen(
    navController: NavController,
    aiPartnerViewModel: AIPartnerViewModel,
    profileViewModel: ProfileViewModel
) {
    val profile by profileViewModel.currentUserProfile.collectAsState()
    val aiResp by aiPartnerViewModel.aiResponse.collectAsState()
    val isLoading by aiPartnerViewModel.isLoading.collectAsState()

    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<AIPartnerChatMessage>() }

    // Start watching profile as soon as this screen is first composed.
    LaunchedEffect(Unit) {
        aiPartnerViewModel.startProfileListener(profileViewModel)
    }

    // When AI responds, push into local chat list.
    LaunchedEffect(aiResp) {
        aiResp?.let { text ->
            messages.add(
                AIPartnerChatMessage(
                    isUser = false,
                    text = text
                )
            )
        }
    }

    // No local Scaffold/top bar – MainScreen’s Scaffold handles bars & padding
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val bubbleColor =
                        if (msg.isUser) Color(0xFF00BF63) else Color(0xFF1E1E1E)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = bubbleColor)
                        ) {
                            Text(
                                text = msg.text,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Tell me something…") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6F00),
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFF6F00),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = {
                        if (inputText.isBlank() || profile == null || isLoading) return@FilledIconButton

                        val text = inputText.trim()
                        inputText = ""

                        // Push user message
                        messages.add(
                            AIPartnerChatMessage(
                                isUser = true,
                                text = text
                            )
                        )

                        // Call AI
                        scope.launch {
                            aiPartnerViewModel.sendMessage(
                                userInput = text,
                                profile = profile!!
                            ) { error ->
                                messages.add(
                                    AIPartnerChatMessage(
                                        isUser = false,
                                        text = error
                                    )
                                )
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
