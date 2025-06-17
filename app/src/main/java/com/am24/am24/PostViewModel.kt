// PostViewModel.kt
package com.am24.am24

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.FirebaseRefs.db
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Calendar


class PostViewModel(application: Application) : AndroidViewModel(application) {

    private var isFeedPaused = false
    // Firebase Realtime Database reference to "posts"
    private val postsRef = FirebaseRefs.db.getReference("posts")

    // Firebase Storage reference
    private val storageRef = FirebaseRefs.storage.reference

    // Define media size limits (in bytes)
    private val VOICE_MAX_SIZE = 5 * 1024 * 1024      // 5 MB

    // Define media time limits (in seconds)
    private val VOICE_MAX_DURATION = 60               // 1 minute

    // Tag for logging
    private val TAG = "PostViewModel"

    // Firebase Realtime Database reference to "notifications"
    private val notificationsRef = FirebaseRefs.db.getReference("notifications")

    private val matchesRef = db.getReference("matches")
    private val chatsRef   = db.getReference("chats")      // or wherever you store DM threads
    private val userChats  = db.getReference("userChats")  // if you have a per-user index
    /**
     * StateFlow holding the list of posts.
     */
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> get() = _posts.asStateFlow()

    private val _userProfiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val userProfiles: StateFlow<Map<String, Profile>> get() = _userProfiles

    // MutableStateFlow for filter settings
    private val _filterSettings = MutableStateFlow(FilterSettings())
    val filterSettings: StateFlow<FilterSettings> get() = _filterSettings

    // Add currentUserId StateFlow
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserIdFlow: StateFlow<String?> get() = _currentUserId

    private val _savedPostIds = MutableStateFlow<Set<String>>(emptySet())
    val savedPostIds: StateFlow<Set<String>> = _savedPostIds.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val    isUploading : StateFlow<Boolean> = _isUploading
    private fun clearUploadingFlag() { _isUploading.value = false }

    // 1) keep track of *your* Profile in-memory
    private val _myProfile = MutableStateFlow<Profile?>(null)
    val myProfile: StateFlow<Profile?> = _myProfile.asStateFlow()

    fun setCurrentUserId(userId: String?) {
        _currentUserId.value = userId

        // stop listening if null
        if (userId == null) return

        // start watching the simple ID set:
        watchSavedPostIds(userId)

        // still call your existing loadSavedPosts() for the saved-posts screen:
        loadSavedPosts(userId)

        // ─── NEW: start listening to your Profile node ────────────
        observeMyProfile(userId)
    }

