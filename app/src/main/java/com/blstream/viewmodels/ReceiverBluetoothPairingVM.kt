package com.blstream.viewmodels

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.blstream.bluetooth.FrameReader
import com.blstream.bluetooth.FrameType
import com.blstream.bluetooth.FrameWriter
import com.blstream.bluetooth.Handshake
import com.blstream.bluetooth.HandshakeWatchdog
import java.io.IOException


class ReceiverViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReceiverBluetoothPairingVM::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReceiverBluetoothPairingVM(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ReceiverBluetoothPairingVM(context: Context) : ViewModel(){
    private val appcontext = context
    var connectedsocket by mutableStateOf<BluetoothSocket?>(null)
        private set // Only the VM can modify it now
    var connecteddevicename by mutableStateOf("")
        private set
    var islistening by mutableStateOf(false)
        private set
    var listenerror by mutableStateOf("") // Shown on screen when listening couldn't start
        private set
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter();
    private var acceptthread: AcceptThread? = null

    @Synchronized
    fun StartListening() {
        if (acceptthread?.isAlive == true) return
        acceptthread = AcceptThread().also { it.start() }
    }

    @Synchronized
    fun StopListening() {
        acceptthread?.cancel()
        acceptthread = null
    }

    inner class AcceptThread : Thread() {
        @delegate:SuppressLint("MissingPermission")
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE)
        {
            if (ContextCompat.checkSelfPermission(appcontext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val UUID = "2077faa4-4b23-42a5-94e6-ee47f50ea485"
                val Connection_UUID = java.util.UUID.fromString(UUID)
                try {
                    bluetoothAdapter?.listenUsingRfcommWithServiceRecord("ReceiverSide", Connection_UUID)
                } catch (e: IOException) {
                    Log.e(TAG, "Couldn't open server socket, is Bluetooth on?", e)
                    null
                }
            } else {
                Log.e(TAG, "Permission not granted")
                null
            }
        }
        @Volatile private var listen = true

        override fun run() {
            listenerror = ""
            islistening = true
            // Keep listening until cancelled or a host passes the handshake
            while (listen) {
                val server = mmServerSocket
                if (server == null) {
                    listenerror = if (bluetoothAdapter?.isEnabled != true) "Bluetooth is off" else "Couldn't start listening, check Bluetooth permission"
                    break
                }
                val socket: BluetoothSocket? = try {
                    Log.d(TAG, "Trying to Listen")
                    server.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    if (listen) listenerror = "Listening stopped: ${e.message}" // Not an error if we cancelled it ourselves
                    break
                }
                socket?.also {
                    Log.d(TAG, "Connected, checking handshake")
                    if (HandleConnection(it)) {
                        cancel() // We are done listening
                    }
                }
            }
            islistening = false
        }

        fun cancel() {
            listen = false
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    // Runs on the accept thread, returns true once a valid BLStream host is attached
    @SuppressLint("MissingPermission")
    private fun HandleConnection(mmSocket: BluetoothSocket): Boolean {
        val watchdog = HandshakeWatchdog(mmSocket)
        watchdog.start()
        try {
            if (mmSocket.remoteDevice.bondState != BluetoothDevice.BOND_BONDED) {
                Log.w(TAG, "Rejected unpaired device")
                watchdog.Done()
                mmSocket.close()
                return false
            }

            val hello = FrameReader(mmSocket.inputStream).Read()
            if (hello.type != FrameType.HELLO || !Handshake.IsFromApp(hello)) {
                Log.w(TAG, "Rejected unknown peer")
                watchdog.Done()
                mmSocket.close()
                return false
            }

            val writer = FrameWriter(mmSocket.outputStream)
            if (Handshake.VersionOf(hello) != Handshake.PROTOCOL_VERSION) {
                writer.Write(FrameType.HELLO_ACK, payload = Handshake.HelloAck(Handshake.REJECTED_VERSION))
                watchdog.Done()
                mmSocket.close()
                return false
            }
            writer.Write(FrameType.HELLO_ACK, payload = Handshake.HelloAck(Handshake.ACCEPTED))
            watchdog.Done()

            Log.d(TAG, "Handshake complete") // Connection is working, let's move on
            connecteddevicename = mmSocket.remoteDevice.name ?: mmSocket.remoteDevice.address
            connectedsocket = mmSocket;
            Log.d("BL", "Socket assigned to Receiver VM")
            return true
        } catch (e: IOException) {
            watchdog.Done()
            Log.d(TAG, "Handshake failed", e)
            try { mmSocket.close() } catch (_: IOException) {}
            return false
        }
    }

    override fun onCleared() {
        StopListening()
    }
}
