package com.findfriends.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.ParcelUuid
import com.findfriends.core.BleMath
import com.findfriends.core.Friend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

@SuppressLint("MissingPermission")
class ProximityManager(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser

    // Unique Service UUID for this app
    private val APP_UUID = ParcelUuid(UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"))

    private val _scannedFriends = MutableStateFlow<List<Friend>>(emptyList())
    val scannedFriends: StateFlow<List<Friend>> = _scannedFriends

    // Battery Efficient Scan Settings
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use LOW_POWER when app is backgrounded
        .build()

    private val scanFilter = listOf(ScanFilter.Builder().setServiceUuid(APP_UUID).build())

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord?.getServiceData(APP_UUID) ?: return
            val friendId = String(serviceData) // Decode user ID from BLE packet
            val distance = BleMath.calculateDistance(result.rssi)

            // Note: Standard BLE has no direction. We hash the ID to create a stable mock angle for the UI.
            val simulatedAngle = (friendId.hashCode() % 360).toFloat()

            updateFriend(friendId, distance, simulatedAngle)
        }
    }

    fun startScanning() {
        scanner?.startScan(scanFilter, scanSettings, scanCallback)
    }

    fun stopScanning() {
        scanner?.stopScan(scanCallback)
    }

    fun startAdvertising(myUserId: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(APP_UUID)
            .addServiceData(APP_UUID, myUserId.toByteArray())
            .build()

        advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {})
    }

    private fun updateFriend(id: String, distance: Float, angle: Float) {
        _scannedFriends.update { current ->
            val mutable = current.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.id == id }
            val newFriend = Friend(id, "User $id", distance, angle)

            if (existingIndex >= 0) mutable[existingIndex] = newFriend
            else mutable.add(newFriend)
            mutable
        }
    }
}

class OrientationManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity = event.values
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic = event.values

        if (gravity != null && geomagnetic != null) {
            val R = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                // Convert radians to degrees (0 to 360)
                var azimutDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimutDeg < 0) azimutDeg += 360
                _azimuth.value = azimutDeg
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}