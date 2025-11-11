package com.am24.am24

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

object FirebaseRefs {
    private const val TAG = "FirebaseRefs"
    private const val PRIMARY_DB_URL = "https://kupidxdefault.asia-southeast1.firebasedatabase.app/"

    const val STORAGE_BUCKET = "gs://am-twentyfour.appspot.com"

    private val primaryDb: FirebaseDatabase = FirebaseDatabase.getInstance(PRIMARY_DB_URL)

    val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance(STORAGE_BUCKET) }

    init {
        enableOfflinePersistence(primaryDb, "primary")
        warmUp()
    }

    private fun enableOfflinePersistence(database: FirebaseDatabase, label: String) {
        try {
            database.setPersistenceEnabled(true)
        } catch (err: Exception) {
            Log.w(TAG, "Unable to enable persistence for $label database", err)
        }
    }

    fun warmUp() {
        Log.d(TAG, "warmUp() no-op; primary database already selected")
    }

    fun database(): FirebaseDatabase = primaryDb

    val db: FirebaseDatabase
        get() = primaryDb

    fun currentDatabaseUrl(): String = PRIMARY_DB_URL

    fun forceBackup() {
        Log.w(TAG, "forceBackup() no-op; primary database only mode")
    }
}