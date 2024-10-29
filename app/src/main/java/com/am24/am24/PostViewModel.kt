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
    private val PHOTO_MAX_SIZE = 10 * 1024 * 1024      // 10 MB
    private val VIDEO_MAX_SIZE = 50 * 1024 * 1024     // 50 MB
    private val VOICE_MAX_SIZE = 5 * 1024 * 1024      // 5 MB

    // Define media time limits (in seconds)
    private val VOICE_MAX_DURATION = 60               // 1 minute
    private val VIDEO_MAX_DURATION = 20               // 20 seconds

    // Tag for logging
    private val TAG = "PostViewModel"

    /**
     * StateFlow holding the list of posts.
     */
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> get() = _posts
    private val _userProfiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val userProfiles: StateFlow<Map<String, Profile>> get() = _userProfiles

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
                    withContext(Dispatchers.Main) {
                        _posts.value = sortedPosts
                    }
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
                "timestamp" to ServerValue.TIMESTAMP, // Pass the special map for server timestamp
                "userTags" to userTags,
                "fontFamily" to fontFamily,
                "fontSize" to fontSize,
                "mediaType" to null,
                "mediaUrl" to null
            )

            try {
                postsRef.child(postId).setValue(post).await()
                onSuccess()
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
                    "voiceDuration" to duration
                )

                // Save post to Realtime Database
                postsRef.child(postId).setValue(post).await()

                onSuccess()
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
     * Ensures that the image is in JPEG format.
     */
    private suspend fun ensureJpegFormat(imageUri: Uri, onFailure: (String) -> Unit): Uri? {
        return try {
            val contentResolver: ContentResolver = getApplication<Application>().contentResolver
            val inputStream = contentResolver.openInputStream(imageUri) ?: run {
                onFailure("Unable to open image for conversion.")
                return null
            }
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Compress bitmap to JPEG
            val jpegFile = File.createTempFile("jpeg_", ".jpg", getApplication<Application>().cacheDir)
            val outputStream = FileOutputStream(jpegFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            Uri.fromFile(jpegFile)
        } catch (e: IOException) {
            Log.e(TAG, "Error converting image to JPEG: ${e.message}", e)
            onFailure("Image format conversion failed.")
            null
        }
    }

    /**
     * Ensures that the video is in MP4 format.
     */
    private suspend fun ensureMp4Format(videoUri: Uri, onFailure: (String) -> Unit): Uri? {
        // Placeholder: Implement video format conversion if necessary.
        // For simplicity, assume video is already in MP4.
        return videoUri
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
     * Compresses the image if it exceeds the maximum allowed size.
     */
    private suspend fun compressImageIfNeeded(imageUri: Uri, maxSize: Int, onFailure: (String) -> Unit): Uri? {
        return try {
            val contentResolver: ContentResolver = getApplication<Application>().contentResolver
            val inputStream = contentResolver.openInputStream(imageUri) ?: run {
                onFailure("Unable to open image for compression.")
                return null
            }
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            var quality = 85
            var byteArray: ByteArray

            do {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                byteArray = outputStream.toByteArray()
                outputStream.close()
                quality -= 5
            } while (byteArray.size > maxSize && quality > 5)

            val compressedFile = File.createTempFile("compressed_", ".jpg", getApplication<Application>().cacheDir)
            val fos = FileOutputStream(compressedFile)
            fos.write(byteArray)
            fos.flush()
            fos.close()

            Uri.fromFile(compressedFile)
        } catch (e: IOException) {
            Log.e(TAG, "Error compressing image: ${e.message}", e)
            onFailure("Image compression failed.")
            null
        }
    }

    /**
     * Compresses the video if it exceeds the maximum allowed size.
     */
    private suspend fun compressVideoIfNeeded(videoUri: Uri, maxSize: Int, onFailure: (String) -> Unit): Uri? {
        // Placeholder: Implement video compression using libraries like FFmpeg.
        // For simplicity, assume video is within size limits.
        return videoUri
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
     * Retrieves the duration of a video in seconds.
     */
    private suspend fun getVideoDuration(videoUri: Uri, onFailure: (String) -> Unit): Int? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(getApplication<Application>(), videoUri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLongOrNull()?.div(1000)?.toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving video duration: ${e.message}", e)
            onFailure("Failed to retrieve video duration.")
            null
        }
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
                        if (error != null) {
                            Log.e("PostViewModel", "Upvote comment transaction failed: ${error.message}")
                            onFailure("Upvote failed: ${error.message}")
                        } else if (committed) {
                            onSuccess()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error upvoting comment: ${e.message}", e)
                onFailure(e.message ?: "Upvote failed.")
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
                        if (error != null) {
                            Log.e("PostViewModel", "Downvote comment transaction failed: ${error.message}")
                            onFailure("Downvote failed: ${error.message}")
                        } else if (committed) {
                            onSuccess()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error downvoting comment: ${e.message}", e)
                onFailure(e.message ?: "Downvote failed.")
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
     * Function to fetch posts from Firebase Realtime Database.
     * Since we're using real-time listeners, this function might not be needed.
     * However, it can be retained for one-time fetches or pagination.
     */
    fun fetchPosts(
        filterOption: String,
        filterValue: String,
        searchQuery: String,
        sortOption: String,
        userId: String?
    ) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val postsList = withContext(Dispatchers.IO) {
                    // Define your query
                    val postsRef = FirebaseDatabase.getInstance().getReference("posts")
                    val query = when (filterOption) {
                        "recent" -> postsRef.orderByChild("timestamp").limitToLast(100)
                        "my posts" -> if (userId != null) {
                            postsRef.orderByChild("userId").equalTo(userId).limitToLast(100)
                        } else {
                            postsRef.orderByChild("timestamp").limitToLast(100)
                        }
                        else -> postsRef.orderByChild("timestamp").limitToLast(100)
                    }

                    // Fetch posts from cache or Firebase
                    val postsList = query.get().await().children.mapNotNull { snapshot ->
                        val cachedData = getCachedPostData(context, snapshot.key!!)
                        cachedData?.let { Post.fromJson(it) } ?: snapshot.getValue(Post::class.java)
                    }.filter { it?.mediaType == null || it.mediaType == "voice" }

                    // Apply filters, search, and sorting
                    applyFiltersAndSort(postsList, filterOption, filterValue, searchQuery, sortOption, context)
                }

                // Update the state on the main thread
                _posts.value = postsList

            } catch (e: Exception) {
                Log.e("PostViewModel", "Error fetching posts: ${e.message}", e)
                // Handle failure (you can show a toast or other error message if needed)
            }
        }
    }

    private suspend fun applyFiltersAndSort(
        postsList: List<Post>,
        filterOption: String,
        filterValue: String,
        searchQuery: String,
        sortOption: String,
        context: Context
    ): List<Post> {
        // Fetch user profiles for the filtered posts
        val userIds = postsList.map { it.userId }.toSet()
        val profiles = fetchUserProfiles(userIds)
        _userProfiles.value = profiles

        var filteredList = postsList

        // Apply profile-based filters
        if (filterOption in listOf("city", "age", "level", "gender", "high-school", "college")) {
            filteredList = filteredList.filter { post ->
                val profile = profiles[post.userId]
                profile != null && checkProfileFilter(profile, filterOption, filterValue)
            }
        }

        // Apply search query filtering for both username and tags
        if (searchQuery.isNotEmpty()) {
            val lowerSearchQuery = searchQuery.lowercase(Locale.getDefault())
            filteredList = filteredList.filter { post ->
                val profile = profiles[post.userId]
                val usernameMatch = profile?.username?.lowercase(Locale.getDefault())
                    ?.contains(lowerSearchQuery) ?: false
                val userTagsMatch = post.userTags.any { tag ->
                    tag.lowercase(Locale.getDefault()).contains(lowerSearchQuery)
                }
                usernameMatch || userTagsMatch
            }
        }

        // Sort posts based on the selected sorting option
        filteredList = when (sortOption) {
            "Sort by Upvotes" -> filteredList.sortedByDescending { it.upvotes }
            "Sort by Downvotes" -> filteredList.sortedByDescending { it.downvotes }
            else -> filteredList.sortedByDescending { it.getPostTimestamp() }
        }

        // Cache the fetched post data locally for future use
        filteredList.forEach { post ->
            cachePostData(context, post.postId, post.toJson())
        }

        return filteredList
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

    fun cachePostData(context: Context, postId: String, postData: String) {
        val cacheDir = File(context.cacheDir, "post_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val postFile = File(cacheDir, "$postId.json")
        postFile.writeText(postData)
    }

    fun getCachedPostData(context: Context, postId: String): String? {
        val cacheDir = File(context.cacheDir, "post_cache")
        val postFile = File(cacheDir, "$postId.json")
        return if (postFile.exists()) postFile.readText() else null
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