package com.logrelay.app.wearprotocol

/**
 * WearOSトリガー機能(v1: watch_gps / manual_pendingの2経路)のプロトコル定数。
 * フォン(:app)とウォッチ(:wear)の両方から参照する、DataItemのパス・DataMapキー・sourceの値の
 * 単一の定義元。文字列リテラルを両モジュールに個別に書くと、片方だけ打ち間違えたときに
 * サイレントに動かなくなる(DataClientはパス/型が一致しないと単に配送しないだけで、
 * エラーにもならない)ため、このモジュールに一本化している。
 *
 * phone_gps_pendingはフェーズ2(付録B)で追加予定。現時点では未実装。
 */
object WearTrigger {
    /** DataItemのパスプレフィックス。実際のパスは "${PATH_PREFIX}{uuid}" */
    const val PATH_PREFIX = "/log-trigger/"

    // DataMapのキー
    const val KEY_TAP_TIME = "tap_time"
    const val KEY_SOURCE = "source"
    const val KEY_LAT = "lat"
    const val KEY_LNG = "lng"

    // sourceの値(v1)
    const val SOURCE_WATCH_GPS = "watch_gps"
    const val SOURCE_MANUAL_PENDING = "manual_pending"
}
