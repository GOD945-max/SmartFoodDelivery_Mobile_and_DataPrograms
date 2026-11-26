package com.group.smartfood.data.api

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.*

interface ApiService {
    @POST("api/orders")
    fun create(@Body order: RequestBody, @Header("Idempotency-Key") key: String): Call<ResponseBody>

    @POST("api/auth/login")
    fun login(@Body credentials: RequestBody): Call<ResponseBody>

    @POST("api/auth/register")
    fun register(@Body account: RequestBody): Call<ResponseBody>

    @GET("api/restaurants")
    fun restaurants(@Query("lat") lat: Double? = null, @Query("lng") lng: Double? = null,
                    @Query("search") search: String? = null): Call<ResponseBody>

    @GET("api/restaurants/{id}/menu")
    fun menu(@Path("id") id: Int): Call<ResponseBody>

    @PUT("api/menu-items/{id}")
    fun updateMenuItem(@Path("id") id: Int, @Body changes: RequestBody): Call<ResponseBody>

    @PATCH("api/orders/{id}/status")
    fun updateStatus(@Path("id") id: String, @Body changes: RequestBody): Call<ResponseBody>

    @POST("api/orders/{id}/verify-qr")
    fun verifyQr(@Path("id") id: String, @Body token: RequestBody): Call<ResponseBody>

    @GET("api/restaurants/{id}/report")
    fun report(@Path("id") id: Int, @Query("from") from: String, @Query("to") to: String): Call<ResponseBody>

    @GET("api/orders/customer/{customerId}")
    fun customerOrders(@Path("customerId") id: Int, @Query("limit") limit: Int,
                       @Query("offset") offset: Int): Call<ResponseBody>

    @GET("api/orders/restaurant/{restaurantId}")
    fun restaurantOrders(@Path("restaurantId") id: Int, @Query("status") status: String? = null,
                         @Query("limit") limit: Int = 20, @Query("offset") offset: Int = 0): Call<ResponseBody>

    @GET("api/vouchers/suggest")
    fun vouchers(@Query("restaurant_id") restaurant: Int, @Query("subtotal") subtotal: Long,
                 @Query("shipping_fee") shipping: Long): Call<ResponseBody>

    @GET("api/orders/{id}")
    fun details(@Path("id") id: String): Call<ResponseBody>

    @POST("api/orders/{id}/cancel")
    fun cancel(@Path("id") id: String, @Body reason: RequestBody): Call<ResponseBody>


}

class AuthInterceptor(private val jwt: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        if (jwt.isBlank()) return chain.proceed(chain.request())
        val request = chain.request().newBuilder().header("Authorization", "Bearer $jwt").build()
        return chain.proceed(request)
    }
}

object RetrofitClient {
    fun create(baseUrl: String, jwt: String): ApiService = Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(AuthInterceptor(jwt)).build())
        .build().create(ApiService::class.java)
}

fun JSONObject.requestBody() = toString().toRequestBody("application/json".toMediaType())