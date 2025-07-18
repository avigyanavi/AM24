package com.am24.am24.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Define custom colors
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val AccentGreen = Color(0xFF00bf63)
val DarkGrayBackground = Color(0xFF1A1A1A)


// App theme composable for the rest of the app
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Black,             // Main background color for the app.
            onPrimary = White,           // Text or icon color on primary elements.
            secondary = AccentGreen,     // Accent color for interactive components.
            onSecondary = White,         // Text color on secondary components.
            background = DarkGrayBackground,          // General background color.
            onBackground = White,        // Text color on background.
            surface = DarkGrayBackground,             // Surface color for cards and other elements.
            onSurface = White            // Text color on components that use the surface color.
        ),
        typography = Typography(),       // Use default typography or customize as needed.
        content = content
    )
}