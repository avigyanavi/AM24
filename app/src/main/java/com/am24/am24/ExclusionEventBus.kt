package com.am24.am24

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Simple event bus that broadcasts user IDs to exclude from the People tab.
 * Screens can emit an ID after likes/compliments/dislikes so MapScreen can
 * update its exclusion list immediately.
 */
object ExclusionEventBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun emit(uid: String) {
        _events.tryEmit(uid)
    }
}