---
id: T-128
type: task
priority: P1
milestone: D19
tags: [task, engine, extractors]
---

# T-128 — Medium extractors, second half

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 19](../00-project/Phase-19-Medium-extractors-b.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

The remaining 110 extractor files in the 8–25 KB band are accounted for. Estimate 110 engineer-days.

## Dependencies

- [T-127](T-127-Medium-extractors-a.md).

## Acceptance criteria

- [ ] The file list is written into Evidence at the start and does not repeat D18.
- [ ] Each file has harness cases and a manifest row. A login wall or DRM wall is Partial with that reason.
- [ ] `:tools:port-manifest:check` passes.

## Evidence / notes

Not started. Helper gaps found here are paid from the 80-day buffer in the schedule. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md).

**Catalog D19 — T-128, 110 medium files (2026-10-01).** First `newgrounds.py`, last `getcourseru.py`, largest first, read from `yt_dlp/extractor/` at pin `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`), excluding the D18 files already closed.

**File 1 of 110 — `newgrounds.py` (2026-10-01).** `NewgroundsIE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `NewgroundsIE` (the listen/view page: the `embedController` source URL or the `/portal/video/` sources map, with title/uploader/timestamp/duration/thumbnail/description/age-limit/views and the audio vcodec/filesize hints), `NewgroundsPlaylistIE` (the wide-column collection/search submission anchors with the page title), and `NewgroundsUserIE` (the paged user movie/audio JSON items, at most five pages eagerly).
- Manifest limits: the netrc `_perform_login` flow is not translated (a 401 is the typed login wall) and the `_check_formats` probe is not translated, so a dead format URL is not pruned before download.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.newgrounds.NewgroundsIETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,610 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 572 partial, 92 planned, 1,087 not started.

Next: file 2, `tvplay.py`.

**File 2 of 110 — `tvplay.py` (2026-10-01).** `TvplayIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `TVPlayIE` (the MTG playapi video/stream JSON: HLS rows, direct rows with the upstream hls/medium/high quality order, SAMI subtitles, and the geo-blocked typed failure) and `TVPlayHomeIE` (the play.tv3.* product/playlist JSON: HLS row, artwork thumbnails, the resolved season/episode title, and the CATCHUP live path).
- Manifest limits: f4m and RTMP streams are skipped (no helper/downloader), the geo-bypass initialization is not translated, and series/season/episode/release-year fields are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tvplay.TvplayIETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,617 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 574 partial, 92 planned, 1,085 not started.

Next: file 3, `iwara.py`.

**File 3 of 110 — `iwara.py` (2026-10-01).** `IwaraIE.kt` accounts for all 3 registered classes plus the unregistered `IwaraBaseIE` (translated as the shared `callApi` base):

- Translated and registered (3): `IwaraIE` (the public video JSON with the SHA-1 `X-Version` file-list request and format rows, the private/not-found typed login wall, the unplayable typed failure, and the embedUrl redirect), `IwaraUserIE` (profile user info plus the paged videos listing, at most five pages eagerly), and `IwaraPlaylistIE` (the playlist first page plus the paged videos listing).
- **New helper named here: `sha1Hex`** (`anydownload`), a pure-Kotlin FIPS 180-4 SHA-1 for the public `X-Version` signature; known-vector test `Sha1Test` — 2 tests, 0 failures. The helper takes the public site constant; no credential is embedded.
- Manifest limits: the upstream `impersonate=True` requests become plain requests, so a Cloudflare challenge fails typed; the netrc user/media token flow is not translated; tags, like/comment counts, and the modified timestamp are dropped.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.iwara.IwaraIETest"` — 10 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,629 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 577 partial, 92 planned, 1,082 not started.

Next: file 4, `idagio.py`.

**File 4 of 110 — `idagio.py` (2026-10-01).** `IdagioIE.kt` accounts for all 5 registered classes plus the unregistered `IdagioPlaylistBaseIE` (translated as the shared track-entry/playlist base):

- Translated and registered (5): `IdagioTrackIE` (the track metadata/content JSON into one mp3 row with vcodec none, and the location-blocked typed geo wall), `IdagioRecordingIE` (work title, created date, and track entries), `IdagioAlbumIE` (id/title/description/thumbnail/publish date and entries), `IdagioPlaylistIE` (editorial playlist metadata and entries), and `IdagioPersonalPlaylistIE` (title/thumbnail/created date and entries).
- Manifest limits: artists, composers, genres, tags, creators, and display_id are not carried; a 406 metadata response surfaces as typed Unavailable rather than GeoRestricted. First run failed on the helper type visibility (the metadata data class is public now); fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.idagio.IdagioIETest"` — 9 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,638 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 582 partial, 92 planned, 1,077 not started.

Next: file 5, `ceskatelevize.py`.

**File 5 of 110 — `ceskatelevize.py` (2026-10-01).** `CeskaTelevizeIE.kt` accounts for the file's only registered class (the file has no bases):

- Translated and registered (1): `CeskaTelevizeIE` — the page/Next.js IDEC discovery (`show.mediaMeta.idec`, `videobonusDetail.bonusId`, and `liveBroadcast.current.idec`), the iframe-hash player page, the `get-client-playlist` POST with the Safari retry, the playlist JSON, and the HLS/DASH rows with the `drmOnly` flag and the audio-description preference. One item becomes one info dict; several items become selectable media.
- Manifest limits: the upstream `x-addr` and `X-Requested-With` headers are refused by the port's header allowlist (no impersonation); the inline millisecond-to-SRT subtitle conversion is not translated (the port's subtitle shape is URL-only); identical Safari-pass rows are deduped instead of doubled.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ceskatelevize.CeskaTelevizeIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,646 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 583 partial, 92 planned, 1,076 not started.

Next: file 6, `err.py`.

**File 6 of 110 — `err.py` (2026-10-01).** `ErrIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `ERRJupiterIE` (the public Jupiter/Jupiter+/Lasteekraan `vodContent` JSON with HLS/DASH/direct rows and title/description/created metadata; a DRM-restricted media fails typed; an episode fills `channel`/`channelId` from the series fields) and `ERRArhiivIE` (the arhiiv `content/video` JSON with HLS/DASH rows and title/synopsis/upload-date/series metadata).
- Manifest limits: alt_title, modified/release timestamps, release year, season/episode numbers, and the series/episode ids are dropped; an m3u8/mpd URL becomes one row, so manifest subtitles are not parsed.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.err.ErrIETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,653 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 585 partial, 92 planned, 1,074 not started.

Next: file 7, `fc2.py`.

**File 7 of 110 — `fc2.py` (2026-10-01).** `FC2IE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `FC2IE` (the content page and `videoplaylist` JSON with the single play row, native for an HLS URL, plus title/thumbnail/description), `FC2EmbedIE` (the `flv2.swf` query delegation to `FC2IE` with the computed thumbnail and embed title), and `FC2LiveIE` (matches the live URL forms and fails typed Unavailable at the FC2 WebSocket control channel).
- Manifest limits: the netrc login and session-cookie clear are not translated (a 401 is the typed login wall); the live HLS playlist needs the WebSocket channel the port does not implement.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.fc2.FC2IETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,660 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 588 partial, 92 planned, 1,071 not started.

Next: file 8, `turner.py`.

**File 8 of 110 — `turner.py` (2026-10-01).** `TurnerBaseIE.kt` accounts for the file's only class, the unregistered `TurnerBaseIE`:

