package com.blstream.viewmodels

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.IOException
import kotlin.let

class HostBluetoothPairingVM() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            val NAME = "RECEIVER"
            val Connection_UUID = java.util.UUID.fromString(NAME)

            device.createRfcommSocketToServiceRecord(Connection_UUID)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                try {
                    socket.connect()
                }catch(e: IOException){
                    Log.e(TAG, "Socket's connect() method failed", e)
                }
                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                HandleConnection(mmSocket!!)
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    inner class HandleConnection(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmOutstream = mmSocket.outputStream

        //To the outstream above, we must write the serialized byte array of the mp3 file.
        private val messageString: String = "HElloooo"

        override fun run() {
            try {
                val bytesToSend = messageString.toByteArray()
                mmOutstream.write(bytesToSend)
                // Flush to ensure it's sent immediately
                mmOutstream.flush()

                Log.d(TAG, "Sent: $messageString")
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
            }
        }
    }

}