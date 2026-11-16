package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConversionHistoryEntity
import com.example.domain.model.BatchJob
import com.example.domain.model.JobStatus
import com.example.ffmpeg.MediaExportHelper
import com.example.ui.components.MediaPreviewPlayer
import com.example.ui.components.StudioSurfaceCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class HistorySubtab(val label: String) {
    HISTORY("History"),
    QUEUE("Queue")
}

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    AUDIO("Audio"),
    FAILED("Failed")
}

@Composable
fun HistoryScreen(
    widthSizeClass: WindowWidthSizeClass,
    items: List<ConversionHistoryEntity>,
    onDeleteRecord: (ConversionHistoryEntity) -> Unit,
    onClearAll: () -> Unit,
    onReuseConfig: (ConversionHistoryEntity) -> Unit,
    onPlayInKvPlayer: (ConversionHistoryEntity) -> Unit,
    onPlayMultipleInKvPlayer: (List<ConversionHistoryEntity>) -> Unit = {},
    onSendMultipleToStudio: (List<ConversionHistoryEntity>) -> Unit = {},
    onDeleteMultiple: (List<ConversionHistoryEntity>) -> Unit = {},
    onShowMessage: (String) -> Unit,
    openQueueSignal: Int = 0,
    queueJobs: List<BatchJob> = emptyList(),
    keepScreenOn: Boolean = false,
    parallelWorkers: Int = 1,
    onCancelJob: (String) -> Unit = {},
    onCancelAll: () -> Unit = {},
    onClearFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val studioColors = LocalStudioColors.current
    var selectedSubtab by rememberSaveable { mutableStateOf(HistorySubtab.HISTORY) }
    LaunchedEffect(openQueueSignal) {
        if (openQueueSignal > 0) {
            selectedSubtab = HistorySubtab.QUEUE
        }
    }

    var activeFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var selectedRecordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(items) {
        val validIds = items.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(validIds)
        if (items.isEmpty()) isSelectionMode = false
    }

    val filtered = remember(items, activeFilter) {
        items.filter { record ->
            when (activeFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.VIDEO -> record.mimeType.startsWith("video/") || record.mimeType.startsWith("image/")
                HistoryFilter.AUDIO -> record.mimeType.startsWith("audio/")
                HistoryFilter.FAILED -> record.status != "COMPLETED"
            }
        }
    }

    val selectedRecords = remember(filtered, items, selectedIds) {
        items.filter { it.id in selectedIds }
    }
    val selectedRecord = filtered.firstOrNull { it.id == selectedRecordId } ?: filtered.firstOrNull()
    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact
    val activeQueueCount = remember(queueJobs) {
        queueJobs.count { it.status == JobStatus.RUNNING || it.status == JobStatus.QUEUED }
    }

    val toggleSelection: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 12.dp)
        ) {
            HistorySubtab.entries.forEachIndexed { index, tab ->
                val tabLabel = when (tab) {
                    HistorySubtab.HISTORY -> "${tab.label} (${items.size})"
                    HistorySubtab.QUEUE -> if (activeQueueCount > 0) "${tab.label} ($activeQueueCount)" else tab.label
                }
                SegmentedButton(
                    selected = selectedSubtab == tab,
                    onClick = { selectedSubtab = tab },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = HistorySubtab.entries.size
                    ),
                    icon = {
                        Icon(
                            imageVector = if (tab == HistorySubtab.HISTORY) Icons.Rounded.History else Icons.Rounded.Queue,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag(
                        if (tab == HistorySubtab.HISTORY) "history_subtab_history" else "history_subtab_queue"
                    )
                ) {
                    Text(tabLabel)
                }
            }
        }

        if (selectedSubtab == HistorySubtab.QUEUE) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("history_queue_subtab_content")
            ) {
                BatchQueueScreen(
                    widthSizeClass = widthSizeClass,
                    jobs = queueJobs,
                    keepScreenOn = keepScreenOn,
                    parallelWorkers = parallelWorkers,
                    onCancelJob = onCancelJob,
                    onCancelAll = onCancelAll,
                    onClearFinished = onClearFinished,
                    onOpenHistory = { selectedSubtab = HistorySubtab.HISTORY }
                )
            }
        } else {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "${items.size} offline conversion record(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (filtered.isNotEmpty()) {
                    FilterChip(
                        selected = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            } else {
                                isSelectionMode = true
                            }
                        },
                        label = { Text(if (isSelectionMode) "Done" else "Select") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isSelectionMode) Icons.Rounded.Close else Icons.Rounded.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        modifier = Modifier.testTag("history_multi_select_toggle")
                    )
                }

                if (items.isNotEmpty() && !isSelectionMode) {
                    OutlinedButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear All")
                    }
                }
            }
        }

        if (isSelectionMode) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, studioColors.hairlineBorderBright),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filtered.isNotEmpty() && filtered.all { it.id in selectedIds },
                            onClick = {
                                val filteredIds = filtered.map { it.id }.toSet()
                                selectedIds = if (filteredIds.all { it in selectedIds }) {
                                    selectedIds - filteredIds
                                } else {
                                    selectedIds + filteredIds
                                }
                            },
                            label = { Text("Select All") },
                            leadingIcon = { Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("history_select_all_chip")
                        )
                        if (selectedIds.isNotEmpty()) {
                            FilterChip(
                                selected = false,
                                onClick = { selectedIds = emptySet() },
                                label = { Text("Clear") },
                                modifier = Modifier.testTag("history_clear_selection_chip")
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text(filter.label) }
                )
            }
        }

        if (filtered.isEmpty()) {
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
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.history_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (isTablet) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.id }) { record ->
                        val isChecked = record.id in selectedIds
                        HistoryRowCard(
                            record = record,
                            selected = if (isSelectionMode) isChecked else selectedRecord?.id == record.id,
                            selectionMode = isSelectionMode,
                            checked = isChecked,
                            onToggleCheck = { toggleSelection(record.id) },
                            onLongClick = {
                                if (!isSelectionMode) isSelectionMode = true
                                if (record.id !in selectedIds) selectedIds = selectedIds + record.id
                            },
                            onClick = {
                                if (isSelectionMode) toggleSelection(record.id)
                                else selectedRecordId = record.id
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                ) {
                    selectedRecord?.let { rec ->
                        HistoryDetailPane(
                            record = rec,
                            onDelete = { onDeleteRecord(rec) },
                            onReuseConfig = { onReuseConfig(rec) },
                            onPlayInKvPlayer = { onPlayInKvPlayer(rec) },
                            onShowMessage = onShowMessage,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { record ->
                    val expanded = !isSelectionMode && selectedRecordId == record.id
                    val isChecked = record.id in selectedIds
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryRowCard(
                            record = record,
                            selected = if (isSelectionMode) isChecked else expanded,
                            selectionMode = isSelectionMode,
                            checked = isChecked,
                            onToggleCheck = { toggleSelection(record.id) },
                            onLongClick = {
                                if (!isSelectionMode) isSelectionMode = true
                                if (record.id !in selectedIds) selectedIds = selectedIds + record.id
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelection(record.id)
                                } else {
                                    selectedRecordId = if (expanded) null else record.id
                                }
                            }
                        )
                        if (expanded) {
                            HistoryDetailPane(
                                record = record,
                                onDelete = { onDeleteRecord(record) },
                                onReuseConfig = { onReuseConfig(record) },
                                onPlayInKvPlayer = { onPlayInKvPlayer(record) },
                                onShowMessage = onShowMessage
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = isSelectionMode && selectedRecords.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_batch_action_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onPlayMultipleInKvPlayer(selectedRecords)
                            isSelectionMode = false
                            selectedIds = emptySet()
                        },
                        modifier = Modifier.testTag("history_batch_kvplayer")
                    ) {
                        Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Kvplayer")
                    }

                    OutlinedButton(
                        onClick = {
                            val shareItems = selectedRecords
                                .filter { it.status == "COMPLETED" }
                                .map { Triple(it.outputFilePath, it.outputGalleryUri, it.mimeType) }
                            val intent = MediaExportHelper.buildMultiShareIntent(context, shareItems)
                            if (intent != null) {
                                context.startActivity(
                                    Intent.createChooser(intent, "Share ${selectedRecords.size} file(s)")
                                )
                            } else {
                                onShowMessage("No shareable completed files in selection.")
                            }
                        },
                        modifier = Modifier.testTag("history_batch_share")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Share")
                    }

                    OutlinedButton(
                        onClick = {
                            val toSave = selectedRecords.filter { rec ->
                                rec.status == "COMPLETED" &&
                                    rec.outputFilePath.isNotBlank() &&
                                    File(rec.outputFilePath).exists()
                            }
                            if (toSave.isEmpty()) {
                                onShowMessage("No local files available to save to Gallery.")
                            } else {
                                scope.launch {
                                    var savedCount = 0
                                    toSave.forEach { rec ->
                                        val saved = MediaExportHelper.exportToPublicMediaStore(
                                            context = context,
                                            sourceFile = File(rec.outputFilePath),
                                            displayName = rec.outputName,
                                            mimeType = rec.mimeType,
                                            isAudioOnly = rec.mimeType.startsWith("audio/")
                                        )
                                        if (saved != null) savedCount++
                                    }
                                    onShowMessage("Saved $savedCount/${toSave.size} file(s) to Gallery.")
                                }
                            }
                        },
                        modifier = Modifier.testTag("history_batch_save")
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Save")
                    }

                    OutlinedButton(
                        onClick = {
                            onSendMultipleToStudio(selectedRecords)
                            isSelectionMode = false
                            selectedIds = emptySet()
                        },
                        modifier = Modifier.testTag("history_batch_studio")
                    ) {
                        Icon(Icons.Rounded.VideoSettings, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Studio")
                    }

                    OutlinedButton(
                        onClick = {
                            onDeleteMultiple(selectedRecords)
                            selectedIds = emptySet()
                            isSelectionMode = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("history_batch_delete")
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Delete (${selectedRecords.size})")
                    }
                }
            }
        }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRowCard(
    record: ConversionHistoryEntity,
    selected: Boolean,
    selectionMode: Boolean,
    checked: Boolean,
    onToggleCheck: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val studioColors = LocalStudioColors.current
    val isCompleted = record.status == "COMPLETED"
    val isAudio = record.mimeType.startsWith("audio/")

    StudioSurfaceCard(
        highlighted = selected,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("history_item_${record.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggleCheck() },
                    modifier = Modifier.testTag("history_checkbox_${record.id}")
                )
            } else {
                Icon(
                    imageVector = if (!isCompleted) Icons.Rounded.ErrorOutline else if (isAudio) Icons.Rounded.AudioFile else Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = if (!isCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.outputName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.containerExtension.uppercase()} • ${record.videoCodec}/${record.audioCodec}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(record.createdAtEpochMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isCompleted) {
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Success",
                        tint = studioColors.success,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = FormatUtils.formatBytes(record.outputSizeBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = studioColors.success
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDetailPane(
    record: ConversionHistoryEntity,
    onDelete: () -> Unit,
    onReuseConfig: () -> Unit,
    onPlayInKvPlayer: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val studioColors = LocalStudioColors.current

    val outputFile = remember(record.outputFilePath) {
        record.outputFilePath.takeIf { it.isNotBlank() }?.let(::File)
    }
    val playableUri: Uri? = remember(record.outputFilePath, record.outputGalleryUri) {
        when {
            outputFile?.exists() == true -> Uri.fromFile(outputFile)
            !record.outputGalleryUri.isNullOrBlank() -> runCatching { Uri.parse(record.outputGalleryUri) }.getOrNull()
            else -> null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (record.status == "COMPLETED" && playableUri != null) {
            MediaPreviewPlayer(
                mediaUri = playableUri,
                title = record.outputName,
                isAudioOnly = record.mimeType.startsWith("audio/")
            )
        }

        StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Conversion Telemetry",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricBlock("Input Size", FormatUtils.formatBytes(record.inputSizeBytes))
                    MetricBlock("Output Size", FormatUtils.formatBytes(record.outputSizeBytes))
                    MetricBlock(
                        "Delta",
                        FormatUtils.formatCompressionDelta(record.inputSizeBytes, record.outputSizeBytes)
                    )
                    MetricBlock("Encode Time", FormatUtils.formatDuration(record.processingTimeMs))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, studioColors.hairlineBorder)
                ) {
                    Text(
                        text = record.commandUsed,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                record.errorMessage?.let { err ->
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (record.status == "COMPLETED" && (outputFile?.exists() == true || !record.outputGalleryUri.isNullOrBlank())) {
                        Button(
                            onClick = {
                                val intent = MediaExportHelper.buildShareIntent(
                                    context = context,
                                    filePath = record.outputFilePath,
                                    galleryUri = record.outputGalleryUri,
                                    mimeType = record.mimeType
                                )
                                if (intent != null) {
                                    context.startActivity(Intent.createChooser(intent, "Share ${record.outputName}"))
                                } else {
                                    onShowMessage("Output file is unavailable.")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share_output))
                        }

                        if (outputFile?.exists() == true) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val saved = MediaExportHelper.exportToPublicMediaStore(
                                            context = context,
                                            sourceFile = outputFile,
                                            displayName = record.outputName,
                                            mimeType = record.mimeType,
                                            isAudioOnly = record.mimeType.startsWith("audio/")
                                        )
                                        onShowMessage(
                                            if (saved != null) "Saved to KonvertVideo folder in gallery."
                                            else "Could not export to MediaStore."
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (record.status == "COMPLETED" && playableUri != null) {
                        OutlinedButton(
                            onClick = onPlayInKvPlayer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Kvplayer")
                        }
                    }
                    OutlinedButton(
                        onClick = onReuseConfig,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.reuse_settings))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.delete_record),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
