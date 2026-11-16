package com.example.ui.util

import java.util.Locale
import kotlin.math.roundToInt

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSec = ms / 1000L
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatCompressionDelta(inputBytes: Long, outputBytes: Long): String {
        if (inputBytes <= 0L || outputBytes <= 0L) return "—"
        val ratio = ((outputBytes.toDouble() - inputBytes.toDouble()) / inputBytes.toDouble()) * 100.0
        val rounded = ratio.roundToInt()
        return if (rounded <= 0) "${rounded}% smaller" else "+${rounded}% size"
    }
}
