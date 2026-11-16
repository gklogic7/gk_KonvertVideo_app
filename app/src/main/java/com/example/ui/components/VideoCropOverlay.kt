package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.domain.model.CropAspectMode
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoOverlayConfig
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import com.example.ui.util.FormatUtils
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class CropDragTarget {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    MOVE_BOX
}

object CropGeometry {
    private const val MIN_SPAN = 0.14f

    fun fitAspectCropRegion(
        mode: CropAspectMode,
        videoAspectRatio: Float,
        current: VideoCropRegion? = null
    ): VideoCropRegion {
        val safeAspect = videoAspectRatio.coerceIn(0.25f, 4f)
        val targetRatio = mode.targetRatio
        if (targetRatio == null) {
            return if (current == null || !current.enabled || current.isFullFrame) {
                VideoCropRegion(
                    enabled = true,
                    aspectMode = CropAspectMode.FREE,
                    left = 0.08f,
                    top = 0.08f,
                    right = 0.92f,
                    bottom = 0.92f
                )
            } else {
                current.copy(enabled = true, aspectMode = CropAspectMode.FREE)
            }
        }

        val normRatio = targetRatio / safeAspect
        val fill = 0.92f
        val (normW, normH) = if (normRatio >= 1f) {
            val w = fill
            val h = (w / normRatio).coerceIn(MIN_SPAN, fill)
            (h * normRatio).coerceAtMost(fill) to h
        } else {
            val h = fill
            val w = (h * normRatio).coerceIn(MIN_SPAN, fill)
            w to (w / normRatio).coerceAtMost(fill)
        }

        val centerX = current?.takeIf { it.enabled }?.let { (it.left + it.right) / 2f } ?: 0.5f
        val centerY = current?.takeIf { it.enabled }?.let { (it.top + it.bottom) / 2f } ?: 0.5f

        val halfW = normW / 2f
        val halfH = normH / 2f
        val left = (centerX - halfW).coerceIn(0f, 1f - normW)
        val top = (centerY - halfH).coerceIn(0f, 1f - normH)

        return VideoCropRegion(
            enabled = true,
            aspectMode = mode,
            left = left,
            top = top,
            right = (left + normW).coerceAtMost(1f),
            bottom = (top + normH).coerceAtMost(1f)
        )
    }

    fun moveCropRegion(
        crop: VideoCropRegion,
        deltaXNorm: Float,
        deltaYNorm: Float
    ): VideoCropRegion {
        val w = (crop.right - crop.left).coerceIn(MIN_SPAN, 1f)
        val h = (crop.bottom - crop.top).coerceIn(MIN_SPAN, 1f)
        val newLeft = (crop.left + deltaXNorm).coerceIn(0f, 1f - w)
        val newTop = (crop.top + deltaYNorm).coerceIn(0f, 1f - h)
        return crop.copy(
            enabled = true,
            left = newLeft,
            top = newTop,
            right = newLeft + w,
            bottom = newTop + h
        )
    }

