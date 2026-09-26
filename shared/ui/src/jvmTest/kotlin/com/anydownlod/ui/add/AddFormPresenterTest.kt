package com.anydownlod.ui.add

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.clipboard_empty
import com.anydownlod.ui.generated.resources.url_error_blank
import com.anydownlod.ui.generated.resources.url_error_one_only
import com.anydownlod.ui.generated.resources.url_error_scheme
import com.anydownlod.ui.generated.resources.url_error_userinfo
import com.anydownlod.ui.i18n.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.StringResource

class AddFormPresenterTest {

    private class Fixture {
        val engine = InMemoryDownloadEngine()
        val subscriptions = InMemorySubscriptionRepository()
        val settings = InMemorySettingsRepository()
        var ids = 0
        val presenter = AddFormPresenter(
            engine = engine,
            subscriptions = subscriptions,
            settingsRepository = settings,
            idGenerator = { "test-${++ids}" },
        )
    }

    private fun fixture() = Fixture()

    @Test
    fun pasteReplacesTheFieldAndBlankClipboardLeavesIt() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=old")

        f.presenter.applyPastedText("  https://example.com/watch?v=pasted  ")

        assertEquals("https://example.com/watch?v=pasted", f.presenter.state.value.urlText)
        assertNull(f.presenter.status.value)

        f.presenter.applyPastedText(" \n ")

