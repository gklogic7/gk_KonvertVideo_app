package com.example

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.KvPlayerAspectMode
import com.example.domain.model.KvPlayerSourceType
import com.example.domain.model.KvPlayerTrack
import com.example.player.KvPlayerController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KvPlayerTest {

    @Test
    fun cycleAspectModeTransitionsThroughAllModes() {
        val context = ApplicationProvider.getApplicationContext<KonvertApplication>()
        val controller = KvPlayerController(context)
        assertEquals(KvPlayerAspectMode.FIT, controller.uiState.value.aspectMode)
        controller.cycleAspectMode()
        assertEquals(KvPlayerAspectMode.FILL, controller.uiState.value.aspectMode)
        controller.cycleAspectMode()
        assertEquals(KvPlayerAspectMode.RATIO_16_9, controller.uiState.value.aspectMode)
        controller.cycleAspectMode()
        assertEquals(KvPlayerAspectMode.RATIO_4_3, controller.uiState.value.aspectMode)
        controller.cycleAspectMode()
        assertEquals(KvPlayerAspectMode.FIT, controller.uiState.value.aspectMode)
    }

    @Test
    fun playlistDeduplicationAndClearWorkSafely() {
        val context = ApplicationProvider.getApplicationContext<KonvertApplication>()
        val controller = KvPlayerController(context)
        val t1 = KvPlayerTrack(
            id = "1",
            uri = Uri.parse("content://media/external/video/media/101"),
            title = "demo.mkv",
            mimeType = "video/x-matroska",
            sourceType = KvPlayerSourceType.STUDIO
        )
        val t2 = KvPlayerTrack(
            id = "2",
            uri = Uri.parse("content://media/external/audio/media/202"),
            title = "track.mp3",
            mimeType = "audio/mpeg",
            isAudioOnly = true,
            sourceType = KvPlayerSourceType.HISTORY
        )
        controller.addTracks(listOf(t1, t2, t1), playFirstAdded = false)
        assertEquals(2, controller.uiState.value.playlist.size)

        controller.removeTrack("1")
        assertEquals(1, controller.uiState.value.playlist.size)
        assertEquals("track.mp3", controller.uiState.value.currentTrack?.title)

        controller.clearPlaylist()
        assertTrue(controller.uiState.value.playlist.isEmpty())
    }

    @Test
    fun newKvPlayerTogglesDefaultToFalseInStudioSettings() {
        val defaults = com.example.domain.model.StudioSettings()
        assertTrue(!defaults.kvShowFullscreenButton)
        assertTrue(!defaults.kvShowScreenshotButton)
        assertTrue(!defaults.kvShowHideHudUiButton)
        assertTrue(!defaults.kvShowMarkInOut)
        assertTrue(!defaults.kvShowFloatingButton)
    }

    @Test
    fun markInOutAndHideUiAndModesUpdateControllerStateCorrectly() {
        val context = ApplicationProvider.getApplicationContext<KonvertApplication>()
        val controller = KvPlayerController(context)
        val t1 = KvPlayerTrack(
            id = "clip1",
            uri = Uri.parse("content://media/external/video/media/303"),
            title = "sample.mp4",
            mimeType = "video/mp4",
            durationMs = 60_000L,
            sourceType = KvPlayerSourceType.STUDIO
        )
        controller.addTracks(listOf(t1), playFirstAdded = false)

        // Hide display/volume UI toggle
        assertTrue(!controller.uiState.value.hideDisplayVolumeUi)
        controller.toggleHideDisplayVolumeUi()
        assertTrue(controller.uiState.value.hideDisplayVolumeUi)

        // Mark In / Out + A-B loop
        controller.setAbLoopEnabled(true)
        assertTrue(!controller.uiState.value.isAbLoopEnabled) // cannot loop without valid range

        controller.setMarkIn(5_000L)
        controller.setMarkOut(15_000L)
        assertEquals(5_000L, controller.uiState.value.markInMs)
        assertEquals(15_000L, controller.uiState.value.markOutMs)
        assertTrue(controller.uiState.value.hasValidMarkRange)

        controller.setAbLoopEnabled(true)
        assertTrue(controller.uiState.value.isAbLoopEnabled)

        controller.clearMarkInOut()
        assertEquals(null, controller.uiState.value.markInMs)
        assertEquals(null, controller.uiState.value.markOutMs)
        assertTrue(!controller.uiState.value.isAbLoopEnabled)

        // Fullscreen & Floating mutual exclusion
        controller.setFullscreen(true)
        assertTrue(controller.uiState.value.isFullscreen)
        controller.setFloatingMode(true)
        assertTrue(controller.uiState.value.isFloatingMode)
        assertTrue(!controller.uiState.value.isFullscreen)
    }
}
