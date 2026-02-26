package com.findfriends.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import com.findfriends.core.algorithms.BleMath
import com.findfriends.core.algorithms.KalmanFilter
import com.findfriends.core.algorithms.PairUtils
import com.findfriends.core.models.Measurement
import com.findfriends.logging.DevLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Core BLE engine: scan for peers + advertise this device's identity.
 *
 * Every operation is wrapped with:
 *   - Null checks (adapter/scanner/advertiser)
 *   - Exception guards
 *   - DevLog entries so you can trace every state change in the UI
 *
 * Duty-cycle scanning: 4s scan / 200ms pause so the shared radio can
 * flush advertisement packets. Without this, LOW_LATENCY scanning
 * monopolises the antenna and other devices never see our ads.
 */
@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    private val myDeviceId: String,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "BLE"

        val APP_UUID: ParcelUuid = ParcelUuid(
            UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
        )

        private const val PEER_TIMEOUT_MS   = 10_000L
        private const val SCAN_DUTY_ON_MS   = 4_000L
        private const val ADV_WINDOW_MS     = 200L
        private const val SCAN_RETRY_DELAY  = 2_000L
        private const val MAX_SCAN_RETRIES  = 3
    }

    // -- Bluetooth system services ------------------------------------------------

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Re-fetched every call so they're never null-cached before permissions grant
    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser?
        get() = adapter?.bluetoothLeAdvertiser

    // -- Per-peer state -----------------------------------------------------------

    private val kalmanFilters = mutableMapOf<String, KalmanFilter>()
    private val lastSeenMap   = mutableMapOf<String, Long>()

    // -- Public: live measurements ------------------------------------------------

    private val _measurements = MutableStateFlow<List<Measurement>>(emptyList())
    val measurements: StateFlow<List<Measurement>> = _measurements.asStateFlow()

    // -- Public: BLE operational status -------------------------------------------

    private val _status = MutableStateFlow(BleStatus())
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    // -- Scan config --------------------------------------------------------------

    private var currentScanMode = ScanSettings.SCAN_MODE_LOW_LATENCY
    private val scanFilter = emptyList<ScanFilter>()
    private var dutyCycleJob: Job? = null
    private var peerPruneJob: Job? = null
    private var scanRetryCount = 0

    // -- Bluetooth state receiver -------------------------------------------------

    private var btStateReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    init {
        DevLog.i(TAG, "BleManager init | deviceId=$myDeviceId")
        DevLog.d(TAG, "Adapter present: ${adapter != null}")
        DevLog.d(TAG, "BLE supported: ${adapter?.isEnabled}")

        if (adapter == null) {
            DevLog.e(TAG, "BluetoothAdapter is NULL - device may not support BLE")
        }

        registerBluetoothStateReceiver()
    }

    /**
     * Monitor Bluetooth on/off. If BT is turned off mid-session, stop
     * everything gracefully. If turned back on, log it (user must restart).
     */
    private fun registerBluetoothStateReceiver() {
        btStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        DevLog.w(TAG, "Bluetooth turned OFF - stopping scan & advertise")
                        stopScanningInternal()
                        stopAdvertisingInternal()
                        _status.value = BleStatus() // reset
                    }
                    BluetoothAdapter.STATE_ON -> {
                        DevLog.i(TAG, "Bluetooth turned ON - ready for scan/advertise")
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        DevLog.w(TAG, "Bluetooth turning off...")
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> {
                        DevLog.d(TAG, "Bluetooth turning on...")
                    }
                }
            }
        }
        try {
            context.registerReceiver(
                btStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            )
            isReceiverRegistered = true
            DevLog.d(TAG, "Bluetooth state receiver registered")
        } catch (e: Exception) {
            DevLog.e(TAG, "Failed to register BT state receiver: ${e.message}")
        }
    }

    // -- Scan callback ------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord?.getServiceData(APP_UUID) ?: return
            val peerId = String(serviceData).trim()
            if (peerId == myDeviceId || peerId.isBlank()) return

            val now = System.currentTimeMillis()
            val isNewPeer = !lastSeenMap.containsKey(peerId)
            lastSeenMap[peerId] = now

            val filter       = kalmanFilters.getOrPut(peerId) { KalmanFilter() }
            val smoothedRssi = filter.update(result.rssi.toDouble())
            val distance     = BleMath.calculateDistance(smoothedRssi.toInt())
            val confidence   = BleMath.confidenceScore(result.rssi, smoothedRssi)
            val pairKey      = PairUtils.canonicalKey(myDeviceId, peerId)

            if (isNewPeer) {
                DevLog.i(TAG, "NEW PEER discovered: $peerId | rssi=${result.rssi} | dist=${String.format("%.2f", distance)}m")
            }

            DevLog.d(TAG, "SCAN | peer=$peerId rssi=${result.rssi} smoothed=${String.format("%.1f", smoothedRssi)} dist=${String.format("%.2f", distance)}m conf=${String.format("%.2f", confidence)} pair=$pairKey")

            val measurement = Measurement(
                pairKey        = pairKey,
                reporterId     = myDeviceId,
                peerId         = peerId,
                distanceMeters = distance,
                rawRssi        = result.rssi,
                smoothedRssi   = smoothedRssi,
                confidence     = confidence,
                timestamp      = now
            )

            updateMeasurement(measurement)
        }

        override fun onScanFailed(errorCode: Int) {
            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED                 -> "ALREADY_STARTED"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "REGISTRATION_FAILED"
                SCAN_FAILED_FEATURE_UNSUPPORTED             -> "FEATURE_UNSUPPORTED"
                SCAN_FAILED_INTERNAL_ERROR                  -> "INTERNAL_ERROR"
                else                                        -> "UNKNOWN($errorCode)"
            }
            DevLog.e(TAG, "Scan FAILED: $reason (code=$errorCode)")
            _status.value = _status.value.copy(isScanning = false, scanMode = "FAILED:$reason")

            // Auto-retry for transient errors
            if (errorCode == SCAN_FAILED_INTERNAL_ERROR && scanRetryCount < MAX_SCAN_RETRIES) {
                scanRetryCount++
                DevLog.w(TAG, "Scheduling scan retry $scanRetryCount/$MAX_SCAN_RETRIES in ${SCAN_RETRY_DELAY}ms")
                scope.launch {
                    delay(SCAN_RETRY_DELAY * scanRetryCount)
                    DevLog.i(TAG, "Retrying scan (attempt $scanRetryCount)")
                    startScannerHw()
                }
            }
        }
    }

    // -- State helpers ------------------------------------------------------------

    private fun updateMeasurement(new: Measurement) {
        _measurements.update { current ->
            val mutable = current.toMutableList()
            val idx = mutable.indexOfFirst { it.pairKey == new.pairKey }
            if (idx >= 0) mutable[idx] = new else mutable.add(new)
            val now = System.currentTimeMillis()
            val before = mutable.size
            val filtered = mutable.filter { m -> (now - (lastSeenMap[m.peerId] ?: 0L)) < PEER_TIMEOUT_MS }
            val pruned = before - filtered.size
            if (pruned > 0) {
                DevLog.w(TAG, "Pruned $pruned stale peer(s) (timeout=${PEER_TIMEOUT_MS}ms)")
            }
            filtered
        }
    }

    /** Periodic job to prune stale peers even when no new scans arrive. */
    private fun startPeerPruneLoop() {
        peerPruneJob?.cancel()
        peerPruneJob = scope.launch {
            while (true) {
                delay(PEER_TIMEOUT_MS / 2)
                val now = System.currentTimeMillis()
                val stale = lastSeenMap.filter { (_, ts) -> now - ts >= PEER_TIMEOUT_MS }
                if (stale.isNotEmpty()) {
                    stale.keys.forEach { peerId ->
                        lastSeenMap.remove(peerId)
                        kalmanFilters.remove(peerId)
                        DevLog.w(TAG, "Peer TIMED OUT: $peerId (not seen for ${PEER_TIMEOUT_MS}ms)")
                    }
                    _measurements.update { current ->
                        current.filter { m -> !stale.containsKey(m.peerId) }
                    }
                }
            }
        }
    }

    // -- Low-level scanner control ------------------------------------------------

    private fun startScannerHw() {
        val s = scanner
        if (s == null) {
            DevLog.e(TAG, "startScannerHw: scanner is NULL (BT off or no permission)")
            return
        }
        try {
            s.stopScan(scanCallback)
        } catch (_: Exception) {}
        try {
            s.startScan(scanFilter, buildScanSettings(currentScanMode), scanCallback)
        } catch (e: Exception) {
            DevLog.e(TAG, "startScannerHw exception: ${e.message}")
        }
    }

    private fun stopScannerHw() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            DevLog.w(TAG, "stopScannerHw exception: ${e.message}")
        }
    }

    private fun buildScanSettings(mode: Int) =
        ScanSettings.Builder().setScanMode(mode).build()

    // -- Public API ---------------------------------------------------------------

    /**
     * Check if Bluetooth is enabled and ready.
     */
    fun isBluetoothReady(): Boolean {
        val ready = adapter?.isEnabled == true
        DevLog.d(TAG, "isBluetoothReady: $ready")
        return ready
    }

    /**
     * Start advertising this device's ID over BLE.
     */
    fun startAdvertising() {
        DevLog.i(TAG, "startAdvertising() called")

        if (adapter == null) {
            DevLog.e(TAG, "Cannot advertise: BluetoothAdapter is NULL")
            _status.value = _status.value.copy(isAdvertising = false)
            return
        }

        if (adapter.isEnabled != true) {
            DevLog.e(TAG, "Cannot advertise: Bluetooth is OFF")
            _status.value = _status.value.copy(isAdvertising = false)
            return
        }

        val adv = advertiser
        if (adv == null) {
            DevLog.e(TAG, "Cannot advertise: BluetoothLeAdvertiser is NULL (device may not support BLE peripheral mode)")
            _status.value = _status.value.copy(isAdvertising = false)
            return
        }

        // Stop any previous advertisement to avoid ALREADY_STARTED
        try { adv.stopAdvertising(advertiseCallback) } catch (_: Exception) {}

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(APP_UUID)
            .addServiceData(APP_UUID, myDeviceId.toByteArray())
            .build()

        try {
            adv.startAdvertising(settings, data, advertiseCallback)
            _status.value = _status.value.copy(isAdvertising = true)
            DevLog.i(TAG, "Advertising STARTED | id=$myDeviceId | uuid=${APP_UUID.uuid} | mode=LOW_LATENCY | connectable=false")
        } catch (e: Exception) {
            DevLog.e(TAG, "startAdvertising exception: ${e.message}")
            _status.value = _status.value.copy(isAdvertising = false)
        }
    }

    /**
     * Start scanning for nearby BLE peers with duty-cycle.
     */
    fun startScanning() {
        DevLog.i(TAG, "startScanning() called | mode=${scanModeLabel(currentScanMode)}")

        if (adapter == null) {
            DevLog.e(TAG, "Cannot scan: BluetoothAdapter is NULL")
            _status.value = _status.value.copy(isScanning = false, scanMode = "NO ADAPTER")
            return
        }

        if (adapter.isEnabled != true) {
            DevLog.e(TAG, "Cannot scan: Bluetooth is OFF")
            _status.value = _status.value.copy(isScanning = false, scanMode = "BT OFF")
            return
        }

        if (scanner == null) {
            DevLog.e(TAG, "Cannot scan: BluetoothLeScanner is NULL (permission missing or BT off)")
            _status.value = _status.value.copy(isScanning = false, scanMode = "SCANNER NULL")
            return
        }

        scanRetryCount = 0
        startScannerHw()
        _status.value = _status.value.copy(
            isScanning = true,
            scanMode   = scanModeLabel(currentScanMode)
        )
        DevLog.i(TAG, "Scanning STARTED | mode=${scanModeLabel(currentScanMode)} | dutyOn=${SCAN_DUTY_ON_MS}ms | advWindow=${ADV_WINDOW_MS}ms")

        // Duty cycle loop
        dutyCycleJob?.cancel()
        dutyCycleJob = scope.launch {
            while (true) {
                delay(SCAN_DUTY_ON_MS)
                DevLog.d(TAG, "Duty cycle: pausing scan for ${ADV_WINDOW_MS}ms (adv window)")
                stopScannerHw()
                delay(ADV_WINDOW_MS)
                DevLog.d(TAG, "Duty cycle: resuming scan")
                startScannerHw()
            }
        }

        // Peer prune loop
        startPeerPruneLoop()
    }

    fun stopScanning() {
        DevLog.i(TAG, "stopScanning() called")
        stopScanningInternal()
    }

    private fun stopScanningInternal() {
        dutyCycleJob?.cancel()
        dutyCycleJob = null
        peerPruneJob?.cancel()
        peerPruneJob = null
        stopScannerHw()
        _status.value = _status.value.copy(isScanning = false)
        DevLog.i(TAG, "Scanning STOPPED")
    }

    fun stopAdvertising() {
        DevLog.i(TAG, "stopAdvertising() called")
        stopAdvertisingInternal()
    }

    private fun stopAdvertisingInternal() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            DevLog.w(TAG, "stopAdvertising exception: ${e.message}")
        }
        _status.value = _status.value.copy(isAdvertising = false)
        DevLog.i(TAG, "Advertising STOPPED")
    }

    /**
     * Switch scan mode. Restarts scan with new setting.
     */
    fun setScanMode(mode: Int) {
        val label = scanModeLabel(mode)
        DevLog.i(TAG, "setScanMode: $label")
        currentScanMode = mode
        if (_status.value.isScanning) {
            stopScanningInternal()
            startScanning()
        }
    }

    /** Expose last-seen timestamps for status display. */
    fun lastSeenTimestamps(): Map<String, Long> = lastSeenMap.toMap()

    /** Total unique peers ever seen this session. */
    fun totalPeersEverSeen(): Int = kalmanFilters.size

    /** Clean up resources. */
    fun destroy() {
        DevLog.i(TAG, "destroy() - cleaning up BleManager")
        stopScanningInternal()
        stopAdvertisingInternal()
        kalmanFilters.clear()
        lastSeenMap.clear()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(btStateReceiver)
                isReceiverRegistered = false
                DevLog.d(TAG, "BT state receiver unregistered")
            } catch (e: Exception) {
                DevLog.w(TAG, "Failed to unregister BT receiver: ${e.message}")
            }
        }
    }

    // -- Advertise callback -------------------------------------------------------

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            DevLog.i(TAG, "Advertise onStartSuccess | txPowerLevel=${settingsInEffect.txPowerLevel} | mode=${settingsInEffect.mode} | timeout=${settingsInEffect.timeout}")
        }

        override fun onStartFailure(errorCode: Int) {
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE       -> "DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED      -> "ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR       -> "INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED  -> "FEATURE_UNSUPPORTED"
                else                                  -> "UNKNOWN($errorCode)"
            }
            DevLog.e(TAG, "Advertise FAILED: $reason (code=$errorCode)")
            _status.value = _status.value.copy(isAdvertising = false)
        }
    }

    private fun scanModeLabel(mode: Int): String = when (mode) {
        ScanSettings.SCAN_MODE_LOW_LATENCY -> "LOW_LATENCY"
        ScanSettings.SCAN_MODE_LOW_POWER   -> "LOW_POWER"
        ScanSettings.SCAN_MODE_BALANCED    -> "BALANCED"
        else                               -> "UNKNOWN($mode)"
    }
}

/**
 * Snapshot of current BLE operational state.
 */
data class BleStatus(
    val isScanning: Boolean    = false,
    val isAdvertising: Boolean = false,
    val scanMode: String       = "-"
)
