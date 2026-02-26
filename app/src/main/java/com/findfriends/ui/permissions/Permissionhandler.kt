package com.findfriends.ui.permissions

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findfriends.logging.DevLog
import com.findfriends.ui.theme.TextPrimary
import com.findfriends.ui.theme.TextSecondary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

private const val TAG = "PERMISSIONS"

/**
 * Gate that ensures ALL required BLE permissions are granted and Bluetooth
 * is enabled before showing the app content.
 *
 * Flow:
 *   1. Auto-request permissions on first compose (no button press needed)
 *   2. If denied, show rationale with "Grant" button
 *   3. If permanently denied, show "Open Settings" button
 *   4. Once permissions granted, check if Bluetooth is on
 *   5. If BT off, prompt to enable
 *   6. All state transitions are logged via DevLog
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequireBluetoothPermissions(
    onGranted: @Composable () -> Unit
) {
    val context = LocalContext.current

    // -- Determine required permissions based on API level --
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions) { results ->
        // Callback when permission dialog finishes
        results.forEach { (perm, granted) ->
            DevLog.i(TAG, "Permission result: $perm = ${if (granted) "GRANTED" else "DENIED"}")
        }
    }

    // Log initial state
    LaunchedEffect(Unit) {
        DevLog.i(TAG, "API level: ${Build.VERSION.SDK_INT} (${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Android 12+" else "pre-12"})")
        DevLog.i(TAG, "Required permissions: ${permissions.joinToString()}")
        permissions.forEach { perm ->
            val status = permissionsState.permissions.find { it.permission == perm }
            DevLog.d(TAG, "  $perm -> granted=${status?.status?.isGranted}, shouldShowRationale=${status?.status?.shouldShowRationale}")
        }
    }

    // Auto-request on first compose if not yet granted
    var hasAutoRequested by remember { mutableStateOf(false) }
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (!permissionsState.allPermissionsGranted && !hasAutoRequested) {
            DevLog.i(TAG, "Auto-requesting permissions...")
            hasAutoRequested = true
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    if (permissionsState.allPermissionsGranted) {
        DevLog.d(TAG, "All permissions GRANTED - checking Bluetooth state")
        BluetoothEnableGate(context) {
            onGranted()
        }
    } else {
        // Check if any permission is permanently denied (user selected "Don't ask again")
        val permanentlyDenied = permissionsState.permissions.any { p ->
            !p.status.isGranted && !p.status.shouldShowRationale
        } && hasAutoRequested

        if (permanentlyDenied) {
            DevLog.w(TAG, "Some permissions PERMANENTLY DENIED - directing to settings")
            PermanentlyDeniedScreen(context)
        } else {
            DevLog.d(TAG, "Showing permission rationale screen")
            PermissionRationaleScreen(
                onRequestPermissions = {
                    DevLog.i(TAG, "User tapped Grant Permissions")
                    permissionsState.launchMultiplePermissionRequest()
                }
            )
        }
    }
}

/**
 * Checks if Bluetooth is enabled. If not, prompts the user to enable it.
 */
@Composable
private fun BluetoothEnableGate(
    context: Context,
    onReady: @Composable () -> Unit
) {
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = btManager?.adapter

    // Track BT enabled state reactively
    var btEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        btEnabled = adapter?.isEnabled == true
        DevLog.i(TAG, "Enable BT result: ${if (btEnabled) "ENABLED" else "STILL OFF"} (resultCode=${result.resultCode})")
    }

    LaunchedEffect(Unit) {
        DevLog.d(TAG, "Bluetooth adapter present: ${adapter != null}")
        DevLog.d(TAG, "Bluetooth enabled: ${adapter?.isEnabled}")
    }

    if (adapter == null) {
        DevLog.e(TAG, "No Bluetooth adapter - device does not support BLE")
        NoBluetoothScreen()
    } else if (!btEnabled) {
        DevLog.w(TAG, "Bluetooth is OFF - prompting user to enable")
        BluetoothOffScreen(
            onEnable = {
                DevLog.i(TAG, "Requesting Bluetooth enable...")
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        )
    } else {
        DevLog.i(TAG, "Bluetooth is ON - all checks passed")
        onReady()
    }
}

// -- UI Screens -------------------------------------------------------------------

@Composable
private fun PermissionRationaleScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp).padding(bottom = 24.dp)
        )
        Text(
            text = "Bluetooth Required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = "This app uses Bluetooth Low Energy to discover nearby devices " +
                    "and measure proximity. We need BLE permissions to scan and advertise.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Grant Permissions", fontSize = 16.sp)
        }
    }
}

@Composable
private fun PermanentlyDeniedScreen(context: Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(80.dp).padding(bottom = 24.dp)
        )
        Text(
            text = "Permissions Denied",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = "Bluetooth permissions were permanently denied. " +
                    "Please open Settings and grant Bluetooth permissions manually.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(
            onClick = {
                DevLog.i(TAG, "Opening app settings for manual permission grant")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Open Settings", fontSize = 16.sp)
        }
    }
}

@Composable
private fun BluetoothOffScreen(onEnable: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(80.dp).padding(bottom = 24.dp)
        )
        Text(
            text = "Bluetooth is Off",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = "Bluetooth needs to be enabled to scan for and advertise to nearby devices.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Enable Bluetooth", fontSize = 16.sp)
        }
    }
}

@Composable
private fun NoBluetoothScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(80.dp).padding(bottom = 24.dp)
        )
        Text(
            text = "BLE Not Supported",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = "This device does not have Bluetooth Low Energy hardware. The app cannot function.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
