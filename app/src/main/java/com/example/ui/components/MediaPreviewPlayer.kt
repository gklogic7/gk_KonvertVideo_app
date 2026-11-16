package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.platform.testTag
import com.example.domain.model.PrecisionCropPanelStyle
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoOverlayConfig
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun MediaPreviewPlayer(
    mediaUri: Uri,
    title: String,
    isAudioOnly: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    sourceWidth: Int? = null,
    sourceHeight: Int? = null,
    enableCropOverlay: Boolean = false,
    enableInlineCrop: Boolean = true,
    enableFullscreenCrop: Boolean = true,
    useKvPlayerInPrecisionCrop: Boolean = false,
    precisionCropPanelStyle: PrecisionCropPanelStyle = PrecisionCropPanelStyle.BOTTOM_DRAWER,
    precisionCropFullKvHud: Boolean = true,
    onChangePrecisionCropPanelStyle: (PrecisionCropPanelStyle) -> Unit = {},
    onTogglePrecisionCropFullKvHud: (Boolean) -> Unit = {},
    cropRegion: VideoCropRegion = VideoCropRegion(),
    onUpdateCropRegion: (VideoCropRegion) -> Unit = {},
    onResetCropRegion: () -> Unit = {},
    overlayConfig: VideoOverlayConfig = VideoOverlayConfig(),
    onUpdateCropAndOverlay: (VideoCropRegion, VideoOverlayConfig) -> Unit = { c, _ -> onUpdateCropRegion(c) },
    externalPrecisionTab: PrecisionEditorTab? = null,
    onConsumeExternalPrecisionTab: () -> Unit = {},
    enableMarkInOut: Boolean = false,
    trimStartMs: Long? = null,
    trimEndMs: Long? = null,
    onUpdateTrimRange: (Long?, Long?) -> Unit = { _, _ -> },
    onClearTrim: () -> Unit = {}
) {
    val context = LocalContext.current
    val studioColors = LocalStudioColors.current

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var playerErrorMsg by remember { mutableStateOf<String?>(null) }
    var showFullscreenCrop by remember { mutableStateOf(false) }
    var fullscreenInitialTab by remember { mutableStateOf(PrecisionEditorTab.CROP) }

    LaunchedEffect(externalPrecisionTab) {
        if (externalPrecisionTab != null && !isAudioOnly) {
            if (exoPlayer.isPlaying) exoPlayer.pause()
            fullscreenInitialTab = externalPrecisionTab
            showFullscreenCrop = true
            onConsumeExternalPrecisionTab()
        }
    }

    var videoAspectRatio by remember(mediaUri, sourceWidth, sourceHeight) {
        val initial = if (sourceWidth != null && sourceHeight != null && sourceWidth > 0 && sourceHeight > 0) {
            (sourceWidth.toFloat() / sourceHeight.toFloat()).coerceIn(0.35f, 3.2f)
        } else {
            16f / 9f
        }
        mutableFloatStateOf(initial)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration
                if (dur > 0L) durationMs = dur
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                    videoAspectRatio = ratio.coerceIn(0.35f, 3.2f)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isPlaying = false
                playerErrorMsg = "Native preview unavailable for this container — ready for FFmpeg conversion."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            runCatching { exoPlayer.release() }
        }
    }

    LaunchedEffect(mediaUri) {
        playerErrorMsg = null
        positionMs = 0L
        durationMs = 0L
        runCatching {
            exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = false
        }.onFailure {
            playerErrorMsg = "Preview stream could not be opened."
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0L) {
                durationMs = exoPlayer.duration
            }
            delay(200L)
        }
    }

    if (showFullscreenCrop && !isAudioOnly) {
        if (useKvPlayerInPrecisionCrop) {
            KvPrecisionCropDialog(
                mediaUri = mediaUri,
                title = title,
                initialPositionMs = positionMs,
                initialCrop = cropRegion,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                initialOverlay = overlayConfig,
                initialTab = fullscreenInitialTab,
                panelStyle = precisionCropPanelStyle,
                fullKvHudEnabled = precisionCropFullKvHud,
                initialTrimStartMs = trimStartMs,
                initialTrimEndMs = trimEndMs,
                onChangePanelStyle = onChangePrecisionCropPanelStyle,
                onToggleFullKvHud = onTogglePrecisionCropFullKvHud,
                onUpdateTrimRange = onUpdateTrimRange,
                onDismiss = { showFullscreenCrop = false },
                onApplyCropAndOverlay = { appliedCrop, appliedOverlay ->
                    onUpdateCropAndOverlay(appliedCrop, appliedOverlay)
                    showFullscreenCrop = false
                }
            )
        } else {
            FullScreenCropDialog(
                mediaUri = mediaUri,
                title = title,
                initialPositionMs = positionMs,
                initialCrop = cropRegion,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                initialOverlay = overlayConfig,
                initialTab = fullscreenInitialTab,
                onDismiss = { showFullscreenCrop = false },
                onApplyCropAndOverlay = { appliedCrop, appliedOverlay ->
                    onUpdateCropAndOverlay(appliedCrop, appliedOverlay)
                    showFullscreenCrop = false
                }
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, studioColors.hairlineBorder)
    ) {
        Column {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isAudioOnly || playerErrorMsg != null) Modifier.height(148.dp) else Modifier.aspectRatio(16f / 9f)
                    )
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (playerErrorMsg != null) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = playerErrorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        TextButton(
                            onClick = {
                                playerErrorMsg = null
                                runCatching {
                                    exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
                                    exoPlayer.prepare()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry Preview")
                        }
                    }
                } else if (isAudioOnly) {
                    AudioStudioWaveformCanvas(
                        isPlaying = isPlaying && !reducedMotion,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Audio preview",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 16f / 9f
                    val fitModifier = if (videoAspectRatio > containerRatio) {
                        Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
                    } else {
                        Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
                    }

                    Box(modifier = fitModifier) {
                        PlayerSurface(
                            player = exoPlayer,
                            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (enableCropOverlay && enableInlineCrop && cropRegion.enabled) {
                            VideoCropOverlay(
                                cropRegion = cropRegion,
                                videoAspectRatio = videoAspectRatio,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                onCropChange = onUpdateCropRegion
                            )
                        }
                        if (overlayConfig.isActive) {
                            VideoOverlayCanvasLayer(
                                cropRegion = cropRegion,
                                overlayConfig = overlayConfig,
                                activeTab = PrecisionEditorTab.CROP,
                                interactive = false,
                                onOverlayChange = {}
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !isAudioOnly &&
                    playerErrorMsg == null &&
                    enableCropOverlay &&
                    (enableInlineCrop || enableFullscreenCrop),
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(160))
            ) {
                CropToolbarStrip(
                    cropRegion = cropRegion,
                    videoAspectRatio = videoAspectRatio,
                    enableInlineCrop = enableInlineCrop,
                    enableFullscreenCrop = enableFullscreenCrop,
                    onUpdateCropRegion = onUpdateCropRegion,
                    onResetCropRegion = onResetCropRegion,
                    onOpenFullscreenCrop = {
                        if (exoPlayer.isPlaying) exoPlayer.pause()
                        fullscreenInitialTab = PrecisionEditorTab.CROP
                        showFullscreenCrop = true
                    },
                    hasActiveOverlays = overlayConfig.isActive,
                    onOpenOverlayTab = { tab ->
                        if (exoPlayer.isPlaying) exoPlayer.pause()
                        fullscreenInitialTab = tab
                        showFullscreenCrop = true
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (playerErrorMsg != null) return@IconButton
                        if (exoPlayer.playbackState == Player.STATE_ENDED) {
                            exoPlayer.seekTo(0L)
                        }
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    enabled = playerErrorMsg == null,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                        tint = if (playerErrorMsg == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = FormatUtils.formatDuration(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                    onValueChange = { target ->
                        positionMs = target.toLong()
                        exoPlayer.seekTo(target.toLong())
                    },
                    enabled = playerErrorMsg == null && durationMs > 0L,
                    valueRange = 0f..sliderMax,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )

                Text(
                    text = FormatUtils.formatDuration(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (isMuted) "Unmute preview" else "Mute preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = enableMarkInOut && playerErrorMsg == null,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(160))
            ) {
                StudioMarkInOutStrip(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    onMarkIn = {
                        val nextEnd = if (trimEndMs != null && trimEndMs <= positionMs) null else trimEndMs
                        onUpdateTrimRange(positionMs, nextEnd)
                    },
                    onMarkOut = {
                        val nextStart = if (trimStartMs != null && trimStartMs >= positionMs) null else trimStartMs
                        onUpdateTrimRange(nextStart, positionMs)
                    },
                    onClearTrim = onClearTrim
                )
            }
        }
    }
}

@Composable
private fun StudioMarkInOutStrip(
    positionMs: Long,
    durationMs: Long,
    trimStartMs: Long?,
    trimEndMs: Long?,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit,
    onClearTrim: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val hasRange = trimStartMs != null || trimEndMs != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (durationMs > 0L && hasRange) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
            ) {
                drawRect(color = primary.copy(alpha = 0.16f))
                val startFrac = ((trimStartMs ?: 0L).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val endFrac = ((trimEndMs ?: durationMs).toFloat() / durationMs.toFloat()).coerceIn(startFrac, 1f)
                drawRect(
                    color = secondary,
                    topLeft = Offset(size.width * startFrac, 0f),
                    size = Size(size.width * (endFrac - startFrac).coerceAtLeast(0.01f), size.height)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCut,
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(16.dp)
            )
            FilterChip(
                selected = trimStartMs != null,
                onClick = onMarkIn,
                label = {
                    Text(
                        text = trimStartMs?.let { "[In ${FormatUtils.formatDuration(it)}" } ?: "[In ${FormatUtils.formatDuration(positionMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = secondary
                ),
                modifier = Modifier.testTag("studio_player_mark_in_button")
            )
            FilterChip(
                selected = trimEndMs != null,
                onClick = onMarkOut,
                label = {
                    Text(
                        text = trimEndMs?.let { "Out] ${FormatUtils.formatDuration(it)}" } ?: "Out] ${FormatUtils.formatDuration(positionMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = secondary
                ),
                modifier = Modifier.testTag("studio_player_mark_out_button")
            )
            Spacer(Modifier.weight(1f))
            if (hasRange) {
                IconButton(
                    onClick = onClearTrim,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("studio_player_clear_trim_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear trim range",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioStudioWaveformCanvas(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val phase = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "waveform")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
        animated
    } else {
        0.7f
    }

    Canvas(modifier = modifier) {
        val bars = 36
        val spacing = size.width / (bars * 1.4f)
        val barWidth = (size.width - spacing * (bars - 1)) / bars
        val centerY = size.height / 2f

        for (i in 0 until bars) {
            val wave = kotlin.math.abs(sin((i * 0.42f) + phase))
            val heightFraction = if (isPlaying) (0.18f + 0.65f * wave) else (0.14f + 0.25f * wave)
            val barHeight = size.height * heightFraction
            val left = i * (barWidth + spacing)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(primary.copy(alpha = 0.28f), secondary.copy(alpha = 0.22f))
                ),
                topLeft = Offset(left, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
