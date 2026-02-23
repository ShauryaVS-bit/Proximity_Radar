package com.findfriends.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.findfriends.core.ClusteringLogic
import com.findfriends.core.RadarNode
import com.findfriends.hardware.OrientationManager
import com.findfriends.hardware.ProximityManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val proximityManager = ProximityManager(application)
    private val orientationManager = OrientationManager(application)

    val compassHeading = orientationManager.azimuth

    // State holding the processed & grouped nodes ready for UI
    val radarNodes: StateFlow<List<RadarNode>> = proximityManager.scannedFriends
        .map { friends -> ClusteringLogic.groupFriends(friends) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedNode = MutableStateFlow<RadarNode?>(null)
    val selectedNode: StateFlow<RadarNode?> = _selectedNode

    fun startSession(userId: String) {
        proximityManager.startAdvertising(userId)
        proximityManager.startScanning()
        orientationManager.start()
    }

    fun stopSession() {
        proximityManager.stopScanning()
        orientationManager.stop()
    }

    fun selectNode(node: RadarNode?) {
        _selectedNode.value = node
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}