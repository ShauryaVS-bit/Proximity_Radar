package com.findfriends.viewmodel

import android.app.Application
import android.bluetooth.le.ScanSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.findfriends.core.models.DeviceIdentity
import com.findfriends.core.models.Measurement
import com.findfriends.core.models.PairDistance
import com.findfriends.data.repository.FirestorePairRepository
import com.findfriends.data.repository.PairRepository
import com.findfriends.data.sync.SyncManager
import com.findfriends.data.sync.SyncStats
import com.findfriends.hardware.BleManager
import com.findfriends.hardware.BleStatus
import com.findfriends.logging.DevLog
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Bare-bone ViewModel: BLE scan + advertise + server sync.
 *
 * Exposes everything the LogScreen needs:
 *   - deviceId, bleStatus, measurements, serverPairs, syncStats, logEntries
 *
 * Both devices in any pair scan AND advertise, so each side reports
 * its own measurement to the server. The server merges them by pairKey.
 * A device can be part of N different pairs simultaneously.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VM"
    }

    // -- Identity -----------------------------------------------------------------
    val myDeviceId: String = DeviceIdentity.getOrCreate(application)

    // -- Hardware -----------------------------------------------------------------
    private val bleManager = BleManager(application, myDeviceId, viewModelScope)

    // -- Data layer ---------------------------------------------------------------
    private val repository: PairRepository = FirestorePairRepository()
    private val syncManager = SyncManager(repository, bleManager, viewModelScope)

    // -- Public state flows -------------------------------------------------------

    val bleStatus: StateFlow<BleStatus> = bleManager.status

    val measurements: StateFlow<List<Measurement>> = bleManager.measurements

    val serverPairs: StateFlow<List<PairDistance>> = repository
        .observeMyPairs(myDeviceId)
        .catch { e ->
            DevLog.e(TAG, "serverPairs flow error: ${e.message}")
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncStats: StateFlow<SyncStats> = syncManager.stats

    val logEntries: StateFlow<List<DevLog.Entry>> = DevLog.entries

    // -- Session management -------------------------------------------------------

    private val _sessionActive = MutableStateFlow(false)

    fun startSession() {
        if (_sessionActive.value) {
            DevLog.w(TAG, "startSession() called but session already active - ignoring")
            return
        }

        DevLog.i(TAG, "========================================")
        DevLog.i(TAG, "SESSION START | device=$myDeviceId")
        DevLog.i(TAG, "========================================")

        // Both devices in a pair scan AND advertise
        bleManager.startAdvertising()
        bleManager.startScanning()
        syncManager.start()
        _sessionActive.value = true

        // Register with server
        viewModelScope.launch {
            try {
                repository.registerDevice(myDeviceId)
                DevLog.i(TAG, "Device registered with server")
            } catch (e: Exception) {
                DevLog.e(TAG, "Device registration failed: ${e.message}")
            }
        }
    }

    fun pauseSession() {
        if (!_sessionActive.value) return
        DevLog.i(TAG, "Session PAUSED - switching to LOW_POWER scan")
        bleManager.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
    }

    fun resumeSession() {
        if (!_sessionActive.value) return
        DevLog.i(TAG, "Session RESUMED - switching to LOW_LATENCY scan")
        bleManager.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    }

    fun stopSession() {
        DevLog.i(TAG, "Session STOPPED")
        bleManager.stopScanning()
        bleManager.stopAdvertising()
        syncManager.stop()
        _sessionActive.value = false
    }

    override fun onCleared() {
        super.onCleared()
        DevLog.i(TAG, "ViewModel onCleared - destroying BleManager")
        bleManager.destroy()
        stopSession()
    }
}
