package com.blstream.views

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.blstream.routes.MainRoutes
import com.blstream.viewmodels.ReceiverBluetoothPairingVM


@Composable
fun ReceiverPairingScreen(vm: ReceiverBluetoothPairingVM, appcontext: Context, navController: NavController) {

    var permissiongranted by remember { mutableStateOf(false) }
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissiongranted = permissions.values.all { it }
        if (!permissiongranted) Log.e("BT", "User denied permissions")
    }

    LaunchedEffect(Unit) {
        permissiongranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(appcontext, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!permissiongranted) permissionLauncher.launch(permissionsToRequest)
    }

    // Remember which socket we started with so coming back here doesn't bounce straight out again
    val initialsocket = remember { vm.connectedsocket }
    LaunchedEffect(vm.connectedsocket) {
        if (vm.connectedsocket!=null && vm.connectedsocket != initialsocket) {
            with(MainRoutes.Receiver){
                navController.toReceiver()
            }
        }
    }

    BluetoothGate(appcontext) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            when {
                !permissiongranted -> "Bluetooth permission is needed"
                vm.islistening -> "Waiting for the host to connect..."
                vm.listenerror.isNotEmpty() -> vm.listenerror
                else -> "Not listening"
            }
        )
        Spacer(Modifier.height(16.dp))
        Row(
        ) {
            Button(
                onClick = { vm.StartListening() },
                enabled = permissiongranted && !vm.islistening
            ) {
                Text("Start Listening")
            }

            Button(
                onClick = { vm.StopListening() },
                enabled = vm.islistening
            ) {
                Text("Stop Listening")
            }
        }
    }
    }
}
