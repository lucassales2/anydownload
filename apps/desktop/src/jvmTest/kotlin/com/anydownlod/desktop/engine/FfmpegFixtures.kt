package com.anydownlod.desktop.engine

import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue

/**
 * Local synthetic media fixtures and `ffprobe` helpers shared by the desktop
 * toolkit tests. Everything is generated with lavfi; no network is involved,
 * and a missing tool skips the test instead of downloading a binary.
 */
internal object FfmpegFixtures {

    /** Returns `ffmpeg` and `ffprobe` from PATH, or skips the test. */
    fun assumeTools(): Pair<String, String> {
        val ffmpeg = ExecutableOnPath.find("ffmpeg")
        val ffprobe = ExecutableOnPath.find("ffprobe")
        assumeTrue("ffmpeg is not on PATH; skipping the local fixture test", ffmpeg != null)
        assumeTrue("ffprobe is not on PATH; skipping the local fixture test", ffprobe != null)
        return ffmpeg!! to ffprobe!!
    }

    fun generateVideoOnly(ffmpeg: String, file: Path) {
        runFfmpeg(
            ffmpeg,
            "-f", "lavfi", "-i", "testsrc2=size=128x72:rate=5",
            "-t", "1", "-pix_fmt", "yuv420p", "-c:v", "libx264", "-an",
            file.toString(),
        )
    }

    fun generateAudioOnly(ffmpeg: String, file: Path) {
        runFfmpeg(
            ffmpeg,
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
            "-t", "1", "-c:a", "aac", "-vn",
            file.toString(),
        )
    }

    fun generateOpusOnly(ffmpeg: String, file: Path) {
        runFfmpeg(
            ffmpeg,
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000",
            "-t", "1", "-c:a", "libopus", "-vn",
            file.toString(),
        )
    }

    fun generateMp3(ffmpeg: String, file: Path) {
        runFfmpeg(
            ffmpeg,
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
            "-t", "1", "-c:a", "libmp3lame", "-vn",
            file.toString(),
        )
    }

    fun runFfmpeg(ffmpeg: String, vararg args: String) {
        val process = ProcessBuilder(listOf(ffmpeg, "-hide_banner", "-nostdin", "-y") + args)
            .redirectErrorStream(true)
            .start()
        process.inputStream.readBytes()
        val exit = process.waitFor()
        check(exit == 0) { "fixture ffmpeg failed with exit $exit" }
    }

    fun probeStreams(ffprobe: String, file: Path): List<Pair<String, String>> =
        probeJson(ffprobe, file)["streams"]?.jsonArray.orEmpty().map { element ->
            val stream = element.jsonObject
            (stream["codec_type"]?.jsonPrimitive?.content ?: "") to
                (stream["codec_name"]?.jsonPrimitive?.content ?: "")
        }

    fun probeFormat(ffprobe: String, file: Path): String =
        probeJson(ffprobe, file)["format"]?.jsonObject?.get("format_name")?.jsonPrimitive?.content.orEmpty()

    fun probeTags(ffprobe: String, file: Path): Map<String, String> {
        val process = ProcessBuilder(
            ffprobe,
            "-v", "error",
            "-print_format", "json",
            "-show_entries", "format_tags",
            file.toString(),
        ).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) { "fixture ffprobe failed with exit $exit" }
        val tags = Json.parseToJsonElement(output).jsonObject["format"]
            ?.jsonObject?.get("tags")?.jsonObject ?: return emptyMap()
        return tags.entries.associate { (key, value) -> key.lowercase() to value.jsonPrimitive.content }
    }

    fun probeJson(ffprobe: String, file: Path): JsonObject {
        val process = ProcessBuilder(
            ffprobe,
            "-v", "error",
            "-print_format", "json",
            "-show_entries", "format=format_name:stream=codec_type,codec_name",
            file.toString(),
        ).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) { "fixture ffprobe failed with exit $exit" }
        return Json.parseToJsonElement(output).jsonObject
    }
}
