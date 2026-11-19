package com.group.smartfood.utils

import com.group.smartfood.BuildConfig

/**
 * Cấu hình dùng chung cho cả phân hệ Khách hàng (TV4) và Chủ quán/Shipper (TV5).
 * Không hardcode IP tĩnh ở nhiều file khác nhau.
 */
object AppConfig {
    const val BASE_URL: String = BuildConfig.BASE_URL

    // Tọa độ mặc định Quận 1, TP.HCM (khớp 100% với 06_seed_data.sql của TV1)
    const val DEFAULT_LAT: Double = 10.7756
    const val DEFAULT_LNG: Double = 106.6924

    // SharedPreferences lưu JWT Token & Role sau khi đăng nhập
    const val PREFS_NAME: String = "smartfood_session_prefs"
    const val KEY_JWT_TOKEN: String = "jwt_token"
    const val KEY_USER_ID: String = "user_id"
    const val KEY_USER_ROLE: String = "user_role"
    const val KEY_RESTAURANT_ID: String = "restaurant_id"
}
