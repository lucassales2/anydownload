/*
 * JS challenge provider — AnyDownload
 *
 * Translation of the stdin/stdout protocol from
 * `yt_dlp/extractor/youtube/jsc/_builtin/ejs.py` and the public request model
 * of `yt_dlp/extractor/youtube/jsc/provider.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24.
 * Unlicense; see shared/core/NOTICE.md. The Python provider plugin machinery
 * and the runtime registry are not translated.
 */
package com.anydownlod.core.jsc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Upstream `JsChallengeType`. */
enum class JsChallengeType(val wire: String) {
    N("n"),
    SIG("sig"),
}

/** One batch of challenges against one player script. */
data class JsChallengeRequest(
    val type: JsChallengeType,
    val challenges: List<String>,
)

/** Upstream `NChallengeOutput`/`SigChallengeOutput`: challenge → result. */
sealed interface JsChallengeOutcome {
    data class Solved(
        val results: Map<JsChallengeType, Map<String, String>>,
        /** Upstream `output_preprocessed`: cache this per player id. */
        val preprocessedPlayer: String?,
    ) : JsChallengeOutcome

    data class Failed(val reason: String) : JsChallengeOutcome
}

/**
 * Builds the upstream solver input, calls [JsRuntime], and parses the reply.
 *
 * The protocol is `{type: player|preprocessed, player|preprocessed_player,
 * requests: [{type, challenges}], output_preprocessed}` in, and
 * `{responses: [{type, data: {challenge: result}}], preprocessed_player}` out;
 * `{type: error, error}` fails typed. The preprocessed player is cached in
 * memory per player id for the app session. Errors never include script
 * output, challenge values, or URLs.
 */
class JsChallengeProvider(
    private val runtime: JsRuntime,
    private val timeout: Duration = 30.seconds,
) {
    private val preprocessedPlayers = mutableMapOf<String, String>()

    suspend fun solve(playerJs: String, requests: List<JsChallengeRequest>): JsChallengeOutcome {
        if (!runtime.available) return JsChallengeOutcome.Failed("No JavaScript runtime is available.")
        if (requests.isEmpty()) return JsChallengeOutcome.Solved(emptyMap(), null)
        val playerKey = playerKey(playerJs)
        val cached = preprocessedPlayers[playerKey]
        val script = EjsScripts.lib + "\nObject.assign(globalThis, lib);\n" + EjsScripts.core
        val input = buildInput(playerJs, cached, requests)
        return when (val evaluated = runtime.evaluate(script, "jsc", input, timeout)) {
            is JsResult.Failed -> JsChallengeOutcome.Failed(evaluated.reason)

            is JsResult.Ok -> {
                val parsed = parseOutput(evaluated.json, requests)
                    ?: return JsChallengeOutcome.Failed("The challenge solver returned an unusable result.")
                if (parsed is JsChallengeOutcome.Solved && parsed.preprocessedPlayer != null) {
                    preprocessedPlayers[playerKey] = parsed.preprocessedPlayer
                }
                parsed
            }
        }
    }

    private fun playerKey(playerJs: String): String {
        // A stable, non-reversible key: FNV-1a over the script text.
        var hash = 0xcbf29ce484222325UL
        for (byte in playerJs.encodeToByteArray()) {
            hash = hash xor (byte.toUByte().toULong())
            hash *= 0x100000001b3UL
        }
        return hash.toString(16) + ":" + playerJs.length
    }

    private fun buildInput(playerJs: String, preprocessed: String?, requests: List<JsChallengeRequest>): String {
        val requestArray = buildJsonArray {
            for (request in requests) {
                add(
                    buildJsonObject {
                        put("type", request.type.wire)
                        put(
                            "challenges",
                            buildJsonArray { request.challenges.forEach { add(JsonPrimitive(it)) } },
                        )
                    },
                )
            }
        }
        val root = buildJsonObject {
            if (preprocessed != null) {
                put("type", "preprocessed")
                put("preprocessed_player", preprocessed)
            } else {
                put("type", "player")
                put("player", playerJs)
                put("output_preprocessed", true)
            }
            put("requests", requestArray)
        }
        return root.toString()
    }

    private fun parseOutput(json: String, requests: List<JsChallengeRequest>): JsChallengeOutcome? {
        val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        if ((root["type"] as? JsonPrimitive)?.contentOrNull == "error") {
            return JsChallengeOutcome.Failed("The challenge solver reported an error.")
        }
        val responses = root["responses"] as? JsonArray ?: return null
        if (responses.size != requests.size) return null
        val results = mutableMapOf<JsChallengeType, MutableMap<String, String>>()
        for ((index, request) in requests.withIndex()) {
            val response = responses[index] as? JsonObject ?: return null
            if ((response["type"] as? JsonPrimitive)?.contentOrNull == "error") {
                return JsChallengeOutcome.Failed(
                    "The challenge solver could not solve a ${request.type.wire} challenge.",
                )
            }
            val data = response["data"] as? JsonObject ?: return null
            val perType = results.getOrPut(request.type) { mutableMapOf() }
            for (challenge in request.challenges) {
                val result = (data[challenge] as? JsonPrimitive)?.contentOrNull ?: return null
                perType[challenge] = result
            }
        }
        val preprocessed = (root["preprocessed_player"] as? JsonPrimitive)?.contentOrNull
        return JsChallengeOutcome.Solved(results, preprocessed)
    }
}
