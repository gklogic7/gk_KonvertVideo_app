package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BrandingWatermark
import androidx.compose.material.icons.automirrored.rounded.BrandingWatermark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.JobStatus
import com.example.domain.model.KamakerBackdrop
import com.example.service.KvPlayerAudioService
import com.example.ui.components.KvFloatingMiniPlayer
import com.example.ui.components.findActivity
import com.example.ui.screens.ConvertStudioScreen
import com.example.ui.screens.CreditsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.KamakerScreen
import com.example.ui.screens.KvPlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.state.KonvertViewModel
import com.example.ui.theme.KonvertVideoTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed interface KonvertRoute {
    @Serializable data object Studio : KonvertRoute
    @Serializable data object KvPlayer : KonvertRoute
    @Serializable data object History : KonvertRoute
    @Serializable data object Settings : KonvertRoute
    @Serializable data object Credits : KonvertRoute
    @Serializable data object Kamaker : KonvertRoute
}

private enum class TopDestination(
    val route: KonvertRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    STUDIO(
        route = KonvertRoute.Studio,
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Rounded.VideoSettings,
        unselectedIcon = Icons.Outlined.VideoSettings,
        testTag = "nav_studio"
    ),
    KVPLAYER(
        route = KonvertRoute.KvPlayer,
        labelRes = R.string.nav_kvplayer,
        selectedIcon = Icons.Rounded.PlayCircle,
        unselectedIcon = Icons.Outlined.PlayCircle,
        testTag = "nav_kvplayer"
    ),
    HISTORY(
        route = KonvertRoute.History,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    ),
    SETTINGS(
        route = KonvertRoute.Settings,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    ),
    CREDITS(
        route = KonvertRoute.Credits,
        labelRes = R.string.nav_credits,
        selectedIcon = Icons.Rounded.Verified,
        unselectedIcon = Icons.Outlined.Verified,
        testTag = "nav_credits"
    ),
    KAMAKER(
        route = KonvertRoute.Kamaker,
        labelRes = R.string.nav_kamaker,
        selectedIcon = Icons.AutoMirrored.Rounded.BrandingWatermark,
        unselectedIcon = Icons.AutoMirrored.Outlined.BrandingWatermark,
        testTag = "nav_kamaker"
    )
}

class MainActivity : ComponentActivity() {
    private var openKvPlayerSignal by mutableIntStateOf(0)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(KvPlayerAudioService.EXTRA_OPEN_KVPLAYER, false) == true) {
            openKvPlayerSignal++
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            KonvertVideoTheme {
                KonvertRootApp(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    openKvPlayerSignal = openKvPlayerSignal
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(KvPlayerAudioService.EXTRA_OPEN_KVPLAYER, false)) {
            openKvPlayerSignal++
        }
    }

    override fun onStart() {
        super.onStart()
        (application as KonvertApplication).kvPlayerController.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        (application as KonvertApplication).kvPlayerController.onAppBackgrounded(isInPipMode = inPip)
    }
}

