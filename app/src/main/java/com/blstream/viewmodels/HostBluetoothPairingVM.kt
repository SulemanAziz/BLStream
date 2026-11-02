package com.blstream.viewmodels

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.blstream.bluetooth.FrameReader
import com.blstream.bluetooth.FrameType
import com.blstream.bluetooth.FrameWriter
import com.blstream.bluetooth.Handshake
import com.blstream.bluetooth.HandshakeWatchdog
import java.io.IOException
import kotlin.let

class HostBluetoothPairingVM() : ViewModel() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    var connectedsocket by mutableStateOf<BluetoothSocket?>(null)
        private set // Only the VM can modify it now
    var connectionstatus by mutableStateOf("")
        private set
    var isconnecting by mutableStateOf(false)
        private set
    // Kept for the whole process so Reconnect still works after the activity was closed and reopened
    val lastdevice: BluetoothDevice? get() = rememberedDevice

    companion object {
        @Volatile private var rememberedDevice: BluetoothDevice? = null
    }

    fun SetActiveSocket(activesocket:BluetoothSocket?){
        connectedsocket = activesocket;
    }

    @SuppressLint("MissingPermission")
    fun Connect(device: BluetoothDevice) {
        if (isconnecting) return
        rememberedDevice = device
        ConnectThread(device).start()
    }

    fun Reconnect() {
        lastdevice?.let { Connect(it) }
    }

    inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            val UUID = "2077faa4-4b23-42a5-94e6-ee47f50ea485"
            val Connection_UUID = java.util.UUID.fromString(UUID)
            device.createRfcommSocketToServiceRecord(Connection_UUID)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun run() {
            isconnecting = true
            connectionstatus = "Connecting..."
            bluetoothAdapter?.cancelDiscovery()
            val socket = try {
                mmSocket?.also { it.connect() }
            } catch (e: IOException) {
                Log.e(TAG, "Socket's connect() method failed", e)
                try { mmSocket?.close() } catch (_: IOException) {}
                null
            }
            if (socket == null) {
                connectionstatus = "Couldn't reach the receiver. Is it listening?"
                isconnecting = false
                return
            }
            GetSocket(socket).start()
        }
    }

    inner class GetSocket(public val mmSocket: BluetoothSocket) : Thread() {
        override fun run() {
            val watchdog = HandshakeWatchdog(mmSocket)
            watchdog.start()
            try {
                FrameWriter(mmSocket.outputStream).Write(FrameType.HELLO, payload = Handshake.Hello())
                val reply = FrameReader(mmSocket.inputStream).Read()
                watchdog.Done()

                if (Handshake.IsAccepted(reply)) {
                    Log.d(TAG, "Handshake accepted") // Connection was successful, let's move on
                    connectionstatus = "Connected"
                    SetActiveSocket(mmSocket)
                    Log.d("BL", "Socket assigned to Host VM")
                } else {
                    connectionstatus = "Receiver rejected the connection (app version mismatch?)"
                    mmSocket.close()
                }
            } catch (e: IOException) {
                watchdog.Done()
                Log.e(TAG, "Handshake failed", e)
                connectionstatus = "Handshake failed"
                try { mmSocket.close() } catch (_: IOException) {}
            } finally {
                isconnecting = false
            }
        }
    }
}
