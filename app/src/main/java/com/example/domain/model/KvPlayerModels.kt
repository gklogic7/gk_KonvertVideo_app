package com.example.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable

enum class KvPlayerSourceType(val label: String) {
    PICKED("Device"),
    STUDIO("Studio"),
    HISTORY("History")
}

enum class KvPlayerAspectMode(val label: String, val fixedRatio: Float?) {
    FIT("Fit", null),
    FILL("Fill", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f)
}

@Immutable
data class KvPlayerSubtitleTrack(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?
)

@Immutable
data class KvPlayerTrack(
    val id: String,
    val uri: Uri,
    val title: String,
    val mimeType: String,
    val durationMs: Long = 0L,
    val isAudioOnly: Boolean = false,
    val sourceType: KvPlayerSourceType = KvPlayerSourceType.PICKED,
    val subtitleUri: Uri? = null,
    val subtitleMimeType: String? = null
)

@Immutable
data class KvPlayerUiState(
    val playlist: List<KvPlayerTrack> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val aspectMode: KvPlayerAspectMode = KvPlayerAspectMode.FIT,
    val bgAudioEnabled: Boolean = false,
    val subtitlesEnabled: Boolean = true,
    val subtitleTracks: List<KvPlayerSubtitleTrack> = emptyList(),
    val selectedSubtitleTrackId: String? = null,
    val activeCues: List<String> = emptyList(),
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val pixelWidthHeightRatio: Float = 1f,
    val isFullscreen: Boolean = false,
    val isFloatingMode: Boolean = false,
    val hideDisplayVolumeUi: Boolean = false,
    val markInMs: Long? = null,
    val markOutMs: Long? = null,
    val isAbLoopEnabled: Boolean = false,
    val isCapturingScreenshot: Boolean = false,
    val errorMessage: String? = null
) {
    val currentTrack: KvPlayerTrack?
        get() = playlist.getOrNull(currentIndex)

    val hasValidMarkRange: Boolean
        get() {
            val inMs = markInMs ?: 0L
            val outMs = markOutMs ?: return false
            return outMs > inMs
        }
}
