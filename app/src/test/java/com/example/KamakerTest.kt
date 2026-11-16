package com.example

import com.example.domain.model.ContainerFormat
import com.example.domain.model.ConversionConfig
import com.example.domain.model.KamakerBuiltInPresets
import com.example.domain.model.KamakerFrameRule
import com.example.domain.model.KamakerFrameShape
import com.example.domain.model.KamakerGlobalAdjust
import com.example.domain.model.KamakerHorizontalAlign
import com.example.domain.model.KamakerMode
import com.example.domain.model.KamakerPresetPayload
import com.example.domain.model.KamakerSpec
import com.example.domain.model.KamakerVerticalAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KamakerTest {

    @Test
    fun autoFrameRuleResolvesClosestAspectShape() {
        val spec = KamakerSpec(frameRule = KamakerFrameRule.AUTO)

        assertEquals(KamakerFrameShape.SQUARE_1_1, spec.resolveShapeForAspect(1.0f))
        assertEquals(KamakerFrameShape.LANDSCAPE_16_9, spec.resolveShapeForAspect(16f / 9f))
        assertEquals(KamakerFrameShape.PORTRAIT_9_16, spec.resolveShapeForAspect(9f / 16f))
        assertEquals(KamakerFrameShape.LANDSCAPE_16_9, spec.resolveShapeForAspect(2.1f))
        assertEquals(KamakerFrameShape.PORTRAIT_9_16, spec.resolveShapeForAspect(0.5f))
    }

    @Test
    fun forcedFrameRuleOverridesVideoAspect() {
        val forced = KamakerSpec(frameRule = KamakerFrameRule.FORCE_1_1)
        assertEquals(KamakerFrameShape.SQUARE_1_1, forced.resolveShapeForAspect(16f / 9f))
        assertEquals(KamakerFrameShape.SQUARE_1_1, forced.resolveShapeForAspect(9f / 16f))
    }

    @Test
    fun effectivePlacementCombinesShapeRuleAndGlobalAdjust() {
        val base = KamakerSpec(
            globalAdjust = KamakerGlobalAdjust(
                scalePercent = 150f,
                offsetXPercent = 5f,
                offsetYPercent = -10f,
                rotateDegrees = 25f
            )
        )
        val matchedShape = base.resolveShapeForAspect(16f / 9f)
        val placement = base.resolveEffectivePlacement(16f / 9f)
        assertEquals(KamakerFrameShape.LANDSCAPE_16_9, matchedShape)
        // Default 16:9 scale is 12f * 1.5 = 18f
        assertEquals(18f, placement.scalePercent, 0.01f)
        assertEquals(25f, placement.rotateDegrees, 0.01f)
    }

    @Test
    fun presetApplicationKeepsExistingLogoUriIntact() {
        val specWithLogo = KamakerSpec(
            mode = KamakerMode.LOGO,
            logoUriString = "content://media/external/images/media/42",
            logoDisplayName = "brand.png",
            logoIsSvg = false
        )
        val topLeftPreset = KamakerBuiltInPresets.all.first { it.id == "builtin_top_left" }
        val updated = topLeftPreset.payload.applyToSpec(specWithLogo)

        assertEquals("content://media/external/images/media/42", updated.logoUriString)
        assertEquals("brand.png", updated.logoDisplayName)
        assertEquals(KamakerVerticalAlign.TOP, updated.squareRule.verticalAlign)
        assertEquals(KamakerHorizontalAlign.LEFT, updated.squareRule.horizontalAlign)
        assertEquals(KamakerPresetPayload.fromSpec(updated).opacityPercent, updated.opacityPercent, 0.01f)
    }

    @Test
    fun conversionConfigDetectsKamakerActiveOverlaysForVideoOnly() {
        val km = KamakerSpec(mode = KamakerMode.TEXT, text = "dani", applyInStudio = true)
        val videoCfg = ConversionConfig(container = ContainerFormat.MP4, kamakerSpec = km)
        val audioCfg = ConversionConfig(container = ContainerFormat.MP3, kamakerSpec = km)

        assertTrue(videoCfg.hasActiveOverlays)
        assertFalse(audioCfg.hasActiveOverlays)
    }
}
