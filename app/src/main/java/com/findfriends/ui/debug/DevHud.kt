package com.findfriends.ui.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

// ── HUD colour palette (intentionally distinct from app palette) ────────────
private val HudBackground   = Color(0xDD0A0A0A)   // near-black, 87% opaque
private val HudBorder       = Color(0x4400FF88)   // green border, 27% opaque
private val HudGreen        = Color(0xFF00FF88)   // section headers + ok values
private val HudYellow       = Color(0xFFFFD600)   // warnings / medium confidence
private val HudRed          = Color(0xFFFF3D3D)   // errors / low confidence / offline
private val HudCyan         = Color(0xFF00E5FF)   // peer IDs
private val HudWhite        = Color(0xFFE0E0E0)   // body text
private val HudDim          = Color(0xFF616161)   // labels / secondary text
private val HudRowAlt       = Color(0x0DFFFFFF)   // alternating table row tint

private val MonoSmall  = FontFamily.Monospace
private val FONT_LABEL = 9.sp
private val FONT_BODY  = 10.sp
private val FONT_VALUE = 10.sp

/**
 * Floating developer HUD overlay.
 *
 * Rendered as a collapsible panel anchored to the top-left corner of the screen.
 * A small bug icon toggles it open/closed so it doesn't obscure the radar during
 * normal use.
 *
 * Sections:
 *   IDENTITY  — device ID
 *   BLE       — scan/advertise status, scan mode, active peer count
 *   PEERS     — one row per live peer: ID, raw RSSI, smoothed RSSI, distance, confidence, age
 *   COMPASS   — heading in degrees + cardinal direction
 *   SYNC      — writes this session, delta-filtered skips, last sync time, server pairs
 *   CLUSTER   — radar node count after grouping
 *
 * Usage:
 *   Box(Modifier.fillMaxSize()) {
 *       RadarScreen(...)
 *       DevHud(state = hudState)   // overlay on top
 *   }
 *
 * @param state   Snapshot of debug data from the ViewModel.
 * @param modifier Optional modifier for positioning.
 */
@Composable
fun DevHud(
    state: HudState,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(10.dp)
            .widthIn(max = 340.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn() + slideInVertically(),
            exit    = fadeOut() + slideOutVertically()
        ) {
            HudPanel(state = state, onClose = { expanded = false })
        }

        // Toggle button — always visible
        if (!expanded) {
            HudToggleButton(onClick = { expanded = true })
        }
    }
}

// ── Toggle button ─────────────────────────────────────────────────────────────

