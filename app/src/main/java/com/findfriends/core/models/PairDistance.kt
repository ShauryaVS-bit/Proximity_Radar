package com.findfriends.core.models

/**
 * Represents the server-side record for a single device pair.
 *
 * The server merges reports from BOTH sides of the pair into one document.
 * For example, if A measures B as 3.3m and B measures A as 3.5m, the server
 * holds both values and exposes a consensusDistance which is the weighted
 * average of both sides (weighted by confidence).
 *
 * @param pairKey           Canonical "DEVICE_A::DEVICE_B" key (alphabetical order).
 * @param latestDistance    The most recently reported distance from either side.
 * @param consensusDistance Weighted average of both sides' readings. More reliable.
 * @param lastUpdated       Unix millis of the most recent write to this document.
 * @param reporters         Map of deviceId → their individual latest reading.
 *                          Will have 1 entry if only one side has reported yet,
 *                          2 entries once both sides have reported.
 */
data class PairDistance(
    val pairKey: String = "",
    val latestDistance: Float = 0f,
    val consensusDistance: Float = 0f,
    val lastUpdated: Long = 0L,
    val reporters: Map<String, ReporterSnapshot> = emptyMap()
) {
    /**
     * Extracts the two device IDs from the pairKey.
     * Safe to call as long as the key was created by PairUtils.canonicalKey().
     */
    val deviceIds: List<String>
        get() = pairKey.split("::")

    val deviceA: String get() = deviceIds.getOrElse(0) { "" }
    val deviceB: String get() = deviceIds.getOrElse(1) { "" }
}

/**
 * A single device's latest reading within a PairDistance document.
 */
data class ReporterSnapshot(
    val distance: Float = 0f,
    val confidence: Float = 0f,
    val ts: Long = 0L
)