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
 * media candidates resolved against the page URL. Playlists, embeds, iframes,
 * JSON-LD, meta refresh, HLS, DASH, and the rest of generic.py are not
 * translated. generic.py itself is not vendored; see shared/core/NOTICE.md.
 *
 * The task note that drives this translation: vault tasks T-045 and
 * ADR-007 in the planning vault.
 */
package com.anydownlod.core.extract

import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy

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
 * Candidates are the `src` of `<video>` and `<audio>` elements plus the `src`
 * of `<source>` elements nested inside those elements. Relative URLs resolve
 * against the page URL (the upstream `urljoin` behavior, with dot-segment
 * normalization), and every candidate must pass [UrlPolicy]. Zero surviving
 * candidates and more than one surviving candidate are both typed failures;
 * the API does not guess.
 */
object GenericExtractor {

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

        val rawSources = mutableListOf<String>()
        scanElements(videoElement, html, rawSources)
        scanElements(audioElement, html, rawSources)

        // Dedupe in first-seen order, like the upstream orderedSet() the
        // generic extractor feeds its found URLs through.
        val seen = mutableSetOf<String>()
        val candidates = rawSources.asSequence()
            .map(::cleanSource)
            .filter { it.isNotEmpty() }
            .filter(seen::add)
            .mapNotNull { resolveAgainst(page, it) }
            .toList()

        val survivors = candidates.filter { candidateCheck(it) is UrlCheck.Allowed }
        return when (survivors.size) {
            0 -> GenericExtraction.Failed(GenericExtractionFailure.NoMedia)
            1 -> GenericExtraction.Direct(survivors.single())
            else -> GenericExtraction.Failed(GenericExtractionFailure.MultipleMedia)
        }
    }

    private fun scanElements(elementRegex: Regex, html: String, out: MutableList<String>) {
        elementRegex.findAll(html).forEach { element ->
            // The opening tag is everything up to the first '>'.
            val openingTag = element.value.substringBefore('>') + ">"
            collectSrc(openingTag, out)
            sourceTag.findAll(element.value)
                .forEach { source -> collectSrc(source.value.substringBefore('>') + ">", out) }
        }
    }

    private fun collectSrc(tag: String, out: MutableList<String>) {
        val match = srcAttribute.find(tag) ?: return
        val value = match.groupValues[1]
            .ifEmpty { match.groupValues[2] }
            .ifEmpty { match.groupValues[3] }
        if (value.isNotEmpty()) out += value
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