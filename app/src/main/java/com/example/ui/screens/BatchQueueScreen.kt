package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BatchJob
import com.example.domain.model.JobStatus
import com.example.ui.components.PulsingSignalBadge
import com.example.ui.components.StudioSurfaceCard
import com.example.ui.components.TweenProgressRing
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import com.example.ui.util.FormatUtils

@Composable
fun BatchQueueScreen(
    widthSizeClass: WindowWidthSizeClass,
    jobs: List<BatchJob>,
    keepScreenOn: Boolean,
    parallelWorkers: Int = 1,
    onCancelJob: (String) -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val hasActiveJobs = jobs.any { it.status == JobStatus.RUNNING || it.status == JobStatus.QUEUED }
    val view = LocalView.current
    DisposableEffect(hasActiveJobs, keepScreenOn) {
        if (hasActiveJobs && keepScreenOn) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.queue_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val runningCount = jobs.count { it.status == JobStatus.RUNNING }
                val queuedCount = jobs.count { it.status == JobStatus.QUEUED }
                val doneCount = jobs.count { it.status == JobStatus.COMPLETED }
                val parallelLabel = if (parallelWorkers > 1) " • ${parallelWorkers}x parallel" else ""
                Text(
                    text = "$runningCount running • $queuedCount queued • $doneCount completed$parallelLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasActiveJobs) {
                    OutlinedButton(
                        onClick = onCancelAll,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("cancel_all_jobs_button")
                    ) {
                        Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop All")
                    }
                }
                if (jobs.any { it.status != JobStatus.RUNNING && it.status != JobStatus.QUEUED }) {
                    OutlinedButton(
                        onClick = onClearFinished,
                        modifier = Modifier.testTag("clear_finished_jobs_button")
                    ) {
                        Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.clear_finished))
                    }
                }
            }
        }

        if (jobs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                StudioSurfaceCard(modifier = Modifier.padding(24.dp)) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = stringResource(R.string.queue_empty_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.queue_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(jobs, key = { it.id }) { job ->
                    BatchJobCard(
                        job = job,
                        onCancel = { onCancelJob(job.id) },
                        onOpenHistory = onOpenHistory
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchJobCard(
    job: BatchJob,
    onCancel: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val studioColors = LocalStudioColors.current
    var logsExpanded by rememberSaveable(job.id) { mutableStateOf(false) }

    val statusColor = when (job.status) {
        JobStatus.RUNNING -> MaterialTheme.colorScheme.secondary
        JobStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
        JobStatus.COMPLETED -> studioColors.success
        JobStatus.FAILED -> MaterialTheme.colorScheme.error
        JobStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    StudioSurfaceCard(
        highlighted = job.status == JobStatus.RUNNING,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_job_${job.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (job.status) {
                    JobStatus.RUNNING -> TweenProgressRing(progress = job.progress, size = 56.dp)
                    JobStatus.COMPLETED -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Completed",
                        tint = studioColors.success,
                        modifier = Modifier.size(40.dp)
                    )
                    JobStatus.FAILED -> Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.HourglassEmpty,
                        contentDescription = job.status.name,
                        tint = statusColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.outputFileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "From ${job.input.displayName} • ${job.config.videoCodec.ffmpegName} / ${job.config.audioCodec.ffmpegName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingSignalBadge(
                            label = job.status.name,
                            active = job.status == JobStatus.RUNNING,
                            color = statusColor
                        )
                        if (job.status == JobStatus.RUNNING && job.speedMultiplier > 0.0) {
                            Text(
                                text = String.format("%.2fx • %.0f fps", job.speedMultiplier, job.currentFps),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (job.status == JobStatus.COMPLETED && job.outputSizeBytes != null) {
                            Text(
                                text = "${FormatUtils.formatBytes(job.outputSizeBytes)} (${FormatUtils.formatDuration(job.elapsedWallMs)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = studioColors.success
                            )
                        }
                    }
                }

                if (job.status == JobStatus.RUNNING || job.status == JobStatus.QUEUED) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                } else if (job.status == JobStatus.COMPLETED) {
                    TextButton(onClick = onOpenHistory) {
                        Text("History")
                    }
                }
            }

            if (job.status == JobStatus.RUNNING) {
                LinearProgressIndicator(
                    progress = { job.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }

            job.errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { logsExpanded = !logsExpanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (logsExpanded) "Hide FFmpeg Console" else "Show FFmpeg Console (${job.logs.size})")
                }
            }

            AnimatedVisibility(
                visible = logsExpanded,
                enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, studioColors.hairlineBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = job.commandPreview,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        val tail = job.logs.takeLast(14)
                        if (tail.isEmpty()) {
                            Text(
                                text = "Waiting for FFmpeg output…",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            tail.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
