package com.findfriends

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.findfriends.logging.DevLog
import com.findfriends.ui.permissions.RequireBluetoothPermissions
import com.findfriends.ui.screen.LogScreen
import com.findfriends.ui.theme.AppDarkColorScheme
import com.findfriends.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevLog.i("ACTIVITY", "onCreate")

        setContent {
            MaterialTheme(colorScheme = AppDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D0D0D)
                ) {
                    RequireBluetoothPermissions {
                        AppScreen(viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DevLog.i("ACTIVITY", "onResume")
        viewModel.resumeSession()
    }

    override fun onPause() {
        super.onPause()
        DevLog.i("ACTIVITY", "onPause")
        viewModel.pauseSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        DevLog.i("ACTIVITY", "onDestroy")
    }
}

@Composable
fun AppScreen(viewModel: MainViewModel) {
    val bleStatus    by viewModel.bleStatus.collectAsState()
    val measurements by viewModel.measurements.collectAsState()
    val serverPairs  by viewModel.serverPairs.collectAsState()
    val syncStats    by viewModel.syncStats.collectAsState()
    val logEntries   by viewModel.logEntries.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startSession()
    }

    LogScreen(
        deviceId     = viewModel.myDeviceId,
        bleStatus    = bleStatus,
        measurements = measurements,
        serverPairs  = serverPairs,
        syncStats    = syncStats,
        logEntries   = logEntries
    )
}
