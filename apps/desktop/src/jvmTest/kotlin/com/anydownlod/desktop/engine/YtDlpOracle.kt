package com.anydownlod.desktop.engine

import com.anydownlod.core.extract.InfoDict
import java.nio.file.Files
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Differential oracle for the Kotlin extractor (T-062).
 *
 * Runs the installed `yt-dlp -J` with the same innertube client the Kotlin
 * side uses, normalizes the fields the port claims to match, and diffs them.
 * The oracle lives in the desktop test source set: `ProcessBuilder` stays
 * under `apps/desktop`, and the raw JSON (which contains signed media URLs)
 * is parsed and discarded, never printed.
 */
object YtDlpOracle {

    const val PINNED_VERSION: String = "2026.08.19"
    const val PLAYER_CLIENT_ARG: String = "youtube:player_client=visionos"
    const val WEB_PLAYER_CLIENT_ARG: String = "youtube:player_client=web,visionos"

    private val json = Json { ignoreUnknownKeys = true }

    /** The `yt-dlp --version` first line, or null when the tool cannot run. */
    fun installedVersion(executable: String, runner: CliProcessRunner = JavaCliProcessRunner): String? =
        run(executable, listOf("--version"), runner)
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun infoDict(
        executable: String,
        url: String,
        playerClientArg: String = PLAYER_CLIENT_ARG,
        runner: CliProcessRunner = JavaCliProcessRunner,
    ): NormalizedInfo {
        val output = run(
            executable,
            listOf("-J", "--no-warnings", "--extractor-args", playerClientArg, url),
            runner,
        ) ?: error("yt-dlp produced no output")
        return normalizeYtDlp(output)
    }

    /**
     * Starts one argument-list process. stderr is drained but never surfaced:
     * it can carry URLs. A non-zero exit is reported by code only.
     */
    private fun run(executable: String, arguments: List<String>, runner: CliProcessRunner): String? {
        val workingDirectory = Files.createTempDirectory("anydownlod-oracle")
        try {
            val process = runner.start(listOf(executable) + arguments, workingDirectory)
            val stdout = process.stdout.readText()
            process.stderr.readText() // drained; never printed
            val exit = process.waitFor(180_000)
            if (exit == null) {
                process.destroyTree()
                error("yt-dlp timed out")
            }
            if (exit != 0) error("yt-dlp exited with code $exit")
            return stdout
        } finally {
            runCatching { workingDirectory.toFile().deleteRecursively() }
        }
    }

    fun normalizeYtDlp(raw: String): NormalizedInfo {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "yt-dlp produced no JSON object" }
        val obj = runCatching { json.parseToJsonElement(raw.substring(start, end + 1)) as? JsonObject }
            .getOrNull() ?: error("yt-dlp JSON was not an object")
        val formats = (obj["formats"] as? JsonArray).orEmpty()
            .mapNotNull { element ->
                val format = element as? JsonObject ?: return@mapNotNull null
                val id = format.text("format_id") ?: return@mapNotNull null
                if (format.text("vcodec") == "none" && format.text("acodec") == "none") {
                    // Storyboards are not media; the Kotlin side does not port them.
                    return@mapNotNull null
                }
                id to NormalizedFormat(
                    formatId = id,
                    ext = format.text("ext"),
                    vcodec = format.text("vcodec"),
                    acodec = format.text("acodec"),
                    height = format.long("height"),
                    fps = format.double("fps"),
                    protocol = format.text("protocol"),
                )
            }
            .toMap()
        return NormalizedInfo(
            id = obj.text("id"),
            title = obj.text("title"),
            duration = obj.double("duration"),
            channelId = obj.text("channel_id"),
            uploadDate = obj.text("upload_date"),
            ageLimit = obj.int("age_limit"),
            formats = formats,
        )
    }

    fun normalizeKotlin(info: InfoDict): NormalizedInfo = NormalizedInfo(
        id = info.id,
        title = info.title,
        duration = info.duration,
        channelId = info.channelId,
        uploadDate = info.uploadDate,
        ageLimit = info.ageLimit,
        formats = info.formats
            .mapNotNull { format ->
                val id = format.formatId ?: return@mapNotNull null
                id to NormalizedFormat(
                    formatId = id,
                    ext = format.ext,
                    vcodec = format.vcodec,
                    acodec = format.acodec,
                    height = format.height,
                    fps = format.fps,
                    protocol = format.protocol,
                )
            }
            .toMap(),
    )

    /**
     * Field-level diff. Values are ids, titles, codecs, heights, and sizes —
     * never a URL. An empty list means the normalized fields agree.
     */
    fun diff(kotlin: NormalizedInfo, oracle: NormalizedInfo): List<String> {
        val diffs = mutableListOf<String>()

        fun compare(field: String, left: Any?, right: Any?) {
            if (left != right) diffs += "$field kotlin=$left oracle=$right"
        }

        compare("id", kotlin.id, oracle.id)
        compare("title", kotlin.title, oracle.title)
        compare("channel_id", kotlin.channelId, oracle.channelId)
        compare("upload_date", kotlin.uploadDate, oracle.uploadDate)
        compare("age_limit", kotlin.ageLimit, oracle.ageLimit)
        if (kotlin.duration == null || oracle.duration == null) {
            compare("duration", kotlin.duration, oracle.duration)
        } else if (abs(kotlin.duration - oracle.duration) > 1.0) {
            diffs += "duration kotlin=${kotlin.duration} oracle=${oracle.duration}"
        }

        val ids = (kotlin.formats.keys + oracle.formats.keys).sorted()
        for (id in ids) {
            val left = kotlin.formats[id]
            val right = oracle.formats[id]
            if (left == null || right == null) {
                diffs += "format $id kotlin=${if (left == null) "<missing>" else "<present>"} " +
                    "oracle=${if (right == null) "<missing>" else "<present>"}"
                continue
            }
            compare("format $id.ext", left.ext?.lowercase(), right.ext?.lowercase())
            compare("format $id.vcodec", codec(left.vcodec), codec(right.vcodec))
            compare("format $id.acodec", codec(left.acodec), codec(right.acodec))
            compare("format $id.height", left.height, right.height)
            compare("format $id.protocol", left.protocol?.lowercase(), right.protocol?.lowercase())
            if (left.fps == null || right.fps == null) {
                compare("format $id.fps", left.fps, right.fps)
            } else if (abs(left.fps - right.fps) > 0.001) {
                diffs += "format $id.fps kotlin=${left.fps} oracle=${right.fps}"
            }
        }
        return diffs
    }

    /** `none` and a missing codec mean the same to the differential. */
    private fun codec(value: String?): String? = value?.lowercase()?.takeIf { it != "none" }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" && it.isNotBlank() }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull ?: long(key)?.toInt()
}

/** Normalized extractor fields the differential compares. */
data class NormalizedInfo(
    val id: String?,
    val title: String?,
    val duration: Double?,
    val channelId: String?,
    val uploadDate: String?,
    val ageLimit: Int?,
    val formats: Map<String, NormalizedFormat>,
)

data class NormalizedFormat(
    val formatId: String,
    val ext: String?,
    val vcodec: String?,
    val acodec: String?,
    val height: Long?,
    val fps: Double?,
    val protocol: String?,
) {
    val isAudioOnly: Boolean
        get() = (vcodec == null || vcodec == "none") && acodec != null && acodec != "none"
}