    fun resizeCropCorner(
        crop: VideoCropRegion,
        corner: CropDragTarget,
        deltaXNorm: Float,
        deltaYNorm: Float,
        videoAspectRatio: Float
    ): VideoCropRegion {
        val safeAspect = videoAspectRatio.coerceIn(0.25f, 4f)
        val targetRatio = crop.aspectMode.targetRatio

        if (targetRatio == null) {
            var left = crop.left
            var top = crop.top
            var right = crop.right
            var bottom = crop.bottom
            when (corner) {
                CropDragTarget.TOP_LEFT -> {
                    left = (left + deltaXNorm).coerceIn(0f, right - MIN_SPAN)
                    top = (top + deltaYNorm).coerceIn(0f, bottom - MIN_SPAN)
                }
                CropDragTarget.TOP_RIGHT -> {
                    right = (right + deltaXNorm).coerceIn(left + MIN_SPAN, 1f)
                    top = (top + deltaYNorm).coerceIn(0f, bottom - MIN_SPAN)
                }
                CropDragTarget.BOTTOM_LEFT -> {
                    left = (left + deltaXNorm).coerceIn(0f, right - MIN_SPAN)
                    bottom = (bottom + deltaYNorm).coerceIn(top + MIN_SPAN, 1f)
                }
                CropDragTarget.BOTTOM_RIGHT -> {
                    right = (right + deltaXNorm).coerceIn(left + MIN_SPAN, 1f)
                    bottom = (bottom + deltaYNorm).coerceIn(top + MIN_SPAN, 1f)
                }
                else -> Unit
            }
            return crop.copy(enabled = true, left = left, top = top, right = right, bottom = bottom)
        }

        val normRatio = targetRatio / safeAspect
        val anchorX: Float
        val anchorY: Float
        val rawDragX: Float
        val rawDragY: Float
        val signX: Float
        val signY: Float

        when (corner) {
            CropDragTarget.TOP_LEFT -> {
                anchorX = crop.right
                anchorY = crop.bottom
                rawDragX = crop.left + deltaXNorm
                rawDragY = crop.top + deltaYNorm
                signX = -1f
                signY = -1f
            }
            CropDragTarget.TOP_RIGHT -> {
                anchorX = crop.left
                anchorY = crop.bottom
                rawDragX = crop.right + deltaXNorm
                rawDragY = crop.top + deltaYNorm
                signX = 1f
                signY = -1f
            }
            CropDragTarget.BOTTOM_LEFT -> {
                anchorX = crop.right
                anchorY = crop.top
                rawDragX = crop.left + deltaXNorm
                rawDragY = crop.bottom + deltaYNorm
                signX = -1f
                signY = 1f
            }
            CropDragTarget.BOTTOM_RIGHT -> {
                anchorX = crop.left
                anchorY = crop.top
                rawDragX = crop.right + deltaXNorm
                rawDragY = crop.bottom + deltaYNorm
                signX = 1f
                signY = 1f
            }
            else -> return crop
        }

        val maxAvailW = if (signX < 0f) anchorX else (1f - anchorX)
        val maxAvailH = if (signY < 0f) anchorY else (1f - anchorY)

        val candidateWFromX = if (signX < 0f) (anchorX - rawDragX) else (rawDragX - anchorX)
        val candidateHFromY = if (signY < 0f) (anchorY - rawDragY) else (rawDragY - anchorY)

        var w = if (abs(deltaXNorm) >= abs(deltaYNorm)) {
            candidateWFromX
        } else {
            candidateHFromY * normRatio
        }
        var h = w / normRatio

        val minW = maxOf(MIN_SPAN, MIN_SPAN * normRatio)
        val minH = minW / normRatio
        if (w < minW || h < minH) {
            w = minW
            h = minH
        }
        if (w > maxAvailW) {
            w = maxAvailW
            h = w / normRatio
        }
        if (h > maxAvailH) {
            h = maxAvailH
            w = h * normRatio
        }
        if (w < MIN_SPAN || h < MIN_SPAN) {
            return crop
        }

        val newLeft = if (signX < 0f) anchorX - w else anchorX
        val newRight = if (signX < 0f) anchorX else anchorX + w
        val newTop = if (signY < 0f) anchorY - h else anchorY
        val newBottom = if (signY < 0f) anchorY else anchorY + h

        return crop.copy(
            enabled = true,
            left = newLeft.coerceIn(0f, 1f),
            top = newTop.coerceIn(0f, 1f),
            right = newRight.coerceIn(0f, 1f),
            bottom = newBottom.coerceIn(0f, 1f)
        )
    }

    fun formatReadout(
        crop: VideoCropRegion,
        sourceWidth: Int?,
        sourceHeight: Int?
    ): String {
        val ratioPrefix = if (crop.aspectMode == CropAspectMode.FREE) "Free" else crop.aspectMode.label
        return if (sourceWidth != null && sourceHeight != null && sourceWidth > 0 && sourceHeight > 0) {
            val pxW = (((sourceWidth * crop.widthFraction) / 2f).toInt() * 2).coerceAtLeast(2)
            val pxH = (((sourceHeight * crop.heightFraction) / 2f).toInt() * 2).coerceAtLeast(2)
            "$ratioPrefix • ${pxW}×${pxH}"
        } else {
            val pctW = (crop.widthFraction * 100f).roundToInt()
            val pctH = (crop.heightFraction * 100f).roundToInt()
            "$ratioPrefix • ${pctW}%×${pctH}%"
        }
    }
}