- The file declares **no registered extractor classes** (`_extractors.py` imports `AdultSwimIE` from `adultswim.py` and `TBSIE` from `tbs.py`), so there is no `port/manifest.json` row and no registry entry for this file; the base is translated now because both later files extend it and both call only `_extract_ngtv_info`.
- Translated: the NGTV `medium.ngtv.io` JSON (`unprotected`/`bulkaes` m3u8 rows, duration, chapters), the public Akamai SPE tokenizer call (`?hdnea=`), the typed Adobe Pass wall for an `auth_required` tokenizer call, and the CVP `dateCreated/@uts` timestamp helper.
- Manifest limits: the CVP XML walk (`_extract_cvp_info`), the SMIL/f4m branches, and the MVPD-authenticated tokenizer path are not translated because no registered class reaches them and the port has no f4m/SMIL helper; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.turner.TurnerBaseIETest"` — 6 tests, 0 failures (the base's protected helpers are driven through a local test subclass).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,666 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 588 partial, 92 planned, 1,071 not started (unchanged; the file declares no registered class).

Next: file 9, `streaks.py`.

**File 9 of 110 — `streaks.py` (2026-10-01).** `StreaksIE.kt` accounts for the only registered class plus the unregistered `StreaksBaseIE` (translated as the shared playback base):

- Translated and registered (1): `StreaksIE` — the players.streaks.jp and playback.api.streaks.jp URL forms, the playback API JSON into HLS rows, the DRM source skip with a typed DRM-only failure, caption/subtitle tracks, and the metadata; a live source merges the public SSAI session query.
- Manifest limits: the `X-Streaks-Api-Key` header and `api_key` extractor arg are not carried (the header allowlist refuses it and no credential is stored), so an API-key-gated media surfaces as typed Unavailable; the 403/404 error-body inspection and the `live_from_start` ffmpeg option are not translated; an m3u8 URL becomes one HLS row.
- First run failed at class init: Java rejects the `api_key` named group (underscore), so it is `apikey`; fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.streaks.StreaksIETest"` — 6 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,672 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 589 partial, 92 planned, 1,070 not started.

Next: file 10, `naver.py` (the tenth-file full-suite and cross-target cadence runs on that wake).

**File 10 of 110 — `naver.py` (2026-10-01).** `NaverIE.kt` accounts for both registered classes plus the unregistered `NaverBaseIE` (translated as the shared signing-wall base):

- Translated and registered (2): `NaverIE` (the tv.naver.com video and embed URL forms) and `NaverLiveIE` (the tv.naver.com live URL forms). Both match and fail typed Unavailable because every path starts at the `now_web_api` `play-info` endpoint, which needs an `md` HMAC-SHA1 signature over a fixed key the port does not embed (the `zingmp3`/`abc` rule).
- Manifest limits: the `play.rmcnmv` `_extract_video_info` walk and the `process_subtitles` helper (upstream notes it is used by WeverseIE, whose port has its own path) are not translated; no cookie, token, or signed media URL is stored.

Commands (tenth-file cadence):

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.naver.NaverIETest"` — 3 tests, 0 failures (URL forms + the typed wall for both classes; a fixture cannot pass the signature).
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL: core 1,675/0, ui 121/0, android-engine 41/0, port-manifest 25/0. (The first run failed one `SubscriptionsUiTest` Compose semantics case; it passes in isolation and the rerun is green — a known Compose test flake, not extractor code.)
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 146 tests, 1 failed, 15 skipped; only the known `FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory` failure recorded in T-126; the flaky `YtDlpCliEngineTest` case passed this run.
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — the first run caught a JVM-only `String.format` leak in `streaks/StreaksIE.kt` (unresolved on wasm/iOS); replaced with a multiplatform `templateFill` helper, then all targets pass.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 591 partial, 92 planned, 1,068 not started.

Next: file 11, `playsuisse.py`.

**File 11 of 110 — `playsuisse.py` (2026-10-01).** `PlaySuisseIE.kt` accounts for the file's only registered class (the file has no bases):

- Translated and registered (1): `PlaySuisseIE` — the watch/detail URL forms match and fail typed LoginRequired. The upstream extractor refuses to run without `_ID_TOKEN` (`raise_login_required(method='password')`), and that token comes from the OAuth password login flow; the GraphQL asset query and the HLS `id_token` URLs are only reached after it, so neither is translated.
- Manifest limits: the OAuth PKCE login flow, the GraphQL asset query, and the token-bearing HLS URLs are not translated; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.playsuisse.PlaySuisseIETest"` — 2 tests, 0 failures (URL forms + typed LoginRequired).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,677 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 592 partial, 92 planned, 1,067 not started.

Next: file 12, `fourtube.py`.

**File 12 of 110 — `fourtube.py` (2026-10-01).** `FourTubeIE.kt` accounts for all 4 registered classes plus the unregistered `FourTubeBaseIE` (translated as the shared token/page base):

- Translated and registered (4): `FourTubeIE`, `FuxIE`, and `PornerBrosIE` (the page meta/anchor metadata, the token API `{mediaId}/desktop/{heights}` into direct rows, and the player-JS initialization fallback) and `PornTubeIE` (the `INITIALSTATE` base64 JSON plus the token API with user/channel metadata).
- Manifest limits: categories, dislike counts, and the channel/uploader fields the port does not model are dropped (the uploader id fills `channelId`; PornTube prefers channel id); no cookie, token, or signed media URL is stored.
- First runs failed on two Java rules: the base cannot resolve a subclass companion `VALID_URL` (the pattern is a base constructor property now), and named groups cannot contain underscores (`display_id` → `displayid`); both fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.fourtube.FourTubeIETest"` — 5 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,682 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 596 partial, 92 planned, 1,063 not started.

Next: file 13, `rtvcplay.py`.

**File 13 of 110 — `rtvcplay.py` (2026-10-01).** `RtvcplayIE.kt` accounts for all 3 registered classes plus the unregistered `RTVCPlayBaseIE` (translated as the shared player-config base):

