package com.blstream.views

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

// Tracks whether Bluetooth is on, updating live when it's toggled from quick settings
@Composable
fun rememberBluetoothOn(appcontext: Context): Boolean {
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    DisposableEffect(adapter) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                enabled = adapter?.isEnabled == true
            }
        }
        ContextCompat.registerReceiver(appcontext, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        onDispose { appcontext.unregisterReceiver(receiver) }
    }
    return enabled
}

// Shows a "turn Bluetooth on" prompt instead of the content while Bluetooth is off
@SuppressLint("MissingPermission")
@Composable
fun BluetoothGate(appcontext: Context, content: @Composable () -> Unit) {
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val enabled = rememberBluetoothOn(appcontext)
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    if (adapter != null && enabled) {
        content()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (adapter == null) "This device doesn't support Bluetooth" else "Bluetooth is off",
            textAlign = TextAlign.Center
        )
        if (adapter != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
                Text("Turn on Bluetooth")
            }
        }
    }
}
