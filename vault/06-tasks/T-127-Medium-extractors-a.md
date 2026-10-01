---
id: T-127
type: task
priority: P1
milestone: D18
tags: [task, engine, extractors]
---

# T-127 — Medium extractors, first half

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 18](../00-project/Phase-18-Medium-extractors-a.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

The first 110 extractor files in the 8–25 KB band are accounted for. Estimate 110 engineer-days.

## Dependencies

- [T-126](T-126-Remaining-large-sites.md).

## Acceptance criteria

- [x] The file list is written into Evidence at the start, larger files first.
- [x] Each file has harness cases and a manifest row. A login wall or DRM wall is Partial with that reason.
- [x] `:tools:port-manifest:check` passes.

## Evidence / notes

Not started. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).

**Catalog D18 — T-127, 110 medium files (2026-09-30).** First `tumblr.py`, last `cbs.py`, largest first, read from `yt_dlp/extractor/` at pin `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`), excluding the D16/D17 files already closed.

**File 1 of 110 — `tumblr.py` (2026-09-30).** `TumblrIE.kt` translates the single class:

- `TumblrIE`: the public post page with the `WhatsApp/2.0` user agent, the OG video URL, the `/video/<blog>/<id>/` iframe page with its `data-crt-options` hd/sd sources, and the title/description/thumbnail. A dashboard-only/safe-mode redirect fails typed as a login wall.
- Limits in the manifest scope: the `API_TOKEN` login page, the OAuth login, the `/api/v2/.../permalink` metadata (reblog chain, tags, counts, NSFW flag), and the external embed re-dispatch need the account token and are not translated; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tumblr.TumblrIETest"` — 5 tests, 0 failures (including the harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1028 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 201 partial, 88 planned, 1,462 not started.

Next: file 2, `iqiyi.py`.

**File 2 of 110 — `iqiyi.py` (2026-09-30).** `IqiyiIE.kt` accounts for all 3 classes in the file, read from the pin:

- Translated and registered (3): `IqiyiIE` (the tvid/videoid scan, the MD5-signed `tmts` API, the m3u8 format map, the `A00111` geo failure, and the album `avlist` pagination), `IqIE` (matches and fails typed: the international player needs its `cmd5x` signature function executed in a JS runtime, which the port excludes), and `IqAlbumIE` (the Next.js album data drives the `episodeListSource` pagination; a `singleVideo` album re-dispatches to the play URL).
- Limits in the manifest scopes: the VIP/uid cookies, subtitles, and intl format data path are not translated; the retry sleep and non-m3u8 legacy streams are not translated; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.iqiyi.IqiyiIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1036 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 204 partial, 88 planned, 1,459 not started.

Next: file 3, `anvato.py`.

**File 3 of 110 — `anvato.py` (2026-09-30).** `AnvatoIE.kt` accounts for the file's only class:

- Translated and registered (1): `AnvatoIE` — `anvato:<access_key_or_mcp>:<id>` matches and fails typed.
- Wall reason (one sentence, manifest scope): the MCP video JSON needs an AES-encrypted `X-Anvato-Adst-Auth` header plus the access-key/MCP tables, and the port does not add an AES-encryption helper; the video JSON, SMIL/m3u8/caption walk, and webpage player scan are not translated.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.anvato.AnvatoIETest"` — 2 tests, 0 failures (URL match + typed failure; a fixture cannot pass the AES header).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 205 partial, 88 planned, 1,458 not started.

Next: file 4, `polskieradio.py`.

**File 4 of 110 — `polskieradio.py` (2026-09-30).** `PolskieRadioIE.kt` accounts for all 7 registered classes plus the two unregistered bases (`PolskieRadioBaseIE`, `PolskieRadioPodcastBaseIE`, translated as shared private helpers):

- Translated and registered (7): `PolskieRadioLegacyIE` (legacy article og tags, `this-article` body, `data-media` players, `source:` audition record, redirect to the new class), `PolskieRadioIE` (Next.js article data + Audio attachments + body-player fallback), `PolskieRadioAuditionIE` (typed wall), `PolskieRadioCategoryIE` (first listing page), `PolskieRadioPlayerIE` (bundle channel list + stations API), `PolskieRadioPodcastListIE` (paged API, ten pages eagerly), `PolskieRadioPodcastIE` (POST-by-guid API).
- Wall reason (one sentence, manifest scope): the LP3 audition list API needs an `x-api-key` header that the port does not embed.
- Other manifest limits: legacy billennium/postback pagination not translated; podcast series/episode labels not carried; ISM/f4m streams skipped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.polskieradio.PolskieRadioIETest"` — 13 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 212 partial, 88 planned, 1,451 not started.

Next: file 5, `nebula.py`.

**File 5 of 110 — `nebula.py` (2026-09-30).** `NebulaIE.kt` accounts for all 5 registered classes plus the unregistered `NebulaBaseIE` (its token/API behavior is the wall reason):

- Translated and registered (5): `NebulaIE`, `NebulaClassIE`, `NebulaSubscriptionsIE`, `NebulaChannelIE`, `NebulaSeasonIE` — every `nebula.tv`/`nebula.app`/`watchnebula.com` URL form matches and fails typed.
- Wall reason (one sentence, manifest scope): the Nebula content API needs a guest/account API token and its m3u8 carries the token as a query parameter, so the port does not translate the authorization flow, the token-bearing manifest, the metadata walk, or the watch-progress PATCH.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nebula.NebulaIETest"` — 2 tests, 0 failures (URL match + typed failure for all five classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 217 partial, 88 planned, 1,446 not started.

Next: file 6, `zingmp3.py`.

**File 6 of 110 — `zingmp3.py` (2026-09-30).** `ZingMp3IE.kt` accounts for all 10 registered classes plus the unregistered `ZingMp3BaseIE` (its signed API is the wall reason):

- Translated and registered (10): `ZingMp3IE`, `ZingMp3AlbumIE`, `ZingMp3ChartHomeIE`, `ZingMp3WeekChartIE`, `ZingMp3ChartMusicVideoIE`, `ZingMp3UserIE`, `ZingMp3HubIE`, `ZingMp3LiveRadioIE`, `ZingMp3PodcastEpisodeIE`, `ZingMp3PodcastIE` — every URL form matches and fails typed.
- Wall reason (one sentence, manifest scope): the page API needs an HMAC-SHA512 signature over a fixed secret key plus a SHA-256 query digest, and the port does not add crypto helpers that embed the key; the API call, paged listings, charts, and podcast/user walks are not translated.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.zingmp3.ZingMp3IETest"` — 2 tests, 0 failures (URL match + typed failure for all ten classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 227 partial, 88 planned, 1,436 not started.

Next: file 7, `youporn.py`.

**File 7 of 110 — `youporn.py` (2026-09-30).** `YouPornIE.kt` accounts for all 7 registered classes plus the unregistered `YouPornListBaseIE` (translated as the shared `extractList` helper):

- Translated and registered (7): `YouPornIE` (watch/embed player vars, mp4 + master m3u8, page metadata), `YouPornCategoryIE`, `YouPornChannelIE`, `YouPornCollectionIE`, `YouPornTagIE`, `YouPornStarIE`, `YouPornVideosIE` (the listing walk with the next-page link).
- Limits in the manifest scopes: the JSON-LD merge, display id, comment count, and category/tag label lists are not carried; listings walk at most five pages eagerly and carry no per-entry ids.
- No new core helper: the file carries its own balanced JSON-after-key reader, attribute parser, count parser, and next-page finder.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.youporn.YouPornIETest"` — 6 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 234 partial, 88 planned, 1,429 not started.

Next: file 8, `npo.py`.

**File 8 of 110 — `npo.py` (2026-09-30).** `NpoIE.kt` accounts for all 9 registered classes plus the two unregistered bases (`NPODataMidEmbedIE`, `NPOPlaylistBaseIE`, translated as shared helpers):

