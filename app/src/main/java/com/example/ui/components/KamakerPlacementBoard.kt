package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.KamakerBackdrop
import com.example.domain.model.KamakerFont
import com.example.domain.model.KamakerFrameShape
import com.example.domain.model.KamakerHorizontalAlign
import com.example.domain.model.KamakerMode
import com.example.domain.model.KamakerShapeRule
import com.example.domain.model.KamakerSpec
import com.example.domain.model.KamakerVerticalAlign
import com.example.ffmpeg.OverlayBitmapRenderer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

val KamakerAccentOrange = Color(0xFFFF6D3B)
val KamakerAccentOrangeContainer = Color(0xFF472013)

@Composable
fun rememberDecodedKamakerLogo(spec: KamakerSpec): Bitmap? {
    val context = LocalContext.current
    var decoded by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(spec.mode, spec.logoUriString, spec.logoIsSvg) {
        decoded = if (spec.mode == KamakerMode.LOGO && !spec.logoUriString.isNullOrBlank()) {
            OverlayBitmapRenderer.decodeLogoBitmap(
                context = context,
                uriString = spec.logoUriString,
                isSvgHint = spec.logoIsSvg,
                targetMaxPx = 420
            )
        } else {
            null
        }
    }
    return decoded
}

/**
 * Self-contained live watermark overlay layer used in Kvplayer viewport and batch thumbnails.
 */
