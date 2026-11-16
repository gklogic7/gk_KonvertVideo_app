package com.example.ffmpeg

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.domain.model.KamakerBatchImageItem
import com.example.domain.model.KamakerExportFormat
import com.example.domain.model.KamakerFont
import com.example.domain.model.KamakerMode
import com.example.domain.model.KamakerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object KamakerRenderer {

    fun drawWatermarkOnCanvas(
        context: Context,
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        spec: KamakerSpec
    ) {
        if (!spec.hasActiveContent || canvasWidth <= 0 || canvasHeight <= 0) return

        val aspect = canvasWidth.toFloat() / canvasHeight.coerceAtLeast(1).toFloat()
        val placement = spec.resolveEffectivePlacement(aspect)
        val alphaInt = ((spec.opacityPercent.coerceIn(2f, 100f) / 100f) * 255f).roundToInt().coerceIn(1, 255)
        val cx = placement.xFraction.coerceIn(0.02f, 0.98f) * canvasWidth
        val cy = placement.yFraction.coerceIn(0.02f, 0.98f) * canvasHeight
        val targetW = (canvasWidth * (placement.scalePercent / 100f)).coerceAtLeast(16f)

        canvas.save()
        canvas.translate(cx, cy)
        if (placement.rotateDegrees != 0f) {
            canvas.rotate(placement.rotateDegrees)
        }

        when (spec.mode) {
            KamakerMode.LOGO -> {
                val logoBmp = OverlayBitmapRenderer.decodeLogoBitmap(
                    context = context,
                    uriString = spec.logoUriString,
                    isSvgHint = spec.logoIsSvg,
                    targetMaxPx = targetW.roundToInt().coerceIn(64, 1536)
                )
                if (logoBmp != null) {
                    val logoAspect = (logoBmp.width.toFloat() / logoBmp.height.coerceAtLeast(1).toFloat())
                        .coerceIn(0.15f, 6f)
                    val targetH = targetW / logoAspect
                    val dstRect = RectF(-targetW / 2f, -targetH / 2f, targetW / 2f, targetH / 2f)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        alpha = alphaInt
                        if (spec.shadow) {
                            setShadowLayer(
                                (targetW * 0.04f).coerceIn(2f, 16f),
                                0f,
                                (targetW * 0.025f).coerceIn(1f, 10f),
                                Color.argb((alphaInt * 0.65f).roundToInt(), 0, 0, 0)
                            )
                        }
                    }
                    canvas.drawBitmap(logoBmp, null, dstRect, paint)
                    logoBmp.recycle()
                }
            }

            KamakerMode.TEXT -> {
                val lines = spec.text.trim().lines().filter { it.isNotEmpty() }
                    .ifEmpty { listOf(spec.text.trim()) }
                val typefaceStyle = when {
                    spec.bold && spec.italic -> Typeface.BOLD_ITALIC
                    spec.bold -> Typeface.BOLD
                    spec.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                val baseFamily = when (spec.font) {
                    KamakerFont.SANS -> Typeface.SANS_SERIF
                    KamakerFont.SERIF -> Typeface.SERIF
                    KamakerFont.MONO -> Typeface.MONOSPACE
                    KamakerFont.DISPLAY -> Typeface.create("sans-serif-medium", typefaceStyle)
                }
                val tf = Typeface.create(baseFamily, typefaceStyle)

                val probePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = 100f
                }
                val measuredMaxW = lines.maxOfOrNull { probePaint.measureText(it) } ?: 100f
                val normalizedRefW = max(measuredMaxW, 180f)
                val fontSizePx = ((targetW / normalizedRefW) * 100f)
                    .coerceIn(10f, canvasHeight * 0.42f)

                val fillColorInt = applyAlpha(spec.textColorHex.toInt(), alphaInt)
                val strokeColorInt = applyAlpha(spec.strokeColorHex.toInt(), alphaInt)

                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = fontSizePx
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.FILL
                    color = fillColorInt
                    if (spec.shadow) {
                        setShadowLayer(
                            (fontSizePx * 0.14f).coerceIn(2f, 18f),
                            0f,
                            (fontSizePx * 0.08f).coerceIn(1f, 12f),
                            Color.argb((alphaInt * 0.72f).roundToInt(), 0, 0, 0)
                        )
                    }
                }

                val fm = fillPaint.fontMetrics
                val lineHeight = (fm.descent - fm.ascent) * 1.15f
                val totalTextH = lineHeight * lines.size
                var baselineY = -(totalTextH / 2f) - fm.ascent

                val strokeWidthPx = if (spec.strokeEnabled && spec.strokeWidthPercent > 0f) {
                    (fontSizePx * (spec.strokeWidthPercent / 100f) * 1.8f).coerceIn(0.8f, fontSizePx * 0.35f)
                } else 0f

                val strokePaint = if (strokeWidthPx > 0f) {
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = tf
                        textSize = fontSizePx
                        textAlign = Paint.Align.CENTER
                        style = Paint.Style.STROKE
                        strokeJoin = Paint.Join.ROUND
                        strokeCap = Paint.Cap.ROUND
                        strokeWidth = strokeWidthPx
                        color = strokeColorInt
                    }
                } else null

                lines.forEach { line ->
                    if (strokePaint != null) {
                        canvas.drawText(line, 0f, baselineY, strokePaint)
                    }
                    canvas.drawText(line, 0f, baselineY, fillPaint)
                    baselineY += lineHeight
                }
            }
        }

        canvas.restore()
    }

    fun renderKamakerOverlayPng(
        context: Context,
        spec: KamakerSpec,
        canvasWidth: Int,
        canvasHeight: Int,
        destFile: File
    ): File? {
        if (!spec.hasActiveContent) return null
        val safeW = canvasWidth.coerceIn(64, 2048)
        val safeH = canvasHeight.coerceIn(64, 2048)
        val outBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(outBitmap)
            drawWatermarkOnCanvas(context, canvas, safeW, safeH, spec)
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { fos ->
                outBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            destFile
        } finally {
            outBitmap.recycle()
        }
    }

    fun renderCombinedOverlayPng(
        context: Context,
        kamakerSpec: KamakerSpec?,
        videoOverlayConfig: com.example.domain.model.VideoOverlayConfig,
        canvasWidth: Int,
        canvasHeight: Int,
        destFile: File
    ): File? {
        val hasKamaker = kamakerSpec?.hasActiveContent == true
        val hasStudioOverlay = videoOverlayConfig.isActive
        if (!hasKamaker && !hasStudioOverlay) return null

        if (hasKamaker && !hasStudioOverlay) {
            return renderKamakerOverlayPng(context, kamakerSpec!!, canvasWidth, canvasHeight, destFile)
        }
        if (!hasKamaker && hasStudioOverlay) {
            return OverlayBitmapRenderer.renderCompositeOverlayPng(
                context, videoOverlayConfig, canvasWidth, canvasHeight, destFile
            )
        }

        val safeW = canvasWidth.coerceIn(64, 2048)
        val safeH = canvasHeight.coerceIn(64, 2048)
        val outBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(outBitmap)
            drawWatermarkOnCanvas(context, canvas, safeW, safeH, kamakerSpec!!)
            val studioTemp = File(destFile.parentFile ?: context.cacheDir, "studio_part_${destFile.name}")
            val studioFile = OverlayBitmapRenderer.renderCompositeOverlayPng(
                context, videoOverlayConfig, safeW, safeH, studioTemp
            )
            if (studioFile != null && studioFile.exists()) {
                val studioBmp = BitmapFactory.decodeFile(studioFile.absolutePath)
                if (studioBmp != null) {
                    canvas.drawBitmap(studioBmp, 0f, 0f, null)
                    studioBmp.recycle()
                }
                studioFile.delete()
            }
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { fos ->
                outBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            destFile
        } finally {
            outBitmap.recycle()
        }
    }

    fun stampWatermarkOnBitmap(
        context: Context,
        source: Bitmap,
        spec: KamakerSpec
    ): Bitmap {
        val stamped = source.copy(Bitmap.Config.ARGB_8888, true)
        if (spec.hasActiveContent) {
            val canvas = Canvas(stamped)
            drawWatermarkOnCanvas(context, canvas, stamped.width, stamped.height, spec)
        }
        return stamped
    }

    suspend fun inspectBatchImages(
        context: Context,
        uris: List<Uri>
    ): List<KamakerBatchImageItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        uris.mapNotNull { uri ->
            runCatching {
                val mime = resolver.getType(uri) ?: "image/png"
                val displayName = queryDisplayName(resolver, uri) ?: "image_${System.currentTimeMillis()}.png"
                val (w, h) = readOrientedImageSize(resolver, uri)
                if (w > 0 && h > 0) {
                    KamakerBatchImageItem(
                        id = "${uri}_${UUID.randomUUID().toString().take(6)}",
                        uri = uri,
                        displayName = displayName,
                        mimeType = mime,
                        width = w,
                        height = h
                    )
                } else null
            }.getOrNull()
        }
    }

    suspend fun scanFolderForImages(
        context: Context,
        treeUri: Uri
    ): List<Uri> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val result = mutableListOf<Uri>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<String>()
        queue.addLast(rootDocId)

        var visitedDirs = 0
        while (queue.isNotEmpty() && visitedDirs < 64 && result.size < 300) {
            val docId = queue.removeFirst()
            visitedDirs++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            runCatching {
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext() && result.size < 300) {
                        val childId = cursor.getString(idCol)
                        val childMime = cursor.getString(mimeCol).orEmpty()
                        if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.addLast(childId)
                        } else if (childMime.startsWith("image/", ignoreCase = true)) {
                            result.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
                        }
                    }
                }
            }
        }
        result
    }

    data class BatchFileExportResult(
        val exportedCount: Int,
        val totalCount: Int,
        val folderSummary: String
    )

    data class BatchZipExportResult(
        val zipUri: Uri?,
        val shareUri: Uri?,
        val fileName: String,
        val stampedCount: Int
    )

    suspend fun exportBatchImagesToFiles(
        context: Context,
        items: List<KamakerBatchImageItem>,
        spec: KamakerSpec,
        renamePrefix: String,
        exportFormat: KamakerExportFormat,
        onProgress: (completed: Int, total: Int) -> Unit
    ): BatchFileExportResult = withContext(Dispatchers.IO) {
        val safeBase = sanitizePrefix(renamePrefix)
        var exported = 0
        items.forEachIndexed { index, item ->
            val src = decodeOrientedBitmap(context.contentResolver, item.uri, maxEdgePx = 3840)
            if (src != null) {
                val stamped = stampWatermarkOnBitmap(context, src, spec)
                if (stamped !== src) src.recycle()

                val targetSpec = resolveOutputFormat(item, exportFormat)
                val numbered = if (items.size > 1) {
                    "${safeBase}_${String.format(Locale.US, "%03d", index + 1)}.${targetSpec.ext}"
                } else {
                    "${safeBase}.${targetSpec.ext}"
                }

                val savedUri = saveBitmapWithFormatToPictures(
                    context = context,
                    bitmap = stamped,
                    displayName = numbered,
                    mimeType = targetSpec.mime,
                    compressFormat = targetSpec.compressFormat,
                    subFolder = "KonvertVideo/Kamaker"
                )
                stamped.recycle()
                if (savedUri != null) exported++
            }
            onProgress(index + 1, items.size)
        }
        BatchFileExportResult(
            exportedCount = exported,
            totalCount = items.size,
            folderSummary = "Pictures/KonvertVideo/Kamaker"
        )
    }

    suspend fun exportBatchImagesToZip(
        context: Context,
        items: List<KamakerBatchImageItem>,
        spec: KamakerSpec,
        renamePrefix: String,
        exportFormat: KamakerExportFormat,
        onProgress: (completed: Int, total: Int) -> Unit
    ): BatchZipExportResult = withContext(Dispatchers.IO) {
        val safeBase = sanitizePrefix(renamePrefix)
        val zipFileName = "${safeBase}_kamaker_${System.currentTimeMillis() % 100000}.zip"
        val exportDir = File(context.cacheDir, "kamaker_zips").apply { mkdirs() }
        val localZipFile = File(exportDir, zipFileName)
        var stampedCount = 0

        ZipOutputStream(FileOutputStream(localZipFile)).use { zos ->
            items.forEachIndexed { index, item ->
                val src = decodeOrientedBitmap(context.contentResolver, item.uri, maxEdgePx = 3840)
                if (src != null) {
                    val stamped = stampWatermarkOnBitmap(context, src, spec)
                    if (stamped !== src) src.recycle()

                    val targetSpec = resolveOutputFormat(item, exportFormat)
                    val entryName = "${safeBase}_${String.format(Locale.US, "%03d", index + 1)}.${targetSpec.ext}"
                    val baos = ByteArrayOutputStream()
                    stamped.compress(targetSpec.compressFormat, 94, baos)
                    stamped.recycle()

                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(baos.toByteArray())
                    zos.closeEntry()
                    stampedCount++
                }
                onProgress(index + 1, items.size)
            }
        }

        val downloadsUri = if (stampedCount > 0 && localZipFile.exists()) {
            MediaExportHelper.exportToPublicMediaStore(
                context = context,
                sourceFile = localZipFile,
                displayName = zipFileName,
                mimeType = "application/zip",
                isAudioOnly = false
            )
        } else null

        val providerUri = if (localZipFile.exists()) {
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localZipFile)
            }.getOrNull()
        } else null

        BatchZipExportResult(
            zipUri = downloadsUri,
            shareUri = providerUri ?: downloadsUri,
            fileName = zipFileName,
            stampedCount = stampedCount
        )
    }

    private data class ResolvedImageOutputFormat(
        val ext: String,
        val mime: String,
        val compressFormat: Bitmap.CompressFormat
    )

    private fun resolveOutputFormat(
        item: KamakerBatchImageItem,
        exportFormat: KamakerExportFormat
    ): ResolvedImageOutputFormat {
        val isSourceJpg = item.mimeType.contains("jpeg", ignoreCase = true) ||
            item.mimeType.contains("jpg", ignoreCase = true) ||
            item.displayName.lowercase(Locale.US).let { it.endsWith(".jpg") || it.endsWith(".jpeg") }

        val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        return when (exportFormat) {
            KamakerExportFormat.JPG -> ResolvedImageOutputFormat("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG)
            KamakerExportFormat.PNG -> ResolvedImageOutputFormat("png", "image/png", Bitmap.CompressFormat.PNG)
            KamakerExportFormat.WEBP -> ResolvedImageOutputFormat("webp", "image/webp", webpFormat)
            KamakerExportFormat.KEEP -> if (isSourceJpg) {
                ResolvedImageOutputFormat("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG)
            } else {
                ResolvedImageOutputFormat("png", "image/png", Bitmap.CompressFormat.PNG)
            }
        }
    }

    private fun saveBitmapWithFormatToPictures(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        mimeType: String,
        compressFormat: Bitmap.CompressFormat,
        subFolder: String
    ): Uri? {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$subFolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            val ok = resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(compressFormat, 94, out)
            } ?: false
            if (!ok) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                runCatching { resolver.update(uri, values, null, null) }
            }
            uri
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun readOrientedImageSize(resolver: ContentResolver, uri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return 0 to 0
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE -> h to w
            else -> w to h
        }
    }

    private fun decodeOrientedBitmap(
        resolver: ContentResolver,
        uri: Uri,
        maxEdgePx: Int
    ): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while (max(rawW / sampleSize, rawH / sampleSize) > maxEdgePx * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }

        val maxSide = max(decoded.width, decoded.height)
        if (maxSide > maxEdgePx) {
            val downscale = maxEdgePx.toFloat() / maxSide.toFloat()
            matrix.postScale(downscale, downscale)
        }

        return if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (transformed !== decoded) decoded.recycle()
            transformed
        } else {
            decoded
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun sanitizePrefix(raw: String): String {
        return raw.trim()
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "kamaker_img" }
    }

    private fun applyAlpha(argb: Int, alphaInt: Int): Int {
        val a = (Color.alpha(argb) * alphaInt / 255).coerceIn(0, 255)
        return Color.argb(a, Color.red(argb), Color.green(argb), Color.blue(argb))
    }
}
