package com.blstream.viewmodels

import android.bluetooth.BluetoothSocket
import android.net.Uri

class MediaControllerVM(val songpath: Uri, val receiver: BluetoothSocket ) {
    val song = songpath

    suspend fun LoadSong(){
        //Load the song into the cache
    }

    suspend fun PlaySong(){
        //Play the song
    }

    suspend fun PauseSong(){
        //Pause the song

    }

    suspend fun StopSong(){
        // Terminate Playback, go back to song selection fragment
    }

    fun StreamSong(songreceiver: BluetoothSocket = receiver){
        //Buffer the loaded song into cache and stream it to the receiver

        //Loop to continue until interrupted or song ends.
    }
}