package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.jsc.JsResult
import com.anydownlod.core.jsc.JsRuntime
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * T-070 stage 2: with a runtime the `web` client's ciphered and `n`-challenged
 * formats are resolved and merged with the visionos set; without one the
 * stage-1 result and hidden count are unchanged. All URLs are synthetic.
 */
class YoutubeStage2Test {

    private val videoId = "YE7VzlLtp-4"
    private val watchUrl = "https://www.youtube.com/watch?v=$videoId"

    private val watchHtml = """
        <html><head>
        <meta itemprop="uploadDate" content="2008-05-29T04:24:26-07:00">
        <meta itemprop="isFamilyFriendly" content="true">
        </head><body>
        <script>ytcfg.set({"visitorData":"FIXTURE_VISITOR","STS":1234,
          "INNERTUBE_CLIENT_VERSION":"2.20260708.00.00",
          "jsUrl":"\/s\/player\/fixture\/player_ias.vflset\/en_US\/base.js"});</script>
        </body></html>
    """.trimIndent()

    private fun videoDetails() = """
        "videoDetails": {
          "videoId": "$videoId", "title": "Big Buck Bunny", "author": "Fixture Channel",
          "channelId": "UCfixturechannel000000000", "lengthSeconds": "596", "viewCount": "123456",
          "isLiveContent": false,
          "thumbnail": {"thumbnails": [{"url": "https://i.example/large.jpg", "width": 1280, "height": 720}]}
        },
        "microformat": {"playerMicroformatRenderer": {"publishDate": "2008-05-29", "isFamilySafe": true}},
    """.trimIndent()

    private val cipheredFormat = """
        {"itag": 140, "signatureCipher": "s=abc&sp=sig&url=https%3A%2F%2Fcdn.fixtures.example.net%2Faudio%3Fitag%3D140",
         "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "averageBitrate": 128000}
    """.trimIndent()

    private val nChallengedFormat = """
        {"itag": 137, "url": "https://cdn.fixtures.example.net/video?itag=137&n=CHALLENGE",
         "mimeType": "video/mp4; codecs=\"avc1.640028\"", "height": 1080}
    """.trimIndent()

    private val plainFormat = """
        {"itag": 18, "url": "https://cdn.fixtures.example.net/plain?itag=18",
         "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "height": 360}
    """.trimIndent()

    private val webOnlyFormat = """
        {"itag": 251, "signatureCipher": "s=xyz&sp=sig&url=https%3A%2F%2Fcdn.fixtures.example.net%2Fweb%3Fitag%3D251",
         "mimeType": "audio/webm; codecs=\"opus\"", "averageBitrate": 128000}
    """.trimIndent()

    private fun visionosJson() = """
        {"playabilityStatus": {"status": "OK"}, ${videoDetails()}
         "streamingData": {"formats": [$plainFormat], "adaptiveFormats": [$cipheredFormat, $nChallengedFormat]}}
    """.trimIndent()

    private fun webJson() = """
        {"playabilityStatus": {"status": "OK"}, ${videoDetails()}
         "streamingData": {"formats": [], "adaptiveFormats": [$cipheredFormat, $webOnlyFormat]}}
    """.trimIndent()

    private class ClientAwareTransfer(
        private val watchHtml: String,
        private val visionosJson: String,
        private val webJson: String,
    ) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val body = if (request.method == "GET") {
                watchHtml
            } else {
                val root = Json.parseToJsonElement(request.body!!.decodeToString()).jsonObject
                val client = root["context"]!!.jsonObject["client"]!!.jsonObject["clientName"]!!.jsonPrimitive.content
                if (client == "WEB") webJson else visionosJson
            }
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = if (request.method == "GET") "text/html" else "application/json",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private class FakeRuntime(private val fail: Boolean = false) : JsRuntime {
        override val available: Boolean = true
        var calls = 0

        override suspend fun evaluate(script: String, entry: String, input: String, timeout: Duration): JsResult {
            calls++
            if (fail) return JsResult.Failed("the solver failed")
            val root = Json.parseToJsonElement(input) as JsonObject
            val requests = root["requests"] as JsonArray
            val responses = buildJsonArray {
                requests.forEach { element ->
                    val request = element as JsonObject
                    val type = request["type"]!!.jsonPrimitive.content
                    val challenges = request["challenges"] as JsonArray
                    add(
                        buildJsonObject {
                            put("type", type)
                            put(
                                "data",
                                buildJsonObject {
                                    challenges.forEach { challenge ->
                                        val value = challenge.jsonPrimitive.content
                                        put(value, if (type == "sig") value.reversed() else "SOLVED-$value")
                                    }
                                },
                            )
                        },
                    )
                }
            }
            return JsResult.Ok(buildJsonObject { put("responses", responses) }.toString())
        }
    }

    private suspend fun extract(runtime: JsRuntime?): InfoDict {
        val transfer = ClientAwareTransfer(watchHtml, visionosJson(), webJson())
        val ie = if (runtime == null) YoutubeIE(ExtractorHttp(transfer)) else YoutubeIE(ExtractorHttp(transfer), runtime)
        return ie.extract(watchUrl)
    }

    @Test
    fun withARuntimeTheWebClientResolvesCiphersAndNChallenges() = runTest {
        val info = extract(FakeRuntime())

        // Stage 1 drops 140 (cipher) and 137 (n); stage 2 resolves both plus
        // the web-only 251 and merges without duplicating 140.
        assertEquals(setOf("18", "137", "140", "251"), info.formats.mapNotNull { it.formatId }.toSet())
        assertEquals(0, info.formatsNeedingJs)

        val audio = info.formats.first { it.formatId == "140" }
        assertEquals("https://cdn.fixtures.example.net/audio?itag=140&sig=cba", audio.url)
        val video = info.formats.first { it.formatId == "137" }
        assertEquals("https://cdn.fixtures.example.net/video?itag=137&n=SOLVED-CHALLENGE", video.url)
        val webOnly = info.formats.first { it.formatId == "251" }
        assertEquals("https://cdn.fixtures.example.net/web?itag=251&sig=zyx", webOnly.url)
    }

    @Test
    fun withoutARuntimeTheStageOneResultAndHiddenCountStay() = runTest {
        val info = extract(null)
        assertEquals(listOf("18"), info.formats.mapNotNull { it.formatId })
        assertEquals(2, info.formatsNeedingJs)
    }

    @Test
    fun aSolverFailureFallsBackAndKeepsTheHiddenCount() = runTest {
        val info = extract(FakeRuntime(fail = true))
        assertEquals(listOf("18"), info.formats.mapNotNull { it.formatId })
        assertTrue(info.formatsNeedingJs >= 2, "the unresolved formats stay counted: ${info.formatsNeedingJs}")
    }
}
