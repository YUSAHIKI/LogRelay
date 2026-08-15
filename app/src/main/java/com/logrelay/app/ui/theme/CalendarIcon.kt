package com.logrelay.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 絵文字の📅は独自の配色を持ちテーマカラーに染まらないため、
 * アプリのアクセントカラーで塗れる最小限のカレンダーアイコンを自前で描画する。
 */
@Composable
fun CalendarIcon(tint: Color, modifier: Modifier = Modifier, iconSize: Dp = 16.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val strokeWidth = 1.4.dp.toPx()
        val bodyTop = size.height * 0.18f
        val cornerRadius = CornerRadius(2.dp.toPx())

        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, size.height - bodyTop),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = tint,
            start = Offset(0f, bodyTop + size.height * 0.2f),
            end = Offset(size.width, bodyTop + size.height * 0.2f),
            strokeWidth = strokeWidth
        )
        val ringY = bodyTop * 0.4f
        drawLine(tint, Offset(size.width * 0.25f, 0f), Offset(size.width * 0.25f, ringY + bodyTop), strokeWidth)
        drawLine(tint, Offset(size.width * 0.75f, 0f), Offset(size.width * 0.75f, ringY + bodyTop), strokeWidth)
    }
}
