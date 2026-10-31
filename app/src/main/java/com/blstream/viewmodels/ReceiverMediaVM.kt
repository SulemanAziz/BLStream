package com.blstream.viewmodels

import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.blstream.bluetooth.BluetoothLink
import com.blstream.bluetooth.Frame
import com.blstream.bluetooth.FrameType
import com.blstream.bluetooth.StreamProtocol
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.CRC32
import kotlin.concurrent.withLock

class ReceiverMediaVM(context: Context) : ViewModel(), BluetoothLink.Listener {
    private val appcontext = context
    private val TAG = "ReceiverMedia"

    enum class PlaybackState(val label: String) {
        IDLE("Idle"), BUFFERING("Buffering"), PAUSED("Paused"), PLAYING("Playing"), ENDED("Ended"), ERROR("Can't play this file")
    }

    companion object {
        const val BUFFER_SECONDS = 5L // Buffer this much audio before starting or resuming
        const val LOW_WATER_SECONDS = 1L // Pause and rebuffer when we get this close to the end of the data
        const val FALLBACK_BYTES_PER_SECOND = 16_000L // 128kbps, used if the host couldn't read a duration
        const val MAX_PLAYER_RETRIES = 3
    }

    val lyrics = LyricsVM()
    var islinkup by mutableStateOf(false)
        private set
    var statusmessage by mutableStateOf("")
        private set
    var songname by mutableStateOf("")
        private set
    var playbackstate by mutableStateOf(PlaybackState.IDLE)
        private set
    var bufferedfraction by mutableStateOf(0f)
        private set
    var positionms by mutableStateOf(0L)
        private set
    var durationms by mutableStateOf(0L)
        private set

    // One song's worth of data, written at its offset into a cache file sized to the song
    private inner class StreamSession(val info: StreamProtocol.SongInfo) {
        val file = File(appcontext.cacheDir, "receiver_stream.bin")
        private val storage = RandomAccessFile(file, "rw").also { it.setLength(info.size) }
        @Volatile var contiguous = 0L // Everything before this offset has arrived
        @Volatile var closed = false
        var lastacked = 0L
        var lastacktime = 0L
        var resendrequestedat = 0L

        val bytespersecond: Long = if (info.durationms > 0) maxOf(1L, info.size * 1000 / info.durationms) else FALLBACK_BYTES_PER_SECOND
        val iscomplete get() = contiguous >= info.size

        fun Write(offset: Long, data: ByteArray, start: Int, length: Int) = synchronized(storage) {
            storage.seek(offset)
            storage.write(data, start, length)
        }

        fun Read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int = synchronized(storage) {
            storage.seek(position)
            storage.read(buffer, offset, length)
        }

        fun Checksum(): Long = synchronized(storage) {
            val crc = CRC32()
            val buffer = ByteArray(64 * 1024)
            storage.seek(0)
            var remaining = info.size
            while (remaining > 0) {
                val count = storage.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) break
                crc.update(buffer, 0, count)
                remaining -= count
            }
            crc.value
        }

