package com.blesense.app.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Response

// Generic data packet to be sent to the backend
data class SensorPacket(
    val timestamp: Long,
    val data: Map<String, Any>
)

interface BackendService {
    @POST("api/packets")
    suspend fun sendSensorData(@Body packet: SensorPacket): Response<Void>

    @POST("api/packets")
    suspend fun sendSensorDataBatch(@Body packets: List<SensorPacket>): Response<Void>
}


object RetrofitClient {
    private val BASE_URL = BuildConfig.BASE_URL

    val instance: BackendService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        retrofit.create(BackendService::class.java)
    }
}