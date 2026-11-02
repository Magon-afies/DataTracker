package com.mycompany.datatracker.media

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class MediaGroup(
    val title: String,
    val count: Int,
    val sampleUri: Uri?,
    val sampleName: String,
    val snippet: String,
    val mime: String = "image/*",
    /** For duplicate groups: ids of the extra copies (everything but the kept one). */
    val extraIds: List<Long> = emptyList()
)

/** Builds the list shown in the Media tab from the cache. */
object MediaGrouper {

    /** Two photos are "the same photo" if their hashes differ in <= 6 bits. */
    private const val SIMILAR_HASH_THRESHOLD = 6

    private fun imageUri(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    fun build(images: List<CachedImage>, docs: List<CachedDoc>): List<MediaGroup> {
        val groups = ArrayList<MediaGroup>()

        // ---- 1) duplicates / near-duplicates (union-find over hash distance)
        val parent = IntArray(images.size) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) {
                parent[root] = parent[parent[root]]
                root = parent[root]
            }
            return root
        }

        for (i in images.indices) {
            val hi = images[i].dHash
            if (hi == 0L) continue
            for (j in i + 1 until images.size) {
                val hj = images[j].dHash
                if (hj == 0L) continue
                if (java.lang.Long.bitCount(hi xor hj) <= SIMILAR_HASH_THRESHOLD) {
                    parent[find(i)] = find(j)
                }
            }
        }

        images.indices.groupBy { find(it) }.values
            .filter { it.size >= 2 }
            .sortedByDescending { it.size }
            .forEach { cluster ->
                val members = cluster.map { images[it] }
                val keep = members.maxByOrNull { it.size } ?: members.first()
                val extras = members.filter { it.id != keep.id }
                val identical = members.all { it.dHash == keep.dHash && it.size == keep.size }
                groups.add(
                    MediaGroup(
                        title = (if (identical) "Identical" else "Similar") +
                            " photos — ${members.size} copies",
                        count = members.size,
                        sampleUri = imageUri(keep.id),
                        sampleName = keep.name,
                        snippet = "Also: " + extras.take(5).joinToString(", ") { it.name },
                        extraIds = extras.map { it.id }
                    )
                )
            }

        // ---- 2) OCR categories for photos
        images.groupBy { Categorizer.categorize(it.ocr, it.name, it.path) }
            .entries
            .sortedByDescending { it.value.size }
            .forEach { (category, list) ->
                val first = list.first()
                groups.add(
                    MediaGroup(
                        title = category,
                        count = list.size,
                        sampleUri = imageUri(first.id),
                        sampleName = first.name,
                        snippet = first.ocr.take(200)
                    )
                )
            }

        // ---- 3) PDFs the user scanned
        docs.groupBy { Categorizer.categorize(it.ocr, it.name) }
            .entries
            .sortedByDescending { it.value.size }
            .forEach { (category, list) ->
                val first = list.first()
                groups.add(
                    MediaGroup(
                        title = "PDFs — $category",
                        count = list.size,
                        sampleUri = Uri.parse(first.uri),
                        sampleName = first.name,
                        snippet = first.ocr.take(200),
                        mime = "application/pdf"
                    )
                )
            }

        return groups
    }
}
