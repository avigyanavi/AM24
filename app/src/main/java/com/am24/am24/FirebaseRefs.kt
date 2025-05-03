package com.am24.am24

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

object FirebaseRefs {
    // Copy EXACT URLs from the console
    const val DB_URL = "https://kupidxdefault.asia-southeast1.firebasedatabase.app/"
    const val STORAGE_BUCKET = "gs://am-twentyfour"

    val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }
    val storage: FirebaseStorage  by lazy { FirebaseStorage.getInstance(STORAGE_BUCKET) }
}