package com.crestpoint.datatracker.files

import java.io.File

/** In-app recycle bin: each item lives in .DataTrackerTrash/<id>/<originalName> with an index file. */
object TrashStore {
    data class Entry(val id: String, val original: String, val deletedAt: Long, val size: Long) {
        val name: String get() = File(original).name
    }

    private val lock = Any()
    private fun dir(): File = FilePaths.trashDir().also { it.mkdirs() }
    private fun index(): File = File(dir(), "index.tsv")
    private fun itemFile(e: Entry): File = File(File(dir(), e.id), e.name)

    private fun readIndex(): List<Entry> {
        val f = index()
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { l ->
            val p = l.split('\t')
            if (p.size < 4) null else Entry(p[0], p[1], p[2].toLongOrNull() ?: 0L, p[3].toLongOrNull() ?: 0L)
        }
    }

    private fun writeIndex(list: List<Entry>) {
        index().writeText(list.joinToString("\n") { "${it.id}\t${it.original.replace('\t', ' ')}\t${it.deletedAt}\t${it.size}" })
    }

    fun list(): List<Entry> = synchronized(lock) {
        readIndex().filter { itemFile(it).exists() }.sortedByDescending { it.deletedAt }
    }

    fun trash(file: File): Boolean = synchronized(lock) {
        val id = "${System.currentTimeMillis()}_${(1000..9999).random()}"
        val holder = File(dir(), id)
        holder.mkdirs()
        val size = FileOps.treeSize(file)
        var ok = file.renameTo(File(holder, file.name))
        if (!ok) {
            val p = FileOps.Progress()
            FileOps.copyTree(file, holder, FileOps.Conflict.REPLACE, p) {}
            ok = p.errors == 0 && File(holder, file.name).exists()
            if (ok) FileOps.deleteTree(file)
        }
        if (ok) {
            val l = readIndex().toMutableList()
            l.add(Entry(id, file.absolutePath, System.currentTimeMillis(), size))
            writeIndex(l)
        } else {
            FileOps.deleteTree(holder)
        }
        ok
    }

    fun restore(e: Entry): Boolean = synchronized(lock) {
        val item = itemFile(e)
        if (!item.exists()) return@synchronized false
        val parent = File(e.original).parentFile ?: FilePaths.internalRoot()
        parent.mkdirs()
        val p = FileOps.Progress()
        FileOps.moveTree(item, parent, FileOps.Conflict.KEEP_BOTH, p) {}
        val ok = p.errors == 0
        if (ok) {
            FileOps.deleteTree(File(dir(), e.id))
            writeIndex(readIndex().filter { it.id != e.id })
        }
        ok
    }

    fun deleteForever(e: Entry) = synchronized(lock) {
        FileOps.deleteTree(File(dir(), e.id))
        writeIndex(readIndex().filter { it.id != e.id })
    }

    fun emptyAll() = synchronized(lock) {
        readIndex().forEach { FileOps.deleteTree(File(dir(), it.id)) }
        writeIndex(emptyList())
    }

    fun purgeOlderThan(days: Int) {
        if (days <= 0) return
        val limit = System.currentTimeMillis() - days * 86_400_000L
        list().filter { it.deletedAt < limit }.forEach { deleteForever(it) }
    }

    fun totalSize(): Long = list().sumOf { it.size }
}
