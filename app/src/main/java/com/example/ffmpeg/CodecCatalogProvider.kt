package com.example.ffmpeg

import android.os.Build
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.LogRedirectionStrategy
import com.example.domain.model.AudioCodecOption
import com.example.domain.model.CodecCatalogEntry
import com.example.domain.model.CodecCategory
import com.example.domain.model.ContainerFormat
import com.example.domain.model.VideoCodecOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RuntimeEngineInfo(
    val ffmpegVersion: String,
    val supportedAbis: List<String>,
    val compiledEncodersCount: Int,
    val compiledFormatsCount: Int,
    val catalog: List<CodecCatalogEntry>
)

object CodecCatalogProvider {

    private fun executeQuietQuery(vararg args: String): String {
        return runCatching {
            FFmpegKitConfig.setLogRedirectionStrategy(LogRedirectionStrategy.NEVER_PRINT_LOGS)
            val buffer = StringBuilder()
            val session = FFmpegSession.create(
                args,
                null,
                { log ->
                    val msg = log?.message
                    if (!msg.isNullOrEmpty()) {
                        synchronized(buffer) {
                            buffer.append(msg)
                        }
                    }
                },
                null,
                LogRedirectionStrategy.NEVER_PRINT_LOGS
            )
            FFmpegKitConfig.ffmpegExecute(session)
            val captured = synchronized(buffer) { buffer.toString() }
            if (captured.isNotBlank()) {
                captured
            } else {
                session.allLogs?.joinToString(separator = "") { it.message.orEmpty() }.orEmpty()
            }
        }.getOrDefault("")
    }

