package com.crestpoint.datatracker.files

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

object FileOps {
    enum class Conflict { REPLACE, SKIP, KEEP_BOTH }

    class Progress {
        @Volatile var done = 0L
        @Volatile var total = 0L
        @Volatile var current = ""
        @Volatile var cancelled = false
        @Volatile var errors = 0
        @Volatile var items = 0
    }

    fun isLink(f: File): Boolean = try { java.nio.file.Files.isSymbolicLink(f.toPath()) } catch (_: Exception) { false }

    fun list(dir: File, showHidden: Boolean, sort: SortMode, asc: Boolean): List<File> {
        val all = dir.listFiles() ?: return emptyList()
        val base = all.filter { showHidden || !it.name.startsWith(".") }
        val nameCmp = Comparator<File> { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
        val cmp: Comparator<File> = when (sort) {
            SortMode.NAME -> nameCmp
            SortMode.SIZE -> Comparator { a, b -> a.length().compareTo(b.length()) }
            SortMode.DATE -> Comparator { a, b -> a.lastModified().compareTo(b.lastModified()) }
            SortMode.TYPE -> Comparator { a, b ->
                val e = a.extension.compareTo(b.extension, ignoreCase = true)
                if (e != 0) e else a.name.compareTo(b.name, ignoreCase = true)
            }
        }
        val dirCmp = if (sort == SortMode.DATE) cmp else nameCmp
        val dirs = base.filter { it.isDirectory }.sortedWith(if (asc) dirCmp else dirCmp.reversed())
        val files = base.filter { !it.isDirectory }.sortedWith(if (asc) cmp else cmp.reversed())
        return dirs + files
    }

    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.').let { if (it <= 0) name.length else it }
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    fun treeSize(f: File): Long {
        if (!f.isDirectory) return f.length()
        if (isLink(f)) return 0L
        var t = 0L
        f.listFiles()?.forEach { t += treeSize(it) }
        return t
    }

    fun deleteTree(f: File): Boolean {
        if (f.isDirectory && !isLink(f)) f.listFiles()?.forEach { deleteTree(it) }
        return f.delete()
    }

    fun isInside(child: File, parent: File): Boolean = try {
        val c = child.canonicalPath
        val p = parent.canonicalPath
        c == p || c.startsWith(p + File.separator)
    } catch (_: Exception) { false }

    private fun sameFile(a: File, b: File): Boolean = try { a.canonicalPath == b.canonicalPath } catch (_: Exception) { false }

    private fun resolveTarget(src: File, dstDir: File, policy: Conflict): File? {
        val t = File(dstDir, src.name)
        if (!t.exists()) return t
        return when (policy) {
            Conflict.SKIP -> null
            Conflict.KEEP_BOTH -> uniqueFile(dstDir, src.name)
            Conflict.REPLACE -> if (sameFile(src, t)) null else t
        }
    }

    fun copyFile(src: File, dst: File, p: Progress, tick: () -> Unit) {
        dst.parentFile?.mkdirs()
        var cancelled = false
        FileInputStream(src).use { ins ->
            FileOutputStream(dst).use { os ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    if (p.cancelled) { cancelled = true; break }
                    val n = ins.read(buf)
                    if (n < 0) break
                    os.write(buf, 0, n)
                    p.done += n
                    tick()
                }
            }
        }
        if (cancelled) dst.delete() else dst.setLastModified(src.lastModified())
    }

    fun copyTree(src: File, dstDir: File, policy: Conflict, p: Progress, tick: () -> Unit): File? {
        if (p.cancelled) return null
        if (src.isDirectory && isInside(dstDir, src)) { p.errors++; return null }
        val t = resolveTarget(src, dstDir, policy) ?: return null
        if (t.exists() && !(src.isDirectory && t.isDirectory)) deleteTree(t)
        p.current = src.name
        try {
            if (src.isDirectory) {
                t.mkdirs()
                src.listFiles()?.forEach { copyTree(it, t, policy, p, tick) }
            } else {
                copyFile(src, t, p, tick)
            }
            p.items++
        } catch (e: Exception) {
            p.errors++
            return null
        }
        return t
    }

    fun moveTree(src: File, dstDir: File, policy: Conflict, p: Progress, tick: () -> Unit) {
        if (p.cancelled) return
        if (src.isDirectory && isInside(dstDir, src)) { p.errors++; return }
        val t = resolveTarget(src, dstDir, policy) ?: return
        p.current = src.name
        if (t.exists()) {
            if (src.isDirectory && t.isDirectory) {
                val before = p.errors
                copyTree(src, dstDir, policy, p, tick)
                if (p.errors == before && !p.cancelled) deleteTree(src)
                return
            }
            deleteTree(t)
        }
        val size = treeSize(src)
        if (src.renameTo(t)) {
            p.done += size; p.items++; tick(); return
        }
        val before = p.errors
        copyTree(src, dstDir, policy, p, tick)
        if (p.errors == before && !p.cancelled) deleteTree(src)
    }

    fun hash(f: File, algo: String, firstBytes: Long = -1L): String {
        val md = MessageDigest.getInstance(algo)
        FileInputStream(f).use { ins ->
            val buf = ByteArray(128 * 1024)
            var left = if (firstBytes < 0) Long.MAX_VALUE else firstBytes
            while (left > 0) {
                val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                md.update(buf, 0, n)
                left -= n
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

object FileWalker {
    /** Iterative walk (no recursion limits). onDir gets each directory and its children. */
    fun walk(
        root: File,
        skip: (File) -> Boolean = { false },
        cancelled: () -> Boolean = { false },
        onDir: (File, Array<File>) -> Unit = { _, _ -> },
        onFile: (File) -> Unit
    ) {
        val stack = ArrayList<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            if (cancelled()) return
            val d = stack.removeAt(stack.size - 1)
            val kids = d.listFiles() ?: continue
            onDir(d, kids)
            for (k in kids) {
                if (skip(k)) continue
                if (k.isDirectory) {
                    if (!FileOps.isLink(k)) stack.add(k)
                } else {
                    onFile(k)
                }
            }
        }
    }
}
