@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.am24.am24

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(navController: NavController, modifier: Modifier = Modifier) {
    // Since the Scaffold is at the top level (MainScreen), we don't need to define it here
    // Just call HomeScreenContent and pass the modifier
    HomeScreenContent(navController = navController, modifier = modifier)
}
@Composable
fun HomeScreenContent(navController: NavController, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    var postContent by remember { mutableStateOf(TextFieldValue("")) }
    var filterOption by remember { mutableStateOf("recent") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var isPostSectionVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val addedUserTags = remember { mutableStateListOf<String>() }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var isPosting by remember { mutableStateOf(false) }

    // Fetch user's profile
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    // State variables for font selection
    var selectedFontFamily by remember { mutableStateOf("Default") }
    var selectedFontSize by remember { mutableStateOf(14) }

    // State variable for filter value
    var filterValue by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userProfile = snapshot.getValue(Profile::class.java)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
    ) {
        // Search Bar
        CustomSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { /* Handle search logic */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Create Post and Filter Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Create Post Button (Toggle Minimize)
            Button(
                onClick = { isPostSectionVisible = !isPostSectionVisible },
                border = BorderStroke(1.dp, Color(0xFF00bf63)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    if (isPostSectionVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isPostSectionVisible) "Minimize" else "Expand",
                    tint = Color(0xFF00bf63)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Create Post", color = Color(0xFF00bf63))
            }

            // Filter Button
            Box {
                Button(
                    onClick = { showFilterMenu = !showFilterMenu },
                    border = BorderStroke(1.dp, Color(0xFF00bf63)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text(text = filterOption.capitalize(), color = Color(0xFF00bf63))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Filter options",
                        tint = Color(0xFF00bf63)
                    )
                }

                // Dropdown Menu for Filter Options
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                ) {
                    val filterOptions = listOf(
                        "recent", "popular", "unpopular", "own echoes",
                        "city", "age", "level", "gender", "high-school", "college"
                    )
                    filterOptions.filter { it != filterOption }.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option.capitalize(), color = Color(0xFF00bf63)) },
                            onClick = {
                                filterOption = option
                                filterValue = "" // Reset filter value when option changes
                                showFilterMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Input Field for Additional Filters
        if (filterOption in listOf("city", "age", "level", "gender", "high-school", "college")) {
            Spacer(modifier = Modifier.height(8.dp))
            when (filterOption) {
                "age" -> {
                    // Integer input field
                    OutlinedTextField(
                        value = filterValue,
                        onValueChange = { newValue ->
                            // Allow only numbers
                            if (newValue.all { it.isDigit() }) {
                                filterValue = newValue
                            }
                        },
                        label = { Text("Enter Age", color = Color.White) },
                        placeholder = { Text("e.g., 25", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFF00bf63)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                "gender" -> {
                    // Dropdown with options 'M', 'F', 'T'
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = filterValue,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Gender", color = Color.White) },
                            placeholder = { Text("M/F/T", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00bf63),
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = Color(0xFF00bf63)
                            ),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = Color.White,
                                    modifier = Modifier.clickable { expanded = true }
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            listOf("M", "F", "T").forEach { gender ->
                                DropdownMenuItem(
                                    text = { Text(gender, color = Color(0xFF00bf63)) },
                                    onClick = {
                                        filterValue = gender
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                "level" -> {
                    // Integer input field (1-7)
                    OutlinedTextField(
                        value = filterValue,
                        onValueChange = { newValue ->
                            // Allow only numbers between 1 and 7
                            if (newValue.all { it.isDigit() } && newValue.toIntOrNull() in 1..7) {
                                filterValue = newValue
                            }
                        },
                        label = { Text("Enter Level (1-7)", color = Color.White) },
                        placeholder = { Text("e.g., 3", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFF00bf63)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                else -> {
                    // Text input field
                    OutlinedTextField(
                        value = filterValue,
                        onValueChange = { filterValue = it },
                        label = { Text("Enter ${filterOption.capitalize()}", color = Color.White) },
                        placeholder = { Text("Type here", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00bf63),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFF00bf63)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Post Input Section
        if (isPostSectionVisible) {
            PostInputSection(
                postContent = postContent,
                onValueChange = { postContent = it },
                onPost = {
                    if (postContent.text.isNotBlank()) {
                        isPostSectionVisible = false
                        isPosting = true // Start posting
                        coroutineScope.launch(Dispatchers.IO) {
                            val currentUserId =
                                FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                            val username = userProfile?.username ?: "Anonymous"

                            val database = FirebaseDatabase.getInstance()
                            val postsRef = database.getReference("posts")

                            val postId = postsRef.push().key ?: return@launch

                            val post = Post(
                                postId = postId,
                                userId = currentUserId,
                                username = username,
                                contentText = postContent.text,
                                timestamp = ServerValue.TIMESTAMP,
                                upvotes = 0,
                                downvotes = 0,
                                totalComments = 0,
                                userTags = addedUserTags.toList(),
                                upvoteToDownvoteRatio = 0.0,
                                fontFamily = selectedFontFamily,
                                fontSize = selectedFontSize
                            )

                            try {
                                postsRef.child(postId).setValue(post).await()
                                withContext(Dispatchers.Main) {
                                    isPosting = false // Posting complete
                                    postContent = TextFieldValue("")
                                    addedUserTags.clear()
                                    Toast.makeText(
                                        context,
                                        "Post uploaded successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isPosting = false // Posting complete even if there was an error
                                    Toast.makeText(
                                        context,
                                        "Failed to upload post: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            Toast.makeText(
                                context,
                                "Please enter some text before posting",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                addedUserTags = addedUserTags,
                selectedFontFamily = selectedFontFamily,
                onFontFamilyChange = { selectedFontFamily = it },
                selectedFontSize = selectedFontSize,
                onFontSizeChange = { selectedFontSize = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Feed Section (Fetch and display posts from Firebase)
        FeedSection(
            navController = navController,
            filterOption = filterOption,
            filterValue = filterValue,
            searchQuery = searchQuery,
            userId = userId,
            userProfile = userProfile,
            isPosting = isPosting,
            onTagClick = { tag -> searchQuery = tag }
        )
    }
}

@Composable
fun PostInputSection(
    postContent: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onPost: () -> Unit,
    addedUserTags: MutableList<String>,
    selectedFontFamily: String,
    onFontFamilyChange: (String) -> Unit,
    selectedFontSize: Int,
    onFontSizeChange: (Int) -> Unit
) {
    // Define available fonts and sizes
    val availableFonts = listOf("Default", "SansSerif", "Serif", "Monospace", "Cursive")
    val availableFontSizes = (12..24 step 2).toList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 8.dp)
    ) {
        // Formatting buttons (Bold, Italic, Strikethrough)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                applyFormatting("**", postContent, onValueChange)
            }) {
                Icon(
                    imageVector = Icons.Default.FormatBold,
                    contentDescription = "Bold",
                    tint = Color.White
                )
            }
            IconButton(onClick = {
                applyFormatting("_", postContent, onValueChange)
            }) {
                Icon(
                    imageVector = Icons.Default.FormatItalic,
                    contentDescription = "Italic",
                    tint = Color.White
                )
            }
            IconButton(onClick = {
                applyFormatting("~", postContent, onValueChange)
            }) {
                Icon(
                    imageVector = Icons.Default.FormatStrikethrough,
                    contentDescription = "Strikethrough",
                    tint = Color.White
                )
            }
        }

        // Font Family Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Font:", color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            var fontMenuExpanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { fontMenuExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                ) {
                    Text(selectedFontFamily, color = Color.White)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = fontMenuExpanded,
                    onDismissRequest = { fontMenuExpanded = false }
                ) {
                    availableFonts.forEach { font ->
                        DropdownMenuItem(
                            text = { Text(font) },
                            onClick = {
                                onFontFamilyChange(font)
                                fontMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Font Size Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text("Size:", color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            var sizeMenuExpanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { sizeMenuExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                ) {
                    Text("$selectedFontSize", color = Color.White)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = sizeMenuExpanded,
                    onDismissRequest = { sizeMenuExpanded = false }
                ) {
                    availableFontSizes.forEach { size ->
                        DropdownMenuItem(
                            text = { Text("$size") },
                            onClick = {
                                onFontSizeChange(size)
                                sizeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Text input field for the post
        OutlinedTextField(
            value = postContent,
            onValueChange = { newValue ->
                onValueChange(newValue)
            },
            placeholder = { Text("Type your thoughts/Voice your opinion", color = Color.Gray) },
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = selectedFontSize.sp, fontFamily = mapFontFamily(selectedFontFamily)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color(0xFF00bf63),
                unfocusedBorderColor = Color(0xFF00bf63)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // User Tag Section
        TagInputSection(
            label = "Add User Tags",
            tags = addedUserTags,
            color = Color(0xFF00bf63)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Post button to submit the post
        Button(
            onClick = {
                onPost()
            },
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
        ) {
            Text("Post", color = Color.White)
        }
    }
}

fun mapFontFamily(fontFamily: String): FontFamily {
    return when (fontFamily) {
        "Default" -> FontFamily.Default
        "SansSerif" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        "Monospace" -> FontFamily.Monospace
        "Cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}


// Helper function to apply formatting
fun applyFormatting(
    marker: String,
    postContent: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val selection = postContent.selection
    val text = postContent.text
    val newText: String
    val newSelection: TextRange

    if (selection.collapsed) {
        // No text selected, insert markers and place cursor between them
        val insertPosition = selection.start
        newText = text.substring(0, insertPosition) + marker + marker + text.substring(insertPosition)
        val cursorPosition = insertPosition + marker.length
        newSelection = TextRange(cursorPosition, cursorPosition)
    } else {
        // Text is selected, wrap it with markers
        val selectedText = text.substring(selection.start, selection.end)
        newText = text.substring(0, selection.start) + marker + selectedText + marker + text.substring(selection.end)
        // Adjust selection to cover the newly formatted text
        newSelection = TextRange(selection.start, selection.end + 2 * marker.length)
    }

    onValueChange(TextFieldValue(newText, newSelection))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInputSection(label: String, tags: MutableList<String>, color: Color) {
    var tagInput by remember { mutableStateOf(TextFieldValue("")) }

    Column {
        Text(text = label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = { newTagInput ->
                    tagInput = newTagInput
                },
                placeholder = { Text("Add tags here", color = Color.Gray) },
                textStyle = LocalTextStyle.current.copy(color = Color.White), // Set input text color to white
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = color,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = color,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            IconButton(
                onClick = {
                    if (tagInput.text.isNotBlank()) {
                        tags.add(tagInput.text.trim())
                        tagInput = TextFieldValue("") // Clear input field
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Tag", tint = color)
            }
        }

        // Display added tags with remove button
        FlowRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            tags.forEach { tag ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black, RoundedCornerShape(8.dp)) // Black background
                            .border(BorderStroke(1.dp, color), RoundedCornerShape(8.dp)) // Green border
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "#$tag",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    IconButton(
                        onClick = { tags.remove(tag) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove Tag", tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun FeedSection(
    navController: NavController,
    filterOption: String,
    searchQuery: String,
    userId: String?,
    userProfile: Profile?,
    isPosting: Boolean,
    onTagClick: (String) -> Unit,
    filterValue: String
) {
    val posts = remember { mutableStateListOf<Post>() }
    val postsRef = FirebaseDatabase.getInstance().getReference("posts")
    val userProfiles = remember { mutableStateMapOf<String, Profile>() }
    val context = LocalContext.current

    var loading by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()

    // Load initial posts or refresh when filter or posting status changes
    LaunchedEffect(filterOption, filterValue, searchQuery, userId, userProfile, isPosting) {
        if (!isPosting) {
            loading = true
            withContext(Dispatchers.IO) {
                loadInitialPosts(
                    posts = posts,
                    postsRef = postsRef,
                    userProfiles = userProfiles,
                    filterOption = filterOption,
                    filterValue = filterValue,
                    searchQuery = searchQuery,
                    userId = userId,
                    userProfile = userProfile
                )
            }
            loading = false
        }
    }


    // Scroll to top when search query changes
    LaunchedEffect(searchQuery) {
        listState.animateScrollToItem(0)
    }

    if (loading) {
        // Show loading indicator
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF00bf63))
        }
    } else {
        // Show posts
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .background(Color.Black)
        ) {
            if (isPosting) {
                // Show a loading indicator in place of the post
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00bf63))
                    }
                }
            }

            items(posts) { post ->
                val profile = userProfiles[post.userId]
                FeedItem(
                    post = post,
                    userProfile = profile,
                    onUpvote = {
                        val postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.postId)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@FeedItem

                        postRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                val post = currentData.getValue(Post::class.java) ?: return Transaction.success(currentData)

                                // Initialize the upvoted and downvoted maps if they are null
                                if (post.upvotedUsers == null) {
                                    post.upvotedUsers = mutableMapOf()
                                }
                                if (post.downvotedUsers == null) {
                                    post.downvotedUsers = mutableMapOf()
                                }

                                // Check if the user has already upvoted
                                if (post.upvotedUsers.containsKey(currentUserId)) {
                                    // Remove the upvote
                                    post.upvotedUsers.remove(currentUserId)
                                    post.upvotes -= 1
                                } else {
                                    // If the user has downvoted, remove the downvote first
                                    if (post.downvotedUsers.containsKey(currentUserId)) {
                                        post.downvotedUsers.remove(currentUserId)
                                        post.downvotes -= 1
                                    }
                                    // Add the upvote
                                    post.upvotedUsers[currentUserId] = true
                                    post.upvotes += 1
                                }

                                // Set the updated post data back
                                currentData.value = post
                                return Transaction.success(currentData)
                            }

                            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                                if (error != null) {
                                    Toast.makeText(context, "Failed to update upvote: ${error.message}", Toast.LENGTH_SHORT).show()
                                } else if (committed) {
                                    val updatedPost = currentData?.getValue(Post::class.java)
                                    if (updatedPost != null) {
                                        val index = posts.indexOfFirst { it.postId == updatedPost.postId }
                                        if (index != -1) {
                                            posts[index] = updatedPost
                                        }
                                    }
                                }
                            }
                        })
                    },

                    onDownvote = {
                        val postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.postId)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@FeedItem

                        postRef.runTransaction(object : Transaction.Handler {
                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                val post = currentData.getValue(Post::class.java) ?: return Transaction.success(currentData)

                                // Initialize the upvoted and downvoted maps if they are null
                                if (post.upvotedUsers == null) {
                                    post.upvotedUsers = mutableMapOf()
                                }
                                if (post.downvotedUsers == null) {
                                    post.downvotedUsers = mutableMapOf()
                                }

                                // Check if the user has already downvoted
                                if (post.downvotedUsers.containsKey(currentUserId)) {
                                    // Remove the downvote
                                    post.downvotedUsers.remove(currentUserId)
                                    post.downvotes -= 1
                                } else {
                                    // If the user has upvoted, remove the upvote first
                                    if (post.upvotedUsers.containsKey(currentUserId)) {
                                        post.upvotedUsers.remove(currentUserId)
                                        post.upvotes -= 1
                                    }
                                    // Add the downvote
                                    post.downvotedUsers[currentUserId] = true
                                    post.downvotes += 1
                                }

                                // Set the updated post data back
                                currentData.value = post
                                return Transaction.success(currentData)
                            }

                            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                                if (error != null) {
                                    Toast.makeText(context, "Failed to update downvote: ${error.message}", Toast.LENGTH_SHORT).show()
                                } else if (committed) {
                                    val updatedPost = currentData?.getValue(Post::class.java)
                                    if (updatedPost != null) {
                                        val index = posts.indexOfFirst { it.postId == updatedPost.postId }
                                        if (index != -1) {
                                            posts[index] = updatedPost
                                        }
                                    }
                                }
                            }
                        })
                    },
                    onUserClick = {
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                        if (post.userId == currentUserId) {
                            // Navigate to ProfileScreen if it's your own profile
                            navController.navigate("profile")
                        } else {
                            // Navigate to DatingScreen with the other user's ID
                            navController.navigate("dating/${post.userId}")
                        }
                    },
                    onTagClick = { tag ->
                        onTagClick(tag)
                    },
                    onShare = {
                        sharePostWithMatches(context, post)
                    },
                    onComment = { commentText ->
                        // Comment logic
                        val postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.postId)
                        val newCommentId = postRef.child("comments").push().key ?: return@FeedItem
                        val newComment = Comment(
                            commentId = newCommentId,
                            userId = userId ?: "",
                            username = userProfile?.username ?: "Anonymous",
                            commentText = commentText,
                            timestamp = ServerValue.TIMESTAMP
                        )
                        postRef.child("comments/$newCommentId").setValue(newComment).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val updatedComments = post.comments.toMutableMap()
                                updatedComments[newCommentId] = newComment
                                val index = posts.indexOfFirst { it.postId == post.postId }
                                if (index != -1) {
                                    posts[index] = posts[index].copy(comments = updatedComments)
                                }
                            } else {
                                Toast.makeText(context, "Failed to add comment: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // No more posts indicator
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No more older posts",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedItem(
    post: Post,
    userProfile: Profile?,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onUserClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onShare: () -> Unit,
    onComment: (String) -> Unit
) {
    var commentText by remember { mutableStateOf(TextFieldValue("")) }
    var showCommentsDialog by remember { mutableStateOf(false) }

    // Annotate post content based on formatting markers
    val annotatedText = buildFormattedText(post.contentText ?: "")

    // Map font family strings to actual FontFamily objects
    val fontMap = mapOf(
        "Default" to FontFamily.Default,
        "SansSerif" to FontFamily.SansSerif,
        "Serif" to FontFamily.Serif,
        "Monospace" to FontFamily.Monospace,
        "Cursive" to FontFamily.Cursive
    )

    val fontFamily = fontMap[post.fontFamily] ?: FontFamily.Default
    val fontSize = post.fontSize.sp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(2.dp, Color(0xFF00bf63)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // User Info Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onUserClick() }
                    .padding(bottom = 8.dp)
            ) {
                if (userProfile?.profilepicUrl != null) {
                    AsyncImage(
                        model = userProfile.profilepicUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = userProfile?.username ?: "Unavailable",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⭐ ${userProfile?.rating ?: 0.0}  🌎 Rank: ${userProfile?.am24RankingGlobal ?: 0}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            // Post Content (Formatted Text)
            if (!post.contentText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = annotatedText,
                    color = Color.White,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * 1.5).sp,
                    fontFamily = fontFamily
                )
            }

            // Hashtags Below Main Content
            if (post.userTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    post.userTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(Color.Black, RoundedCornerShape(8.dp)) // Black background
                                .border(BorderStroke(1.dp, Color(0xFF00bf63)), RoundedCornerShape(8.dp)) // Accent green border
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { onTagClick(tag) }
                            )
                        }
                    }
                }
            }

            // Comment section
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add a comment...", color = Color.Gray) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00bf63),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00bf63)
                    ),
                    shape = RoundedCornerShape(12.dp) // Rounded corners for comment input
                )
                Button(
                    onClick = {
                        onComment(commentText.text)
                        commentText = TextFieldValue("") // Clear comment input after submitting
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bf63))
                ) {
                    Text("Comment", color = Color.White)
                }

                if (post.comments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "View Comments (${post.comments.size})",
                        color = Color(0xFF00bf63),
                        modifier = Modifier.clickable {
                            showCommentsDialog = true
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Comments Dialog
            if (showCommentsDialog) {
                CommentsDialog(
                    post = post,
                    onDismiss = { showCommentsDialog = false }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sharing and Upvote/Downvote Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Share Button
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }

                // Upvote and Downvote Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onUpvote) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Upvote",
                                tint = Color(0xFF00ff00) // Brighter green for upvote
                            )
                        }
                        Text(text = "${post.upvotes}", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDownvote) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Downvote",
                                tint = Color(0xffff0000) // Brighter red for downvote
                            )
                        }
                        Text(text = "${post.downvotes}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Post Timestamp
            Text(
                text = formatTimestamp(post.getPostTimestamp()),
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun CommentsDialog(
    post: Post,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val userProfile = remember { mutableStateOf<Profile?>(null) }

    // Fetch user profile
    LaunchedEffect(userId) {
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userProfile.value = snapshot.getValue(Profile::class.java)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
        }
    }

    var sortOption by remember { mutableStateOf("Recent") }

    // Convert comments map to list and sort
    val commentsList = remember(post.comments, sortOption) {
        post.comments.values.toList().sortedWith(
            when (sortOption) {
                "Recent" -> compareByDescending<Comment> { it.getCommentTimestamp() }
                "Popular" -> compareByDescending<Comment> { it.upvotes - it.downvotes }
                else -> compareByDescending<Comment> { it.getCommentTimestamp() }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Comments", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row {
                    Button(
                        onClick = { sortOption = "Recent" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sortOption == "Recent") Color(0xFF00bf63) else Color.Gray
                        )
                    ) {
                        Text("Recent", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { sortOption = "Popular" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sortOption == "Popular") Color(0xFF00bf63) else Color.Gray
                        )
                    ) {
                        Text("Popular", color = Color.White)
                    }
                }
            }
        },
        text = {
            LazyColumn {
                items(commentsList) { comment ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Comment Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = comment.username,
                                color = Color(0xFF00bf63),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = formatTimestamp(comment.getCommentTimestamp()),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        // Comment Text
                        Text(
                            text = comment.commentText,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        // Upvote/Downvote Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                handleCommentUpvote(post.postId, comment, context)
                            }) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Upvote", tint = Color(0xFF00ff00))
                            }
                            Text(text = "${comment.upvotes}", color = Color.White)
                            IconButton(onClick = {
                                handleCommentDownvote(post.postId, comment, context)
                            }) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Downvote", tint = Color(0xffff0000))
                            }
                            Text(text = "${comment.downvotes}", color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF00bf63))
            }
        },
        containerColor = Color.Black
    )
}

// Function to handle comment upvote
fun handleCommentUpvote(postId: String, comment: Comment, context: Context) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val commentRef = FirebaseDatabase.getInstance()
        .getReference("posts")
        .child(postId)
        .child("comments")
        .child(comment.commentId)

    commentRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(currentData: MutableData): Transaction.Result {
            val currentComment = currentData.getValue(Comment::class.java) ?: return Transaction.success(currentData)

            if (currentComment.upvotedUsers == null) {
                currentComment.upvotedUsers = mutableMapOf()
            }
            if (currentComment.downvotedUsers == null) {
                currentComment.downvotedUsers = mutableMapOf()
            }

            if (currentComment.upvotedUsers.containsKey(currentUserId)) {
                currentComment.upvotedUsers.remove(currentUserId)
                currentComment.upvotes -= 1
            } else {
                if (currentComment.downvotedUsers.containsKey(currentUserId)) {
                    currentComment.downvotedUsers.remove(currentUserId)
                    currentComment.downvotes -= 1
                }
                currentComment.upvotedUsers[currentUserId] = true
                currentComment.upvotes += 1
            }

            currentData.value = currentComment
            return Transaction.success(currentData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
            if (error != null) {
                Toast.makeText(context, "Failed to upvote comment: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    })
}

// Function to handle comment downvote
fun handleCommentDownvote(postId: String, comment: Comment, context: Context) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val commentRef = FirebaseDatabase.getInstance()
        .getReference("posts")
        .child(postId)
        .child("comments")
        .child(comment.commentId)

    commentRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(currentData: MutableData): Transaction.Result {
            val currentComment = currentData.getValue(Comment::class.java) ?: return Transaction.success(currentData)

            if (currentComment.upvotedUsers == null) {
                currentComment.upvotedUsers = mutableMapOf()
            }
            if (currentComment.downvotedUsers == null) {
                currentComment.downvotedUsers = mutableMapOf()
            }

            if (currentComment.downvotedUsers.containsKey(currentUserId)) {
                currentComment.downvotedUsers.remove(currentUserId)
                currentComment.downvotes -= 1
            } else {
                if (currentComment.upvotedUsers.containsKey(currentUserId)) {
                    currentComment.upvotedUsers.remove(currentUserId)
                    currentComment.upvotes -= 1
                }
                currentComment.downvotedUsers[currentUserId] = true
                currentComment.downvotes += 1
            }

            currentData.value = currentComment
            return Transaction.success(currentData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
            if (error != null) {
                Toast.makeText(context, "Failed to downvote comment: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    })
}

@Composable
fun buildFormattedText(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", startIndex = i + 2)
                    if (end != -1) {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text.substring(i))
                        break
                    }
                }
                text.startsWith("_", i) -> {
                    val end = text.indexOf("_", startIndex = i + 1)
                    if (end != -1) {
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text.substring(i))
                        break
                    }
                }
                text.startsWith("~", i) -> {
                    val end = text.indexOf("~", startIndex = i + 1)
                    if (end != -1) {
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text.substring(i))
                        break
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

suspend fun loadInitialPosts(
    posts: SnapshotStateList<Post>,
    postsRef: DatabaseReference,
    userProfiles: SnapshotStateMap<String, Profile>,
    filterOption: String,
    filterValue: String,
    searchQuery: String,
    userId: String?,
    userProfile: Profile?
) {
    posts.clear()
    val query = when (filterOption) {
        "recent" -> postsRef.orderByChild("timestamp").limitToLast(10)
        "popular" -> postsRef.orderByChild("upvotes").limitToLast(10)
        "unpopular" -> postsRef.orderByChild("downvotes").limitToLast(10)
        "own echoes" -> {
            if (userId != null) {
                postsRef.orderByChild("userId").equalTo(userId).limitToLast(10)
            } else {
                postsRef.orderByChild("timestamp").limitToLast(10)
            }
        }
        else -> postsRef.orderByChild("timestamp").limitToLast(10)
    }

    val snapshot = query.get().await()

    handlePostSnapshot(
        snapshot = snapshot,
        posts = posts,
        userProfiles = userProfiles,
        filterOption = filterOption,
        filterValue = filterValue,
        searchQuery = searchQuery,
        userId = userId,
        userProfile = userProfile
    )
}


fun handlePostSnapshot(
    snapshot: DataSnapshot,
    posts: SnapshotStateList<Post>,
    userProfiles: SnapshotStateMap<String, Profile>,
    filterOption: String,
    filterValue: String,
    searchQuery: String,
    userId: String?,
    userProfile: Profile?
) {
    val userIdsToFetch = mutableSetOf<String>()
    val newPosts = mutableListOf<Post>()

    for (postSnapshot in snapshot.children) {
        try {
            val post = postSnapshot.getValue(Post::class.java)
            if (post != null) {
                var includePost = true

                // Check if we have the user's profile; if not, add to fetch list
                val profile = userProfiles[post.userId]
                if (profile == null) {
                    userIdsToFetch.add(post.userId)
                }

                // Apply filterOption
                when (filterOption) {
                    "own echoes" -> includePost = includePost && (post.userId == userId)
                    "city", "age", "level", "gender", "high-school", "college" -> {
                        if (profile != null) {
                            includePost = includePost && checkProfileFilter(profile, filterOption, filterValue)
                        } else {
                            includePost = false // Exclude for now, will include after fetching profile
                        }
                    }
                }

                // Apply search query filtering
                if (searchQuery.isNotEmpty()) {
                    val lowerSearchQuery = searchQuery.lowercase(Locale.getDefault())
                    val usernameMatch = post.username.lowercase(Locale.getDefault()).contains(lowerSearchQuery)
                    val userTagsMatch = post.userTags.any { tag ->
                        tag.lowercase(Locale.getDefault()).contains(lowerSearchQuery)
                    }

                    includePost = includePost && (usernameMatch || userTagsMatch)
                }

                if (includePost) {
                    newPosts.add(post)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("handlePostSnapshot", "Error deserializing post: ${e.message}", e)
        }
    }

    // Fetch user profiles if needed
    if (userIdsToFetch.isNotEmpty()) {
        fetchUserProfiles(userIdsToFetch, userProfiles) {
            // After fetching profiles, re-apply the filters
            handlePostSnapshot(snapshot, posts, userProfiles, filterOption, filterValue, searchQuery, userId, userProfile)
        }
        return // Exit the function to wait for profiles to be fetched
    }

    // Sort posts
    when (filterOption) {
        "recent", "own echoes" -> newPosts.sortByDescending { it.getPostTimestamp() }
        "popular" -> newPosts.sortByDescending { it.upvotes }
        "unpopular" -> newPosts.sortByDescending { it.downvotes }
    }

    posts.clear()
    posts.addAll(newPosts)
}


fun checkProfileFilter(profile: Profile, filterOption: String, filterValue: String): Boolean {
    return when (filterOption) {
        "country" -> profile.country.equals(filterValue, ignoreCase = true)
        "city" -> profile.city.equals(filterValue, ignoreCase = true)
        "age" -> {
            val age = calculateAge(profile.dob)
            val requiredAge = filterValue.toIntOrNull()
            age != null && requiredAge != null && age == requiredAge
        }
        "level" -> {
            val requiredLevel = filterValue.toIntOrNull()
            requiredLevel != null && profile.level == requiredLevel
        }
        "gender" -> profile.gender.equals(filterValue, ignoreCase = true)
        "high-school" -> profile.highSchool.equals(filterValue, ignoreCase = true)
        "college" -> profile.college.equals(filterValue, ignoreCase = true)
        else -> true
    }
}

fun fetchUserProfiles(
    userIds: Set<String>,
    userProfiles: SnapshotStateMap<String, Profile>,
    onComplete: () -> Unit
) {
    val remaining = userIds.toMutableSet()

    userIds.forEach { uid ->
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val profile = snapshot.getValue(Profile::class.java)
                if (profile != null) {
                    userProfiles[uid] = profile
                }
                remaining.remove(uid)
                if (remaining.isEmpty()) {
                    onComplete()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("fetchUserProfiles", "Error fetching profile: ${error.message}", error.toException())
                remaining.remove(uid)
                if (remaining.isEmpty()) {
                    onComplete()
                }
            }
        })
    }
}


fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun CustomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it) },
        placeholder = { Text("Search by username or tags", color = Color.Gray) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        textStyle = LocalTextStyle.current.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00bf63),
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color(0xFF00bf63)
        ),
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF00bf63))
            }
        }
    )
}

fun sharePostWithMatches(context: Context, post: Post) {
    // Assuming matches list exists, you can implement this logic to share the post in DMs.
    // For now, display a toast or similar action.
    Toast.makeText(context, "Post shared with your Matches!", Toast.LENGTH_SHORT).show()
}