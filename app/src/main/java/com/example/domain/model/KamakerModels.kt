package com.example.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class KamakerMode(val label: String) {
    LOGO("Logo (PNG)"),
    TEXT("Text")
}

@Serializable
enum class KamakerFrameShape(
    val ratioLabel: String,
    val shapeName: String,
    val aspectRatio: Float
) {
    SQUARE_1_1("1:1", "square", 1f),
    LANDSCAPE_16_9("16:9", "landscape", 16f / 9f),
    PORTRAIT_9_16("9:16", "portrait", 9f / 16f)
}

@Serializable
enum class KamakerVerticalAlign(val label: String) {
    TOP("Top"),
    CENTRE("Centre"),
    BOTTOM("Bottom"),
    CUSTOM("Custom")
}

@Serializable
enum class KamakerHorizontalAlign(val label: String) {
    LEFT("Left"),
    MIDDLE("Middle"),
    RIGHT("Right"),
    CUSTOM("Custom")
}

@Serializable
enum class KamakerFont(val id: String, val label: String) {
    SANS("sans", "Sans"),
    SERIF("serif", "Serif"),
    MONO("mono", "Monospace"),
    DISPLAY("display", "Display")
}

@Serializable
enum class KamakerFrameRule(val id: String, val label: String) {
    AUTO("auto", "Auto (match the video)"),
    FORCE_1_1("1:1", "1:1 square"),
    FORCE_16_9("16:9", "16:9 landscape"),
    FORCE_9_16("9:16", "9:16 portrait")
}

@Serializable
enum class KamakerBackdrop(val id: String, val label: String) {
    CURRENT_VIDEO_FRAME("video_frame", "Current video frame"),
    DARK_STUDIO("dark", "Dark studio slate"),
    CHECKERBOARD("checkerboard", "Transparency grid")
}

@Serializable
enum class KamakerExportFormat(val id: String, val label: String) {
    KEEP("keep", "Keep (JPG stays JPG, others PNG)"),
    JPG("jpg", "JPG (Compressed)"),
    PNG("png", "PNG (Lossless)"),
    WEBP("webp", "WebP (Compact)")
}

@Serializable
@Immutable
data class KamakerShapeRule(
    val xFraction: Float = 0.82f,
    val yFraction: Float = 0.84f,
    val scalePercent: Float = 18f,
    val rotateDegrees: Float = 0f,
    val verticalAlign: KamakerVerticalAlign = KamakerVerticalAlign.BOTTOM,
    val horizontalAlign: KamakerHorizontalAlign = KamakerHorizontalAlign.RIGHT
)

@Serializable
@Immutable
data class KamakerGlobalAdjust(
    val scalePercent: Float = 100f,
    val offsetXPercent: Float = 0f,
    val offsetYPercent: Float = 0f,
    val rotateDegrees: Float = 0f
)

