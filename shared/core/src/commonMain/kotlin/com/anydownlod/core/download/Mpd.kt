/*
 * DASH MPD parser — AnyDownload (T-073)
 *
 * Translation of the non-live, non-DRM subset of
 * `yt_dlp/extractor/common.py` `_parse_mpd_formats` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-24. Unlicense; see shared/core/NOTICE.md. `SegmentTemplate`
 * (`$Number$`/`$Time$`), `SegmentList`, and `SegmentBase` initialization
 * ranges are translated; DRM (`hasDrm`) and live MPDs fail typed. No FFmpeg.
 */
package com.anydownlod.core.download

import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.GenericExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.MediaFragment
import kotlin.math.ceil

sealed interface MpdResult {
    data class Formats(val formats: List<MediaFormat>) : MpdResult
    data class Failed(val reason: String) : MpdResult
}

object Mpd {

    fun parse(url: String, xml: String): MpdResult {
        if (xml.contains("ContentProtection", ignoreCase = true)) {
            return MpdResult.Failed("DRM-protected DASH manifests are not supported.")
        }
        if (xml.contains("type=\"dynamic\"", ignoreCase = true)) {
            return MpdResult.Failed("Live DASH manifests are not supported.")
        }
        val totalDuration = durationOf(attribute(Regex("<MPD\\b([^>]*)>").find(xml)?.groupValues?.get(1), "mediaPresentationDuration"))
        val formats = mutableListOf<MediaFormat>()
        for (adaptation in blocks(xml, "AdaptationSet")) {
            val adaptationAttributes = adaptation.first
            for (representation in representations(adaptation.second)) {
                val attributes = representation.first
                val body = representation.second.ifBlank { adaptation.second }
                val id = attributes["id"] ?: attributes["mimeType"]?.substringAfter('/') ?: "dash"
                val bandwidth = attributes["bandwidth"]?.toLongOrNull()
                val codecs = ExtractorUtils.parseCodecs((attributes["codecs"] ?: adaptationAttributes["codecs"])?.let { "codecs=$it" })
                val mimeType = attributes["mimeType"] ?: adaptationAttributes["mimeType"]
                val periodDuration = durationOf(attribute(Regex("<Period\\b([^>]*)>").find(xml)?.groupValues?.get(1), "duration")) ?: totalDuration
                val fragments = fragmentsOf(url, id, bandwidth, body, periodDuration)
                    ?: return MpdResult.Failed("DASH representation '$id' has no usable segment information.")
                formats += MediaFormat(
                    formatId = id,
                    url = url,
                    protocol = "http_dash_segments",
                    vcodec = codecs.vcodec ?: if (mimeType?.startsWith("video/") == true) mimeType else null,
                    acodec = codecs.acodec ?: if (mimeType?.startsWith("audio/") == true) mimeType else null,
                    width = attributes["width"]?.toLongOrNull(),
                    height = attributes["height"]?.toLongOrNull(),
                    tbr = bandwidth?.let { it / 1000.0 },
                    manifestUrl = url,
                    fragments = fragments,
                    formatNote = "DASH representation",
                )
            }
        }
        return if (formats.isEmpty()) MpdResult.Failed("The MPD declared no representations.") else MpdResult.Formats(formats)
    }

