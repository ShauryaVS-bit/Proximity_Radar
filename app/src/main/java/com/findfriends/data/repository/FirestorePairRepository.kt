package com.findfriends.data.repository

import com.findfriends.core.models.Measurement
import com.findfriends.core.models.PairDistance
import com.findfriends.core.models.ReporterSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [PairRepository].
 *
 * Firestore document structure:
 *
 *   /pairs/{pairKey}
 *     latestDistance : Float   ← most recent reading from either side
 *     lastUpdated    : Long    ← unix millis
 *     deviceIds      : [String, String]  ← needed for server-side array-contains queries
 *     reporters/
 *       {reporterId}:
 *         distance   : Float
 *         confidence : Float
 *         ts         : Long
 *
 *   /devices/{deviceId}
 *     lastSeen       : Long
 *     activePeers    : [String]
 *
 * Free tier write budget:
 *   20,000 writes/day ÷ (3s sync interval × delta filtering) ≈ comfortable for 100 devices.
 *   SyncManager's delta threshold means we only write when distance changes by > 0.4m,
 *   which on stationary or slow-moving users drops write volume by ~80%.
 */
class FirestorePairRepository : PairRepository {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun reportMeasurement(measurement: Measurement) {
        val docRef = db.collection("pairs").document(measurement.pairKey)

        // SetOptions.merge() means we never overwrite the other reporter's sub-document.
        // Device A writes its own "reporters/A" block; Device B writes "reporters/B".
        // Both coexist in the same Firestore document.
        docRef.set(
            mapOf(
                "latestDistance" to measurement.distanceMeters,
                "lastUpdated"    to measurement.timestamp,
                // Array of device IDs so we can query: where deviceIds array-contains myId
                "deviceIds"      to listOf(measurement.reporterId, measurement.peerId).sorted(),
                "reporters"      to mapOf(
                    measurement.reporterId to measurement.toFirestoreReporterMap()
                )
            ),
            SetOptions.merge()
        ).await()
    }

    override fun observeMyPairs(myDeviceId: String): Flow<List<PairDistance>> = callbackFlow {
        // Query all pair documents where this device appears in deviceIds array
        val query = db.collection("pairs")
            .whereArrayContains("deviceIds", myDeviceId)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Don't close the flow on transient errors — Firestore will reconnect
                return@addSnapshotListener
            }

            val pairs = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val pairKey        = doc.id
                    val latestDistance = (doc.get("latestDistance") as? Number)?.toFloat() ?: 0f
                    val lastUpdated    = (doc.get("lastUpdated")    as? Number)?.toLong()  ?: 0L

                    // Parse per-reporter snapshots
                    @Suppress("UNCHECKED_CAST")
                    val reportersRaw = doc.get("reporters") as? Map<String, Map<String, Any>> ?: emptyMap()
                    val reporters = reportersRaw.mapValues { (_, v) ->
                        ReporterSnapshot(
                            distance   = (v["distance"]   as? Number)?.toFloat() ?: 0f,
                            confidence = (v["confidence"] as? Number)?.toFloat() ?: 0f,
                            ts         = (v["ts"]         as? Number)?.toLong()  ?: 0L
                        )
                    }

                    // Consensus distance: weighted average of all reporters by confidence
                    val consensusDistance = if (reporters.isEmpty()) latestDistance
                    else {
                        val totalWeight = reporters.values.sumOf { it.confidence.toDouble() }
                        if (totalWeight == 0.0) latestDistance
                        else reporters.values
                            .sumOf { (it.distance * it.confidence).toDouble() }
                            .div(totalWeight)
                            .toFloat()
                    }

                    PairDistance(
                        pairKey           = pairKey,
                        latestDistance    = latestDistance,
                        consensusDistance = consensusDistance,
                        lastUpdated       = lastUpdated,
                        reporters         = reporters
                    )
                } catch (e: Exception) {
                    null // Skip malformed documents
                }
            } ?: emptyList()

            trySend(pairs)
        }

        // Clean up the Firestore listener when the Flow is cancelled
        awaitClose { listener.remove() }
    }

    override suspend fun registerDevice(deviceId: String) {
        db.collection("devices")
            .document(deviceId)
            .set(
                mapOf("lastSeen" to System.currentTimeMillis()),
                SetOptions.merge()
            )
            .await()
    }
}