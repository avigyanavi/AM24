package com.am24.am24

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ProfileViewModel"
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val database = FirebaseDatabase.getInstance()

    /**
     * Swipe Right to like a user profile.
     */
    fun swipeRight(
        currentUserId: String,
        targetUserId: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$targetUserId")
                val otherUserSwipesRef = database.getReference("swipes/$targetUserId/$currentUserId")

                val currentUserLikesGivenRef = database.getReference("likesGiven/$currentUserId/$targetUserId")
                val otherUserLikesReceivedRef = database.getReference("likesReceived/$targetUserId/$currentUserId")

                // Record the swipe right with timestamp
                val swipeData = SwipeData(liked = true, timestamp = timestamp)
                currentUserSwipesRef.setValue(swipeData)

                // Update likesGiven and likesReceived
                currentUserLikesGivenRef.setValue(timestamp)
                otherUserLikesReceivedRef.setValue(timestamp)

                // Check if the other user has swiped right on the current user
                otherUserSwipesRef.get().addOnSuccessListener { snapshot ->
                    val otherUserSwipeData = snapshot.getValue(SwipeData::class.java)
                    val otherUserSwipedRight = otherUserSwipeData?.liked == true

                    if (otherUserSwipedRight) {
                        // It's a match!
                        val currentUserMatchesRef = database.getReference("matches/$currentUserId/$targetUserId")
                        val otherUserMatchesRef = database.getReference("matches/$targetUserId/$currentUserId")
                        currentUserMatchesRef.setValue(timestamp)
                        otherUserMatchesRef.setValue(timestamp)

                        // Optionally, send notifications to both users about the match
                    }
                }.await()

                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to swipe right: ${e.message}")
                onFailure(e.message ?: "Unknown error occurred.")
            }
        }
    }

    /**
     * Swipe Left to dislike a user profile.
     */
    fun swipeLeft(
        currentUserId: String,
        targetUserId: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val currentUserSwipesRef = database.getReference("swipes/$currentUserId/$targetUserId")

                // Record the swipe left with timestamp
                val swipeData = SwipeData(liked = false, timestamp = timestamp)
                currentUserSwipesRef.setValue(swipeData).await()

                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to swipe left: ${e.message}")
                onFailure(e.message ?: "Unknown error occurred.")
            }
        }
    }

    /**
     * Upvote a user profile.
     */
    fun upvoteProfile(
        profileId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileRef = usersRef.child(profileId)
            try {
                profileRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val profile = currentData.getValue(Profile::class.java) ?: return Transaction.success(currentData)

                        if (profile.profileUpvotes.containsKey(userId)) {
                            // Remove upvote
                            profile.profileUpvotes.remove(userId)
                            profile.upvoteCount -= 1
                        } else {
                            // Add upvote
                            profile.profileUpvotes[userId] = true
                            profile.upvoteCount += 1
                            // Remove downvote if it exists
                            if (profile.profileDownvotes.containsKey(userId)) {
                                profile.profileDownvotes.remove(userId)
                                profile.downvoteCount -= 1
                            }
                        }

                        currentData.value = profile
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                        if (error != null) {
                            onFailure(error.message)
                        } else {
                            onSuccess()
                        }
                    }
                })
            } catch (e: Exception) {
                onFailure(e.message ?: "Upvote failed.")
            }
        }
    }

    /**
     * Downvote a user profile.
     */
    fun downvoteProfile(
        profileId: String,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileRef = usersRef.child(profileId)
            try {
                profileRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val profile = currentData.getValue(Profile::class.java) ?: return Transaction.success(currentData)

                        if (profile.profileDownvotes.containsKey(userId)) {
                            // Remove downvote
                            profile.profileDownvotes.remove(userId)
                            profile.downvoteCount -= 1
                        } else {
                            // Add downvote
                            profile.profileDownvotes[userId] = true
                            profile.downvoteCount += 1
                            // Remove upvote if it exists
                            if (profile.profileUpvotes.containsKey(userId)) {
                                profile.profileUpvotes.remove(userId)
                                profile.upvoteCount -= 1
                            }
                        }

                        currentData.value = profile
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                        if (error != null) {
                            onFailure(error.message)
                        } else {
                            onSuccess()
                        }
                    }
                })
            } catch (e: Exception) {
                onFailure(e.message ?: "Downvote failed.")
            }
        }
    }

    /**
     * Report a user profile.
     */
    fun reportProfile(
        profileId: String,
        reporterId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reportRef = FirebaseDatabase.getInstance().getReference("reportedProfiles").child(profileId)
                val reportId = reportRef.push().key ?: throw Exception("Unable to generate report ID.")

                val reportData = mapOf(
                    "reportId" to reportId,
                    "profileId" to profileId,
                    "reporterId" to reporterId,
                    "timestamp" to ServerValue.TIMESTAMP
                )

                reportRef.child(reportId).setValue(reportData).await()
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report profile: ${e.message}")
                onFailure(e.message ?: "Failed to report profile.")
            }
        }
    }

    /**
     * Block a user profile.
     */
    fun blockProfile(
        currentUserId: String,
        targetUserId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val blockedRef = usersRef.child(currentUserId).child("blockedUsers").child(targetUserId)
                blockedRef.setValue(true).await()
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to block profile: ${e.message}")
                onFailure(e.message ?: "Failed to block profile.")
            }
        }
    }
}
