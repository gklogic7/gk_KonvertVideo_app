package com.example.ffmpeg

import com.example.domain.model.AudioArtworkSelection
import com.example.domain.model.AudioCodecOption
import com.example.domain.model.ContainerFormat
import com.example.domain.model.ConversionConfig
import com.example.domain.model.VideoCodecOption
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoRateControlMode
import java.util.Locale

object FFmpegCommandBuilder {

    fun buildEditableTemplateArgs(
        config: ConversionConfig,
        hardwareThreads: Int = 0
    ): String {
        val args = buildArgsOnly(
            config = config,
            hardwareThreads = hardwareThreads,
            hasArtworkStream = hasActiveEmbeddedArtwork(config),
            hasOverlayStream = hasActiveVideoOverlay(config)
        )
        return args.joinToString(" ") { token ->
            if (token.contains(" ") && !token.startsWith("\"")) "\"$token\"" else token
        }
    }

    fun buildFullPreviewCommand(
        inputName: String,
        outputName: String,
        config: ConversionConfig,
        hardwareThreads: Int = 0,
        artworkPreviewName: String? = null,
        overlayPreviewName: String? = null
    ): String {
        val resolvedArt = if (hasActiveEmbeddedArtwork(config)) {
            artworkPreviewName ?: when (val art = config.audioArtwork) {
                is AudioArtworkSelection.GalleryImage -> "cover_art.jpg"
                is AudioArtworkSelection.StudioIcon -> "${art.icon.id}_cover.jpg"
                AudioArtworkSelection.None -> null
            }
        } else null

        val resolvedOverlay = if (hasActiveVideoOverlay(config)) {
            overlayPreviewName ?: "overlay_layer.png"
        } else null

        val tokens = buildExecutionArguments(
            inputPath = inputName,
            outputPath = outputName,
            config = config,
            hardwareThreads = hardwareThreads,
            artworkPath = resolvedArt,
            overlayPath = resolvedOverlay
        )
        return "ffmpeg " + tokens.joinToString(" ") { token ->
            if (token.contains(" ")) "\"$token\"" else token
        }
    }

    fun hasActiveEmbeddedArtwork(config: ConversionConfig): Boolean {
        return config.isAudioExport &&
            config.container.supportsCoverArt &&
            config.audioArtwork !is AudioArtworkSelection.None
    }

    fun hasActiveVideoOverlay(config: ConversionConfig): Boolean {
        return config.hasActiveOverlays && config.videoCodec != VideoCodecOption.COPY
    }

    fun buildExecutionArguments(
        inputPath: String,
        outputPath: String,
        config: ConversionConfig,
        hardwareThreads: Int = 0,
        artworkPath: String? = null,
        overlayPath: String? = null
    ): Array<String> {
        val embedArtwork = !artworkPath.isNullOrBlank() &&
            config.isAudioExport &&
            config.container.supportsCoverArt
        val burnOverlay = !overlayPath.isNullOrBlank() && hasActiveVideoOverlay(config)

        if (config.useCustomCommand && config.customCommandArgs.isNotBlank()) {
            val rawTokens = tokenizeCommand(config.customCommandArgs)
            val cleaned = if (rawTokens.firstOrNull()?.equals("ffmpeg", ignoreCase = true) == true) {
                rawTokens.drop(1)
            } else {
                rawTokens
            }

            val hasInputPlaceholder = cleaned.any { it.contains("{input}") }
            val hasOutputPlaceholder = cleaned.any { it.contains("{output}") }

            return if (hasInputPlaceholder || hasOutputPlaceholder) {
                cleaned.map { token ->
                    token.replace("{input}", inputPath).replace("{output}", outputPath)
                }.toTypedArray()
            } else {
                buildList {
                    add("-y")
                    add("-i")
                    add(inputPath)
                    if (embedArtwork) {
                        add("-i")
                        add(artworkPath!!)
                    } else if (burnOverlay) {
                        add("-i")
                        add(overlayPath!!)
                    }
                    addAll(cleaned)
                    add(outputPath)
                }.toTypedArray()
            }
        }

        val middleArgs = buildArgsOnly(
            config = config,
            hardwareThreads = hardwareThreads,
            hasArtworkStream = embedArtwork,
            hasOverlayStream = burnOverlay
        )
        return buildList {
            add("-y")
            add("-i")
            add(inputPath)
            if (embedArtwork) {
                add("-i")
                add(artworkPath!!)
            } else if (burnOverlay) {
                add("-i")
                add(overlayPath!!)
            }
            addAll(middleArgs)
            add(outputPath)
        }.toTypedArray()
    }

