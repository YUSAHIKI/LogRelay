package com.logrelay.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * 手帳のドット方眼紙を模した背景。
 * 24dp間隔でごく薄いドットを敷き、「記録一覧＝手帳の1ページ」という
 * メタファーを画面全体の下地として表現する。
 * (dp間隔は実機の1画面あたりのドット数が多すぎて重くならない値に調整済み)
 */
@Composable
fun DotGridBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val spacing = 24.dp.toPx()
            val dotRadius = 1.1.dp.toPx()
            var y = spacing
            while (y < size.height) {
                var x = spacing
                while (x < size.width) {
                    drawCircle(
                        color = LogRelayColors.PaperDot,
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                    x += spacing
                }
                y += spacing
            }
        }
        content()
    }
}
