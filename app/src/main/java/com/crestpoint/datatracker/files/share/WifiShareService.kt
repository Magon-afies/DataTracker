package com.crestpoint.datatracker.files.share

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
import androidx.core.app.NotificationCompat
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileKinds
import com.crestpoint.datatracker.files.FileOps
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Tiny PIN-protected web server so you can browse, download and upload files from a PC browser on the same Wi-Fi. */
class WifiShareService : Service() {
    companion object {
        const val ACTION_STOP = "com.crestpoint.datatracker.share.STOP"
        const val CH = "wifi_share"
        @Volatile var running = false
        @Volatile var url = ""
        @Volatile var pin = ""
        @Volatile var rootPath = ""
        @Volatile var lastActivity = 0L
        private const val IDLE_MS = 15 * 60_000L

        fun localIp(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        } catch (_: Exception) { null }

        fun start(ctx: Context, root: File) {
            val i = Intent(ctx, WifiShareService::class.java).putExtra("root", root.absolutePath)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, WifiShareService::class.java).setAction(ACTION_STOP))
        }
    }

    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private var failures = 0
    private lateinit var root: File

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        root = File(intent?.getStringExtra("root") ?: "/sdcard/Download")
        ensureChannel()
        val n = notification("Starting…")
        if (Build.VERSION.SDK_INT >= 34) startForeground(4201, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(4201, n)
        val sock = try { ServerSocket(8080) } catch (_: Exception) { try { ServerSocket(0) } catch (_: Exception) { null } }
        val ip = localIp()
        if (sock == null || ip == null) { shutdown(); return START_NOT_STICKY }
        server = sock
        pin = (100000..999999).random().toString()
        rootPath = root.absolutePath
        url = "http://$ip:${sock.localPort}/$pin/"
        lastActivity = System.currentTimeMillis()
        running = true
        getSystemService(NotificationManager::class.java).notify(4201, notification(url))
        pool.execute { acceptLoop(sock) }
        pool.execute { watchdog() }
        return START_NOT_STICKY
    }

    private fun watchdog() {
        while (running) {
            try { Thread.sleep(30_000) } catch (_: InterruptedException) { return }
            if (System.currentTimeMillis() - lastActivity > IDLE_MS) { shutdown(); return }
        }
    }

    private fun shutdown() {
        running = false; url = ""
        try { server?.close() } catch (_: Exception) { }
        pool.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { running = false; try { server?.close() } catch (_: Exception) { }; super.onDestroy() }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(NotificationChannel(CH, "Wi-Fi share", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, WifiShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CH).setSmallIcon(R.drawable.ic_stat_datatracker)
            .setContentTitle("Wi-Fi share is on").setContentText(text).setOngoing(true)
            .addAction(0, "Stop", stop).build()
    }

    // ------------------------------------------------------------ server

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            try {
                val c = s.accept()
                pool.execute { try { handle(c) } catch (_: Exception) { } }
            } catch (_: Exception) { return }
        }
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun respond(out: OutputStream, code: Int, status: String, type: String, body: ByteArray, extra: String = "") {
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n$extra\r\n"
        out.write(head.toByteArray()); out.write(body); out.flush()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun handle(sock: Socket) {
        sock.use { c ->
            c.soTimeout = 60_000
            val ins = BufferedInputStream(c.getInputStream())
            val out = BufferedOutputStream(c.getOutputStream())
            val req = readLine(ins) ?: return
            val parts = req.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            var target = parts[1]
            val headers = HashMap<String, String>()
            while (true) {
                val l = readLine(ins) ?: break
                if (l.isEmpty()) break
                val i = l.indexOf(':')
                if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            val q = target.indexOf('?')
            if (q >= 0) target = target.substring(0, q)
            val segs = target.split('/').filter { it.isNotEmpty() }.map { try { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { it } }
            if (segs.isEmpty() || !MessageDigest.isEqual(segs[0].toByteArray(), pin.toByteArray())) {
                failures++
                respond(out, 403, "Forbidden", "text/plain", "Forbidden".toByteArray())
                if (failures >= 20) shutdown()
                return
            }
            lastActivity = System.currentTimeMillis()
            val rel = segs.drop(1)
            if (rel.any { it == ".." || it.contains('/') || it.contains('\\') }) { respond(out, 400, "Bad Request", "text/plain", "Bad path".toByteArray()); return }
            val file = File(root, rel.joinToString("/"))
            val canon = try { file.canonicalPath } catch (_: Exception) { "" }
            if (!(canon == root.canonicalPath || canon.startsWith(root.canonicalPath + File.separator))) {
                respond(out, 403, "Forbidden", "text/plain", "Forbidden".toByteArray()); return
            }
            when (method) {
                "GET", "HEAD" -> if (file.isDirectory) listing(out, file, rel) else if (file.isFile) sendFile(out, file, headers["range"], method == "HEAD")
                    else respond(out, 404, "Not Found", "text/plain", "Not found".toByteArray())
                "PUT" -> upload(ins, out, file, headers["content-length"]?.toLongOrNull())
                else -> respond(out, 405, "Method Not Allowed", "text/plain", "No".toByteArray())
            }
        }
    }

    private fun listing(out: OutputStream, dir: File, rel: List<String>) {
        val items = FileOps.list(dir, false, com.crestpoint.datatracker.files.SortMode.NAME, true)
        val sb = StringBuilder()
        sb.append("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Data Tracker Files</title><style>")
        sb.append("body{font-family:system-ui,sans-serif;background:#0F1020;color:#F7F5FF;margin:0;padding:24px;max-width:860px;margin:auto}")
        sb.append("h1{font-size:22px}a{color:#26C6DA;text-decoration:none}.r{display:flex;justify-content:space-between;padding:12px 14px;margin:6px 0;background:#191A2D;border-radius:12px}")
        sb.append(".m{color:#B8B5C9;font-size:13px}#drop{border:2px dashed #7657F6;border-radius:16px;padding:22px;text-align:center;margin:16px 0;color:#B8B5C9}</style></head><body>")
        sb.append("<h1>📂 /").append(esc(rel.joinToString("/"))).append("</h1>")
        if (rel.isNotEmpty()) sb.append("<div class='r'><a href='../'>⬆ Up</a></div>")
        for (f in items) {
            val n = esc(f.name) + if (f.isDirectory) "/" else ""
            val href = enc(f.name) + if (f.isDirectory) "/" else ""
            val kind = FileKinds.of(f).emoji
            sb.append("<div class='r'><a href='").append(href).append("'>").append(kind).append(' ').append(n).append("</a><span class='m'>")
            sb.append(if (f.isFile) com.crestpoint.datatracker.util.FormatBytes.human(f.length()) else "").append("</span></div>")
        }
        sb.append("<div id='drop'>Drop files here or <input type='file' id='f' multiple></div><div id='s' class='m'></div>")
        sb.append("<script>const s=document.getElementById('s');async function up(files){for(const f of files){s.textContent='Uploading '+f.name+'…';")
        sb.append("const r=await fetch(location.pathname+encodeURIComponent(f.name),{method:'PUT',body:f});s.textContent=r.ok?'Uploaded '+f.name:'Failed: '+f.name}location.reload()}")
        sb.append("document.getElementById('f').onchange=e=>up(e.target.files);const d=document.getElementById('drop');")
        sb.append("d.ondragover=e=>{e.preventDefault()};d.ondrop=e=>{e.preventDefault();up(e.dataTransfer.files)}</script></body></html>")
        respond(out, 200, "OK", "text/html; charset=utf-8", sb.toString().toByteArray())
    }

    private fun sendFile(out: OutputStream, f: File, range: String?, headOnly: Boolean) {
        val len = f.length()
        var start = 0L
        var end = len - 1
        var partial = false
        if (range != null && range.startsWith("bytes=")) {
            val r = range.removePrefix("bytes=").split("-")
            start = r[0].toLongOrNull() ?: 0L
            end = r.getOrNull(1)?.toLongOrNull() ?: (len - 1)
            if (start in 0 until len && end >= start) { partial = true; end = minOf(end, len - 1) } else { start = 0; end = len - 1 }
        }
        val size = end - start + 1
        val name = enc(f.name)
        val head = StringBuilder()
        head.append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        head.append("Content-Type: ${FileKinds.mime(f)}\r\nContent-Length: $size\r\nAccept-Ranges: bytes\r\n")
        if (partial) head.append("Content-Range: bytes $start-$end/$len\r\n")
        head.append("Content-Disposition: inline; filename*=UTF-8''$name\r\nConnection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        if (!headOnly) {
            f.inputStream().use { ins ->
                ins.skip(start)
                val buf = ByteArray(64 * 1024)
                var left = size
                while (left > 0) {
                    val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n); left -= n
                    lastActivity = System.currentTimeMillis()
                }
            }
        }
        out.flush()
    }

    private fun upload(ins: InputStream, out: OutputStream, target: File, length: Long?) {
        val dir = target.parentFile
        if (length == null || dir == null || !dir.isDirectory || target.name.isEmpty()) {
            respond(out, 400, "Bad Request", "text/plain", "Bad upload".toByteArray()); return
        }
        val dest = FileOps.uniqueFile(dir, target.name)
        dest.outputStream().use { os ->
            val buf = ByteArray(64 * 1024)
            var left = length
            while (left > 0) {
                val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                os.write(buf, 0, n); left -= n
                lastActivity = System.currentTimeMillis()
            }
        }
        respond(out, 201, "Created", "text/plain", "OK".toByteArray())
    }
}
