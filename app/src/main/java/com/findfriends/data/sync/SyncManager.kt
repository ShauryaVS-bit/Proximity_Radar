package com.findfriends.data.sync

import com.findfriends.data.repository.PairRepository
import com.findfriends.hardware.BleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bridges BleManager (local BLE measurements) and PairRepository (server writes).
 *
 * Two key optimisations:
 *   1. INTERVAL BATCHING  — write once every SYNC_INTERVAL_MS, not on every scan event.
 *   2. DELTA FILTERING    — only write if distance changed by > DELTA_THRESHOLD_M.
 *
 * Also exposes [SyncStats] as a StateFlow so the Developer HUD can show
 * live write counts, skip counts, and last-sync timestamp.
 */
class SyncManager(
    private val repository: PairRepository,
    private val bleManager: BleManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val SYNC_INTERVAL_MS  = 3_000L
        private const val DELTA_THRESHOLD_M = 0.4f
    }

    private val lastReportedDistance = mutableMapOf<String, Float>()
    private var running = false

    // ── Public stats (consumed by HUD via ViewModel) ──────────────────────────
    private val _stats = MutableStateFlow(SyncStats())

    /**
     * Live sync statistics. Collect in ViewModel and forward to HudState.
     */
    val stats: StateFlow<SyncStats> = _stats.asStateFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        if (running) return
        running = true
        scope.launch {
            while (true) {
                delay(SYNC_INTERVAL_MS)
                pushChangedMeasurements()
            }
        }
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    private suspend fun pushChangedMeasurements() {
        val current = bleManager.measurements.value
        var writtenThisCycle = 0
        var skippedThisCycle = 0

        for (measurement in current) {
            val lastDistance = lastReportedDistance[measurement.pairKey]
            val changed = lastDistance == null ||
                    Math.abs(measurement.distanceMeters - lastDistance) > DELTA_THRESHOLD_M

            if (changed) {
                try {
                    repository.reportMeasurement(measurement)
                    lastReportedDistance[measurement.pairKey] = measurement.distanceMeters
                    writtenThisCycle++
                } catch (e: Exception) {
                    // Retry next cycle
                }
            } else {
                skippedThisCycle++
            }
        }

        if (writtenThisCycle > 0 || skippedThisCycle > 0) {
            _stats.value = _stats.value.copy(
                totalWrites        = _stats.value.totalWrites + writtenThisCycle,
                deltaFilteredCount = _stats.value.deltaFilteredCount + skippedThisCycle,
                lastSyncMs         = if (writtenThisCycle > 0) System.currentTimeMillis()
                else _stats.value.lastSyncMs
            )
        }
    }

    suspend fun forceSync() {
        bleManager.measurements.value.forEach { measurement ->
            try {
                repository.reportMeasurement(measurement)
                lastReportedDistance[measurement.pairKey] = measurement.distanceMeters
                _stats.value = _stats.value.copy(
                    totalWrites = _stats.value.totalWrites + 1,
                    lastSyncMs  = System.currentTimeMillis()
                )
            } catch (_: Exception) {}
        }
    }
}

/**
 * Immutable snapshot of sync statistics.
 */
data class SyncStats(
    val totalWrites: Int = 0,
    val deltaFilteredCount: Int = 0,
    val lastSyncMs: Long = 0L
)