package com.findfriends.data.repository

import com.findfriends.core.models.Measurement
import com.findfriends.core.models.PairDistance
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction layer between the ViewModel and the actual data source.
 *
 * WHY THIS INTERFACE EXISTS:
 * Right now the implementation is Firestore. In 6–12 months when you
 * outgrow the free tier, you swap in a SupabasePairRepository or an
 * ApiPairRepository backed by your own server — and nothing else in the
 * codebase changes. The ViewModel, SyncManager, and all tests work
 * against this interface, never against Firestore directly.
 *
 * This pattern is called the Repository Pattern and it's one of the most
 * important architectural decisions you can make early in a project.
 */
interface PairRepository {

    /**
     * Upload a single distance measurement to the server.
     * This is a suspending function — call it from a coroutine.
     * Implementations should handle retries internally.
     *
     * @param measurement  The Measurement to report.
     */
    suspend fun reportMeasurement(measurement: Measurement)

    /**
     * Returns a Flow that emits the latest list of PairDistance records
     * that involve [myDeviceId] as either device A or device B.
     *
     * The Flow stays active and emits a new list every time Firestore
     * (or whatever backend) pushes an update. Collect it in the ViewModel
     * with stateIn() to expose it as a StateFlow for the UI.
     *
     * @param myDeviceId  This device's ID.
     */
    fun observeMyPairs(myDeviceId: String): Flow<List<PairDistance>>

    /**
     * Write a heartbeat record so the server knows this device is active.
     * Call once on session start. The server can use this to build a list
     * of currently online devices.
     *
     * @param deviceId  This device's ID.
     */
    suspend fun registerDevice(deviceId: String)
}