package com.group.smartfood.utils

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices

/**
 * Tiện ích lấy tọa độ GPS kết hợp cơ chế Fallback an toàn về Quận 1, TP.HCM
 * nhằm tránh lỗi máy ảo Android Emulator trả về tọa độ Mỹ (37.42, -122.08).
 */
object LocationHelper {
    const val DEFAULT_LAT = AppConfig.DEFAULT_LAT
    const val DEFAULT_LNG = AppConfig.DEFAULT_LNG

    fun resolveSafeVietnamLocation(lat: Double?, lng: Double?): Pair<Double, Double> {
        if (lat == null || lng == null || lat < 8.0 || lat > 24.0 || lng < 102.0 || lng > 110.0) {
            return Pair(DEFAULT_LAT, DEFAULT_LNG)
        }
        return Pair(lat, lng)
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentOrFallbackLocation(
        context: Context,
        onResult: (lat: Double, lng: Double, isFallback: Boolean) -> Unit
    ) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val safe = resolveSafeVietnamLocation(location.latitude, location.longitude)
                    val isFallback = (safe.first != location.latitude || safe.second != location.longitude)
                    onResult(safe.first, safe.second, isFallback)
                } else {
                    onResult(DEFAULT_LAT, DEFAULT_LNG, true)
                }
            }
            .addOnFailureListener {
                onResult(DEFAULT_LAT, DEFAULT_LNG, true)
            }
    }
}
