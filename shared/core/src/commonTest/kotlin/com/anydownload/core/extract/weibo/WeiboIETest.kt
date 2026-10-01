package com.anydownload.core.extract.weibo

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Weibo URL surface is a typed wall: the API needs the passport.weibo.com
 * guest-cookie flow, so no fixture can pass.
 */
class WeiboIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            WeiboIE(http) to "https://weibo.com/7827771738/N4xlMvjhI",
            WeiboIE(http) to "https://m.weibo.cn/status/N4xlMvjhI",
            WeiboVideoIE(http) to "https://weibo.com/tv/show/1034:4797699866951785?from=old_pc_videoshow",
            WeiboVideoIE(http) to "https://video.weibo.com/show?fid=1034:4797699866951785",
            WeiboUserIE(http) to "https://weibo.com/u/2066652961?tabtype=video",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(WeiboIE(http).suitable("https://weibo.com/u/2066652961"))
    }

    @Test
    fun everyClassFailsTypedOnTheGuestCookieWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            WeiboIE(http) to "https://weibo.com/7827771738/N4xlMvjhI",
            WeiboVideoIE(http) to "https://weibo.com/tv/show/1034:4797699866951785",
            WeiboUserIE(http) to "https://weibo.com/u/2066652961",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("guest-cookie"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
