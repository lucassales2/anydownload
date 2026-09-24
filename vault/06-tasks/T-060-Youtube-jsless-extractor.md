---
id: T-060
type: task
priority: P0
milestone: D4
tags: [task, engine, youtube]
---

# T-060 — YouTube extractor, stage 1: JS-less `visionos` client

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md)

## Outcome

`YoutubeIE` in shared Kotlin turns a single-video URL into an `InfoDict` with metadata and the formats the `visionos` innertube client returns, without any JavaScript. No download in this task.

## Dependencies

- [T-057](T-057-Extractor-core.md), [T-059](T-059-Extractor-test-harness.md).

## Context the next session needs

Upstream at the pin: `extractor/youtube/_base.py` (1,350 lines; `INNERTUBE_CLIENTS`, `visionos` = `clientName VISIONOS`, `clientVersion 1.02`, `INNERTUBE_CONTEXT_CLIENT_NAME 101`, `REQUIRE_JS_PLAYER: False`), `extractor/youtube/_video.py` (4,592 lines; `_DEFAULT_JSLESS_CLIENTS = ('visionos',)`). Translate only the video path for one client. Package `com.anydownlod.core.extract.youtube`.

A player response contains signed `googlevideo` URLs. Fixtures must be synthesized with `https://rr1---sn-example.googlevideo.example/videoplayback?...` style hosts and fake parameters. Never record a real player response into the repo.

## Work

- `_VALID_URL` subset: `youtube.com/watch?v=`, `youtu.be/<id>`, `youtube.com/shorts/<id>`, `youtube.com/embed/<id>`, `youtube.com/live/<id>`, `youtube-nocookie.com/embed/<id>`, `music.youtube.com/watch?v=`. Everything else (playlists, channels, `@handle`, search) is `UnsupportedUrl` in D4. Video id is 11 characters from the upstream class.
- Innertube `player` request: `POST https://www.youtube.com/youtubei/v1/player?prettyPrint=false` with the `visionos` context from `_base.py`, `videoId`, `contentCheckOk`, `racyCheckOk`, the client headers, and the `playbackContext` fields upstream sends for that client. No API key beyond what upstream sends at the pin. No visitor data persistence.
- Parse `playabilityStatus` to `ExtractionError`: `LOGIN_REQUIRED`, `AGE_VERIFICATION`/`age_limit`, `UNPLAYABLE`, `ERROR` with reasons for private/removed/geo, `LIVE_STREAM_OFFLINE`. Live videos (`isLive`, `isLiveContent` with `hlsManifestUrl` only) fail typed as not supported in D4.
- `streamingData.formats` and `adaptiveFormats` → `MediaFormat`: `itag` → `formatId`, `mimeType` → `ext`/`vcodec`/`acodec` via `parseCodecs`, `bitrate`/`averageBitrate`, `width`/`height`/`fps`, `contentLength`, `audioQuality`, `audioSampleRate`, `quality`/`qualityLabel`, `url`. Formats with `signatureCipher` and no `url`, and any `n` parameter needing a transform, are **dropped** in this stage as upstream does without a runtime; count them so the engine can report "N more formats need the JavaScript runtime". Set `downloaderOptions.httpChunkSize` as upstream does for this client.
- `videoDetails` and `microformat.playerMicroformatRenderer` → title, channel, channelId, duration, viewCount, description, uploadDate, thumbnails (sorted by size), `isLive`, `ageLimit`.
- Harness cases: translate the first upstream `_TESTS` entry (`YE7VzlLtp-4`, Big Buck Bunny) as the live case; fixture cases from synthesized player JSON for a normal video, a login-required video, an age-gated video, a private video, a removed video, a live stream, and a response with only ciphered formats.
- Manifest: `youtube/_base.py` partial, `youtube/_video.py` partial (single video, visionos).

## Acceptance criteria

- [x] Fixture cases pass for every URL form and every typed failure above.
- [x] Live case passes with `-PliveExtractorTests=true` on the public Big Buck Bunny URL; evidence records the date and the format count, never the URLs returned.
- [x] No fixture in the repo contains a real `googlevideo.com` host, visitor id, or token (grep in the test).
- [x] Notice headers and manifest name both upstream files at the pin.

