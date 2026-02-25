package com.findfriends.core.models

import kotlin.math.sqrt

/**
 * What the radar UI renders. A node is either a single friend or a group
 * of friends that are close enough together to be clustered.
 *
 * The UI only knows about RadarNodes — it has no knowledge of Measurements,
 * BLE, or Firestore. This keeps the composables clean and testable.
 */
sealed class RadarNode {
    abstract val x: Float
    abstract val y: Float
    abstract val displayDistance: Float
    abstract val displayAngle: Float
}

/**
 * A RadarNode representing exactly one friend.
 */
data class SingleNode(val friend: Friend) : RadarNode() {
    override val x             = friend.x
    override val y             = friend.y
    override val displayDistance = friend.distanceMeters
    override val displayAngle    = friend.angleDegrees
}

/**
 * A RadarNode representing 2 or more friends whose positions are within
 * CLUSTER_THRESHOLD_METERS of each other on the radar plane.
 *
 * The group's position is the centroid of its members.
 */
data class GroupNode(
    val id: String,
    val members: List<Friend>
) : RadarNode() {

    override val x = members.map { it.x }.average().toFloat()
    override val y = members.map { it.y }.average().toFloat()

    // Convert centroid Cartesian → Polar so the UI can render it
    override val displayDistance = sqrt(x * x + y * y)
    override val displayAngle    = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())).toFloat()
}