package com.crestpoint.datatracker.data

import java.util.Locale

data class DataPlan(
    val id: Long,
    val sizeBytes: Long,
    val carrier: String,
    val startMs: Long,
    val expiryMs: Long,
    val priceNaira: Double,
    val firedMask: Int = 0
) {
    val nairaPerByte: Double
        get() = if (sizeBytes > 0L) priceNaira / sizeBytes.toDouble() else 0.0

    val nairaPerGb: Double
        get() = nairaPerByte * BYTES_PER_GB

    fun nairaFor(bytes: Long): Double = nairaPerByte * bytes.coerceAtLeast(0L).toDouble()

    companion object {
        private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
    }
}

object Naira {
    private const val SYMBOL = "\u20A6"

    fun format(value: Double): String {
        val v = if (value.isNaN() || value < 0.0) 0.0 else value
        return when {
            v >= 100.0 -> SYMBOL + String.format(Locale.US, "%,.0f", v)
            v >= 1.0 -> SYMBOL + String.format(Locale.US, "%,.1f", v)
            else -> SYMBOL + String.format(Locale.US, "%,.2f", v)
        }
    }
}
