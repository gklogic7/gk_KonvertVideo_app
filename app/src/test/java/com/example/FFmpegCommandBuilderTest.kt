package com.example

import com.example.domain.model.AudioArtworkSelection
import com.example.domain.model.AudioCodecOption
import com.example.domain.model.ContainerFormat
import com.example.domain.model.ConversionConfig
import com.example.domain.model.CropAspectMode
import com.example.domain.model.ResolutionOption
import com.example.domain.model.StudioCoverIcon
import com.example.domain.model.TextOverlayConfig
import com.example.domain.model.VideoCodecOption
import com.example.domain.model.VideoCropRegion
import com.example.domain.model.VideoOverlayConfig
import com.example.domain.model.VideoRateControlMode
import com.example.ffmpeg.FFmpegCommandBuilder
import com.example.ffmpeg.PresetCatalog
import com.example.ui.components.CropDragTarget
import com.example.ui.components.CropGeometry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegCommandBuilderTest {

    @Test
    fun buildsH265WithEvenScalingAndFastStart() {
        val config = ConversionConfig(
            container = ContainerFormat.MP4,
            videoCodec = VideoCodecOption.H265,
            audioCodec = AudioCodecOption.AAC,
            resolution = ResolutionOption.HD_720,
            rateControlMode = VideoRateControlMode.CRF,
            crf = 25,
            audioBitrateKbps = 160,
            fps = 30,
            speedPreset = "fast"
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/in/sample video.mov",
            outputPath = "/out/result.mp4",
            config = config,
            hardwareThreads = 4
        )
        assertArrayEquals(
            arrayOf(
                "-y", "-i", "/in/sample video.mov",
                "-threads", "4",
                "-c:v", "libx265",
                "-preset", "fast",
                "-crf", "25",
                "-tag:v", "hvc1",
                "-pix_fmt", "yuv420p",
                "-vf", "scale='min(1280,iw)':-2",
                "-r", "30",
                "-c:a", "aac",
                "-b:a", "160k",
                "-movflags", "+faststart",
                "/out/result.mp4"
            ),
            args
        )
    }

    @Test
    fun buildsTargetBitrateVideoArgsWhenBitrateModeSelected() {
        val config = ConversionConfig(
            container = ContainerFormat.MP4,
            videoCodec = VideoCodecOption.H264,
            audioCodec = AudioCodecOption.AAC,
            rateControlMode = VideoRateControlMode.BITRATE,
            videoBitrateKbps = 4000,
            audioBitrateKbps = 192
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/in/clip.mp4",
            outputPath = "/out/clip_br.mp4",
            config = config
        )
        assertTrue(args.contains("-b:v"))
        assertTrue(args.contains("4000k"))
        assertTrue(args.contains("-maxrate"))
        assertTrue(!args.contains("-crf"))
    }

    @Test
    fun buildsAudioOnlyMp3WithEmbeddedCoverArt() {
        val config = ConversionConfig(
            container = ContainerFormat.MP3,
            videoCodec = VideoCodecOption.NONE,
            audioCodec = AudioCodecOption.MP3,
            audioBitrateKbps = 320,
            audioArtwork = AudioArtworkSelection.StudioIcon(StudioCoverIcon.VINYL_DISC)
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/in/clip.mp4",
            outputPath = "/out/audio.mp3",
            config = config,
            artworkPath = "/cache/artwork/cover.jpg"
        )
        assertTrue(args.contains("/cache/artwork/cover.jpg"))
        assertTrue(args.contains("attached_pic"))
        assertTrue(args.contains("-id3v2_version"))
        assertTrue(args.contains("libmp3lame"))
        assertTrue(args.contains("320k"))
    }

    @Test
    fun buildsAudioOnlyMp3WithoutVideoTrackWhenNoArtwork() {
        val config = ConversionConfig(
            container = ContainerFormat.MP3,
            videoCodec = VideoCodecOption.NONE,
            audioCodec = AudioCodecOption.MP3,
            audioBitrateKbps = 256
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/in/clip.mp4",
            outputPath = "/out/audio.mp3",
            config = config
        )
        assertTrue(args.contains("-vn"))
        assertTrue(args.contains("libmp3lame"))
        assertTrue(args.contains("256k"))
    }

    @Test
    fun tokenizesQuotedCustomWorkbenchCommandWithPlaceholders() {
        val config = ConversionConfig(
            customCommandArgs = "-y -i \"{input}\" -vf \"scale=640:-2\" -c:v libxvid \"{output}\"",
            useCustomCommand = true
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/storage/my clip.mkv",
            outputPath = "/storage/out clip.avi",
            config = config
        )
        assertEquals("/storage/my clip.mkv", args[2])
        assertEquals("scale=640:-2", args[4])
        assertEquals("/storage/out clip.avi", args.last())
    }

    @Test
    fun presetCatalogCoversRequestedContainersAndCodecs() {
        assertTrue(PresetCatalog.compatibleVideoCodecs(ContainerFormat.OGG).contains(VideoCodecOption.THEORA))
        assertTrue(PresetCatalog.compatibleAudioCodecs(ContainerFormat.THREE_GP).contains(AudioCodecOption.AMR_WB))
        assertTrue(PresetCatalog.compatibleVideoCodecs(ContainerFormat.WEBM).contains(VideoCodecOption.VP9))
    }

    @Test
    fun buildsVideoCropFilterBeforeScaleWhenCropActive() {
        val config = ConversionConfig(
            container = ContainerFormat.MP4,
            videoCodec = VideoCodecOption.H264,
            audioCodec = AudioCodecOption.AAC,
            resolution = ResolutionOption.HD_720,
            cropRegion = VideoCropRegion(
                enabled = true,
                aspectMode = CropAspectMode.PORTRAIT_9_16,
                left = 0.25f,
                top = 0.05f,
                right = 0.75f,
                bottom = 0.95f
            )
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/in/wide.mp4",
            outputPath = "/out/vertical.mp4",
            config = config
        )
        val vfIndex = args.indexOf("-vf")
        assertTrue(vfIndex >= 0)
        val filterArg = args[vfIndex + 1]
        assertEquals(
            "crop=trunc((iw*0.5000)/2)*2:trunc((ih*0.9000)/2)*2:trunc((iw*0.2500)/2)*2:trunc((ih*0.0500)/2)*2,scale='min(1280,iw)':-2",
            filterArg
        )
    }

    @Test
    fun cropGeometryMaintainsAspectRatioAndClampsWithinFrame() {
        val videoAspect = 16f / 9f
        val portrait = CropGeometry.fitAspectCropRegion(CropAspectMode.PORTRAIT_9_16, videoAspect)
        assertTrue(portrait.enabled)
        val actualPortraitRatio = (portrait.widthFraction * videoAspect) / portrait.heightFraction
        assertEquals(9f / 16f, actualPortraitRatio, 0.01f)

        val square = CropGeometry.fitAspectCropRegion(CropAspectMode.SQUARE_1_1, videoAspect)
        val resized = CropGeometry.resizeCropCorner(
            crop = square,
            corner = CropDragTarget.BOTTOM_RIGHT,
            deltaXNorm = -0.15f,
            deltaYNorm = -0.15f,
            videoAspectRatio = videoAspect
        )
        val resizedRatio = (resized.widthFraction * videoAspect) / resized.heightFraction
        assertEquals(1f, resizedRatio, 0.01f)
        assertTrue(resized.left >= 0f && resized.right <= 1f)
        assertTrue(resized.top >= 0f && resized.bottom <= 1f)
    }

    @Test
    fun emitsTrimStartAndEndArgumentsWhenSetInConfig() {
        val config = ConversionConfig(
            container = ContainerFormat.MP4,
            videoCodec = VideoCodecOption.H264,
            audioCodec = AudioCodecOption.AAC,
            trimStartMs = 4500L,
            trimEndMs = 19250L
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/in/video.mp4",
            outputPath = "/out/trimmed.mp4",
            config = config
        )
        val ssIdx = args.indexOf("-ss")
        val toIdx = args.indexOf("-to")
        assertTrue(ssIdx >= 0 && toIdx > ssIdx)
        assertEquals("4.500", args[ssIdx + 1])
        assertEquals("19.250", args[toIdx + 1])
    }

    @Test
    fun emitsCustomSliderWidthAndFpsArguments() {
        val config = ConversionConfig(
            container = ContainerFormat.MP4,
            videoCodec = VideoCodecOption.H264,
            audioCodec = AudioCodecOption.AAC,
            customResolutionWidth = 1440,
            fps = 48
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/in/clip.mp4",
            outputPath = "/out/scaled.mp4",
            config = config
        )
        val vfIdx = args.indexOf("-vf")
        assertTrue(vfIdx >= 0)
        assertEquals("scale='min(1440,iw)':-2", args[vfIdx + 1])
        val rIdx = args.indexOf("-r")
        assertTrue(rIdx >= 0)
        assertEquals("48", args[rIdx + 1])
    }

    @Test
    fun buildsFilterComplexOverlayWithCropAndScale() {
        val config = ConversionConfig(
            container = ContainerFormat.MP4,
            videoCodec = VideoCodecOption.H264,
            audioCodec = AudioCodecOption.AAC,
            customResolutionWidth = 1280,
            cropRegion = VideoCropRegion(
                enabled = true,
                aspectMode = CropAspectMode.SQUARE_1_1,
                left = 0.20f,
                top = 0.10f,
                right = 0.80f,
                bottom = 0.90f
            ),
            overlayConfig = VideoOverlayConfig(
                textBox = TextOverlayConfig(text = "kvect studio")
            )
        )
        val args = FFmpegCommandBuilder.buildExecutionArguments(
            inputPath = "/in/src.mp4",
            outputPath = "/out/branded.mp4",
            config = config,
            overlayPath = "/cache/overlays/overlay_1.png"
        )
        assertTrue(args.contains("/cache/overlays/overlay_1.png"))
        assertTrue(!args.contains("-vf"))
        val fcIdx = args.indexOf("-filter_complex")
        assertTrue(fcIdx >= 0)
        val fc = args[fcIdx + 1]
        assertTrue(fc.contains("crop=trunc((iw*0.6000)/2)*2"))
        assertTrue(fc.contains("scale='min(1280,iw)':-2[base]"))
        assertTrue(fc.contains("[1:v][base]scale2ref[ov][base2];[base2][ov]overlay=0:0:format=auto[vout]"))
        assertTrue(args.contains("-map"))
        assertTrue(args.contains("[vout]"))
        assertTrue(args.contains("0:a?"))
    }
}
