package com.blstream.viewmodels

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.blstream.service.HostStreamEngine

class MediaViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(MediaControllerVM::class.java) -> MediaControllerVM(context.applicationContext) as T
            modelClass.isAssignableFrom(ReceiverMediaVM::class.java) -> ReceiverMediaVM(context.applicationContext) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// Thin layer over HostStreamEngine, the engine keeps streaming after this VM is gone
class MediaControllerVM(context: Context): ViewModel() {
    private val engine = HostStreamEngine.Get(context)

    val currentsong: Uri? get() = engine.currentsong
    val currentlyrics: Uri? get() = engine.currentlyrics
    val songname: String get() = engine.songname
    val lyricsname: String get() = engine.lyricsname
    val lyricslinecount: Int get() = engine.lyricslinecount
    val issongloading: Boolean get() = engine.issongloading
    val issongplaying: Boolean get() = engine.issongplaying
    val isstreaming: Boolean get() = engine.isstreaming
    val islinkup: Boolean get() = engine.islinkup
    val statusmessage: String get() = engine.statusmessage
    val transferprogress: Float get() = engine.transferprogress
    val receiverstate: String get() = engine.receiverstate
    val receiverpositionms: Long get() = engine.receiverpositionms

    fun SelectSong(uri: Uri) = engine.SelectSong(uri)
    fun SelectLyrics(uri: Uri?) = engine.SelectLyrics(uri)
    fun AttachSocket(socket: BluetoothSocket) = engine.AttachSocket(socket)
    fun Disconnect() = engine.Disconnect()
    fun PlaySong() = engine.PlaySong()
    fun PauseSong() = engine.PauseSong()
    fun StopSong() = engine.StopSong()
}
