/*
 * Unsupported extractors — AnyDownload
 *
 * Kotlin translation of `unsupported.py` from
 * `yt_dlp/extractor/unsupported.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `unsupported.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the three known-unsupported URL lists. Each URL form matches and
 * fails typed Unavailable with the upstream reason (DRM protection, piracy,
 * or liability), so the engine reports the same decision instead of falling
 * through to the generic extractor. No URL is downloaded and no cookie,
 * token, or media URL is stored here.
 */
package com.anydownload.core.extract.unsupported

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `KnownDRMIE`: sites that use DRM for all their videos. */
class KnownDRMIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.Unavailable(
            "The requested site is known to use DRM protection and will not be supported.",
        )
    }

    companion object {
        const val IE_KEY: String = "KnownDRM"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:" +
                "play\\.hbomax\\.com|channel(?:4|5)\\.com|peacocktv\\.com|" +
                "(?:[\\w.]+\\.)?disneyplus\\.com|open\\.spotify\\.com|tvnz\\.co\\.nz|oneplus\\.ch|" +
                "artstation\\.com/learning/courses|philo\\.com|(?:[\\w.]+\\.)?mech-plus\\.com|" +
                "aha\\.video|mubi\\.com|vootkids\\.com|nowtv\\.it/watch|tv\\.apple\\.com|" +
                "primevideo\\.com|hulu\\.com|resource\\.inkryptvideos\\.com|joyn\\.de|" +
                "amazon\\.(?:\\w{2}\\.)?\\w+/gp/video|music\\.amazon\\.(?:\\w{2}\\.)?\\w+|" +
                "(?:watch|front)\\.njpwworld\\.com|qub\\.ca/vrai|(?:beta\\.)?crunchyroll\\.com|" +
                "viki\\.com|deezer\\.com|b-ch\\.com|ctv\\.ca|noovo\\.ca|tsn\\.ca|" +
                "paramountplus\\.com|(?:m\\.)?(?:sony)?crackle\\.com|cw(?:tv(?:pr)?|seed)\\.com|" +
                "6play\\.fr|rtlplay\\.be|play\\.rtl\\.hr|rtlmost\\.hu|plus\\.rtl\\.de(?!/podcast/)|" +
                "mediasetinfinity\\.es|tv5mondeplus\\.com|tv\\.rakuten\\.co\\.jp|" +
                "watch\\.telusoriginals\\.com|video\\.unext\\.jp|www\\.web\\.nhk|" +
                "fod\\.fujitv\\.co\\.jp|zee5\\.com" +
                ")",
        )
    }
}

/** Upstream `KnownPiracyIE`: sites that were once supported. */
class KnownPiracyIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.Unavailable(
            "This website is no longer supported since it has been determined to be primarily " +
                "used for piracy.",
        )
    }

    companion object {
        const val IE_KEY: String = "KnownPiracy"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:" +
                "dood\\.(?:to|watch|so|pm|wf|re)|viewsb\\.com|filemoon\\.sx|" +
                "hentai\\.animestigma\\.com|thisav\\.com|gounlimited\\.to|highstream\\.tv|" +
                "uqload\\.com|vedbam\\.xyz|vadbam\\.net|vidlo\\.us|wolfstream\\.tv|" +
                "xvideosharing\\.com|(?:\\w+\\.)?viidshar\\.com|sxyprn\\.com|jable\\.tv|" +
                "91porn\\.com|einthusan\\.(?:tv|com|ca)|yourupload\\.com|xanimu\\.com|" +
                "musicdex\\.org|duboku\\.io|gofile\\.io" +
                ")",
        )
    }
}

/** Upstream `KnownLiabilityIE`: sites that would be a liability. */
class KnownLiabilityIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.Unavailable(
            "This website is not supported and will not be supported.",
        )
    }

    companion object {
        const val IE_KEY: String = "KnownLiability"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:motherless\\.\\w+|suno\\.com|udio\\.com)",
        )
    }
}
