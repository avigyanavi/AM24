package com.am24.am24.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color

// Define custom colors
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val AccentGreen = Color(0xFF00bf63)
val DarkGrayBackground = Color(0xFF1C1C1C)
val LightGrayBackground = Color(0xFFF5F5F5)

private val DarkColorScheme = darkColorScheme(
    primary = Black,             // Main background color for the app.
    onPrimary = White,           // Text or icon color on primary elements.
    secondary = AccentGreen,     // Accent color for interactive components.
    onSecondary = White,         // Text color on secondary components.
    background = DarkGrayBackground,          // General background color.
    onBackground = White,        // Text color on background.
    surface = DarkGrayBackground,             // Surface color for cards and other elements.
    onSurface = White            // Text color on components that use the surface color.
)

private val LightColorScheme = lightColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = AccentGreen,
    onSecondary = White,
    background = LightGrayBackground,
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1C)
)

// App theme composable for the rest of the app
@Composable
fun AppTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val managerDarkTheme = ThemeManager.isDarkTheme.collectAsState().value
    val useDarkTheme = darkTheme ?: managerDarkTheme
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),       // Use default typography or customize as needed.
        content = content
    )
}