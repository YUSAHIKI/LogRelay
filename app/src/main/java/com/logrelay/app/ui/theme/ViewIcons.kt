package com.logrelay.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 横長リスト表示への切り替えアイコン(3本の横線) */
@Composable
fun ListViewIcon(tint: Color, modifier: Modifier = Modifier, iconSize: Dp = 16.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val strokeWidth = 1.6.dp.toPx()
        val ys = listOf(size.height * 0.2f, size.height * 0.5f, size.height * 0.8f)
        ys.forEach { y ->
            drawLine(tint, Offset(0f, y), Offset(size.width, y), strokeWidth)
        }
    }
}

/** カード(グリッド)表示への切り替えアイコン(2x2の四角) */
@Composable
fun GridViewIcon(tint: Color, modifier: Modifier = Modifier, iconSize: Dp = 16.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val cell = size.width * 0.42f
        val gap = size.width * 0.16f
        listOf(0, 1).forEach { row ->
            listOf(0, 1).forEach { col ->
                drawRect(
                    color = tint,
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell)
                )
            }
        }
    }
}
