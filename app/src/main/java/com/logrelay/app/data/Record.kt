package com.logrelay.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1件の記録 = 「その瞬間の位置＋時刻」
 *
 * tag は今回のMVPでは未使用だが、将来ウィジェットC（アイコン選択型）や
 * バレットジャーナル層向けの分類機能を足すときにマイグレーションなしで
 * 対応できるよう、最初からnull許容カラムとして確保しておく。
 */
@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // epoch millis。端末のローカル時刻から取得
    val timestamp: Long,

    // 取得できなかった場合(権限拒否・GPS取得失敗)はnullを許容し、
    // 記録自体は失敗させない（時刻だけでも残す）
    val latitude: Double? = null,
    val longitude: Double? = null,

    // 将来のアイコン分類・バレットジャーナル記号などに使う拡張余地
    val tag: String? = null,

    // 夜の振り返りで追記するメモ
    val memo: String = "",

    // 論理削除の日時(epoch millis)。nullなら削除されていない。
    // 物理削除ではなくここに時刻を入れることで、ゴミ箱からの復元・
    // 一定期間後の自動パージ(RETENTION_DAYS)の両方に対応できる。
    val deletedAt: Long? = null,

    // 逆ジオコーディングで取得した地名のキャッシュ。
    // 毎回APIを叩かないよう、一度取得できたら保存しておく。
    val placeName: String? = null,

    // 添付写真のアプリ内部ストレージ上のパス(1ログにつき1枚まで)
    val photoPath: String? = null
)
