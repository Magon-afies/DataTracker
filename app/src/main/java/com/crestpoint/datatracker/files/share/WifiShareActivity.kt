package com.crestpoint.datatracker.files.share

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.UiKit
import java.io.File

/** Start/stop the Wi-Fi file server and show the address + PIN to type into a PC browser. */
class WifiShareActivity : AppCompatActivity() {
    private lateinit var urlText: TextView
    private lateinit var info: TextView
    private lateinit var toggle: com.google.android.material.button.MaterialButton
    private lateinit var fullAccess: Switch
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable { override fun run() { render(); handler.postDelayed(this, 1000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(this, R.color.surface))
        val pad = UiKit.dp(this, 18)
        root.setPadding(pad, UiKit.dp(this, 40), pad, pad)
        root.addView(UiKit.text(this, "📡 Wi-Fi share", 28f, bold = true))
        root.addView(UiKit.text(this, "Open the address below in a browser on a computer connected to the same Wi-Fi. You can browse, download and upload files. It switches off after 15 idle minutes.", 13f, R.color.on_surface_muted))

        val card = UiKit.card(this)
        urlText = UiKit.text(this, "Not running", 17f, bold = true)
        urlText.setTextIsSelectable(true)
        info = UiKit.text(this, "", 13f, R.color.on_surface_muted)
        card.addView(urlText); card.addView(info)
        root.addView(card)

        fullAccess = Switch(this)
        fullAccess.text = "Share ALL storage (otherwise only the Download folder)"
        fullAccess.setTextColor(UiKit.color(this, R.color.on_surface))
        root.addView(fullAccess)
        root.addView(UiKit.text(this, "Anyone who has the address AND PIN on your Wi-Fi can read your files. Don't use this on public Wi-Fi.", 11f, R.color.health_warm)
            .also { it.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 10)) })

        toggle = UiKit.button(this, "Start sharing") {
            if (WifiShareService.running) WifiShareService.stop(this) else {
                if (WifiShareService.localIp() == null) { UiKit.toast(this, "Connect to Wi-Fi (or turn on hotspot) first"); return@button }
                val rootDir = if (fullAccess.isChecked) FilePaths.internalRoot() else File(FilePaths.internalRoot(), "Download")
                rootDir.mkdirs()
                WifiShareService.start(this, rootDir)
            }
        }
        root.addView(toggle)
        root.addView(Design.chip(this, "Copy address", false) {
            if (WifiShareService.running) {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", WifiShareService.url))
                UiKit.toast(this, "Copied")
            }
        }.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = UiKit.dp(this, 10) })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); handler.post(refresher) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresher) }

    private fun render() {
        if (WifiShareService.running && WifiShareService.url.isNotEmpty()) {
            urlText.text = WifiShareService.url
            info.text = "PIN is part of the address (${WifiShareService.pin}). Sharing: ${WifiShareService.rootPath}"
            toggle.text = "Stop sharing"
        } else {
            urlText.text = "Not running"
            info.text = ""
            toggle.text = "Start sharing"
        }
    }
}
