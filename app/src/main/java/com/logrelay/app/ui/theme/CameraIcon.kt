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

/** 写真添付済みであることを示す簡易カメラアイコン */
@Composable
fun CameraIcon(tint: Color, modifier: Modifier = Modifier, iconSize: Dp = 13.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val strokeWidth = 1.3.dp.toPx()
        val bodyTop = size.height * 0.28f
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, size.height - bodyTop),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )
        // 上部のファインダー突起
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.32f, 0f),
            size = Size(size.width * 0.36f, bodyTop * 0.9f),
            cornerRadius = CornerRadius(1.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )
        // レンズ
        drawCircle(
            color = tint,
            radius = size.width * 0.18f,
            center = Offset(size.width / 2f, bodyTop + (size.height - bodyTop) / 2f),
            style = Stroke(width = strokeWidth)
        )
    }
}
