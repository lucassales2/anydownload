/*
 * DPlay and Discovery+ extractors — AnyDownload
 *
 * Kotlin translation of the public video and show-page subset of `dplay.py`
 * from `yt_dlp/extractor/dplay.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `dplay.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `disco-api` token call, `content/videos/<id>` metadata, the v1
 * `playback/videoPlaybackInfo/<id>` and v3 `playback/v3/videoPlaybackInfo`
 * streaming maps, and the CMS season listing for the two show pages. HLS and
 * DASH manifests are recorded as `m3u8_native` / `http_dash_segments` formats
 * and parsed by the engine at download time; subtitle tracks inside those
 * manifests are not read at extract time. Upstream fields the port's
 * `InfoDict` does not model (`series`, `season_number`, `episode_number`,
 * `creator`, `tags`, `categories`, `display_id`, `http_headers`) are
 * dropped, and the creator name fills `channel`. Geo-block and
 * missing-package error bodies cannot be read through the port's typed HTTP
 * seam, so those responses fail typed as unavailable rather than as a named
 * geo wall. The anonymous device token is fetched per extraction and never
 * stored in a fixture or a log.
 *
 * The only `x-disco-*` values are the fixed public client identifiers
 * upstream sends; no credential, cookie, or signed URL is stored here.
 */
package com.anydownlod.core.extract.dplay

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoEntry
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.Thumbnail
import com.anydownlod.core.platform.HttpMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/** Upstream playback endpoint generation. */
enum class DiscoPlayback { V1, V3 }

/** One `{type, url}` entry of a playback response. */
private data class DiscoStream(val type: String?, val url: String?)

/**
 * Upstream `DPlayBaseIE`: the shared disco-api machinery. Every registered
 * class in `dplay.py` extends it.
 */
