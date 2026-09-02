package com.logrelay.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import com.logrelay.app.util.ExportHelper
import com.logrelay.app.util.GeocodeHelper
import com.logrelay.app.util.LocationHelper
import com.logrelay.app.util.PhotoStorage
import com.logrelay.app.wearprotocol.WearTrigger
import java.util.concurrent.TimeUnit

// ゴミ箱に入れた記録を何日間保持するか
private const val TRASH_RETENTION_DAYS = 7L

/** captureFromWatchの結果。AlreadyProcessedは異常系ではなく、DataItemの重複配送を示す正常な結果 */
sealed class CaptureFromWatchResult {
    data class Inserted(val record: Record) : CaptureFromWatchResult()
    data object AlreadyProcessed : CaptureFromWatchResult()
}

class RecordRepository(context: Context) {

    private val dao = RecordDatabase.getInstance(context).recordDao()
    private val appContext = context.applicationContext

    /**
     * ウィジェットの「ワンタップ記録」から呼ばれる中心的な処理。
     * 位置が取れなくても時刻だけは必ず記録する（記録の失敗を作らない）。
     */
    suspend fun captureNow(): Record {
        val location = LocationHelper.getCurrentLocationOrNull(appContext)
        val record = Record(
            timestamp = System.currentTimeMillis(),
            latitude = location?.latitude,
            longitude = location?.longitude
        )
        val id = dao.insert(record)
        return record.copy(id = id)
    }

    /**
     * アプリ内「＋」ボタンからの手動追加。指定したtimestampで記録を作る。
     * 位置情報は(ウィジェット経由のcaptureNowと同様)記録操作を行った時点＝現在地取得時点のものを使う。
     * 選んだ時刻が過去の時刻の場合(isPastEntry=true)、位置情報欄への注記表示のためtagに印を付ける。
     */
    suspend fun captureManual(timestamp: Long, isPastEntry: Boolean): Record {
        val location = LocationHelper.getCurrentLocationOrNull(appContext)
        val record = Record(
            timestamp = timestamp,
            latitude = location?.latitude,
            longitude = location?.longitude,
            isManualPast = isPastEntry
        )
        val id = dao.insert(record)
        return record.copy(id = id)
    }

    /**
     * WearOSトリガー(DataClient経由)からの記録。ウィジェット/NFCと同様、新しい記録ロジックは作らず
     * 既存のRecord/DAO/day-boundary処理をそのまま使う。ウォッチ側では記録ロジックを持たない。
     *
     * source="watch_gps"の場合のみ座標を採用する。それ以外(manual_pending、または座標が
     * 欠落した不正なwatch_gps Payload)は位置なしで記録する。「後から追加」注記(isManualPast)は
     * manual_pendingの場合のみ立てる(座標欠落は診断ログを残すのみで注記は立てない。既存の
     * GPS取得失敗時の挙動と揃えるため)。
     *
     * sourceTriggerId(DataItemのUUID)にUNIQUE制約を持たせているため、同一DataItemの重複配送は
     * SQLiteConstraintExceptionとして検出できる。呼び出し側(WearableListenerService)は
     * AlreadyProcessedを異常系ではなく「既に処理済みなのでDataItemを削除してよい」という
     * 正常な結果として扱うこと。
     */
    suspend fun captureFromWatch(
        tapTime: Long,
        source: String,
        latitude: Double?,
        longitude: Double?,
        sourceTriggerId: String
    ): CaptureFromWatchResult {
        val hasValidWatchGps = source == WearTrigger.SOURCE_WATCH_GPS && latitude != null && longitude != null
        val record = Record(
            timestamp = tapTime,
            latitude = if (hasValidWatchGps) latitude else null,
            longitude = if (hasValidWatchGps) longitude else null,
            isManualPast = source == WearTrigger.SOURCE_MANUAL_PENDING,
            sourceTriggerId = sourceTriggerId
        )
        return try {
            val id = dao.insert(record)
            CaptureFromWatchResult.Inserted(record.copy(id = id))
        } catch (e: SQLiteConstraintException) {
            CaptureFromWatchResult.AlreadyProcessed
        }
    }

    suspend fun updateMemo(recordId: Long, memo: String) {
        val existing = dao.getById(recordId) ?: return
        dao.update(existing.copy(memo = memo))
    }

    /** 分類タグを設定/解除する。tag=nullで未分類に戻す */
    suspend fun updateTag(recordId: Long, tag: String?) {
        dao.updateTag(recordId, tag)
    }

    fun observeAll() = dao.observeAll()

