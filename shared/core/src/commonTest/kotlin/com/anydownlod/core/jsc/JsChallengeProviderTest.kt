package com.anydownlod.core.jsc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * T-070: the provider builds the upstream stdin JSON, parses the solver
 * reply, and caches the preprocessed player. The runtime is a fake; no script
 * is executed here.
 */
class JsChallengeProviderTest {

    private class FakeRuntime(private val reply: (String) -> JsResult) : JsRuntime {
        override val available: Boolean = true
        val inputs = mutableListOf<String>()

        override suspend fun evaluate(
            script: String,
            entry: String,
            input: String,
            timeout: kotlin.time.Duration,
        ): JsResult {
            inputs += input
            return reply(input)
        }
    }

    private fun solverReply(input: String): JsResult {
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
        return JsResult.Ok(
            buildJsonObject {
                put("responses", responses)
                put("preprocessed_player", "cached-player")
            }.toString(),
        )
    }

    @Test
    fun buildsTheUpstreamInputAndParsesTheResults() = runTest {
        val runtime = FakeRuntime(::solverReply)
        val provider = JsChallengeProvider(runtime, 5.seconds)
        val sigChallenge = CharArray(3) { it.toChar() }.concatToString()
        val outcome = assertIs<JsChallengeOutcome.Solved>(
            provider.solve(
                "player-js",
                listOf(
                    JsChallengeRequest(JsChallengeType.SIG, listOf(sigChallenge)),
                    JsChallengeRequest(JsChallengeType.N, listOf("n-value")),
                ),
            ),
        )

        assertTrue(runtime.inputs.single().contains("\"type\":\"player\""))
        assertTrue(runtime.inputs.single().contains("\"player\":\"player-js\""))
        assertTrue(runtime.inputs.single().contains("\"output_preprocessed\":true"))
        assertEquals("n-value", outcome.results.getValue(JsChallengeType.N).keys.single())
        assertEquals("SOLVED-n-value", outcome.results.getValue(JsChallengeType.N).values.single())
        assertEquals(sigChallenge.reversed(), outcome.results.getValue(JsChallengeType.SIG).values.single())
        assertEquals("cached-player", outcome.preprocessedPlayer)
    }

    @Test
    fun thePreprocessedPlayerIsReusedOnTheNextCall() = runTest {
        val runtime = FakeRuntime(::solverReply)
        val provider = JsChallengeProvider(runtime)
        provider.solve("player-js", listOf(JsChallengeRequest(JsChallengeType.N, listOf("a"))))
        provider.solve("player-js", listOf(JsChallengeRequest(JsChallengeType.N, listOf("b"))))

        assertTrue(runtime.inputs.first().contains("\"type\":\"player\""))
        assertTrue(runtime.inputs.last().contains("\"type\":\"preprocessed\""))
        assertTrue(runtime.inputs.last().contains("\"preprocessed_player\":\"cached-player\""))
    }

    @Test
    fun aSolverErrorIsTypedAndRedacted() = runTest {
        val runtime = FakeRuntime {
            JsResult.Ok("""{"type":"error","error":"secret player detail"}""")
        }
        val outcome = assertIs<JsChallengeOutcome.Failed>(
            provider(runtime).solve("player-js", listOf(JsChallengeRequest(JsChallengeType.N, listOf("a")))),
        )
        assertTrue(!outcome.reason.contains("secret"), outcome.reason)
    }

    @Test
    fun aMissingRuntimeIsUnavailable() = runTest {
        val outcome = assertIs<JsChallengeOutcome.Failed>(
            JsChallengeProvider(NoJsRuntime).solve("player-js", listOf(JsChallengeRequest(JsChallengeType.N, listOf("a")))),
        )
        assertEquals("No JavaScript runtime is available.", outcome.reason)
    }

    private fun provider(runtime: JsRuntime) = JsChallengeProvider(runtime)
}
