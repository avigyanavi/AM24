package com.am24.am24

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

/**
 * Activity-level traces capture app startup and entry-point overhead.
 * Screen/tab traces are scoped to composable lifetimes to surface memory growth as users navigate.
 */
private fun heapUsedMb(): Long {
    val runtime = Runtime.getRuntime()
    val usedBytes = runtime.totalMemory() - runtime.freeMemory()
    return usedBytes / 1024 / 1024
}

fun startPerfTrace(name: String, attributes: Map<String, String> = emptyMap()): Trace? {
    val app = runCatching {
        FirebaseApp.getApps(MyApp.instance).firstOrNull() ?: FirebaseApp.initializeApp(MyApp.instance)
    }.getOrNull() ?: return null
    val trace = FirebasePerformance.getInstance(app).newTrace(name)
    attributes.forEach { (key, value) -> trace.putAttribute(key, value) }
    trace.start()
    trace.putMetric("heap_used_mb_start", heapUsedMb())
    return trace
}

fun stopPerfTrace(trace: Trace?) {
    if (trace == null) return
    trace.putMetric("heap_used_mb_end", heapUsedMb())
    trace.stop()
}

@Composable
fun TrackScreenPerformance(screenName: String, attributes: Map<String, String> = emptyMap()) {
    DisposableEffect(screenName) {
        val trace = startPerfTrace(
            name = "screen_$screenName",
            attributes = attributes + mapOf("screen" to screenName)
        )
        onDispose { stopPerfTrace(trace) }
    }
}

@Composable
fun TrackTabPerformance(screenName: String, tabName: String) {
    DisposableEffect(tabName) {
        val trace = startPerfTrace(
            name = "tab_${screenName}_$tabName",
            attributes = mapOf("screen" to screenName, "tab" to tabName)
        )
        onDispose { stopPerfTrace(trace) }
    }
}