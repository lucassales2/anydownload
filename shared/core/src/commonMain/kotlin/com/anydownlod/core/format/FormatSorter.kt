/*
 * Format sorter — AnyDownload
 *
 * Translation of the `FormatSorter` field tables and preference tuple logic
 * from `yt_dlp/utils/_utils.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), re-read 2026-09-24, plus the
 * `_fill_sorting_fields` derivations. Unlicense; see shared/core/NOTICE.md.
 *
 * Upstream sorts formats ascending by a preference tuple and lets the
 * selector reverse the list for `best*`; this port keeps that contract.
 * Fields the port does not fill (`hidden`, extractor `preference`) stay at
 * their upstream defaults instead of being invented.
 */
package com.anydownlod.core.format

import com.anydownlod.core.extract.MediaFormat
import kotlin.math.abs

/** One sort key, from `-S`/`format_sort` syntax (`+fps`, `res:720`, `vcodec:vp9`). */
data class SortField(
    val name: String,
    val reverse: Boolean = false,
    val closest: Boolean = false,
    val limitText: String? = null,
    /** The upstream-resolved limit: an ordered preference or a parsed number. */
    val limit: Double? = null,
)

object FormatSorter {
    /**
     * Upstream `FormatSorter.default`. `hdr:12` caps the ordered HDR field at
     * HDR12's preference, exactly as upstream's default tuple does.
     */
    val DEFAULT_FIELDS: List<String> = listOf(
        "hidden", "aud_or_vid", "hasvid", "ie_pref", "lang", "quality", "res", "fps",
        "hdr:12", "vcodec", "channels", "acodec", "size", "br", "asr", "proto", "ext",
        "hasaud", "source", "id",
    )

    private val fieldPattern = Regex("^\\s*([+-])?([a-zA-Z0-9_]+)(?:([~:])(.*?))?\\s*$")

    private val aliases = mapOf(
        "format_id" to "id",
        "preference" to "ie_pref",
        "language_preference" to "lang",
        "source_preference" to "source",
        "protocol" to "proto",
        "filesize_approx" to "fs_approx",
        "audio_channels" to "channels",
        "total_bitrate" to "tbr",
        "video_bitrate" to "vbr",
        "audio_bitrate" to "abr",
        "framerate" to "fps",
        "video_ext" to "vext",
        "audio_ext" to "aext",
        "dimension" to "res",
        "resolution" to "res",
        "extension" to "ext",
        "video_codec" to "vcodec",
        "audio_codec" to "acodec",
        "video" to "hasvid",
        "has_video" to "hasvid",
        "audio" to "hasaud",
        "has_audio" to "hasaud",
        "extractor" to "ie_pref",
    )

    /** The ordered tables exactly as upstream declares them. */
    private val vcodecOrder = listOf(
        "av0?1", "vp0?9\\.0?2", "vp0?9", "[hx]265|he?vc?", "[hx]264|avc", "vp0?8",
        "mp4v|h263", "theora", "", null, "none",
    )
    private val acodecOrder = listOf(
        "[af]lac", "wav|aiff", "opus", "vorbis|ogg", "aac", "mp?4a?", "mp3", "ac-?4",
        "e-?a?c-?3", "ac-?3", "dts", "", null, "none",
    )
    private val hdrOrder = listOf("dv", "(hdr)?12", "(hdr)?10\\+", "(hdr)?10", "hlg", "", "sdr", null)
    private val protoOrder = listOf(
        "(ht|f)tps", "(ht|f)tp$", "m3u8.*", ".*dash", "websocket_frag", "rtmpe?", "",
        "ws|websocket", "f4",
    )
    private val vextOrder = listOf("mp4", "mov", "webm", "flv", "", "none")
    private val aextOrder = listOf("m4a", "aac", "mp3", "ogg", "opus", "web[am]", "", "none")

    /**
     * Parses user sort fields (`-S` values) and appends the defaults that were
     * not already named, mirroring upstream `evaluate_params`' `add_item`
     * de-duplication. `ext` expands to `vext` and `aext`.
     */
    fun order(userFields: List<String>, extractorFields: List<String> = emptyList()): List<SortField> {
        val ordered = mutableListOf<SortField>()
        val seen = mutableSetOf<String>()
        for (raw in userFields + extractorFields + DEFAULT_FIELDS) {
            val parsed = parseSortField(raw) ?: continue
            for (field in expand(parsed)) {
                if (seen.add(field.name)) ordered += resolveLimit(field)
            }
        }
        return ordered
    }

