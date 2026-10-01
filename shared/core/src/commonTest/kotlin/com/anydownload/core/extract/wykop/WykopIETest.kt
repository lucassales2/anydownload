package com.anydownload.core.extract.wykop

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Wykop subset. The API needs an anonymous bearer token
 * minted from a frontend key/secret the port does not embed, so all four
 * classes match and fail typed; no cookie, token, or signed URL appears.
 */
class WykopIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    private val digUrl =
        "https://wykop.pl/link/6912923/najbardziej-zrzedliwy-kot-na-swiecie-i-frozen-planet-ii-i-bbc-earth"
    private val digCommentUrl =
        "https://wykop.pl/link/6992589/strollowal-oszusta/komentarz/114540527/podobna-sytuacja"
    private val postUrl = "https://wykop.pl/wpis/68893343/kot-koty-smiesznykotek"
    private val postCommentUrl = "https://wykop.pl/wpis/70084873/test-test-test#249303979"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val dig = WykopDigIE(http())
        assertTrue(dig.suitable(digUrl))
        assertTrue(dig.suitable("https://www.wykop.pl/link/6912923"))
        assertFalse(dig.suitable(digCommentUrl), "the dig class must yield comment URLs")
        assertFalse(dig.suitable("https://www.example.com/link/6912923"))

        val digComment = WykopDigCommentIE(http())
        assertTrue(digComment.suitable(digCommentUrl))
        assertFalse(digComment.suitable(digUrl))

        val post = WykopPostIE(http())
        assertTrue(post.suitable(postUrl))
        assertFalse(post.suitable(postCommentUrl), "the post class must yield comment URLs")

        val postComment = WykopPostCommentIE(http())
        assertTrue(postComment.suitable(postCommentUrl))
        assertFalse(postComment.suitable(postUrl))
    }

    // ----------------------------------------------------------------- walls

    @Test
    fun digFailsTypedAtTheTokenWall() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            WykopDigIE(http()).extract(digUrl)
        }
        assertTrue(error.message!!.contains("bearer token"), error.message)
    }

    @Test
    fun digCommentFailsTypedAtTheTokenWall() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            WykopDigCommentIE(http()).extract(digCommentUrl)
        }
        assertTrue(error.message!!.contains("bearer token"), error.message)
    }

    @Test
    fun postFailsTypedAtTheTokenWall() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            WykopPostIE(http()).extract(postUrl)
        }
        assertTrue(error.message!!.contains("bearer token"), error.message)
    }

    @Test
    fun postCommentFailsTypedAtTheTokenWall() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            WykopPostCommentIE(http()).extract(postCommentUrl)
        }
        assertTrue(error.message!!.contains("bearer token"), error.message)
    }
}
