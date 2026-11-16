package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Intent
import com.example.domain.model.LogoOverlayConfig
import com.example.domain.model.PrecisionCropPanelStyle
import com.example.domain.model.StudioSettings
import com.example.domain.model.TextOverlayConfig
import com.example.domain.model.VideoOverlayConfig
import com.example.ffmpeg.CodecCatalogProvider
import com.example.ffmpeg.MediaExportHelper
import com.example.ffmpeg.OverlayBitmapRenderer
import com.example.service.ConversionForegroundService
import com.example.ui.components.KvPrecisionCropStateHelper
import com.example.ui.components.PrecisionEditorTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayAndParallelTest {

    @Test
    fun codecCatalogParsesReconstructedEncoderAndFormatTables() {
        val encodersTable = """
            Encoders:
             V..... = Video
             ------
             V....D a64multi             Multicolor charset for Commodore 64 (codec a64_multi)
             V.S..D libx264              libx264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10
             VF...D libx265              libx265 H.265 / HEVC
             A....D libmp3lame           libmp3lame MP3 (MPEG audio layer 3)
             A....D libopus              libopus Opus
        """.trimIndent()
        val parsedEncoders = CodecCatalogProvider.parseNamesFromTable(encodersTable, prefixLength = 8)
        assertTrue("libx264" in parsedEncoders)
        assertTrue("libx265" in parsedEncoders)
        assertTrue("libmp3lame" in parsedEncoders)
        assertTrue("libopus" in parsedEncoders)

        val formatsTable = """
            File formats:
             D. = Demuxing supported
             .E = Muxing supported
             --
             DE mp4             MP4 (MPEG-4 Part 14)
              E matroska        Matroska
             DE ogg             Ogg
             DE 3gp             3GP (3GPP file format)
        """.trimIndent()
        val parsedFormats = CodecCatalogProvider.parseNamesFromTable(formatsTable, prefixLength = 4)
        assertTrue("mp4" in parsedFormats)
        assertTrue("matroska" in parsedFormats)
        assertTrue("ogg" in parsedFormats)
        assertTrue("3gp" in parsedFormats)
    }

    @Test
    fun newSettingsDefaultsMatchUserExpectations() {
        val defaults = StudioSettings()
        assertFalse(defaults.enableStudioMarkInOut)
        assertFalse(defaults.useKvPlayerInPrecisionCrop)
        assertFalse(defaults.kvHideBottomTiles)
        assertEquals(PrecisionCropPanelStyle.BOTTOM_DRAWER, defaults.precisionCropPanelStyle)
        assertEquals(
            listOf(
                PrecisionCropPanelStyle.BOTTOM_DRAWER,
                PrecisionCropPanelStyle.FLOATING_CARD,
                PrecisionCropPanelStyle.SIDE_PANEL
            ),
            PrecisionCropPanelStyle.entries
        )
        assertTrue(defaults.precisionCropFullKvHud)
        assertEquals(0, defaults.smartParallelLimit)
        assertTrue(defaults.playBatchFinishSound)
    }

    @Test
    fun buildMultiShareIntentHandlesEmptySingleAndMultipleItems() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNull(MediaExportHelper.buildMultiShareIntent(context, emptyList()))

        val singleIntent = MediaExportHelper.buildMultiShareIntent(
            context,
            listOf(Triple("", "content://media/external/video/media/1", "video/mp4"))
        )
        assertNotNull(singleIntent)
        assertEquals(Intent.ACTION_SEND, singleIntent!!.action)
        assertEquals("video/mp4", singleIntent.type)

        val multiIntent = MediaExportHelper.buildMultiShareIntent(
            context,
            listOf(
                Triple("", "content://media/external/video/media/1", "video/mp4"),
                Triple("", "content://media/external/video/media/2", "video/webm")
            )
        )
        assertNotNull(multiIntent)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, multiIntent!!.action)
        assertEquals("video/*", multiIntent.type)
    }

    @Test
    fun kvPrecisionCropLocksBoxesAndEnablesSwipeWhenPanelMinimized() {
        // Expanded + Crop tab -> Crop is interactive, overlays & swipe gestures are not
        assertTrue(KvPrecisionCropStateHelper.isCropInteractive(isPanelMinimized = false, activeTab = PrecisionEditorTab.CROP, isKvHudTab = false))
        assertFalse(KvPrecisionCropStateHelper.isOverlayInteractive(isPanelMinimized = false, activeTab = PrecisionEditorTab.CROP, isKvHudTab = false))
        assertFalse(KvPrecisionCropStateHelper.areSwipeGesturesActive(isPanelMinimized = false))

        // Expanded + Logo/Text tab -> Overlay is interactive, crop & swipe gestures are not
        assertFalse(KvPrecisionCropStateHelper.isCropInteractive(isPanelMinimized = false, activeTab = PrecisionEditorTab.LOGO, isKvHudTab = false))
        assertTrue(KvPrecisionCropStateHelper.isOverlayInteractive(isPanelMinimized = false, activeTab = PrecisionEditorTab.LOGO, isKvHudTab = false))
        assertTrue(KvPrecisionCropStateHelper.isOverlayInteractive(isPanelMinimized = false, activeTab = PrecisionEditorTab.TEXT, isKvHudTab = false))

        // Minimized -> Both Crop and Overlays lock so swipe gestures control brightness/volume
        assertFalse(KvPrecisionCropStateHelper.isCropInteractive(isPanelMinimized = true, activeTab = PrecisionEditorTab.CROP, isKvHudTab = false))
        assertFalse(KvPrecisionCropStateHelper.isOverlayInteractive(isPanelMinimized = true, activeTab = PrecisionEditorTab.LOGO, isKvHudTab = false))
        assertFalse(KvPrecisionCropStateHelper.isOverlayInteractive(isPanelMinimized = true, activeTab = PrecisionEditorTab.TEXT, isKvHudTab = false))
        assertTrue(KvPrecisionCropStateHelper.areSwipeGesturesActive(isPanelMinimized = true))
    }

    @Test
    fun smartParallelWorkerCountRespectsCoresLightweightAndExplicitLimit() {
        assertEquals(1, ConversionForegroundService.computeSmartWorkerCount(0, cpuCores = 2, lightweightMode = false))
        assertEquals(2, ConversionForegroundService.computeSmartWorkerCount(0, cpuCores = 4, lightweightMode = false))
        assertEquals(3, ConversionForegroundService.computeSmartWorkerCount(0, cpuCores = 6, lightweightMode = false))
        assertEquals(4, ConversionForegroundService.computeSmartWorkerCount(0, cpuCores = 12, lightweightMode = false))
        assertEquals(2, ConversionForegroundService.computeSmartWorkerCount(0, cpuCores = 8, lightweightMode = true))
        assertEquals(3, ConversionForegroundService.computeSmartWorkerCount(3, cpuCores = 2, lightweightMode = false))
        assertEquals(4, ConversionForegroundService.computeSmartWorkerCount(4, cpuCores = 4, lightweightMode = true))
    }

    @Test
    fun parsesSvgVectorIntoNonEmptyBitmap() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 60">
                <rect x="0" y="0" width="120" height="60" rx="12" fill="#00E5FF"/>
                <circle cx="30" cy="30" r="14" fill="#FFB300"/>
                <path d="M55,15 L100,30 L55,45 Z" fill="#FFFFFF"/>
            </svg>
        """.trimIndent()
        val bmp = OverlayBitmapRenderer.parseSvgToBitmap(
            inputStream = ByteArrayInputStream(svg.toByteArray()),
            targetMaxPx = 240
        )
        assertNotNull(bmp)
        assertEquals(240, bmp!!.width)
        assertEquals(120, bmp.height)
        assertTrue(hasNonTransparentPixels(bmp))
        bmp.recycle()
    }

    @Test
    fun rendersCompositeSvgLogoAndTextOverlayPng() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val svgFile = File(context.cacheDir, "test_logo.svg").apply {
            writeText(
                """
                <svg viewBox="0 0 64 64">
                    <rect x="4" y="4" width="56" height="56" rx="10" fill="#00E5FF"/>
                </svg>
                """.trimIndent()
            )
        }
        val destPng = File(context.cacheDir, "composite_test.png")
        if (destPng.exists()) destPng.delete()

        val overlayConfig = VideoOverlayConfig(
            logo = LogoOverlayConfig(
                uriString = Uri.fromFile(svgFile).toString(),
                displayName = "test_logo.svg",
                isSvg = true,
                xFraction = 0.85f,
                yFraction = 0.14f,
                widthFraction = 0.20f,
                opacity = 0.9f
            ),
            textBox = TextOverlayConfig(
                text = "kvect • HDR",
                xFraction = 0.50f,
                yFraction = 0.86f,
                scaleFraction = 0.06f,
                colorHex = 0xFF00E5FF,
                hasBackdrop = true
            )
        )

        val result = OverlayBitmapRenderer.renderCompositeOverlayPng(
            context = context,
            overlayConfig = overlayConfig,
            canvasWidth = 640,
            canvasHeight = 360,
            destFile = destPng
        )

        assertNotNull(result)
        assertTrue(destPng.exists() && destPng.length() > 100L)

        val decoded = BitmapFactory.decodeFile(destPng.absolutePath)
        assertNotNull(decoded)
        assertEquals(640, decoded.width)
        assertEquals(360, decoded.height)
        assertTrue(hasNonTransparentPixels(decoded))

        decoded.recycle()
        svgFile.delete()
        destPng.delete()
    }

    private fun hasNonTransparentPixels(bmp: Bitmap): Boolean {
        for (y in 0 until bmp.height step 4) {
            for (x in 0 until bmp.width step 4) {
                if ((bmp.getPixel(x, y) ushr 24) > 0) return true
            }
        }
        return false
    }
}
