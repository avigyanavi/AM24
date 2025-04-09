package com.am24.am24

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object VideoCacheProvider {
    // Use a private volatile variable to hold the cache instance.
    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: SimpleCache(
                File(context.cacheDir, "videoCache"),
                LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024) // e.g., 100 MB
            ).also { simpleCache = it }
        }
    }
}
