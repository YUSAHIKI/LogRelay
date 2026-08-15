package com.logrelay.app.data

import android.content.Context
import android.net.Uri
import com.logrelay.app.util.ExportHelper
import com.logrelay.app.util.GeocodeHelper
import com.logrelay.app.util.LocationHelper
import com.logrelay.app.util.PhotoStorage
import java.util.concurrent.TimeUnit

// ゴミ箱に入れた記録を何日間保持するか
private const val TRASH_RETENTION_DAYS = 7L

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

    suspend fun updateMemo(recordId: Long, memo: String) {
        val existing = dao.getById(recordId) ?: return
        dao.update(existing.copy(memo = memo))
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
     */
    suspend fun restoreFromBackupZip(inputStream: java.io.InputStream) {
        val records = com.logrelay.app.util.BackupHelper.readZip(appContext, inputStream)
        dao.deleteAllRaw()
        dao.insertAll(records)
    }

    companion object {
        const val TRASH_RETENTION_DAYS_CONST = TRASH_RETENTION_DAYS
    }
}
