package com.findfriends.core.algorithms

/**
 * A simple scalar Kalman Filter for smoothing noisy BLE RSSI values.
 *
 * Why this matters:
 * Raw RSSI from BLE fluctuates ±10 dBm just from ambient interference and
 * the phone being held at different angles. That translates to ±2–4 metre
 * jumps in the distance estimate. The Kalman filter treats RSSI as a signal
 * with known noise and blends each new reading with the running prediction,
 * dramatically reducing the jitter in the radar display.
 *
 * One instance of this class should be created PER peer device. Do not share
 * a single filter across multiple peers — each peer has its own signal path.
 *
 * @param processNoise      Q — how much we trust the system's own prediction.
 *                          Increase to make the filter react faster to real
 *                          movement at the cost of more noise.
 * @param measurementNoise  R — how noisy we expect the sensor to be.
 *                          Increase to smooth more aggressively but add lag.
 * @param initialEstimate   Starting RSSI guess. -70 is a reasonable default
 *                          for a device a few metres away.
 */
class KalmanFilter(
    private val processNoise: Double = 0.008,
    private val measurementNoise: Double = 0.5,
    initialEstimate: Double = -70.0
) {
    private var estimate = initialEstimate
    private var errorCovariance = 1.0

    /**
     * Feed in a new raw RSSI reading and get back the smoothed estimate.
     * Call this every time a BLE scan result arrives for this peer.
     *
     * @param rawRssi  The raw integer RSSI from ScanResult.
     * @return         Smoothed RSSI as a Double.
     */
    fun update(rawRssi: Double): Double {
        // --- Predict step ---
        // Without a new measurement the uncertainty grows by processNoise.
        val predictedCovariance = errorCovariance + processNoise

        // --- Update step ---
        // Kalman gain: how much weight to give the new measurement vs our prediction.
        // High gain → trust measurement more. Low gain → trust prediction more.
        val kalmanGain = predictedCovariance / (predictedCovariance + measurementNoise)

        // Blend prediction with measurement
        estimate += kalmanGain * (rawRssi - estimate)

        // Shrink error covariance — we now know more than we did before
        errorCovariance = (1.0 - kalmanGain) * predictedCovariance

        return estimate
    }

    /** Reset the filter state, e.g. after a long gap where the peer disappeared. */
    fun reset(initialEstimate: Double = -70.0) {
        estimate = initialEstimate
        errorCovariance = 1.0
    }
}