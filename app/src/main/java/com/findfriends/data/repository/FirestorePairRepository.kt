package com.findfriends.data.repository

import com.findfriends.core.models.Measurement
import com.findfriends.core.models.PairDistance
import com.findfriends.core.models.ReporterSnapshot
import com.findfriends.logging.DevLog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [PairRepository] with extensive dev logging.
 *
 * Document structure:
 *   /pairs/{pairKey}
 *     latestDistance, lastUpdated, deviceIds[], reporters/{reporterId}
 *   /devices/{deviceId}
 *     lastSeen
 */
class FirestorePairRepository : PairRepository {

    companion object {
        private const val TAG = "FIRESTORE"
    }

    private val db = FirebaseFirestore.getInstance()

    override suspend fun reportMeasurement(measurement: Measurement) {
        val docRef = db.collection("pairs").document(measurement.pairKey)

        val payload = mapOf(
            "latestDistance" to measurement.distanceMeters,
            "lastUpdated"   to measurement.timestamp,
            "deviceIds"     to listOf(measurement.reporterId, measurement.peerId).sorted(),
            "reporters"     to mapOf(
                measurement.reporterId to measurement.toFirestoreReporterMap()
            )
        )

        DevLog.d(TAG, "WRITE /pairs/${measurement.pairKey} | reporter=${measurement.reporterId} | dist=${String.format("%.2f", measurement.distanceMeters)}m | conf=${String.format("%.2f", measurement.confidence)}")

        try {
            docRef.set(payload, SetOptions.merge()).await()
            DevLog.i(TAG, "WRITE OK /pairs/${measurement.pairKey}")
        } catch (e: Exception) {
            DevLog.e(TAG, "WRITE FAILED /pairs/${measurement.pairKey}: ${e.message}")
            throw e
        }
    }

    override fun observeMyPairs(myDeviceId: String): Flow<List<PairDistance>> = callbackFlow {
        DevLog.i(TAG, "observeMyPairs: starting listener for device=$myDeviceId")

        val query = db.collection("pairs")
            .whereArrayContains("deviceIds", myDeviceId)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                DevLog.e(TAG, "Snapshot listener error: ${error.message}")
                return@addSnapshotListener
            }

            val docCount = snapshot?.documents?.size ?: 0
            DevLog.d(TAG, "Snapshot update: $docCount pair document(s)")

            val pairs = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val pairKey        = doc.id
                    val latestDistance = (doc.get("latestDistance") as? Number)?.toFloat() ?: 0f
                    val lastUpdated   = (doc.get("lastUpdated") as? Number)?.toLong() ?: 0L

                    @Suppress("UNCHECKED_CAST")
                    val reportersRaw = doc.get("reporters") as? Map<String, Map<String, Any>> ?: emptyMap()
                    val reporters = reportersRaw.mapValues { (_, v) ->
                        ReporterSnapshot(
                            distance   = (v["distance"]   as? Number)?.toFloat() ?: 0f,
                            confidence = (v["confidence"] as? Number)?.toFloat() ?: 0f,
                            ts         = (v["ts"]         as? Number)?.toLong()  ?: 0L
                        )
                    }

                    val consensusDistance = if (reporters.isEmpty()) latestDistance
                    else {
                        val totalWeight = reporters.values.sumOf { it.confidence.toDouble() }
                        if (totalWeight == 0.0) latestDistance
                        else reporters.values
                            .sumOf { (it.distance * it.confidence).toDouble() }
                            .div(totalWeight)
                            .toFloat()
                    }

                    DevLog.d(TAG, "PAIR $pairKey | latest=${String.format("%.2f", latestDistance)}m | consensus=${String.format("%.2f", consensusDistance)}m | reporters=${reporters.size}")

                    PairDistance(
                        pairKey          = pairKey,
                        latestDistance   = latestDistance,
                        consensusDistance = consensusDistance,
                        lastUpdated      = lastUpdated,
                        reporters        = reporters
                    )
                } catch (e: Exception) {
                    DevLog.e(TAG, "Failed to parse pair doc ${doc.id}: ${e.message}")
                    null
                }
            } ?: emptyList()

            trySend(pairs)
        }

        awaitClose {
            listener.remove()
            DevLog.i(TAG, "observeMyPairs: listener removed for device=$myDeviceId")
        }
    }

    override suspend fun registerDevice(deviceId: String) {
        DevLog.i(TAG, "registerDevice: $deviceId")
        try {
            db.collection("devices")
                .document(deviceId)
                .set(
                    mapOf("lastSeen" to System.currentTimeMillis()),
                    SetOptions.merge()
                )
                .await()
            DevLog.i(TAG, "Device registered: $deviceId")
        } catch (e: Exception) {
            DevLog.e(TAG, "registerDevice FAILED for $deviceId: ${e.message}")
        }
    }
}
