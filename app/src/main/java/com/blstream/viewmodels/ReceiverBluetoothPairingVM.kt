package com.blstream.viewmodels

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import com.blstream.routes.MainRoutes
import com.blstream.routes.MainRoutes.Receiver.toReceiver
import com.blstream.routes.MainRoutes.Receiver.toReceiverPairing
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException


class ReceiverViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReceiverBluetoothPairingVM::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReceiverBluetoothPairingVM(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ReceiverBluetoothPairingVM(context: Context) : ViewModel(){
    private val appcontext = context
    var connectedsocket by mutableStateOf<BluetoothSocket?>(null)
        private set // Only the VM can modify it now
    val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    var listen: Boolean = false;
    var messagefromHost:String = "";
    inner class AcceptThread : Thread() {
        @delegate:SuppressLint("MissingPermission")
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE)
        {
            if (ContextCompat.checkSelfPermission(appcontext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val UUID = "2077faa4-4b23-42a5-94e6-ee47f50ea485"
                val Connection_UUID = java.util.UUID.fromString(UUID)
                bluetoothAdapter.listenUsingRfcommWithServiceRecord("ReceiverSide", Connection_UUID)
            } else {
                Log.e(TAG, "Permission not granted")
                null
            }
        }

        override fun run() {
            // Keep listening until exception occurs or a socket is returned.
            listen = true;
            while (listen) {
                val socket: BluetoothSocket? = try {
                    Log.d(TAG, "Trying to Listen")
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    null
                }
                socket?.also {
                    Log.d(TAG, "Connected, attempting to read buffer")
                    AcceptThread().cancel() // We are done listening
                    HandleConnection(socket).start()
                }
            }
        }
        fun cancel() {
            try {
                mmServerSocket?.close()
                listen = false;
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    inner class HandleConnection(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream = mmSocket.inputStream
        private val expected = "HElloooo";
        private val mmBuffer: ByteArray = ByteArray(expected.length)

        override fun run() {
            var numBytes:Int
            try {
                numBytes = mmInStream?.read(mmBuffer) ?: 0
                messagefromHost = String(mmBuffer, 0, numBytes)
                Log.d(TAG, "Received: $messagefromHost") // Connection is working, let's move on
                connectedsocket = mmSocket;
                Log.d("BL", "Socket assigned to Receiver VM")
                return
            } catch (e: IOException) {
                Log.d(TAG, "Input stream was disconnected", e)
                HandleConnection(mmSocket).cancel()
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

}