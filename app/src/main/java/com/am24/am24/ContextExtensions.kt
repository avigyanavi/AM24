package com.am24.am24

import android.app.Application
import android.content.Context
import android.content.ContextWrapper

/**
 * Traverses the ContextWrapper chain to find an [Application] instance.
 * Returns null if no application can be found, allowing callers to bail out safely.
 */
fun Context.findApplication(): Application? {
    var currentContext: Context = this
    while (true) {
        when (currentContext) {
            is Application -> return currentContext
            is ContextWrapper -> {
                val base = currentContext.baseContext ?: return null
                if (base === currentContext) {
                    return null
                }
                currentContext = base
            }
            else -> return null
        }
    }
}