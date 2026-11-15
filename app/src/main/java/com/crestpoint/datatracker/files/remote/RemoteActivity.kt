package com.crestpoint.datatracker.files.remote

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileKinds
import com.crestpoint.datatracker.files.FileOps
import com.crestpoint.datatracker.files.FileOpener
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.ui.ActionSheet
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.Palette
import com.crestpoint.datatracker.files.ui.SheetAction
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPSClient
import java.io.File

/** Browse, download, upload and delete on FTP / FTPS servers. */
class RemoteActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var title: TextView
    private lateinit var status: TextView
    private var client: FTPClient? = null
    private var conn: Connection? = null
    private var path = "/"
    private val lock = Any()

    private val uploadPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val c = client ?: return@registerForActivityResult
        if (uris.isEmpty()) return@registerForActivityResult
        status.text = "Uploading ${uris.size} file(s)…"
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                var n = 0
                for (u in uris) {
                    try {
                        var name = "upload"
                        contentResolver.query(u, null, null, null, null)?.use { q ->
                            val i = q.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (i >= 0 && q.moveToFirst()) name = q.getString(i) ?: name
                        }
                        val okOne = synchronized(lock) { contentResolver.openInputStream(u)?.use { c.storeFile(name, it) } ?: false }
                        if (okOne) n++
                    } catch (_: Exception) { }
                }
                n
            }
            UiKit.toast(this@RemoteActivity, "Uploaded $ok file(s)")
            browse(path)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        sv.setBackgroundColor(UiKit.color(this, R.color.surface))
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val pad = UiKit.dp(this, 16)
        content.setPadding(pad, UiKit.dp(this, 38), pad, pad)
        sv.addView(content)
        setContentView(sv)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (client == null) { finish(); return }
                if (path != "/") browse(path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }) else disconnect()
            }
        })
        showConnections()
    }

    override fun onDestroy() { disconnect(false); super.onDestroy() }

    // ------------------------------------------------------- connections

    private fun showConnections() {
        content.removeAllViews()
        content.addView(UiKit.text(this, "🌐 Remote files", 28f, bold = true))
        content.addView(UiKit.text(this, "Connect to an FTP or FTPS server — a PC, NAS, router drive or hosting account.", 12f, R.color.on_surface_muted))
        content.addView(Design.chip(this, "＋ Add server", true) { addDialog() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).setMargins(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 8))
        })
        val list = RemoteStore.list(this)
        if (list.isEmpty()) content.addView(UiKit.text(this, "No servers yet.", 13f, R.color.on_surface_muted))
        for (c in list) {
            val card = UiKit.card(this)
            (card.layoutParams as LinearLayout.LayoutParams).setMargins(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6))
            card.addView(UiKit.text(this, (if (c.secure) "🔒 " else "🌐 ") + c.name, 16f, bold = true))
            card.addView(UiKit.text(this, "${c.user}@${c.host}:${c.port}", 12f, R.color.on_surface_muted))
            card.setOnClickListener { connect(c) }
            card.setOnLongClickListener {
                UiKit.confirm(this, "Remove ${c.name}?", "Only the saved login is removed.", "Remove") { RemoteStore.remove(this, c.name); showConnections() }
                true
            }
            Design.ripple(card)
            content.addView(card)
        }
        content.addView(UiKit.text(this, "Tip: long-press a saved server to remove it.", 11f, R.color.on_surface_muted))
    }

    private fun addDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val p = UiKit.dp(this, 20)
        box.setPadding(p, UiKit.dp(this, 8), p, 0)
        fun field(h: String, pw: Boolean = false, v: String = ""): EditText {
            val e = EditText(this); e.hint = h; e.setSingleLine(true); e.setText(v)
            if (pw) e.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(e); return e
        }
        val name = field("Name (e.g. Home NAS)")
        val host = field("Host or IP")
        val port = field("Port", false, "21")
        val user = field("Username", false, "anonymous")
        val pass = field("Password", true)
        val sec = Switch(this); sec.text = "Use FTPS (encrypted)"; box.addView(sec)
        MaterialAlertDialogBuilder(this).setTitle("Add server").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val h = host.text.toString().trim()
                if (h.isEmpty()) { UiKit.toast(this, "Host is required"); return@setPositiveButton }
                RemoteStore.add(this, Connection(name.text.toString().ifBlank { h }, h, port.text.toString().toIntOrNull() ?: 21,
                    user.text.toString().ifBlank { "anonymous" }, pass.text.toString(), sec.isChecked))
                showConnections()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun connect(c: Connection) {
        content.removeAllViews()
        content.addView(UiKit.text(this, "Connecting to ${c.name}…", 16f))
        lifecycleScope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    val f: FTPClient = if (c.secure) FTPSClient() else FTPClient()
                    f.connectTimeout = 12000
                    f.controlEncoding = "UTF-8"
                    f.connect(c.host, c.port)
                    if (!f.login(c.user, c.pass)) { f.disconnect(); return@withContext "Login failed" }
                    f.enterLocalPassiveMode()
                    f.setFileType(FTP.BINARY_FILE_TYPE)
                    if (f is FTPSClient) { f.execPBSZ(0); f.execPROT("P") }
                    client = f; conn = c
                    null
                } catch (e: Exception) { e.message ?: "Connection failed" }
            }
            if (err != null) { UiKit.toast(this@RemoteActivity, err); showConnections() } else browse("/")
        }
    }

    private fun disconnect(redraw: Boolean = true) {
        val c = client; client = null; conn = null
        if (c != null) Thread { try { c.logout(); c.disconnect() } catch (_: Exception) { } }.start()
        if (redraw) showConnections()
    }

    // ------------------------------------------------------------ browsing

    private fun browse(dir: String) {
        val c = client ?: return
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    synchronized(lock) {
                        if (!c.changeWorkingDirectory(dir)) return@synchronized null
                        c.listFiles().filter { it.name != "." && it.name != ".." }
                            .sortedWith(compareBy<FTPFile>({ !it.isDirectory }, { it.name.lowercase() }))
                    }
                } catch (e: Exception) { null }
            }
            if (res == null) { UiKit.toast(this@RemoteActivity, "Could not open $dir"); return@launch }
            path = dir
            render(res)
        }
    }

    private fun render(files: List<FTPFile>) {
        content.removeAllViews()
        title = UiKit.text(this, conn?.name ?: "Remote", 24f, bold = true)
        content.addView(title)
        content.addView(UiKit.text(this, path, 12f, R.color.on_surface_muted))
        val bar = LinearLayout(this)
        bar.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 6))
        bar.addView(Design.chip(this, "⬆ Up", false) { if (path != "/") browse(path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }) })
        bar.addView(Design.chip(this, "＋ Upload", true) { uploadPicker.launch(arrayOf("*/*")) })
        bar.addView(Design.chip(this, "New folder", false) { newFolder() })
        bar.addView(Design.chip(this, "Disconnect", false) { disconnect() })
        val hs = android.widget.HorizontalScrollView(this); hs.addView(bar)
        content.addView(hs)
        status = UiKit.text(this, "${files.size} item(s)", 12f, R.color.brand_cyan)
        content.addView(status)
        for (f in files) {
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 8))
            val kind = if (f.isDirectory) com.crestpoint.datatracker.files.FileKind.FOLDER else FileKinds.of(File(f.name))
            row.addView(Design.iconTile(this, kind.emoji, Palette.kind(kind), 44, 14, 22f))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(UiKit.dp(this, 12), 0, 0, 0)
            col.addView(UiKit.text(this, f.name, 15f).also { it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
            col.addView(UiKit.text(this, if (f.isDirectory) "Folder" else FormatBytes.human(f.size), 11f, R.color.on_surface_muted))
            row.addView(col, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            Design.ripple(row)
            row.setOnClickListener { if (f.isDirectory) browse((if (path == "/") "" else path) + "/" + f.name) else download(f, true) }
            row.setOnLongClickListener {
                ActionSheet.show(this, f.name, listOf(
                    SheetAction("⬇️", "Download to phone") { download(f, false) },
                    SheetAction("🗑️", "Delete on server", true) {
                        UiKit.confirm(this, "Delete ${f.name}?", "This removes it from the server.", "Delete") { remoteDelete(f) }
                    }
                ))
                true
            }
            content.addView(row)
        }
    }

    private fun newFolder() {
        val c = client ?: return
        UiKit.prompt(this, "Folder name") { n ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { try { synchronized(lock) { c.makeDirectory(n.trim()) } } catch (_: Exception) { false } }
                browse(path)
            }
        }
    }

    private fun remoteDelete(f: FTPFile) {
        val c = client ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try { synchronized(lock) { if (f.isDirectory) c.removeDirectory(f.name) else c.deleteFile(f.name) } } catch (_: Exception) { false }
            }
            browse(path)
        }
    }

    private fun download(f: FTPFile, openAfter: Boolean) {
        val c = client ?: return
        val dir = File(FilePaths.internalRoot(), "Download/Remote")
        status.text = "Downloading ${f.name}…"
        lifecycleScope.launch {
            val out = FileOps.uniqueFile(dir, f.name)
            val ok = withContext(Dispatchers.IO) {
                try {
                    dir.mkdirs()
                    synchronized(lock) { out.outputStream().use { c.retrieveFile(f.name, it) } }
                } catch (e: Exception) { false }
            }
            if (ok) {
                status.text = "Saved to ${out.absolutePath}"
                if (openAfter) FileOpener.open(this@RemoteActivity, out)
            } else { out.delete(); status.text = "Download failed" }
        }
    }
}
