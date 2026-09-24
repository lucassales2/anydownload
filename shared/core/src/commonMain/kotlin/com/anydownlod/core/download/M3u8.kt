/*
 * HLS playlist parser — AnyDownload (T-073)
 *
 * Translation of the non-live, non-FFmpeg paths of
 * `yt_dlp/downloader/hls.py` and `yt_dlp/extractor/common.py`
 * `_parse_m3u8_formats` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24. Unlicense; see
 * shared/core/NOTICE.md. Live playlists, SAMPLE-AES, and other key methods
 * fail typed; no FFmpeg is involved.
 */
package com.anydownlod.core.download

import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.GenericExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.MediaFragment

/** One `EXT-X-KEY` AES-128 declaration; the key bytes are fetched later. */
data class Aes128KeyInfo(
    val uri: String,
    val iv: ByteArray?,
)

sealed interface ManifestResult {
    /** A master playlist resolved to variants. */
    data class Master(val formats: List<MediaFormat>) : ManifestResult

    /** A media playlist resolved to fragments. */
    data class Media(
        val fragments: List<MediaFragment>,
        val initSegment: MediaFragment?,
        val key: Aes128KeyInfo?,
    ) : ManifestResult

    data class Failed(val reason: String) : ManifestResult
}

object M3u8 {

