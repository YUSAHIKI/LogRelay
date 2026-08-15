package com.logrelay.app.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.logrelay.app.data.RecordRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 設定されたフォルダに、バックアップZIPを定期的に書き出すバックグラウンド処理。
 * 保存先フォルダが未設定、またはアクセス権限が失効している場合は静かに諦める
 * (ユーザーがアプリを開いたときに再設定してもらう想定。通知などでの失敗報告は今回は行わない)。
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val folderUriString = SettingsStore.getBackupFolderUri(applicationContext) ?: return Result.success()
        val folderUri = Uri.parse(folderUriString)
        val treeDoc = DocumentFile.fromTreeUri(applicationContext, folderUri) ?: return Result.success()
        if (!treeDoc.canWrite()) return Result.success()

        return try {
            val filenameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val filename = "logrelay_autobackup_${filenameFormat.format(Date())}.zip"
            val newFile = treeDoc.createFile("application/zip", filename) ?: return Result.retry()

            val repository = RecordRepository(applicationContext)
            applicationContext.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                repository.exportBackupZip(out)
            }

            pruneOldBackups(treeDoc)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** 自動バックアップが際限なく溜まらないよう、古いものから消して直近5件だけ残す */
    private fun pruneOldBackups(treeDoc: DocumentFile) {
        val backups = treeDoc.listFiles()
            .filter { it.name?.startsWith("logrelay_autobackup_") == true }
            .sortedByDescending { it.name }
        backups.drop(5).forEach { it.delete() }
    }
}