@Serializable
@Immutable
data class KamakerSpec(
    val mode: KamakerMode = KamakerMode.TEXT,
    val logoUriString: String? = null,
    val logoDisplayName: String = "",
    val logoIsSvg: Boolean = false,
    val text: String = "Konvert",
    val font: KamakerFont = KamakerFont.SANS,
    val textColorHex: Long = 0xFFFFFFFF,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val shadow: Boolean = true,
    val strokeEnabled: Boolean = true,
    val strokeWidthPercent: Float = 6f,
    val strokeColorHex: Long = 0xFF000000,
    val opacityPercent: Float = 75f,
    val edgeMarginPercent: Float = 3f,
    val globalAdjust: KamakerGlobalAdjust = KamakerGlobalAdjust(),
    val frameRule: KamakerFrameRule = KamakerFrameRule.AUTO,
    val boardBackdrop: KamakerBackdrop = KamakerBackdrop.CURRENT_VIDEO_FRAME,
    val stampOnCapturedFrames: Boolean = true,
    val showOnPlayer: Boolean = true,
    val applyInStudio: Boolean = false,
    val squareRule: KamakerShapeRule = defaultRuleFor(KamakerFrameShape.SQUARE_1_1, 3f),
    val landscapeRule: KamakerShapeRule = defaultRuleFor(KamakerFrameShape.LANDSCAPE_16_9, 3f),
    val portraitRule: KamakerShapeRule = defaultRuleFor(KamakerFrameShape.PORTRAIT_9_16, 3f)
) {
    val hasActiveContent: Boolean
        get() = when (mode) {
            KamakerMode.LOGO -> !logoUriString.isNullOrBlank()
            KamakerMode.TEXT -> text.isNotBlank()
        }

    fun ruleFor(shape: KamakerFrameShape): KamakerShapeRule = when (shape) {
        KamakerFrameShape.SQUARE_1_1 -> squareRule
        KamakerFrameShape.LANDSCAPE_16_9 -> landscapeRule
        KamakerFrameShape.PORTRAIT_9_16 -> portraitRule
    }

    fun withRuleFor(shape: KamakerFrameShape, rule: KamakerShapeRule): KamakerSpec = when (shape) {
        KamakerFrameShape.SQUARE_1_1 -> copy(squareRule = rule)
        KamakerFrameShape.LANDSCAPE_16_9 -> copy(landscapeRule = rule)
        KamakerFrameShape.PORTRAIT_9_16 -> copy(portraitRule = rule)
    }

    fun resolveShapeForAspect(aspectRatio: Float): KamakerFrameShape = when (frameRule) {
        KamakerFrameRule.FORCE_1_1 -> KamakerFrameShape.SQUARE_1_1
        KamakerFrameRule.FORCE_16_9 -> KamakerFrameShape.LANDSCAPE_16_9
        KamakerFrameRule.FORCE_9_16 -> KamakerFrameShape.PORTRAIT_9_16
        KamakerFrameRule.AUTO -> when {
            aspectRatio < 0.85f -> KamakerFrameShape.PORTRAIT_9_16
            aspectRatio > 1.25f -> KamakerFrameShape.LANDSCAPE_16_9
            else -> KamakerFrameShape.SQUARE_1_1
        }
    }

    fun resolveEffectivePlacement(aspectRatio: Float): KamakerShapeRule {
        val base = ruleFor(resolveShapeForAspect(aspectRatio))
        val scaledPct = (base.scalePercent * (globalAdjust.scalePercent / 100f)).coerceIn(4f, 95f)
        val margin = (edgeMarginPercent.coerceIn(0f, 20f) / 100f)
        val rawX = base.xFraction + (globalAdjust.offsetXPercent / 100f)
        val rawY = base.yFraction + (globalAdjust.offsetYPercent / 100f)
        val minCoord = (0.04f + margin * 0.5f).coerceIn(0.03f, 0.35f)
        val maxCoord = (1f - minCoord).coerceIn(0.65f, 0.97f)
        return base.copy(
            xFraction = rawX.coerceIn(minCoord, maxCoord),
            yFraction = rawY.coerceIn(minCoord, maxCoord),
            scalePercent = scaledPct,
            rotateDegrees = wrapDegrees(base.rotateDegrees + globalAdjust.rotateDegrees)
        )
    }

    companion object {
        fun defaultRuleFor(
            shape: KamakerFrameShape,
            edgeMarginPercent: Float = 3f
        ): KamakerShapeRule {
            val scale = when (shape) {
                KamakerFrameShape.SQUARE_1_1 -> 18f
                KamakerFrameShape.LANDSCAPE_16_9 -> 12f
                KamakerFrameShape.PORTRAIT_9_16 -> 29f
            }
            return snapAlign(
                current = KamakerShapeRule(scalePercent = scale, rotateDegrees = 0f),
                vertical = KamakerVerticalAlign.BOTTOM,
                horizontal = KamakerHorizontalAlign.RIGHT,
                edgeMarginPercent = edgeMarginPercent
            )
        }

        fun snapAlign(
            current: KamakerShapeRule,
            vertical: KamakerVerticalAlign,
            horizontal: KamakerHorizontalAlign,
            edgeMarginPercent: Float
        ): KamakerShapeRule {
            val margin = (edgeMarginPercent.coerceIn(0f, 20f) / 100f)
            val halfScale = (current.scalePercent / 100f) * 0.45f
            val x = when (horizontal) {
                KamakerHorizontalAlign.LEFT -> (margin + halfScale).coerceIn(0.08f, 0.45f)
                KamakerHorizontalAlign.MIDDLE -> 0.50f
                KamakerHorizontalAlign.RIGHT -> (1f - margin - halfScale).coerceIn(0.55f, 0.92f)
                KamakerHorizontalAlign.CUSTOM -> current.xFraction
            }
            val y = when (vertical) {
                KamakerVerticalAlign.TOP -> (margin + 0.08f).coerceIn(0.08f, 0.45f)
                KamakerVerticalAlign.CENTRE -> 0.50f
                KamakerVerticalAlign.BOTTOM -> (1f - margin - 0.08f).coerceIn(0.55f, 0.92f)
                KamakerVerticalAlign.CUSTOM -> current.yFraction
            }
            return current.copy(
                xFraction = x,
                yFraction = y,
                verticalAlign = vertical,
                horizontalAlign = horizontal
            )
        }

        fun detectVerticalAlign(yFraction: Float): KamakerVerticalAlign = when {
            yFraction < 0.28f -> KamakerVerticalAlign.TOP
            abs(yFraction - 0.50f) <= 0.08f -> KamakerVerticalAlign.CENTRE
            yFraction > 0.72f -> KamakerVerticalAlign.BOTTOM
            else -> KamakerVerticalAlign.CUSTOM
        }

        fun detectHorizontalAlign(xFraction: Float): KamakerHorizontalAlign = when {
            xFraction < 0.28f -> KamakerHorizontalAlign.LEFT
            abs(xFraction - 0.50f) <= 0.08f -> KamakerHorizontalAlign.MIDDLE
            xFraction > 0.72f -> KamakerHorizontalAlign.RIGHT
            else -> KamakerHorizontalAlign.CUSTOM
        }

        fun wrapDegrees(deg: Float): Float {
            var d = deg % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }
    }
}

