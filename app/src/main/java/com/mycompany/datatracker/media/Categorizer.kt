package com.mycompany.datatracker.media

import java.util.Locale

/** Keyword classification over OCR text + file name + folder. */
object Categorizer {

    private fun has(t: String, vararg words: String) = words.any { it in t }

    fun categorize(ocr: String, name: String, path: String = ""): String {
        val t = "$ocr $name $path".lowercase(Locale.US)
        return when {
            has(t, "invoice", "bill to") -> "Invoices"
            has(
                t, "receipt", "subtotal", "total due", "transfer", "transaction",
                "₦", "ngn", "pos ", "payment successful", "debit alert", "credit alert",
                "amount paid"
            ) -> "Receipts / transfers"
            has(t, "bank statement", "statement of account", "account statement") -> "Bank statements"
            has(
                t, "passport", "id card", "identity", "driver's licen", "drivers licen",
                "voter", "national identification"
            ) -> "ID documents"
            has(t, "qr code", "barcode") -> "QR codes / barcodes"
            has(t, "screenshot", "screencap", "screenshots/") -> "Screenshots"
            has(
                t, "agreement", "contract", "certificate", "curriculum vitae", "resume",
                "terms and conditions", "dear sir", "dear madam", "page 1 of"
            ) -> "Documents / letters"
            ocr.isBlank() -> "Photos without text"
            else -> "Other text content"
        }
    }
}