    /** Ascending preference sort; the selector reverses for the `best` side. */
    fun sort(formats: List<MediaFormat>, order: List<SortField>): List<MediaFormat> =
        formats.map { SortableFormat.from(it) }
            .sortedWith { left, right -> compare(left, right, order) }
            .map { it.format }

    // ------------------------------------------------------------- internals

    private fun parseSortField(raw: String): SortField? {
        val match = fieldPattern.matchEntire(raw) ?: return null
        val name = match.groupValues[2].lowercase()
        if (name.isEmpty()) return null
        return SortField(
            name = aliases[name] ?: name,
            reverse = match.groupValues[1].isNotEmpty(),
            closest = match.groupValues[3] == "~",
            limitText = match.groupValues[4].ifEmpty { null },
        )
    }

    private fun expand(field: SortField): List<SortField> = when (field.name) {
        "ext" -> listOf(field.copy(name = "vext"), field.copy(name = "aext"))
        else -> listOf(field)
    }

    private fun resolveLimit(field: SortField): SortField {
        val text = field.limitText ?: return field
        val resolved = when (field.name) {
            "vcodec" -> orderedPreference(text, vcodecOrder, regex = true)
            "acodec" -> orderedPreference(text, acodecOrder, regex = true)
            "hdr" -> orderedPreference(text, hdrOrder, regex = true)
            "proto" -> orderedPreference(text, protoOrder, regex = true)
            "vext" -> orderedPreference(text, vextOrder, regex = false)
            "aext" -> orderedPreference(text, aextOrder, regex = true)
            else -> parseFilesize(text) ?: text.toDoubleOrNull()
        }
        return field.copy(limit = resolved)
    }

    private fun compare(left: SortableFormat, right: SortableFormat, order: List<SortField>): Int {
        for (field in order) {
            val result = preference(left, field).compareTo(preference(right, field))
            if (result != 0) return result
        }
        return 0
    }

    private fun preference(format: SortableFormat, field: SortField): FieldPreference = when (field.name) {
        "hidden" -> FieldPreference.Missing
        "aud_or_vid" -> FieldPreference.fromTuple(
            if (format.format.vcodec != MediaFormat.CODEC_NONE ||
                format.format.acodec != MediaFormat.CODEC_NONE
            ) {
                1.0
            } else {
                0.0
            },
            field,
        )

        "hasvid" -> FieldPreference.fromTuple(booleanValue(format.format.vcodec), field)
        "hasaud" -> FieldPreference.fromTuple(booleanValue(format.format.acodec), field)
        "ie_pref" -> FieldPreference.fromTuple(format.format.preference?.toDouble() ?: -1.0, field)
        "lang" -> FieldPreference.fromTuple(format.format.languagePreference ?: -1.0, field)
        "quality" -> FieldPreference.fromTuple(format.format.quality?.toDoubleOrNull() ?: -1.0, field)
        "res" -> {
            val height = format.format.height?.toDouble()
            val width = format.format.width?.toDouble()
            FieldPreference.fromTuple(listOfNotNull(height, width).minOrNull() ?: 0.0, field)
        }

        "fps" -> FieldPreference.number(format.format.fps, field)
        "hdr" -> FieldPreference.fromTuple(orderedPreference(format.format.dynamicRange, hdrOrder, true), field)
        "vcodec" -> FieldPreference.fromTuple(orderedPreference(format.format.vcodec, vcodecOrder, true), field)
        "channels" -> FieldPreference.number(format.format.audioChannels?.toDouble(), field)
        "acodec" -> FieldPreference.fromTuple(orderedPreference(format.format.acodec, acodecOrder, true), field)
        "size" -> FieldPreference.number(
            (format.format.filesize ?: format.format.filesizeApprox)?.toDouble(),
            field,
        )

        "br" -> FieldPreference.number(format.tbr ?: format.vbr ?: format.abr, field)
        "asr" -> FieldPreference.number(format.format.asr?.toDouble(), field)
        "proto" -> FieldPreference.fromTuple(orderedPreference(format.protocol, protoOrder, true), field)
        "vext" -> FieldPreference.fromTuple(orderedPreference(format.videoExt, vextOrder, false), field)
        "aext" -> FieldPreference.fromTuple(orderedPreference(format.audioExt, aextOrder, true), field)
        "source" -> FieldPreference.fromTuple(format.format.sourcePreference?.toDouble() ?: -1.0, field)
        "id" -> format.format.formatId?.let { FieldPreference.Text(it) } ?: FieldPreference.Missing
        else -> FieldPreference.Missing
    }

