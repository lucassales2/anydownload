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

- [ ] The file list is written into Evidence at the start, from the pin, excluding files already closed in D16.
- [ ] Each file has harness cases and a manifest row. A login wall or DRM wall is Partial with that reason.
- [ ] `:tools:port-manifest:check` passes.

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

- Helper added and named: `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/theplatform/ThePlatformBaseIE.kt` translates the metadata and SMIL subset of `theplatform.py` (`_download_theplatform_metadata`, `_parse_theplatform_metadata`, and a SMIL reader) that `nbc.py` subclasses call. The full `ThePlatformIE`, F4M, `hdnea` cookie rewrite, geo headers, and hmac feed signing stay for D18 `theplatform.py`; a `core`/`partial` row records it.
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
