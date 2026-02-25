package com.findfriends.core.models

/**
 * Represents a single distance reading between THIS device and ONE peer.
 *
 * This is the core unit of data produced by BleManager and consumed by
 * both the radar UI (local) and SyncManager (server upload).
 *
 * @param pairKey       Canonical sorted key e.g. "AABBCCDD::EEFF0011"
 *                      Always the same regardless of who is reporting.
 * @param reporterId    The device ID of the phone that made this measurement.
 * @param peerId        The device ID of the phone that was detected.
 * @param distanceMeters Estimated distance in metres from RSSI via FSPL formula.
 * @param rawRssi       The raw RSSI value straight from the BLE scan result.
 * @param smoothedRssi  RSSI after Kalman filtering — what distanceMeters is based on.
 * @param confidence    0.0–1.0 score. Drops when rawRssi deviates far from smoothedRssi,
 *                      meaning the signal is noisy (obstacles, multipath reflection).
 * @param timestamp     Unix millis when this reading was taken.
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
     * Converts this measurement into a Friend object so the existing
     * ClusteringLogic and RadarNode system can consume it without changes.
     *
     * Note: angleDegrees is derived from the peer ID hash — a stable
     * placeholder until UWB Angle-of-Arrival hardware is available.
     */
    fun toFriend(): Friend = Friend(
        id = peerId,
        name = "User $peerId",
        distanceMeters = distanceMeters,
        angleDegrees = (peerId.hashCode() % 360).toFloat()
    )

    /**
     * Serialises to a plain Map for Firestore.
     * We avoid @DocumentId or FirestoreData annotations to keep the
     * model layer free of Firebase imports — only the repository touches Firebase.
     */
    fun toFirestoreReporterMap(): Map<String, Any> = mapOf(
        "distance"    to distanceMeters,
        "rawRssi"     to rawRssi,
        "confidence"  to confidence,
        "ts"          to timestamp
    )
}

/**
 * Lightweight friend representation used by the radar / clustering layer.
 * Kept separate from Measurement so the UI layer has no dependency on
 * BLE or Firebase concepts.
 */
data class Friend(
    val id: String,
    val name: String,
    val distanceMeters: Float,
    val angleDegrees: Float
) {
    val x: Float get() = distanceMeters * Math.cos(Math.toRadians(angleDegrees.toDouble())).toFloat()
    val y: Float get() = distanceMeters * Math.sin(Math.toRadians(angleDegrees.toDouble())).toFloat()
}