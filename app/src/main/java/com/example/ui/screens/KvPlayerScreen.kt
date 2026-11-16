package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlin.math.roundToInt
import com.example.R
import com.example.data.local.ConversionHistoryEntity
import com.example.domain.model.KamakerSpec
import com.example.domain.model.KvPlayerTrack
import com.example.domain.model.KvPlayerUiState
import com.example.domain.model.MediaInputItem
import com.example.domain.model.StudioSettings
import com.example.player.KvPlayerController
import com.example.ui.components.KvPlayerFullscreenDialog
import com.example.ui.components.KvPlayerViewport
import com.example.ui.components.PulsingSignalBadge
import com.example.ui.components.StudioSurfaceCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import com.example.ui.util.FormatUtils
import java.io.File

private val SUPPORTED_KVPLAYER_FORMATS = listOf(
    "MP4", "MKV", "MOV", "AVI", "M4V", "3GP", "MP3", "WAV", "M4A", "APE", "WEBM", "OGG", "OPUS"
)

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun KvPlayerScreen(
    widthSizeClass: WindowWidthSizeClass,
    controller: KvPlayerController,
    uiState: KvPlayerUiState,
    settings: StudioSettings,
    studioInputs: List<MediaInputItem>,
    historyItems: List<ConversionHistoryEntity>,
    isInPipMode: Boolean,
    reducedMotion: Boolean,
    onLoadStudioClips: () -> Unit,
    onLoadHistoryClips: () -> Unit,
    onEnterPip: (Int, Int) -> Unit,
    onTakeScreenshot: () -> Unit,
    onSendTrimToStudio: () -> Unit,
    onToggleHideBottomTiles: (Boolean) -> Unit = {},
    kamakerSpec: KamakerSpec? = null,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(controller) { controller.getOrCreatePlayer() }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) controller.addPickedUris(uris)
    }

    val anyMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) controller.addPickedUris(uris)
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            controller.attachExternalSubtitle(uri)
            onShowMessage("Loaded external subtitle track.")
        }
    }

    var showSpeedDialog by rememberSaveable { mutableStateOf(false) }
    var showSubtitlesDialog by rememberSaveable { mutableStateOf(false) }
    var showCastDialog by rememberSaveable { mutableStateOf(false) }
    var showPlaylistSheet by rememberSaveable { mutableStateOf(false) }
    var isPlaylistMinimized by rememberSaveable { mutableStateOf(false) }
    var isPlaylistFloating by rememberSaveable { mutableStateOf(false) }
    var playlistOffsetX by remember { mutableFloatStateOf(0f) }
    var playlistOffsetY by remember { mutableFloatStateOf(0f) }

    // PiP mode collapses all chrome to full-bleed player only
    if (isInPipMode) {
        KvPlayerViewport(
            exoPlayer = exoPlayer,
            uiState = uiState,
            isInPipMode = true,
            reducedMotion = reducedMotion,
            onTogglePlayPause = controller::togglePlayPause,
            onSeekTo = controller::seekTo,
            onSeekBy = controller::seekBy,
            onSkipNext = controller::skipNext,
            onSkipPrevious = controller::skipPrevious,
            onCycleAspectMode = controller::cycleAspectMode,
            onOpenSpeedDialog = {},
            onOpenSubtitlesDialog = {},
            onOpenCastDialog = {},
            onEnterPip = {},
            onTogglePlaylistSheet = {},
            onRetry = controller::retryCurrentTrack,
            kamakerSpec = kamakerSpec,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    if (uiState.isFullscreen && settings.kvShowFullscreenButton && uiState.currentTrack != null && !uiState.isFloatingMode) {
        KvPlayerFullscreenDialog(
            exoPlayer = exoPlayer,
            uiState = uiState,
            reducedMotion = reducedMotion,
            showScreenshotButton = settings.kvShowScreenshotButton,
            showHideHudUiButton = settings.kvShowHideHudUiButton,
            showMarkInOut = settings.kvShowMarkInOut,
            onDismiss = { controller.setFullscreen(false) },
            onTogglePlayPause = controller::togglePlayPause,
            onSeekTo = controller::seekTo,
            onSeekBy = controller::seekBy,
            onSkipNext = controller::skipNext,
            onSkipPrevious = controller::skipPrevious,
            onCycleAspectMode = controller::cycleAspectMode,
            onOpenSpeedDialog = { showSpeedDialog = true },
            onOpenSubtitlesDialog = { showSubtitlesDialog = true },
            onOpenCastDialog = { showCastDialog = true },
            onEnterPip = {
                controller.setFullscreen(false)
                onEnterPip(uiState.videoWidth, uiState.videoHeight)
            },
            onTogglePlaylistSheet = { showPlaylistSheet = !showPlaylistSheet },
            onRetry = controller::retryCurrentTrack,
            onTakeScreenshot = onTakeScreenshot,
            onToggleHideDisplayVolumeUi = controller::toggleHideDisplayVolumeUi,
            onMarkIn = controller::markInAtCurrent,
            onMarkOut = controller::markOutAtCurrent,
            onToggleAbLoop = controller::toggleAbLoop,
            onClearMarks = controller::clearMarkInOut,
            onSendTrimToStudio = onSendTrimToStudio,
            kamakerSpec = kamakerSpec
        )
    }

    // Speed Selector Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Playback Speed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PLAYBACK_SPEEDS.forEach { spd ->
                        val selected = uiState.playbackSpeed == spd
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else LocalStudioColors.current.hairlineBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    controller.setPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${spd}x" + if (spd == 1.0f) " (Normal)" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }

    // Subtitles / CC Dialog
    if (showSubtitlesDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitlesDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Subtitles & Captions (CC)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (!uiState.subtitlesEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                controller.selectSubtitleTrack(null)
                                showSubtitlesDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Off (Disable Subtitles)", style = MaterialTheme.typography.titleSmall)
                            if (!uiState.subtitlesEnabled) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (uiState.subtitleTracks.isEmpty()) {
                        Text(
                            text = "No embedded subtitle streams detected in this file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.subtitleTracks.forEach { track ->
                            val selected = uiState.subtitlesEnabled && uiState.selectedSubtitleTrackId == track.id
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        controller.selectSubtitleTrack(track.id)
                                        showSubtitlesDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(track.label, style = MaterialTheme.typography.titleSmall)
                                    if (selected) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            showSubtitlesDialog = false
                            subtitlePickerLauncher.launch(arrayOf("application/x-subrip", "text/vtt", "text/*", "*/*"))
                        },
                        enabled = uiState.currentTrack != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Load External .SRT / .VTT File")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitlesDialog = false }) { Text("Done") }
            }
        )
    }

    // Casting & External Output Dialog
    if (showCastDialog) {
        AlertDialog(
            onDismissRequest = { showCastDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Casting & External Display") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Mirror to a wireless display / Cast device or hand off playback to an external Cast-enabled player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showCastDialog = false
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                            }.onFailure {
                                onShowMessage("Cast settings are unavailable on this device.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Cast, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open System Cast / Wireless Display")
                    }

                    uiState.currentTrack?.let { track ->
                        OutlinedButton(
                            onClick = {
                                showCastDialog = false
                                val viewUri = resolveShareableUri(context, track.uri)
                                if (viewUri != null) {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(viewUri, track.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(viewIntent, "Play or Cast with…"))
                                    }.onFailure {
                                        onShowMessage("No external player app available.")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open in External Cast / Player App")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCastDialog = false }) { Text("Close") }
            }
        )
    }

    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact
    val studioColors = LocalStudioColors.current
    val launchVideoPicker = {
        videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }
    val launchAnyPicker = {
        anyMediaPickerLauncher.launch(arrayOf("video/*", "audio/*", "application/ogg"))
    }
    val completedHistoryCount = remember(historyItems) { historyItems.count { it.status == "COMPLETED" } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("kvplayer_screen")
    ) {
        val density = LocalDensity.current
        val maxScreenWidthPx = with(density) { maxWidth.toPx() }
        val maxScreenHeightPx = with(density) { maxHeight.toPx() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row + Supported Format Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.kvplayer_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.kvplayer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPlaylistFloating) {
                        FilterChip(
                            selected = true,
                            onClick = { isPlaylistFloating = false },
                            label = { Text("Dock Playlist", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("kvplayer_dock_playlist_chip")
                        )
                    }
                    if (uiState.bgAudioEnabled) {
                        PulsingSignalBadge(
                            label = "BG AUDIO ON",
                            active = uiState.isPlaying,
                            color = studioColors.success
                        )
                    }
                }
            }

            // Format Chips Row matching reference image
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SUPPORTED_KVPLAYER_FORMATS.forEach { ext ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, studioColors.hairlineBorder)
                    ) {
                        Text(
                            text = ext,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isTablet) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Left Pane: Floating-HUD Player + Feature Cards
                    Column(
                        modifier = Modifier
                            .weight(if (isPlaylistFloating) 1f else if (isPlaylistMinimized) 0.72f else 0.62f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        KvPlayerStage(
                            exoPlayer = exoPlayer,
                            controller = controller,
                            uiState = uiState,
                            settings = settings,
                            reducedMotion = reducedMotion,
                            studioCount = studioInputs.size,
                            historyCount = completedHistoryCount,
                            onPickVideo = launchVideoPicker,
                            onPickAny = launchAnyPicker,
                            onLoadStudio = onLoadStudioClips,
                            onLoadHistory = onLoadHistoryClips,
                            onOpenSpeedDialog = { showSpeedDialog = true },
                            onOpenSubtitlesDialog = { showSubtitlesDialog = true },
                            onOpenCastDialog = { showCastDialog = true },
                            onEnterPip = { onEnterPip(uiState.videoWidth, uiState.videoHeight) },
                            onTogglePlaylist = {
                                isPlaylistMinimized = false
                                showPlaylistSheet = !showPlaylistSheet
                            },
                            onTakeScreenshot = onTakeScreenshot,
                            onSendTrimToStudio = onSendTrimToStudio,
                            kamakerSpec = kamakerSpec
                        )

                        KvPlayerBottomTilesSection(
                            uiState = uiState,
                            hideBottomTiles = settings.kvHideBottomTiles,
                            onToggleHideBottomTiles = onToggleHideBottomTiles,
                            onOpenCast = { showCastDialog = true },
                            onEnterPip = { onEnterPip(uiState.videoWidth, uiState.videoHeight) },
                            onToggleBgAudio = controller::toggleBgAudio
                        )
                    }

                    // Right Pane: Docked Multi-Source Playlist Drawer (unless detached to Floating)
                    if (!isPlaylistFloating) {
                        KvPlaylistPanel(
                            uiState = uiState,
                            studioCount = studioInputs.size,
                            historyCount = completedHistoryCount,
                            isMinimized = isPlaylistMinimized,
                            isFloating = false,
                            onToggleMinimize = { isPlaylistMinimized = !isPlaylistMinimized },
                            onToggleFloating = {
                                playlistOffsetX = 0f
                                playlistOffsetY = 0f
                                isPlaylistFloating = true
                            },
                            onPickVideo = launchVideoPicker,
                            onPickAny = launchAnyPicker,
                            onLoadStudio = onLoadStudioClips,
                            onLoadHistory = onLoadHistoryClips,
                            onSelectTrack = { idx -> controller.selectTrack(idx) },
                            onRemoveTrack = controller::removeTrack,
                            onClearPlaylist = controller::clearPlaylist,
                            modifier = if (isPlaylistMinimized) {
                                Modifier
                                    .weight(0.28f)
                                    .wrapContentHeight(Alignment.Top)
                            } else {
                                Modifier
                                    .weight(0.38f)
                                    .fillMaxHeight()
                            }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KvPlayerStage(
                        exoPlayer = exoPlayer,
                        controller = controller,
                        uiState = uiState,
                        settings = settings,
                        reducedMotion = reducedMotion,
                        studioCount = studioInputs.size,
                        historyCount = completedHistoryCount,
                        onPickVideo = launchVideoPicker,
                        onPickAny = launchAnyPicker,
                        onLoadStudio = onLoadStudioClips,
                        onLoadHistory = onLoadHistoryClips,
                        onOpenSpeedDialog = { showSpeedDialog = true },
                        onOpenSubtitlesDialog = { showSubtitlesDialog = true },
                        onOpenCastDialog = { showCastDialog = true },
                        onEnterPip = { onEnterPip(uiState.videoWidth, uiState.videoHeight) },
                        onTogglePlaylist = {
                            isPlaylistMinimized = false
                            showPlaylistSheet = !showPlaylistSheet
                        },
                        onTakeScreenshot = onTakeScreenshot,
                        onSendTrimToStudio = onSendTrimToStudio,
                        kamakerSpec = kamakerSpec
                    )

                    KvPlayerBottomTilesSection(
                        uiState = uiState,
                        hideBottomTiles = settings.kvHideBottomTiles,
                        onToggleHideBottomTiles = onToggleHideBottomTiles,
                        onOpenCast = { showCastDialog = true },
                        onEnterPip = { onEnterPip(uiState.videoWidth, uiState.videoHeight) },
                        onToggleBgAudio = controller::toggleBgAudio
                    )

                    if (!isPlaylistFloating) {
                        KvPlaylistPanel(
                            uiState = uiState,
                            studioCount = studioInputs.size,
                            historyCount = completedHistoryCount,
                            isMinimized = isPlaylistMinimized,
                            isFloating = false,
                            onToggleMinimize = { isPlaylistMinimized = !isPlaylistMinimized },
                            onToggleFloating = {
                                playlistOffsetX = 0f
                                playlistOffsetY = 0f
                                isPlaylistFloating = true
                            },
                            onPickVideo = launchVideoPicker,
                            onPickAny = launchAnyPicker,
                            onLoadStudio = onLoadStudioClips,
                            onLoadHistory = onLoadHistoryClips,
                            onSelectTrack = { idx -> controller.selectTrack(idx) },
                            onRemoveTrack = controller::removeTrack,
                            onClearPlaylist = controller::clearPlaylist,
                            modifier = if (isPlaylistMinimized) {
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                            }
                        )
                    }
                }
            }
        }

        // Detached Floating + Movable Media Playlist Overlay
        if (isPlaylistFloating) {
            val maxDragX = (maxScreenWidthPx * 0.5f).coerceAtLeast(80f)
            val maxDragY = (maxScreenHeightPx * 0.72f).coerceAtLeast(80f)
            val clampedX = playlistOffsetX.coerceIn(-maxDragX, 16f)
            val clampedY = playlistOffsetY.coerceIn(-maxDragY, 16f)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                    .widthIn(min = 260.dp, max = if (isPlaylistMinimized) 310.dp else 370.dp)
                    .then(
                        if (isPlaylistMinimized) Modifier.wrapContentHeight()
                        else Modifier.heightIn(max = 360.dp)
                    )
                    .testTag("kvplayer_floating_playlist")
            ) {
                Column {
                    // Movable Drag Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                            .pointerInput(maxDragX, maxDragY) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    playlistOffsetX = (playlistOffsetX + dragAmount.x).coerceIn(-maxDragX, 16f)
                                    playlistOffsetY = (playlistOffsetY + dragAmount.y).coerceIn(-maxDragY, 16f)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .testTag("kvplayer_floating_playlist_drag_handle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = "Drag floating playlist",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Floating Playlist • Drag to Move",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    KvPlaylistPanel(
                        uiState = uiState,
                        studioCount = studioInputs.size,
                        historyCount = completedHistoryCount,
                        isMinimized = isPlaylistMinimized,
                        isFloating = true,
                        onToggleMinimize = { isPlaylistMinimized = !isPlaylistMinimized },
                        onToggleFloating = { isPlaylistFloating = false },
                        onPickVideo = launchVideoPicker,
                        onPickAny = launchAnyPicker,
                        onLoadStudio = onLoadStudioClips,
                        onLoadHistory = onLoadHistoryClips,
                        onSelectTrack = { idx -> controller.selectTrack(idx) },
                        onRemoveTrack = controller::removeTrack,
                        onClearPlaylist = controller::clearPlaylist,
                        modifier = if (isPlaylistMinimized) {
                            Modifier.fillMaxWidth().wrapContentHeight()
                        } else {
                            Modifier.fillMaxWidth().height(290.dp)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KvPlayerStage(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer,
    controller: KvPlayerController,
    uiState: KvPlayerUiState,
    settings: StudioSettings,
    reducedMotion: Boolean,
    studioCount: Int,
    historyCount: Int,
    onPickVideo: () -> Unit,
    onPickAny: () -> Unit,
    onLoadStudio: () -> Unit,
    onLoadHistory: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenSubtitlesDialog: () -> Unit,
    onOpenCastDialog: () -> Unit,
    onEnterPip: () -> Unit,
    onTogglePlaylist: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onSendTrimToStudio: () -> Unit,
    kamakerSpec: KamakerSpec? = null
) {
    if (uiState.currentTrack == null) {
        StudioSurfaceCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Load Media into Kvplayer",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pick files from device or import from Studio and Conversion History",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("kvplayer_empty_pick_video")
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Video")
                    }
                    OutlinedButton(
                        onClick = onPickAny,
                        modifier = Modifier.testTag("kvplayer_empty_pick_any")
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Audio / Any")
                    }
                    if (studioCount > 0) {
                        OutlinedButton(onClick = onLoadStudio) {
                            Text("Import Studio ($studioCount)")
                        }
                    }
                    if (historyCount > 0) {
                        OutlinedButton(onClick = onLoadHistory) {
                            Text("Import History ($historyCount)")
                        }
                    }
                }
            }
        }
    } else if (uiState.isFloatingMode && settings.kvShowFloatingButton) {
        StudioSurfaceCard(
            highlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .testTag("kvplayer_floating_placeholder")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureInPictureAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Playing in Floating Window",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Drag the floating player across tabs or dock it back here anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { controller.setFloatingMode(false) }) {
                        Text("Dock Back Here")
                    }
                    OutlinedButton(onClick = onEnterPip) {
                        Text("System PiP")
                    }
                }
            }
        }
    } else if (uiState.isFullscreen && settings.kvShowFullscreenButton) {
        StudioSurfaceCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { controller.setFullscreen(false) }) {
                    Text("Exit Full-Screen Player")
                }
            }
        }
    } else {
        KvPlayerViewport(
            exoPlayer = exoPlayer,
            uiState = uiState,
            isInPipMode = false,
            reducedMotion = reducedMotion,
            onTogglePlayPause = controller::togglePlayPause,
            onSeekTo = controller::seekTo,
            onSeekBy = controller::seekBy,
            onSkipNext = controller::skipNext,
            onSkipPrevious = controller::skipPrevious,
            onCycleAspectMode = controller::cycleAspectMode,
            onOpenSpeedDialog = onOpenSpeedDialog,
            onOpenSubtitlesDialog = onOpenSubtitlesDialog,
            onOpenCastDialog = onOpenCastDialog,
            onEnterPip = onEnterPip,
            onTogglePlaylistSheet = onTogglePlaylist,
            onRetry = controller::retryCurrentTrack,
            isFullscreen = false,
            showFullscreenButton = settings.kvShowFullscreenButton,
            showScreenshotButton = settings.kvShowScreenshotButton,
            showHideHudUiButton = settings.kvShowHideHudUiButton,
            showMarkInOut = settings.kvShowMarkInOut,
            showFloatingButton = settings.kvShowFloatingButton,
            onToggleFullscreen = { controller.setFullscreen(true) },
            onTakeScreenshot = onTakeScreenshot,
            onToggleHideDisplayVolumeUi = controller::toggleHideDisplayVolumeUi,
            onMarkIn = controller::markInAtCurrent,
            onMarkOut = controller::markOutAtCurrent,
            onToggleAbLoop = controller::toggleAbLoop,
            onClearMarks = controller::clearMarkInOut,
            onSendTrimToStudio = onSendTrimToStudio,
            onToggleFloating = { controller.setFloatingMode(true) },
            kamakerSpec = kamakerSpec
        )
    }
}

@Composable
private fun KvPlayerBottomTilesSection(
    uiState: KvPlayerUiState,
    hideBottomTiles: Boolean,
    onToggleHideBottomTiles: (Boolean) -> Unit,
    onOpenCast: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBgAudio: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hideBottomTiles) "Cast, PiP & Bg Audio Hidden" else "Playback Output Modes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilterChip(
                selected = hideBottomTiles,
                onClick = { onToggleHideBottomTiles(!hideBottomTiles) },
                label = {
                    Text(
                        text = if (hideBottomTiles) "Show Tiles" else "Hide Tiles",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (hideBottomTiles) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                modifier = Modifier.testTag("kvplayer_toggle_bottom_tiles_button")
            )
        }
        if (!hideBottomTiles) {
            KvPlayerFeatureTilesRow(
                uiState = uiState,
                onOpenCast = onOpenCast,
                onEnterPip = onEnterPip,
                onToggleBgAudio = onToggleBgAudio
            )
        }
    }
}

