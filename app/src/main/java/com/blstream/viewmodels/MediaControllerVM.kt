package com.blstream.viewmodels

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel

class MediaControllerVM(): ViewModel() {
    private val socket: BluetoothSocket? = null
    var currentsong: Uri? = null
    var issongplaying: Boolean = false
    var songData: Array<ByteArray>? = null
    var songlengthinBytes:Int = 0
    var incomingsongbuffer: ByteArray = ByteArray(2048)
    var outgoingsongbuffer: ByteArray = ByteArray(2048)

    inner class LoadSong(): Thread(){
        val chunks = mutableListOf<ByteArray>()
        fun run(appcontext: Context) {
            try {
                appcontext.contentResolver.openInputStream(currentsong!!)?.use { inputStream ->
                    val buffer = ByteArray(2048)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val chunk = if (bytesRead == 2048) {
                            buffer.copyOf()
                        } else {
                            buffer.copyOfRange(0, bytesRead)
                        }
                        chunks.add(chunk)
                    }
                }
                songData = chunks.toTypedArray()
                songlengthinBytes = songData!!.sumOf { it.size }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancel(){

        }

    }

    inner class PlaySong: Thread(){
        //Play the song

        fun run(socket: BluetoothSocket){
            issongplaying = true

            //Start the stream sending

            for (chunk in songData!!){ // To do, this sends everything in one go, it should be buffered
                //Loop until song ends
                socket.outputStream.write(chunk)
                socket.outputStream.flush()
            }

        }

        fun cancel(){

        }

    }

    suspend fun PauseSong(){
        //Pause the song
        PlaySong().cancel()
    }

    suspend fun StopSong(){
        // Terminate Playback
    }

    inner class ReceiverSink():Thread(){

        var receivedData: MutableList<ByteArray>? = null

        fun run(socket: BluetoothSocket){
            val readbuffer = ByteArray(2048)
            var bytesRead: Int
            val buffer = ByteArray(socket.inputStream.read(readbuffer, 0, readbuffer.size))
            receivedData?.add(buffer)
        }

        fun cancel(){


        }
    }

}