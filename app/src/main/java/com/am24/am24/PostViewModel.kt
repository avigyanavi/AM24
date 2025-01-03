// PostViewModel.kt
package com.am24.am24

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


class PostViewModel(application: Application) : AndroidViewModel(application) {

    private var isFeedPaused = false
    // Firebase Realtime Database reference to "posts"
    private val postsRef = FirebaseDatabase.getInstance().getReference("posts")

    // Firebase Storage reference
    private val storageRef = FirebaseStorage.getInstance().reference

    // Define media size limits (in bytes)
    private val VOICE_MAX_SIZE = 5 * 1024 * 1024      // 5 MB

    // Define media time limits (in seconds)
    private val VOICE_MAX_DURATION = 60               // 1 minute

    // Tag for logging
    private val TAG = "PostViewModel"

    // Firebase Realtime Database reference to "notifications"
    private val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications")

    // Firebase Realtime Database reference to "friends" and "matches"
    private val matchesRef = FirebaseDatabase.getInstance().getReference("matches")

    /**
     * StateFlow holding the list of posts.
     */
    private val _posts = MutableStateFlow<List<Post>>(emptyList())

    private val _userProfiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val userProfiles: StateFlow<Map<String, Profile>> get() = _userProfiles

    // MutableStateFlow for filter settings
    private val _filterSettings = MutableStateFlow(FilterSettings())
    val filterSettings: StateFlow<FilterSettings> get() = _filterSettings

    // Add currentUserId StateFlow
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserIdFlow: StateFlow<String?> get() = _currentUserId

    fun setCurrentUserId(userId: String?) {
        _currentUserId.value = userId
    }

    private val _feedFilters = MutableStateFlow(FilterSettings())
    val feedFilters: StateFlow<FilterSettings> get() = _feedFilters

    fun setFeedFilters(newFilters: FilterSettings) {
        _feedFilters.value = newFilters
    }

    fun setIsVoiceOnly(isVoiceOnly: Boolean) {
        _filterSettings.value = _filterSettings.value.copy(isVoiceOnly = isVoiceOnly)
    }
    private val _filtersLoaded = MutableStateFlow(false)
    val filtersLoaded: StateFlow<Boolean> get() = _filtersLoaded


    // Update filteredPosts to combine both filters
    val filteredPosts: StateFlow<List<Post>> = combine(
        _posts.filter { it.isNotEmpty() },
        _userProfiles.filter { it.isNotEmpty() },
        _filterSettings,
        _feedFilters,
        currentUserIdFlow
    ) { posts, profiles, homeFilters, feedFilters, currentUserId ->
        Log.d(TAG, "Combining filters with ${posts.size} posts and ${profiles.size} profiles")
        val homeFilteredPosts = applyFiltersAndSort(
            posts,
            profiles,
            homeFilters.filterOption,
            homeFilters.searchQuery,
            homeFilters.sortOption,
            currentUserId,
            isVoiceOnly = homeFilters.isVoiceOnly
        )
        val finalFilteredPosts = applyFiltersAndSort(
            homeFilteredPosts,
            profiles,
            feedFilters.filterOption,
            homeFilters.searchQuery,
            homeFilters.sortOption,
            currentUserId,
            feedFilters.feedFilters,
        )
        finalFilteredPosts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())




    // Listener registration to remove when ViewModel is cleared
    private var postsListener: ValueEventListener? = null

    fun refreshPosts() {
        Log.d(TAG, "Refreshing posts...")
        observePosts() // Re-attach listener
    }

    /**
     * Sets up a real-time listener to observe changes in "posts" node.
     */

