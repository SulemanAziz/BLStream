package com.blstream.viewmodels

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.let

class HostBluetoothPairingVM() : ViewModel() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            val UUID = "2077faa4-4b23-42a5-94e6-ee47f50ea485"
            val Connection_UUID = java.util.UUID.fromString(UUID)

            device.createRfcommSocketToServiceRecord(Connection_UUID)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                try {
                    socket.connect()
                }catch(e: IOException){
                    Log.e(TAG, "Socket's connect() method failed", e)
                }

                HandleConnection(mmSocket!!).start()
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

    inner class HandleConnection(mmSocket: BluetoothSocket) : Thread() {
        private val mmOutstream = mmSocket.outputStream
//        private val songpath: String = ""; // Audio File path
//        private val inputStream: ByteArrayInputStream = ByteArrayInputStream(ByteArray(songpath.toByteArray()));
//        private val SoundArray: ByteArray = inputStream; // This byte array has a size which needs to be buffered and then written into mmOutstream in chunks

        //To the outstream above, we must write the serialized byte array of the mp3 file.
        private val messageString: String = "HElloooo"

        override fun run() {
            try {
                val SongByteArray = messageString.toByteArray()

                mmOutstream.write(SongByteArray)
                mmOutstream.flush()

                Log.d(TAG, "Sent: $messageString")
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
            }
        }
    }

}