    fun parse(url: String, text: String): ManifestResult {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val variants = lines.filter { it.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) }
        return if (variants.isNotEmpty()) parseMaster(url, lines) else parseMedia(url, lines)
    }

    private fun parseMaster(url: String, lines: List<String>): ManifestResult {
        val audioGroups = mutableMapOf<String, String>()
        val formats = mutableListOf<MediaFormat>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("#EXT-X-MEDIA:", ignoreCase = true)) {
                val attributes = attributes(line.substringAfter(':'))
                val type = attributes["TYPE"]?.uppercase()
                val group = attributes["GROUP-ID"]
                val uri = attributes["URI"]
                if (type == "AUDIO" && group != null && uri != null) {
                    audioGroups[group] = resolve(url, uri)
                }
            } else if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                val attributes = attributes(line.substringAfter(':'))
                var uriIndex = index + 1
                while (uriIndex < lines.size && lines[uriIndex].startsWith("#")) uriIndex++
                if (uriIndex >= lines.size) return ManifestResult.Failed("The master playlist has a variant without a URI.")
                val variantUrl = resolve(url, lines[uriIndex])
                val bandwidth = attributes["BANDWIDTH"]?.toLongOrNull()
                val codecs = ExtractorUtils.parseCodecs(attributes["CODECS"]?.let { "codecs=$it" })
                val resolution = attributes["RESOLUTION"]?.lowercase()?.split('x')
                val videoOnly = codecs.vcodec != null && codecs.acodec == null
                formats += MediaFormat(
                    formatId = bandwidth?.toString(),
                    url = variantUrl,
                    protocol = "m3u8_native",
                    vcodec = codecs.vcodec,
                    acodec = codecs.acodec,
                    width = resolution?.getOrNull(0)?.toLongOrNull(),
                    height = resolution?.getOrNull(1)?.toLongOrNull(),
                    tbr = bandwidth?.let { it / 1000.0 },
                    manifestUrl = url,
                    formatNote = "HLS variant",
                )
                val group = attributes["AUDIO"]
                if (videoOnly && group != null && audioGroups.containsKey(group)) {
                    formats += MediaFormat(
                        formatId = "audio-$group",
                        url = audioGroups.getValue(group),
                        protocol = "m3u8_native",
                        vcodec = "none",
                        manifestUrl = url,
                        formatNote = "HLS audio group",
                    )
                }
                index = uriIndex
            }
            index++
        }
        return if (formats.isEmpty()) {
            ManifestResult.Failed("The master playlist declared no variants.")
        } else {
            ManifestResult.Master(formats)
        }
    }

    private fun parseMedia(url: String, lines: List<String>): ManifestResult {
        var ended = false
        var mediaSequence = 0L
        var currentKey: Aes128KeyInfo? = null
        var pendingRange: Pair<Long, Long>? = null
        var lastRangeEnd = -1L
        var pendingMap: Pair<String, Pair<Long, Long>?>? = null
        var initSegment: MediaFragment? = null
        val fragments = mutableListOf<MediaFragment>()

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-ENDLIST", ignoreCase = true) -> ended = true
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:", ignoreCase = true) ->
                    mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L

                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    val attributes = attributes(line.substringAfter(':'))
                    val uri = attributes["URI"] ?: return ManifestResult.Failed("EXT-X-MAP has no URI.")
                    pendingMap = resolve(url, uri) to rangeOf(attributes["BYTERANGE"])
                }

                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val attributes = attributes(line.substringAfter(':'))
                    val method = attributes["METHOD"]?.uppercase() ?: "NONE"
                    currentKey = when (method) {
                        "NONE" -> null
                        "AES-128" -> {
                            val uri = attributes["URI"]
                                ?: return ManifestResult.Failed("An AES-128 key has no URI.")
                            Aes128KeyInfo(resolve(url, uri), ivOf(attributes["IV"]))
                        }

                        else -> return ManifestResult.Failed("Unsupported HLS key method '$method'.")
                    }
                }

                line.startsWith("#EXT-X-BYTERANGE:", ignoreCase = true) -> {
                    val text = line.substringAfter(':').trim()
                    val length = text.substringBefore('@').toLongOrNull()
                    if (length != null) {
                        val offset = text.substringAfter('@', "").toLongOrNull() ?: (lastRangeEnd + 1)
                        pendingRange = offset to (offset + length - 1)
                    }
                }

                line.startsWith("#") -> Unit
                else -> {
                    val map = pendingMap
                    if (map != null) {
                        initSegment = MediaFragment(map.first, map.second?.first, map.second?.second)
                        pendingMap = null
                    }
                    val range = pendingRange
                    fragments += MediaFragment(
                        url = resolve(url, line),
                        rangeStart = range?.first,
                        rangeEnd = range?.second,
                        sequence = mediaSequence + fragments.size,
                    )
                    if (range != null) lastRangeEnd = range.second
                    pendingRange = null
                }
            }
        }
        if (!ended) return ManifestResult.Failed("Live HLS playlists are not supported.")
        if (fragments.isEmpty()) return ManifestResult.Failed("The media playlist declared no fragments.")
        return ManifestResult.Media(fragments, initSegment, currentKey)
    }

    /** `n@o`, `n`, or null. */
    private fun rangeOf(value: String?): Pair<Long, Long>? {
        val text = value?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() } ?: return null
        val length = text.substringBefore('@').toLongOrNull() ?: return null
        val offset = text.substringAfter('@', "0").toLongOrNull() ?: 0L
        return offset to (offset + length - 1)
    }

    private fun ivOf(value: String?): ByteArray? {
        val text = value?.trim()?.removePrefix("0x")?.removePrefix("0X") ?: return null
        if (text.length != 32) return null
        return runCatching {
            ByteArray(16) { index -> text.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    private fun attributes(value: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < value.length) {
            val equals = value.indexOf('=', index)
            if (equals < 0) break
            val name = value.substring(index, equals).trim().trimStart(',').uppercase()
            val valueStart = equals + 1
            if (valueStart >= value.length) break
            if (value[valueStart] == '"') {
                val end = value.indexOf('"', valueStart + 1)
                if (end < 0) break
                result[name] = value.substring(valueStart + 1, end)
                index = end + 1
            } else {
                val end = value.indexOf(',', valueStart).let { if (it < 0) value.length else it }
                result[name] = value.substring(valueStart, end).trim()
                index = end
            }
        }
        return result
    }

    private fun resolve(base: String, reference: String): String =
        GenericExtractor.resolveAgainst(base, reference) ?: reference
}
