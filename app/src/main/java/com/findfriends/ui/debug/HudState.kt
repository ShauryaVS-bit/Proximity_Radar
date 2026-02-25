package com.findfriends.ui.debug

/**
 * All the information the Developer HUD needs, bundled into one immutable snapshot.
 * The ViewModel derives this from BleManager + SyncManager state every second.
 *
 * Keeping this as a plain data class means the HUD composable is a pure function
 * of [HudState] — easy to preview and test with fake data.
 */
data class HudState(

    // ── Identity ──────────────────────────────────────────────────────────────
    /** This device's 8-char persistent ID. */
    val myDeviceId: String = "",

    // ── BLE ───────────────────────────────────────────────────────────────────
    /** Whether the BLE scanner is currently running. */
    val isScanning: Boolean = false,

    /** Whether BLE advertising is active (other phones can find us). */
    val isAdvertising: Boolean = false,

    /**
     * "LOW_LATENCY" (foreground) or "LOW_POWER" (background).
     * LOW_LATENCY scans ~10×/s; LOW_POWER ~1×/s.
     */
    val scanMode: String = "—",

    /** How many distinct peer devices are currently in the measurements list. */
    val activePeerCount: Int = 0,

    /** Per-peer debug rows — one entry per live peer. */
    val peers: List<PeerHudRow> = emptyList(),

    // ── Compass ───────────────────────────────────────────────────────────────
    /** Raw compass heading degrees 0–360 from OrientationManager. */
    val compassDegrees: Float = 0f,

    // ── Sync / Firestore ──────────────────────────────────────────────────────
    /** Total number of successful Firestore writes this session. */
    val totalWritesThisSession: Int = 0,

    /** Writes skipped because distance didn't change beyond the delta threshold. */
    val deltaFilteredCount: Int = 0,

    /** Unix millis of the last successful server write, 0 if never. */
    val lastSyncMs: Long = 0L,

    /** Number of pair documents currently reflected back from Firestore. */
    val serverPairCount: Int = 0,

    // ── Clustering ────────────────────────────────────────────────────────────
    /** Number of radar nodes after clustering (≤ activePeerCount). */
    val radarNodeCount: Int = 0,
)

/**
 * One row in the peer table — the raw numbers behind a single BLE peer.
 */
data class PeerHudRow(
    val peerId: String,

    /** Raw RSSI straight from the Android BLE scan result. Very noisy. */
    val rawRssi: Int,

    /**
     * RSSI after Kalman filtering. This is what distance is calculated from.
     * Should be much smoother than rawRssi over time.
     */
    val smoothedRssi: Double,

    /** Distance estimate in metres derived from smoothedRssi via FSPL formula. */
    val distanceMeters: Float,

    /**
     * 0.0–1.0 confidence score.
     * High = rawRssi ≈ smoothedRssi (stable signal).
     * Low  = rawRssi deviates a lot (body blocking, multipath).
     */
    val confidence: Float,

    /** Millis since this peer last sent a BLE advertisement. */
    val lastSeenMs: Long,
)