@Composable
fun VideoCropOverlay(
    cropRegion: VideoCropRegion,
    videoAspectRatio: Float,
    sourceWidth: Int?,
    sourceHeight: Int?,
    onCropChange: (VideoCropRegion) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    val primary = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.secondary
    val latestCrop by rememberUpdatedState(cropRegion)
    val latestAspect by rememberUpdatedState(videoAspectRatio)
    val latestOnCropChange by rememberUpdatedState(onCropChange)

    var activeTarget by remember { mutableStateOf(CropDragTarget.NONE) }
    val hitRadiusPx = with(LocalDensity.current) { 30.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (interactive) {
                    Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val c = latestCrop
                        val l = c.left * w
                        val t = c.top * h
                        val r = c.right * w
                        val b = c.bottom * h

                        val corners = listOf(
                            CropDragTarget.TOP_LEFT to Offset(l, t),
                            CropDragTarget.TOP_RIGHT to Offset(r, t),
                            CropDragTarget.BOTTOM_LEFT to Offset(l, b),
                            CropDragTarget.BOTTOM_RIGHT to Offset(r, b)
                        )
                        val nearestCorner = corners
                            .map { (target, pos) -> target to hypot(offset.x - pos.x, offset.y - pos.y) }
                            .filter { it.second <= hitRadiusPx }
                            .minByOrNull { it.second }
                            ?.first

                        activeTarget = when {
                            nearestCorner != null -> nearestCorner
                            offset.x in l..r && offset.y in t..b -> CropDragTarget.MOVE_BOX
                            else -> CropDragTarget.NONE
                        }
                    },
                    onDragEnd = { activeTarget = CropDragTarget.NONE },
                    onDragCancel = { activeTarget = CropDragTarget.NONE },
                    onDrag = { change, dragAmount ->
                        if (activeTarget == CropDragTarget.NONE) return@detectDragGestures
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h
                        val updated = if (activeTarget == CropDragTarget.MOVE_BOX) {
                            CropGeometry.moveCropRegion(latestCrop, dx, dy)
                        } else {
                            CropGeometry.resizeCropCorner(
                                crop = latestCrop,
                                corner = activeTarget,
                                deltaXNorm = dx,
                                deltaYNorm = dy,
                                videoAspectRatio = latestAspect
                            )
                        }
                        latestOnCropChange(updated)
                    }
                )
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val l = cropRegion.left * size.width
            val t = cropRegion.top * size.height
            val r = cropRegion.right * size.width
            val b = cropRegion.bottom * size.height
            val boxW = (r - l).coerceAtLeast(1f)
            val boxH = (b - t).coerceAtLeast(1f)

            val scrimColor = Color(0x9E06090F)
            // Top, Bottom, Left, Right scrim panels
            if (t > 0f) {
                drawRect(color = scrimColor, topLeft = Offset.Zero, size = Size(size.width, t))
            }
            if (b < size.height) {
                drawRect(color = scrimColor, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
            }
            if (l > 0f) {
                drawRect(color = scrimColor, topLeft = Offset(0f, t), size = Size(l, boxH))
            }
            if (r < size.width) {
                drawRect(color = scrimColor, topLeft = Offset(r, t), size = Size(size.width - r, boxH))
            }

            // Rule-of-thirds inner guides
            val gridColor = Color.White.copy(alpha = 0.28f)
            val thirdW = boxW / 3f
            val thirdH = boxH / 3f
            for (i in 1..2) {
                drawLine(
                    color = gridColor,
                    start = Offset(l + thirdW * i, t),
                    end = Offset(l + thirdW * i, b),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(l, t + thirdH * i),
                    end = Offset(r, t + thirdH * i),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Active border
            val borderColor = if (activeTarget != CropDragTarget.NONE) amber else primary
            drawRect(
                color = borderColor,
                topLeft = Offset(l, t),
                size = Size(boxW, boxH),
                style = Stroke(width = 2.dp.toPx())
            )

            // Corner L-brackets and drag knobs
            val bracketLen = minOf(18.dp.toPx(), boxW * 0.28f, boxH * 0.28f)
            val bracketStroke = 3.5.dp.toPx()
            val handleRadius = 5.5.dp.toPx()

            // Top-Left
            drawLine(borderColor, Offset(l, t), Offset(l + bracketLen, t), bracketStroke, StrokeCap.Round)
            drawLine(borderColor, Offset(l, t), Offset(l, t + bracketLen), bracketStroke, StrokeCap.Round)
            drawCircle(borderColor, handleRadius, Offset(l, t))

            // Top-Right
            drawLine(borderColor, Offset(r, t), Offset(r - bracketLen, t), bracketStroke, StrokeCap.Round)
            drawLine(borderColor, Offset(r, t), Offset(r, t + bracketLen), bracketStroke, StrokeCap.Round)
            drawCircle(borderColor, handleRadius, Offset(r, t))

            // Bottom-Left
            drawLine(borderColor, Offset(l, b), Offset(l + bracketLen, b), bracketStroke, StrokeCap.Round)
            drawLine(borderColor, Offset(l, b), Offset(l, b - bracketLen), bracketStroke, StrokeCap.Round)
            drawCircle(borderColor, handleRadius, Offset(l, b))

            // Bottom-Right
            drawLine(borderColor, Offset(r, b), Offset(r - bracketLen, b), bracketStroke, StrokeCap.Round)
            drawLine(borderColor, Offset(r, b), Offset(r, b - bracketLen), bracketStroke, StrokeCap.Round)
            drawCircle(borderColor, handleRadius, Offset(r, b))
        }

        // Dimension readout pill
        Surface(
            color = Color(0xCC0B0E14),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Text(
                text = CropGeometry.formatReadout(cropRegion, sourceWidth, sourceHeight),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun CropToolbarStrip(
    cropRegion: VideoCropRegion,
    videoAspectRatio: Float,
    enableInlineCrop: Boolean,
    enableFullscreenCrop: Boolean,
    onUpdateCropRegion: (VideoCropRegion) -> Unit,
    onResetCropRegion: () -> Unit,
    onOpenFullscreenCrop: () -> Unit,
    modifier: Modifier = Modifier,
    hasActiveOverlays: Boolean = false,
    onOpenOverlayTab: (PrecisionEditorTab) -> Unit = {}
) {
    val studioColors = LocalStudioColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Crop,
            contentDescription = null,
            tint = if (cropRegion.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !cropRegion.enabled,
                onClick = onResetCropRegion,
                label = { Text("Off") },
                modifier = Modifier.testTag("crop_chip_off"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )
            CropAspectMode.entries.forEach { mode ->
                val selected = cropRegion.enabled && cropRegion.aspectMode == mode
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = CropGeometry.fitAspectCropRegion(
                            mode = mode,
                            videoAspectRatio = videoAspectRatio,
                            current = cropRegion
                        )
                        onUpdateCropRegion(next)
                        if (!enableInlineCrop && enableFullscreenCrop) {
                            onOpenFullscreenCrop()
                        }
                    },
                    label = { Text(mode.label) },
                    modifier = Modifier.testTag("crop_chip_${mode.id}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = cropRegion.enabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            IconButton(
                onClick = onResetCropRegion,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("crop_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = "Reset crop region",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (enableFullscreenCrop) {
            FilterChip(
                selected = hasActiveOverlays,
                onClick = { onOpenOverlayTab(PrecisionEditorTab.LOGO) },
                label = { Text(if (hasActiveOverlays) "Logo/Text •" else "Logo/Text") },
                modifier = Modifier.testTag("crop_strip_overlays_button"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                )
            )
            IconButton(
                onClick = onOpenFullscreenCrop,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("crop_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = "Open full-screen crop editor",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun FullScreenCropDialog(
    mediaUri: Uri,
    title: String,
    initialPositionMs: Long,
    initialCrop: VideoCropRegion,
    sourceWidth: Int?,
    sourceHeight: Int?,
    onDismiss: () -> Unit,
    onApplyCrop: (VideoCropRegion) -> Unit = {},
    initialOverlay: VideoOverlayConfig = VideoOverlayConfig(),
    initialTab: PrecisionEditorTab = PrecisionEditorTab.CROP,
    onApplyCropAndOverlay: (VideoCropRegion, VideoOverlayConfig) -> Unit = { crop, _ -> onApplyCrop(crop) }
) {
    val context = LocalContext.current
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

    val dialogPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(initialPositionMs) }

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

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = dialogPlayer.currentPosition.coerceAtLeast(0L)
            if (dialogPlayer.duration > 0L) durationMs = dialogPlayer.duration
            delay(180L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, studioColors.hairlineBorderBright)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Precision Box Crop",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$title  •  ${CropGeometry.formatReadout(draftCrop, sourceWidth, sourceHeight)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close crop editor")
                    }
                }

                // Video Canvas + Crop Overlay
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 16f / 9f
                    val fitModifier = if (videoAspectRatio > containerRatio) {
                        Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
                    } else {
                        Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
                    }
                    Box(modifier = fitModifier) {
                        PlayerSurface(
                            player = dialogPlayer,
                            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (draftCrop.enabled || activeTab == PrecisionEditorTab.CROP) {
                            VideoCropOverlay(
                                cropRegion = if (draftCrop.enabled) draftCrop else VideoCropRegion(enabled = true),
                                videoAspectRatio = videoAspectRatio,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                onCropChange = { draftCrop = it },
                                interactive = activeTab == PrecisionEditorTab.CROP
                            )
                        }
                        VideoOverlayCanvasLayer(
                            cropRegion = draftCrop,
                            overlayConfig = draftOverlay,
                            activeTab = activeTab,
                            interactive = activeTab != PrecisionEditorTab.CROP,
                            onOverlayChange = { draftOverlay = it }
                        )
                    }
                }

                // Transport Scrubber
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (dialogPlayer.playbackState == Player.STATE_ENDED) {
                                dialogPlayer.seekTo(0L)
                            }
                            if (dialogPlayer.isPlaying) dialogPlayer.pause() else dialogPlayer.play()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary
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
                        onValueChange = {
                            positionMs = it.toLong()
                            dialogPlayer.seekTo(it.toLong())
                        },
                        enabled = durationMs > 0L,
                        valueRange = 0f..sliderMax,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = FormatUtils.formatDuration(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Mode Tabs (Crop Frame / Logo Box / Text Box)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrecisionEditorTab.entries.forEach { tab ->
                        val isSelected = activeTab == tab
                        val activeSuffix = when (tab) {
                            PrecisionEditorTab.CROP -> if (draftCrop.enabled && !draftCrop.isFullFrame) " •" else ""
                            PrecisionEditorTab.LOGO -> if (draftOverlay.logo.hasLogo) " •" else ""
                            PrecisionEditorTab.TEXT -> if (draftOverlay.textBox.hasText) " •" else ""
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                activeTab = tab
                                if (tab == PrecisionEditorTab.CROP && !draftCrop.enabled) {
                                    draftCrop = CropGeometry.fitAspectCropRegion(CropAspectMode.FREE, videoAspectRatio)
                                }
                            },
                            label = { Text("${tab.label}$activeSuffix") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("precision_tab_${tab.id}")
                        )
                    }
                }

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
                                onClick = { draftCrop = VideoCropRegion(enabled = false) },
                                label = { Text("Full Frame") }
                            )
                            CropAspectMode.entries.forEach { mode ->
                                val selected = draftCrop.enabled && draftCrop.aspectMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        draftCrop = CropGeometry.fitAspectCropRegion(
                                            mode = mode,
                                            videoAspectRatio = videoAspectRatio,
                                            current = draftCrop
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
                            onChange = { draftOverlay = draftOverlay.copy(logo = it) }
                        )
                    }

                    PrecisionEditorTab.TEXT -> {
                        TextOverlayControlPanel(
                            textBox = draftOverlay.textBox,
                            onChange = { draftOverlay = draftOverlay.copy(textBox = it) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            when (activeTab) {
                                PrecisionEditorTab.CROP ->
                                    draftCrop = CropGeometry.fitAspectCropRegion(CropAspectMode.FREE, videoAspectRatio)
                                PrecisionEditorTab.LOGO ->
                                    draftOverlay = draftOverlay.copy(logo = com.example.domain.model.LogoOverlayConfig())
                                PrecisionEditorTab.TEXT ->
                                    draftOverlay = draftOverlay.copy(textBox = com.example.domain.model.TextOverlayConfig())
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset")
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val finalCrop = if (draftCrop.enabled && !draftCrop.isFullFrame) {
                                draftCrop.copy(enabled = true)
                            } else {
                                VideoCropRegion(enabled = false)
                            }
                            onApplyCropAndOverlay(finalCrop, draftOverlay)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("apply_fullscreen_crop_button")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply")
                    }
                }
            }
        }
    }
}