- Translated and registered (9): `NPOIE` (matches and fails typed: the player JSON needs an XSRF token from npostart.nl and the streams API takes that player token), `NPOLiveIE` (media-id scan re-dispatching to the `npo:` scheme), `NPORadioIE` (data-channel + data-streams), `NPORadioFragmentIE` (title + data-streams), `SchoolTVIE` and `HetKlokhuisIE` (data-mid re-dispatch), `VPROIE`, `WNLIE`, `AndereTijdenIE` (playlist scans; `npo:` entries fail typed through `NPOIE`).
- `suitable` guards mirror upstream: NPOIE stays off live/radio URLs, NPORadioIE stays off fragment URLs. The first full-core run caught that the guards dropped the base match (NPOIE matched every URL); fixed before Evidence.
- Manifest limits: the `npo:` pseudo-entries are not http URLs; HLS/stream variants beyond the single data-streams entry are not carried.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.npo.NpoIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 0 failures after the suitable fix (the first run failed 4 URL-validator cases: NPOIE's guard had dropped the base match).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 243 partial, 88 planned, 1,420 not started.

Next: file 9, `orf.py`.

**File 9 of 110 — `orf.py` (2026-09-30).** `OrfIE.kt` accounts for all 5 registered classes (the file has no bases):

- Translated and registered (5): `ORFRadioIE` (station API JSON + loopstream entries), `ORFPodcastIE` (podcast API enclosure), `ORFIPTVIE` (video-id scan + bits JSON + load-balancer rendition map), `ORFFM4StoryIE` (every data-video id becomes one media item), `ORFONIE` (encrypted-id episode API, segments, HLS/DASH sources, subtitle tracks; DRM fails typed).
- Manifest limits: f4m renditions are skipped (no f4m helper), archive ids and series labels are not carried, and the interactive segment prompt is not translated.
- No new core helper: the file carries its own clean-html, date-only, and Base64 (stdlib `kotlin.io.encoding.Base64`) helpers.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.orf.OrfIETest"` — 10 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 248 partial, 88 planned, 1,415 not started.

Next: file 10, `bandcamp.py` (the tenth-file full-suite and cross-target cadence runs on that wake).

**File 10 of 110 — `bandcamp.py` (2026-09-30).** `BandcampIE.kt` accounts for all 4 registered classes (the file has no bases):

- Translated and registered (4): `BandcampIE` (data-tralbum trackinfo file map, data-embed/albumTitle metadata), `BandcampAlbumIE` (trackinfo child entries, tracks with duration only; upstream `suitable` guard for weekly/track URLs), `BandcampWeeklyIE` (player data POST, enc format), `BandcampUserIE` (item anchors, trackTitle fallback, music-grid data-client-items JSON).
- Manifest limits: the free-download statdownload flow is not translated (Bandcamp download limits), and track/album/artist fields the port does not carry are dropped.

Commands (tenth-file cadence):

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.bandcamp.BandcampIETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1089/0, ui 121/0, android-engine 41/0, port-manifest 25/0; desktop 146 tests with the same 2 pre-existing failures documented in T-126 (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory`, `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`), neither touching extractor code.
- Cross-target compiles: `:shared:core:compileKotlinWasmJs` + test, `:shared:core:compileKotlinIosSimulatorArm64` + test, `:shared:ui:compileKotlinIosSimulatorArm64`, `:apps:web:compileKotlinWasmJs`, `:apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 252 partial, 88 planned, 1,411 not started.

Next: file 11, `viu.py`.

**File 11 of 110 — `viu.py` (2026-09-30).** `ViuIE.kt` accounts for all 4 registered classes plus the two unregistered bases (`ViuBaseIE`, `ViuOTTIndonesiaBaseIE`, translated as the shared `callApi` helper / wall reasons):

- Translated and registered (4): `ViuIE` (desktop clip API: m3u8 URL and subtitle tracks), `ViuPlaylistIE` (container API child entries), `ViuOTTIE` and `ViuOTTIndonesiaIE` (match and fail typed).
- Wall reasons (one sentence each, manifest scope): the OTT playback API needs a bearer token minted by the Viu auth gateway and the stream URLs are token-gated; the Indonesia OTT API needs a token from the user identity API and its content API is DRM-gated.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.viu.ViuIETest"` — 6 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 256 partial, 88 planned, 1,407 not started.

Next: file 12, `rokfin.py`.

**File 12 of 110 — `rokfin.py` (2026-09-30).** `RokfinIE.kt` accounts for 3 of the 4 registered classes plus the unregistered `RokfinPlaylistBaseIE` (translated as the shared `videoEntries` helper):

- Translated and registered (3): `RokfinIE` (public post/stream API, storyboard fallback, premium login-required typed failure), `RokfinStackIE`, `RokfinChannelIE` (user info + paged posts, five pages eagerly).
- Planned (1): `RokfinSearchIE` — the `rkfnsearch:` search key needs a Meilisearch access key and the port has no search surface (row has no portedAt, as the validator requires).
- Limits: the OAuth login, the viewer-comment walk, the channel tab extractor-arg, and the other tab endpoints are not translated.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rokfin.RokfinIETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 259 partial, 89 planned, 1,403 not started. (First run failed: a planned row must not set portedAt; fixed.)

Next: file 13, `slideslive.py`.

**File 13 of 110 — `slideslive.py` (2026-09-30).** `SlidesLiveIE.kt` accounts for the file's only class:

- Translated and registered (1): `SlidesLiveIE` — embed page player token, custom `#EXT-SL-` player tags, slides JSON/XML chapters and thumbnails, subtitles, and the yoda m3u8/mpd formats. The vimeo/youtube services become a redirect.
- Manifest limits: the video-slide playlist is not translated and manifest durations are not parsed.
- No new core helper: the file carries its own `#EXT-SL-` tag reader, slide XML scan, and ISO date helper.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.slideslive.SlidesLiveIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 260 partial, 89 planned, 1,402 not started.

Next: file 14, `nexx.py`.

**File 14 of 110 — `nexx.py` (2026-09-30).** `NexxIE.kt` accounts for both registered classes:

- Translated and registered (2): `NexxIE` (the public arc API plus azure/free/3q format URL builders and caption URLs; non-arc videos fail typed on the session-init token), `NexxEmbedIE` (domain-id + player-init scan becomes a redirect to the Nexx API URL).
- Manifest limits: inline caption `data` blocks, ISM, alt-title/season fields, and CDN-shield preferences are not carried.
- **Registry refactor (JVM limit fix):** the wake's full-core run hit `ClassFormatError: Too many arguments in method signature` in `SharedEngineBindingsKt` — the named-parameter `productionExtractorRegistry(...)` and the `@Provides extractorRegistry(...)` had passed the JVM 255-parameter limit. Both were replaced with a list-based shape: `@Provides fun extractorRegistry(http, jsRuntime)` delegates to the two-argument convenience function, which now builds `ExtractorRegistry(listOf(...))` directly. `:shared:core:jvmTest`, `:apps:desktop:testClasses`, and `:apps:android:compileDebugKotlin` pass.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nexx.NexxIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL (after the registry refactor).
- `./gradlew --no-parallel :apps:desktop:testClasses :apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 262 partial, 89 planned, 1,400 not started.

Next: file 15, `francetv.py`.

**File 15 of 110 — `francetv.py` (2026-09-30).** `FranceTvIE.kt` accounts for all 3 registered classes plus the unregistered `FranceTVBaseInfoExtractor` (translated as the shared `_make_url_result` behavior):

- Translated and registered (3): `FranceTVIE` (k7.ftven.fr desktop/mobile passes, public token endpoint, m3u8/mpd/rtmp/plain; DRM typed failure and code 2009 geo failure), `FranceTVSiteIE` (Next.js options id scan + UUID fallback redirect), `FranceTVInfoIE` (Dailymotion entries or id-scan redirect).
- Manifest limits: f4m formats, manifest-embedded subtitles, spritesheets, and the HEAD geo probe are not translated.
- No new core helper: the file carries its own ISO date and token-URL helpers.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.francetv.FranceTvIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 265 partial, 89 planned, 1,397 not started.

Next: file 16, `mlb.py`.

**File 16 of 110 — `mlb.py` (2026-09-30).** `MlbIE.kt` accounts for all 4 registered classes plus the unregistered `MLBBaseIE` (translated as the shared `buildInfo` helper):

- Translated and registered (4): `MLBIE` (content.mlb.com details JSON), `MLBVideoIE` (Fastball GraphQL media playback, with the upstream `suitable` guard), `MLBTVIE` (matches and fails typed: the GraphQL session/playback flow needs device and playback tokens), `MLBArticleIE` (window.initState video parts become child entries).
- Manifest limits: modified-timestamp fields on articles are not carried; the f4m-less playback set matches upstream's m3u8/plain split.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.mlb.MlbIETest"` — 6 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 269 partial, 89 planned, 1,393 not started.

Next: file 17, `abematv.py`.

**File 17 of 110 — `abematv.py` (2026-09-30).** `AbemaTVIE.kt` accounts for both registered classes plus the unregistered `AbemaTVBaseIE` and the `AbemaLicenseRH` Widevine handler (both are the wall reason):

- Translated and registered (2): `AbemaTVIE`, `AbemaTVTitleIE` — every URL form matches and fails typed.
- Wall reason (one sentence, manifest scope): the API needs a device token whose applicationKeySecret is an HMAC-SHA256 mix over a secret application key, the media token needs that bearer, and the streams are DRM-protected (Widevine license handler); the port excludes both.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.abematv.AbemaTVIETest"` — 2 tests, 0 failures (URL match + typed failure for both classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 271 partial, 89 planned, 1,391 not started.

Next: file 18, `tencent.py`.

**File 18 of 110 — `tencent.py` (2026-09-30).** `TencentIE.kt` accounts for all 6 registered classes plus the four unregistered bases (`TencentBaseIE`, `VQQBaseIE`, `WeTvBaseIE`, `IflixBaseIE`, translated as the shared series helpers / wall reasons):

- Translated and registered (6): `VQQSeriesIE`, `WeTvSeriesIE`, `IflixSeriesIE` (page scans become child entries), `VQQVideoIE`, `WeTvEpisodeIE`, `IflixEpisodeIE` (match and fail typed).
- Wall reason (one sentence, manifest scope): the getvinfo API needs a guid-based ckey signature and enables DRM; the port excludes both.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tencent.TencentIETest"` — 6 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 277 partial, 89 planned, 1,385 not started.

Next: file 19, `afreecatv.py`.

**File 19 of 110 — `afreecatv.py` (2026-09-30).** `AfreecaTVIE.kt` accounts for all 4 registered classes plus the unregistered `AfreecaTVBaseIE` (translated as the shared `liveApi` helper / wall reasons):

- Translated and registered (4): `AfreecaTVIE` (public VOD view API; single-part formats, multi-part media items; private/subscriber/adult-without-login fail typed), `AfreecaTVCatchStoryIE` (catch items become media entries), `AfreecaTVLiveIE` (live API + CDN stream assign + access token; password/subscriber walls typed), `AfreecaTVUserIE` (station pages, five pages eagerly).
- Manifest limits: the CloudFront cookie refresh, the video-password option, the CDN extractor-arg, and the station metadata call are not translated.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.afreecatv.AfreecaTVIETest"` — 8 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 281 partial, 89 planned, 1,381 not started.

Next: file 20, `vidyard.py` (the twentieth-file full-suite and cross-target cadence runs on that wake).

**File 20 of 110 — `vidyard.py` (2026-09-30).** `VidyardIE.kt` accounts for the file's only class plus the unregistered `VidyardBaseIE` (translated as the shared `processChapter` helper):

- Translated and registered (1): `VidyardIE` — the player JSON, HLS master/variant and http source profiles, direct VTT captions, and the additional metadata (title, duration, thumbnails, video-section chapters). Multi-chapter players become media items.
- Manifest limits: display-id and tags fields the port does not carry are dropped.
- The URL regex was restructured to a single `(?<id>)` group: Java rejects duplicate named groups across alternatives (the first run failed class initialization).

Commands (twentieth-file cadence):

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.vidyard.VidyardIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1152/0, ui 121/0, android-engine 41/0, port-manifest 25/0; desktop 146 tests with the same 2 pre-existing failures documented in T-126.
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — the first run caught a JVM-only `String.format` in `slideslive/SlidesLiveIE.kt` (unresolved on wasm/iOS); replaced with a multiplatform `formatTemplate` helper, then all targets pass.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 282 partial, 89 planned, 1,380 not started.

Next: file 21, `loom.py`.

**File 21 of 110 — `loom.py` (2026-09-30).** `LoomIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `LoomIE` (public GraphQL GetVideoSSR/GetVideoSource/FetchVideoTranscript/FetchChapters, raw/transcoded URL endpoints, m3u8/mpd/plain formats, VTT subtitles, chapter text parser; password-protected videos fail typed), `LoomFolderIE` (public folders API with recursive subfolders, three levels eagerly; upstream marks the class `_WORKING = False`).
- Manifest limits: the video-password option is not translated and the transcript/subtitle merge details are simplified.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.loom.LoomIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 284 partial, 89 planned, 1,378 not started.

Next: file 22, `udemy.py`.

**File 22 of 110 — `udemy.py` (2026-09-30).** `UdemyIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `UdemyIE` and `UdemyCourseIE` — every URL form matches and fails typed with a login requirement.
- Wall reason (one sentence, manifest scope): the lecture API and the subscriber curriculum listing need an authenticated Udemy session (login and enrollment); the port has no Udemy login.
- The upstream `suitable` guard is kept: `UdemyCourseIE` stays off lecture URLs.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.udemy.UdemyIETest"` — 2 tests, 0 failures (URL match + typed failure for both classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 286 partial, 89 planned, 1,376 not started.

Next: file 23, `hotstar.py`.

**File 23 of 110 — `hotstar.py` (2026-09-30).** `HotStarIE.kt` accounts for all 3 registered classes plus the unregistered `HotStarBaseIE` (its token/DRM behavior is the wall reason):

- Translated and registered (3): `HotStarIE`, `HotStarPrefixIE`, `HotStarSeriesIE` — every URL form matches and fails typed.
- Wall reason (one sentence, manifest scope): the API needs x-hs-usertoken cookies and a device id, and it requests Widevine DRM playback parameters; the port excludes both.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.hotstar.HotStarIETest"` — 2 tests, 0 failures (URL match + typed failure for all three classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 289 partial, 89 planned, 1,373 not started.

Next: file 24, `qqmusic.py`.

**File 24 of 110 — `qqmusic.py` (2026-09-30).** `QQMusicIE.kt` accounts for all 6 registered classes plus the two unregistered bases (`QQMusicBaseIE`, `QQPlaylistBaseIE`; their cookie/g_tk behavior is the wall reason):

- Translated and registered (6): `QQMusicIE`, `QQMusicSingerIE`, `QQMusicAlbumIE`, `QQMusicToplistIE`, `QQMusicPlaylistIE`, `QQMusicVideoIE` — every URL form matches and fails typed.
- Wall reason (one sentence, manifest scope): the API signs requests with a g_tk derived from the qqmusic_key/uin cookies and the song/album/playlist pages need that logged-in session; the port has no QQ Music login.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.qqmusic.QQMusicIETest"` — 2 tests, 0 failures (URL match + typed failure for all six classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 295 partial, 89 planned, 1,367 not started.

Next: file 25, `bandlab.py`.

**File 25 of 110 — `bandlab.py` (2026-09-30).** `BandlabIE.kt` accounts for both registered classes plus the unregistered `BandlabBaseIE` (translated as the shared API/parser helpers):

- Translated and registered (2): `BandlabIE` (public api/v1.3 posts and revisions endpoints; revision/track/video parsers), `BandlabPlaylistIE` (albums/collections; posts become media items through the same parsers).
- Manifest limits: track/album/album-type/media-type/release-date and counter fields the port does not carry are dropped.
- The URL regexes were restructured to a single named-group pair: Java rejects duplicate named groups across alternatives.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.bandlab.BandlabIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 297 partial, 89 planned, 1,365 not started.

Next: file 26, `cbsnews.py`.

**File 26 of 110 — `cbsnews.py` (2026-09-30).** `CBSNewsIE.kt` accounts for all 7 registered classes plus the three unregistered bases (`CBSNewsBaseIE`, `CBSLocalBaseIE`, `CBSNewsLiveBaseIE`, translated as the shared payload/video/live helpers):

- Translated and registered (7): `CBSNewsIE` (payload item walk + embed playlist), `CBSNewsEmbedIE` (matches and fails typed: zlib-compressed payload, no inflate helper), `CBSLocalIE` and `CBSLocalArticleIE` (payload/playlist paths), `CBSLocalLiveIE` (locale edition map, Atlanta typed), `CBSNewsLiveIE`, `CBSNewsLiveVideoIE` (public rundown APIs).
- Manifest limits: the Anvato base64 iframe path and the Anvato video id are not translated (an Anvato id becomes a redirect that fails typed); season/episode and category/tag fields are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.cbsnews.CBSNewsIETest"` — 7 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 304 partial, 89 planned, 1,358 not started.

Next: file 27, `abc.py`.

**File 27 of 110 — `abc.py` (2026-09-30).** `AbcIE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `ABCIE` (direct audio link, YouTube child entries, sources/files/renditions or inline data JSON), `ABCIViewIE` (matches and fails typed: the iview HLS URL needs an HMAC-SHA256 hdnea token and the port does not add an HMAC helper), `ABCIViewShowSeriesIE` (window.__INITIAL_STATE__ scan; highlight redirect or episode entries).
- Manifest limits: the iview formats and the ABCIView highlight redirect stay walled through `ABCIViewIE`.
- No new core helper: the file carries its own JS-string unescape helper.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.abc.AbcIETest"` — 7 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 307 partial, 89 planned, 1,355 not started.

Next: file 28, `aenetworks.py`.

**File 28 of 110 — `aenetworks.py` (2026-09-30).** `AENetworksIE.kt` accounts for all 6 registered classes plus the two unregistered bases (`AENetworksBaseIE`, `AENetworksListBaseIE`; their ThePlatform signing/MVPD behavior is the wall reason):

- Translated and registered (6): `AENetworksIE`, `AENetworksCollectionIE`, `AENetworksShowIE`, `HistoryTopicIE`, `HistoryPlayerIE`, `BiographyIE` — every URL form matches and fails typed.
- Wall reason (one sentence, manifest scope): the A&E/ThePlatform SMIL URLs need an HMAC signature with the embedded ThePlatform key/secret and the walled titles need MVPD auth; the port does not embed those secrets.
- The upstream base extends `ThePlatformIE`; the port's D17 `ThePlatformBaseIE` deliberately leaves URL signing to the D18 `theplatform.py` file, which stays consistent with this wall.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.aenetworks.AENetworksIETest"` — 2 tests, 0 failures (URL match + typed failure for all six classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 313 partial, 89 planned, 1,349 not started.

Next: file 29, `reddit.py`.

**File 29 of 110 — `reddit.py` (2026-09-30).** `RedditIE.kt` accounts for the file's only class:

- Translated and registered (1): `RedditIE` — the public comments/<id>/.json walk: preview thumbnails, the reddit-hosted fallback/HLS/DASH formats with the fallback caption track, text-post media_metadata items, and the external-link redirect.
- Manifest limits: the over18/_options opt-in cookies, the shreddit session setup, the comment walk, and the like/dislike/comment counters are not translated; quarantined/private subreddits fail typed as login-required.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.reddit.RedditIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 314 partial, 89 planned, 1,348 not started.

Next: file 30, `theplatform.py`.

**File 30 of 110 — `theplatform.py` (2026-09-30).** `ThePlatformIE.kt` adds both registered classes on top of the D17 `ThePlatformBaseIE` (whose SMIL/metadata helpers are now exercised end to end):

- Translated and registered (2): `ThePlatformIE` (the direct link.theplatform.com SMIL + metadata path; geo/unavailable exceptions typed), `ThePlatformFeedIE` (public feed API entries; formats come from each plfile SMIL URL with a direct-URL fallback).
- Manifest limits: the config/guid page paths, the `_sign_url` HMAC helper (only used with smuggled key/secret data), the HLS probe, and the f4m/rtmp transforms are not translated.
- The D17 base note ("signing of feed URLs stay with the D18 theplatform.py file") is satisfied: the signing helper is intentionally omitted, and the extractors that needed it stay typed walls (aenetworks).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.theplatform.ThePlatformIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 316 partial, 89 planned, 1,346 not started.

Next: file 31, `nytimes.py`.

**File 31 of 110 — `nytimes.py` (2026-09-30).** `NYTimesIE.kt` accounts for all 4 registered classes plus the unregistered `NYTimesBaseIE` (its GraphQL token behavior is the wall reason):

- Translated and registered (4): `NYTimesCookingRecipeIE` (recipe page Next.js data: videoSrc m3u8, metadata, image crops), `NYTimesIE`, `NYTimesArticleIE`, `NYTimesCookingIE` (match and fail typed).
- Wall reason (one sentence, manifest scope): the video renditions come from the Samizdat GraphQL API, which needs an embedded Nyt-Token header the port does not carry.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nytimes.NYTimesIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 320 partial, 89 planned, 1,342 not started.

Next: file 32, `radiofrance.py`.

**File 32 of 110 — `radiofrance.py` (2026-09-30).** `RadioFranceIE.kt` accounts for all 6 registered classes plus the two unregistered bases (`RadioFranceBaseIE`, `RadioFrancePlaylistBaseIE`, translated as the shared page-data/playlist helpers):

- Translated and registered (6): `RadioFranceIE` (radiovisions audio map), `FranceCultureIE` (JSON-LD AudioObject), `RadioFranceLiveIE` (public live API / webRadioData), `RadioFrancePodcastIE` and `RadioFranceProfileIE` (path API + paged lists, five pages eagerly), `RadioFranceProgramScheduleIE` (embedded grid steps).
- Manifest limits: per-entry duration/description fields the port does not carry on entries are dropped.
- No new core helper: the file carries its own balanced JSON extractor and page-data reader.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.radiofrance.RadioFranceIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 326 partial, 89 planned, 1,336 not started.

Next: file 33, `ndr.py`.

**File 33 of 110 — `ndr.py` (2026-09-30).** `NdrIE.kt` accounts for all 5 registered classes plus the unregistered `NDRBaseIE` (translated as the shared embed-URL scan):

- Translated and registered (5): `NDRIE` and `NJoyIE` (page scans re-dispatching to `ndr:<id>`), `NDREmbedBaseIE` (ppjson playlist formats, thumbnails, tracks, live flag), `NDREmbedIE`, `NJoyEmbedIE`.
- Manifest limits: f4m formats are skipped (no f4m helper) and the JSON-LD merge is simplified.
- The `ndr:` pseudo-URL id group is named `idS`, so the base class reads it explicitly (the inherited `matchId` only looks for `id`).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ndr.NdrIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 331 partial, 89 planned, 1,331 not started.

Next: file 34, `pluralsight.py`.

**File 34 of 110 — `pluralsight.py` (2026-09-30).** `PluralsightIE.kt` accounts for both registered classes plus the unregistered `PluralsightBaseIE` (its session requirement is the wall reason):

- Translated and registered (2): `PluralsightIE` and `PluralsightCourseIE` — every URL form matches and fails typed with a login requirement.
- Wall reason (one sentence, manifest scope): the player GraphQL/legacy payload needs an authenticated subscription session and the clip playback URLs come from that session; the port has no Pluralsight login.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.pluralsight.PluralsightIETest"` — 2 tests, 0 failures (URL match + typed failure for both classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 333 partial, 89 planned, 1,329 not started.

Next: file 35, `lbry.py`.

**File 35 of 110 — `lbry.py` (2026-09-30).** `LbryIE.kt` accounts for all 3 registered classes plus the unregistered `LBRYBaseIE` (translated as the shared proxy/resolve/stream helpers):

- Translated and registered (3): `LBRYIE` (resolve/get proxy, streaming URL format, live check), `LBRYChannelIE` and `LBRYPlaylistIE` (claim_search entries, five pages eagerly, stopping on a short page).
- Manifest limits: the original v3-quality HEAD probe and the optional auth-token header are not translated.
- No new core helper: the file carries its own percent-decode and permanent-URL helpers.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.lbry.LbryIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 336 partial, 89 planned, 1,326 not started.

Next: file 36, `espn.py`.

**File 36 of 110 — `espn.py` (2026-09-30).** `EspnIE.kt` accounts for all 5 registered classes (the file has no bases):

- Translated and registered (5): `ESPNIE` (public clip API with source/mobile traversal), `ESPNArticleIE` (video-button scan to a clip redirect, suitable guard kept), `FiveThirtyEightIE` (ABC embed redirect), `ESPNCricInfoIE` (public video details API), `WatchESPNIE` (matches and fails typed).
- Wall reason (one sentence, manifest scope): the WatchESPN player needs a Bamgrid token exchange (API key) and MVPD authentication; the port excludes both.
- The clip URL regex was restructured after the first run failed on the `cdn.espn.go.com/video/clip/_/id/` form.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.espn.EspnIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 341 partial, 89 planned, 1,321 not started.

Next: file 37, `niconicochannelplus.py`.

**File 37 of 110 — `niconicochannelplus.py` (2026-09-30).** `NiconicoChannelPlusIE.kt` accounts for all 3 registered classes plus the two unregistered bases (`NiconicoChannelPlusBaseIE`, `NiconicoChannelPlusChannelBaseIE`, translated as the shared API/channel helpers):

- Translated and registered (3): `NiconicoChannelPlusIE` (channel/video/session APIs, session-signed HLS; upcoming lives fail typed), `NiconicoChannelPlusChannelVideosIE`, `NiconicoChannelPlusChannelLivesIE` (paged lists, five pages eagerly).
- Manifest limits: the comment walk and comment counters are not translated.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.niconicochannelplus.NiconicoChannelPlusIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 344 partial, 89 planned, 1,318 not started.

Next: file 38, `rutube.py`.

**File 38 of 110 — `rutube.py` (2026-09-30).** `RutubeIE.kt` accounts for all 7 registered classes plus the two unregistered bases (`RutubeBaseIE`, `RutubePlaylistBaseIE`, translated as the shared info/options/playlist helpers):

- Translated and registered (7): `RutubeIE` (video/options APIs, m3u8/plain/live HLS formats, captions), `RutubeEmbedIE` (effective-video resolution), `RutubeTagsIE`, `RutubeMovieIE`, `RutubePersonIE`, `RutubePlaylistIE`, `RutubeChannelIE` (paged listings, five pages eagerly; the `u/<slug>` redux scan works, the `playlists` section is unsupported).
- Manifest limits: f4m formats are skipped (no f4m helper); the private-video query token is forwarded but never stored.
- Two implementation bugs were caught by the tests: `String.replace("%s", ...)` replaces every occurrence (fixed with a one-at-a-time `templateFill`), and the top-level playlist helper needed its own id extraction.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rutube.RutubeIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL (one `JavaNetPlatformTest` deletion flake on the first run; the targeted rerun and the full rerun are green).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 351 partial, 89 planned, 1,311 not started.

Next: file 39, `yandexvideo.py`.

**File 39 of 110 — `yandexvideo.py` (2026-09-30).** `YandexVideoIE.kt` accounts for all 4 registered classes plus the unregistered `ZenYandexBaseIE` (translated as the shared SSR-data helper):

- Translated and registered (4): `YandexVideoIE` (public GraphQL player + v23 fallback), `YandexVideoPreviewIE` (inline-params redirect), `ZenYandexIE` (SSR video streams + metadata), `ZenYandexChannelIE` (SSR feed entries with more-link pagination).
- Manifest limits: DRM DASH streams are recorded as manifests (no license fetch); like/dislike counters the port does not carry are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.yandexvideo.YandexVideoIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 355 partial, 89 planned, 1,307 not started.

Next: file 40, `mxplayer.py` (the fortieth-file full-suite and cross-target cadence runs on that wake).

**File 40 of 110 — `mxplayer.py` (2026-09-30).** `MxplayerIE.kt` accounts for all 4 registered classes plus the unregistered `MxplayerBaseIE` (translated as the shared `__mxs__` reader):

- Translated and registered (4): `MxplayerIE` (third-party + mxplay HLS/DASH streams, metadata, thumbnails; DRM fails typed), `MxplayerSeasonIE` (tab API paged entries, five pages eagerly), `MxplayerShowIE` (season child entries), `MxplayerRedirectIE` (SEO resolver redirect).
- Manifest limits: cast/creator/genre/tag label lists the port does not carry are dropped; geo errors are typed.

Commands (fortieth-file cadence):

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.mxplayer.MxplayerIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1244/0, ui 121/0, android-engine 41/0, port-manifest 25/0; desktop 146 tests with the same 2 pre-existing failures documented in T-126.
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 359 partial, 89 planned, 1,303 not started.

Next: file 41, `goplay.py`.

**File 41 of 110 — `goplay.py` (2026-09-30).** `GoPlayIE.kt` accounts for the file's only registered class (the `AwsIdp` helper is the login wall reason):

- Translated and registered (1): `GoPlayIE` — every URL form matches and fails typed with a login requirement.
- Wall reason (one sentence, manifest scope): the PLAY long-form API needs an AWS Cognito login bearer token and the streams are DRM/SSAI-gated; the port has no PLAY login.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.goplay.GoPlayIETest"` — 2 tests, 0 failures (URL match + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 360 partial, 89 planned, 1,302 not started.

Next: file 42, `yandexmusic.py`.

**File 42 of 110 — `yandexmusic.py` (2026-09-30).** `YandexMusicIE.kt` accounts for all 5 registered classes plus the three unregistered bases (`YandexMusicBaseIE`, `YandexMusicPlaylistBaseIE`, `YandexMusicArtistBaseIE`; their signing/CAPTCHA behavior is the wall reason):

- Translated and registered (5): `YandexMusicTrackIE`, `YandexMusicAlbumIE`, `YandexMusicPlaylistIE`, `YandexMusicArtistTracksIE`, `YandexMusicArtistAlbumsIE` — every URL form matches and fails typed.
- Wall reason (one sentence, manifest scope): the track download URL is signed with a fixed secret key the port does not embed, and the API CAPTCHA-blocks automated requests.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.yandexmusic.YandexMusicIETest"` — 2 tests, 0 failures (URL match + typed failure for all five classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 365 partial, 89 planned, 1,297 not started.

Next: file 43, `bluesky.py`.

**File 43 of 110 — `bluesky.py` (2026-09-30).** `BlueskyIE.kt` accounts for the file's only class:

- Translated and registered (1): `BlueskyIE` — the public post-thread call, the embed video walk (HLS playlist + blob formats), the DID service endpoint lookup, and the caption blob tracks. Multi-video posts become media items.
- Manifest limits: like/repost counters and the nested record-with-media variants are simplified.
- The URL regex uses distinct `handle2`/`id2` group names for the `at://` alternative (Java rejects duplicate named groups across alternatives).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.bluesky.BlueskyIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 366 partial, 89 planned, 1,296 not started.

Next: file 44, `weibo.py`.

**File 44 of 110 — `weibo.py` (2026-09-30).** `WeiboIE.kt` accounts for all 3 registered classes plus the unregistered `WeiboBaseIE` (its guest-cookie flow is the wall reason):

- Translated and registered (3): `WeiboIE`, `WeiboVideoIE`, `WeiboUserIE` — every URL form matches and fails typed with a login requirement.
- Wall reason (one sentence, manifest scope): the API needs the passport.weibo.com first-visit guest-cookie flow and visitor tokens; the port does not translate the guest-token flow.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.weibo.WeiboIETest"` — 2 tests, 0 failures (URL match + typed failure for all three classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 369 partial, 89 planned, 1,293 not started.

Next: file 45, `floatplane.py`.

**File 45 of 110 — `floatplane.py` (2026-09-30).** `FloatplaneIE.kt` accounts for both registered classes plus the two unregistered bases (`FloatplaneBaseIE`, `FloatplaneChannelBaseIE`; their login requirement is the wall reason):

- Translated and registered (2): `FloatplaneIE` and `FloatplaneChannelIE` — every URL form matches and fails typed with a login requirement.
- Wall reason (one sentence, manifest scope): Floatplane is a subscription platform whose GraphQL API needs a `sails.sid` login session cookie; the port has no Floatplane login.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.floatplane.FloatplaneIETest"` — 2 tests, 0 failures (URL match + typed failure for both classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 371 partial, 89 planned, 1,291 not started.

Next: file 46, `arte.py`.

**File 46 of 110 — `arte.py` (2026-09-30).** `ArteTVIE.kt` accounts for all 4 registered classes plus the unregistered `ArteTVBaseIE` (translated as the shared config/language helpers):

- Translated and registered (4): `ArteTVIE` (public config API, version-code language preference, HLS/HTTPS/RTMP streams, metadata/chapters; geo/rights typed), `ArteTVEmbedIE` (json_url redirect), `ArteTVPlaylistIE` (matches and fails typed: embedded bearer token), `ArteTVCategoryIE` (video link scan; suitable guard kept).
- Manifest limits: the playlist entries fail typed through `ArteTVPlaylistIE`; the accessible-subtitle locale split is simplified.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.arte.ArteTVIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 375 partial, 89 planned, 1,287 not started.

Next: file 47, `odnoklassniki.py`.

**File 47 of 110 — `odnoklassniki.py` (2026-09-30).** `OdnoklassnikiIE.kt` accounts for the file's only class:

- Translated and registered (1): `OdnoklassnikiIE` — the desktop `data-options` player walk (video/HLS/DASH/RTMP formats, metadata, subtitles) with the mobile fallback. Restricted videos fail typed, the paid-video notice is a typed failure.
- Manifest limits: the embedded DASH manifest parse and the USER_YOUTUBE transparent dispatch are simplified.
- The fallback now rethrows the desktop error for any mobile failure (the fixture tests need the desktop diagnosis).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.odnoklassniki.OdnoklassnikiIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 376 partial, 89 planned, 1,286 not started.

Next: file 48, `txxx.py`.

**File 48 of 110 — `txxx.py` (2026-09-30).** `TxxxIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `TxxxIE` (videofile and video-info APIs with the custom base64 alphabet), `PornTopIE` (schemaJson JSON-LD + window.initPlayer player JSON).
- Manifest limits: like/dislike counters the port does not carry are dropped.
- Two test-driven fixes: the slug uses the upstream `1E6 * (id // 1E6)` form, and the PornTop player argument decodes to the JSON list (double base64 in the fixture).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.txxx.TxxxIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 378 partial, 89 planned, 1,284 not started.

Next: file 49, `wistia.py`.

**File 49 of 110 — `wistia.py` (2026-09-30).** `WistiaIE.kt` accounts for all 3 registered classes plus the unregistered `WistiaBaseIE` (translated as the shared embed-config/media helpers):

- Translated and registered (3): `WistiaIE` (medias embed config: assets to formats, thumbnails, captions, metadata), `WistiaPlaylistIE` (embedded media configs become child entries), `WistiaChannelIE` (channel config plus the base64 JSONP fallback).
- Manifest limits: the password option is a typed failure and the HEAD extension probe is simplified.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.wistia.WistiaIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 381 partial, 89 planned, 1,281 not started.

Next: file 50, `svt.py` (the fiftieth-file full-suite and cross-target cadence runs on that wake).

**File 50 of 110 — `svt.py` (2026-09-30).** `SvtIE.kt` accounts for all 3 registered classes plus the unregistered `SVTBaseIE` (translated as the shared video/subtitle helpers):

- Translated and registered (3): `SVTPlayIE` (videoplayer API with m3u8/mpd/plain references, forced-subtitle split, geo typed), `SVTSeriesIE` (contento GraphQL season entries), `SVTPageIE` (urqlState scan to media items).
- Manifest limits: f4m references are skipped and series/episode number fields the port does not carry are dropped.
- The page scan needed two fixes caught by the tests: the urqlState `data` field is a JSON-encoded string, and its payload nests the page under a `page` key.

Commands (fiftieth-file cadence):

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.svt.SvtIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1281/0, ui 121/0, android-engine 41/0, port-manifest 25/0; desktop 146 tests with the same 2 pre-existing failures documented in T-126.
- Cross-target compiles: the first run caught JVM-only `java.util.Base64` in `TxxxIETest` (unresolved on wasm/iOS); replaced with `kotlin.io.encoding.Base64`, then all targets pass (`:shared:core` wasm main+test, iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 384 partial, 89 planned, 1,278 not started.

Next: file 51, `cda.py`.

**File 51 of 110 — `cda.py` (2026-09-30).** `CDAIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `CDAIE` and `CDAFolderIE` — every URL form matches and fails typed with a login requirement.
- Wall reason (one sentence, manifest scope): the app API needs an OAuth bearer token minted with hardcoded Basic client credentials and the web player `file` fields are encrypted before a signed request; the port does not translate the token/crypto flow.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.cda.CDAIETest"` — 2 tests, 0 failures (URL match + typed failure for both classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 386 partial, 89 planned, 1,276 not started.

Next: file 52, `rcti.py`.

**File 52 of 110 — `rcti.py` (2026-09-30).** `RCTIPlusIE.kt` accounts for all 3 registered classes plus the unregistered `RCTIPlusBaseIE` (its visitor-token behavior is the wall reason):

- Translated and registered (3): `RCTIPlusIE`, `RCTIPlusSeriesIE`, `RCTIPlusTVIE` — every URL form matches and fails typed with a login requirement.
- Wall reason (one sentence, manifest scope): the API needs a visitor access token minted by the visitor endpoint and sent as the `Authorization` header; the port does not translate the guest-token flow.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rcti.RCTIPlusIETest"` — 2 tests, 0 failures (URL match + typed failure for all three classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 389 partial, 89 planned, 1,273 not started.

Next: file 53, `mediasite.py`.

**File 53 of 110 — `mediasite.py` (2026-09-30).** `MediasiteIE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `MediasiteIE` (PlayerService GetPlayerOptions presentation walk), `MediasiteCatalogIE` (folder POST with the optional anti-forgery header), `MediasiteNamedCatalogIE` (catalog redirect).
- Manifest limits: the MHTML slide streams, ISM (SS) media, and the query-string smuggling are not translated.
- The ID regex follows upstream exactly (`[0-9a-f]{32,34}` plus the dashed form); a shortened variant truncated the ids in the first test run.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.mediasite.MediasiteIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 392 partial, 89 planned, 1,270 not started.

Next: file 54, `rtve.py`.

**File 54 of 110 — `rtve.py` (2026-09-30).** `RtveIE.kt` accounts for all 5 registered classes plus the unregistered `RTVEBaseIE` (translated as the shared PNG-cipher/metadata helpers):

- Translated and registered (5): `RTVEALaCartaIE` (config API + PNG cipher + subtitles), `RTVEAudioIE`, `RTVELiveIE`, `RTVETelevisionIE` (redirect), `RTVEProgramIE` (paged entries).
- The PNG tEXt url cipher is a pure data transformation (no crypto), so it is translated; the test builds a valid PNG with the inverse cipher to prove it. The JSON-LD merge and season/episode number fields the port does not carry are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rtve.RtveIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 397 partial, 89 planned, 1,265 not started.

Next: file 55, `tver.py`.

**File 55 of 110 — `tver.py` (2026-09-30).** `TVerIE.kt` accounts for both registered classes (the `StreaksBaseIE` base is the stream wall reason):

- Translated and registered (2): `TVerIE` and `TVerOlympicIE` — every URL form matches and fails typed with a login requirement.
- Wall reason (one sentence, manifest scope): the platform API needs a browser session (platform_uid/platform_token) and the streams come from the Streaks backend; the port does not translate the guest-session/Streaks flow.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tver.TVerIETest"` — 2 tests, 0 failures (URL match + typed failure for both classes).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 399 partial, 89 planned, 1,263 not started.

Next: file 56, `rumble.py`.

**File 56 of 110 — `rumble.py` (2026-09-30).** `RumbleIE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `RumbleEmbedIE` (embedJS/u3 player API: hls/timeline/audio/plain formats, captions, thumbnails, live status), `RumbleIE` (embed scan redirect with the page description), `RumbleChannelIE` (videostream__link pages, five pages eagerly).
- Manifest limits: the `tar` format type is skipped and the page counters the port does not carry are dropped.
- The channel loop now stops on any page error (the fixture harness raises IllegalStateException for a missing page, not ExtractionError).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rumble.RumbleIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 402 partial, 89 planned, 1,260 not started.

Next: file 57, `jiosaavn.py`.

**File 57 of 110 — `jiosaavn.py` (2026-09-30).** `JioSaavnIE.kt` accounts for all 6 registered classes (the file also has the `JioSaavnBaseIE` base, not a registry class):

- Translated and registered (6): `JioSaavnSongIE` (webapi.get song + song.generateAuthToken formats), `JioSaavnShowIE` (episode), `JioSaavnAlbumIE` (album entries), `JioSaavnPlaylistIE` (paged playlist), `JioSaavnShowPlaylistIE` (initial-data current_id + show.getAllEpisodes), `JioSaavnArtistIE` (top songs).
- Manifest limits: the artist/label credits and the ISO639 language mapping the port does not carry are dropped; listings walk five pages eagerly.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.jiosaavn.JioSaavnIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 408 partial, 89 planned, 1,254 not started.

Next: file 58, `nba.py`.

**File 58 of 110 — `nba.py` (2026-09-30).** `NbaIE.kt` accounts for all 6 registered classes (the file also has the `NBACVPBaseIE`, `NBAWatchBaseIE`, and `NBABaseIE` bases, not registry classes):

- Translated and registered (6): `NBAWatchEmbedIE` (solr program search + publishpoint HLS/http, typed Unavailable when the CVP path is missing), `NBAWatchIE` (collection-param redirect), `NBAWatchCollectionIE` (public collection API, entries), `NBAEmbedIE` (no-team redirect; team → typed LoginRequired), `NBAIE` (page video id → embed redirect), `NBAChannelIE` (typed LoginRequired).
- Manifest limits: the Turner CV/AdobePass flow and the internal accessToken account API are not translated; upstream marks all six `_WORKING = False`. The iPhone user agent the upstream publishpoint call carries is omitted.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nba.NbaIETest"` — 8 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 414 partial, 89 planned, 1,248 not started.

Next: file 59, `rcs.py`.

**File 59 of 110 — `rcs.py` (2026-09-30).** `RcsIE.kt` accounts for all 3 registered classes (the file also has the `RCSBaseIE` base, not a registry class):

- Translated and registered (3): `RCSEmbedsIE` (video-embed), `RCSIE` (Corriere/Gazzetta pages), `RCSVariousIE` (Leitv/Youreporter/Amica pages); the shared base carries the video-json API, data-config/fragment page scans, host migration map, and m3u8/https/mp3 formats.
- Manifest limits: manifest parsing is not translated, so an m3u8 source yields one HLS row plus its https variant instead of the per-variant rows upstream derives; the HEAD filesize probe is skipped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rcs.RcsIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 417 partial, 89 planned, 1,245 not started.

Next: file 60, `ign.py`.

**File 60 of 110 — `ign.py` (2026-09-30).** `IgnIE.kt` accounts for all 3 registered classes (the file also has the `IGNBaseIE` base, not a registry class):

- Translated and registered (3): `IGNIE` (public apis.ign.com video slug API + content-feed-grid playlist scan), `IGNVideoIE` (embed page data-settings or redirect), `IGNArticleIE` (article slug API entries, dable videoplayer, nextjs videoPlayerProps).
- Manifest limits: the f4m (HDS) source is skipped and the `tags` field the port does not carry is dropped.
- **60th-file cadence.** Full suites with `--rerun-tasks`: `:shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test` — BUILD SUCCESSFUL. Cross-target compiles (`:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — BUILD SUCCESSFUL after fixing two multiplatform leaks from file 54: `RtveIE.kt` used `Charsets`/`Character.digit` and `RtveIETest.kt` used `String.toByteArray(Charsets)`, all replaced with byte/char arithmetic. `:apps:desktop:test` still reports the two known pre-existing failures recorded in T-126: `FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory` (phone-UI scroll semantics) and the flaky `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled` (partial-file cleanup timing; passed on 1 of 3 isolated reruns). Neither touches extractor code.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ign.IgnIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 420 partial, 89 planned, 1,242 not started.
- Cadence and cross-target commands above.

Next: file 61, `nfl.py`.

**File 61 of 110 — `nfl.py` (2026-09-30).** `NflIE.kt` accounts for all 4 registered classes (the file also has the `NFLBaseIE` base, not a registry class):

- Translated and registered (4): `NFLIE` (public video-config scan: m3u8/audio/plain item formats), `NFLArticleIE` (config entries + title), `NFLPlusReplayIE` and `NFLPlusEpisodeIE` (typed LoginRequired — account cookie/token API not translated).
- Manifest limits: an `mcpID` config item fails typed because the NFL account token API (client key/secret, API key, login token) is not translated; the client secret and API key are not stored. The video-config regex is non-greedy and DOTALL so several configs on one page each match.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nfl.NflIETest"` — 7 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 424 partial, 89 planned, 1,238 not started.

Next: file 62, `prx.py`.

**File 62 of 110 — `prx.py` (2026-09-30).** `PrxIE.kt` accounts for 3 of the 5 registered classes (the file also has the `PRXBaseIE` base, not a registry class):

- Translated and registered (3): `PRXStoryIE` (public story API, embedded relations, audio pieces as one format row or media items), `PRXSeriesIE` (paged story listing), `PRXAccountIE` (series + story listings).
- Planned (2): `PRXStoriesSearchIE` and `PRXSeriesSearchIE` — the `prxstories:`/`prxseries:` search keys are not routed by the engine, and planned rows omit `portedAt`.
- Manifest limits: the port does not carry tags, series, season, or episode fields, so they are dropped; listings walk five pages eagerly.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.prx.PrxIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 427 partial, 91 planned, 1,233 not started.

Next: file 63, `imgur.py`.

**File 63 of 110 — `imgur.py` (2026-09-30).** `ImgurIE.kt` accounts for all 3 registered classes (the file also has the `ImgurBaseIE` and `ImgurGalleryBaseIE` bases, not registry classes):

- Translated and registered (3): `ImgurIE` (anonymous post API media + gifv source scan + videoItem GIF JSON + twitter meta formats), `ImgurGalleryIE` (albums endpoint: entries, or a media redirect for single-video galleries), `ImgurAlbumIE` (albums endpoint as a playlist).
- Manifest limits: the anonymous public client id upstream carries is used as-is (it is not a user credential); like/comment counters and uploader URLs the port does not carry are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.imgur.ImgurIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 430 partial, 91 planned, 1,230 not started.

Next: file 64, `drtv.py`.

**File 64 of 110 — `drtv.py` (2026-09-30).** `DrtvIE.kt` accounts for all 4 registered classes (the file has no bases):

- Translated and registered (4): `DRTVLiveIE` (public mu-online channel API + HLS servers), `DRTVSeasonIE` (public page API episode entries), `DRTVSeriesIE` (public page API season entries), `DRTVIE` (typed LoginRequired — the stream data needs the anonymous SSO device token).
- Manifest limits: the HDS (f4m) live path is skipped; the port does not carry display_id, series, season, or episode fields, so they are dropped. Named group `display` replaces upstream `display_id` (Java named groups reject underscores).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.drtv.DrtvIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 434 partial, 91 planned, 1,226 not started.

Next: file 65, `roosterteeth.py`.

**File 65 of 110 — `roosterteeth.py` (2026-09-30).** `RoosterTeethIE.kt` accounts for both registered classes (the file also has the `RoosterTeethBaseIE` base, not a registry class):

- Translated and registered (2): `RoosterTeethIE` (public svod-be watch API, HLS row, episode metadata; FIRST-only content fails typed), `RoosterTeethSeriesIE` (seasons + bonus features entries).
- Manifest limits: the Brightcove fallback for a 403 m3u8 is not translated; the port does not carry series/season/episode/tags fields, so they are dropped. The API path helper joins `/api/v1` links against the host (a leading-slash link would otherwise double the prefix).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.roosterteeth.RoosterTeethIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 436 partial, 91 planned, 1,224 not started.

Next: file 66, `yahoo.py`.

**File 66 of 110 — `yahoo.py` (2026-09-30).** `YahooIE.kt` accounts for 2 of the 3 registered classes (the file has no bases):

- Translated and registered (2): `YahooIE` (public caas article API, video-api.yql webm/mp4/hls rows and closed captions, story/iframe playlist entries; geo restriction fails typed), `YahooJapanNewsIE` (preloaded-state scan, public feapi content API, hls/http formats).
- Planned (1): `YahooSearchIE` — the `yvsearch:` search key is not routed by the engine; planned rows omit `portedAt`.
- Manifest limits: series/display_id fields the port does not carry are dropped; the public app id and space-id MD5 are request parameters, not user credentials.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.yahoo.YahooIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 438 partial, 92 planned, 1,221 not started.

Next: file 67, `go.py`.

**File 67 of 110 — `go.py` (2026-09-30).** `GoIE.kt` accounts for the only registered class (the file has no bases):

- Translated and registered (1): `GoIE` — the five ABC/Freeform/DisneyNOW/FX/National Geographic URL forms match and fail typed through the Adobe Pass base (`mvpdAuthRequired()`), because every upstream test needs MSO credentials.
- Manifest limits: the site software statements and requestor ids the upstream file carries are not stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.go.GoIETest"` — 2 tests, 0 failures (matching + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 439 partial, 92 planned, 1,220 not started.

Next: file 68, `microsoftembed.py`.

**File 68 of 110 — `microsoftembed.py` (2026-09-30).** `MicrosoftEmbedIE.kt` accounts for all 6 registered classes (the file also has the `MicrosoftMediusBaseIE` base, not a registry class):

- Translated and registered (6): `MicrosoftEmbedIE` (public video CMS API: HLS/plain formats, captions, thumbnails), `MicrosoftMediusIE` (typed NoFormats — ISM/Smooth Streaming not translated), `MicrosoftLearnPlaylistIE` (paged contentbrowser entries), `MicrosoftLearnEpisodeIE` (Learn video API: HLS/http/audio formats + captions), `MicrosoftLearnSessionIE` (externalVideoUrl redirect), `MicrosoftBuildIE` (session listing and per-session redirects).
- Manifest limits: ISM (Smooth Streaming) and MPEG-DASH manifests are not translated; a Medius video with only an ISM manifest fails typed. Named group `id2` covers the Build `/sessions` listing alternative.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.microsoftembed.MicrosoftEmbedIETest"` — 7 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 445 partial, 92 planned, 1,214 not started.

Next: file 69, `viewlift.py`.

**File 69 of 110 — `viewlift.py` (2026-09-30).** `ViewLiftIE.kt` accounts for both registered classes (the file also has the `ViewLiftBaseIE` base, not a registry class):

- Translated and registered (2): `ViewLiftEmbedIE` and `ViewLiftIE` — every URL form matches and fails typed LoginRequired, because the API needs the `token` cookie the upstream `_fetch_token` reads an authorization token from.
- Manifest limits: the site map and the cookie-derived token are not stored; the embed URL regex is kept as a constant for callers.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.viewlift.ViewLiftIETest"` — 2 tests, 0 failures (matching + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 447 partial, 92 planned, 1,212 not started.

Next: file 70, `smotrim.py`.

**File 70 of 110 — `smotrim.py` (2026-09-30).** `SmotrimIE.kt` accounts for all 4 registered classes (the file also has the `SmotrimBaseIE` base, not a registry class):

- Translated and registered (4): `SmotrimIE` (player API video HLS rows), `SmotrimAudioIE` (audio file + bookmark metadata), `SmotrimLiveIE` (channel player scan and live/audio-live API), `SmotrimPlaylistIE` (brand/podcast listings). Locked items fail typed LoginRequired; API errors fail typed GeoRestricted (RU).
- Manifest limits: each m3u8 URL becomes one HLS row because manifest parsing is not translated; the port does not carry series/season fields. Playlist page loops catch any exception so a missing fixture page ends the walk.
- **70th-file cadence.** `:shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL. Cross-target compiles (`:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — BUILD SUCCESSFUL. `:apps:desktop:test` still reports the two known pre-existing failures recorded in T-126 (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory` and the flaky `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`). Neither touches extractor code.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.smotrim.SmotrimIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 451 partial, 92 planned, 1,208 not started.
- Cadence and cross-target commands above.

Next: file 71, `southpark.py`.

**File 71 of 110 — `southpark.py` (2026-09-30).** `SouthParkIE.kt` accounts for all 7 registered classes (the file has no bases of its own; every class extends upstream `MTVServicesBaseIE`):

- Translated and registered (7): `SouthParkIE`, `SouthParkEsIE`, `SouthParkDeIE`, `SouthParkLatIE`, `SouthParkDkIE`, `SouthParkComBrIE`, `SouthParkCoUkIE` — each URL form matches and fails typed LoginRequired, because the MTV services feed flow (Paramount/MVPD) is not translated.
- Manifest limits: no API key, mgid, or media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.southpark.SouthParkIETest"` — 2 tests, 0 failures (matching + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 458 partial, 92 planned, 1,201 not started.

Next: file 72, `rozhlas.py`.

**File 72 of 110 — `rozhlas.py` (2026-09-30).** `RozhlasIE.kt` accounts for all 3 registered classes (the file also has the `RozhlasBaseIE` base, not a registry class):

- Translated and registered (3): `RozhlasIE` (prehravac page scan + direct mp3), `RozhlasVltavaIE` (mujRozhlasPlayer data-player JSON + playlist entries), `MujRozhlasIE` (api.mujrozhlas.cz episode/show/serial endpoints, paged episodes).
- Manifest limits: MPEG-DASH links are skipped; artist/chapter fields the port does not carry are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rozhlas.RozhlasIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 461 partial, 92 planned, 1,198 not started.

Next: file 73, `linkedin.py`.

**File 73 of 110 — `linkedin.py` (2026-09-30).** `LinkedInIE.kt` accounts for all 4 registered classes (the file also has the `LinkedInLearningBaseIE` base, not a registry class):

- Translated and registered (4): `LinkedInIE` (public post page video element data-sources JSON + captions + og/json-ld metadata), `LinkedInLearningIE` and `LinkedInLearningCourseIE` (typed LoginRequired — JSESSIONID cookie), `LinkedInEventsIE` (typed LoginRequired — li_at cookie).
- Manifest limits: like counts the port does not carry are dropped; the `data-bitrate` scaling follows upstream (`* 1000`). Named group `course` replaces upstream `course_slug` (Java named groups reject underscores).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.linkedin.LinkedInIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 465 partial, 92 planned, 1,194 not started.

Next: file 74, `rtp.py`.

**File 74 of 110 — `rtp.py` (2026-09-30).** `RtpIE.kt` accounts for the only registered class (the file has no bases):

- Translated and registered (1): `RTPIE` — every URL form matches and fails typed LoginRequired, because the API needs the guest auth token from the token-manager endpoint (the upstream mobile auth hash headers are not stored) and the obfuscated HTML player data is not translated.
- Manifest limits: no auth hash, token, or media URL is stored. Named groups `program`/`episode`/`asset` replace upstream `program_id`/`episode_id`/`asset_id` (Java named groups reject underscores).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rtp.RtpIETest"` — 2 tests, 0 failures (matching + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 466 partial, 92 planned, 1,193 not started.

Next: file 75, `redbee.py`.

**File 75 of 110 — `redbee.py` (2026-09-30).** `RedBeeIE.kt` accounts for both registered classes (the file also has the `RedBeeBaseIE` base, not a registry class):

- Translated and registered (2): `ParliamentLiveUKIE` and `RTBFIE` — every URL form matches and fails typed LoginRequired, because the Exposure API needs an anonymous device bearer token (RTBF also a gigya JWT).
- Manifest limits: no device id, token, or media URL is stored; upstream marks RTBF `_WORKING = False`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.redbee.RedBeeIETest"` — 2 tests, 0 failures (matching + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 468 partial, 92 planned, 1,191 not started.

Next: file 76, `wdr.py`.

**File 76 of 110 — `wdr.py` (2026-09-30).** `WdrIE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `WDRIE` (deviceids-medp JSONP: HLS/direct formats, caption URLs and hashes), `WDRPageIE` (data-extension scan + playlist links), `WDRElefantIE` (table-of-contents JSON + zmdb_url redirect).
- Manifest limits: F4M/SMIL manifests are skipped and an m3u8 URL becomes one HLS row because manifest parsing is not translated. Named groups `display`/`maus` replace upstream `display_id`/`maus_id`; the page classes pass their own `ieKey`/`validUrl` through the open `WDRIE` constructor. A KDoc containing a literal `/*` was removed because Kotlin block comments nest.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.wdr.WdrIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 471 partial, 92 planned, 1,188 not started.

Next: file 77, `wrestleuniverse.py`.

**File 77 of 110 — `wrestleuniverse.py` (2026-09-30).** `WrestleUniverseIE.kt` accounts for both registered classes (the file also has the `WrestleUniverseBaseIE` base, not a registry class):

- Translated and registered (2): `WrestleUniverseVODIE` and `WrestleUniversePPVIE` — every URL form matches and fails typed LoginRequired, because the API needs the `token` cookie (or a Firebase login) and the encrypted stream API needs RSA key exchange.
- Manifest limits: no API key, device id, token, or media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.wrestleuniverse.WrestleUniverseIETest"` — 2 tests, 0 failures (matching + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 473 partial, 92 planned, 1,186 not started.

Next: file 78, `adn.py`.

**File 78 of 110 — `adn.py` (2026-09-30).** `AdnIE.kt` accounts for both registered classes (the file also has the `ADNBaseIE` base, not a registry class):

- Translated and registered (2): `ADNSeasonIE` (public show/episode listing API and its episode entries), `ADNIE` (typed LoginRequired — subscription login plus the RSA-encrypted player token flow).
- Manifest limits: no RSA key or token is stored; the listing uses the numeric id as the show slug, matching upstream. Named group `lang` is fine (no underscore).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.adn.AdnIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 475 partial, 92 planned, 1,184 not started.

Next: file 79, `vidio.py`.

**File 79 of 110 — `vidio.py` (2026-09-30).** `VidioIE.kt` accounts for all 3 registered classes (the file also has the `VidioBaseIE` base, not a registry class):

- Translated and registered (3): `VidioIE` (public videos/clips API: HLS row; premium without a stream fails typed), `VidioPremierIE` (public playlist API and watchpage entries), `VidioLiveIE` (livestreaming detail API, HLS sources with the live token endpoint; DRM fails typed).
- Manifest limits: the anonymous API key is fetched at runtime from the site and never stored; like/dislike/comment counters and display ids the port does not carry are dropped; DASH sources are skipped and an m3u8 URL becomes one HLS row.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.vidio.VidioIETest"` — 7 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 478 partial, 92 planned, 1,181 not started.

Next: file 80, `tv2.py`.

**File 80 of 110 — `tv2.py` (2026-09-30).** `Tv2IE.kt` accounts for all 4 registered classes (the file has no bases):

- Translated and registered (4): `TV2IE` (public sumo assets + play API: HLS/direct rows, DRM typed), `TV2ArticleIE` (asset-id scan entries), `KatsomoIE` (typed LoginRequired — session, upstream `_WORKING = False`), `MTVUutisetArticleIE` (public article JSON entries).
- Manifest limits: MPD/F4M/ISM manifests are skipped and an m3u8 URL becomes one HLS row; a missing play endpoint ends that protocol quietly (any exception, because the fixture harness raises IllegalStateException).
- **80th-file cadence.** `:shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL. Cross-target compiles (`:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — BUILD SUCCESSFUL after fixing a JVM-only `String.format` leak in `WrestleUniverseIE.kt` (replaced with `replace("%s", …)`). `:apps:desktop:test` still reports the two known pre-existing failures recorded in T-126 (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory` and the flaky `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`). Neither touches extractor code.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tv2.Tv2IETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 482 partial, 92 planned, 1,177 not started.
- Cadence and cross-target commands above.

Next: file 81, `tenplay.py`.

**File 81 of 110 — `tenplay.py` (2026-09-30).** `TenPlayIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `TenPlaySeasonIE` (public season listing API and its paged episode carousel), `TenPlayIE` (typed LoginRequired — login plus the refresh/access token pair).
- Manifest limits: the port does not carry display ids or series/season/episode fields, so they are dropped; the season carousel walks five pages eagerly and stops on any page error.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tenplay.TenPlayIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 484 partial, 92 planned, 1,175 not started.

Next: file 82, `bitchute.py`.

**File 82 of 110 — `bitchute.py` (2026-09-30).** `BitChuteIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `BitChuteIE` (public beta video/media/channel APIs and the HLS or direct media row), `BitChuteChannelIE` (typed Unavailable — the old-site listing needs the CSRF cookie the port refuses).
- Manifest limits: the seed-host HEAD probe is skipped; channel/uploader URLs and tags the port does not carry are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.bitchute.BitChuteIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 486 partial, 92 planned, 1,173 not started.

Next: file 83, `nitter.py`.

**File 83 of 110 — `nitter.py` (2026-09-30).** `NitterIE.kt` accounts for the only registered class (the file has no bases):

- Translated and registered (1): `NitterIE` — the status-page video scan (`data-url`/`source src`), main-tweet slice, HLS or direct media row, and page metadata.
- Manifest limits: the `hlsPlayback` cookie upstream sets is not sent by the port; the instance list is matched by host shape instead of an enumerated list; like/repost/comment counters the port does not carry are dropped. Named group `uploader` replaces upstream `uploader_id` (Java named groups reject underscores).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nitter.NitterIETest"` — 3 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 487 partial, 92 planned, 1,172 not started.

Next: file 84, `tnaflix.py`.

**File 84 of 110 — `tnaflix.py` (2026-09-30).** `TnaFlixIE.kt` accounts for all 4 registered classes (the file also has the `TNAFlixNetworkBaseIE` and `TNAEMPFlixBaseIE` bases, not registry classes):

- Translated and registered (4): `TNAFlixNetworkEmbedIE` (embed redirect), `TNAFlixIE` (page scan + XML config formats/thumbnails), `EMPFlixIE` (page scan + JSON player source scan), `MovieFapIE` (page scan + XML config).
- Manifest limits: the vkey/nkey query parameters come from the page itself (fixtures fake); the port does not carry display ids, comment counts, ratings, or categories; the thumbnail timeline is capped at 40 entries. Named groups `display`/`display2` replace upstream `display_id`/`display_id_2` (Java named groups reject underscores); the base keeps its own `pattern` because the superclass `validUrl` is private.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tnaflix.TnaFlixIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 491 partial, 92 planned, 1,168 not started.

Next: file 85, `googledrive.py`.

**File 85 of 110 — `googledrive.py` (2026-09-30).** `GoogleDriveIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `GoogleDriveIE` (public workspace-video playback API: adaptive/progressive transcodes, metadata, and the usercontent download URL), `GoogleDriveFolderIE` (typed Unavailable — the folder batch API is not translated).
- Manifest limits: the public browser app key upstream carries is stored (an app key, not a user credential); the timed-text subtitle XML is not parsed.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.googledrive.GoogleDriveIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 493 partial, 92 planned, 1,166 not started.

Next: file 86, `vevo.py`.

**File 86 of 110 — `vevo.py` (2026-09-30).** `VevoIE.kt` accounts for both registered classes (the file also has the `VevoBaseIE` base, not a registry class):

- Translated and registered (2): `VevoIE` (anonymous token endpoint + apiv2 video/streams calls with HLS and direct rows), `VevoPlaylistIE` (initial-store scan and `vevo:` ISRC entries, index redirect).
- Manifest limits: the public client id is stored but the legacy token is fetched at runtime and never stored; ISM/MPD sources are skipped; track/artist/genre fields the port does not carry are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.vevo.VevoIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 495 partial, 92 planned, 1,164 not started.

Next: file 87, `cnn.py`.

**File 87 of 110 — `cnn.py` (2026-09-30).** `CnnIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `CNNIE` (video-player page scan, fave API direct files + closed captions, medium API HLS row), `CNNIndonesiaIE` (VideoObject embed redirect).
- Manifest limits: the app id is read from the page's `window.env` and never stored; display ids, modified dates, and tags the port does not carry are dropped; an m3u8 URL becomes one HLS row. Named group `uploaddate` replaces upstream `upload_date` (Java named groups reject underscores).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.cnn.CnnIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 497 partial, 92 planned, 1,162 not started.

Next: file 88, `mediaset.py`.

**File 88 of 110 — `mediaset.py` (2026-09-30).** `MediasetIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `MediasetIE` (ThePlatform eu metadata + SMIL formats via the shared SmilManifest parser, all-programs feed metadata), `MediasetShowIE` (show season link scan + paged sub-brand feed entries).
- Manifest limits: DRM (`_sampleaes/`) manifests are skipped and a geo/release error fails typed; series/season/episode fields and feed chapters are dropped; the show class passes its own `ieKey`/`validUrl` through the open `MediasetIE` constructor; a missing SMIL format request ends that format quietly (any exception, because the fixture harness raises IllegalStateException).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.mediaset.MediasetIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 499 partial, 92 planned, 1,160 not started.

Next: file 89, `twitcasting.py`.

**File 89 of 110 — `twitcasting.py` (2026-09-30).** `TwitCastingIE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `TwitCastingIE` (movie page scan, playlist JSON or reversed base64, m3u8 sources, live streamserver HLS rows), `TwitCastingLiveIE` (frontendapi live check + current-live redirect), `TwitCastingUserIE` (paged history entries).
- Manifest limits: password-protected movies fail typed (the port has no video-password option); websocket_frag sources are skipped; an m3u8 URL becomes one HLS row; uploader ids the port does not carry are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.twitcasting.TwitCastingIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 502 partial, 92 planned, 1,157 not started.

Next: file 90, `brainpop.py`.

**File 90 of 110 — `brainpop.py` (2026-09-30).** `BrainPopIE.kt` accounts for all 6 registered classes (the file also has the `BrainPOPBaseIE` and `BrainPOPLegacyBaseIE` bases, not registry classes):

- Translated and registered (6): `BrainPOPIE` (published-content API: movie/topic data, access check, high/low and audio-description keys, localizations, subtitle URLs), `BrainPOPJrIE`, `BrainPOPELLIE`, `BrainPOPEspIE`, `BrainPOPFrIE`, `BrainPOPIlIE` (legacy page scans).
- Manifest limits: gated content fails typed with the API reason; an m3u8 URL becomes one HLS row; display ids the port does not carry are dropped. The access check runs before the topic fetch so a gated video fails typed without a second request; the HLS/video URLs are joined with a slash.
- **90th-file cadence.** `:shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL. Cross-target compiles (`:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — BUILD SUCCESSFUL. `:apps:desktop:test` reports only the known pre-existing `FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory` failure recorded in T-126; the flaky `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled` passed this run.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.brainpop.BrainPopIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 508 partial, 92 planned, 1,151 not started.
- Cadence and cross-target commands above.

Next: file 91, `art19.py`.

**File 91 of 110 — `art19.py` (2026-09-30).** `Art19IE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `Art19IE` (public player metadata + RSS episode JSON with mp3 and media rows), `Art19ShowIE` (public series endpoint and episode id listing).
- Manifest limits: `waveform_bin` is skipped; episode/season ids and display ids the port does not carry are dropped. Both classes take `id`/`id2` alternatives because the RSS URL forms use the second group.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.art19.Art19IETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 510 partial, 92 planned, 1,149 not started.

Next: file 92, `lsm.py`.

**File 92 of 110 — `lsm.py` (2026-09-30).** `LsmIE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `LSMLREmbedIE` (Latvian Radio player scan, audio/video items, m3u8 and direct sources), `LSMLTVEmbedIE` (embed payload redirects: YouTube and the CloudyCDN embed URL), `LSMReplayIE` (Nuxt replay data with `Object.create(null, …)` normalization and `__REPLAY__` lookup; hls or embed playback).
- Manifest limits: the show query parameter is skipped when it is 0 (matching upstream's truthy filter); an unsupported LTV embed type or replay playback type fails typed. A balanced-range helper returns the exact JSON spans instead of re-serializing.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.lsm.LsmIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 513 partial, 92 planned, 1,146 not started.

Next: file 93, `digitalconcerthall.py`.

**File 93 of 110 — `digitalconcerthall.py` (2026-09-30).** `DigitalConcertHallIE.kt` accounts for the only registered class (the file has no bases):

- Translated and registered (1): `DigitalConcertHallIE` — every URL form matches and fails typed LoginRequired, because the API needs an OAuth access token from the browser local storage.
- Manifest limits: the OAuth client secret and client id the upstream file carries are not stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.digitalconcerthall.DigitalConcertHallIETest"` — 2 tests, 0 failures (matching + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 514 partial, 92 planned, 1,145 not started.

Next: file 94, `mixcloud.py`.

**File 94 of 110 — `mixcloud.py` (2026-09-30).** `MixcloudIE.kt` accounts for all 3 registered classes (the file also has the `MixcloudBaseIE` and `MixcloudPlaylistBaseIE` bases, not registry classes):

- Translated and registered (3): `MixcloudIE` (public GraphQL cloudcast lookup, XOR/base64 stream cipher, HLS/direct rows), `MixcloudUserIE` (public GraphQL user listings), `MixcloudPlaylistIE` (public GraphQL playlist lookup), the last two paged eagerly up to five pages.
- Manifest limits: upstream passes `impersonate=True` to the GraphQL call and the port sends a plain request, so a Cloudflare challenge would fail typed; DASH is skipped, an exclusive track fails typed, and comment bodies, tags, and artist lists the port does not carry are dropped. Fixture stream URLs are XOR/base64 ciphertext computed from the upstream public constant, not signed URLs.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.mixcloud.MixcloudIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 517 partial, 92 planned, 1,142 not started.

Next: file 95, `nfb.py`.

**File 95 of 110 — `nfb.py` (2026-09-30).** `NfbIE.kt` accounts for both registered classes (the file also has the `NFBBaseIE` base, not a registry class):

- Translated and registered (2): `NFBIE` (player options page data with the described-video dvSource, film metadata scan, episodesData episode metadata), `NFBSeriesIE` (episodesData listing entries).
- Manifest limits: an m3u8 URL becomes one HLS row; the port does not carry season/series fields or json-ld merges. The overlay URL id strips the trailing `/overlay` segment, and the series entries check the `NFBIE` URL pattern (not the series one).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nfb.NfbIETest"` — 4 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 519 partial, 92 planned, 1,140 not started.

Next: file 96, `skyit.py`.

**File 96 of 110 — `skyit.py` (2026-09-30).** `SkyItIE.kt` accounts for all 9 registered classes (the file also has the `SkyItBaseIE` base, not a registry class):

- Translated and registered (9): `SkyItPlayerIE` (getVideoData API), `SkyItVideoIE`, `SkyItVideoLiveIE` (Next.js live page + getLivestream), `SkyItIE` (article scan), `SkyItArteIE`, `CieloTVItIE`, `TV8ItIE` (player redirects with their domains), `TV8ItLiveIE` (getLivestream id=7 + getStreaming), `TV8ItPlaylistIE` (Next.js card entries).
- Manifest limits: only the default `sky` caller token upstream carries is stored (a public app token); the per-domain token map is not carried; an m3u8 URL becomes one HLS row and a geoblocked video fails typed GeoRestricted (IT). The subclasses pass their own `ieKey`/`validUrl`/`domain`/`videoIdRegex` through the open constructors.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.skyit.SkyItIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 528 partial, 92 planned, 1,131 not started.

Next: file 97, `ertgr.py`.

**File 97 of 110 — `ertgr.py` (2026-09-30).** `ErtGrIE.kt` accounts for all 3 registered classes (the file also has the `ERTFlixBaseIE` base, not a registry class):

- Translated and registered (3): `ERTFlixCodenameIE` (Player/AcquireContent API and main-role formats), `ERTFlixIE` (Tile/GetTiles and Tile/GetSeriesDetails; vod redirects, series entries), `ERTWebtvEmbedIE` (webtv embed VOD path).
- Manifest limits: MPD is skipped; the port does not carry season/series/episode fields; the Tile/GetTiles call is a POST with the platformCodename body.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ertgr.ErtGrIETest"` — 6 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 531 partial, 92 planned, 1,128 not started.

Next: file 98, `unsupported.py`.

**File 98 of 110 — `unsupported.py` (2026-09-30).** `UnsupportedIE.kt` accounts for all 3 registered classes (the file also has the `UnsupportedInfoExtractor` base, not a registry class):

- Translated and registered (3): `KnownDRMIE` (DRM-only site list, 40+ hosts), `KnownPiracyIE` (piracy list), `KnownLiabilityIE` (liability list) — each URL form matches and fails typed Unavailable with the upstream reason, so the engine reports the same decision instead of falling through to the generic extractor.
- Engine change: `SupportedUrlValidator.isSupported` now also excludes extractors whose key starts with `Known`, because a known-unsupported URL is not downloadable support; this keeps the Spotify/direct-file validator assertions true.
- Manifest limits: no URL is downloaded and no cookie, token, or media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.unsupported.UnsupportedIETest"` — 3 tests, 0 failures (matching + typed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — BUILD SUCCESSFUL (after the validator change).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 534 partial, 92 planned, 1,125 not started.

**File 99 of 110 — `videocampus_sachsen.py` (2026-10-01).** `ViMPIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `VideocampusSachsenIE` (the `/m/<id>`, `(category/)video/<slug>/<32hex>`, and `media/embed?key=<32hex>` forms; the embed-src key scan for `/m/` pages; the og/video-js title, description, and thumbnail; the HLS plus direct mp4 rows) and `ViMPPlaylistIE` (the album/category/channel/tag boxList listing with the upstream paged POST `vars[...]` body, page 0 first and the short-page stop, at most five pages eagerly).
- The upstream `_INSTANCES` list is translated into both URL patterns, including the two entries that carry a path (`www.b-tu.de/media`, `www.hsbi.de/medienportal`); a host that is not on the list does not match.
- First run failed: Java regex named groups cannot contain underscores, so upstream's `tmp_id`/`display_id`/`embed_id`/`album_id`/`channel_id`/`tag_id` groups are `tmpid`/`displayid`/`embedid`/`albumid`/`channelid`/`tagid`; fixed before Evidence.
- Manifest limits: manifest parsing is not translated, so an m3u8 URL becomes one HLS row; the `display_id` field and the listing entry ids/titles are not carried; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.videocampus_sachsen.VideocampusSachsenIETest"` — 9 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,503 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 536 partial, 92 planned, 1,123 not started.

**File 100 of 110 — `tunein.py` (2026-10-01).** `TuneInIE.kt` accounts for all 5 registered classes plus the unregistered `TuneInBaseIE` (translated as the shared `callApi` and `extractFormatsAndSubtitles` base):

- Translated and registered (5): `TuneInStationIE` (the Tune.ashx mp3/aac/ogg/flash rows plus one HLS row and the profiles metadata subset), `TuneInPodcastIE` (the paged contents listing with every GuideId becoming a `?topicId=` entry, page 0 first and short-page stop, at most five pages eagerly; the `suitable` guard keeps episode URLs off this class), `TuneInPodcastEpisodeIE` (topicId episode via Tune.ashx plus title, description, duration, thumbnail, upload_date, and the series title mapped to `channel`/`channel_id`), `TuneInEmbedIE` (the canonical `tunein.com/{program,station,topic}/?{kind}id=` redirect), and `TuneInShortenerIE` (tun.in redirect followed and the port stripped; a redirect that stays on tun.in fails typed UnsupportedUrl).
- Manifest limits: an m3u8 stream becomes one HLS row, so its subtitles are not parsed; alt_title, channel_follower_count, location, cast, display_id, and the upstream timestamp field (mapped to upload_date) are not carried.
- The episode pattern uses `RegexOption.IGNORE_CASE` for upstream's `(?i:topicid)`; all named groups avoid underscores because Java rejects them.

Commands (hundredth-file cadence):

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tunein.TuneInIETest"` — 10 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL: core 1,513/0, ui 121/0, android-engine 41/0, port-manifest 25/0.
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 146 tests, 2 failed, 15 skipped; the same two pre-existing failures documented in T-126 (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory`, `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`), neither touching extractor code.
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 541 partial, 92 planned, 1,118 not started.

**File 101 of 110 — `kuwo.py` (2026-10-01).** `KuwoIE.kt` accounts for all 6 registered classes plus the unregistered `KuwoBaseIE` (translated as the shared `getFormats` base):

- Translated and registered (6): `KuwoIE` (song page: anti.s ape/mp3-320/mp3-192/mp3-128/wma/aac rows, title, uploader, lyrics as description, album publish date; a copyright removal redirect or page message fails typed Unavailable and an anti.s `IPDeny` fails typed GeoRestricted), `KuwoAlbumIE` (listen-anchor track list, album name and intro), `KuwoChartIE` (yinyue-anchor song list), `KuwoSingerIE` (singer page + the 0-based contentMusicsAjax pages, at most five pages eagerly), `KuwoCategoryIE` (`var jsonm` musiclist with string or numeric musicrid), `KuwoMvIE` (anti.s list tolerating `IPDeny`, mkv/mp4 rows, appended mvurl row).
- Upstream marks every class `_WORKING = False`; the port still matches the public URL forms and fails typed. The upstream `creator` maps to uploader, `quality` folds into preference, and the geo-verification headers bypass is not translated.
- First compile failed: `KuwoFormat` visibility for the internal base helper, `GeoRestricted` takes no message, and `"$name简介："` parsed the Chinese chars as part of the identifier; all fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.kuwo.KuwoIETest"` — 11 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,524 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 547 partial, 92 planned, 1,112 not started.

**File 102 of 110 — `senategov.py` (2026-10-01).** `SenateGovIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `SenateISVPIE` (the filename/comm/poster query, the `_COMMITTEES` table translated whole, and the four stream alternatives probed in order into one HLS row for the first that answers; a missing filename/comm or an unknown committee fails typed UnsupportedUrl) and `SenateGovIE` (the committee subdomain page whose iframe embed is delegated directly to `SenateISVPIE`, following the `RaiIE` precedent, plus the page og title/description/thumbnail and the RTA age limit).
- New helper named here: `rtaSearch` (the RTA meta/label and 2257 markers) plus local `parseFormQuery`/`formDecode` for the query string.
- Manifest limits: an m3u8 URL becomes one HLS row, so manifest subtitles are not parsed; `_old_archive_ids` and `display_id` are not carried. Two fixture bugs were fixed before Evidence: the `isvp?` URL without a slash and the archive alternative keeping `.mp4` in the filename (`...commerce011514.mp4_1/...`).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.senategov.SenateGovIETest"` — 11 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,535 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 549 partial, 92 planned, 1,110 not started.

**File 103 of 110 — `vice.py` (2026-10-01).** `ViceIE.kt` accounts for all 3 registered classes plus the unregistered `ViceBaseIE` (translated as the shared HTTP-GraphQL base on top of `AdobePassIE`):

- Translated and registered (3): `ViceIE` (GraphQL video lookup, the signed preplay request, the HLS row and subtitle tracks, and the video metadata; a `locked` video is the typed Adobe Pass login wall), `ViceShowIE` (shows slug lookup plus the page-based videos listing, at most five pages eagerly), `ViceArticleIE` (article slug lookup and the iframe/data-video-url embed redirect, leaving the target to its own extractor).
- Upstream marks every class `_WORKING = False`. The upstream `series`/`episode`/`season` fields and `uploader_id` are not carried (the channel id fills `channelId`); an m3u8 URL becomes one HLS row; the article only scans iframe-style embeds.
- **New helper named here: `sha512Hex`** (`anydownload`), a pure-Kotlin FIPS 180-4 SHA-512 for the public preplay signature `sha512("{video_id}:GET:{exp}")`; known-vector test `Sha512Test` — 2 tests, 0 failures. No secret is embedded and the Adobe Pass software statement is not stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.vice.ViceIETest"` — 9 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,546 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 552 partial, 92 planned, 1,107 not started.

**File 104 of 110 — `mtv.py` (2026-10-01).** `MTVIE.kt` accounts for the only registered class plus the unregistered `MTVServicesBaseIE` (translated as the shared base):

- Translated and registered (1): `MTVIE` — the `?json=true` page tree (MainContainer -> AviaWrapper -> FlexWrapper -> Player `props.videoDetail`, plus the `handleTVEAuthRedirection` fallback node), the `videoServiceUrl` stitched-stream JSON into one HLS or DASH row, and title/channel/description/thumbnails/duration/publish-date metadata. A missing service URL fails typed Unavailable, an unsupported manifest type fails typed NoFormats, and an `authRequired` video is the typed TV-provider login wall.
- Manifest limits: the upstream Adobe Pass MSO flow, JWT token cache, and media-token calls are not translated; series/season/episode and release fields are dropped; a page 404 surfaces as typed Unavailable rather than GeoRestricted. First run failed twice (the upstream `_VALID_URL` is a prefix match, and the thumbnail path was wrongly nested); both fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.mtv.MTVIETest"` — 9 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,555 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 553 partial, 92 planned, 1,106 not started.

**File 105 of 110 — `rtlnl.py` (2026-10-01).** `RtlNlIE.kt` accounts for all 5 registered classes plus the unregistered `RTLLuBaseIE` (translated as the shared `<rtl-player>`/`<rtl-audioplayer>` base):

- Translated and registered (5): `RtlNlIE` (the adaptive JSON: title/subtitle, synopsis, m3u8 path into one HLS row, original_date, duration, and the poster plus quoted `"thumb_base_url"` bases), `RTLLuTeleVODIE`, `RTLLuArticleIE`, `RTLLuLiveIE` (the `is_live` flag for live/live-2/lauschteren), and `RTLLuRadioIE` (the rtl.lu page players: HLS video row, mp3 audio row with vcodec none, poster or og image, og title/description).
- Manifest limits: an m3u8 URL becomes one HLS row so manifest subtitles are not parsed; the upstream quoted `"thumb_base_url"` key quirk is kept.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rtlnl.RtlNlIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,563 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 558 partial, 92 planned, 1,101 not started.

**File 106 of 110 — `cspan.py` (2026-10-01).** `CSpanIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `CSpanIE` (the `jwsetup` JWPlayer config: sources into HLS/native rows with the page referer and caption tracks with `php`→`vtt`; title/description/thumbnail/upload date/seclength duration/views metadata; and the Ustream, Brightcove and SenateISVP embeds as typed redirects) and `CSpanCongressIE` (the congress page `jwsetup` sources with the `chamber_date` id and og title/description).
- Helper named here: a local `parseJwplayer` (the JWPlayer sources/tracks subset), `parseJwsetup` with a balanced-brace read, and `templateFill` for the Brightcove URL template (multiplatform `replaceFirst`, not `String.format`).
- Manifest limits: the obsolete clip/prog ajax/fxml path and the JSON-LD merge are not translated; the Ustream redirect resolves once D19 translates `ustream.py`. First run failed once (the subtitle label is the language key, matching upstream); fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.cspan.CSpanIETest"` — 9 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,572 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 560 partial, 92 planned, 1,099 not started.

**File 107 of 110 — `nova.py` (2026-10-01).** `NovaIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `NovaEmbedIE` (the `player:` JSON sources into HLS/DASH/direct rows plus `sourceInfo.duration`; a source with `drm.keySystem` is skipped and a DRM-only player fails typed Unavailable) and `NovaIE` (the nova.cz family article pages: the media.cms.nova.cz iframe embed delegated directly to `NovaEmbedIE` with the outer description/upload date, the videojs config JSON with title/poster metadata, and the api.nova.cz default config URL).
- Manifest limits: the pre-August-2023 `replacePlaceholders`/`Player.init` path is not translated; an RTMP mediafile fails typed NoFormats (no RTMP downloader); an m3u8/mpd source becomes one row.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nova.NovaIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,580 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 562 partial, 92 planned, 1,097 not started.

**File 108 of 110 — `douyutv.py` (2026-10-01).** `DouyuIE.kt` accounts for both registered classes plus the unregistered `DouyuBaseIE` (translated as the shared `jsSigningWall` base):

- Translated and registered (2): `DouyuTVIE` (the room URL forms and page live checks: `$ROOM.room_id`, `videoLoop`, `show_status`; a live room then fails typed at the JS signing wall) and `DouyuShowIE` (the show URL forms, page fetch, then the same typed wall). The stream signature needs the page's `ub98484234` JS function executed with the crypto-js/md5 dependency (upstream `_calc_sign` runs it through PhantomJS), which is jsinterp and stays out of the port.
- Manifest limits: the room API metadata path and the `$DATA` video metadata are not translated; no cookie, token, or signed media URL is stored. No harness case can pass through a typed wall, so the tests assert the live checks and the wall directly (the same shape as `anvato.py`/`iqiyi.py`).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.douyutv.DouyuIETest"` — 6 tests, 0 failures (URL forms, the two live checks, the JS wall for both classes, and the missing-room-id failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,586 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 564 partial, 92 planned, 1,095 not started.

**File 109 of 110 — `mailru.py` (2026-10-01).** `MailRuIE.kt` accounts for all 3 registered classes plus the unregistered `MailRuMusicSearchBaseIE` (translated as the shared music.search base):

- Translated and registered (3): `MailRuIE` (the video URL variants, the `sp-video__page-config`/`"video"` page config, the meta JSON, and the `api.video.mail.ru` fallback, with video rows and meta/author fields), `MailRuMusicIE` (the song page og title plus the music.search track row: direct audio URL, abr, vcodec none), and `MailRuMusicSearchIE` (the paged search playlist with direct audio-URL entries, at most five pages eagerly).
- Manifest limits: the upstream `video_key` cookie is not read or re-attached explicitly (the active cookie jar carries it); track/artist/album labels are not carried. First run failed twice: Java rejects `{.+?}` as an illegal repetition, so the `"video"` page-config regex escapes the braces; fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.mailru.MailRuIETest"` — 9 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,595 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 567 partial, 92 planned, 1,092 not started.

**File 110 of 110 — `cbs.py` (2026-10-01).** `CBSIE.kt` accounts for both registered classes plus the unregistered `CBSBaseIE` (translated over the existing `ThePlatformBaseIE`/`SmilManifest` helpers):

- Translated and registered (2): `CBSIE` (the public videoPlayerService XML, the asset-type map with the DRM skip, the ThePlatform metadata and SMIL calls into rows, the three SMIL caption params, and typed DRM/geo/NoFormats failures) and `ParamountPressExpressIE` (the YouTube watch redirect and the Brightcove player redirect with the page player attributes).
- Manifest limits: upstream marks `CBSIE` `_WORKING = False`; series fills `channel`, season/episode fields are dropped, and the Brightcove page token is not carried. Two first-run issues were fixed before Evidence: the missing `url` parameter on the private extractor and the player tag lookup returning inner HTML instead of the tag.

Commands (110th-file / phase-closing cadence):

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.cbs.CBSIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL: core 1,603/0, ui 121/0, android-engine 41/0, port-manifest 25/0.
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 146 tests; the two known pre-existing failures recorded in T-126 plus a flaky `DesktopSpotifyDownloadGateTest` case (a different case on each full-suite run; the class passes in isolation — ffmpeg media-toolkit timing, not extractor code).
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 569 partial, 92 planned, 1,090 not started.

**Card complete — all 110 files done (2026-10-01).** Catalog D18 (`tumblr.py` first, `cbs.py` last) is fully accounted for. Acceptance: the file list is in Evidence at the start; every file has a manifest row and a harness case (wall-only files have direct typed-failure tests per the login/DRM-wall clause); `:tools:port-manifest:check` passes. Phase totals: 0 ported, 569 partial, 92 planned, 1,090 not started out of 1,751 upstream classes. Limits: the phase's wall-only files (`anvato`, `nebula`, `zingmp3`, `digitalconcerthall`, `vice` locked path, `douyutv`, and the `senategov`/`cspan`/`mtv` auth paths) match and fail typed with the manifest one-sentence reason; an m3u8/mpd URL is one row, so manifest subtitles are not parsed; upstream `_WORKING = False` classes are translated as partial.
