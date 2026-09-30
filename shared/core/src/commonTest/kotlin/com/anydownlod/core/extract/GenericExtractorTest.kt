package com.anydownlod.core.extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fixture-string tests for the generic extractor subset (T-045). No live
 * host is ever contacted; pages and media URLs are local fixtures on
 * example.org-shaped hosts only.
 */
class GenericExtractorTest {

    private fun direct(page: String, html: String): String =
        when (val result = GenericExtractor.extract(page, html)) {
            is GenericExtraction.Direct -> result.url
            is GenericExtraction.Failed -> error("expected Direct, got ${result.reason}")
        }

    private fun failed(page: String, html: String): GenericExtractionFailure =
        when (val result = GenericExtractor.extract(page, html)) {
            is GenericExtraction.Direct -> error("expected Failed, got ${result.url}")
            is GenericExtraction.Failed -> result.reason
        }

    @Test
    fun oneVideoSrcReturnsTheResolvedUrl() {
        assertEquals(
            "https://example.org/clip.mp4",
            direct(
                "https://example.org/watch",
                """<html><body><video src="/clip.mp4"></video></body></html>""",
            ),
        )
    }

    @Test
    fun oneAudioSrcReturnsTheResolvedUrl() {
        assertEquals(
            "https://example.org/sound.ogg",
            direct(
                "https://example.org/",
                """<audio src="sound.ogg"></audio>""",
            ),
        )
    }

    @Test
    fun relativeSourceNestedInVideoResolvesAgainstTheDirectory() {
        assertEquals(
            "https://example.org/videos/clip.webm",
            direct(
                "https://example.org/videos/watch",
                """<video poster="p.jpg"><source src="clip.webm" type="video/webm"></video>""",
            ),
        )
    }

    @Test
    fun parentSegmentsResolveThroughDotDot() {
        assertEquals(
            "https://example.org/a/clip.mp4",
            direct(
                "https://example.org/a/b/page.html",
                """<video><source src="../clip.mp4"></video>""",
            ),
        )
    }

    @Test
    fun schemeRelativeSourceTakesThePageScheme() {
        assertEquals(
            "https://cdn.example.org/clip.mp4",
            direct(
                "https://example.org/watch",
                """<video src="//cdn.example.org/clip.mp4"></video>""",
            ),
        )
    }

    @Test
    fun uppercaseTagsAndEntityEscapingStillMatch() {
        assertEquals(
            "https://example.org/a%20b/c&d.mp4",
            direct(
                "https://example.org/watch",
                """<VIDEO SRC="/a%20b/c&amp;d.mp4"></VIDEO>""",
            ),
        )
        assertEquals(
            "https://example.org/x.mp4",
            direct(
                "https://example.org/watch",
                """<video src='&#x2F;x.mp4'></video>""",
            ),
        )
    }

    @Test
    fun duplicateSourcesCountOnce() {
        assertEquals(
            "https://example.org/clip.mp4",
            direct(
                "https://example.org/watch",
                """<video src="clip.mp4"></video><video src="clip.mp4"></video>""",
            ),
        )
    }

