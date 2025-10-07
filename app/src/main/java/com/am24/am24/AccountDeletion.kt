package com.am24.am24

import android.util.Log
import com.firebase.geofire.GeoFire
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object AccountDeletion {
    private const val TAG = "AccountDeletion"

    suspend fun deleteAccount(
        auth: FirebaseAuth = FirebaseAuth.getInstance(),
        db: FirebaseDatabase = FirebaseRefs.db,
        storage: FirebaseStorage = FirebaseRefs.storage
    ) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        try {
            // Remove user posts
            val postsRef = db.getReference("posts")
            val postsSnap = postsRef.orderByChild("userId").equalTo(uid).get().await()
            for (child in postsSnap.children) {
                val mediaUrl = child.child("mediaUrl").getValue(String::class.java)
                val mediaThumb = child.child("mediaThumb").getValue(String::class.java)

                listOfNotNull(mediaUrl, mediaThumb).forEach { url ->
                    try {
                        storage.getReferenceFromUrl(url).delete().await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete storage object: $url", e)
                    }
                }

                try {
                    child.ref.removeValue().await()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete post", e)
                }
            }

            // Remove comments left by the user on other posts (including voice comments)
            val allPostsSnap = postsRef.get().await()
            for (post in allPostsSnap.children) {
                val comments = post.child("comments")
                for (comment in comments.children) {
                    val commenterId = comment.child("userId").getValue(String::class.java)
                    if (commenterId == uid) {
                        val mediaUrl = comment.child("mediaUrl").getValue(String::class.java)
                        if (!mediaUrl.isNullOrBlank()) {
                            try {
                                storage.getReferenceFromUrl(mediaUrl).delete().await()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to delete comment media: $mediaUrl", e)
                            }
                        }
                        try {
                            comment.ref.removeValue().await()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete comment", e)
                        }
                    }
                }
            }

            // Remove matches and chats
            val matchesRef = db.getReference("matches")
            val userChatsRef = db.getReference("userChats")
            val chatsRef = db.getReference("chats")
            val messagesRef = db.getReference("messages")
            val matchesSnap = matchesRef.child(uid).get().await()
            for (child in matchesSnap.children) {
                val other = child.key ?: continue
                try {
                    matchesRef.child(other).child(uid).removeValue().await()
                    matchesRef.child(uid).child(other).removeValue().await()
                    userChatsRef.child(uid).child(other).removeValue().await()
                    userChatsRef.child(other).child(uid).removeValue().await()
                    val conv = listOf(uid, other).sorted().joinToString("_")
                    chatsRef.child(conv).removeValue().await()
                    val conversationRef = messagesRef.child(conv)
                    val conversationSnap = conversationRef.get().await()
                    for (message in conversationSnap.children) {
                        val sender = message.child("senderId").getValue(String::class.java)
                        if (sender == uid) {
                            val mediaUrl = message.child("mediaUrl").getValue(String::class.java)
                            if (!mediaUrl.isNullOrBlank()) {
                                try {
                                    storage.getReferenceFromUrl(mediaUrl).delete().await()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to delete message media: $mediaUrl", e)
                                }
                            }
                        }
                    }
                    conversationRef.removeValue().await()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clean chat for $other", e)
                }
            }

            // Remove notifications
            db.getReference("notifications").child(uid).removeValue().await()

            // Remove GeoFire location
            val geoFire = GeoFire(db.getReference("geoFireLocations"))
            geoFire.removeLocation(uid) { _, err ->
                err?.let { Log.e(TAG, "GeoFire error: ${it.message}") }
            }

            // Remove user entry
            db.getReference("users").child(uid).removeValue().await()

            // Remove storage data
            val userStorage = storage.reference.child("users").child(uid)
            try { userStorage.child("profile_pic.jpg").delete().await() } catch (_: Exception) {}
            try { userStorage.child("voice_note.mp3").delete().await() } catch (_: Exception) {}
            try {
                val list = userStorage.child("photos").listAll().await()
                list.items.forEach { item ->
                    try { item.delete().await() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            try {
                val privateList = userStorage.child("private").listAll().await()
                privateList.items.forEach { item ->
                    try { item.delete().await() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Delete auth account
            user.delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "deleteAccount failed: ${e.message}", e)
            throw e
        }
    }
}