    fun observeForDay(startOfDay: Long, endOfDay: Long) = dao.observeForDay(startOfDay, endOfDay)

    fun observeTrash() = dao.observeTrash()

    /** 誤タップ対策：即座に一覧から消すが、物理削除はせずゴミ箱に移す */
    suspend fun softDelete(recordId: Long) {
        dao.softDelete(recordId, System.currentTimeMillis())
    }

    /** 複数選択からのまとめ削除。こちらも論理削除のみ（Snackbarで一括Undo可能にするため） */
    suspend fun softDeleteMany(recordIds: List<Long>) {
        if (recordIds.isEmpty()) return
        dao.softDeleteMany(recordIds, System.currentTimeMillis())
    }

    /** ゴミ箱からの復元 */
    suspend fun restore(recordId: Long) {
        dao.restore(recordId)
    }

    suspend fun restoreMany(recordIds: List<Long>) {
        if (recordIds.isEmpty()) return
        dao.restoreMany(recordIds)
    }

    /**
     * まだ地名を取得していない記録に対して、逆ジオコーディングを行いキャッシュする。
     * 失敗しても例外を投げず、静かに諦める(UI側はlatitude/longitude表示のまま)。
     */
    suspend fun resolvePlaceName(record: Record) {
        if (record.placeName != null) return
        val lat = record.latitude ?: return
        val lng = record.longitude ?: return
        val name = GeocodeHelper.reverseGeocode(appContext, lat, lng)
        if (!name.isNullOrBlank()) {
            dao.updatePlaceName(record.id, name)
        }
    }

    /** ゴミ箱から選択した記録を完全に削除する。呼び出し元でユーザーの明示確認を必ず取ること */
    suspend fun hardDeleteMany(recordIds: List<Long>) {
        if (recordIds.isEmpty()) return
        dao.hardDeleteMany(recordIds)
    }

    /**
     * 保持期限(TRASH_RETENTION_DAYS)を過ぎたゴミ箱の記録を完全に削除する。
     * アプリ起動時に一度呼ぶ想定（バックグラウンド定期実行の仕組みは今は持たない）。
     */
    suspend fun purgeExpiredTrash() {
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(TRASH_RETENTION_DAYS)
        dao.purgeExpired(threshold)
    }

    /** 写真を1枚添付する。既に添付済みの写真があれば古いファイルは削除して差し替える */
    /** 写真を1枚添付する。既に添付済みの写真があれば古いファイルは削除して差し替える。新しいパスを返す */
    suspend fun attachPhoto(recordId: Long, sourceUri: Uri): String? {
        val existing = dao.getById(recordId) ?: return null
        val newPath = PhotoStorage.copyToInternalStorage(appContext, sourceUri) ?: return null
        PhotoStorage.delete(existing.photoPath)
        dao.updatePhotoPath(recordId, newPath)
        return newPath
    }

    suspend fun removePhoto(recordId: Long) {
        val existing = dao.getById(recordId) ?: return
        PhotoStorage.delete(existing.photoPath)
        dao.updatePhotoPath(recordId, null)
    }

    /** 渡された記録の一覧をMarkdownとして書き出す(呼び出し側が「今表示している範囲」を渡す想定) */
    fun exportMarkdown(records: List<Record>): String {
        return ExportHelper.toMarkdown(records)
    }

    /** 渡された記録の一覧をCSVとして書き出す */
    fun exportCsv(records: List<Record>): String {
        return ExportHelper.toCsv(records)
    }

    /** ゴミ箱の中身も含めた完全なバックアップを、写真の実体も同梱したZIPとして書き出す */
    suspend fun exportBackupZip(outputStream: java.io.OutputStream) {
        val records = dao.getAllRaw()
        com.logrelay.app.util.BackupHelper.writeZip(records, outputStream)
    }

    /**
     * バックアップZIPから復元する。既存データ(写真含む)は全て消え、バックアップの内容で置き換わる。
     * 呼び出し元で必ず「上書きされる」ことをユーザーに確認してから呼ぶこと。
     *
     * ZIPの読み出しを先に済ませてからDBへ触る。ZIPが壊れていて例外になった時点では
     * まだ既存データに手を付けていないため、復元に失敗しても元のデータが残る。
     */
    suspend fun restoreFromBackupZip(inputStream: java.io.InputStream) {
        val records = com.logrelay.app.util.BackupHelper.readZip(appContext, inputStream)
        dao.replaceAll(records)
    }

    companion object {
        const val TRASH_RETENTION_DAYS_CONST = TRASH_RETENTION_DAYS
    }
}