    @Test
    fun zeroMediaElementsIsATypedFailure() {
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed("https://example.org/watch", "<html><body>nothing here</body></html>"),
        )
    }

    @Test
    fun sourceOutsideVideoOrAudioIsIgnored() {
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<div class="player"><source src="leak.mp4"></div>""",
            ),
        )
    }

    @Test
    fun twoSurvivingCandidatesFailClosed() {
        assertEquals(
            GenericExtractionFailure.MultipleMedia,
            failed(
                "https://example.org/watch",
                """<video src="one.mp4"></video><audio src="two.ogg"></audio>""",
            ),
        )
    }

    @Test
    fun policyRejectedCandidateDropsToNoMedia() {
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<video src="https://user:pass@example.org/clip.mp4"></video>""",
            ),
        )
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<video src="ftp://example.org/clip.mp4"></video>""",
            ),
        )
    }

    @Test
    fun oneGoodCandidateStillWinsWhenAnotherFailsPolicy() {
        assertEquals(
            "https://example.org/good.mp4",
            direct(
                "https://example.org/watch",
                """<video src="good.mp4"></video><audio src="https://token@example.org/bad.ogg"></audio>""",
            ),
        )
    }

    @Test
    fun relativeResolutionStripsQueryAndFragmentFromTheBase() {
        assertEquals(
            "https://example.org/clip.mp4",
            direct(
                "https://example.org/watch?page=2#top",
                """<video src="clip.mp4"></video>""",
            ),
        )
    }

    @Test
    fun unsharablePageUrlIsATypedFailure() {
        assertEquals(
            GenericExtractionFailure.UnsupportedPageUrl,
            failed("ftp://example.org/watch", "<video src='clip.mp4'></video>"),
        )
    }

    @Test
    fun oneEmbedSrcWithADirectMediaTargetIsACandidate() {
        assertEquals(
            "https://example.org/clip.mp4",
            direct(
                "https://example.org/watch",
                """<embed src="/clip.mp4" type="video/mp4">""",
            ),
        )
    }

    @Test
    fun aNonMediaEmbedIsIgnored() {
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<embed src="/player.swf">""",
            ),
        )
    }

    @Test
    fun anIframeWhoseSrcIsMediaIsACandidate() {
        assertEquals(
            "https://cdn.example.org/clip.webm",
            direct(
                "https://example.org/watch",
                """<iframe src="https://cdn.example.org/clip.webm"></iframe>""",
            ),
        )
    }

    @Test
    fun aNonMediaIframeIsNotCrawled() {
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<iframe src="https://player.example.org/embed/1"></iframe>""",
            ),
        )
    }

    @Test
    fun oneJsonLdContentUrlIsACandidate() {
        assertEquals(
            "https://cdn.example.org/v.mp4",
            direct(
                "https://example.org/watch",
                """<script type="application/ld+json">{"@context":"https://schema.org","contentUrl":"https://cdn.example.org/v.mp4"}</script>""",
            ),
        )
    }

    @Test
    fun jsonLdGraphEntriesAreScanned() {
        assertEquals(
            "https://cdn.example.org/graph.mp4",
            direct(
                "https://example.org/watch",
                """<script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":"VideoObject","contentUrl":"https://cdn.example.org/graph.mp4"}]}</script>""",
            ),
        )
    }

    @Test
    fun jsonLdEmbedUrlOnlyCountsForMedia() {
        assertEquals(
            "https://cdn.example.org/e.mp4",
            direct(
                "https://example.org/watch",
                """<script type="application/ld+json">{"@context":"https://schema.org","embedUrl":"https://cdn.example.org/e.mp4"}</script>""",
            ),
        )
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<script type="application/ld+json">{"@context":"https://schema.org","embedUrl":"https://player.example.org/embed/1"}</script>""",
            ),
        )
    }

    @Test
    fun jsonLdUrlOnlyCountsWhenItIsMedia() {
        assertEquals(
            "https://cdn.example.org/u.mp4",
            direct(
                "https://example.org/watch",
                """<script type="application/ld+json">{"@context":"https://schema.org","url":"https://cdn.example.org/u.mp4"}</script>""",
            ),
        )
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<script type="application/ld+json">{"@context":"https://schema.org","url":"https://example.org/other-page"}</script>""",
            ),
        )
    }

    @Test
    fun candidatesFromDifferentSourcesStillFailClosed() {
        assertEquals(
            GenericExtractionFailure.MultipleMedia,
            failed(
                "https://example.org/watch",
                """<video src="a.mp4"></video><embed src="b.mp4">""",
            ),
        )
    }

    @Test
    fun aPolicyRejectedEmbedDoesNotWin() {
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<embed src="https://user:pass@example.org/clip.mp4">""",
            ),
        )
    }

    @Test
    fun oneManifestInRawTextIsACandidate() {
        assertEquals(
            "https://example.org/media.m3u8",
            direct(
                "https://example.org/watch",
                """<script>var stream = "/media.m3u8";</script>""",
            ),
        )
    }

    @Test
    fun oneManifestInADataAttributeIsACandidate() {
        assertEquals(
            "https://example.org/dash/manifest.mpd",
            direct(
                "https://example.org/watch",
                """<div data-src="/dash/manifest.mpd"></div>""",
            ),
        )
    }

    @Test
    fun aManifestWithAQueryStillCounts() {
        assertEquals(
            "https://example.org/media.m3u8?token=1",
            direct(
                "https://example.org/watch",
                """<div data-url="/media.m3u8?token=1"></div>""",
            ),
        )
    }

    @Test
    fun zeroManifestsIsATypedFailure() {
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed("https://example.org/watch", "<html><body>nothing</body></html>"),
        )
    }

    @Test
    fun twoManifestsFailClosed() {
        assertEquals(
            GenericExtractionFailure.MultipleMedia,
            failed(
                "https://example.org/watch",
                """<script>var a = "/one.m3u8", b = "/two.mpd";</script>""",
            ),
        )
    }

    @Test
    fun aDirectFileAndAManifestTogetherFailClosed() {
        assertEquals(
            GenericExtractionFailure.MultipleMedia,
            failed(
                "https://example.org/watch",
                """<video src="clip.mp4"></video><div data-src="stream.m3u8"></div>""",
            ),
        )
    }

    @Test
    fun aManifestOutsideThePolicyDoesNotWin() {
        assertEquals(
            GenericExtractionFailure.NoMedia,
            failed(
                "https://example.org/watch",
                """<div data-src="ftp://example.org/stream.m3u8"></div>""",
            ),
        )
    }

    @Test
    fun metaRefreshTargetParsesTheCommonShapes() {
        assertEquals(
            "/next.html",
            GenericExtractor.metaRefreshTarget("""<meta http-equiv="refresh" content="0; url=/next.html">"""),
        )
        assertEquals(
            "/next.html",
            GenericExtractor.metaRefreshTarget("""<meta content="5;URL='/next.html'" http-equiv="REFRESH">"""),
        )
        assertEquals(
            "https://example.org/a?b=1&c=2",
            GenericExtractor.metaRefreshTarget("""<meta http-equiv='refresh' content='0; url=https://example.org/a?b=1&amp;c=2'>"""),
        )
        assertNull(GenericExtractor.metaRefreshTarget("""<meta http-equiv="refresh" content="5">"""))
        assertNull(GenericExtractor.metaRefreshTarget("""<meta name="refresh" content="0; url=/x">"""))
    }

    @Test
    fun directUrlCandidatesAreReturnedAsIs() {
        assertEquals(
            "https://cdn.example.org/absolute.mp4",
            direct(
                "https://example.org/watch",
                """<video src="https://cdn.example.org/absolute.mp4"></video>""",
            ),
        )
    }
}