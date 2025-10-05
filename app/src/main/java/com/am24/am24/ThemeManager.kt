package com.am24.am24.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple manager that keeps track of the chosen theme and persists it.
 */
object ThemeManager {
    private const val PREFS_NAME = "settings"
    private const val KEY_DARK_THEME = "dark_theme_enabled"

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                val prefs = context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                _isDarkTheme.value = prefs.getBoolean(KEY_DARK_THEME, false)
                initialized = true
            }
        }
    }

    fun setDarkTheme(context: Context, darkTheme: Boolean) {
        val appContext = context.applicationContext
        initialize(appContext)
        _isDarkTheme.value = darkTheme
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_THEME, darkTheme)
            .apply()
    }
}