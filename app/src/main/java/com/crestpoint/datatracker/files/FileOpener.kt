package com.crestpoint.datatracker.files

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.crestpoint.datatracker.files.archive.ArchiveActivity
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.files.viewers.ImageViewerActivity
import com.crestpoint.datatracker.files.viewers.MediaActivity
import com.crestpoint.datatracker.files.viewers.PdfViewerActivity
import com.crestpoint.datatracker.files.viewers.TextEditorActivity
import com.crestpoint.datatracker.util.FormatBytes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.security.MessageDigest

object FileOpener {
    private fun launch(ctx: Context, i: Intent) {
        if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun open(ctx: Context, f: File, siblings: List<File>? = null) {
        FilesPrefs(ctx).addRecent(f.path)
        when (FileKinds.of(f)) {
            FileKind.IMAGE -> {
                val imgs = (siblings ?: listOf(f)).filter { FileKinds.of(it) == FileKind.IMAGE }
                val i = Intent(ctx, ImageViewerActivity::class.java)
                i.putStringArrayListExtra("paths", ArrayList(imgs.map { it.path }))
                i.putExtra("index", imgs.indexOfFirst { it.path == f.path }.coerceAtLeast(0))
                launch(ctx, i)
            }
            FileKind.VIDEO, FileKind.AUDIO ->
                launch(ctx, Intent(ctx, MediaActivity::class.java).putExtra("path", f.path))
            FileKind.TEXT ->
                launch(ctx, Intent(ctx, TextEditorActivity::class.java).putExtra("path", f.path))
            FileKind.ARCHIVE ->
                launch(ctx, Intent(ctx, ArchiveActivity::class.java).putExtra("path", f.path))
            FileKind.APK -> showApkInfo(ctx, f)
            else ->
                if (FileKinds.ext(f) == "pdf") launch(ctx, Intent(ctx, PdfViewerActivity::class.java).putExtra("path", f.path))
                else external(ctx, f)
        }
    }

    fun external(ctx: Context, f: File) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(ctx, f), FileKinds.mime(f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            launch(ctx, Intent.createChooser(view, "Open with"))
        } catch (e: ActivityNotFoundException) {
            UiKit.toast(ctx, "No app found to open this file")
        }
    }

    fun share(ctx: Context, files: List<File>) {
        val list = files.filter { it.isFile }
        if (list.isEmpty()) { UiKit.toast(ctx, "Only files can be shared"); return }
        val i = if (list.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uriFor(ctx, list[0])).setType(FileKinds.mime(list[0]))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(list.map { uriFor(ctx, it) }))
                .setType("*/*")
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(ctx, Intent.createChooser(i, "Share"))
    }

    @Suppress("DEPRECATION")
    fun showApkInfo(ctx: Context, f: File) {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        val info = try { pm.getPackageArchiveInfo(f.absolutePath, flags) } catch (_: Exception) { null }
        if (info == null) { external(ctx, f); return }
        val ai = info.applicationInfo
        val label = if (ai != null) {
            ai.sourceDir = f.absolutePath; ai.publicSourceDir = f.absolutePath
            ai.loadLabel(pm).toString()
        } else f.name
        val sig = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()?.let {
            MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) }
        } ?: "unknown"
        val installed = try { pm.getPackageInfo(info.packageName, 0).versionName } catch (_: Exception) { null }
        val perms = info.requestedPermissions?.map { it.substringAfterLast('.') } ?: emptyList()
        val msg = buildString {
            append("Package: ${info.packageName}\n")
            append("Version: ${info.versionName} (${info.longVersionCode})\n")
            append("Size: ${FormatBytes.human(f.length())}\n")
            if (ai != null) append("Min SDK ${ai.minSdkVersion} · Target ${ai.targetSdkVersion}\n")
            append(if (installed != null) "Installed on this phone: $installed\n" else "Not installed on this phone\n")
            append("Signature SHA-256:\n${sig.take(32)}\n${sig.drop(32)}\n\n")
            append("Permissions (${perms.size}): ${perms.take(25).joinToString(", ")}")
            if (perms.size > 25) append("…")
        }
        MaterialAlertDialogBuilder(ctx).setTitle(label).setMessage(msg)
            .setPositiveButton("Install") { _, _ ->
                val i = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uriFor(ctx, f), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { launch(ctx, i) } catch (e: Exception) { UiKit.toast(ctx, "Cannot start installer") }
            }
            .setNegativeButton("Close", null).show()
    }
}