@Composable
private fun rememberIsInPipMode(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return false
    var inPip by remember(activity) { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

@Composable
private fun KonvertRootApp(
    widthSizeClass: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass,
    openKvPlayerSignal: Int,
    viewModel: KonvertViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isInPipMode = rememberIsInPipMode()

    val selectedInputs by viewModel.selectedInputs.collectAsStateWithLifecycle()
    val activePreviewIndex by viewModel.activePreviewIndex.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
    val queueJobs by viewModel.queueJobs.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val engineInfo by viewModel.engineInfo.collectAsStateWithLifecycle()
    val kvPlayerState by viewModel.kvPlayerState.collectAsStateWithLifecycle()
    val kamakerSpec by viewModel.kamakerSpec.collectAsStateWithLifecycle()
    val customKamakerPresets by viewModel.customKamakerPresets.collectAsStateWithLifecycle()
    val kamakerBackdropBitmap by viewModel.kamakerBackdropBitmap.collectAsStateWithLifecycle()
    val kamakerBatchImages by viewModel.kamakerBatchImages.collectAsStateWithLifecycle()
    val kamakerBatchExportState by viewModel.kamakerBatchExportState.collectAsStateWithLifecycle()
    val kamakerRenamePrefix by viewModel.kamakerRenamePrefix.collectAsStateWithLifecycle()
    val kamakerExportFormat by viewModel.kamakerExportFormat.collectAsStateWithLifecycle()

    LaunchedEffect(kvPlayerState.currentTrack?.id, kamakerSpec.boardBackdrop) {
        if (kamakerSpec.boardBackdrop == KamakerBackdrop.CURRENT_VIDEO_FRAME) {
            viewModel.refreshKamakerBackdropBitmap()
        }
    }

    LaunchedEffect(openKvPlayerSignal) {
        if (openKvPlayerSignal > 0) {
            navController.navigate(KonvertRoute.KvPlayer) {
                launchSingleTop = true
            }
        }
    }

    val enterPip: (Int, Int) -> Unit = { videoW, videoH ->
        val act = context.findActivity()
        if (act != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            act.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            val rawRatio = if (videoW > 0 && videoH > 0) {
                videoW.toFloat() / videoH.toFloat()
            } else {
                16f / 9f
            }
            val clamped = rawRatio.coerceIn(0.42f, 2.38f)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
                .build()
            runCatching { act.enterPictureInPictureMode(params) }
                .onFailure {
                    scope.launch { snackbarHostState.showSnackbar("Picture-in-Picture unavailable right now.") }
                }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Picture-in-Picture is not supported on this device.") }
        }
    }

    if (isInPipMode) {
        KvPlayerScreen(
            widthSizeClass = widthSizeClass,
            controller = viewModel.kvPlayerController,
            uiState = kvPlayerState,
            settings = settings,
            studioInputs = selectedInputs,
            historyItems = historyItems,
            isInPipMode = true,
            reducedMotion = settings.lowOverheadUi,
            onLoadStudioClips = { viewModel.loadStudioIntoKvPlayer() },
            onLoadHistoryClips = { viewModel.loadHistoryIntoKvPlayer() },
            onEnterPip = enterPip,
            onTakeScreenshot = viewModel::captureKvPlayerScreenshot,
            onSendTrimToStudio = {},
            kamakerSpec = kamakerSpec,
            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
        return
    }

    var openQueueSignal by rememberSaveable { mutableIntStateOf(0) }
    val navigateToQueueSubtab = {
        openQueueSignal++
        navController.navigate(KonvertRoute.History) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startBatchConversion {
            navigateToQueueSubtab()
        }
    }

    val startConversionFlow = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startBatchConversion {
                navigateToQueueSubtab()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val activeQueueCount = queueJobs.count { it.status == JobStatus.RUNNING || it.status == JobStatus.QUEUED }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo).let { defaultType ->
        if (widthSizeClass != androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact) {
            NavigationSuiteType.NavigationRail
        } else {
            defaultType
        }
    }

    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
            navigationRailContainerColor = MaterialTheme.colorScheme.surface
        ),
        containerColor = MaterialTheme.colorScheme.background,
        navigationSuiteItems = {
            TopDestination.entries.forEach { dest ->
                val selected = currentDestination?.hasRoute(dest.route::class) == true
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        val iconVec = if (selected) dest.selectedIcon else dest.unselectedIcon
                        if (dest == TopDestination.HISTORY && activeQueueCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ) {
                                        Text("$activeQueueCount")
                                    }
                                }
                            ) {
                                Icon(iconVec, contentDescription = stringResource(dest.labelRes))
                            }
                        } else {
                            Icon(iconVec, contentDescription = stringResource(dest.labelRes))
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(dest.labelRes),
                            fontSize = if (layoutType == NavigationSuiteType.NavigationBar) 10.sp else 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = itemColors,
                    modifier = Modifier.testTag(dest.testTag)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF131B29),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = KonvertRoute.Studio,
                    enterTransition = {
                        fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                    }
                ) {
                    composable<KonvertRoute.Studio> {
                        ConvertStudioScreen(
                            widthSizeClass = widthSizeClass,
                            selectedInputs = selectedInputs,
                            activePreviewIndex = activePreviewIndex,
                            config = config,
                            settings = settings,
                            customPresets = customPresets,
                            onAddUris = viewModel::addPickedUris,
                            onRemoveInput = viewModel::removeInput,
                            onClearInputs = viewModel::clearInputs,
                            onSelectPreviewIndex = viewModel::selectPreviewIndex,
                            onApplyPreset = viewModel::applyPreset,
                            onUpdateContainer = viewModel::updateContainer,
                            onUpdateVideoCodec = viewModel::updateVideoCodec,
                            onUpdateAudioCodec = viewModel::updateAudioCodec,
                            onUpdateResolution = viewModel::updateResolution,
                            onUpdateCustomResolutionWidth = viewModel::updateCustomResolutionWidth,
                            onUpdateRateControlMode = viewModel::updateRateControlMode,
                            onUpdateCrf = viewModel::updateCrf,
                            onUpdateVideoBitrateKbps = viewModel::updateVideoBitrateKbps,
                            onUpdateAudioBitrate = viewModel::updateAudioBitrate,
                            onUpdateFps = viewModel::updateFps,
                            onUpdateSpeedPreset = viewModel::updateSpeedPreset,
                            onUpdateAudioArtwork = viewModel::updateAudioArtwork,
                            onUpdateCropRegion = viewModel::updateCropRegion,
                            onResetCropRegion = viewModel::resetCropRegion,
                            onUpdateCropAndOverlay = viewModel::updateCropAndOverlay,
                            onClearOverlays = viewModel::clearOverlays,
                            onUpdateTrimRange = viewModel::updateTrimRange,
                            onClearTrim = viewModel::clearTrimRange,
                            onUpdateCustomCommand = viewModel::updateCustomCommand,
                            onResetCommandFromControls = viewModel::resetCommandFromControls,
                            onSaveCustomPreset = viewModel::saveCurrentAsPreset,
                            onStartConversion = startConversionFlow,
                            onOpenSettings = {
                                navController.navigate(KonvertRoute.Settings) { launchSingleTop = true }
                            },
                            onChangePrecisionCropPanelStyle = viewModel::setPrecisionCropPanelStyle,
                            onTogglePrecisionCropFullKvHud = viewModel::setPrecisionCropFullKvHud,
                            kamakerSpec = kamakerSpec,
                            onToggleApplyKamakerInStudio = viewModel::setApplyKamakerInStudio,
                            onOpenKamaker = {
                                navController.navigate(KonvertRoute.Kamaker) { launchSingleTop = true }
                            }
                        )
                    }

                    composable<KonvertRoute.KvPlayer> {
                        KvPlayerScreen(
                            widthSizeClass = widthSizeClass,
                            controller = viewModel.kvPlayerController,
                            uiState = kvPlayerState,
                            settings = settings,
                            studioInputs = selectedInputs,
                            historyItems = historyItems,
                            isInPipMode = false,
                            reducedMotion = settings.lowOverheadUi,
                            onLoadStudioClips = { viewModel.loadStudioIntoKvPlayer() },
                            onLoadHistoryClips = { viewModel.loadHistoryIntoKvPlayer() },
                            onEnterPip = enterPip,
                            onTakeScreenshot = viewModel::captureKvPlayerScreenshot,
                            onSendTrimToStudio = {
                                viewModel.sendKvPlayerTrimToStudio {
                                    navController.navigate(KonvertRoute.Studio) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onToggleHideBottomTiles = viewModel::setKvHideBottomTiles,
                            kamakerSpec = kamakerSpec,
                            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                    }

                    composable<KonvertRoute.History> {
                        val workerCap = remember(settings.smartParallelLimit, settings.lightweightEngineMode) {
                            com.example.service.ConversionForegroundService.computeSmartWorkerCount(
                                limitSetting = settings.smartParallelLimit,
                                lightweightMode = settings.lightweightEngineMode
                            )
                        }
                        HistoryScreen(
                            widthSizeClass = widthSizeClass,
                            items = historyItems,
                            onDeleteRecord = viewModel::deleteHistoryRecord,
                            onClearAll = viewModel::clearAllHistory,
                            onReuseConfig = { record ->
                                viewModel.reuseHistoryConfig(record) {
                                    navController.navigate(KonvertRoute.Studio) { launchSingleTop = true }
                                }
                            },
                            onPlayInKvPlayer = { record ->
                                viewModel.playHistoryRecordInKvPlayer(record) {
                                    navController.navigate(KonvertRoute.KvPlayer) { launchSingleTop = true }
                                }
                            },
                            onPlayMultipleInKvPlayer = { records ->
                                viewModel.playMultipleHistoryInKvPlayer(records) {
                                    navController.navigate(KonvertRoute.KvPlayer) { launchSingleTop = true }
                                }
                            },
                            onSendMultipleToStudio = { records ->
                                viewModel.sendMultipleHistoryToStudio(records) {
                                    navController.navigate(KonvertRoute.Studio) { launchSingleTop = true }
                                }
                            },
                            onDeleteMultiple = viewModel::deleteMultipleHistoryRecords,
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            openQueueSignal = openQueueSignal,
                            queueJobs = queueJobs,
                            keepScreenOn = settings.keepScreenOn,
                            parallelWorkers = workerCap,
                            onCancelJob = viewModel::cancelJob,
                            onCancelAll = viewModel::cancelAllActive,
                            onClearFinished = viewModel::clearFinishedJobs
                        )
                    }

                    composable<KonvertRoute.Settings> {
                        SettingsScreen(
                            widthSizeClass = widthSizeClass,
                            settings = settings,
                            onToggleQuickPresets = viewModel::setShowQuickPresets,
                            onToggleProControls = viewModel::setShowProControls,
                            onToggleCommandWorkbench = viewModel::setShowCommandWorkbench,
                            onToggleAutoExport = viewModel::setAutoExportToGallery,
                            onToggleKeepScreenOn = viewModel::setKeepScreenOn,
                            onChangeHardwareThreads = viewModel::setHardwareThreads,
                            onToggleLightweightEngine = viewModel::setLightweightEngineMode,
                            onToggleLowOverheadUi = viewModel::setLowOverheadUi,
                            onToggleAutoPruneCache = viewModel::setAutoPruneTempCache,
                            onToggleCropOverlay = viewModel::setEnableCropOverlay,
                            onToggleInlineCrop = viewModel::setEnableInlineCrop,
                            onToggleFullscreenCrop = viewModel::setEnableFullscreenCrop,
                            onToggleStudioMarkInOut = viewModel::setEnableStudioMarkInOut,
                            onChangeSmartParallelLimit = viewModel::setSmartParallelLimit,
                            onToggleBatchFinishSound = viewModel::setPlayBatchFinishSound,
                            onToggleKvFullscreen = viewModel::setKvShowFullscreenButton,
                            onToggleKvScreenshot = viewModel::setKvShowScreenshotButton,
                            onToggleKvHideHudUi = viewModel::setKvShowHideHudUiButton,
                            onToggleKvMarkInOut = viewModel::setKvShowMarkInOut,
                            onToggleKvFloating = viewModel::setKvShowFloatingButton,
                            onToggleKvHideBottomTiles = viewModel::setKvHideBottomTiles,
                            onEnableAllModes = viewModel::enableAllStudioModes,
                            onToggleUseKvPlayerInPrecisionCrop = viewModel::setUseKvPlayerInPrecisionCrop,
                            onChangePrecisionCropPanelStyle = viewModel::setPrecisionCropPanelStyle,
                            onTogglePrecisionCropFullKvHud = viewModel::setPrecisionCropFullKvHud
                        )
                    }

                    composable<KonvertRoute.Credits> {
                        CreditsScreen(
                            widthSizeClass = widthSizeClass,
                            engineInfo = engineInfo,
                            onEnsureFormatsLoaded = viewModel::ensureEngineCapabilitiesLoaded,
                            onRefreshFormats = viewModel::refreshCapabilities,
                            onUseCodecInStudio = { entry ->
                                viewModel.applyCatalogCodecToStudio(entry) {
                                    navController.navigate(KonvertRoute.Studio) { launchSingleTop = true }
                                }
                            }
                        )
                    }

                    composable<KonvertRoute.Kamaker> {
                        KamakerScreen(
                            widthSizeClass = widthSizeClass,
                            spec = kamakerSpec,
                            customPresets = customKamakerPresets,
                            backdropBitmap = kamakerBackdropBitmap,
                            batchImages = kamakerBatchImages,
                            renamePrefix = kamakerRenamePrefix,
                            exportFormat = kamakerExportFormat,
                            batchExportState = kamakerBatchExportState,
                            onUpdateSpec = viewModel::updateKamakerSpec,
                            onUpdateShapeRule = viewModel::updateKamakerShapeRule,
                            onResetShape = viewModel::resetKamakerShape,
                            onCopyShapeToAll = viewModel::copyKamakerRuleToAllShapes,
                            onResetAllKamaker = viewModel::resetKamakerAll,
                            onApplyPreset = viewModel::applyKamakerPreset,
                            onSavePreset = viewModel::saveKamakerPreset,
                            onDeletePreset = viewModel::deleteKamakerPreset,
                            onRefreshBackdrop = viewModel::refreshKamakerBackdropBitmap,
                            onImportBatchImages = viewModel::importKamakerBatchImages,
                            onImportBatchFolder = viewModel::importKamakerBatchFolder,
                            onRemoveBatchImage = viewModel::removeKamakerBatchImage,
                            onClearBatchImages = viewModel::clearKamakerBatchImages,
                            onRenamePrefixChange = viewModel::setKamakerRenamePrefix,
                            onExportFormatChange = viewModel::setKamakerExportFormat,
                            onExportFiles = viewModel::exportKamakerBatchFiles,
                            onExportZip = { onReadyShare -> viewModel.exportKamakerBatchZip(onReadyShare) },
                            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                    }
                }

                if (kvPlayerState.isFloatingMode &&
                    settings.kvShowFloatingButton &&
                    kvPlayerState.currentTrack != null &&
                    !isInPipMode
                ) {
                    val floatingPlayer = remember(viewModel.kvPlayerController) {
                        viewModel.kvPlayerController.getOrCreatePlayer()
                    }
                    KvFloatingMiniPlayer(
                        exoPlayer = floatingPlayer,
                        uiState = kvPlayerState,
                        reducedMotion = settings.lowOverheadUi,
                        onTogglePlayPause = viewModel.kvPlayerController::togglePlayPause,
                        onSeekBy = viewModel.kvPlayerController::seekBy,
                        onExpandToKvPlayer = {
                            viewModel.kvPlayerController.setFloatingMode(false)
                            navController.navigate(KonvertRoute.KvPlayer) {
                                launchSingleTop = true
                            }
                        },
                        onEnterPip = {
                            viewModel.kvPlayerController.setFloatingMode(false)
                            enterPip(kvPlayerState.videoWidth, kvPlayerState.videoHeight)
                        },
                        onCloseFloating = {
                            viewModel.kvPlayerController.setFloatingMode(false)
                        }
                    )
                }
            }
        }
    }
}
