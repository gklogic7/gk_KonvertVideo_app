package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.KamakerPreset
import com.example.domain.model.KamakerSpec
import com.example.domain.model.PrecisionCropPanelStyle
import com.example.domain.model.StudioSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_settings")

private val kamakerJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SHOW_QUICK_PRESETS = booleanPreferencesKey("show_quick_presets")
        val SHOW_PRO_CONTROLS = booleanPreferencesKey("show_pro_controls")
        val SHOW_COMMAND_WORKBENCH = booleanPreferencesKey("show_command_workbench")
        val AUTO_EXPORT_GALLERY = booleanPreferencesKey("auto_export_gallery")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HARDWARE_THREADS = intPreferencesKey("hardware_threads")
        val LIGHTWEIGHT_ENGINE_MODE = booleanPreferencesKey("lightweight_engine_mode")
        val LOW_OVERHEAD_UI = booleanPreferencesKey("low_overhead_ui")
        val AUTO_PRUNE_TEMP_CACHE = booleanPreferencesKey("auto_prune_temp_cache")
        val ENABLE_CROP_OVERLAY = booleanPreferencesKey("enable_crop_overlay")
        val ENABLE_INLINE_CROP = booleanPreferencesKey("enable_inline_crop")
        val ENABLE_FULLSCREEN_CROP = booleanPreferencesKey("enable_fullscreen_crop")
        val USE_KVPLAYER_IN_PRECISION_CROP = booleanPreferencesKey("use_kvplayer_in_precision_crop")
        val PRECISION_CROP_PANEL_STYLE = stringPreferencesKey("precision_crop_panel_style")
        val PRECISION_CROP_FULL_KV_HUD = booleanPreferencesKey("precision_crop_full_kv_hud")
        val ENABLE_STUDIO_MARK_IN_OUT = booleanPreferencesKey("enable_studio_mark_in_out")
        val SMART_PARALLEL_LIMIT = intPreferencesKey("smart_parallel_limit")
        val PLAY_BATCH_FINISH_SOUND = booleanPreferencesKey("play_batch_finish_sound")
        val KV_SHOW_FULLSCREEN = booleanPreferencesKey("kv_show_fullscreen")
        val KV_SHOW_SCREENSHOT = booleanPreferencesKey("kv_show_screenshot")
        val KV_SHOW_HIDE_HUD_UI = booleanPreferencesKey("kv_show_hide_hud_ui")
        val KV_SHOW_MARK_IN_OUT = booleanPreferencesKey("kv_show_mark_in_out")
        val KV_SHOW_FLOATING = booleanPreferencesKey("kv_show_floating")
        val KV_HIDE_BOTTOM_TILES = booleanPreferencesKey("kv_hide_bottom_tiles")
        val KAMAKER_SPEC_JSON = stringPreferencesKey("kamaker_spec_json")
        val KAMAKER_PRESETS_JSON = stringPreferencesKey("kamaker_presets_json")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val styleId = prefs[Keys.PRECISION_CROP_PANEL_STYLE]
        val panelStyle = PrecisionCropPanelStyle.entries.firstOrNull { it.id == styleId }
            ?: PrecisionCropPanelStyle.BOTTOM_DRAWER
        StudioSettings(
            showQuickPresets = prefs[Keys.SHOW_QUICK_PRESETS] ?: true,
            showProControls = prefs[Keys.SHOW_PRO_CONTROLS] ?: true,
            showCommandWorkbench = prefs[Keys.SHOW_COMMAND_WORKBENCH] ?: true,
            autoExportToGallery = prefs[Keys.AUTO_EXPORT_GALLERY] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            hardwareThreads = prefs[Keys.HARDWARE_THREADS] ?: 0,
            lightweightEngineMode = prefs[Keys.LIGHTWEIGHT_ENGINE_MODE] ?: false,
            lowOverheadUi = prefs[Keys.LOW_OVERHEAD_UI] ?: false,
            autoPruneTempCache = prefs[Keys.AUTO_PRUNE_TEMP_CACHE] ?: false,
            enableCropOverlay = prefs[Keys.ENABLE_CROP_OVERLAY] ?: false,
            enableInlineCrop = prefs[Keys.ENABLE_INLINE_CROP] ?: true,
            enableFullscreenCrop = prefs[Keys.ENABLE_FULLSCREEN_CROP] ?: true,
            useKvPlayerInPrecisionCrop = prefs[Keys.USE_KVPLAYER_IN_PRECISION_CROP] ?: false,
            precisionCropPanelStyle = panelStyle,
            precisionCropFullKvHud = prefs[Keys.PRECISION_CROP_FULL_KV_HUD] ?: true,
            enableStudioMarkInOut = prefs[Keys.ENABLE_STUDIO_MARK_IN_OUT] ?: false,
            smartParallelLimit = prefs[Keys.SMART_PARALLEL_LIMIT] ?: 0,
            playBatchFinishSound = prefs[Keys.PLAY_BATCH_FINISH_SOUND] ?: true,
            kvShowFullscreenButton = prefs[Keys.KV_SHOW_FULLSCREEN] ?: false,
            kvShowScreenshotButton = prefs[Keys.KV_SHOW_SCREENSHOT] ?: false,
            kvShowHideHudUiButton = prefs[Keys.KV_SHOW_HIDE_HUD_UI] ?: false,
            kvShowMarkInOut = prefs[Keys.KV_SHOW_MARK_IN_OUT] ?: false,
            kvShowFloatingButton = prefs[Keys.KV_SHOW_FLOATING] ?: false,
            kvHideBottomTiles = prefs[Keys.KV_HIDE_BOTTOM_TILES] ?: false
        )
    }

    suspend fun setShowQuickPresets(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.SHOW_QUICK_PRESETS] = enabled }
    }

    suspend fun setShowProControls(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.SHOW_PRO_CONTROLS] = enabled }
    }

    suspend fun setShowCommandWorkbench(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.SHOW_COMMAND_WORKBENCH] = enabled }
    }

    suspend fun setAutoExportToGallery(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_EXPORT_GALLERY] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setHardwareThreads(threads: Int) {
        context.studioDataStore.edit { it[Keys.HARDWARE_THREADS] = threads.coerceIn(0, 16) }
    }

    suspend fun setLightweightEngineMode(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.LIGHTWEIGHT_ENGINE_MODE] = enabled }
    }

    suspend fun setLowOverheadUi(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.LOW_OVERHEAD_UI] = enabled }
    }

    suspend fun setAutoPruneTempCache(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_PRUNE_TEMP_CACHE] = enabled }
    }

    suspend fun setEnableCropOverlay(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.ENABLE_CROP_OVERLAY] = enabled }
    }

    suspend fun setEnableInlineCrop(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.ENABLE_INLINE_CROP] = enabled }
    }

    suspend fun setEnableFullscreenCrop(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.ENABLE_FULLSCREEN_CROP] = enabled }
    }

    suspend fun setUseKvPlayerInPrecisionCrop(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.USE_KVPLAYER_IN_PRECISION_CROP] = enabled }
    }

    suspend fun setPrecisionCropPanelStyle(style: PrecisionCropPanelStyle) {
        context.studioDataStore.edit { it[Keys.PRECISION_CROP_PANEL_STYLE] = style.id }
    }

    suspend fun setPrecisionCropFullKvHud(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.PRECISION_CROP_FULL_KV_HUD] = enabled }
    }

    suspend fun setEnableStudioMarkInOut(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.ENABLE_STUDIO_MARK_IN_OUT] = enabled }
    }

    suspend fun setSmartParallelLimit(limit: Int) {
        context.studioDataStore.edit { it[Keys.SMART_PARALLEL_LIMIT] = limit.coerceIn(0, 4) }
    }

    suspend fun setPlayBatchFinishSound(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.PLAY_BATCH_FINISH_SOUND] = enabled }
    }

    suspend fun setKvShowFullscreenButton(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.KV_SHOW_FULLSCREEN] = enabled }
    }

    suspend fun setKvShowScreenshotButton(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.KV_SHOW_SCREENSHOT] = enabled }
    }

    suspend fun setKvShowHideHudUiButton(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.KV_SHOW_HIDE_HUD_UI] = enabled }
    }

    suspend fun setKvShowMarkInOut(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.KV_SHOW_MARK_IN_OUT] = enabled }
    }

    suspend fun setKvShowFloatingButton(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.KV_SHOW_FLOATING] = enabled }
    }

    suspend fun setKvHideBottomTiles(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.KV_HIDE_BOTTOM_TILES] = enabled }
    }

    suspend fun enableAllStudioModes() {
        context.studioDataStore.edit {
            it[Keys.SHOW_QUICK_PRESETS] = true
            it[Keys.SHOW_PRO_CONTROLS] = true
            it[Keys.SHOW_COMMAND_WORKBENCH] = true
        }
    }

    val kamakerSpecFlow: Flow<KamakerSpec> = context.studioDataStore.data.map { prefs ->
        val raw = prefs[Keys.KAMAKER_SPEC_JSON]
        if (raw.isNullOrBlank()) {
            KamakerSpec()
        } else {
            runCatching { kamakerJson.decodeFromString<KamakerSpec>(raw) }
                .getOrElse { KamakerSpec() }
        }
    }

    val customKamakerPresetsFlow: Flow<List<KamakerPreset>> = context.studioDataStore.data.map { prefs ->
        val raw = prefs[Keys.KAMAKER_PRESETS_JSON]
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { kamakerJson.decodeFromString<List<KamakerPreset>>(raw) }
                .getOrElse { emptyList() }
        }
    }

    suspend fun saveKamakerSpec(spec: KamakerSpec) {
        val encoded = kamakerJson.encodeToString(spec)
        context.studioDataStore.edit { it[Keys.KAMAKER_SPEC_JSON] = encoded }
    }

    suspend fun saveCustomKamakerPresets(presets: List<KamakerPreset>) {
        val encoded = kamakerJson.encodeToString(presets)
        context.studioDataStore.edit { it[Keys.KAMAKER_PRESETS_JSON] = encoded }
    }
}