abstract class DPlayBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    private val authTokens = mutableMapOf<String, String>()

    /** Upstream `_get_auth`: an anonymous device token minted per realm. */
    protected suspend fun discoAuth(discoBase: String, realm: String): String {
        val key = "$discoBase|$realm"
        authTokens[key]?.let { return it }
        val json = http.downloadJson(
            "$discoBase" + "token?realm=" + realm + "&deviceId=" + randomDeviceId(),
        )
        val token = (json as? JsonObject)?.obj("data")?.obj("attributes")?.str("token")
            ?: throw ExtractionError.Malformed("The Discovery token response had no token.")
        val header = "Bearer $token"
        authTokens[key] = header
        return header
    }

    /** Upstream `_update_disco_api_headers`; the `Authorization` field is separate. */
    protected open fun discoClientHeaders(realm: String): Map<String, String> = emptyMap()

    /** Upstream `_download_video_playback_info` (v1): object map or list. */
    private suspend fun playbackInfoV1(
        discoBase: String,
        videoId: String,
        headers: Map<String, String>,
        authorization: String,
    ): List<DiscoStream> {
        val json = http.downloadJson(
            discoBase + "playback/videoPlaybackInfo/" + videoId,
            headers = headers,
            authorization = authorization,
        )
        return (json as? JsonObject)?.obj("data")?.obj("attributes")
            ?.let { streamingMap(it["streaming"]) }.orEmpty()
    }

    /** Upstream `DiscoveryPlusBaseIE._download_video_playback_info` (v3). */
    private suspend fun playbackInfoV3(
        discoBase: String,
        videoId: String,
        headers: Map<String, String>,
        authorization: String,
    ): List<DiscoStream> {
        val body = buildJsonObject {
            putJsonObject("deviceInfo") {
                put("adBlocker", false)
                put("drmSupported", false)
            }
            put("videoId", videoId)
            putJsonObject("wisteriaProperties") {}
        }.toString().encodeToByteArray()
        val json = http.downloadJson(
            discoBase + "playback/v3/videoPlaybackInfo",
            method = HttpMethods.POST,
            headers = headers,
            body = body,
            authorization = authorization,
        )
        return (json as? JsonObject)?.obj("data")?.obj("attributes")
            ?.let { streamingMap(it["streaming"]) }.orEmpty()
    }

    /** Upstream `_get_disco_api_info`: content metadata plus streaming formats. */
    protected suspend fun discoApiInfo(
        url: String,
        displayId: String,
        discoHost: String,
        realm: String,
        country: String,
        domain: String = "",
        playback: DiscoPlayback = DiscoPlayback.V1,
    ): InfoDict {
        val discoBase = "https://$discoHost/"
        val authorization = discoAuth(discoBase, realm)
        val headers = linkedMapOf("referer" to url) + discoClientHeaders(realm)
        val video = http.downloadJson(
            discoBase + "content/videos/" + displayId + CONTENT_QUERY,
            headers = headers,
            authorization = authorization,
        )
        val data = (video as? JsonObject)?.obj("data")
            ?: throw ExtractionError.Malformed("The Discovery video response had no data.")
        val videoId = data.str("id") ?: displayId
        val attributes = data.obj("attributes")
            ?: throw ExtractionError.Malformed("The Discovery video response had no attributes.")
        val title = attributes.str("name")?.trim()?.takeIf { it.isNotEmpty() }

        val streams = when (playback) {
            DiscoPlayback.V1 -> playbackInfoV1(discoBase, videoId, headers, authorization)
            DiscoPlayback.V3 -> playbackInfoV3(discoBase, videoId, headers, authorization)
        }
        val formats = streams.mapNotNull { it.toFormat(domain.takeIf { value -> value.isNotEmpty() }) }

        var creator: String? = null
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in (video as? JsonObject)?.array("included").orEmpty()) {
            val entry = element as? JsonObject ?: continue
            val entryAttributes = entry.obj("attributes") ?: continue
            when (entry.str("type")) {
                "channel" -> creator = entryAttributes.str("name")
                "image" -> entryAttributes.str("src")?.let { src ->
                    thumbnails += Thumbnail(
                        url = src,
                        width = entryAttributes.number("width")?.toLong(),
                        height = entryAttributes.number("height")?.toLong(),
                    )
                }
            }
        }

        return InfoDict(
            id = videoId,
            title = title,
            description = attributes.str("description")?.trim()?.takeIf { it.isNotEmpty() },
            duration = attributes.number("videoDuration")?.div(1000.0),
            uploadDate = attributes.str("publishStart")?.let(ExtractorUtils::unifiedStrdate),
            channel = creator,
            thumbnails = thumbnails,
            formats = formats,
            webpageUrl = url,
            extractor = "dplay",
            extractorKey = ieKey,
        )
    }

    companion object {
        /** Upstream `_DISCO_CLIENT_VER` on `DiscoveryPlusBaseIE`. */
        const val DISCO_CLIENT_VER: String = "27.43.0"

        /** Upstream `content/videos` query, `urllib.parse.urlencode` shaped. */
        private const val CONTENT_QUERY: String =
            "?fields%5Bchannel%5D=name" +
                "&fields%5Bimage%5D=height%2Csrc%2Cwidth" +
                "&fields%5Bshow%5D=name" +
                "&fields%5Btag%5D=name" +
                "&fields%5Bvideo%5D=description%2CepisodeNumber%2Cname%2CpublishStart%2CseasonNumber%2CvideoDuration" +
                "&include=images%2CprimaryChannel%2Cshow%2Ctags"
    }
}

