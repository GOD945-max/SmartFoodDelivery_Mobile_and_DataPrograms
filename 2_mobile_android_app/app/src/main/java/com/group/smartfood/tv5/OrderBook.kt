package com.group.smartfood.tv5

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.text.NumberFormat
import java.util.Locale

internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
internal fun dong(value: Long) = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ"

internal class OrderBook(val prefs: SharedPreferences, private val live: Boolean = false) {
    companion object {
        val demoCodes = listOf("FOODFLOW20","FREESHIP15K","HIGHLANDS30","FLASH50_LAST1","VIPDIAMOND40")
    }
    fun value(key: String, fallback: String = "") = prefs.getString(key, fallback) ?: fallback
    fun save(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun orders() = JSONArray(value("orders", "[]")).objects().onEach { order ->
        when (order.optString("status")) {
            "CONFIRMED" -> order.put("status","ACCEPTED")
            "PREPARING" -> order.put("status","COOKING")
        }
    }
    fun order(id: String) = orders().find { it.optString("id") == id }
    fun put(order: JSONObject) {
        val all = orders().toMutableList()
        val index = all.indexOfFirst { it.getString("id") == order.getString("id") }
        if (index < 0) all.add(0, order) else all[index] = order
        save("orders", JSONArray(all).toString())
    }
    fun draftId(): String = value("draft").ifBlank {
        UUID.randomUUID().toString().also { save("draft", it) }
    }
    fun addresses() = JSONArray(value("addresses", if (live) "[]" else """[{"name":"Home","address":"12 Nguyễn Huệ, Quận 1, TP.HCM","lat":10.7756,"lng":106.6924,"source":"Điểm mẫu Quận 1"}]"""))
    fun address(): JSONObject {
        val entries = addresses()
        if (entries.length() == 0) return JSONObject()
        return entries.getJSONObject(prefs.getInt("addressIndex", 0).coerceIn(0, entries.length()-1))
    }
    fun addAddress(entry: JSONObject) {
        val list = addresses().put(entry)
        prefs.edit().putString("addresses", list.toString()).putInt("addressIndex", list.length()-1).apply()
    }

    fun sampleCart() = JSONObject("""{"restaurant_id":1,"restaurant_name":"Healthy Kitchen","restaurant_lat":10.7738,"restaurant_lng":106.7041,"delivery_fee":15000,"items":[{"item_id":1,"name":"Grilled Chicken Bowl","quantity":1,"unit_price":89000},{"item_id":2,"name":"Avocado Smoothie","quantity":1,"unit_price":49000}]}""")
    fun quote(cart: JSONObject, voucher: String): JSONObject {
        val items = cart.getJSONArray("items").objects()
        require(items.isNotEmpty() && items.all { it.getInt("quantity") > 0 && it.getLong("unit_price") >= 0 })
        val subtotal = items.sumOf { Math.multiplyExact(it.getLong("unit_price"), it.getLong("quantity")) }
        val shipping = cart.optLong("delivery_fee", 15000)
        val discount = if (live) {
            if (voucher.isNotBlank() && value("voucherSubtotal").toLongOrNull()==subtotal)
                value("voucherSaving").toLongOrNull() ?: 0L else 0L
        } else when (voucher) {
            "FOODFLOW20" -> if(subtotal>=100000) minOf(subtotal/5,50000) else 0L
            "HIGHLANDS30" -> if(subtotal>=80000 && cart.optInt("restaurant_id")==1 && cart.optInt("points")>=100) minOf(subtotal*30/100,40000) else 0L
            "VIPDIAMOND40" -> if(subtotal>=200000 && cart.optInt("points")>=500) minOf(subtotal*40/100,100000) else 0L
            "FLASH50_LAST1" -> if(subtotal>=80000) minOf(subtotal/2,60000) else 0L
            "FREESHIP15K" -> if(subtotal>=50000) minOf(shipping,15000) else 0L
            else -> 0L
        }
        return JSONObject(cart.toString()).put("subtotal",subtotal).put("delivery_fee",shipping)
            .put("discount",discount).put("total", subtotal + shipping - discount)
            .put("voucher_code",voucher).put("destination",JSONObject(address().toString()))
    }
}


internal fun applyServerOrder(row: JSONObject, local: JSONObject): JSONObject {
    fun amount(key: String) = row.get(key).toString().toBigDecimal().longValueExact()
    val destination = JSONObject().put("address", row.getString("delivery_address"))
        .put("lat", row.opt("delivery_lat")).put("lng", row.opt("delivery_lng"))
    return local.put("id", row.get("id").toString()).put("status", row.getString("order_status"))
        .put("subtotal", amount("subtotal")).put("total", amount("total_amount"))
        .put("delivery_fee", amount("shipping_fee")).put("discount", amount("discount_amount"))
        .put("destination", destination).put("items", row.getJSONArray("items"))
        .put("method", local.optString("method")).put("payment", local.optString("payment", "Chưa có thông tin thanh toán"))
        .put("token", row.optString("qr_verify_token"))
}
