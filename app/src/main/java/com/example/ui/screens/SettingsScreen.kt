package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.PrecisionCropPanelStyle
import com.example.domain.model.StudioSettings
import com.example.ui.components.StudioSurfaceCard
import com.example.ui.theme.LocalStudioColors

@Composable
fun SettingsScreen(
    widthSizeClass: WindowWidthSizeClass,
    settings: StudioSettings,
    onToggleQuickPresets: (Boolean) -> Unit,
    onToggleProControls: (Boolean) -> Unit,
    onToggleCommandWorkbench: (Boolean) -> Unit,
    onToggleAutoExport: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onChangeHardwareThreads: (Int) -> Unit,
    onToggleLightweightEngine: (Boolean) -> Unit,
    onToggleLowOverheadUi: (Boolean) -> Unit,
    onToggleAutoPruneCache: (Boolean) -> Unit,
    onToggleCropOverlay: (Boolean) -> Unit,
    onToggleInlineCrop: (Boolean) -> Unit,
    onToggleFullscreenCrop: (Boolean) -> Unit,
    onToggleStudioMarkInOut: (Boolean) -> Unit,
    onChangeSmartParallelLimit: (Int) -> Unit,
    onToggleBatchFinishSound: (Boolean) -> Unit,
    onToggleKvFullscreen: (Boolean) -> Unit,
    onToggleKvScreenshot: (Boolean) -> Unit,
    onToggleKvHideHudUi: (Boolean) -> Unit,
    onToggleKvMarkInOut: (Boolean) -> Unit,
    onToggleKvFloating: (Boolean) -> Unit,
    onToggleKvHideBottomTiles: (Boolean) -> Unit = {},
    onEnableAllModes: () -> Unit,
    onToggleUseKvPlayerInPrecisionCrop: (Boolean) -> Unit = {},
    onChangePrecisionCropPanelStyle: (PrecisionCropPanelStyle) -> Unit = {},
    onTogglePrecisionCropFullKvHud: (Boolean) -> Unit = {}
) {
    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact
    val studioColors = LocalStudioColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTablet) 28.dp else 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Customize Studio workflow tiers & offline processing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!settings.showQuickPresets || !settings.showProControls || !settings.showCommandWorkbench) {
                    OutlinedButton(
                        onClick = onEnableAllModes,
                        modifier = Modifier.testTag("enable_all_tiers_button")
                    ) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enable All")
                    }
                }
            }

            // Studio Interface Tiers (Checkbox Ticks for All 3 Modes)
            StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_modes_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_modes_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TierCheckboxRow(
                        title = stringResource(R.string.toggle_quick_presets),
                        description = stringResource(R.string.toggle_quick_presets_desc),
                        icon = Icons.Rounded.AutoAwesome,
                        checked = settings.showQuickPresets,
                        onCheckedChange = onToggleQuickPresets,
                        testTag = "setting_checkbox_presets"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    TierCheckboxRow(
                        title = stringResource(R.string.toggle_pro_controls),
                        description = stringResource(R.string.toggle_pro_controls_desc),
                        icon = Icons.Rounded.Tune,
                        checked = settings.showProControls,
                        onCheckedChange = onToggleProControls,
                        testTag = "setting_checkbox_pro"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    TierCheckboxRow(
                        title = stringResource(R.string.toggle_workbench),
                        description = stringResource(R.string.toggle_workbench_desc),
                        icon = Icons.Rounded.Terminal,
                        checked = settings.showCommandWorkbench,
                        onCheckedChange = onToggleCommandWorkbench,
                        testTag = "setting_checkbox_workbench"
                    )
                }
            }

            // Media Viewer Box Crop Section (OFF by default)
            StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_crop_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_crop_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_crop_overlay),
                        description = stringResource(R.string.toggle_crop_overlay_desc),
                        icon = Icons.Rounded.Crop,
                        checked = settings.enableCropOverlay,
                        onCheckedChange = onToggleCropOverlay,
                        testTag = "setting_switch_crop_overlay"
                    )

                    AnimatedVisibility(visible = settings.enableCropOverlay) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = studioColors.hairlineBorder)
                            SwitchSettingRow(
                                title = stringResource(R.string.toggle_inline_crop),
                                description = stringResource(R.string.toggle_inline_crop_desc),
                                icon = Icons.Rounded.TouchApp,
                                checked = settings.enableInlineCrop,
                                onCheckedChange = onToggleInlineCrop,
                                testTag = "setting_switch_inline_crop"
                            )
                            HorizontalDivider(color = studioColors.hairlineBorder)
                            SwitchSettingRow(
                                title = stringResource(R.string.toggle_fullscreen_crop),
                                description = stringResource(R.string.toggle_fullscreen_crop_desc),
                                icon = Icons.Rounded.OpenInFull,
                                checked = settings.enableFullscreenCrop,
                                onCheckedChange = onToggleFullscreenCrop,
                                testTag = "setting_switch_fullscreen_crop"
                            )
                        }
                    }

                    HorizontalDivider(color = studioColors.hairlineBorder)
                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_kv_precision_crop),
                        description = stringResource(R.string.toggle_kv_precision_crop_desc),
                        icon = Icons.Rounded.FilterNone,
                        checked = settings.useKvPlayerInPrecisionCrop,
                        onCheckedChange = onToggleUseKvPlayerInPrecisionCrop,
                        testTag = "setting_switch_kv_precision_crop"
                    )

                    AnimatedVisibility(visible = settings.useKvPlayerInPrecisionCrop) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = studioColors.hairlineBorder)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Minimizable Panel Style (Drawer, Floating & Side)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Choose between Bottom Drawer → Pill Bar, Floating & Movable card, or docked Minimizable Side Panel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PrecisionCropPanelStyle.entries.forEach { style ->
                                        FilterChip(
                                            selected = settings.precisionCropPanelStyle == style,
                                            onClick = { onChangePrecisionCropPanelStyle(style) },
                                            label = { Text(style.label) },
                                            modifier = Modifier.testTag("setting_crop_panel_style_${style.id}")
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = studioColors.hairlineBorder)
                            SwitchSettingRow(
                                title = stringResource(R.string.toggle_kv_crop_full_hud),
                                description = stringResource(R.string.toggle_kv_crop_full_hud_desc),
                                icon = Icons.Rounded.Tune,
                                checked = settings.precisionCropFullKvHud,
                                onCheckedChange = onTogglePrecisionCropFullKvHud,
                                testTag = "setting_switch_kv_crop_full_hud"
                            )
                        }
                    }

                    HorizontalDivider(color = studioColors.hairlineBorder)
                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_studio_mark_in_out),
                        description = stringResource(R.string.toggle_studio_mark_in_out_desc),
                        icon = Icons.Rounded.ContentCut,
                        checked = settings.enableStudioMarkInOut,
                        onCheckedChange = onToggleStudioMarkInOut,
                        testTag = "setting_switch_studio_mark_in_out"
                    )
                }
            }

            // Kvplayer HUD & Controls Section (All OFF by default)
            StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_kvplayer_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_kvplayer_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_kv_fullscreen),
                        description = stringResource(R.string.toggle_kv_fullscreen_desc),
                        icon = Icons.Rounded.Fullscreen,
                        checked = settings.kvShowFullscreenButton,
                        onCheckedChange = onToggleKvFullscreen,
                        testTag = "setting_switch_kv_fullscreen"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_kv_screenshot),
                        description = stringResource(R.string.toggle_kv_screenshot_desc),
                        icon = Icons.Rounded.PhotoCamera,
                        checked = settings.kvShowScreenshotButton,
                        onCheckedChange = onToggleKvScreenshot,
                        testTag = "setting_switch_kv_screenshot"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_kv_hide_hud_ui),
                        description = stringResource(R.string.toggle_kv_hide_hud_ui_desc),
                        icon = Icons.Rounded.VisibilityOff,
                        checked = settings.kvShowHideHudUiButton,
                        onCheckedChange = onToggleKvHideHudUi,
                        testTag = "setting_switch_kv_hide_ui"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_kv_mark_in_out),
                        description = stringResource(R.string.toggle_kv_mark_in_out_desc),
                        icon = Icons.Rounded.ContentCut,
                        checked = settings.kvShowMarkInOut,
                        onCheckedChange = onToggleKvMarkInOut,
                        testTag = "setting_switch_kv_mark_in_out"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_kv_floating),
                        description = stringResource(R.string.toggle_kv_floating_desc),
                        icon = Icons.Rounded.FilterNone,
                        checked = settings.kvShowFloatingButton,
                        onCheckedChange = onToggleKvFloating,
                        testTag = "setting_switch_kv_floating"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_kv_hide_bottom_tiles),
                        description = stringResource(R.string.toggle_kv_hide_bottom_tiles_desc),
                        icon = Icons.Rounded.VisibilityOff,
                        checked = settings.kvHideBottomTiles,
                        onCheckedChange = onToggleKvHideBottomTiles,
                        testTag = "setting_switch_kv_hide_bottom_tiles"
                    )
                }
            }

            // Lightweight & Speed Section (All OFF by default)
            StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_perf_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = stringResource(R.string.settings_perf_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_lightweight_engine),
                        description = stringResource(R.string.toggle_lightweight_engine_desc),
                        icon = Icons.Rounded.Bolt,
                        checked = settings.lightweightEngineMode,
                        onCheckedChange = onToggleLightweightEngine,
                        testTag = "setting_switch_lightweight_engine"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_low_overhead_ui),
                        description = stringResource(R.string.toggle_low_overhead_ui_desc),
                        icon = Icons.Rounded.Speed,
                        checked = settings.lowOverheadUi,
                        onCheckedChange = onToggleLowOverheadUi,
                        testTag = "setting_switch_low_overhead_ui"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_auto_prune_cache),
                        description = stringResource(R.string.toggle_auto_prune_cache_desc),
                        icon = Icons.Rounded.CleaningServices,
                        checked = settings.autoPruneTempCache,
                        onCheckedChange = onToggleAutoPruneCache,
                        testTag = "setting_switch_auto_prune"
                    )
                }
            }

            // Engine & Output Preferences
            StudioSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_engine_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_auto_gallery),
                        description = stringResource(R.string.toggle_auto_gallery_desc),
                        icon = Icons.Rounded.SaveAlt,
                        checked = settings.autoExportToGallery,
                        onCheckedChange = onToggleAutoExport,
                        testTag = "setting_switch_auto_export"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_keep_screen_on),
                        description = stringResource(R.string.toggle_keep_screen_on_desc),
                        icon = Icons.Rounded.ScreenLockPortrait,
                        checked = settings.keepScreenOn,
                        onCheckedChange = onToggleKeepScreenOn,
                        testTag = "setting_switch_keep_screen_on"
                    )
                    HorizontalDivider(color = studioColors.hairlineBorder)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = "FFmpeg CPU Worker Threads",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Adjust thread allocation for your tablet or phone SoC",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0, 2, 4, 6, 8).forEach { count ->
                                FilterChip(
                                    selected = settings.hardwareThreads == count,
                                    onClick = { onChangeHardwareThreads(count) },
                                    label = { Text(if (count == 0) "Auto (Optimal)" else "$count Threads") }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = studioColors.hairlineBorder)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val autoWorkers = com.example.service.ConversionForegroundService.computeSmartWorkerCount(
                            limitSetting = 0,
                            lightweightMode = settings.lightweightEngineMode
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewStream,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = "Smart Parallel Converting (Up to 4)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Smart Auto resolves to ${autoWorkers}x concurrent jobs on this device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0, 1, 2, 3, 4).forEach { limit ->
                                FilterChip(
                                    selected = settings.smartParallelLimit == limit,
                                    onClick = { onChangeSmartParallelLimit(limit) },
                                    label = { Text(if (limit == 0) "Smart Auto (1–4)" else "${limit}x Jobs") },
                                    modifier = Modifier.testTag("setting_parallel_${limit}")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = studioColors.hairlineBorder)

                    SwitchSettingRow(
                        title = stringResource(R.string.toggle_batch_finish_sound),
                        description = stringResource(R.string.toggle_batch_finish_sound_desc),
                        icon = Icons.Rounded.NotificationsActive,
                        checked = settings.playBatchFinishSound,
                        onCheckedChange = onToggleBatchFinishSound,
                        testTag = "setting_switch_batch_sound"
                    )
                }
            }

            // Offline Privacy Guarantee Card
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
                        modifier = Modifier.size(32.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "100% Private On-Device Processing",
                            style = MaterialTheme.typography.titleSmall,
                            color = studioColors.success
                        )
                        Text(
                            text = "Konvert Video bundles FFmpeg locally with zero internet permissions, zero cloud servers, and zero external codec downloads.",
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
private fun TierCheckboxRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            } else null
        )
    }
}
