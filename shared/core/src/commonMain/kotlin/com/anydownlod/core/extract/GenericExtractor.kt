/*
 * Generic extractor subset — AnyDownload
 *
 * This file is a small Kotlin translation of a subset of yt-dlp's generic
 * extractor, `yt_dlp/extractor/generic.py`, read at the upstream tag
 * `2026.08.19` (pip "yt-dlp==2026.8.19", the Android Chaquopy pin; commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf) on 2026-09-24.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. It covers only the behavior listed in
 * the class docs: raw `<video src>`, `<audio src>`, and nested `<source src>`
 * media candidates, plus `<embed src>`, `<iframe src>` (media targets only),
 * and JSON-LD `contentUrl`/`embedUrl`/`url` candidates, plus T-138 HLS/DASH
 * manifest discovery in `src`/`data-*` attributes and raw page text, all
 * resolved against the page URL. The meta-refresh target is parsed here;
 * `GenericIE` follows it at most one hop. Playlists, iframe crawling, and the
 * rest of generic.py are not translated. generic.py itself is not vendored;
 * see shared/core/NOTICE.md.
 *
 * The task notes that drive this translation: vault tasks T-045, T-137, and
 * T-138 (ADR-007 and ADR-014 in the planning vault).
 */
package com.anydownlod.core.extract

import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy
import kotlinx.serialization.json.JsonPrimitive

/**
 * Result of asking the generic extractor subset about one HTML page.
 *
 * The failure reasons are typed and never carry page content, so a failure
 * cannot leak HTML back into logs or the UI.
 */
sealed interface GenericExtraction {
    /** Exactly one media URL survived the policy; download it with the HTTP engine. */
    data class Direct(val url: String) : GenericExtraction

    data class Failed(val reason: GenericExtractionFailure) : GenericExtraction
}

enum class GenericExtractionFailure {
    /** The page URL is not an HTTP(S) URL, so nothing can be resolved against it. */
    UnsupportedPageUrl,

    /** No `<video>`/`<audio>` with a usable src was found, or none survived UrlPolicy. */
    NoMedia,

    /** More than one distinct media candidate survived UrlPolicy; fail closed. */
    MultipleMedia,
}

/**
 * The generic extractor subset: reads one simple HTML page and returns one
 * direct media URL, or a typed failure. It never downloads, never runs a
 * process, and never touches Python.
 *
 * Candidates are the `src` of `<video>` and `<audio>` elements, the `src` of
 * `<source>` elements nested inside those elements, the `src` of `<embed>`
 * and `<iframe>` elements when it names a direct media file (a non-media
 * iframe is never crawled), JSON-LD `contentUrl` plus `embedUrl`/`url` when
 * they resolve to media, and `.m3u8`/`.mpd` manifest URLs from `src`/`data-*`
 * attributes or raw text. Relative URLs resolve against the page URL (the
 * upstream `urljoin` behavior, with dot-segment normalization), and every
 * candidate must pass [UrlPolicy]. Zero surviving candidates and more than
 * one surviving candidate are both typed failures; the API does not guess.
 */
object GenericExtractor {

    /** One raw source and the shape its resolved URL must have to count. */
    private class RawSource(val value: String, val kind: SourceKind)

    private enum class SourceKind { Any, DirectMedia, Manifest }