/** Upstream `DPlayIE`: the dplay.* and country-subdomain forms. */
class DPlayIE(
    http: ExtractorHttp,
) : DPlayBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val domain = match.groups["domain"]?.value?.removePrefix("www.")
            ?: throw ExtractionError.UnsupportedUrl()
        val country = match.groups["country"]?.value
            ?: match.groups["subdomainCountry"]?.value
            ?: match.groups["plusCountry"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val host = if (domain.startsWith("d")) "disco-api.$domain" else "eu2-prod.disco-api.com"
        return discoApiInfo(url, displayId, host, "dplay$country", country, domain)
    }

    companion object {
        const val IE_KEY: String = "DPlay"

        /** Upstream `_VALID_URL` with `_PATH_REGEX` expanded. */
        val VALID_URL: Regex = Regex(
            "https?://(?<domain>(?:www\\.)?(?<host>d(?:play\\.(?<country>dk|fi|jp|se|no)|" +
                "iscoveryplus\\.(?<plusCountry>dk|es|fi|it|se|no)))|" +
                "(?<subdomainCountry>es|it)\\.dplay\\.com)/[^/]+/(?<id>[^/]+/[^/?#]+)",
        )
    }
}

/**
 * Upstream `DiscoveryPlusBaseIE`: the v3 playback path shared by the
 * Discovery+ product pages. The constructor carries what upstream stores in
 * `_PRODUCT` and `_DISCO_API_PARAMS`.
 */
abstract class DiscoveryPlusBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
    protected val product: String,
    private val discoHost: String,
    private val realm: String,
    private val country: String,
    private val domain: String = "",
) : DPlayBaseIE(ieKey = ieKey, http = http, validUrl = validUrl) {

    override fun discoClientHeaders(realm: String): Map<String, String> = mapOf(
        "x-disco-params" to "realm=$realm,siteLookupKey=$product",
        "x-disco-client" to "WEB:UNKNOWN:$product:$DISCO_CLIENT_VER",
    )

    override suspend fun extract(url: String): InfoDict {
        val id = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return discoApiInfo(url, id, discoHost, realm, country, domain, DiscoPlayback.V3)
    }
}

class HGTVDeIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "hgtv", "eu1-prod.disco-api.com", "hgtv", "de",
) {
    override fun discoClientHeaders(realm: String): Map<String, String> = mapOf(
        "x-disco-params" to "realm=$realm",
        "x-disco-client" to "Alps:HyogaPlayer:0.0.0",
    )

    companion object {
        const val IE_KEY: String = "HGTVDe"
        val VALID_URL: Regex = Regex("https?://de\\.hgtv\\.com/sendungen/(?<id>[^/]+/[^/?#]+)")
    }
}

class GoDiscoveryIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "dsc", "us1-prod-direct.go.discovery.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "GoDiscovery"
        val VALID_URL: Regex = Regex("https?://(?:go\\.)?discovery\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class TravelChannelIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "trav", "us1-prod-direct.watch.travelchannel.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "TravelChannel"
        val VALID_URL: Regex = Regex("https?://(?:watch\\.)?travelchannel\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class CookingChannelIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "cook", "us1-prod-direct.watch.cookingchanneltv.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "CookingChannel"
        val VALID_URL: Regex = Regex("https?://(?:watch\\.)?cookingchanneltv\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class HGTVUsaIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "hgtv", "us1-prod-direct.watch.hgtv.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "HGTVUsa"
        val VALID_URL: Regex = Regex("https?://(?:watch\\.)?hgtv\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class FoodNetworkIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "food", "us1-prod-direct.watch.foodnetwork.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "FoodNetwork"
        val VALID_URL: Regex = Regex("https?://(?:watch\\.)?foodnetwork\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class DestinationAmericaIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "dam", "us1-prod-direct.destinationamerica.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "DestinationAmerica"
        val VALID_URL: Regex = Regex("https?://(?:www\\.)?destinationamerica\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class InvestigationDiscoveryIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "ids", "us1-prod-direct.investigationdiscovery.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "InvestigationDiscovery"
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?investigationdiscovery\\.com/video/(?<id>[^/]+/[^/?#]+)",
        )
    }
}

class AmHistoryChannelIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "ahc", "us1-prod-direct.ahctv.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "AmHistoryChannel"
        val VALID_URL: Regex = Regex("https?://(?:www\\.)?ahctv\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class ScienceChannelIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "sci", "us1-prod-direct.sciencechannel.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "ScienceChannel"
        val VALID_URL: Regex = Regex("https?://(?:www\\.)?sciencechannel\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class DiscoveryLifeIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "dlf", "us1-prod-direct.discoverylife.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "DiscoveryLife"
        val VALID_URL: Regex = Regex("https?://(?:www\\.)?discoverylife\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class AnimalPlanetIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "apl", "us1-prod-direct.animalplanet.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "AnimalPlanet"
        val VALID_URL: Regex = Regex("https?://(?:www\\.)?animalplanet\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

class TLCIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "tlc", "us1-prod-direct.tlc.com", "go", "us",
) {
    companion object {
        const val IE_KEY: String = "TLC"
        val VALID_URL: Regex = Regex("https?://(?:go\\.)?tlc\\.com/video/(?<id>[^/]+/[^/?#]+)")
    }
}

/** Upstream `DiscoveryPlusIE`: the country-agnostic discoveryplus.com form. */
class DiscoveryPlusIE(
    http: ExtractorHttp,
) : DPlayBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    /** Upstream `(?!it/)`; the Italy form belongs to `DiscoveryPlusItalyIE`. */
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !ITALY_FORM.containsMatchIn(url)

    override fun discoClientHeaders(realm: String): Map<String, String> {
        val product = dynamicProduct ?: "dplus_us"
        return mapOf(
            "x-disco-params" to "realm=$realm,siteLookupKey=$product",
            "x-disco-client" to "WEB:UNKNOWN:dplus_us:$DISCO_CLIENT_VER",
        )
    }

    private var dynamicProduct: String? = null

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val country = match.groups["country"]?.value ?: "us"
        dynamicProduct = "dplus_$country"
        val host: String
        val realm: String
        if (country in US_COUNTRIES) {
            host = "us1-prod-direct.discoveryplus.com"
            realm = "go"
        } else {
            host = "eu1-prod-direct.discoveryplus.com"
            realm = "dplay"
        }
        return discoApiInfo(url, videoId, host, realm, country, playback = DiscoPlayback.V3)
    }

    companion object {
        const val IE_KEY: String = "DiscoveryPlus"

        private val US_COUNTRIES = setOf("br", "ca", "us")
        private val ITALY_FORM = Regex("^https?://(?:www\\.)?discoveryplus\\.com/it/")

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?discoveryplus\\.com/(?:(?<country>[a-z]{2})/)?" +
                "video(?:/sport|/olympics)?/(?<id>[^/]+/[^/?#]+)",
        )
    }
}

class DiscoveryPlusIndiaIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "dplus-india", "ap2-prod-direct.discoveryplus.in", "dplusindia", "in",
    "https://www.discoveryplus.in/",
) {
    override fun discoClientHeaders(realm: String): Map<String, String> = mapOf(
        "x-disco-params" to "realm=$realm",
        "x-disco-client" to "WEB:UNKNOWN:dplus-india:17.0.0",
    )

    companion object {
        const val IE_KEY: String = "DiscoveryPlusIndia"
        val VALID_URL: Regex = Regex("https?://(?:www\\.)?discoveryplus\\.in/videos?/(?<id>[^/]+/[^/?#]+)")
    }
}

/** Upstream `DiscoveryNetworksDeIE`: the tlc.de/dmax.de programme pages. */
class DiscoveryNetworksDeIE(
    http: ExtractorHttp,
) : DPlayBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override fun discoClientHeaders(realm: String): Map<String, String> = mapOf(
        "x-disco-params" to "realm=$realm",
        "x-disco-client" to "Alps:HyogaPlayer:0.0.0",
    )

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val domain = match.groups["domain"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val programme = match.groups["programme"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val alternateId = match.groups["alternateId"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = "$programme/$alternateId"
        val meta = runCatching {
            http.downloadJson(
                "https://de-api.loma-cms.com/feloma/videos/$alternateId/" +
                    "?environment=" + domain.substringBefore('.') +
                    "&v=2&filter%5Bshow.slug%5D=" + programme,
            )
        }.getOrNull() as? JsonObject
        val videoId = meta?.str("uid")?.takeLast(7) ?: displayId
        return discoApiInfo(
            url,
            videoId,
            "eu1-prod.disco-api.com",
            domain.replace(".", ""),
            "DE",
            playback = DiscoPlayback.V3,
        )
    }

    companion object {
        const val IE_KEY: String = "DiscoveryNetworksDe"
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<domain>(?:tlc|dmax)\\.de)/(?:programme|show|sendungen)/" +
                "(?<programme>[^/?#]+)/(?:video/)?(?<alternateId>[^/?#]+)",
        )
    }
}

class DiscoveryPlusItalyIE(http: ExtractorHttp) : DiscoveryPlusBaseIE(
    IE_KEY, http, VALID_URL, "dplus_it", "eu1-prod-direct.discoveryplus.com", "dplay", "it",
) {
    override fun discoClientHeaders(realm: String): Map<String, String> = mapOf(
        "x-disco-params" to "realm=$realm,siteLookupKey=dplus_it",
        "x-disco-client" to "WEB:UNKNOWN:dplus_us:$DISCO_CLIENT_VER",
    )

    companion object {
        const val IE_KEY: String = "DiscoveryPlusItaly"
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?discoveryplus\\.com/it/video(?:/sport|/olympics)?/(?<id>[^/]+/[^/?#]+)",
        )
    }
}

/**
 * Upstream `DiscoveryPlusShowBaseIE`: the CMS route lookup and the bounded
 * season listing. Entries re-enter the registry as `videos/<path>` URLs of
 * the sibling video extractor, mirroring upstream `url_result`.
 */
abstract class DiscoveryPlusShowBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    protected val showUrl: Regex,
    private val baseApi: String,
    private val domainUrl: String,
    private val xClient: String,
    private val realm: String,
    private val showStr: String,
    private val index: Int,
) : DPlayBaseIE(ieKey = ieKey, http = http, validUrl = showUrl) {

    override fun matchId(url: String): String? =
        showUrl.find(url)?.groups?.get("showName")?.value

    override suspend fun extract(url: String): InfoDict {
        val showName = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = showName,
            title = showName,
            entries = entries(showName),
            webpageUrl = url,
            extractor = "dplay",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_entries`: one bounded page walk per season. */
    private suspend fun entries(showName: String): List<InfoEntry> {
        val headers = mapOf(
            "x-disco-client" to xClient,
            "x-disco-params" to "realm=$realm",
            "referer" to domainUrl,
        )
        val authorization = discoAuth(baseApi, realm)
        val showJson = http.downloadJson(
            "$baseApi" + "cms/routes/$showStr/$showName?include=default",
            headers = headers,
            authorization = authorization,
        )
        val component = ((showJson as? JsonObject)?.array("included")?.getOrNull(index) as? JsonObject)
            ?.obj("attributes")?.obj("component")
            ?: throw ExtractionError.Malformed("The Discovery show route had no component.")
        val showId = component.str("mandatoryParams")?.substringAfterLast('=')
            ?: throw ExtractionError.Malformed("The Discovery show route had no show id.")
        val seasons = (component.array("filters")?.firstOrNull() as? JsonObject)?.array("options").orEmpty()

        val entries = mutableListOf<InfoEntry>()
        for (season in seasons) {
            val seasonId = (season as? JsonObject)?.str("id") ?: continue
            var totalPages = 1
            var pageNumber = 0
            while (pageNumber < totalPages) {
                val page = http.downloadJson(
                    "$baseApi" + "content/videos?sort=episodeNumber" +
                        "&filter%5BseasonNumber%5D=$seasonId" +
                        "&filter%5Bshow.id%5D=$showId" +
                        "&page%5Bsize%5D=100" +
                        "&page%5Bnumber%5D=${pageNumber + 1}",
                    headers = headers,
                    authorization = authorization,
                )
                if (pageNumber == 0) {
                    totalPages = (page as? JsonObject)?.obj("meta")?.number("totalPages")?.toInt() ?: 1
                }
                for (episode in (page as? JsonObject)?.array("data").orEmpty()) {
                    val episodeObject = episode as? JsonObject ?: continue
                    val path = episodeObject.obj("attributes")?.str("path") ?: continue
                    entries += InfoEntry(
                        id = episodeObject.str("id") ?: path,
                        url = domainUrl + "videos/" + path,
                    )
                }
                pageNumber++
            }
        }
        return entries
    }
}

class DiscoveryPlusItalyShowIE(http: ExtractorHttp) : DiscoveryPlusShowBaseIE(
    IE_KEY, http, VALID_URL,
    "https://disco-api.discoveryplus.it/",
    "https://www.discoveryplus.it/",
    "WEB:UNKNOWN:dplay-client:2.6.0",
    "dplayit",
    "programmi",
    1,
) {
    companion object {
        const val IE_KEY: String = "DiscoveryPlusItalyShow"
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?discoveryplus\\.it/programmi/(?<showName>[^/]+)/?(?:[?#]|$)",
        )
    }
}

class DiscoveryPlusIndiaShowIE(http: ExtractorHttp) : DiscoveryPlusShowBaseIE(
    IE_KEY, http, VALID_URL,
    "https://ap2-prod-direct.discoveryplus.in/",
    "https://www.discoveryplus.in/",
    "WEB:UNKNOWN:dplus-india:prod",
    "dplusindia",
    "show",
    4,
) {
    companion object {
        const val IE_KEY: String = "DiscoveryPlusIndiaShow"
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?discoveryplus\\.in/show/(?<showName>[^/]+)/?(?:[?#]|$)",
        )
    }
}

/** Upstream `streaming` handling, tolerant of the v1 object map and v3 list. */
private fun streamingMap(streaming: JsonElement?): List<DiscoStream> = when (streaming) {
    is JsonArray -> streaming.mapNotNull { element ->
        val entry = element as? JsonObject ?: return@mapNotNull null
        DiscoStream(entry.str("type"), entry.str("url"))
    }

    is JsonObject -> streaming.map { (type, value) ->
        DiscoStream(type, (value as? JsonObject)?.str("url"))
    }

    else -> emptyList()
}

/** Upstream `determine_ext` plus the `dash`/`hls` format-id branches. */
private fun DiscoStream.toFormat(referer: String?): MediaFormat? {
    val streamUrl = url ?: return null
    val ext = ExtractorUtils.determineExt(streamUrl, defaultExt = "")
    val httpHeaders = referer?.let { mapOf("referer" to it) }
    return when {
        type == "dash" || ext == "mpd" -> MediaFormat(
            formatId = "dash",
            url = streamUrl,
            ext = "mp4",
            protocol = "http_dash_segments",
            httpHeaders = httpHeaders,
        )

        type == "hls" || ext == "m3u8" -> MediaFormat(
            formatId = "hls",
            url = streamUrl,
            ext = "mp4",
            protocol = "m3u8_native",
            httpHeaders = httpHeaders,
        )

        else -> MediaFormat(
            formatId = type,
            url = streamUrl,
            ext = ext.takeIf { it.isNotEmpty() },
            httpHeaders = httpHeaders,
        )
    }
}

/** Upstream `uuid.uuid4().hex` for the anonymous device id. */
private fun randomDeviceId(): String {
    val hex = "0123456789abcdef"
    return buildString(32) {
        repeat(32) { append(hex[Random.nextInt(hex.length)]) }
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
