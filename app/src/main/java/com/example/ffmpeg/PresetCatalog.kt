package com.example.ffmpeg

import com.example.domain.model.AudioCodecOption
import com.example.domain.model.ContainerFormat
import com.example.domain.model.ConversionPreset
import com.example.domain.model.ResolutionOption
import com.example.domain.model.VideoCodecOption

object PresetCatalog {
    val builtInPresets: List<ConversionPreset> = listOf(
        ConversionPreset(
            id = "mp4_h264",
            name = "MP4 H.264 Universal",
            subtitle = "Best compatibility across phones, tablets & web",
            badge = "H.264 + AAC",
            container = ContainerFormat.MP4,
            videoCodec = VideoCodecOption.H264,
            audioCodec = AudioCodecOption.AAC,
            resolution = ResolutionOption.ORIGINAL,
            crf = 22,
            audioBitrateKbps = 192
        ),
        ConversionPreset(
            id = "mp4_h265",
            name = "HEVC H.265 Compact",
            subtitle = "Up to 45% smaller files at crisp visual quality",
            badge = "H.265 + AAC",
            container = ContainerFormat.MP4,
            videoCodec = VideoCodecOption.H265,
            audioCodec = AudioCodecOption.AAC,
            resolution = ResolutionOption.ORIGINAL,
            crf = 26,
            audioBitrateKbps = 160
        ),
        ConversionPreset(
            id = "webm_vp9",
            name = "WebM VP9 + Opus",
            subtitle = "Open royalty-free HD web video format",
            badge = "VP9 + OPUS",
            container = ContainerFormat.WEBM,
            videoCodec = VideoCodecOption.VP9,
            audioCodec = AudioCodecOption.OPUS,
            resolution = ResolutionOption.FHD_1080,
            crf = 31,
            audioBitrateKbps = 128
        ),
        ConversionPreset(
            id = "webm_vp8",
            name = "WebM VP8 Fast",
            subtitle = "High-speed VP8 + Vorbis transcode",
            badge = "VP8 + VORBIS",
            container = ContainerFormat.WEBM,
            videoCodec = VideoCodecOption.VP8,
            audioCodec = AudioCodecOption.VORBIS,
            resolution = ResolutionOption.HD_720,
            crf = 28,
            audioBitrateKbps = 128
        ),
        ConversionPreset(
            id = "ogg_theora",
            name = "OGG Theora + Vorbis",
            subtitle = "Open Xiph.org container with Theora video",
            badge = "THEORA + VORBIS",
            container = ContainerFormat.OGG,
            videoCodec = VideoCodecOption.THEORA,
            audioCodec = AudioCodecOption.VORBIS,
            resolution = ResolutionOption.HD_720,
            crf = 24,
            audioBitrateKbps = 160
        ),
        ConversionPreset(
            id = "3gp_mobile",
            name = "3GP Legacy Mobile",
            subtitle = "MPEG-4 + AMR-WB ultra-compact cellular video",
            badge = "MPEG4 + AMR-WB",
            container = ContainerFormat.THREE_GP,
            videoCodec = VideoCodecOption.MPEG4,
            audioCodec = AudioCodecOption.AMR_WB,
            resolution = ResolutionOption.MOBILE_360,
            crf = 26,
            audioBitrateKbps = 24
        ),
        ConversionPreset(
            id = "mp3_audio",
            name = "Extract MP3 320k",
            subtitle = "High-bitrate LAME MP3 audio extraction",
            badge = "MP3 LAME",
            container = ContainerFormat.MP3,
            videoCodec = VideoCodecOption.NONE,
            audioCodec = AudioCodecOption.MP3,
            audioBitrateKbps = 320
        ),
        ConversionPreset(
            id = "opus_audio",
            name = "Opus Studio Audio",
            subtitle = "Transparent next-gen audio at 128 kbps",
            badge = "OPUS",
            container = ContainerFormat.OPUS,
            videoCodec = VideoCodecOption.NONE,
            audioCodec = AudioCodecOption.OPUS,
            audioBitrateKbps = 128
        ),
        ConversionPreset(
            id = "aac_audio",
            name = "AAC / M4A Audio",
            subtitle = "Clean AAC audio stream extraction",
            badge = "AAC",
            container = ContainerFormat.M4A,
            videoCodec = VideoCodecOption.NONE,
            audioCodec = AudioCodecOption.AAC,
            audioBitrateKbps = 192
        ),
        ConversionPreset(
            id = "wavpack_audio",
            name = "WavPack Lossless",
            subtitle = "Archival lossless WavPack (.wv) audio",
            badge = "WAVPACK",
            container = ContainerFormat.WAVPACK,
            videoCodec = VideoCodecOption.NONE,
            audioCodec = AudioCodecOption.WAVPACK,
            audioBitrateKbps = 320
        ),
        ConversionPreset(
            id = "webp_anim",
            name = "Animated WebP Clip",
            subtitle = "High-efficiency animated WebP sequence",
            badge = "LIBWEBP",
            container = ContainerFormat.WEBP,
            videoCodec = VideoCodecOption.WEBP,
            audioCodec = AudioCodecOption.NONE,
            resolution = ResolutionOption.SD_480,
            crf = 24,
            fps = 15
        )
    )

