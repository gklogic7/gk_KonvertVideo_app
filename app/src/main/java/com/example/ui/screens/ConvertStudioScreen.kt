package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AudioArtworkSelection
import com.example.domain.model.AudioCodecOption
import com.example.domain.model.ContainerFormat
import com.example.domain.model.ConversionConfig
import com.example.domain.model.ConversionPreset
import com.example.domain.model.KamakerMode
import com.example.domain.model.KamakerSpec
import com.example.domain.model.MediaInputItem
import com.example.domain.model.PrecisionCropPanelStyle
import com.example.domain.model.ResolutionOption
import com.example.domain.model.StudioCoverIcon
import com.example.domain.model.StudioSettings
import com.example.domain.model.VideoCodecOption
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoOverlayConfig
import com.example.domain.model.VideoRateControlMode
import com.example.ffmpeg.AudioArtworkGenerator
import com.example.ffmpeg.FFmpegCommandBuilder
import com.example.ffmpeg.PresetCatalog
import com.example.ui.components.CropGeometry
import com.example.ui.components.KamakerAccentOrange
import com.example.ui.components.MediaPreviewPlayer
import com.example.ui.components.PrecisionEditorTab
import com.example.ui.components.PulsingSignalBadge
import com.example.ui.components.StudioSurfaceCard
import com.example.ui.components.TweenAccordionSection
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ConvertStudioScreen(
    widthSizeClass: WindowWidthSizeClass,
    selectedInputs: List<MediaInputItem>,
    activePreviewIndex: Int,
    config: ConversionConfig,
    settings: StudioSettings,
    customPresets: List<ConversionPreset>,
    onAddUris: (List<Uri>) -> Unit,
    onRemoveInput: (String) -> Unit,
    onClearInputs: () -> Unit,
    onSelectPreviewIndex: (Int) -> Unit,
    onApplyPreset: (ConversionPreset) -> Unit,
    onUpdateContainer: (ContainerFormat) -> Unit,
    onUpdateVideoCodec: (VideoCodecOption) -> Unit,
    onUpdateAudioCodec: (AudioCodecOption) -> Unit,
    onUpdateResolution: (ResolutionOption) -> Unit,
    onUpdateCustomResolutionWidth: (Int?) -> Unit,
    onUpdateRateControlMode: (VideoRateControlMode) -> Unit,
    onUpdateCrf: (Int) -> Unit,
    onUpdateVideoBitrateKbps: (Int) -> Unit,
    onUpdateAudioBitrate: (Int) -> Unit,
    onUpdateFps: (Int?) -> Unit,
    onUpdateSpeedPreset: (String) -> Unit,
    onUpdateAudioArtwork: (AudioArtworkSelection) -> Unit,
    onUpdateCropRegion: (VideoCropRegion) -> Unit,
    onResetCropRegion: () -> Unit,
    onUpdateCropAndOverlay: (VideoCropRegion, VideoOverlayConfig) -> Unit,
    onClearOverlays: () -> Unit,
    onUpdateTrimRange: (Long?, Long?) -> Unit,
    onClearTrim: () -> Unit,
    onUpdateCustomCommand: (String) -> Unit,
    onResetCommandFromControls: () -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onStartConversion: () -> Unit,
    onOpenSettings: () -> Unit,
    onChangePrecisionCropPanelStyle: (PrecisionCropPanelStyle) -> Unit = {},
    onTogglePrecisionCropFullKvHud: (Boolean) -> Unit = {},
    kamakerSpec: KamakerSpec = KamakerSpec(),
    onToggleApplyKamakerInStudio: (Boolean) -> Unit = {},
    onOpenKamaker: () -> Unit = {}
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) onAddUris(uris)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onAddUris(uris)
    }

    val coverImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUpdateAudioArtwork(AudioArtworkSelection.GalleryImage(uri.toString()))
        }
    }

    val launchVideoPicker = {
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }
    val launchAnyMediaPicker = {
        documentPickerLauncher.launch(arrayOf("video/*", "audio/*", "application/ogg"))
    }
    val launchCoverImagePicker = {
        coverImagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var showSavePresetDialog by rememberSaveable { mutableStateOf(false) }
    var presetNameDraft by rememberSaveable { mutableStateOf("") }
    var pendingPrecisionTab by remember { mutableStateOf<PrecisionEditorTab?>(null) }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Save Custom Preset") },
            text = {
                OutlinedTextField(
                    value = presetNameDraft,
                    onValueChange = { presetNameDraft = it },
                    label = { Text("Preset name") },
                    placeholder = { Text("e.g. Tablet HEVC 1080p") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetNameDraft.isNotBlank()) {
                            onSaveCustomPreset(presetNameDraft)
                            presetNameDraft = ""
                            showSavePresetDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val isExpandedTablet = widthSizeClass != WindowWidthSizeClass.Compact

    AnimatedContent(
        targetState = selectedInputs.isEmpty(),
        transitionSpec = {
            (fadeIn(tween(if (settings.lowOverheadUi) 120 else 300, easing = FastOutSlowInEasing)))
                .togetherWith(fadeOut(tween(if (settings.lowOverheadUi) 100 else 220, easing = FastOutSlowInEasing)))
        },
        label = "studioZeroVsActive"
    ) { isEmpty ->
        if (isEmpty) {
            EmptyStudioLanding(
                isTablet = isExpandedTablet,
                onPickVideo = launchVideoPicker,
                onPickAudioOrAny = launchAnyMediaPicker,
                settings = settings,
                config = config,
                customPresets = customPresets,
                onApplyPreset = onApplyPreset,
                onOpenSettings = onOpenSettings
            )
        } else {
            val activeItem = selectedInputs.getOrNull(activePreviewIndex) ?: selectedInputs.first()

            if (isExpandedTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StudioHeaderRow(
                            fileCount = selectedInputs.size,
                            onAddVideo = launchVideoPicker,
                            onAddAny = launchAnyMediaPicker,
                            onClear = onClearInputs
                        )

                        MediaPreviewPlayer(
                            mediaUri = activeItem.uri,
                            title = activeItem.displayName,
                            isAudioOnly = activeItem.isAudioOnly,
                            reducedMotion = settings.lowOverheadUi,
                            sourceWidth = activeItem.width,
                            sourceHeight = activeItem.height,
                            enableCropOverlay = settings.enableCropOverlay,
                            enableInlineCrop = settings.enableInlineCrop,
                            enableFullscreenCrop = settings.enableFullscreenCrop,
                            useKvPlayerInPrecisionCrop = settings.useKvPlayerInPrecisionCrop,
                            precisionCropPanelStyle = settings.precisionCropPanelStyle,
                            precisionCropFullKvHud = settings.precisionCropFullKvHud,
                            onChangePrecisionCropPanelStyle = onChangePrecisionCropPanelStyle,
                            onTogglePrecisionCropFullKvHud = onTogglePrecisionCropFullKvHud,
                            cropRegion = config.cropRegion,
                            onUpdateCropRegion = onUpdateCropRegion,
                            onResetCropRegion = onResetCropRegion,
                            overlayConfig = config.overlayConfig,
                            onUpdateCropAndOverlay = onUpdateCropAndOverlay,
                            externalPrecisionTab = pendingPrecisionTab,
                            onConsumeExternalPrecisionTab = { pendingPrecisionTab = null },
                            enableMarkInOut = settings.enableStudioMarkInOut,
                            trimStartMs = config.trimStartMs,
                            trimEndMs = config.trimEndMs,
                            onUpdateTrimRange = onUpdateTrimRange,
                            onClearTrim = onClearTrim
                        )

                        StreamInspectorCard(item = activeItem)

                        BatchInputStrip(
                            items = selectedInputs,
                            selectedIndex = activePreviewIndex,
                            onSelect = onSelectPreviewIndex,
                            onRemove = onRemoveInput
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ConversionControlsColumn(
                                activeItem = activeItem,
                                config = config,
                                settings = settings,
                                customPresets = customPresets,
                                onApplyPreset = onApplyPreset,
                                onUpdateContainer = onUpdateContainer,
                                onUpdateVideoCodec = onUpdateVideoCodec,
                                onUpdateAudioCodec = onUpdateAudioCodec,
                                onUpdateResolution = onUpdateResolution,
                                onUpdateCustomResolutionWidth = onUpdateCustomResolutionWidth,
                                onUpdateRateControlMode = onUpdateRateControlMode,
                                onUpdateCrf = onUpdateCrf,
                                onUpdateVideoBitrateKbps = onUpdateVideoBitrateKbps,
                                onUpdateAudioBitrate = onUpdateAudioBitrate,
                                onUpdateFps = onUpdateFps,
                                onUpdateSpeedPreset = onUpdateSpeedPreset,
                                onPickCoverImage = launchCoverImagePicker,
                                onUpdateAudioArtwork = onUpdateAudioArtwork,
                                onResetCropRegion = onResetCropRegion,
                                onClearOverlays = onClearOverlays,
                                onOpenPrecisionTab = { pendingPrecisionTab = it },
                                onClearTrim = onClearTrim,
                                onUpdateCustomCommand = onUpdateCustomCommand,
                                onResetCommandFromControls = onResetCommandFromControls,
                                onRequestSavePreset = { showSavePresetDialog = true },
                                onOpenSettings = onOpenSettings,
                                kamakerSpec = kamakerSpec,
                                onToggleApplyKamakerInStudio = onToggleApplyKamakerInStudio,
                                onOpenKamaker = onOpenKamaker
                            )
                        }

                        ConvertActionFooter(
                            fileCount = selectedInputs.size,
                            isProbingAny = selectedInputs.any { it.isProbing },
                            config = config,
                            onStartConversion = onStartConversion
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StudioHeaderRow(
                            fileCount = selectedInputs.size,
                            onAddVideo = launchVideoPicker,
                            onAddAny = launchAnyMediaPicker,
                            onClear = onClearInputs
                        )

                        MediaPreviewPlayer(
                            mediaUri = activeItem.uri,
                            title = activeItem.displayName,
                            isAudioOnly = activeItem.isAudioOnly,
                            reducedMotion = settings.lowOverheadUi,
                            sourceWidth = activeItem.width,
                            sourceHeight = activeItem.height,
                            enableCropOverlay = settings.enableCropOverlay,
                            enableInlineCrop = settings.enableInlineCrop,
                            enableFullscreenCrop = settings.enableFullscreenCrop,
                            useKvPlayerInPrecisionCrop = settings.useKvPlayerInPrecisionCrop,
                            precisionCropPanelStyle = settings.precisionCropPanelStyle,
                            precisionCropFullKvHud = settings.precisionCropFullKvHud,
                            onChangePrecisionCropPanelStyle = onChangePrecisionCropPanelStyle,
                            onTogglePrecisionCropFullKvHud = onTogglePrecisionCropFullKvHud,
                            cropRegion = config.cropRegion,
                            onUpdateCropRegion = onUpdateCropRegion,
                            onResetCropRegion = onResetCropRegion,
                            overlayConfig = config.overlayConfig,
                            onUpdateCropAndOverlay = onUpdateCropAndOverlay,
                            externalPrecisionTab = pendingPrecisionTab,
                            onConsumeExternalPrecisionTab = { pendingPrecisionTab = null },
                            enableMarkInOut = settings.enableStudioMarkInOut,
                            trimStartMs = config.trimStartMs,
                            trimEndMs = config.trimEndMs,
                            onUpdateTrimRange = onUpdateTrimRange,
                            onClearTrim = onClearTrim
                        )

                        StreamInspectorCard(item = activeItem)

                        BatchInputStrip(
                            items = selectedInputs,
                            selectedIndex = activePreviewIndex,
                            onSelect = onSelectPreviewIndex,
                            onRemove = onRemoveInput
                        )

                        ConversionControlsColumn(
                            activeItem = activeItem,
                            config = config,
                            settings = settings,
                            customPresets = customPresets,
                            onApplyPreset = onApplyPreset,
                            onUpdateContainer = onUpdateContainer,
                            onUpdateVideoCodec = onUpdateVideoCodec,
                            onUpdateAudioCodec = onUpdateAudioCodec,
                            onUpdateResolution = onUpdateResolution,
                            onUpdateCustomResolutionWidth = onUpdateCustomResolutionWidth,
                            onUpdateRateControlMode = onUpdateRateControlMode,
                            onUpdateCrf = onUpdateCrf,
                            onUpdateVideoBitrateKbps = onUpdateVideoBitrateKbps,
                            onUpdateAudioBitrate = onUpdateAudioBitrate,
                            onUpdateFps = onUpdateFps,
                            onUpdateSpeedPreset = onUpdateSpeedPreset,
                            onPickCoverImage = launchCoverImagePicker,
                            onUpdateAudioArtwork = onUpdateAudioArtwork,
                            onResetCropRegion = onResetCropRegion,
                            onClearOverlays = onClearOverlays,
                            onOpenPrecisionTab = { pendingPrecisionTab = it },
                            onClearTrim = onClearTrim,
                            onUpdateCustomCommand = onUpdateCustomCommand,
                            onResetCommandFromControls = onResetCommandFromControls,
                            onRequestSavePreset = { showSavePresetDialog = true },
                            onOpenSettings = onOpenSettings,
                            kamakerSpec = kamakerSpec,
                            onToggleApplyKamakerInStudio = onToggleApplyKamakerInStudio,
                            onOpenKamaker = onOpenKamaker
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        ConvertActionFooter(
                            fileCount = selectedInputs.size,
                            isProbingAny = selectedInputs.any { it.isProbing },
                            config = config,
                            onStartConversion = onStartConversion
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStudioLanding(
    isTablet: Boolean,
    onPickVideo: () -> Unit,
    onPickAudioOrAny: () -> Unit,
    settings: StudioSettings,
    config: ConversionConfig,
    customPresets: List<ConversionPreset>,
    onApplyPreset: (ConversionPreset) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTablet) 28.dp else 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.studio_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.studio_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PulsingSignalBadge(
                label = "OFFLINE ENGINE",
                active = false,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, LocalStudioColors.current.hairlineBorderBright)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero),
                    contentDescription = "Konvert Video mastering studio backdrop",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTablet) 240.dp else 200.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTablet) 240.dp else 200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x550B0E14),
                                    Color(0xEE0B0E14)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.empty_studio_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.empty_studio_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onPickVideo,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pick_video_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.pick_video_gallery))
                        }
                        OutlinedButton(
                            onClick = onPickAudioOrAny,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pick_audio_any_button"),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(
                                Icons.Rounded.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pick_any_media),
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (settings.showQuickPresets) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ready-to-Use Offline Profiles",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Studio Tiers")
                    }
                }
                PresetHorizontalDeck(
                    presets = customPresets + PresetCatalog.builtInPresets,
                    selectedPresetId = config.selectedPresetId,
                    onSelect = onApplyPreset
                )
            }
        }
    }
}

