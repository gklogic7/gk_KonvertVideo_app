package com.example.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.example.data.local.ConversionHistoryDao
import com.example.data.local.ConversionHistoryEntity
import com.example.data.local.CustomPresetDao
import com.example.data.local.CustomPresetEntity
import com.example.data.local.SettingsDataStore
import com.example.domain.model.AudioCodecOption
import com.example.domain.model.BatchJob
import com.example.domain.model.ContainerFormat
import com.example.domain.model.ConversionConfig
import com.example.domain.model.ConversionPreset
import com.example.domain.model.JobStatus
import com.example.domain.model.KamakerPreset
import com.example.domain.model.KamakerSpec
import com.example.domain.model.MediaInputItem
import com.example.domain.model.ResolutionOption
import com.example.domain.model.StudioSettings
import com.example.domain.model.VideoCodecOption
import com.example.ffmpeg.CodecCatalogProvider
import com.example.ffmpeg.FFmpegCommandBuilder
import com.example.ffmpeg.RuntimeEngineInfo
import com.example.service.ConversionForegroundService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

class ConversionRepository(
    private val appContext: Context,
    private val historyDao: ConversionHistoryDao,
    private val customPresetDao: CustomPresetDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val _queueJobs = MutableStateFlow<List<BatchJob>>(emptyList())
    val queueJobs: StateFlow<List<BatchJob>> = _queueJobs.asStateFlow()

    val settings: Flow<StudioSettings> = settingsDataStore.settingsFlow
    val kamakerSpec: Flow<KamakerSpec> = settingsDataStore.kamakerSpecFlow
    val customKamakerPresets: Flow<List<KamakerPreset>> = settingsDataStore.customKamakerPresetsFlow
    val history: Flow<List<ConversionHistoryEntity>> = historyDao.observeHistory()

    val customPresets: Flow<List<ConversionPreset>> = customPresetDao.observeCustomPresets().map { list ->
        list.map { entity ->
            val container = ContainerFormat.entries.firstOrNull { it.id == entity.containerId } ?: ContainerFormat.MP4
            val vCodec = VideoCodecOption.entries.firstOrNull { it.id == entity.videoCodecId } ?: VideoCodecOption.H264
            val aCodec = AudioCodecOption.entries.firstOrNull { it.id == entity.audioCodecId } ?: AudioCodecOption.AAC
            val res = ResolutionOption.entries.firstOrNull { it.name == entity.resolutionName } ?: ResolutionOption.ORIGINAL
            ConversionPreset(
                id = entity.id,
                name = entity.name,
                subtitle = "${container.extension.uppercase()} • ${vCodec.ffmpegName} • ${aCodec.ffmpegName}",
                badge = "CUSTOM",
                container = container,
                videoCodec = vCodec,
                audioCodec = aCodec,
                resolution = res,
                crf = entity.crf,
                audioBitrateKbps = entity.audioBitrateKbps,
                fps = entity.fps,
                isCustom = true
            )
        }
    }

    private val _engineInfo = MutableStateFlow<RuntimeEngineInfo?>(null)
    val engineInfo: StateFlow<RuntimeEngineInfo?> = _engineInfo.asStateFlow()

    suspend fun loadEngineCapabilitiesIfNeeded() {
        if (_engineInfo.value == null) {
            _engineInfo.value = CodecCatalogProvider.inspectRuntimeCapabilities()
        }
    }

    suspend fun refreshEngineCapabilities() {
        _engineInfo.value = CodecCatalogProvider.inspectRuntimeCapabilities()
    }

    fun enqueueBatch(
        inputs: List<MediaInputItem>,
        config: ConversionConfig,
        hardwareThreads: Int
    ) {
        if (inputs.isEmpty()) return
        val newJobs = inputs.map { input ->
            val baseName = input.displayName.substringBeforeLast('.').ifBlank { "output" }
            val uniqueSuffix = System.currentTimeMillis().toString().takeLast(4)
            val outputName = "${baseName}_konvert_${uniqueSuffix}.${config.container.extension}"
            val previewCmd = FFmpegCommandBuilder.buildFullPreviewCommand(
                inputName = input.displayName,
                outputName = outputName,
                config = config,
                hardwareThreads = hardwareThreads
            )
            BatchJob(
                id = UUID.randomUUID().toString(),
                input = input,
                config = config,
                commandPreview = previewCmd,
                outputFileName = outputName,
                status = JobStatus.QUEUED
            )
        }
        _queueJobs.update { current -> current + newJobs }
        startServiceIfNeeded()
    }

    private fun startServiceIfNeeded() {
        val intent = Intent(appContext, ConversionForegroundService::class.java).apply {
            action = ConversionForegroundService.ACTION_START_QUEUE
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, intent)
        }
    }

    @Synchronized
    fun claimNextQueuedJob(): BatchJob? {
        val next = _queueJobs.value.firstOrNull { it.status == JobStatus.QUEUED } ?: return null
        val claimed = next.copy(status = JobStatus.RUNNING, progress = 0f)
        _queueJobs.update { jobs ->
            jobs.map { if (it.id == next.id) claimed else it }
        }
        return claimed
    }

    fun updateJob(jobId: String, transform: (BatchJob) -> BatchJob) {
        _queueJobs.update { jobs ->
            jobs.map { if (it.id == jobId) transform(it) else it }
        }
    }

    fun flushJobTelemetry(
        jobId: String,
        newLogs: List<String>,
        maxLogLines: Int,
        progress: Float?,
        speedMultiplier: Double?,
        currentFps: Float?,
        processedTimeMs: Long?,
        elapsedWallMs: Long?
    ) {
        if (newLogs.isEmpty() && progress == null) return
        _queueJobs.update { jobs ->
            jobs.map { job ->
                if (job.id == jobId && job.status == JobStatus.RUNNING) {
                    val combinedLogs = if (newLogs.isNotEmpty()) {
                        (job.logs + newLogs).takeLast(maxLogLines)
                    } else {
                        job.logs
                    }
                    job.copy(
                        logs = combinedLogs,
                        progress = progress ?: job.progress,
                        speedMultiplier = speedMultiplier ?: job.speedMultiplier,
                        currentFps = currentFps ?: job.currentFps,
                        processedTimeMs = processedTimeMs ?: job.processedTimeMs,
                        elapsedWallMs = elapsedWallMs ?: job.elapsedWallMs
                    )
                } else {
                    job
                }
            }
        }
    }

    fun cancelJob(jobId: String) {
        val target = _queueJobs.value.firstOrNull { it.id == jobId } ?: return
        if (target.status == JobStatus.RUNNING && target.ffmpegSessionId != null) {
            runCatching { FFmpegKit.cancel(target.ffmpegSessionId) }
        }
        updateJob(jobId) {
            it.copy(status = JobStatus.CANCELLED, errorMessage = "Cancelled by user")
        }
    }

    fun cancelAllActive() {
        val jobs = _queueJobs.value
        jobs.filter { it.status == JobStatus.RUNNING }.forEach { job ->
            job.ffmpegSessionId?.let { runCatching { FFmpegKit.cancel(it) } }
        }
        _queueJobs.update { current ->
            current.map { job ->
                if (job.status == JobStatus.QUEUED || job.status == JobStatus.RUNNING) {
                    job.copy(status = JobStatus.CANCELLED, errorMessage = "Batch cancelled")
                } else {
                    job
                }
            }
        }
    }

    fun clearFinishedJobs() {
        _queueJobs.update { current ->
            current.filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
        }
    }

    suspend fun saveCustomPreset(name: String, config: ConversionConfig) {
        val entity = CustomPresetEntity(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.trim().ifBlank { "Custom Preset" },
            containerId = config.container.id,
            videoCodecId = config.videoCodec.id,
            audioCodecId = config.audioCodec.id,
            resolutionName = config.resolution.name,
            crf = config.crf,
            audioBitrateKbps = config.audioBitrateKbps,
            fps = config.fps
        )
        customPresetDao.insertPreset(entity)
    }

    suspend fun deleteCustomPreset(id: String) {
        customPresetDao.deletePreset(id)
    }

    suspend fun insertHistoryRecord(entity: ConversionHistoryEntity) {
        historyDao.insertRecord(entity)
    }

    suspend fun deleteHistoryRecord(entity: ConversionHistoryEntity) {
        historyDao.deleteById(entity.id)
        runCatching {
            val file = File(entity.outputFilePath)
            if (file.exists()) file.delete()
        }
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    suspend fun setShowQuickPresets(enabled: Boolean) = settingsDataStore.setShowQuickPresets(enabled)
    suspend fun setShowProControls(enabled: Boolean) = settingsDataStore.setShowProControls(enabled)
    suspend fun setShowCommandWorkbench(enabled: Boolean) = settingsDataStore.setShowCommandWorkbench(enabled)
    suspend fun setAutoExportToGallery(enabled: Boolean) = settingsDataStore.setAutoExportToGallery(enabled)
    suspend fun setKeepScreenOn(enabled: Boolean) = settingsDataStore.setKeepScreenOn(enabled)
    suspend fun setHardwareThreads(threads: Int) = settingsDataStore.setHardwareThreads(threads)
    suspend fun setLightweightEngineMode(enabled: Boolean) = settingsDataStore.setLightweightEngineMode(enabled)
    suspend fun setLowOverheadUi(enabled: Boolean) = settingsDataStore.setLowOverheadUi(enabled)
    suspend fun setAutoPruneTempCache(enabled: Boolean) = settingsDataStore.setAutoPruneTempCache(enabled)
    suspend fun setEnableCropOverlay(enabled: Boolean) = settingsDataStore.setEnableCropOverlay(enabled)
    suspend fun setEnableInlineCrop(enabled: Boolean) = settingsDataStore.setEnableInlineCrop(enabled)
    suspend fun setEnableFullscreenCrop(enabled: Boolean) = settingsDataStore.setEnableFullscreenCrop(enabled)
    suspend fun setUseKvPlayerInPrecisionCrop(enabled: Boolean) = settingsDataStore.setUseKvPlayerInPrecisionCrop(enabled)
    suspend fun setPrecisionCropPanelStyle(style: com.example.domain.model.PrecisionCropPanelStyle) = settingsDataStore.setPrecisionCropPanelStyle(style)
    suspend fun setPrecisionCropFullKvHud(enabled: Boolean) = settingsDataStore.setPrecisionCropFullKvHud(enabled)
    suspend fun setEnableStudioMarkInOut(enabled: Boolean) = settingsDataStore.setEnableStudioMarkInOut(enabled)
    suspend fun setSmartParallelLimit(limit: Int) = settingsDataStore.setSmartParallelLimit(limit)
    suspend fun setPlayBatchFinishSound(enabled: Boolean) = settingsDataStore.setPlayBatchFinishSound(enabled)
    suspend fun setKvShowFullscreenButton(enabled: Boolean) = settingsDataStore.setKvShowFullscreenButton(enabled)
    suspend fun setKvShowScreenshotButton(enabled: Boolean) = settingsDataStore.setKvShowScreenshotButton(enabled)
    suspend fun setKvShowHideHudUiButton(enabled: Boolean) = settingsDataStore.setKvShowHideHudUiButton(enabled)
    suspend fun setKvShowMarkInOut(enabled: Boolean) = settingsDataStore.setKvShowMarkInOut(enabled)
    suspend fun setKvShowFloatingButton(enabled: Boolean) = settingsDataStore.setKvShowFloatingButton(enabled)
    suspend fun setKvHideBottomTiles(enabled: Boolean) = settingsDataStore.setKvHideBottomTiles(enabled)
    suspend fun enableAllStudioModes() = settingsDataStore.enableAllStudioModes()
    suspend fun saveKamakerSpec(spec: KamakerSpec) = settingsDataStore.saveKamakerSpec(spec)
    suspend fun saveCustomKamakerPresets(presets: List<KamakerPreset>) = settingsDataStore.saveCustomKamakerPresets(presets)
}