@Composable
private fun HudToggleButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(HudBackground, RoundedCornerShape(8.dp))
            .border(1.dp, HudBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = "Open Dev HUD",
            tint = HudGreen,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Main panel ────────────────────────────────────────────────────────────────

@Composable
private fun HudPanel(state: HudState, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .background(HudBackground, RoundedCornerShape(10.dp))
            .border(1.dp, HudBorder, RoundedCornerShape(10.dp))
            .width(320.dp)
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "DEV HUD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoSmall,
                color = HudGreen,
                letterSpacing = 2.sp
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close HUD",
                    tint = HudDim, modifier = Modifier.size(14.dp))
            }
        }

        HudDivider()

        // ── IDENTITY ─────────────────────────────────────────────────────────
        SectionHeader("IDENTITY")
        HudRow("Device ID", state.myDeviceId, valueColor = HudCyan)

        HudDivider()

        // ── BLE ──────────────────────────────────────────────────────────────
        SectionHeader("BLUETOOTH LE")
        HudRow("Scanning",    if (state.isScanning)    "ACTIVE" else "STOPPED",
            valueColor = if (state.isScanning) HudGreen else HudRed)
        HudRow("Advertising", if (state.isAdvertising) "ACTIVE" else "STOPPED",
            valueColor = if (state.isAdvertising) HudGreen else HudRed)
        HudRow("Scan mode",   state.scanMode,
            valueColor = if (state.scanMode == "LOW_LATENCY") HudGreen else HudYellow)
        HudRow("Active peers", "${state.activePeerCount}",
            valueColor = if (state.activePeerCount > 0) HudGreen else HudDim)

        HudDivider()

        // ── PEERS TABLE ───────────────────────────────────────────────────────
        SectionHeader("PEERS  (${state.peers.size})")

        if (state.peers.isEmpty()) {
            Text(
                text = "  no peers detected",
                fontSize = FONT_BODY,
                color = HudDim,
                fontFamily = MonoSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            PeersTable(state.peers)
        }

        HudDivider()

        // ── COMPASS ───────────────────────────────────────────────────────────
        SectionHeader("COMPASS")
        HudRow("Heading", "${"%.1f".format(state.compassDegrees)}°  ${cardinalDirection(state.compassDegrees)}")

        HudDivider()

        // ── SYNC ──────────────────────────────────────────────────────────────
        SectionHeader("SERVER SYNC")
        HudRow("Writes (session)", "${state.totalWritesThisSession}",
            valueColor = if (state.totalWritesThisSession > 0) HudGreen else HudDim)
        HudRow("Delta-skipped",   "${state.deltaFilteredCount}",
            valueColor = HudYellow)
        HudRow("Server pairs",    "${state.serverPairCount}",
            valueColor = if (state.serverPairCount > 0) HudGreen else HudDim)
        HudRow("Last sync",       formatLastSync(state.lastSyncMs),
            valueColor = syncAgeColor(state.lastSyncMs))

        HudDivider()

        // ── CLUSTERING ────────────────────────────────────────────────────────
        SectionHeader("RADAR")
        HudRow("Radar nodes",  "${state.radarNodeCount}")
        HudRow("Raw peers",    "${state.activePeerCount}")
        if (state.activePeerCount > 0) {
            val grouped = state.activePeerCount - state.radarNodeCount
            HudRow("Clustered",  if (grouped > 0) "$grouped merged into groups" else "none",
                valueColor = if (grouped > 0) HudYellow else HudDim)
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "tap bug icon to collapse",
            fontSize = 8.sp,
            color = HudDim,
            fontFamily = MonoSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// ── Peers table ───────────────────────────────────────────────────────────────

@Composable
private fun PeersTable(peers: List<PeerHudRow>) {
    // Scrollable horizontally in case columns are wide
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        // Column header
        Row(modifier = Modifier.fillMaxWidth()) {
            PeerCell("ID",       weight = 1.2f, isHeader = true)
            PeerCell("Raw",      weight = 0.8f, isHeader = true)
            PeerCell("Smooth",   weight = 0.9f, isHeader = true)
            PeerCell("Dist",     weight = 0.9f, isHeader = true)
            PeerCell("Conf",     weight = 0.8f, isHeader = true)
            PeerCell("Age",      weight = 0.8f, isHeader = true)
        }

        peers.forEachIndexed { idx, peer ->
            val rowBg = if (idx % 2 == 0) Color.Transparent else HudRowAlt
            val confidenceColor = when {
                peer.confidence >= 0.7f -> HudGreen
                peer.confidence >= 0.4f -> HudYellow
                else                    -> HudRed
            }
            val ageMs  = System.currentTimeMillis() - peer.lastSeenMs
            val ageColor = when {
                ageMs < 2_000  -> HudGreen
                ageMs < 6_000  -> HudYellow
                else           -> HudRed
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
            ) {
                PeerCell(peer.peerId,                                 weight = 1.2f, color = HudCyan)
                PeerCell("${peer.rawRssi}",                           weight = 0.8f)
                PeerCell("${"%.1f".format(peer.smoothedRssi)}",       weight = 0.9f)
                PeerCell("${"%.1f".format(peer.distanceMeters)}m",    weight = 0.9f)
                PeerCell("${"%.0f".format(peer.confidence * 100)}%",  weight = 0.8f, color = confidenceColor)
                PeerCell("${ageMs / 1000}s",                          weight = 0.8f, color = ageColor)
            }
        }
    }
}

@Composable
private fun RowScope.PeerCell(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
    color: Color = if (isHeader) HudDim else HudWhite
) {
    Text(
        text       = text,
        fontSize   = if (isHeader) FONT_LABEL else FONT_BODY,
        fontFamily = MonoSmall,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        color      = color,
        maxLines   = 1,
        modifier   = Modifier
            .weight(weight)
            .padding(horizontal = 2.dp, vertical = 2.dp)
    )
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun SectionHeader(label: String) {
    Text(
        text       = label,
        fontSize   = FONT_LABEL,
        fontWeight = FontWeight.Bold,
        fontFamily = MonoSmall,
        color      = HudGreen,
        letterSpacing = 1.sp,
        modifier   = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun HudRow(
    label: String,
    value: String,
    valueColor: Color = HudWhite
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text       = label,
            fontSize   = FONT_LABEL,
            color      = HudDim,
            fontFamily = MonoSmall,
            modifier   = Modifier.weight(1f)
        )
        Text(
            text       = value,
            fontSize   = FONT_VALUE,
            color      = valueColor,
            fontFamily = MonoSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HudDivider() {
    Divider(
        color     = HudBorder,
        thickness = 0.5.dp,
        modifier  = Modifier.padding(vertical = 4.dp)
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun cardinalDirection(degrees: Float): String = when ((degrees / 45).toInt() % 8) {
    0 -> "N"
    1 -> "NE"
    2 -> "E"
    3 -> "SE"
    4 -> "S"
    5 -> "SW"
    6 -> "W"
    7 -> "NW"
    else -> "—"
}

private fun formatLastSync(lastSyncMs: Long): String {
    if (lastSyncMs == 0L) return "never"
    val ageMs = System.currentTimeMillis() - lastSyncMs
    return when {
        ageMs < 5_000  -> "${ageMs / 1000}s ago"
        ageMs < 60_000 -> "${ageMs / 1000}s ago"
        else           -> SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date(lastSyncMs))
    }
}

private fun syncAgeColor(lastSyncMs: Long): Color {
    if (lastSyncMs == 0L) return HudRed
    val ageMs = System.currentTimeMillis() - lastSyncMs
    return when {
        ageMs < 5_000  -> HudGreen
        ageMs < 15_000 -> HudYellow
        else           -> HudRed
    }
}