@Serializable
@Immutable
data class KamakerPresetPayload(
    val mode: KamakerMode = KamakerMode.TEXT,
    val text: String = "Konvert",
    val font: KamakerFont = KamakerFont.SANS,
    val textColorHex: Long = 0xFFFFFFFF,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val shadow: Boolean = true,
    val strokeEnabled: Boolean = true,
    val strokeWidthPercent: Float = 6f,
    val strokeColorHex: Long = 0xFF000000,
    val opacityPercent: Float = 75f,
    val edgeMarginPercent: Float = 3f,
    val globalAdjust: KamakerGlobalAdjust = KamakerGlobalAdjust(),
    val squareRule: KamakerShapeRule = KamakerSpec.defaultRuleFor(KamakerFrameShape.SQUARE_1_1, 3f),
    val landscapeRule: KamakerShapeRule = KamakerSpec.defaultRuleFor(KamakerFrameShape.LANDSCAPE_16_9, 3f),
    val portraitRule: KamakerShapeRule = KamakerSpec.defaultRuleFor(KamakerFrameShape.PORTRAIT_9_16, 3f)
) {
    fun applyToSpec(spec: KamakerSpec): KamakerSpec = spec.copy(
        mode = mode,
        text = text,
        font = font,
        textColorHex = textColorHex,
        bold = bold,
        italic = italic,
        shadow = shadow,
        strokeEnabled = strokeEnabled,
        strokeWidthPercent = strokeWidthPercent,
        strokeColorHex = strokeColorHex,
        opacityPercent = opacityPercent,
        edgeMarginPercent = edgeMarginPercent,
        globalAdjust = globalAdjust,
        squareRule = squareRule,
        landscapeRule = landscapeRule,
        portraitRule = portraitRule
    )

    companion object {
        fun fromSpec(spec: KamakerSpec): KamakerPresetPayload = KamakerPresetPayload(
            mode = spec.mode,
            text = spec.text,
            font = spec.font,
            textColorHex = spec.textColorHex,
            bold = spec.bold,
            italic = spec.italic,
            shadow = spec.shadow,
            strokeEnabled = spec.strokeEnabled,
            strokeWidthPercent = spec.strokeWidthPercent,
            strokeColorHex = spec.strokeColorHex,
            opacityPercent = spec.opacityPercent,
            edgeMarginPercent = spec.edgeMarginPercent,
            globalAdjust = spec.globalAdjust,
            squareRule = spec.squareRule,
            landscapeRule = spec.landscapeRule,
            portraitRule = spec.portraitRule
        )
    }
}