    fun compatibleVideoCodecs(container: ContainerFormat): List<VideoCodecOption> {
        if (container.isAudioOnly) return listOf(VideoCodecOption.NONE)
        return when (container) {
            ContainerFormat.MP4 -> listOf(
                VideoCodecOption.H264,
                VideoCodecOption.H265,
                VideoCodecOption.MPEG4,
                VideoCodecOption.COPY
            )
            ContainerFormat.MKV -> listOf(
                VideoCodecOption.H264,
                VideoCodecOption.H265,
                VideoCodecOption.VP9,
                VideoCodecOption.VP8,
                VideoCodecOption.MPEG4,
                VideoCodecOption.XVID,
                VideoCodecOption.THEORA,
                VideoCodecOption.COPY
            )
            ContainerFormat.WEBM -> listOf(
                VideoCodecOption.VP9,
                VideoCodecOption.VP8,
                VideoCodecOption.COPY
            )
            ContainerFormat.OGG -> listOf(
                VideoCodecOption.THEORA,
                VideoCodecOption.COPY,
                VideoCodecOption.NONE
            )
            ContainerFormat.THREE_GP -> listOf(
                VideoCodecOption.H264,
                VideoCodecOption.MPEG4,
                VideoCodecOption.COPY
            )
            ContainerFormat.AVI -> listOf(
                VideoCodecOption.XVID,
                VideoCodecOption.MPEG4,
                VideoCodecOption.H264,
                VideoCodecOption.COPY
            )
            ContainerFormat.WEBP -> listOf(VideoCodecOption.WEBP)
            else -> listOf(VideoCodecOption.NONE)
        }
    }

    fun compatibleAudioCodecs(container: ContainerFormat): List<AudioCodecOption> {
        return when (container) {
            ContainerFormat.MP4, ContainerFormat.M4A -> listOf(
                AudioCodecOption.AAC,
                AudioCodecOption.FDK_AAC,
                AudioCodecOption.MP3,
                AudioCodecOption.OPUS,
                AudioCodecOption.COPY,
                AudioCodecOption.NONE
            )
            ContainerFormat.MKV -> listOf(
                AudioCodecOption.AAC,
                AudioCodecOption.OPUS,
                AudioCodecOption.VORBIS,
                AudioCodecOption.MP3,
                AudioCodecOption.SPEEX,
                AudioCodecOption.WAVPACK,
                AudioCodecOption.COPY,
                AudioCodecOption.NONE
            )
            ContainerFormat.WEBM -> listOf(
                AudioCodecOption.OPUS,
                AudioCodecOption.VORBIS,
                AudioCodecOption.COPY,
                AudioCodecOption.NONE
            )
            ContainerFormat.OGG, ContainerFormat.OGG_AUDIO -> listOf(
                AudioCodecOption.VORBIS,
                AudioCodecOption.OPUS,
                AudioCodecOption.SPEEX,
                AudioCodecOption.COPY,
                AudioCodecOption.NONE
            )
            ContainerFormat.THREE_GP -> listOf(
                AudioCodecOption.AAC,
                AudioCodecOption.AMR_WB,
                AudioCodecOption.AMR_NB,
                AudioCodecOption.NONE
            )
            ContainerFormat.AVI -> listOf(
                AudioCodecOption.MP3,
                AudioCodecOption.AAC,
                AudioCodecOption.PCM,
                AudioCodecOption.COPY,
                AudioCodecOption.NONE
            )
            ContainerFormat.WEBP -> listOf(AudioCodecOption.NONE)
            ContainerFormat.MP3 -> listOf(AudioCodecOption.MP3)
            ContainerFormat.AAC -> listOf(AudioCodecOption.AAC, AudioCodecOption.FDK_AAC)
            ContainerFormat.OPUS -> listOf(AudioCodecOption.OPUS)
            ContainerFormat.WAV -> listOf(AudioCodecOption.PCM)
            ContainerFormat.WAVPACK -> listOf(AudioCodecOption.WAVPACK)
            ContainerFormat.AMR -> listOf(AudioCodecOption.AMR_NB, AudioCodecOption.AMR_WB)
        }
    }
}