        assertEquals("https://example.com/watch?v=pasted", f.presenter.state.value.urlText)
        assertEquals(UiText.of(Res.string.clipboard_empty), f.presenter.status.value?.message)
        assertEquals(true, f.presenter.status.value?.isError)
    }

    @Test
    fun audioChoicesDoNotCarryVideoFields() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.setMediaType(MediaType.AUDIO)
        f.presenter.setAudioContainer(AudioContainer.FLAC)
        f.presenter.setAudioBitrate("320")

        f.presenter.submit()

        val options = f.engine.jobs.value.single().request.options
        assertEquals(MediaType.AUDIO, options.mediaType)
        assertNull(options.videoProfile)
        assertEquals(VideoCodec.AUTO, options.videoCodec)
        assertEquals(QualityPreference.Best, options.quality)
        assertEquals(AudioContainer.FLAC, options.audioContainer)
        assertNull(options.audioBitrate, "A lossless container must not carry a bitrate.")
        assertNull(options.captionLanguage)
    }

    @Test
    fun captionsChoicesDoNotCarryVideoOrAudioFields() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.setMediaType(MediaType.CAPTIONS)
        f.presenter.setCaptionLanguage("pt-BR")
        f.presenter.setCaptionFormat(CaptionFormat.VTT)
        f.presenter.setQuality(QualityPreference.Resolution("2160"))

        f.presenter.submit()

        val options = f.engine.jobs.value.single().request.options
        assertEquals(MediaType.CAPTIONS, options.mediaType)
        assertNull(options.videoProfile)
        assertNull(options.audioContainer)
        assertEquals(QualityPreference.Best, options.quality)
        assertEquals("pt-BR", options.captionLanguage)
        assertEquals(CaptionFormat.VTT, options.captionFormat)
    }

    @Test
    fun batchSplitKeepsValidLinesAndReportsTheBadOne() {
        val f = fixture()
        f.presenter.setUrl(
            "https://example.com/watch?v=one\nnot-a-url\nhttps://example.com/watch?v=two"
        )

        val report = f.presenter.submit()

        assertNotNull(report)
        assertEquals(2, f.engine.jobs.value.size)
        assertEquals(
            listOf("https://example.com/watch?v=one", "https://example.com/watch?v=two"),
            report.acceptedUrls,
        )
        assertEquals(1, report.rejected.size)
        assertEquals("not-a-url", report.rejected.single().line)
        assertEquals(2, report.started)
        assertEquals(0, report.pending)

        // Only the accepted lines leave the field; the bad line stays for fixing.
        assertEquals("not-a-url", f.presenter.state.value.urlText)
        assertEquals(1, f.presenter.state.value.lineErrors.size)
    }

    @Test
    fun manualStartIsCountedAsWaiting() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.setStartPolicy(StartPolicy.MANUAL)

        val report = f.presenter.submit()

        assertNotNull(report)
        assertEquals(0, report.started)
        assertEquals(1, report.pending)
        assertEquals(JobState.PENDING, f.engine.jobs.value.single().state)
    }

    @Test
    fun doubleSubmitCreatesOneJobPerUrl() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")

        f.presenter.submit()
        f.presenter.submit()

        assertEquals(1, f.engine.jobs.value.size)
        assertTrue(f.presenter.state.value.urlText.isEmpty())
    }

    @Test
    fun aLaterSubmitOfTheSameUrlIsANewDownload() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.submit()

        // The user pastes the same URL again later.
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.submit()

        assertEquals(2, f.engine.jobs.value.size)
    }

    @Test
    fun subscribeRefusesABatch() {
        val f = fixture()
        f.presenter.setUrl(
            "https://example.com/channel/one\nhttps://example.com/channel/two"
        )

        val report = f.presenter.subscribe()

        assertFalse(report.accepted)
        assertNotNull(report.rejectedReason)
        assertTrue(f.subscriptions.subscriptions.value.isEmpty())
    }

    @Test
    fun subscribeCapturesOptionsAndTheSettingsInterval() {
        val f = fixture()
        f.settings.update { it.copy(subscriptionIntervalMinutes = 90) }
        f.presenter.setUrl("https://example.com/channel/fixture")
        f.presenter.setMediaType(MediaType.AUDIO)
        f.presenter.setAudioContainer(AudioContainer.MP3)
        f.presenter.setAudioBitrate("192")

        val report = f.presenter.subscribe()

        assertTrue(report.accepted)
        val subscription = f.subscriptions.subscriptions.value.single()
        assertEquals("example.com", subscription.displayName)
        assertEquals(90, subscription.checkIntervalMinutes)
        assertEquals(MediaType.AUDIO, subscription.downloadOptions.mediaType)
        assertEquals(AudioContainer.MP3, subscription.downloadOptions.audioContainer)
        assertEquals("192", subscription.downloadOptions.audioBitrate)

        // The form changing later must not rewrite the captured options.
        f.presenter.setMediaType(MediaType.VIDEO)
        assertEquals(
            AudioContainer.MP3,
            f.subscriptions.subscriptions.value.single().downloadOptions.audioContainer,
        )
    }

    @Test
    fun cookieOptInOnlyAppliesWhenConfigured() {
        val configured = fixture()
        configured.settings.setCookiesConfigured(true)
        configured.presenter.setUrl("https://example.com/watch?v=fixture")
        configured.presenter.setUseCookies(true)
        configured.presenter.submit()
        assertTrue(configured.engine.jobs.value.single().request.options.useCookies)

        val unconfigured = fixture()
        unconfigured.presenter.setUrl("https://example.com/watch?v=fixture")
        unconfigured.presenter.setUseCookies(true)
        unconfigured.presenter.submit()
        assertFalse(unconfigured.engine.jobs.value.single().request.options.useCookies)
    }

    @Test
    fun customYtDlpJsonIsNeverOnTheRequest() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")

        f.presenter.submit()

        assertTrue(f.engine.jobs.value.all { it.request.options.customYtDlpJson.isEmpty() })
    }

    @Test
    fun videoChoicesReachTheRequest() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.setVideoProfile(VideoContainerProfile.MP4)
        f.presenter.setVideoCodec(VideoCodec.H264)
        f.presenter.setQuality(QualityPreference.Resolution("1080"))

        f.presenter.submit()

        val options = f.engine.jobs.value.single().request.options
        assertEquals(VideoContainerProfile.MP4, options.videoProfile)
        assertEquals(VideoCodec.H264, options.videoCodec)
        assertEquals(QualityPreference.Resolution("1080"), options.quality)
    }

    @Test
    fun advancedControlsReachTheRequest() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.setPlaylistItemLimit("25")
        f.presenter.setClipStart("10")
        f.presenter.setClipEnd("1:30")
        f.presenter.setSplitByChapters(true)
        f.presenter.setSponsorBlockRemove(true)
        f.presenter.setEmbedSubtitles(true)
        f.presenter.setWriteMetadata(true)
        f.presenter.setWriteThumbnail(true)
        f.presenter.setFilenamePrefix("pre-")
        f.presenter.setDestinationFolder("audio/2026")

        f.presenter.submit()

        val options = f.engine.jobs.value.single().request.options
        assertEquals(25, options.playlistItemLimit)
        assertEquals("10", options.clipStart)
        assertEquals("1:30", options.clipEnd)
        assertTrue(options.splitByChapters)
        assertTrue(options.sponsorBlockRemove)
        assertTrue(options.embedSubtitles)
        assertTrue(options.writeMetadata)
        assertTrue(options.writeThumbnail)
        assertEquals("pre-", options.filenamePrefix)
        assertEquals("audio/2026", options.destinationFolder)
    }

    @Test
    fun thumbnailOnlyDoesNotCarrySidecarFlagsThatDoNotApply() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.setMediaType(MediaType.THUMBNAIL)
        f.presenter.setEmbedSubtitles(true)
        f.presenter.setWriteMetadata(true)
        f.presenter.setWriteThumbnail(true)

        f.presenter.submit()

        val options = f.engine.jobs.value.single().request.options
        assertEquals(MediaType.THUMBNAIL, options.mediaType)
        assertFalse(options.embedSubtitles)
        assertFalse(options.writeMetadata)
        assertFalse(options.writeThumbnail)
    }

    @Test
    fun invalidDestinationOrClipBlocksSubmit() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.setDestinationFolder("../escape")

        assertNull(f.presenter.submit())
        assertTrue(f.engine.jobs.value.isEmpty())

        f.presenter.setDestinationFolder("")
        f.presenter.setClipStart("120")
        f.presenter.setClipEnd("60")

        assertNull(f.presenter.submit())
        assertTrue(f.engine.jobs.value.isEmpty())
        assertTrue(f.presenter.status.value?.isError == true)
    }

    @Test
    fun presetsAreSentInSettingsOrder() {
        val f = fixture()
        val first = f.settings.addPreset("A")!!
        f.settings.addPreset("B")
        val third = f.settings.addPreset("C")!!
        f.presenter.setUrl("https://example.com/watch?v=fixture")
        f.presenter.setPresetSelected(third.id, true)
        f.presenter.setPresetSelected(first.id, true)

        f.presenter.submit()

        assertEquals(
            listOf(first.id, third.id),
            f.engine.jobs.value.single().request.options.presetIds,
        )
    }

    @Test
    fun validateForPreviewAcceptsOneCompatibleUrlAndNeverStartsAJob() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/watch?v=one")

        assertEquals("https://example.com/watch?v=one", f.presenter.validateForPreview())
        assertTrue(f.engine.jobs.value.isEmpty())
    }

    @Test
    fun validateForPreviewRejectsBlankSchemeUserinfoAndBatches() {
        val f = fixture()

        f.presenter.setUrl("   ")
        assertEquals(null, f.presenter.validateForPreview())
        assertEquals(Res.string.url_error_blank, f.presenter.status.value?.message?.resourceOrNull())

        f.presenter.setUrl("https://user:pass@example.com/watch")
        assertEquals(null, f.presenter.validateForPreview())
        assertEquals(Res.string.url_error_userinfo, f.presenter.status.value?.message?.resourceOrNull())

        f.presenter.setUrl("https://example.com/a\nhttps://example.com/b")
        assertEquals(null, f.presenter.validateForPreview())
        assertEquals(Res.string.url_error_one_only, f.presenter.status.value?.message?.resourceOrNull())
        assertTrue(f.engine.jobs.value.isEmpty())
    }

    @Test
    fun validateForPreviewAcceptsASpotifyUrlOrSearchText() {
        val f = fixture()

        f.presenter.setUrl("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
        assertEquals(
            "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC",
            f.presenter.validateForPreview(),
        )

        f.presenter.setUrl("Rick Astley - Never Gonna Give You Up")
        assertEquals("Rick Astley - Never Gonna Give You Up", f.presenter.validateForPreview())

        f.presenter.setUrl("album:Whenever You Need Somebody")
        assertEquals("album:Whenever You Need Somebody", f.presenter.validateForPreview())

        f.presenter.setUrl("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
        assertEquals(
            "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC",
            f.presenter.singleSourceUrl(),
        )
        assertTrue(f.engine.jobs.value.isEmpty(), "preview must not start a job")
    }

    @Test
    fun detectedLinkFillsOnlyAnEmptyField() {
        val f = fixture()

        assertEquals(
            "https://example.com/watch?v=copied",
            f.presenter.applyDetectedLink("  https://example.com/watch?v=copied  "),
        )
        assertEquals("https://example.com/watch?v=copied", f.presenter.singleSourceUrl())
        assertNull(f.presenter.applyDetectedLink("https://example.com/watch?v=other"))
        assertEquals("https://example.com/watch?v=copied", f.presenter.state.value.urlText)
        assertNull(f.presenter.applyDetectedLink("not a link"))
    }

    @Test
    fun singleSourceUrlIsNullForABatchOrAnInputError() {
        val f = fixture()
        f.presenter.setUrl("https://example.com/a\nhttps://example.com/b")
        assertNull(f.presenter.singleSourceUrl())

        f.presenter.setUrl("https://example.com/a")
        f.presenter.setDestinationFolder("../escape")
        assertNull(f.presenter.singleSourceUrl())
    }

    @Test
    fun clipTimestampsAcceptSecondsAndClockFormats() {
        assertEquals(45L, parseClipTimestamp("45"))
        assertEquals(90L, parseClipTimestamp("1:30"))
        assertEquals(3723L, parseClipTimestamp("01:02:03"))
        assertNull(parseClipTimestamp("1:75"))
        assertNull(parseClipTimestamp("nope"))
        assertNull(parseClipTimestamp(":"))
    }
}

/** The resource key of a resource-backed status message, for assertions. */
private fun UiText?.resourceOrNull(): StringResource? = (this as? UiText.Of)?.resource
