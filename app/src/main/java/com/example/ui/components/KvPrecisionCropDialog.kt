package com.example.ui.components

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.domain.model.CropAspectMode
import com.example.domain.model.KvPlayerAspectMode
import com.example.domain.model.LogoOverlayConfig
import com.example.domain.model.PrecisionCropPanelStyle
import com.example.domain.model.TextOverlayConfig
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoOverlayConfig
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

object KvPrecisionCropStateHelper {
    fun isCropInteractive(isPanelMinimized: Boolean, activeTab: PrecisionEditorTab, isKvHudTab: Boolean): Boolean {
        return !isPanelMinimized && !isKvHudTab && activeTab == PrecisionEditorTab.CROP
    }

    fun isOverlayInteractive(isPanelMinimized: Boolean, activeTab: PrecisionEditorTab, isKvHudTab: Boolean): Boolean {
        return !isPanelMinimized && !isKvHudTab && (activeTab == PrecisionEditorTab.LOGO || activeTab == PrecisionEditorTab.TEXT)
    }

    fun areSwipeGesturesActive(isPanelMinimized: Boolean): Boolean {
        return isPanelMinimized
    }
}

private enum class KvCropGestureFeedback {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK_BACK,
    SEEK_FORWARD
}

@Composable
fun KvPrecisionCropDialog(
    mediaUri: Uri,
    title: String,
    initialPositionMs: Long,
    initialCrop: VideoCropRegion,
    sourceWidth: Int?,
    sourceHeight: Int?,
    initialOverlay: VideoOverlayConfig,
    initialTab: PrecisionEditorTab,
    panelStyle: PrecisionCropPanelStyle,
    fullKvHudEnabled: Boolean,
    initialTrimStartMs: Long? = null,
    initialTrimEndMs: Long? = null,
    onChangePanelStyle: (PrecisionCropPanelStyle) -> Unit = {},
    onToggleFullKvHud: (Boolean) -> Unit = {},
    onUpdateTrimRange: (Long?, Long?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onApplyCropAndOverlay: (VideoCropRegion, VideoOverlayConfig) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val studioColors = LocalStudioColors.current

    var videoAspectRatio by remember(sourceWidth, sourceHeight) {
        val fallback = if (sourceWidth != null && sourceHeight != null && sourceWidth > 0 && sourceHeight > 0) {
            sourceWidth.toFloat() / sourceHeight.toFloat()
        } else {
            16f / 9f
        }
        mutableFloatStateOf(fallback.coerceIn(0.25f, 4f))
    }

    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var isKvHudTabSelected by remember { mutableStateOf(false) }
    var isPanelMinimized by remember { mutableStateOf(false) }
    var floatingOffsetX by remember { mutableFloatStateOf(0f) }
    var floatingOffsetY by remember { mutableFloatStateOf(0f) }
    var transportVisible by remember { mutableStateOf(true) }
    var aspectMode by remember { mutableStateOf(KvPlayerAspectMode.FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var draftCrop by remember(initialCrop, initialTab) {
        mutableStateOf(
            when {
                initialCrop.enabled -> initialCrop
                initialTab == PrecisionEditorTab.CROP ->
                    CropGeometry.fitAspectCropRegion(CropAspectMode.FREE, videoAspectRatio)
                else -> VideoCropRegion(enabled = false)
            }
        )
    }
    var draftOverlay by remember(initialOverlay) { mutableStateOf(initialOverlay) }
    var markInMs by remember(initialTrimStartMs) { mutableStateOf(initialTrimStartMs) }
    var markOutMs by remember(initialTrimEndMs) { mutableStateOf(initialTrimEndMs) }
    var abLoopEnabled by remember { mutableStateOf(initialTrimStartMs != null && initialTrimEndMs != null) }

    val dialogPlayer = remember(context) { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(initialPositionMs) }

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
        mutableFloatStateOf(if (current in 0f..1f) current else 0.6f)
    }
    var activeGesture by remember { mutableStateOf(KvCropGestureFeedback.NONE) }

    LaunchedEffect(activeGesture, brightnessPercent, volumePercent) {
        if (activeGesture != KvCropGestureFeedback.NONE) {
            delay(1000L)
            activeGesture = KvCropGestureFeedback.NONE
        }
    }

    DisposableEffect(dialogPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = dialogPlayer.duration
                if (dur > 0L) durationMs = dur
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                    videoAspectRatio = ratio.coerceIn(0.25f, 4f)
                }
            }
        }
        dialogPlayer.addListener(listener)
        onDispose {
            dialogPlayer.removeListener(listener)
            runCatching { dialogPlayer.release() }
        }
    }

    LaunchedEffect(mediaUri) {
        runCatching {
            dialogPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
            dialogPlayer.prepare()
            if (initialPositionMs > 0L) {
                dialogPlayer.seekTo(initialPositionMs)
            }
            dialogPlayer.playWhenReady = false
        }
    }

    LaunchedEffect(playbackSpeed) {
        runCatching {
            dialogPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
        }
    }

    LaunchedEffect(isPlaying, abLoopEnabled, markInMs, markOutMs) {
        while (isPlaying) {
            val pos = dialogPlayer.currentPosition.coerceAtLeast(0L)
            positionMs = pos
            if (dialogPlayer.duration > 0L) durationMs = dialogPlayer.duration
            val outPt = markOutMs
            val inPt = markInMs ?: 0L
            if (abLoopEnabled && outPt != null && outPt > inPt && pos >= outPt) {
                dialogPlayer.seekTo(inPt)
                positionMs = inPt
            }
            delay(160L)
        }
    }

    val commitAndClose = {
        val finalCrop = if (draftCrop.enabled && !draftCrop.isFullFrame) {
            draftCrop.copy(enabled = true)
        } else {
            VideoCropRegion(enabled = false)
        }
        if (markInMs != initialTrimStartMs || markOutMs != initialTrimEndMs) {
            onUpdateTrimRange(markInMs, markOutMs)
        }
        onApplyCropAndOverlay(finalCrop, draftOverlay)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler { onDismiss() }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("kv_precision_crop_dialog"),
            color = Color(0xFF05070B)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Kvplayer Crop Header Bar
                Surface(
                    color = Color(0xE60B0E14),
                    border = BorderStroke(1.dp, studioColors.hairlineBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close crop viewer", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kvplayer Precision Crop",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$title • ${CropGeometry.formatReadout(draftCrop, sourceWidth, sourceHeight)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Panel Style Switcher Chip (Bottom Drawer <-> Floating & Movable <-> Side Panel)
                        FilterChip(
                            selected = panelStyle != PrecisionCropPanelStyle.BOTTOM_DRAWER,
                            onClick = {
                                val styles = PrecisionCropPanelStyle.entries
                                val nextStyle = styles[(styles.indexOf(panelStyle) + 1) % styles.size]
                                floatingOffsetX = 0f
                                floatingOffsetY = 0f
                                onChangePanelStyle(nextStyle)
                            },
                            label = {
                                Text(
                                    text = when (panelStyle) {
                                        PrecisionCropPanelStyle.BOTTOM_DRAWER -> "Drawer"
                                        PrecisionCropPanelStyle.FLOATING_CARD -> "Floating"
                                        PrecisionCropPanelStyle.SIDE_PANEL -> "Side"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.FilterNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.testTag("kv_crop_style_switch_chip")
                        )

                        // Minimize / Expand Panel Toggle Button
                        OutlinedButton(
                            onClick = { isPanelMinimized = !isPanelMinimized },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isPanelMinimized) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("kv_crop_minimize_toggle")
                        ) {
                            Icon(
                                imageVector = if (isPanelMinimized) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = null,
                                tint = if (isPanelMinimized) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isPanelMinimized) "Expand" else "Minimize",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPanelMinimized) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Main Kvplayer Stage + Floating Card / Side Panel
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF05070B))
                ) {
                    val responsiveSideWidth = (maxWidth * 0.44f).coerceIn(200.dp, 310.dp)
                    Row(modifier = Modifier.fillMaxSize()) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF05070B)),
                            contentAlignment = Alignment.Center
                        ) {
                        val density = LocalDensity.current
                        val stageWidthPx = with(density) { maxWidth.toPx() }
                        val stageHeightPx = with(density) { maxHeight.toPx() }
                    val effectiveAspect = (aspectMode.fixedRatio ?: videoAspectRatio).coerceIn(0.25f, 4f)
                    val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 16f / 9f
                    val fitModifier = if (effectiveAspect > containerRatio) {
                        Modifier.fillMaxWidth().aspectRatio(effectiveAspect)
                    } else {
                        Modifier.fillMaxHeight().aspectRatio(effectiveAspect)
                    }

                    val cropInteractive = KvPrecisionCropStateHelper.isCropInteractive(
                        isPanelMinimized = isPanelMinimized,
                        activeTab = activeTab,
                        isKvHudTab = isKvHudTabSelected
                    )
                    val overlayInteractive = KvPrecisionCropStateHelper.isOverlayInteractive(
                        isPanelMinimized = isPanelMinimized,
                        activeTab = activeTab,
                        isKvHudTab = isKvHudTabSelected
                    )

                    Box(modifier = fitModifier) {
                        PlayerSurface(
                            player = dialogPlayer,
                            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (draftCrop.enabled || (!isKvHudTabSelected && activeTab == PrecisionEditorTab.CROP)) {
                            VideoCropOverlay(
                                cropRegion = if (draftCrop.enabled) draftCrop else VideoCropRegion(enabled = true),
                                videoAspectRatio = effectiveAspect,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                onCropChange = { draftCrop = it },
                                interactive = cropInteractive
                            )
                        }
                        VideoOverlayCanvasLayer(
                            cropRegion = draftCrop,
                            overlayConfig = draftOverlay,
                            activeTab = activeTab,
                            interactive = overlayInteractive,
                            onOverlayChange = { draftOverlay = it }
                        )
                    }

                    // When Panel is Minimized -> Boxes Lock & Swipe Gestures Control Brightness/Volume + Double-Tap Seek
                    if (KvPrecisionCropStateHelper.areSwipeGesturesActive(isPanelMinimized)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("kv_crop_minimized_gesture_layer")
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { transportVisible = !transportVisible },
                                        onDoubleTap = { offset ->
                                            val isLeft = offset.x < size.width / 2f
                                            val delta = if (isLeft) -10_000L else 10_000L
                                            val target = (dialogPlayer.currentPosition + delta)
                                                .coerceIn(0L, durationMs.coerceAtLeast(1L))
                                            dialogPlayer.seekTo(target)
                                            positionMs = target
                                            activeGesture = if (isLeft) {
                                                KvCropGestureFeedback.SEEK_BACK
                                            } else {
                                                KvCropGestureFeedback.SEEK_FORWARD
                                            }
                                        }
                                    )
                                }
                                .pointerInput(activity, maxVol) {
                                    var draggingLeft = true
                                    detectVerticalDragGestures(
                                        onDragStart = { pos ->
                                            draggingLeft = pos.x < size.width / 2f
                                            if (!draggingLeft) {
                                                volumePercent = audioManager
                                                    .getStreamVolume(AudioManager.STREAM_MUSIC)
                                                    .toFloat() / maxVol.toFloat()
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            val delta = -dragAmount / size.height.toFloat().coerceAtLeast(1f)
                                            if (draggingLeft) {
                                                val next = (brightnessPercent + delta * 1.25f).coerceIn(0.05f, 1f)
                                                brightnessPercent = next
                                                activity?.window?.let { win ->
                                                    val lp = win.attributes
                                                    lp.screenBrightness = next
                                                    win.attributes = lp
                                                }
                                                activeGesture = KvCropGestureFeedback.BRIGHTNESS
                                            } else {
                                                val next = (volumePercent + delta * 1.25f).coerceIn(0f, 1f)
                                                volumePercent = next
                                                val targetVol = (next * maxVol).roundToInt().coerceIn(0, maxVol)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                                activeGesture = KvCropGestureFeedback.VOLUME
                                            }
                                        }
                                    )
                                }
                        )

                        // Locked Status Pill at Top of Canvas
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xD90B0E14),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .testTag("kv_crop_locked_gesture_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Boxes Locked • Swipe Brightness/Volume • Double-Tap ±10s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Brightness / Volume / Seek HUD Feedback Pills
                    if (fullKvHudEnabled || isPanelMinimized) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = activeGesture == KvCropGestureFeedback.BRIGHTNESS,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(180)),
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                        ) {
                            KvCropHudBadge(
                                icon = Icons.Rounded.BrightnessHigh,
                                text = "${(brightnessPercent * 100).roundToInt()}%",
                                accent = Color(0xFFFFA000)
                            )
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = activeGesture == KvCropGestureFeedback.VOLUME,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(180)),
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                        ) {
                            KvCropHudBadge(
                                icon = if (volumePercent <= 0.01f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                text = "${(volumePercent * 100).roundToInt()}%",
                                accent = Color(0xFF1E88E5)
                            )
                        }
                    }

                    // Option 2: Floating & Movable Minimizable Card Overlay Mode
                    if (panelStyle == PrecisionCropPanelStyle.FLOATING_CARD) {
                        val maxDragX = (stageWidthPx * 0.42f).coerceAtLeast(80f)
                        val maxDragY = (stageHeightPx * 0.62f).coerceAtLeast(80f)
                        val clampedX = floatingOffsetX.coerceIn(-maxDragX, maxDragX)
                        val clampedY = floatingOffsetY.coerceIn(-maxDragY, 24f)

                        if (isPanelMinimized) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xEB0D121B),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                shadowElevation = 10.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp)
                                    .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                                    .pointerInput(maxDragX, maxDragY) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            floatingOffsetX = (floatingOffsetX + dragAmount.x).coerceIn(-maxDragX, 24f)
                                            floatingOffsetY = (floatingOffsetY + dragAmount.y).coerceIn(-maxDragY, 24f)
                                        }
                                    }
                                    .testTag("kv_crop_minimized_floating_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "Drag floating pill",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    TextButtonOrPill(
                                        label = "Expand Crop Panel",
                                        icon = Icons.Rounded.ExpandLess,
                                        color = MaterialTheme.colorScheme.primary,
                                        onClick = { isPanelMinimized = false }
                                    )
                                    Button(
                                        onClick = commitAndClose,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("apply_fullscreen_crop_button")
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Apply", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xF20D121B),
                                border = BorderStroke(1.dp, studioColors.hairlineBorderBright),
                                shadowElevation = 14.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                                    .widthIn(max = 600.dp)
                                    .heightIn(max = 360.dp)
                                    .fillMaxWidth()
                                    .testTag("kv_crop_floating_card_panel")
                            ) {
                                Column {
                                    // Movable Drag Handle Bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF151C28))
                                            .pointerInput(maxDragX, maxDragY) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    floatingOffsetX = (floatingOffsetX + dragAmount.x).coerceIn(-maxDragX, maxDragX)
                                                    floatingOffsetY = (floatingOffsetY + dragAmount.y).coerceIn(-maxDragY, 24f)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 5.dp)
                                            .testTag("kv_crop_floating_drag_handle"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = "Drag floating panel",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Floating Panel • Drag to Move",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    PrecisionEditorPanelContent(
                                        activeTab = activeTab,
                                        isKvHudTabSelected = isKvHudTabSelected,
                                        draftCrop = draftCrop,
                                        draftOverlay = draftOverlay,
                                        videoAspectRatio = videoAspectRatio,
                                        panelStyle = panelStyle,
                                        fullKvHudEnabled = fullKvHudEnabled,
                                        playbackSpeed = playbackSpeed,
                                        positionMs = positionMs,
                                        markInMs = markInMs,
                                        markOutMs = markOutMs,
                                        abLoopEnabled = abLoopEnabled,
                                        onSelectTab = { tab ->
                                            isKvHudTabSelected = false
                                            activeTab = tab
                                            if (tab == PrecisionEditorTab.CROP && !draftCrop.enabled) {
                                                draftCrop = CropGeometry.fitAspectCropRegion(CropAspectMode.FREE, videoAspectRatio)
                                            }
                                        },
                                        onSelectKvHudTab = { isKvHudTabSelected = true },
                                        onCropChange = { draftCrop = it },
                                        onOverlayChange = { draftOverlay = it },
                                        onToggleFullKvHud = onToggleFullKvHud,
                                        onChangeSpeed = { playbackSpeed = it },
                                        onMarkIn = { markInMs = positionMs },
                                        onMarkOut = { markOutMs = positionMs },
                                        onToggleAbLoop = { abLoopEnabled = !abLoopEnabled },
                                        onClearMarks = {
                                            markInMs = null
                                            markOutMs = null
                                            abLoopEnabled = false
                                        },
                                        onChangePanelStyle = onChangePanelStyle,
                                        onMinimize = { isPanelMinimized = true },
                                        onDismiss = onDismiss,
                                        onApply = commitAndClose
                                    )
                                }
                            }
                        }
                    }
                }

                    // Option 3: Minimizable Side Panel Style
                    if (panelStyle == PrecisionCropPanelStyle.SIDE_PANEL) {
                        if (isPanelMinimized) {
                            Surface(
                                color = Color(0xF20D121B),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                                modifier = Modifier
                                    .width(68.dp)
                                    .fillMaxHeight()
                                    .testTag("kv_crop_minimized_side_rail")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 12.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { isPanelMinimized = false },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("kv_crop_expand_side_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronLeft,
                                            contentDescription = "Expand Side Panel",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Boxes Locked",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Locked",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Button(
                                        onClick = commitAndClose,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("apply_fullscreen_crop_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Apply crop",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, studioColors.hairlineBorderBright),
                                shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                                modifier = Modifier
                                    .width(responsiveSideWidth)
                                    .fillMaxHeight()
                                    .testTag("kv_crop_side_panel")
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF151C28))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Side Studio Panel",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            onClick = { isPanelMinimized = true },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("kv_crop_minimize_side_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = "Minimize Side Panel",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        PrecisionEditorPanelContent(
                                            activeTab = activeTab,
                                            isKvHudTabSelected = isKvHudTabSelected,
                                            draftCrop = draftCrop,
                                            draftOverlay = draftOverlay,
                                            videoAspectRatio = videoAspectRatio,
                                            panelStyle = panelStyle,
                                            fullKvHudEnabled = fullKvHudEnabled,
                                            playbackSpeed = playbackSpeed,
                                            positionMs = positionMs,
                                            markInMs = markInMs,
                                            markOutMs = markOutMs,
                                            abLoopEnabled = abLoopEnabled,
                                            onSelectTab = { tab ->
                                                isKvHudTabSelected = false
                                                activeTab = tab
                                                if (tab == PrecisionEditorTab.CROP && !draftCrop.enabled) {
                                                    draftCrop = CropGeometry.fitAspectCropRegion(CropAspectMode.FREE, videoAspectRatio)
                                                }
                                            },
                                            onSelectKvHudTab = { isKvHudTabSelected = true },
                                            onCropChange = { draftCrop = it },
                                            onOverlayChange = { draftOverlay = it },
                                            onToggleFullKvHud = onToggleFullKvHud,
                                            onChangeSpeed = { playbackSpeed = it },
                                            onMarkIn = { markInMs = positionMs },
                                            onMarkOut = { markOutMs = positionMs },
                                            onToggleAbLoop = { abLoopEnabled = !abLoopEnabled },
                                            onClearMarks = {
                                                markInMs = null
                                                markOutMs = null
                                                abLoopEnabled = false
                                            },
                                            onChangePanelStyle = onChangePanelStyle,
                                            onMinimize = { isPanelMinimized = true },
                                            onDismiss = onDismiss,
                                            onApply = commitAndClose
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                // Core Kvplayer Transport Bar (Always accessible unless toggled off by single tap while minimized)
                AnimatedVisibility(visible = transportVisible || !isPanelMinimized) {
                    KvCropTransportBar(
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        playbackSpeed = playbackSpeed,
                        aspectMode = aspectMode,
                        fullKvHudEnabled = fullKvHudEnabled,
                        markInMs = markInMs,
                        markOutMs = markOutMs,
                        abLoopEnabled = abLoopEnabled,
                        onTogglePlayPause = {
                            if (dialogPlayer.playbackState == Player.STATE_ENDED) {
                                dialogPlayer.seekTo(0L)
                            }
                            if (dialogPlayer.isPlaying) dialogPlayer.pause() else dialogPlayer.play()
                        },
                        onSeekTo = {
                            positionMs = it
                            dialogPlayer.seekTo(it)
                        },
                        onSeekBy = { delta ->
                            val next = (dialogPlayer.currentPosition + delta).coerceIn(0L, durationMs.coerceAtLeast(1L))
                            positionMs = next
                            dialogPlayer.seekTo(next)
                        },
                        onCycleSpeed = {
                            val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                            val idx = speeds.indexOfFirst { it == playbackSpeed }
                            playbackSpeed = speeds[(idx + 1) % speeds.size]
                        },
                        onCycleAspect = {
                            val modes = KvPlayerAspectMode.entries
                            aspectMode = modes[(modes.indexOf(aspectMode) + 1) % modes.size]
                        },
                        onMarkIn = { markInMs = positionMs },
                        onMarkOut = { markOutMs = positionMs },
                        onToggleAbLoop = { abLoopEnabled = !abLoopEnabled },
                        onClearMarks = {
                            markInMs = null
                            markOutMs = null
                            abLoopEnabled = false
                        }
                    )
                }

                // Option 1: Bottom Drawer that Collapses to a Compact Pill Bar
                if (panelStyle == PrecisionCropPanelStyle.BOTTOM_DRAWER) {
                    if (isPanelMinimized) {
                        Surface(
                            color = Color(0xF20D121B),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("kv_crop_minimized_pill_bar")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Panel Minimized • Boxes Locked",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${CropGeometry.formatReadout(draftCrop, sourceWidth, sourceHeight)} • Swipe Vol/Bright",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OutlinedButton(
                                    onClick = { isPanelMinimized = false },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("kv_crop_expand_drawer_button")
                                ) {
                                    Icon(Icons.Rounded.ExpandLess, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Expand Panel")
                                }
                                Button(
                                    onClick = commitAndClose,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("apply_fullscreen_crop_button")
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Apply")
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, studioColors.hairlineBorderBright),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("kv_crop_bottom_drawer_panel")
                        ) {
                            PrecisionEditorPanelContent(
                                activeTab = activeTab,
                                isKvHudTabSelected = isKvHudTabSelected,
                                draftCrop = draftCrop,
                                draftOverlay = draftOverlay,
                                videoAspectRatio = videoAspectRatio,
                                panelStyle = panelStyle,
                                fullKvHudEnabled = fullKvHudEnabled,
                                playbackSpeed = playbackSpeed,
                                positionMs = positionMs,
                                markInMs = markInMs,
                                markOutMs = markOutMs,
                                abLoopEnabled = abLoopEnabled,
                                onSelectTab = { tab ->
                                    isKvHudTabSelected = false
                                    activeTab = tab
                                    if (tab == PrecisionEditorTab.CROP && !draftCrop.enabled) {
                                        draftCrop = CropGeometry.fitAspectCropRegion(CropAspectMode.FREE, videoAspectRatio)
                                    }
                                },
                                onSelectKvHudTab = { isKvHudTabSelected = true },
                                onCropChange = { draftCrop = it },
                                onOverlayChange = { draftOverlay = it },
                                onToggleFullKvHud = onToggleFullKvHud,
                                onChangeSpeed = { playbackSpeed = it },
                                onMarkIn = { markInMs = positionMs },
                                onMarkOut = { markOutMs = positionMs },
                                onToggleAbLoop = { abLoopEnabled = !abLoopEnabled },
                                onClearMarks = {
                                    markInMs = null
                                    markOutMs = null
                                    abLoopEnabled = false
                                },
                                onChangePanelStyle = onChangePanelStyle,
                                onMinimize = { isPanelMinimized = true },
                                onDismiss = onDismiss,
                                onApply = commitAndClose
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrecisionEditorPanelContent(
    activeTab: PrecisionEditorTab,
    isKvHudTabSelected: Boolean,
    draftCrop: VideoCropRegion,
    draftOverlay: VideoOverlayConfig,
    videoAspectRatio: Float,
    panelStyle: PrecisionCropPanelStyle,
    fullKvHudEnabled: Boolean,
    playbackSpeed: Float,
    positionMs: Long,
    markInMs: Long?,
    markOutMs: Long?,
    abLoopEnabled: Boolean,
    onSelectTab: (PrecisionEditorTab) -> Unit,
    onSelectKvHudTab: () -> Unit,
    onCropChange: (VideoCropRegion) -> Unit,
    onOverlayChange: (VideoOverlayConfig) -> Unit,
    onToggleFullKvHud: (Boolean) -> Unit,
    onChangeSpeed: (Float) -> Unit,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit,
    onToggleAbLoop: () -> Unit,
    onClearMarks: () -> Unit,
    onChangePanelStyle: (PrecisionCropPanelStyle) -> Unit,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tabs Row + Minimize Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrecisionEditorTab.entries.forEach { tab ->
                    val selected = !isKvHudTabSelected && activeTab == tab
                    val dot = when (tab) {
                        PrecisionEditorTab.CROP -> if (draftCrop.enabled && !draftCrop.isFullFrame) " •" else ""
                        PrecisionEditorTab.LOGO -> if (draftOverlay.logo.hasLogo) " •" else ""
                        PrecisionEditorTab.TEXT -> if (draftOverlay.textBox.hasText) " •" else ""
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        label = { Text("${tab.label}$dot") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("precision_tab_${tab.id}")
                    )
                }

                // Option 1 Setting inside Minimize Panel: Kvplayer HUD Options Tab
                FilterChip(
                    selected = isKvHudTabSelected,
                    onClick = onSelectKvHudTab,
                    label = { Text(if (fullKvHudEnabled) "Kvplayer HUD •" else "Kvplayer HUD") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("precision_tab_kvhud")
                )
            }

            IconButton(
                onClick = onMinimize,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("kv_crop_panel_minimize_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = "Minimize panel and lock boxes",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isKvHudTabSelected) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Full Kvplayer HUD Controls in Crop Viewer",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enable Mark In/Out A-B loop, speed chips, and Brightness/Volume HUD pills",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = fullKvHudEnabled,
                        onCheckedChange = onToggleFullKvHud,
                        modifier = Modifier.testTag("kv_crop_full_hud_switch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrecisionCropPanelStyle.entries.forEach { style ->
                        FilterChip(
                            selected = panelStyle == style,
                            onClick = { onChangePanelStyle(style) },
                            label = { Text(style.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                        FilterChip(
                            selected = playbackSpeed == spd,
                            onClick = { onChangeSpeed(spd) },
                            label = { Text("${spd}x", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = markInMs != null,
                        onClick = onMarkIn,
                        label = {
                            Text(
                                text = markInMs?.let { "[In ${FormatUtils.formatDuration(it)}" }
                                    ?: "[In ${FormatUtils.formatDuration(positionMs)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    )
                    FilterChip(
                        selected = markOutMs != null,
                        onClick = onMarkOut,
                        label = {
                            Text(
                                text = markOutMs?.let { "Out] ${FormatUtils.formatDuration(it)}" }
                                    ?: "Out] ${FormatUtils.formatDuration(positionMs)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    )
                    FilterChip(
                        selected = abLoopEnabled,
                        onClick = onToggleAbLoop,
                        label = { Text("A-B Loop") },
                        leadingIcon = { Icon(Icons.Rounded.Repeat, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    if (markInMs != null || markOutMs != null) {
                        OutlinedButton(onClick = onClearMarks) {
                            Text("Clear In/Out", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else {
            when (activeTab) {
                PrecisionEditorTab.CROP -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !draftCrop.enabled,
                            onClick = { onCropChange(VideoCropRegion(enabled = false)) },
                            label = { Text("Full Frame") }
                        )
                        CropAspectMode.entries.forEach { mode ->
                            val selected = draftCrop.enabled && draftCrop.aspectMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onCropChange(
                                        CropGeometry.fitAspectCropRegion(
                                            mode = mode,
                                            videoAspectRatio = videoAspectRatio,
                                            current = draftCrop
                                        )
                                    )
                                },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                PrecisionEditorTab.LOGO -> {
                    LogoOverlayControlPanel(
                        logo = draftOverlay.logo,
                        onChange = { onOverlayChange(draftOverlay.copy(logo = it)) }
                    )
                }

                PrecisionEditorTab.TEXT -> {
                    TextOverlayControlPanel(
                        textBox = draftOverlay.textBox,
                        onChange = { onOverlayChange(draftOverlay.copy(textBox = it)) }
                    )
                }
            }
        }

        // Action Footer inside Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (isKvHudTabSelected) {
                        onClearMarks()
                    } else {
                        when (activeTab) {
                            PrecisionEditorTab.CROP ->
                                onCropChange(CropGeometry.fitAspectCropRegion(CropAspectMode.FREE, videoAspectRatio))
                            PrecisionEditorTab.LOGO ->
                                onOverlayChange(draftOverlay.copy(logo = LogoOverlayConfig()))
                            PrecisionEditorTab.TEXT ->
                                onOverlayChange(draftOverlay.copy(textBox = TextOverlayConfig()))
                        }
                    }
                }
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("apply_fullscreen_crop_button")
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Apply")
            }
        }
    }
}

@Composable
private fun KvCropTransportBar(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    aspectMode: KvPlayerAspectMode,
    fullKvHudEnabled: Boolean,
    markInMs: Long?,
    markOutMs: Long?,
    abLoopEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleAspect: () -> Unit,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit,
    onToggleAbLoop: () -> Unit,
    onClearMarks: () -> Unit
) {
    val maxDur = durationMs.coerceAtLeast(1L)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Surface(
        color = Color(0xE60B0E14),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = primary
                    )
                }
                IconButton(onClick = { onSeekBy(-10_000L) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onSeekBy(10_000L) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = FormatUtils.formatDuration(positionMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = Color.White
                )
                Box(modifier = Modifier.weight(1f).height(26.dp), contentAlignment = Alignment.Center) {
                    if (durationMs > 0L && (markInMs != null || markOutMs != null)) {
                        val sFrac = ((markInMs ?: 0L).toFloat() / maxDur.toFloat()).coerceIn(0f, 1f)
                        val eFrac = ((markOutMs ?: maxDur).toFloat() / maxDur.toFloat()).coerceIn(sFrac, 1f)
                        Canvas(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(8.dp)) {
                            drawRoundRect(
                                color = secondary.copy(alpha = 0.45f),
                                topLeft = Offset(size.width * sFrac, 0f),
                                size = Size((size.width * (eFrac - sFrac)).coerceAtLeast(3f), size.height),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                    Slider(
                        value = positionMs.coerceIn(0L, maxDur).toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
                        enabled = durationMs > 0L,
                        valueRange = 0f..maxDur.toFloat(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = FormatUtils.formatDuration(durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = Color.White.copy(alpha = 0.75f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = primary.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.45f)),
                    modifier = Modifier.clickable { onCycleSpeed() }
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                IconButton(onClick = onCycleAspect, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.AspectRatio, contentDescription = "Aspect fit (${aspectMode.label})", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            if (fullKvHudEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null, tint = secondary, modifier = Modifier.size(14.dp))
                    FilterChip(
                        selected = markInMs != null,
                        onClick = onMarkIn,
                        label = {
                            Text(
                                text = markInMs?.let { "[In ${FormatUtils.formatDuration(it)}" } ?: "[In",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    )
                    FilterChip(
                        selected = markOutMs != null,
                        onClick = onMarkOut,
                        label = {
                            Text(
                                text = markOutMs?.let { "Out] ${FormatUtils.formatDuration(it)}" } ?: "Out]",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    )
                    FilterChip(
                        selected = abLoopEnabled,
                        onClick = onToggleAbLoop,
                        label = { Text("A-B Loop", style = MaterialTheme.typography.labelSmall) }
                    )
                    if (markInMs != null || markOutMs != null) {
                        IconButton(onClick = onClearMarks, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear In/Out", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KvCropHudBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xD90B0E14),
        border = BorderStroke(1.5.dp, accent)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(shape = CircleShape, color = accent, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = Color.White)
        }
    }
}

@Composable
private fun TextButtonOrPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
