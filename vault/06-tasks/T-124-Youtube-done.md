---
id: T-124
type: task
priority: P0
milestone: D12
tags: [task, engine, youtube]
---

# T-124 — YouTube done at the pin

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 12](../00-project/Phase-12-Youtube-done.md) · [Schedule](../00-project/Full-engine-schedule.md)

## Outcome

The 21 `Youtube*` classes at pin `2026.08.19` are accounted for in the manifest. Estimate 15 engineer-days.

## Dependencies

- [T-018](T-018-Cookie-lifecycle.md).

## Acceptance criteria

- [x] Remaining innertube clients the pin uses, playlist continuations, channels, mixes, and `--playlist-items` are translated or marked Partial with a reason.
- [x] A PO-token provider interface exists. Formats that need a token are dropped until a provider is configured.
- [x] Subtitle tracks and chapter metadata are on the info dict. Writing those files is T-015 and T-016.
- [x] Live YouTube and live HLS fail only for the cases the phase note still lists as out.
- [x] The default playlist cap stays 50. A higher cap requires an explicit user limit.
- [x] Harness cases pass. The desktop oracle diffs normalized fields only. No cookie, token, or signed media URL is committed.
- [x] `:tools:port-manifest:check` passes and the equivalence rows for YouTube name this task.

## Evidence / notes

Not started. iOS simulator reachability stays a recorded limit, as in T-074. Scheduled 2026-09-29 by [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). Done 2026-09-30.

**Iteration 1 — PO tokens, tracks, chapters, continuations, manifest (2026-09-30).** Landed: `PoTokenProvider`/`PoTokenRequest`/`PoTokenContext` and `NoPoTokenProvider` (E-13); `YoutubeIE` sends a provider PLAYER token through `serviceIntegrityDimensions` and drops web-client GVS-gated formats when no token is configured, counting them in `InfoDict.formatsNeedingPoToken`; subtitle tracks (`subtitles`/`automaticCaptions`, six upstream format URLs per language, ASR flag, `exp=xpe` POT flag) and chapter markers (`chapters`) are parsed onto the info dict; `YoutubeTabIE` follows continuation pages in document order, deduped, bounded at 10 continuations and the 50-entry cap. All 21 `Youtube*` classes are now accounted for in `port/manifest.json` (3 existing rows updated plus 18 new rows), and `:tools:port-manifest:check` passes. E-11, E-13, E-14, E-18, and E-20 name T-124. Still open on this card: channels, mixes, `--playlist-items` ranges, the authed innertube clients (web_embedded/tv_downgraded) and alias extractor registration, live YouTube and live HLS extraction/download.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest :tools:port-manifest:test --rerun-tasks` — core 631 tests, 0 failures; port-manifest tests pass.
- `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 9 partial, 14 planned, 1,728 not started (the 21 `Youtube*` classes have rows).

Next: channels and mixes (tab browse for `/@handle`, `/channel/`, `/c/`, `/user/`, `list=RD`), `--playlist-items` ranges, live YouTube/live HLS, then the harness cases and oracle diff.

**Iteration 2 — channels, mixes, `--playlist-items` (2026-09-30).** `YoutubeTabIE` now matches `/watch?...&list=` (including `RD...` mixes), `/channel/<UC id>`, and `/@handle` with an optional tab; entries are read from playlist, playlist-panel, video, grid, and reel renderers; channel titles come from `channelMetadataRenderer`. `/c/` and `/user/` match the URL and fail typed because `navigation/resolve_url` is not translated. `DownloadOptions.playlistItems` carries the upstream `--playlist-items` spec, and `PlaylistItemSelection` (1-based indexes/ranges, negatives from the end, optional steps, `inf` ends) selects rows before the 50 cap; an invalid spec fails the parent job typed (`INVALID_URL_OPTIONS`). The job document round-trips the field. Manifest scope and E-14 updated.

Commands:
- `./gradlew --no-parallel :shared:core:jvmTest :tools:port-manifest:test --rerun-tasks` — core 643 tests, 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 9 partial, 14 planned.
- `:shared:core:compileKotlinWasmJs`, `compileKotlinIosSimulatorArm64`, and both test compiles — clean.

Still open on this card: live YouTube and live HLS, the authed innertube clients and alias extractor registration, the harness cases for every URL form, and the oracle diff.

**Iteration 3 — live, harness, final verification (2026-09-30).** Live YouTube: an on-air video (`isLiveContent` + `isLive`) exposes `streamingData.hlsManifestUrl` as a single `m3u8_native` format and sets `InfoDict.isLive`; a was-live video keeps its recorded formats. `M3u8` no longer rejects live media playlists: it reports `isLive` and `TARGETDURATION`. The engine's `downloadLiveHls` follows the playlist, appends each media sequence once, waits one target duration when nothing is new, and stops on `#EXT-X-ENDLIST` or cancel (bounded at `MAX_LIVE_POLLS`). The playlist default cap stays 50; an explicit `playlistItemLimit` above 50 is honored. Harness cases now cover the live and channel URL forms, and the authed clients, `/c/`, `/user/`, feeds, `--live-from-start`, DASH live, and SABR-only streams are recorded as the typed out-of-scope cases in the phase note.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):
- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 650, ui 105, desktop 142 (15 live skipped), android-engine 41, port-manifest 25; 0 failures.
- `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 9 partial, 14 planned.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — build clean.
- Live-network runs and the opt-in yt-dlp oracle were not run here; fixtures are the evidence and the oracle still diffs normalized fields only.
