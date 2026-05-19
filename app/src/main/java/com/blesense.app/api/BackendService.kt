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
    // Replace with your actual backend IP address if running on a real device on the same network
    // e.g., "http://192.168.1.10:5000/"
    // Note: If using Android Emulator, 10.0.2.2 points to localhost of your dev machine.
    private const val BASE_URL = "https://ble-sense.onrender.com/"

    val instance: BackendService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(BackendService::class.java)
    }
}
