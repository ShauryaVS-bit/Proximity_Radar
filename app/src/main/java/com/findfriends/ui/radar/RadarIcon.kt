package com.findfriends.ui.radar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findfriends.core.models.GroupNode
import com.findfriends.core.models.RadarNode
import com.findfriends.core.models.SingleNode
import com.findfriends.ui.theme.RadarCyan
import com.findfriends.ui.theme.RadarGreen
import kotlin.math.cos
import kotlin.math.sin

/**
 * A single blip on the radar — either a friend (green person icon) or a
 * group of friends (cyan group icon with member count).
 *
 * Position calculation:
 *   Polar coordinates (displayDistance, displayAngle) are converted to
 *   Cartesian (xDp, yDp) and applied as offsets from the centre of the
 *   parent Box (which is the radar container).
 *
 * Scale:
 *   PIXELS_PER_METER maps metres → dp offset. At 15 dp/m, a friend
 *   20 m away sits right at the edge of a typical phone screen.
 *   The .coerceAtMost(MAX_OFFSET_DP) clamps anything beyond that so
 *   distant nodes stay visible at the screen edge rather than going off screen.
 *
 * Animation:
 *   Both the distance and angle are animated with a 1s tween so nodes
 *   glide smoothly when distances update, rather than teleporting.
 */
@Composable
fun RadarIcon(
    node: RadarNode,
    onClick: (RadarNode) -> Unit
) {
    val PIXELS_PER_METER = 15f
    val MAX_OFFSET_DP    = 380f

    val rawScreenDist = (node.displayDistance * PIXELS_PER_METER).coerceAtMost(MAX_OFFSET_DP)

    val animatedDist by animateFloatAsState(
        targetValue   = rawScreenDist,
        animationSpec = tween(durationMillis = 1000),
        label         = "radarDist"
    )

    val animatedAngle by animateFloatAsState(
        targetValue   = node.displayAngle,
        animationSpec = tween(durationMillis = 1000),
        label         = "radarAngle"
    )

    val angleRad = Math.toRadians(animatedAngle.toDouble())
    val xOffset  = (animatedDist * cos(angleRad)).toFloat()
    val yOffset  = (animatedDist * sin(angleRad)).toFloat()

    Box(
        modifier = Modifier
            .offset(x = xOffset.dp, y = yOffset.dp)
            .size(52.dp)
            .background(Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable { onClick(node) },
        contentAlignment = Alignment.Center
    ) {
        when (node) {
            is GroupNode -> {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Group of ${node.members.size}",
                    tint = RadarCyan,
                    modifier = Modifier.size(26.dp)
                )
                // Member count badge — top-right corner of the icon box
                Text(
                    text     = "${node.members.size}",
                    fontSize = 9.sp,
                    color    = RadarCyan,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
            is SingleNode -> {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = node.friend.name,
                    tint = RadarGreen,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}