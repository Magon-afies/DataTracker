package com.crestpoint.datatracker.files.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.crestpoint.datatracker.R
import java.io.File

/** Background audio player (system MediaPlayer) with notification controls. */
class MusicService : Service() {
    companion object {
        const val A_PLAY = "music.play"
        const val A_TOGGLE = "music.toggle"
        const val A_NEXT = "music.next"
        const val A_PREV = "music.prev"
        const val A_STOP = "music.stop"
        const val A_SEEK = "music.seek"
        const val CH = "music"
        @Volatile var queue: List<File> = emptyList()
        @Volatile var index = 0
        @Volatile var player: MediaPlayer? = null
        @Volatile var playing = false
        @Volatile var shuffle = false
        @Volatile var repeat = false
        val current: File? get() = queue.getOrNull(index)

        fun play(ctx: Context, files: List<File>, start: Int) {
            queue = files; index = start.coerceIn(0, (files.size - 1).coerceAtLeast(0))
            androidx.core.content.ContextCompat.startForegroundService(
                ctx.applicationContext, Intent(ctx, MusicService::class.java).setAction(A_PLAY)
            )
        }
        fun command(ctx: Context, action: String, pos: Int = 0) {
            ctx.applicationContext.startService(Intent(ctx, MusicService::class.java).setAction(action).putExtra("pos", pos))
        }
    }

    private lateinit var am: AudioManager
    private var focus: AudioFocusRequest? = null
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { if (playing) pause() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(NotificationChannel(CH, "Music player", NotificationManager.IMPORTANCE_LOW))
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // always satisfy the foreground-service contract first
        val n = notif()
        if (Build.VERSION.SDK_INT >= 34) startForeground(4401, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(4401, n)
        when (intent?.action) {
            A_PLAY -> startTrack()
            A_TOGGLE -> if (playing) pause() else resume()
            A_NEXT -> next()
            A_PREV -> prev()
            A_SEEK -> player?.seekTo(intent?.getIntExtra("pos", 0) ?: 0)
            A_STOP -> { stopAll(); return START_NOT_STICKY }
        }
        refreshNotif()
        return START_NOT_STICKY
    }

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { c ->
                if (c == AudioManager.AUDIOFOCUS_LOSS || c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { if (playing) pause() }
            }.build()
        focus = req
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun startTrack() {
        val f = current ?: return
        player?.release()
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { if (repeat) startTrack() else next() }
            p.setOnErrorListener { _, _, _ -> next(); true }
            p.prepare()
            player = p
            if (requestFocus()) { p.start(); playing = true }
        } catch (e: Exception) { playing = false; p.release(); player = null }
    }

    private fun pause() { try { player?.pause() } catch (_: Exception) { }; playing = false; refreshNotif() }
    private fun resume() { if (player == null) { startTrack() } else if (requestFocus()) { player?.start(); playing = true } ; refreshNotif() }

    private fun next() {
        if (queue.isEmpty()) return
        index = if (shuffle) queue.indices.random() else (index + 1) % queue.size
        startTrack(); refreshNotif()
    }

    private fun prev() {
        if (queue.isEmpty()) return
        val p = player
        if (p != null && p.currentPosition > 3000) { p.seekTo(0); return }
        index = (index - 1 + queue.size) % queue.size
        startTrack(); refreshNotif()
    }

    private fun stopAll() {
        playing = false
        try { player?.release() } catch (_: Exception) { }
        player = null
        focus?.let { am.abandonAudioFocusRequest(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { unregisterReceiver(noisy) } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null; playing = false
        super.onDestroy()
    }

    private fun pi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, MusicService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notif(): Notification {
        val open = PendingIntent.getActivity(this, 9, Intent(this, MusicActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_datatracker)
            .setContentTitle(current?.nameWithoutExtension ?: "Music")
            .setContentText(if (playing) "Playing" else "Paused")
            .setContentIntent(open).setOngoing(playing).setOnlyAlertOnce(true)
            .addAction(0, "⏮", pi(A_PREV, 1))
            .addAction(0, if (playing) "⏸" else "▶", pi(A_TOGGLE, 2))
            .addAction(0, "⏭", pi(A_NEXT, 3))
            .addAction(0, "✕", pi(A_STOP, 4))
            .build()
    }

    private fun refreshNotif() { getSystemService(NotificationManager::class.java).notify(4401, notif()) }
}
