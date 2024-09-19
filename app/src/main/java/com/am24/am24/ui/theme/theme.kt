package com.am24.am24.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun BlogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            secondary = Color.Black,
            background = Color.White,
            onBackground = Color.Black
        ),
        content = content
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            secondary = Color.White,
            background = Color.Black,
            onBackground = Color.White
        ),
        content = content
    )
}
