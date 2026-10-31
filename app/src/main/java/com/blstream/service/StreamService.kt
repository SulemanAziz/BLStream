package com.blstream.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.blstream.MainActivity
import com.example.blstream.R

// Keeps the host process alive while streaming so Android doesn't kill it in the background.
// The streaming itself happens in HostStreamEngine, this just holds the notification and wake lock
class StreamService : Service() {

    companion object {
        private const val TAG = "StreamService"
        private const val CHANNEL_ID = "blstream_host"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PLAY = "com.blstream.action.PLAY"
        private const val ACTION_PAUSE = "com.blstream.action.PAUSE"
        private const val ACTION_STOP = "com.blstream.action.STOP"
        private const val NOTIFY_INTERVAL_MS = 1000L

        @Volatile private var running = false

        // Called by the engine whenever its state changes
        fun Sync(context: Context, needed: Boolean) {
            if (needed && !running) {
                running = true
                try {
                    ContextCompat.startForegroundService(context, Intent(context, StreamService::class.java))
                } catch (e: Exception) {
                    // Android 12+ refuses to start one from the background, we'll try again on the next change
                    Log.e(TAG, "Couldn't start the stream service", e)
                    running = false
                }
            } else if (!needed && running) {
                running = false
                context.stopService(Intent(context, StreamService::class.java))
            }
        }
    }

    private lateinit var engine: HostStreamEngine
    private lateinit var wakelock: PowerManager.WakeLock
    private var lastnotify = 0L
    private val onchanged: () -> Unit = { Refresh(false) }

    override fun onCreate() {
        super.onCreate()
        engine = HostStreamEngine.Get(this)
        wakelock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BLStream:Transfer")
            .apply { setReferenceCounted(false) }
        CreateChannel()
        engine.AddListener(onchanged)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground straight away or Android kills us
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, BuildNotification(), type)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't go foreground", e)
            running = false
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PLAY -> engine.PlaySong()
            ACTION_PAUSE -> engine.PauseSong()
            ACTION_STOP -> engine.StopSong()
        }
        Refresh(true)
        // The Bluetooth socket dies with the process, so restarting us empty would be pointless
        return START_NOT_STICKY
    }

    @Synchronized
    private fun Refresh(force: Boolean) {
        if (engine.IsTransferring()) {
            if (!wakelock.isHeld) wakelock.acquire(60 * 60 * 1000L) // Safety timeout, released as soon as the transfer ends
        } else if (wakelock.isHeld) {
            wakelock.release()
        }

        // Progress updates come in on every ack, don't spam the notification
        val now = System.currentTimeMillis()
        if (!force && now - lastnotify < NOTIFY_INTERVAL_MS) return
        lastnotify = now
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, BuildNotification())
    }

    private fun BuildNotification(): Notification {
        val openapp = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)

        val title = if (engine.songname.isEmpty()) "BLStream" else engine.songname
        val text = when {
            !engine.islinkup -> "Receiver disconnected"
            engine.issongloading -> "Preparing song..."
            engine.isstreaming -> "Receiver: ${engine.receiverstate}  -  Sent ${(engine.transferprogress * 100).toInt()}%"
            else -> "Connected to receiver"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_devices)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openapp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (engine.isstreaming || engine.issongloading) {
            if (engine.issongplaying) {
                builder.addAction(R.drawable.ic_pause, "Pause", ActionIntent(ACTION_PAUSE))
            } else {
                builder.addAction(R.drawable.ic_play, "Play", ActionIntent(ACTION_PLAY))
            }
            builder.addAction(R.drawable.ic_stop, "Stop", ActionIntent(ACTION_STOP))
        }
        return builder.build()
    }

    private fun ActionIntent(action: String): PendingIntent {
        return PendingIntent.getService(this, action.hashCode(),
            Intent(this, StreamService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE)
    }

    private fun CreateChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Streaming", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Shown while BLStream is streaming to a receiver"
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        engine.RemoveListener(onchanged)
        if (wakelock.isHeld) wakelock.release()
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
