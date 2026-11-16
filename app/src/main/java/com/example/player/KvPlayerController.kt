package com.example.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import com.example.domain.model.KamakerSpec
import com.example.domain.model.KvPlayerAspectMode
import com.example.domain.model.KvPlayerSourceType
import com.example.domain.model.KvPlayerSubtitleTrack
import com.example.domain.model.KvPlayerTrack
import com.example.domain.model.KvPlayerUiState
import com.example.ffmpeg.KamakerRenderer
import com.example.ffmpeg.MediaExportHelper
import com.example.service.KvPlayerAudioService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KvPlayerController(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionTickerJob: Job? = null
    private var playerInstance: ExoPlayer? = null

    private val _uiState = MutableStateFlow(KvPlayerUiState())
    val uiState: StateFlow<KvPlayerUiState> = _uiState.asStateFlow()

    fun getOrCreatePlayer(): ExoPlayer {
        playerInstance?.let { return it }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val exo = ExoPlayer.Builder(appContext)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPositionTicker() else stopPositionTicker()
                syncBackgroundServiceNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exo.duration.takeIf { it > 0L } ?: _uiState.value.currentTrack?.durationMs ?: 0L
                _uiState.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        durationMs = dur
                    )
                }
                if (playbackState == Player.STATE_ENDED) {
                    val state = _uiState.value
                    if (state.isAbLoopEnabled && state.hasValidMarkRange) {
                        val loopStart = state.markInMs ?: 0L
                        exo.seekTo(loopStart)
                        exo.playWhenReady = true
                        _uiState.update { it.copy(positionMs = loopStart) }
                    } else if (state.currentIndex + 1 < state.playlist.size) {
                        selectTrack(state.currentIndex + 1)
                    } else {
                        _uiState.update { it.copy(positionMs = dur, isPlaying = false) }
                        syncBackgroundServiceNotification()
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    _uiState.update {
                        it.copy(
                            videoWidth = videoSize.width,
                            videoHeight = videoSize.height,
                            pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio
                        )
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val extracted = mutableListOf<KvPlayerSubtitleTrack>()
                var activeSubId: String? = null
                tracks.groups.forEachIndexed { groupIdx, group ->
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (trackIdx in 0 until group.length) {
                            val fmt = group.getTrackFormat(trackIdx)
                            val id = "${groupIdx}_$trackIdx"
                            val lang = fmt.language?.takeIf { it.isNotBlank() && it != "und" }
                            val label = fmt.label?.takeIf { it.isNotBlank() }
                                ?: lang?.uppercase()
                                ?: "Subtitle ${extracted.size + 1}"
                            extracted.add(
                                KvPlayerSubtitleTrack(
                                    id = id,
                                    groupIndex = groupIdx,
                                    trackIndex = trackIdx,
                                    label = label,
                                    language = lang
                                )
                            )
                            if (group.isTrackSelected(trackIdx)) {
                                activeSubId = id
                            }
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        subtitleTracks = extracted,
                        selectedSubtitleTrackId = if (it.subtitlesEnabled) activeSubId else null
                    )
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                val lines = cueGroup.cues.mapNotNull { cue ->
                    cue.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                }
                _uiState.update { it.copy(activeCues = lines) }
            }

            override fun onPlayerError(error: PlaybackException) {
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = error.localizedMessage
                            ?: "This format cannot be decoded natively — try converting it in Konvert Studio."
                    )
                }
                syncBackgroundServiceNotification()
            }
        })

        playerInstance = exo
        return exo
    }

    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = appContext.contentResolver
        val existingUris = _uiState.value.playlist.map { it.uri }.toSet()
        val newTracks = uris.filterNot { it in existingUris }.map { uri ->
            var displayName = "Media Track"
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) displayName = c.getString(idx) ?: displayName
                    }
                }
            }
            val mime = runCatching { resolver.getType(uri) }.getOrNull() ?: guessMime(displayName)
            val isAudio = mime.startsWith("audio/") || isAudioExtension(displayName)
            KvPlayerTrack(
                id = "picked_${uri.toString().hashCode()}_${System.nanoTime()}",
                uri = uri,
                title = displayName,
                mimeType = mime,
                isAudioOnly = isAudio,
                sourceType = KvPlayerSourceType.PICKED
            )
        }
        if (newTracks.isNotEmpty()) {
            addTracks(newTracks, playFirstAdded = _uiState.value.playlist.isEmpty())
        }
    }

    fun addTracks(tracks: List<KvPlayerTrack>, playFirstAdded: Boolean = false) {
        if (tracks.isEmpty()) return
        val currentList = _uiState.value.playlist
        val existingUris = currentList.map { it.uri }.toSet()
        val uniqueNew = tracks
            .distinctBy { it.uri }
            .filterNot { it.uri in existingUris }
        if (uniqueNew.isEmpty()) {
            val matchIdx = currentList.indexOfFirst { it.uri == tracks.first().uri }
            if (playFirstAdded && matchIdx >= 0) {
                selectTrack(matchIdx)
            }
            return
        }
        val firstNewIndex = currentList.size
        val updated = currentList + uniqueNew
        _uiState.update { it.copy(playlist = updated) }
        if (playFirstAdded || currentList.isEmpty()) {
            selectTrack(firstNewIndex)
        }
    }

    fun selectTrack(index: Int, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        val list = _uiState.value.playlist
        if (index !in list.indices) return
        val track = list[index]
        val exo = getOrCreatePlayer()
        _uiState.update {
            it.copy(
                currentIndex = index,
                positionMs = startPositionMs,
                durationMs = track.durationMs,
                errorMessage = null,
                activeCues = emptyList(),
                subtitleTracks = emptyList(),
                selectedSubtitleTrackId = null,
                videoWidth = 0,
                videoHeight = 0,
                markInMs = null,
                markOutMs = null,
                isAbLoopEnabled = false
            )
        }
        runCatching {
            val item = buildMediaItem(track)
            exo.setMediaItem(item)
            exo.prepare()
            if (startPositionMs > 0L) exo.seekTo(startPositionMs)
            exo.setPlaybackSpeed(_uiState.value.playbackSpeed)
            exo.playWhenReady = autoPlay
        }.onFailure { err ->
            _uiState.update {
                it.copy(errorMessage = err.localizedMessage ?: "Failed to open media track.")
            }
        }
        syncBackgroundServiceNotification()
    }

    fun removeTrack(trackId: String) {
        val state = _uiState.value
        val removeIdx = state.playlist.indexOfFirst { it.id == trackId }
        if (removeIdx < 0) return
        val updated = state.playlist.filterNot { it.id == trackId }
        if (updated.isEmpty()) {
            clearPlaylist()
            return
        }
        if (removeIdx == state.currentIndex) {
            val nextIdx = removeIdx.coerceAtMost(updated.lastIndex)
            _uiState.update { it.copy(playlist = updated) }
            selectTrack(nextIdx)
        } else {
            val adjustedIdx = if (removeIdx < state.currentIndex) state.currentIndex - 1 else state.currentIndex
            _uiState.update { it.copy(playlist = updated, currentIndex = adjustedIdx) }
        }
    }

    fun clearPlaylist() {
        playerInstance?.stop()
        playerInstance?.clearMediaItems()
        stopPositionTicker()
        _uiState.update {
            it.copy(
                playlist = emptyList(),
                currentIndex = 0,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
                activeCues = emptyList(),
                subtitleTracks = emptyList(),
                selectedSubtitleTrackId = null,
                isFullscreen = false,
                isFloatingMode = false,
                markInMs = null,
                markOutMs = null,
                isAbLoopEnabled = false,
                errorMessage = null
            )
        }
        stopBackgroundService()
    }

    fun togglePlayPause() {
        val exo = playerInstance ?: run {
            if (_uiState.value.playlist.isNotEmpty()) {
                selectTrack(_uiState.value.currentIndex)
            }
            return
        }
        if (exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0L)
        }
        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }
    }

    fun pause() {
        playerInstance?.pause()
    }

    fun play() {
        playerInstance?.play()
    }

    fun seekTo(positionMs: Long) {
        val exo = playerInstance ?: return
        val maxDur = _uiState.value.durationMs.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, maxDur)
        exo.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
    }

    fun seekBy(deltaMs: Long) {
        val current = playerInstance?.currentPosition ?: _uiState.value.positionMs
        seekTo(current + deltaMs)
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.playlist.isEmpty()) return
        val next = (state.currentIndex + 1) % state.playlist.size
        selectTrack(next)
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (state.playlist.isEmpty()) return
        val currentPos = playerInstance?.currentPosition ?: state.positionMs
        if (currentPos > 3000L) {
            seekTo(0L)
        } else {
            val prev = if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.lastIndex
            selectTrack(prev)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 3.0f)
        playerInstance?.setPlaybackSpeed(safeSpeed)
        _uiState.update { it.copy(playbackSpeed = safeSpeed) }
    }

    fun cycleAspectMode() {
        val all = KvPlayerAspectMode.entries
        val next = all[(all.indexOf(_uiState.value.aspectMode) + 1) % all.size]
        _uiState.update { it.copy(aspectMode = next) }
    }

    fun setAspectMode(mode: KvPlayerAspectMode) {
        _uiState.update { it.copy(aspectMode = mode) }
    }

    fun toggleBgAudio() {
        setBgAudioEnabled(!_uiState.value.bgAudioEnabled)
    }

    fun setBgAudioEnabled(enabled: Boolean) {
        _uiState.update { it.copy(bgAudioEnabled = enabled) }
        if (enabled) {
            syncBackgroundServiceNotification()
        } else {
            stopBackgroundService()
        }
    }

    fun setFullscreen(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isFullscreen = enabled,
                isFloatingMode = if (enabled) false else it.isFloatingMode
            )
        }
    }

    fun toggleFullscreen() {
        setFullscreen(!_uiState.value.isFullscreen)
    }

    fun setFloatingMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isFloatingMode = enabled && it.currentTrack != null,
                isFullscreen = if (enabled) false else it.isFullscreen
            )
        }
    }

    fun toggleFloatingMode() {
        setFloatingMode(!_uiState.value.isFloatingMode)
    }

    fun setHideDisplayVolumeUi(hidden: Boolean) {
        _uiState.update { it.copy(hideDisplayVolumeUi = hidden) }
    }

    fun toggleHideDisplayVolumeUi() {
        setHideDisplayVolumeUi(!_uiState.value.hideDisplayVolumeUi)
    }

    fun markInAtCurrent() {
        val pos = playerInstance?.currentPosition?.coerceAtLeast(0L) ?: _uiState.value.positionMs
        setMarkIn(pos)
    }

    fun setMarkIn(ms: Long?) {
        _uiState.update { state ->
            if (ms == null) {
                state.copy(markInMs = null, isAbLoopEnabled = false)
            } else {
                val safeIn = ms.coerceAtLeast(0L)
                val adjustedOut = state.markOutMs?.takeIf { it > safeIn + 250L }
                state.copy(
                    markInMs = safeIn,
                    markOutMs = adjustedOut,
                    isAbLoopEnabled = if (adjustedOut != null) state.isAbLoopEnabled else false
                )
            }
        }
    }

    fun markOutAtCurrent() {
        val pos = playerInstance?.currentPosition?.coerceAtLeast(0L) ?: _uiState.value.positionMs
        setMarkOut(pos)
    }

    fun setMarkOut(ms: Long?) {
        _uiState.update { state ->
            if (ms == null) {
                state.copy(markOutMs = null, isAbLoopEnabled = false)
            } else {
                val dur = state.durationMs
                val safeOut = if (dur > 0L) ms.coerceIn(0L, dur) else ms.coerceAtLeast(0L)
                val currentIn = state.markInMs
                if (currentIn != null && safeOut <= currentIn) {
                    // Swap/shift if user marked Out earlier than In so range remains valid
                    state.copy(markInMs = safeOut, markOutMs = currentIn)
                } else {
                    state.copy(
                        markInMs = currentIn ?: 0L,
                        markOutMs = safeOut
                    )
                }
            }
        }
    }

    fun clearMarkInOut() {
        _uiState.update {
            it.copy(markInMs = null, markOutMs = null, isAbLoopEnabled = false)
        }
    }

    fun toggleAbLoop() {
        setAbLoopEnabled(!_uiState.value.isAbLoopEnabled)
    }

    fun setAbLoopEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (enabled && !state.hasValidMarkRange) return
        _uiState.update { it.copy(isAbLoopEnabled = enabled) }
        if (enabled) {
            val inMs = state.markInMs ?: 0L
            val outMs = state.markOutMs ?: Long.MAX_VALUE
            val pos = playerInstance?.currentPosition ?: state.positionMs
            if (pos < inMs || pos >= outMs) {
                seekTo(inMs)
            }
        }
    }

    suspend fun extractCurrentFramePreviewBitmap(maxEdgePx: Int = 560): Bitmap? {
        val state = _uiState.value
        val track = state.currentTrack ?: return null
        if (track.isAudioOnly) return null
        val capturePosMs = (playerInstance?.currentPosition ?: state.positionMs).coerceAtLeast(0L)
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, track.uri)
                val timeUs = capturePosMs * 1000L
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: return@withContext null
                val maxSide = maxOf(raw.width, raw.height)
                if (maxSide > maxEdgePx && maxSide > 0) {
                    val scale = maxEdgePx.toFloat() / maxSide.toFloat()
                    val scaled = Bitmap.createScaledBitmap(
                        raw,
                        (raw.width * scale).toInt().coerceAtLeast(1),
                        (raw.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled !== raw) raw.recycle()
                    scaled
                } else {
                    raw
                }
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    suspend fun captureCurrentFrame(kamakerSpec: KamakerSpec? = null): Result<Uri> {
        val state = _uiState.value
        val track = state.currentTrack
            ?: return Result.failure(IllegalStateException("No active media track to screenshot."))
        if (track.isAudioOnly) {
            return Result.failure(IllegalStateException("Screenshots are available for video tracks."))
        }
        val capturePosMs = (playerInstance?.currentPosition ?: state.positionMs).coerceAtLeast(0L)
        _uiState.update { it.copy(isCapturingScreenshot = true) }
        return try {
            val rawBitmap = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(appContext, track.uri)
                    val timeUs = capturePosMs * 1000L
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { retriever.release() }
                }
            } ?: return Result.failure(IllegalStateException("Unable to decode a frame from this video."))

            val finalBitmap = if (kamakerSpec != null && kamakerSpec.stampOnCapturedFrames && kamakerSpec.hasActiveContent) {
                try {
                    KamakerRenderer.stampWatermarkOnBitmap(appContext, rawBitmap, kamakerSpec)
                } finally {
                    rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            val safeBase = track.title.substringBeforeLast('.')
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .take(28)
                .ifBlank { "kvplayer" }
            val fileName = "${safeBase}_ss_${System.currentTimeMillis()}.png"
            val savedUri = try {
                MediaExportHelper.saveBitmapToPictures(appContext, finalBitmap, fileName)
            } finally {
                finalBitmap.recycle()
            }
            if (savedUri != null) {
                Result.success(savedUri)
            } else {
                Result.failure(IllegalStateException("Failed to save screenshot to Pictures."))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            _uiState.update { it.copy(isCapturingScreenshot = false) }
        }
    }

    fun selectSubtitleTrack(trackId: String?) {
        val exo = playerInstance ?: return
        if (trackId == null) {
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            _uiState.update {
                it.copy(
                    subtitlesEnabled = false,
                    selectedSubtitleTrackId = null,
                    activeCues = emptyList()
                )
            }
            return
        }

        val target = _uiState.value.subtitleTracks.firstOrNull { it.id == trackId } ?: return
        val group = exo.currentTracks.groups.getOrNull(target.groupIndex) ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, target.trackIndex))
            .build()
        _uiState.update {
            it.copy(subtitlesEnabled = true, selectedSubtitleTrackId = trackId)
        }
    }

    fun attachExternalSubtitle(uri: Uri) {
        val state = _uiState.value
        val track = state.currentTrack ?: return
        val resolver = appContext.contentResolver
        var subName = "sub.srt"
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) subName = c.getString(idx) ?: subName
                }
            }
        }
        val subMime = when (subName.substringAfterLast('.', "").lowercase()) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val resumePos = playerInstance?.currentPosition ?: state.positionMs
        val wasPlaying = playerInstance?.isPlaying ?: state.isPlaying
        val updatedTrack = track.copy(subtitleUri = uri, subtitleMimeType = subMime)
        val updatedList = state.playlist.toMutableList().apply {
            set(state.currentIndex, updatedTrack)
        }
        _uiState.update { it.copy(playlist = updatedList, subtitlesEnabled = true) }
        selectTrack(state.currentIndex, startPositionMs = resumePos, autoPlay = wasPlaying)
    }

    fun retryCurrentTrack() {
        val state = _uiState.value
        if (state.currentIndex in state.playlist.indices) {
            selectTrack(state.currentIndex, startPositionMs = state.positionMs, autoPlay = true)
        }
    }

    fun onAppBackgrounded(isInPipMode: Boolean) {
        val state = _uiState.value
        if (isInPipMode) return
        if (state.bgAudioEnabled && state.currentTrack != null && state.isPlaying) {
            startOrUpdateBackgroundService()
        } else if (!state.bgAudioEnabled && state.isPlaying) {
            pause()
        }
    }

    fun onAppForegrounded() {
        // Keep Bg Audio notification active if enabled & playing so user sees status, or refresh state
        syncBackgroundServiceNotification()
    }

    fun stopAndDismissBgAudio() {
        pause()
        _uiState.update { it.copy(bgAudioEnabled = false) }
        stopBackgroundService()
    }

    private fun buildMediaItem(track: KvPlayerTrack): MediaItem {
        val builder = MediaItem.Builder().setUri(track.uri)
        if (track.subtitleUri != null) {
            val subConfig = MediaItem.SubtitleConfiguration.Builder(track.subtitleUri)
                .setMimeType(track.subtitleMimeType ?: MimeTypes.APPLICATION_SUBRIP)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setLabel("External Subtitle")
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }
        return builder.build()
    }

    private fun syncBackgroundServiceNotification() {
        val state = _uiState.value
        if (state.bgAudioEnabled && state.currentTrack != null && state.isPlaying) {
            startOrUpdateBackgroundService()
        } else if (!state.bgAudioEnabled) {
            stopBackgroundService()
        }
    }

    private fun startOrUpdateBackgroundService() {
        val intent = Intent(appContext, KvPlayerAudioService::class.java).apply {
            action = KvPlayerAudioService.ACTION_SYNC
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, intent)
        }
    }

    private fun stopBackgroundService() {
        val intent = Intent(appContext, KvPlayerAudioService::class.java)
        runCatching { appContext.stopService(intent) }
    }

    private fun startPositionTicker() {
        if (positionTickerJob?.isActive == true) return
        positionTickerJob = scope.launch {
            while (isActive) {
                val exo = playerInstance
                if (exo != null && exo.isPlaying) {
                    val pos = exo.currentPosition.coerceAtLeast(0L)
                    val state = _uiState.value
                    val dur = exo.duration.takeIf { it > 0L } ?: state.durationMs
                    val loopIn = state.markInMs ?: 0L
                    val loopOut = state.markOutMs
                    if (state.isAbLoopEnabled && loopOut != null && loopOut > loopIn && pos >= loopOut) {
                        exo.seekTo(loopIn)
                        _uiState.update { it.copy(positionMs = loopIn, durationMs = dur) }
                    } else {
                        _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                    }
                }
                delay(180L)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    private fun isAudioExtension(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "wav", "m4a", "aac", "ogg", "opus", "ape", "flac", "wv", "amr")
    }

    private fun guessMime(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ape" -> "audio/x-ape"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            else -> "video/*"
        }
    }
}
