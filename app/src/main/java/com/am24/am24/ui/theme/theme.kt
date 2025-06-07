package com.am24.am24.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// custom colors
private val Black       = Color(0xFF000000)
private val White       = Color(0xFFFFFFFF)
private val AccentGreen = Color(0xFF00BF63)

// Light scheme
private val LightColorScheme = lightColorScheme(
    primary       = White,
    onPrimary     = Black,
    secondary     = AccentGreen,
    onSecondary   = White,
    background    = White,
    onBackground  = Black,
    surface       = White,
    onSurface     = Black,
)

// Dark scheme
private val DarkColorScheme = darkColorScheme(
    primary       = Black,
    onPrimary     = White,
    secondary     = AccentGreen,
    onSecondary   = Black,
    background    = Black,
    onBackground  = White,
    surface       = Black,
    onSurface     = White,
)

// Splash uses always-dark
private val SplashColorScheme = darkColorScheme(
    primary       = Black,
    onPrimary     = White,
    background    = Black,
    onBackground  = White,
    surface       = Black,
    onSurface     = White,
)

/** Use for your splash screen only */
@Composable
fun SplashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SplashColorScheme,
        typography  = Typography(),
        content     = content
    )
}

/**
 * AppTheme lets you toggle light/dark via [darkTheme].
 * Default follows system setting.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography  = Typography(),
        content     = content
    )
}
