package com.logrelay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 「手帳のドット方眼紙」をモチーフにしたカラートークン。
 * ライフログ・振り返りという行為の温度感（紙とインク）を軸に、
 * よくあるAI生成デザインの定型（クリーム×セリフ×テラコッタ）は避けている。
 */
object LogRelayColors {
    val Paper = Color(0xFFFAF7F0)       // 方眼紙の地色
    val PaperDot = Color(0xFFD9D3C3)    // 方眼紙のドット（ごく薄く）
    val Ink = Color(0xFF2B2A33)         // 万年筆インクを意識した本文色
    val InkFaint = Color(0xFF6E6C78)    // 補助テキスト（薄いインク）
    val Indigo = Color(0xFF37477A)      // メインアクセント：ペンのインク色
    val IndigoSoft = Color(0xFFE7E9F2)  // インディゴの淡色（バッジ背景など）
    val Vermilion = Color(0xFFB23B32)   // 赤ペン添削を思わせるサブアクセント
    val CardSurface = Color(0xFFFFFFFF) // 記録カードの紙面（本紙よりわずかに白い）
}

private val quickLogColorScheme = lightColorScheme(
    primary = LogRelayColors.Indigo,
    onPrimary = LogRelayColors.Paper,
    secondary = LogRelayColors.Vermilion,
    background = LogRelayColors.Paper,
    onBackground = LogRelayColors.Ink,
    surface = LogRelayColors.CardSurface,
    onSurface = LogRelayColors.Ink,
    surfaceVariant = LogRelayColors.IndigoSoft,
    onSurfaceVariant = LogRelayColors.Indigo,
)

// タイムスタンプ表示用：日付スタンプを意識してmonospaceを使い、
// 数字の並びが紙に押されたスタンプのように見えるようにする
val StampTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    letterSpacing = 0.5.sp,
    color = LogRelayColors.Indigo
)

private val quickLogTypography = Typography()

@Composable
fun LogRelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = quickLogColorScheme,
        typography = quickLogTypography,
        content = content
    )
}
