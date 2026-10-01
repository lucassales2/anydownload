/*
 * Zattoo platform extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `zattoo.py` from
 * `yt_dlp/extractor/zattoo.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `zattoo.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the Zattoo-platform family URL forms (program/live/recording/VOD on
 * zattoo.com and its twelve white-label hosts) match and fail typed as a
 * login wall. Every `_extract_*` path needs a subscription account
 * (`_power_guide_hash` from `zapi/v2/account/login`) plus the session
 * handshake, so the login flow and the watch/channel/playlist APIs are not
 * translated. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.zattoo

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `ZattooPlatformBaseIE`: the shared account wall. */
abstract class ZattooPlatformBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Every Zattoo-platform API path needs a subscription account. */
    protected fun loginWall(): Nothing = throw ExtractionError.LoginRequired(LOGIN_WALL_MESSAGE)

    companion object {
        /** The one-sentence reason on every Zattoo URL form. */
        const val LOGIN_WALL_MESSAGE: String =
            "This Zattoo-platform media needs a subscription account; the login flow and the " +
                "watch/channel/playlist APIs are not translated."
    }
}

/** Upstream `ZattooBaseIE`: the `zattoo.com` platform marker. */
abstract class ZattooBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `NetPlusTVBaseIE`: the `netplus.tv` platform marker. */
abstract class NetPlusTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `MNetTVBaseIE`: the `tvplus.m-net.de` platform marker. */
abstract class MNetTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `WalyTVBaseIE`: the `player.waly.tv` platform marker. */
abstract class WalyTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `BBVTVBaseIE`: the `bbv-tv.net` platform marker. */
abstract class BBVTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `VTXTVBaseIE`: the `vtxtv.ch` platform marker. */
abstract class VTXTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `GlattvisionTVBaseIE`: the `iptv.glattvision.ch` platform marker. */
abstract class GlattvisionTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `SAKTVBaseIE`: the `saktv.ch` platform marker. */
abstract class SAKTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `EWETVBaseIE`: the `tvonline.ewe.de` platform marker. */
abstract class EWETVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `QuantumTVBaseIE`: the `quantum-tv.com` platform marker. */
abstract class QuantumTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `OsnatelTVBaseIE`: the `tvonline.osnatel.de` platform marker. */
abstract class OsnatelTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `EinsUndEinsTVBaseIE`: the `1und1.tv` platform marker. */
abstract class EinsUndEinsTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `SaltTVBaseIE`: the `tv.salt.ch` platform marker. */
abstract class SaltTVBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ZattooPlatformBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
)

/** Upstream `ZattooIE`: the `zattoo.com` video URL form. */
class ZattooIE(
    http: ExtractorHttp,
) : ZattooBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "Zattoo"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?zattoo\\.com/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `ZattooLiveIE`: the `zattoo.com` live URL form. */
class ZattooLiveIE(
    http: ExtractorHttp,
) : ZattooBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "ZattooLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?zattoo\\.com/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `ZattooMoviesIE`: the `zattoo.com` ondemand URL form. */
class ZattooMoviesIE(
    http: ExtractorHttp,
) : ZattooBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "ZattooMovies"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?zattoo\\.com/(?:[^?#]+\\?(?:[^#]+&)?movie_id=(?<vid2>\\w+)|vod/movies/(?<vid1>\\w+))")
    }
}

