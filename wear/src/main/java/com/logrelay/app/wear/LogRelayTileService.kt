package com.logrelay.app.wear

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"
private const val INDIGO = 0xFF1A237E.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/**
 * ホーム画面のタイルから、画面オン・アクティブ状態でのタップだけで記録を送れるようにする。
 * タイル自体は静的な内容(動的データの取得なし)なので、ListenableFutureは即時完了で返す。
 *
 * タップ時の実際の送信処理はMainActivity側(EXTRA_AUTO_SEND=true起動)で行う。
 * TileのClickableはActivityの起動しかできない(バックグラウンドで直接処理を実行する手段がない)ため。
 *
 * 「送信OK」表示はDataClientへの登録完了を意味し、フォンでの記録完了を意味しないことに注意
 * (設計仕様書7節)。振動・トースト・詳細なレイアウトデザインは今回未確定のため最小構成としている。
 *
 * TileService/RequestBuilders/TileBuilders自体はandroidx.wear.tiles、レイアウトを組み立てる
 * ビルダー群(LayoutElementBuilders等)はandroidx.wear.protolayoutと、パッケージが分かれている点に注意
 * (androidx.wear.tiles側のレイアウトビルダーは現行のTileBuilders.Tile.Builderの引数型と一致しない)。
 */
class LogRelayTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(buildLayout(this))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }

    private fun buildLayout(context: Context): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("tap_to_record")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        // MainActivityは「起動＝記録」方式(設計仕様書5.1節)のため、
                        // extraなしの通常起動で足りる(ホームキー二回押し・ランチャー起動と同じ経路)
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        val tapButton = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(64f))
            .setHeight(dp(64f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(INDIGO))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder().setRadius(dp(32f)).build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("＋")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setColor(argb(WHITE))
                            .setSize(sp(28f))
                            .build()
                    )
                    .build()
            )
            .build()

        return LayoutElementBuilders.Column.Builder()
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("LogRelay")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setColor(argb(WHITE))
                            .setSize(sp(12f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build()
            )
            .addContent(tapButton)
            .build()
    }
}