@Composable
fun KamakerLiveOverlayLayer(
    spec: KamakerSpec,
    modifier: Modifier = Modifier,
    forceShape: KamakerFrameShape? = null,
    showSelectionHandles: Boolean = false
) {
    val decodedLogo = rememberDecodedKamakerLogo(spec)
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val ratio = if (maxHeight > 0.dp) maxWidth / maxHeight else 16f / 9f
        KamakerLiveWatermarkCanvas(
            spec = spec,
            frameAspectRatio = ratio,
            decodedLogo = decodedLogo,
            forceShape = forceShape,
            showSelectionHandles = showSelectionHandles,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Pixel-accurate live Canvas watermark preview shared by Kamaker placement boards
 * and the Kvplayer live overlay viewport.
 */
@Composable
fun KamakerLiveWatermarkCanvas(
    spec: KamakerSpec,
    frameAspectRatio: Float,
    decodedLogo: Bitmap?,
    modifier: Modifier = Modifier,
    forceShape: KamakerFrameShape? = null,
    showSelectionHandles: Boolean = false
) {
    Canvas(modifier = modifier.clipToBounds()) {
        val w = size.width
        val h = size.height
        if (w <= 2f || h <= 2f) return@Canvas

        val resolvedRule = if (forceShape != null) {
            val base = spec.ruleFor(forceShape)
            val scaledPct = (base.scalePercent * (spec.globalAdjust.scalePercent / 100f)).coerceIn(4f, 95f)
            val margin = (spec.edgeMarginPercent.coerceIn(0f, 20f) / 100f)
            val rawX = base.xFraction + (spec.globalAdjust.offsetXPercent / 100f)
            val rawY = base.yFraction + (spec.globalAdjust.offsetYPercent / 100f)
            val minCoord = (0.04f + margin * 0.5f).coerceIn(0.03f, 0.35f)
            val maxCoord = (1f - minCoord).coerceIn(0.65f, 0.97f)
            base.copy(
                xFraction = rawX.coerceIn(minCoord, maxCoord),
                yFraction = rawY.coerceIn(minCoord, maxCoord),
                scalePercent = scaledPct,
                rotateDegrees = KamakerSpec.wrapDegrees(base.rotateDegrees + spec.globalAdjust.rotateDegrees)
            )
        } else {
            spec.resolveEffectivePlacement(frameAspectRatio)
        }

        val cx = resolvedRule.xFraction.coerceIn(0.02f, 0.98f) * w
        val cy = resolvedRule.yFraction.coerceIn(0.02f, 0.98f) * h
        val targetW = (w * (resolvedRule.scalePercent / 100f)).coerceAtLeast(14f)
        var boxHalfW = targetW / 2f
        var boxHalfH = (targetW * 0.32f).coerceAtLeast(14f)

        if (spec.hasActiveContent) {
            val alphaInt = ((spec.opacityPercent.coerceIn(2f, 100f) / 100f) * 255f).roundToInt().coerceIn(1, 255)
            when (spec.mode) {
                KamakerMode.LOGO -> {
                    val bmp = decodedLogo
                    if (bmp != null && !bmp.isRecycled) {
                        val logoAspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                            .coerceIn(0.15f, 6f)
                        val targetH = targetW / logoAspect
                        boxHalfW = targetW / 2f
                        boxHalfH = targetH / 2f
                        drawIntoCanvas { composeCanvas ->
                            val nc = composeCanvas.nativeCanvas
                            nc.save()
                            nc.translate(cx, cy)
                            if (resolvedRule.rotateDegrees != 0f) {
                                nc.rotate(resolvedRule.rotateDegrees)
                            }
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                                alpha = alphaInt
                                if (spec.shadow) {
                                    setShadowLayer(
                                        (targetW * 0.04f).coerceIn(2f, 12f),
                                        0f,
                                        (targetW * 0.025f).coerceIn(1f, 8f),
                                        AndroidColor.argb((alphaInt * 0.65f).roundToInt(), 0, 0, 0)
                                    )
                                }
                            }
                            nc.drawBitmap(
                                bmp,
                                null,
                                RectF(-boxHalfW, -boxHalfH, boxHalfW, boxHalfH),
                                paint
                            )
                            nc.restore()
                        }
                    }
                }

                KamakerMode.TEXT -> {
                    val lines = spec.text.trim().lines().filter { it.isNotEmpty() }
                        .ifEmpty { listOf(spec.text.trim()) }
                    val style = when {
                        spec.bold && spec.italic -> Typeface.BOLD_ITALIC
                        spec.bold -> Typeface.BOLD
                        spec.italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    val baseFamily = when (spec.font) {
                        KamakerFont.SANS -> Typeface.SANS_SERIF
                        KamakerFont.SERIF -> Typeface.SERIF
                        KamakerFont.MONO -> Typeface.MONOSPACE
                        KamakerFont.DISPLAY -> Typeface.create("sans-serif-medium", style)
                    }
                    val tf = Typeface.create(baseFamily, style)
                    val probePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = tf
                        textSize = 100f
                    }
                    val measuredMaxW = lines.maxOfOrNull { probePaint.measureText(it) } ?: 100f
                    val normalizedRefW = max(measuredMaxW, 180f)
                    val fontSizePx = ((targetW / normalizedRefW) * 100f).coerceIn(8f, h * 0.42f)

                    val fillAlphaColor = applyAlphaInt(spec.textColorHex.toInt(), alphaInt)
                    val strokeAlphaColor = applyAlphaInt(spec.strokeColorHex.toInt(), alphaInt)

                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = tf
                        textSize = fontSizePx
                        textAlign = Paint.Align.CENTER
                        this.style = Paint.Style.FILL
                        color = fillAlphaColor
                        if (spec.shadow) {
                            setShadowLayer(
                                (fontSizePx * 0.14f).coerceIn(1.5f, 12f),
                                0f,
                                (fontSizePx * 0.08f).coerceIn(1f, 8f),
                                AndroidColor.argb((alphaInt * 0.72f).roundToInt(), 0, 0, 0)
                            )
                        }
                    }
                    val fm = fillPaint.fontMetrics
                    val lineHeight = (fm.descent - fm.ascent) * 1.15f
                    val totalTextH = lineHeight * lines.size
                    val actualTextW = lines.maxOfOrNull { fillPaint.measureText(it) } ?: targetW
                    boxHalfW = max(actualTextW / 2f + 6f, 18f)
                    boxHalfH = max(totalTextH / 2f + 4f, 12f)

                    val strokeWidthPx = if (spec.strokeEnabled && spec.strokeWidthPercent > 0f) {
                        (fontSizePx * (spec.strokeWidthPercent / 100f) * 1.8f).coerceIn(0.6f, fontSizePx * 0.35f)
                    } else 0f

                    val strokePaint = if (strokeWidthPx > 0f) {
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = tf
                            textSize = fontSizePx
                            textAlign = Paint.Align.CENTER
                            this.style = Paint.Style.STROKE
                            strokeJoin = Paint.Join.ROUND
                            strokeCap = Paint.Cap.ROUND
                            strokeWidth = strokeWidthPx
                            color = strokeAlphaColor
                        }
                    } else null

                    drawIntoCanvas { composeCanvas ->
                        val nc: Canvas = composeCanvas.nativeCanvas
                        nc.save()
                        nc.translate(cx, cy)
                        if (resolvedRule.rotateDegrees != 0f) {
                            nc.rotate(resolvedRule.rotateDegrees)
                        }
                        var baselineY = -(totalTextH / 2f) - fm.ascent
                        lines.forEach { line ->
                            if (strokePaint != null) {
                                nc.drawText(line, 0f, baselineY, strokePaint)
                            }
                            nc.drawText(line, 0f, baselineY, fillPaint)
                            baselineY += lineHeight
                        }
                        nc.restore()
                    }
                }
            }
        }

        if (showSelectionHandles) {
            val rad = resolvedRule.rotateDegrees * (PI.toFloat() / 180f)
            val cosR = cos(rad)
            val sinR = sin(rad)
            fun rotPoint(lx: Float, ly: Float): Offset = Offset(
                x = cx + lx * cosR - ly * sinR,
                y = cy + lx * sinR + ly * cosR
            )

            val tl = rotPoint(-boxHalfW, -boxHalfH)
            val tr = rotPoint(boxHalfW, -boxHalfH)
            val br = rotPoint(boxHalfW, boxHalfH)
            val bl = rotPoint(-boxHalfW, boxHalfH)
            val topMid = rotPoint(0f, -boxHalfH)
            val stemLen = 22f.coerceAtMost(h * 0.14f)
            val rotKnob = rotPoint(0f, -boxHalfH - stemLen)

            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            val edges = listOf(tl to tr, tr to br, br to bl, bl to tl)
            edges.forEach { (p1, p2) ->
                drawLine(
                    color = KamakerAccentOrange,
                    start = p1,
                    end = p2,
                    strokeWidth = 2.4f,
                    pathEffect = dash
                )
            }
            // Rotation stem + circular handle
            drawLine(
                color = KamakerAccentOrange,
                start = topMid,
                end = rotKnob,
                strokeWidth = 2.4f
            )
            drawCircle(
                color = Color.White,
                radius = 7f,
                center = rotKnob
            )
            drawCircle(
                color = KamakerAccentOrange,
                radius = 7f,
                center = rotKnob,
                style = Stroke(width = 3f)
            )

            // 4 corner square scale handles
            listOf(tl, tr, br, bl).forEach { corner ->
                val hs = 9f
                drawRect(
                    color = Color.White,
                    topLeft = Offset(corner.x - hs / 2f, corner.y - hs / 2f),
                    size = Size(hs, hs)
                )
                drawRect(
                    color = KamakerAccentOrange,
                    topLeft = Offset(corner.x - hs / 2f, corner.y - hs / 2f),
                    size = Size(hs, hs),
                    style = Stroke(width = 2.5f)
                )
            }
        }
    }
}

