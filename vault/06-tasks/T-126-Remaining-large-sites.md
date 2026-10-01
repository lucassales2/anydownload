---
id: T-126
type: task
priority: P1
milestone: D17
tags: [task, engine, extractors]
---

# T-126 — Remaining large sites

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 17](../00-project/Phase-17-Remaining-large-sites.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

The other 23 extractor files in the 25–80 KB band are accounted for. Estimate 46 engineer-days.

## Dependencies

- [T-125](T-125-Priority-sites.md).

## Acceptance criteria

- [x] The file list is written into Evidence at the start, from the pin, excluding files already closed in D16.
- [x] Each file has harness cases and a manifest row. A login wall or DRM wall is Partial with that reason. (The wall-only files — `weverse.py`, `zattoo.py`, `neteasemusic.py`, `vrt.py` — have URL-matching and typed-failure tests instead of passable harness cases, and every wall row states the reason in one sentence.)
- [x] `:tools:port-manifest:check` passes.

## Evidence / notes

Not started. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).

**File 1 of 23 — `adobepass.py` (2026-09-30).** Catalog D17 — T-126, 23 large files, first `adobepass.py`, last `gamejolt.py`, read from `yt_dlp/extractor/` at pin `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`), excluding D16’s nine.

The file declares one class, `AdobePassIE` (upstream line 1365): the Adobe Pass TV Everywhere base class, with no `_VALID_URL`. It is not in the upstream registered-class list, so its manifest row is `kind: core` and it is deliberately not in `productionExtractorRegistry` — there is no URL form to dispatch, and it stays the base for the later TV Everywhere site extractors in D17/D18 (for example `nbc.py` and `brightcove.py`, whose harness cases cover their own forms).

- `AdobePassIE.kt` translates `_get_mvpd_resource` (the XML-escaped v-chip RSS resource string, including ElementTree’s self-closed empty rating) and the `_extract_mvpd_auth` boundary as the typed `ExtractionError.LoginRequired` login wall. The MSO table, device registration, SAML/OAuth authorize flow, token cache, and credential submission are not translated; no credential, cookie, or token is stored or requested. Manifest row `AdobePassIE` is `partial` with the login-wall reason in its scope.
- `AdobePassIETest` (4 cases, all passing) covers the resource string escaping, the empty-rating self-close, the typed wall, and the absence of a base URL form, using `*.example` hosts and fake values only. There is no URL-form harness case because the base has no `_VALID_URL`; the plan’s harness clause applies to translated URL forms.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.adobepass.AdobePassIETest"` — 4 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — BUILD SUCCESSFUL; coverage unchanged at 0 ported, 35 partial, 80 planned, 1,636 not started of 1,751.

Next: file 2, `dplay.py`.

**File 2 of 23 — `dplay.py` (2026-09-30).** `DPlayIE.kt` translates all 20 registered classes plus the three unregistered bases, read from the pin:

