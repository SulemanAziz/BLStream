package com.blstream.viewmodels

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.let

class HostBluetoothPairingVM() : ViewModel() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    var connectedsocket by mutableStateOf<BluetoothSocket?>(null)
        private set // Only the VM can modify it now
    fun SetActiveSocket(activesocket:BluetoothSocket?){
        connectedsocket = activesocket;
    }
    inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            val UUID = "2077faa4-4b23-42a5-94e6-ee47f50ea485"
            val Connection_UUID = java.util.UUID.fromString(UUID)
            device.createRfcommSocketToServiceRecord(Connection_UUID)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun run() {
            bluetoothAdapter?.cancelDiscovery()
            mmSocket?.let { socket ->
                try {
                    socket.connect()
                }catch(e: IOException){
                    socket.close()
                    Log.e(TAG, "Socket's connect() method failed", e)
                }
                GetSocket(mmSocket!!).start()
                null
            }
        }
    }
    inner class GetSocket(public val mmSocket: BluetoothSocket) : Thread() {
        private val mmOutstream = mmSocket.outputStream
        private val messageString: String = "HElloooo"

        override fun run() {
            try {
                val HandShake = messageString.toByteArray()
                mmOutstream.write(HandShake)
                mmOutstream.flush()
                Log.d(TAG, "Sent: $messageString") // Connection was successful, let's move on

                SetActiveSocket(mmSocket)
                Log.d("BL", "Socket assigned to Host VM")
                return

            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
            }
        }
    }
}