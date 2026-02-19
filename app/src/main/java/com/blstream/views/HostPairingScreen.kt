package com.blstream.views

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.gestures.forEach
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blstream.viewmodels.HostBluetoothPairingVM
import com.blstream.viewmodels.ReceiverBluetoothPairingVM

@Composable
fun HostPairingScreen() {
    HostScreen()
    val context = LocalContext.current
    val vm: HostBluetoothPairingVM = viewModel() // Use the correct delegate
    val bluetoothAdapter: BluetoothAdapter? = remember { BluetoothAdapter.getDefaultAdapter() }

    // State to hold the list of paired devices
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    // Query the devices (Simplified: assumes permission is already granted)
    @delegate:SuppressLint("MissingPermission")
    LaunchedEffect(Unit)
    {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                val bonded = bluetoothAdapter?.bondedDevices
                if (bonded != null) {
                    pairedDevices = bonded.toList()
                }
            } catch (s: SecurityException) {
                // This happens on Android 12+ if BLUETOOTH_CONNECT permission isn't granted
                Log.e("BT", "Permission missing for bondedDevices", s)
            }
        }
    }
    PairedDevicesList(pairedDevices = pairedDevices,context = context)
}
@Composable
fun HostScreen() {
    Text(text = "Welcome to the Host Screen!")
}

@Composable
fun PairedDevicesList(pairedDevices: List<BluetoothDevice>, vm: HostBluetoothPairingVM = HostBluetoothPairingVM(), context: Context){
    Column {
        pairedDevices.forEach { device ->
            Button(onClick = {
                vm.ConnectThread(device).start()
            })  {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                @delegate:SuppressLint("MissingPermission")
                {
                    Text(text = "Connect to ${device.name ?: "Unknown"} (${device.address})")
                }
            }
        }
    }
}

