package com.example.ffmpeg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import com.example.domain.model.AudioArtworkSelection
import com.example.domain.model.StudioCoverIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object AudioArtworkGenerator {

    suspend fun prepareArtworkFile(
        context: Context,
        selection: AudioArtworkSelection,
        jobId: String
    ): File? = withContext(Dispatchers.IO) {
        if (selection is AudioArtworkSelection.None) return@withContext null

        val artworkDir = File(context.cacheDir, "artwork").apply {
            if (!exists()) mkdirs()
        }
        val outFile = File(artworkDir, "cover_${jobId}.jpg")

        runCatching {
            when (selection) {
                is AudioArtworkSelection.GalleryImage -> {
                    val uri = Uri.parse(selection.uriString)
                    val bitmap = decodeScaledBitmap(context, uri, maxSizePx = 720)
                        ?: return@runCatching null
                    try {
                        FileOutputStream(outFile).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    outFile
                }

                is AudioArtworkSelection.StudioIcon -> {
                    val bitmap = renderStudioIconBitmap(selection.icon, sizePx = 512)
                    try {
                        FileOutputStream(outFile).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    outFile
                }

                AudioArtworkSelection.None -> null
            }
        }.getOrNull()
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxSizePx: Int): Bitmap? {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var sampleSize = 1
        while ((srcW / sampleSize) > maxSizePx * 2 || (srcH / sampleSize) > maxSizePx * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val scale = min(maxSizePx.toFloat() / sampled.width, maxSizePx.toFloat() / sampled.height)
        return if (scale < 1f) {
            val targetW = (sampled.width * scale).toInt().coerceAtLeast(1)
            val targetH = (sampled.height * scale).toInt().coerceAtLeast(1)
            val resized = Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
            if (resized != sampled) sampled.recycle()
            resized
        } else {
            sampled
        }
    }

    fun renderStudioIconBitmap(icon: StudioCoverIcon, sizePx: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = sizePx.toFloat()
        val accent = icon.accentHex.toInt()
        val secondary = icon.secondaryHex.toInt()

        // Dark Obsidian studio gradient background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, s, s,
                intArrayOf(Color.parseColor("#0B0E14"), secondary, Color.parseColor("#131822")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, s, s, bgPaint)

        // Subtle outer ring frame
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.012f
            color = accent
            alpha = 110
        }
        canvas.drawRoundRect(
            RectF(s * 0.08f, s * 0.08f, s * 0.92f, s * 0.92f),
            s * 0.14f,
            s * 0.14f,
            framePaint
        )

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = s * 0.045f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val cx = s / 2f
        val cy = s / 2f

        when (icon) {
            StudioCoverIcon.STUDIO_WAVE -> {
                val heights = floatArrayOf(0.18f, 0.34f, 0.52f, 0.38f, 0.58f, 0.30f, 0.20f)
                val barW = s * 0.055f
                val gap = s * 0.032f
                val totalW = heights.size * barW + (heights.size - 1) * gap
                val startX = cx - totalW / 2f
                heights.forEachIndexed { idx, hf ->
                    val bh = s * hf
                    val left = startX + idx * (barW + gap)
                    canvas.drawRoundRect(
                        RectF(left, cy - bh / 2f, left + barW, cy + bh / 2f),
                        barW / 2f,
                        barW / 2f,
                        fgPaint
                    )
                }
            }

            StudioCoverIcon.VINYL_DISC -> {
                canvas.drawCircle(cx, cy, s * 0.28f, strokePaint)
                strokePaint.strokeWidth = s * 0.02f
                strokePaint.alpha = 140
                canvas.drawCircle(cx, cy, s * 0.19f, strokePaint)
                canvas.drawCircle(cx, cy, s * 0.085f, fgPaint)
                fgPaint.color = Color.parseColor("#0B0E14")
                canvas.drawCircle(cx, cy, s * 0.028f, fgPaint)
            }

            StudioCoverIcon.PODCAST_MIC -> {
                canvas.drawRoundRect(
                    RectF(cx - s * 0.09f, cy - s * 0.23f, cx + s * 0.09f, cy + s * 0.04f),
                    s * 0.09f,
                    s * 0.09f,
                    fgPaint
                )
                canvas.drawArc(
                    RectF(cx - s * 0.17f, cy - s * 0.12f, cx + s * 0.17f, cy + s * 0.14f),
                    0f,
                    180f,
                    false,
                    strokePaint
                )
                canvas.drawLine(cx, cy + s * 0.14f, cx, cy + s * 0.25f, strokePaint)
                canvas.drawLine(cx - s * 0.1f, cy + s * 0.25f, cx + s * 0.1f, cy + s * 0.25f, strokePaint)
            }

            StudioCoverIcon.CYBER_EQ -> {
                val cols = 4
                val barW = s * 0.09f
                val gap = s * 0.04f
                val totalW = cols * barW + (cols - 1) * gap
                val startX = cx - totalW / 2f
                val levels = floatArrayOf(0.32f, 0.56f, 0.44f, 0.62f)
                val bottom = cy + s * 0.28f
                levels.forEachIndexed { idx, lf ->
                    val left = startX + idx * (barW + gap)
                    val top = bottom - s * lf
                    canvas.drawRoundRect(
                        RectF(left, top, left + barW, bottom),
                        s * 0.025f,
                        s * 0.025f,
                        fgPaint
                    )
                }
            }

            StudioCoverIcon.HI_RES_BADGE -> {
                val hexPath = Path().apply {
                    moveTo(cx, cy - s * 0.27f)
                    lineTo(cx + s * 0.23f, cy - s * 0.135f)
                    lineTo(cx + s * 0.23f, cy + s * 0.135f)
                    lineTo(cx, cy + s * 0.27f)
                    lineTo(cx - s * 0.23f, cy + s * 0.135f)
                    lineTo(cx - s * 0.23f, cy - s * 0.135f)
                    close()
                }
                canvas.drawPath(hexPath, strokePaint)
                val playPath = Path().apply {
                    moveTo(cx - s * 0.07f, cy - s * 0.11f)
                    lineTo(cx + s * 0.12f, cy)
                    lineTo(cx - s * 0.07f, cy + s * 0.11f)
                    close()
                }
                canvas.drawPath(playPath, fgPaint)
            }

            StudioCoverIcon.STUDIO_TAPE -> {
                canvas.drawRoundRect(
                    RectF(cx - s * 0.27f, cy - s * 0.17f, cx + s * 0.27f, cy + s * 0.17f),
                    s * 0.04f,
                    s * 0.04f,
                    strokePaint
                )
                canvas.drawCircle(cx - s * 0.12f, cy, s * 0.065f, fgPaint)
                canvas.drawCircle(cx + s * 0.12f, cy, s * 0.065f, fgPaint)
            }
        }

        return bitmap
    }
}
