package com.findfriends.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.findfriends.core.algorithms.BleMath
import com.findfriends.core.algorithms.KalmanFilter
import com.findfriends.core.algorithms.PairUtils
import com.findfriends.core.models.Measurement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Manages BLE advertising and scanning.
 *
 * Each discovered peer gets its own [KalmanFilter] for RSSI smoothing.
 * Peers not seen for [PEER_TIMEOUT_MS] are pruned from the measurements list.
 *
 * Now also exposes [BleStatus] — a small state object the HUD reads to show
 * whether scanning/advertising is active and which scan mode is in use.
 */
@SuppressLint("MissingPermission")
class BleManager(
    context: Context,
    private val myDeviceId: String
) {
    companion object {
        val APP_UUID: ParcelUuid = ParcelUuid(
            UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
        )
        private const val PEER_TIMEOUT_MS = 10_000L
    }

    // ── Bluetooth system services ──────────────────────────────────────────────
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    // Computed properties instead of vals — re-fetched on every call so they
    // are never null-cached at construction time before permissions are granted.
    // This is the root fix for the "Phone B scanner is null" asymmetry bug.
    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser?
        get() = adapter?.bluetoothLeAdvertiser

    // ── Per-peer state ─────────────────────────────────────────────────────────
    private val kalmanFilters = mutableMapOf<String, KalmanFilter>()
    private val lastSeenMap   = mutableMapOf<String, Long>()

    // ── Public: live measurements ──────────────────────────────────────────────
    private val _measurements = MutableStateFlow<List<Measurement>>(emptyList())
    val measurements: StateFlow<List<Measurement>> = _measurements.asStateFlow()

    // ── Public: BLE status for HUD ─────────────────────────────────────────────
    private val _status = MutableStateFlow(BleStatus())

    /**
     * Live BLE operational status. Collect in ViewModel, forward to HudState.
     */
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    // ── Scan config ────────────────────────────────────────────────────────────
    private var currentScanMode = ScanSettings.SCAN_MODE_LOW_LATENCY

    // Empty filter = scan ALL nearby BLE devices, then filter manually in the callback.
    // Hardware-level filters on Samsung/Xiaomi/Pixel can silently drop matching packets,
    // causing the asymmetry bug where one phone sees the other but not vice versa.
    private val scanFilter = emptyList<ScanFilter>()

    private fun buildScanSettings(mode: Int) =
        ScanSettings.Builder().setScanMode(mode).build()

    // ── Scan callback ──────────────────────────────────────────────────────────
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Manual UUID filter — replaces the removed hardware ScanFilter.
            // We now manually check for our service UUID so we still ignore
            // irrelevant BLE devices, but without relying on the hardware filter
            // that silently fails on certain Android manufacturers.
            val serviceData = result.scanRecord?.getServiceData(APP_UUID) ?: return
            val peerId = String(serviceData).trim()
            if (peerId == myDeviceId || peerId.isBlank()) return

            lastSeenMap[peerId] = System.currentTimeMillis()

            val filter        = kalmanFilters.getOrPut(peerId) { KalmanFilter() }
            val smoothedRssi  = filter.update(result.rssi.toDouble())
            val distance      = BleMath.calculateDistance(smoothedRssi.toInt())
            val confidence    = BleMath.confidenceScore(result.rssi, smoothedRssi)

            updateMeasurement(
                Measurement(
                    pairKey        = PairUtils.canonicalKey(myDeviceId, peerId),
                    reporterId     = myDeviceId,
                    peerId         = peerId,
                    distanceMeters = distance,
                    rawRssi        = result.rssi,
                    smoothedRssi   = smoothedRssi,
                    confidence     = confidence,
                    timestamp      = System.currentTimeMillis()
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            // Decode the error so it shows up clearly in the HUD and logcat.
            // Previously this was silent — making debugging impossible.
            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED                 -> "ALREADY_STARTED"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "REGISTRATION_FAILED"
                SCAN_FAILED_FEATURE_UNSUPPORTED             -> "FEATURE_UNSUPPORTED"
                SCAN_FAILED_INTERNAL_ERROR                  -> "INTERNAL_ERROR"
                else                                        -> "UNKNOWN($errorCode)"
            }
            _status.value = _status.value.copy(
                isScanning = false,
                scanMode   = "FAILED:$reason"
            )
            android.util.Log.e("BleManager", "onScanFailed: $reason (code=$errorCode)")
        }
    }

    // ── State helpers ──────────────────────────────────────────────────────────
    private fun updateMeasurement(new: Measurement) {
        _measurements.update { current ->
            val mutable = current.toMutableList()
            val idx = mutable.indexOfFirst { it.pairKey == new.pairKey }
            if (idx >= 0) mutable[idx] = new else mutable.add(new)
            val now = System.currentTimeMillis()
            mutable.filter { m -> (now - (lastSeenMap[m.peerId] ?: 0L)) < PEER_TIMEOUT_MS }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun startAdvertising() {
        val adv = adapter?.bluetoothLeAdvertiser
        if (adv == null) {
            _status.value = _status.value.copy(isAdvertising = false)
            android.util.Log.e(
                "BleManager",
                "startAdvertising: bluetoothLeAdvertiser is null — " +
                        "BLUETOOTH_ADVERTISE permission missing, Bluetooth off, " +
                        "or device does not support BLE advertising."
            )
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(APP_UUID)
            .addServiceData(APP_UUID, myDeviceId.toByteArray())
            .build()

        adv.startAdvertising(settings, data, advertiseCallback)
        _status.value = _status.value.copy(isAdvertising = true)
        android.util.Log.d("BleManager", "Advertising started — id=$myDeviceId")
    }

    fun startScanning() {
        val s = adapter?.bluetoothLeScanner
        if (s == null) {
            // This is the key symptom of the asymmetry bug:
            // permissions weren't granted yet when this was first called,
            // or Bluetooth is disabled. The HUD will show "SCANNER NULL".
            _status.value = _status.value.copy(
                isScanning = false,
                scanMode   = "SCANNER NULL"
            )
            android.util.Log.e(
                "BleManager",
                "startScanning: bluetoothLeScanner is null — " +
                        "BLUETOOTH_SCAN permission missing or Bluetooth is off."
            )
            return
        }
        s.startScan(scanFilter, buildScanSettings(currentScanMode), scanCallback)
        _status.value = _status.value.copy(
            isScanning = true,
            scanMode   = scanModeLabel(currentScanMode)
        )
        android.util.Log.d("BleManager", "Scanning started — mode=${scanModeLabel(currentScanMode)}")
    }

    fun stopScanning() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _status.value = _status.value.copy(isScanning = false)
        android.util.Log.d("BleManager", "Scanning stopped")
    }

    fun stopAdvertising() {
        adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        _status.value = _status.value.copy(isAdvertising = false)
        android.util.Log.d("BleManager", "Advertising stopped")
    }

    /**
     * Switch scan mode (LOW_LATENCY foreground / LOW_POWER background).
     * Restarts the scan automatically with the new setting.
     */
    fun setScanMode(mode: Int) {
        currentScanMode = mode
        stopScanning()
        startScanning()
    }

    /** Exposes the last-seen timestamps map so the HUD can calculate peer age. */
    fun lastSeenTimestamps(): Map<String, Long> = lastSeenMap.toMap()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            _status.value = _status.value.copy(isAdvertising = false)
        }
    }

    private fun scanModeLabel(mode: Int): String = when (mode) {
        ScanSettings.SCAN_MODE_LOW_LATENCY -> "LOW_LATENCY"
        ScanSettings.SCAN_MODE_LOW_POWER   -> "LOW_POWER"
        ScanSettings.SCAN_MODE_BALANCED    -> "BALANCED"
        else                               -> "UNKNOWN"
    }
}

/**
 * Snapshot of current BLE operational state for the HUD.
 */
data class BleStatus(
    val isScanning: Boolean    = false,
    val isAdvertising: Boolean = false,
    val scanMode: String       = "—"
)