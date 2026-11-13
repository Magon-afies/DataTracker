package com.crestpoint.datatracker.files

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import java.io.File

enum class SortMode { NAME, SIZE, DATE, TYPE }

enum class FileKind(val emoji: String, val label: String) {
    FOLDER("📁", "Folders"),
    IMAGE("🖼️", "Images"),
    VIDEO("🎞️", "Videos"),
    AUDIO("🎵", "Audio"),
    DOC("📄", "Documents"),
    TEXT("📝", "Text & code"),
    ARCHIVE("📦", "Archives"),
    APK("📱", "APKs"),
    OTHER("📎", "Other")
}

object FileKinds {
    private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
    private val videos = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v", "ts")
    private val audio = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "opus", "amr", "wma", "mid")
    private val docs = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "epub", "rtf")
    private val text = setOf(
        "txt", "md", "log", "json", "xml", "csv", "tsv", "html", "htm", "css", "js", "kt", "java", "py",
        "sh", "ini", "conf", "yml", "yaml", "properties", "sql", "gradle", "kts", "c", "h", "cpp", "php"
    )
    private val archives = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "tbz2", "txz", "jar")

    fun ext(f: File): String = f.extension.lowercase()

    fun of(f: File): FileKind {
        if (f.isDirectory) return FileKind.FOLDER
        val e = ext(f)
        return when {
            e == "apk" || e == "xapk" || e == "apks" -> FileKind.APK
            e in images -> FileKind.IMAGE
            e in videos -> FileKind.VIDEO
            e in audio -> FileKind.AUDIO
            e in docs -> FileKind.DOC
            e in text -> FileKind.TEXT
            e in archives -> FileKind.ARCHIVE
            else -> FileKind.OTHER
        }
    }

    fun mime(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(f)) ?: "*/*"
}

object FilePaths {
    fun internalRoot(): File = Environment.getExternalStorageDirectory()
    fun trashDir(): File = File(internalRoot(), ".DataTrackerTrash")
    fun backupDir(): File = File(internalRoot(), "DataTracker/AppBackups")
    fun exportDir(): File = File(internalRoot(), "DataTracker/Exports")

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** Internal storage first, then any SD card / USB drive Android exposes. */
    fun roots(ctx: Context): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        out.add("Internal storage" to internalRoot())
        try {
            ctx.getExternalFilesDirs(null).filterNotNull().drop(1).forEach { d ->
                val s = d.absolutePath
                val i = s.indexOf("/Android/data")
                if (i > 0) {
                    val r = File(s.substring(0, i))
                    if (r.exists()) out.add((if (out.size == 1) "SD card / USB" else "SD card / USB ${out.size}") to r)
                }
            }
        } catch (_: Exception) { }
        return out
    }

    fun skipForScan(root: File): (File) -> Boolean {
        val android = File(root, "Android").absolutePath
        return { f -> f.absolutePath == android || f.name == ".DataTrackerTrash" }
    }
}

class FilesPrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("files_prefs", Context.MODE_PRIVATE)

    var grid: Boolean
        get() = sp.getBoolean("grid", false)
        set(v) { sp.edit().putBoolean("grid", v).apply() }
    var sort: SortMode
        get() = try { SortMode.valueOf(sp.getString("sort", "NAME") ?: "NAME") } catch (_: Exception) { SortMode.NAME }
        set(v) { sp.edit().putString("sort", v.name).apply() }
    var asc: Boolean
        get() = sp.getBoolean("asc", true)
        set(v) { sp.edit().putBoolean("asc", v).apply() }
    var showHidden: Boolean
        get() = sp.getBoolean("hidden", false)
        set(v) { sp.edit().putBoolean("hidden", v).apply() }
    var trashDays: Int
        get() = sp.getInt("trash_days", 30)
        set(v) { sp.edit().putInt("trash_days", v).apply() }

    private fun getList(key: String): MutableList<String> =
        (sp.getString(key, "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()
    private fun putList(key: String, l: List<String>) { sp.edit().putString(key, l.joinToString("\n")).apply() }

    fun bookmarks(): List<String> = getList("bookmarks")
    fun addBookmark(path: String) { val l = getList("bookmarks"); if (path !in l) { l.add(path); putList("bookmarks", l) } }
    fun removeBookmark(path: String) { val l = getList("bookmarks"); l.remove(path); putList("bookmarks", l) }

    fun recents(): List<String> = getList("recents")
    fun addRecent(path: String) {
        val l = getList("recents"); l.remove(path); l.add(0, path)
        putList("recents", l.take(40))
    }

    fun mediaPos(path: String): Int = sp.getInt("pos_" + path.hashCode(), 0)
    fun setMediaPos(path: String, ms: Int) { sp.edit().putInt("pos_" + path.hashCode(), ms).apply() }
}

object FileClipboard {
    var files: List<File> = emptyList()
    var move: Boolean = false
    fun clear() { files = emptyList() }
}
