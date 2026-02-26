package com.findfriends.core.models

/**
 * A single distance reading between THIS device and ONE peer.
 *
 * Core data unit: produced by BleManager, consumed by SyncManager (server upload).
 *
 * @param pairKey        Canonical sorted key e.g. "AABBCCDD::EEFF0011"
 * @param reporterId     Device ID of the phone that made this measurement.
 * @param peerId         Device ID of the peer that was detected.
 * @param distanceMeters Estimated distance in metres (RSSI via FSPL formula).
 * @param rawRssi        Raw RSSI from BLE scan result.
 * @param smoothedRssi   Kalman-filtered RSSI.
 * @param confidence     0.0-1.0 signal stability score.
 * @param timestamp      Unix millis when this reading was taken.
 */
data class Measurement(
    val pairKey: String,
    val reporterId: String,
    val peerId: String,
    val distanceMeters: Float,
    val rawRssi: Int,
    val smoothedRssi: Double,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Serialises to a plain Map for Firestore.
     * Model layer stays free of Firebase imports.
     */
    fun toFirestoreReporterMap(): Map<String, Any> = mapOf(
        "distance"   to distanceMeters,
        "rawRssi"    to rawRssi,
        "confidence" to confidence,
        "ts"         to timestamp
    )

    /** Human-readable summary for dev logs. */
    fun toLogString(): String =
        "pair=$pairKey reporter=$reporterId peer=$peerId dist=${String.format("%.2f", distanceMeters)}m rssi=$rawRssi smooth=${String.format("%.1f", smoothedRssi)} conf=${String.format("%.2f", confidence)}"
}
