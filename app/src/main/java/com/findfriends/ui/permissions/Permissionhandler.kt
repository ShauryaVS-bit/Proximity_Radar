package com.findfriends.ui.permissions

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findfriends.ui.theme.TextPrimary
import com.findfriends.ui.theme.TextSecondary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Wraps content that requires Bluetooth permissions.
 *
 * On Android 12+ (API 31+) the required permissions changed from location-based
 * to explicit BLUETOOTH_SCAN / ADVERTISE / CONNECT. This composable handles both.
 *
 * If all permissions are granted, [onGranted] is composed immediately.
 * Otherwise a rationale screen is shown with a single "Grant Permissions" button.
 *
 * Dependency required in build.gradle:
 *   implementation("com.google.accompanist:accompanist-permissions:0.34.0")
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequireBluetoothPermissions(
    onGranted: @Composable () -> Unit
) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // Below Android 12, BLE scanning requires location permission
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions)

    if (permissionsState.allPermissionsGranted) {
        onGranted()
    } else {
        PermissionRationaleScreen(
            onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
        )
    }
}

@Composable
private fun PermissionRationaleScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 24.dp)
        )

        Text(
            text = "Bluetooth Required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "FindFriends uses Bluetooth Low Energy to detect nearby friends " +
                    "without connecting to the internet. No calls or data are made " +
                    "to your contacts.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Grant Permissions", fontSize = 16.sp)
        }
    }
}