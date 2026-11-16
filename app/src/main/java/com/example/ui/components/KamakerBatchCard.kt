package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.KamakerBatchExportState
import com.example.domain.model.KamakerBatchImageItem
import com.example.domain.model.KamakerExportFormat
import com.example.domain.model.KamakerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KamakerBatchWatermarkerCard(
    spec: KamakerSpec,
    batchImages: List<KamakerBatchImageItem>,
    exportState: KamakerBatchExportState,
    renamePrefix: String,
    exportFormat: KamakerExportFormat,
    onImportImages: (List<android.net.Uri>) -> Unit,
    onImportFolder: (android.net.Uri) -> Unit,
    onRemoveImage: (String) -> Unit,
    onClearImages: () -> Unit,
    onRenamePrefixChange: (String) -> Unit,
    onExportFormatChange: (KamakerExportFormat) -> Unit,
    onExportZip: () -> Unit,
    onExportFiles: () -> Unit,
    collapsible: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var formatDropdownOpen by remember { mutableStateOf(false) }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100)
    ) { uris ->
        if (uris.isNotEmpty()) onImportImages(uris)
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) onImportFolder(treeUri)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("kamaker_batch_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsible) Modifier.clickable { expanded = !expanded }
                        else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Watermark your own images",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Import files or a folder, stamp them with shape rules, export renamed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (collapsible) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse batch section" else "Expand batch section",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded || !collapsible) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                multiPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !exportState.isExporting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KamakerAccentOrange,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("kamaker_import_images_btn")
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import images")
                        }

                        OutlinedButton(
                            onClick = { folderLauncher.launch(null) },
                            enabled = !exportState.isExporting,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("kamaker_import_folder_btn")
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import folder")
                        }

                        OutlinedTextField(
                            value = renamePrefix,
                            onValueChange = onRenamePrefixChange,
                            label = { Text("Rename to") },
                            singleLine = true,
                            enabled = !exportState.isExporting,
                            modifier = Modifier
                                .widthIn(min = 140.dp, max = 210.dp)
                                .testTag("kamaker_rename_input")
                        )

                        Box {
                            OutlinedButton(
                                onClick = { formatDropdownOpen = true },
                                enabled = !exportState.isExporting,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier.testTag("kamaker_format_dropdown")
                            ) {
                                Text(
                                    text = "Format: ${exportFormat.label}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            DropdownMenu(
                                expanded = formatDropdownOpen,
                                onDismissRequest = { formatDropdownOpen = false }
                            ) {
                                KamakerExportFormat.entries.forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text(fmt.label) },
                                        onClick = {
                                            onExportFormatChange(fmt)
                                            formatDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onExportZip,
                            enabled = batchImages.isNotEmpty() && !exportState.isExporting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KamakerAccentOrange,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("kamaker_export_zip_btn")
                        ) {
                            Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export ZIP")
                        }

                        OutlinedButton(
                            onClick = onExportFiles,
                            enabled = batchImages.isNotEmpty() && !exportState.isExporting,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("kamaker_export_files_btn")
                        ) {
                            Icon(Icons.Rounded.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export files")
                        }

                        OutlinedButton(
                            onClick = onClearImages,
                            enabled = batchImages.isNotEmpty() && !exportState.isExporting,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("kamaker_batch_clear_btn")
                        ) {
                            Text("Clear")
                        }
                    }

                    if (exportState.isExporting && exportState.totalCount > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = {
                                    (exportState.completedCount.toFloat() / exportState.totalCount.toFloat())
                                        .coerceIn(0f, 1f)
                                },
                                color = KamakerAccentOrange,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = exportState.lastStatusMessage ?: "Processing...",
                                style = MaterialTheme.typography.labelMedium,
                                color = KamakerAccentOrange
                            )
                        }
                    } else if (batchImages.isEmpty()) {
                        Text(
                            text = "No images yet — import files or a folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = exportState.lastStatusMessage
                                ?: "${batchImages.size} image(s) queued — aspect-matched watermark preview:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(batchImages, key = { it.id }) { item ->
                                KamakerBatchThumbItem(
                                    item = item,
                                    spec = spec,
                                    onRemove = { onRemoveImage(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KamakerBatchThumbItem(
    item: KamakerBatchImageItem,
    spec: KamakerSpec,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(item.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }
    }
    val matchedShape = spec.resolveShapeForAspect(item.width.toFloat() / item.height.coerceAtLeast(1).toFloat())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(88.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (spec.hasActiveContent) {
                KamakerLiveOverlayLayer(
                    spec = spec,
                    forceShape = matchedShape,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clickable(onClick = onRemove)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove ${item.displayName}",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
            ) {
                Text(
                    text = matchedShape.ratioLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = KamakerAccentOrange,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
