package com.logrelay.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.logrelay.app.R

/**
 * RelayLabデザイン基盤のタイポグラフィ。
 * 見出し・本文: Inter / ラベル系(時刻・タグ・件数などデータ的要素): JetBrains Mono
 *
 * どちらも可変フォント(1ファイルで全ウェイトをカバー、Google Fonts配布・OFLライセンス)。
 * ウェイトごとにFontVariation.Settingsでweight軸を指定してFontFamilyを構成している
 * (可変フォントの機軸描画はAPI 26以降。minSdk=26のため問題ない)。
 */
@OptIn(ExperimentalTextApi::class)
object RelayLabFonts {
    val Inter: FontFamily = FontFamily(
        Font(
            R.font.inter_variable,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            R.font.inter_variable,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))
        ),
        Font(
            R.font.inter_variable,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))
        ),
        Font(
            R.font.inter_variable,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))
        ),
    )

    val JetBrainsMono: FontFamily = FontFamily(
        Font(
            R.font.jetbrains_mono_variable,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            R.font.jetbrains_mono_variable,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))
        ),
        Font(
            R.font.jetbrains_mono_variable,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))
        ),
    )
}
