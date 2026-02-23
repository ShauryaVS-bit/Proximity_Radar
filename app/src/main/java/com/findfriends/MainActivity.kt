package com.findfriends

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findfriends.core.GroupNode
import com.findfriends.core.RadarNode
import com.findfriends.core.SingleNode
import com.findfriends.viewmodel.RadarViewModel
import kotlin.math.cos
import kotlin.math.sin


class MainActivity : ComponentActivity() {
    private val viewModel: RadarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start mocked session for MVP (In prod, use Firebase Auth UID here)
        viewModel.startSession("USER_${(1000..9999).random()}")

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    AppScreen(viewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Battery Efficiency: Pause active fast-scanning in background
        viewModel.stopSession()
    }

    override fun onResume() {
        super.onResume()
        // Note: Needs permission checks here in production!
        viewModel.startSession("MY_USER_ID")
    }
}

@Composable
fun AppScreen(viewModel: RadarViewModel) {
    val nodes by viewModel.radarNodes.collectAsState()
    val selectedNode by viewModel.selectedNode.collectAsState()
    val compassHeading by viewModel.compassHeading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedNode == null) {
            RadarScreen(nodes) { viewModel.selectNode(it) }
        } else {
            GuidanceScreen(
                node = selectedNode!!,
                compassHeading = compassHeading,
                onClose = { viewModel.selectNode(null) }
            )
        }
    }
}

@Composable
fun RadarScreen(nodes: List<RadarNode>, onNodeSelected: (RadarNode) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Background Radar Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f
            for (i in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = maxRadius * (i / 4f),
                    style = Stroke(width = 2f)
                )
            }
            // User Center Dot
            drawCircle(color = Color.Blue, radius = 20f)
        }

        // Friend Nodes
        nodes.forEach { node ->
            RadarIcon(node, onNodeSelected)
        }
    }
}

@Composable
fun RadarIcon(node: RadarNode, onClick: (RadarNode) -> Unit) {
    // Map max distance to screen edges (e.g., 20 meters = edge of screen)
    val maxMeters = 20f
    val scaleFactor = 15f
    val screenDist = (node.displayDistance * scaleFactor).coerceAtMost(400f)

    // Smooth animation for movements
    val animatedDist by animateFloatAsState(targetValue = screenDist, animationSpec = tween(1000))

    val xOffset = animatedDist * cos(Math.toRadians(node.displayAngle.toDouble())).toFloat()
    val yOffset = animatedDist * sin(Math.toRadians(node.displayAngle.toDouble())).toFloat()

    Box(
        modifier = Modifier
            .offset(x = xOffset.dp, y = yOffset.dp)
            .size(50.dp)
            .background(Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable { onClick(node) },
        contentAlignment = Alignment.Center
    ) {
        if (node is GroupNode) {
            Icon(Icons.Default.Groups, contentDescription = "Group", tint = Color.Cyan)
            Text("${node.members.size}", fontSize = 10.sp, modifier = Modifier.offset(y = (-15).dp))
        } else {
            Icon(Icons.Default.Person, contentDescription = "Friend", tint = Color.Green)
        }
    }
}

@Composable
fun GuidanceScreen(node: RadarNode, compassHeading: Float, onClose: () -> Unit) {
    // Direction calculation (Target angle minus compass heading)
    val targetAngle = node.displayAngle
    val arrowRotation by animateFloatAsState(targetValue = targetAngle - compassHeading)

    val distanceText = when {
        node.displayDistance < 2f -> "Very Close"
        node.displayDistance < 8f -> "Near"
        node.displayDistance < 15f -> "Medium"
        else -> "Far"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Icon(
            imageVector = Icons.Default.ArrowUpward,
            contentDescription = "Direction",
            tint = Color.White,
            modifier = Modifier
                .size(200.dp)
                .rotate(arrowRotation)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (node is GroupNode) node.id else (node as SingleNode).friend.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = distanceText,
                fontSize = 20.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "~${String.format("%.1f", node.displayDistance)} meters",
                fontSize = 14.sp,
                color = Color.DarkGray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}