    private fun observePosts() {
        if (postsListener == null) { // Avoid re-adding listener if already active
            postsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val postsList = snapshot.children.mapNotNull { it.getValue(Post::class.java) }
                        val sortedPosts = postsList.sortedByDescending { it.getTimestampLong() }
                        val userIds = sortedPosts.map { it.userId }.toSet()
                        val profiles = fetchUserProfiles(userIds)

                        Log.d(TAG, "Fetched ${sortedPosts.size} posts and ${profiles.size} profiles")

                        _userProfiles.value = profiles
                        _posts.value = sortedPosts

                        // Confirm StateFlow update
                        Log.d(TAG, "Updated _posts with ${_posts.value.size} posts")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to observe posts: ${error.message}")
                }
            }
            postsRef.addValueEventListener(postsListener!!)
        }

        // Trigger a one-time fetch for immediate data availability
        viewModelScope.launch {
            try {
                val snapshot = postsRef.get().await()
                val postsList = snapshot.children.mapNotNull { it.getValue(Post::class.java) }
                val sortedPosts = postsList.sortedByDescending { it.getTimestampLong() }
                val userIds = sortedPosts.map { it.userId }.toSet()
                val profiles = fetchUserProfiles(userIds)

                Log.d(TAG, "One-time fetch: ${sortedPosts.size} posts and ${profiles.size} profiles")

                _userProfiles.value = profiles
                _posts.value = sortedPosts
            } catch (e: Exception) {
                Log.e(TAG, "One-time fetch failed: ${e.message}")
            }
        }
    }


    // Pause observing posts
    fun pauseFeed() {
        isFeedPaused = true
        postsListener?.let {
            FirebaseDatabase.getInstance().getReference("posts").removeEventListener(it)
        }
    }

    // Resume observing posts
    fun resumeFeed() {
        if (!isFeedPaused) return
        isFeedPaused = false
        observePosts()
    }

    override fun onCleared() {
        super.onCleared()
        // Remove the listener to prevent memory leaks
        postsListener?.let { postsRef.removeEventListener(it) }
        pauseFeed() // Cleanup listeners to prevent memory leaks
    }


    // Helper function to send a notification
    private suspend fun sendNotification(
        receiverId: String,
        type: String,
        senderId: String,
        senderUsername: String,
        message: String
    ) {
        try {
            val timestamp = System.currentTimeMillis()
            val notificationId = notificationsRef.child(receiverId).push().key
                ?: throw Exception("Failed to generate notification ID")

            val notification = Notification(
                id = notificationId,
                type = type,
                senderId = senderId,
                senderUsername = senderUsername,
                message = message,
                timestamp = timestamp,
                isRead = "false"
            )
            notificationsRef.child(receiverId).child(notificationId).setValue(notification).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification: ${e.message}")
            // Handle the error if necessary
        }
    }

    // Helper function to fetch a username by user ID
    private suspend fun fetchUsernameById(userId: String): String {
        return try {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
            val snapshot = userRef.child("username").get().await()
            snapshot.getValue(String::class.java) ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch username: ${e.message}")
            "Unknown"
        }
    }

    private suspend fun getRelationship(userId1: String, userId2: String): String {
        try {
            val matchSnapshot = matchesRef.child(userId1).child(userId2).get().await()
            if (matchSnapshot.exists()) {
                return "match"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch relationship: ${e.message}")
        }
        return ""
    }

    /**
     * Function to create a text post.
     */
    fun createTextPost(
        userId: String,
        username: String,
        contentText: String,
        userTags: List<String>,
        fontFamily: String,
        fontSize: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Enforce text character limit
        if (contentText.length > 10000) {
            onFailure("Text exceeds the maximum allowed length of 10,000 characters.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val postId = postsRef.push().key
            if (postId == null) {
                onFailure("Unable to generate post ID.")
                return@launch
            }

            val post = mapOf(
                "postId" to postId,
                "userId" to userId,
                "username" to username,
                "contentText" to contentText,
                "timestamp" to ServerValue.TIMESTAMP,
                "userTags" to userTags,
                "fontFamily" to fontFamily,
                "fontSize" to fontSize,
                "mediaType" to null,
                "mediaUrl" to null,
                "upvotes" to 0,
                "downvotes" to 0,
                "upvotedUsers" to emptyMap<String, Boolean>(),
                "downvotedUsers" to emptyMap<String, Boolean>(),
                "totalComments" to 0
            )

            try {
                postsRef.child(postId).setValue(post).await()
                onSuccess()
                // Send notifications to friends and matches
                val matches = getMatches(userId)
                matches.forEach { receiverId ->
                    val message = "$username - your match posted a new text update."
                    sendNotification(
                        receiverId = receiverId,
                        type = "new_post",
                        senderId = userId,
                        senderUsername = username,
                        message = message
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating text post: ${e.message}", e)
                onFailure(e.message ?: "Unknown error occurred.")
            }
        }
    }

    /**
     * Function to create a voice post.
     */
    fun createVoicePost(
        userId: String,
        username: String,
        voiceUri: Uri,
        userTags: List<String>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure audio is in MP3 format
                val mp3Uri = ensureMp3Format(voiceUri, onFailure) ?: return@launch

                // Check audio duration
                val duration = getAudioDuration(mp3Uri, onFailure) ?: return@launch
                if (duration > VOICE_MAX_DURATION) {
                    onFailure("Voice recording exceeds the maximum allowed duration of $VOICE_MAX_DURATION seconds.")
                    return@launch
                }

                // Check and compress audio if necessary
                val compressedVoiceUri = compressAudioIfNeeded(mp3Uri, VOICE_MAX_SIZE, onFailure) ?: return@launch

                // Upload voice recording to Firebase Storage
                val downloadUrl = uploadMediaToStorage(compressedVoiceUri, "voices", onFailure) ?: return@launch

                // Generate post ID
                val postId = postsRef.push().key
                if (postId == null) {
                    onFailure("Unable to generate post ID.")
                    return@launch
                }

                // Create Post object
                val post = mapOf(
                    "postId" to postId,
                    "userId" to userId,
                    "username" to username,
                    "contentText" to null,
                    "timestamp" to ServerValue.TIMESTAMP, // Pass the special map for server timestamp
                    "userTags" to userTags,
                    "mediaType" to "voice",
                    "mediaUrl" to downloadUrl,
                    "voiceDuration" to duration,
                    "upvotes" to 0,
                    "downvotes" to 0,
                    "upvotedUsers" to emptyMap<String, Boolean>(),
                    "downvotedUsers" to emptyMap<String, Boolean>(),
                    "totalComments" to 0
                )

                // Save post to Realtime Database
                postsRef.child(postId).setValue(post).await()

                onSuccess()

                // Send notifications to friends and matches
                val matches = getMatches(userId)
                matches.forEach { receiverId ->
                    val message = "$username - your match posted a new voice update."
                    sendNotification(
                        receiverId = receiverId,
                        type = "new_post",
                        senderId = userId,
                        senderUsername = username,
                        message = message
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating voice post: ${e.message}", e)
                onFailure(e.message ?: "Unknown error occurred.")
            }
        }
    }

    /**
     * Helper function to upload media to Firebase Storage.
     */
    private suspend fun uploadMediaToStorage(
        mediaUri: Uri,
        mediaType: String,
        onFailure: (String) -> Unit
    ): String? {
        return try {
            val fileName = "${System.currentTimeMillis()}_${mediaUri.lastPathSegment}"
            val storageReference = storageRef.child("$mediaType/$fileName")

            // Upload the file
            storageReference.putFile(mediaUri).await()

            // Get the download URL
            storageReference.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading media: ${e.message}", e)
            onFailure(e.message ?: "Media upload failed.")
            null
        }
    }

    /**
     * Ensures that the audio is in MP3 format.
     */
    private suspend fun ensureMp3Format(voiceUri: Uri, onFailure: (String) -> Unit): Uri? {
        // Placeholder: Implement audio format conversion if necessary.
        // For simplicity, assume audio is already in MP3.
        return voiceUri
    }

    /**
     * Compresses the audio if it exceeds the maximum allowed size.
     */
    private suspend fun compressAudioIfNeeded(audioUri: Uri, maxSize: Int, onFailure: (String) -> Unit): Uri? {
        // Placeholder: Implement audio compression using libraries like FFmpeg.
        // For simplicity, assume audio is within size limits.
        return audioUri
    }

    /**
     * Retrieves the duration of an audio file in seconds.
     */
    private suspend fun getAudioDuration(audioUri: Uri, onFailure: (String) -> Unit): Int? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(getApplication<Application>(), audioUri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLongOrNull()?.div(1000)?.toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving audio duration: ${e.message}", e)
            onFailure("Failed to retrieve audio duration.")
            null
        }
    }

    /**
     * Function to upvote a post.
     */
    fun upvotePost(
        postId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val postReference = postsRef.child(postId)
            try {
                postReference.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.value == null) {
                            return Transaction.success(currentData)
                        }

                        val post = currentData.getValue(Post::class.java) ?: return Transaction.success(currentData)
                        // Ensure timestamp is correctly handled
                        val originalTimestamp = post.timestamp

                        // Read the current values
                        var upvotes = currentData.child("upvotes").getValue(Int::class.java) ?: 0
                        var downvotes = currentData.child("downvotes").getValue(Int::class.java) ?: 0
                        val upvotedUsers = currentData.child("upvotedUsers").getValue<HashMap<String, Boolean>>()?.toMutableMap() ?: mutableMapOf()
                        val downvotedUsers = currentData.child("downvotedUsers").getValue<HashMap<String, Boolean>>()?.toMutableMap() ?: mutableMapOf()


                        // Modify the vote counts and user lists
                        if (upvotedUsers.containsKey(userId)) {
                            upvotedUsers.remove(userId)
                            upvotes -= 1
                        } else {
                            upvotedUsers[userId] = true
                            upvotes += 1
                            if (downvotedUsers.containsKey(userId)) {
                                downvotedUsers.remove(userId)
                                downvotes -= 1
                            }
                        }

                        // Update only the necessary fields
                        currentData.child("upvotes").value = upvotes
                        currentData.child("downvotes").value = downvotes
                        currentData.child("upvotedUsers").value = upvotedUsers
                        currentData.child("downvotedUsers").value = downvotedUsers
                        currentData.child("timestamp").value = originalTimestamp

                        // Do not modify other fields like timestamp
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (error != null) {
                                onFailure("Upvote failed: ${error.message}")
                            } else if (committed) {
                                onSuccess()
                                // Send notification to post owner
                                val post = currentData?.getValue(Post::class.java)
                                if (post != null && post.userId != userId) {
                                    val upvoterUsername = fetchUsernameById(userId)
                                    val message = "$upvoterUsername upvoted your post."
                                    sendNotification(
                                        receiverId = post.userId,
                                        type = "post_upvote",
                                        senderId = userId,
                                        senderUsername = upvoterUsername,
                                        message = message
                                    )
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(e.message ?: "Upvote failed.")
                }
            }
        }
    }


    /**
     * Function to downvote a post.
     */
    fun downvotePost(
        postId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val postReference = postsRef.child(postId)
            try {
                postReference.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.value == null) {
                            return Transaction.success(currentData)
                        }
                        val post = currentData.getValue(Post::class.java) ?: return Transaction.success(currentData)
                        // Ensure timestamp is correctly handled
                        val originalTimestamp = post.timestamp

                        // Read the current values
                        var upvotes = currentData.child("upvotes").getValue(Int::class.java) ?: 0
                        var downvotes = currentData.child("downvotes").getValue(Int::class.java) ?: 0
                        val upvotedUsers = currentData.child("upvotedUsers").getValue<HashMap<String, Boolean>>()?.toMutableMap() ?: mutableMapOf()
                        val downvotedUsers = currentData.child("downvotedUsers").getValue<HashMap<String, Boolean>>()?.toMutableMap() ?: mutableMapOf()


                        // Modify the vote counts and user lists
                        if (downvotedUsers.containsKey(userId)) {
                            downvotedUsers.remove(userId)
                            downvotes -= 1
                        } else {
                            downvotedUsers[userId] = true
                            downvotes += 1
                            if (upvotedUsers.containsKey(userId)) {
                                upvotedUsers.remove(userId)
                                upvotes -= 1
                            }
                        }

                        // Update only the necessary fields
                        currentData.child("upvotes").value = upvotes
                        currentData.child("downvotes").value = downvotes
                        currentData.child("upvotedUsers").value = upvotedUsers
                        currentData.child("downvotedUsers").value = downvotedUsers
                        currentData.child("timestamp").value = originalTimestamp

                        // Do not modify other fields like timestamp
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (error != null) {
                                onFailure("Downvote failed: ${error.message}")
                            } else if (committed) {
                                onSuccess()
                                // Send notification to post owner
                                val post = currentData?.getValue(Post::class.java)
                                if (post != null && post.userId != userId) {
                                    val downvoterUsername = fetchUsernameById(userId)
                                    val message = "$downvoterUsername downvoted your post."
                                    sendNotification(
                                        receiverId = post.userId,
                                        type = "post_downvote",
                                        senderId = userId,
                                        senderUsername = downvoterUsername,
                                        message = message
                                    )
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(e.message ?: "Downvote failed.")
                }
            }
        }
    }


    /**
     * Function to upvote a comment.
     */
    fun upvoteComment(
        postId: String,
        commentId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val commentReference = postsRef.child(postId).child("comments").child(commentId)
            try {
                commentReference.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.value == null) {
                            return Transaction.success(currentData)
                        }

                        // Read the current values
                        var upvotes = currentData.child("upvotes").getValue(Int::class.java) ?: 0
                        var downvotes = currentData.child("downvotes").getValue(Int::class.java) ?: 0
                        val upvotedUsers = currentData.child("upvotedUsers").getValue<HashMap<String, Boolean>>()?.toMutableMap() ?: mutableMapOf()
                        val downvotedUsers = currentData.child("downvotedUsers").getValue<HashMap<String, Boolean>>()?.toMutableMap() ?: mutableMapOf()

                        // Modify the vote counts and user lists
                        if (upvotedUsers.containsKey(userId)) {
                            upvotedUsers.remove(userId)
                            upvotes -= 1
                        } else {
                            upvotedUsers[userId] = true
                            upvotes += 1
                            if (downvotedUsers.containsKey(userId)) {
                                downvotedUsers.remove(userId)
                                downvotes -= 1
                            }
                        }

                        // Update only the necessary fields
                        currentData.child("upvotes").value = upvotes
                        currentData.child("downvotes").value = downvotes
                        currentData.child("upvotedUsers").value = upvotedUsers
                        currentData.child("downvotedUsers").value = downvotedUsers

                        // Do not modify other fields like timestamp
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (error != null) {
                                onFailure("Upvote failed: ${error.message}")
                            } else if (committed) {
                                onSuccess()
                                // Send notification to comment owner
                                val comment = currentData?.getValue(Comment::class.java)
                                if (comment != null && comment.userId != userId) {
                                    val upvoterUsername = fetchUsernameById(userId)
                                    val relationship = getRelationship(userId, comment.userId)
                                    val relationshipText = if (relationship.isNotEmpty()) " - your $relationship" else ""
                                    val message = "$upvoterUsername$relationshipText upvoted your comment."
                                    sendNotification(
                                        receiverId = comment.userId,
                                        type = "comment_upvote",
                                        senderId = userId,
                                        senderUsername = upvoterUsername,
                                        message = message
                                    )
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(e.message ?: "Upvote failed.")
                }
            }
        }
    }


    /**
     * Function to downvote a comment.
     */
    fun downvoteComment(
        postId: String,
        commentId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val commentReference = postsRef.child(postId).child("comments").child(commentId)
            try {
                commentReference.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        if (currentData.value == null) {
                            return Transaction.success(currentData)
                        }

                        // Read the current values
                        var upvotes = currentData.child("upvotes").getValue(Int::class.java) ?: 0
                        var downvotes = currentData.child("downvotes").getValue(Int::class.java) ?: 0
                        val upvotedUsers = currentData.child("upvotedUsers").getValue<HashMap<String, Boolean>>()?.toMutableMap() ?: mutableMapOf()
                        val downvotedUsers = currentData.child("downvotedUsers").getValue<HashMap<String, Boolean>>()?.toMutableMap() ?: mutableMapOf()

                        // Modify the vote counts and user lists
                        if (downvotedUsers.containsKey(userId)) {
                            downvotedUsers.remove(userId)
                            downvotes -= 1
                        } else {
                            downvotedUsers[userId] = true
                            downvotes += 1
                            if (upvotedUsers.containsKey(userId)) {
                                upvotedUsers.remove(userId)
                                upvotes -= 1
                            }
                        }

                        // Update only the necessary fields
                        currentData.child("upvotes").value = upvotes
                        currentData.child("downvotes").value = downvotes
                        currentData.child("upvotedUsers").value = upvotedUsers
                        currentData.child("downvotedUsers").value = downvotedUsers


                        // Do not modify other fields like timestamp
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (error != null) {
                                onFailure("Downvote failed: ${error.message}")
                            } else if (committed) {
                                onSuccess()
                                // Send notification to comment owner
                                val comment = currentData?.getValue(Comment::class.java)
                                if (comment != null && comment.userId != userId) {
                                    val downvoterUsername = fetchUsernameById(userId)
                                    val relationship = getRelationship(userId, comment.userId)
                                    val relationshipText = if (relationship.isNotEmpty()) " - your $relationship" else ""
                                    val message = "$downvoterUsername$relationshipText downvoted your comment."
                                    sendNotification(
                                        receiverId = comment.userId,
                                        type = "comment_downvote",
                                        senderId = userId,
                                        senderUsername = downvoterUsername,
                                        message = message
                                    )
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(e.message ?: "Downvote failed.")
                }
            }
        }
    }


    /**
     * Function to add a comment to a post.
     */
    fun addComment(
        postId: String,
        comment: Comment,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val commentsRef = postsRef.child(postId).child("comments")
                val commentId = commentsRef.push().key
                if (commentId == null) {
                    onFailure("Unable to generate comment ID.")
                    return@launch
                }

                val newComment = comment.copy(commentId = commentId)
                commentsRef.child(commentId).setValue(newComment).await()

                // Optionally, update totalComments count
                val totalCommentsRef = postsRef.child(postId).child("totalComments")
                totalCommentsRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        var total = currentData.getValue(Int::class.java) ?: 0
                        total += 1
                        currentData.value = total
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        if (error != null) {
                            Log.e(TAG, "Updating total comments failed: ${error.message}")
                            onFailure("Failed to update comment count.")
                        } else if (committed) {
                            onSuccess()
                            // Send notification to post owner
                            viewModelScope.launch(Dispatchers.Main) {
                                val postSnapshot = postsRef.child(postId).get().await()
                                val post = postSnapshot.getValue(Post::class.java)
                                if (post != null && post.userId != comment.userId) {
                                    // Fetch the commenter's username
                                    val commenterUsername = fetchUsernameById(comment.userId)
                                    val message = "$commenterUsername commented on your post."
                                    sendNotification(
                                        receiverId = post.userId,
                                        type = "post_comment",
                                        senderId = comment.userId,
                                        senderUsername = commenterUsername,
                                        message = message
                                    )
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error adding comment: ${e.message}", e)
                onFailure(e.message ?: "Failed to add comment.")
            }
        }
    }

    /**
     * Function to share a post with matches.
     * Implementation depends on your app's specific sharing mechanism.
     */
    fun sharePostWithMatches(
        postId: String,
        matches: List<String>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Example: Create a share entry in each match's "sharedPosts" node
                val sharedPostsRef = FirebaseDatabase.getInstance().getReference("sharedPosts")
                matches.forEach { matchId ->
                    sharedPostsRef.child(matchId).child(postId).setValue(true).await()
                }
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing post: ${e.message}", e)
                onFailure(e.message ?: "Failed to share post.")
            }
        }
    }


    /**
     * Function to apply filters and sorting to the list of posts.
     */
    /**
     * Function to apply filters and sorting to the list of posts.
     */
    private fun applyFiltersAndSort(
        postsList: List<Post>,
        profiles: Map<String, Profile>,
        filterOption: String,
        searchQuery: String,
        sortOption: String,
        currentUserId: String?,
        feedFilters: FeedFilterSettings? = null,
        isVoiceOnly: Boolean = false
    ): List<Post> {
        var filteredList = postsList

        // Special case: "My Posts" bypasses all other filters
        if (filterOption == "my posts" && currentUserId != null) {
            filteredList = filteredList.filter { post -> post.userId == currentUserId }
            Log.d(TAG, "Applying 'My Posts' filter: Showing only posts by user $currentUserId")
            return filteredList // Early return ensures no further filters are applied
        }
        // Apply Voice Only filter
        if (isVoiceOnly) {
            filteredList = filteredList.filter { post -> post.mediaType == "voice" }
        }

        // Apply HomeScreen filters
        when (filterOption) {
            "everyone" -> Log.d(TAG, "Filter Option: everyone (no filtering)")
            "matches" -> {
                filteredList = filteredList.filter { post ->
                    profiles[post.userId]?.relationship == "match"
                }
            }
        }

        // Apply Feed Filters
        feedFilters?.let { filters ->

            // Gender filter
            if (filters.gender.isNotBlank()) {
                filteredList = filteredList.filter { post ->
                    val profileGender = profiles[post.userId]?.gender ?: ""
                    profileGender.equals(filters.gender, ignoreCase = true)
                }
            }

            // Age range filter
            if (filters.ageStart != 0 && filters.ageEnd != 0) {
                filteredList = filteredList.filter { post ->
                    val age = profiles[post.userId]?.dob?.let { calculateAge(it) }
                    age != null && (age in filters.ageStart..filters.ageEnd)
                }
            }

            // Rating filter
            if (filters.rating.isNotBlank()) {
                filteredList = filteredList.filter { post ->
                    val profile = profiles[post.userId]
                    val averageRating = profile?.averageRating ?: 0.0
                    val ratingRange = when (filters.rating) {
                        "0-2" -> 0.0..2.0
                        "2-4" -> 2.0..4.0
                        "4-5" -> 4.0..5.0
                        else -> 0.0..5.0
                    }
                    averageRating in ratingRange
                }
            }

            // City filter
            if (filters.city.isNotBlank() && filters.city != "All") {
                filteredList = filteredList.filter { post ->
                    val profileCity = profiles[post.userId]?.city ?: ""
                    profileCity.equals(filters.city, ignoreCase = true)
                }
            }

            // Localities filter
            if (filters.localities.isNotEmpty()) {
                filteredList = filteredList.filter { post ->
                    val profileLocality = profiles[post.userId]?.hometown ?: ""
                    filters.localities.contains(profileLocality)
                }
            }

            // High School filter
            if (filters.highSchool.isNotBlank()) {
                filteredList = filteredList.filter { post ->
                    val profileHighSchool = profiles[post.userId]?.highSchool ?: ""
                    profileHighSchool.equals(filters.highSchool, ignoreCase = true)
                }
            }

            // College filter
            if (filters.college.isNotBlank()) {
                filteredList = filteredList.filter { post ->
                    val profileCollege = profiles[post.userId]?.college ?: ""
                    profileCollege.equals(filters.college, ignoreCase = true)
                }
            }

            // PostGrad filter
            if (filters.postGrad.isNotBlank()) {
                filteredList = filteredList.filter { post ->
                    val profilePostGrad = profiles[post.userId]?.postGraduation ?: ""
                    profilePostGrad.equals(filters.postGrad, ignoreCase = true)
                }
            }

            // Work filter
            if (filters.work.isNotBlank()) {
                filteredList = filteredList.filter { post ->
                    val profileWork = profiles[post.userId]?.work ?: ""
                    profileWork.equals(filters.work, ignoreCase = true)
                }
            }

            // Additional filters can be added here
        }

        // Apply search query
        if (searchQuery.isNotBlank()) {
            filteredList = filteredList.filter { post ->
                post.contentText?.contains(searchQuery, ignoreCase = true) == true ||
                        post.userTags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
            }
        }

        // Sorting with a secondary key (timestamp) to stabilize sort order
        filteredList = when (sortOption) {
            "Sort by Upvotes" -> filteredList.sortedWith(
                compareByDescending<Post> { it.upvotes }
                    .thenByDescending { it.getTimestampLong() }
            )
            "Sort by Downvotes" -> filteredList.sortedWith(
                compareByDescending<Post> { it.downvotes }
                    .thenByDescending { it.getTimestampLong() }
            )
            else -> filteredList.sortedByDescending { it.getTimestampLong() }
        }
        Log.d(TAG, "After Sorting: ${filteredList.size} posts sorted")

        return filteredList
    }


    fun loadFiltersFromFirebase(userId: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("feedFilters")
        userRef.get().addOnSuccessListener { snapshot ->
            val feedFilters = snapshot.getValue(FeedFilterSettings::class.java)
            if (feedFilters != null) {
                // Use setFeedFilters to update _feedFilters
                setFeedFilters(_feedFilters.value.copy(feedFilters = feedFilters))
            } else {
                // Optionally, set default feedFilters
                setFeedFilters(_feedFilters.value.copy(feedFilters = FeedFilterSettings()))
            }
            // Set filtersLoaded to true here
            _filtersLoaded.value = true
        }.addOnFailureListener {
            // Even if there's an error, set filtersLoaded to true to proceed
            _filtersLoaded.value = true
        }
    }


    /**
     * Functions to update filter options.
     */
    fun setFilterOption(newOption: String) {
        Log.d(TAG, "Filter Option Changed to: $newOption")

        // Update filter settings
        _filterSettings.value = _filterSettings.value.copy(filterOption = newOption)

        // Reset feed filters when switching filters (except for "my posts")
        if (newOption != "my posts") {
            setFeedFilters(FilterSettings())
        }
    }


    fun setSearchQuery(newQuery: String) {
        Log.d(TAG, "Search Query Changed to: $newQuery")
        _filterSettings.value = _filterSettings.value.copy(searchQuery = newQuery)
    }

    fun setSortOption(newSortOption: String) {
        Log.d(TAG, "Sort Option Changed to: $newSortOption")
        _filterSettings.value = _filterSettings.value.copy(sortOption = newSortOption)
    }


    private suspend fun fetchUserProfiles(userIds: Set<String>): Map<String, Profile> = coroutineScope {
        val currentUserId = currentUserIdFlow.value ?: ""
        val matches = getMatches(currentUserId)

        val profiles = mutableMapOf<String, Profile>()
        val deferreds = userIds.map { userId ->
            async {
                val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
                val snapshot = userRef.get().await()
                snapshot.getValue(Profile::class.java)?.let { profile ->
                    // Set relationship to "match" if userId is in matches
                    profile.relationship = if (matches.contains(userId)) "match" else null
                    profiles[userId] = profile
                }
            }
        }
        deferreds.awaitAll()
        profiles
    }


    private suspend fun getMatches(userId: String): List<String> {
        val matches = mutableListOf<String>()
        try {
            val matchesSnapshot = matchesRef.child(userId).get().await()
            matchesSnapshot.children.forEach { child ->
                child.key?.let { matches.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch matches: ${e.message}")
        }
        return matches
    }



    fun deletePost(
        postId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val postRef = FirebaseDatabase.getInstance().getReference("posts").child(postId)
                postRef.removeValue().await()
                onSuccess()
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error deleting post: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onFailure(e.message ?: "Failed to report post.")
                }
            }
        }
    }

    /**
     * Function to save a post for a user.
     */
    fun savePost(
        postId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Reference to the "savedPosts" node for the user
                val savedPostsRef = FirebaseDatabase.getInstance()
                    .getReference("savedPosts")
                    .child(userId)
                    .child(postId)

                // Save the post by setting it as true in "savedPosts"
                savedPostsRef.setValue(true).await()

                withContext(Dispatchers.Main) {
                    onSuccess() // Call success callback
                }
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error saving post: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onFailure(e.message ?: "Failed to save post.")
                }
            }
        }
    }


    /**
     * Function to report a post.
     */
    fun reportPost(
        postId: String,
        reporterId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reportsRef = FirebaseDatabase.getInstance().getReference("reportedPosts").child(postId)
                val reportId = reportsRef.push().key
                if (reportId == null) {
                    onFailure("Unable to generate report ID.")
                    return@launch
                }

                val report = mapOf(
                    "reportId" to reportId,
                    "postId" to postId,
                    "reporterId" to reporterId,
                    "timestamp" to ServerValue.TIMESTAMP
                )

                reportsRef.child(reportId).setValue(report).await()
                onSuccess()
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error reporting post: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onFailure(e.message ?: "Failed to report post.")
                }
            }
        }
    }


}