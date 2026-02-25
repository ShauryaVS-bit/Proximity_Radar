package com.findfriends.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides a continuous compass heading (azimuth) by fusing accelerometer
 * and magnetometer data through the system's rotation matrix.
 *
 * Used by GuidanceScreen to rotate the direction arrow so it always points
 * toward the selected friend regardless of how the phone is oriented.
 *
 * Lifecycle: call start() in onResume / ViewModel init, stop() in onPause /
 * onCleared to unregister the sensor listener and save battery.
 */
class OrientationManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _azimuth = MutableStateFlow(0f)

    /**
     * Current compass heading in degrees, 0–360.
     * 0° = North, 90° = East, 180° = South, 270° = West.
     * Collect this in the ViewModel and pass it to GuidanceScreen.
     */
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private var gravity:     FloatArray? = null
    private var geomagnetic: FloatArray? = null

    /** Register sensor listeners. Call from ViewModel.init or Activity.onResume. */
    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer,  SensorManager.SENSOR_DELAY_UI)
    }

    /** Unregister listeners to save battery. Call from ViewModel.onCleared or Activity.onPause. */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER    -> gravity     = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD   -> geomagnetic = event.values.clone()
        }

        val g = gravity     ?: return
        val m = geomagnetic ?: return

        val rotationMatrix   = FloatArray(9)
        val inclinationMatrix = FloatArray(9)

        if (!SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, g, m)) return

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // orientation[0] = azimuth in radians, range -π to +π
        var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (degrees < 0f) degrees += 360f

        _azimuth.value = degrees
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Low accuracy is expected for magnetometer indoors.
        // In production: expose accuracy level to UI so we can warn the user
        // to calibrate their compass (the figure-8 gesture).
    }
}