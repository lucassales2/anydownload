package com.anydownlod.core.format

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OptionsToSpecTest {

    @Test
    fun videoQualityCompilesToASingleFileSpec() {
        val best = assertIs<CompiledSpec.SingleFile>(OptionsToSpec.compile(DownloadOptions()))
        assertEquals("b", best.specText)
        assertEquals(FormatSpec.Single("b"), best.spec)

        val worst = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(quality = QualityPreference.Worst)),
        )
        assertEquals("w", worst.specText)

        val capped = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(quality = QualityPreference.Resolution("720"))),
        )
        assertEquals("b[height<=720]", capped.specText)
    }

    @Test
    fun mp4AndIosProfilesAddFiltersWithASingleFallback() {
        val mp4 = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(videoProfile = VideoContainerProfile.MP4)),
        )
        assertEquals("b[ext=mp4][vcodec^=avc1]/b", mp4.specText)
        assertIs<FormatSpec.Fallback>(mp4.spec)

        val ios = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(videoProfile = VideoContainerProfile.IOS_COMPATIBLE)),
        )
        assertEquals("b[ext=mp4][vcodec^=avc1]/b", ios.specText)

        val capped = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(
                DownloadOptions(
                    videoProfile = VideoContainerProfile.MP4,
                    quality = QualityPreference.Resolution("1080"),
                ),
            ),
        )
        assertEquals("b[height<=1080][ext=mp4][vcodec^=avc1]/b[height<=1080]", capped.specText)
    }

    @Test
    fun codecPreferenceBecomesASortKey() {
        assertEquals(
            listOf("vcodec:avc"),
            assertIs<CompiledSpec.SingleFile>(OptionsToSpec.compile(DownloadOptions(videoCodec = VideoCodec.H264))).sort,
        )
        assertEquals(
            listOf("vcodec:h265"),
            assertIs<CompiledSpec.SingleFile>(OptionsToSpec.compile(DownloadOptions(videoCodec = VideoCodec.HEVC))).sort,
        )
        assertEquals(
            listOf("vcodec:av01"),
            assertIs<CompiledSpec.SingleFile>(OptionsToSpec.compile(DownloadOptions(videoCodec = VideoCodec.AV1))).sort,
        )
        assertEquals(
            listOf("vcodec:vp9"),
            assertIs<CompiledSpec.SingleFile>(OptionsToSpec.compile(DownloadOptions(videoCodec = VideoCodec.VP9))).sort,
        )
        assertEquals(
            emptyList(),
            assertIs<CompiledSpec.SingleFile>(OptionsToSpec.compile(DownloadOptions(videoCodec = VideoCodec.AUTO))).sort,
        )
    }

    @Test
    fun audioContainersCompileOrAskForTheToolkit() {
        assertEquals(
            "ba",
            assertIs<CompiledSpec.SingleFile>(
                OptionsToSpec.compile(DownloadOptions(mediaType = MediaType.AUDIO)),
            ).specText,
        )
        assertEquals(
            "ba[ext=m4a]",
            assertIs<CompiledSpec.SingleFile>(
                OptionsToSpec.compile(DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.M4A)),
            ).specText,
        )
        assertEquals(
            "ba[acodec=opus]",
            assertIs<CompiledSpec.SingleFile>(
                OptionsToSpec.compile(DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.OPUS)),
            ).specText,
        )
        for (container in listOf(AudioContainer.MP3, AudioContainer.WAV, AudioContainer.FLAC)) {
            val needs = assertIs<CompiledSpec.NeedsToolkit>(
                OptionsToSpec.compile(DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = container)),
            )
            assertEquals(container.wireName, needs.container)
            assertTrue(needs.message.contains("cannot write"), needs.message)

            val extract = assertIs<CompiledSpec.ExtractAudio>(
                OptionsToSpec.compile(
                    DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = container),
                    audioContainers = setOf(container),
                ),
            )
            assertEquals(container, extract.container)
            assertEquals("ba", extract.specText)
        }
    }

    @Test
    fun captionsAndThumbnailsAreNotInPhase() {
        assertEquals(
            CompiledSpec.NotInPhase,
            OptionsToSpec.compile(DownloadOptions(mediaType = MediaType.CAPTIONS)),
        )
        assertEquals(
            CompiledSpec.NotInPhase,
            OptionsToSpec.compile(DownloadOptions(mediaType = MediaType.THUMBNAIL)),
        )
    }

    @Test
    fun everyVideoCombinationIsSingleFileAndNeverMerges() {
        val profiles = VideoContainerProfile.entries
        val codecs = VideoCodec.entries
        val qualities = listOf(
            QualityPreference.Best,
            QualityPreference.Worst,
            QualityPreference.Resolution("480"),
            QualityPreference.Resolution("1080"),
            QualityPreference.Resolution("not-a-number"),
        )
        for (profile in profiles) {
            for (codec in codecs) {
                for (quality in qualities) {
                    val compiled = assertIs<CompiledSpec.SingleFile>(
                        OptionsToSpec.compile(
                            DownloadOptions(videoProfile = profile, videoCodec = codec, quality = quality),
                        ),
                        "$profile/$codec/$quality must compile to a single-file spec",
                    )
                    assertFalse(compiled.specText.contains("+"), "${compiled.specText} must not request a merge")
                    // Every compiled spec is re-parsable by the selector.
                    FormatSpec.parse(compiled.specText)
                }
            }
        }
    }

    @Test
    fun mergeCapableHostCompilesOneMergeWithASingleFileFallback() {
        val best = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(), canMerge = true),
        )
        assertEquals("bv*+ba/b", best.specText)

        val worst = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(quality = QualityPreference.Worst), canMerge = true),
        )
        assertEquals("wv*+wa/w", worst.specText)

        val capped = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(quality = QualityPreference.Resolution("720")), canMerge = true),
        )
        assertEquals("bv*[height<=720]+ba/b[height<=720]", capped.specText)

        val mp4 = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(videoProfile = VideoContainerProfile.MP4), canMerge = true),
        )
        assertEquals("bv*[ext=mp4][vcodec^=avc1]+ba/b[ext=mp4][vcodec^=avc1]/b", mp4.specText)

        val cappedMp4 = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(
                DownloadOptions(
                    videoProfile = VideoContainerProfile.MP4,
                    quality = QualityPreference.Resolution("1080"),
                ),
                canMerge = true,
            ),
        )
        assertEquals(
            "bv*[height<=1080][ext=mp4][vcodec^=avc1]+ba/b[height<=1080][ext=mp4][vcodec^=avc1]/b[height<=1080]",
            cappedMp4.specText,
        )

        for (spec in listOf(best, worst, capped, mp4, cappedMp4)) {
            assertEquals(1, spec.specText.count { it == '+' }, spec.specText)
            FormatSpec.parse(spec.specText)
        }

        // Audio options never grow a merge, even on a merge-capable host.
        val audio = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(
                DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.M4A),
                canMerge = true,
            ),
        )
        assertFalse(audio.specText.contains("+"))
    }

    @Test
    fun mergeSpecSelectsASplitPair() {
        val info = com.anydownlod.core.extract.InfoDict(
            formats = listOf(
                format(id = "v1080", ext = "webm", vcodec = "vp9", acodec = "none", height = 1080),
                format(id = "v720", ext = "mp4", vcodec = "avc1", acodec = "none", height = 720),
                format(id = "audio", ext = "m4a", vcodec = "none", acodec = "mp4a"),
                format(id = "progressive", ext = "mp4", vcodec = "avc1", acodec = "mp4a", height = 360),
            ),
        )
        val compiled = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(quality = QualityPreference.Resolution("720")), canMerge = true),
        )
        val selection = assertIs<Selection.Merge>(FormatSelector.select(info, compiled.spec, compiled.sort))
        assertEquals("v720", selection.video.formatId)
        assertEquals("audio", selection.audio.formatId)
    }

    @Test
    fun compiledSpecsSelectFromASyntheticInfoDict() {
        val formats = listOf(
            format(id = "v1080", ext = "webm", vcodec = "vp9", acodec = "none", height = 1080),
            format(id = "v720mp4", ext = "mp4", vcodec = "avc1", acodec = "none", height = 720),
            format(id = "progressive", ext = "mp4", vcodec = "avc1.640028", acodec = "mp4a.40.2", height = 360),
            format(id = "audio", ext = "webm", vcodec = "none", acodec = "opus"),
        )
        val info = com.anydownlod.core.extract.InfoDict(formats = formats)

        val mp4 = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(videoProfile = VideoContainerProfile.MP4)),
        )
        val selected = assertIs<Selection.Single>(FormatSelector.select(info, mp4.spec, mp4.sort))
        assertEquals("progressive", selected.format.formatId)

        val audio = assertIs<CompiledSpec.SingleFile>(
            OptionsToSpec.compile(DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.OPUS)),
        )
        val audioSelected = assertIs<Selection.Single>(FormatSelector.select(info, audio.spec, audio.sort))
        assertEquals("audio", audioSelected.format.formatId)
    }
}
