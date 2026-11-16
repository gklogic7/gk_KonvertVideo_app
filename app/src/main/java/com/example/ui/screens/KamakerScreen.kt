package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.domain.model.KamakerBackdrop
import com.example.domain.model.KamakerBatchExportState
import com.example.domain.model.KamakerBatchImageItem
import com.example.domain.model.KamakerBuiltInPresets
import com.example.domain.model.KamakerExportFormat
import com.example.domain.model.KamakerFont
import com.example.domain.model.KamakerFrameRule
import com.example.domain.model.KamakerFrameShape
import com.example.domain.model.KamakerGlobalAdjust
import com.example.domain.model.KamakerHorizontalAlign
import com.example.domain.model.KamakerMode
import com.example.domain.model.KamakerPreset
import com.example.domain.model.KamakerShapeRule
import com.example.domain.model.KamakerSpec
import com.example.domain.model.KamakerVerticalAlign
import com.example.ui.components.KamakerAccentOrange
import com.example.ui.components.KamakerBatchWatermarkerCard
import com.example.ui.components.KamakerColorPickerDialog
import com.example.ui.components.KamakerLabeledSliderRow
import com.example.ui.components.KamakerShapeBoardCard
import com.example.ui.components.rememberDecodedKamakerLogo
import kotlin.math.roundToInt

private enum class KamakerSubtab(val label: String) {
    PLACEMENT("Placement"),
    EDITOR("Editor")
}

@Composable
fun KamakerScreen(
    widthSizeClass: WindowWidthSizeClass,
    spec: KamakerSpec,
    customPresets: List<KamakerPreset>,
    backdropBitmap: Bitmap?,
    batchImages: List<KamakerBatchImageItem>,
    renamePrefix: String,
    exportFormat: KamakerExportFormat,
    batchExportState: KamakerBatchExportState,
    onUpdateSpec: ((KamakerSpec) -> KamakerSpec) -> Unit,
    onUpdateShapeRule: (KamakerFrameShape, (KamakerShapeRule) -> KamakerShapeRule) -> Unit,
    onResetShape: (KamakerFrameShape) -> Unit,
    onCopyShapeToAll: (KamakerFrameShape) -> Unit,
    onResetAllKamaker: () -> Unit,
    onApplyPreset: (KamakerPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onRefreshBackdrop: () -> Unit,
    onImportBatchImages: (List<Uri>) -> Unit,
    onImportBatchFolder: (Uri) -> Unit,
    onRemoveBatchImage: (String) -> Unit,
    onClearBatchImages: () -> Unit,
    onRenamePrefixChange: (String) -> Unit,
    onExportFormatChange: (KamakerExportFormat) -> Unit,
    onExportFiles: () -> Unit,
    onExportZip: ((Uri, String) -> Unit) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val isExpandedLayout = widthSizeClass != WindowWidthSizeClass.Compact
    var selectedSubtab by rememberSaveable { mutableStateOf(KamakerSubtab.PLACEMENT) }
    val decodedLogo = rememberDecodedKamakerLogo(spec)

    LaunchedEffect(spec.boardBackdrop) {
        if (spec.boardBackdrop == KamakerBackdrop.CURRENT_VIDEO_FRAME) {
            onRefreshBackdrop()
        }
    }

    val handleZipExport: () -> Unit = {
        onExportZip { shareUri, fileName ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(shareIntent, "Share $fileName"))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("kamaker_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Subtab bar: Placement first, Editor second
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = if (isExpandedLayout) 24.dp else 16.dp, vertical = 10.dp)
        ) {
            KamakerSubtab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedSubtab == tab,
                    onClick = { selectedSubtab = tab },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = KamakerSubtab.entries.size
                    ),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = KamakerAccentOrange,
                        activeContentColor = Color.White,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    icon = {
                        Icon(
                            imageVector = if (tab == KamakerSubtab.PLACEMENT) {
                                Icons.Rounded.AutoAwesomeMosaic
                            } else {
                                Icons.Rounded.EditNote
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag(
                        if (tab == KamakerSubtab.PLACEMENT) "kamaker_subtab_placement" else "kamaker_subtab_editor"
                    )
                ) {
                    Text(tab.label)
                }
            }
        }

        if (selectedSubtab == KamakerSubtab.PLACEMENT) {
            KamakerPlacementTabContent(
                isExpandedLayout = isExpandedLayout,
                spec = spec,
                decodedLogo = decodedLogo,
                backdropBitmap = backdropBitmap,
                batchImages = batchImages,
                renamePrefix = renamePrefix,
                exportFormat = exportFormat,
                batchExportState = batchExportState,
                onUpdateShapeRule = onUpdateShapeRule,
                onUpdateSpec = onUpdateSpec,
                onResetShape = onResetShape,
                onCopyShapeToAll = onCopyShapeToAll,
                onResetAllKamaker = onResetAllKamaker,
                onImportBatchImages = onImportBatchImages,
                onImportBatchFolder = onImportBatchFolder,
                onRemoveBatchImage = onRemoveBatchImage,
                onClearBatchImages = onClearBatchImages,
                onRenamePrefixChange = onRenamePrefixChange,
                onExportFormatChange = onExportFormatChange,
                onExportFiles = onExportFiles,
                onExportZip = handleZipExport
            )
        } else {
            KamakerEditorTabContent(
                isExpandedLayout = isExpandedLayout,
                spec = spec,
                decodedLogo = decodedLogo,
                customPresets = customPresets,
                batchImages = batchImages,
                renamePrefix = renamePrefix,
                exportFormat = exportFormat,
                batchExportState = batchExportState,
                onUpdateSpec = onUpdateSpec,
                onRefreshBackdrop = onRefreshBackdrop,
                onApplyPreset = onApplyPreset,
                onSavePreset = onSavePreset,
                onDeletePreset = onDeletePreset,
                onDone = {
                    selectedSubtab = KamakerSubtab.PLACEMENT
                    onShowMessage("Kamaker watermark settings applied.")
                },
                onImportBatchImages = onImportBatchImages,
                onImportBatchFolder = onImportBatchFolder,
                onRemoveBatchImage = onRemoveBatchImage,
                onClearBatchImages = onClearBatchImages,
                onRenamePrefixChange = onRenamePrefixChange,
                onExportFormatChange = onExportFormatChange,
                onExportFiles = onExportFiles,
                onExportZip = handleZipExport
            )
        }
    }
}

