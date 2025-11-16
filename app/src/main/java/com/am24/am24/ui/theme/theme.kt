package com.am24.am24.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

import com.am24.am24.ui.theme.LocalThreadsAvatarSizes
import com.am24.am24.ui.theme.LocalThreadsIconSizes
import com.am24.am24.ui.theme.LocalThreadsSpacing
import com.am24.am24.ui.theme.ThreadsAvatarSizes
import com.am24.am24.ui.theme.ThreadsIconSizes
import com.am24.am24.ui.theme.ThreadsSpacing

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

private val ThreadsTypography = Typography(
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium
    )
)

// App theme composable for the rest of the app
@Composable
fun AppTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val managerDarkTheme = ThemeManager.isDarkTheme.collectAsState().value
    val useDarkTheme = darkTheme ?: managerDarkTheme
    val spacing = ThreadsSpacing()
    val iconSizes = ThreadsIconSizes()
    val avatarSizes = ThreadsAvatarSizes()
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalThreadsSpacing provides spacing,
        LocalThreadsIconSizes provides iconSizes,
        LocalThreadsAvatarSizes provides avatarSizes,
        LocalDensity provides Density(
            density = density.density * 0.94f,
            fontScale = 0.9f
        )
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
            typography = ThreadsTypography,
            content = content
        )
    }
}