package com.logrelay.app.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {

    private const val WORK_NAME = "logrelay_auto_backup"

    /**
     * intervalHours <= 0 なら自動バックアップを停止する。
     * policy=UPDATEは設定変更時、KEEPはアプリ起動時の「念のための再確認」に使う
     * (KEEPなら既に動いている定期実行のタイマーをリセットしない)。
     */
    fun schedule(context: Context, intervalHours: Long, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE) {
        val workManager = WorkManager.getInstance(context)
        if (intervalHours <= 0) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // WorkManagerの定期実行は15分未満を指定できない仕様のため、安全のため下限を設ける
        val safeInterval = intervalHours.coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(safeInterval, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }
}
