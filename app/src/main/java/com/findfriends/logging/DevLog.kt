package com.findfriends.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central dev logging system. Every component writes here so the UI
 * can display a real-time stream of everything happening in the app.
 *
 * Also mirrors every entry to android.util.Log (logcat) for ADB debugging.
 *
 * Thread-safe: uses StateFlow + synchronized access.
 */
object DevLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestamp: Long = System.currentTimeMillis(),
        val tag: String,
        val level: Level,
        val message: String
    )

    private const val MAX_ENTRIES = 500
    private const val LOGCAT_TAG = "FindFriends"

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    /** Live stream of all log entries. Newest last. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun d(tag: String, message: String) = log(tag, Level.DEBUG, message)
    fun i(tag: String, message: String) = log(tag, Level.INFO, message)
    fun w(tag: String, message: String) = log(tag, Level.WARN, message)
    fun e(tag: String, message: String) = log(tag, Level.ERROR, message)

    fun log(tag: String, level: Level, message: String) {
        val entry = Entry(tag = tag, level = level, message = message)

        // Mirror to logcat
        val logcatMsg = "[$tag] $message"
        when (level) {
            Level.DEBUG -> Log.d(LOGCAT_TAG, logcatMsg)
            Level.INFO  -> Log.i(LOGCAT_TAG, logcatMsg)
            Level.WARN  -> Log.w(LOGCAT_TAG, logcatMsg)
            Level.ERROR -> Log.e(LOGCAT_TAG, logcatMsg)
        }

        // Append to in-memory list, cap at MAX_ENTRIES
        _entries.update { current ->
            val updated = current + entry
            if (updated.size > MAX_ENTRIES) updated.drop(updated.size - MAX_ENTRIES) else updated
        }
    }

    /** Clear all log entries. */
    fun clear() {
        _entries.value = emptyList()
    }
}