    /** `<video ...>...</video>` or an unclosed/self-closing `<video ...>` tag. */
    private val videoElement = Regex(
        """<video\b[^>]*>.*?</video\s*>|<video\b[^>]*/?>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** `<audio ...>...</audio>` or an unclosed/self-closing `<audio ...>` tag. */
    private val audioElement = Regex(
        """<audio\b[^>]*>.*?</audio\s*>|<audio\b[^>]*/?>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** `<source ...>` tag. Only counts when nested in a video/audio element. */
    private val sourceTag = Regex("""<source\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** `<embed ...>` tag; only a direct media `src` becomes a candidate. */
    private val embedTag = Regex("""<embed\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** `<iframe ...>` tag; only a direct media `src` becomes a candidate. */
    private val iframeTag = Regex("""<iframe\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** The direct audio/video containers this slice downloads itself. */
    private val directMediaExtensions = setOf(
        "mp4", "m4v", "mov", "webm", "mkv", "avi", "flv", "3gp", "3g2", "ogv",
        "m4a", "mp3", "aac", "opus", "ogg", "oga", "flac", "wav", "weba",
        "ts", "m2ts", "mts",
    )

    /** The manifest containers the engine's M3u8/Mpd parsers consume. */
    private val manifestExtensions = setOf("m3u8", "mpd")

    /** `src` or any `data-*` attribute; manifest discovery reads their values. */
    private val manifestAttribute = Regex(
        """(?i)\b(?:src|data-[a-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
    )

    /**
     * A `.m3u8`/`.mpd` tail anywhere in the raw text. The scan expands
     * backwards to the token boundary and forwards through a query/fragment,
     * so a page without any manifest costs one linear pass (T-138).
     */
    private val manifestExtension = Regex("""\.(?:m3u8|mpd)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)

    /** The `src` attribute value: double-quoted, single-quoted, or bare. */
    private val srcAttribute = Regex(
        """\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @param candidateCheck policy for the page URL and every media candidate.
     *   Production uses [UrlPolicy] (the default). Callers with a strictly
     *   test-only fixture exception for exactly one local origin may pass
     *   their own check, mirroring [HttpDownloadEngine.urlCheck]; hosts must
     *   never weaken the default.
     */
    fun extract(
        pageUrl: String,
        html: String,
        candidateCheck: (String) -> UrlCheck = UrlPolicy::check,
    ): GenericExtraction {
        val page = pageUrl.trim()
        if (candidateCheck(page) !is UrlCheck.Allowed) {
            return GenericExtraction.Failed(GenericExtractionFailure.UnsupportedPageUrl)
        }

        val rawSources = mutableListOf<RawSource>()
        scanElements(videoElement, html, rawSources)
        scanElements(audioElement, html, rawSources)
        scanTagSrc(embedTag, html, rawSources)
        scanTagSrc(iframeTag, html, rawSources)
        scanJsonLd(html, rawSources)
        scanManifests(html, rawSources)

        // Resolve, drop non-media embed/iframe/JSON-LD targets, then dedupe in
        // first-seen order like the upstream orderedSet() the generic
        // extractor feeds its found URLs through.
        val seen = mutableSetOf<String>()
        val candidates = rawSources.asSequence()
            .mapNotNull { raw ->
                val value = cleanSource(raw.value)
                if (value.isEmpty()) null else resolveAgainst(page, value)?.let { raw.kind to it }
            }
            .filter { (kind, url) ->
                when (kind) {
                    SourceKind.Any -> true
                    SourceKind.DirectMedia -> isDirectMediaUrl(url)
                    SourceKind.Manifest -> isManifestUrl(url)
                }
            }
            .map { it.second }
            .filter(seen::add)
            .toList()

        val survivors = candidates.filter { candidateCheck(it) is UrlCheck.Allowed }
        return when (survivors.size) {
            0 -> GenericExtraction.Failed(GenericExtractionFailure.NoMedia)
            1 -> GenericExtraction.Direct(survivors.single())
            else -> GenericExtraction.Failed(GenericExtractionFailure.MultipleMedia)
        }
    }

    private fun scanElements(elementRegex: Regex, html: String, out: MutableList<RawSource>) {
        elementRegex.findAll(html).forEach { element ->
            // The opening tag is everything up to the first '>'.
            val openingTag = element.value.substringBefore('>') + ">"
            collectSrc(openingTag, out)
            sourceTag.findAll(element.value)
                .forEach { source -> collectSrc(source.value.substringBefore('>') + ">", out) }
        }
    }

    private fun collectSrc(tag: String, out: MutableList<RawSource>) {
        val match = srcAttribute.find(tag) ?: return
        val value = match.groupValues[1]
            .ifEmpty { match.groupValues[2] }
            .ifEmpty { match.groupValues[3] }
        if (value.isNotEmpty()) out += RawSource(value, SourceKind.Any)
    }

    /** Reads the `src` of [tagRegex] matches; only direct media files count. */
    private fun scanTagSrc(tagRegex: Regex, html: String, out: MutableList<RawSource>) {
        tagRegex.findAll(html).forEach { tag ->
            val match = srcAttribute.find(tag.value) ?: return@forEach
            val value = match.groupValues[1]
                .ifEmpty { match.groupValues[2] }
                .ifEmpty { match.groupValues[3] }
            if (value.isNotEmpty()) out += RawSource(value, SourceKind.DirectMedia)
        }
    }

    /**
     * JSON-LD `contentUrl` is the declared media file; `embedUrl` and `url`
     * only count when they name a direct media file (the upstream
     * `_search_json_ld` source, scoped by T-137).
     */
    private fun scanJsonLd(html: String, out: MutableList<RawSource>) {
        for (entry in JsonLd.entries(html)) {
            val content = (entry["contentUrl"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (!content.isNullOrBlank()) out += RawSource(content, SourceKind.Any)
            for (key in listOf("embedUrl", "url")) {
                val value = (entry[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (!value.isNullOrBlank()) out += RawSource(value, SourceKind.DirectMedia)
            }
        }
    }

    /**
     * Manifest discovery (T-138): `src`/`data-*` attribute values plus any
     * `.m3u8`/`.mpd` URL in the raw page text. Values still resolve against
     * the page URL and pass the caller's policy, and only URLs whose path
     * tail really is a manifest count.
     */
    private fun scanManifests(html: String, out: MutableList<RawSource>) {
        manifestAttribute.findAll(html).forEach { match ->
            val value = match.groupValues[1]
                .ifEmpty { match.groupValues[2] }
                .ifEmpty { match.groupValues[3] }
            if (value.isNotEmpty()) out += RawSource(value, SourceKind.Manifest)
        }
        for (match in manifestExtension.findAll(html)) {
            var start = match.range.first
            while (start > 0 && isUrlTokenChar(html[start - 1])) start--
            var end = match.range.last + 1
            if (end < html.length && (html[end] == '?' || html[end] == '#')) {
                end++
                while (end < html.length && isUrlTokenChar(html[end])) end++
            }
            if (start < end) out += RawSource(html.substring(start, end), SourceKind.Manifest)
        }
    }

    private fun isUrlTokenChar(char: Char): Boolean =
        !char.isWhitespace() && char !in "\"'<>`"

    /** True when [url] names a direct audio/video file this slice can fetch. */
    fun isDirectMediaUrl(url: String): Boolean =
        ExtractorUtils.determineExt(url).lowercase() in directMediaExtensions

    /** True when [url] names an HLS/DASH manifest the engine parses. */
    fun isManifestUrl(url: String): Boolean =
        ExtractorUtils.determineExt(url).lowercase() in manifestExtensions

    private val metaTag = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val metaRefreshUrl = Regex("""[0-9]{0,2}\s*;\s*(?:URL|url)\s*=\s*'?([^'"]+)""", RegexOption.IGNORE_CASE)

    /**
     * The raw meta-refresh target (`<meta http-equiv="refresh" content="0; url=...">`),
     * or null. Mirrors the upstream `REDIRECT_REGEX` shape; the caller resolves
     * it against the page URL and applies the policy. The `Refresh` response
     * header is not read (the port has no header access here).
     */
    internal fun metaRefreshTarget(html: String): String? {
        for (tag in metaTag.findAll(html)) {
            val equiv = attributeValue(tag.value, "http-equiv") ?: continue
            if (!equiv.equals("refresh", ignoreCase = true)) continue
            val content = attributeValue(tag.value, "content") ?: continue
            val match = metaRefreshUrl.find(content) ?: continue
            val target = ExtractorUtils.unescapeHtml(match.groupValues[1].trim())?.trim()
            if (!target.isNullOrEmpty()) return target
        }
        return null
    }

    private fun attributeValue(tag: String, name: String): String? {
        val pattern = Regex("""(?i)\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
        val match = pattern.find(tag) ?: return null
        return match.groupValues[1]
            .ifEmpty { match.groupValues[2] }
            .ifEmpty { match.groupValues[3] }
            .ifEmpty { null }
    }

    /** The upstream unescapeHTML plus its `\/` de-escape, scoped to this subset. */
    private fun cleanSource(raw: String): String {
        val unescaped = raw.trim()
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replaceUnicodeEscapes()
        return unescaped.replace("\\/", "/")
    }

    /** `&#xNN;` and `&#NN;` numeric character references, as upstream unescapeHTML does. */
    private fun String.replaceUnicodeEscapes(): String =
        NUMERIC_ENTITY.replace(this) { match ->
            val code = when {
                match.groupValues[1].isNotEmpty() -> match.groupValues[1].toIntOrNull(16)
                else -> match.groupValues[2].toIntOrNull(10)
            }
            code?.let { char -> char.toChar().toString() } ?: match.value
        }

    private val NUMERIC_ENTITY = Regex("&#(?:x([0-9a-fA-F]+)|([0-9]+));")

    /**
     * Resolves [ref] against [page] the way `urllib.parse.urljoin` does for
     * the URL shapes this subset accepts: absolute http(s), scheme-relative
     * `//host/...`, query/fragment-only, root-relative `/...`, and relative
     * paths with `.`/`..` segments. [page] is validated http(s) by the caller.
     */
    internal fun resolveAgainst(page: String, ref: String): String? {
        val lower = ref.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return ref
        if (lower.startsWith("//")) return page.substringBefore("://") + ":" + ref
        // Any other scheme (ftp:, mailto:, ...) stays absolute so UrlPolicy can refuse it.
        if (ref.contains("://")) return ref

        val marker = page.indexOf("://")
        if (marker < 0) return null
        val scheme = page.substring(0, marker)
        val afterScheme = page.substring(marker + 3)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val path = afterScheme.substringAfter('/', "")
            .substringBefore('?').substringBefore('#')

        if (ref.startsWith("?")) {
            val base = scheme + "://" + authority + "/" + path
            return base.substringBefore('?').substringBefore('#') + ref
        }
        if (ref.startsWith("#")) {
            return scheme + "://" + authority + "/" + path + ref
        }
        if (ref.startsWith("/")) {
            return scheme + "://" + authority + ref
        }

        val directory = if (path.isEmpty()) "" else path.substringBeforeLast('/', "")
        val joined = if (directory.isEmpty()) ref else "$directory/$ref"
        val withoutQuery = joined.substringBefore('?').substringBefore('#')
        val suffix = joined.substring(withoutQuery.length)
        val normalized = removeDotSegments(scheme + "://" + authority + "/" + withoutQuery)
        return normalized + suffix
    }

    /** RFC 3986-style removal of `.` and `..` path segments. */
    internal fun removeDotSegments(path: String): String {
        val segments = path.split('/').toMutableList()
        val output = mutableListOf<String>()
        while (segments.isNotEmpty()) {
            val segment = segments.removeAt(0)
            when (segment) {
                "." -> Unit
                ".." -> if (output.isNotEmpty()) output.removeAt(output.lastIndex)
                else -> output.add(segment)
            }
        }
        return output.joinToString("/")
    }
}