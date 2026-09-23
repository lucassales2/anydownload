package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YtDlpArgumentsTest {

    private val root: Path = Path.of("/tmp/anydownlod-root")

    private fun request(options: DownloadOptions = DownloadOptions()) =
        DownloadRequest(sourceUrl = "https://example.com/watch?v=fixture", options = options)

    private fun build(options: DownloadOptions = DownloadOptions(), chapter: Path? = null) =
        YtDlpArguments.build(
            executable = "/usr/bin/yt-dlp",
            request = request(options),
            outputTemplatePath = root.resolve("%(title)s.%(ext)s"),
            chapterTemplatePath = chapter,
        )

    private fun List<String>.valueAfter(flag: String): String = this[indexOf(flag) + 1]

    @Test
    fun buildsAnArgumentListWithStableTemplatesAndNoShell() {
        val args = build()

        assertEquals("/usr/bin/yt-dlp", args.first())
        assertTrue(args.contains("--newline"))
        assertTrue(args.contains("--progress"))
        assertTrue(args.contains("--no-simulate"))
        assertTrue(args.contains("--no-overwrites"))
        assertTrue(args.contains(YtDlpArguments.DOWNLOAD_PROGRESS_TEMPLATE))
        assertTrue(args.contains(YtDlpArguments.POSTPROCESS_PROGRESS_TEMPLATE))
        assertTrue(args.contains(YtDlpArguments.TITLE_PRINT_TEMPLATE))
        assertTrue(args.contains(YtDlpArguments.FILE_PRINT_ARGUMENT))
        assertEquals("/tmp/anydownlod-root/%(title)s.%(ext)s", args.valueAfter("-o"))
        assertEquals("https://example.com/watch?v=fixture", args.last())
        assertTrue(args.none { it.contains("customYtDlp") })
        assertTrue(args.none { it.contains("&&") || it.contains(";") || it.contains("| sh") })
    }

    @Test
    fun videoProfilesAndCodecsProduceDifferentSelectors() {
        val auto = build(DownloadOptions(mediaType = MediaType.VIDEO))
        assertFalse(auto.contains("-f"))

        val mp4 = build(DownloadOptions(videoProfile = VideoContainerProfile.MP4))
        assertEquals("bv*[ext=mp4]+ba/b[ext=mp4]", mp4.valueAfter("-f"))
        assertEquals("mp4", mp4.valueAfter("--merge-output-format"))

        val ios = build(DownloadOptions(videoProfile = VideoContainerProfile.IOS_COMPATIBLE))
        assertEquals("bv*[ext=mp4][vcodec^=avc1]+ba/b[ext=mp4][vcodec^=avc1]", ios.valueAfter("-f"))

        val h264 = build(DownloadOptions(videoCodec = VideoCodec.H264))
        assertEquals("bv*[vcodec^=avc1]+ba/b[vcodec^=avc1]", h264.valueAfter("-f"))

        val hevc = build(DownloadOptions(videoCodec = VideoCodec.HEVC))
        assertEquals("bv*[vcodec~='^(hev1|hvc1)']+ba/b[vcodec~='^(hev1|hvc1)']", hevc.valueAfter("-f"))

        val av1 = build(DownloadOptions(videoCodec = VideoCodec.AV1))
        assertEquals("bv*[vcodec~='^av01']+ba/b[vcodec~='^av01']", av1.valueAfter("-f"))

        val vp9 = build(DownloadOptions(videoCodec = VideoCodec.VP9))
        assertEquals("bv*[vcodec~='^vp0?9']+ba/b[vcodec~='^vp0?9']", vp9.valueAfter("-f"))
    }

    @Test
    fun qualityAddsAnExactHeightAndWorstUsesTheWorstSelector() {
        val height = build(DownloadOptions(quality = QualityPreference.Resolution("1080")))
        assertEquals("bv*[height=1080]+ba/b[height=1080]", height.valueAfter("-f"))

        val combined = build(
            DownloadOptions(videoCodec = VideoCodec.H264, quality = QualityPreference.Resolution("720")),
        )
        assertEquals("bv*[vcodec^=avc1][height=720]+ba/b[vcodec^=avc1][height=720]", combined.valueAfter("-f"))

        val worst = build(DownloadOptions(quality = QualityPreference.Worst))
        assertEquals("wv*+wa/w", worst.valueAfter("-f"))
    }

    @Test
    fun audioUsesExtractionAndOnlyLossyContainersGetABitrate() {
        val mp3 = build(
            DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3, audioBitrate = "192"),
        )
        assertTrue(mp3.contains("-x"))
        assertEquals("mp3", mp3.valueAfter("--audio-format"))
        assertEquals("192K", mp3.valueAfter("--audio-quality"))

        val flac = build(
            DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.FLAC, audioBitrate = "320"),
        )
        assertEquals("flac", flac.valueAfter("--audio-format"))
        assertFalse(flac.contains("--audio-quality"))
    }

    @Test
    fun captionsOnlySelectThePreferenceLanguageAndFormat() {
        val both = build(
            DownloadOptions(
                mediaType = MediaType.CAPTIONS,
                captionLanguage = "pt-BR",
                captionPreference = CaptionPreference.EITHER,
                captionFormat = CaptionFormat.VTT,
            ),
        )
        assertTrue(both.contains("--skip-download"))
        assertTrue(both.contains("--write-subs"))
        assertTrue(both.contains("--write-auto-subs"))
        assertEquals("pt-BR", both.valueAfter("--sub-langs"))
        assertEquals("vtt", both.valueAfter("--sub-format"))

        val automatic = build(
            DownloadOptions(mediaType = MediaType.CAPTIONS, captionPreference = CaptionPreference.AUTOMATIC),
        )
        assertTrue(automatic.contains("--write-auto-subs"))
        assertFalse(automatic.contains("--write-subs"))

        val manual = build(
            DownloadOptions(mediaType = MediaType.CAPTIONS, captionPreference = CaptionPreference.MANUAL),
        )
        assertTrue(manual.contains("--write-subs"))
        assertFalse(manual.contains("--write-auto-subs"))
    }

    @Test
    fun thumbnailOnlySkipsMediaAndConvertsToJpg() {
        val args = build(DownloadOptions(mediaType = MediaType.THUMBNAIL))

        assertTrue(args.contains("--skip-download"))
        assertTrue(args.contains("--write-thumbnail"))
        assertEquals("jpg", args.valueAfter("--convert-thumbnails"))
        assertFalse(args.contains("-x"))
    }

    @Test
    fun sidecarsAndSponsorBlockFlags() {
        val args = build(
            DownloadOptions(
                mediaType = MediaType.VIDEO,
                embedSubtitles = true,
                captionLanguage = "en",
                writeMetadata = true,
                writeThumbnail = true,
                sponsorBlockRemove = true,
            ),
        )

        assertTrue(args.contains("--embed-subs"))
        assertEquals("en", args.valueAfter("--sub-langs"))
        assertTrue(args.contains("--embed-metadata"))
        assertTrue(args.contains("--write-thumbnail"))
        assertEquals(YtDlpArguments.SPONSORBLOCK_CATEGORIES, args.valueAfter("--sponsorblock-remove"))
    }

    @Test
    fun clipsBecomeSectionsAndInvertedRangesNeverReachTheProcess() {
        val both = build(DownloadOptions(clipStart = "10", clipEnd = "1:30"))
        assertEquals("*00:00:10-00:01:30", both.valueAfter("--download-sections"))

        val startOnly = build(DownloadOptions(clipStart = "90"))
        assertEquals("*00:01:30-inf", startOnly.valueAfter("--download-sections"))

        val endOnly = build(DownloadOptions(clipEnd = "30"))
        assertEquals("*0-00:00:30", endOnly.valueAfter("--download-sections"))

        val none = build()
        assertFalse(none.contains("--download-sections"))
    }

    @Test
    fun chaptersAndSponsorBlockAreOffByDefaultAndOnWhenEnabled() {
        val off = build()
        assertFalse(off.contains("--split-chapters"))
        assertFalse(off.contains("--sponsorblock-remove"))

        val chapterPath = root.resolve("chapter-%(section_number)02d.%(ext)s")
        val on = build(
            DownloadOptions(splitByChapters = true, sponsorBlockRemove = true),
            chapter = chapterPath,
        )
        assertTrue(on.contains("--split-chapters"))
        assertTrue(on.contains("chapter:$chapterPath"))
        assertEquals(YtDlpArguments.SPONSORBLOCK_CATEGORIES, on.valueAfter("--sponsorblock-remove"))
    }

    @Test
    fun cookiesArePassedOnlyWhenOptedInAndAFileExists() {
        val optedIn = YtDlpArguments.build(
            executable = "/usr/bin/yt-dlp",
            request = request(DownloadOptions(useCookies = true)),
            outputTemplatePath = root.resolve("%(title)s.%(ext)s"),
            cookieFilePath = "/state/cookies.txt",
        )
        assertEquals("/state/cookies.txt", optedIn.valueAfter("--cookies"))

        val optedOut = YtDlpArguments.build(
            executable = "/usr/bin/yt-dlp",
            request = request(DownloadOptions(useCookies = false)),
            outputTemplatePath = root.resolve("%(title)s.%(ext)s"),
            cookieFilePath = "/state/cookies.txt",
        )
        assertFalse(optedOut.contains("--cookies"))

        val noFile = YtDlpArguments.build(
            executable = "/usr/bin/yt-dlp",
            request = request(DownloadOptions(useCookies = true)),
            outputTemplatePath = root.resolve("%(title)s.%(ext)s"),
            cookieFilePath = null,
        )
        assertFalse(noFile.contains("--cookies"))
    }

    @Test
    fun prefixAppliesToTheFileNameSegmentOnly() {
        assertEquals(
            "pre-%(title)s.%(ext)s",
            DownloadPaths.applyFilenamePrefix("%(title)s.%(ext)s", "pre-"),
        )
        assertEquals(
            "%(playlist_title)s/pre-%(title)s.%(ext)s",
            DownloadPaths.applyFilenamePrefix("%(playlist_title)s/%(title)s.%(ext)s", "pre-"),
        )
        assertEquals(
            "%(title)s.%(ext)s",
            DownloadPaths.applyFilenamePrefix("%(title)s.%(ext)s", "   "),
        )
    }

    @Test
    fun destinationFolderStaysInsideTheRoot() {
        assertEquals(root.normalize(), DownloadPaths.resolveDestination(root, null))
        assertEquals(root.normalize(), DownloadPaths.resolveDestination(root, "  "))
        assertEquals(
            root.resolve("audio/2026").normalize(),
            DownloadPaths.resolveDestination(root, "audio/2026"),
        )
        assertFailsWith<IllegalArgumentException> {
            DownloadPaths.resolveDestination(root, "../escape")
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadPaths.resolveDestination(root, "/absolute")
        }
    }

    @Test
    fun rejectsTemplateAndArtifactPathsOutsideTheRoot() {
        assertFailsWith<IllegalArgumentException> {
            DownloadPaths.outputTemplatePath(root, "../escape/%(title)s.%(ext)s")
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadPaths.artifactPath(root, "/tmp/elsewhere/file.mp4")
        }
        assertFailsWith<IllegalArgumentException> {
            DownloadPaths.artifactPath(root, "../file.mp4")
        }
        assertTrue(DownloadPaths.artifactPath(root, "sub/file.mp4").startsWith(root.toAbsolutePath().normalize()))
    }
}
