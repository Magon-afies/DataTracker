package com.crestpoint.datatracker.files.vault

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileKinds
import com.crestpoint.datatracker.files.FileOps
import com.crestpoint.datatracker.files.FileOpener
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.Palette
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Locked + encrypted storage. Opens only after your screen lock (PIN / pattern / fingerprint). */
class VaultActivity : AppCompatActivity() {
    companion object {
        private var pendingImport: List<String> = emptyList()
        fun importFiles(ctx: Context, files: List<File>) {
            val f = files.filter { it.isFile }
            if (f.isEmpty()) { UiKit.toast(ctx, "Pick files (not folders) to lock"); return }
            pendingImport = f.map { it.path }
            ctx.startActivity(Intent(ctx, VaultActivity::class.java))
        }
        private const val RELOCK_MS = 60_000L
    }

    private var unlocked = false
    private var authInFlight = false
    private var leftAt = 0L
    private var items: List<VaultStore.Item> = emptyList()
    private val checked = HashSet<String>()
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView
    private lateinit var content: LinearLayout

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        authInFlight = false
        if (r.resultCode == RESULT_OK) { unlocked = true; showContent() } else finish()
    }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        status.text = "Encrypting ${uris.size} file(s)…"
        lifecycleScope.launch {
            val n = withContext(Dispatchers.IO) {
                var ok = 0
                for (u in uris) {
                    try {
                        var name = "file"
                        contentResolver.query(u, null, null, null, null)?.use { c ->
                            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
                        }
                        contentResolver.openInputStream(u)?.use { if (VaultStore.addStream(this@VaultActivity, name, it, 0L)) ok++ }
                    } catch (_: Exception) { }
                }
                ok
            }
            UiKit.toast(this@VaultActivity, "Locked $n file(s). Originals are still where they were — delete them if you want.")
            reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val sv = ScrollView(this)
        sv.setBackgroundColor(UiKit.color(this, R.color.surface))
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.visibility = android.view.View.GONE
        val pad = UiKit.dp(this, 16)
        content.setPadding(pad, UiKit.dp(this, 38), pad, pad)
        sv.addView(content)
        setContentView(sv)

        content.addView(UiKit.text(this, "🔐 Vault", 28f, bold = true))
        content.addView(UiKit.text(this, "Encrypted with a key that never leaves this phone. Hidden from gallery and other apps.", 12f, R.color.on_surface_muted))
        status = UiKit.text(this, "", 13f, R.color.brand_cyan)
        val bar = LinearLayout(this)
        bar.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 6))
        bar.addView(Design.chip(this, "＋ Add from phone", true) { picker.launch(arrayOf("*/*")) })
        bar.addView(Design.chip(this, "Export", false) { exportSelected() })
        bar.addView(Design.chip(this, "Delete", false) { deleteSelected() })
        val hs = android.widget.HorizontalScrollView(this); hs.addView(bar)
        content.addView(hs)
        content.addView(status)
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        content.addView(listBox)
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - leftAt
        if (!authInFlight && (!unlocked || away > RELOCK_MS)) authenticate() else if (unlocked) showContent()
    }

    override fun onStop() { super.onStop(); leftAt = SystemClock.elapsedRealtime(); cleanTmp() }

    private fun authenticate() {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) {
            UiKit.toast(this, "Set a screen lock (PIN, pattern or fingerprint) to use the Vault")
            finish(); return
        }
        val i = km.createConfirmDeviceCredentialIntent("Unlock Vault", "Confirm your screen lock")
        if (i == null) { finish(); return }
        unlocked = false
        content.visibility = android.view.View.GONE
        authInFlight = true
        authLauncher.launch(i)
    }

    private fun showContent() {
        content.visibility = android.view.View.VISIBLE
        val imp = pendingImport
        if (imp.isNotEmpty()) {
            pendingImport = emptyList()
            status.text = "Locking ${imp.size} file(s)…"
            lifecycleScope.launch {
                val n = withContext(Dispatchers.IO) { imp.count { VaultStore.addFile(this@VaultActivity, File(it)) } }
                UiKit.toast(this@VaultActivity, "Locked $n file(s) and removed the originals")
                reload()
            }
        } else reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            items = withContext(Dispatchers.IO) { VaultStore.list(this@VaultActivity) }
            checked.clear()
            status.text = "${items.size} locked file(s)"
            render()
        }
    }

    private fun render() {
        listBox.removeAllViews()
        if (items.isEmpty()) {
            listBox.addView(UiKit.text(this, "Nothing locked yet.\nUse “＋ Add from phone”, or select files in the Files tab → More → Move to Vault.", 13f, R.color.on_surface_muted)
                .also { it.setPadding(0, UiKit.dp(this, 30), 0, 0) })
            return
        }
        for (it in items) {
            val item = it
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 8))
            val kind = FileKinds.of(File(item.name))
            row.addView(Design.iconTile(this, kind.emoji, Palette.kind(kind), 44, 14, 22f))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(UiKit.dp(this, 12), 0, 0, 0)
            col.addView(UiKit.text(this, item.name, 15f).also { t -> t.maxLines = 1; t.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
            col.addView(UiKit.text(this, if (item.size > 0) FormatBytes.human(item.size) else "Locked file", 11f, R.color.on_surface_muted))
            row.addView(col, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val cb = CheckBox(this)
            cb.setOnCheckedChangeListener { _, on -> if (on) checked.add(item.id) else checked.remove(item.id) }
            row.addView(cb)
            row.setOnClickListener { openItem(item) }
            listBox.addView(row)
        }
    }

    private fun tmpDir() = File(cacheDir, "vault_tmp")

    private fun cleanTmp() { try { FileOps.deleteTree(tmpDir()) } catch (_: Exception) { } }

    private fun openItem(item: VaultStore.Item) {
        status.text = "Decrypting…"
        lifecycleScope.launch {
            val out = File(tmpDir(), item.name)
            val ok = withContext(Dispatchers.IO) { VaultStore.decryptTo(this@VaultActivity, item, out) }
            status.text = "${items.size} locked file(s)"
            if (ok) FileOpener.open(this@VaultActivity, out) else UiKit.toast(this@VaultActivity, "Could not decrypt")
        }
    }

    private fun exportSelected() {
        val sel = items.filter { it.id in checked }
        if (sel.isEmpty()) { UiKit.toast(this, "Tick files to export"); return }
        lifecycleScope.launch {
            val dir = FilePaths.exportDir()
            val n = withContext(Dispatchers.IO) {
                dir.mkdirs()
                sel.count { VaultStore.decryptTo(this@VaultActivity, it, FileOps.uniqueFile(dir, it.name)) }
            }
            UiKit.toast(this@VaultActivity, "Exported $n file(s) to ${dir.absolutePath}")
        }
    }

    private fun deleteSelected() {
        val sel = items.filter { it.id in checked }
        if (sel.isEmpty()) return
        UiKit.confirm(this, "Delete ${sel.size} locked file(s)?", "They will be gone for good.", "Delete") {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { sel.forEach { VaultStore.remove(this@VaultActivity, it) } }
                reload()
            }
        }
    }
}
