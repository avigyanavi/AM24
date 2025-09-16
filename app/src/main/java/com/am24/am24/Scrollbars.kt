package com.am24.am24

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.consumeDownChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Simple modifier that draws a thin vertical scrollbar based on the provided [LazyListState].
 * The scrollbar becomes visible whenever the content is scrollable.
 */
fun Modifier.visibleScrollbar(state: LazyListState): Modifier = composed {
    val density = LocalDensity.current
    val widthPx = with(density) { 4.dp.toPx() }
    val minHeightPx = with(density) { 16.dp.toPx() }
    val inactiveColor = Color.White.copy(alpha = 0.4f)
    val activeColor = Color.White.copy(alpha = 0.7f)
    var isDragging by remember { mutableStateOf(false) }

    fun LazyListLayoutInfo.averageItemSize(): Float? {
        val visibleItems = visibleItemsInfo
        if (visibleItems.isEmpty()) return null
        val totalSize = visibleItems.sumOf { it.size }
        if (totalSize <= 0) return null
        return totalSize.toFloat() / visibleItems.size
    }

    suspend fun LazyListState.scrollToScrollbarPosition(positionY: Float, containerHeight: Float) {
        if (containerHeight <= 0f) return
        val layoutInfo = layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) return
        val averageItemSize = layoutInfo.averageItemSize() ?: return
        val totalHeight = averageItemSize * totalItems
        if (totalHeight <= containerHeight) return
        val maxScroll = totalHeight - containerHeight
        if (maxScroll <= 0f) return

        val clampedY = positionY.coerceIn(0f, containerHeight)
        val fraction = (clampedY / containerHeight).coerceIn(0f, 1f)
        val targetScroll = fraction * maxScroll
        val targetIndex = (targetScroll / averageItemSize).toInt().coerceIn(0, totalItems - 1)
        val targetOffset = (targetScroll - targetIndex * averageItemSize).roundToInt().coerceAtLeast(0)
        scrollToItem(targetIndex, targetOffset)
    }

    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val layoutWidth = size.width.toFloat()
            val layoutHeight = size.height.toFloat()
            if (layoutWidth <= 0f || layoutHeight <= 0f) return@awaitEachGesture

            val detectionWidth = widthPx * 2f
            val layoutInfo = state.layoutInfo
            val averageItemSize = layoutInfo.averageItemSize()
            val totalItems = layoutInfo.totalItemsCount
            val totalHeight = averageItemSize?.times(totalItems) ?: 0f
            val isScrollable = totalItems > 0 && averageItemSize != null && totalHeight > layoutHeight
            if (!isScrollable || down.position.x < layoutWidth - detectionWidth) {
                return@awaitEachGesture
            }

            down.consumeDownChange()
            isDragging = true
            try {
                state.scrollToScrollbarPosition(down.position.y, layoutHeight)
                drag(down.id) { change ->
                    change.consumeAllChanges()
                    state.scrollToScrollbarPosition(change.position.y, layoutHeight)
                }
            } finally {
                isDragging = false
            }
        }
    }.drawWithContent {
        drawContent()
        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val averageItemSize = layoutInfo.averageItemSize()
        val containerHeight = size.height
        if (totalItems == 0 || averageItemSize == null || containerHeight <= 0f) return@drawWithContent

        val totalHeight = averageItemSize * totalItems
        if (totalHeight <= containerHeight) return@drawWithContent

        val maxScroll = totalHeight - containerHeight
        val scrollOffset = state.firstVisibleItemIndex * averageItemSize + state.firstVisibleItemScrollOffset.toFloat()
        val constrainedOffset = if (maxScroll > 0f) scrollOffset.coerceIn(0f, maxScroll) else 0f
        val barHeight = (containerHeight * (containerHeight / totalHeight)).coerceAtLeast(minHeightPx)
        val yOffset = if (maxScroll > 0f) {
            (constrainedOffset / maxScroll) * (containerHeight - barHeight)
        } else {
            0f
        }
        val color = if (isDragging) activeColor else inactiveColor
        drawRect(
            color = color,
            topLeft = Offset(size.width - widthPx, yOffset),
            size = Size(widthPx, barHeight)
        )
    }
}