/** Upstream `ZattooRecordingsIE`: the `zattoo.com` record URL form. */
class ZattooRecordingsIE(
    http: ExtractorHttp,
) : ZattooBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "ZattooRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?zattoo\\.com/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `NetPlusTVIE`: the `netplus.tv` video URL form. */
class NetPlusTVIE(
    http: ExtractorHttp,
) : NetPlusTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "NetPlusTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?netplus\\.tv/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `NetPlusTVLiveIE`: the `netplus.tv` live URL form. */
class NetPlusTVLiveIE(
    http: ExtractorHttp,
) : NetPlusTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "NetPlusTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?netplus\\.tv/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `NetPlusTVRecordingsIE`: the `netplus.tv` record URL form. */
class NetPlusTVRecordingsIE(
    http: ExtractorHttp,
) : NetPlusTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "NetPlusTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?netplus\\.tv/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `MNetTVIE`: the `tvplus.m-net.de` video URL form. */
class MNetTVIE(
    http: ExtractorHttp,
) : MNetTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "MNetTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvplus\\.m-net\\.de/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `MNetTVLiveIE`: the `tvplus.m-net.de` live URL form. */
class MNetTVLiveIE(
    http: ExtractorHttp,
) : MNetTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "MNetTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvplus\\.m-net\\.de/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `MNetTVRecordingsIE`: the `tvplus.m-net.de` record URL form. */
class MNetTVRecordingsIE(
    http: ExtractorHttp,
) : MNetTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "MNetTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvplus\\.m-net\\.de/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `WalyTVIE`: the `player.waly.tv` video URL form. */
class WalyTVIE(
    http: ExtractorHttp,
) : WalyTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "WalyTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?player\\.waly\\.tv/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `WalyTVLiveIE`: the `player.waly.tv` live URL form. */
class WalyTVLiveIE(
    http: ExtractorHttp,
) : WalyTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "WalyTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?player\\.waly\\.tv/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `WalyTVRecordingsIE`: the `player.waly.tv` record URL form. */
class WalyTVRecordingsIE(
    http: ExtractorHttp,
) : WalyTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "WalyTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?player\\.waly\\.tv/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `BBVTVIE`: the `bbv-tv.net` video URL form. */
class BBVTVIE(
    http: ExtractorHttp,
) : BBVTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "BBVTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?bbv-tv\\.net/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `BBVTVLiveIE`: the `bbv-tv.net` live URL form. */
class BBVTVLiveIE(
    http: ExtractorHttp,
) : BBVTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "BBVTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?bbv-tv\\.net/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `BBVTVRecordingsIE`: the `bbv-tv.net` record URL form. */
class BBVTVRecordingsIE(
    http: ExtractorHttp,
) : BBVTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "BBVTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?bbv-tv\\.net/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `VTXTVIE`: the `vtxtv.ch` video URL form. */
class VTXTVIE(
    http: ExtractorHttp,
) : VTXTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "VTXTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?vtxtv\\.ch/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `VTXTVLiveIE`: the `vtxtv.ch` live URL form. */
class VTXTVLiveIE(
    http: ExtractorHttp,
) : VTXTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "VTXTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?vtxtv\\.ch/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `VTXTVRecordingsIE`: the `vtxtv.ch` record URL form. */
class VTXTVRecordingsIE(
    http: ExtractorHttp,
) : VTXTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "VTXTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?vtxtv\\.ch/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `GlattvisionTVIE`: the `iptv.glattvision.ch` video URL form. */
class GlattvisionTVIE(
    http: ExtractorHttp,
) : GlattvisionTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "GlattvisionTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?iptv\\.glattvision\\.ch/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `GlattvisionTVLiveIE`: the `iptv.glattvision.ch` live URL form. */
class GlattvisionTVLiveIE(
    http: ExtractorHttp,
) : GlattvisionTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "GlattvisionTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?iptv\\.glattvision\\.ch/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `GlattvisionTVRecordingsIE`: the `iptv.glattvision.ch` record URL form. */
class GlattvisionTVRecordingsIE(
    http: ExtractorHttp,
) : GlattvisionTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "GlattvisionTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?iptv\\.glattvision\\.ch/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `SAKTVIE`: the `saktv.ch` video URL form. */
class SAKTVIE(
    http: ExtractorHttp,
) : SAKTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "SAKTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?saktv\\.ch/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `SAKTVLiveIE`: the `saktv.ch` live URL form. */
class SAKTVLiveIE(
    http: ExtractorHttp,
) : SAKTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "SAKTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?saktv\\.ch/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `SAKTVRecordingsIE`: the `saktv.ch` record URL form. */
class SAKTVRecordingsIE(
    http: ExtractorHttp,
) : SAKTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "SAKTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?saktv\\.ch/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `EWETVIE`: the `tvonline.ewe.de` video URL form. */
class EWETVIE(
    http: ExtractorHttp,
) : EWETVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "EWETV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvonline\\.ewe\\.de/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `EWETVLiveIE`: the `tvonline.ewe.de` live URL form. */
class EWETVLiveIE(
    http: ExtractorHttp,
) : EWETVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "EWETVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvonline\\.ewe\\.de/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `EWETVRecordingsIE`: the `tvonline.ewe.de` record URL form. */
class EWETVRecordingsIE(
    http: ExtractorHttp,
) : EWETVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "EWETVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvonline\\.ewe\\.de/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `QuantumTVIE`: the `quantum-tv.com` video URL form. */
class QuantumTVIE(
    http: ExtractorHttp,
) : QuantumTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "QuantumTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?quantum-tv\\.com/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `QuantumTVLiveIE`: the `quantum-tv.com` live URL form. */
class QuantumTVLiveIE(
    http: ExtractorHttp,
) : QuantumTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "QuantumTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?quantum-tv\\.com/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `QuantumTVRecordingsIE`: the `quantum-tv.com` record URL form. */
class QuantumTVRecordingsIE(
    http: ExtractorHttp,
) : QuantumTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "QuantumTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?quantum-tv\\.com/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `OsnatelTVIE`: the `tvonline.osnatel.de` video URL form. */
class OsnatelTVIE(
    http: ExtractorHttp,
) : OsnatelTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "OsnatelTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvonline\\.osnatel\\.de/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `OsnatelTVLiveIE`: the `tvonline.osnatel.de` live URL form. */
class OsnatelTVLiveIE(
    http: ExtractorHttp,
) : OsnatelTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "OsnatelTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvonline\\.osnatel\\.de/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `OsnatelTVRecordingsIE`: the `tvonline.osnatel.de` record URL form. */
class OsnatelTVRecordingsIE(
    http: ExtractorHttp,
) : OsnatelTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "OsnatelTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tvonline\\.osnatel\\.de/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `EinsUndEinsTVIE`: the `1und1.tv` video URL form. */
class EinsUndEinsTVIE(
    http: ExtractorHttp,
) : EinsUndEinsTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "EinsUndEinsTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?1und1\\.tv/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `EinsUndEinsTVLiveIE`: the `1und1.tv` live URL form. */
class EinsUndEinsTVLiveIE(
    http: ExtractorHttp,
) : EinsUndEinsTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "EinsUndEinsTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?1und1\\.tv/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `EinsUndEinsTVRecordingsIE`: the `1und1.tv` record URL form. */
class EinsUndEinsTVRecordingsIE(
    http: ExtractorHttp,
) : EinsUndEinsTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "EinsUndEinsTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?1und1\\.tv/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

/** Upstream `SaltTVIE`: the `tv.salt.ch` video URL form. */
class SaltTVIE(
    http: ExtractorHttp,
) : SaltTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "SaltTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tv\\.salt\\.ch/(?:[^?#]+\\?(?:[^#]+&)?program=(?<vid2>\\d+)|(?:program|watch)/[^/]+/(?<vid1>\\d+))")
    }
}

/** Upstream `SaltTVLiveIE`: the `tv.salt.ch` live URL form. */
class SaltTVLiveIE(
    http: ExtractorHttp,
) : SaltTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "SaltTVLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tv\\.salt\\.ch/(?:[^?#]+\\?(?:[^#]+&)?channel=(?<vid2>[^/?&#]+)|live/(?<vid1>[^/?&#]+))")
    }
}

/** Upstream `SaltTVRecordingsIE`: the `tv.salt.ch` record URL form. */
class SaltTVRecordingsIE(
    http: ExtractorHttp,
) : SaltTVBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = loginWall()

    companion object {
        const val IE_KEY: String = "SaltTVRecordings"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tv\\.salt\\.ch/(?:[^?#]+\\?(?:[^#]+&)?recording=(?<vid2>\\d+))")
    }
}

