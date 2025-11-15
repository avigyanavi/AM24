package com.am24.am24

import com.google.firebase.database.FirebaseDatabase

fun createMatch(
    database: FirebaseDatabase,
    currentUserId: String,
    otherUserId: String
) {
    val ts = System.currentTimeMillis()
    val updates = mapOf(
        "matches/$currentUserId/$otherUserId" to ts,
        "matches/$otherUserId/$currentUserId" to ts
    )
    database.reference.updateChildren(updates)
}