package com.logrelay.app.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 座標から「大まかな地名」を取得する。
 * 都道府県+市区町村+できれば町名までを1行にまとめる簡易フォーマット。
 * ネットワーク環境やGeocoderの実装によっては失敗することがあり、
 * その場合は呼び出し側で緯度経度表示にフォールバックする想定。
 */
object GeocodeHelper {

    private const val TIMEOUT_MS = 5000L

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
        return withTimeoutOrNull(TIMEOUT_MS) {
            val geocoder = Geocoder(context, java.util.Locale.JAPAN)
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getAddressAsync(geocoder, latitude, longitude)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
            address?.let { formatAddress(it) }
        }
    }

    private suspend fun getAddressAsync(geocoder: Geocoder, lat: Double, lng: Double): Address? =
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(lat, lng, 1) { list ->
                if (cont.isActive) cont.resume(list.firstOrNull())
            }
        }

    private fun formatAddress(address: Address): String {
        // 都道府県〜町名くらいまでの粒度に絞る。番地までは出さない(冗長になるため)
        val parts = listOfNotNull(
            address.adminArea,      // 都道府県
            address.locality ?: address.subAdminArea, // 市区町村
            address.subLocality     // 町名
        )
        return if (parts.isNotEmpty()) parts.joinToString("") else address.getAddressLine(0) ?: ""
    }
}
