package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.example.KonvertApplication
import com.example.MainActivity
import com.example.R
import com.example.data.local.ConversionHistoryEntity
import com.example.domain.model.BatchJob
import com.example.domain.model.JobStatus
import com.example.domain.model.StudioSettings
import com.example.ffmpeg.AudioArtworkGenerator
import com.example.ffmpeg.FFmpegCommandBuilder
import com.example.ffmpeg.KamakerRenderer
import com.example.ffmpeg.MediaExportHelper
import com.example.ffmpeg.OverlayBitmapRenderer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ConversionForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val initialNotification = buildNotification(
            title = getString(R.string.app_name),
            content = "Preparing offline FFmpeg engine…",
            progress = 0,
            indeterminate = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, initialNotification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        if (workerJob == null || workerJob?.isActive != true) {
            workerJob = serviceScope.launch {
                try {
                    processQueue()
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        runCatching {
            val repo = (application as KonvertApplication).repository
            repo.cancelAllActive()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val lastProgressNotifMs = AtomicLong(0L)

    private suspend fun processQueue() {
        val app = application as KonvertApplication
        val repo = app.repository
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val completedInBatch = AtomicInteger(0)
        val failedInBatch = AtomicInteger(0)

        while (true) {
            val initialSettings = repo.settings.first()
            val workerCount = computeSmartWorkerCount(
                limitSetting = initialSettings.smartParallelLimit,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                lightweightMode = initialSettings.lightweightEngineMode
            )

            val firstCheck = repo.queueJobs.value.any { it.status == JobStatus.QUEUED }
            if (!firstCheck) break

            coroutineScope {
                repeat(workerCount) {
                    launch(Dispatchers.IO) {
                        while (isActive) {
                            val nextJob = repo.claimNextQueuedJob() ?: break
                            val currentSettings = repo.settings.first()
                            try {
                                executeSingleJob(nextJob, currentSettings, nm, repo)
                                val endStatus = repo.queueJobs.value.firstOrNull { it.id == nextJob.id }?.status
                                if (endStatus == JobStatus.COMPLETED) {
                                    completedInBatch.incrementAndGet()
                                } else if (endStatus == JobStatus.FAILED) {
                                    failedInBatch.incrementAndGet()
                                }
                            } catch (t: Throwable) {
                                failedInBatch.incrementAndGet()
                                repo.updateJob(nextJob.id) {
                                    it.copy(
                                        status = JobStatus.FAILED,
                                        errorMessage = t.localizedMessage ?: "Unexpected conversion error"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val completed = completedInBatch.get()
        val failed = failedInBatch.get()
        if (completed > 0 || failed > 0) {
            val finalSettings = repo.settings.first()
            notifyBatchFinished(
                nm = nm,
                completedCount = completed,
                failedCount = failed,
                playSound = finalSettings.playBatchFinishSound
            )
        }
    }

    private suspend fun executeSingleJob(
        job: BatchJob,
        settings: StudioSettings,
        nm: NotificationManager,
        repo: com.example.data.repository.ConversionRepository
    ) {
        val startWallMs = System.currentTimeMillis()
        val outputDir = MediaExportHelper.getAppOutputDirectory(applicationContext)
        val outputFile = File(outputDir, job.outputFileName)
        if (outputFile.exists()) outputFile.delete()

        val artworkFile: File? = if (FFmpegCommandBuilder.hasActiveEmbeddedArtwork(job.config)) {
            AudioArtworkGenerator.prepareArtworkFile(
                context = applicationContext,
                selection = job.config.audioArtwork,
                jobId = job.id
            )
        } else null

        val overlayFile: File? = if (FFmpegCommandBuilder.hasActiveVideoOverlay(job.config)) {
            val (canvasW, canvasH) = OverlayBitmapRenderer.resolveOverlayCanvasSize(
                sourceWidth = job.input.width,
                sourceHeight = job.input.height,
                crop = job.config.cropRegion,
                targetWidth = job.config.effectiveTargetWidth
            )
            val dest = File(File(applicationContext.cacheDir, "overlays"), "overlay_${job.id}.png")
            runCatching {
                KamakerRenderer.renderCombinedOverlayPng(
                    context = applicationContext,
                    kamakerSpec = job.config.kamakerSpec,
                    videoOverlayConfig = job.config.overlayConfig,
                    canvasWidth = canvasW,
                    canvasHeight = canvasH,
                    destFile = dest
                )
            }.getOrNull()
        } else null

        try {
            val inputSafPath = runCatching {
                FFmpegKitConfig.getSafParameterForRead(applicationContext, job.input.uri)
            }.getOrElse { job.input.uri.toString() }

            val args = FFmpegCommandBuilder.buildExecutionArguments(
                inputPath = inputSafPath,
                outputPath = outputFile.absolutePath,
                config = job.config,
                hardwareThreads = settings.hardwareThreads,
                artworkPath = artworkFile?.absolutePath,
                overlayPath = overlayFile?.absolutePath
            )

            repo.updateJob(job.id) {
                it.copy(
                    status = JobStatus.RUNNING,
                    progress = 0f,
                    outputFilePath = outputFile.absolutePath
                )
            }

            val completion = CompletableDeferred<Unit>()
            val trimStart = job.config.trimStartMs?.coerceAtLeast(0L) ?: 0L
            val trimEnd = job.config.trimEndMs?.takeIf { it > trimStart } ?: job.input.durationMs
            val effectiveDurationMs = if (job.config.hasActiveTrim && trimEnd > trimStart) {
                (trimEnd - trimStart).coerceAtLeast(1L)
            } else {
                job.input.durationMs.coerceAtLeast(1L)
            }
            val durationMs = effectiveDurationMs
            val pendingLogs = ConcurrentLinkedQueue<String>()
            val latestStats = AtomicReference<Statistics?>(null)
            val maxLogLines = if (settings.lightweightEngineMode) 25 else 100
            val throttleMs = if (settings.lowOverheadUi) 600L else 250L

            val flushJob = serviceScope.launch {
                var lastNotifMs = 0L
                while (isActive) {
                    delay(throttleMs)
                    val drainedLogs = mutableListOf<String>()
                    while (true) {
                        val line = pendingLogs.poll() ?: break
                        drainedLogs.add(line)
                    }
                    val stats = latestStats.getAndSet(null)
                    val wallElapsed = System.currentTimeMillis() - startWallMs

                    var progressVal: Float? = null
                    var speedVal: Double? = null
                    var fpsVal: Float? = null
                    var processedMsVal: Long? = null

                    if (stats != null) {
                        val processedMs = stats.time.toLong().coerceAtLeast(0L)
                        val fraction = if (job.input.durationMs > 0L) {
                            (processedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.99f)
                        } else {
                            0.5f
                        }
                        progressVal = fraction
                        speedVal = stats.speed
                        fpsVal = stats.videoFps
                        processedMsVal = processedMs

                        val now = System.currentTimeMillis()
                        val prev = lastProgressNotifMs.get()
                        if (now - prev > 750L && lastProgressNotifMs.compareAndSet(prev, now)) {
                            updateAggregateNotification(nm, repo)
                        }
                    }

                    if (drainedLogs.isNotEmpty() || stats != null) {
                        repo.flushJobTelemetry(
                            jobId = job.id,
                            newLogs = drainedLogs,
                            maxLogLines = maxLogLines,
                            progress = progressVal,
                            speedMultiplier = speedVal,
                            currentFps = fpsVal,
                            processedTimeMs = processedMsVal,
                            elapsedWallMs = wallElapsed
                        )
                    }
                }
            }

            val session = FFmpegKit.executeWithArgumentsAsync(
                args,
                { completedSession ->
                    flushJob.cancel()
                    val remainingLogs = mutableListOf<String>()
                    while (true) {
                        val line = pendingLogs.poll() ?: break
                        remainingLogs.add(line)
                    }
                    val rc = completedSession.returnCode
                    val wallElapsed = System.currentTimeMillis() - startWallMs
                    val currentJobState = repo.queueJobs.value.firstOrNull { it.id == job.id }

                    if (ReturnCode.isCancel(rc) || currentJobState?.status == JobStatus.CANCELLED) {
                        if (outputFile.exists()) outputFile.delete()
                        repo.updateJob(job.id) {
                            it.copy(
                                status = JobStatus.CANCELLED,
                                elapsedWallMs = wallElapsed,
                                logs = (it.logs + remainingLogs).takeLast(maxLogLines),
                                errorMessage = "Cancelled"
                            )
                        }
                    } else if (ReturnCode.isSuccess(rc) && outputFile.exists()) {
                        val finalSize = outputFile.length()
                        repo.updateJob(job.id) {
                            it.copy(
                                status = JobStatus.COMPLETED,
                                progress = 1f,
                                elapsedWallMs = wallElapsed,
                                outputSizeBytes = finalSize,
                                outputFilePath = outputFile.absolutePath,
                                logs = (it.logs + remainingLogs).takeLast(maxLogLines)
                            )
                        }
                    } else {
                        if (outputFile.exists()) outputFile.delete()
                        val tailErr = completedSession.failStackTrace?.lineSequence()?.lastOrNull { it.isNotBlank() }
                            ?: completedSession.allLogsAsString?.lineSequence()?.lastOrNull { it.isNotBlank() }
                            ?: "FFmpeg exited with code ${rc?.value ?: -1}"
                        repo.updateJob(job.id) {
                            it.copy(
                                status = JobStatus.FAILED,
                                elapsedWallMs = wallElapsed,
                                logs = (it.logs + remainingLogs).takeLast(maxLogLines),
                                errorMessage = tailErr
                            )
                        }
                    }
                    completion.complete(Unit)
                },
                { log ->
                    val msg = log.message?.trimEnd()
                    if (!msg.isNullOrBlank()) {
                        pendingLogs.offer(msg)
                    }
                },
                { stats ->
                    latestStats.set(stats)
                }
            )

            repo.updateJob(job.id) { it.copy(ffmpegSessionId = session.sessionId) }
            completion.await()

            val finishedJob = repo.queueJobs.value.firstOrNull { it.id == job.id } ?: return
            var exportedUriString: String? = null
            if (finishedJob.status == JobStatus.COMPLETED && settings.autoExportToGallery && outputFile.exists()) {
                val exportedUri = MediaExportHelper.exportToPublicMediaStore(
                    context = applicationContext,
                    sourceFile = outputFile,
                    displayName = job.outputFileName,
                    mimeType = job.config.container.mimeType,
                    isAudioOnly = job.config.isAudioExport
                )
                exportedUriString = exportedUri?.toString()
                if (exportedUriString != null) {
                    if (settings.autoPruneTempCache) {
                        runCatching { outputFile.delete() }
                    }
                    repo.updateJob(job.id) {
                        it.copy(
                            outputGalleryUri = exportedUriString,
                            outputFilePath = if (settings.autoPruneTempCache) "" else outputFile.absolutePath
                        )
                    }
                }
            }

            if (finishedJob.status == JobStatus.COMPLETED || finishedJob.status == JobStatus.FAILED) {
                repo.insertHistoryRecord(
                    ConversionHistoryEntity(
                        sourceName = job.input.displayName,
                        outputName = job.outputFileName,
                        outputFilePath = if (settings.autoPruneTempCache && exportedUriString != null) "" else outputFile.absolutePath,
                        outputGalleryUri = exportedUriString,
                        containerId = job.config.container.id,
                        containerExtension = job.config.container.extension,
                        mimeType = job.config.container.mimeType,
                        videoCodec = job.config.videoCodec.ffmpegName,
                        audioCodec = job.config.audioCodec.ffmpegName,
                        inputSizeBytes = job.input.sizeBytes,
                        outputSizeBytes = finishedJob.outputSizeBytes ?: 0L,
                        durationMs = if (job.config.hasActiveTrim) effectiveDurationMs else job.input.durationMs,
                        processingTimeMs = finishedJob.elapsedWallMs,
                        status = finishedJob.status.name,
                        commandUsed = job.commandPreview,
                        errorMessage = finishedJob.errorMessage
                    )
                )
            }
        } finally {
            artworkFile?.let { runCatching { if (it.exists()) it.delete() } }
            overlayFile?.let { runCatching { if (it.exists()) it.delete() } }
        }
    }

    private fun updateAggregateNotification(
        nm: NotificationManager,
        repo: com.example.data.repository.ConversionRepository
    ) {
        val jobs = repo.queueJobs.value
        val running = jobs.filter { it.status == JobStatus.RUNNING }
        if (running.isEmpty()) return
        val queuedCount = jobs.count { it.status == JobStatus.QUEUED }

        val title: String
        val content: String
        val pct: Int
        val indeterminate: Boolean

        if (running.size == 1) {
            val single = running.first()
            pct = (single.progress * 100f).toInt().coerceIn(0, 100)
            indeterminate = single.input.durationMs <= 0L
            val speedStr = if (single.speedMultiplier > 0.0) String.format("%.1fx", single.speedMultiplier) else ""
            title = "Converting ${single.input.displayName}"
            content = buildString {
                append("$pct%")
                if (speedStr.isNotEmpty()) append(" • $speedStr speed")
                if (queuedCount > 0) append(" • $queuedCount queued")
            }
        } else {
            val avg = running.map { it.progress }.average().toFloat()
            pct = (avg * 100f).toInt().coerceIn(0, 100)
            indeterminate = false
            title = "Parallel Converting (${running.size} active)"
            content = "$pct% avg" + if (queuedCount > 0) " • $queuedCount queued" else ""
        }

        nm.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = title,
                content = content,
                progress = pct,
                indeterminate = indeterminate
            )
        )
    }

    private fun notifyBatchFinished(
        nm: NotificationManager,
        completedCount: Int,
        failedCount: Int,
        playSound: Boolean
    ) {
        val channelId = if (playSound) CHANNEL_COMPLETE_SOUND_ID else CHANNEL_COMPLETE_SILENT_ID
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (failedCount == 0) {
            getString(R.string.notif_batch_complete_title)
        } else {
            getString(R.string.notif_batch_finished_issues_title)
        }
        val summary = buildString {
            if (completedCount > 0) append("$completedCount completed")
            if (failedCount > 0) {
                if (isNotEmpty()) append(" • ")
                append("$failedCount failed")
            }
        }.ifBlank { "Queue finished" }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summary)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .setSilent(!playSound)
            .apply {
                if (playSound && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                }
            }
            .build()

        runCatching { nm.notify(COMPLETE_NOTIFICATION_ID, notification) }

        if (playSound && !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(applicationContext, soundUri)?.play()
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notif_channel_desc)
                }
                nm.createNotificationChannel(channel)
            }
            if (nm.getNotificationChannel(CHANNEL_COMPLETE_SOUND_ID) == null) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val completeChannel = NotificationChannel(
                    CHANNEL_COMPLETE_SOUND_ID,
                    getString(R.string.notif_complete_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notif_complete_channel_desc)
                    setSound(soundUri, attrs)
                }
                nm.createNotificationChannel(completeChannel)
            }
            if (nm.getNotificationChannel(CHANNEL_COMPLETE_SILENT_ID) == null) {
                val silentChannel = NotificationChannel(
                    CHANNEL_COMPLETE_SILENT_ID,
                    getString(R.string.notif_complete_silent_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                }
                nm.createNotificationChannel(silentChannel)
            }
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        indeterminate: Boolean
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "konvert_media_processing"
        const val CHANNEL_COMPLETE_SOUND_ID = "konvert_batch_complete_sound"
        const val CHANNEL_COMPLETE_SILENT_ID = "konvert_batch_complete_silent"
        const val NOTIFICATION_ID = 4102
        const val COMPLETE_NOTIFICATION_ID = 4103
        const val ACTION_START_QUEUE = "com.example.action.START_QUEUE"

        fun computeSmartWorkerCount(
            limitSetting: Int,
            cpuCores: Int = Runtime.getRuntime().availableProcessors(),
            lightweightMode: Boolean = false
        ): Int {
            if (limitSetting in 1..4) return limitSetting
            val auto = when {
                cpuCores >= 8 -> 4
                cpuCores >= 6 -> 3
                cpuCores >= 4 -> 2
                else -> 1
            }
            return if (lightweightMode) auto.coerceAtMost(2) else auto.coerceIn(1, 4)
        }
    }
}
