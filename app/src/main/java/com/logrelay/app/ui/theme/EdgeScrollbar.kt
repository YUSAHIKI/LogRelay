package com.logrelay.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * スワイプでのスクロールに加えて、右端をドラッグ/タップして
 * リスト内を素早く移動できるようにするバー(LazyColumn用)。
 */
@Composable
fun BoxScope.EdgeScrollbar(listState: LazyListState, totalItemCount: Int) {
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(0f) }

    val visibleCount = listState.layoutInfo.visibleItemsInfo.size
    if (totalItemCount <= visibleCount || totalItemCount == 0) return

    val firstVisible = listState.firstVisibleItemIndex.coerceIn(0, totalItemCount - 1)
    val scrollFraction = firstVisible.toFloat() / totalItemCount.toFloat()
    val thumbHeightFraction = (visibleCount.toFloat() / totalItemCount.toFloat()).coerceIn(0.08f, 1f)

    fun jumpTo(yPx: Float) {
        val fraction = (yPx / trackHeightPx).coerceIn(0f, 1f)
        val targetIndex = (fraction * totalItemCount).roundToInt().coerceIn(0, totalItemCount - 1)
        scope.launch { listState.scrollToItem(targetIndex) }
    }

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .pointerInput(totalItemCount) {
                detectTapGestures { offset -> jumpTo(offset.y) }
            }
            .pointerInput(totalItemCount) {
                detectDragGestures { change, _ ->
                    change.consume()
                    jumpTo(change.position.y)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight(thumbHeightFraction)
                .width(4.dp)
                .offset { IntOffset(-6, (trackHeightPx * scrollFraction).roundToInt()) }
                .background(LogRelayColors.Indigo.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
        )
    }
}

/**
 * カード(グリッド)表示用のスクロールバー。LazyGridStateもLazyListStateと
 * 同じ形の情報(firstVisibleItemIndex, layoutInfo)を持つため、ロジックは共通。
 */
@Composable
fun BoxScope.EdgeScrollbarGrid(gridState: LazyGridState, totalItemCount: Int) {
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(0f) }

    val visibleCount = gridState.layoutInfo.visibleItemsInfo.size
    if (totalItemCount <= visibleCount || totalItemCount == 0) return

    val firstVisible = gridState.firstVisibleItemIndex.coerceIn(0, totalItemCount - 1)
    val scrollFraction = firstVisible.toFloat() / totalItemCount.toFloat()
    val thumbHeightFraction = (visibleCount.toFloat() / totalItemCount.toFloat()).coerceIn(0.08f, 1f)

    fun jumpTo(yPx: Float) {
        val fraction = (yPx / trackHeightPx).coerceIn(0f, 1f)
        val targetIndex = (fraction * totalItemCount).roundToInt().coerceIn(0, totalItemCount - 1)
        scope.launch { gridState.scrollToItem(targetIndex) }
    }

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .pointerInput(totalItemCount) {
                detectTapGestures { offset -> jumpTo(offset.y) }
            }
            .pointerInput(totalItemCount) {
                detectDragGestures { change, _ ->
                    change.consume()
                    jumpTo(change.position.y)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight(thumbHeightFraction)
                .width(4.dp)
                .offset { IntOffset(-6, (trackHeightPx * scrollFraction).roundToInt()) }
                .background(LogRelayColors.Indigo.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
        )
    }
}
