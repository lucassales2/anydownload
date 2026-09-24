package com.anydownlod.core.extract.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixtureRedactorTest {

    /** A synthetic player response, shaped like innertube but with fake values. */
    private val playerJson = """
        {
          "playabilityStatus": {"status": "OK"},
          "streamingData": {
            "adaptiveFormats": [
              {
                "itag": 137,
                "url": "https://rr1---sn-realhost.googlevideo.com/videoplayback?expire=12345&ei=abcdef&ip=1.2.3.4&itag=137&sig=SECRETSIG&n=SECRETN&cpn=SECRETCPN",
                "signatureCipher": "s=SECRETS&sp=sig&url=https%3A%2F%2Frr1---sn-realhost.googlevideo.com%2Fvideoplayback"
              }
            ]
          },
          "visitorData": "Cgt2aXNpdG9yRGF0YQ%3D%3D",
          "poToken": "SECRET_POT"
        }
    """.trimIndent()

    @Test
    fun redactsSignedUrlsAndTokens() {
        val redacted = FixtureRedactor.redact(playerJson)
        assertFalse(redacted.contains("googlevideo.com"), redacted)
        assertFalse(redacted.contains("SECRETSIG"))
        assertFalse(redacted.contains("SECRETN"))
        assertFalse(redacted.contains("SECRETCPN"))
        assertFalse(redacted.contains("SECRETS"))
        assertFalse(redacted.contains("SECRET_POT"))
        assertFalse(redacted.contains("Cgt2aXNpdG9yRGF0YQ"))
        assertFalse(redacted.contains("expire=12345"))
        assertFalse(redacted.contains("1.2.3.4"))
        // The synthetic placeholder keeps the JSON shape valid.
        assertTrue(redacted.contains("googlevideo.example"))
        assertTrue(redacted.contains("\"visitorData\": \"REDACTED\""))
    }

    @Test
    fun keepsOrdinaryFieldsAndSyntheticHosts() {
        val body = """
            {"id":"fixture","title":"Fixture video","url":"https://cdn.fixtures.example.net/clip.mp4"}
        """.trimIndent()
        assertEquals(body, FixtureRedactor.redact(body))
    }

    @Test
    fun redactsVisitorDataInQueryStrings() {
        val redacted = FixtureRedactor.redact("https://www.youtube.example/api?visitorData=Cgt2aXNpdG9y&plain=kept")
        assertFalse(redacted.contains("Cgt2aXNpdG9y"))
        assertTrue(redacted.contains("visitorData=REDACTED"))
        assertTrue(redacted.contains("plain=kept"))
    }
}