    suspend fun inspectRuntimeCapabilities(): RuntimeEngineInfo = withContext(Dispatchers.IO) {
        val ffmpegVersion = try {
            FFmpegKitConfig.setLogRedirectionStrategy(LogRedirectionStrategy.NEVER_PRINT_LOGS)
            FFmpegKitConfig.getFFmpegVersion().orEmpty().ifBlank { "FFmpeg 8.x (Built-in)" }
        } catch (_: Throwable) {
            "FFmpeg Built-in"
        }

        val encodersOutput = executeQuietQuery("-hide_banner", "-encoders")
        val formatsOutput = executeQuietQuery("-hide_banner", "-formats")
        val protocolsOutput = executeQuietQuery("-hide_banner", "-protocols")
        val filtersOutput = executeQuietQuery("-hide_banner", "-filters")

        val encoderIds = parseNamesFromTable(encodersOutput, prefixLength = 8)
        val formatIds = parseNamesFromTable(formatsOutput, prefixLength = 4)
        val protocolText = protocolsOutput.lowercase()
        val filterText = filtersOutput.lowercase()

        val highlighted = listOf(
            CodecCatalogEntry(
                name = "H.264 / AVC (x264)",
                ffmpegId = "libx264",
                category = CodecCategory.VIDEO,
                description = "High-speed MPEG-4 AVC encoder for universal MP4/MKV/3GP playback.",
                isAvailableInBinary = "libx264" in encoderIds || "h264" in encoderIds,
                supportsEncode = "libx264" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.MP4,
                suggestedVideoCodec = VideoCodecOption.H264
            ),
            CodecCatalogEntry(
                name = "H.265 / HEVC (x265)",
                ffmpegId = "libx265",
                category = CodecCategory.VIDEO,
                description = "High Efficiency Video Coding for 4K and compact mobile archiving.",
                isAvailableInBinary = "libx265" in encoderIds || "hevc" in encoderIds,
                supportsEncode = "libx265" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.MP4,
                suggestedVideoCodec = VideoCodecOption.H265
            ),
            CodecCatalogEntry(
                name = "VP9 (libvpx-vp9)",
                ffmpegId = "libvpx-vp9",
                category = CodecCategory.VIDEO,
                description = "Open royalty-free WebM video codec with row multithreading.",
                isAvailableInBinary = "libvpx-vp9" in encoderIds,
                supportsEncode = "libvpx-vp9" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.WEBM,
                suggestedVideoCodec = VideoCodecOption.VP9
            ),
            CodecCatalogEntry(
                name = "VP8 (libvpx)",
                ffmpegId = "libvpx",
                category = CodecCategory.VIDEO,
                description = "Fast WebM VP8 encoding for broad browser and Android compatibility.",
                isAvailableInBinary = "libvpx" in encoderIds,
                supportsEncode = "libvpx" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.WEBM,
                suggestedVideoCodec = VideoCodecOption.VP8
            ),
            CodecCatalogEntry(
                name = "Theora (libtheora)",
                ffmpegId = "libtheora",
                category = CodecCategory.VIDEO,
                description = "Open Xiph.org video codec paired with Vorbis inside OGG containers.",
                isAvailableInBinary = "libtheora" in encoderIds,
                supportsEncode = "libtheora" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.OGG,
                suggestedVideoCodec = VideoCodecOption.THEORA
            ),
            CodecCatalogEntry(
                name = "Xvid (libxvid)",
                ffmpegId = "libxvid",
                category = CodecCategory.VIDEO,
                description = "Classic MPEG-4 ASP encoder for AVI and standalone media players.",
                isAvailableInBinary = "libxvid" in encoderIds,
                supportsEncode = "libxvid" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.AVI,
                suggestedVideoCodec = VideoCodecOption.XVID
            ),
            CodecCatalogEntry(
                name = "MPEG-4 Part 2",
                ffmpegId = "mpeg4",
                category = CodecCategory.VIDEO,
                description = "Fast MPEG-4 video encoder suited for 3GP and legacy hardware.",
                isAvailableInBinary = "mpeg4" in encoderIds,
                supportsEncode = "mpeg4" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.THREE_GP,
                suggestedVideoCodec = VideoCodecOption.MPEG4
            ),
            CodecCatalogEntry(
                name = "WebP (libwebp)",
                ffmpegId = "libwebp",
                category = CodecCategory.VIDEO,
                description = "Animated and still WebP image encoding directly from video clips.",
                isAvailableInBinary = "libwebp" in encoderIds || "libwebp_anim" in encoderIds,
                supportsEncode = "libwebp" in encoderIds || "libwebp_anim" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.WEBP,
                suggestedVideoCodec = VideoCodecOption.WEBP
            ),
            CodecCatalogEntry(
                name = "MP3 (libmp3lame)",
                ffmpegId = "libmp3lame",
                category = CodecCategory.AUDIO,
                description = "LAME MP3 audio encoder supporting CBR and VBR up to 320 kbps.",
                isAvailableInBinary = "libmp3lame" in encoderIds,
                supportsEncode = "libmp3lame" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.MP3,
                suggestedAudioCodec = AudioCodecOption.MP3
            ),
            CodecCatalogEntry(
                name = "AAC (Native & fdk_aac)",
                ffmpegId = if ("libfdk_aac" in encoderIds) "libfdk_aac" else "aac",
                category = CodecCategory.AUDIO,
                description = "Advanced Audio Coding for MP4, M4A, and streaming workflows.",
                isAvailableInBinary = "aac" in encoderIds || "libfdk_aac" in encoderIds,
                supportsEncode = "aac" in encoderIds || "libfdk_aac" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.M4A,
                suggestedAudioCodec = AudioCodecOption.AAC
            ),
            CodecCatalogEntry(
                name = "Opus (libopus)",
                ffmpegId = "libopus",
                category = CodecCategory.AUDIO,
                description = "State-of-the-art low-latency audio codec for music and speech.",
                isAvailableInBinary = "libopus" in encoderIds,
                supportsEncode = "libopus" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.OPUS,
                suggestedAudioCodec = AudioCodecOption.OPUS
            ),
            CodecCatalogEntry(
                name = "Vorbis (libvorbis)",
                ffmpegId = "libvorbis",
                category = CodecCategory.AUDIO,
                description = "Open-source perceptual audio codec for OGG and WebM.",
                isAvailableInBinary = "libvorbis" in encoderIds || "vorbis" in encoderIds,
                supportsEncode = "libvorbis" in encoderIds || "vorbis" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.OGG_AUDIO,
                suggestedAudioCodec = AudioCodecOption.VORBIS
            ),
            CodecCatalogEntry(
                name = "Speex (libspeex)",
                ffmpegId = "libspeex",
                category = CodecCategory.AUDIO,
                description = "Patent-free voice codec tuned for speech compression.",
                isAvailableInBinary = "libspeex" in encoderIds,
                supportsEncode = "libspeex" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.OGG_AUDIO,
                suggestedAudioCodec = AudioCodecOption.SPEEX
            ),
            CodecCatalogEntry(
                name = "AMR-WB (libvo_amrwbenc)",
                ffmpegId = "libvo_amrwbenc",
                category = CodecCategory.AUDIO,
                description = "Adaptive Multi-Rate Wideband speech encoder for 3GP and AMR.",
                isAvailableInBinary = "libvo_amrwbenc" in encoderIds,
                supportsEncode = "libvo_amrwbenc" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.THREE_GP,
                suggestedAudioCodec = AudioCodecOption.AMR_WB
            ),
            CodecCatalogEntry(
                name = "AMR-NB (libopencore_amrnb)",
                ffmpegId = "libopencore_amrnb",
                category = CodecCategory.AUDIO,
                description = "Adaptive Multi-Rate Narrowband telephony audio encoder.",
                isAvailableInBinary = "libopencore_amrnb" in encoderIds,
                supportsEncode = "libopencore_amrnb" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.AMR,
                suggestedAudioCodec = AudioCodecOption.AMR_NB
            ),
            CodecCatalogEntry(
                name = "WavPack (libwavpack)",
                ffmpegId = "wavpack",
                category = CodecCategory.AUDIO,
                description = "Hybrid/lossless archival audio compression (.wv container).",
                isAvailableInBinary = "wavpack" in encoderIds || "libwavpack" in encoderIds,
                supportsEncode = "wavpack" in encoderIds || "libwavpack" in encoderIds,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.WAVPACK,
                suggestedAudioCodec = AudioCodecOption.WAVPACK
            ),
            CodecCatalogEntry(
                name = "OGG / Matroska / MP4 / 3GP",
                ffmpegId = "mp4 / matroska / ogg / 3gp",
                category = CodecCategory.CONTAINER,
                description = "Full container muxing and demuxing across MPEG-4, MKV, OGG, WebM, AVI, and 3GP.",
                isAvailableInBinary = "mp4" in formatIds && "ogg" in formatIds && "3gp" in formatIds,
                supportsEncode = true,
                supportsDecode = true,
                suggestedContainer = ContainerFormat.MP4
            ),
            CodecCatalogEntry(
                name = "SoX Resampler (libsox / soxr)",
                ffmpegId = "aresample=resampler=soxr",
                category = CodecCategory.PROTOCOL,
                description = "High-precision audio sample rate conversion filter.",
                isAvailableInBinary = "aresample" in filterText || "soxr" in filterText,
                supportsEncode = true,
                supportsDecode = true
            ),
            CodecCatalogEntry(
                name = "RTMP / RTMPS (librtmp)",
                ffmpegId = "rtmp",
                category = CodecCategory.PROTOCOL,
                description = "Real-Time Messaging Protocol support compiled into FFmpeg.",
                isAvailableInBinary = "rtmp" in protocolText,
                supportsEncode = true,
                supportsDecode = true
            )
        )

        RuntimeEngineInfo(
            ffmpegVersion = ffmpegVersion,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            compiledEncodersCount = encoderIds.size.coerceAtLeast(highlighted.count { it.isAvailableInBinary }),
            compiledFormatsCount = formatIds.size.coerceAtLeast(20),
            catalog = highlighted
        )
    }

