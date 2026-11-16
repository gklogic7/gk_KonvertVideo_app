package com.example.ui.state

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.KonvertApplication
import com.example.data.local.ConversionHistoryEntity
import com.example.domain.model.AudioArtworkSelection
import com.example.domain.model.AudioCodecOption
import com.example.domain.model.BatchJob
import com.example.domain.model.CodecCatalogEntry
import com.example.domain.model.ContainerFormat
import com.example.domain.model.ConversionConfig
import com.example.domain.model.ConversionPreset
import com.example.domain.model.KamakerBatchExportState
import com.example.domain.model.KamakerBatchImageItem
import com.example.domain.model.KamakerBuiltInPresets
import com.example.domain.model.KamakerExportFormat
import com.example.domain.model.KamakerFrameShape
import com.example.domain.model.KamakerPreset
import com.example.domain.model.KamakerPresetPayload
import com.example.domain.model.KamakerShapeRule
import com.example.domain.model.KamakerSpec
import com.example.domain.model.KvPlayerSourceType
import com.example.domain.model.KvPlayerTrack
import com.example.domain.model.KvPlayerUiState
import com.example.domain.model.MediaInputItem
import com.example.domain.model.ResolutionOption
import com.example.domain.model.StudioSettings
import com.example.domain.model.VideoCodecOption
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoOverlayConfig
import com.example.domain.model.VideoRateControlMode
import com.example.ffmpeg.FFmpegCommandBuilder
import com.example.ffmpeg.FFprobeInspector
import com.example.ffmpeg.KamakerRenderer
import com.example.ffmpeg.PresetCatalog
import com.example.ffmpeg.RuntimeEngineInfo
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KonvertViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KonvertApplication
    private val repo = app.repository
    val kvPlayerController = app.kvPlayerController
    val kvPlayerState: StateFlow<KvPlayerUiState> = kvPlayerController.uiState

    private val _selectedInputs = MutableStateFlow<List<MediaInputItem>>(emptyList())
    val selectedInputs: StateFlow<List<MediaInputItem>> = _selectedInputs.asStateFlow()

    private val _activePreviewIndex = MutableStateFlow(0)
    val activePreviewIndex: StateFlow<Int> = _activePreviewIndex.asStateFlow()

    private val _config = MutableStateFlow(
        ConversionConfig().let { base ->
            base.copy(customCommandArgs = FFmpegCommandBuilder.buildEditableTemplateArgs(base))
        }
    )
    val config: StateFlow<ConversionConfig> = _config.asStateFlow()

    val settings: StateFlow<StudioSettings> = repo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    val customPresets: StateFlow<List<ConversionPreset>> = repo.customPresets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val queueJobs: StateFlow<List<BatchJob>> = repo.queueJobs

    val historyItems: StateFlow<List<ConversionHistoryEntity>> = repo.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val engineInfo: StateFlow<RuntimeEngineInfo?> = repo.engineInfo

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _kamakerSpec = MutableStateFlow(KamakerSpec())
    val kamakerSpec: StateFlow<KamakerSpec> = _kamakerSpec.asStateFlow()

    val customKamakerPresets: StateFlow<List<KamakerPreset>> = repo.customKamakerPresets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _kamakerBackdropBitmap = MutableStateFlow<Bitmap?>(null)
    val kamakerBackdropBitmap: StateFlow<Bitmap?> = _kamakerBackdropBitmap.asStateFlow()

    private val _kamakerBatchImages = MutableStateFlow<List<KamakerBatchImageItem>>(emptyList())
    val kamakerBatchImages: StateFlow<List<KamakerBatchImageItem>> = _kamakerBatchImages.asStateFlow()

    private val _kamakerBatchExportState = MutableStateFlow(KamakerBatchExportState())
    val kamakerBatchExportState: StateFlow<KamakerBatchExportState> = _kamakerBatchExportState.asStateFlow()

    private val _kamakerRenamePrefix = MutableStateFlow("katche img")
    val kamakerRenamePrefix: StateFlow<String> = _kamakerRenamePrefix.asStateFlow()

    private val _kamakerExportFormat = MutableStateFlow(KamakerExportFormat.KEEP)
    val kamakerExportFormat: StateFlow<KamakerExportFormat> = _kamakerExportFormat.asStateFlow()

    private var kamakerPersistJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = repo.kamakerSpec.first()
            _kamakerSpec.value = loaded
            if (loaded.applyInStudio) {
                syncKamakerToStudioConfig(loaded)
            }
        }
    }

    fun ensureEngineCapabilitiesLoaded() {
        if (repo.engineInfo.value == null) {
            viewModelScope.launch {
                repo.loadEngineCapabilitiesIfNeeded()
            }
        }
    }

    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            val currentUris = _selectedInputs.value.map { it.uri }.toSet()
            val newUris = uris.filterNot { it in currentUris }
            if (newUris.isEmpty()) {
                _snackbarEvents.emit("Selected file(s) are already in the batch.")
                return@launch
            }

            val fastMode = settings.value.lightweightEngineMode
            val placeholders = newUris.mapIndexed { idx, uri ->
                MediaInputItem(
                    id = "probing_${System.nanoTime()}_$idx",
                    uri = uri,
                    displayName = "Inspecting media…",
                    mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "video/mp4",
                    sizeBytes = 0L,
                    durationMs = 0L,
                    width = null,
                    height = null,
                    videoCodec = null,
                    audioCodec = null,
                    fps = null,
                    bitrateKbps = null,
                    isAudioOnly = false,
                    isProbing = true
                )
            }
            val startIndex = _selectedInputs.value.size
            _selectedInputs.update { it + placeholders }
            if (startIndex == 0) {
                _activePreviewIndex.value = 0
            }

            placeholders.forEach { placeholder ->
                val probed = FFprobeInspector.inspectUri(
                    context = context,
                    uri = placeholder.uri,
                    fastLightweightMode = fastMode
                )
                _selectedInputs.update { list ->
                    list.map { if (it.id == placeholder.id) probed else it }
                }
            }
        }
    }

    fun removeInput(id: String) {
        _selectedInputs.update { list ->
            val updated = list.filterNot { it.id == id }
            if (_activePreviewIndex.value >= updated.size) {
                _activePreviewIndex.value = (updated.size - 1).coerceAtLeast(0)
            }
            updated
        }
    }

    fun clearInputs() {
        _selectedInputs.value = emptyList()
        _activePreviewIndex.value = 0
    }

    fun selectPreviewIndex(index: Int) {
        if (index in _selectedInputs.value.indices) {
            _activePreviewIndex.value = index
        }
    }

    fun applyPreset(preset: ConversionPreset) {
        updateConfigAndSync { current ->
            ConversionConfig(
                selectedPresetId = preset.id,
                container = preset.container,
                videoCodec = preset.videoCodec,
                audioCodec = preset.audioCodec,
                resolution = preset.resolution,
                customResolutionWidth = preset.resolution.width,
                rateControlMode = VideoRateControlMode.CRF,
                crf = preset.crf,
                videoBitrateKbps = current.videoBitrateKbps,
                audioBitrateKbps = preset.audioBitrateKbps,
                fps = preset.fps,
                audioArtwork = current.audioArtwork,
                cropRegion = current.cropRegion,
                overlayConfig = current.overlayConfig,
                kamakerSpec = current.kamakerSpec,
                trimStartMs = current.trimStartMs,
                trimEndMs = current.trimEndMs,
                useCustomCommand = false
            )
        }
    }

    fun updateContainer(container: ContainerFormat) {
        updateConfigAndSync { current ->
            val validVideo = PresetCatalog.compatibleVideoCodecs(container)
            val validAudio = PresetCatalog.compatibleAudioCodecs(container)
            val nextVideo = if (current.videoCodec in validVideo) current.videoCodec else validVideo.first()
            val nextAudio = if (current.audioCodec in validAudio) current.audioCodec else validAudio.first()
            current.copy(
                selectedPresetId = "custom",
                container = container,
                videoCodec = nextVideo,
                audioCodec = nextAudio,
                useCustomCommand = false
            )
        }
    }

    fun updateVideoCodec(codec: VideoCodecOption) {
        updateConfigAndSync {
            it.copy(selectedPresetId = "custom", videoCodec = codec, useCustomCommand = false)
        }
    }

    fun updateAudioCodec(codec: AudioCodecOption) {
        updateConfigAndSync {
            it.copy(selectedPresetId = "custom", audioCodec = codec, useCustomCommand = false)
        }
    }

    fun updateResolution(resolution: ResolutionOption) {
        updateConfigAndSync {
            it.copy(
                selectedPresetId = "custom",
                resolution = resolution,
                customResolutionWidth = resolution.width,
                useCustomCommand = false
            )
        }
    }

    fun updateCustomResolutionWidth(widthPx: Int?) {
        val snapped = widthPx?.let { (((it.coerceIn(320, 3840)) + 1) / 2) * 2 }
        val matchingPreset = ResolutionOption.entries.firstOrNull { it.width == snapped }
            ?: ResolutionOption.ORIGINAL
        updateConfigAndSync {
            it.copy(
                selectedPresetId = "custom",
                resolution = matchingPreset,
                customResolutionWidth = snapped,
                useCustomCommand = false
            )
        }
    }

    fun updateRateControlMode(mode: VideoRateControlMode) {
        updateConfigAndSync {
            it.copy(selectedPresetId = "custom", rateControlMode = mode, useCustomCommand = false)
        }
    }

    fun updateCrf(crf: Int) {
        updateConfigAndSync {
            it.copy(selectedPresetId = "custom", crf = crf.coerceIn(10, 48), useCustomCommand = false)
        }
    }

    fun updateVideoBitrateKbps(kbps: Int) {
        updateConfigAndSync {
            it.copy(selectedPresetId = "custom", videoBitrateKbps = kbps.coerceIn(250, 20000), useCustomCommand = false)
        }
    }

    fun updateAudioBitrate(kbps: Int) {
        updateConfigAndSync {
            it.copy(selectedPresetId = "custom", audioBitrateKbps = kbps.coerceIn(32, 320), useCustomCommand = false)
        }
    }

    fun updateFps(fps: Int?) {
        updateConfigAndSync {
            it.copy(selectedPresetId = "custom", fps = fps, useCustomCommand = false)
        }
    }

    fun updateSpeedPreset(speedPreset: String) {
        updateConfigAndSync {
            it.copy(selectedPresetId = "custom", speedPreset = speedPreset, useCustomCommand = false)
        }
    }

    fun updateAudioArtwork(artwork: AudioArtworkSelection) {
        updateConfigAndSync {
            it.copy(audioArtwork = artwork, useCustomCommand = false)
        }
    }

    fun updateCropRegion(crop: VideoCropRegion) {
        updateConfigAndSync {
            it.copy(cropRegion = crop, useCustomCommand = false)
        }
    }

    fun resetCropRegion() {
        updateConfigAndSync {
            it.copy(cropRegion = VideoCropRegion(), useCustomCommand = false)
        }
    }

    fun updateCropAndOverlay(crop: VideoCropRegion, overlay: VideoOverlayConfig) {
        updateConfigAndSync {
            it.copy(cropRegion = crop, overlayConfig = overlay, useCustomCommand = false)
        }
    }

    fun updateOverlayConfig(overlay: VideoOverlayConfig) {
        updateConfigAndSync {
            it.copy(overlayConfig = overlay, useCustomCommand = false)
        }
    }

    fun clearOverlays() {
        updateConfigAndSync {
            it.copy(overlayConfig = VideoOverlayConfig(), useCustomCommand = false)
        }
    }

    fun updateTrimRange(startMs: Long?, endMs: Long?) {
        updateConfigAndSync {
            it.copy(trimStartMs = startMs, trimEndMs = endMs, useCustomCommand = false)
        }
    }

    fun clearTrimRange() {
        updateConfigAndSync {
            it.copy(trimStartMs = null, trimEndMs = null, useCustomCommand = false)
        }
    }

    fun updateCustomCommand(rawArgs: String) {
        _config.update {
            it.copy(
                customCommandArgs = rawArgs,
                useCustomCommand = true
            )
        }
    }

    fun resetCommandFromControls() {
        viewModelScope.launch {
            val threads = settings.value.hardwareThreads
            _config.update { current ->
                val synced = current.copy(useCustomCommand = false)
                synced.copy(customCommandArgs = FFmpegCommandBuilder.buildEditableTemplateArgs(synced, threads))
            }
            _snackbarEvents.emit("CLI arguments synced from studio controls.")
        }
    }

    private fun updateConfigAndSync(transform: (ConversionConfig) -> ConversionConfig) {
        val threads = settings.value.hardwareThreads
        _config.update { current ->
            val updated = transform(current)
            updated.copy(
                customCommandArgs = FFmpegCommandBuilder.buildEditableTemplateArgs(updated, threads)
            )
        }
    }

    fun saveCurrentAsPreset(name: String) {
        viewModelScope.launch {
            repo.saveCustomPreset(name, _config.value)
            _snackbarEvents.emit("Saved preset \"$name\"")
        }
    }

    fun deleteCustomPreset(id: String) {
        viewModelScope.launch {
            repo.deleteCustomPreset(id)
        }
    }

    fun startBatchConversion(onEnqueued: () -> Unit) {
        val inputs = _selectedInputs.value.filterNot { it.isProbing }
        if (inputs.isEmpty()) {
            viewModelScope.launch { _snackbarEvents.emit("Select at least one media file first.") }
            return
        }
        viewModelScope.launch {
            val threads = repo.settings.first().hardwareThreads
            repo.enqueueBatch(inputs, _config.value, threads)
            _snackbarEvents.emit("Queued ${inputs.size} conversion job(s).")
            onEnqueued()
        }
    }

    fun cancelJob(jobId: String) = repo.cancelJob(jobId)
    fun cancelAllActive() = repo.cancelAllActive()
    fun clearFinishedJobs() = repo.clearFinishedJobs()

    fun deleteHistoryRecord(entity: ConversionHistoryEntity) {
        viewModelScope.launch {
            repo.deleteHistoryRecord(entity)
            _snackbarEvents.emit("Deleted conversion record.")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repo.clearHistory()
            _snackbarEvents.emit("Cleared conversion history.")
        }
    }

    fun reuseHistoryConfig(entity: ConversionHistoryEntity, onNavigateStudio: () -> Unit) {
        val container = ContainerFormat.entries.firstOrNull { it.id == entity.containerId } ?: ContainerFormat.MP4
        val vCodec = VideoCodecOption.entries.firstOrNull { it.ffmpegName == entity.videoCodec } ?: VideoCodecOption.H264
        val aCodec = AudioCodecOption.entries.firstOrNull { it.ffmpegName == entity.audioCodec } ?: AudioCodecOption.AAC
        updateConfigAndSync { current ->
            ConversionConfig(
                selectedPresetId = "custom",
                container = container,
                videoCodec = vCodec,
                audioCodec = aCodec,
                kamakerSpec = current.kamakerSpec,
                useCustomCommand = false
            )
        }
        onNavigateStudio()
        viewModelScope.launch { _snackbarEvents.emit("Loaded settings from ${entity.outputName}.") }
    }

    fun applyCatalogCodecToStudio(entry: CodecCatalogEntry, onNavigateStudio: () -> Unit) {
        val container = entry.suggestedContainer ?: _config.value.container
        updateConfigAndSync { current ->
            val vCodec = entry.suggestedVideoCodec
                ?: if (container.isAudioOnly) VideoCodecOption.NONE else PresetCatalog.compatibleVideoCodecs(container).first()
            val aCodec = entry.suggestedAudioCodec
                ?: PresetCatalog.compatibleAudioCodecs(container).first()
            current.copy(
                selectedPresetId = "custom",
                container = container,
                videoCodec = vCodec,
                audioCodec = aCodec,
                useCustomCommand = false
            )
        }
        onNavigateStudio()
        viewModelScope.launch { _snackbarEvents.emit("Selected ${entry.name} in Studio.") }
    }

    fun setShowQuickPresets(enabled: Boolean) {
        viewModelScope.launch { repo.setShowQuickPresets(enabled) }
    }

    fun setShowProControls(enabled: Boolean) {
        viewModelScope.launch { repo.setShowProControls(enabled) }
    }

    fun setShowCommandWorkbench(enabled: Boolean) {
        viewModelScope.launch { repo.setShowCommandWorkbench(enabled) }
    }

    fun setAutoExportToGallery(enabled: Boolean) {
        viewModelScope.launch { repo.setAutoExportToGallery(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { repo.setKeepScreenOn(enabled) }
    }

    fun setHardwareThreads(threads: Int) {
        viewModelScope.launch {
            repo.setHardwareThreads(threads)
            _config.update { current ->
                if (current.useCustomCommand) current
                else current.copy(customCommandArgs = FFmpegCommandBuilder.buildEditableTemplateArgs(current, threads))
            }
        }
    }

    fun setLightweightEngineMode(enabled: Boolean) {
        viewModelScope.launch { repo.setLightweightEngineMode(enabled) }
    }

    fun setLowOverheadUi(enabled: Boolean) {
        viewModelScope.launch { repo.setLowOverheadUi(enabled) }
    }

    fun setAutoPruneTempCache(enabled: Boolean) {
        viewModelScope.launch { repo.setAutoPruneTempCache(enabled) }
    }

    fun setEnableCropOverlay(enabled: Boolean) {
        viewModelScope.launch {
            repo.setEnableCropOverlay(enabled)
            if (!enabled) {
                updateConfigAndSync { current ->
                    current.copy(
                        cropRegion = current.cropRegion.copy(enabled = false),
                        useCustomCommand = false
                    )
                }
            }
        }
    }

    fun setEnableInlineCrop(enabled: Boolean) {
        viewModelScope.launch { repo.setEnableInlineCrop(enabled) }
    }

    fun setEnableFullscreenCrop(enabled: Boolean) {
        viewModelScope.launch { repo.setEnableFullscreenCrop(enabled) }
    }

    fun setUseKvPlayerInPrecisionCrop(enabled: Boolean) {
        viewModelScope.launch { repo.setUseKvPlayerInPrecisionCrop(enabled) }
    }

    fun setPrecisionCropPanelStyle(style: com.example.domain.model.PrecisionCropPanelStyle) {
        viewModelScope.launch { repo.setPrecisionCropPanelStyle(style) }
    }

    fun setPrecisionCropFullKvHud(enabled: Boolean) {
        viewModelScope.launch { repo.setPrecisionCropFullKvHud(enabled) }
    }

    fun setEnableStudioMarkInOut(enabled: Boolean) {
        viewModelScope.launch { repo.setEnableStudioMarkInOut(enabled) }
    }

    fun setSmartParallelLimit(limit: Int) {
        viewModelScope.launch { repo.setSmartParallelLimit(limit) }
    }

    fun setPlayBatchFinishSound(enabled: Boolean) {
        viewModelScope.launch { repo.setPlayBatchFinishSound(enabled) }
    }

    fun setKvShowFullscreenButton(enabled: Boolean) {
        viewModelScope.launch {
            repo.setKvShowFullscreenButton(enabled)
            if (!enabled) kvPlayerController.setFullscreen(false)
        }
    }

    fun setKvShowScreenshotButton(enabled: Boolean) {
        viewModelScope.launch { repo.setKvShowScreenshotButton(enabled) }
    }

    fun setKvShowHideHudUiButton(enabled: Boolean) {
        viewModelScope.launch {
            repo.setKvShowHideHudUiButton(enabled)
            if (!enabled) kvPlayerController.setHideDisplayVolumeUi(false)
        }
    }

    fun setKvShowMarkInOut(enabled: Boolean) {
        viewModelScope.launch {
            repo.setKvShowMarkInOut(enabled)
            if (!enabled) kvPlayerController.clearMarkInOut()
        }
    }

    fun setKvShowFloatingButton(enabled: Boolean) {
        viewModelScope.launch {
            repo.setKvShowFloatingButton(enabled)
            if (!enabled) kvPlayerController.setFloatingMode(false)
        }
    }

    fun setKvHideBottomTiles(enabled: Boolean) {
        viewModelScope.launch { repo.setKvHideBottomTiles(enabled) }
    }

    fun enableAllStudioModes() {
        viewModelScope.launch { repo.enableAllStudioModes() }
    }

    fun refreshCapabilities() {
        viewModelScope.launch { repo.refreshEngineCapabilities() }
    }

    fun loadStudioIntoKvPlayer(autoPlay: Boolean = true) {
        val inputs = _selectedInputs.value.filterNot { it.isProbing }
        if (inputs.isEmpty()) {
            viewModelScope.launch { _snackbarEvents.emit("No media files currently loaded in Konvert Studio.") }
            return
        }
        val tracks = inputs.map { item ->
            KvPlayerTrack(
                id = "studio_${item.id}",
                uri = item.uri,
                title = item.displayName,
                mimeType = item.mimeType,
                durationMs = item.durationMs,
                isAudioOnly = item.isAudioOnly,
                sourceType = KvPlayerSourceType.STUDIO
            )
        }
        kvPlayerController.addTracks(tracks, playFirstAdded = autoPlay)
    }

    fun loadHistoryIntoKvPlayer(autoPlay: Boolean = true) {
        val playable = historyItems.value.mapNotNull { historyToTrack(it) }
        if (playable.isEmpty()) {
            viewModelScope.launch { _snackbarEvents.emit("No playable converted files in History yet.") }
            return
        }
        kvPlayerController.addTracks(playable, playFirstAdded = autoPlay)
    }

    fun playHistoryRecordInKvPlayer(entity: ConversionHistoryEntity, onNavigatePlayer: () -> Unit) {
        val track = historyToTrack(entity)
        if (track == null) {
            viewModelScope.launch { _snackbarEvents.emit("Converted file is no longer available on device.") }
            return
        }
        kvPlayerController.addTracks(listOf(track), playFirstAdded = true)
        onNavigatePlayer()
    }

    fun playMultipleHistoryInKvPlayer(entities: List<ConversionHistoryEntity>, onNavigatePlayer: () -> Unit) {
        val tracks = entities.mapNotNull { historyToTrack(it) }
        if (tracks.isEmpty()) {
            viewModelScope.launch { _snackbarEvents.emit("None of the selected files are playable on device.") }
            return
        }
        kvPlayerController.addTracks(tracks, playFirstAdded = true)
        onNavigatePlayer()
        viewModelScope.launch { _snackbarEvents.emit("Loaded ${tracks.size} file(s) into Kvplayer.") }
    }

    fun sendMultipleHistoryToStudio(entities: List<ConversionHistoryEntity>, onNavigateStudio: () -> Unit) {
        val uris = entities.mapNotNull { historyToTrack(it)?.uri }.distinct()
        if (uris.isEmpty()) {
            viewModelScope.launch { _snackbarEvents.emit("None of the selected files exist on device.") }
            return
        }
        addPickedUris(uris)
        onNavigateStudio()
        viewModelScope.launch { _snackbarEvents.emit("Sent ${uris.size} file(s) to Konvert Studio.") }
    }

    fun deleteMultipleHistoryRecords(entities: List<ConversionHistoryEntity>) {
        if (entities.isEmpty()) return
        viewModelScope.launch {
            entities.forEach { repo.deleteHistoryRecord(it) }
            _snackbarEvents.emit("Deleted ${entities.size} conversion record(s).")
        }
    }

    fun captureKvPlayerScreenshot() {
        viewModelScope.launch {
            val result = kvPlayerController.captureCurrentFrame(_kamakerSpec.value)
            result.onSuccess {
                val stamped = _kamakerSpec.value.stampOnCapturedFrames && _kamakerSpec.value.hasActiveContent
                _snackbarEvents.emit(
                    if (stamped) "Saved watermarked screenshot to Pictures/KonvertVideo."
                    else "Saved screenshot to Pictures/KonvertVideo."
                )
            }.onFailure { err ->
                _snackbarEvents.emit(err.localizedMessage ?: "Screenshot unavailable for this track.")
            }
        }
    }

    fun sendKvPlayerTrimToStudio(onNavigateStudio: () -> Unit) {
        val playerState = kvPlayerState.value
        val track = playerState.currentTrack
        if (track == null) {
            viewModelScope.launch { _snackbarEvents.emit("Load a video or audio track first.") }
            return
        }
        val context = getApplication<Application>()
        viewModelScope.launch {
            val startMs = playerState.markInMs
            val endMs = playerState.markOutMs
            val fastMode = settings.value.lightweightEngineMode

            val existingIdx = _selectedInputs.value.indexOfFirst { it.uri == track.uri }
            if (existingIdx >= 0) {
                _activePreviewIndex.value = existingIdx
            } else {
                val probed = FFprobeInspector.inspectUri(
                    context = context,
                    uri = track.uri,
                    fastLightweightMode = fastMode
                )
                _selectedInputs.update { it + probed }
                _activePreviewIndex.value = _selectedInputs.value.lastIndex.coerceAtLeast(0)
            }

            updateTrimRange(startMs = startMs, endMs = endMs)
            kvPlayerController.setFloatingMode(false)
            kvPlayerController.setFullscreen(false)
            onNavigateStudio()
            _snackbarEvents.emit("Loaded clip and In/Out trim bounds into Konvert Studio.")
        }
    }

    private fun historyToTrack(entity: ConversionHistoryEntity): KvPlayerTrack? {
        if (entity.status != "COMPLETED") return null
        val file = entity.outputFilePath.takeIf { it.isNotBlank() }?.let(::File)
        val uri = when {
            file?.exists() == true -> Uri.fromFile(file)
            !entity.outputGalleryUri.isNullOrBlank() -> runCatching { Uri.parse(entity.outputGalleryUri) }.getOrNull()
            else -> null
        } ?: return null

        return KvPlayerTrack(
            id = "history_${entity.id}",
            uri = uri,
            title = entity.outputName,
            mimeType = entity.mimeType,
            durationMs = entity.durationMs,
            isAudioOnly = entity.mimeType.startsWith("audio/"),
            sourceType = KvPlayerSourceType.HISTORY
        )
    }

    // ---- Kamaker Watermark Studio & Batch Image Watermarker ----

    fun updateKamakerSpec(transform: (KamakerSpec) -> KamakerSpec) {
        val updated = transform(_kamakerSpec.value)
        _kamakerSpec.value = updated
        if (updated.applyInStudio || _config.value.kamakerSpec != null) {
            syncKamakerToStudioConfig(updated)
        }
        kamakerPersistJob?.cancel()
        kamakerPersistJob = viewModelScope.launch {
            delay(180L)
            repo.saveKamakerSpec(updated)
        }
    }

    fun setApplyKamakerInStudio(enabled: Boolean) {
        updateKamakerSpec { it.copy(applyInStudio = enabled) }
    }

    private fun syncKamakerToStudioConfig(spec: KamakerSpec) {
        val activeForStudio = if (spec.applyInStudio && spec.hasActiveContent) spec else null
        updateConfigAndSync { current ->
            if (current.kamakerSpec == activeForStudio) current
            else current.copy(kamakerSpec = activeForStudio, useCustomCommand = false)
        }
    }

    fun updateKamakerShapeRule(
        shape: KamakerFrameShape,
        transform: (KamakerShapeRule) -> KamakerShapeRule
    ) {
        updateKamakerSpec { current ->
            current.withRuleFor(shape, transform(current.ruleFor(shape)))
        }
    }

    fun resetKamakerShape(shape: KamakerFrameShape) {
        updateKamakerSpec { current ->
            current.withRuleFor(
                shape,
                KamakerSpec.defaultRuleFor(shape, edgeMarginPercent = current.edgeMarginPercent)
            )
        }
        viewModelScope.launch { _snackbarEvents.emit("Reset ${shape.ratioLabel} ${shape.shapeName} placement.") }
    }

    fun copyKamakerRuleToAllShapes(sourceShape: KamakerFrameShape) {
        updateKamakerSpec { current ->
            val src = current.ruleFor(sourceShape)
            current.copy(
                squareRule = src,
                landscapeRule = src,
                portraitRule = src
            )
        }
        viewModelScope.launch { _snackbarEvents.emit("Copied ${sourceShape.ratioLabel} placement to all 3 frame shapes.") }
    }

    fun resetKamakerAll() {
        val fresh = KamakerSpec()
        _kamakerSpec.value = fresh
        syncKamakerToStudioConfig(fresh)
        viewModelScope.launch {
            repo.saveKamakerSpec(fresh)
            _snackbarEvents.emit("Kamaker reset to factory defaults.")
        }
    }

    fun applyKamakerPreset(preset: KamakerPreset) {
        updateKamakerSpec { current -> preset.payload.applyToSpec(current) }
        viewModelScope.launch { _snackbarEvents.emit("Applied Kamaker preset \"${preset.name}\".") }
    }

    fun saveKamakerPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            viewModelScope.launch { _snackbarEvents.emit("Enter a preset name first.") }
            return
        }
        viewModelScope.launch {
            val newPreset = KamakerPreset(
                id = "kamaker_${UUID.randomUUID().toString().take(8)}",
                name = trimmed,
                isBuiltIn = false,
                payload = KamakerPresetPayload.fromSpec(_kamakerSpec.value)
            )
            val updated = (customKamakerPresets.value.filterNot { it.name.equals(trimmed, ignoreCase = true) } + newPreset)
            repo.saveCustomKamakerPresets(updated)
            _snackbarEvents.emit("Saved Kamaker preset \"$trimmed\".")
        }
    }

    fun deleteKamakerPreset(presetId: String) {
        if (KamakerBuiltInPresets.all.any { it.id == presetId }) return
        viewModelScope.launch {
            val updated = customKamakerPresets.value.filterNot { it.id == presetId }
            repo.saveCustomKamakerPresets(updated)
            _snackbarEvents.emit("Deleted custom Kamaker preset.")
        }
    }

    fun refreshKamakerBackdropBitmap() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val kvBmp = kvPlayerController.extractCurrentFramePreviewBitmap(maxEdgePx = 560)
            if (kvBmp != null) {
                _kamakerBackdropBitmap.value = kvBmp
                return@launch
            }
            // Fallback: use active video input from Konvert Studio if Kvplayer has no loaded clip
            val studioVideo = _selectedInputs.value.getOrNull(_activePreviewIndex.value)
                ?: _selectedInputs.value.firstOrNull { !it.isAudioOnly }
            if (studioVideo != null && !studioVideo.isAudioOnly) {
                val fallbackBmp = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, studioVideo.uri)
                        val raw = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: return@withContext null
                        val maxSide = maxOf(raw.width, raw.height)
                        if (maxSide > 560 && maxSide > 0) {
                            val s = 560f / maxSide.toFloat()
                            val scaled = Bitmap.createScaledBitmap(
                                raw,
                                (raw.width * s).toInt().coerceAtLeast(1),
                                (raw.height * s).toInt().coerceAtLeast(1),
                                true
                            )
                            if (scaled !== raw) raw.recycle()
                            scaled
                        } else raw
                    } catch (_: Throwable) {
                        null
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
                _kamakerBackdropBitmap.value = fallbackBmp
            } else {
                _kamakerBackdropBitmap.value = null
            }
        }
    }

    fun importKamakerBatchImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            val inspected = KamakerRenderer.inspectBatchImages(context, uris)
            if (inspected.isEmpty()) {
                _snackbarEvents.emit("No readable images found in selection.")
                return@launch
            }
            val existingUris = _kamakerBatchImages.value.map { it.uri }.toSet()
            val fresh = inspected.filterNot { it.uri in existingUris }
            _kamakerBatchImages.update { it + fresh }
            _snackbarEvents.emit("Imported ${fresh.size} image(s) into Kamaker batch.")
        }
    }

    fun importKamakerBatchFolder(treeUri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val foundUris = KamakerRenderer.scanFolderForImages(context, treeUri)
            if (foundUris.isEmpty()) {
                _snackbarEvents.emit("No image files found inside selected folder.")
                return@launch
            }
            val inspected = KamakerRenderer.inspectBatchImages(context, foundUris)
            val existingUris = _kamakerBatchImages.value.map { it.uri }.toSet()
            val fresh = inspected.filterNot { it.uri in existingUris }
            _kamakerBatchImages.update { it + fresh }
            _snackbarEvents.emit("Imported ${fresh.size} image(s) from folder.")
        }
    }

    fun removeKamakerBatchImage(id: String) {
        _kamakerBatchImages.update { list -> list.filterNot { it.id == id } }
    }

    fun clearKamakerBatchImages() {
        _kamakerBatchImages.value = emptyList()
        _kamakerBatchExportState.value = KamakerBatchExportState()
    }

    fun setKamakerRenamePrefix(prefix: String) {
        _kamakerRenamePrefix.value = prefix
    }

    fun setKamakerExportFormat(format: KamakerExportFormat) {
        _kamakerExportFormat.value = format
    }

    fun exportKamakerBatchFiles() {
        val items = _kamakerBatchImages.value
        if (items.isEmpty()) {
            viewModelScope.launch { _snackbarEvents.emit("Import images or a folder first.") }
            return
        }
        val spec = _kamakerSpec.value
        val context = getApplication<Application>()
        viewModelScope.launch {
            _kamakerBatchExportState.value = KamakerBatchExportState(
                isExporting = true,
                completedCount = 0,
                totalCount = items.size,
                lastStatusMessage = "Stamping 0/${items.size}..."
            )
            val res = KamakerRenderer.exportBatchImagesToFiles(
                context = context,
                items = items,
                spec = spec,
                renamePrefix = _kamakerRenamePrefix.value,
                exportFormat = _kamakerExportFormat.value,
                onProgress = { done, total ->
                    _kamakerBatchExportState.value = KamakerBatchExportState(
                        isExporting = true,
                        completedCount = done,
                        totalCount = total,
                        lastStatusMessage = "Stamping $done/$total..."
                    )
                }
            )
            val msg = "Exported ${res.exportedCount}/${res.totalCount} file(s) to ${res.folderSummary}."
            _kamakerBatchExportState.value = KamakerBatchExportState(
                isExporting = false,
                completedCount = res.exportedCount,
                totalCount = res.totalCount,
                lastStatusMessage = msg
            )
            _snackbarEvents.emit(msg)
        }
    }

    fun exportKamakerBatchZip(onReadyToShareZip: (Uri, String) -> Unit = { _, _ -> }) {
        val items = _kamakerBatchImages.value
        if (items.isEmpty()) {
            viewModelScope.launch { _snackbarEvents.emit("Import images or a folder first.") }
            return
        }
        val spec = _kamakerSpec.value
        val context = getApplication<Application>()
        viewModelScope.launch {
            _kamakerBatchExportState.value = KamakerBatchExportState(
                isExporting = true,
                completedCount = 0,
                totalCount = items.size,
                lastStatusMessage = "Zipping 0/${items.size}..."
            )
            val res = KamakerRenderer.exportBatchImagesToZip(
                context = context,
                items = items,
                spec = spec,
                renamePrefix = _kamakerRenamePrefix.value,
                exportFormat = _kamakerExportFormat.value,
                onProgress = { done, total ->
                    _kamakerBatchExportState.value = KamakerBatchExportState(
                        isExporting = true,
                        completedCount = done,
                        totalCount = total,
                        lastStatusMessage = "Zipping $done/$total..."
                    )
                }
            )
            val msg = if (res.stampedCount > 0) {
                "Exported ${res.stampedCount} stamped image(s) into ${res.fileName} (Downloads/KonvertVideo)."
            } else {
                "Unable to build ZIP archive."
            }
            _kamakerBatchExportState.value = KamakerBatchExportState(
                isExporting = false,
                completedCount = res.stampedCount,
                totalCount = items.size,
                lastStatusMessage = msg
            )
            _snackbarEvents.emit(msg)
            res.shareUri?.let { uri -> onReadyToShareZip(uri, res.fileName) }
        }
    }
}
