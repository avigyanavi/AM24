package com.am24.am24

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

data class OmegleMatch(
    val chatId: String,
    val otherUserId: String
)

suspend fun matchRandomOmegleUser(): OmegleMatch? {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
    val db = FirebaseDatabase.getInstance().reference
    val presenceSnap = db.child("presence").get().await()
    val onlineUsers = presenceSnap.children.mapNotNull { it.key }.filter { it != uid }
    if (onlineUsers.isEmpty()) return null
    val partnerId = onlineUsers.random()
    val chatId = db.child("omegleChats").push().key ?: return null
    val chatData = mapOf(
        "user1" to uid,
        "user2" to partnerId,
        "ended" to false
    )
    db.child("omegleChats").child(chatId).setValue(chatData).await()

    // Also create an invite so the partner can accept the chat
    db.child("omegleInvites")
        .child(partnerId)
        .child(chatId)
        .setValue(uid)
        .await()
    return OmegleMatch(chatId, partnerId)
}

suspend fun inviteOmegleUser(targetUid: String): OmegleMatch? {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
    val db = FirebaseDatabase.getInstance().reference
    val chatId = db.child("omegleChats").push().key ?: return null
    val chatData = mapOf(
        "user1" to uid,
        "user2" to targetUid,
        "ended" to false,
        "status" to "pending"
    )
    db.child("omegleChats").child(chatId).setValue(chatData).await()
    db.child("omegleInvites")
        .child(targetUid)
        .child(chatId)
        .setValue(uid)
        .await()
    return OmegleMatch(chatId, targetUid)
}