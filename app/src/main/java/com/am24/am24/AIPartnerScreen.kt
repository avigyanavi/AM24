@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class      // 👈 add this
)

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
import androidx.compose.foundation.lazy.LazyRow
import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import com.google.firebase.database.DatabaseReference

data class AIPartnerChatMessage(
    val isUser: Boolean,
    val text: String? = null,
    val imageBytes: ByteArray? = null,
    val isImageLoadingBubble: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// What we actually store in Firebase
data class AIPartnerMessageRemote(
    val user: Boolean = false, // <-- field name in RTDB
    val text: String? = null,
    val imageB64: String? = null,
    val timestamp: Long = 0L
)

// Convert remote -> local UI model
fun AIPartnerMessageRemote.toLocal(): AIPartnerChatMessage {
    val bytes = imageB64?.let {
        try { Base64.decode(it, Base64.DEFAULT) } catch (_: Exception) { null }
    }

    if (bytes != null) {
        AIPartnerImageCache.put(timestamp, bytes)
    }

    return AIPartnerChatMessage(
        isUser = user,
        text = text,
        imageBytes = bytes,
        isImageLoadingBubble = false,
        timestamp = timestamp
    )
}


object AIPartnerImageCache {
    private val cache = mutableMapOf<Long, ByteArray>()

    fun put(timestamp: Long, bytes: ByteArray) {
        cache[timestamp] = bytes
    }

    fun get(timestamp: Long): ByteArray? = cache[timestamp]

    fun remove(timestamp: Long) {
        cache.remove(timestamp)
    }

    fun clear() {
        cache.clear()
    }
}

@Composable
fun AIPartnerScreen(
    navController: NavController,
    aiPartnerViewModel: AIPartnerViewModel,
    profileViewModel: ProfileViewModel
) {
    val profile by profileViewModel.currentUserProfile.collectAsState()
    val aiResp by aiPartnerViewModel.aiResponse.collectAsState()

    val aiImageBase64 by aiPartnerViewModel.aiImageBase64.collectAsState()
    val isImageLoading by aiPartnerViewModel.isImageLoading.collectAsState()
    val isLoading by aiPartnerViewModel.isLoading.collectAsState()

    val partnerGender by aiPartnerViewModel.partnerGender.collectAsState()
    var selectedMessage by remember { mutableStateOf<AIPartnerChatMessage?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val currentLanguageCode by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0].language
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale.language
            }
        )
    }

    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<AIPartnerChatMessage>() }
    val listState = rememberLazyListState()
    val quickPrompts = remember { listOf("Hi! How are you?") }

    // Scroll to bottom whenever list size changes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val effectiveProfile = profile?.let {
        if (currentLanguageCode != it.preferredLanguage) {
            it.copy(preferredLanguage = currentLanguageCode)
        } else {
            it
        }
    }

    // 🔢 Shared AI message credits
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val userRef = remember(currentUserId) {
        currentUserId?.let { uid ->
            FirebaseRefs.db.getReference("users").child(uid)
        }
    }

    val chatRef = remember(currentUserId) {
        currentUserId?.let { uid ->
            FirebaseRefs.db.getReference("aiPartnerChats").child(uid)
        }
    }

    var aiMessagesLeft by remember { mutableStateOf(-1) }
    var pendingDebit by remember { mutableStateOf(0) }

    fun persistMessageRemote(
        isUser: Boolean,
        text: String? = null,
        imageB64: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val ref = chatRef ?: return
        val dto = AIPartnerMessageRemote(
            user = isUser,
            text = text,
            imageB64 = imageB64,
            timestamp = timestamp
        )
        ref.child(timestamp.toString()).setValue(dto)
    }

    // Load last 100 messages
    LaunchedEffect(chatRef) {
        val ref = chatRef ?: return@LaunchedEffect
        try {
            val snap = ref
                .orderByChild("timestamp")
                .limitToLast(100)
                .get()
                .await()

            val loaded = snap.children
                .mapNotNull { it.getValue(AIPartnerMessageRemote::class.java) }
                .sortedBy { it.timestamp }
                .map { it.toLocal() }

            messages.clear()
            messages.addAll(loaded)

        } catch (e: Exception) {
            Log.e("AIPartnerScreen", "Failed loading AI partner chat history", e)
        }
    }

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

    // Start watching profile
    LaunchedEffect(Unit) {
        aiPartnerViewModel.startProfileListener(profileViewModel)
    }

    // When AI responds
    LaunchedEffect(aiResp) {
        aiResp?.let { text ->
            val nowTs = System.currentTimeMillis()

            messages.add(
                AIPartnerChatMessage(
                    isUser = false,
                    text = text,
                    timestamp = nowTs
                )
            )

            persistMessageRemote(
                isUser = false,
                text = text,
                imageB64 = null,
                timestamp = nowTs
            )

            if (pendingDebit > 0 && aiMessagesLeft > 0 && userRef != null) {
                pendingDebit -= 1
                aiMessagesLeft -= 1
                try {
                    userRef.child("availableAiMessages").setValue(aiMessagesLeft)
                } catch (e: Exception) {
                    Log.e("AIPartnerScreen", "Failed saving AiMessages", e)
                }
            } else {
                pendingDebit = 0
            }
        }
    }

    // When an image comes back
    LaunchedEffect(aiImageBase64) {
        aiImageBase64?.let { b64 ->
            // remove loading bubble if present
            messages.removeAll { !it.isUser && it.isImageLoadingBubble }

            val nowTs = System.currentTimeMillis()

            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                // cache it
                AIPartnerImageCache.put(nowTs, bytes)

                messages.add(
                    AIPartnerChatMessage(
                        isUser = false,
                        imageBytes = bytes,
                        timestamp = nowTs
                    )
                )

                persistMessageRemote(
                    isUser = false,
                    text = null,
                    imageB64 = b64,
                    timestamp = nowTs
                )

                // <-- NEW: consume reserved credit on successful image
                if (pendingDebit > 0 && aiMessagesLeft > 0 && userRef != null) {
                    pendingDebit -= 1
                    aiMessagesLeft -= 1
                    try {
                        userRef.child("availableAiMessages").setValue(aiMessagesLeft)
                        Log.d("AIPartnerScreen", "Consumed 1 credit for image. aiMessagesLeft=$aiMessagesLeft, pendingDebit=$pendingDebit")
                    } catch (e: Exception) {
                        Log.e("AIPartnerScreen", "Failed saving AiMessages after image", e)
                    }
                } else {
                    // Normalise pendingDebit in case it's inconsistent
                    pendingDebit = 0
                }

            } catch (e: IllegalArgumentException) {
                Log.e("AIPartnerScreen", "Base64 decode failed", e)
                messages.add(
                    AIPartnerChatMessage(
                        isUser = false,
                        text = "Couldn't load picture right now. 😔"
                    )
                )

                persistMessageRemote(
                    isUser = false,
                    text = "Couldn't load picture right now. 😔",
                    imageB64 = null,
                    timestamp = nowTs
                )

                // Release reserved credit on decode failure too
                if (pendingDebit > 0) {
                    pendingDebit -= 1
                    Log.d("AIPartnerScreen", "Base64 decode failed — released reserved credit. pendingDebit=$pendingDebit")
                }
            }
        }
    }

    // 🔐 Explicit word list + censor
    val EXPLICIT_WORDS = listOf(
        "boobs", "tits", "breast", "ass",
        "dick", "cock", "pussy",
        "chod", "chodo", "chud", "rand", "bhosd"
    )

    fun censorExplicit(text: String): String {
        var out = text
        for (w in EXPLICIT_WORDS) {
            val regex = Regex("\\b${Regex.escape(w)}\\b", RegexOption.IGNORE_CASE)
            out = out.replace(regex, "***")
        }
        return out
    }

    fun buildPicContext(messages: List<AIPartnerChatMessage>): String? {
        val lastAiIndex = messages.indexOfLast { !it.isUser && it.text != null }
        if (lastAiIndex <= 0) return null

        val lastUserIndex = (lastAiIndex - 1 downTo 0)
            .firstOrNull { messages[it].isUser && messages[it].text != null }
            ?: return null

        // Censor only in what we send to the model; UI + Firebase keep original text
        val userText = censorExplicit(messages[lastUserIndex].text!!)
        val aiText = censorExplicit(messages[lastAiIndex].text!!)

        val combined = "User: $userText | AI: $aiText"
        val words = combined.split(Regex("\\s+")).filter { it.isNotBlank() }
        val sliced = if (words.size > 20) words.take(20) else words
        return sliced.joinToString(" ")
    }

    fun lastMessageIsAiImage(messages: List<AIPartnerChatMessage>): Boolean {
        // look for the most recent AI message (non-user) and see if THAT was an image
        val lastAi = messages.asReversed().firstOrNull { !it.isUser }
        return lastAi?.imageBytes != null
    }

    fun shouldTriggerImageGen(
        rawText: String,
        messages: List<AIPartnerChatMessage>,
        isImageLoading: Boolean
    ): Boolean {
        if (isImageLoading) return false

        val lower = rawText.lowercase()

        // 🔥 Explicit words → send to image instead of text
        val isExplicit = EXPLICIT_WORDS.any { lower.contains(it) }
        if (isExplicit) {
            return true
        }

        // Existing pic keywords path
        val hasPicKeyword = listOf(
            "pic",
            "photo",
            "selfie",
            "image",
            "foto",
            "fotografia",
            "fotografía",
            "imagen",
            "imagem",
            "photographie",
            "bild",
            "immagine",
            "fotoğraf",
            "фото",
            "фотография",
            "obraz",
            "사진",
            "画像",
            "写真",
            "图片",
            "ảnh",
            "ảnh chụp",
            "tasveer"
        ).any { lower.contains(it) }

        if (!hasPicKeyword) return false

        val hasRequestVerb = listOf(
            "send", "bhej", "bhejo", "dede", "de de",
            "dikha", "show",
            "another", "one more", "ek aur", "more pic", "next pic"
        ).any { lower.contains(it) }

        val lastWasAiImage = lastMessageIsAiImage(messages)

        return if (lastWasAiImage) {
            hasRequestVerb   // false → no new pic
        } else {
            true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Partner gender toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AI Character",
                    color = Color.White,
                    fontSize = 13.sp
                )
                Row {
                    FilterChip(
                        selected = partnerGender == PartnerGender.MALE,
                        onClick = { aiPartnerViewModel.setPartnerGender(PartnerGender.MALE) },
                        label = { Text("Male") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = partnerGender == PartnerGender.FEMALE,
                        onClick = { aiPartnerViewModel.setPartnerGender(PartnerGender.FEMALE) },
                        label = { Text("Female") }
                    )
                }
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val bubbleColor =
                        if (msg.isUser) KupidxOrange else Color(0xFF1E1E1E)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .combinedClickable(
                                    onClick = {
                                        // Single tap → open full image if this message has an image
                                        if (msg.imageBytes != null) {
                                            navController.navigate("aiImageFull/$currentUserId/${msg.timestamp}")
                                        }
                                    },
                                    onLongClick = {
                                        selectedMessage = msg
                                        showMenu = true
                                    }
                                ),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = bubbleColor)
                        ) {
                            when {
                                msg.isImageLoadingBubble -> {
                                    Box(
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(220.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            strokeWidth = 3.dp,
                                            color = Color.White
                                        )
                                    }
                                }
                                msg.imageBytes != null -> {
                                    AsyncImage(
                                        model = msg.imageBytes,
                                        contentDescription = "AI Partner Picture",
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(220.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                else -> {
                                    Text(
                                        text = msg.text.orEmpty(),
                                        color = Color.White,
                                        modifier = Modifier.padding(10.dp),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Typing bubble when AI is thinking
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "typing...",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showMenu && selectedMessage != null) {
                ModalBottomSheet(
                    onDismissRequest = { showMenu = false }
                ) {
                    val m = selectedMessage!!
                    val ctx = context              // 👈 capture once

                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Delete entire chat",
                                color = Color.Red
                            )
                        },
                        modifier = Modifier.clickable {
                            deleteAllMessages(
                                messages = messages,
                                chatRef = chatRef,
                                aiPartnerViewModel = aiPartnerViewModel
                            )
                            showMenu = false
                        }
                    )

                    if (m.text != null) {
                        ListItem(
                            headlineContent = { Text("Copy text") },
                            modifier = Modifier.clickable {
                                val clipboard = ctx
                                    .getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("msg", m.text)
                                )
                                showMenu = false
                            }
                        )
                    }

                    if (m.imageBytes != null) {
                        ListItem(
                            headlineContent = { Text("Open full image") },
                            modifier = Modifier.clickable {
                                // Call full-screen viewer route
                                navController.navigate("aiImageFull/$currentUserId/${m.timestamp}")
                                showMenu = false
                            }
                        )

                        ListItem(
                            headlineContent = { Text("Save image to gallery") },
                            modifier = Modifier.clickable {
                                saveImageToGallery(ctx, m.imageBytes)
                                showMenu = false
                            }
                        )
                    }

                    ListItem(
                        headlineContent = { Text("Delete") },
                        modifier = Modifier.clickable {
                            deleteMessage(
                                m.timestamp,
                                messages,
                                chatRef
                            )
                            showMenu = false
                        }
                    )
                }
            }

            Text(
                text = if (aiMessagesLeft >= 0) "AI messages left: $aiMessagesLeft" else "",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (quickPrompts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        AssistChip(
                            onClick = { inputText = prompt },
                            label = { Text(prompt) },
                            shape = RoundedCornerShape(24.dp)
                        )
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
                        if (inputText.isBlank() || effectiveProfile == null || isLoading) {
                            return@FilledIconButton
                        }

                        if (aiMessagesLeft == 0) {
                            navController.navigate("buyAiMessages")
                            return@FilledIconButton
                        }

                        val text = inputText.trim()
                        inputText = ""

                        val nowTs = System.currentTimeMillis()

                        messages.add(
                            AIPartnerChatMessage(
                                isUser = true,
                                text = text,
                                timestamp = nowTs
                            )
                        )

                        persistMessageRemote(
                            isUser = true,
                            text = text,
                            imageB64 = null,
                            timestamp = nowTs
                        )

                        val triggerImage = shouldTriggerImageGen(
                            rawText = text,
                            messages = messages,
                            isImageLoading = isImageLoading
                        )

                        if (triggerImage) {
                            val ctxRaw = buildPicContext(messages)
                            val safeCtx = ctxRaw?.let { censorExplicit(it) }
                            val safeUser = censorExplicit(text)

                            val promptForImage = buildString {
                                safeCtx?.let { append("Recent vibe: $it. ") }
                                append("User now says: \"$safeUser\". ")
                                append("Generate a flirty, picture that matches this mood.")
                            }

                            // <-- NEW: reserve a credit for the upcoming image attempt
                            pendingDebit += 1
                            Log.d("AIPartnerScreen", "Reserved 1 credit for image generation. pendingDebit=$pendingDebit, aiMessagesLeft=$aiMessagesLeft")

                            messages.add(
                                AIPartnerChatMessage(
                                    isUser = false,
                                    isImageLoadingBubble = true
                                )
                            )

                            scope.launch {
                                aiPartnerViewModel.generateImageFromUserPrompt(
                                    userPrompt = promptForImage
                                ) { error ->
                                    // UPDATED onError handler (see next block)
                                    Log.d("AIPartnerScreen", "Image generation failed: $error")
                                    messages.removeAll { !it.isUser && it.isImageLoadingBubble }

                                    // If we reserved a credit earlier, release it on failure (do NOT consume it)
                                    if (pendingDebit > 0) {
                                        pendingDebit -= 1
                                        Log.d("AIPartnerScreen", "Image gen failed — released reserved credit. pendingDebit=$pendingDebit")
                                    }

                                    messages.add(
                                        AIPartnerChatMessage(
                                            isUser = false,
                                            // FRIENDLY explicit message for the user
                                            text = "I can’t create that kind of picture right now. How about a flirty message instead? 😉"
                                        )
                                    )
                                }
                            }

                            return@FilledIconButton
                        }

                        pendingDebit += 1
                        scope.launch {
                            aiPartnerViewModel.sendMessage(
                                userInput = text,
                                profile = effectiveProfile
                            ) { error ->
                                messages.add(
                                    AIPartnerChatMessage(
                                        isUser = false,
                                        text = error
                                    )
                                )
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

fun deleteMessage(
    timestamp: Long,
    messages: MutableList<AIPartnerChatMessage>,
    chatRef: DatabaseReference?
) {
    messages.removeAll { it.timestamp == timestamp }
    AIPartnerImageCache.remove(timestamp)
    chatRef?.child(timestamp.toString())?.removeValue()
}

fun saveImageToGallery(context: Context, bytes: ByteArray) {
    val name = "AIPartner_${System.currentTimeMillis()}.jpg"
    val fos = context.contentResolver.openOutputStream(
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        "rw"
    )
    fos?.write(bytes)
    fos?.close()
}

fun deleteAllMessages(
    messages: MutableList<AIPartnerChatMessage>,
    chatRef: DatabaseReference?,
    aiPartnerViewModel: AIPartnerViewModel
) {
    messages.clear()
    chatRef?.removeValue()
    AIPartnerImageCache.clear()
    aiPartnerViewModel.clearAllState()
}
