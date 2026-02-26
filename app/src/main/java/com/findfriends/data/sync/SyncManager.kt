package com.findfriends.data.sync

import com.findfriends.data.repository.PairRepository
import com.findfriends.hardware.BleManager
import com.findfriends.logging.DevLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bridges BleManager (local BLE measurements) and PairRepository (server writes).
 *
 * Optimisations:
 *   1. INTERVAL BATCHING  - write every SYNC_INTERVAL_MS, not per scan event.
 *   2. DELTA FILTERING    - only write if distance changed by > DELTA_THRESHOLD_M.
 *
 * All operations are logged via DevLog.
 */
class SyncManager(
    private val repository: PairRepository,
    private val bleManager: BleManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SYNC"
        private const val SYNC_INTERVAL_MS  = 3_000L
        private const val DELTA_THRESHOLD_M = 0.4f
    }

    private val lastReportedDistance = mutableMapOf<String, Float>()
    private var syncJob: Job? = null

    // -- Public stats -------------------------------------------------------------

    private val _stats = MutableStateFlow(SyncStats())
    val stats: StateFlow<SyncStats> = _stats.asStateFlow()

    // -- Lifecycle -----------------------------------------------------------------

    fun start() {
        if (syncJob != null) {
            DevLog.w(TAG, "start() called but sync is already running - ignoring")
            return
        }
        DevLog.i(TAG, "Sync loop STARTED | interval=${SYNC_INTERVAL_MS}ms | deltaThreshold=${DELTA_THRESHOLD_M}m")
        syncJob = scope.launch {
            while (true) {
                delay(SYNC_INTERVAL_MS)
                pushChangedMeasurements()
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        DevLog.i(TAG, "Sync loop STOPPED")
    }

    // -- Core logic ---------------------------------------------------------------

    private suspend fun pushChangedMeasurements() {
        val current = bleManager.measurements.value
        if (current.isEmpty()) return

        var writtenThisCycle = 0
        var skippedThisCycle = 0
        var failedThisCycle  = 0

        DevLog.d(TAG, "Sync cycle: ${current.size} measurement(s) to evaluate")

        for (measurement in current) {
            val lastDistance = lastReportedDistance[measurement.pairKey]
            val delta = if (lastDistance != null) Math.abs(measurement.distanceMeters - lastDistance) else Float.MAX_VALUE
            val changed = lastDistance == null || delta > DELTA_THRESHOLD_M

            if (changed) {
                try {
                    repository.reportMeasurement(measurement)
                    lastReportedDistance[measurement.pairKey] = measurement.distanceMeters
                    writtenThisCycle++
                    DevLog.d(TAG, "PUSHED ${measurement.pairKey} | dist=${String.format("%.2f", measurement.distanceMeters)}m | delta=${if (lastDistance != null) String.format("%.2f", delta) else "NEW"}m")
                } catch (e: Exception) {
                    failedThisCycle++
                    DevLog.e(TAG, "PUSH FAILED ${measurement.pairKey}: ${e.message}")
                }
            } else {
                skippedThisCycle++
            }
        }

        if (writtenThisCycle > 0 || skippedThisCycle > 0 || failedThisCycle > 0) {
            _stats.value = _stats.value.copy(
                totalWrites        = _stats.value.totalWrites + writtenThisCycle,
                totalFailed        = _stats.value.totalFailed + failedThisCycle,
                deltaFilteredCount = _stats.value.deltaFilteredCount + skippedThisCycle,
                lastSyncMs         = if (writtenThisCycle > 0) System.currentTimeMillis()
                                     else _stats.value.lastSyncMs
            )
            DevLog.i(TAG, "Cycle done: wrote=$writtenThisCycle skipped=$skippedThisCycle failed=$failedThisCycle | totals: writes=${_stats.value.totalWrites} filtered=${_stats.value.deltaFilteredCount} failed=${_stats.value.totalFailed}")
        }
    }

    suspend fun forceSync() {
        DevLog.i(TAG, "forceSync() called")
        val current = bleManager.measurements.value
        var written = 0
        var failed = 0
        for (measurement in current) {
            try {
                repository.reportMeasurement(measurement)
                lastReportedDistance[measurement.pairKey] = measurement.distanceMeters
                written++
            } catch (e: Exception) {
                failed++
                DevLog.e(TAG, "forceSync FAILED ${measurement.pairKey}: ${e.message}")
            }
        }
        _stats.value = _stats.value.copy(
            totalWrites = _stats.value.totalWrites + written,
            totalFailed = _stats.value.totalFailed + failed,
            lastSyncMs  = if (written > 0) System.currentTimeMillis() else _stats.value.lastSyncMs
        )
        DevLog.i(TAG, "forceSync done: wrote=$written failed=$failed")
    }
}

/**
 * Immutable snapshot of sync statistics.
 */
data class SyncStats(
    val totalWrites: Int = 0,
    val totalFailed: Int = 0,
    val deltaFilteredCount: Int = 0,
    val lastSyncMs: Long = 0L
)
