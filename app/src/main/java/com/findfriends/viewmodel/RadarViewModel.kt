package com.findfriends.viewmodel

import android.app.Application
import android.bluetooth.le.ScanSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.findfriends.core.algorithms.ClusteringLogic
import com.findfriends.core.models.DeviceIdentity
import com.findfriends.core.models.PairDistance
import com.findfriends.core.models.RadarNode
import com.findfriends.data.repository.FirestorePairRepository
import com.findfriends.data.repository.PairRepository
import com.findfriends.data.sync.SyncManager
import com.findfriends.hardware.BleManager
import com.findfriends.hardware.OrientationManager
import com.findfriends.ui.debug.HudState
import com.findfriends.ui.debug.PeerHudRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Single source of truth for all UI state including the Developer HUD.
 *
 * [hudState] is derived every second by combining:
 *   - BleManager.status      (scan / advertise flags, scan mode)
 *   - BleManager.measurements (per-peer RSSI, distance, confidence)
 *   - OrientationManager.azimuth
 *   - SyncManager.stats      (write counts, last sync time)
 *   - serverPairs            (Firestore pair count)
 *   - radarNodes             (post-clustering node count)
 */
class RadarViewModel(application: Application) : AndroidViewModel(application) {

    // ── Identity ───────────────────────────────────────────────────────────────
    val myDeviceId: String = DeviceIdentity.getOrCreate(application)

    // ── Hardware ───────────────────────────────────────────────────────────────
    private val bleManager         = BleManager(application, myDeviceId)
    private val orientationManager = OrientationManager(application)

    // ── Data layer ─────────────────────────────────────────────────────────────
    private val repository: PairRepository = FirestorePairRepository()
    private val syncManager = SyncManager(repository, bleManager, viewModelScope)

    // ── Compass ────────────────────────────────────────────────────────────────
    val compassHeading: StateFlow<Float> = orientationManager.azimuth

    // ── Radar nodes (local, BLE-derived) ──────────────────────────────────────
    val radarNodes: StateFlow<List<RadarNode>> = bleManager.measurements
        .map { measurements ->
            ClusteringLogic.groupFriends(measurements.map { it.toFriend() })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Server pairs ───────────────────────────────────────────────────────────
    val serverPairs: StateFlow<List<PairDistance>> = repository
        .observeMyPairs(myDeviceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Selected node ──────────────────────────────────────────────────────────
    private val _selectedNode = MutableStateFlow<RadarNode?>(null)
    val selectedNode: StateFlow<RadarNode?> = _selectedNode.asStateFlow()

    // ── Session flag ───────────────────────────────────────────────────────────
    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    // ── Developer HUD state ────────────────────────────────────────────────────
    /**
     * Rebuilt every second by [startHudRefreshLoop].
     * Combining 5+ flows into one tick-driven snapshot avoids creating a
     * complex combineTransform chain while still feeling live in the UI.
     */
    private val _hudState = MutableStateFlow(HudState(myDeviceId = myDeviceId))
    val hudState: StateFlow<HudState> = _hudState.asStateFlow()

    private fun startHudRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                delay(1_000) // Refresh HUD every second

                val measurements  = bleManager.measurements.value
                val bleStatus     = bleManager.status.value
                val syncStats     = syncManager.stats.value
                val lastSeen      = bleManager.lastSeenTimestamps()
                val nodes         = radarNodes.value
                val pairs         = serverPairs.value
                val heading       = orientationManager.azimuth.value

                val peerRows = measurements.map { m ->
                    PeerHudRow(
                        peerId         = m.peerId,
                        rawRssi        = m.rawRssi,
                        smoothedRssi   = m.smoothedRssi,
                        distanceMeters = m.distanceMeters,
                        confidence     = m.confidence,
                        lastSeenMs     = lastSeen[m.peerId] ?: m.timestamp
                    )
                }.sortedBy { it.distanceMeters } // Closest peers at top

                _hudState.value = HudState(
                    myDeviceId             = myDeviceId,
                    isScanning             = bleStatus.isScanning,
                    isAdvertising          = bleStatus.isAdvertising,
                    scanMode               = bleStatus.scanMode,
                    activePeerCount        = measurements.size,
                    peers                  = peerRows,
                    compassDegrees         = heading,
                    totalWritesThisSession = syncStats.totalWrites,
                    deltaFilteredCount     = syncStats.deltaFilteredCount,
                    lastSyncMs             = syncStats.lastSyncMs,
                    serverPairCount        = pairs.size,
                    radarNodeCount         = nodes.size
                )
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun startSession() {
        bleManager.startAdvertising()
        bleManager.startScanning()
        orientationManager.start()
        syncManager.start()
        startHudRefreshLoop()
        _sessionActive.value = true

        viewModelScope.launch {
            repository.registerDevice(myDeviceId)
        }
    }

    fun pauseSession() {
        bleManager.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
        orientationManager.stop()
    }

    fun resumeSession() {
        bleManager.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        orientationManager.start()
    }

    fun stopSession() {
        bleManager.stopScanning()
        bleManager.stopAdvertising()
        orientationManager.stop()
        _sessionActive.value = false
    }

    fun selectNode(node: RadarNode?) {
        _selectedNode.value = node
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}