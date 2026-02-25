package com.findfriends.core.models

import android.content.Context
import java.util.UUID

/**
 * Manages the persistent unique identity for this device.
 * The ID is generated once and stored in SharedPreferences so it
 * survives app restarts and is stable across BLE scan cycles.
 *
 * In production: swap getOrCreate() to return FirebaseAuth.uid
 * after the user signs in anonymously — that gives you a server-
 * validated identity instead of a locally generated one.
 */
object DeviceIdentity {

    private const val PREFS_NAME = "findfriends_prefs"
    private const val KEY_DEVICE_ID = "device_uuid"

    /**
     * Returns the existing device ID or creates and persists a new one.
     * The ID is trimmed to 8 characters so it fits inside a single BLE
     * advertisement payload without fragmentation.
     */
    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
            .replace("-", "")
            .take(8)
            .uppercase()

        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }
}