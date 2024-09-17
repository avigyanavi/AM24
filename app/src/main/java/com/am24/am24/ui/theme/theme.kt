package com.am24.am24.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            secondary = Color.White,
            background = Color.Black,
            onSecondary = Color.White,
            onBackground = Color.White
        ),
        content = content
    )
}
