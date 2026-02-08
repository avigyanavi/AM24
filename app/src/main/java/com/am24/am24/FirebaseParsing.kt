package com.am24.am24

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.firestore.DocumentSnapshot
private val gson = com.google.gson.Gson()

private const val PARSING_TAG = "FirebaseParsing"

fun DataSnapshot.safeGetProfile(context: String): Profile? {
    return try {
        getValue(Profile::class.java)
    } catch (e: Exception) {
        Log.w(PARSING_TAG, "Failed to parse Profile at $context (${key ?: "no-key"}): ${e.message}")
        null
    }
}

fun DocumentSnapshot.safeGetProfile(context: String): Profile? {
    return try {
        toObject(Profile::class.java)
    } catch (e: Exception) {
        Log.w(PARSING_TAG, "Failed to parse Profile at $context (${id.ifBlank { "no-id" }}): ${e.message}")
        null
    }
}

fun Any?.safeMapToProfile(context: String): Profile? {
    val map = this as? Map<*, *> ?: return null
    return try {
        gson.fromJson(gson.toJson(map), Profile::class.java)
    } catch (e: Exception) {
        Log.w(PARSING_TAG, "Failed to map Profile at $context: ${e.message}")
        null
    }
}