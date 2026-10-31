package com.blstream.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException

// Wraps a handshaked socket: one thread reads frames, one sends heartbeats and watches for silence
class BluetoothLink(private val socket: BluetoothSocket, private val listener: Listener) {

    interface Listener {
        fun OnFrame(link: BluetoothLink, frame: Frame)
        fun OnCorruptFrame(link: BluetoothLink) {}
        fun OnLinkLost(link: BluetoothLink, reason: String)
    }

    companion object {
        private const val TAG = "BluetoothLink"
        const val PING_INTERVAL_MS = 2000L
        const val LINK_TIMEOUT_MS = 6000L
    }

    private val writer = FrameWriter(socket.outputStream)
    private val reader = FrameReader(socket.inputStream)
    private val readthread = ReadThread()
    private val heartbeatthread = HeartbeatThread()

    @Volatile private var lastreceived = System.currentTimeMillis()
    @Volatile private var lastsent = System.currentTimeMillis()
    @Volatile var isopen = true
        private set

    fun Start() {
        readthread.start()
        heartbeatthread.start()
    }

    fun Send(type: Byte, offset: Long = 0, payload: ByteArray = ByteArray(0)): Boolean {
        if (!isopen) return false
        return try {
            writer.Write(type, offset, payload)
            lastsent = System.currentTimeMillis()
            true
        } catch (e: IOException) {
            Close("Write failed: ${e.message}")
            false
        }
    }

    fun Close(reason: String) {
        synchronized(this) {
            if (!isopen) return
            isopen = false
        }
        Log.d(TAG, "Link closed: $reason")
        try {
            socket.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the socket", e)
        }
        heartbeatthread.interrupt()
        listener.OnLinkLost(this, reason)
    }

    private inner class ReadThread : Thread("BL-Read") {
        override fun run() {
            while (isopen) {
                try {
                    val frame = reader.Read()
                    lastreceived = System.currentTimeMillis()
                    if (frame.type != FrameType.PING) listener.OnFrame(this@BluetoothLink, frame)
                } catch (e: CorruptFrameException) {
                    Log.w(TAG, "Dropped corrupt frame: ${e.message}")
                    lastreceived = System.currentTimeMillis()
                    listener.OnCorruptFrame(this@BluetoothLink)
                } catch (e: IOException) {
                    Close("Connection lost: ${e.message}")
                    return
                }
            }
        }
    }

    // Catches the case where the peer walks out of range and the socket never errors
    private inner class HeartbeatThread : Thread("BL-Heartbeat") {
        override fun run() {
            while (isopen) {
                try {
                    sleep(500)
                } catch (e: InterruptedException) {
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastreceived > LINK_TIMEOUT_MS) {
                    Close("No response from the other device")
                    return
                }
                if (now - lastsent >= PING_INTERVAL_MS) Send(FrameType.PING)
            }
        }
    }
}

// Closes the socket if the handshake hasn't finished in time, so a silent peer can't hang the thread
class HandshakeWatchdog(private val socket: BluetoothSocket) : Thread("BL-Handshake") {
    override fun run() {
        try {
            sleep(Handshake.TIMEOUT_MS)
            socket.close()
        } catch (e: InterruptedException) {
            // Handshake finished
        } catch (e: IOException) {
            Log.e("HandshakeWatchdog", "Could not close the socket", e)
        }
    }

    fun Done() = interrupt()
}