    private fun observeMyProfile(userId: String) {
        val ref = db.getReference("users").child(userId)
        ref.addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                snap.getValue(Profile::class.java)?.let {
                    _myProfile.value = it
                }
            }
            override fun onCancelled(err: DatabaseError) { /* log if you like */ }
        })
    }

    /**
     * Returns true if Plus/Premium, or still under the 5-views-per-day limit.
     * Also auto-resets the counter the first time you call it each new day.
     */
    fun canPlayMedia(): Boolean {
        val profile = _myProfile.value ?: return false
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        // if a new day has rolled over, reset on the server:
        if (profile.lastMediaResetDayOfYear != today) {
            resetMediaQuota(profile.userId, today)
            // after resetting we’re at 0, so free users can view up to DAILY_FREE_QUOTA
            return true
        }

        return profile.isPlus
                || profile.isPremium
                || profile.mediaViewsToday!! < DAILY_FREE_QUOTA
    }

    /** Call *after* a successful play to bump the counter in-DB (no-ops for premium). */
    fun recordMediaPlay() {
        val profile = _myProfile.value ?: return
        if (profile.isPlus || profile.isPremium) return

        // increment only the count; leave the “last reset” untouched
        val newCount = profile.mediaViewsToday?.plus(1)
        db.getReference("users")
            .child(profile.userId)
            .child("mediaViewsToday")
            .setValue(newCount)
    }

    /** Atomically do both: zero today’s count & stamp the day-of-year. */
    private fun resetMediaQuota(userId: String, todayDayOfYear: Int) {
        db.getReference("users")
            .child(userId)
            .updateChildren(mapOf(
                "mediaViewsToday" to 0,
                "lastMediaResetDayOfYear" to todayDayOfYear
            ))
    }

    /**
     * Returns a StateFlow of only those posts whose checkIn matches the given lat/lng exactly.
     */
    /**
     * Return only those posts whose checkIn.placeId matches.
     */
    fun checkInPosts(placeId: String): Flow<List<Post>> = callbackFlow {
        val ref = FirebaseRefs.db.getReference("posts")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val posts = snapshot.children.mapNotNull { it.getValue(Post::class.java) }
                val filtered = posts.filter { it.checkIn?.placeId == placeId }

                Log.d("PostViewModel", "🔍 checkInPosts($placeId): full posts size = ${posts.size}")
                Log.d("PostViewModel", "✅ checkInPosts($placeId) → filtered size = ${filtered.size}")

                trySend(filtered)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PostViewModel", "checkInPosts error: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }


    private val _feedFilters = MutableStateFlow(FilterSettings())
    val feedFilters: StateFlow<FilterSettings> get() = _feedFilters

    fun setFeedFilters(newFilters: FilterSettings) {
        _feedFilters.value = newFilters
    }

    private val _filtersLoaded = MutableStateFlow(false)
    val filtersLoaded: StateFlow<Boolean> get() = _filtersLoaded

    // 2) quota helpers
    private val DAILY_FREE_QUOTA = 7
    //
    // 3) listen for changes under users/{uid}/savedPosts → true
    //
    private fun watchSavedPostIds(userId: String) {
        val ref = db.getReference("users")
            .child(userId)
            .child("savedPosts")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // collect all keys under /savedPosts → Set<String>
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                _savedPostIds.value = ids
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "watchSavedPostIds failed: ${error.message}")
            }
        })
    }

    //
    // 4) Unsaving a post: remove the boolean under users/{uid}/savedPosts/{postId}
    //
    fun unsavePost(
        postId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = _currentUserId.value
        if (uid == null) {
            onFailure("Not signed in")
            return
        }

        db.getReference("users")
            .child(uid)
            .child("savedPosts")
            .child(postId)
            .removeValue()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Failed to unsave") }
    }

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

    // New StateFlow for profile screen posts
    private val _profilePosts = MutableStateFlow<List<Post>>(emptyList())
    val profilePosts: StateFlow<List<Post>> = _profilePosts.asStateFlow()

    // Loading state to indicate when posts are being fetched
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _savedPosts = MutableStateFlow<List<Post>>(emptyList())
    val savedPosts: StateFlow<List<Post>> = _savedPosts.asStateFlow()

    fun loadSavedPosts(userId: String) {
        val savedPostsRef = FirebaseRefs.db.getReference("users").child(userId).child("savedPosts")
        savedPostsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val savedPostIds = snapshot.children.mapNotNull { it.key }
                fetchSavedPosts(savedPostIds)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("PostViewModel", "Failed to load saved posts: ${error.message}")
            }
        })
    }

    private fun fetchSavedPosts(postIds: List<String>) {
        val postsRef = FirebaseRefs.db.getReference("posts")
        viewModelScope.launch {
            val savedPostsList = mutableListOf<Post>()
            postIds.forEach { postId ->
                postsRef.child(postId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        snapshot.getValue(Post::class.java)?.let { post ->
                            savedPostsList.add(post)
                            _savedPosts.value = savedPostsList.sortedByDescending { it.getTimestampLong() }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("PostViewModel", "Failed to fetch post $postId: ${error.message}")
                    }
                })
            }
        }
    }

    //
    // 5) You already have this; it’ll continue to work for your “Saved Posts” screen
    //
    fun savePost(
        postId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.getReference("users")
            .child(userId)
            .child("savedPosts")
            .child(postId)
            .setValue(true)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Failed to save post") }
    }

    fun fetchPosts() {
        viewModelScope.launch {
            Log.d("PostViewModel", "Starting fetchPosts")
            _isLoading.value = true
            _profilePosts.value = emptyList() // Reset to avoid stale data
            val currentUserId = _currentUserId.value ?: return@launch
            // Fetch blocked users
            val blockedUsers = fetchBlockedUsers(currentUserId)

            postsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val postsList = mutableListOf<Post>()
                    for (postSnapshot in snapshot.children) {
                        try {
                            val post = postSnapshot.getValue(Post::class.java)
                            if (post != null && !blockedUsers.contains(post.userId)) { // Filter out blocked users
                                postsList.add(post)
                                Log.d("PostViewModel", "Added post: $post")
                            } else if (post == null) {
                                Log.w("PostViewModel", "Failed to deserialize post at ${postSnapshot.key}: ${postSnapshot.value}")
                            }
                        } catch (e: Exception) {
                            Log.e("PostViewModel", "Error deserializing post at ${postSnapshot.key}: ${e.message}")
                        }
                    }
                    Log.d("PostViewModel", "Setting _profilePosts to ${postsList.size} posts: $postsList")
                    _profilePosts.value = postsList
                    _isLoading.value = false
                    Log.d("PostViewModel", "Fetched ${postsList.size} posts, _profilePosts.value.size=${_profilePosts.value.size}")
                }

                override fun onCancelled(error: DatabaseError) {
                    _isLoading.value = false
                    Log.e("PostViewModel", "Failed to fetch posts: ${error.message}")
                }
            })
        }
    }

    /**
     * Generic helper for image / short-video posts.
     *
     * @param mediaType  "image" or "video"
     */
    fun createMediaPost(
        userId:   String,
        username: String,
        mediaUri: Uri,
        mediaType:String,
        caption:  String,
        userTags: List<String>,
        checkIn:  CheckIn? = null,          // ← NEW
        onDone:   () -> Unit,
        onError:  (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isUploading.value = true            // <─── ① RAISE FLAG RIGHT AWAY

            try {
                // ─── validations ──────────────────────────────────────────────
                if (mediaType !in listOf("image","video")) {
                    onError("Invalid media type"); return@launch
                }
                if (mediaType == "video" && videoLongerThan(mediaUri, 15_000)) {
                    onError("Video must be 15 s or shorter"); return@launch
                }

                // ─── upload main file ────────────────────────────────────────
                val folder = if (mediaType == "image") "images" else "videos"
                val mediaUrl = uploadMediaToStorage(mediaUri, folder) { err ->
                    onError(err); return@uploadMediaToStorage
                } ?: return@launch

                // ─── optional thumbnail for videos ───────────────────────────
                var thumbUrl: String? = null
                if (mediaType == "video") {
                    generateVideoThumbnail(mediaUri)?.let { thumbFile ->
                        thumbUrl = uploadMediaToStorage(Uri.fromFile(thumbFile), "thumbs") { err ->
                            Log.e(TAG,"Thumb upload failed: $err")
                        }
                        thumbFile.delete()
                    }
                }

                // ─── push post object ────────────────────────────────────────
                val postId = postsRef.push().key ?: throw Exception("No postId")
                val post   = mapOf(
                    "postId"       to postId,
                    "userId"       to userId,
                    "username"     to username,
                    "contentText"  to caption.ifBlank { null },
                    "timestamp"    to ServerValue.TIMESTAMP,
                    "userTags"     to userTags,
                    "mediaType"    to mediaType,
                    "mediaUrl"     to mediaUrl,
                    "mediaThumb"   to thumbUrl,
                    // --- optional place block ----------------
                    "checkIn"    to checkIn?.let {
                        mapOf(
                            "placeId" to it.placeId,
                            "name"    to it.name,
                            "address" to it.address,
                            "lat"     to it.lat,
                            "lng"     to it.lng
                        )
                    },
                    "upvotes"      to 0,
                    "downvotes"    to 0,
                    "upvotedUsers"   to emptyMap<String, Boolean>(),
                    "downvotedUsers" to emptyMap<String, Boolean>(),
                    "totalComments"  to 0
                )
                postsRef.child(postId).setValue(post).await()
                clearUploadingFlag() // <─── ② LOWER FLAG
                refreshPosts()
                withContext(Dispatchers.Main) { onDone() }
                // ─── notify matches, same style as text/voice ───────────────
                val matches = getMatches(userId)
                matches.forEach { receiverId ->
                    val notifType = if (checkIn != null) "match_checkin" else "match_post"
                    val msg       = if (checkIn != null)
                        "$username checked in at ${checkIn?.name} 📍"
                    else
                        "$username posted a new ${if (mediaType=="image") "photo" else "video"} update."
                    sendNotification(receiverId, notifType, userId, username, msg)
                }

            } catch (e: Exception) {
                clearUploadingFlag()            //  ↓ also drop the flag on failure
                Log.e(TAG,"createMediaPost: ${e.message}",e)
                withContext(Dispatchers.Main) { onError(e.message ?: "Failed") }
            }
        }
    }

    /* ---------- small helpers ---------- */

    // true if video is longer than maxMs
    private fun videoLongerThan(uri: Uri, maxMs: Long): Boolean {
        return try {
            MediaMetadataRetriever().run {
                setDataSource(getApplication<Application>(), uri)
                val dur = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                release()
                dur > maxMs
            }
        } catch(_: Exception){ false }
    }

    // saves first frame to cache, returns the File (caller must delete)
    private fun generateVideoThumbnail(uri: Uri): File? = try {
        val bmp = MediaMetadataRetriever().run {
            setDataSource(getApplication<Application>(), uri)
            val frame = getFrameAtTime(0L)
            release()
            frame
        } ?: return null
        val file = File.createTempFile("thumb_${System.currentTimeMillis()}", ".jpg",
            getApplication<Application>().cacheDir)
        val out = java.io.FileOutputStream(file)
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
        out.flush(); out.close()
        file
    } catch(_: Exception){ null }

    // Listener registration to remove when ViewModel is cleared
    private var postsListener: ValueEventListener? = null

    fun refreshPosts() {
        Log.d(TAG, "Refreshing posts...")
        clearAllFilters()
        observePosts() // Re-attach listener
    }

    /**
     * Sets up a real-time listener to observe changes in "posts" node.
     */
    private fun observePosts() {
        isFeedPaused = false                // ← add this
        if (postsListener == null) { // Avoid re-adding listener if already active
            postsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val currentUserId = _currentUserId.value ?: return@launch
                        // Fetch blocked users
                        val blockedUsers = fetchBlockedUsers(currentUserId)

                        val postsList = snapshot.children.mapNotNull { it.getValue(Post::class.java) }
                            .filter { post -> !blockedUsers.contains(post.userId) } // Filter out posts from blocked users
                        val sortedPosts = postsList.sortedByDescending { it.getTimestampLong() }
                        val userIds = sortedPosts.map { it.userId }.toSet()
                        val profiles = fetchUserProfiles(userIds)

                        Log.d(TAG, "Fetched ${sortedPosts.size} posts and ${profiles.size} profiles after filtering blocked users")

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
                val currentUserId = _currentUserId.value ?: return@launch
                // Fetch blocked users
                val blockedUsers = fetchBlockedUsers(currentUserId)

                val snapshot = postsRef.get().await()
                val postsList = snapshot.children.mapNotNull { it.getValue(Post::class.java) }
                    .filter { post -> !blockedUsers.contains(post.userId) } // Filter out posts from blocked users
                val sortedPosts = postsList.sortedByDescending { it.getTimestampLong() }
                val userIds = sortedPosts.map { it.userId }.toSet()
                val profiles = fetchUserProfiles(userIds)

                Log.d(TAG, "One-time fetch: ${sortedPosts.size} posts and ${profiles.size} profiles after filtering blocked users")

                _userProfiles.value = profiles
                _posts.value = sortedPosts
            } catch (e: Exception) {
                Log.e(TAG, "One-time fetch failed: ${e.message}")
            }
        }
    }

    // Pause observing posts
