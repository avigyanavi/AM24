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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale


class PostViewModel(application: Application) : AndroidViewModel(application) {

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
    private val friendsRef = FirebaseDatabase.getInstance().getReference("friends")
    private val matchesRef = FirebaseDatabase.getInstance().getReference("matches")

    /**
     * StateFlow holding the list of posts.
     */
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> get() = _posts

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

    // Update filteredPosts StateFlow
    val filteredPosts: StateFlow<List<Post>> = combine(
        _posts,
        _userProfiles,
        _filterSettings,
        currentUserIdFlow
    ) { posts, profiles, filterSettings, currentUserId ->
        applyFiltersAndSort(
            posts,
            profiles,
            filterSettings.filterOption,
            filterSettings.filterValue,
            filterSettings.searchQuery,
            filterSettings.sortOption,
            currentUserId
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Listener registration to remove when ViewModel is cleared
    private var postsListener: ValueEventListener? = null
    init {
        observePosts()
    }

    /**
     * Sets up a real-time listener to observe changes in "posts" node.
     */

    private fun observePosts() {
        postsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch(Dispatchers.IO) {
                    val postsList = snapshot.children.mapNotNull { it.getValue(Post::class.java) }
                    val sortedPosts = postsList.sortedByDescending { parseTimestamp(it.timestamp) }

                    // Fetch user profiles
                    val userIds = sortedPosts.map { it.userId }.toSet()
                    val profiles = fetchUserProfiles(userIds)

                    // Update the StateFlows
                    _userProfiles.value = profiles
                    _posts.value = sortedPosts
                    // The filteredPosts StateFlow will automatically update
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read posts: ${error.message}")
            }
        }
        postsRef.addValueEventListener(postsListener!!)
    }

    override fun onCleared() {
        super.onCleared()
        // Remove the listener to prevent memory leaks
        postsListener?.let { postsRef.removeEventListener(it) }
    }

    /**
     * Parses the timestamp from Firebase (could be Long or ServerValue.TIMESTAMP).
     */
    /**
     * Parses the timestamp from Firebase (could be Long or ServerValue.TIMESTAMP).
     * Returns a Long representing the timestamp in milliseconds.
     */
    private fun parseTimestamp(timestamp: Any?): Long {
        return when (timestamp) {
            is Long -> timestamp
            is Map<*, *> -> {
                // Check for known keys like .sv for ServerValue.TIMESTAMP or other timestamp formats
                val serverTimestamp = timestamp[".sv"] // Firebase usually uses ".sv" for server values
                if (serverTimestamp == "timestamp") {
                    // Interpret as current time, assuming that this was set by ServerValue.TIMESTAMP
                    System.currentTimeMillis()
                } else {
                    // In case of other structures, attempt a direct conversion
                    (timestamp["timestamp"] as? Long) ?: System.currentTimeMillis()
                }
            }
            else -> System.currentTimeMillis()
        }
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

    // Helper function to get the relationship between two users
    private suspend fun getRelationship(userId1: String, userId2: String): String {
        var isFriend = false
        var isMatch = false
        try {
            // Check if userId2 is a friend of userId1
            val friendSnapshot = friendsRef.child(userId1).child(userId2).get().await()
            val friendStatus = friendSnapshot.child("status").getValue(String::class.java)
            if (friendStatus == "accepted") {
                isFriend = true
            }

            // Check if userId2 is a match of userId1
            val matchSnapshot = matchesRef.child(userId1).child(userId2).get().await()
            if (matchSnapshot.exists()) {
                isMatch = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch relationship between $userId1 and $userId2: ${e.message}")
        }

        return when {
            isFriend && isMatch -> "friend and match"
            isFriend -> "friend"
            isMatch -> "match"
            else -> ""
        }
    }


    // Helper function to get friends and matches along with their relationship
    private suspend fun getFriendsAndMatches(userId: String): Map<String, String> {
        val relationships = mutableMapOf<String, String>()
        try {
            // Fetch friends
            val friendsSnapshot = friendsRef.child(userId).get().await()
            friendsSnapshot.children.forEach { child ->
                val status = child.child("status").getValue(String::class.java)
                if (status == "accepted") {
                    val friendId = child.key ?: ""
                    relationships[friendId] = "friend"
                }
            }

            // Fetch matches
            val matchesSnapshot = matchesRef.child(userId).get().await()
            matchesSnapshot.children.forEach { child ->
                val matchId = child.key ?: ""
                val existingRelation = relationships[matchId]
                if (existingRelation == "friend") {
                    relationships[matchId] = "friend and match"
                } else {
                    relationships[matchId] = "match"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch friends and matches: ${e.message}")
        }
        return relationships
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
        if (contentText.length > 75000) {
            onFailure("Text exceeds the maximum allowed length of 75,000 characters.")
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
                val relationships = getFriendsAndMatches(userId)
                relationships.forEach { (receiverId, relationship) ->
                    val message = "$username - your $relationship posted a new update."
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
                val relationships = getFriendsAndMatches(userId)
                relationships.forEach { (receiverId, relationship) ->
                    val message = "$username - your $relationship posted a new voice update."
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
                        val post = currentData.getValue(Post::class.java) ?: return Transaction.success(currentData)
                        if (post.upvotedUsers.containsKey(userId)) {
                            post.upvotedUsers.remove(userId)
                            post.upvotes -= 1
                        } else {
                            post.upvotedUsers[userId] = true
                            post.upvotes += 1
                            if (post.downvotedUsers.containsKey(userId)) {
                                post.downvotedUsers.remove(userId)
                                post.downvotes -= 1
                            }
                        }
                        currentData.value = post
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
                                    // Fetch the upvoter's username
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
                Log.e(TAG, "Error upvoting post: ${e.message}", e)
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
                        val post = currentData.getValue(Post::class.java) ?: return Transaction.success(currentData)
                        if (post.downvotedUsers.containsKey(userId)) {
                            // User already downvoted; remove downvote
                            post.downvotedUsers.remove(userId)
                            post.downvotes -= 1
                        } else {
                            // Add downvote
                            post.downvotedUsers[userId] = true
                            post.downvotes += 1
                            // If the user had upvoted before, remove the upvote
                            if (post.upvotedUsers.containsKey(userId)) {
                                post.upvotedUsers.remove(userId)
                                post.upvotes -= 1
                            }
                        }
                        currentData.value = post
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (error != null) {
                                Log.e(TAG, "Downvote transaction failed: ${error.message}")
                                onFailure("Downvote failed: ${error.message}")
                            } else if (committed) {
                                onSuccess()
                                // Send notification to post owner
                                val post = currentData?.getValue(Post::class.java)
                                if (post != null && post.userId != userId) {
                                    // Fetch the downvoter's username
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
                    Log.e(TAG, "Error downvoting post: ${e.message}", e)
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
                        val comment = currentData.getValue(Comment::class.java) ?: return Transaction.success(currentData)
                        if (comment.upvotedUsers.containsKey(userId)) {
                            // User already upvoted; remove upvote
                            comment.upvotedUsers.remove(userId)
                            comment.upvotes -= 1
                        } else {
                            // Add upvote
                            comment.upvotedUsers[userId] = true
                            comment.upvotes += 1
                            // If the user had downvoted before, remove the downvote
                            if (comment.downvotedUsers.containsKey(userId)) {
                                comment.downvotedUsers.remove(userId)
                                comment.downvotes -= 1
                            }
                        }
                        currentData.value = comment
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (error != null) {
                                Log.e("PostViewModel", "Upvote comment transaction failed: ${error.message}")
                                onFailure("Upvote failed: ${error.message}")
                            } else if (committed) {
                                onSuccess()
                                // Send notification to comment owner
                                val comment = currentData?.getValue(Comment::class.java)
                                if (comment != null && comment.userId != userId) {
                                    // Fetch the upvoter's username and relationship
                                    val upvoterUsername = withContext(Dispatchers.IO) { fetchUsernameById(userId) }
                                    val relationship = withContext(Dispatchers.IO) { getRelationship(userId, comment.userId) }
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
                Log.e("PostViewModel", "Error upvoting comment: ${e.message}", e)
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
                        val comment = currentData.getValue(Comment::class.java) ?: return Transaction.success(currentData)
                        if (comment.downvotedUsers.containsKey(userId)) {
                            // User already downvoted; remove downvote
                            comment.downvotedUsers.remove(userId)
                            comment.downvotes -= 1
                        } else {
                            // Add downvote
                            comment.downvotedUsers[userId] = true
                            comment.downvotes += 1
                            // If the user had upvoted before, remove the upvote
                            if (comment.upvotedUsers.containsKey(userId)) {
                                comment.upvotedUsers.remove(userId)
                                comment.upvotes -= 1
                            }
                        }
                        currentData.value = comment
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (error != null) {
                                Log.e("PostViewModel", "Downvote comment transaction failed: ${error.message}")
                                onFailure("Downvote failed: ${error.message}")
                            } else if (committed) {
                                onSuccess()
                                // Send notification to comment owner
                                val comment = currentData?.getValue(Comment::class.java)
                                if (comment != null && comment.userId != userId) {
                                    // Fetch the downvoter's username and relationship
                                    val downvoterUsername = withContext(Dispatchers.IO) { fetchUsernameById(userId) }
                                    val relationship = withContext(Dispatchers.IO) { getRelationship(userId, comment.userId) }
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
                Log.e("PostViewModel", "Error downvoting comment: ${e.message}", e)
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
        filterValue: String,
        searchQuery: String,
        sortOption: String,
        currentUserId: String?
    ): List<Post> {
        var filteredList = postsList

        Log.d(TAG, "Filter Option: $filterOption")
        Log.d(TAG, "Filter Value: $filterValue")
        Log.d(TAG, "Search Query: $searchQuery")
        Log.d(TAG, "Sort Option: $sortOption")

        Log.d(TAG, "Original posts count: ${filteredList.size}")

        // Apply "my posts" filter
        if (filterOption == "my posts" && currentUserId != null) {
            filteredList = filteredList.filter { post ->
                post.userId == currentUserId
            }
            Log.d(TAG, "After 'my posts' filter, count: ${filteredList.size}")
        }

        // Apply "voice only" filter
        if (filterOption == "voice only") {
            filteredList = filteredList.filter { post ->
                post.mediaType == "voice"
            }
            Log.d(TAG, "After 'voice only' filter, count: ${filteredList.size}")
        }

        // Apply profile-based filters
        if (filterOption in listOf("city", "age", "rating", "gender", "high-school", "college", "locality")) {
            filteredList = filteredList.filter { post ->
                val profile = profiles[post.userId]
                profile != null && checkProfileFilter(profile, filterOption, filterValue)
            }
            Log.d(TAG, "After profile-based filter, count: ${filteredList.size}")
        }

        // Apply search query filtering
        if (searchQuery.isNotEmpty()) {
            val lowerSearchQuery = searchQuery.lowercase(Locale.getDefault())
            filteredList = filteredList.filter { post ->
                val profile = profiles[post.userId]

                val fullName = ((profile?.username ?: "Unknown"))
                    .lowercase(Locale.getDefault())
                val nameMatch = fullName.contains(lowerSearchQuery)

                val userTagsMatch = post.userTags.any { tag ->
                    tag.lowercase(Locale.getDefault()).contains(lowerSearchQuery)
                }

                nameMatch || userTagsMatch
            }
            Log.d(TAG, "After search query filter, count: ${filteredList.size}")
        }

        // Sort posts
        filteredList = when (sortOption) {
            "Sort by Upvotes" -> filteredList.sortedByDescending { it.upvotes }
            "Sort by Downvotes" -> filteredList.sortedByDescending { it.downvotes }
            "No Sort" -> filteredList // Do not sort
            else -> filteredList.sortedByDescending { it.getPostTimestamp() }
        }

        return filteredList
    }


    /**
     * Function to check profile-based filters.
     */
    private fun checkProfileFilter(profile: Profile, filterOption: String, filterValue: String): Boolean {
        return when (filterOption) {
            "locality" -> {
                // Exact match (case-insensitive)
                profile.hometown.contains(filterValue, ignoreCase = true)
            }
            "city" -> {
                // Contains match (case-insensitive)
                profile.city.contains(filterValue, ignoreCase = true)
            }
            "age" -> {
                val age = calculateAge(profile.dob)
                val requiredAge = filterValue.toIntOrNull()
                age != null && requiredAge != null && age == requiredAge
            }
            "rating" -> {
                val userRating = profile.rating ?: 0.0
                when (filterValue) {
                    "0-2" -> userRating in 0.0..2.0
                    "3-4" -> userRating > 2 && userRating <= 4
                    "5" -> userRating > 4 && userRating <= 5
                    else -> false
                }
            }
            "gender" -> profile.gender.contains(filterValue, ignoreCase = true)
            "high-school" -> profile.highSchool.contains(filterValue, ignoreCase = true)
            "college" -> profile.college.contains(filterValue, ignoreCase = true)
            else -> true
        }
    }

    /**
     * Functions to update filter options.
     */
    fun setFilterOption(newOption: String) {
        _filterSettings.value = _filterSettings.value.copy(filterOption = newOption)
    }

    fun setFilterValue(newValue: String) {
        _filterSettings.value = _filterSettings.value.copy(filterValue = newValue)
    }

    fun setSearchQuery(newQuery: String) {
        _filterSettings.value = _filterSettings.value.copy(searchQuery = newQuery)
    }

    fun setSortOption(newSortOption: String) {
        _filterSettings.value = _filterSettings.value.copy(sortOption = newSortOption)
    }

    private suspend fun fetchUserProfiles(userIds: Set<String>): Map<String, Profile> = coroutineScope {
        val profiles = mutableMapOf<String, Profile>()
        val deferreds = userIds.map { userId ->
            async {
                val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
                val snapshot = userRef.get().await()
                snapshot.getValue(Profile::class.java)?.let { profile ->
                    profiles[userId] = profile
                }
            }
        }
        deferreds.awaitAll()
        profiles
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