package com.blstream.views

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blstream.routes.MainRoutes
import com.blstream.viewmodels.HostBluetoothPairingVM

@Composable
fun HostPairingScreen(vm: HostBluetoothPairingVM, appcontext: Context, navcontroller: NavController) {

    val bluetoothAdapter: BluetoothAdapter? = remember { BluetoothAdapter.getDefaultAdapter() }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    // 1. Define the permissions needed based on Android version
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    // 2. Setup the Launcher to handle the dialog result
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.all { it }
        if (isGranted) {
            val bonded = bluetoothAdapter?.bondedDevices
            if (bonded != null) pairedDevices = bonded.toList()
        } else {
            Log.e("BT", "User denied permissions")
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(appcontext, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            val bonded = bluetoothAdapter?.bondedDevices
            if (bonded != null) pairedDevices = bonded.toList()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    if(pairedDevices!={}){
        PairedDevicesList(pairedDevices = pairedDevices, vm = vm, context = appcontext, navcontroller = navcontroller)
    }
}
@Composable
fun PairedDevicesList(
    pairedDevices: List<BluetoothDevice>,
    vm: HostBluetoothPairingVM,
    context: Context,
    navcontroller: NavController
) {
    LaunchedEffect(vm.connectedsocket){
        if(vm.connectedsocket!=null){
            with(MainRoutes.Host){
                navcontroller.toHost()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        pairedDevices.forEach { device ->
            Button(
            onClick = { vm.ConnectThread(device).start() }
            ) {
                val deviceName = if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device.name ?: "Unknown Device"
                } else {
                    "Permission Denied"
                }
                Text(text = "Connect to $deviceName (${device.address})") // Should start the connection thread
            }
        }
    }
}