---
id: T-108
type: task
priority: P0
milestone: D8
tags: [task, engine, youtube]
---

# T-108 — YouTube playlist subset

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

A `/playlist?list=` fixture becomes entries the T-107 expander can turn into child jobs. The children download as ordinary YouTube videos.

## Dependencies

- [T-107](T-107-Playlist-expansion.md).

## Context the next session needs

`YoutubeIE` already handles a single video. Upstream `yt_dlp/extractor/youtube/_tab.py` at pin `2026.08.19` also handles channels, mixes, and continuations. This task translates only the playlist URL and the first browse payload needed to fill the cap. The port manifest and `NOTICE.md` must list the new module as partial.

## Work

- Add `YoutubeTabIE` for `youtube.com/playlist?list=` and `music.youtube.com/playlist?list=` only. Match after `YoutubeIE` so a watch URL with a list parameter stays a single video.
- From a redacted fixture, read video id and title into entries. Stop at the cap. Do not call a continuation unless the fixture's first page has fewer videos than the cap and the fixture itself contains the next page. Do not invent entries.
- A playlist that is private or missing fails the parent typed, with no children and no cookie.
- Register the extractor on desktop, Android, iOS, and web next to `YoutubeIE`. A child URL is a watch URL, so the existing YouTube download path runs it. No `yt-dlp` process for this URL.
- Update `port/manifest.json`, regenerate the coverage block, and add the Unlicense notice.

## Tests

- Harness: the fixture playlist yields the expected ids and titles, in order, and ignores a channel-shaped URL.
- Engine: two fixture entries become two child jobs; downloading a child uses the single-video path and writes one file.
- A private fixture fails the parent and creates no child.

## Acceptance criteria

- [x] `YoutubeTabIE` is partial in the manifest, with the upstream path, pin, and Kotlin file.
- [x] A fixture playlist expands to child jobs and one child downloads through `YoutubeIE`.
- [x] Channel, mix, cookie, and live URLs are not handled here.
- [x] No `yt-dlp` process starts for a matched playlist URL.

## Evidence / notes

2026-09-29.

- New `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/youtube/YoutubeTabIE.kt`: `/playlist?list=` only (`youtube.com`, `www`, `m`, `music`; `list=RD...` mixes and watch URLs with a `list` parameter do not match). One innertube `browse` request (`browseId = VL<listId>`) with the upstream `web` client context/headers, flat `playlistVideoRenderer` id/title pairs in document order, first page only, capped at `MAX_PLAYLIST_ENTRIES = 50`. A private/empty result throws `ExtractionError.Unavailable`; no entries are invented and no continuation is requested.
- Redacted fixtures: `shared/core/src/commonTest/resources/fixtures/youtube-playlist/playlist_page.json` (3 entries) and `playlist_private.json` (alert, no videos). Both contain no `googlevideo`/`visitorData`/continuation/`ytimg` data, asserted by test.
- Registered after `YoutubeIE` on all four hosts: `AndroidExtractors`, `IosExtractors`, desktop `Main.kt`, and web `WebAppGraph.kt`. A matched playlist URL goes to the shared engine (`ExtractorRegistry.suitableFor` first-match; Android's classifier returns `KOTLIN` before any probe), so no `yt-dlp` process is started; child watch URLs keep the existing `YoutubeIE` path.
- Manifest: `YoutubeTabIE` added to `port/manifest.json` (upstream `yt_dlp/extractor/youtube/_tab.py`, partial, T-108, 2026-09-29); `:tools:port-manifest:run` regenerated the coverage block in `vault/01-product/Ytdlp-equivalence.md`; `shared/core/NOTICE.md` gained the T-108 Unlicense notice. `CaseFieldValues` now exposes `entries` so the harness can assert dotted entry paths.

Verification run:

- `./gradlew :tools:port-manifest:validatePortManifest :shared:core:jvmTest --tests "com.anydownlod.core.extract.youtube.YoutubeTabIEFixturesTest" --tests "com.anydownlod.core.extract.youtube.YoutubePlaylistEngineTest"` — BUILD SUCCESSFUL; fixtures 5/5 (harness case with ordered ids/titles, watch entries, private typed failure, playlist-only matching, no signed/session fixture data), engine 1/1 (browse + two watch pages + two player requests + two media GETs, two files `Fixture One.mp4`/`Fixture Two.mp4`, parent COMPLETED with no artifact).
- Regression: `CHROME_BIN=... ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :shared:ui:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:web:wasmJsBrowserTest :apps:android:compileDebugKotlin :tools:port-manifest:validatePortManifest` — BUILD SUCCESSFUL; core jvm 514, core wasm 460, core iOS 471, ui jvm 91, ui iOS 8, android-engine-tests 26, web wasm 6, all 0 failures. `:apps:desktop:cleanTest :apps:desktop:test` alone — 131 tests, 0 failures.
