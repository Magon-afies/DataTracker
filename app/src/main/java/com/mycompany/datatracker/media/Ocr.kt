package com.mycompany.datatracker.media

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** On-device OCR (ML Kit bundled model, works offline). */
object Ocr {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun read(bitmap: Bitmap?): String {
        if (bitmap == null) return ""
        return suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text -> if (cont.isActive) cont.resume(text.text) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        }
    }
}
