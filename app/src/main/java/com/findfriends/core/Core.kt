package com.findfriends.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// --- Models ---
data class Friend(
    val id: String,
    val name: String,
    val distanceMeters: Float,
    val angleDegrees: Float // Simulated for MVP, UWB required for real Angle of Arrival
) {
    // Convert polar (distance, angle) to cartesian (x, y) for radar math
    val x: Float get() = distanceMeters * cos(Math.toRadians(angleDegrees.toDouble())).toFloat()
    val y: Float get() = distanceMeters * sin(Math.toRadians(angleDegrees.toDouble())).toFloat()
}

sealed class RadarNode {
    abstract val x: Float
    abstract val y: Float
    abstract val displayDistance: Float
    abstract val displayAngle: Float
}

data class SingleNode(val friend: Friend) : RadarNode() {
    override val x = friend.x
    override val y = friend.y
    override val displayDistance = friend.distanceMeters
    override val displayAngle = friend.angleDegrees
}

data class GroupNode(val id: String, val members: List<Friend>) : RadarNode() {
    override val x = members.map { it.x }.average().toFloat()
    override val y = members.map { it.y }.average().toFloat()

    // Reverse Cartesian to Polar for group center
    override val displayDistance = sqrt(x * x + y * y)
    override val displayAngle = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())).toFloat()
}

// --- Smart Grouping Algorithm ---
object ClusteringLogic {
    private const val CLUSTER_THRESHOLD_METERS = 3.0f

    fun groupFriends(friends: List<Friend>): List<RadarNode> {
        val clusters = mutableListOf<MutableList<Friend>>()

        for (friend in friends) {
            var addedToCluster = false
            for (cluster in clusters) {
                val cx = cluster.map { it.x }.average().toFloat()
                val cy = cluster.map { it.y }.average().toFloat()

                // Euclidean distance check
                val distToCenter = sqrt((friend.x - cx) * (friend.x - cx) + (friend.y - cy) * (friend.y - cy))
                if (distToCenter <= CLUSTER_THRESHOLD_METERS) {
                    cluster.add(friend)
                    addedToCluster = true
                    break
                }
            }
            if (!addedToCluster) clusters.add(mutableListOf(friend))
        }

        return clusters.mapIndexed { index, cluster ->
            if (cluster.size == 1) SingleNode(cluster.first())
            else GroupNode("Group ${index + 1}", cluster)
        }
    }
}

// --- Utilities ---
object BleMath {
    // Standard Free Space Path Loss (FSPL) formula to estimate distance from RSSI
    fun calculateDistance(rssi: Int, txPower: Int = -59): Float {
        if (rssi == 0) return -1f
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0).toFloat()
        } else {
            (0.89976 * Math.pow(ratio, 7.7095) + 0.111).toFloat()
        }
    }
}