@Composable
private fun KamakerPlacementTabContent(
    isExpandedLayout: Boolean,
    spec: KamakerSpec,
    decodedLogo: Bitmap?,
    backdropBitmap: Bitmap?,
    batchImages: List<KamakerBatchImageItem>,
    renamePrefix: String,
    exportFormat: KamakerExportFormat,
    batchExportState: KamakerBatchExportState,
    onUpdateShapeRule: (KamakerFrameShape, (KamakerShapeRule) -> KamakerShapeRule) -> Unit,
    onUpdateSpec: ((KamakerSpec) -> KamakerSpec) -> Unit,
    onResetShape: (KamakerFrameShape) -> Unit,
    onCopyShapeToAll: (KamakerFrameShape) -> Unit,
    onResetAllKamaker: () -> Unit,
    onImportBatchImages: (List<Uri>) -> Unit,
    onImportBatchFolder: (Uri) -> Unit,
    onRemoveBatchImage: (String) -> Unit,
    onClearBatchImages: () -> Unit,
    onRenamePrefixChange: (String) -> Unit,
    onExportFormatChange: (KamakerExportFormat) -> Unit,
    onExportFiles: () -> Unit,
    onExportZip: () -> Unit
) {
    val snapForShape: (KamakerFrameShape, KamakerVerticalAlign?, KamakerHorizontalAlign?) -> Unit =
        { shape, v, h ->
            onUpdateSpec { cur ->
                val rule = cur.ruleFor(shape)
                val snapped = KamakerSpec.snapAlign(
                    current = rule,
                    vertical = v ?: rule.verticalAlign,
                    horizontal = h ?: rule.horizontalAlign,
                    edgeMarginPercent = cur.edgeMarginPercent
                )
                cur.withRuleFor(shape, snapped)
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpandedLayout) 24.dp else 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        KamakerBatchWatermarkerCard(
            spec = spec,
            batchImages = batchImages,
            exportState = batchExportState,
            renamePrefix = renamePrefix,
            exportFormat = exportFormat,
            onImportImages = onImportBatchImages,
            onImportFolder = onImportBatchFolder,
            onRemoveImage = onRemoveBatchImage,
            onClearImages = onClearBatchImages,
            onRenamePrefixChange = onRenamePrefixChange,
            onExportFormatChange = onExportFormatChange,
            onExportZip = onExportZip,
            onExportFiles = onExportFiles
        )

        if (isExpandedLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KamakerFrameShape.entries.forEach { shape ->
                    KamakerShapeBoardCard(
                        shape = shape,
                        spec = spec,
                        decodedLogo = decodedLogo,
                        backdropBitmap = backdropBitmap,
                        onUpdateRule = { transform -> onUpdateShapeRule(shape, transform) },
                        onSnapAlign = { v, h -> snapForShape(shape, v, h) },
                        onResetShape = { onResetShape(shape) },
                        onCopyToAllShapes = { onCopyShapeToAll(shape) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KamakerFrameShape.entries.forEach { shape ->
                    KamakerShapeBoardCard(
                        shape = shape,
                        spec = spec,
                        decodedLogo = decodedLogo,
                        backdropBitmap = backdropBitmap,
                        onUpdateRule = { transform -> onUpdateShapeRule(shape, transform) },
                        onSnapAlign = { v, h -> snapForShape(shape, v, h) },
                        onResetShape = { onResetShape(shape) },
                        onCopyToAllShapes = { onCopyShapeToAll(shape) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onResetAllKamaker,
                modifier = Modifier.testTag("kamaker_reset_all_btn")
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset Kamaker")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KamakerEditorTabContent(
    isExpandedLayout: Boolean,
    spec: KamakerSpec,
    decodedLogo: Bitmap?,
    customPresets: List<KamakerPreset>,
    batchImages: List<KamakerBatchImageItem>,
    renamePrefix: String,
    exportFormat: KamakerExportFormat,
    batchExportState: KamakerBatchExportState,
    onUpdateSpec: ((KamakerSpec) -> KamakerSpec) -> Unit,
    onRefreshBackdrop: () -> Unit,
    onApplyPreset: (KamakerPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDone: () -> Unit,
    onImportBatchImages: (List<Uri>) -> Unit,
    onImportBatchFolder: (Uri) -> Unit,
    onRemoveBatchImage: (String) -> Unit,
    onClearBatchImages: () -> Unit,
    onRenamePrefixChange: (String) -> Unit,
    onExportFormatChange: (KamakerExportFormat) -> Unit,
    onExportFiles: () -> Unit,
    onExportZip: () -> Unit
) {
    val context = LocalContext.current
    val allPresets = remember(customPresets) { KamakerBuiltInPresets.all + customPresets }
    var selectedPresetId by rememberSaveable { mutableStateOf(allPresets.first().id) }
    val selectedPreset = allPresets.firstOrNull { it.id == selectedPresetId } ?: allPresets.first()
    var newPresetName by rememberSaveable { mutableStateOf("") }

    var showStrokePanel by rememberSaveable { mutableStateOf(true) }
    var showGlobalPanel by rememberSaveable { mutableStateOf(true) }
    var showFontMenu by remember { mutableStateOf(false) }
    var showFrameRuleMenu by remember { mutableStateOf(false) }
    var showBackdropMenu by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }

    var colorPickerTarget by remember { mutableStateOf<String?>(null) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "logo.png"
            val isSvg = mime.contains("svg", ignoreCase = true) ||
                name.lowercase().endsWith(".svg") ||
                uri.toString().lowercase().endsWith(".svg")
            onUpdateSpec {
                it.copy(
                    mode = KamakerMode.LOGO,
                    logoUriString = uri.toString(),
                    logoDisplayName = name,
                    logoIsSvg = isSvg
                )
            }
        }
    }

    if (colorPickerTarget == "text") {
        KamakerColorPickerDialog(
            title = "Text Colour",
            initialColorHex = spec.textColorHex,
            onDismiss = { colorPickerTarget = null },
            onColorSelected = { hex ->
                onUpdateSpec { it.copy(textColorHex = hex) }
                colorPickerTarget = null
            }
        )
    } else if (colorPickerTarget == "stroke") {
        KamakerColorPickerDialog(
            title = "Stroke Colour",
            initialColorHex = spec.strokeColorHex,
            onDismiss = { colorPickerTarget = null },
            onColorSelected = { hex ->
                onUpdateSpec { it.copy(strokeColorHex = hex, strokeEnabled = true) }
                colorPickerTarget = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isExpandedLayout) 24.dp else 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header matching Screenshot 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kamaker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "watermark editor — set a placement rule for each frame shape",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KamakerAccentOrange,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("kamaker_done_btn")
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done")
            }
        }

        // Main two-pane Editor layout on wide screens, stacked on phones
        val leftPane: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mode switcher: Logo (PNG) | Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KamakerMode.entries.forEach { mode ->
                            val selected = spec.mode == mode
                            Surface(
                                onClick = { onUpdateSpec { it.copy(mode = mode) } },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) KamakerAccentOrange else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) KamakerAccentOrange else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("kamaker_mode_${mode.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (spec.mode == KamakerMode.TEXT) {
                        OutlinedTextField(
                            value = spec.text,
                            onValueChange = { txt -> onUpdateSpec { it.copy(text = txt) } },
                            label = { Text("Watermark text") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("kamaker_text_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    onClick = { showFontMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Font", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(spec.font.label, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                                    }
                                }
                                DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }) {
                                    KamakerFont.entries.forEach { font ->
                                        DropdownMenuItem(
                                            text = { Text(font.label) },
                                            onClick = {
                                                onUpdateSpec { it.copy(font = font) }
                                                showFontMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Colour", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(spec.textColorHex))
                                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                        .clickable { colorPickerTarget = "text" }
                                        .testTag("kamaker_text_color_swatch")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KamakerCheckboxLabel("Bold", spec.bold) { checked ->
                                onUpdateSpec { it.copy(bold = checked) }
                            }
                            KamakerCheckboxLabel("Italic", spec.italic) { checked ->
                                onUpdateSpec { it.copy(italic = checked) }
                            }
                            KamakerCheckboxLabel("Shadow", spec.shadow) { checked ->
                                onUpdateSpec { it.copy(shadow = checked) }
                            }
                        }

                        // Stroke — text section
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showStrokePanel = !showStrokePanel },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (showStrokePanel) "▼ Stroke — text" else "▶ Stroke — text",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    KamakerCheckboxLabel("Enabled", spec.strokeEnabled) { checked ->
                                        onUpdateSpec { it.copy(strokeEnabled = checked) }
                                    }
                                }

                                AnimatedVisibility(visible = showStrokePanel) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        KamakerLabeledSliderRow(
                                            label = "Width",
                                            valueText = "${spec.strokeWidthPercent.roundToInt()}%",
                                            value = spec.strokeWidthPercent,
                                            valueRange = 0f..25f,
                                            onValueChange = { w ->
                                                onUpdateSpec { it.copy(strokeWidthPercent = w, strokeEnabled = w > 0.2f) }
                                            }
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "Colour",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(68.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(68.dp)
                                                    .height(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(spec.strokeColorHex))
                                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                    .clickable { colorPickerTarget = "stroke" }
                                                    .testTag("kamaker_stroke_color_swatch")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Logo (PNG / SVG) Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (decodedLogo != null && !decodedLogo.isRecycled) {
                                    Image(
                                        bitmap = decodedLogo.asImageBitmap(),
                                        contentDescription = "Selected logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = spec.logoDisplayName.ifBlank { "No logo image selected" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { logoPickerLauncher.launch(arrayOf("image/*")) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = KamakerAccentOrange,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("kamaker_pick_logo_btn")
                                    ) {
                                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Pick PNG / SVG")
                                    }
                                    if (!spec.logoUriString.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                onUpdateSpec { it.copy(logoUriString = null, logoDisplayName = "", logoIsSvg = false) }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                }
                            }
                        }
                        KamakerCheckboxLabel("Drop Shadow on Logo", spec.shadow) { checked ->
                            onUpdateSpec { it.copy(shadow = checked) }
                        }
                    }
                }
            }
        }

        val rightPane: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KamakerLabeledSliderRow(
                        label = "Opacity",
                        valueText = "${spec.opacityPercent.roundToInt()}%",
                        value = spec.opacityPercent,
                        valueRange = 2f..100f,
                        onValueChange = { op -> onUpdateSpec { it.copy(opacityPercent = op) } }
                    )

                    KamakerLabeledSliderRow(
                        label = "Edge margin",
                        valueText = "${spec.edgeMarginPercent.roundToInt()}%",
                        value = spec.edgeMarginPercent,
                        valueRange = 0f..20f,
                        onValueChange = { m -> onUpdateSpec { it.copy(edgeMarginPercent = m) } }
                    )

                    // Global adjust — text / logo
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showGlobalPanel = !showGlobalPanel },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val modeTag = if (spec.mode == KamakerMode.TEXT) "text" else "logo"
                                Text(
                                    text = if (showGlobalPanel) "▼ Global adjust — $modeTag" else "▶ Global adjust — $modeTag",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Icon(
                                    imageVector = if (showGlobalPanel) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null
                                )
                            }

                            AnimatedVisibility(visible = showGlobalPanel) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    KamakerLabeledSliderRow(
                                        label = "Scale",
                                        valueText = "${spec.globalAdjust.scalePercent.roundToInt()}%",
                                        value = spec.globalAdjust.scalePercent,
                                        valueRange = 20f..200f,
                                        onValueChange = { s ->
                                            onUpdateSpec { it.copy(globalAdjust = it.globalAdjust.copy(scalePercent = s)) }
                                        }
                                    )
                                    KamakerLabeledSliderRow(
                                        label = "Position X",
                                        valueText = "${spec.globalAdjust.offsetXPercent.roundToInt()}%",
                                        value = spec.globalAdjust.offsetXPercent,
                                        valueRange = -50f..50f,
                                        onValueChange = { x ->
                                            onUpdateSpec { it.copy(globalAdjust = it.globalAdjust.copy(offsetXPercent = x)) }
                                        }
                                    )
                                    KamakerLabeledSliderRow(
                                        label = "Position Y",
                                        valueText = "${spec.globalAdjust.offsetYPercent.roundToInt()}%",
                                        value = spec.globalAdjust.offsetYPercent,
                                        valueRange = -50f..50f,
                                        onValueChange = { y ->
                                            onUpdateSpec { it.copy(globalAdjust = it.globalAdjust.copy(offsetYPercent = y)) }
                                        }
                                    )
                                    KamakerLabeledSliderRow(
                                        label = "Rotate",
                                        valueText = "${spec.globalAdjust.rotateDegrees.roundToInt()}°",
                                        value = spec.globalAdjust.rotateDegrees,
                                        valueRange = -180f..180f,
                                        onValueChange = { r ->
                                            onUpdateSpec { it.copy(globalAdjust = it.globalAdjust.copy(rotateDegrees = r)) }
                                        }
                                    )
                                    OutlinedButton(
                                        onClick = { onUpdateSpec { it.copy(globalAdjust = KamakerGlobalAdjust()) } },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Reset global")
                                    }
                                }
                            }
                        }
                    }

                    // Frame rule + Board backdrop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                onClick = { showFrameRuleMenu = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Frame rule", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(spec.frameRule.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = showFrameRuleMenu, onDismissRequest = { showFrameRuleMenu = false }) {
                                KamakerFrameRule.entries.forEach { rule ->
                                    DropdownMenuItem(
                                        text = { Text(rule.label) },
                                        onClick = {
                                            onUpdateSpec { it.copy(frameRule = rule) }
                                            showFrameRuleMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                onClick = { showBackdropMenu = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Board backdrop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(spec.boardBackdrop.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = showBackdropMenu, onDismissRequest = { showBackdropMenu = false }) {
                                KamakerBackdrop.entries.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b.label) },
                                        onClick = {
                                            onUpdateSpec { it.copy(boardBackdrop = b) }
                                            if (b == KamakerBackdrop.CURRENT_VIDEO_FRAME) {
                                                onRefreshBackdrop()
                                            }
                                            showBackdropMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        if (spec.boardBackdrop == KamakerBackdrop.CURRENT_VIDEO_FRAME) {
                            IconButton(onClick = onRefreshBackdrop) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh current video frame")
                            }
                        }
                    }

                    KamakerCheckboxLabel(
                        label = "Stamp on captured frames (Kvplayer screenshots)",
                        checked = spec.stampOnCapturedFrames
                    ) { checked ->
                        onUpdateSpec { it.copy(stampOnCapturedFrames = checked) }
                    }
                    KamakerCheckboxLabel(
                        label = "Show on player (live Kvplayer overlay)",
                        checked = spec.showOnPlayer
                    ) { checked ->
                        onUpdateSpec { it.copy(showOnPlayer = checked) }
                    }
                    KamakerCheckboxLabel(
                        label = "Apply active Kamaker watermark in Konvert Studio exports",
                        checked = spec.applyInStudio
                    ) { checked ->
                        onUpdateSpec { it.copy(applyInStudio = checked) }
                    }
                }
            }
        }

        if (isExpandedLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f)) { leftPane() }
                Box(modifier = Modifier.weight(1f)) { rightPane() }
            }
        } else {
            leftPane()
            rightPane()
        }

        // Presets Bar matching Screenshot 2
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.widthIn(min = 180.dp, max = 260.dp)) {
                        Surface(
                            onClick = { showPresetMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Presets: ${selectedPreset.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                            allPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(if (preset.isBuiltIn) preset.name else "${preset.name} (Custom)") },
                                    onClick = {
                                        selectedPresetId = preset.id
                                        showPresetMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onApplyPreset(selectedPreset) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KamakerAccentOrange,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Apply")
                    }

                    OutlinedButton(
                        onClick = {
                            if (!selectedPreset.isBuiltIn) {
                                onDeletePreset(selectedPreset.id)
                                selectedPresetId = KamakerBuiltInPresets.all.first().id
                            }
                        },
                        enabled = !selectedPreset.isBuiltIn
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }

                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        placeholder = { Text("New preset name") },
                        singleLine = true,
                        modifier = Modifier.widthIn(min = 160.dp, max = 240.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            onSavePreset(newPresetName)
                            newPresetName = ""
                        }
                    ) {
                        Text("Save preset")
                    }
                }

                Text(
                    text = "Presets keep placement for all three shapes plus text, stroke, opacity and margin — not the logo image. " +
                        "Pick an edge or drag handles on each shape board; Konvert & Kvplayer automatically match the rule closest to the video's shape.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Batch Watermark bar at bottom of Editor tab too
        KamakerBatchWatermarkerCard(
            spec = spec,
            batchImages = batchImages,
            exportState = batchExportState,
            renamePrefix = renamePrefix,
            exportFormat = exportFormat,
            onImportImages = onImportBatchImages,
            onImportFolder = onImportBatchFolder,
            onRemoveImage = onRemoveBatchImage,
            onClearImages = onClearBatchImages,
            onRenamePrefixChange = onRenamePrefixChange,
            onExportFormatChange = onExportFormatChange,
            onExportZip = onExportZip,
            onExportFiles = onExportFiles,
            modifier = Modifier.padding(bottom = 18.dp)
        )
    }
}

@Composable
private fun KamakerCheckboxLabel(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