- `DPlayBaseIE` carries the shared disco-api path: the anonymous device-token mint (`token?realm=&deviceId=`), `content/videos/<id>` metadata, the v1 `playback/videoPlaybackInfo/<id>` object map and the `DiscoveryPlusBaseIE` v3 `playback/v3/videoPlaybackInfo` request, the `included` channel/image/show/tag walk, and HLS/DASH/progressive format mapping. Fifteen product classes extend `DiscoveryPlusBaseIE`; `DiscoveryPlusIE`, `DiscoveryNetworksDeIE`, and `DPlayIE` extend the base directly; the two show classes extend `DiscoveryPlusShowBaseIE`.
- URL forms: `dplay.<dk|fi|jp|se|no>`, `discoveryplus.<dk|es|fi|it|se|no>`, and `es|it.dplay.com` (`DPlayIE`); the thirteen US/DE product domains; `discoveryplus.com[/<country>]/video` with the upstream `(?!it/)` split; `discoveryplus.in` video and show; `tlc.de`/`dmax.de`; `discoveryplus.com/it/video` and `discoveryplus.it/programmi`.
- All 20 classes are in `productionExtractorRegistry` and in the Metro `SharedEngineBindings`, in the `dplay.py` class order with `DiscoveryPlusIE` before Italy. Tests cover every URL form, the v1 and v3 extractions, the country host split, the loma-cms `uid` video id, and both season listings.
- Limits recorded in the manifest scopes: `series`, `season_number`, `episode_number`, `creator`, `tags`, `categories`, and `display_id` are not modeled on the port’s InfoDict (the creator name fills `channel`); geo-block and missing-package error bodies cannot be read through the typed HTTP seam, so those responses fail typed as unavailable; subtitle tracks inside the HLS/DASH manifests are read at download time, not extract time; the `de-api.loma-cms.com` meta call is best-effort, mirroring upstream `fatal=False`.
- Helper added and named: the fixed public `x-disco-client` and `x-disco-params` client headers joined the request-header allowlist in `HttpRequest.kt`, with an `HttpRequestTest` assertion. They carry no credential; no cookie, token, or signed URL is stored. No other D10 helper was missing.
- Manifest rows: the 20 registered classes are `extractor`/`partial`; `DPlayBaseIE`, `DiscoveryPlusBaseIE`, and `DiscoveryPlusShowBaseIE` are `core`/`partial`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest` — 851 tests, 0 failures (`DPlayIETest`: 9 tests, including the two harness cases).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 55 partial, 80 planned, 1,616 not started.

Next: file 3, `nbc.py`.

**File 3 of 23 — `nbc.py` (2026-09-30).** `NbcIE.kt` plus the small shared `ThePlatformBaseIE.kt` helper account for all 11 classes in the file, read from the pin:

- Helper added and named: `anydownload` translates the metadata and SMIL subset of `theplatform.py` (`_download_theplatform_metadata`, `_parse_theplatform_metadata`, and a SMIL reader) that `nbc.py` subclasses call. The full `ThePlatformIE`, F4M, `hdnea` cookie rewrite, geo headers, and hmac feed signing stay for D18 `theplatform.py`; a `core`/`partial` row records it.
- Translated and registered (6): `NBCIE` (friendship GraphQL plus the PRELOAD fallback; a `locked` video fails typed as a TV-provider login wall), `NBCNewsIE` (Next.js `videoAssets`), `NBCOlympicsIE` (hands the embed URL to the ThePlatform player; the player lands in D18), `NBCStationsIE` (`data-videos`/`data-meta` plus the SMIL video list), `BravoTVIE`, and `SyfyIE` (both through `NBCUniversalBaseIE._extract_nbcu_video`, whose TVE deck and LS-playlist branches are translated; an `auth` entitlement fails typed). `NBCUniversalBaseIE` is a `core`/`partial` row.
- Planned (4) with the one-sentence reason: `NBCSportsVPlayerIE`, `NBCSportsIE`, `NBCSportsStreamIE`, and `NBCOlympicsStreamIE` are upstream `_WORKING = False`; the two streams also need Adobe Pass credentials (login wall).
- Limits in the manifest scopes: info-dict fields the port does not model (`media_type`, `categories`, `episode`, season/episode numbers, `series`, `creators`, `tags`, `location`, `_old_archive_ids`) are dropped; manifests are parsed at download time; the stations live HEAD check is not performed. No Adobe Pass software statement, cookie, token, or signed URL is stored.
- Manifest rows: 12 added (7 `core`/`partial` or `extractor`/`partial`, 4 `extractor`/`planned`, and the ThePlatform base).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nbc.NbcIETest" --tests "com.anydownlod.core.extract.theplatform.ThePlatformBaseIETest"` — `NbcIETest` 13 tests, `ThePlatformBaseIETest` 4 tests, 0 failures.
- `./gradlew --no-parallel :shared:core:jvmTest` — 868 tests, 0 failures (111 classes).
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL (shared registry/DI change compiles for the desktop host).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 61 partial, 84 planned, 1,606 not started.

Next: file 4, `cbc.py`.

**File 4 of 23 — `cbc.py` (2026-09-30).** `CbcIE.kt` translates nine registered classes plus the `CBCGemBaseIE` base, read from the pin:

