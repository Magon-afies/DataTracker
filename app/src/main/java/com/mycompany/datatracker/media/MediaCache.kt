package com.mycompany.datatracker.media

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mycompany.datatracker.data.Db

data class CachedImage(
    val id: Long,
    val name: String,
    val path: String,
    val modified: Long,
    val size: Long,
    val dHash: Long,
    val ocr: String
)

data class CachedDoc(val uri: String, val name: String, val ocr: String)

/** Remembers hashes + OCR text so photos are never re-processed. */
class MediaCache(context: Context) {

    private val db = Db.get(context)

    fun put(img: CachedImage) {
        val cv = ContentValues()
        cv.put("id", img.id)
        cv.put("name", img.name)
        cv.put("path", img.path)
        cv.put("modified", img.modified)
        cv.put("size", img.size)
        cv.put("dhash", img.dHash)
        cv.put("ocr", img.ocr)
        db.writableDatabase.insertWithOnConflict(
            "media_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** id -> modified time for every photo already processed. */
    fun knownModified(): Map<Long, Long> {
        val map = HashMap<Long, Long>()
        db.readableDatabase.rawQuery("SELECT id, modified FROM media_cache", null).use { c ->
            while (c.moveToNext()) map[c.getLong(0)] = c.getLong(1)
        }
        return map
    }

    fun allImages(): List<CachedImage> {
        val out = ArrayList<CachedImage>()
        db.readableDatabase.rawQuery(
            "SELECT id, name, path, modified, size, dhash, ocr FROM media_cache " +
                "ORDER BY modified DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    CachedImage(
                        c.getLong(0), c.getString(1), c.getString(2),
                        c.getLong(3), c.getLong(4), c.getLong(5), c.getString(6)
                    )
                )
            }
        }
        return out
    }

    fun deleteIds(ids: Collection<Long>) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            for (id in ids) w.delete("media_cache", "id = ?", arrayOf(id.toString()))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Drop cache rows for photos that no longer exist. */
    fun pruneMissing(liveIds: Set<Long>) {
        val gone = knownModified().keys.filter { it !in liveIds }
        if (gone.isNotEmpty()) deleteIds(gone)
    }

    fun putDoc(uri: String, name: String, ocr: String) {
        val cv = ContentValues()
        cv.put("uri", uri)
        cv.put("name", name)
        cv.put("ocr", ocr)
        db.writableDatabase.insertWithOnConflict(
            "doc_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun knownDocUris(): Set<String> {
        val set = HashSet<String>()
        db.readableDatabase.rawQuery("SELECT uri FROM doc_cache", null).use { c ->
            while (c.moveToNext()) set.add(c.getString(0))
        }
        return set
    }

    fun allDocs(): List<CachedDoc> {
        val out = ArrayList<CachedDoc>()
        db.readableDatabase.rawQuery("SELECT uri, name, ocr FROM doc_cache", null).use { c ->
            while (c.moveToNext()) out.add(CachedDoc(c.getString(0), c.getString(1), c.getString(2)))
        }
        return out
    }
}
