package com.crestpoint.datatracker.files.vault

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Files are AES-256-GCM encrypted with a key held in the Android Keystore; names live in an encrypted index. */
object VaultStore {
    data class Item(val id: String, val name: String, val size: Long, val added: Long)

    private val lock = Any()

    private fun dir(ctx: Context): File = File(ctx.filesDir, "vault").also { it.mkdirs() }
    private fun key(ctx: Context): MasterKey =
        MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private fun enc(ctx: Context, f: File): EncryptedFile =
        EncryptedFile.Builder(ctx.applicationContext, f, key(ctx), EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

    fun list(ctx: Context): List<Item> = synchronized(lock) {
        val f = File(dir(ctx), "index.bin")
        if (!f.exists()) return@synchronized emptyList()
        try {
            val txt = enc(ctx, f).openFileInput().use { it.readBytes().toString(Charsets.UTF_8) }
            val arr = JSONArray(txt)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Item(o.getString("id"), o.getString("name"), o.getLong("size"), o.getLong("added"))
            }.sortedByDescending { it.added }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveIndex(ctx: Context, items: List<Item>) {
        val f = File(dir(ctx), "index.bin")
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("added", it.added)) }
        f.delete()
        enc(ctx, f).openFileOutput().use { it.write(arr.toString().toByteArray(Charsets.UTF_8)) }
    }

    fun addStream(ctx: Context, name: String, ins: InputStream, size: Long): Boolean = synchronized(lock) {
        try {
            val id = UUID.randomUUID().toString()
            val out = File(dir(ctx), "$id.enc")
            enc(ctx, out).openFileOutput().use { os -> ins.copyTo(os) }
            saveIndex(ctx, list(ctx) + Item(id, name, size, System.currentTimeMillis()))
            true
        } catch (e: Exception) { false }
    }

    /** Encrypts a real file into the vault and deletes the original on success. */
    fun addFile(ctx: Context, src: File): Boolean {
        val ok = src.inputStream().use { addStream(ctx, src.name, it, src.length()) }
        if (ok) src.delete()
        return ok
    }

    fun decryptTo(ctx: Context, item: Item, dest: File): Boolean = synchronized(lock) {
        try {
            dest.parentFile?.mkdirs()
            enc(ctx, File(dir(ctx), "${item.id}.enc")).openFileInput().use { ins -> dest.outputStream().use { ins.copyTo(it) } }
            true
        } catch (e: Exception) { dest.delete(); false }
    }

    fun remove(ctx: Context, item: Item) = synchronized(lock) {
        File(dir(ctx), "${item.id}.enc").delete()
        saveIndex(ctx, list(ctx).filter { it.id != item.id })
    }
}