@Composable
private fun KvPlayerFeatureTilesRow(
    uiState: KvPlayerUiState,
    onOpenCast: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleBgAudio: () -> Unit
) {
    val studioColors = LocalStudioColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FeatureTileCard(
            title = "Casting",
            subtitle = "Wireless / App",
            icon = Icons.Rounded.Cast,
            active = false,
            accentColor = studioColors.success,
            onClick = onOpenCast,
            testTag = "kvplayer_tile_cast",
            modifier = Modifier.weight(1f)
        )
        FeatureTileCard(
            title = "PIP Mode",
            subtitle = "Floating Window",
            icon = Icons.Rounded.PictureInPictureAlt,
            active = false,
            accentColor = MaterialTheme.colorScheme.secondary,
            onClick = onEnterPip,
            testTag = "kvplayer_tile_pip",
            modifier = Modifier.weight(1f)
        )
        FeatureTileCard(
            title = "Bg Audio",
            subtitle = if (uiState.bgAudioEnabled) "Active" else "Off",
            icon = Icons.Rounded.Headphones,
            active = uiState.bgAudioEnabled,
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = onToggleBgAudio,
            testTag = "kvplayer_tile_bg_audio",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FeatureTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    StudioSurfaceCard(
        highlighted = active,
        onClick = onClick,
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = if (active) 0.24f else 0.14f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = if (active) 0.8f else 0.35f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KvPlaylistPanel(
    uiState: KvPlayerUiState,
    studioCount: Int,
    historyCount: Int,
    isMinimized: Boolean,
    isFloating: Boolean,
    onToggleMinimize: () -> Unit,
    onToggleFloating: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAny: () -> Unit,
    onLoadStudio: () -> Unit,
    onLoadHistory: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onClearPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .then(if (isMinimized) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isMinimized) 0.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Media Playlist (${uiState.playlist.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isMinimized) {
                        Text(
                            text = "Device picks, Studio clips & History exports",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.playlist.isNotEmpty() && !isMinimized) {
                        IconButton(onClick = onClearPlaylist, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleFloating,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("kvplayer_playlist_float_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterNone,
                            contentDescription = if (isFloating) "Dock playlist" else "Float playlist",
                            tint = if (isFloating) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleMinimize,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("kvplayer_playlist_minimize_button")
                    ) {
                        Icon(
                            imageVector = if (isMinimized) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = if (isMinimized) "Expand playlist" else "Minimize playlist",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!isMinimized) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickVideo,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Video")
                    }
                    OutlinedButton(
                        onClick = onPickAny,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Audio / Any")
                    }
                    OutlinedButton(
                        onClick = onLoadStudio,
                        enabled = studioCount > 0,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("kvplayer_import_studio_button")
                    ) {
                        Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Studio ($studioCount)")
                    }
                    OutlinedButton(
                        onClick = onLoadHistory,
                        enabled = historyCount > 0,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("kvplayer_import_history_button")
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("History ($historyCount)")
                    }
                }

                if (uiState.playlist.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Queue is empty. Add videos, audio, Studio clips, or History exports above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(uiState.playlist, key = { _, item -> item.id }) { index, track ->
                            val active = index == uiState.currentIndex
                            PlaylistTrackRow(
                                track = track,
                                active = active,
                                onClick = { onSelectTrack(index) },
                                onRemove = { onRemoveTrack(track.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    track: KvPlayerTrack,
    active: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else LocalStudioColors.current.hairlineBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (active) Icons.Rounded.PlayArrow else if (track.isAudioOnly) Icons.Rounded.AudioFile else Icons.Rounded.Movie,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val durText = if (track.durationMs > 0L) " • ${FormatUtils.formatDuration(track.durationMs)}" else ""
                Text(
                    text = "${track.sourceType.label}$durText",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${track.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun resolveShareableUri(context: android.content.Context, uri: Uri): Uri? {
    return if (uri.scheme == "file") {
        val path = uri.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    } else {
        uri
    }
}
