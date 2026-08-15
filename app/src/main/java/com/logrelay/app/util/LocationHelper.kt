package com.logrelay.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * ウィジェットタップという「一瞬で終わらせたい」文脈から呼ばれるため、
 * 通常のロケーション更新購読ではなく、単発取得(getCurrentLocation)を使う。
 *
 * 設計方針：
 * - 権限がなければ即座にnullを返す（ここでダイアログを出したりはしない。
 *   権限リクエストは本体アプリ側の初回起動フローで完結させる）
 * - 4秒でタイムアウト。ウィジェットタップの体感速度を優先し、
 *   精度より「記録が一瞬で完了する」ことを重視する
 * - 取得できなくても記録自体は失敗させない設計（呼び出し側でnull許容）
 */
object LocationHelper {

    private const val TIMEOUT_MS = 4000L

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocationOrNull(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(60_000L) // 直近1分以内のキャッシュがあれば即返す
            .build()

        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    client.getCurrentLocation(request, null)
                        .addOnSuccessListener { location ->
                            if (cont.isActive) cont.resume(location)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                } catch (e: SecurityException) {
                    // 権限が実行時に取り消されるレアケースへの保険
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}
