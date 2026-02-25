package com.findfriends.core.algorithms

/**
 * RSSI → Distance conversion using the empirical Free Space Path Loss model.
 *
 * Accuracy expectations:
 *   Open air, phones facing each other : ±0.5 – 1.5 m
 *   Indoors, line of sight             : ±1 – 2.5 m
 *   Through walls / body obstruction   : ±2 – 5 m
 *
 * This is inherently approximate. The Kalman-filtered RSSI fed into this
 * function gives significantly better results than raw RSSI.
 */
object BleMath {

    /**
     * Estimates distance in metres from a smoothed RSSI value.
     *
     * @param rssi    Smoothed RSSI (from KalmanFilter). Negative integer, e.g. -65.
     * @param txPower Calibrated RSSI at 1 metre. Most Android devices broadcast
     *                -59 dBm at 1 m. Adjust per-device for better accuracy.
     * @return        Estimated distance in metres. Returns -1 if rssi is 0 (invalid).
     */
    fun calculateDistance(rssi: Int, txPower: Int = -59): Float {
        if (rssi == 0) return -1f

        val ratio = rssi.toDouble() / txPower.toDouble()

        return if (ratio < 1.0) {
            // Device is very close (under ~1 m) — simple 10th-power law
            Math.pow(ratio, 10.0).toFloat()
        } else {
            // Standard empirical model for distances > 1 m
            // Constants derived from real-world BLE calibration datasets
            (0.89976 * Math.pow(ratio, 7.7095) + 0.111).toFloat()
        }
    }

    /**
     * Confidence score: how stable is this RSSI reading right now?
     * When the raw reading deviates far from the smoothed estimate (e.g. a person
     * walks between the two phones) the confidence drops toward 0.
     *
     * @param rawRssi       The raw unfiltered RSSI.
     * @param smoothedRssi  The Kalman-filtered RSSI estimate.
     * @return              Value in [0.0, 1.0]. 1.0 = very stable, 0.0 = very noisy.
     */
    fun confidenceScore(rawRssi: Int, smoothedRssi: Double): Float {
        val deviation = Math.abs(rawRssi - smoothedRssi)
        return (1f - (deviation / 30f).toFloat()).coerceIn(0f, 1f)
    }
}