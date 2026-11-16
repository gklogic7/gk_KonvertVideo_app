package com.example.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable

enum class ContainerFormat(
    val id: String,
    val extension: String,
    val label: String,
    val mimeType: String,
    val isAudioOnly: Boolean,
    val supportsVideo: Boolean = !isAudioOnly,
    val supportsCoverArt: Boolean = false
) {
    MP4("mp4", "mp4", "MP4 (MPEG-4)", "video/mp4", false, supportsCoverArt = true),
    MKV("matroska", "mkv", "MKV (Matroska)", "video/x-matroska", false, supportsCoverArt = true),
    WEBM("webm", "webm", "WebM (VP8/VP9)", "video/webm", false),
    OGG("ogg", "ogg", "OGG (Theora/Vorbis)", "video/ogg", false),
    THREE_GP("3gp", "3gp", "3GP (Mobile)", "video/3gpp", false),
    AVI("avi", "avi", "AVI (Xvid/MPEG4)", "video/x-msvideo", false),
    WEBP("webp", "webp", "WebP (Animated)", "image/webp", false),
    MP3("mp3", "mp3", "MP3 (LAME Audio)", "audio/mpeg", true, supportsCoverArt = true),
    AAC("adts", "aac", "AAC Audio", "audio/aac", true),
    M4A("ipod", "m4a", "M4A (AAC)", "audio/mp4", true, supportsCoverArt = true),
    OPUS("opus", "opus", "Opus Audio", "audio/opus", true),
    OGG_AUDIO("ogg", "ogg", "OGG Vorbis Audio", "audio/ogg", true),
    WAV("wav", "wav", "WAV (PCM)", "audio/wav", true),
    WAVPACK("wv", "wv", "WavPack (.wv)", "audio/x-wavpack", true),
    AMR("amr", "amr", "AMR (Narrow/Wide)", "audio/amr", true)
}

enum class VideoCodecOption(
    val id: String,
    val label: String,
    val ffmpegName: String,
    val description: String
) {
    H264("h264", "H.264 (libx264)", "libx264", "Universal fast AVC encoding"),
    H265("h265", "H.265 / HEVC (libx265)", "libx265", "High-efficiency compression"),
    VP9("vp9", "VP9 (libvpx-vp9)", "libvpx-vp9", "Open royalty-free WebM HD"),
    VP8("vp8", "VP8 (libvpx)", "libvpx", "Fast WebM compatibility"),
    THEORA("theora", "Theora (libtheora)", "libtheora", "Open OGG video codec"),
    MPEG4("mpeg4", "MPEG-4 Part 2", "mpeg4", "Legacy hardware friendly"),
    XVID("xvid", "Xvid (libxvid)", "libxvid", "AVI & classic MPEG-4 ASP"),
    WEBP("webp", "WebP (libwebp)", "libwebp", "Animated WebP image stream"),
    COPY("copy", "Stream Copy (No Re-encode)", "copy", "Instant lossless remux"),
    NONE("none", "Disable Video (Audio Only)", "none", "Strip video track")
}

enum class AudioCodecOption(
    val id: String,
    val label: String,
    val ffmpegName: String,
    val description: String
) {
    AAC("aac", "AAC (Native FFmpeg)", "aac", "Standard MP4/M4A/3GP audio"),
    FDK_AAC("libfdk_aac", "AAC (libfdk_aac)", "libfdk_aac", "High-profile AAC if compiled"),
    MP3("mp3", "MP3 (libmp3lame)", "libmp3lame", "Universal LAME MP3"),
    OPUS("opus", "Opus (libopus)", "libopus", "Ultra-efficient modern audio"),
    VORBIS("vorbis", "Vorbis (libvorbis)", "libvorbis", "Open OGG/WebM audio"),
    SPEEX("speex", "Speex (libspeex)", "libspeex", "Voice compression codec"),
    AMR_WB("amr_wb", "AMR-WB (libvo_amrwbenc)", "libvo_amrwbenc", "Wideband 3GP speech"),
    AMR_NB("amr_nb", "AMR-NB (libopencore_amrnb)", "libopencore_amrnb", "Narrowband 3GP speech"),
    WAVPACK("wavpack", "WavPack (wavpack)", "wavpack", "Lossless/hybrid audio"),
    PCM("pcm", "PCM 16-bit (WAV)", "pcm_s16le", "Uncompressed studio WAV"),
    COPY("copy", "Stream Copy (No Re-encode)", "copy", "Instant lossless audio copy"),
    NONE("none", "Mute (No Audio)", "none", "Strip audio track")
}

