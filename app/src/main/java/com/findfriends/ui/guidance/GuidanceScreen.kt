package com.findfriends.ui.guidance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findfriends.core.models.GroupNode
import com.findfriends.core.models.RadarNode
import com.findfriends.core.models.SingleNode
import com.findfriends.ui.theme.TextPrimary
import com.findfriends.ui.theme.TextSecondary

/**
 * Shown when the user taps a radar node. Replaces the radar view with a large
 * direction arrow and distance readout.
 *
 * Direction arrow logic:
 *   arrowRotation = targetAngle - compassHeading
 *   This means the arrow always points at the correct real-world bearing to
 *   the friend, accounting for which direction the phone is currently facing.
 *   If the friend is due North and the phone faces East, the arrow points left.
 *
 * Note: displayAngle is currently hash-derived (not real AoA) so the arrow
 * gives a consistent placeholder direction rather than a real compass bearing.
 * Replace with UWB AoA data when hardware is available.
 *
 * @param node            The selected radar node (single friend or group).
 * @param compassHeading  Current device heading 0–360° from OrientationManager.
 * @param onClose         Called when the user dismisses the guidance view.
 */
@Composable
fun GuidanceScreen(
    node: RadarNode,
    compassHeading: Float,
    onClose: () -> Unit
) {
    val targetAngle = node.displayAngle
    val rawRotation = targetAngle - compassHeading

    val arrowRotation by animateFloatAsState(
        targetValue   = rawRotation,
        animationSpec = tween(durationMillis = 300),
        label         = "arrowRotation"
    )

    val displayName = when (node) {
        is GroupNode  -> node.id                 // e.g. "Group 1"
        is SingleNode -> node.friend.name        // e.g. "User ABC123"
    }

    val distanceLabel = when {
        node.displayDistance < 2f  -> "Very Close"
        node.displayDistance < 8f  -> "Near"
        node.displayDistance < 15f -> "Medium"
        else                       -> "Far"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- Top bar: close button ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Back to radar",
                    tint = TextPrimary
                )
            }
        }

        // --- Direction arrow ---
        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = "Direction to $displayName",
            tint = TextPrimary,
            modifier = Modifier
                .size(200.dp)
                .rotate(arrowRotation)
        )

        // --- Friend info ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = displayName,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text     = distanceLabel,
                fontSize = 20.sp,
                color    = TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text     = "~${"%.1f".format(node.displayDistance)} m",
                fontSize = 14.sp,
                color    = TextSecondary.copy(alpha = 0.6f)
            )

            // Show member count for groups
            if (node is GroupNode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text     = "${node.members.size} people here",
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}