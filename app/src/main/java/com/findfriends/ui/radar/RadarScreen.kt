package com.findfriends.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findfriends.core.models.RadarNode
import com.findfriends.ui.theme.*

/**
 * The main radar view.
 *
 * Renders:
 *   • 4 concentric range rings (each = 5 metres in real scale)
 *   • A blue dot at the centre representing the user's own device
 *   • One [RadarIcon] per RadarNode (SingleNode or GroupNode)
 *   • An empty state message when no peers are detected
 *
 * All positioning is relative to the centre of the Canvas, matching the
 * coordinate space used by RadarNode.displayAngle and displayDistance.
 *
 * @param nodes          The list of radar nodes to render (from ViewModel).
 * @param onNodeSelected Called when the user taps a node — triggers guidance view.
 */
@Composable
fun RadarScreen(
    nodes: List<RadarNode>,
    onNodeSelected: (RadarNode) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // --- Background rings + centre dot ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centre    = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Draw 4 range rings — each ring = 25% of max screen radius
            for (i in 1..4) {
                drawCircle(
                    color  = RingWhite,
                    radius = maxRadius * (i / 4f),
                    center = centre,
                    style  = Stroke(width = 1.5f)
                )
            }

            // Crosshair lines (subtle)
            drawLine(
                color       = RingWhite,
                start       = Offset(centre.x, centre.y - maxRadius),
                end         = Offset(centre.x, centre.y + maxRadius),
                strokeWidth = 1f
            )
            drawLine(
                color       = RingWhite,
                start       = Offset(centre.x - maxRadius, centre.y),
                end         = Offset(centre.x + maxRadius, centre.y),
                strokeWidth = 1f
            )

            // User centre dot
            drawCircle(color = RadarBlue, radius = 18f, center = centre)
        }

        // --- Range labels ---
        RangeLabels()

        // --- Friend / group nodes ---
        nodes.forEach { node ->
            RadarIcon(node = node, onClick = onNodeSelected)
        }

        // --- Empty state ---
        if (nodes.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 180.dp)
            ) {
                Text(
                    text = "Scanning...",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = "No nearby devices found",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Small distance labels along the top of the vertical crosshair.
 * 5 m / 10 m / 15 m / 20 m (matching the 4 rings assuming maxMeters = 20).
 */
@Composable
private fun BoxScope.RangeLabels() {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf("20m", "15m", "10m", "5m").forEach { label ->
            Text(
                text  = label,
                color = TextSecondary.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }
    }
}