enum class ResolutionOption(
    val label: String,
    val width: Int?
) {
    ORIGINAL("Original", null),
    UHD_4K("2160p (4K)", 3840),
    FHD_1080("1080p (FHD)", 1920),
    HD_720("720p (HD)", 1280),
    SD_480("480p (SD)", 854),
    MOBILE_360("360p (Compact)", 640)
}

enum class VideoRateControlMode(val label: String) {
    CRF("CRF Quality"),
    BITRATE("Target Bitrate")
}

enum class CropAspectMode(
    val id: String,
    val label: String,
    val targetRatio: Float?
) {
    FREE("free", "Free", null),
    SQUARE_1_1("1_1", "1:1", 1f),
    PORTRAIT_9_16("9_16", "9:16", 9f / 16f),
    LANDSCAPE_16_9("16_9", "16:9", 16f / 9f)
}

@Immutable
data class VideoCropRegion(
    val enabled: Boolean = false,
    val aspectMode: CropAspectMode = CropAspectMode.FREE,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val widthFraction: Float get() = (right - left).coerceIn(0.05f, 1f)
    val heightFraction: Float get() = (bottom - top).coerceIn(0.05f, 1f)
    val isFullFrame: Boolean
        get() = !enabled || (left <= 0.002f && top <= 0.002f && right >= 0.998f && bottom >= 0.998f)
}

enum class OverlayCornerPreset(
    val id: String,
    val label: String,
    val xFraction: Float,
    val yFraction: Float
) {
    TOP_LEFT("tl", "Top-Left", 0.15f, 0.14f),
    TOP_RIGHT("tr", "Top-Right", 0.85f, 0.14f),
    BOTTOM_LEFT("bl", "Bottom-Left", 0.15f, 0.86f),
    BOTTOM_RIGHT("br", "Bottom-Right", 0.85f, 0.86f),
    CENTER("center", "Center", 0.50f, 0.50f)
}

@Immutable
data class LogoOverlayConfig(
    val uriString: String? = null,
    val displayName: String = "",
    val isSvg: Boolean = false,
    val xFraction: Float = 0.85f,
    val yFraction: Float = 0.14f,
    val widthFraction: Float = 0.18f,
    val opacity: Float = 0.92f
) {
    val hasLogo: Boolean
        get() = !uriString.isNullOrBlank()
}

@Immutable
data class TextOverlayConfig(
    val text: String = "",
    val xFraction: Float = 0.50f,
    val yFraction: Float = 0.86f,
    val scaleFraction: Float = 0.055f,
    val colorHex: Long = 0xFFFFFFFF,
    val hasBackdrop: Boolean = true,
    val opacity: Float = 0.95f
) {
    val hasText: Boolean
        get() = text.isNotBlank()
}

@Immutable
data class VideoOverlayConfig(
    val logo: LogoOverlayConfig = LogoOverlayConfig(),
    val textBox: TextOverlayConfig = TextOverlayConfig()
) {
    val isActive: Boolean
        get() = logo.hasLogo || textBox.hasText
}

enum class StudioCoverIcon(
    val id: String,
    val label: String,
    val accentHex: Long,
    val secondaryHex: Long
) {
    STUDIO_WAVE("studio_wave", "Wave", 0xFF00E5FF, 0xFF004B59),
    VINYL_DISC("vinyl_disc", "Vinyl", 0xFFFFB300, 0xFF4A3300),
    PODCAST_MIC("podcast_mic", "Voice", 0xFF00E676, 0xFF004D28),
    CYBER_EQ("cyber_eq", "EQ Bars", 0xFF38BDF8, 0xFF1E3A5F),
    HI_RES_BADGE("hi_res", "Hi-Res", 0xFFF472B6, 0xFF501B3C),
    STUDIO_TAPE("studio_tape", "Reel", 0xFFA78BFA, 0xFF31245C)
}

@Immutable
sealed interface AudioArtworkSelection {
    @Immutable
    data object None : AudioArtworkSelection

    @Immutable
    data class GalleryImage(val uriString: String) : AudioArtworkSelection

    @Immutable
    data class StudioIcon(val icon: StudioCoverIcon) : AudioArtworkSelection
}

@Immutable
data class ConversionPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val badge: String,
    val container: ContainerFormat,
    val videoCodec: VideoCodecOption,
    val audioCodec: AudioCodecOption,
    val resolution: ResolutionOption = ResolutionOption.ORIGINAL,
    val crf: Int = 23,
    val audioBitrateKbps: Int = 192,
    val fps: Int? = null,
    val isCustom: Boolean = false
)