## Evidence / notes

Done 2026-09-24.

- New `com.anydownlod.core.extract.youtube.YoutubeIE` with the Unlicense header naming `youtube/_base.py` and `youtube/_video.py` at tag `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`): the `visionos` client values (name 101, version 1.02, Apple/visionOS fields, upstream UA), the `youtubei/v1/player?prettyPrint=false` POST through `ExtractorHttp.downloadJson`, `playbackContext`/`contentCheckOk`/`racyCheckOk`, playability mapping (`LOGIN_REQUIRED` → `LoginRequired`, age statuses/reasons → `AgeRestricted`, private/removed/`UNPLAYABLE`/`ERROR`/`LIVE_STREAM_OFFLINE` → `Unavailable`), live rejection, `streamingData` → `MediaFormat` (itag, mimeType codecs preserving case, `http_chunk_size`, tbr from averageBitrate/bitrate, single-stream container), and `videoDetails`/`microformat` metadata with thumbnails sorted by area. Signature-cipher and `n`-challenged formats are dropped and counted in the new port-only `InfoDict.formatsNeedingJs`.
- The watch-page visitor id is fetched per call and sent as `X-Goog-Visitor-Id` for that request only (upstream `_initial_extract`/`generate_api_headers`); it is never persisted, logged, or put in an `InfoDict`. The first live attempt without it returned `LOGIN_REQUIRED` ("Sign in to confirm you're not a bot"); with it the live run succeeded.
- `ExtractorUtils.parseCodecs` now preserves the codec case from `mimeType` (upstream does not lowercase `avc1.42001E`).
- Tests: `YoutubeIETest` 12 fixture cases — metadata/format mapping (3 kept, 2 dropped), 10 supported URL forms, 6 unsupported URL shapes, login/age/private/removed/offline/error/live typed failures, ciphered-only `NoFormats` with the JavaScript-runtime message, the visitor header reaching the player request, fixture purity (`.example` hosts, no real hosts/visitor), harness-shape case, and the missing-fixture message. `YoutubeLiveTest` is the opt-in live case.
- Live evidence (2026-09-24): `./gradlew :shared:core:jvmTest --tests "com.anydownlod.core.extract.youtube.YoutubeLiveTest" -PliveExtractorTests=true` → BUILD SUCCESSFUL; stdout `live youtube: formats=27, needsJs=0, titleLength=14`. No media URLs, visitor ids, or tokens were recorded.
- Fixture grep: the only `googlevideo.com` strings in the repo are the T-059 redaction-test inputs (`rr1---sn-realhost.googlevideo.com` with `SECRET*` placeholder values) and their assertions; the YouTube fixtures use `cdn.fixtures.example.net`, `i9.ytimg.example`, and the synthetic `FIXTURE_VISITOR`, and the test asserts that.
- 2026-09-24 (T-062 oracle follow-up): the desktop differential found and fixed `isDrc` → `-drc` format ids, URL `xtags` `sr=1` → `-sr` format ids, and the watch-page `uploadDate`/`isFamilyFriendly`/`og:restrictions:age` fallbacks; `YoutubeIETest` covers both and the live differential now reports 27/27 formats with 0 diffs.
- Manifest: `youtube/_base.py` and `youtube/_video.py` added as partial `core` entries, `YoutubeIE` moved to partial with its Kotlin file; `./gradlew :tools:port-manifest:run` regenerated the coverage block (0 ported, 2 partial, 0 planned, 1,749 not started); `NOTICE.md` records both upstream paths.
- Verification: `./gradlew :shared:core:jvmTest` → 237 tests, 0 failures; `:apps:desktop:test` and `:apps:android-engine-tests:test` green; `:shared:core:iosSimulatorArm64Test` green; `:shared:core:compileTestKotlinWasmJs`, `:apps:web:compileKotlinWasmJs`, `:apps:android:compileDebugKotlin` green; `:tools:port-manifest:check` green.
