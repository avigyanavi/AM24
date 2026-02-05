// PostViewModel.kt
package com.am24.am24

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.am24.am24.FirebaseRefs.db
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

enum class FeedSearchTagType { TAG, PLACE }

data class FeedSearchTagResult(
    val value: String,
    val type: FeedSearchTagType
)

data class FeedSearchResults(
    val users: List<Profile> = emptyList(),
    val posts: List<Post> = emptyList(),
    val tags: List<FeedSearchTagResult> = emptyList()
)

class PostViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionRepository = SessionDataRepository

    private var isFeedPaused = false
    // Firestore reference to "posts"
    private val firestore: FirebaseFirestore = FirebaseRefs.firestore
    private val postsCollection = firestore.collection("posts")
    private var postsQuery: Query? = null

    private val FEED_PAGE_SIZE = 40
    val feedPageSize: Int = FEED_PAGE_SIZE
    private val MAX_FEED_CACHE = FEED_PAGE_SIZE * 5
    private var oldestLoadedTimestamp: Long? = null
    private var oldestLoadedSnapshot: DocumentSnapshot? = null

    private val _hasMorePosts = MutableStateFlow(true)
    val hasMorePosts: StateFlow<Boolean> = _hasMorePosts.asStateFlow()
    private val _feedErrorMessage = MutableStateFlow<String?>(null)
    val feedErrorMessage: StateFlow<String?> = _feedErrorMessage.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
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
    private val _rawPosts = MutableStateFlow<List<Post>>(emptyList())
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> get() = _posts.asStateFlow()
    private val _postsLoaded = MutableStateFlow(false)
    val postsLoaded: StateFlow<Boolean> = _postsLoaded
    private val _isInitialFeedLoading = MutableStateFlow(true)
    val isInitialFeedLoading: StateFlow<Boolean> = _isInitialFeedLoading.asStateFlow()

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

    private val _blockedUsers = MutableStateFlow<Set<String>>(emptySet())
    private val _matchedUsers = MutableStateFlow<Set<String>>(emptySet())

    init {
        sessionRepository.ensureStarted()

        viewModelScope.launch {
            sessionRepository.profile.collect { profile ->
                _myProfile.value = profile
            }
        }

        viewModelScope.launch {
            sessionRepository.blockedUserIds.collect { blocked ->
                _blockedUsers.value = blocked
                applyPostFilters()
            }
        }

        viewModelScope.launch {
            sessionRepository.matchIds.collect { matches ->
                _matchedUsers.value = matches
            }
        }
        viewModelScope.launch {
            _postsLoaded.collect { loaded ->
                if (loaded) {
                    _isInitialFeedLoading.value = false
                }
            }
        }
    }

    private val _postFlow = MutableStateFlow<Post?>(null)
    val    postFlow: StateFlow<Post?> = _postFlow.asStateFlow()

    private val _isFeedSearchVisible = MutableStateFlow(false)
    val isFeedSearchVisible: StateFlow<Boolean> = _isFeedSearchVisible.asStateFlow()

    private val _isFeedSearchMode = MutableStateFlow(false)
    val isFeedSearchMode: StateFlow<Boolean> = _isFeedSearchMode.asStateFlow()

    private val _isFeedSearchLoading = MutableStateFlow(false)
    val isFeedSearchLoading: StateFlow<Boolean> = _isFeedSearchLoading.asStateFlow()

    private val _feedSearchQuery = MutableStateFlow("")
    val feedSearchQuery: StateFlow<String> = _feedSearchQuery.asStateFlow()

    private val _feedSearchResults = MutableStateFlow(FeedSearchResults())
    val feedSearchResults: StateFlow<FeedSearchResults> = _feedSearchResults.asStateFlow()

    private val _feedSearchSelectedTab = MutableStateFlow(0)
    val feedSearchSelectedTab: StateFlow<Int> = _feedSearchSelectedTab.asStateFlow()

    fun setCurrentUserId(userId: String?) {
        _currentUserId.value = userId

        // stop listening if null
        if (userId == null) return

        sessionRepository.start(userId)
        // start watching the simple ID set:
        watchSavedPostIds(userId)

        // still call your existing loadSavedPosts() for the saved-posts screen:
        loadSavedPosts(userId)
    }

    fun showFeedSearchBar() {
        _isFeedSearchVisible.value = true
    }

    fun hideFeedSearch() {
        _isFeedSearchVisible.value = false
        _isFeedSearchMode.value = false
        _feedSearchSelectedTab.value = 0
        _feedSearchQuery.value = ""
        _feedSearchResults.value = FeedSearchResults()
        _isFeedSearchLoading.value = false
        setSearchQuery("")
    }

    fun updateFeedSearchQuery(newQuery: String) {
        _feedSearchQuery.value = newQuery
        _isFeedSearchMode.value = false
    }

    fun performFeedSearch() {
        val query = _feedSearchQuery.value.trim()
        if (query.isBlank()) {
            _feedSearchResults.value = FeedSearchResults()
            _isFeedSearchMode.value = false
            _isFeedSearchLoading.value = false
            setSearchQuery("")
            return
        }

        _feedSearchSelectedTab.value = 0
        setSearchQuery(query)

        // Enter search mode immediately so the UI can render the results scaffold
        _isFeedSearchMode.value = true
        _feedSearchResults.value = FeedSearchResults()
        _isFeedSearchLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val users = searchUsersByQuery(query)
                val posts = computePostsForSearch(query)
                val tags = computeTagsForQuery(query)

                withContext(Dispatchers.Main) {
                    _feedSearchResults.value = FeedSearchResults(
                        users = users,
                        posts = posts,
                        tags = tags
                    )
                    _isFeedSearchLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "performFeedSearch failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _feedSearchResults.value = FeedSearchResults()
                    _isFeedSearchLoading.value = false
                }
            }
        }
    }

    fun setFeedSearchSelectedTab(index: Int) {
        _feedSearchSelectedTab.value = index
    }

    private suspend fun searchUsersByQuery(query: String): List<Profile> {
        return try {
            val cachedMatches = _userProfiles.value.values.filter { profile ->
                profile.username.contains(query, ignoreCase = true) ||
                        profile.name.contains(query, ignoreCase = true)
            }

            val snapshot = FirebaseRefs.db.getReference("users").get().await()
            val remoteMatches = snapshot.children.mapNotNull { it.getValue(Profile::class.java) }
                .filter { profile ->
                    profile.username.contains(query, ignoreCase = true) ||
                            profile.name.contains(query, ignoreCase = true) ||
                            profile.userTags.any { tag -> tag.contains(query, ignoreCase = true) }
                }

            (cachedMatches + remoteMatches)
                .distinctBy { it.userId }
                .sortedBy { it.username.lowercase() }
                .take(40)
        } catch (e: Exception) {
            Log.e(TAG, "searchUsersByQuery failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun computePostsForSearch(query: String): List<Post> {
        val homeFilters = _filterSettings.value
        val feedFilters = _feedFilters.value
        val currentUserId = _currentUserId.value

        val stageOne = applyFiltersAndSort(
            postsList = _posts.value,
            profiles = _userProfiles.value,
            filterOption = homeFilters.filterOption,
            searchQuery = query,
            sortOption = homeFilters.sortOption,
            currentUserId = currentUserId,
            isVoiceOnly = homeFilters.isVoiceOnly
        )

        return applyFiltersAndSort(
            postsList = stageOne,
            profiles = _userProfiles.value,
            filterOption = feedFilters.filterOption,
            searchQuery = query,
            sortOption = homeFilters.sortOption,
            currentUserId = currentUserId,
            feedFilters = feedFilters.feedFilters,
            isVoiceOnly = homeFilters.isVoiceOnly
        )
    }

    private fun computeTagsForQuery(query: String): List<FeedSearchTagResult> {
        if (query.isBlank()) return emptyList()

        val results = _posts.value.flatMap { post ->
            val tagMatches = post.userTags
                .filter { tag -> tag.contains(query, ignoreCase = true) }
                .map { tagValue -> FeedSearchTagResult(tagValue, FeedSearchTagType.TAG) }

            val placeMatch = post.checkIn?.name
                ?.takeIf { it.contains(query, ignoreCase = true) }
                ?.let { placeName -> FeedSearchTagResult(placeName, FeedSearchTagType.PLACE) }

            if (placeMatch != null) tagMatches + placeMatch else tagMatches
        }

        return results
            .distinctBy { it.value.lowercase() }
            .sortedBy { it.value.lowercase() }
    }

    private fun DocumentSnapshot.toPost(): Post? {
        val basePost = try {
            toObject(Post::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode post ${id}: ${e.message}", e)
            null
        } ?: return null

        val comments = parseComments(get("comments") as? Map<String, Any?>)
        return basePost.copy(
            postId = id,
            comments = if (comments.isNotEmpty()) comments else basePost.comments
        )
    }

    private fun parseComments(raw: Map<String, Any?>?): Map<String, Comment> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.mapNotNull { (key, value) ->
            val map = value as? Map<*, *> ?: return@mapNotNull null
            val comment = Comment(
                commentId = (map["commentId"] as? String) ?: key,
                userId = map["userId"] as? String ?: "",
                username = map["username"] as? String ?: "",
                commentText = map["commentText"] as? String ?: "",
                timestamp = map["timestamp"] ?: System.currentTimeMillis(),
                upvotes = (map["upvotes"] as? Number)?.toInt() ?: 0,
                downvotes = (map["downvotes"] as? Number)?.toInt() ?: 0,
                upvotedUsers = (map["upvotedUsers"] as? Map<String, Boolean>)?.toMutableMap()
                    ?: mutableMapOf(),
                downvotedUsers = (map["downvotedUsers"] as? Map<String, Boolean>)?.toMutableMap()
                    ?: mutableMapOf(),
                mediaUrl = map["mediaUrl"] as? String
            )
            key to comment
        }.toMap()
    }

    private var currentPostId: String? = null          // <— NEW
    private var singlePostListener: ListenerRegistration? = null
    private var savedPostIdsRef: DatabaseReference? = null
    private var savedPostIdsListener: ValueEventListener? = null


    /** Starts (or switches) a realtime listener for one post. */
    fun startPostListener(postId: String) {
        // Already listening to this post?  Nothing to do
        if (currentPostId == postId && singlePostListener != null) return

        // 1️⃣  Detach the old listener (if any)
        singlePostListener?.remove()

        // 2️⃣  Attach a fresh listener to the requested post
        currentPostId = postId
        singlePostListener = postsCollection.document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "post listener cancelled", error)
                    return@addSnapshotListener
                }
                _postFlow.value = snapshot?.toPost()
            }
    }

    /** Call when the screen/ViewModel is done */
    private fun stopPostListener() {
        singlePostListener?.remove()
        singlePostListener = null
        currentPostId     = null
    }

    override fun onCleared() {
        super.onCleared()

        // ─── 1. main feed listener (your existing code) ───────────────
        detachPostsListener()
        postsListener = null

        // ─── 2. single-post listener we added for PostDetailScreen ────
        singlePostListener?.remove()
        singlePostListener = null
        currentPostId     = null          // <- also clear the flag

        // ─── 3. saved post listener ─────────────────────────────────────
        savedPostIdsListener?.let { listener ->
            savedPostIdsRef?.removeEventListener(listener)
        }
        savedPostIdsListener = null
        savedPostIdsRef = null

        // ─── 4. any additional cleanup you already perform ────────────
        pauseFeed()                       // keeps your existing behaviour
    }

    private fun refreshSinglePost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snap   = postsCollection.document(postId).get().await()
                val latest = snap.toPost()
                if (latest != null) _postFlow.value = latest
            } catch (_: Exception) { /* ignore – listener will catch up */ }
        }
    }

    /**
     * Returns a StateFlow of only those posts whose checkIn matches the given lat/lng exactly.
     */
    /**
     * Return only those posts whose checkIn.placeId matches.
     */
    fun checkInPosts(placeId: String): Flow<List<Post>> = callbackFlow {
        val query = postsCollection
            .whereEqualTo("checkIn.placeId", placeId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("PostViewModel", "checkInPosts error: ${error.message}")
                return@addSnapshotListener
            }
            val posts = snapshot?.documents?.mapNotNull { it.toPost() }.orEmpty()
            val filtered = posts.filter { it.checkIn?.placeId == placeId }

            Log.d("PostViewModel", "🔍 checkInPosts($placeId): full posts size = ${posts.size}")
            Log.d("PostViewModel", "✅ checkInPosts($placeId) → filtered size = ${filtered.size}")

            trySend(filtered)
        }

        awaitClose { listener.remove() }
    }


    private val _feedFilters = MutableStateFlow(FilterSettings())
    val feedFilters: StateFlow<FilterSettings> get() = _feedFilters

    fun setFeedFilters(newFilters: FilterSettings) {
        _feedFilters.value = newFilters
    }

    private val _filtersLoaded = MutableStateFlow(false)
    val filtersLoaded: StateFlow<Boolean> get() = _filtersLoaded

    //
    private fun watchSavedPostIds(userId: String) {
        savedPostIdsRef = db.getReference("users")
            .child(userId)
            .child("savedPosts")

        savedPostIdsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // collect all keys under /savedPosts → Set<String>
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                _savedPostIds.value = ids
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "watchSavedPostIds failed: ${error.message}")
            }
        }

        savedPostIdsRef?.addValueEventListener(savedPostIdsListener!!)
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
        _posts,
        _userProfiles,
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
        viewModelScope.launch(Dispatchers.IO) {
            val savedPostsList = mutableListOf<Post>()
            postIds.forEach { postId ->
                try {
                    val snapshot = postsCollection.document(postId).get().await()
                    snapshot.toPost()?.let { post ->
                        savedPostsList.add(post)
                        _savedPosts.value = savedPostsList.sortedByDescending { it.getTimestampLong() }
                    }
                } catch (e: Exception) {
                    Log.e("PostViewModel", "Failed to fetch post $postId: ${e.message}")
                }
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

    fun fetchPosts(userId: String? = null) {
        viewModelScope.launch {
            Log.d("PostViewModel", "Starting fetchPosts")
            _isLoading.value = true
            _profilePosts.value = emptyList() // Reset to avoid stale data
            _postsLoaded.value = false      // ✅ finished – even if list is empty

            if (_currentUserId.value == null) {
                _isLoading.value = false
                _postsLoaded.value = true
            }

            try {
                val query = if (userId.isNullOrBlank()) {
                    postsCollection
                } else {
                    postsCollection.whereEqualTo("userId", userId)
                }
                val snapshot = query.get().await()
                val postsList = snapshot.documents.mapNotNull { it.toPost() }
                Log.d("PostViewModel", "Setting _profilePosts to ${postsList.size} posts: $postsList")
                _profilePosts.value = postsList
                _rawPosts.value = postsList
                applyPostFilters()
                _isLoading.value = false
                _postsLoaded.value = true      // ✅ finished – even if list is empty
                Log.d("PostViewModel", "Fetched ${postsList.size} posts, _profilePosts.value.size=${_profilePosts.value.size}")
            } catch (e: Exception) {
                _isLoading.value = false
                _postsLoaded.value = true      // ✅ finished – even if list is empty
                Log.e("PostViewModel", "Failed to fetch posts: ${e.message}")
            }
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

                val country = fetchUserCountry(userId)

                // ─── push post object ────────────────────────────────────────
                val postRef = postsCollection.document()
                val postId = postRef.id
                val post   = mapOf(
                    "postId"       to postId,
                    "userId"       to userId,
                    "username"     to username,
                    "country"      to country,
                    "contentText"  to caption.ifBlank { null },
                    "timestamp"    to FieldValue.serverTimestamp(),
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
                postRef.set(post).await()
                clearUploadingFlag() // <─── ② LOWER FLAG
                refreshPosts()
                withContext(Dispatchers.Main) { onDone() }
                // ─── notify matches, same style as text/voice ───────────────
                val matches = _matchedUsers.value
                matches.forEach { receiverId ->
                    val notifType = if (checkIn != null) "match_checkin" else "match_post"
                    val msg       = if (checkIn != null)
                        "$username checked in at ${checkIn?.name} 📍"
                    else
                        "$username posted a new ${if (mediaType=="image") "photo" else "video"} update."
                    sendNotification(
                        receiverId     = receiverId,
                        type           = notifType,
                        senderId       = userId,
                        senderUsername = username,
                        message        = msg,
                        postId         = postId
                    )
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
    private var postsListener: ListenerRegistration? = null

    private fun detachPostsListener() {
        postsListener?.remove()
        postsQuery = null
    }

    fun refreshPosts() {
        Log.d(TAG, "Refreshing posts...")
        clearAllFilters()
        val shouldShowLoading = _currentUserId.value != null && _rawPosts.value.isEmpty()
        _isInitialFeedLoading.value = shouldShowLoading
        oldestLoadedTimestamp = null
        oldestLoadedSnapshot = null
        _hasMorePosts.value = true
        _isLoadingMore.value = false
        _feedErrorMessage.value = null
        detachPostsListener()
        postsListener = null
        observePosts() // Re-attach listener
    }

    private fun applyPostFilters() {
        val blocked = _blockedUsers.value
        val filtered = _rawPosts.value.filter { post -> post.userId !in blocked }
        if (filtered != _posts.value) {
            _posts.value = filtered
        }
    }
    private fun trimFeedCache(posts: List<Post>): List<Post> {
        if (posts.size <= MAX_FEED_CACHE) return posts
        return posts.take(MAX_FEED_CACHE)
    }
    /**
     * Sets up a real-time listener to observe changes in "posts" node.
     */
    private fun observePosts() {
        isFeedPaused = false
        val query = postsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(FEED_PAGE_SIZE.toLong())

        detachPostsListener()
        postsQuery = query
        postsListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Failed to observe posts: ${error.message}")
                _feedErrorMessage.value = "Posts couldn't be loaded"
                _isInitialFeedLoading.value = false
                return@addSnapshotListener
            }

            val documents = snapshot?.documents.orEmpty()
            viewModelScope.launch(Dispatchers.IO) {
                val postsList = documents.mapNotNull { it.toPost() }
                val sortedPosts = postsList.sortedByDescending { it.getTimestampLong() }
                val userIds = sortedPosts.map { it.userId }.toSet()
                val profiles = fetchUserProfiles(userIds)
                val existingPosts = _rawPosts.value
                val hadExistingPosts = existingPosts.isNotEmpty()
                val recentIds = sortedPosts.map { it.postId }.toSet()
                val remainingOldPosts = existingPosts.filterNot { recentIds.contains(it.postId) }
                val mergedPosts = trimFeedCache(
                    (sortedPosts + remainingOldPosts)
                        .distinctBy { it.postId }
                        .sortedByDescending { it.getTimestampLong() }
                )

                val combinedProfiles = _userProfiles.value.toMutableMap().apply { putAll(profiles) }

                _userProfiles.value = combinedProfiles
                _rawPosts.value = mergedPosts
                applyPostFilters()
                oldestLoadedTimestamp = mergedPosts.lastOrNull()?.getTimestampLong()
                oldestLoadedSnapshot = documents.lastOrNull()
                if (!hadExistingPosts) {
                    _hasMorePosts.value = sortedPosts.size >= FEED_PAGE_SIZE
                }
                if (_isInitialFeedLoading.value) {
                    _isInitialFeedLoading.value = false
                }

                // Confirm StateFlow update
                Log.d(TAG, "Updated _posts with ${mergedPosts.size} posts")
            }
        }
        viewModelScope.launch {
            try {
                val snapshot = query.get().await()
                val docs = snapshot.documents
                val postsList = docs.mapNotNull { it.toPost() }
                val sortedPosts = postsList.sortedByDescending { it.getTimestampLong() }
                val userIds = sortedPosts.map { it.userId }.toSet()
                val profiles = fetchUserProfiles(userIds)

                val existingPosts = _rawPosts.value
                val recentIds = sortedPosts.map { it.postId }.toSet()
                val remainingOldPosts = existingPosts.filterNot { recentIds.contains(it.postId) }
                val mergedPosts = trimFeedCache(
                    (sortedPosts + remainingOldPosts)
                        .distinctBy { it.postId }
                        .sortedByDescending { it.getTimestampLong() }
                )

                val combinedProfiles = _userProfiles.value.toMutableMap().apply { putAll(profiles) }

                _userProfiles.value = combinedProfiles
                _rawPosts.value = mergedPosts
                applyPostFilters()
                oldestLoadedTimestamp = mergedPosts.lastOrNull()?.getTimestampLong()
                oldestLoadedSnapshot = docs.lastOrNull()
                _hasMorePosts.value = sortedPosts.size >= FEED_PAGE_SIZE
                _isInitialFeedLoading.value = false
            } catch (e: Exception) {
                Log.e(TAG, "One-time fetch failed: ${e.message}")
                _feedErrorMessage.value = "Posts couldn't be loaded"
                _isInitialFeedLoading.value = false
            }
        }
    }

    fun loadMorePosts() {
        if (_isLoadingMore.value || !_hasMorePosts.value) return
        if (oldestLoadedSnapshot == null) return

        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = postsCollection
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .startAfter(oldestLoadedSnapshot!!)
                    .limit(FEED_PAGE_SIZE.toLong())
                    .get()
                    .await()

                val postsList = snapshot.documents.mapNotNull { it.toPost() }
                val sortedPosts = postsList.sortedByDescending { it.getTimestampLong() }
                val newPosts = sortedPosts.filterNot { post -> _rawPosts.value.any { it.postId == post.postId } }
                if (newPosts.isNotEmpty()) {
                    val userIds = newPosts.map { it.userId }.toSet()
                    val profiles = fetchUserProfiles(userIds)
                    val combinedProfiles = _userProfiles.value.toMutableMap().apply { putAll(profiles) }
                    val mergedPosts = trimFeedCache(
                        (_rawPosts.value + newPosts)
                            .distinctBy { it.postId }
                            .sortedByDescending { it.getTimestampLong() }
                    )

                    _userProfiles.value = combinedProfiles
                    _rawPosts.value = mergedPosts
                    applyPostFilters()
                    oldestLoadedTimestamp = mergedPosts.lastOrNull()?.getTimestampLong()
                    oldestLoadedSnapshot = snapshot.documents.lastOrNull() ?: oldestLoadedSnapshot
                }

                _hasMorePosts.value = newPosts.size >= FEED_PAGE_SIZE
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load older posts: ${e.message}", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    // Pause observing posts
// PostViewModel.kt
    fun pauseFeed() {
        if (postsListener != null) {
            detachPostsListener()
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
            val messagesRef = FirebaseRefs.db.getReference("messages/$conversationId")
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

    // Helper function to fetch a user's country by ID
    private suspend fun fetchUserCountry(userId: String): String {
        return try {
            val userRef = FirebaseRefs.db.getReference("users").child(userId)
            val snapshot = userRef.child("country").get().await()
            snapshot.getValue(String::class.java) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch country: ${e.message}")
            ""
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
            val postRef = postsCollection.document()
            val postId = postRef.id
            if (postId.isBlank()) {
                onFailure("Unable to generate post ID."); return@launch
            }

            val country = fetchUserCountry(userId)
            val post = mapOf(
                "postId"      to postId,
                "userId"      to userId,
                "username"    to username,
                "country"     to country,
                "contentText" to contentText,
                "timestamp"   to FieldValue.serverTimestamp(),
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
                postRef.set(post).await()
                clearUploadingFlag()
                refreshPosts()
                onSuccess()
                // Send notifications to friends and matches
                val matches = _matchedUsers.value
                matches.forEach { receiverId ->
                    val isCheckIn = checkIn != null                      // 🆕
                    val msg = if (isCheckIn)
                        "$username checked in at ${checkIn?.name} 📍"
                    else
                        "$username posted a new text update."
                    val notifType = if (isCheckIn) "match_checkin" else "match_post"   // 🆕
                    sendNotification(
                        receiverId     = receiverId,
                        type           = notifType,       // 🆕
                        senderId       = userId,
                        senderUsername = username,
                        message        = msg,
                        postId         = postId
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
            _isUploading.value = true
            try {
                // Ensure audio is in MP3 format
                val mp3Uri = ensureMp3Format(voiceUri, onFailure) ?: run {
                    clearUploadingFlag(); return@launch
                }

                // Check audio duration
                val duration = getAudioDuration(mp3Uri, onFailure) ?: run {
                    clearUploadingFlag(); return@launch
                }
                if (duration > VOICE_MAX_DURATION) {
                    onFailure("Voice recording exceeds the maximum allowed duration of $VOICE_MAX_DURATION seconds.")
                    clearUploadingFlag()
                    return@launch
                }

                // Check and compress audio if necessary
                val compressedVoiceUri = compressAudioIfNeeded(mp3Uri, VOICE_MAX_SIZE, onFailure) ?: run {
                    clearUploadingFlag(); return@launch
                }

                // Upload voice recording to Firebase Storage
                val downloadUrl = uploadMediaToStorage(compressedVoiceUri, "voices", onFailure) ?: run {
                    clearUploadingFlag(); return@launch
                }

                // Generate post ID
                val postRef = postsCollection.document()
                val postId = postRef.id
                if (postId.isBlank()) {
                    onFailure("Unable to generate post ID.")
                    clearUploadingFlag()
                    return@launch
                }

                val country = fetchUserCountry(userId)
                // Create Post object
                val post = mapOf(
                    "postId" to postId,
                    "userId" to userId,
                    "username" to username,
                    "country" to country,
                    "contentText" to null,
                    "timestamp" to FieldValue.serverTimestamp(),
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
                postRef.set(post).await()
                clearUploadingFlag()
                refreshPosts()
                onSuccess()

                // Send notifications to friends and matches
                val matches = _matchedUsers.value
                matches.forEach { receiverId ->
                    val msg = "$username posted a new voice note 🎤"
                    sendNotification(
                        receiverId     = receiverId,
                        type           = "match_post",   // ← NEW type (was "new_post")
                        senderId       = userId,
                        senderUsername = username,
                        message        = msg,
                        postId         = postId
                    )
                }
            } catch (e: Exception) {
                clearUploadingFlag()
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
            try {
                val postRef = postsCollection.document(postId)
                val postOwnerId = firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(postRef)
                    if (!snapshot.exists()) return@runTransaction null

                    val upvotes = (snapshot.getLong("upvotes") ?: 0L).toInt()
                    val downvotes = (snapshot.getLong("downvotes") ?: 0L).toInt()
                    val upvotedUsers = (snapshot.get("upvotedUsers") as? Map<String, Boolean>)?.toMutableMap()
                        ?: mutableMapOf()
                    val downvotedUsers = (snapshot.get("downvotedUsers") as? Map<String, Boolean>)?.toMutableMap()
                        ?: mutableMapOf()

                    var updatedUpvotes = upvotes
                    var updatedDownvotes = downvotes

                    if (upvotedUsers.containsKey(userId)) {
                        upvotedUsers.remove(userId)
                        updatedUpvotes -= 1
                    } else {
                        upvotedUsers[userId] = true
                        updatedUpvotes += 1
                        if (downvotedUsers.containsKey(userId)) {
                            downvotedUsers.remove(userId)
                            updatedDownvotes -= 1
                        }
                    }

                    transaction.update(
                        postRef,
                        mapOf(
                            "upvotes" to updatedUpvotes,
                            "downvotes" to updatedDownvotes,
                            "upvotedUsers" to upvotedUsers,
                            "downvotedUsers" to downvotedUsers
                        )
                    )
                    snapshot.getString("userId")
                }.await()

                withContext(Dispatchers.Main) {
                    onSuccess()
                    refreshSinglePost(postId)
                    if (!postOwnerId.isNullOrBlank() && postOwnerId != userId) {
                        val upvoterUsername = fetchUsernameById(userId)
                        val message = "$upvoterUsername upvoted your post."
                        sendNotification(
                            receiverId     = postOwnerId,
                            type           = "post_upvote",
                            senderId       = userId,
                            senderUsername = upvoterUsername,
                            message        = message,
                            postId         = postId
                        )
                    }
                }
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
            try {
                val postRef = postsCollection.document(postId)
                val postOwnerId = firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(postRef)
                    if (!snapshot.exists()) return@runTransaction null

                    val upvotes = (snapshot.getLong("upvotes") ?: 0L).toInt()
                    val downvotes = (snapshot.getLong("downvotes") ?: 0L).toInt()
                    val upvotedUsers = (snapshot.get("upvotedUsers") as? Map<String, Boolean>)?.toMutableMap()
                        ?: mutableMapOf()
                    val downvotedUsers = (snapshot.get("downvotedUsers") as? Map<String, Boolean>)?.toMutableMap()
                        ?: mutableMapOf()

                    var updatedUpvotes = upvotes
                    var updatedDownvotes = downvotes

                    if (downvotedUsers.containsKey(userId)) {
                        downvotedUsers.remove(userId)
                        updatedDownvotes -= 1
                    } else {
                        downvotedUsers[userId] = true
                        updatedDownvotes += 1
                        if (upvotedUsers.containsKey(userId)) {
                            upvotedUsers.remove(userId)
                            updatedUpvotes -= 1
                        }
                    }
                    transaction.update(
                        postRef,
                        mapOf(
                            "upvotes" to updatedUpvotes,
                            "downvotes" to updatedDownvotes,
                            "upvotedUsers" to upvotedUsers,
                            "downvotedUsers" to downvotedUsers
                        )
                    )
                    snapshot.getString("userId")
                }.await()

                withContext(Dispatchers.Main) {
                    onSuccess()
                    refreshSinglePost(postId)
                    if (!postOwnerId.isNullOrBlank() && postOwnerId != userId) {
                        val downvoterUsername = fetchUsernameById(userId)
                        val message = "$downvoterUsername downvoted your post."
                        sendNotification(
                            receiverId     = postOwnerId,
                            type           = "post_downvote",
                            senderId       = userId,
                            senderUsername = downvoterUsername,
                            message        = message,
                            postId         = postId
                        )
                    }
                }
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
            try {
                val postRef = postsCollection.document(postId)
                val commentOwnerId = firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(postRef)
                    val rawComments = snapshot.get("comments") as? Map<String, Any?> ?: emptyMap()
                    val commentData = rawComments[commentId] as? Map<*, *> ?: return@runTransaction null

                    val upvotes = (commentData["upvotes"] as? Number)?.toInt() ?: 0
                    val downvotes = (commentData["downvotes"] as? Number)?.toInt() ?: 0
                    val upvotedUsers = (commentData["upvotedUsers"] as? Map<String, Boolean>)?.toMutableMap()
                        ?: mutableMapOf()
                    val downvotedUsers = (commentData["downvotedUsers"] as? Map<String, Boolean>)?.toMutableMap()
                        ?: mutableMapOf()

                    var updatedUpvotes = upvotes
                    var updatedDownvotes = downvotes

                    if (upvotedUsers.containsKey(userId)) {
                        upvotedUsers.remove(userId)
                        updatedUpvotes -= 1
                    } else {
                        upvotedUsers[userId] = true
                        updatedUpvotes += 1
                        if (downvotedUsers.containsKey(userId)) {
                            downvotedUsers.remove(userId)
                            updatedDownvotes -= 1
                        }
                    }
                        val updatedComment = commentData.toMutableMap().apply {
                            this["upvotes"] = updatedUpvotes
                            this["downvotes"] = updatedDownvotes
                            this["upvotedUsers"] = upvotedUsers
                            this["downvotedUsers"] = downvotedUsers
                        }

                    val updatedComments = rawComments.toMutableMap().apply {
                        this[commentId] = updatedComment
                    }

                    transaction.update(postRef, "comments", updatedComments)
                    commentData["userId"] as? String
                }.await()

                withContext(Dispatchers.Main) {
                    onSuccess()
                    refreshSinglePost(postId)
                    if (!commentOwnerId.isNullOrBlank() && commentOwnerId != userId) {
                        val upvoterUsername = fetchUsernameById(userId)
                        val relationship = getRelationship(userId, commentOwnerId)
                        val relationshipText = if (relationship.isNotEmpty()) " - your $relationship" else ""
                        val message = "$upvoterUsername$relationshipText upvoted your comment."
                        sendNotification(
                            receiverId     = commentOwnerId,
                            type           = "comment_upvote",
                            senderId       = userId,
                            senderUsername = upvoterUsername,
                            message        = message,
                            postId         = postId,
                            commentId      = commentId
                        )
                    }
                }
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
            try {
                val postRef = postsCollection.document(postId)
                val commentOwnerId = firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(postRef)
                    val rawComments = snapshot.get("comments") as? Map<String, Any?> ?: emptyMap()
                    val commentData = rawComments[commentId] as? Map<*, *> ?: return@runTransaction null

                    val upvotes = (commentData["upvotes"] as? Number)?.toInt() ?: 0
                    val downvotes = (commentData["downvotes"] as? Number)?.toInt() ?: 0
                    val upvotedUsers = (commentData["upvotedUsers"] as? Map<String, Boolean>)?.toMutableMap()
                        ?: mutableMapOf()
                    val downvotedUsers = (commentData["downvotedUsers"] as? Map<String, Boolean>)?.toMutableMap()
                        ?: mutableMapOf()

                    var updatedUpvotes = upvotes
                    var updatedDownvotes = downvotes

                    if (downvotedUsers.containsKey(userId)) {
                        downvotedUsers.remove(userId)
                        updatedDownvotes -= 1
                    } else {
                        downvotedUsers[userId] = true
                        updatedDownvotes += 1
                        if (upvotedUsers.containsKey(userId)) {
                            upvotedUsers.remove(userId)
                            updatedUpvotes -= 1
                        }
                    }
                    val updatedComment = commentData.toMutableMap().apply {
                        this["upvotes"] = updatedUpvotes
                        this["downvotes"] = updatedDownvotes
                        this["upvotedUsers"] = upvotedUsers
                        this["downvotedUsers"] = downvotedUsers
                    }

                    val updatedComments = rawComments.toMutableMap().apply {
                        this[commentId] = updatedComment
                    }

                    transaction.update(postRef, "comments", updatedComments)
                    commentData["userId"] as? String
                }.await()

                withContext(Dispatchers.Main) {
                    onSuccess()
                    refreshSinglePost(postId)
                    if (!commentOwnerId.isNullOrBlank() && commentOwnerId != userId) {
                        val downvoterUsername = fetchUsernameById(userId)
                        val relationship = getRelationship(userId, commentOwnerId)
                        val relationshipText = if (relationship.isNotEmpty()) " - your $relationship" else ""
                        val message = "$downvoterUsername$relationshipText downvoted your comment."
                        sendNotification(
                            receiverId     = commentOwnerId,
                            type           = "comment_downvote",
                            senderId       = userId,
                            senderUsername = downvoterUsername,
                            message        = message,
                            postId         = postId,
                            commentId      = commentId
                        )
                    }
                }
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
                val postRef = postsCollection.document(postId)
                val commentId = postRef.collection("comments").document().id
                val newComment = comment.copy(commentId = commentId, timestamp = System.currentTimeMillis())

                val postOwnerId = firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(postRef)
                    if (!snapshot.exists()) return@runTransaction null

                    val rawComments = snapshot.get("comments") as? Map<String, Any?> ?: emptyMap()
                    val updatedComments = rawComments.toMutableMap().apply {
                        this[commentId] = mapOf(
                            "commentId" to newComment.commentId,
                            "userId" to newComment.userId,
                            "username" to newComment.username,
                            "commentText" to newComment.commentText,
                            "timestamp" to newComment.timestamp,
                            "upvotes" to newComment.upvotes,
                            "downvotes" to newComment.downvotes,
                            "upvotedUsers" to newComment.upvotedUsers,
                            "downvotedUsers" to newComment.downvotedUsers,
                            "mediaUrl" to newComment.mediaUrl
                        )
                    }

                    val totalComments = (snapshot.getLong("totalComments") ?: 0L) + 1L
                    transaction.update(
                        postRef,
                        mapOf(
                            "comments" to updatedComments,
                            "totalComments" to totalComments
                        )
                    )
                    snapshot.getString("userId")
                }.await()

                withContext(Dispatchers.Main) {
                    onSuccess()
                    refreshSinglePost(postId)
                    if (!postOwnerId.isNullOrBlank() && postOwnerId != comment.userId) {
                        val commenterUsername = fetchUsernameById(comment.userId)
                        val message = "$commenterUsername commented on your post."
                        sendNotification(
                            receiverId     = postOwnerId,
                            type           = "post_comment",
                            senderId       = comment.userId,
                            senderUsername = commenterUsername,
                            message        = message,
                            postId         = postId,
                            commentId      = commentId
                        )
                    }
                }
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

                val postSnapshot = postsCollection.document(postId).get().await()
                val post = postSnapshot.toPost() ?: run {
                    onFailure("Post not found")
                    return@launch
                }
                val username = fetchUsernameById(currentUserId)
                matches.forEach { matchId ->
                    val chatId = if (currentUserId < matchId) "${currentUserId}_$matchId" else "${matchId}_$currentUserId"
                    val messagesRef = FirebaseRefs.db.getReference("messages/$chatId")
                    val participantsRef = FirebaseRefs.db.getReference("chatParticipants/$chatId")
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

                    val notificationsRef = FirebaseRefs.db.getReference("notifications")
                    postNotification(
                        notificationsRef = notificationsRef,
                        toUserId = matchId,
                        fromUserId = currentUserId,
                        fromUsername = username,
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
                val targetId = canonicalGenderRes(filters.gender)
                filteredList = filteredList.filter { post ->
                    val profileGender = profiles[post.userId]?.gender
                    val profId = canonicalGenderRes(profileGender)
                    targetId == null || profId == targetId
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


    private suspend fun fetchUserProfiles(userIds: Set<String>): Map<String, Profile> {
        if (userIds.isEmpty()) return emptyMap()
        val cachedProfiles = ProfileCache.getProfiles(userIds)
        val matches = _matchedUsers.value
        return cachedProfiles.mapValues { (id, profile) ->
            val relationship = if (matches.contains(id)) "match" else null
            if (profile.relationship == relationship) {
                profile
            } else {
                profile.copy(relationship = relationship)
            }
        }
    }



    fun deletePost(
        postId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                postsCollection.document(postId).delete().await()
                refreshPosts()
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