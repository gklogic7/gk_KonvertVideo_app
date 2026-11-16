package com.example.ffmpeg

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.example.domain.model.MediaInputItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object FFprobeInspector {

    suspend fun inspectUri(
        context: Context,
        uri: Uri,
        fastLightweightMode: Boolean = false
    ): MediaInputItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "media_file"
        var sizeBytes = 0L

        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }

        val mimeType = runCatching { resolver.getType(uri) }.getOrNull() ?: guessMimeFromName(displayName)

        if (fastLightweightMode) {
            return@withContext inspectWithMetadataRetriever(
                context = context,
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes
            )
        }

        val safPath = runCatching {
            FFmpegKitConfig.getSafParameterForRead(context, uri)
        }.getOrElse { uri.toString() }

        val info = runCatching {
            FFprobeKit.getMediaInformation(safPath)?.mediaInformation
        }.getOrNull()

        if (info == null) {
            return@withContext inspectWithMetadataRetriever(
                context = context,
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes
            )
        }

        val durationSec = info.duration?.toDoubleOrNull() ?: 0.0
        val durationMs = (durationSec * 1000.0).toLong().coerceAtLeast(0L)
        val bitrateKbps = info.bitrate?.toLongOrNull()?.let { it / 1000L }

        val streams = info.streams.orEmpty()
        val videoStream = streams.firstOrNull { it.type?.equals("video", ignoreCase = true) == true }
        val audioStream = streams.firstOrNull { it.type?.equals("audio", ignoreCase = true) == true }

        val width = videoStream?.width?.toInt()
        val height = videoStream?.height?.toInt()
        val fps = parseFrameRate(videoStream?.averageFrameRate ?: videoStream?.realFrameRate)
        val videoCodec = videoStream?.codec
        val audioCodec = audioStream?.codec
        val isAudioOnly = videoStream == null && (audioStream != null || mimeType.startsWith("audio/"))

        MediaInputItem(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            fps = fps,
            bitrateKbps = bitrateKbps,
            isAudioOnly = isAudioOnly,
            isProbing = false
        )
    }

    private fun inspectWithMetadataRetriever(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String,
        sizeBytes: Long
    ): MediaInputItem {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width: Int? = null
        var height: Int? = null
        var bitrateKbps: Long? = null
        var hasVideo = false

        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (hasVideo) {
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            }
            bitrateKbps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()?.let { it / 1000L }
        } catch (_: Throwable) {
            // Safe fallback if container isn't supported by Android MediaMetadataRetriever
        } finally {
            runCatching { retriever.release() }
        }

        val isAudioOnly = !hasVideo && mimeType.startsWith("audio/")

        return MediaInputItem(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            videoCodec = if (hasVideo) mimeType.substringAfter('/').uppercase() else null,
            audioCodec = if (isAudioOnly) mimeType.substringAfter('/').uppercase() else null,
            fps = null,
            bitrateKbps = bitrateKbps,
            isAudioOnly = isAudioOnly,
            isProbing = false
        )
    }

    private fun parseFrameRate(raw: String?): Double? {
        if (raw.isNullOrBlank() || raw == "0/0") return null
        val parts = raw.split("/")
        return if (parts.size == 2) {
            val num = parts[0].toDoubleOrNull() ?: return null
            val den = parts[1].toDoubleOrNull() ?: return null
            if (den == 0.0) null else (num / den)
        } else {
            raw.toDoubleOrNull()
        }
    }

    private fun guessMimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "wv" -> "audio/x-wavpack"
            "amr" -> "audio/amr"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
