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
    fun value(key: String, fallback: String = "") = prefs.getString(key, fallback) ?: fallback
    fun save(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun orders() = JSONArray(value("orders", "[]")).objects()
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
        val discount = when (voucher) {
            "SMART20" -> minOf(subtotal / 5, 50000L)
            "FREESHIP" -> shipping
            else -> 0L
        }
        return JSONObject(cart.toString()).put("subtotal",subtotal).put("delivery_fee",shipping)
            .put("discount",discount).put("total", subtotal + shipping - discount)
            .put("voucher_code",voucher).put("destination",JSONObject(address().toString()))
    }
}

