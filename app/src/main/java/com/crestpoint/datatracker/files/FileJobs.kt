package com.crestpoint.datatracker.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.archive.ArchiveManager
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

sealed class FileJob {
    data class Copy(val sources: List<File>, val dest: File, val policy: FileOps.Conflict, val move: Boolean) : FileJob()
    data class Delete(val files: List<File>, val permanent: Boolean) : FileJob()
    data class Zip(val sources: List<File>, val out: File, val format: String, val password: String?) : FileJob()
    data class Extract(val archive: File, val dest: File, val password: String?, val only: Set<String>?) : FileJob()
}

object FileJobs {
    val queue = ConcurrentLinkedQueue<FileJob>()
    @Volatile var current: FileOps.Progress? = null
    val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun submit(ctx: Context, job: FileJob) {
        val app = ctx.applicationContext
        queue.add(job)
        ContextCompat.startForegroundService(app, Intent(app, FileOperationService::class.java))
    }

    fun cancelCurrent() { current?.cancelled = true }

    fun notifyDone(msg: String) { main.post { listeners.forEach { it(msg) } } }
}

/** Runs copy / move / delete / zip / extract jobs in the background with a progress notification. */
class FileOperationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var running = false
    private var lastNotif = 0L
    private var jobStart = 0L

    companion object {
        const val CH = "file_ops"
        const val ACTION_CANCEL = "com.crestpoint.datatracker.files.CANCEL"
        const val NOTIF_ID = 4101
        const val RESULT_ID = 4102
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            FileJobs.cancelCurrent()
            return START_NOT_STICKY
        }
        ensureChannel()
        val n = build("Preparing…", "", 0, true)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
        var alreadyRunning = false
        synchronized(lock) {
            if (running) alreadyRunning = true else running = true
        }
        if (!alreadyRunning) scope.launch { loop() }
        return START_NOT_STICKY
    }

    private fun loop() {
        while (true) {
            val job = FileJobs.queue.poll()
            if (job == null) {
                var finished = false
                synchronized(lock) {
                    if (FileJobs.queue.isEmpty()) { running = false; finished = true }
                }
                if (finished) break else continue
            }
            val msg = try { runJob(job) } catch (e: Throwable) { "Failed: ${e.message ?: e.javaClass.simpleName}" }
            FileJobs.current = null
            FileJobs.notifyDone(msg)
            postResult(msg)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runJob(job: FileJob): String {
        val p = FileOps.Progress()
        FileJobs.current = p
        jobStart = SystemClock.elapsedRealtime()
        val tick = { throttledUpdate(p) }
        return when (job) {
            is FileJob.Copy -> {
                p.total = job.sources.sumOf { FileOps.treeSize(it) }
                for (s in job.sources) {
                    if (job.move) FileOps.moveTree(s, job.dest, job.policy, p, tick)
                    else FileOps.copyTree(s, job.dest, job.policy, p, tick)
                }
                val verb = if (job.move) "Moved" else "Copied"
                summary(verb, p)
            }
            is FileJob.Delete -> {
                p.total = job.files.size.toLong()
                var ok = 0
                for (f in job.files) {
                    if (p.cancelled) break
                    p.current = f.name
                    val done = if (job.permanent) FileOps.deleteTree(f) else TrashStore.trash(f)
                    if (done) ok++ else p.errors++
                    p.done++
                    tick()
                }
                (if (job.permanent) "Deleted $ok item(s)" else "Moved $ok item(s) to Recycle Bin") +
                    (if (p.errors > 0) " · ${p.errors} failed" else "")
            }
            is FileJob.Zip -> {
                p.total = job.sources.sumOf { FileOps.treeSize(it) }
                ArchiveManager.create(job.sources, job.out, job.format, job.password, p, tick)
                if (p.cancelled) { job.out.delete(); "Cancelled" } else "Created ${job.out.name} (${FormatBytes.human(job.out.length())})"
            }
            is FileJob.Extract -> {
                try {
                    p.total = ArchiveManager.list(job.archive, job.password).sumOf { it.size.coerceAtLeast(0L) }
                    ArchiveManager.extract(job.archive, job.dest, job.password, job.only, p, tick)
                    if (p.cancelled) "Cancelled" else "Extracted ${p.items} file(s) to ${job.dest.name}"
                } catch (e: ArchiveManager.NeedPassword) {
                    "This archive needs a password"
                } catch (e: ArchiveManager.WrongPassword) {
                    "Wrong password"
                }
            }
        }
    }

    private fun summary(verb: String, p: FileOps.Progress): String =
        if (p.cancelled) "Cancelled"
        else "$verb ${p.items} item(s)" + (if (p.errors > 0) " · ${p.errors} failed" else "")

    private fun throttledUpdate(p: FileOps.Progress) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotif < 400) return
        lastNotif = now
        val pct = if (p.total > 0) ((p.done * 100) / p.total).toInt().coerceIn(0, 100) else 0
        val secs = ((now - jobStart) / 1000.0).coerceAtLeast(0.5)
        val speed = p.done / secs
        val eta = if (speed > 1 && p.total > p.done) ((p.total - p.done) / speed).toLong() else -1L
        val line = FormatBytes.human(speed.toLong()) + "/s" + (if (eta >= 0) " · ETA ${eta / 60}:${"%02d".format(eta % 60)}" else "")
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, build(p.current.ifEmpty { "Working…" }, line, pct, p.total <= 0))
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "File operations", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun build(title: String, text: String, pct: Int, indeterminate: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, 0,
            Intent(this, FileOperationService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_datatracker)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun postResult(msg: String) {
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_datatracker)
            .setContentTitle("Files")
            .setContentText(msg)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(RESULT_ID, n)
    }
}