- Translated and registered (3): `RTVCPlayIE` (the `window.__RTVCPLAY_STATE__` hydration: live channel HLS, the asset-id HLS template, season/podcast playlists, and metadata), `RTVCPlayEmbedIE` (the `config` player object's HLS/direct rows plus the CMS asset-id metadata), and `RTVCKalturaIE` (the player config rows plus the CMS streaming-term channel HLS and metadata, marked live).
- Manifest limits: an m3u8 URL becomes one HLS row, so manifest subtitles are not parsed; season/episode numbers are not carried on entries; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rtvcplay.RtvcplayIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,690 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 599 partial, 92 planned, 1,060 not started.

Next: file 14, `omnyfm.py`.

**File 14 of 110 — `omnyfm.py` (2026-10-01).** `OmnyfmIE.kt` accounts for all 3 registered classes plus the unregistered `OmnyfmPlaylistBaseIE` (translated as the shared clip-entry base):

- Translated and registered (3): `OmnyfmIE` (the Next.js clip props into one mp3 row with vcodec none, chapters, and metadata), `OmnyfmPlaylistIE` (the playlist props plus the paged clips API, and the bare playlists page listing playlist slugs), and `OmnyfmShowIE` (the program props plus the paged orgs clips API); both listings walk at most five pages eagerly.
- Manifest limits: the `section_start` query field, categories, tags, and episode/season numbers are not carried; the thumbnail query is stripped; no cookie, token, or signed media URL is stored.
- First runs failed twice: the Kotlin-escaped `\$` in the URL regexes became a literal dollar instead of the end anchor, and the harness cannot check `chapters` (not a case field); both fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.omnyfm.OmnyfmIETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,697 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 602 partial, 92 planned, 1,057 not started.

Next: file 15, `tubetugraz.py`.

**File 15 of 110 — `tubetugraz.py` (2026-10-01).** `TubetugrazIE.kt` accounts for both registered classes plus the unregistered `TubeTuGrazBaseIE` (translated as the shared episode/format base):

- Translated and registered (2): `TubeTuGrazIE` (the `search/episode.json` metadata and tracks into https rows with bitrates/resolution, HLS and DASH rows, the presentation/presenter format note and -2 preference, and the Wowza SMIL fallbacks probed non-fatally) and `TubeTuGrazSeriesIE` (the series episode list as watch-URL entries plus the series.json title).
- Manifest limits: the Shibboleth/TFA login flow is not translated; a series entry points at the episode's watch URL instead of inlining formats; the episode/series label fields are dropped (series fills `channel`, series id fills `channelId`, creator fills `uploader`).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tubetugraz.TubetugrazIETest"` — 6 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,703 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 604 partial, 92 planned, 1,055 not started.

Next: file 16, `sohu.py`.

**File 16 of 110 — `sohu.py` (2026-10-01).** `SohuIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `SohuIE` (the vrs_flash/videonew JSON, the six format ids, the clipsURL/mp4PlayUrl CDN resolution loop, the mytv publish date, and the geo/status-12 typed failures; a multipart video becomes selectable media) and `SohuVIE` (the base64 /v/ id decodes to the tv or my.tv page URL as a redirect).
- Manifest limits: the 8-hour publish-time adjustment, alt_title, and tags are not carried; the CDN URL is requested per extraction and never stored; no cookie or token is stored.
- First runs failed twice: Java has no `(?(name)...)` conditional groups, so the URL regex uses two host alternatives with `id`/`id2` (fixed); the mytv fixture page needed its `publishTime:` line (fixed).

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.sohu.SohuIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,711 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 606 partial, 92 planned, 1,053 not started.

Next: file 17, `boosty.py`.

**File 17 of 110 — `boosty.py` (2026-10-01).** `BoostyIE.kt` accounts for the file's only registered class (the file has no bases):

- Translated and registered (1): `BoostyIE` — the `api.boosty.to` post JSON, the ok_video `playerUrls` into HLS/DASH/direct rows with the upstream quality order, the title fallback page, the subscription wall, and the external-video redirect. A single item returns its info; several items become URL entries (an ok_video entry points at its best direct player URL, since the engine expands `entries` before `media`).
- Manifest limits: the upstream `auth` cookie Bearer extraction is not translated (the active cookie jar attaches the cookie itself); alt_title, tags, likes, and the release/modified timestamps are not carried. First compile failed on the `playerUrls` list type; fixed before Evidence.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.boosty.BoostyIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,719 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 607 partial, 92 planned, 1,052 not started.

Next: file 18, `youku.py`.

**File 18 of 110 — `youku.py` (2026-10-01).** `YoukuIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `YoukuIE` (the `ups.youku.com` JSON into m3u8 rows, the tail channel skipped, and the geo/private/generic typed failures) and `YoukuShowIE` (the JSONP module/episode pages as entry lists with the page title/description).
- Manifest limits: the `cna` value from the `eg.js` etag is not read (the port's HTTP seam does not expose response headers), so `utid` is sent empty; the `__ysuid`/`xreferrer` cookies are not set by the extractor; the `videopassword` param, uploader_url, and tags are not carried.
- Fixes before Evidence: the `get_element_by_class` helper is now tag-balanced (a nested `p-intro`/`intro-more` lost its closing tag), and the fixtures now use the double-quoted attributes the upstream anchor/reload regexes expect.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.youku.YoukuIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,727 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 609 partial, 92 planned, 1,050 not started.

Next: file 19, `stageplus.py`.

**File 19 of 110 — `stageplus.py` (2026-10-01).** `StageplusIE.kt` accounts for the file's only registered class (the file has no bases):

- Translated and registered (1): `StagePlusVODConcertIE` — the `vod_concert_*` URL forms match and fail typed LoginRequired because upstream refuses to run without a `dgplus_access_token` cookie or the OAuth password login, and the GraphQL query sends that token as a Bearer header while every HLS URL carries it as a `token` query parameter.
- Manifest limits: the OAuth password login, the GraphQL concert query, and the token-bearing HLS walk are not translated; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.stageplus.StageplusIETest"` — 2 tests, 0 failures (URL forms + typed LoginRequired).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,729 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 610 partial, 92 planned, 1,049 not started.

Next: file 20, `ruutu.py` (the twentieth-file full-suite and cross-target cadence runs on that wake).

**File 20 of 110 — `ruutu.py` (2026-10-01).** `RuutuIE.kt` accounts for the file's only registered class (the file has no bases):

- Translated and registered (1): `RuutuIE` — the `media-xml-cache` XML (the HTTPFile/AudioMediaFile walk, the `auth/access` lookup, m3u8 and audio rows, and the resolution/bitrate/label fields), the PassthroughVariables metadata, and the DRM/non-free typed failures.
- Manifest limits: upstream marks the class `_WORKING = False`; f4m and mpd rows are skipped, the `_is_valid_url` probe and the embed-url classmethod are not translated, and categories/season/episode numbers/rtmp preference are not carried. The first fixture used a bare `<File>` tag, which the upstream `tag.startswith('HTTP')` guard rejects; changed to `<HTTPFile>` before Evidence.

Commands (twentieth-file cadence):

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ruutu.RuutuIETest"` — 5 tests, 0 failures (including one harness case).
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL: core 1,734/0, ui 121/0, android-engine 41/0, port-manifest 25/0.
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 146 tests, 2 failed, 15 skipped; the two known pre-existing failures recorded in T-126 (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory`, `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`), neither touching extractor code.
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 611 partial, 92 planned, 1,048 not started.

Next: file 21, `vgtv.py`.

**File 21 of 110 — `vgtv.py` (2026-10-01).** `VgtvIE.kt` accounts for all 3 registered classes (the file has no bases):

- Translated and registered (3): `VGTVIE` (the `svp.vg.no` asset JSON with the host/appname/vendor mapping, HLS and direct rows with the width_height_tbr pattern, the geo-blocked typed failure, and the inactive typed failure), `BTArticleIE` (the bt.no article `video data-id` redirect to the `bttv:` scheme), and `BTVestlendingenIE` (the Vestlendingen fragment redirect).
- Manifest limits: upstream marks `VGTVIE` `_WORKING = False`; hds/f4m is skipped (no f4m helper), the 5-digit bttv `_extract_video_info` branch is not translated (the method does not exist upstream), and the extra f4m segment param is not carried.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.vgtv.VgtvIETest"` — 8 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,742 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 614 partial, 92 planned, 1,045 not started.

Next: file 22, `itv.py`.

**File 22 of 110 — `itv.py` (2026-10-01).** `ItvIE.kt` accounts for both registered classes (the file has no bases):

- Translated and registered (2): `ITVIE` (the ITV Hub `data-video-*` params, the featureset selection with the last platform tag first, the playlist POST with the public `hmac` header, MediaFiles into HLS/direct rows, the outband-webvtt subtitle call, and the posterframe/og thumbnails) and `ITVBTCCIE` (the news/btcc Next.js article Brightcove entries as player URLs).
- Manifest limits: the JSON-LD merge, the Brightcove smuggle payload (geo IP blocks/referrer), and the geo headers bypass are not translated; no cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.itv.ItvIETest"` — 6 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,748 tests, 0 failures. (The first full run failed one `JavaNetRequestContractTest` byte-range case with a local-socket read timeout; it passes in isolation and the rerun is green — an environmental flake, not extractor code.)
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 616 partial, 92 planned, 1,043 not started.

Next: file 23, `audius.py`.

**File 23 of 110 — `audius.py` (2026-10-01).** `AudiusIE.kt` accounts for the registered classes (plus the unregistered `AudiusBaseIE`):

- Translated and registered (4): `AudiusIE` (the `api.audius.co` host list, the resolve JSON, and the single stream row with the artwork map), `AudiusTrackIE` (the `audius:` id/API link), `AudiusPlaylistIE` (the resolve playlist JSON and the track list as `audius:` entries), and `AudiusProfileIE` (the profile handle JSON and its track list).
- Manifest limits: the track/genre labels and the like/repost counts are not carried (artist fills `uploader`); a resolve 404 surfaces as typed Unavailable because the port cannot read a non-2xx body.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.audius.AudiusIETest"` — 7 tests, 0 failures (including two harness cases).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,755 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 620 partial, 92 planned, 1,039 not started.

Next: file 24, `amazonminitv.py`.

**File 24 of 110 — `amazonminitv.py` (2026-10-01).** `AmazonMiniTvIE.kt` accounts for the registered classes (plus the unregistered `AmazonMiniTVBaseIE`):

- Translated and registered (3): `AmazonMiniTVIE` (the `/prs` playback assets as one HLS row and one DASH row, and the `content` GraphQL metadata with the artwork images, synopsis, duration, and the End Credits chapter), `AmazonMiniTVSeasonIE` (the `getEpisodes` entries), and `AmazonMiniTVSeriesIE` (the `getSeasons` entries).
- Manifest limits: upstream reads the guest `session-id` cookie from the minitv page in `_real_initialize`; the platform strips `Set-Cookie` at its boundary by design and the port does not read user cookie values, so `sessionIdToken` is sent empty and a real deployment may refuse the GraphQL calls (a typed Unavailable then names the API message). The `language`, `release_timestamp`, and series/season/episode fields the port does not model are dropped. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.amazonminitv.AmazonMiniTvIETest"` — 6 tests, 0 failures (including one harness case; the harness does not check `chapters`, so the direct test asserts them).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,761 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 623 partial, 92 planned, 1,036 not started.

Next: file 25, `wykop.py`.

**File 25 of 110 — `wykop.py` (2026-10-01).** `WykopIE.kt` accounts for the registered classes (plus the unregistered `WykopBaseIE`):

- Translated and registered (4): `WykopDigIE` (the link URL form, with `suitable` yielding comment URLs), `WykopDigCommentIE` (the `/komentarz/` form), `WykopPostIE` (the wpis form, with `suitable` yielding comment URLs), and `WykopPostCommentIE` (the `#comment` form).
- Manifest limits: all four match and fail typed Unavailable because every API path starts at `/api/v3/auth`, which mints an anonymous bearer token from a frontend key/secret the port does not carry (the naver/zingmp3/abc rule); the `_common_data_extract` walk and the transparent-URL child resolution are not translated. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.wykop.WykopIETest"` — 5 tests, 0 failures (URL matching plus the four typed walls).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,766 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 627 partial, 92 planned, 1,032 not started.

Next: file 26, `sonyliv.py`.

**File 26 of 110 — `sonyliv.py` (2026-10-01).** `SonyLivIE.kt` accounts for both registered classes (plus the shared base):

- Translated and registered (2): `SonyLIVIE` (the anonymous `GETTOKEN` call, the VOD playback JSON as one DASH row and one HLS row from the same `videoURL`, the detail metadata, and the subtitle tracks) and `SonyLIVSeriesIE` (the show season list and the paginated episode bundles as `sonyliv:` entries, capped at 100 pages).
- Manifest limits: the platform's request-header allowlist refuses the `security_token` header upstream sends on every call (the streaks/`x-addr` rule); the OTP/login flow is not translated and a subscription wall fails typed; an `isEncrypted` asset fails typed DRM; the `timestamp`/`season_number`/`series`/`episode_number`/`release_year` fields are dropped; the `sort_order` arg is not carried (ascending only). No cookie, token, or signed media URL is stored.
- A wake fix: the series endpoints use the `R/ENG/WEB` segment, not `A/ENG/WEB`; the shared `callApi` now takes the segment and the fixture caught it.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.sonyliv.SonyLivIETest"` — 5 tests, 0 failures (including one harness case and the typed DRM wall).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,771 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 629 partial, 92 planned, 1,030 not started.

Next: file 27, `iprima.py`.

**File 27 of 110 — `iprima.py` (2026-10-01).** `IPrimaIE.kt` accounts for both registered classes:

- Translated and registered (2): `IPrimaIE` (every non-CNN URL form matches and fails typed `LoginRequired` — upstream `_real_initialize` needs the email/password session token before any content call, and `X-OTT-Access-Token` is refused by the allowlist) and `IPrimaCNNIE` (the public CNN page title, player id search, `prehravac/init` player options with the `jsToJson` subset, HLS tracks as one row per m3u8, the `src:` regex fallback, and the geo marker).
- Manifest limits: the `ott_adult_confirmed` cookie upstream sets cannot be set by an extractor here; DASH tracks are skipped exactly as the upstream dead `return` does. No cookie, token, or signed media URL is stored.
- A wake fix: the player-options regex needed escaped braces (`\{.+?\}`) for the Java engine — the same mailru pitfall; the fixture caught it.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.iprima.IPrimaIETest"` — 5 tests, 0 failures (including one harness case, the typed login wall, and the typed geo wall).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,776 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 631 partial, 92 planned, 1,028 not started.

Next: file 28, `pinterest.py`.

**File 28 of 110 — `pinterest.py` (2026-10-01).** `PinterestIE.kt` accounts for both registered classes (plus the shared base):

- Translated and registered (2): `PinterestIE` (the PinResource JSON as one HLS row or direct row per `video_list` entry, thumbnails, title/description/uploader, and uploadDate from `created_at`) and `PinterestCollectionIE` (the Board JSON and the paginated BoardFeed JSON as pin-URL entries, 100-page cap).
- Manifest limits: the platform allowlist refuses the `X-Pinterest-PWS-Handler` header (streaks/`x-addr` rule); an embed-src pin returns one child entry at the embed URL because the port's transparent dispatch is the entry expansion, so the pin metadata merge is not carried; the repost/comment/category/tag fields the info dict does not model are dropped. No cookie, token, or signed media URL is stored.
- A wake fix: the port's `parseIso8601` is the ISO-duration parser, so the ISO `created_at` goes through `unifiedStrdate` (the port convention); the fixture caught it.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.pinterest.PinterestIETest"` — 5 tests, 0 failures (including one harness case, the embed child entry, and the two-page board pagination).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,781 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 633 partial, 92 planned, 1,026 not started.

Next: file 29, `kick.py`.

**File 29 of 110 — `kick.py` (2026-10-01).** `KickIE.kt` accounts for the three registered classes (plus the shared base):

- Translated and registered (3): `KickIE` (the `v2/channels` JSON with the one HLS row from `playback_url` and the live metadata; a channel without a `livestream` object fails typed `NotYetAvailable`), `KickVODIE` (the `v1/video` JSON with the one HLS row from `source` and the VOD metadata), and `KickClipIE` (the `v2/clips/play` JSON with one HLS row or one direct row and the clip metadata).
- Manifest limits: upstream reads the `session_token` cookie and sends `Authorization: Bearer` plus `impersonate=True`; the platform allowlist refuses `Authorization` and the port does not impersonate, so only public resources resolve; `uploader_id`, `concurrent_view_count`, `release_timestamp`, `categories`, and `like_count` are not modeled and are dropped. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.kick.KickIETest"` — 7 tests, 0 failures (including one harness case, the typed offline wall, and the direct-clip row).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,788 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 636 partial, 92 planned, 1,023 not started.

Next: file 30, `taptap.py` — the 30th file, so this wake also runs the full cadence (`:shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks`, desktop separately, and the cross-target compiles).

**File 30 of 110 — `taptap.py` (2026-10-01).** `TapTapIE.kt` accounts for the four registered classes (plus the shared base):

- Translated and registered (4): `TapTapMomentIE` (the moment detail JSON and the video-resource multi-get JSON), `TapTapAppIE` (the cn app detail JSON), `TapTapAppIntlIE` (the taptap.io app detail JSON), and `TapTapPostIntlIE` (the taptap.io post detail JSON with the `id_str` query). Each video id becomes one selectable `media` item (the port's multi-video model, Vidyard precedent) with the page metadata merged into the item title.
- Manifest limits: the upstream playlist result maps to selectable media items rather than child jobs; the `modified_timestamp` field is dropped and an m3u8 URL becomes one HLS row (so the h265 format-id rename is not carried). No cookie, token, or signed media URL is stored.
- A wake fix: the `TapTapMeta` holder had to be a public top-level type because a `protected` member cannot expose a private-in-file type.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.taptap.TapTapIETest"` — 6 tests, 0 failures (including one harness case, the app-list dedupe, and both intl data paths).
- **10th-file cadence:** `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — first run failed the port-manifest `committedCoverageBlockIsCurrent` check because `:tools:port-manifest:run` had not yet been called this wake; after the run it was green: core 1,794/0 (one `JavaNetChunkedDownloadTest.singleBodyDropResumesWithARangedRetry` flake passed in isolation and the clean full rerun is green), ui 121/0, android-engine 41/0, port-manifest 25/0.
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 146 tests, 2 failed, 15 skipped: only the two known pre-existing owner-work failures (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory`, `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`); neither uses the extractor registry.
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 640 partial, 92 planned, 1,019 not started.

Next: file 31, `ustream.py`.

**File 31 of 110 — `ustream.py` (2026-10-01).** `UstreamIE.kt` accounts for both registered classes:

- Translated and registered (2): `UstreamIE` (the recorded JSON as direct `media_urls` rows with the upstream filesize, or the HLS fallback through the `ums.ustream.tv` connection info with the random host/rsid/rpin values; the embed page's `offAirContentVideoIds` list; and the `embed/recorded` self-dispatch as a child entry) and `UstreamChannelIE` (the `ustream:channel_id` meta and the paginated `socialstream` JSON as recorded-URL entries, 100-page cap).
- Manifest limits: the upstream `_EMBED_REGEX` generic discovery is not carried (GenericIE stays out); `uploader_id` and the channel `display_id` slug are dropped; an m3u8 fallback becomes one HLS row; the commented-out segmented-MP4 DASH path stays unported. No cookie, token, or signed media URL is stored.
- A wake fix: the channel fixture had both `data-content-id` divs on one line, and the greedy `(\d.*)` capture (which upstream also has, with `.` not matching newlines) consumed both — the fixture now separates them with `\n`, matching the real page shape.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ustream.UstreamIETest"` — 8 tests, 0 failures (including one harness case, the embed entries, the HLS fallback, the typed API error, and the channel pagination).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,802 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 642 partial, 92 planned, 1,017 not started.

Next: file 32, `ted.py`.

**File 32 of 110 — `ted.py` (2026-10-01).** `TedIE.kt` accounts for the four registered classes (plus the shared base):

- Translated and registered (4): `TedTalkIE` (the Next.js `videoData`/`playerData` resources as one HLS row, the h264 rows with bitrate ids, the simplified http cross-fill, and the audio row, with the talk metadata), `TedSeriesIE` (the seasons/series props as canonical-URL entries with the `#season_N` filter and season naming), `TedPlaylistIE` (the playlist props as entries), and `TedEmbedIE` (the embed host rewrite delegating to `TedTalkIE`).
- Manifest limits: rtmp rows are skipped (no RTMP support); m3u8 variants and their subtitles are not parsed, so the `http_url` cross-fill cannot exclude audio-only variants; an external embed becomes one child entry; `release_date`/`tags` are dropped; the `#season_N` filter compares string forms. No cookie, token, or signed media URL is stored.
- A small in-file helper was added: `strToInt` (the port had no `str_to_int`), handling commas and K/M/B suffixes; named here per the rule.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ted.TedIETest"` — 8 tests, 0 failures (including one harness case, the external child entry, both series shapes, the playlist, and the embed rewrite).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,810 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 646 partial, 92 planned, 1,013 not started.

Next: file 33, `teachable.py`.

**File 33 of 110 — `teachable.py` (2026-10-01).** `TeachableIE.kt` accounts for both registered classes (plus the shared base):

- Translated and registered (2): `TeachableIE` (the lecture page's Wistia embed discovery as child entries, the `teachable:` prefix handling, and the locked-lecture typed `LoginRequired` wall) and `TeachableCourseIE` (the course page's `section-item` walk as lecture-URL entries with the prefix and course title, plus the `suitable` override).
- Manifest limits: the netrc login (`_login`) is not translated, so a locked lecture fails typed; `chapter`/`chapter_number` and the `video_title` extra are not modeled and are dropped. No cookie, token, or signed media URL is stored.
- A wake helper was added and named here per the rule: `wistiaEmbedUrls` (the `WistiaIE._extract_embed_urls` subset: the meta/iframe/script src regex plus the `data-wistia-id`/`Wistia.embed`/`id="wistia_` regex), because the port's `WistiaIE` does not expose the upstream classmethod. A URL-join fix was also needed (upstream `urljoin` semantics) so relative lecture links do not double the slash.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.teachable.TeachableIETest"` — 7 tests, 0 failures (including one harness case, the `Wistia.embed` id regex, the typed locked wall, and both prefixed shapes).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,817 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 648 partial, 92 planned, 1,011 not started.

Next: file 34, `ninaprotocol.py`.

**File 34 of 110 — `ninaprotocol.py` (2026-10-01).** `NinaProtocolIE.kt` accounts for the one registered class:

- Translated and registered (1): `NinaProtocolIE` (the `v1/releases` JSON with the `publicKey` override, the metadata/publisher/hub fields, and each `metadata.properties.files` entry as one selectable `media` item with its direct audio row `vcodec: none`).
- Manifest limits: the upstream playlist result maps to the port's selectable media items (TapTap/Vidyard model); `album`, `album_artist`, `tags`, `uploader_id`, `display_id`, and `track`/`track_number` are not modeled and are dropped; an m3u8 file would still become one row. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ninaprotocol.NinaProtocolIETest"` — 4 tests, 0 failures (including one harness case and the URL-id fallback).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,821 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 649 partial, 92 planned, 1,010 not started.

Next: file 35, `condenast.py`.

**File 35 of 110 — `condenast.py` (2026-10-01).** `CondeNastIE.kt` accounts for the one registered class:

- Translated and registered (1): `CondeNastIE` (the `var params` JSON and `data-js="video-player"` attributes, the `embed-api.json`/`player/video.js`/`player/loader.js`/`inline/video/{id}.js` fallback chain, the source rows with one HLS row for m3u8 and the numeric quality on direct rows, the vtt/srt/tml captions, the `__PRELOADED_STATE__` description, and the series thumb-title entries).
- Manifest limits: the `_EMBED_REGEX` generic discovery and the JSON-LD merge on the params path are not carried (GenericIE stays out; the port's JsonLd has no VideoObject mapping); `tags`/`series`/`season`/`categories` are dropped; the series playlist takes the URL slug as its id (upstream leaves it unset). No cookie, token, or signed media URL is stored.
- A small in-file helper was added and named here per the rule: `extractAttributes` (upstream `extract_attributes`), because `ExtractorUtils` keeps its attribute parser private.
- A wake fix: the header comment's `inline/video/*.js` opened a nested block comment in Kotlin (block comments nest), which the compiler caught; rewritten as `{id}.js`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.condenast.CondeNastIETest"` — 7 tests, 0 failures (including one harness case, the preloaded/params/attributes watch paths, and the series dedupe).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,828 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 650 partial, 92 planned, 1,009 not started.

Next: file 36, `japandiet.py`.

**File 36 of 110 — `japandiet.py` (2026-10-01).** `JapanDietIE.kt` accounts for the five registered classes (plus the shared base):

- Translated and registered (5): `ShugiinItvLiveIE` (the index page's `play_live` room list as child entries, with the `suitable` override), `ShugiinItvLiveRoomIE` (the room HLS row and the title from the room list), `ShugiinItvVodIE` (the VOD m3u8 URL, title, Japanese-era release date folded into `uploadDate`, and the `play_vod` chapters with the last end-time), `SangiinIE` (date/title/description, live marker, videopath HLS row), and `SangiinInstructionIE` (the index form fails typed `Unavailable` with the copy-the-link instruction).
- Manifest limits: upstream reads the Shugiin pages as EUC-JP and the port's HTTP layer decodes UTF-8, so real EUC-JP titles may be garbled (fixture-level fidelity only); the smuggled room tuple is replaced by the same room-list fetch; m3u8 subtitles are not parsed (one HLS row); `release_date` folds into `uploadDate`. No cookie, token, or signed media URL is stored.
- Wake fixes: `matchId` throws on a regex with no `id` group, so the index class validates with `containsMatchIn` instead; the VOD title fixture had a space before `(`, which upstream's greedy `(.+)` keeps (the real page has none); and the era fixture was switched to the Western date form the real page uses, since the port mirrors upstream's era arithmetic exactly.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.japandiet.JapanDietIETest"` — 7 tests, 0 failures (including one harness case, the room list, the VOD chapters, the Sangiin page, and the typed instruction).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,835 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 655 partial, 92 planned, 1,004 not started.

Next: file 37, `redgifs.py`.

**File 37 of 110 — `redgifs.py` (2026-10-01).** `RedGifsIE.kt` accounts for the three registered classes (plus the shared base):

- Translated and registered (3): `RedGifsIE` (the temporary-token fetch with the one-time 401 refresh — the DPlay `discoAuth` shape using the `authorization` parameter — the gif JSON as gif/sd/hd rows with the aspect-ratio width and numeric quality, and the `RedGifs said:` typed error), `RedGifsSearchIE` (the browse query fields, the tags requirement, and the paged `gifs/search` entries), and `RedGifsUserIE` (the user query fields and the paged `users/search` entries).
- Manifest limits: the `x-customheader` header is refused by the platform allowlist (only `referer`/`origin` are carried); the search/user entries expand as child watch jobs (an extra API call) instead of the upstream inline infos; `categories`/`tags`/`like_count` are dropped; pagination is capped at 100 pages. The temporary bearer token is fetched at runtime and never stored; fixtures use `fake_value`.
- A wake fix: the `page=` search fixture needed a wildcard because the API query also carries `type=g`; the direct-page path now matches.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.redgifs.RedGifsIETest"` — 8 tests, 0 failures (including one harness case, the gif/sd/hd rows, both pagination shapes, the typed tags error, and the typed API error).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,843 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 658 partial, 92 planned, 1,001 not started.

Next: file 38, `mediastream.py`.

**File 38 of 110 — `mediastream.py` (2026-10-01).** `MediaStreamIE.kt` accounts for both registered classes (plus the shared base):

- Translated and registered (2): `MediaStreamIE` (the `window.MDSTRM.OPTIONS` JSON as one HLS row with the `at`/`access_token`/`uid`/`sid`/`pid`/`av` query params, one MPD row, and direct rows, plus the four geo messages and the og metadata) and `WinSportsVideoIE` (the drupal-settings JSON and its `mediastream_formatter` lookup, the JSON-LD/player-script/iframe discovery fallback, the title with the `| Win Sports` suffix removed, dispatching to `MediaStreamIE` directly).
- Manifest limits: the MediaStreamVideoPlayer div discovery and `_extract_from_webpage` are not carried; m3u8/mpd subtitles are not parsed (one row per manifest); the WinSports dispatch is direct rather than transparent. No cookie, token, or signed media URL is stored.
- Wake helpers named per the rule: `extractBalancedJson` (upstream `_search_json`), `updateUrlQuery` (upstream `update_url_query`), `queryValue`, `urlJoin`, and `removeEnd`.
- A wake fix: the WinSports test expected the raw m3u8 URL, but upstream's `filter_dict` keeps `at=web-app` even when every other param is null — the port is faithful and the fixture was corrected.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.mediastream.MediaStreamIETest"` — 5 tests, 0 failures (including one harness case, the HLS query merge, the typed geo wall, and the WinSports drupal path).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,848 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 660 partial, 92 planned, 999 not started.

Next: file 39, `dropout.py`.

**File 39 of 110 — `dropout.py` (2026-10-01).** `DropoutIE.kt` accounts for both registered classes:

- Translated and registered (2): `DropoutIE` (the watch page's `embed_url`, title, description, thumbnail with the crop query removed, release date folded into `uploadDate`, and the `watch-unauthorized` typed login wall) and `DropoutSeasonIE` (the season page's `browse-item-link` pagination as child entries with the season id/title naming).
- Manifest limits: the netrc login is not translated, so a page without the `_session` cookie fails typed; the upstream `url_transparent` dispatch to `VHXEmbedIE` becomes one child entry at the embed URL, and `VHXEmbedIE` itself is not part of this card (it stays not-started); `episode_number`/`season_number`/`display_id` are dropped; a 400 page end is treated as an empty page. No cookie, token, or signed media URL is stored.
- Wake helpers named per the rule: `elementById`/`elementByClass`/`elementsByClass` (the tag-balanced `get_element_by_id`/`get_element_by_class`/`get_elements_html_by_class` subset) and `extractAttributes`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.dropout.DropoutIETest"` — 6 tests, 0 failures (including one harness case, the typed login wall, and both season shapes).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,854 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 662 partial, 92 planned, 997 not started.

Next: file 40, `teamcoco.py` — the 40th file, so this wake also runs the full cadence (`:shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks`, desktop separately, and the cross-target compiles).

**File 40 of 110 — `teamcoco.py` (2026-10-01).** `TeamcocoIE.kt` accounts for both registered classes (plus the shared base):

- Translated and registered (2): `TeamcocoIE` (the Next.js `pageData` blocks merged into one info, the `src` rows as one HLS row or direct low/sd/hd/uhd rows, the thumbnail-derived video id, and the metadata) and `ConanClassicIE` (the `incomingVideoId` lookup, the legacy GraphQL JSON with the `findRecord` metadata, and the NGTV media info through the shared Turner base with the `jws` token, falling back to the metadata `src` rows).
- Manifest limits: the `_initialize_geo_bypass(['US'])` call is not carried; m3u8 subtitles are not parsed (one row per manifest); `display_id` and `_old_archive_ids` are dropped; `timestamp` folds into `uploadDate`. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.teamcoco.TeamcocoIETest"` — 5 tests, 0 failures (including one harness case and both Conan Classic paths).
- **40th-file cadence:** `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1,859/0, ui 121/0, android-engine 41/0, port-manifest 25/0.
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 146 tests, 2 failed, 15 skipped: only the two known pre-existing owner-work failures (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory`, `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`); neither uses the extractor registry.
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — the first run caught a JVM-only `String.format` leak in `ted/TedIE.kt` (the same streaks pitfall, latent since file 32 because that wake was not a cadence wake); replaced with a multiplatform `replace("%s", ...)`, then all targets pass and the clean `:shared:core:jvmTest --rerun-tasks` rerun is 1,859/0 with the TED test 8/8.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 664 partial, 92 planned, 995 not started.

Next: file 41, `ivi.py`.

**File 41 of 110 — `ivi.py` (2026-10-01).** `IviIE.kt` accounts for both registered classes:

- Translated and registered (2): `IviIE` (the `api.ivi.ru/light` `da.content.get` call as the unsigned site-183 fallback, the file rows with the known-format quality index and the DRM/FPS skip, the compilation title split, the preview thumbnails, and the og description) and `IviCompilationIE` (the compilation/season page walk with the episode links as child entries and the meta title).
- Manifest limits: upstream tries site 353 first, which signs the request with a CMAC-Blowfish key (`_LIGHT_KEY`) and pycryptodomex; the port does not embed that key or add a crypto helper (the naver/zingmp3/abc rule) and uses the unsigned site-183 call upstream falls back to; `series`/`season`/`season_number`/`episode`/`episode_number` are dropped; the generic `_EMBED_REGEX` discovery is not carried. No cookie, token, or signed media URL is stored.
- A wake fix: the season-link regex has two groups (compilation id, season), so the walk uses group 2; the compilation fixture caught the off-by-one.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ivi.IviIETest"` — 6 tests, 0 failures (including one harness case, the typed geo wall, and both playlist shapes).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,865 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 666 partial, 92 planned, 993 not started.

Next: file 42, `radiko.py`.

**File 42 of 110 — `radiko.py` (2026-10-01).** `RadikoIE.kt` accounts for both registered classes (plus the shared base):

- Translated and registered (2): `RadikoIE` (the time-free URL form) and `RadikoRadioIE` (the live URL form). Both match and fail typed `Unavailable` at the X-Radiko auth wall.
- Manifest limits: every stream starts at the `v2/api/auth1` handshake, which needs the `X-Radiko-*` request headers (refused by the allowlist), reads the token/key length/key offset from response headers (stripped by design), and cuts the partial key from the player's full key, which upstream also embeds as a fallback — the port does not carry that key (the naver/zingmp3/abc rule). The program XML walk and the m3u8 `preference`/`ffmpeg_args` shaping are not translated. No cookie, token, or signed media URL is stored.
- A wake fix: `matchId` throws on a regex without an `id` group (the japandiet lesson again), so `RadikoIE` validates with `containsMatchIn`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.radiko.RadikoIETest"` — 3 tests, 0 failures (URL matching plus the two typed walls).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,868 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 668 partial, 92 planned, 991 not started.

Next: file 43, `srgssr.py`.

**File 43 of 110 — `srgssr.py` (2026-10-01).** `SrgssrIE.kt` accounts for both registered classes:

- Translated and registered (2): `SRGSSRIE` (the `mediaComposition` JSON with the block-reason map — `GEOBLOCK` to typed `GeoRestricted(CH)` — the `akahd` token authparams append, the resource rows (one HLS row for HLS/AKAMAI, direct HTTP(S) rows with the SD/HD quality index, the podcast rows), and the subtitle list with the per-bu default language) and `SRGSSRPlayIE` (the srf/rts/rsi/rtr/swissinfo play URL forms, including the popup players and `urn` ids, dispatching to the `srgssr:` id).
- Manifest limits: an HDS resource is skipped (the port has no f4m helper); m3u8 subtitle tracks are not parsed; the AKAMAI rows become one HLS row instead of the upstream akamai manifest walk; `timestamp` folds into `uploadDate`. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.srgssr.SrgssrIETest"` — 6 tests, 0 failures (including one harness case, the AKAMAI token append, the two typed block walls, and the play dispatch).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,874 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 670 partial, 92 planned, 989 not started.

Next: file 44, `onet.py`.

**File 44 of 110 — `onet.py` (2026-10-01).** `OnetIE.kt` accounts for the four registered classes (plus the shared base):

- Translated and registered (4): `OnetIE` (the `qi.ckm.onetapi.pl` asset detail as one MPD row, one HLS row, and the direct rows with resolution/bitrate and the audio `vcodec: none`, plus the mvp id search), `OnetMVPIE` (the `onetmvp:` id form), `OnetChannelIE` (the channel page's video links as child entries with the name/description classes), and `OnetPlIE` (the onet.pl/businessinsider/plejada `data-mvp` search with the pulsembed fallback, dispatching to `OnetMVPIE` directly).
- Manifest limits: an `ism` format is skipped (the port has no ISM/MSS helper); m3u8/mpd subtitles are not parsed (one row per manifest); the upstream `_yes_playlist` prompt is not carried, so the channel page always returns its playlist; `display_id` is dropped; `timestamp` folds into `uploadDate`. No cookie, token, or signed media URL is stored.
- Wake fixes: both mvp-id regexes used Python-style `(?P<...>)` groups, which the Java engine rejects (an unknown inline modifier); the fixture caught the pulsembed one after the base-class one was fixed.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.onet.OnetIETest"` — 7 tests, 0 failures (including one harness case, the mvp form, both onet.pl paths, and the channel links).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,881 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 674 partial, 92 planned, 985 not started.

Next: file 45, `safari.py`.

**File 45 of 110 — `safari.py` (2026-10-01).** `SafariIE.kt` accounts for the three registered classes (plus the shared base):

- Translated and registered (3): `SafariIE` (the library/videos URL forms, the `data-reference-id`/`data-partner-id`/`data-ui-id` page search, and the Kaltura `mwEmbedFrame` URL construction dispatched to the port's `KalturaIE`), `SafariApiIE` (the chapter API JSON with the `library/view` to `videos` rewrite and the `natural_key` path, dispatching to `SafariIE` directly), and `SafariCourseIE` (the course book JSON `chapters` as child API entries with the `suitable` override).
- Manifest limits: the email/password login (`_perform_login`, the two Set-Cookie instances, the kaltura_session branch) is not translated, so `LOGGED_IN` is always false; the post-redirect URL re-match uses `followRedirects`; the transparent dispatches are direct calls. No cookie, token, or signed media URL is stored.
- The Safari tests reuse the port's Kaltura iframe fixture shape, so the Kaltura dispatch is verified end-to-end (including the HLS row from the playManifest data URL); the Kaltura multirequest probe is `runCatching` and tolerates the missing fixture.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.safari.SafariIETest"` — 7 tests, 0 failures (including two harness cases, the library data attributes, the API rewrite, and the course playlist).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,888 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 677 partial, 92 planned, 982 not started.

Next: file 46, `msn.py`.

**File 46 of 110 — `msn.py` (2026-10-01).** `MSNIE.kt` accounts for the one registered class:

- Translated and registered (1): `MSNIE` (the `assets.msn.com/content/view/v2/Detail` JSON for the video, webcontent, and article page types — the `externalVideoFiles` rows as one HLS row, one MPD row, or direct rows with format/size/height/width, the `closedCaptions` ttml tracks, the common metadata, and the transparent dispatches as child entries).
- Manifest limits: the third-party/webcontent dispatches become one child entry at the source URL (the port's transparent dispatch), so the page metadata is not merged; `release_timestamp`/`modified_timestamp`, `uploader_id`, `display_id`, and `tags` are dropped; `timestamp` folds into `uploadDate`; m3u8/mpd subtitles are not parsed. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.msn.MSNIETest"` — 8 tests, 0 failures (including one harness case, the third-party child entry, the webcontent typed failure, the article embeds, and the unsupported type).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,896 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 678 partial, 92 planned, 981 not started.

Next: file 47, `stacommu.py`.

**File 47 of 110 — `stacommu.py` (2026-10-01).** `StacommuIE.kt` accounts for the four registered classes:

- Translated and registered (4): `StacommuVODIE`, `StacommuLiveIE`, `TheaterComplexTownVODIE`, and `TheaterComplexTownPPVIE`. All four match their URL forms and fail typed `LoginRequired`.
- Manifest limits: both bases extend `WrestleUniverseBaseIE`, whose API needs the `token` cookie (or a Firebase email/password login) and whose stream endpoint is an RSA-OAEP encrypted exchange needing pycryptodomex; the port carries neither (the wrestleuniverse/naver/zingmp3 rule), so the wall message names both requirements. No API key, device id, token, or media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.stacommu.StacommuIETest"` — 5 tests, 0 failures (URL matching plus the four typed walls).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,901 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 682 partial, 92 planned, 977 not started.

Next: file 48, `tmz.py`.

**File 48 of 110 — `tmz.py` (2026-10-01).** `TMZIE.kt` accounts for the one registered class:

- Translated and registered (1): `TMZIE` (the JSON-LD VideoObject mapping — name/description/thumbnailUrl/uploadDate/duration/contentUrl — the `.cueVideoById` YouTube fallback, and the `twitter-tweet` blockquote link fallback).
- Manifest limits: the port's `JsonLd` has no `_json_ld` VideoObject walker, so the mapping is written in the extractor; the YouTube fallback dispatches one child entry at the watch URL (upstream passes a bare id); the Twitter dispatch is one child entry. No cookie, token, or signed media URL is stored.
- A small in-file helper was added and named per the rule: `elementByClass` (the tag-balanced `get_element_by_attribute` subset) for the Twitter blockquote.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.tmz.TMZIETest"` — 7 tests, 0 failures (including one harness case, the HLS contentUrl row, both fallbacks, and the typed no-video wall).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,908 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 683 partial, 92 planned, 976 not started.

Next: file 49, `agora.py`.

**File 49 of 110 — `agora.py` (2026-10-01).** `AgoraIE.kt` accounts for the four registered classes:

- Translated and registered (4): `WyborczaVideoIE` (the api-video JSON as standard/high rows with the p-height and one DASH row), `WyborczaPodcastIE` (the api/podcast JSON with the Polish month mapping for `uploadDate` and one audio row, plus the playlist form dispatching to `TokFMAuditionIE`), `TokFMPodcastIE` (the metadata list and the getSongUrl row with the random device id), and `TokFMAuditionIE` (the getSeries JSON and getPodcasts paging in offset steps of 30 as child entries with the mobile user-agent header).
- Manifest limits: the upstream `RetryManager` on an empty page is replaced by a plain stop; m3u8/mpd subtitles are not parsed; `series`/`episode`/`cast` are dropped. No cookie, token, or signed media URL is stored.
- A small in-file helper was added and named per the rule: `randomUuid` for the TokFM device id (the DPlay/TapTap shape), plus the `POLISH_MONTHS` map for `month_by_name(lang='pl')`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.agora.AgoraIETest"` — 8 tests, 0 failures (including one harness case, the Polish date, both TokFM paging paths, and the typed missing-podcast wall).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,916 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 687 partial, 92 planned, 972 not started.

Next: file 50, `ximalaya.py` — the 50th file, so this wake also runs the full cadence (`:shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks`, desktop separately, and the cross-target compiles).

**File 50 of 110 — `ximalaya.py` (2026-10-01).** `XimalayaIE.kt` accounts for both registered classes (plus the shared base):

- Translated and registered (2): `XimalayaIE` (the `m.ximalaya.com/tracks` JSON with the public `play_path_32`/`play_path_64` rows `vcodec: none`, the cover thumbnails with the 142 size, and the intro newline cleanup) and `XimalayaAlbumIE` (the `getTracksList` paging as child track entries with the album title, 100-page cap).
- Manifest limits: the VIP `mpay` path (the seeded filename decrypt and the RC4 `ep` URL params needing the embedded key) is not carried (the naver/zingmp3/abc rule); a VIP-only track fails typed, while a VIP track that also carries public play paths still yields them; `uploader_id`/`uploader_url`/`categories`/`like_count` are dropped. No cookie, token, or signed media URL is stored.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.ximalaya.XimalayaIETest"` — 6 tests, 0 failures (including one harness case, the VIP-only typed failure, the VIP-with-public-rows case, and the two-page album).
- **50th-file cadence:** `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1,922/0, ui 121/0, android-engine 41/0, port-manifest 25/0.
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 146 tests, 2 failed, 15 skipped: only the two known pre-existing owner-work failures (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory`, `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`); neither uses the extractor registry.
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 689 partial, 92 planned, 970 not started.

Next: file 51, `nekohacker.py`.

**File 51 of 110 — `nekohacker.py` (2026-10-01).** `NekoHackerIE.kt` accounts for the one registered class:

- Translated and registered (1): `NekoHackerIE` (the `playlist` element's `li[data-audiopath]` tracks as selectable `media` items with the track id/title/duration, the `srp_player_params` artwork thumbnail, the release date, and the no-playlist iframe handling).
- Manifest limits: the upstream playlist result maps to `media` items, so `album`/`track`/`artists`/`track_number` are dropped; the `url_result(url, 'Generic')` fallback becomes one child entry at the iframe src (GenericIE stays out); a Spotify embed fails typed. No cookie, token, or signed media URL is stored.
- Wake helpers named per the rule: `elementByClass` (tag-balanced `get_element_by_class`), `extractBalancedJson` (upstream `_search_json`), and `extractAttributes`.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.nekohacker.NekoHackerIETest"` — 6 tests, 0 failures (including one harness case, the two-track album, the Spotify typed failure, the other-embed child entry, and the no-embed failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,928 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 690 partial, 92 planned, 969 not started.

Next: file 52, `glomex.py`.

**File 52 of 110 — `glomex.py` (2026-10-01).** `GlomexIE.kt` accounts for both registered classes (plus the shared base):

- Translated and registered (2): `GlomexIE` (the video.glomex.com URL builds the player URL with the fixed integration id and calls `GlomexEmbedIE` directly) and `GlomexEmbedIE` (the integration-cloudfront API as one HLS row per m3u8 source, direct rows, the language tag on every row, the 960x540 image thumbnails, the geo-blocked error, and the iframe-player URL form with its `integrationId`).
- Manifest limits: the upstream `smuggle_url` origin is replaced by a direct `extractEmbed` call argument; a multi-video playlist maps to selectable `media` items; m3u8 subtitles are not parsed; the `_extract_embed_urls` generic discovery is not carried. No cookie, token, or signed media URL is stored.
- A wake fix: the test file's empty-video-list case was kept and the `no videos found` message verified; no source fix was needed this wake.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.glomex.GlomexIETest"` — 8 tests, 0 failures (including one harness case, the embed form, the multi-video playlist, the typed geo wall, the missing-integration failure, and the empty-video failure).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,936 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 692 partial, 92 planned, 967 not started.

Next: file 53, `rts.py`.

**File 53 of 110 — `rts.py` (2026-10-01).** `RTSIE.kt` accounts for the one registered class:

- Translated and registered (1): `RTSIE` (subclasses the port's `SRGSSRIE` — the `rts.ch/a/{id}.html?f=json/article` JSON for the video and audio forms, the streams rows with one HLS row via the SRG SSR akahd token, the `hds_sd`/`hls_sd` skips, the direct bitrate rows, the `media` rows joined to the `rtsww` download base, the SRG SSR block-reason check, and the article/redirect dispatches as child entries).
- Manifest limits: f4m streams are skipped (the port has no f4m helper); `_check_formats` HEAD probes are not carried; m3u8 subtitles are not parsed; `display_id` is dropped; `timestamp` folds into `uploadDate`. No cookie, token, or signed media URL is stored.
- Supporting change named per the rule: `srgssr/SrgssrIE.kt` was made `open`, its constructor now takes `ieKeyName`/`validUrl` defaults (the Audius shape), and `getMediaData`/`tokenize` became `protected` so the RTS subclass can call them; the SRGSSR behaviour and tests are unchanged.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.rts.RTSIETest"` — 6 tests, 0 failures (including one harness case, the audio base, the items article, and the urn article).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,942 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 693 partial, 92 planned, 966 not started.

Next: file 54, `zan.py`.

**File 54 of 110 — `zan.py` (2026-10-01).** `ZanIE.kt` accounts for the one registered class:

- Translated and registered (1): `ZanIE` (the play page is fetched, the geo error element maps to a typed `GeoRestricted(JP)`, and every other page fails typed `LoginRequired`).
- Manifest limits: the play API needs the session `csrf-token`/`vod-pct`/`live-player-token` meta values (absent for an anonymous visitor, so upstream itself raises a login requirement) and sends them with an `X-Csrf-Token` header the platform allowlist refuses (the streaks/sonyliv rule); the m3u8 DISPLAY-NAME resolution fixup, the multi-angle crop formats (ffmpeg args), and the detail-page metadata walk are not translated. No cookie, token, or signed media URL is stored.
- A small in-file helper was added and named per the rule: `elementByClass` (tag-balanced) for the geo error element.

Commands:

- `./gradlew --no-parallel :shared:core:jvmTest --tests "com.anydownlod.core.extract.zan.ZanIETest"` — 3 tests, 0 failures (URL matching, the typed token wall, and the typed geo wall).
- `./gradlew --no-parallel :shared:core:jvmTest` — 1,945 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 694 partial, 92 planned, 965 not started.

Next: file 55, `lifenews.py`.