- Translated and registered (9): `CBCIE` (initial-state and player-init media ids become `/player/play/<id>` child entries), `CBCPlayerIE` (currentClip assets, text tracks, chapters; the deprecated media-id-only path fails typed), `CBCPlayerPlaylistIE` (`clipsByCategory`), `CBCGemIE`, `CBCGemPlaylistIE`, `CBCGemContentIE`, `CBCGemOlympicsIE`, `CBCGemLiveIE`, and `CBCListenIE`. `CBCGemBaseIE` is a `core`/`partial` row.
- Helper added and named: `ExtractorUtils.parseAgeLimit` (`parse_age_limit`), moved from a private ThePlatform helper to the shared utils because `cbc.py` calls it too; the `_utils.py` manifest scope names it.
- Limits in the manifest scopes: the Gem OAuth login/refresh and claims-token flow is not translated, so account-only videos fail typed through the validation error codes (geo, password login, unavailable); HLS masters are recorded `m3u8_native` and parsed at download time, so per-variant descriptive-audio preference and the direct https-mp4 HEAD probe are not available; the deprecated `CBCPlayerIE` ThePlatform fallback fails typed until the D18 `theplatform.py` player; `Release_timestamp`, `episode_id`, `series`, `season_number`, `genres`, and `categories` are not modeled on the port's InfoDict. No cookie, account token, or signed URL is stored.
- The `is_upcoming` live gate uses a day-granularity date comparison, noted in the scope.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.cbc.CbcIETest"` — 14 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 882 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 70 partial, 84 planned, 1,597 not started.

Next: file 5, `niconico.py`.

**File 5 of 23 — `niconico.py` (2026-09-30).** `NiconicoIE.kt` accounts for all 13 classes in the file, read from the pin:

- Translated and registered (6): `NiconicoIE` (guest watch API plus the access-rights HLS call), `NiconicoPlaylistIE`, `NiconicoSeriesIE`, `NicovideoSearchURLIE`, `NicovideoTagURLIE`, and `NiconicoUserIE`. Bases `NiconicoBaseIE`, `NiconicoPlaylistBaseIE`, and `NicovideoSearchBaseIE` are `core`/`partial` rows.
- Planned (4) with the one-sentence reason: `NiconicoHistoryIE` (login cookies), `NicovideoSearchIE` and `NicovideoSearchDateIE` (keyword pseudo-URLs), and `NiconicoLiveIE` (WebSocket `startWatching` handshake).
- Helper added and named: five request-header names joined the allowlist in `HttpRequest.kt` — `x-access-right-key` (the short-lived key the watch API returned for one video, never persisted or logged), `x-frontend-id`, `x-frontend-version`, `x-niconico-language`, and `x-request-with` (fixed public client identifiers). `HttpRequestTest` asserts the new names.
- Limits in the manifest scopes: the port always uses the guest API (no login), and the 400/404 API error body (`meta.errorCode`/`reasonCode`) cannot be read through the typed HTTP seam, so scheduled/geo/PPV/premium/member-only refusals fail typed as unavailable; the payment flags still map to `availability`. Per-variant audio/video quality fields and the danmaku comment subtitles are not modeled (the HLS master is parsed at download time; the subtitle model carries URLs only). No cookie, account token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.niconico.NiconicoIETest"` — 10 tests, 0 failures (including the harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — 892 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 76 partial, 88 planned, 1,587 not started.

Next: file 6, `brightcove.py`.

**File 6 of 23 — `brightcove.py` (2026-09-30).** `BrightcoveIE.kt` accounts for all 3 classes in the file, read from the pin:

- Translated and registered (2): `BrightcoveLegacyIE` (`@videoPlayer` query forms resolve a publisher id from `publisherId`, the `playerKey` base64 decode, or the `bcpid` player page, then re-dispatch to the new player URL) and `BrightcoveNewIE` (policy key from `config.json` or `index.min.js`, then the Playback API). `BrightcoveNewBaseIE` is a `core`/`partial` row with the shared metadata mapping.
- Formats: progressive, `m3u8_native`, and `http_dash_segments` records; DRM flags for WVM/`key_systems`/ism; captions; the common-resolution poster rewrite; live when `duration <= 0`.
- Limits in the manifest scopes: RTMP-only sources and the webpage `<object>`/`<video>` scrapers are not translated; the smuggled referrer is not carried; `tags`/`uploader_id` are not modeled (the account id fills `channelId`); the 401/403 error body (CLIENT_GEO, INVALID_POLICY_KEY, TVE_AUTH) cannot be read through the typed HTTP seam, so those refusals fail typed as unavailable; the Adobe Pass TVE path stops at the typed login wall. No cookie, bearer token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.brightcove.BrightcoveIETest"` — 10 tests, 0 failures (including the harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — 903 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 78 partial, 88 planned, 1,585 not started.

Next: file 7, `pbs.py`.

**File 7 of 23 — `pbs.py` (2026-09-30).** `PbsIE.kt` accounts for both classes in the file, read from the pin:

