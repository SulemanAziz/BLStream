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
import androidx.core.content.ContextCompat
import java.io.IOException


class ReceiverBluetoothPairingVM(context: Context){
    private val appcontext = context
    val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private inner class AcceptThread : Thread() {
        @delegate:SuppressLint("MissingPermission")
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE)
        {
            if (ContextCompat.checkSelfPermission(appcontext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val NAME = "RECEIVER"
                val Connection_UUID = java.util.UUID.fromString(NAME)
                bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, Connection_UUID)
            } else {
                null
            }
        }

        override fun run() {
            // Keep listening until exception occurs or a socket is returned.
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    shouldLoop = false
                    null
                }
                socket?.also {
                    HandleConnection(socket)
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    inner class HandleConnection(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream = mmSocket.inputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        override fun run(){
            var numBytes:Int
            while (true) {
                try {
                    numBytes = mmInStream?.read(mmBuffer) ?: 0

                    val message = String(mmBuffer, 0, numBytes)
                    Log.d(TAG, "Received: $message")

                    // TODO: Update UI or State with the received message
                    // Since this is a ViewModel, use a StateFlow or LiveData here

                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
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