private enum class BoardDragMode {
    MOVE,
    SCALE,
    ROTATE
}

@Composable
fun KamakerShapeBoardCard(
    shape: KamakerFrameShape,
    spec: KamakerSpec,
    decodedLogo: Bitmap?,
    backdropBitmap: Bitmap?,
    onUpdateRule: ((KamakerShapeRule) -> KamakerShapeRule) -> Unit,
    onSnapAlign: (KamakerVerticalAlign?, KamakerHorizontalAlign?) -> Unit,
    onResetShape: () -> Unit,
    onCopyToAllShapes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rule = spec.ruleFor(shape)
    val latestRule by rememberUpdatedState(rule)
    val latestSpec by rememberUpdatedState(spec)
    val latestOnUpdate by rememberUpdatedState(onUpdateRule)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.testTag("kamaker_board_${shape.ratioLabel}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: "1:1 square"
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = shape.ratioLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = shape.shapeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Stage container with fixed compact height + centered aspect-ratio frame
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF07090E))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 1.5f
                val frameModifier = if (containerRatio >= shape.aspectRatio) {
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(shape.aspectRatio)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(shape.aspectRatio)
                }

                var activeDragMode by remember { mutableStateOf(BoardDragMode.MOVE) }
                var startDistance by remember { mutableStateOf(40f) }
                var startScale by remember { mutableStateOf(rule.scalePercent) }

                Box(
                    modifier = frameModifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A202C))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .pointerInput(shape) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val cur = latestRule
                                    val cx = cur.xFraction * w
                                    val cy = cur.yFraction * h
                                    val halfW = (w * (cur.scalePercent / 100f)) / 2f
                                    val halfH = (halfW * 0.42f).coerceAtLeast(14f)

                                    val rad = cur.rotateDegrees * (PI.toFloat() / 180f)
                                    val rotX = cx + 0f * cos(rad) - (-halfH - 22f) * sin(rad)
                                    val rotY = cy + 0f * sin(rad) + (-halfH - 22f) * cos(rad)

                                    val distToRot = hypot(offset.x - rotX, offset.y - rotY)
                                    val distToCenter = hypot(offset.x - cx, offset.y - cy)

                                    activeDragMode = when {
                                        distToRot < 32f -> BoardDragMode.ROTATE
                                        distToCenter > max(halfW * 0.72f, 22f) && distToCenter < max(halfW * 1.9f, 64f) -> {
                                            startDistance = distToCenter.coerceAtLeast(16f)
                                            startScale = cur.scalePercent
                                            BoardDragMode.SCALE
                                        }
                                        else -> BoardDragMode.MOVE
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val cur = latestRule
                                    when (activeDragMode) {
                                        BoardDragMode.MOVE -> {
                                            val margin = latestSpec.edgeMarginPercent.coerceIn(0f, 20f) / 100f
                                            val minC = (0.06f + margin * 0.3f).coerceIn(0.05f, 0.3f)
                                            val maxC = (1f - minC).coerceIn(0.7f, 0.95f)
                                            val nx = (cur.xFraction + dragAmount.x / w).coerceIn(minC, maxC)
                                            val ny = (cur.yFraction + dragAmount.y / h).coerceIn(minC, maxC)
                                            latestOnUpdate { r ->
                                                r.copy(
                                                    xFraction = nx,
                                                    yFraction = ny,
                                                    verticalAlign = KamakerSpec.detectVerticalAlign(ny),
                                                    horizontalAlign = KamakerSpec.detectHorizontalAlign(nx)
                                                )
                                            }
                                        }
                                        BoardDragMode.SCALE -> {
                                            val cx = cur.xFraction * w
                                            val cy = cur.yFraction * h
                                            val newDist = hypot(change.position.x - cx, change.position.y - cy).coerceAtLeast(12f)
                                            val ratio = newDist / startDistance.coerceAtLeast(12f)
                                            val nextScale = (startScale * ratio).coerceIn(6f, 80f)
                                            latestOnUpdate { r -> r.copy(scalePercent = nextScale) }
                                        }
                                        BoardDragMode.ROTATE -> {
                                            val cx = cur.xFraction * w
                                            val cy = cur.yFraction * h
                                            val angleRad = atan2(change.position.y - cy, change.position.x - cx)
                                            val deg = (angleRad * 180f / PI.toFloat()) + 90f
                                            latestOnUpdate { r ->
                                                r.copy(rotateDegrees = KamakerSpec.wrapDegrees(deg))
                                            }
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // Board Backdrop layer
                    when (spec.boardBackdrop) {
                        KamakerBackdrop.CURRENT_VIDEO_FRAME -> {
                            if (backdropBitmap != null && !backdropBitmap.isRecycled) {
                                Image(
                                    bitmap = backdropBitmap.asImageBitmap(),
                                    contentDescription = "Current video frame backdrop",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        KamakerBackdrop.DARK_STUDIO -> {
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A202C)))
                        }
                        KamakerBackdrop.CHECKERBOARD -> {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cell = 14.dp.toPx()
                                val cols = (size.width / cell).toInt() + 1
                                val rows = (size.height / cell).toInt() + 1
                                for (r in 0..rows) {
                                    for (c in 0..cols) {
                                        val isLight = (r + c) % 2 == 0
                                        drawRect(
                                            color = if (isLight) Color(0xFF283040) else Color(0xFF181D27),
                                            topLeft = Offset(c * cell, r * cell),
                                            size = Size(cell, cell)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Live Watermark + Handles
                    KamakerLiveWatermarkCanvas(
                        spec = spec,
                        frameAspectRatio = shape.aspectRatio,
                        decodedLogo = decodedLogo,
                        forceShape = shape,
                        showSelectionHandles = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Vertical align row
            Text(
                text = "Vertical",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    KamakerVerticalAlign.TOP,
                    KamakerVerticalAlign.CENTRE,
                    KamakerVerticalAlign.BOTTOM
                ).forEach { v ->
                    KamakerAlignPillButton(
                        label = v.label,
                        selected = rule.verticalAlign == v,
                        onClick = { onSnapAlign(v, null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Horizontal align row
            Text(
                text = "Horizontal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    KamakerHorizontalAlign.LEFT,
                    KamakerHorizontalAlign.MIDDLE,
                    KamakerHorizontalAlign.RIGHT
                ).forEach { h ->
                    KamakerAlignPillButton(
                        label = h.label,
                        selected = rule.horizontalAlign == h,
                        onClick = { onSnapAlign(null, h) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Scale slider
            KamakerLabeledSliderRow(
                label = "Scale",
                valueText = "${rule.scalePercent.roundToInt()}%",
                value = rule.scalePercent,
                valueRange = 6f..80f,
                onValueChange = { next ->
                    onUpdateRule { it.copy(scalePercent = next) }
                }
            )

            // Rotate slider
            KamakerLabeledSliderRow(
                label = "Rotate",
                valueText = "${rule.rotateDegrees.roundToInt()}°",
                value = rule.rotateDegrees,
                valueRange = -180f..180f,
                onValueChange = { next ->
                    onUpdateRule { it.copy(rotateDegrees = next) }
                }
            )

            // Action buttons: Reset this shape + Copy to all shapes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResetShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Reset shape",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = onCopyToAllShapes,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Copy to all",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun KamakerAlignPillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) KamakerAccentOrange else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) KamakerAccentOrange else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.height(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun KamakerLabeledSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = KamakerAccentOrange,
                activeTrackColor = KamakerAccentOrange,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(44.dp),
            maxLines = 1
        )
    }
}

private fun applyAlphaInt(argb: Int, alphaInt: Int): Int {
    val a = (AndroidColor.alpha(argb) * alphaInt / 255).coerceIn(0, 255)
    return AndroidColor.argb(a, AndroidColor.red(argb), AndroidColor.green(argb), AndroidColor.blue(argb))
}