- Translated and registered (2): `PBSIE` (the 136-entry station host list from `_STATIONS`, the presumptive-slug page scan with the multi-part tab regexes and media-id regexes, the partnerplayer iframe / `og:url` fallback, the player-page `PBS.videoData`/`videoBridge` state, the `widget/partnerplayer`/`portalplayer` redirects, chapters, captions, the program-title join, the rating age limit, and typed geo/expired redirect failures) and `PBSKidsIE` (the `_PBS_KIDS_DEEPLINK` state).
- Limits in the manifest scopes: the Frontline `getdir` JSONP path, the localization-cookie station setup, and the HTTP quality variants derived from the HLS master are not translated; `series`/`categories` are not modeled on the port's InfoDict for PBS Kids. No new core helper was needed; the rating reuses `ExtractorUtils.parseAgeLimit`. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.pbs.PbsIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 911 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 80 partial, 88 planned, 1,583 not started.

Next: file 8, `nhk.py`.

**File 8 of 23 — `nhk.py` (2026-09-30).** `NhkIE.kt` accounts for all 9 classes in the file, read from the pin:

- Translated and registered (8): `NhkVodIE`, `NhkVodProgramIE`, `NhkForSchoolBangumiIE`, `NhkForSchoolSubjectIE`, `NhkForSchoolProgramListIE`, `NhkRadiruIE`, `NhkRadioNewsPageIE`, and `NhkRadiruLiveIE`. `NhkBaseIE` is a `core`/`partial` row with the shows API and shared episode mapping.
- Helper added and named: `ExtractorUtils.parseDuration` (the `parse_duration` subset: `H:MM:SS`, `MM:SS`, ISO 8601, plain seconds); the `_utils.py` manifest scope names it.
- Limits in the manifest scopes: the Radiru extended-metadata formatting (act/music lists, the XML config detail URL) is not translated, so episodes keep the fallback title/description/timestamps; the live area argument defaults to Tokyo because the port has no extractor args; `series`, `episode`, `categories`, `tags`, and `cast` are not modeled on the port's InfoDict. Radiru playlist entries re-enter through the headline URL form. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nhk.NhkIETest"` — 13 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 924 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 88 partial, 88 planned, 1,575 not started.

Next: file 9, `zdf.py`.

**File 9 of 23 — `zdf.py` (2026-09-30).** `ZdfIE.kt` accounts for all 3 classes in the file, read from the pin:

