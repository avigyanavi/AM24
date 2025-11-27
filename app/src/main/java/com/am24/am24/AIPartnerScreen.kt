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
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

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

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val defaultLang = defaultLanguageCode()
    var selectedLanguage by remember { mutableStateOf(prefs.getString("language", defaultLang) ?: defaultLang) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    val languages = listOf(
        stringResource(R.string.language_name_english) to "en",
        stringResource(R.string.language_name_hindi) to "hi",
        stringResource(R.string.language_name_spanish) to "es",
        stringResource(R.string.language_name_thai) to "th",
        stringResource(R.string.language_name_vietnamese) to "vi",
    )


    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<AIPartnerChatMessage>() }

    val effectiveProfile = profile?.let {
        if (selectedLanguage != it.preferredLanguage) it.copy(preferredLanguage = selectedLanguage) else it
    }

    // 🔢 Shared AI message credits
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val userRef = remember(currentUserId) {
        currentUserId?.let { uid ->
            FirebaseRefs.db.getReference("users").child(uid)
        }
    }

    // -1 = not loaded yet
    var aiMessagesLeft by remember { mutableStateOf(-1) }
    var pendingDebit by remember { mutableStateOf(0) }

    LaunchedEffect(currentUserId) {
        if (currentUserId == null || userRef == null) return@LaunchedEffect
        try {
            val snap = userRef.get().await()
            aiMessagesLeft = snap.child("availableAiMessages")
                .getValue(Int::class.java) ?: 0
        } catch (e: Exception) {
            Log.e("AIPartnerScreen", "Failed loading AiMessages", e)
        }
    }

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

            // 1 reply = 1 AI message, but only if we had a pending send
            if (pendingDebit > 0 && aiMessagesLeft > 0 && userRef != null) {
                pendingDebit -= 1
                aiMessagesLeft -= 1
                try {
                    userRef.child("availableAiMessages").setValue(aiMessagesLeft)
                } catch (e: Exception) {
                    Log.e("AIPartnerScreen", "Failed saving AiMessages", e)
                }
            } else {
                // safety: don't leave junk pending
                pendingDebit = 0
            }
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
            val currentLanguageLabel =
                languages.firstOrNull { it.second == selectedLanguage }?.first ?: selectedLanguage

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.settings_preferred_language),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Box {
                    OutlinedButton(
                        onClick = { showLanguageMenu = true },
                        border = BorderStroke(1.dp, Color(0xFFFF6F00)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(currentLanguageLabel, color = Color.White, fontSize = 13.sp)
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false }
                    ) {
                        languages.forEach { (label, code) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showLanguageMenu = false
                                    if (selectedLanguage != code) {
                                        selectedLanguage = code
                                        prefs.edit().putString("language", code).apply()
                                        userRef?.child("preferredLanguage")?.setValue(code)
                                        updateLocale(context, code)
                                        (context as? Activity)?.recreate()
                                    }
                                }
                            )
                        }
                    }
                }
            }

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
            Text(
                text = if (aiMessagesLeft >= 0) "AI messages left: $aiMessagesLeft" else "",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
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
                        if (inputText.isBlank() || effectiveProfile == null || isLoading) {
                            return@FilledIconButton
                        }

                        // If we've loaded credits and have zero, go to top-up
                        if (aiMessagesLeft == 0) {
                            navController.navigate("buyAiMessages")
                            return@FilledIconButton
                        }

                        val text = inputText.trim()
                        inputText = ""

                        // Push user message
                        messages.add(
                            AIPartnerChatMessage(
                                isUser = true,
                                text = text
                            )
                        )

                        // Mark that next AI reply should burn 1 token
                        pendingDebit += 1

                        // Call AI
                        scope.launch {
                            aiPartnerViewModel.sendMessage(
                                userInput = text,
                                profile = effectiveProfile
                            ) { error ->
                                // Show error bubble
                                messages.add(
                                    AIPartnerChatMessage(
                                        isUser = false,
                                        text = error
                                    )
                                )
                                // If AI failed, don’t charge
                                if (pendingDebit > 0) pendingDebit -= 1
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
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
