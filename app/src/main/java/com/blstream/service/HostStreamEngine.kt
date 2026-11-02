package com.blstream.service

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blstream.bluetooth.BluetoothLink
import com.blstream.bluetooth.Frame
import com.blstream.bluetooth.FrameType
import com.blstream.bluetooth.StreamProtocol
import com.blstream.viewmodels.LyricsVM
import com.blstream.viewmodels.ReceiverMediaVM
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.CRC32
import kotlin.concurrent.withLock
import kotlin.random.Random

// Owns the host side of the stream. Lives for the whole process, not the screen,
// so StreamService can keep it running while the app is in the background
class HostStreamEngine private constructor(context: Context) : BluetoothLink.Listener {
    private val appcontext = context
    private val TAG = "HostStreamEngine"

    companion object {
        @Volatile private var instance: HostStreamEngine? = null

        fun Get(context: Context): HostStreamEngine {
            return instance ?: synchronized(this) {
                instance ?: HostStreamEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    fun AddListener(listener: () -> Unit) = listeners.add(listener)
    fun RemoveListener(listener: () -> Unit) = listeners.remove(listener)

    // Keep the service up while there's a receiver to talk to or a song in flight
    fun NeedsService(): Boolean = islinkup || isstreaming || issongloading

    // Wake lock only while there's still data to push to a connected receiver
    fun IsTransferring(): Boolean = lock.withLock {
        val current = song
        isstreaming && link != null && current != null && ackedoffset < current.info.size
    }

    private fun NotifyChanged() {
        StreamService.Sync(appcontext, NeedsService())
        listeners.forEach { it() }
    }

    private class LoadedSong(val uri: Uri, val lyricsuri: Uri?, val info: StreamProtocol.SongInfo, val file: File, val lyrics: ByteArray)

    var currentsong by mutableStateOf<Uri?>(null)
        private set
    var currentlyrics by mutableStateOf<Uri?>(null)
        private set
    var songname by mutableStateOf("")
        private set
    var lyricsname by mutableStateOf("")
        private set
    var lyricslinecount by mutableStateOf(0)
        private set
    var issongloading by mutableStateOf(false)
        private set
    var issongplaying by mutableStateOf(false)
        private set
    var isstreaming by mutableStateOf(false)
        private set
    var islinkup by mutableStateOf(false)
        private set
    var statusmessage by mutableStateOf("")
        private set
    var transferprogress by mutableStateOf(0f)
        private set
    var receiverstate by mutableStateOf("Idle")
        private set
    var receiverpositionms by mutableStateOf(0L)
        private set

    // Everything below is touched by the sender, reader and UI threads, guarded by lock
    private val lock = ReentrantLock()
    private val canSend = lock.newCondition()
    private var link: BluetoothLink? = null
    private var song: LoadedSong? = null
    private var ackedoffset = 0L
    private var sendoffset = 0L
    private var awaitingready = false // Waiting for the receiver to tell us where to resume from
    private var lastacktime = 0L

    private val sender = SenderThread().also { it.start() }
    private val controlqueue = Executors.newSingleThreadExecutor() // Keeps control frames in order, off the UI thread

    fun SelectSong(uri: Uri) {
        if (isstreaming) StopSong()
        currentsong = uri
        songname = DisplayName(uri)
        currentlyrics = null
        lyricsname = ""
        lyricslinecount = 0
    }

    fun SelectLyrics(uri: Uri?) {
        currentlyrics = uri
        lyricsname = uri?.let { DisplayName(it) } ?: ""
    }

    private var attachedsocket: BluetoothSocket? = null

    fun AttachSocket(socket: BluetoothSocket) {
        if (socket == attachedsocket) return // Same socket coming back through recomposition
        attachedsocket = socket
        val newlink = BluetoothLink(socket, this)
        lock.withLock {
            link?.let { old -> link = null; old.Close("Replaced by a new connection") }
            link = newlink
            awaitingready = true
            canSend.signalAll()
        }
        islinkup = true
        statusmessage = ""
        receiverstate = "Connected"
        newlink.Start()
        controlqueue.execute { SendSessionHeader(newlink) }
        NotifyChanged()
    }

    fun Disconnect() {
        val old = lock.withLock { link.also { link = null } }
        old?.Close("Disconnected by user")
        islinkup = false
        receiverstate = "Disconnected"
        NotifyChanged()
    }

    // Start Stream: loads the song if needed then tells the receiver to play
    fun PlaySong() {
        val uri = currentsong
        if (uri == null) {
            statusmessage = "Pick a song first"
            return
        }
        issongplaying = true
        val loaded = lock.withLock { song }
        if (loaded == null || loaded.uri != uri || loaded.lyricsuri != currentlyrics) {
            if (!issongloading) {
                issongloading = true // Set here, not on the thread, so the service starts while we're still in the foreground
                NotifyChanged()
                LoadSong(uri, currentlyrics).start()
            }
            return // LoadSong starts the session when it's done
        }
        if (!isstreaming) {
            BeginSession(loaded)
            return
        }
        SendControl(FrameType.PLAY)
        NotifyChanged()
    }

    fun PauseSong() {
        issongplaying = false
        SendControl(FrameType.PAUSE)
        NotifyChanged()
    }

    fun StopSong() {
        // Terminate Playback
        issongplaying = false
        isstreaming = false
        val sessionid = lock.withLock {
            val id = song?.info?.sessionid
            awaitingready = false
            ackedoffset = 0
            sendoffset = 0
            canSend.signalAll()
            id
        }
        transferprogress = 0f
        receiverpositionms = 0
        if (sessionid != null) SendControl(FrameType.STOP, StreamProtocol.WithSession(sessionid))
        NotifyChanged()
    }

    inner class LoadSong(private val songuri: Uri, private val lyricsuri: Uri?): Thread(){
        override fun run() {
            statusmessage = "Preparing song..."
            try {
                // Copy into our own cache so we can seek to any offset when the receiver asks for a resend
                val file = File(appcontext.cacheDir, "host_song.bin")
                val crc = CRC32()
                appcontext.contentResolver.openInputStream(songuri)?.use { inputStream ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(StreamProtocol.CHUNK_SIZE)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            crc.update(buffer, 0, bytesRead)
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                } ?: throw IOException("Couldn't open the song")
                if (file.length() == 0L) throw IOException("The song file is empty")

                val durationms = ReadDuration(file)
                if (durationms <= 0L) throw IOException("That file doesn't look like playable audio")

                var lyrics = ByteArray(0)
                var linecount = 0
                if (lyricsuri != null) {
                    val bytes = appcontext.contentResolver.openInputStream(lyricsuri)?.use { it.readBytes() } ?: ByteArray(0)
                    linecount = LyricsVM.ParseLyrics(String(bytes, Charsets.UTF_8)).size
                    when {
                        bytes.size > StreamProtocol.MAX_LYRICS_SIZE -> statusmessage = "Lyrics file is too large, streaming without lyrics"
                        linecount == 0 -> statusmessage = "No valid lines in the .srt, streaming without lyrics"
                        else -> lyrics = bytes
                    }
                    if (lyrics.isEmpty()) linecount = 0
                }

                val info = StreamProtocol.SongInfo(Random.nextLong(), file.length(), durationms, crc.value, songname)
                val loaded = LoadedSong(songuri, lyricsuri, info, file, lyrics)
                lyricslinecount = linecount
                lock.withLock { song = loaded }
                if (statusmessage == "Preparing song...") statusmessage = ""
                if (issongplaying) BeginSession(loaded)
            } catch (e: Exception) {
                Log.e(TAG, "LoadSong failed", e)
                statusmessage = "Couldn't load song: ${e.message}"
                issongplaying = false
            } finally {
                issongloading = false
                NotifyChanged()
            }
        }
    }

    private fun BeginSession(loaded: LoadedSong) {
        // A fresh id every time so the receiver never resumes into an old copy
        val fresh = LoadedSong(loaded.uri, loaded.lyricsuri,
            StreamProtocol.SongInfo(Random.nextLong(), loaded.info.size, loaded.info.durationms, loaded.info.crc, loaded.info.name),
            loaded.file, loaded.lyrics)
        val currentlink = lock.withLock {
            song = fresh
            ackedoffset = 0
            sendoffset = 0
            awaitingready = true
            link
        }
        isstreaming = true
        transferprogress = 0f
        if (currentlink != null) controlqueue.execute { SendSessionHeader(currentlink) }
        NotifyChanged()
    }

    // Sent on every new session and after every reconnect, the receiver answers with READY(offset)
    private fun SendSessionHeader(target: BluetoothLink) {
        val current = lock.withLock { song } ?: return
        if (!isstreaming) {
            // Stopped while we were disconnected, make sure the receiver doesn't keep playing the old session
            target.Send(FrameType.STOP, payload = StreamProtocol.WithSession(current.info.sessionid))
            return
        }
        target.Send(FrameType.SONG_INFO, payload = StreamProtocol.EncodeSongInfo(current.info))
        target.Send(FrameType.LYRICS, payload = StreamProtocol.WithSession(current.info.sessionid, current.lyrics))
        target.Send(if (issongplaying) FrameType.PLAY else FrameType.PAUSE)
    }

    private fun SendControl(type: Byte, payload: ByteArray = ByteArray(0)) {
        controlqueue.execute {
            val target = lock.withLock { link }
            target?.Send(type, payload = payload)
        }
    }

    override fun OnFrame(link: BluetoothLink, frame: Frame) {
        when (frame.type) {
            FrameType.READY -> lock.withLock {
                if (link != this.link || StreamProtocol.SessionOf(frame.payload) != song?.info?.sessionid) return
                val size = song?.info?.size ?: 0
                ackedoffset = frame.offset.coerceIn(0, size)
                sendoffset = ackedoffset
                awaitingready = false
                lastacktime = System.currentTimeMillis()
                Log.d(TAG, "Receiver ready from offset $ackedoffset")
                canSend.signalAll()
            }
            FrameType.ACK -> lock.withLock {
                if (StreamProtocol.SessionOf(frame.payload) != song?.info?.sessionid) return
                if (frame.offset > ackedoffset) ackedoffset = frame.offset
                if (sendoffset < ackedoffset) sendoffset = ackedoffset
                lastacktime = System.currentTimeMillis()
                canSend.signalAll()
            }
            FrameType.RESEND -> lock.withLock {
                if (StreamProtocol.SessionOf(frame.payload) != song?.info?.sessionid) return
                // Go back to whatever the receiver has in one piece and send everything after it again
                Log.w(TAG, "Receiver asked to resend from ${frame.offset}")
                ackedoffset = frame.offset.coerceAtLeast(0)
                sendoffset = ackedoffset
                lastacktime = System.currentTimeMillis()
                canSend.signalAll()
            }
            FrameType.CONTROL_REQUEST -> when (frame.payload.firstOrNull()) {
                FrameType.PLAY -> PlaySong()
                FrameType.PAUSE -> PauseSong()
                FrameType.STOP -> StopSong()
            }
            FrameType.STATUS -> {
                if (frame.payload.size < 9) return
                val buffer = ByteBuffer.wrap(frame.payload)
                receiverstate = ReceiverMediaVM.PlaybackState.entries.getOrNull(buffer.get().toInt())?.label ?: "Unknown"
                receiverpositionms = buffer.long
            }
        }
        val size = lock.withLock { song?.info?.size ?: 0L }
        if (size > 0) transferprogress = (lock.withLock { ackedoffset }.toFloat() / size).coerceIn(0f, 1f)
        NotifyChanged()
    }

    override fun OnLinkLost(link: BluetoothLink, reason: String) {
        lock.withLock {
            if (link != this.link) return
            this.link = null
            awaitingready = true
            canSend.signalAll()
        }
        islinkup = false
        receiverstate = "Disconnected"
        statusmessage = "Receiver disconnected ($reason). Tap Reconnect to resume."
        NotifyChanged()
    }

    private inner class SenderThread : Thread("BL-Sender") {
        @Volatile var running = true

        override fun run() {
            val buffer = ByteArray(StreamProtocol.CHUNK_SIZE)
            var reader: RandomAccessFile? = null
            var readerfile: File? = null
            while (running) {
                var target: BluetoothLink? = null
                var sessionid = 0L
                var offset = 0L
                var length = 0
                var file: File? = null

                lock.withLock {
                    val current = song
                    val currentlink = link
                    val now = System.currentTimeMillis()
                    val cansend = current != null && currentlink != null && isstreaming && !awaitingready &&
                            sendoffset < current.info.size && sendoffset - ackedoffset < StreamProtocol.WINDOW_SIZE
                    if (!cansend) {
                        // Window is full (or everything is sent) and acks stopped coming, assume the tail got lost
                        if (current != null && !awaitingready && ackedoffset < sendoffset && now - lastacktime > StreamProtocol.ACK_TIMEOUT_MS) {
                            Log.w(TAG, "No ack for ${now - lastacktime}ms, resending from $ackedoffset")
                            sendoffset = ackedoffset
                            lastacktime = now
                        } else {
                            try {
                                canSend.await(250, TimeUnit.MILLISECONDS)
                            } catch (e: InterruptedException) {
                                running = false
                            }
                        }
                    } else {
                        if (sendoffset == ackedoffset) lastacktime = now
                        target = currentlink
                        sessionid = current!!.info.sessionid
                        offset = sendoffset
                        length = minOf(StreamProtocol.CHUNK_SIZE.toLong(), current.info.size - sendoffset).toInt()
                        file = current.file
                        sendoffset += length
                    }
                }

                val sendto = target ?: continue
                val songfile = file ?: continue
                try {
                    if (readerfile != songfile) {
                        reader?.close()
                        reader = RandomAccessFile(songfile, "r")
                        readerfile = songfile
                    }
                    reader!!.seek(offset)
                    reader.readFully(buffer, 0, length)
                    sendto.Send(FrameType.AUDIO_CHUNK, offset, StreamProtocol.WithSession(sessionid, buffer, length))
                } catch (e: IOException) {
                    Log.e(TAG, "Couldn't read song at $offset", e)
                    lock.withLock { if (sendoffset > offset) sendoffset = offset }
                    try { sleep(250) } catch (_: InterruptedException) { running = false }
                }
            }
            reader?.close()
        }
    }

    private fun DisplayName(uri: Uri): String {
        return try {
            appcontext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "Unknown"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "Unknown"
        }
    }

    private fun ReadDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}
