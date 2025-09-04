package com.am24.am24

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Simple modifier that draws a thin vertical scrollbar based on the provided [LazyListState].
 * The scrollbar becomes visible whenever the content is scrollable.
 */
fun Modifier.visibleScrollbar(state: LazyListState): Modifier = composed {
    val density = LocalDensity.current
    val widthPx = with(density) { 4.dp.toPx() }
    val minHeightPx = with(density) { 16.dp.toPx() }
    val color = Color.White.copy(alpha = 0.4f)

    drawWithContent {
        drawContent()
        val totalItems = state.layoutInfo.totalItemsCount
        if (totalItems == 0) return@drawWithContent

        val firstVisible = state.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@drawWithContent
        val itemHeight = firstVisible.size
        if (itemHeight == 0) return@drawWithContent

        val viewportHeight = size.height
        val totalHeight = itemHeight * totalItems
        if (totalHeight <= viewportHeight) return@drawWithContent

        val scrollOffset = state.firstVisibleItemIndex * itemHeight + state.firstVisibleItemScrollOffset
        val proportion = viewportHeight / totalHeight.toFloat()
        val barHeight = (viewportHeight * proportion).coerceAtLeast(minHeightPx)
        val maxScroll = totalHeight - viewportHeight
        val yOffset = (scrollOffset.toFloat() / maxScroll) * (viewportHeight - barHeight)

        drawRect(
            color = color,
            topLeft = Offset(size.width - widthPx, yOffset),
            size = Size(widthPx, barHeight)
        )
    }
}