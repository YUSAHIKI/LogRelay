package com.logrelay.app.service

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.logrelay.app.data.CaptureFromWatchResult
import com.logrelay.app.data.RecordRepository
import com.logrelay.app.wearprotocol.WearTrigger
import kotlinx.coroutines.runBlocking

private const val TAG = "LogRelayWearListener"

/**
 * WearOSトリガー(v1: watch_gps / manual_pending)のフォン側受け口。
 * 新しい記録ロジックは作らず、ウィジェット/NFCと共通のRecordRepositoryを呼ぶだけ
 * (day-boundary判定・「後から追加」注記もすべて既存のフォン側処理に委ねる)。
 *
 * onDataChanged自体はメインスレッドではなくバックグラウンドスレッドで呼ばれる
 * (Wearable Data Layerの仕様)ため、runBlockingでローカルDBへの短い書き込みを
 * 同期的に行っても問題ない。
 */
class LogRelayWearableListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            val repository = RecordRepository(applicationContext)
            val dataClient = Wearable.getDataClient(this)

            dataEvents.forEach { event -> handleEvent(event, repository, dataClient) }
        } finally {
            dataEvents.release()
        }
    }

    private fun handleEvent(
        event: DataEvent,
        repository: RecordRepository,
        dataClient: com.google.android.gms.wearable.DataClient
    ) {
        if (event.type != DataEvent.TYPE_CHANGED) return
        val dataItem = event.dataItem
        val path = dataItem.uri.path ?: return
        if (!path.startsWith(WearTrigger.PATH_PREFIX)) return

        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
        val tapTime = dataMap.getLong(WearTrigger.KEY_TAP_TIME, -1L)
        val source = dataMap.getString(WearTrigger.KEY_SOURCE)
        if (tapTime <= 0L || source == null) {
            Log.w(TAG, "不正なPayloadのため処理をスキップする: $path")
            return
        }

        val latitude = if (dataMap.containsKey(WearTrigger.KEY_LAT)) dataMap.getDouble(WearTrigger.KEY_LAT) else null
        val longitude = if (dataMap.containsKey(WearTrigger.KEY_LNG)) dataMap.getDouble(WearTrigger.KEY_LNG) else null
        if (source == WearTrigger.SOURCE_WATCH_GPS && (latitude == null || longitude == null)) {
            // 不正Payload: watch_gpsのはずが座標欠落。位置なしへ格下げする(captureFromWatch側の処理)
            Log.w(TAG, "watch_gpsのはずが座標が欠落していたため、位置なしへ格下げする: $path")
        }

        // DataItemのパスからUUID部分を取り出し、重複処理防止用のsourceTriggerIdとして使う
        val sourceTriggerId = path.removePrefix(WearTrigger.PATH_PREFIX)

        try {
            val result = runBlocking {
                repository.captureFromWatch(
                    tapTime = tapTime,
                    source = source,
                    latitude = latitude,
                    longitude = longitude,
                    sourceTriggerId = sourceTriggerId
                )
            }
            // Inserted・AlreadyProcessedのどちらも「処理済み」を意味するため、DataItemを削除してよい
            when (result) {
                is CaptureFromWatchResult.Inserted -> Unit
                is CaptureFromWatchResult.AlreadyProcessed -> Log.i(TAG, "重複配送のため記録済みDataItemとしてスキップ: $path")
            }
            dataClient.deleteDataItems(dataItem.uri)
        } catch (e: Exception) {
            // 記録失敗時は、再試行方針が確定するまでDataItemを削除しない(設計仕様書6.3節)
            Log.e(TAG, "WearOSトリガーの記録処理に失敗した。DataItemは削除せず残す: $path", e)
        }
    }
}