        fun Close() {
            closed = true
            datalock.withLock { dataarrived.signalAll() }
            synchronized(storage) {
                try { storage.close() } catch (_: IOException) {}
            }
            file.delete()
        }
    }

    // MediaPlayer reads through this, blocking until the bytes it wants have arrived over Bluetooth
    private inner class StreamingDataSource(private val session: StreamSession) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (position >= session.info.size) return -1
            datalock.withLock {
                while (session.contiguous <= position && !session.closed) {
                    dataarrived.await(500, TimeUnit.MILLISECONDS)
                }
            }
            if (session.closed) return -1
            val count = minOf(size.toLong(), session.contiguous - position).toInt()
            return session.Read(position, buffer, offset, count)
        }

        override fun getSize(): Long = session.info.size

        override fun close() {}
    }

    private val datalock = ReentrantLock()
    private val dataarrived = datalock.newCondition()
    @Volatile private var link: BluetoothLink? = null
    @Volatile private var session: StreamSession? = null
    @Volatile private var hostwantsplaying = false
    @Volatile private var restartrequested = false
    @Volatile private var focuspaused = false
    private val sendqueue = Executors.newSingleThreadExecutor() // Keeps acks/status off the playback thread

    private val audiomanager = appcontext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusrequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    // Another app took over for good, pause everywhere
                    focuspaused = false
                    hasfocus = false
                    RequestControl(FrameType.PAUSE)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> focuspaused = true // Calls, notifications
                AudioManager.AUDIOFOCUS_GAIN -> focuspaused = false
            }
        }
        .build()
    @Volatile private var hasfocus = false

    // Headphones unplugged
    private val noisyreceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) RequestControl(FrameType.PAUSE)
        }
    }

    private val playbackthread = PlaybackThread().also { it.start() }

    init {
        ContextCompat.registerReceiver(appcontext, noisyreceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    var linklost by mutableStateOf(false) // Screen goes back to listening when this flips
        private set
    private var attachedsocket: BluetoothSocket? = null

    fun AttachSocket(socket: BluetoothSocket) {
        if (socket == attachedsocket) return // Same socket coming back through recomposition
        attachedsocket = socket
        val newlink = BluetoothLink(socket, this)
        link?.let { old -> link = null; old.Close("Replaced by a new connection") }
        link = newlink
        islinkup = true
        linklost = false
        statusmessage = ""
        newlink.Start()
    }

    // Receiver buttons go through the host so both screens stay in step.
    // While disconnected we act locally so the buffered audio can still be controlled
    fun RequestControl(action: Byte) {
        val current = link
        if (current != null && current.isopen) {
            sendqueue.execute { current.Send(FrameType.CONTROL_REQUEST, payload = byteArrayOf(action)) }
        } else {
            ApplyControl(action)
        }
    }

    fun TogglePlayback() {
        RequestControl(if (playbackstate == PlaybackState.PLAYING || playbackstate == PlaybackState.BUFFERING && hostwantsplaying) FrameType.PAUSE else FrameType.PLAY)
    }

    private fun ApplyControl(action: Byte) {
        when (action) {
            FrameType.PLAY -> {
                if (playbackstate == PlaybackState.ENDED) restartrequested = true
                hostwantsplaying = true
            }
            FrameType.PAUSE -> hostwantsplaying = false
            FrameType.STOP -> EndSession()
        }
    }

    override fun OnFrame(link: BluetoothLink, frame: Frame) {
        when (frame.type) {
            FrameType.SONG_INFO -> {
                val info = StreamProtocol.DecodeSongInfo(frame.payload) ?: return
                val current = session
                val ready = if (current != null && current.info.sessionid == info.sessionid && current.info.size == info.size) {
                    Log.d(TAG, "Resuming session at ${current.contiguous}")
                    current.contiguous // Same song after a reconnect, carry on where we were
                } else {
                    EndSession()
                    if (appcontext.cacheDir.usableSpace < info.size) {
                        statusmessage = "Not enough storage for this song"
                        link.Send(FrameType.CONTROL_REQUEST, payload = byteArrayOf(FrameType.STOP))
                        return
                    }
                    try {
                        session = StreamSession(info)
                    } catch (e: IOException) {
                        Log.e(TAG, "Couldn't create stream file", e)
                        statusmessage = "Couldn't create a buffer file"
                        link.Send(FrameType.CONTROL_REQUEST, payload = byteArrayOf(FrameType.STOP))
                        return
                    }
                    songname = info.name
                    durationms = info.durationms
                    statusmessage = ""
                    0L
                }
                session?.lastacked = ready
                link.Send(FrameType.READY, ready, StreamProtocol.WithSession(info.sessionid))
            }
            FrameType.LYRICS -> {
                val current = session ?: return
                if (StreamProtocol.SessionOf(frame.payload) != current.info.sessionid) return
                lyrics.LoadLyrics(String(frame.payload, 8, frame.payload.size - 8, Charsets.UTF_8))
            }
            FrameType.AUDIO_CHUNK -> OnAudioChunk(link, frame)
            FrameType.PLAY, FrameType.PAUSE -> ApplyControl(frame.type)
            FrameType.STOP -> {
                if (StreamProtocol.SessionOf(frame.payload) == session?.info?.sessionid) EndSession()
            }
        }
    }

    // Called on the read thread, keep it quick
    private fun OnAudioChunk(link: BluetoothLink, frame: Frame) {
        val current = session ?: return
        if (StreamProtocol.SessionOf(frame.payload) != current.info.sessionid) return
        val length = frame.payload.size - 8
        val now = System.currentTimeMillis()

        when {
            frame.offset == current.contiguous -> {
                if (frame.offset + length > current.info.size) return
                try {
                    current.Write(frame.offset, frame.payload, 8, length)
                } catch (e: IOException) {
                    Log.e(TAG, "Couldn't write chunk", e)
                    return // Not acked, the host will resend it
                }
                datalock.withLock {
                    current.contiguous += length
                    dataarrived.signalAll()
                }
                current.resendrequestedat = 0
                if (current.contiguous - current.lastacked >= StreamProtocol.ACK_EVERY || current.iscomplete) {
                    SendAck(link, current)
                }
                if (current.iscomplete) VerifySong(link, current)
            }
            // Something went missing in between, ask for everything after what we have (go back N)
            frame.offset > current.contiguous -> {
                if (now - current.resendrequestedat > StreamProtocol.ACK_TIMEOUT_MS) {
                    current.resendrequestedat = now
                    link.Send(FrameType.RESEND, current.contiguous, StreamProtocol.WithSession(current.info.sessionid))
                }
            }
            // Duplicate from a resend, just remind the host where we are
            else -> if (now - current.lastacktime > 200) SendAck(link, current)
        }
    }

    override fun OnCorruptFrame(link: BluetoothLink) {
        val current = session ?: return
        val now = System.currentTimeMillis()
        if (now - current.resendrequestedat > StreamProtocol.ACK_TIMEOUT_MS) {
            current.resendrequestedat = now
            link.Send(FrameType.RESEND, current.contiguous, StreamProtocol.WithSession(current.info.sessionid))
        }
    }

    private fun SendAck(link: BluetoothLink, current: StreamSession) {
        current.lastacked = current.contiguous
        current.lastacktime = System.currentTimeMillis()
        link.Send(FrameType.ACK, current.contiguous, StreamProtocol.WithSession(current.info.sessionid))
    }

    private fun VerifySong(link: BluetoothLink, current: StreamSession) {
        Thread {
            val crc = try { current.Checksum() } catch (e: IOException) { -1L }
            if (current.closed || crc == current.info.crc) return@Thread
            // Every frame passed its own check so this is very unlikely, but start over rather than play garbage
            Log.e(TAG, "Song checksum mismatch, downloading again")
            statusmessage = "Song arrived damaged, downloading again"
            datalock.withLock { current.contiguous = 0 }
            current.lastacked = 0
            link.Send(FrameType.RESEND, 0, StreamProtocol.WithSession(current.info.sessionid))
        }.start()
    }

    override fun OnLinkLost(link: BluetoothLink, reason: String) {
        if (link != this.link) return
        this.link = null
        islinkup = false
        linklost = true
        statusmessage = "Host disconnected ($reason)"
        // Keep playing whatever is buffered, playback thread pauses if it runs dry
    }

    private fun EndSession() {
        val current = session
        session = null
        current?.Close()
        hostwantsplaying = false
        restartrequested = false
        lyrics.Clear()
        songname = ""
        durationms = 0
        positionms = 0
        bufferedfraction = 0f
    }

    // The only thread that touches the MediaPlayer
    private inner class PlaybackThread : Thread("BL-Playback") {
        @Volatile var running = true
        private var player: MediaPlayer? = null
        private var playersession: StreamSession? = null
        @Volatile private var prepared = false
        @Volatile private var ended = false
        @Volatile private var failed = false
        private var buffering = true
        private var retries = 0
        private var resumeat = 0L
        private var laststatus = 0L

        override fun run() {
            while (running) {
                try {
                    Tick()
                    sleep(200)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    // MediaPlayer throws IllegalStateException in odd states, rebuild it rather than die
                    Log.e(TAG, "Playback error", e)
                    failed = true
                }
            }
            ReleasePlayer()
        }

        private fun Tick() {
            val current = session
            if (current == null) {
                if (player != null) ReleasePlayer()
                Publish(PlaybackState.IDLE)
                return
            }
            if (playersession != current) {
                ReleasePlayer()
                playersession = current
                retries = 0
                resumeat = 0
                buffering = true
            }
            bufferedfraction = (current.contiguous.toFloat() / current.info.size).coerceIn(0f, 1f)

            if (failed) {
                resumeat = player?.let { try { it.currentPosition.toLong() } catch (e: Exception) { null } } ?: resumeat
                ReleasePlayer()
                failed = false
                if (++retries > MAX_PLAYER_RETRIES) {
                    Publish(PlaybackState.ERROR)
                    return
                }
            }
            if (retries > MAX_PLAYER_RETRIES) {
                Publish(PlaybackState.ERROR)
                return
            }

            val threshold = minOf(current.info.size, current.bytespersecond * BUFFER_SECONDS)
            val mediaplayer = player
            if (mediaplayer == null) {
                if (current.contiguous >= threshold || current.iscomplete) StartPlayer(current)
                Publish(PlaybackState.BUFFERING)
                return
            }
            if (!prepared) {
                Publish(PlaybackState.BUFFERING)
                return
            }

            if (restartrequested) {
                restartrequested = false
                ended = false
                mediaplayer.seekTo(0)
            }

            val position = mediaplayer.currentPosition.toLong()
            val bytepos = if (current.info.durationms > 0) current.info.size * position / current.info.durationms else position * current.bytespersecond / 1000
            val ahead = current.contiguous - bytepos
            if (!current.iscomplete && !buffering && ahead < current.bytespersecond * LOW_WATER_SECONDS) {
                Log.d(TAG, "Buffer ran low, rebuffering")
                buffering = true
            }
            if (buffering && (current.iscomplete || ahead >= minOf(threshold, current.info.size - bytepos))) {
                buffering = false
            }

            val shouldplay = hostwantsplaying && !buffering && !focuspaused && !ended
            if (shouldplay && !mediaplayer.isPlaying) {
                if (RequestFocus()) mediaplayer.start()
            } else if (!shouldplay && mediaplayer.isPlaying) {
                mediaplayer.pause()
            }

            positionms = position
            lyrics.UpdatePosition(position)
            Publish(when {
                ended -> PlaybackState.ENDED
                buffering && hostwantsplaying -> PlaybackState.BUFFERING
                mediaplayer.isPlaying -> PlaybackState.PLAYING
                else -> PlaybackState.PAUSED
            })
        }

        private fun StartPlayer(current: StreamSession) {
            prepared = false
            ended = false
            val seekto = resumeat
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                setDataSource(StreamingDataSource(current))
                setOnPreparedListener {
                    if (seekto > 0) it.seekTo(seekto.toInt())
                    prepared = true
                }
                setOnCompletionListener { ended = true }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error $what/$extra")
                    failed = true
                    true
                }
                prepareAsync()
            }
        }

        private fun ReleasePlayer() {
            val old = player ?: return
            player = null
            prepared = false
            try { old.release() } catch (e: Exception) { Log.e(TAG, "Release failed", e) }
            AbandonFocus()
        }

        private fun Publish(state: PlaybackState) {
            if (playbackstate != state) playbackstate = state
            val now = System.currentTimeMillis()
            val current = link ?: return
            if (now - laststatus < 1000) return
            laststatus = now
            val payload = ByteBuffer.allocate(9).put(state.ordinal.toByte()).putLong(positionms).array()
            sendqueue.execute { current.Send(FrameType.STATUS, payload = payload) }
        }
    }

    private fun RequestFocus(): Boolean {
        if (hasfocus) return true
        hasfocus = audiomanager.requestAudioFocus(focusrequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasfocus
    }

    private fun AbandonFocus() {
        if (!hasfocus) return
        audiomanager.abandonAudioFocusRequest(focusrequest)
        hasfocus = false
    }

    override fun onCleared() {
        playbackthread.running = false
        playbackthread.interrupt()
        link?.Close("App closed")
        EndSession()
        sendqueue.shutdown()
        try { appcontext.unregisterReceiver(noisyreceiver) } catch (_: IllegalArgumentException) {}
    }
}