@Serializable
@Immutable
data class KamakerPreset(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val payload: KamakerPresetPayload
)

object KamakerBuiltInPresets {
    val all: List<KamakerPreset> = listOf(
        KamakerPreset(
            id = "builtin_bottom_right",
            name = "Bottom right",
            isBuiltIn = true,
            payload = KamakerPresetPayload()
        ),
        KamakerPreset(
            id = "builtin_top_left",
            name = "Top left",
            isBuiltIn = true,
            payload = KamakerPresetPayload(
                squareRule = KamakerShapeRule(0.18f, 0.14f, 18f, 0f, KamakerVerticalAlign.TOP, KamakerHorizontalAlign.LEFT),
                landscapeRule = KamakerShapeRule(0.14f, 0.14f, 12f, 0f, KamakerVerticalAlign.TOP, KamakerHorizontalAlign.LEFT),
                portraitRule = KamakerShapeRule(0.22f, 0.12f, 28f, 0f, KamakerVerticalAlign.TOP, KamakerHorizontalAlign.LEFT)
            )
        ),
        KamakerPreset(
            id = "builtin_center_stamp",
            name = "Center stamp",
            isBuiltIn = true,
            payload = KamakerPresetPayload(
                opacityPercent = 35f,
                squareRule = KamakerShapeRule(0.50f, 0.50f, 32f, -15f, KamakerVerticalAlign.CENTRE, KamakerHorizontalAlign.MIDDLE),
                landscapeRule = KamakerShapeRule(0.50f, 0.50f, 24f, -15f, KamakerVerticalAlign.CENTRE, KamakerHorizontalAlign.MIDDLE),
                portraitRule = KamakerShapeRule(0.50f, 0.50f, 44f, -15f, KamakerVerticalAlign.CENTRE, KamakerHorizontalAlign.MIDDLE)
            )
        ),
        KamakerPreset(
            id = "builtin_top_right",
            name = "Top right badge",
            isBuiltIn = true,
            payload = KamakerPresetPayload(
                opacityPercent = 82f,
                squareRule = KamakerShapeRule(0.82f, 0.14f, 16f, 0f, KamakerVerticalAlign.TOP, KamakerHorizontalAlign.RIGHT),
                landscapeRule = KamakerShapeRule(0.86f, 0.14f, 12f, 0f, KamakerVerticalAlign.TOP, KamakerHorizontalAlign.RIGHT),
                portraitRule = KamakerShapeRule(0.78f, 0.12f, 24f, 0f, KamakerVerticalAlign.TOP, KamakerHorizontalAlign.RIGHT)
            )
        )
    )
}

@Immutable
data class KamakerBatchImageItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int
)

@Immutable
data class KamakerBatchExportState(
    val isExporting: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val lastStatusMessage: String? = null
)