    internal fun parseNamesFromTable(tableOutput: String, prefixLength: Int): Set<String> {
        val names = mutableSetOf<String>()
        val encoderRowRegex = Regex("""^\s*[VASD\.FXB]{4,7}\s+([A-Za-z0-9_,\-]+)""")
        val formatRowRegex = Regex("""^\s*[DE]{1,2}\s+([A-Za-z0-9_,\-]+)""")

        tableOutput.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() ||
                trimmed.startsWith("-") ||
                trimmed.startsWith("Encoders:") ||
                trimmed.startsWith("File formats:") ||
                trimmed.contains("=")
            ) {
                return@forEach
            }

            val regexMatch = if (prefixLength >= 6) {
                encoderRowRegex.find(rawLine)
            } else {
                formatRowRegex.find(rawLine)
            }

            val token = when {
                regexMatch != null -> regexMatch.groupValues[1]
                trimmed.length <= 2 && trimmed.all { it in "VASDFXBED." } -> ""
                trimmed.startsWith("(") -> ""
                rawLine.length > prefixLength && rawLine.take(prefixLength).isNotBlank() &&
                    rawLine.take(prefixLength).trim().all { it in "VASDFXBED." } -> {
                    rawLine.drop(prefixLength).trimStart().substringBefore(' ').trim()
                }
                else -> ""
            }

            if (token.isNotEmpty()) {
                token.split(',').forEach { part ->
                    val clean = part.trim().lowercase()
                    if (clean.isNotBlank()) names.add(clean)
                }
            }
        }
        return names
    }
}
