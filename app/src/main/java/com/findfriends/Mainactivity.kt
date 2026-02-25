package com.findfriends

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.findfriends.ui.debug.DevHud
import com.findfriends.ui.guidance.GuidanceScreen
import com.findfriends.ui.permissions.RequireBluetoothPermissions
import com.findfriends.ui.radar.RadarScreen
import com.findfriends.ui.theme.AppDarkColorScheme
import com.findfriends.viewmodel.RadarViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RadarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    RequireBluetoothPermissions {
                        AppScreen(viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeSession()
    }

    override fun onPause() {
        super.onPause()
        viewModel.pauseSession()
    }
}

/**
 * Root composable.
 *
 * Layer order (back → front):
 *   1. RadarScreen or GuidanceScreen  — main content
 *   2. DevHud                         — floating overlay, top-left corner
 *
 * The HUD is always rendered on top regardless of which screen is active,
 * so you can inspect BLE stats while on the guidance screen too.
 */
@Composable
fun AppScreen(viewModel: RadarViewModel) {
    val nodes          by viewModel.radarNodes.collectAsState()
    val selectedNode   by viewModel.selectedNode.collectAsState()
    val compassHeading by viewModel.compassHeading.collectAsState()
    val hudState       by viewModel.hudState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startSession()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Layer 1: main screen content ─────────────────────────────────────
        if (selectedNode == null) {
            RadarScreen(
                nodes          = nodes,
                onNodeSelected = { viewModel.selectNode(it) }
            )
        } else {
            GuidanceScreen(
                node           = selectedNode!!,
                compassHeading = compassHeading,
                onClose        = { viewModel.selectNode(null) }
            )
        }

        // ── Layer 2: Developer HUD overlay ───────────────────────────────────
        // Pinned to top-start. Tap the bug icon to expand/collapse.
        // Remove this composable (or wrap in BuildConfig.DEBUG check) for release.
        DevHud(
            state    = hudState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 8.dp)  // clear the status bar
        )
    }
}