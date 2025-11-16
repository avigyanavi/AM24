package com.am24.am24.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ThreadsSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 6.dp,
    val md: Dp = 8.dp,
    val lg: Dp = 12.dp,
    val xl: Dp = 16.dp,
    val xxl: Dp = 20.dp,
    val screenEdge: Dp = 18.dp,
    val cardPadding: Dp = 12.dp,
    val itemSpacing: Dp = 10.dp
)

@Immutable
data class ThreadsIconSizes(
    val xs: Dp = 12.dp,
    val sm: Dp = 14.dp,
    val md: Dp = 18.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp
)

@Immutable
data class ThreadsAvatarSizes(
    val feed: Dp = 36.dp,
    val comment: Dp = 28.dp,
    val profileLarge: Dp = 88.dp
)

val LocalThreadsSpacing = staticCompositionLocalOf { ThreadsSpacing() }
val LocalThreadsIconSizes = staticCompositionLocalOf { ThreadsIconSizes() }
val LocalThreadsAvatarSizes = staticCompositionLocalOf { ThreadsAvatarSizes() }