    private fun fragmentsOf(
        manifestUrl: String,
        id: String,
        bandwidth: Long?,
        body: String,
        periodDuration: Double?,
    ): List<MediaFragment>? {
        blocks(body, "SegmentTemplate").firstOrNull()?.let { (attributes, _) ->
            val media = attributes["media"] ?: return@let
            val timescale = attributes["timescale"]?.toDoubleOrNull() ?: 1.0
            val segment = attributes["duration"]?.toDoubleOrNull() ?: return@let
            if (segment <= 0) return@let
            val periodSeconds = periodDuration ?: return@let
            val startNumber = attributes["startNumber"]?.toLongOrNull() ?: 1L
            val presentationOffset = attributes["presentationTimeOffset"]?.toDoubleOrNull() ?: 0.0
            val count = ceil(periodSeconds * timescale / segment).toLong()
            val fragments = mutableListOf<MediaFragment>()
            val baseTemplate = media
                .replace("\$RepresentationID\$", id)
                .replace("\$Bandwidth\$", bandwidth?.toString() ?: "")
            attributes["initialization"]?.let { init ->
                val initUrl = init
                    .replace("\$RepresentationID\$", id)
                    .replace("\$Bandwidth\$", bandwidth?.toString() ?: "")
                fragments += MediaFragment(resolve(manifestUrl, initUrl))
            }
            when {
                baseTemplate.contains("\$Number\$") -> for (index in 0 until count) {
                    val number = startNumber + index
                    fragments += MediaFragment(resolve(manifestUrl, baseTemplate.replace("\$Number\$", number.toString())))
                }

                baseTemplate.contains("\$Time\$") -> for (index in 0 until count) {
                    val time = (presentationOffset + index * segment).toLong()
                    fragments += MediaFragment(resolve(manifestUrl, baseTemplate.replace("\$Time\$", time.toString())))
                }

                else -> fragments += MediaFragment(resolve(manifestUrl, baseTemplate))
            }
            return fragments
        }
        val segmentUrls = Regex("<SegmentURL\\b([^>]*)/?>", RegexOption.IGNORE_CASE)
            .findAll(body)
            .mapNotNull { match ->
                val attributes = parseAttributes(match.groupValues[1])
                val media = attributes["media"] ?: return@mapNotNull null
                val range = attributes["mediaRange"]?.split('-')?.mapNotNull { it.toLongOrNull() }
                MediaFragment(resolve(manifestUrl, media), range?.getOrNull(0), range?.getOrNull(1))
            }
            .toList()
        if (segmentUrls.isNotEmpty()) return segmentUrls
        Regex("<Initialization\\b([^>]*)/?>", RegexOption.IGNORE_CASE).find(body)?.let { match ->
            val range = parseAttributes(match.groupValues[1])["range"]?.split('-')?.mapNotNull { it.toLongOrNull() }
            if (range != null) {
                return listOf(MediaFragment(manifestUrl, range.getOrNull(0), range.getOrNull(1)))
            }
        }
        return null
    }

    private fun substitute(template: String, id: String, bandwidth: Long?, number: Long, time: Long): String =
        template
            .replace("\$RepresentationID\$", id)
            .replace("\$Bandwidth\$", bandwidth?.toString() ?: "")
            .replace("\$Number\$", number.toString())
            .replace("\$Time\$", time.toString())

    private fun representations(body: String): List<Pair<Map<String, String>, String>> {
        val selfClosing = Regex("<Representation\\b([^>]*)/>", RegexOption.IGNORE_CASE)
            .findAll(body)
            .map { parseAttributes(it.groupValues[1]) to "" }
        val withBody = Regex("<Representation\\b([^>]*)>([\\s\\S]*?)</Representation>", RegexOption.IGNORE_CASE)
            .findAll(body)
            .map { parseAttributes(it.groupValues[1]) to it.groupValues[2] }
        return (selfClosing + withBody).toList()
    }

    private fun blocks(xml: String, tag: String): List<Pair<Map<String, String>, String>> {
        val selfClosing = Regex("<$tag\\b([^>]*?)/>", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .map { parseAttributes(it.groupValues[1]) to "" }
        val paired = Regex("<$tag\\b([^>]*)>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .map { parseAttributes(it.groupValues[1]) to it.groupValues[2] }
        return (selfClosing + paired).toList()
    }

    private fun attribute(attributes: String?, name: String): String? =
        attributes?.let { parseAttributes(it)[name] }

    private fun parseAttributes(value: String): Map<String, String> =
        Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*\"([^\"]*)\"")
            .findAll(value)
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** ISO 8601 durations such as `PT1H2M3.5S`; days are ignored. */
    private fun durationOf(value: String?): Double? {
        val text = value?.trim() ?: return null
        val match = Regex("^P(?:([0-9.]+)D)?T(?:([0-9.]+)H)?(?:([0-9.]+)M)?(?:([0-9.]+)S)?\$").find(text) ?: return null
        val days = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val hours = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[3].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[4].toDoubleOrNull() ?: 0.0
        return days * 86_400 + hours * 3600 + minutes * 60 + seconds
    }

    private fun resolve(base: String, reference: String): String =
        GenericExtractor.resolveAgainst(base, reference) ?: reference
}
