---
id: T-073
type: task
priority: P1
milestone: D4
tags: [task, engine, kmp, downloads]
---

# T-073 — Native HLS and DASH fragment downloaders

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

A media playlist (`.m3u8`) or MPD manifest downloads as one media file by fetching its fragments in order and concatenating them, with no FFmpeg. Today such a URL is classified as a direct file and the playlist text is saved. Covers E-08, E-09, E-10 (partial).

## Dependencies

- [T-068](T-068-Gate-jsless-youtube-four-hosts.md), [T-056](T-056-Http-request-port.md).

## Context the next session needs

Upstream: `downloader/hls.py`, `downloader/dash.py`, `downloader/fragment.py`, `extractor/common.py` `_parse_m3u8_formats`/`_parse_mpd_formats`. Translate the non-live, non-FFmpeg paths. Package `com.anydownlod.core.download`. Live playlists, `EXT-X-KEY` methods other than `AES-128`, SAMPLE-AES, and DRM (`hasDrm`) fail typed.

## Work

- `UrlClassifier`: `application/vnd.apple.mpegurl`, `application/x-mpegurl`, `audio/mpegurl`, and `application/dash+xml` become `MANIFEST`; `.m3u8`/`.mpd` URL tails are a hint only.
- `M3u8`: master playlist → `MediaFormat` list (`protocol = m3u8_native`, bandwidth, resolution, codecs, audio groups as upstream does); media playlist → fragment list (absolute URLs, byte ranges, `EXT-X-MAP` init segment, `EXT-X-KEY` AES-128 with IV or sequence number, `EXT-X-DISCONTINUITY` kept as concatenation).
- `Mpd`: `Period`/`AdaptationSet`/`Representation` → `MediaFormat` (`protocol = http_dash_segments`); `SegmentTemplate` with `$Number$`/`$Time$` and timelines, `SegmentList`, `SegmentBase` with `Initialization` ranges.
- `FragmentDownloader`: sequential fetch through the request port into the temp file; AES-128-CBC decrypt in Kotlin (common implementation, no platform crypto dependency in D4, unit-tested against known vectors); per-fragment retries bounded; progress as `fragmentIndex/fragmentCount` with bytes; `percent` only from the fragment ratio, marked as an estimate in `phase`; cancel stops between fragments and discards.
- Engine: `MANIFEST` classification runs the parser and the selector (`best` by bandwidth) then the fragment downloader; extracted `MediaFormat`s with these protocols use the same path.
- Fixtures: a local `HttpServer` serving a master + media playlist with three TS fragments, an fMP4 variant with init segment, an AES-128 variant, and an MPD with `SegmentTemplate`; assert the concatenated bytes equal the source file.

## Acceptance criteria

- [x] Parser tests translated from upstream `test/test_InfoExtractor.py` m3u8/mpd cases (Unlicense notice) pass on synthetic fixtures.
- [x] Fragment downloader assembles byte-exact files for TS, fMP4, and AES-128 fixtures; cancel mid-playlist leaves no file.
- [x] A `.m3u8` URL no longer saves playlist text; the D2 direct-file tests stay green.
- [x] Live playlists and unsupported key methods fail typed; manifest updated.

## Evidence / notes

Done 2026-09-24.

- New `com.anydownlod.core.download` package:
  - `M3u8.kt` — master playlists to `MediaFormat` variants (`m3u8_native`, bandwidth, resolution, codecs, `EXT-X-MEDIA` audio groups), media playlists to fragments (absolute URLs, `EXT-X-BYTERANGE` with implicit continuation, `EXT-X-MAP` init segment, `EXT-X-KEY` AES-128 with explicit IV or media sequence, `EXT-X-DISCONTINUITY` as concatenation). Live playlists (`#EXT-X-ENDLIST` missing) and key methods other than AES-128/NONE fail typed.
  - `Mpd.kt` — `Period`/`AdaptationSet`/`Representation` to `http_dash_segments` formats; `SegmentTemplate` with `$Number$`/`$Time$` timelines, `SegmentList` media ranges, and `SegmentBase` initialization ranges. `ContentProtection` (DRM) and `type="dynamic"` fail typed.
  - `Aes128Cbc.kt` — AES-128-CBC decryption in common Kotlin (no platform crypto), tested against the NIST SP 800-38A CBC vector and an openssl-generated PKCS7 fragment vector.
  - `FragmentDownloader.kt` — sequential fetch through the request port into one temp file: init segment first, per-fragment ranges, decryption, bounded retries, progress as `fragmentIndex/fragmentCount` with bytes and `phase = "fragments i/N (estimated)"`, cancellation between fragments.
- `UrlClassifier` now returns `MANIFEST` for `application/vnd.apple.mpegurl`, `application/x-mpegurl`, `audio/mpegurl`, and `application/dash+xml`; a `.m3u8`/`.mpd` URL tail is a hint only when the content type is missing. Engine and route classifiers pass the URL.
- Engine `MANIFEST` branch: parse, pick the best variant by bandwidth, concatenate its fragments into one artifact (`.ts` for HLS, `.mp4` for DASH), typed `UNSUPPORTED_FORMAT` for live/DRM/unsupported keys, cancel discards the temp file. No FFmpeg, MediaMuxer, AVFoundation, or merge step.
- Tests (+24, core JVM 273 → 297): `M3u8Test` (5), `MpdTest` (5), `Aes128CbcTest` (3), `FragmentDownloaderTest` (7), and `ManifestEngineDownloadTest` (4) against a local `HttpServer` serving a master + media playlist, an AES-128 variant, and an MPD `SegmentTemplate`. The `.m3u8` run asserts the artifact is the concatenated fragment bytes and contains no `#EXTM3U`; the cancel run asserts no published file.
- Verification: `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :shared:core:compileKotlinIosSimulatorArm64 :shared:core:compileTestKotlinWasmJs :apps:web:compileKotlinWasmJs :apps:android:compileDebugKotlin :tools:port-manifest:check` — all green. `JavaNetChunkedDownloadTest.a403OnALaterChunkIsUnavailableOrPrivate` (known flake) failed once in a full run and passed on rerun.
- Manifest: three `downloader/*` partial entries (`hls.py`, `dash.py`, `fragment.py`) with upstream paths, Kotlin files, scopes, and T-073; `:tools:port-manifest:run` regenerated the coverage block. No live network was used; every fixture URL, fragment, and AES key is synthetic.
