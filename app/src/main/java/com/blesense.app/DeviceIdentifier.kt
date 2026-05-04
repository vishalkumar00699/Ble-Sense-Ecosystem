package com.blesense.app

import android.content.Context
import java.util.UUID

object DeviceIdentifier {
    private const val PREFS_NAME = "device_prefs"
    private const val KEY_DEVICE_ID = "app_unique_id"

    fun getOrGenerateId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        
        return id
    }
}
