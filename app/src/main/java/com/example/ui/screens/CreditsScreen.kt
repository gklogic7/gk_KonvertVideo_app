package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.CodecCatalogEntry
import com.example.ffmpeg.RuntimeEngineInfo
import com.example.ui.components.PulsingSignalBadge
import com.example.ui.components.StudioSurfaceCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStudioColors

private enum class CreditsSubtab(val label: String) {
    CREDITS("Credits"),
    FORMATS("Formats & Codecs")
}

private data class CreditLibraryGroup(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentIsAmber: Boolean = false,
    val libraries: List<Pair<String, String>>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditsScreen(
    widthSizeClass: WindowWidthSizeClass,
    engineInfo: RuntimeEngineInfo? = null,
    onEnsureFormatsLoaded: () -> Unit = {},
    onRefreshFormats: () -> Unit = {},
    onUseCodecInStudio: (CodecCatalogEntry) -> Unit = {}
) {
    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact
    val studioColors = LocalStudioColors.current
    var selectedSubtab by rememberSaveable { mutableStateOf(CreditsSubtab.CREDITS) }

    val libraryGroups = listOf(
        CreditLibraryGroup(
            title = "FFmpeg Core & Engine",
            subtitle = "On-device multimedia demuxing, filtering, and transcoding",
            icon = Icons.Rounded.Memory,
            libraries = listOf(
                "FFmpeg" to "libavcodec, libavformat, libavfilter, libswscale, libswresample",
                "FFmpegKit (Full-GPL)" to "Maintained Android JNI bridge (ARMv8, x86, x86_64)",
                "librtmp" to "RTMP streaming & protocol support"
            )
        ),
        CreditLibraryGroup(
            title = "Bundled Video Codecs",
            subtitle = "Hardware-independent offline video compression & animation",
            icon = Icons.Rounded.Movie,
            accentIsAmber = true,
            libraries = listOf(
                "x264 (libx264)" to "H.264 / MPEG-4 AVC encoder",
                "x265 (libx265)" to "H.265 / HEVC high-efficiency encoder",
                "libvpx (VP8 & VP9)" to "Open WebM royalty-free video codecs",
                "libtheora" to "Open OGG video compression",
                "libxvid & MPEG-4" to "AVI & MPEG-4 Part 2 ASP encoding",
                "libwebp" to "Animated & still WebP frame encoder"
            )
        ),
        CreditLibraryGroup(
            title = "Bundled Audio & Speech Codecs",
            subtitle = "Music, podcast, lossless hybrid, and wideband voice encoding",
            icon = Icons.Rounded.Audiotrack,
            libraries = listOf(
                "mp3lame (libmp3lame)" to "LAME MP3 audio compression + ID3v2 artwork",
                "libfdk_aac & AAC" to "High-profile & native AAC-LC encoding",
                "libopus & libvorbis" to "Modern Opus & OGG Vorbis audio",
                "libspeex" to "Low-latency speech compression",
                "libvo_amrwbenc & libopencore-amr" to "AMR-WB & AMR-NB 3GP voice codecs",
                "libwavpack & libsox" to "WavPack lossless/hybrid & SoX resampling"
            )
        ),
        CreditLibraryGroup(
            title = "Android & Studio Frameworks",
            subtitle = "Adaptive tablet UI, local persistence, and media preview",
            icon = Icons.Rounded.Layers,
            accentIsAmber = true,
            libraries = listOf(
                "Jetpack Compose & Material 3" to "Adaptive NavigationRail & Obsidian Dark Studio UI",
                "AndroidX Media3 ExoPlayer" to "Hardware-accelerated preview & crop scrubber",
                "Room & DataStore" to "Local conversion history, presets & studio preferences"
            )
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("credits_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = if (isTablet) 28.dp else 16.dp, vertical = 12.dp)
        ) {
            CreditsSubtab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedSubtab == tab,
                    onClick = { selectedSubtab = tab },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = CreditsSubtab.entries.size
                    ),
                    icon = {
                        Icon(
                            imageVector = if (tab == CreditsSubtab.CREDITS) Icons.Rounded.Verified else Icons.Rounded.AutoAwesomeMosaic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag(
                        if (tab == CreditsSubtab.CREDITS) "credits_subtab_credits" else "credits_subtab_formats"
                    )
                ) {
                    Text(tab.label)
                }
            }
        }

        if (selectedSubtab == CreditsSubtab.FORMATS) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("credits_formats_subtab_content")
            ) {
                FormatsCatalogScreen(
                    widthSizeClass = widthSizeClass,
                    engineInfo = engineInfo,
                    onEnsureLoaded = onEnsureFormatsLoaded,
                    onRefresh = onRefreshFormats,
                    onUseCodecInStudio = onUseCodecInStudio
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTablet) 28.dp else 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // kvect - Konvert video Brand Hero Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, studioColors.hairlineBorderBright)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0F212E),
                                    Color(0xFF131822),
                                    Color(0xFF221A10)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(28.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.credits_brand_title),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.credits_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            PulsingSignalBadge(
                                label = "v${BuildConfig.VERSION_NAME}",
                                active = false,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CreditMetaPill(label = "Studio: kvect")
                            CreditMetaPill(label = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                            CreditMetaPill(label = "Target Android 14+ (API ${Build.VERSION.SDK_INT})")
                            CreditMetaPill(label = "Offline FFmpeg Built-In")
                        }
                    }
                }
            }

            // Offline Privacy & Architecture Summary
            StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = studioColors.success,
                        modifier = Modifier.size(30.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Created by kvect — Private Local Processing",
                            style = MaterialTheme.typography.titleSmall,
                            color = studioColors.success
                        )
                        Text(
                            text = "All video cropping, codec transcoding, and audio cover embedding run locally on your device CPU without cloud servers, telemetry, or extra codec downloads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Library Credits Grid (2 columns on Tablet, 1 column on Phone)
            if (isTablet) {
                libraryGroups.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        pair.forEach { group ->
                            CreditLibraryGroupCard(
                                group = group,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                libraryGroups.forEach { group ->
                    CreditLibraryGroupCard(
                        group = group,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Open-Source Attribution Footer
            StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "FFmpeg is a trademark of Fabrice Bellard. Bundled open-source codecs and libraries remain the property of their respective maintainers and open-source licenses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun CreditMetaPill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, LocalStudioColors.current.hairlineBorder)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CreditLibraryGroupCard(
    group: CreditLibraryGroup,
    modifier: Modifier = Modifier
) {
    val accent = if (group.accentIsAmber) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    StudioSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent
                    )
                    Text(
                        text = group.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.libraries.forEach { (name, detail) ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, LocalStudioColors.current.hairlineBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
