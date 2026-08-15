package com.logrelay.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 位置情報の先頭に添える、テーマ色で塗れる簡易マップピン */
@Composable
fun PinIcon(tint: Color, modifier: Modifier = Modifier, iconSize: Dp = 13.dp) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, h)
            cubicTo(w * 0.1f, h * 0.55f, w * 0.1f, 0f, w / 2f, 0f)
            cubicTo(w * 0.9f, 0f, w * 0.9f, h * 0.55f, w / 2f, h)
            close()
        }
        drawPath(path, color = tint)
        drawCircle(color = Color.White, radius = w * 0.16f, center = Offset(w / 2f, h * 0.38f))
    }
}
