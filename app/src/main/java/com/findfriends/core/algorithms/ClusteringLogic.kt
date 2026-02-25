package com.findfriends.core.algorithms

import com.findfriends.core.models.Friend
import com.findfriends.core.models.GroupNode
import com.findfriends.core.models.RadarNode
import com.findfriends.core.models.SingleNode
import kotlin.math.sqrt

/**
 * Groups a flat list of Friend objects into RadarNodes for the UI.
 *
 * Algorithm: greedy centroid clustering.
 *   1. For each friend, check whether they fall within CLUSTER_THRESHOLD_METERS
 *      of any existing cluster's centroid.
 *   2. If yes → add them to that cluster (centroid shifts slightly).
 *   3. If no  → start a new cluster containing just this friend.
 *   4. After all friends are assigned, clusters of size 1 become SingleNodes
 *      and clusters of size 2+ become GroupNodes.
 *
 * This is O(n²) but n is at most ~10 devices per phone so it's irrelevant.
 * A proper k-means or DBSCAN would be overkill here.
 */
object ClusteringLogic {

    /** Two friends within this distance (metres) are drawn as one group dot. */
    private const val CLUSTER_THRESHOLD_METERS = 3.0f

    fun groupFriends(friends: List<Friend>): List<RadarNode> {
        if (friends.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<Friend>>()

        for (friend in friends) {
            var addedToCluster = false

            for (cluster in clusters) {
                val cx = cluster.map { it.x }.average().toFloat()
                val cy = cluster.map { it.y }.average().toFloat()

                val dist = sqrt(
                    (friend.x - cx) * (friend.x - cx) +
                            (friend.y - cy) * (friend.y - cy)
                )

                if (dist <= CLUSTER_THRESHOLD_METERS) {
                    cluster.add(friend)
                    addedToCluster = true
                    break
                }
            }

            if (!addedToCluster) {
                clusters.add(mutableListOf(friend))
            }
        }

        return clusters.mapIndexed { index, cluster ->
            if (cluster.size == 1) {
                SingleNode(cluster.first())
            } else {
                GroupNode(id = "Group ${index + 1}", members = cluster)
            }
        }
    }
}