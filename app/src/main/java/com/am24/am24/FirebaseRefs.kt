package com.am24.am24

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object FirebaseRefs {
    // Copy EXACT URLs from the console
    private const val TAG = "FirebaseRefs"
    private const val PRIMARY_DB_URL = "https://kupidxdefault.asia-southeast1.firebasedatabase.app/"
    private const val BACKUP_DB_URL  = "https://kupidx.asia-southeast1.firebasedatabase.app/"
    private const val FAILOVER_TIMEOUT_MS = 5_000L
    private const val HEALTH_CHECK_INTERVAL_MS = 5 * 60 * 1_000L

    const val STORAGE_BUCKET = "gs://am-twentyfour.appspot.com"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val primaryDb: FirebaseDatabase = FirebaseDatabase.getInstance(PRIMARY_DB_URL)
    private val backupDb: FirebaseDatabase = FirebaseDatabase.getInstance(BACKUP_DB_URL)

    @Volatile private var activeDb: FirebaseDatabase = primaryDb
    @Volatile private var lastHealthCheckAt: Long = 0L
    @Volatile private var healthChecksStarted = false

    val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance(STORAGE_BUCKET) }

    init {
        enableOfflinePersistence(primaryDb, "primary")
        enableOfflinePersistence(backupDb, "backup")
        warmUp()
    }

    private fun enableOfflinePersistence(database: FirebaseDatabase, label: String) {
        try {
            database.setPersistenceEnabled(true)
        } catch (err: Exception) {
            Log.w(TAG, "Unable to enable persistence for $label database", err)
        }
    }

    private fun startHealthChecksIfNeeded() {
        if (healthChecksStarted) return
        synchronized(this) {
            if (healthChecksStarted) return
            healthChecksStarted = true
            scope.launch {
                while (isActive) {
                    runHealthCheck(force = true)
                    delay(HEALTH_CHECK_INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun runHealthCheck(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHealthCheckAt < HEALTH_CHECK_INTERVAL_MS) return
        lastHealthCheckAt = now

        val healthy = try {
            isDatabaseConnected(primaryDb)
        } catch (err: Exception) {
            Log.w(TAG, "Primary database health check threw", err)
            false
        }

        val target = if (healthy) primaryDb else backupDb
        if (activeDb !== target) {
            activeDb = target
            Log.w(TAG, "Realtime DB failover → ${if (target === primaryDb) "PRIMARY" else "BACKUP"}")
        } else if (!healthy) {
            Log.w(TAG, "Primary database unhealthy; continuing on backup")
        }
    }

    fun warmUp() {
        startHealthChecksIfNeeded()
        scope.launch {
            runHealthCheck(force = true)
        }
    }

    fun database(): FirebaseDatabase {
        startHealthChecksIfNeeded()
        if (System.currentTimeMillis() - lastHealthCheckAt > HEALTH_CHECK_INTERVAL_MS) {
            scope.launch { runHealthCheck(force = true) }
        }
        return activeDb
    }

    val db: FirebaseDatabase
        get() = database()

    fun currentDatabaseUrl(): String = if (activeDb === primaryDb) PRIMARY_DB_URL else BACKUP_DB_URL

    fun forceBackup() {
        activeDb = backupDb
        Log.w(TAG, "Realtime DB manually forced to BACKUP")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun isDatabaseConnected(database: FirebaseDatabase): Boolean {
        return withTimeoutOrNull(FAILOVER_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val ref = database.getReference(".info/connected")
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!continuation.isActive) return
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        if (connected) {
                            ref.removeEventListener(this)
                            continuation.resume(true) { /* no-op */ }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (!continuation.isActive) return
                        Log.w(TAG, "Primary database .info/connected listener cancelled: ${error.message}")
                        ref.removeEventListener(this)
                        continuation.resume(false) { /* no-op */ }
                    }
                }

                continuation.invokeOnCancellation {
                    ref.removeEventListener(listener)
                }

                ref.addValueEventListener(listener)
            }
        } ?: false
    }
}