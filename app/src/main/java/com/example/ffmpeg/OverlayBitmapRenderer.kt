package com.example.ffmpeg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.Xml
import androidx.core.graphics.PathParser
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoOverlayConfig
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object OverlayBitmapRenderer {

    private data class SvgStyleState(
        val fillColor: Int? = Color.BLACK,
        val strokeColor: Int? = null,
        val strokeWidth: Float = 1f,
        val fillOpacity: Float = 1f,
        val strokeOpacity: Float = 1f,
        val groupOpacity: Float = 1f,
        val hasExplicitMatrix: Boolean = false
    )

    fun resolveOverlayCanvasSize(
        sourceWidth: Int?,
        sourceHeight: Int?,
        crop: VideoCropRegion,
        targetWidth: Int?
    ): Pair<Int, Int> {
        val rawW = (sourceWidth?.takeIf { it > 0 } ?: 1280).toFloat()
        val rawH = (sourceHeight?.takeIf { it > 0 } ?: 720).toFloat()
        val croppedW = if (crop.isFullFrame) rawW else rawW * crop.widthFraction
        val croppedH = if (crop.isFullFrame) rawH else rawH * crop.heightFraction
        val aspect = (croppedW / croppedH.coerceAtLeast(1f)).coerceIn(0.25f, 4f)

        val desiredW = (targetWidth?.coerceIn(480, 1920) ?: 1080)
        val outW = ((desiredW + 1) / 2) * 2
        val outH = (((outW / aspect).roundToInt().coerceIn(320, 1920) + 1) / 2) * 2
        return outW to outH
    }

    fun decodeLogoBitmap(
        context: Context,
        uriString: String?,
        isSvgHint: Boolean = false,
        targetMaxPx: Int = 512
    ): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val isSvg = isSvgHint ||
            mime.contains("svg", ignoreCase = true) ||
            uriString.lowercase().endsWith(".svg")

        if (isSvg) {
            return runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    parseSvgToBitmap(stream, targetMaxPx)
                }
            }.getOrNull()
        }

        // First sniff bytes in case OpenDocument returned a generic MIME for an SVG file
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null

        val prefix = bytes.copyOfRange(0, min(bytes.size, 256)).decodeToString().trimStart()
        if (prefix.startsWith("<svg", ignoreCase = true) ||
            (prefix.startsWith("<?xml", ignoreCase = true) && prefix.contains("<svg", ignoreCase = true))
        ) {
            return parseSvgToBitmap(ByteArrayInputStream(bytes), targetMaxPx)
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var sampleSize = 1
        while (max(srcW / sampleSize, srcH / sampleSize) > targetMaxPx * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        val scale = min(
            targetMaxPx.toFloat() / decoded.width.coerceAtLeast(1),
            targetMaxPx.toFloat() / decoded.height.coerceAtLeast(1)
        )
        return if (scale < 0.99f) {
            val scaledW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
            val scaledH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
            val resized = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
            if (resized != decoded) decoded.recycle()
            resized
        } else {
            decoded
        }
    }

    fun parseSvgToBitmap(inputStream: InputStream, targetMaxPx: Int = 512): Bitmap? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var bitmap: Bitmap? = null
        var canvas: Canvas? = null

        val styleStack = ArrayDeque<SvgStyleState>()
        styleStack.addLast(SvgStyleState())
        val matrixStack = ArrayDeque<Matrix>()
        matrixStack.addLast(Matrix())

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    val attrs = readAttributeMap(parser)
                    if (tag == "svg") {
                        val (vbX, vbY, vbW, vbH) = parseSvgViewport(attrs)
                        val aspect = (vbW / vbH.coerceAtLeast(1e-3f)).coerceIn(0.1f, 10f)
                        val outW: Int
                        val outH: Int
                        if (aspect >= 1f) {
                            outW = targetMaxPx.coerceAtLeast(32)
                            outH = (outW / aspect).roundToInt().coerceAtLeast(32)
                        } else {
                            outH = targetMaxPx.coerceAtLeast(32)
                            outW = (outH * aspect).roundToInt().coerceAtLeast(32)
                        }
                        bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        canvas = Canvas(bitmap!!)
                        val scaleX = outW / vbW.coerceAtLeast(1e-3f)
                        val scaleY = outH / vbH.coerceAtLeast(1e-3f)
                        val rootMatrix = Matrix().apply {
                            setTranslate(-vbX, -vbY)
                            postScale(scaleX, scaleY)
                        }
                        matrixStack.clear()
                        matrixStack.addLast(rootMatrix)
                        styleStack.clear()
                        styleStack.addLast(mergeSvgStyle(SvgStyleState(), attrs))
                    } else if (canvas != null) {
                        val parentStyle = styleStack.last()
                        val mergedStyle = mergeSvgStyle(parentStyle, attrs)
                        styleStack.addLast(mergedStyle)

                        val transformAttr = attrs["transform"]
                        if (!transformAttr.isNullOrBlank()) {
                            canvas!!.save()
                            val m = parseSvgTransform(transformAttr)
                            canvas!!.concat(m)
                            styleStack.removeLast()
                            styleStack.addLast(mergedStyle.copy(hasExplicitMatrix = true))
                        }

                        val c = canvas!!
                        val path: Path? = when (tag) {
                            "path" -> {
                                val d = attrs["d"]
                                if (!d.isNullOrBlank()) {
                                    runCatching { PathParser.createPathFromPathData(d) }.getOrNull()
                                } else null
                            }
                            "rect" -> {
                                val x = parseLength(attrs["x"], 0f)
                                val y = parseLength(attrs["y"], 0f)
                                val w = parseLength(attrs["width"], 0f)
                                val h = parseLength(attrs["height"], 0f)
                                val rx = parseLength(attrs["rx"] ?: attrs["ry"], 0f)
                                val ry = parseLength(attrs["ry"] ?: attrs["rx"], rx)
                                if (w > 0f && h > 0f) {
                                    Path().apply {
                                        if (rx > 0f || ry > 0f) {
                                            addRoundRect(RectF(x, y, x + w, y + h), rx, ry, Path.Direction.CW)
                                        } else {
                                            addRect(x, y, x + w, y + h, Path.Direction.CW)
                                        }
                                    }
                                } else null
                            }
                            "circle" -> {
                                val cx = parseLength(attrs["cx"], 0f)
                                val cy = parseLength(attrs["cy"], 0f)
                                val r = parseLength(attrs["r"], 0f)
                                if (r > 0f) Path().apply { addCircle(cx, cy, r, Path.Direction.CW) } else null
                            }
                            "ellipse" -> {
                                val cx = parseLength(attrs["cx"], 0f)
                                val cy = parseLength(attrs["cy"], 0f)
                                val rx = parseLength(attrs["rx"], 0f)
                                val ry = parseLength(attrs["ry"], 0f)
                                if (rx > 0f && ry > 0f) {
                                    Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }
                                } else null
                            }
                            "line" -> {
                                val x1 = parseLength(attrs["x1"], 0f)
                                val y1 = parseLength(attrs["y1"], 0f)
                                val x2 = parseLength(attrs["x2"], 0f)
                                val y2 = parseLength(attrs["y2"], 0f)
                                Path().apply {
                                    moveTo(x1, y1)
                                    lineTo(x2, y2)
                                }
                            }
                            "polygon" -> parsePointsPath(attrs["points"], close = true)
                            "polyline" -> parsePointsPath(attrs["points"], close = false)
                            else -> null
                        }

                        if (path != null) {
                            mergedStyle.fillColor?.let { fc ->
                                val alpha = (mergedStyle.fillOpacity * mergedStyle.groupOpacity * 255f)
                                    .roundToInt().coerceIn(0, 255)
                                if (alpha > 0) {
                                    fillPaint.color = fc
                                    fillPaint.alpha = (Color.alpha(fc) * alpha / 255).coerceIn(0, 255)
                                    c.drawPath(path, fillPaint)
                                }
                            }
                            mergedStyle.strokeColor?.let { sc ->
                                val alpha = (mergedStyle.strokeOpacity * mergedStyle.groupOpacity * 255f)
                                    .roundToInt().coerceIn(0, 255)
                                if (alpha > 0 && mergedStyle.strokeWidth > 0f) {
                                    strokePaint.color = sc
                                    strokePaint.alpha = (Color.alpha(sc) * alpha / 255).coerceIn(0, 255)
                                    strokePaint.strokeWidth = mergedStyle.strokeWidth
                                    c.drawPath(path, strokePaint)
                                }
                            }
                        }

                        if (tag == "g") {
                            // Keep stacked state for child elements
                        } else if (!parser.isEmptyElementTag) {
                            // Will pop on END_TAG
                        }
                    }

                    if (tag == "svg" && canvas != null) {
                        canvas!!.concat(matrixStack.first())
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    if (tag != "svg" && styleStack.size > 1) {
                        val popped = styleStack.removeLast()
                        if (popped.hasExplicitMatrix && canvas != null) {
                            canvas!!.restore()
                        }
                    }
                }
            }
            event = parser.next()
        }

        return bitmap
    }

    fun renderCompositeOverlayPng(
        context: Context,
        overlayConfig: VideoOverlayConfig,
        canvasWidth: Int,
        canvasHeight: Int,
        destFile: File
    ): File? {
        if (!overlayConfig.isActive) return null
        val safeW = canvasWidth.coerceIn(64, 2048)
        val safeH = canvasHeight.coerceIn(64, 2048)
        val outBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)

        // 1. Logo Box
        if (overlayConfig.logo.hasLogo) {
            val logoCfg = overlayConfig.logo
            val logoBmp = decodeLogoBitmap(
                context = context,
                uriString = logoCfg.uriString,
                isSvgHint = logoCfg.isSvg,
                targetMaxPx = (safeW * logoCfg.widthFraction.coerceIn(0.06f, 0.7f)).roundToInt().coerceIn(64, 1024)
            )
            if (logoBmp != null) {
                val targetW = (safeW * logoCfg.widthFraction.coerceIn(0.06f, 0.65f)).coerceAtLeast(16f)
                val logoAspect = logoBmp.width.toFloat() / logoBmp.height.coerceAtLeast(1).toFloat()
                val targetH = targetW / logoAspect.coerceIn(0.15f, 6f)
                val cx = (logoCfg.xFraction.coerceIn(0.02f, 0.98f) * safeW)
                val cy = (logoCfg.yFraction.coerceIn(0.02f, 0.98f) * safeH)
                val left = (cx - targetW / 2f).coerceIn(0f, (safeW - targetW).coerceAtLeast(0f))
                val top = (cy - targetH / 2f).coerceIn(0f, (safeH - targetH).coerceAtLeast(0f))
                val dstRect = RectF(left, top, left + targetW, top + targetH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (logoCfg.opacity.coerceIn(0.05f, 1f) * 255f).roundToInt()
                }
                canvas.drawBitmap(logoBmp, null, dstRect, paint)
                logoBmp.recycle()
            }
        }

        // 2. Text Box
        if (overlayConfig.textBox.hasText) {
            val txtCfg = overlayConfig.textBox
            val lines = txtCfg.text.trim().lines().filter { it.isNotEmpty() }.ifEmpty { listOf(txtCfg.text.trim()) }
            val textSizePx = (safeH * txtCfg.scaleFraction.coerceIn(0.022f, 0.16f)).coerceAtLeast(14f)
            val alphaInt = (txtCfg.opacity.coerceIn(0.05f, 1f) * 255f).roundToInt()
            val argbColor = txtCfg.colorHex.toInt()
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(
                    (Color.alpha(argbColor) * alphaInt / 255).coerceIn(0, 255),
                    Color.red(argbColor),
                    Color.green(argbColor),
                    Color.blue(argbColor)
                )
                textSize = textSizePx
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                if (!txtCfg.hasBackdrop) {
                    setShadowLayer(textSizePx * 0.14f, 0f, textSizePx * 0.06f, Color.argb((alphaInt * 0.85f).roundToInt(), 0, 0, 0))
                }
            }

            val lineHeight = textSizePx * 1.24f
            val maxLineWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
            val padH = textSizePx * 0.55f
            val padV = textSizePx * 0.32f
            val boxW = (maxLineWidth + padH * 2f).coerceAtMost(safeW * 0.94f)
            val boxH = (lines.size * lineHeight + padV * 2f).coerceAtMost(safeH * 0.9f)

            val cx = (txtCfg.xFraction.coerceIn(0.02f, 0.98f) * safeW)
                .coerceIn(boxW / 2f + 4f, (safeW - boxW / 2f - 4f).coerceAtLeast(boxW / 2f + 4f))
            val cy = (txtCfg.yFraction.coerceIn(0.02f, 0.98f) * safeH)
                .coerceIn(boxH / 2f + 4f, (safeH - boxH / 2f - 4f).coerceAtLeast(boxH / 2f + 4f))

            if (txtCfg.hasBackdrop) {
                val bgRect = RectF(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)
                val radius = textSizePx * 0.35f
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((alphaInt * 0.72f).roundToInt(), 11, 14, 20)
                    style = Paint.Style.FILL
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((alphaInt * 0.45f).roundToInt(), 0, 229, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = (textSizePx * 0.05f).coerceAtLeast(1.5f)
                }
                canvas.drawRoundRect(bgRect, radius, radius, bgPaint)
                canvas.drawRoundRect(bgRect, radius, radius, strokePaint)
            }

            val fm = textPaint.fontMetrics
            val totalTextHeight = lines.size * lineHeight
            var baseline = cy - totalTextHeight / 2f - fm.ascent - (lineHeight - (fm.descent - fm.ascent)) / 2f
            for (line in lines) {
                canvas.drawText(line, cx, baseline, textPaint)
                baseline += lineHeight
            }
        }

        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { fos ->
            outBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        outBitmap.recycle()
        return destFile
    }

    private fun readAttributeMap(parser: XmlPullParser): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until parser.attributeCount) {
            map[parser.getAttributeName(i).lowercase()] = parser.getAttributeValue(i)
        }
        map["style"]?.let { styleStr ->
            styleStr.split(';').forEach { decl ->
                val parts = decl.split(':', limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        map[key] = value
                    }
                }
            }
        }
        return map
    }

    private fun parseSvgViewport(attrs: Map<String, String>): FloatArray {
        val vb = attrs["viewbox"]?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() }
        if (vb != null && vb.size == 4 && vb[2] > 0f && vb[3] > 0f) {
            return floatArrayOf(vb[0], vb[1], vb[2], vb[3])
        }
        val w = parseLength(attrs["width"], 100f).coerceAtLeast(1f)
        val h = parseLength(attrs["height"], 100f).coerceAtLeast(1f)
        return floatArrayOf(0f, 0f, w, h)
    }

    private fun mergeSvgStyle(parent: SvgStyleState, attrs: Map<String, String>): SvgStyleState {
        val fillVal = attrs["fill"]
        val nextFill = when {
            fillVal == null -> parent.fillColor
            fillVal.equals("none", ignoreCase = true) || fillVal.equals("transparent", ignoreCase = true) -> null
            else -> parseSvgColor(fillVal) ?: parent.fillColor
        }
        val strokeVal = attrs["stroke"]
        val nextStroke = when {
            strokeVal == null -> parent.strokeColor
            strokeVal.equals("none", ignoreCase = true) || strokeVal.equals("transparent", ignoreCase = true) -> null
            else -> parseSvgColor(strokeVal) ?: parent.strokeColor
        }
        val nextStrokeW = attrs["stroke-width"]?.let { parseLength(it, parent.strokeWidth) } ?: parent.strokeWidth
        val nextFillOp = attrs["fill-opacity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: parent.fillOpacity
        val nextStrokeOp = attrs["stroke-opacity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: parent.strokeOpacity
        val localOp = attrs["opacity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

        return parent.copy(
            fillColor = nextFill,
            strokeColor = nextStroke,
            strokeWidth = nextStrokeW,
            fillOpacity = nextFillOp,
            strokeOpacity = nextStrokeOp,
            groupOpacity = (parent.groupOpacity * localOp).coerceIn(0f, 1f),
            hasExplicitMatrix = false
        )
    }

    private fun parseSvgColor(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.equals("currentColor", ignoreCase = true)) return Color.CYAN
        if (trimmed.startsWith("#")) {
            val hex = trimmed.substring(1)
            val normalized = when (hex.length) {
                3 -> "#${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}"
                4 -> "#${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}"
                else -> trimmed
            }
            return runCatching { Color.parseColor(normalized) }.getOrNull()
        }
        if (trimmed.startsWith("rgb(", ignoreCase = true) && trimmed.endsWith(")")) {
            val nums = trimmed.substring(4, trimmed.length - 1)
                .split(',')
                .mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 255) }
            if (nums.size >= 3) {
                return Color.rgb(nums[0], nums[1], nums[2])
            }
        }
        return when (trimmed.lowercase()) {
            "black" -> Color.BLACK
            "white" -> Color.WHITE
            "red" -> Color.RED
            "green" -> Color.GREEN
            "blue" -> Color.BLUE
            "cyan", "aqua" -> Color.CYAN
            "yellow" -> Color.YELLOW
            "magenta" -> Color.MAGENTA
            "gray", "grey" -> Color.GRAY
            "orange" -> Color.rgb(255, 165, 0)
            "amber" -> Color.rgb(255, 179, 0)
            else -> runCatching { Color.parseColor(trimmed) }.getOrNull()
        }
    }

    private fun parseLength(raw: String?, default: Float): Float {
        if (raw.isNullOrBlank()) return default
        val cleaned = raw.trim().replace(Regex("(px|pt|dp|sp|em|rem|%)$", RegexOption.IGNORE_CASE), "")
        return cleaned.toFloatOrNull() ?: default
    }

    private fun parsePointsPath(raw: String?, close: Boolean): Path? {
        if (raw.isNullOrBlank()) return null
        val nums = raw.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
        if (nums.size < 4) return null
        return Path().apply {
            moveTo(nums[0], nums[1])
            var i = 2
            while (i + 1 < nums.size) {
                lineTo(nums[i], nums[i + 1])
                i += 2
            }
            if (close) close()
        }
    }

    private fun parseSvgTransform(raw: String): Matrix {
        val result = Matrix()
        val regex = Regex("([a-zA-Z]+)\\s*\\(([^)]*)\\)")
        for (match in regex.findAll(raw)) {
            val fn = match.groupValues[1].lowercase()
            val args = match.groupValues[2].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            val step = Matrix()
            when (fn) {
                "translate" -> {
                    val tx = args.getOrElse(0) { 0f }
                    val ty = args.getOrElse(1) { 0f }
                    step.setTranslate(tx, ty)
                }
                "scale" -> {
                    val sx = args.getOrElse(0) { 1f }
                    val sy = args.getOrElse(1) { sx }
                    step.setScale(sx, sy)
                }
                "rotate" -> {
                    val deg = args.getOrElse(0) { 0f }
                    if (args.size >= 3) {
                        step.setRotate(deg, args[1], args[2])
                    } else {
                        step.setRotate(deg)
                    }
                }
            }
            result.preConcat(step)
        }
        return result
    }
}
