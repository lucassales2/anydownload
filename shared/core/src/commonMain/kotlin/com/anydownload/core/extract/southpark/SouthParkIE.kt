/*
 * South Park extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `southpark.py` from
 * `yt_dlp/extractor/southpark.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `southpark.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the URL surface of the seven South Park sites. Every class extends
 * upstream `MTVServicesBaseIE`, whose feed flow (Paramount/MVPD) is not
 * translated, so each URL form matches and fails typed. No API key, mgid,
 * or media URL is stored here.
 */
package com.anydownload.core.extract.southpark

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val LOGIN_WALL =
    "The MTV services feed flow (MTVServicesBaseIE) is not translated, so this site cannot be extracted."

/** Upstream `SouthParkIE`: southpark.cc.com / southparkstudios.com. */
class SouthParkIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "SouthPark"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?southpark(?:\\.cc|studios)\\.com/" +
                "(?:video-clips|episodes|collections)/(?<id>[^?#]+)",
        )
    }
}

/** Upstream `SouthParkEsIE`: southpark.cc.com/es. */
class SouthParkEsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "SouthParkEs"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?southpark\\.cc\\.com/es/episodios/(?<id>[^?#]+)",
        )
    }
}

/** Upstream `SouthParkDeIE`: southpark.de. */
class SouthParkDeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "SouthParkDe"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?southpark\\.de/(?:en/)?(?:videoclip|collections|episodes|" +
                "video-clips|folgen)/(?<id>[^?#]+)",
        )
    }
}

/** Upstream `SouthParkLatIE`: southpark.lat. */
class SouthParkLatIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "SouthParkLat"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?southpark\\.lat/(?:en/)?(?:video-?clips?|collections|" +
                "episod(?:e|io)s)/(?<id>[^?#]+)",
        )
    }
}

/** Upstream `SouthParkDkIE`: southparkstudios.nu. */
class SouthParkDkIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "SouthParkDk"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?southparkstudios\\.nu/(?:video-clips|episodes|collections)/(?<id>[^?#]+)",
        )
    }
}

/** Upstream `SouthParkComBrIE`: southparkstudios.com.br. */
class SouthParkComBrIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "SouthParkComBr"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?southparkstudios\\.com\\.br/(?:en/)?(?:video-clips|episodios|" +
                "collections|episodes)/(?<id>[^?#]+)",
        )
    }
}

/** Upstream `SouthParkCoUkIE`: southparkstudios.co.uk. */
class SouthParkCoUkIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "SouthParkCoUk"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?southparkstudios\\.co\\.uk/(?:video-clips|collections|episodes)/(?<id>[^?#]+)",
        )
    }
}
