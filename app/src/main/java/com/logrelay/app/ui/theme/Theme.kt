package com.logrelay.app.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun Color.lighten(fraction: Float): Color = lerp(this, Color.White, fraction)
private fun Color.darken(fraction: Float): Color = lerp(this, Color.Black, fraction)

/**
 * RelayLabファミリー共通のデザイン基盤(サブトラクティブ・パレット)に合わせたカラートークン。
 * プロパティ名は移行前のもの(Paper/Ink/Indigo等)を踏襲しつつ、値と役割をRelayLab基盤に合わせて
 * 再定義している(呼び出し側での置き換え漏れ・タイポを避けるため、名称は意図的に変えていない)。
 *
 * 4つの基準色(seed)は指示書のもの:
 * Primary(インディゴ) #1A237E / Secondary(グレー) #5D5D6A /
 * Tertiary(レンガ) #5C1800 / Neutral(オフホワイト) #F9F9F8
 * 各基準色からのコンテナ/オン系トーンは、白/黒方向への単純なブレンドで算出している
 * (参照画像のトーンスケールを厳密にピクセル採取したものではなく、近似値である点に注意)。
 */
object LogRelayColors {
    // --- Primary: インディゴ。主要アクション・選択状態・ウィジェット背景 ---
    val Indigo = Color(0xFF1A237E)
    val IndigoSoft = Indigo.lighten(0.88f)       // 淡色コンテナ(バッジ背景など)
    val OnIndigoSoft = Indigo.darken(0.1f)       // IndigoSoft上のテキスト

    // --- Secondary: グレー。補助UI・非アクティブ状態・本文の弱色 ---
    val InkFaint = Color(0xFF5D5D6A)

    // --- Tertiary: レンガ/ダークレッド。破壊的操作(削除・ゴミ箱)のみに限定使用 ---
    val Vermilion = Color(0xFF5C1800)
    val VermilionSoft = Vermilion.lighten(0.88f)

    // --- Inverted: 濃色背景+明色文字(強調が必要な特定箇所) ---
    val InvertedBg = Color(0xFF17181C)
    val InvertedText = Color(0xFFFFFFFF)

    // --- Neutral: オフホワイト基調の背景・面・罫線・本文色 ---
    val Paper = Color(0xFFF9F9F8)         // 背景
    val CardSurface = Color(0xFFFFFFFF)   // カード面(背景よりわずかに明るい)
    val Ink = Color(0xFF1F2126)           // 本文色
    val PaperDot = Color(0xFFE1E1DE)      // 区切り線・淡い境界線
    val Outline = Color(0xFFC9C9C6)       // Outlinedボタンなど、視認できる境界線
}

/**
 * ボタンの4スタイル(RelayLabデザイン基盤): Primary(塗りつぶし/主要アクション)、
 * Secondary(アウトライン風/補助アクション、実体は既存のTextButton運用を踏襲)、
 * Inverted(濃色背景+明色文字/強調箇所)、Outlined(境界線のみ/タブ切り替え等の選択UI)。
 * 既存のButton()呼び出しにcolorsとして渡すだけで適用できるよう、関数群として定義する。
 */
object RelayButtonColors {
    @Composable
    fun primary(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = LogRelayColors.Indigo,
        contentColor = Color.White
    )

    /** 破壊的操作の確定ボタンのみに使う、Primaryの限定バリエーション */
    @Composable
    fun primaryDestructive(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = LogRelayColors.Vermilion,
        contentColor = Color.White
    )

    @Composable
    fun inverted(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = LogRelayColors.InvertedBg,
        contentColor = LogRelayColors.InvertedText
    )

    @Composable
    fun outlined(): ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = LogRelayColors.Indigo
    )
}

private val quickLogColorScheme = lightColorScheme(
    primary = LogRelayColors.Indigo,
    onPrimary = Color.White,
    secondary = LogRelayColors.InkFaint,
    onSecondary = Color.White,
    tertiary = LogRelayColors.Vermilion,
    onTertiary = Color.White,
    background = LogRelayColors.Paper,
    onBackground = LogRelayColors.Ink,
    surface = LogRelayColors.CardSurface,
    onSurface = LogRelayColors.Ink,
    surfaceVariant = LogRelayColors.IndigoSoft,
    onSurfaceVariant = LogRelayColors.Indigo,
    outline = LogRelayColors.Outline,
)

// ラベル系(時刻表示・タグ・件数など、データをそのまま見せたい要素)用のテキストスタイル。
// 「日付スタンプ風」の装飾(太字・過剰な字間)は廃止し、JetBrains Monoの素の見え方を活かす。
val MonoLabelStyle = TextStyle(
    fontFamily = RelayLabFonts.JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    color = LogRelayColors.Indigo
)

private val quickLogTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = RelayLabFonts.Inter),
    displayMedium = Typography().displayMedium.copy(fontFamily = RelayLabFonts.Inter),
    displaySmall = Typography().displaySmall.copy(fontFamily = RelayLabFonts.Inter),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = RelayLabFonts.Inter),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = RelayLabFonts.Inter),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = RelayLabFonts.Inter),
    titleLarge = Typography().titleLarge.copy(fontFamily = RelayLabFonts.Inter),
    titleMedium = Typography().titleMedium.copy(fontFamily = RelayLabFonts.Inter),
    titleSmall = Typography().titleSmall.copy(fontFamily = RelayLabFonts.Inter),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = RelayLabFonts.Inter),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = RelayLabFonts.Inter),
    bodySmall = Typography().bodySmall.copy(fontFamily = RelayLabFonts.Inter),
    labelLarge = Typography().labelLarge.copy(fontFamily = RelayLabFonts.JetBrainsMono),
    labelMedium = Typography().labelMedium.copy(fontFamily = RelayLabFonts.JetBrainsMono),
    labelSmall = Typography().labelSmall.copy(fontFamily = RelayLabFonts.JetBrainsMono),
)

@Composable
fun LogRelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = quickLogColorScheme,
        typography = quickLogTypography,
        content = content
    )
}