    private fun booleanValue(codec: String?): Double = if (codec != MediaFormat.CODEC_NONE) 0.0 else -1.0

    /** Upstream `_resolve_field_value` for `convert: order`. */
    private fun orderedPreference(value: String?, order: List<String?>, regex: Boolean): Double {
        val listLength = order.size
        val emptyPos = order.indexOfFirst { it == "" }.let { if (it >= 0) it else listLength + 1 }
        val lower = value?.lowercase()
        if (regex && lower != null) {
            for ((index, pattern) in order.withIndex()) {
                if (!pattern.isNullOrEmpty() && Regex("^$pattern").containsMatchIn(lower)) {
                    return (listLength - index).toDouble()
                }
            }
            return (listLength - emptyPos).toDouble()
        }
        val index = order.indexOf(lower)
        return (if (index >= 0) listLength - index else listLength - emptyPos).toDouble()
    }

    /** Upstream `_calculate_field_preference_from_value` as a comparable value. */
    private sealed interface FieldPreference : Comparable<FieldPreference> {
        data object Missing : FieldPreference
        data class Num(val group: Int, val first: Double, val second: Double) : FieldPreference
        data class Text(val value: String) : FieldPreference

        override fun compareTo(other: FieldPreference): Int = when {
            this is Missing && other is Missing -> 0
            this is Missing -> -1
            other is Missing -> 1
            this is Num && other is Num ->
                compareValuesBy(this, other, { it.group }, { it.first }, { it.second })

            this is Text && other is Text -> value.compareTo(other.value)
            this is Num -> -1
            else -> 1
        }

        companion object {
            /** A `convert: float_none` field: null stays Missing. */
            fun number(value: Double?, field: SortField): FieldPreference =
                if (value == null) Missing else fromTuple(value, field)

            /** The upstream tuple for a present numeric value. */
            fun fromTuple(value: Double, field: SortField): FieldPreference {
                val limit = field.limit
                return when {
                    field.closest && limit != null ->
                        Num(0, -abs(value - limit), if (field.reverse) value - limit else limit - value)

                    !field.reverse && (limit == null || value <= limit) -> Num(0, value, 0.0)
                    limit == null || (field.reverse && value == limit) || value > limit -> Num(0, -value, 0.0)
                    else -> Num(-1, value, 0.0)
                }
            }
        }
    }

    private class SortableFormat(
        val format: MediaFormat,
        val protocol: String,
        val videoExt: String,
        val audioExt: String,
        val vbr: Double?,
        val abr: Double?,
        val tbr: Double?,
    ) {
        companion object {
            fun from(format: MediaFormat): SortableFormat {
                val protocol = format.protocol ?: determineProtocol(format)
                val ext = format.ext ?: determineExt(format.url)
                val videoExt: String
                val audioExt: String
                if (format.vcodec == MediaFormat.CODEC_NONE) {
                    videoExt = "none"
                    audioExt = if (format.acodec != MediaFormat.CODEC_NONE) ext else "none"
                } else {
                    videoExt = ext
                    audioExt = "none"
                }
                var vbr = format.vbr
                var abr = format.abr
                var tbr = format.tbr
                if (format.vcodec == MediaFormat.CODEC_NONE) vbr = 0.0
                if (format.acodec == MediaFormat.CODEC_NONE) abr = 0.0
                if (vbr == null && format.vcodec != MediaFormat.CODEC_NONE && tbr != null && abr != null) {
                    vbr = tbr - abr
                }
                if (abr == null && format.acodec != MediaFormat.CODEC_NONE && tbr != null && vbr != null) {
                    abr = tbr - vbr
                }
                if (tbr == null && vbr != null && abr != null) tbr = vbr + abr
                return SortableFormat(format, protocol, videoExt, audioExt, vbr, abr, tbr)
            }

            private fun determineProtocol(format: MediaFormat): String {
                val url = format.url.orEmpty()
                val ext = format.ext.orEmpty()
                return when {
                    url.startsWith("rtmp") -> "rtmp"
                    ext.startsWith("m3u8") || url.contains(".m3u8") -> "m3u8_native"
                    ext.startsWith("mpd") || url.contains(".mpd") -> "http_dash_segments"
                    url.startsWith("http") -> url.substringBefore("://")
                    else -> "http"
                }
            }

            private fun determineExt(url: String?): String {
                val path = url?.substringBefore('?')?.substringBefore('#') ?: return "none"
                val name = path.substringAfterLast('/', "")
                val ext = name.substringAfterLast('.', "")
                return ext.lowercase().ifEmpty { "none" }
            }
        }
    }
}