@Immutable
data class ConversionConfig(
    val selectedPresetId: String = "mp4_h264",
    val container: ContainerFormat = ContainerFormat.MP4,
    val videoCodec: VideoCodecOption = VideoCodecOption.H264,
    val audioCodec: AudioCodecOption = AudioCodecOption.AAC,
    val resolution: ResolutionOption = ResolutionOption.ORIGINAL,
    val customResolutionWidth: Int? = null,
    val rateControlMode: VideoRateControlMode = VideoRateControlMode.CRF,
    val crf: Int = 23,
    val videoBitrateKbps: Int = 2500,
    val audioBitrateKbps: Int = 192,
    val fps: Int? = null,
    val customCommandArgs: String = "",
    val useCustomCommand: Boolean = false,
    val speedPreset: String = "medium",
    val audioArtwork: AudioArtworkSelection = AudioArtworkSelection.None,
    val cropRegion: VideoCropRegion = VideoCropRegion(),
    val overlayConfig: VideoOverlayConfig = VideoOverlayConfig(),
    val kamakerSpec: KamakerSpec? = null,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null
) {
    val isAudioExport: Boolean
        get() = container.isAudioOnly || videoCodec == VideoCodecOption.NONE

    val effectiveTargetWidth: Int?
        get() = customResolutionWidth ?: resolution.width

    val hasActiveTrim: Boolean
        get() = (trimStartMs != null && trimStartMs > 0L) || trimEndMs != null

    val hasActiveOverlays: Boolean
        get() = !isAudioExport && (overlayConfig.isActive || (kamakerSpec?.hasActiveContent == true))
}

@Immutable
data class MediaInputItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int?,
    val height: Int?,
    val videoCodec: String?,
    val audioCodec: String?,
    val fps: Double?,
    val bitrateKbps: Long?,
    val isAudioOnly: Boolean,
    val isProbing: Boolean = false
)

enum class JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Immutable
data class BatchJob(
    val id: String,
    val input: MediaInputItem,
    val config: ConversionConfig,
    val commandPreview: String,
    val outputFileName: String,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0f,
    val speedMultiplier: Double = 0.0,
    val currentFps: Float = 0f,
    val processedTimeMs: Long = 0L,
    val elapsedWallMs: Long = 0L,
    val outputFilePath: String? = null,
    val outputGalleryUri: String? = null,
    val outputSizeBytes: Long? = null,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList(),
    val ffmpegSessionId: Long? = null
)

enum class CodecCategory(val label: String) {
    VIDEO("Video Codecs"),
    AUDIO("Audio Codecs"),
    CONTAINER("Containers"),
    PROTOCOL("Streaming & Filters")
}

@Immutable
data class CodecCatalogEntry(
    val name: String,
    val ffmpegId: String,
    val category: CodecCategory,
    val description: String,
    val isAvailableInBinary: Boolean,
    val supportsEncode: Boolean,
    val supportsDecode: Boolean,
    val suggestedContainer: ContainerFormat? = null,
    val suggestedVideoCodec: VideoCodecOption? = null,
    val suggestedAudioCodec: AudioCodecOption? = null
)

enum class PrecisionCropPanelStyle(
    val id: String,
    val label: String
) {
    BOTTOM_DRAWER("drawer", "Bottom Drawer → Pill"),
    FLOATING_CARD("floating", "Floating & Movable"),
    SIDE_PANEL("side", "Side Panel (Minimizable)")
}

@Immutable
data class StudioSettings(
    val showQuickPresets: Boolean = true,
    val showProControls: Boolean = true,
    val showCommandWorkbench: Boolean = true,
    val autoExportToGallery: Boolean = true,
    val keepScreenOn: Boolean = true,
    val hardwareThreads: Int = 0,
    val lightweightEngineMode: Boolean = false,
    val lowOverheadUi: Boolean = false,
    val autoPruneTempCache: Boolean = false,
    val enableCropOverlay: Boolean = false,
    val enableInlineCrop: Boolean = true,
    val enableFullscreenCrop: Boolean = true,
    val useKvPlayerInPrecisionCrop: Boolean = false,
    val precisionCropPanelStyle: PrecisionCropPanelStyle = PrecisionCropPanelStyle.BOTTOM_DRAWER,
    val precisionCropFullKvHud: Boolean = true,
    val enableStudioMarkInOut: Boolean = false,
    val smartParallelLimit: Int = 0,
    val playBatchFinishSound: Boolean = true,
    val kvShowFullscreenButton: Boolean = false,
    val kvShowScreenshotButton: Boolean = false,
    val kvShowHideHudUiButton: Boolean = false,
    val kvShowMarkInOut: Boolean = false,
    val kvShowFloatingButton: Boolean = false,
    val kvHideBottomTiles: Boolean = false
)


