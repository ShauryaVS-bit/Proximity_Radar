package com.findfriends.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findfriends.core.models.Measurement
import com.findfriends.core.models.PairDistance
import com.findfriends.data.sync.SyncStats
import com.findfriends.hardware.BleStatus
import com.findfriends.logging.DevLog
import java.text.SimpleDateFormat
import java.util.*

// -- Colors for log levels --
private val ColorDebug = Color(0xFF888888)
private val ColorInfo  = Color(0xFF4CAF50)
private val ColorWarn  = Color(0xFFFF9800)
private val ColorError = Color(0xFFFF5252)
private val ColorTag   = Color(0xFF64B5F6)
private val MonoFont   = FontFamily.Monospace

/**
 * Main screen: status dashboard at top, live log stream below.
 * Shows everything the developer needs to see in real time.
 */
@Composable
fun LogScreen(
    deviceId: String,
    bleStatus: BleStatus,
    measurements: List<Measurement>,
    serverPairs: List<PairDistance>,
    syncStats: SyncStats,
    logEntries: List<DevLog.Entry>
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 36.dp) // clear status bar
    ) {
        // ── Status Dashboard ──────────────────────────────────────────
        StatusDashboard(deviceId, bleStatus, measurements, serverPairs, syncStats)

        Divider(color = Color(0xFF333333), thickness = 1.dp)

        // ── Log Header ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DEV LOG (${logEntries.size})",
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFont
            )
            TextButton(
                onClick = { DevLog.clear() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("CLEAR", fontSize = 10.sp, color = Color(0xFF666666))
            }
        }

        // ── Log Stream ────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .padding(horizontal = 4.dp)
        ) {
            items(logEntries, key = { "${it.timestamp}-${it.tag}-${it.message.hashCode()}" }) { entry ->
                LogEntryRow(entry, timeFormat)
            }
        }
    }
}

@Composable
private fun StatusDashboard(
    deviceId: String,
    bleStatus: BleStatus,
    measurements: List<Measurement>,
    serverPairs: List<PairDistance>,
    syncStats: SyncStats
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(8.dp)
    ) {
        // Row 1: Device ID + BLE status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusChip("ID: $deviceId", Color(0xFF64B5F6))
            StatusChip(
                if (bleStatus.isScanning) "SCAN: ON" else "SCAN: OFF",
                if (bleStatus.isScanning) ColorInfo else ColorError
            )
            StatusChip(
                if (bleStatus.isAdvertising) "ADV: ON" else "ADV: OFF",
                if (bleStatus.isAdvertising) ColorInfo else ColorError
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Peers + Server + Sync
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusChip("PEERS: ${measurements.size}", Color(0xFFCE93D8))
            StatusChip("SERVER: ${serverPairs.size}", Color(0xFF80CBC4))
            StatusChip("SYNC: ${syncStats.totalWrites}W/${syncStats.deltaFilteredCount}S/${syncStats.totalFailed}F", Color(0xFFFFCC02))
        }

        // Row 3: Active peers table (if any)
        if (measurements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            measurements.forEach { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = m.peerId,
                        color = ColorTag,
                        fontSize = 10.sp,
                        fontFamily = MonoFont
                    )
                    Text(
                        text = "${String.format("%.1f", m.distanceMeters)}m",
                        color = TextColor(m.distanceMeters),
                        fontSize = 10.sp,
                        fontFamily = MonoFont
                    )
                    Text(
                        text = "rssi:${m.rawRssi}",
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                        fontFamily = MonoFont
                    )
                    Text(
                        text = "conf:${String.format("%.0f", m.confidence * 100)}%",
                        color = ConfidenceColor(m.confidence),
                        fontSize = 10.sp,
                        fontFamily = MonoFont
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = MonoFont,
        modifier = Modifier
            .background(Color(0xFF252525), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun LogEntryRow(entry: DevLog.Entry, timeFormat: SimpleDateFormat) {
    val levelColor = when (entry.level) {
        DevLog.Level.DEBUG -> ColorDebug
        DevLog.Level.INFO  -> ColorInfo
        DevLog.Level.WARN  -> ColorWarn
        DevLog.Level.ERROR -> ColorError
    }
    val levelChar = when (entry.level) {
        DevLog.Level.DEBUG -> "D"
        DevLog.Level.INFO  -> "I"
        DevLog.Level.WARN  -> "W"
        DevLog.Level.ERROR -> "E"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        // Timestamp
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            color = Color(0xFF555555),
            fontSize = 9.sp,
            fontFamily = MonoFont
        )
        Spacer(modifier = Modifier.width(4.dp))
        // Level
        Text(
            text = levelChar,
            color = levelColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MonoFont
        )
        Spacer(modifier = Modifier.width(4.dp))
        // Tag
        Text(
            text = entry.tag,
            color = ColorTag,
            fontSize = 9.sp,
            fontFamily = MonoFont,
            modifier = Modifier.widthIn(max = 70.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        // Message
        Text(
            text = entry.message,
            color = levelColor.copy(alpha = 0.9f),
            fontSize = 9.sp,
            fontFamily = MonoFont,
            maxLines = 3
        )
    }
}

@Composable
private fun TextColor(distanceMeters: Float): Color = when {
    distanceMeters < 2f  -> Color(0xFF4CAF50) // very close = green
    distanceMeters < 5f  -> Color(0xFFFFEB3B) // near = yellow
    distanceMeters < 10f -> Color(0xFFFF9800) // medium = orange
    else                 -> Color(0xFFFF5252) // far = red
}

@Composable
private fun ConfidenceColor(confidence: Float): Color = when {
    confidence > 0.7f -> Color(0xFF4CAF50)
    confidence > 0.4f -> Color(0xFFFF9800)
    else              -> Color(0xFFFF5252)
}