@Composable
private fun StudioHeaderRow(
    fileCount: Int,
    onAddVideo: () -> Unit,
    onAddAny: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Active Batch ($fileCount)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tap any clip to preview & inspect streams",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onAddVideo,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("add_video_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Video")
            }
            OutlinedButton(
                onClick = onAddAny,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("add_any_button")
            ) {
                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("File")
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("clear_batch_button")
            ) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.clear_batch),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamInspectorCard(item: MediaInputItem) {
    StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (item.isAudioOnly) Icons.Rounded.AudioFile else Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.isProbing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpecBadge("Size", FormatUtils.formatBytes(item.sizeBytes))
                SpecBadge("Duration", FormatUtils.formatDuration(item.durationMs))
                if (item.width != null && item.height != null) {
                    SpecBadge("Res", "${item.width}×${item.height}")
                }
                item.fps?.let { fps ->
                    SpecBadge("FPS", String.format("%.1f", fps))
                }
                item.videoCodec?.let { vc ->
                    SpecBadge("Video", vc.uppercase())
                }
                item.audioCodec?.let { ac ->
                    SpecBadge("Audio", ac.uppercase())
                }
                item.bitrateKbps?.let { br ->
                    SpecBadge("Bitrate", "${br} kbps")
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, LocalStudioColors.current.hairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BatchInputStrip(
    items: List<MediaInputItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val selected = index == selectedIndex
            StudioSurfaceCard(
                highlighted = selected,
                onClick = { onSelect(index) },
                modifier = Modifier.width(210.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (item.isAudioOnly) Icons.Rounded.AudioFile else Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FormatUtils.formatDuration(item.durationMs)} • ${FormatUtils.formatBytes(item.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onRemove(item.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove ${item.displayName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversionControlsColumn(
    activeItem: MediaInputItem,
    config: ConversionConfig,
    settings: StudioSettings,
    customPresets: List<ConversionPreset>,
    onApplyPreset: (ConversionPreset) -> Unit,
    onUpdateContainer: (ContainerFormat) -> Unit,
    onUpdateVideoCodec: (VideoCodecOption) -> Unit,
    onUpdateAudioCodec: (AudioCodecOption) -> Unit,
    onUpdateResolution: (ResolutionOption) -> Unit,
    onUpdateCustomResolutionWidth: (Int?) -> Unit,
    onUpdateRateControlMode: (VideoRateControlMode) -> Unit,
    onUpdateCrf: (Int) -> Unit,
    onUpdateVideoBitrateKbps: (Int) -> Unit,
    onUpdateAudioBitrate: (Int) -> Unit,
    onUpdateFps: (Int?) -> Unit,
    onUpdateSpeedPreset: (String) -> Unit,
    onPickCoverImage: () -> Unit,
    onUpdateAudioArtwork: (AudioArtworkSelection) -> Unit,
    onResetCropRegion: () -> Unit,
    onClearOverlays: () -> Unit,
    onOpenPrecisionTab: (PrecisionEditorTab) -> Unit,
    onClearTrim: () -> Unit,
    onUpdateCustomCommand: (String) -> Unit,
    onResetCommandFromControls: () -> Unit,
    onRequestSavePreset: () -> Unit,
    onOpenSettings: () -> Unit,
    kamakerSpec: KamakerSpec = KamakerSpec(),
    onToggleApplyKamakerInStudio: (Boolean) -> Unit = {},
    onOpenKamaker: () -> Unit = {}
) {
    var presetsExpanded by rememberSaveable { mutableStateOf(true) }
    var proExpanded by rememberSaveable { mutableStateOf(true) }
    var workbenchExpanded by rememberSaveable { mutableStateOf(false) }

    if (!settings.showQuickPresets && !settings.showProControls && !settings.showCommandWorkbench) {
        StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "All Studio Control Tiers Hidden",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Enable Quick Presets, Pro Codec Controls, or the Command Workbench in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Settings")
                }
            }
        }
        return
    }

    // Active Crop Banner (with Stream Copy notice if applicable)
    AnimatedVisibility(visible = !activeItem.isAudioOnly && !config.cropRegion.isFullFrame && !config.isAudioExport) {
        val isCopy = config.videoCodec == VideoCodecOption.COPY
        StudioSurfaceCard(
            highlighted = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Crop,
                    contentDescription = null,
                    tint = if (isCopy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCopy) {
                            "Crop active — Stream Copy cannot filter video"
                        } else {
                            "Crop Active: ${CropGeometry.formatReadout(config.cropRegion, activeItem.width, activeItem.height)}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isCopy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isCopy) {
                            "Switch to H.264 or another video codec to export the cropped region."
                        } else {
                            "FFmpeg will export this framed region with even-aligned pixels."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isCopy) {
                    OutlinedButton(onClick = { onUpdateVideoCodec(VideoCodecOption.H264) }) {
                        Text("Use H.264")
                    }
                } else {
                    TextButton(onClick = onResetCropRegion) {
                        Text("Reset")
                    }
                }
            }
        }
    }

    // Active Logo / Text / Kamaker Overlay Banner
    AnimatedVisibility(visible = !activeItem.isAudioOnly && config.hasActiveOverlays) {
        val isCopy = config.videoCodec == VideoCodecOption.COPY
        val parts = buildList {
            if (config.overlayConfig.logo.hasLogo) add("Logo: ${config.overlayConfig.logo.displayName}")
            if (config.overlayConfig.textBox.hasText) add("Text: \"${config.overlayConfig.textBox.text.trim().take(22)}\"")
            config.kamakerSpec?.takeIf { it.hasActiveContent }?.let { km ->
                val tag = if (km.mode == KamakerMode.TEXT) "\"${km.text.trim().take(16)}\"" else km.logoDisplayName.ifBlank { "Logo" }
                add("Kamaker: $tag")
            }
        }
        StudioSurfaceCard(
            highlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("studio_active_overlay_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = if (isCopy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCopy) "Overlays paused on Stream Copy" else parts.joinToString(" • "),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isCopy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isCopy) {
                            "Switch to H.264 or another codec to burn logo, text & Kamaker watermarks."
                        } else {
                            "Burned directly into the exported frame during FFmpeg conversion."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isCopy) {
                    OutlinedButton(onClick = { onUpdateVideoCodec(VideoCodecOption.H264) }) {
                        Text("Use H.264")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (config.kamakerSpec?.hasActiveContent == true) {
                            TextButton(onClick = onOpenKamaker) {
                                Text("Kamaker")
                            }
                        } else {
                            TextButton(onClick = { onOpenPrecisionTab(PrecisionEditorTab.LOGO) }) {
                                Text("Edit")
                            }
                        }
                        TextButton(onClick = {
                            onClearOverlays()
                            if (kamakerSpec.applyInStudio) onToggleApplyKamakerInStudio(false)
                        }) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }

    // Active Trim Banner (from Kvplayer Mark In / Out)
    AnimatedVisibility(visible = config.hasActiveTrim) {
        val startTxt = FormatUtils.formatDuration(config.trimStartMs ?: 0L)
        val endTxt = config.trimEndMs?.let { FormatUtils.formatDuration(it) } ?: "End"
        StudioSurfaceCard(
            highlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("studio_active_trim_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCut,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trim Active: $startTxt → $endTxt",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "FFmpeg will cut this In/Out segment during export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onClearTrim,
                    modifier = Modifier.testTag("studio_clear_trim_button")
                ) {
                    Text("Clear")
                }
            }
        }
    }

    // Tier 1: One-Tap Quick Presets
    AnimatedVisibility(
        visible = settings.showQuickPresets,
        enter = expandVertically(tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
    ) {
        TweenAccordionSection(
            title = stringResource(R.string.section_quick_presets),
            subtitle = "Instant profiles for MP4, H.265, WebM, OGG, 3GP, MP3 & Opus",
            icon = Icons.Rounded.AutoAwesome,
            expanded = presetsExpanded,
            onToggleExpanded = { presetsExpanded = !presetsExpanded },
            accentColor = MaterialTheme.colorScheme.primary
        ) {
            PresetHorizontalDeck(
                presets = customPresets + PresetCatalog.builtInPresets,
                selectedPresetId = config.selectedPresetId,
                onSelect = onApplyPreset
            )
        }
    }

    // Tier 2: Expandable Pro Codec Controls
    AnimatedVisibility(
        visible = settings.showProControls,
        enter = expandVertically(tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
    ) {
        TweenAccordionSection(
            title = stringResource(R.string.section_pro_controls),
            subtitle = "${config.container.extension.uppercase()} • ${config.videoCodec.ffmpegName} • ${config.audioCodec.ffmpegName}",
            icon = Icons.Rounded.Tune,
            expanded = proExpanded,
            onToggleExpanded = { proExpanded = !proExpanded },
            accentColor = MaterialTheme.colorScheme.secondary,
            headerTrailing = {
                IconButton(
                    onClick = onRequestSavePreset,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = stringResource(R.string.save_custom_preset),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Output Container",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContainerFormat.entries.forEach { format ->
                        val selected = config.container == format
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateContainer(format) },
                            label = {
                                Text(
                                    text = ".${format.extension.uppercase()}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                if (!config.container.isAudioOnly) {
                    val compatibleVideo = PresetCatalog.compatibleVideoCodecs(config.container)
                    Text(
                        text = "Video Codec",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        compatibleVideo.forEach { vc ->
                            FilterChip(
                                selected = config.videoCodec == vc,
                                onClick = { onUpdateVideoCodec(vc) },
                                label = { Text(vc.label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    if (config.videoCodec != VideoCodecOption.COPY &&
                        config.videoCodec != VideoCodecOption.NONE
                    ) {
                        if (!activeItem.isAudioOnly) {
                            OutlinedButton(
                                onClick = { onOpenPrecisionTab(PrecisionEditorTab.LOGO) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_precision_overlays_button")
                            ) {
                                Icon(Icons.Rounded.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (config.hasActiveOverlays) {
                                        "Precision Box Crop • Edit Logo & Text Overlays"
                                    } else {
                                        "Precision Box Crop • Add Logo (PNG/JPEG/WebP/SVG) & Text"
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = kamakerSpec.applyInStudio,
                                    onClick = { onToggleApplyKamakerInStudio(!kamakerSpec.applyInStudio) },
                                    label = {
                                        Text(
                                            if (kamakerSpec.applyInStudio) "Kamaker Watermark: ON" else "Apply Kamaker Watermark",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KamakerAccentOrange.copy(alpha = 0.22f),
                                        selectedLabelColor = KamakerAccentOrange
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("studio_kamaker_toggle_chip")
                                )
                                TextButton(
                                    onClick = onOpenKamaker,
                                    modifier = Modifier.testTag("studio_open_kamaker_btn")
                                ) {
                                    Text("Open Kamaker")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Resolution Scale",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = config.effectiveTargetWidth?.let { "${it}px width" } ?: "Original (Source)",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResolutionOption.entries.forEach { res ->
                                val selected = config.effectiveTargetWidth == res.width
                                FilterChip(
                                    selected = selected,
                                    onClick = { onUpdateResolution(res) },
                                    label = { Text(res.label, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.testTag("resolution_chip_${res.name.lowercase()}")
                                )
                            }
                        }
                        Slider(
                            value = (config.effectiveTargetWidth ?: (activeItem.width?.coerceIn(320, 3840) ?: 1920)).toFloat(),
                            onValueChange = { raw ->
                                val snapped = ((raw / 16f).roundToInt() * 16).coerceIn(320, 3840)
                                onUpdateCustomResolutionWidth(snapped)
                            },
                            valueRange = 320f..3840f,
                            modifier = Modifier.testTag("resolution_width_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        if (config.videoCodec != VideoCodecOption.WEBP) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Video Rate Control", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    VideoRateControlMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = config.rateControlMode == mode,
                                            onClick = { onUpdateRateControlMode(mode) },
                                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.testTag("rate_mode_${mode.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = config.rateControlMode,
                            transitionSpec = {
                                fadeIn(tween(200)).togetherWith(fadeOut(tween(150)))
                            },
                            label = "videoRateSlider"
                        ) { mode ->
                            if (mode == VideoRateControlMode.CRF || config.videoCodec == VideoCodecOption.WEBP) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Quality (CRF ${config.crf})", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = if (config.crf <= 20) "High Quality" else if (config.crf <= 28) "Balanced" else "Compact Size",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Slider(
                                        value = config.crf.toFloat(),
                                        onValueChange = { onUpdateCrf(it.roundToInt()) },
                                        valueRange = 14f..40f,
                                        steps = 25,
                                        modifier = Modifier.testTag("video_crf_slider"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            } else {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Target Video Bitrate", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = if (config.videoBitrateKbps >= 1000) {
                                                String.format("%.1f Mbps (%d kbps)", config.videoBitrateKbps / 1000f, config.videoBitrateKbps)
                                            } else {
                                                "${config.videoBitrateKbps} kbps"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = config.videoBitrateKbps.toFloat(),
                                        onValueChange = { raw ->
                                            val snapped = ((raw / 250f).roundToInt() * 250).coerceIn(250, 20000)
                                            onUpdateVideoBitrateKbps(snapped)
                                        },
                                        valueRange = 250f..20000f,
                                        modifier = Modifier.testTag("video_bitrate_slider"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.secondary,
                                            activeTrackColor = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Frame Rate (FPS)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = config.fps?.let { "$it fps" } ?: "Source FPS",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf<Int?>(null, 24, 30, 60, 120).forEach { fps ->
                                FilterChip(
                                    selected = config.fps == fps,
                                    onClick = { onUpdateFps(fps) },
                                    label = { Text(fps?.let { "$it fps" } ?: "Source FPS") },
                                    modifier = Modifier.testTag("fps_chip_${fps ?: "source"}")
                                )
                            }
                        }
                        Slider(
                            value = (config.fps ?: (activeItem.fps?.roundToInt()?.coerceIn(12, 120) ?: 30)).toFloat(),
                            onValueChange = { raw ->
                                onUpdateFps(raw.roundToInt().coerceIn(12, 120))
                            },
                            valueRange = 12f..120f,
                            modifier = Modifier.testTag("fps_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )

                        if (config.videoCodec == VideoCodecOption.H264 ||
                            config.videoCodec == VideoCodecOption.H265
                        ) {
                            Text(
                                text = "Encoder Speed Preset",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("ultrafast", "veryfast", "fast", "medium", "slow").forEach { sp ->
                                    FilterChip(
                                        selected = config.speedPreset == sp,
                                        onClick = { onUpdateSpeedPreset(sp) },
                                        label = { Text(sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Audio Codec & Bitrate Slider
                val compatibleAudio = PresetCatalog.compatibleAudioCodecs(config.container)
                Text(
                    text = "Audio Codec",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    compatibleAudio.forEach { ac ->
                        FilterChip(
                            selected = config.audioCodec == ac,
                            onClick = { onUpdateAudioCodec(ac) },
                            label = { Text(ac.label, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }

                if (config.audioCodec != AudioCodecOption.NONE &&
                    config.audioCodec != AudioCodecOption.COPY &&
                    config.audioCodec != AudioCodecOption.PCM &&
                    config.audioCodec != AudioCodecOption.WAVPACK
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Audio Bitrate",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${config.audioBitrateKbps} kbps",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = config.audioBitrateKbps.toFloat(),
                            onValueChange = { raw ->
                                val snapped = ((raw / 16f).roundToInt() * 16).coerceIn(32, 320)
                                onUpdateAudioBitrate(snapped)
                            },
                            valueRange = 32f..320f,
                            steps = 17,
                            modifier = Modifier.testTag("audio_bitrate_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(64, 128, 192, 256, 320).forEach { snap ->
                                Text(
                                    text = "${snap}k",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (config.audioBitrateKbps == snap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onUpdateAudioBitrate(snap) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Audio Cover Art & Studio Icon Assignment (visible when exporting audio)
                AnimatedVisibility(
                    visible = config.isAudioExport,
                    enter = expandVertically(tween(240)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
                ) {
                    AudioArtworkSelectorPanel(
                        container = config.container,
                        selection = config.audioArtwork,
                        onPickImage = onPickCoverImage,
                        onSelectIcon = { icon -> onUpdateAudioArtwork(AudioArtworkSelection.StudioIcon(icon)) },
                        onClear = { onUpdateAudioArtwork(AudioArtworkSelection.None) },
                        onSwitchContainer = onUpdateContainer
                    )
                }
            }
        }
    }

    // Tier 3: Full Pro Workbench (Custom FFmpeg CLI Editor)
    AnimatedVisibility(
        visible = settings.showCommandWorkbench,
        enter = expandVertically(tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
    ) {
        TweenAccordionSection(
            title = stringResource(R.string.section_command_workbench),
            subtitle = if (config.useCustomCommand) "Custom CLI Override Active" else "Synced with Studio Controls",
            icon = Icons.Rounded.Terminal,
            expanded = workbenchExpanded,
            onToggleExpanded = { workbenchExpanded = !workbenchExpanded },
            accentColor = MaterialTheme.colorScheme.tertiary
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Edit FFmpeg flags directly (supports {input} & {output} placeholders or flags-only):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = config.customCommandArgs,
                    onValueChange = onUpdateCustomCommand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workbench_command_input"),
                    textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = LocalStudioColors.current.hairlineBorder
                    )
                )

                val fullPreview = remember(activeItem.displayName, config, settings.hardwareThreads) {
                    FFmpegCommandBuilder.buildFullPreviewCommand(
                        inputName = activeItem.displayName,
                        outputName = "${activeItem.displayName.substringBeforeLast('.')}_konvert.${config.container.extension}",
                        config = config,
                        hardwareThreads = settings.hardwareThreads
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(1.dp, LocalStudioColors.current.hairlineBorder)
                ) {
                    Text(
                        text = fullPreview,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onResetCommandFromControls,
                        modifier = Modifier.testTag("sync_cli_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sync_from_controls))
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioArtworkSelectorPanel(
    container: ContainerFormat,
    selection: AudioArtworkSelection,
    onPickImage: () -> Unit,
    onSelectIcon: (StudioCoverIcon) -> Unit,
    onClear: () -> Unit,
    onSwitchContainer: (ContainerFormat) -> Unit
) {
    val context = LocalContext.current
    val studioColors = LocalStudioColors.current

    val previewBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = selection) {
        value = withContext(Dispatchers.IO) {
            when (selection) {
                AudioArtworkSelection.None -> null
                is AudioArtworkSelection.StudioIcon ->
                    AudioArtworkGenerator.renderStudioIconBitmap(selection.icon, 256).asImageBitmap()
                is AudioArtworkSelection.GalleryImage -> runCatching {
                    val uri = Uri.parse(selection.uriString)
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = studioColors.hairlineBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio Cover Art & Studio Icon",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (container.supportsCoverArt) {
                        "Embedded directly as cover art in .${container.extension.uppercase()}"
                    } else {
                        ".${container.extension.uppercase()} ignores cover streams — switch to MP3 or M4A to embed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (container.supportsCoverArt) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary
                )
            }
            if (selection !is AudioArtworkSelection.None) {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }

        if (!container.supportsCoverArt) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSwitchContainer(ContainerFormat.MP3) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Switch to .MP3")
                }
                OutlinedButton(
                    onClick = { onSwitchContainer(ContainerFormat.M4A) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Switch to .M4A")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, studioColors.hairlineBorderBright)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!,
                            contentDescription = "Selected audio cover art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = "No cover art assigned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onPickImage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_audio_cover_image_button")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose Image from Device")
                }
            }
        }

        Text(
            text = "Or pick a Studio Emblem Icon",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioCoverIcon.entries.forEach { icon ->
                val isSelected = selection is AudioArtworkSelection.StudioIcon && selection.icon == icon
                val iconBitmap = remember(icon) {
                    AudioArtworkGenerator.renderStudioIconBitmap(icon, 128).asImageBitmap()
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectIcon(icon) }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else studioColors.hairlineBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(6.dp)
                        .testTag("studio_cover_icon_${icon.id}")
                ) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = icon.label,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = icon.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetHorizontalDeck(
    presets: List<ConversionPreset>,
    selectedPresetId: String,
    onSelect: (ConversionPreset) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(presets, key = { it.id }) { preset ->
            val isSelected = preset.id == selectedPresetId
            StudioSurfaceCard(
                highlighted = isSelected,
                onClick = { onSelect(preset) },
                modifier = Modifier
                    .width(204.dp)
                    .testTag("preset_card_${preset.id}")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (preset.isCustom) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = preset.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (preset.isCustom) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = preset.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvertActionFooter(
    fileCount: Int,
    isProbingAny: Boolean,
    config: ConversionConfig,
    onStartConversion: () -> Unit
) {
    Button(
        onClick = onStartConversion,
        enabled = !isProbingAny && fileCount > 0,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("start_conversion_button"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (isProbingAny) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(10.dp))
            Text("Inspecting Media Streams…")
        } else {
            Text(
                text = if (fileCount == 1) {
                    "Convert to .${config.container.extension.uppercase()}"
                } else {
                    "Convert Batch ($fileCount Files → .${config.container.extension.uppercase()})"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
