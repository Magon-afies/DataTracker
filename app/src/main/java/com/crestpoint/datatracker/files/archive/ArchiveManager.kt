package com.crestpoint.datatracker.files.archive

import com.crestpoint.datatracker.files.FileOps
import com.github.junrar.Archive
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

object ArchiveManager {
    enum class Kind { ZIP, SEVENZ, RAR, TAR, TGZ, TBZ2, TXZ, GZ, BZ2, XZ }
    data class Entry(val path: String, val isDir: Boolean, val size: Long)
    class NeedPassword : Exception("This archive needs a password")
    class WrongPassword : Exception("Wrong password")

    fun kindOf(f: File): Kind? {
        val n = f.name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> Kind.TGZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> Kind.TBZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> Kind.TXZ
            n.endsWith(".tar") -> Kind.TAR
            n.endsWith(".zip") || n.endsWith(".apk") || n.endsWith(".jar") ||
                n.endsWith(".xapk") || n.endsWith(".apks") -> Kind.ZIP
            n.endsWith(".7z") -> Kind.SEVENZ
            n.endsWith(".rar") -> Kind.RAR
            n.endsWith(".gz") -> Kind.GZ
            n.endsWith(".bz2") -> Kind.BZ2
            n.endsWith(".xz") -> Kind.XZ
            else -> null
        }
    }

    fun isArchive(f: File): Boolean = kindOf(f) != null

    /** Name for a new extraction folder: "photos.tar.gz" -> "photos". */
    fun baseName(f: File): String {
        val n = f.name
        val lower = n.lowercase()
        for (s in listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".txz")) {
            if (lower.endsWith(s)) return n.substring(0, n.length - s.length)
        }
        return f.nameWithoutExtension
    }

    private fun zip(f: File, password: String?): ZipFile =
        if (password.isNullOrEmpty()) ZipFile(f) else ZipFile(f, password.toCharArray())

    fun needsPassword(f: File): Boolean =
        kindOf(f) == Kind.ZIP && try { ZipFile(f).isEncrypted } catch (_: Exception) { false }

    // ---------------------------------------------------------------- list

    fun list(f: File, password: String?): List<Entry> {
        val kind = kindOf(f) ?: throw IllegalArgumentException("Unsupported archive type")
        return when (kind) {
            Kind.ZIP -> zip(f, password).fileHeaders.map {
                Entry(it.fileName.trimEnd('/'), it.isDirectory, it.uncompressedSize)
            }
            Kind.SEVENZ -> list7z(f, password)
            Kind.RAR -> listRar(f)
            Kind.TAR, Kind.TGZ, Kind.TBZ2, Kind.TXZ -> listTar(f, kind)
            Kind.GZ, Kind.BZ2, Kind.XZ -> listOf(Entry(singleName(f), false, f.length()))
        }
    }

    private fun singleName(f: File): String = f.nameWithoutExtension.ifEmpty { "output" }

    private fun list7z(f: File, password: String?): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            val sz = if (password.isNullOrEmpty()) SevenZFile(f) else SevenZFile(f, password.toCharArray())
            sz.use { s ->
                var e = s.nextEntry
                while (e != null) {
                    out.add(Entry(e.name.trimEnd('/'), e.isDirectory, e.size))
                    e = s.nextEntry
                }
            }
        } catch (e: PasswordRequiredException) {
            throw NeedPassword()
        }
        return out
    }

    private fun listRar(f: File): List<Entry> {
        val out = ArrayList<Entry>()
        Archive(f).use { a ->
            for (h in a.fileHeaders) {
                out.add(Entry(h.fileName.replace('\\', '/').trimEnd('/'), h.isDirectory, h.fullUnpackSize))
            }
        }
        return out
    }

    private fun tarStream(f: File, kind: Kind): TarArchiveInputStream {
        val raw = BufferedInputStream(FileInputStream(f))
        val s: InputStream = when (kind) {
            Kind.TGZ -> GzipCompressorInputStream(raw)
            Kind.TBZ2 -> BZip2CompressorInputStream(raw)
            Kind.TXZ -> XZCompressorInputStream(raw)
            else -> raw
        }
        return TarArchiveInputStream(s)
    }

    private fun listTar(f: File, kind: Kind): List<Entry> {
        val out = ArrayList<Entry>()
        tarStream(f, kind).use { t ->
            var e: ArchiveEntry? = t.nextEntry
            while (e != null) {
                val n = e.name.trimStart('.', '/').trimEnd('/')
                if (n.isNotEmpty()) out.add(Entry(n, e.isDirectory, e.size))
                e = t.nextEntry
            }
        }
        return out
    }

    // ------------------------------------------------------------- extract

    private fun wanted(name: String, only: Set<String>?): Boolean {
        if (only == null) return true
        val n = name.trimEnd('/')
        return only.any { o -> n == o || n.startsWith("$o/") }
    }

    private fun safeOut(dest: File, name: String): File {
        val out = File(dest, name).canonicalFile
        if (!out.path.startsWith(dest.canonicalPath + File.separator)) {
            throw SecurityException("Blocked unsafe path in archive: $name")
        }
        return out
    }

    private fun writeStream(ins: InputStream, out: File, p: FileOps.Progress, tick: () -> Unit) {
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { os ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                if (p.cancelled) break
                val n = ins.read(buf)
                if (n < 0) break
                os.write(buf, 0, n)
                p.done += n
                tick()
            }
        }
    }

    fun extract(
        f: File, dest: File, password: String?, only: Set<String>?,
        p: FileOps.Progress, tick: () -> Unit
    ) {
        val kind = kindOf(f) ?: throw IllegalArgumentException("Unsupported archive type")
        dest.mkdirs()
        when (kind) {
            Kind.ZIP -> extractZip(f, dest, password, only, p, tick)
            Kind.SEVENZ -> extract7z(f, dest, password, only, p, tick)
            Kind.RAR -> extractRar(f, dest, only, p, tick)
            Kind.TAR, Kind.TGZ, Kind.TBZ2, Kind.TXZ -> extractTar(f, kind, dest, only, p, tick)
            Kind.GZ, Kind.BZ2, Kind.XZ -> {
                val raw = BufferedInputStream(FileInputStream(f))
                val s: InputStream = when (kind) {
                    Kind.GZ -> GzipCompressorInputStream(raw)
                    Kind.BZ2 -> BZip2CompressorInputStream(raw)
                    else -> XZCompressorInputStream(raw)
                }
                s.use { writeStream(it, safeOut(dest, singleName(f)), p, tick) }
                p.items++
            }
        }
    }

    private fun extractZip(f: File, dest: File, password: String?, only: Set<String>?, p: FileOps.Progress, tick: () -> Unit) {
        val z = zip(f, password)
        if (z.isEncrypted && password.isNullOrEmpty()) throw NeedPassword()
        try {
            for (h in z.fileHeaders) {
                if (p.cancelled) break
                if (!wanted(h.fileName, only)) continue
                p.current = h.fileName
                z.extractFile(h, dest.absolutePath)
                p.done += h.uncompressedSize
                p.items++
                tick()
            }
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) throw WrongPassword()
            throw e
        }
    }

    private fun extract7z(f: File, dest: File, password: String?, only: Set<String>?, p: FileOps.Progress, tick: () -> Unit) {
        try {
            val sz = if (password.isNullOrEmpty()) SevenZFile(f) else SevenZFile(f, password.toCharArray())
            sz.use { s ->
                var e = s.nextEntry
                while (e != null && !p.cancelled) {
                    if (wanted(e.name, only)) {
                        p.current = e.name
                        val out = safeOut(dest, e.name)
                        if (e.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { os ->
                                val buf = ByteArray(128 * 1024)
                                while (!p.cancelled) {
                                    val n = s.read(buf)
                                    if (n < 0) break
                                    os.write(buf, 0, n)
                                    p.done += n
                                    tick()
                                }
                            }
                            p.items++
                        }
                    }
                    e = s.nextEntry
                }
            }
        } catch (e: PasswordRequiredException) {
            throw NeedPassword()
        }
    }

    private fun extractRar(f: File, dest: File, only: Set<String>?, p: FileOps.Progress, tick: () -> Unit) {
        Archive(f).use { a ->
            for (h in a.fileHeaders) {
                if (p.cancelled) break
                val name = h.fileName.replace('\\', '/')
                if (!wanted(name, only)) continue
                p.current = name
                val out = safeOut(dest, name)
                if (h.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { os -> a.extractFile(h, os) }
                    p.done += h.fullUnpackSize
                    p.items++
                    tick()
                }
            }
        }
    }

    private fun extractTar(f: File, kind: Kind, dest: File, only: Set<String>?, p: FileOps.Progress, tick: () -> Unit) {
        tarStream(f, kind).use { t ->
            var e: ArchiveEntry? = t.nextEntry
            while (e != null && !p.cancelled) {
                val name = e.name
                val clean = name.trimStart('.', '/')
                val isLink = (e as? TarArchiveEntry)?.let { it.isSymbolicLink || it.isLink } ?: false
                if (clean.isNotEmpty() && !isLink && wanted(clean, only)) {
                    p.current = clean
                    val out = safeOut(dest, clean)
                    if (e.isDirectory) out.mkdirs() else { writeStream(t, out, p, tick); p.items++ }
                }
                e = t.nextEntry
            }
        }
    }

    // -------------------------------------------------------------- create

    fun create(
        sources: List<File>, out: File, format: String, password: String?,
        p: FileOps.Progress, tick: () -> Unit
    ) {
        out.parentFile?.mkdirs()
        when (format) {
            "7z" -> create7z(sources, out, p, tick)
            "tar.gz" -> createTarGz(sources, out, p, tick)
            else -> createZip(sources, out, password, p, tick)
        }
    }

    private fun createZip(sources: List<File>, out: File, password: String?, p: FileOps.Progress, tick: () -> Unit) {
        val z = zip(out, password)
        val params = ZipParameters()
        params.compressionLevel = CompressionLevel.NORMAL
        if (!password.isNullOrEmpty()) {
            params.isEncryptFiles = true
            params.encryptionMethod = EncryptionMethod.AES
            params.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
        for (src in sources) {
            if (p.cancelled) break
            p.current = src.name
            if (src.isDirectory) z.addFolder(src, params) else z.addFile(src, params)
            p.done += FileOps.treeSize(src)
            p.items++
            tick()
        }
    }

    private fun create7z(sources: List<File>, out: File, p: FileOps.Progress, tick: () -> Unit) {
        SevenZOutputFile(out).use { o ->
            for (src in sources) addTo7z(o, src, src.name, p, tick)
        }
    }

    private fun addTo7z(o: SevenZOutputFile, f: File, name: String, p: FileOps.Progress, tick: () -> Unit) {
        if (p.cancelled) return
        p.current = f.name
        val entry = o.createArchiveEntry(f, if (f.isDirectory) "$name/" else name)
        o.putArchiveEntry(entry)
        if (f.isFile) {
            FileInputStream(f).use { ins ->
                val buf = ByteArray(128 * 1024)
                while (!p.cancelled) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    p.done += n
                    tick()
                }
            }
            p.items++
        }
        o.closeArchiveEntry()
        if (f.isDirectory) f.listFiles()?.forEach { addTo7z(o, it, "$name/${it.name}", p, tick) }
    }

    private fun createTarGz(sources: List<File>, out: File, p: FileOps.Progress, tick: () -> Unit) {
        val os = GzipCompressorOutputStream(BufferedOutputStream(FileOutputStream(out)))
        TarArchiveOutputStream(os).use { t ->
            t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            t.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            for (src in sources) addToTar(t, src, src.name, p, tick)
        }
    }

    private fun addToTar(t: TarArchiveOutputStream, f: File, name: String, p: FileOps.Progress, tick: () -> Unit) {
        if (p.cancelled) return
        p.current = f.name
        val e = TarArchiveEntry(f, name)
        t.putArchiveEntry(e)
        if (f.isFile) {
            FileInputStream(f).use { ins ->
                val buf = ByteArray(128 * 1024)
                while (!p.cancelled) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    t.write(buf, 0, n)
                    p.done += n
                    tick()
                }
            }
            p.items++
        }
        t.closeArchiveEntry()
        if (f.isDirectory) f.listFiles()?.forEach { addToTar(t, it, "$name/${it.name}", p, tick) }
    }
}
