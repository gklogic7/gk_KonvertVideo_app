package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.LogoOverlayConfig
import com.example.domain.model.OverlayCornerPreset
import com.example.domain.model.TextOverlayConfig
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoOverlayConfig
import com.example.ffmpeg.OverlayBitmapRenderer
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class PrecisionEditorTab(val id: String, val label: String) {
    CROP("crop", "Crop Frame"),
    LOGO("logo", "Logo Box"),
    TEXT("text", "Text Box")
}

private enum class OverlayDragMode {
    NONE,
    MOVE,
    RESIZE
}

@Composable
fun VideoOverlayCanvasLayer(
    cropRegion: VideoCropRegion,
    overlayConfig: VideoOverlayConfig,
    activeTab: PrecisionEditorTab,
    interactive: Boolean,
    onOverlayChange: (VideoOverlayConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val latestOverlay by rememberUpdatedState(overlayConfig)
    val latestOnChange by rememberUpdatedState(onOverlayChange)

    val logoBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = overlayConfig.logo.uriString,
        key2 = overlayConfig.logo.isSvg
    ) {
        value = withContext(Dispatchers.IO) {
            OverlayBitmapRenderer.decodeLogoBitmap(
                context = context,
                uriString = overlayConfig.logo.uriString,
                isSvgHint = overlayConfig.logo.isSvg,
                targetMaxPx = 384
            )?.asImageBitmap()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val fullH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val cropLeftPx = if (cropRegion.enabled) cropRegion.left * fullW else 0f
        val cropTopPx = if (cropRegion.enabled) cropRegion.top * fullH else 0f
        val cropW = if (cropRegion.enabled) (cropRegion.widthFraction * fullW).coerceAtLeast(32f) else fullW
        val cropH = if (cropRegion.enabled) (cropRegion.heightFraction * fullH).coerceAtLeast(32f) else fullH

        val cropWDp = with(density) { cropW.toDp() }
        val cropHDp = with(density) { cropH.toDp() }

        Box(
            modifier = Modifier
                .offset { IntOffset(cropLeftPx.roundToInt(), cropTopPx.roundToInt()) }
                .size(cropWDp, cropHDp)
        ) {
            // 1. Logo Box
            val logo = overlayConfig.logo
            if (logo.hasLogo) {
                val logoW = (cropW * logo.widthFraction.coerceIn(0.06f, 0.65f)).coerceAtLeast(24f)
                val aspect = logoBitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1).toFloat() } ?: 1f
                val logoH = (logoW / aspect.coerceIn(0.2f, 5f)).coerceAtLeast(20f)
                val cx = (logo.xFraction.coerceIn(0.02f, 0.98f) * cropW)
                val cy = (logo.yFraction.coerceIn(0.02f, 0.98f) * cropH)
                val leftPx = (cx - logoW / 2f).coerceIn(0f, (cropW - logoW).coerceAtLeast(0f))
                val topPx = (cy - logoH / 2f).coerceIn(0f, (cropH - logoH).coerceAtLeast(0f))
                val isLogoEditing = interactive && activeTab == PrecisionEditorTab.LOGO
                var dragMode by remember { mutableStateOf(OverlayDragMode.NONE) }
                val handleHitPx = with(density) { 26.dp.toPx() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .size(with(density) { logoW.toDp() }, with(density) { logoH.toDp() })
                        .then(
                            if (isLogoEditing) {
                                Modifier
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .pointerInput(cropW, cropH) {
                                        detectDragGestures(
                                            onDragStart = { pos ->
                                                val distToBr = hypot(pos.x - size.width, pos.y - size.height)
                                                dragMode = if (distToBr <= handleHitPx) {
                                                    OverlayDragMode.RESIZE
                                                } else {
                                                    OverlayDragMode.MOVE
                                                }
                                            },
                                            onDragEnd = { dragMode = OverlayDragMode.NONE },
                                            onDragCancel = { dragMode = OverlayDragMode.NONE },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val current = latestOverlay.logo
                                                if (dragMode == OverlayDragMode.RESIZE) {
                                                    val nextW = (current.widthFraction + dragAmount.x / cropW)
                                                        .coerceIn(0.06f, 0.60f)
                                                    latestOnChange(latestOverlay.copy(logo = current.copy(widthFraction = nextW)))
                                                } else if (dragMode == OverlayDragMode.MOVE) {
                                                    val nextX = (current.xFraction + dragAmount.x / cropW).coerceIn(0.04f, 0.96f)
                                                    val nextY = (current.yFraction + dragAmount.y / cropH).coerceIn(0.04f, 0.96f)
                                                    latestOnChange(latestOverlay.copy(logo = current.copy(xFraction = nextX, yFraction = nextY)))
                                                }
                                            }
                                        )
                                    }
                            } else Modifier
                        )
                ) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap!!,
                            contentDescription = "Logo overlay preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(logo.opacity.coerceIn(0.05f, 1f))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x66131822), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LOGO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isLogoEditing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(14.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                                .border(1.5.dp, Color(0xFF0B0E14), CircleShape)
                        )
                    }
                }
            }

            // 2. Text Box
            val txt = overlayConfig.textBox
            if (txt.hasText) {
                val textPx = (cropH * txt.scaleFraction.coerceIn(0.022f, 0.16f)).coerceAtLeast(11f)
                val textSp = with(density) { (textPx * 0.85f).toSp() }
                val estWidth = (cropW * 0.88f).coerceAtLeast(80f)
                val isTextEditing = interactive && activeTab == PrecisionEditorTab.TEXT
                var textDragMode by remember { mutableStateOf(OverlayDragMode.NONE) }
                val handleHitPx = with(density) { 26.dp.toPx() }

                val cx = (txt.xFraction.coerceIn(0.04f, 0.96f) * cropW)
                val cy = (txt.yFraction.coerceIn(0.04f, 0.96f) * cropH)
                val approxW = (txt.text.length * textPx * 0.62f + textPx * 1.4f).coerceIn(48f, estWidth)
                val linesCount = txt.text.trim().lines().size.coerceAtLeast(1)
                val approxH = (linesCount * textPx * 1.3f + textPx * 0.7f).coerceAtLeast(24f)
                val leftPx = (cx - approxW / 2f).coerceIn(0f, (cropW - approxW).coerceAtLeast(0f))
                val topPx = (cy - approxH / 2f).coerceIn(0f, (cropH - approxH).coerceAtLeast(0f))

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .then(
                            if (isTextEditing) {
                                Modifier
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .pointerInput(cropW, cropH) {
                                        detectDragGestures(
                                            onDragStart = { pos ->
                                                val distToBr = hypot(pos.x - size.width, pos.y - size.height)
                                                textDragMode = if (distToBr <= handleHitPx) {
                                                    OverlayDragMode.RESIZE
                                                } else {
                                                    OverlayDragMode.MOVE
                                                }
                                            },
                                            onDragEnd = { textDragMode = OverlayDragMode.NONE },
                                            onDragCancel = { textDragMode = OverlayDragMode.NONE },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val current = latestOverlay.textBox
                                                if (textDragMode == OverlayDragMode.RESIZE) {
                                                    val nextScale = (current.scaleFraction + dragAmount.y / cropH)
                                                        .coerceIn(0.025f, 0.14f)
                                                    latestOnChange(latestOverlay.copy(textBox = current.copy(scaleFraction = nextScale)))
                                                } else if (textDragMode == OverlayDragMode.MOVE) {
                                                    val nextX = (current.xFraction + dragAmount.x / cropW).coerceIn(0.04f, 0.96f)
                                                    val nextY = (current.yFraction + dragAmount.y / cropH).coerceIn(0.04f, 0.96f)
                                                    latestOnChange(latestOverlay.copy(textBox = current.copy(xFraction = nextX, yFraction = nextY)))
                                                }
                                            }
                                        )
                                    }
                            } else Modifier
                        )
                ) {
                    val fgColor = Color(txt.colorHex.toInt()).copy(alpha = txt.opacity.coerceIn(0.05f, 1f))
                    Surface(
                        color = if (txt.hasBackdrop) {
                            Color(0xFF0B0E14).copy(alpha = (txt.opacity * 0.72f).coerceIn(0f, 0.9f))
                        } else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        border = if (txt.hasBackdrop) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = txt.opacity * 0.45f))
                        } else null
                    ) {
                        Text(
                            text = txt.text.trim(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = textSp,
                                lineHeight = (textSp.value * 1.2f).sp,
                                fontWeight = FontWeight.Bold,
                                shadow = if (!txt.hasBackdrop) {
                                    Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 4f)
                                } else null
                            ),
                            color = fgColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (isTextEditing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(14.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(1.5.dp, Color(0xFF0B0E14), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogoOverlayControlPanel(
    logo: LogoOverlayConfig,
    onChange: (LogoOverlayConfig) -> Unit
) {
    val context = LocalContext.current
    val studioColors = LocalStudioColors.current

    fun resolveName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "logo"
    }

    val rasterPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = resolveName(uri)
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            onChange(
                logo.copy(
                    uriString = uri.toString(),
                    displayName = name,
                    isSvg = mime.contains("svg", ignoreCase = true) || name.lowercase().endsWith(".svg")
                )
            )
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = resolveName(uri)
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            onChange(
                logo.copy(
                    uriString = uri.toString(),
                    displayName = name,
                    isSvg = mime.contains("svg", ignoreCase = true) || name.lowercase().endsWith(".svg")
                )
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { rasterPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("pick_logo_image_button")
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("JPEG / PNG / WebP")
            }
            OutlinedButton(
                onClick = {
                    documentPicker.launch(arrayOf("image/svg+xml", "image/png", "image/jpeg", "image/webp"))
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("pick_logo_svg_button")
            ) {
                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("SVG / File")
            }
            Spacer(Modifier.weight(1f))
            if (logo.hasLogo) {
                TextButton(
                    onClick = { onChange(LogoOverlayConfig()) },
                    modifier = Modifier.testTag("clear_logo_overlay_button")
                ) {
                    Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }

        if (logo.hasLogo) {
            CornerPresetStrip(
                selectedX = logo.xFraction,
                selectedY = logo.yFraction,
                onSelectCorner = { preset ->
                    onChange(logo.copy(xFraction = preset.xFraction, yFraction = preset.yFraction))
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Logo Width", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${(logo.widthFraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = logo.widthFraction,
                        onValueChange = { onChange(logo.copy(widthFraction = it.coerceIn(0.06f, 0.60f))) },
                        valueRange = 0.06f..0.60f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Opacity", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${(logo.opacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = logo.opacity,
                        onValueChange = { onChange(logo.copy(opacity = it.coerceIn(0.15f, 1f))) },
                        valueRange = 0.15f..1f
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, studioColors.hairlineBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.OpenWith, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Pick a JPEG, PNG, WebP, or SVG logo, then drag inside the crop frame or snap to any corner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TextOverlayControlPanel(
    textBox: TextOverlayConfig,
    onChange: (TextOverlayConfig) -> Unit
) {
    val swatchColors = listOf(
        0xFFFFFFFFL to "White",
        0xFF00E5FFL to "Cyan",
        0xFFFFB300L to "Amber",
        0xFF00E676L to "Emerald",
        0xFFFF5252L to "Coral"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textBox.text,
                onValueChange = { onChange(textBox.copy(text = it.take(80))) },
                placeholder = { Text("Watermark or caption text…") },
                singleLine = false,
                maxLines = 2,
                modifier = Modifier
                    .weight(1f)
                    .testTag("overlay_text_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = LocalStudioColors.current.hairlineBorder
                )
            )
            if (textBox.hasText) {
                TextButton(
                    onClick = { onChange(textBox.copy(text = "")) },
                    modifier = Modifier.testTag("clear_text_overlay_button")
                ) {
                    Text("Clear")
                }
            }
        }

        CornerPresetStrip(
            selectedX = textBox.xFraction,
            selectedY = textBox.yFraction,
            onSelectCorner = { preset ->
                onChange(textBox.copy(xFraction = preset.xFraction, yFraction = preset.yFraction))
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            swatchColors.forEach { (hex, label) ->
                val selected = textBox.colorHex == hex
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(hex.toInt()))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable { onChange(textBox.copy(colorHex = hex)) }
                        .testTag("text_color_${label.lowercase()}")
                )
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = textBox.hasBackdrop,
                onClick = { onChange(textBox.copy(hasBackdrop = !textBox.hasBackdrop)) },
                label = { Text("Badge Backdrop") },
                modifier = Modifier.testTag("text_backdrop_toggle")
            )
        }

        if (textBox.hasText) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Text Size", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${(textBox.scaleFraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = textBox.scaleFraction,
                        onValueChange = { onChange(textBox.copy(scaleFraction = it.coerceIn(0.025f, 0.14f))) },
                        valueRange = 0.025f..0.14f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Opacity", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${(textBox.opacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = textBox.opacity,
                        onValueChange = { onChange(textBox.copy(opacity = it.coerceIn(0.15f, 1f))) },
                        valueRange = 0.15f..1f
                    )
                }
            }
        }
    }
}

@Composable
private fun CornerPresetStrip(
    selectedX: Float,
    selectedY: Float,
    onSelectCorner: (OverlayCornerPreset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OverlayCornerPreset.entries.forEach { preset ->
            val isNear = kotlin.math.abs(selectedX - preset.xFraction) < 0.06f &&
                kotlin.math.abs(selectedY - preset.yFraction) < 0.06f
            FilterChip(
                selected = isNear,
                onClick = { onSelectCorner(preset) },
                label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.testTag("overlay_corner_${preset.id}")
            )
        }
    }
}