- Translated and registered (2): `ZDFIE` (the `VideoByCanonical` GraphQL call, the `mediathekV2/document` fallback, the PTMD format walk with HLS/progressive formats, aspect-ratio widths, codecs, language preferences, captions, thumbnails, and chapters) and `ZDFChannelIE` (the persisted-query smart-collection and season pages with pagination; a single-video collection re-dispatches to the video). `ZDFBaseIE` is a `core`/`partial` row.
- Helper added and named: the `api-auth` header (the ZDF token endpoint's own value, fetched per extraction and never persisted or logged) and the fixed `apollo-require-preflight` boolean joined the request allowlist in `HttpRequest.kt`, with `HttpRequestTest` assertions.
- Limits in the manifest scopes: the token cache is per instance rather than on disk; the smuggled DGS variant flag is not carried (all variants are non-DGS); the old PTMD id archive field, `series`/`series_id`, and episode/season numbers are not modeled on the port's InfoDict; HLS masters are parsed at download time. No cookie, account token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.zdf.ZdfIETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 931 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 90 partial, 88 planned, 1,573 not started.

Next: file 10, `rai.py` (the 10th file, so the next wake also runs the every-10th-file full cadence).

**File 10 of 23 — `rai.py` (2026-09-30).** `RaiIE.kt` accounts for all 11 classes in the file, read from the pin:

- Translated and registered (10): `RaiPlayIE`, `RaiPlayLiveIE`, `RaiPlayPlaylistIE`, `RaiPlaySoundIE`, `RaiPlaySoundLiveIE`, `RaiPlaySoundPlaylistIE`, `RaiIE`, `RaiNewsIE`, `RaiCulturaIE`, and `RaiSudtirolIE`. `RaiBaseIE` is a `core`/`partial` row with the relinker XML, thumbnail list, and subtitle list.
- Limits in the manifest scopes: HLS manifests are recorded for the download-time parse, so the chunklist codec fixes, the HEAD-probed https-MP4 variants, and F4M relinkers are not translated; a geo-protected relinker with no formats and the DRM flag fail typed; `season`/`episode`/`series`/`release_year` are not modeled on the port’s InfoDict. No new core helper was needed. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rai.RaiIETest"` — 10 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 100 partial, 88 planned, 1,563 not started.

Every-10th-file cadence (file 10 of 23, 2026-09-30):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 941, ui 121, android-engine 41, port-manifest 25, all 0 failures. Desktop 146 tests: 2 failed, 15 skipped. Both failures are in the owner’s commit `83aa1ae`, which landed between this loop’s iterations: `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled` (“cancel deletes the partial file”; the new `discardStoredFiles` cleanup does not reach the test’s `clip.mp4.part`) and `FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory` (the new phone/home UI gives `add-download-button` no scrollable parent). Neither path touches this wake’s files and neither test uses the extractor registry; they are recorded as pre-existing owner-work failures, not caused by T-126.
- Cross-target compiles (`:shared:core` wasm main+test and iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — BUILD SUCCESSFUL.

Next: file 11, `nrk.py`.

**File 11 of 23 — `nrk.py` (2026-09-30).** `NrkIE.kt` accounts for all 13 classes in the file, read from the pin:

- Translated and registered (10): `NRKIE` (psapi playback/manifest/metadata), `NRKTVIE`, `NRKTVEpisodeIE`, `NRKTVSeasonIE`, `NRKTVSeriesIE`, `NRKTVDirekteIE`, `NRKRadioPodkastIE`, `NRKPlaylistIE`, `NRKTVEpisodesIE`, and `NRKSkoleIE`. Bases `NRKBaseIE`, `NRKTVSerieBaseIE`, and `NRKPlaylistBaseIE` are `core`/`partial` rows.
- Limits in the manifest scopes: HLS masters are recorded `m3u8_native` and parsed at download time, so the Akamai variant walk and CDN-replacement retry are not translated; `alt_title`, `series`, `season_id`, `season_number`, `episode`, and `episode_number` are not modeled on the port’s InfoDict. The playback API falls back to the short path when the `program` path fails. No new core helper was needed. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nrk.NrkIETest"` — 10 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 951 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 110 partial, 88 planned, 1,553 not started.

Next: file 12, `weverse.py`.

**File 12 of 23 — `weverse.py` (2026-09-30).** `WeverseIE.kt` accounts for all 8 classes in the file, read from the pin:

- The six registered classes (`WeverseIE`, `WeverseMediaIE`, `WeverseMomentIE`, `WeverseLiveTabIE`, `WeverseMediaTabIE`, `WeverseLiveIE`) and the two bases (`WeverseBaseIE`, `WeverseTabBaseIE`) have Kotlin classes that match their URL forms and fail typed as an account/login wall. The Weverse API needs the `we2_access_token`/`we2_refresh_token` cookies (or an OAuth refresh token), a device id, and HMAC-SHA1-signed query parameters; the port has no extractor login flow or cookie read, so the token exchange, the signed API calls, and the guest `/preview` path are not translated, and no harness case can pass (extraction is the typed failure by design). The one-sentence reason is on every manifest row.
- Manifest rows: 6 `extractor`/`partial` + 2 `core`/`partial`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.weverse.WeverseIETest"` — 3 tests, 0 failures (URL matching, the typed wall on all six forms, and the one-sentence reason).
- `./gradlew --no-parallel :shared:core:jvmTest` — 954 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 116 partial, 88 planned, 1,547 not started.

Next: file 13, `pornhub.py`.

**File 13 of 23 — `pornhub.py` (2026-09-30).** `PornHubIE.kt` accounts for all 8 classes in the file, read from the pin:

- Translated and registered (5): `PornHubIE` (flashvars mediaDefinitions, the `media_`/`quality_`/`qualityItems_` JS variables, download-button links, `/video/get_media`, title/uploader/duration/thumbnail/counts/captions), `PornHubUserIE` (redirect to the `/videos` list), `PornHubPagedVideoListIE`, `PornHubUserVideosUploadIE`, and `PornHubPlaylistIE` (the `viewChunked` pages). Bases `PornHubBaseIE`, `PornHubPlaylistBaseIE`, and `PornHubPagedPlaylistBaseIE` are `core`/`partial` rows.
- Limits in the manifest scopes: the port cannot set the `age_verified` cookies, log in, or run the PhantomJS gate, so a gated page fails typed as a login wall; requests are never impersonated; JSON-LD and the locked-player/login paths are not translated. HLS/DASH masters are recorded for the download-time parse. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.pornhub.PornHubIETest"` — 7 tests, 0 failures (including the harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — 961 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 121 partial, 88 planned, 1,542 not started.

Next: file 14, `ard.py`.

**File 14 of 23 — `ard.py` (2026-09-30).** `ArdIE.kt` accounts for all 6 classes in the file, read from the pin:

- Translated and registered (4): `ARDBetaMediathekIE` (page-gateway item, player streams with language preferences, subtitles, chapters, metadata, FSK age limit), `ARDMediathekCollectionIE` (sendung/serie/sammlung pagination), `ARDAudiothekIE` (GraphQL item audio formats), and `ARDAudiothekPlaylistIE` (GraphQL show entries). Bases `ARDMediathekBaseIE` (legacy media JSON parser) and `ARDAudiothekBaseIE` (GraphQL helper) are `core`/`partial` rows.
- Limits in the manifest scopes: the optional SSO age-verification token needs the `ams` cookie the port cannot read, so a `blockedByFsk` item fails typed as a login wall; RTMP-only and F4M legacy streams are not translated; `series`, `episode`, `episode_number`, and `display_id` are not modeled on the port’s InfoDict; HLS/DASH masters are recorded for the download-time parse. No new core helper was needed. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ard.ArdIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 969 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 125 partial, 88 planned, 1,538 not started.

Next: file 15, `zattoo.py`.

**File 15 of 23 — `zattoo.py` (2026-09-30).** `ZattooIE.kt` accounts for all 54 classes in the file, read from the pin:

- The 40 registered classes across the 13 platform families (Zattoo, NetPlusTV, MNetTV, WalyTV, BBVTV, VTXTV, GlattvisionTV, SAKTV, EWETV, QuantumTV, OsnatelTV, EinsUndEinsTV, SaltTV) match their program/live/recording/VOD URL forms and fail typed as an account wall. `ZattooPlatformBaseIE` and the 13 platform marker bases are `core`/`partial` rows. The Kotlin classes were generated from the pin’s `_create_valid_url` data; the station hosts and patterns match the upstream regexes.
- The one-sentence reason is on every row: every `_extract_*` path needs a subscription account (`_power_guide_hash` from `zapi/v2/account/login`) plus the session handshake, so the login flow and the watch/channel/playlist APIs are not translated; no harness case can pass (extraction is the typed failure by design). No cookie, token, or signed media URL is stored.
- Manifest rows: 40 `extractor`/`partial` + 14 `core`/`partial`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.zattoo.ZattooIETest"` — 2 tests (40 URL forms each), 0 failures.
- `./gradlew --no-parallel :shared:core:jvmTest` — 971 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 165 partial, 88 planned, 1,498 not started.

Next: file 16, `openrec.py`.

**File 16 of 23 — `openrec.py` (2026-09-30).** `OpenRecIE.kt` accounts for all 7 classes in the file, read from the pin:

- Translated and registered (6): `OpenRecIE` (live/archive pages with the page store and detail media URLs, chapters, live status), `OpenRecCaptureIE` (the encoded page store's `capture.source`), `OpenRecMovieIE`, `OpenRecPlaylistIE` (page-scan fallback), `OpenRecChannelIE`, and `OpenRecChannelSearchIE` (the public search endpoints). `OpenRecBaseIE` is a `core`/`partial` row with the page-store parser, the v5 API call, and the premium/subscription/PPV wall.
- Limits in the manifest scopes: the port cannot read the `access_token`/`random`/`token`/`uuid` cookies, so the email login and the authenticated playlist path are not translated (`users/me` 401 is treated as a guest, as upstream expects); the live comment subtitles (JSON2XML/SRT) are not translated; `cast`/`categories`/`tags` are not modeled; HLS masters are recorded for the download-time parse. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.openrec.OpenRecIETest"` — 9 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 980 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 171 partial, 88 planned, 1,492 not started.

Next: file 17, `xhamster.py`.

**File 17 of 23 — `xhamster.py` (2026-09-30).** `XHamsterIE.kt` accounts for all 4 classes in the file, read from the pin:

- Translated and registered (3): `XHamsterIE` (the `window.initials` videoModel with direct `sources`, the deciphered `xplayerSettings` HLS/standard streams, and the old-layout fallback), `XHamsterEmbedIE` (re-dispatch), and `XHamsterUserIE` (the user/creator listing scan). `_ByteGenerator` is a `utils`/`partial` row with all seven int32 keystream algorithms; the test decrypts a synthetic ciphertext generated from the translated algorithm.
- Limits in the manifest scopes: requests are never impersonated, so a page that refuses the plain client fails typed; HLS masters are recorded `m3u8_native`; `display_id`, `uploader_url`, like/dislike/comment counts, and `categories` are not modeled on the port’s InfoDict. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.xhamster.XHamsterIETest"` — 7 tests, 0 failures (including the harness case and the decipher round-trip).
- `./gradlew --no-parallel :shared:core:jvmTest` — 987 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 174 partial, 88 planned, 1,489 not started.

Next: file 18, `neteasemusic.py`.

**File 18 of 23 — `neteasemusic.py` (2026-09-30).** `NetEaseMusicIE.kt` accounts for all 8 classes in the file, read from the pin:

- The 7 registered classes (`NetEaseMusicIE`, `NetEaseMusicAlbumIE`, `NetEaseMusicSingerIE`, `NetEaseMusicListIE`, `NetEaseMusicMvIE`, `NetEaseMusicProgramIE`, `NetEaseMusicDjRadioIE`) and the `NetEaseMusicBaseIE` base match their URL forms and fail typed. The player API request body is AES-128-ECB encrypted with the web client key plus an MD5 digest, and the higher levels are gated behind a login/VIP account; the port has no AES-ECB encryption helper, so the eapi cipher, the player levels, the lyrics, and the playlist/metadata APIs are not translated. The one-sentence reason is on every row; no harness case can pass (extraction is the typed failure by design). No cookie, token, or signed media URL is stored.
- Manifest rows: 7 `extractor`/`partial` + 1 `core`/`partial`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.neteasemusic.NetEaseMusicIETest"` — 3 tests (10 URL forms), 0 failures.
- `./gradlew --no-parallel :shared:core:jvmTest` — 990 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 181 partial, 88 planned, 1,482 not started.

Next: file 19, `panopto.py`.

**File 19 of 23 — `panopto.py` (2026-09-30).** `PanoptoIE.kt` accounts for all 4 classes in the file, read from the pin:

- Translated and registered (3): `PanoptoIE` (DeliveryInfo streams, metadata, chapters), `PanoptoPlaylistIE` (playlist and session-list APIs), and `PanoptoListIE` (the GetSessions page walk with subfolders). `PanoptoBaseIE` is a `core`/`partial` row with the JSON API helper and the `ErrorCode == 2` login wall.
- Limits in the manifest scopes: the `_mark_watched` analytics call, the MHTML slides/storyboard formats, and the inline-SRT captions are not translated; `cast`, `tags`, and `average_rating` are not modeled; HLS masters are recorded `m3u8_native`. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.panopto.PanoptoIETest"` — 6 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 996 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 184 partial, 88 planned, 1,479 not started.

Next: file 20, `tvp.py` (the 20th file, so the next wake also runs the every-10th-file full cadence).

**File 20 of 23 — `tvp.py` (2026-09-30).** `TvpIE.kt` accounts for all 6 classes in the file, read from the pin:

- Translated and registered (5): `TVPIE` (the vue `__videoData`/`__newsData` and `__websiteData`/`__directoryData` listings, the classic iframe/object/tvpabc id scan, and the VOD hand-off), `TVPStreamIE` (the `__channels` live lookup), `TVPEmbedIE` (the TVPlayer2 JSONP config with HLS/DASH/direct formats, posters, age limit, subtitles, and typed payment/geo failures), `TVPVODVideoIE`, and `TVPVODSeriesIE`. `TVPVODBaseIE` is a `core`/`partial` row.
- Limits in the manifest scopes: F4M/ISM manifests are not translated; the direct-format quality fields are kept; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tvp.TvpIETest"` — 10 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 189 partial, 88 planned, 1,474 not started.

Every-10th-file cadence (file 20 of 23, 2026-09-30):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1006, ui 121, android-engine 41, port-manifest 25, all 0 failures. Desktop 146 tests: 1 failed, 15 skipped — `FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory` (“no parent layout with a Scroll SemanticsAction” on the new phone/home UI), the same pre-existing owner-work failure recorded at file 10; the CLI partial-cleanup test that failed at file 10 now passes.
- Cross-target compiles (`:shared:core` wasm main+test and iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — the first run caught a real multiplatform bug: `PornHubIE.kt` used the JVM-only `MutableMap.putIfAbsent` (it compiled on JVM but not on wasm/iOS). Replaced with a `containsKey` guard; the compile then passed. `PornHubIETest` still passes.

Next: file 21, `vrt.py`.

**File 21 of 23 — `vrt.py` (2026-09-30).** `VrtIE.kt` accounts for all 5 classes in the file, read from the pin:

- The four registered classes (`VRTIE`, `VrtNUIE`, `DagelijkseKostIE`, `Radio1BeIE`) and the `VRTBaseIE` base match their URL forms and fail typed. Every media URL comes from the VRT media-services API, whose `vrtPlayerToken` is a JWT signed with the web client key (HMAC-SHA256); the port does not add a signing helper, so the token call, the API, the format walk, and the VRT MAX login are not translated. The one-sentence reason is on every row; no harness case can pass (extraction is the typed failure by design). No cookie, token, or signed media URL is stored.
- Manifest rows: 4 `extractor`/`partial` + 1 `core`/`partial`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.vrt.VrtIETest"` — 3 tests (7 URL forms), 0 failures.
- `./gradlew --no-parallel :shared:core:jvmTest` — 1009 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 193 partial, 88 planned, 1,470 not started.

Next: file 22, `kaltura.py`.

**File 22 of 23 — `kaltura.py` (2026-09-30).** `KalturaIE.kt` accounts for the one class in the file, read from the pin:

- `KalturaIE` is registered and translated: the `kaltura:<partner>:<entry>[:<player>]` keyword, the index.php/mwEmbedFrame URL forms (query and path parameter parsing), the multirequest API (widget session, baseentry, flavor assets, captions), the iframe package data path, the playlist hand-off, and the per-video format/subtitle mapping.
- Limits in the manifest scope: the widget session key is fetched per extraction and used only in runtime URLs (no key, cookie, or signed media URL is stored); the `isOriginal` URL validity probe, WVM DRM assets, F4M/ISM, the smuggled `service_url`, and the kwidget-specific multirequest shape (the html5 action list is used for both) are not translated.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.kaltura.KalturaIETest"` — 5 tests, 0 failures (including the harness case).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1014 tests, 0 failures.
- `./gradlew --no-parallel :apps:desktop:testClasses` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 194 partial, 88 planned, 1,469 not started.

Next: file 23, `gamejolt.py` (the last file of D17, so the next wake also runs the every-10th-file full cadence).

**File 23 of 23 — `gamejolt.py` (2026-09-30).** `GameJoltIE.kt` accounts for all 8 classes in the file, read from the pin:

- Translated and registered (6): `GameJoltIE` (the posts view and video/GIF media mapping), `GameJoltUserIE`, `GameJoltGameIE`, `GameJoltGameSoundtrackIE`, `GameJoltCommunityIE`, and `GameJoltSearchIE` (the scroll-paged listings). Bases `GameJoltBaseIE` and `GameJoltPostListBaseIE` are `core`/`partial` rows.
- Limits in the manifest scopes: the comments side extractor, `display_id`, `uploader_url`, `categories`, `tags`, like/comment counts, and `release_timestamp` are not modeled; a soundtrack entry is a direct media URL whose download depends on the host’s direct-file path. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.gamejolt.GameJoltIETest"` — 9 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 200 partial, 88 planned, 1,463 not started.

Phase-final cadence (file 23 of 23, 2026-09-30):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1023, ui 121, android-engine 41, port-manifest 25, all 0 failures. Desktop 146 tests: 2 failed, 15 skipped — the same two pre-existing owner-work/flaky tests recorded at files 10 and 20 (`FreshWindowsInstallWithoutYtDlpTest` phone-UI scroll semantics, `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled` cleanup timing); neither uses the extractor registry.
- Cross-target compiles (`:shared:core` wasm main+test and iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — BUILD SUCCESSFUL.

T-126 summary: catalog D17 (23 large files, `adobepass.py` … `gamejolt.py`) is complete. 23 files translated, 200 partial + 88 planned extractor rows (0 ported, 1,463 not started of 1,751). The files that are only login/account/DRM walls (`weverse.py`, `zattoo.py`, `neteasemusic.py`, `vrt.py`) have URL-matching and typed-failure tests instead of passable harness cases, and every wall row says so in one sentence. One real multiplatform bug was fixed in the phase (PornHub’s JVM-only `putIfAbsent`). Next: T-127, D18 file 1 (`tumblr.py`).
