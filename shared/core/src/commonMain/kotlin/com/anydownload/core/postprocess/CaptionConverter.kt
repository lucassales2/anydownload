/*
 * Caption conversion — AnyDownload (T-015)
 *
 * Translation of the subtitle reading/writing behavior in
 * `yt_dlp/postprocessor/ffmpeg.py` (`FFmpegSubtitlesConvertorPP`) and the
 * `json3`/`srv1-3`/`ttml`/`vtt` caption shapes at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 * Unlicense; see shared/core/NOTICE.md. The conversion is pure Kotlin: no
 * FFmpeg and no process is involved, so every host can write sidecars.
 *
 * A conversion that cannot parse the source returns null; the engine records
 * the original track and a truthful "not converted" note instead of writing a
 * broken file.
 */
package com.anydownload.core.postprocess

import com.anydownload.core.domain.CaptionFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One caption cue in milliseconds; [text] keeps its line breaks. */
data class CaptionCue(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
)

/** The source formats the port can read (upstream `_SUBTITLE_FORMATS`). */
object CaptionConverter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses [body] in [sourceFormat] into cues. Returns null when the body
     * does not match the declared format; never throws for malformed input.
     */
    fun parse(sourceFormat: String, body: String): List<CaptionCue>? = when (sourceFormat.lowercase()) {
        "json3" -> parseJson3(body)
        "srv1", "srv2" -> parseSrvText(body)
        "srv3" -> parseSrv3(body)
        "ttml" -> parseTtml(body)
        "vtt", "webvtt" -> parseVtt(body)
        "srt" -> parseSrt(body)
        else -> null
    }

    /**
     * Converts [body] from [sourceFormat] into [target]. Returns null when the
     * source cannot be parsed; an empty cue list still renders a valid file
     * with no cues.
     */
    fun convert(sourceFormat: String, target: CaptionFormat, body: String): String? {
        val cues = parse(sourceFormat, body) ?: return null
        return render(target, cues)
    }

    /** Renders cues in [target]. */
    fun render(target: CaptionFormat, cues: List<CaptionCue>): String = when (target) {
        CaptionFormat.SRT -> renderSrt(cues)
        CaptionFormat.VTT -> renderVtt(cues)
        CaptionFormat.TTML -> renderTtml(cues)
        CaptionFormat.TXT -> renderTxt(cues)
    }

    // ------------------------------------------------------------- sources

    private fun parseJson3(body: String): List<CaptionCue>? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val events = root["events"] as? JsonArray ?: return null
        return events.mapNotNull { element ->
            val event = element as? JsonObject ?: return@mapNotNull null
            val start = (event["tStartMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return@mapNotNull null
            val duration = (event["dDurationMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            val segments = event["segs"] as? JsonArray ?: return@mapNotNull null
            val text = segments.mapNotNull { segment ->
                ((segment as? JsonObject)?.get("utf8") as? JsonPrimitive)?.content
            }.joinToString("").trim()
            if (text.isEmpty()) return@mapNotNull null
            CaptionCue(start, start + duration, text)
        }
    }

    /** srv1/srv2: `<text start="1.0" dur="2.0">Hello <i>world</i></text>`. */
    private fun parseSrvText(body: String): List<CaptionCue>? {
        val matches = Regex("""<text\b([^>]*)>([\s\S]*?)</text>""").findAll(body).toList()
        if (matches.isEmpty()) return null
        return matches.map { match ->
            val attributes = parseAttributes(match.groupValues[1])
            val start = secondsToMillis(attributes["start"]) ?: 0L
            val duration = secondsToMillis(attributes["dur"]) ?: 0L
            CaptionCue(start, start + duration, cleanMarkup(match.groupValues[2]))
        }
    }

    /** srv3: `<p t="1000" d="2000"><s>Hello</s> <s>world</s></p>`. */
    private fun parseSrv3(body: String): List<CaptionCue>? {
        val matches = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""").findAll(body).toList()
        if (matches.isEmpty()) return null
        return matches.map { match ->
            val attributes = parseAttributes(match.groupValues[1])
            val start = attributes["t"]?.toLongOrNull() ?: 0L
            val duration = attributes["d"]?.toLongOrNull() ?: 0L
            CaptionCue(start, start + duration, cleanMarkup(match.groupValues[2]))
        }
    }

    /** TTML: `<p begin="00:00:01.000" end="00:00:04.000">Hello<br/>world</p>`. */
    private fun parseTtml(body: String): List<CaptionCue>? {
        val matches = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""").findAll(body).toList()
        if (matches.isEmpty()) return null
        return matches.map { match ->
            val attributes = parseAttributes(match.groupValues[1])
            val start = parseTimestamp(attributes["begin"]) ?: 0L
            val end = parseTimestamp(attributes["end"]) ?: start
            CaptionCue(start, end, cleanMarkup(match.groupValues[2]))
        }
    }

    /** WebVTT: `00:00:01.000 --> 00:00:04.000` then the cue text. */
    private fun parseVtt(body: String): List<CaptionCue>? {
        val cues = mutableListOf<CaptionCue>()
        val blocks = body.replace("\r\n", "\n").split(Regex("\n{2,}"))
        var sawCue = false
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) continue
            val timing = lines[timingIndex].split("-->")
            val start = parseTimestamp(timing.getOrNull(0)) ?: continue
            val end = parseTimestamp(timing.getOrNull(1)) ?: start
            val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
            sawCue = true
            if (text.isNotEmpty()) cues += CaptionCue(start, end, text)
        }
        return if (sawCue) cues else null
    }

    /** SRT input is accepted so a sidecar can be converted again. */
    private fun parseSrt(body: String): List<CaptionCue>? {
        val cues = mutableListOf<CaptionCue>()
        val blocks = body.replace("\r\n", "\n").split(Regex("\n{2,}"))
        var sawCue = false
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) continue
            val timing = lines[timingIndex].split("-->")
            val start = parseTimestamp(timing.getOrNull(0)?.replace(',', '.')) ?: continue
            val end = parseTimestamp(timing.getOrNull(1)?.replace(',', '.')) ?: start
            val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
            sawCue = true
            if (text.isNotEmpty()) cues += CaptionCue(start, end, text)
        }
        return if (sawCue) cues else null
    }

    // ------------------------------------------------------------- writers

    private fun renderSrt(cues: List<CaptionCue>): String = buildString {
        cues.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(formatSrt(cue.startMillis)).append(" --> ").append(formatSrt(cue.endMillis)).append('\n')
            append(cue.text).append("\n\n")
        }
    }

    private fun renderVtt(cues: List<CaptionCue>): String = buildString {
        append("WEBVTT\n\n")
        for (cue in cues) {
            append(formatVtt(cue.startMillis)).append(" --> ").append(formatVtt(cue.endMillis)).append('\n')
            append(cue.text).append("\n\n")
        }
    }

    private fun renderTtml(cues: List<CaptionCue>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div>\n")
        for (cue in cues) {
            append("<p begin=\"").append(formatTtml(cue.startMillis)).append("\" end=\"")
                .append(formatTtml(cue.endMillis)).append("\">")
                .append(escapeXml(cue.text).replace("\n", "<br/>"))
                .append("</p>\n")
        }
        append("</div></body></tt>\n")
    }

    private fun renderTxt(cues: List<CaptionCue>): String = buildString {
        for (cue in cues) {
            append(cue.text.replace('\n', ' ').trim()).append('\n')
        }
    }

    // ------------------------------------------------------------- helpers

    private fun cleanMarkup(text: String): String =
        text.replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("</?[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .trim()

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun parseAttributes(text: String): Map<String, String> =
        Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*\"([^\"]*)\"").findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun secondsToMillis(value: String?): Long? =
        value?.trim()?.removeSuffix("s")?.toDoubleOrNull()?.let { (it * 1000.0).toLong() }

    /**
     * Accepts `HH:MM:SS.mmm`, `MM:SS.mmm`, `SS.mmm`, and TTML clock forms with
     * frames (`HH:MM:SS:FF`). Returns null for blank or malformed input.
     */
    private fun parseTimestamp(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.endsWith("ms")) return text.removeSuffix("ms").toDoubleOrNull()?.toLong()
        if (text.endsWith("s")) return text.removeSuffix("s").toDoubleOrNull()?.let { (it * 1000.0).toLong() }
        if (text.contains(":")) {
            val parts = text.split(':')
            return when (parts.size) {
                2 -> {
                    val minutes = parts[0].toDoubleOrNull() ?: return null
                    val seconds = parts[1].replace(',', '.').toDoubleOrNull() ?: return null
                    ((minutes * 60.0 + seconds) * 1000.0).toLong()
                }

                3 -> {
                    val hours = parts[0].toDoubleOrNull() ?: return null
                    val minutes = parts[1].toDoubleOrNull() ?: return null
                    val seconds = parts[2].replace(',', '.').toDoubleOrNull() ?: return null
                    ((hours * 3600.0 + minutes * 60.0 + seconds) * 1000.0).toLong()
                }

                else -> null
            }
        }
        return text.replace(',', '.').toDoubleOrNull()?.let { (it * 1000.0).toLong() }
    }

    private fun formatSrt(millis: Long): String {
        val value = millis.coerceAtLeast(0)
        return "${pad(value / 3_600_000, 2)}:${pad((value / 60_000) % 60, 2)}:" +
            "${pad((value / 1_000) % 60, 2)},${pad(value % 1_000, 3)}"
    }

    private fun formatVtt(millis: Long): String = formatSrt(millis).replace(',', '.')

    private fun formatTtml(millis: Long): String {
        val value = millis.coerceAtLeast(0)
        return "${pad(value / 3_600_000, 2)}:${pad((value / 60_000) % 60, 2)}:" +
            "${pad((value / 1_000) % 60, 2)}.${pad(value % 1_000, 3)}"
    }

    private fun pad(value: Long, width: Int): String = value.toString().padStart(width, '0')
}
