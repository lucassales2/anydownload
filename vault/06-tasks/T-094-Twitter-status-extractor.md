---
id: T-094
type: task
priority: P0
milestone: D7
tags: [task, engine, extractor, twitter]
---

# T-094 — TwitterIE for public status videos

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 7](../00-project/Phase-7-X-Twitter.md) · [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md) · [Ytdlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

`TwitterIE` matches `x.com`, `twitter.com`, `mobile.x.com`, and `mobile.twitter.com` `/user/status/<id>` URLs (plus upstream's `www.`/`m.` and `i/web` variants). It extracts a public status through the public guest lookup into the status metadata plus the status's videos, grouped by stable media id. Photos are not media. Photo-only, protected, and deleted posts fail typed. This task downloads nothing.

## Dependencies

- [T-093](T-093-Phase-6-verification.md) — Phase D6 is verified.
- [T-057](T-057-Extractor-core.md) — `InfoExtractor`, `InfoDict`, typed errors, registry.
- [T-059](T-059-Extractor-test-harness.md) — the fixture transfer and `_TESTS`-style harness.
- [T-073](T-073-Native-hls-dash-downloaders.md) — the manifest format path a `.m3u8` variant uses.

## Context the next session needs

Upstream `yt_dlp/extractor/twitter.py` at tag `2026.08.19`, commit `3a08beaf031ab68f966401ead017ac81fe8486cf`, is the source. `TwitterIE._VALID_URL` is `TwitterBaseIE._BASE_REGEX + r'(?:(?:i/web|[^/]+)/status|statuses)/(?P<id>\d+)(?:/(?:video|photo)/(?P<index>\d+))?'`; the port drops the `/video` and `/photo` suffixes (photos are out). Upstream's default API is GraphQL with a hard-coded bearer; the port uses the public syndication lookup instead, which is cookie-free. Do not copy `_AUTH`/`_LEGACY_AUTH` and do not send a cookie. `_MEDIA_ID_RE` in upstream is `_video/(\d+)/`; use it as the fallback media-id source when the JSON has no `id_str`/`id`.

## Work

- New `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/twitter/TwitterIE.kt` extending `InfoExtractor` with the URL subset above and `matchId` returning the status id.
- Port-only model for several selectable media in one source: `InfoDict.media: List<InfoMedia> = emptyList()` and `InfoMedia(mediaId, title, duration, thumbnails, formats)` in `InfoDict.kt`. `FormatChoices.from(info)` unions `info.formats` with `info.media.flatMap { it.formats }`. Other extractors leave `media` empty and behave as before.
- Guest lookup: `GET https://cdn.syndication.twimg.com/tweet-result` with `id` and the derived `token`. Derive `token` as upstream `_generate_syndication_token` does (JS-style base-36 of `(id / 1e15) * π` with `0` and `.` removed) in a small helper. Send `User-Agent: Googlebot` like upstream; the web extension may drop it (MV3), which is a recorded web gap and must not be required for the fixture path.
- Parse the syndication JSON: status metadata (`id`, text/`full_text`, user name and screen name, `created_at`, `possibly_sensitive`, counts) into `InfoDict`; `mediaDetails` entries with `video_info.variants` into `InfoMedia`, one per distinct media id. Ignore photos (`type == "photo"`), quoted tweets, and cards.
- Variants: `.m3u8` becomes one format routed through the D4 manifest path; other variants become direct formats with `tbr` from `bitrate`/`bit_rate` and `width`/`height` parsed from the `/WxH/` path segment. Sort with the existing format sorter.
- Typed failures: a response with no video media → `ExtractionError.NoFormats`; a protected or login-only payload/status → `ExtractionError.LoginRequired`; a deleted or not-found response → `ExtractionError.Unavailable`. No cookie or login is attempted.
- Register nothing on a host in this task; T-096 through T-099 do the wiring. Update `port/manifest.json` with `TwitterIE` as partial (upstream path `yt_dlp/extractor/twitter.py`, the new Kotlin file, scope, task T-094) and rerun the coverage generator.

## Tests

- `TwitterIETest` with `FixtureHttpTransfer`: all four host forms and `i/web/status/<id>` match; a profile URL does not; `matchId` returns the status id.
- Single-video fixture: metadata fields and one `InfoMedia` with the stable id, duration, thumbnails, and formats.
- Two-video fixture with a photo sibling: two groups in status order, distinct ids, no photo group.
- HLS variant fixture: the `.m3u8` variant is present as a manifest-backed format; direct variants carry `tbr` and dimensions.
- Photo-only → `NoFormats`; protected → `LoginRequired`; deleted (404) → `Unavailable`.
- The transfer log shows only the syndication request; no media URL is fetched (an unmatched request would throw `FixtureMissingException`).

## Acceptance criteria

- [x] `TwitterIE` matches the four host forms and extracts redacted fixtures into metadata plus videos grouped by stable media id.
- [x] Photos never become media; photo-only, protected, and deleted posts fail typed with the errors above.
- [x] Only the guest lookup is requested; no media GET and no download happens in this task.
- [x] Fixtures are synthesized or redacted: no real handle/status text, guest token, cookie, or `twimg.com` URL; media addresses use `*.example` hosts.
- [x] `port/manifest.json` has the `TwitterIE` partial entry and the generated coverage block is regenerated; `:tools:port-manifest:check` is green.
- [x] `:shared:core:jvmTest` and `:shared:core:iosSimulatorArm64Test` pass; `shared/core` still has no `ProcessBuilder`.

## Evidence / notes

Done 2026-09-25.

What landed:

- `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/twitter/TwitterIE.kt`: the upstream `TwitterIE` subset for `x.com`, `twitter.com`, `mobile.x.com`, `mobile.twitter.com` `/user/status/<id>` plus the `www.`/`m.`, `i/web`, and `statuses` forms; `/photo/<n>`, `/video/<n>`, profile, and `t.co` URLs do not match. The only request is the public `cdn.syndication.twimg.com/tweet-result` lookup with the Googlebot user agent and the per-request derived token. The token derives through a port of `jsinterp.js_number_to_string`, is used in memory for that request, and is never stored.
- Parsing: `mediaDetails` videos become `InfoMedia`, grouped by `id_str`/`id` or the `_video/(\d+)/` URL segment; photos, quoted tweets, and cards are ignored. MP4 variants carry `formatId`/`tbr`/width/height from the URL; `.m3u8` variants become one `m3u8_native` format. Metadata covers id, title, description, uploader, channel id, upload date, view count, thumbnails, and age limit.
- Typed failures: no video media → `NoFormats`; a protected payload (or HTTP 401) → `LoginRequired`; deleted/not-found (404) → `Unavailable`; a non-object payload → `Unavailable`. No cookie or authorization is attempted.
- `InfoDict` gains the port-only `media: List<InfoMedia>` model; `FormatChoices.from` unions `info.formats` with the listed media formats so T-095's Edit panel sees the video heights. `shared/core/NOTICE.md` gained the T-094 translation notice.
- `port/manifest.json` lists `TwitterIE` as partial with the upstream path and `shared/core/NOTICE.md`-tracked file; the coverage block in the [equivalence note](../01-product/Ytdlp-equivalence.md) regenerated to 4 partial and 1,747 not started.
- `TwitterIETest` (commonTest, inline synthesized JSON): URL matching for the four hosts plus `www`/`m`/`i/web`/`statuses`; profile, photo, Spaces, broadcast, and `t.co` non-matches; single-video metadata, media, formats, and thumbnails; two videos plus a photo sibling and a quoted video grouped by stable id; `FormatChoices` union; photo-only; protected payload; deleted 404; one-request discipline (no cookie/authorization); token determinism and shape. No exact token value is written; the algorithm was cross-checked outside the repo against upstream's Python helper on 20,000 synthetic ids with 0 mismatches.

Verification run 2026-09-25:

- `./gradlew :shared:core:jvmTest --tests "com.anydownlod.core.extract.twitter.TwitterIETest"` — 10 tests, 0 failures.
- `./gradlew :shared:core:jvmTest` — 475 tests, 0 failures (10 TwitterIE).
- `./gradlew :shared:core:iosSimulatorArm64Test` — 438 tests, 0 failures (10 TwitterIE).
- `./gradlew :shared:core:compileKotlinWasmJs :shared:ui:compileKotlinJvm` — BUILD SUCCESSFUL.
- `./gradlew :tools:port-manifest:run :tools:port-manifest:check` — 1,751 upstream classes, 0 ported, 4 partial, 0 planned, 1,747 not started; ok.
- No `ProcessBuilder` or `Runtime` in `shared/core`; the tests' fixture transfer shows exactly one syndication request, and an unmatched media GET would fail the fixture.
- Hygiene: no guest token, cookie, signed media URL, private URL, or media file added; no host registry was wired (T-096–T-099 own that).