// PostViewModel.kt
    fun pauseFeed() {
        if (postsListener != null) {
            postsRef.removeEventListener(postsListener!!)
            postsListener = null          // ←  important!
        }
        isFeedPaused = true
    }
    // Resume observing posts
    fun resumeFeed() {
        if (!isFeedPaused) return
        isFeedPaused = false
        observePosts()                    // will add a fresh listener
    }

    override fun onCleared() {
        super.onCleared()
        // Remove the listener to prevent memory leaks
        postsListener?.let { postsRef.removeEventListener(it) }
        pauseFeed() // Cleanup listeners to prevent memory leaks
    }


    /**
     * Remove the mutual match, then delete their chat/thread entry.
     */
    private suspend fun unmatchAndRemoveChat(userA: String, userB: String) {
        try {
            // 1) Delete the match entries both ways
            matchesRef.child(userA).child(userB).removeValue().await()
            matchesRef.child(userB).child(userA).removeValue().await()

            // 2) Remove chat entries from userChats (if you have a per-user index)
            userChats.child(userA).child(userB).removeValue().await()
            userChats.child(userB).child(userA).removeValue().await()

            // 3) Delete the conversation node (if it’s under chats/{conversationId})
            val conversationId = listOf(userA, userB).sorted().joinToString("_")
            chatsRef.child(conversationId).removeValue().await()

            // 4) Delete the messages node (contains chat history and shared posts)
            val messagesRef = FirebaseDatabase.getInstance().getReference("messages/$conversationId")
            messagesRef.removeValue().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error unmatching and removing chat: ${e.message}", e)
            throw e // Rethrow to allow caller (e.g., reportAndBlock) to handle
        }
    }

    /**
     * Combined “report → block → unmatch” workflow
     */
    suspend fun reportAndBlock(
        postId: String,
        reporterId: String,
        reportedUserId: String,
        reason: String
    ) {
        // 1) report
        val repRef = db.getReference("reportedPosts").child(postId).push()
        val reportId = repRef.key ?: throw IllegalStateException("No key")
        repRef.setValue(
            mapOf(
                "reportId" to reportId,
                "postId" to postId,
                "reporterId" to reporterId,
                "reportedUser" to reportedUserId,
                "reason" to reason,
                "timestamp" to ServerValue.TIMESTAMP
            )
        ).await()

        // 2) block
        db.getReference("blocks")
            .child(reporterId)
            .child(reportedUserId)
            .setValue(true)
            .await()

        // 3) unmatch + remove chat
        unmatchAndRemoveChat(reporterId, reportedUserId)
    }

    // Helper function to send a notification
    private suspend fun sendNotification(
        receiverId: String,
        type: String,
        senderId: String,
        senderUsername: String,
        message: String,
        postId:          String? = null,     // NEW
        commentId:       String? = null      // NEW
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
                isRead = "false",
                postId          = postId,        // ⬅
                commentId       = commentId      // ⬅
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
            val userRef = FirebaseRefs.db.getReference("users").child(userId)
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
        checkIn: CheckIn? = null,                // ← NEW
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Enforce text character limit
        if (contentText.length > 10000) {
            onFailure("Text exceeds the maximum allowed length of 10,000 characters.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isUploading.value = true
            val postId = postsRef.push().key
            if (postId == null) {
                onFailure("Unable to generate post ID."); return@launch
            }

            val post = mapOf(
                "postId"      to postId,
                "userId"      to userId,
                "username"    to username,
                "contentText" to contentText,
                "timestamp"   to ServerValue.TIMESTAMP,
                "userTags"    to userTags,
                "fontFamily"  to fontFamily,
                "fontSize"    to fontSize,
                "mediaType"   to null,
                "mediaUrl"    to null,
                // ─── NEW block ───────────────────────────────
                "checkIn" to checkIn?.let {
                    mapOf(
                        "placeId" to it.placeId,
                        "name"    to it.name,
                        "address" to it.address,
                        "lat"     to it.lat,
                        "lng"     to it.lng
                    )
                },
                // ─────────────────────────────────────────────
                "upvotes"       to 0,
                "downvotes"     to 0,
                "upvotedUsers"  to emptyMap<String, Boolean>(),
                "downvotedUsers" to emptyMap<String, Boolean>(),
                "totalComments"  to 0
            )

            try {
                postsRef.child(postId).setValue(post).await()
                clearUploadingFlag()
                refreshPosts()
                onSuccess()
                // Send notifications to friends and matches
                val matches = getMatches(userId)
                matches.forEach { receiverId ->
                    val isCheckIn = checkIn != null                      // 🆕
                    val msg = if (isCheckIn)
                        "$username checked in at ${checkIn?.name} 📍"
                    else
                        "$username posted a new text update."
                    val notifType = if (isCheckIn) "match_checkin" else "match_post"   // 🆕
                    sendNotification(
                        receiverId      = receiverId,
                        type            = notifType,       // 🆕
                        senderId        = userId,
                        senderUsername  = username,
                        message         = msg
                    )
                }
            } catch (e: Exception) {
                clearUploadingFlag()
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
                    val msg = "$username posted a new voice note 🎤"
                    sendNotification(
                        receiverId      = receiverId,
                        type            = "match_post",   // ← NEW type (was "new_post")
                        senderId        = userId,
                        senderUsername  = username,
                        message         = msg
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
     * Fetches the list of user IDs blocked by the given user.
     */
    private suspend fun fetchBlockedUsers(userId: String): List<String> {
        return try {
            val snapshot = FirebaseRefs.db.getReference("blocks/$userId").get().await()
            snapshot.children.mapNotNull { it.key }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching blocked users: ${e.message}", e)
            emptyList()
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
                val currentUserId = _currentUserId.value ?: run {
                    onFailure("User not logged in")
                    return@launch
                }

                val postSnapshot = postsRef.child(postId).get().await()
                val post = postSnapshot.getValue(Post::class.java) ?: run {
                    onFailure("Post not found")
                    return@launch
                }

                matches.forEach { matchId ->
                    val chatId = if (currentUserId < matchId) "${currentUserId}_$matchId" else "${matchId}_$currentUserId"
                    val messagesRef = FirebaseDatabase.getInstance().getReference("messages/$chatId")
                    val participantsRef = FirebaseDatabase.getInstance().getReference("chatParticipants/$chatId")
                    val newMessageId = messagesRef.push().key ?: run {
                        onFailure("Failed to generate new message ID")
                        return@forEach
                    }

                    // Ensure the shared post has some content
                    if (post.contentText.isNullOrEmpty() && post.mediaUrl.isNullOrEmpty()) {
                        onFailure("Cannot share an empty post")
                        return@forEach
                    }

                    val sharedMessage = Message(
                        id = newMessageId,
                        senderId = currentUserId,
                        receiverId = matchId,
                        text = post.contentText ?: "",
                        timestamp = System.currentTimeMillis(),
                        read = false,
                        mediaType = post.mediaType,
                        mediaUrl = post.mediaUrl,
                        processed = false,
                        isPost = true
                    )

                    // Write participants to a separate path
                    participantsRef.setValue(mapOf(currentUserId to true, matchId to true)).await()
                    messagesRef.child(newMessageId).setValue(sharedMessage).await()
                    Log.d("PostViewModel", "Shared post message written: $newMessageId to chat $chatId, message=$sharedMessage")

                    val notificationsRef = FirebaseDatabase.getInstance().getReference("notifications")
                    postNotification(
                        notificationsRef = notificationsRef,
                        toUserId = matchId,
                        fromUserId = currentUserId,
                        message = "Shared a post"
                    )
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error sharing post: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onFailure(e.message ?: "Failed to share post")
                }
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
        val userRef = FirebaseRefs.db.getReference("users").child(userId).child("feedFilters")
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
                val userRef = FirebaseRefs.db.getReference("users").child(userId)
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
                val postRef = FirebaseRefs.db.getReference("posts").child(postId)
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

    /** Call from UI when you want a totally un-filtered feed. */
    fun clearAllFilters() {
        // Wipe the Home-screen filter settings
        _filterSettings.value = FilterSettings()           // == everything default

        // Wipe the deeper feed filters (gender, locality, etc.)
        _feedFilters.value   = FilterSettings()            // likewise

        // -- If you cache the user’s saved filters in Firebase, also wipe them there
        _currentUserId.value?.let { uid ->
            FirebaseRefs.db.getReference("users")
                .child(uid)
                .child("feedFilters")
                .removeValue()        //  ← delete the saved copy so it can’t re-apply
        }
    }
}