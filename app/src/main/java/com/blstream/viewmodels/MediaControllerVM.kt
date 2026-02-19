package com.blstream.viewmodels

import android.bluetooth.BluetoothSocket
import android.net.Uri

class MediaControllerVM(val songpath: Uri, val receiver: BluetoothSocket ) {
    val song = songpath
    var songbuffer: ByteArray = ByteArray(1024);
    suspend fun LoadSong(){
        //Load the song into the cache
    }

    fun PlaySong(){
        //Play the song


    }

    suspend fun PauseSong(){
        //Pause the song

    }

    suspend fun StopSong(){
        // Terminate Playback
    }

    fun StreamSong(songreceiver: BluetoothSocket = receiver){
        // Use the songbuffer defined above and send to the receiver



        //Loop to continue until interrupted or song ends.
    }
}