package com.group.smartfood.utils

import android.net.Uri

/**
 * Tiện ích tạo URL ảnh mã QR thanh toán VietQR QuickLink API (Napas)
 * dùng chung cho SCR-14 (Chia tiền nhóm) và SCR-18 (Thanh toán đơn hàng).
 */
object VietQrHelper {
    private const val BANK_ID = "MB"
    private const val ACCOUNT_NO = "0901234567"
    private const val ACCOUNT_NAME = "SMART FOOD DELIVERY"

    fun buildVietQrUrl(amountVnd: Long, transferNote: String): String {
        val encodedInfo = Uri.encode(transferNote)
        val encodedName = Uri.encode(ACCOUNT_NAME)
        return "https://img.vietqr.io/image/$BANK_ID-$ACCOUNT_NO-compact2.png" +
            "?amount=$amountVnd&addInfo=$encodedInfo&accountName=$encodedName"
    }
}
