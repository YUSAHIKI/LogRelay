# LogRelay Wear用 ProGuard/R8ルール
#
# Compose・Wearable Data Layer・play-services-locationはそれぞれのAARに
# consumer-rules.proを同梱しており、通常はここに書かなくても自動的に守られる。
# マニフェストに宣言したActivity(MainActivity)もAndroidの既定ルールで自動的に保護される。
# リフレクションで生成/呼び出される独自コードは今のところないため、追加のルールはない。
