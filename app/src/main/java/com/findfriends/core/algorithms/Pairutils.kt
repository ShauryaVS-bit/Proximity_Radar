package com.findfriends.core.algorithms

/**
 * Utilities for working with device pairs.
 *
 * The central problem: if Device A detects Device B, A will report the pair as
 * "A::B". If Device B independently detects Device A, B will report the same
 * pair as "B::A". Without a canonical key these appear as two separate pairs
 * on the server, which is wrong.
 *
 * Solution: always sort the two IDs alphabetically. Both "A::B" and "B::A"
 * produce the same canonical key, so they map to the same Firestore document.
 */
object PairUtils {

    /**
     * Returns a canonical, stable key for a pair of device IDs.
     * Order of arguments does not matter — you always get the same result.
     *
     * Examples:
     *   canonicalKey("AABB", "CCDD") → "AABB::CCDD"
     *   canonicalKey("CCDD", "AABB") → "AABB::CCDD"  ← same!
     */
    fun canonicalKey(idA: String, idB: String): String =
        if (idA < idB) "$idA::$idB" else "$idB::$idA"

    /**
     * Splits a canonical pairKey back into its two device IDs.
     * Returns null if the key is malformed (doesn't contain "::").
     */
    fun splitKey(pairKey: String): Pair<String, String>? {
        val parts = pairKey.split("::")
        if (parts.size != 2) return null
        return Pair(parts[0], parts[1])
    }

    /**
     * Returns true if the given deviceId is one of the two participants
     * in the pair identified by pairKey.
     */
    fun involves(pairKey: String, deviceId: String): Boolean =
        pairKey.startsWith(deviceId) || pairKey.endsWith(deviceId)
}