    private fun buildArgsOnly(
        config: ConversionConfig,
        hardwareThreads: Int,
        hasArtworkStream: Boolean,
        hasOverlayStream: Boolean
    ): List<String> = buildList {
        if (hardwareThreads > 0) {
            add("-threads")
            add(hardwareThreads.toString())
        }

        val startMs = config.trimStartMs?.takeIf { it > 0L }
        val endMs = config.trimEndMs?.takeIf { it > (startMs ?: 0L) }
        if (startMs != null) {
            add("-ss")
            add(String.format(Locale.US, "%.3f", startMs / 1000.0))
        }
        if (endMs != null) {
            add("-to")
            add(String.format(Locale.US, "%.3f", endMs / 1000.0))
        }

        val effectiveAudioOnly = config.isAudioExport

        if (effectiveAudioOnly) {
            if (hasArtworkStream) {
                add("-map")
                add("0:a")
                add("-map")
                add("1:v")
                add("-c:v")
                add("mjpeg")
                add("-disposition:v:0")
                add("attached_pic")
                if (config.container == ContainerFormat.MP3) {
                    add("-id3v2_version")
                    add("3")
                }
            } else {
                add("-vn")
            }
        } else {
            add("-c:v")
            add(config.videoCodec.ffmpegName)

            if (config.videoCodec != VideoCodecOption.COPY) {
                val useTargetBitrate = config.rateControlMode == VideoRateControlMode.BITRATE
                val targetKbps = config.videoBitrateKbps.coerceIn(250, 20000)

                when (config.videoCodec) {
                    VideoCodecOption.H264 -> {
                        add("-preset")
                        add(config.speedPreset)
                        if (useTargetBitrate) {
                            add("-b:v")
                            add("${targetKbps}k")
                            add("-maxrate")
                            add("${(targetKbps * 1.4).toInt()}k")
                            add("-bufsize")
                            add("${targetKbps * 2}k")
                        } else {
                            add("-crf")
                            add(config.crf.toString())
                        }
                        add("-pix_fmt")
                        add("yuv420p")
                    }
                    VideoCodecOption.H265 -> {
                        add("-preset")
                        add(config.speedPreset)
                        if (useTargetBitrate) {
                            add("-b:v")
                            add("${targetKbps}k")
                            add("-maxrate")
                            add("${(targetKbps * 1.4).toInt()}k")
                            add("-bufsize")
                            add("${targetKbps * 2}k")
                        } else {
                            add("-crf")
                            add(config.crf.toString())
                        }
                        add("-tag:v")
                        add("hvc1")
                        add("-pix_fmt")
                        add("yuv420p")
                    }
                    VideoCodecOption.VP9 -> {
                        if (useTargetBitrate) {
                            add("-b:v")
                            add("${targetKbps}k")
                        } else {
                            add("-crf")
                            add(config.crf.coerceIn(15, 50).toString())
                            add("-b:v")
                            add("0")
                        }
                        add("-row-mt")
                        add("1")
                    }
                    VideoCodecOption.VP8 -> {
                        if (useTargetBitrate) {
                            add("-b:v")
                            add("${targetKbps}k")
                        } else {
                            add("-crf")
                            add(config.crf.coerceIn(10, 50).toString())
                            add("-b:v")
                            add("1500k")
                        }
                    }
                    VideoCodecOption.THEORA -> {
                        if (useTargetBitrate) {
                            add("-b:v")
                            add("${targetKbps}k")
                        } else {
                            val qscale = (10 - ((config.crf - 15) / 3.5f)).toInt().coerceIn(1, 10)
                            add("-q:v")
                            add(qscale.toString())
                        }
                    }
                    VideoCodecOption.MPEG4, VideoCodecOption.XVID -> {
                        if (useTargetBitrate) {
                            add("-b:v")
                            add("${targetKbps}k")
                        } else {
                            val qscale = ((config.crf - 10) / 1.5f).toInt().coerceIn(2, 24)
                            add("-q:v")
                            add(qscale.toString())
                        }
                    }
                    VideoCodecOption.WEBP -> {
                        val quality = (100 - (config.crf * 1.8f)).toInt().coerceIn(20, 95)
                        add("-quality")
                        add(quality.toString())
                        add("-loop")
                        add("0")
                    }
                    VideoCodecOption.COPY, VideoCodecOption.NONE -> Unit
                }

                val filters = mutableListOf<String>()
                buildCropFilter(config.cropRegion)?.let { filters.add(it) }
                config.effectiveTargetWidth?.let { targetWidth ->
                    filters.add("scale='min($targetWidth,iw)':-2")
                }
                if (hasOverlayStream) {
                    val baseChain = filters.joinToString(",")
                    val filterComplex = if (baseChain.isNotEmpty()) {
                        "[0:v]$baseChain[base];[1:v][base]scale2ref[ov][base2];[base2][ov]overlay=0:0:format=auto[vout]"
                    } else {
                        "[1:v][0:v]scale2ref[ov][base2];[base2][ov]overlay=0:0:format=auto[vout]"
                    }
                    add("-filter_complex")
                    add(filterComplex)
                    add("-map")
                    add("[vout]")
                    if (config.audioCodec != AudioCodecOption.NONE && config.container != ContainerFormat.WEBP) {
                        add("-map")
                        add("0:a?")
                    }
                } else if (filters.isNotEmpty()) {
                    add("-vf")
                    add(filters.joinToString(","))
                }
                config.fps?.let { targetFps ->
                    add("-r")
                    add(targetFps.toString())
                }
            }
        }

        if (config.audioCodec == AudioCodecOption.NONE || config.container == ContainerFormat.WEBP) {
            add("-an")
        } else {
            add("-c:a")
            add(config.audioCodec.ffmpegName)
            if (config.audioCodec != AudioCodecOption.COPY) {
                when (config.audioCodec) {
                    AudioCodecOption.AMR_NB -> {
                        add("-ar")
                        add("8000")
                        add("-ac")
                        add("1")
                        add("-b:a")
                        add("12.2k")
                    }
                    AudioCodecOption.AMR_WB -> {
                        add("-ar")
                        add("16000")
                        add("-ac")
                        add("1")
                        add("-b:a")
                        add("23.85k")
                    }
                    AudioCodecOption.SPEEX -> {
                        add("-ar")
                        add("16000")
                        add("-b:a")
                        add("${config.audioBitrateKbps.coerceIn(16, 44)}k")
                    }
                    AudioCodecOption.PCM, AudioCodecOption.WAVPACK -> Unit
                    else -> {
                        add("-b:a")
                        add("${config.audioBitrateKbps}k")
                    }
                }
            }
        }

        if (config.container == ContainerFormat.MP4 || config.container == ContainerFormat.M4A) {
            add("-movflags")
            add("+faststart")
        }
    }

    fun buildCropFilter(crop: VideoCropRegion): String? {
        if (crop.isFullFrame) return null
        val x = crop.left.coerceIn(0f, 0.95f)
        val y = crop.top.coerceIn(0f, 0.95f)
        val w = crop.widthFraction.coerceAtMost(1f - x).coerceAtLeast(0.05f)
        val h = crop.heightFraction.coerceAtMost(1f - y).coerceAtLeast(0.05f)
        val wStr = String.format(Locale.US, "%.4f", w)
        val hStr = String.format(Locale.US, "%.4f", h)
        val xStr = String.format(Locale.US, "%.4f", x)
        val yStr = String.format(Locale.US, "%.4f", y)
        return "crop=trunc((iw*$wStr)/2)*2:trunc((ih*$hStr)/2)*2:trunc((iw*$xStr)/2)*2:trunc((ih*$yStr)/2)*2"
    }

    fun tokenizeCommand(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaping = false

        for (ch in command.trim()) {
            when {
                escaping -> {
                    current.append(ch)
                    escaping = false
                }
                ch == '\\' && !inSingleQuote -> {
                    escaping = true
                }
                ch == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                }
                ch == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                }
                ch.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }
}
