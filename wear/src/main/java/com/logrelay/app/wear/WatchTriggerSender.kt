package com.logrelay.app.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.logrelay.app.wearprotocol.WearTrigger
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val GPS_TIMEOUT_MS = 8000L

/**
 * タップ操作からDataClient送信までの一連の処理。
 * ウォッチは記録ロジックを持たない薄いトリガーであり、tap_timeと(取れれば)位置情報を
 * フォンへ渡すだけ。DBへの書き込み・day-boundary判定・「後から追加」注記は
 * すべてフォン側の既存共通処理(RecordRepository.captureFromWatch)に委ねる。
 *
 * v1ではNodeClientによる接続確認は行わない。DataClientは接続の有無に関わらず
 * ローカル保持→再接続時同期を行うため、送信前に接続状態を確認する意味がないため
 * (詳細は設計仕様書3節「v1での簡略化」を参照)。
 */
object WatchTriggerSender {

    sealed class Result {
        data class Sent(val source: String) : Result()
        data class Failed(val message: String) : Result()
    }

    suspend fun sendTrigger(context: Context): Result {
        // tap_timeは他の処理(GPS取得など)を待たず、タップの瞬間に即時確定する
        val tapTime = System.currentTimeMillis()
        val location = tryGetLocation(context)
        val source = if (location != null) WearTrigger.SOURCE_WATCH_GPS else WearTrigger.SOURCE_MANUAL_PENDING

        val uuid = UUID.randomUUID().toString()
        val putDataMapRequest = PutDataMapRequest.create(WearTrigger.PATH_PREFIX + uuid).apply {
            dataMap.putLong(WearTrigger.KEY_TAP_TIME, tapTime)
            dataMap.putString(WearTrigger.KEY_SOURCE, source)
            if (location != null) {
                dataMap.putDouble(WearTrigger.KEY_LAT, location.first)
                dataMap.putDouble(WearTrigger.KEY_LNG, location.second)
            }
        }
        // 通信復帰を待たず即座にローカルのData Layerへ登録するため urgent を指定する
        val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()

        return try {
            Wearable.getDataClient(context).putDataItem(putDataRequest).await()
            Result.Sent(source)
        } catch (e: Exception) {
            // putDataItem()の成功はローカルData Layerへの登録成功を示すのみで、
            // フォンでの処理完了を意味しない(設計仕様書3節)。ここでの失敗は
            // ローカル登録自体が失敗した稀なケースのみを指す
            Result.Failed(e.message ?: "送信に失敗しました")
        }
    }

    /**
     * ウォッチ内蔵GPSでのFix取得。フォン側のLocationHelperと同じ方針
     * (単発取得・タイムアウトで諦める・権限なしや失敗時はnullを返すだけで例外を投げない)を踏襲するが、
     * ネットワーク位置情報での代替は使わずGPS自体の精度を優先する(PRIORITY_HIGH_ACCURACY)。
     */
    private suspend fun tryGetLocation(context: Context): Pair<Double, Double>? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        return withTimeoutOrNull(GPS_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    client.getCurrentLocation(request, null)
                        .addOnSuccessListener { location ->
                            if (cont.isActive) {
                                cont.resume(location?.let { it.latitude to it.longitude })
                            }
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}
