package com.group.smartfood.data.api

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.*

interface ApiService {
    @POST("api/orders")
    fun create(@Body order: RequestBody, @Header("Idempotency-Key") key: String): Call<ResponseBody>

    @GET("api/orders/{id}")
    fun details(@Path("id") id: String): Call<ResponseBody>

    @POST("api/orders/{id}/cancel")
    fun cancel(@Path("id") id: String, @Body reason: RequestBody): Call<ResponseBody>

    @GET("api/orders/{id}/chat")
    fun getChat(@Path("id") id: String): Call<ResponseBody>

    @POST("api/orders/{id}/chat")
    fun sendChat(
        @Path("id") id: String,
        @Body message: RequestBody
    ): Call<ResponseBody>
}

class AuthInterceptor(private val jwt: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
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

fun JSONObject.requestBody() = RequestBody.create(MediaType.parse("application/json"), toString())