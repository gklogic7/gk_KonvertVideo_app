package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.domain.model.KamakerSpec
import com.example.domain.model.KvPlayerAspectMode
import com.example.domain.model.KvPlayerUiState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class GestureOverlayType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK_BACK,
    SEEK_FORWARD
}

@Composable
fun KvPlayerViewport(
    exoPlayer: ExoPlayer,
    uiState: KvPlayerUiState,
    isInPipMode: Boolean,
    reducedMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCycleAspectMode: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenSubtitlesDialog: () -> Unit,
    onOpenCastDialog: () -> Unit,
    onEnterPip: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onRetry: () -> Unit,
    isFullscreen: Boolean = false,
    showFullscreenButton: Boolean = false,
    showScreenshotButton: Boolean = false,
    showHideHudUiButton: Boolean = false,
    showMarkInOut: Boolean = false,
    showFloatingButton: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onTakeScreenshot: () -> Unit = {},
    onToggleHideDisplayVolumeUi: () -> Unit = {},
    onMarkIn: () -> Unit = {},
    onMarkOut: () -> Unit = {},
    onToggleAbLoop: () -> Unit = {},
    onClearMarks: () -> Unit = {},
    onSendTrimToStudio: () -> Unit = {},
    onToggleFloating: () -> Unit = {},
    kamakerSpec: KamakerSpec? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var hudVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var activeGesture by remember { mutableStateOf(GestureOverlayType.NONE) }

    val maxVol = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volumePercent by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol.toFloat()
        )
    }
    var brightnessPercent by remember {
        val current = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (current in 0f..1f) current else 0.55f)
    }

    // Auto-hide floating HUD after 3.5s while playing
    LaunchedEffect(hudVisible, uiState.isPlaying, interactionTick) {
        if (hudVisible && uiState.isPlaying && !isInPipMode) {
            delay(3500L)
            hudVisible = false
        }
    }

    // Auto-clear transient gesture indicator
    LaunchedEffect(activeGesture, brightnessPercent, volumePercent) {
        if (activeGesture != GestureOverlayType.NONE) {
            delay(1100L)
            activeGesture = GestureOverlayType.NONE
        }
    }

    val currentTrack = uiState.currentTrack
    val rawAspect = if (uiState.videoWidth > 0 && uiState.videoHeight > 0) {
        (uiState.videoWidth.toFloat() * uiState.pixelWidthHeightRatio) / uiState.videoHeight.toFloat()
    } else {
        16f / 9f
    }
    val effectiveAspect = (uiState.aspectMode.fixedRatio ?: rawAspect).coerceIn(0.35f, 3.2f)
    var shutterFlash by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isCapturingScreenshot) {
        if (uiState.isCapturingScreenshot) {
            shutterFlash = true
            delay(130L)
            shutterFlash = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isInPipMode || isFullscreen) Modifier.fillMaxSize()
                else Modifier.aspectRatio(16f / 9f)
            )
            .clip(if (isInPipMode || isFullscreen) RoundedCornerShape(0.dp) else MaterialTheme.shapes.large)
            .background(Color(0xFF05070B))
            .clipToBounds()
    ) {
        val boxW = maxWidth
        val boxH = maxHeight
        val containerRatio = if (boxH > 0.dp) boxW / boxH else 16f / 9f

        if (uiState.errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry Track")
                }
            }
        } else if (currentTrack?.isAudioOnly == true) {
            KvAudioCanvas(
                title = currentTrack.title,
                isPlaying = uiState.isPlaying && !reducedMotion,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val videoSizeModifier = if (uiState.aspectMode == KvPlayerAspectMode.FILL) {
                val targetW = if (effectiveAspect > containerRatio) boxH * effectiveAspect else boxW
                val targetH = if (effectiveAspect > containerRatio) boxH else boxW / effectiveAspect
                Modifier
                    .fillMaxSize()
                    .wrapContentSize(unbounded = true)
                    .size(width = targetW, height = targetH)
            } else {
                if (effectiveAspect > containerRatio) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(effectiveAspect)
                } else {
                    Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                        .height(boxH)
                        .aspectRatio(effectiveAspect)
                }
            }

            Box(
                modifier = videoSizeModifier.align(Alignment.Center)
            ) {
                PlayerSurface(
                    player = exoPlayer,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    modifier = Modifier.fillMaxSize()
                )
                if (kamakerSpec != null && kamakerSpec.showOnPlayer && kamakerSpec.hasActiveContent) {
                    KamakerLiveOverlayLayer(
                        spec = kamakerSpec,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Buffering spinner
        if (uiState.isBuffering && uiState.errorMessage == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        }

        // Brief screenshot shutter flash
        if (shutterFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.42f))
            )
        }

        // Gesture touch surface (disabled in PiP)
        if (!isInPipMode && uiState.errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                hudVisible = !hudVisible
                                interactionTick++
                            },
                            onDoubleTap = { offset ->
                                val isLeft = offset.x < size.width / 2f
                                if (isLeft) {
                                    onSeekBy(-10_000L)
                                    activeGesture = GestureOverlayType.SEEK_BACK
                                } else {
                                    onSeekBy(10_000L)
                                    activeGesture = GestureOverlayType.SEEK_FORWARD
                                }
                                interactionTick++
                            }
                        )
                    }
                    .pointerInput(activity, maxVol) {
                        var draggingLeftSide = true
                        detectVerticalDragGestures(
                            onDragStart = { startOffset ->
                                draggingLeftSide = startOffset.x < size.width / 2f
                                if (!draggingLeftSide) {
                                    volumePercent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol.toFloat()
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / size.height.toFloat().coerceAtLeast(1f)
                                if (draggingLeftSide) {
                                    val next = (brightnessPercent + delta * 1.25f).coerceIn(0.05f, 1f)
                                    brightnessPercent = next
                                    activity?.window?.let { win ->
                                        val lp = win.attributes
                                        lp.screenBrightness = next
                                        win.attributes = lp
                                    }
                                    activeGesture = GestureOverlayType.BRIGHTNESS
                                } else {
                                    val next = (volumePercent + delta * 1.25f).coerceIn(0f, 1f)
                                    volumePercent = next
                                    val targetVol = (next * maxVol).roundToInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    activeGesture = GestureOverlayType.VOLUME
                                }
                            }
                        )
                    }
            )
        }

        // Subtitles / CC Cue Overlay
        if (uiState.subtitlesEnabled && uiState.activeCues.isNotEmpty()) {
            Surface(
                color = Color(0xCC06090F),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (!isInPipMode && hudVisible) 114.dp else 14.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
            ) {
                Text(
                    text = uiState.activeCues.joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Left / Right Circular Brightness & Volume Indicators (can be hidden via Hide Display/Volume UI button)
        if (!isInPipMode) {
            AnimatedVisibility(
                visible = !uiState.hideDisplayVolumeUi && (
                    activeGesture == GestureOverlayType.BRIGHTNESS ||
                        (hudVisible && activeGesture == GestureOverlayType.NONE)
                    ),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
            ) {
                GestureIndicatorPill(
                    icon = Icons.Rounded.BrightnessHigh,
                    label = "${(brightnessPercent * 100).roundToInt()}%",
                    accentColor = Color(0xFFFFA000),
                    expanded = activeGesture == GestureOverlayType.BRIGHTNESS
                )
            }

            AnimatedVisibility(
                visible = !uiState.hideDisplayVolumeUi && (
                    activeGesture == GestureOverlayType.VOLUME ||
                        (hudVisible && activeGesture == GestureOverlayType.NONE)
                    ),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
            ) {
                GestureIndicatorPill(
                    icon = if (volumePercent <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    label = "${(volumePercent * 100).roundToInt()}%",
                    accentColor = Color(0xFF1E88E5),
                    expanded = activeGesture == GestureOverlayType.VOLUME
                )
            }

            // Double-Tap Seek Feedback Badge
            AnimatedVisibility(
                visible = activeGesture == GestureOverlayType.SEEK_BACK || activeGesture == GestureOverlayType.SEEK_FORWARD,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(220)),
                modifier = Modifier.align(
                    if (activeGesture == GestureOverlayType.SEEK_BACK) Alignment.CenterStart else Alignment.CenterEnd
                ).padding(horizontal = 44.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xCC0B0E14),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (activeGesture == GestureOverlayType.SEEK_BACK) "-10s" else "+10s",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // Top Floating HUD Bar
            AnimatedVisibility(
                visible = hudVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(220)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xD905070B), Color(0x0005070B))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                    ) {
                        Text(
                            text = currentTrack?.title ?: "Kvplayer",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentTrack != null) {
                            Text(
                                text = "${currentTrack.sourceType.label} • ${uiState.aspectMode.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (showHideHudUiButton) {
                        IconButton(
                            onClick = {
                                interactionTick++
                                onToggleHideDisplayVolumeUi()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("kvplayer_hide_ui_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.hideDisplayVolumeUi) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (uiState.hideDisplayVolumeUi) {
                                    "Show volume and brightness UI"
                                } else {
                                    "Hide volume and brightness UI"
                                },
                                tint = if (uiState.hideDisplayVolumeUi) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    Color.White
                                }
                            )
                        }
                    }

                    if (showScreenshotButton && currentTrack?.isAudioOnly == false) {
                        IconButton(
                            onClick = {
                                interactionTick++
                                onTakeScreenshot()
                            },
                            enabled = !uiState.isCapturingScreenshot,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("kvplayer_screenshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = "Capture video screenshot",
                                tint = if (uiState.isCapturingScreenshot) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                }
                            )
                        }
                    }

                    if (showFloatingButton && !isFullscreen) {
                        IconButton(
                            onClick = {
                                interactionTick++
                                onToggleFloating()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("kvplayer_float_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FilterNone,
                                contentDescription = "Detach into floating window",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            interactionTick++
                            onOpenCastDialog()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("kvplayer_top_cast_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cast,
                            contentDescription = "Cast or external display",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            interactionTick++
                            onOpenSubtitlesDialog()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("kvplayer_top_cc_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ClosedCaption,
                            contentDescription = "Subtitles and captions",
                            tint = if (uiState.subtitlesEnabled && (uiState.subtitleTracks.isNotEmpty() || currentTrack?.subtitleUri != null)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White
                            }
                        )
                    }

                    IconButton(
                        onClick = {
                            interactionTick++
                            onTogglePlaylistSheet()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("kvplayer_top_tracks_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Playback options",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom Floating Translucent Pill Dock (modeled after reference image)
            AnimatedVisibility(
                visible = hudVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(220)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                var scrubbing by remember { mutableStateOf(false) }
                var scrubMs by remember { mutableLongStateOf(0L) }
                val shownPos = if (scrubbing) scrubMs else uiState.positionMs
                val maxDur = uiState.durationMs.coerceAtLeast(1L)

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xCC0D121B),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // Scrubber + Speed Pill + Menu
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = FormatUtils.formatDuration(shownPos),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showMarkInOut && uiState.durationMs > 0L && (uiState.markInMs != null || uiState.markOutMs != null)) {
                                    val startFrac = ((uiState.markInMs ?: 0L).toFloat() / maxDur.toFloat()).coerceIn(0f, 1f)
                                    val endFrac = ((uiState.markOutMs ?: maxDur).toFloat() / maxDur.toFloat()).coerceIn(startFrac, 1f)
                                    val primaryColor = MaterialTheme.colorScheme.primary
                                    val secondaryColor = MaterialTheme.colorScheme.secondary
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                            .height(10.dp)
                                    ) {
                                        val leftX = size.width * startFrac
                                        val rightX = size.width * endFrac
                                        drawRoundRect(
                                            color = secondaryColor.copy(alpha = 0.42f),
                                            topLeft = Offset(leftX, size.height * 0.25f),
                                            size = Size((rightX - leftX).coerceAtLeast(3f), size.height * 0.5f),
                                            cornerRadius = CornerRadius(4f, 4f)
                                        )
                                        if (uiState.markInMs != null) {
                                            drawCircle(
                                                color = primaryColor,
                                                radius = 5f,
                                                center = Offset(leftX, size.height / 2f)
                                            )
                                        }
                                        if (uiState.markOutMs != null) {
                                            drawCircle(
                                                color = secondaryColor,
                                                radius = 5f,
                                                center = Offset(rightX, size.height / 2f)
                                            )
                                        }
                                    }
                                }

                                Slider(
                                    value = shownPos.coerceIn(0L, maxDur).toFloat(),
                                    onValueChange = {
                                        scrubbing = true
                                        scrubMs = it.toLong()
                                        interactionTick++
                                    },
                                    onValueChangeFinished = {
                                        onSeekTo(scrubMs)
                                        scrubbing = false
                                    },
                                    enabled = uiState.durationMs > 0L,
                                    valueRange = 0f..maxDur.toFloat(),
                                    modifier = Modifier.fillMaxSize(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                    )
                                )
                            }
                            Text(
                                text = FormatUtils.formatDuration(uiState.durationMs),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = Color.White.copy(alpha = 0.75f)
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clickable {
                                        interactionTick++
                                        onOpenSpeedDialog()
                                    }
                                    .testTag("kvplayer_speed_chip")
                            ) {
                                Text(
                                    text = "${uiState.playbackSpeed}x",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    interactionTick++
                                    onOpenSubtitlesDialog()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = "Subtitle & track menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Mark In / Mark Out & A-B Loop Capsule Row (gated by Settings)
                        if (showMarkInOut) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val hasIn = uiState.markInMs != null
                                val hasOut = uiState.markOutMs != null
                                HudActionChip(
                                    label = if (hasIn) "In ${FormatUtils.formatDuration(uiState.markInMs!!)}" else "[In",
                                    active = hasIn,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        interactionTick++
                                        onMarkIn()
                                    },
                                    modifier = Modifier.testTag("kvplayer_mark_in_button")
                                )
                                HudActionChip(
                                    label = if (hasOut) "Out ${FormatUtils.formatDuration(uiState.markOutMs!!)}" else "Out]",
                                    active = hasOut,
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    onClick = {
                                        interactionTick++
                                        onMarkOut()
                                    },
                                    modifier = Modifier.testTag("kvplayer_mark_out_button")
                                )
                                HudActionChip(
                                    label = "A-B Loop",
                                    icon = Icons.Rounded.Repeat,
                                    active = uiState.isAbLoopEnabled,
                                    enabled = uiState.hasValidMarkRange,
                                    accentColor = Color(0xFFFFB300),
                                    onClick = {
                                        interactionTick++
                                        onToggleAbLoop()
                                    },
                                    modifier = Modifier.testTag("kvplayer_ab_loop_button")
                                )
                                HudActionChip(
                                    label = "Trim Studio",
                                    icon = Icons.Rounded.ContentCut,
                                    active = uiState.markInMs != null || uiState.markOutMs != null,
                                    accentColor = LocalStudioColors.current.success,
                                    onClick = {
                                        interactionTick++
                                        onSendTrimToStudio()
                                    },
                                    modifier = Modifier.testTag("kvplayer_send_trim_button")
                                )
                                if (hasIn || hasOut) {
                                    IconButton(
                                        onClick = {
                                            interactionTick++
                                            onClearMarks()
                                        },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("kvplayer_clear_marks_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear In/Out markers",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Transport Buttons Row: AspectFit | Prev | -10s | Play/Pause | +10s | Next | PiP + Fullscreen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    interactionTick++
                                    onCycleAspectMode()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("kvplayer_aspect_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AspectRatio,
                                    contentDescription = "Cycle aspect fit (${uiState.aspectMode.label})",
                                    tint = Color.White
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        onSkipPrevious()
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                                }
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        onSeekBy(-10_000L)
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind 10s", tint = Color.White)
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clickable {
                                            interactionTick++
                                            onTogglePlayPause()
                                        }
                                        .testTag("kvplayer_play_pause_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        onSeekBy(10_000L)
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Rounded.FastForward, contentDescription = "Forward 10s", tint = Color.White)
                                }
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        onSkipNext()
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        interactionTick++
                                        onEnterPip()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("kvplayer_pip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PictureInPictureAlt,
                                        contentDescription = "Enter Picture-in-Picture",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                if (showFullscreenButton) {
                                    IconButton(
                                        onClick = {
                                            interactionTick++
                                            onToggleFullscreen()
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .testTag("kvplayer_fullscreen_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                            contentDescription = if (isFullscreen) "Exit full screen" else "Full screen player",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudActionChip(
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true
) {
    val bg = when {
        !enabled -> Color.White.copy(alpha = 0.05f)
        active -> accentColor.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val border = when {
        !enabled -> Color.White.copy(alpha = 0.12f)
        active -> accentColor.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.24f)
    }
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        active -> accentColor
        else -> Color.White
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun KvPlayerFullscreenDialog(
    exoPlayer: ExoPlayer,
    uiState: KvPlayerUiState,
    reducedMotion: Boolean,
    showScreenshotButton: Boolean,
    showHideHudUiButton: Boolean,
    showMarkInOut: Boolean,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCycleAspectMode: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenSubtitlesDialog: () -> Unit,
    onOpenCastDialog: () -> Unit,
    onEnterPip: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onRetry: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onToggleHideDisplayVolumeUi: () -> Unit,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit,
    onToggleAbLoop: () -> Unit,
    onClearMarks: () -> Unit,
    onSendTrimToStudio: () -> Unit,
    kamakerSpec: KamakerSpec? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window: Window? = (view.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            KvPlayerViewport(
                exoPlayer = exoPlayer,
                uiState = uiState,
                isInPipMode = false,
                reducedMotion = reducedMotion,
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onSeekBy = onSeekBy,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onCycleAspectMode = onCycleAspectMode,
                onOpenSpeedDialog = onOpenSpeedDialog,
                onOpenSubtitlesDialog = onOpenSubtitlesDialog,
                onOpenCastDialog = onOpenCastDialog,
                onEnterPip = onEnterPip,
                onTogglePlaylistSheet = onTogglePlaylistSheet,
                onRetry = onRetry,
                isFullscreen = true,
                showFullscreenButton = true,
                showScreenshotButton = showScreenshotButton,
                showHideHudUiButton = showHideHudUiButton,
                showMarkInOut = showMarkInOut,
                showFloatingButton = false,
                onToggleFullscreen = onDismiss,
                onTakeScreenshot = onTakeScreenshot,
                onToggleHideDisplayVolumeUi = onToggleHideDisplayVolumeUi,
                onMarkIn = onMarkIn,
                onMarkOut = onMarkOut,
                onToggleAbLoop = onToggleAbLoop,
                onClearMarks = onClearMarks,
                onSendTrimToStudio = onSendTrimToStudio,
                kamakerSpec = kamakerSpec,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun KvFloatingMiniPlayer(
    exoPlayer: ExoPlayer,
    uiState: KvPlayerUiState,
    reducedMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onExpandToKvPlayer: () -> Unit,
    onEnterPip: () -> Unit,
    onCloseFloating: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardWidthDp = 280.dp
    val cardHeightDp = 222.dp

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val cardW = with(density) { cardWidthDp.toPx() }
        val cardH = with(density) { cardHeightDp.toPx() }

        var offsetX by remember(maxW) {
            mutableFloatStateOf((maxW - cardW - 24f).coerceAtLeast(12f))
        }
        var offsetY by remember(maxH) {
            mutableFloatStateOf((maxH - cardH - 36f).coerceAtLeast(16f))
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF20D121B),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(cardWidthDp)
                .pointerInput(maxW, maxH) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(8f, (maxW - cardW - 8f).coerceAtLeast(8f))
                        offsetY = (offsetY + dragAmount.y).coerceIn(8f, (maxH - cardH - 8f).coerceAtLeast(8f))
                    }
                }
                .testTag("kvplayer_floating_window")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Drag & Action Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141B27))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = "Drag floating player",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = uiState.currentTrack?.title ?: "Kvplayer",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("kvplayer_floating_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureInPictureAlt,
                            contentDescription = "System PiP",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onExpandToKvPlayer,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("kvplayer_floating_expand_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "Dock back to Kvplayer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCloseFloating,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("kvplayer_floating_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close floating player",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Live Video / Audio Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color(0xFF05070B))
                        .clickable { onTogglePlayPause() }
                ) {
                    if (uiState.currentTrack?.isAudioOnly == true) {
                        KvAudioCanvas(
                            title = uiState.currentTrack?.title ?: "Audio",
                            isPlaying = uiState.isPlaying && !reducedMotion,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PlayerSurface(
                            player = exoPlayer,
                            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (uiState.subtitlesEnabled && uiState.activeCues.isNotEmpty()) {
                        Surface(
                            color = Color(0xCC06090F),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = uiState.activeCues.joinToString("\n"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Compact Bottom Transport
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${FormatUtils.formatDuration(uiState.positionMs)} / ${FormatUtils.formatDuration(uiState.durationMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onSeekBy(-10_000L) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = { onSeekBy(10_000L) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Rounded.FastForward, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureIndicatorPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    expanded: Boolean
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xD90B0E14),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = if (expanded) 0.95f else 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = Color.White
            )
        }
    }
}

@Composable
private fun KvAudioCanvas(
    title: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val phase = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "kvAudioWave")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28318f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
        animated
    } else {
        0.6f
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bars = 40
            val spacing = size.width / (bars * 1.4f)
            val barWidth = (size.width - spacing * (bars - 1)) / bars
            val centerY = size.height / 2f
            for (i in 0 until bars) {
                val wave = abs(sin((i * 0.42f) + phase))
                val heightFraction = if (isPlaying) (0.16f + 0.58f * wave) else (0.12f + 0.22f * wave)
                val barHeight = size.height * heightFraction
                val left = i * (barWidth + spacing)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(primary.copy(alpha = 0.34f), secondary.copy(alpha = 0.24f))
                    ),
                    topLeft = Offset(left, centerY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
