package com.crestpoint.datatracker.util

import java.util.Locale

object FormatBytes {
    fun human(bytes: Long): String {
        val kb = bytes / 1024.0
        return when {
            bytes < 1024 -> "$bytes B"
            kb < 1024 -> String.format(Locale.US, "%.1f KB", kb)
            kb < 1024 * 1024 -> String.format(Locale.US, "%.1f MB", kb / 1024)
            else -> String.format(Locale.US, "%.2f GB", kb / 1024 / 1024)
        }
    }
}
