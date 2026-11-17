package com.crestpoint.datatracker.files.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.crestpoint.datatracker.R
import java.io.File

/**
 * Background music on Media3 ExoPlayer: instant pause/resume (the activity talks to the player directly),
 * non-blocking loading, proper audio focus, a wake lock while playing and a sleep timer.
 */
@OptIn(UnstableApi::class)
class MusicService : Service() {
    companion object {
        const val A_PLAY = "music.play"
        const val A_TOGGLE = "music.toggle"
        const val A_NEXT = "music.next"
        const val A_PREV = "music.prev"
        const val A_STOP = "music.stop"
        const val CH = "music"

        @Volatile var player: ExoPlayer? = null
        @Volatile var queue: List<File> = emptyList()
        @Volatile private var startIndex = 0
        @Volatile var sleepAt = 0L

        val index: Int get() = player?.currentMediaItemIndex ?: startIndex
        val current: File? get() = queue.getOrNull(index)
        val playing: Boolean get() { val p = player; return p != null && p.playWhenReady && p.playbackState != Player.STATE_ENDED }

        fun play(ctx: Context, files: List<File>, start: Int) {
            queue = files
            startIndex = start.coerceIn(0, (files.size - 1).coerceAtLeast(0))
            ContextCompat.startForegroundService(ctx.applicationContext, Intent(ctx, MusicService::class.java).setAction(A_PLAY))
        }

        /** Instant: talks to the player directly, no service round-trip. */
        fun toggle(ctx: Context) {
            val p = player
            if (p == null) { if (queue.isNotEmpty()) play(ctx, queue, startIndex); return }
            if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) p.pause()
            else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0L); p.play() }
        }

        fun next() { player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() else it.seekTo(0, 0L) } }
        fun prev() { player?.seekToPrevious() }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, MusicService::class.java).setAction(A_STOP)) }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val sleepCheck = object : Runnable {
        override fun run() {
            if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) { player?.pause(); sleepAt = 0L }
            handler.postDelayed(this, 4000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(NotificationChannel(CH, "Music player", NotificationManager.IMPORTANCE_LOW))
        handler.post(sleepCheck)
    }

    private fun startFg() {
        val n = notif()
        if (Build.VERSION.SDK_INT >= 34) startForeground(4401, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(4401, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        when (intent?.action) {
            A_PLAY -> setup()
            A_TOGGLE -> toggle(this)
            A_NEXT -> next()
            A_PREV -> prev()
            A_STOP -> { stopAll(); return START_NOT_STICKY }
        }
        return START_NOT_STICKY
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { refreshNotif() }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { refreshNotif() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { refreshNotif() }
            override fun onPlayerError(error: PlaybackException) {
                val pl = player ?: return
                if (pl.hasNextMediaItem()) { pl.seekToNextMediaItem(); pl.prepare(); pl.play() } else pl.pause()
            }
        })
        player = p
        return p
    }

    private fun setup() {
        if (queue.isEmpty()) return
        val p = ensurePlayer()
        p.setMediaItems(queue.map { MediaItem.fromUri(Uri.fromFile(it)) }, startIndex, 0L)
        p.prepare()
        p.play()
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        try { player?.release() } catch (_: Exception) { }
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { player?.release() } catch (_: Exception) { }
        player = null
        super.onDestroy()
    }

    private fun pi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, MusicService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notif(): Notification {
        val open = PendingIntent.getActivity(this, 9, Intent(this, MusicActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val on = playing
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_datatracker)
            .setContentTitle(current?.nameWithoutExtension ?: "Music")
            .setContentText(if (on) "Playing" else "Paused")
            .setContentIntent(open).setOngoing(on).setOnlyAlertOnce(true).setSilent(true)
            .addAction(R.drawable.ic_fx_prev, "Previous", pi(A_PREV, 1))
            .addAction(if (on) R.drawable.ic_fx_pause else R.drawable.ic_fx_play, if (on) "Pause" else "Play", pi(A_TOGGLE, 2))
            .addAction(R.drawable.ic_fx_next, "Next", pi(A_NEXT, 3))
            .addAction(R.drawable.ic_fx_close, "Stop", pi(A_STOP, 4))
            .build()
    }

    private fun refreshNotif() { getSystemService(NotificationManager::class.java).notify(4401, notif()) }
}
