package com.group.smartfood.utils

import io.socket.client.IO
import org.json.JSONObject
import java.net.URI

internal class SocketManager(baseUrl: String, token: String) {
    private val server = URI(baseUrl)
    private val socket = IO.socket(URI(server.scheme, null, server.host, server.port, null, null, null),
        IO.Options.builder().setForceNew(true).setAuth(mapOf("token" to token)).build())

    fun track(orderId: String, update: (Double, Double) -> Unit) {
        socket.on("SHIPPER_LOCATION_CHANGED") { args ->
            val data = args.firstOrNull() as? JSONObject
            if (data?.optString("order_id") == orderId) {
                val lat = data.optDouble("lat")
                val lng = data.optDouble("lng")
                if (lat in -90.0..90.0 && lng in -180.0..180.0) update(lat,lng)
            }
        }
        socket.connect()
    }

    fun close() {
        socket.off()
        socket.disconnect()
    }
}
