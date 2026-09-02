package com.logrelay.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 1件の記録 = 「その瞬間の位置＋時刻」
 */
@Entity(
    tableName = "records",
    indices = [Index(value = ["sourceTriggerId"], unique = true)]
)
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // epoch millis。端末のローカル時刻から取得
    val timestamp: Long,

    // 取得できなかった場合(権限拒否・GPS取得失敗)はnullを許容し、
    // 記録自体は失敗させない（時刻だけでも残す）
    val latitude: Double? = null,
    val longitude: Double? = null,

    // 分類タグ。固定5スロットのキー(SLOT_KEYSのいずれか)を格納し、未分類ならnull。
    // 表示名はSettingsStore側で編集可能なため、ここにはキーのみを持たせる
    // (名称を後から変えても過去の記録の表示が自動的に追従するように)。
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
    val photoPath: String? = null,

    // 「＋」ボタンからの手動追加で、現在時刻以外の過去の時刻を選んで記録したことを示すフラグ。
    // 位置情報欄への「※後から追加」注記の表示判定に使う(一覧上のバッジ等には使わない)。
    // WearOSのmanual_pending(位置なし)経路もこのフラグを意図的に再利用している
    // (本来は「位置情報が記録操作時点のものと必ずしも一致しない」という時間差の警告として
    // 設計したものだが、manual_pendingでは位置情報自体が存在しないため、警告の性質としては
    // 「位置なしそのものの明示」に近い。表示パターンを増やさないための意図的な転用であり、
    // 実装漏れではない)。
    val isManualPast: Boolean = false,

    // WearOSトリガー経由の記録の重複処理防止に使うID(DataItemのUUID)。
    // ウィジェット/NFC等、WearOS以外の経路からの記録は常にnullのまま(既存動作を変えない)。
    // UNIQUE制約により、同じIDでのINSERTはSQLiteConstraintExceptionになる
    // (SQLiteのUNIQUE制約はnull同士を「等しくない」ものとして扱うため、
    // null値の行が何件あってもこの制約には抵触しない)。
    val sourceTriggerId: String? = null
) {
    companion object {
        // タグの固定5スロット。個数・並び順は固定で、増減や並び替えはしない仕様。
        // 各スロットの表示名(初期値は下記)はSettingsStoreで編集可能。
        val TAG_SLOT_KEYS = listOf("tag_1", "tag_2", "tag_3", "tag_4", "tag_5")
        val TAG_DEFAULT_LABELS = mapOf(
            "tag_1" to "外出",
            "tag_2" to "仕事",
            "tag_3" to "食事",
            "tag_4" to "家事",
            "tag_5" to "その他"
        )
    }
}
