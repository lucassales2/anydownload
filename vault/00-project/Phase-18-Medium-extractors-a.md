---
type: phase
status: done
milestone: D18
tags: [project, engine, extractors]
---

# Phase 18 — Medium extractors, first half

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-10-01.** Depends on D17. The first 110 extractor files in the 8–25 KB decimal band, one file per wake, larger files first. The ordered list is fixed in [Catalog D17–D21](Catalog-D17-D21.md). Session prompt: [Phase 17–22 loop](Phase-17-22-Loop-prompt.md).

## Task

[T-127](../06-tasks/T-127-Medium-extractors-a.md) — done. Catalog D18 (`tumblr.py` first, `cbs.py` last) is fully accounted for; the per-file evidence is in the task note.

## Done — commands, coverage, limits

Phase-closing cadence (2026-10-01):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL: core 1,603/0, ui 121/0, android-engine 41/0, port-manifest 25/0.
- `./gradlew --no-parallel :apps:desktop:test --rerun-tasks` — 146 tests; only the two known pre-existing failures recorded in T-126 (`FreshWindowsInstallWithoutYtDlpTest.settingsShowsNotFoundAndTheFailedDownloadLandsInHistory`, the flaky `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled`) plus a flaky `DesktopSpotifyDownloadGateTest` case that passes in isolation (ffmpeg media-toolkit timing, not extractor code).
- Cross-target compiles: `:shared:core` wasm main+test, `:shared:core` iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 569 partial, 92 planned, 1,090 not started.

Limits:

- Wall-only files (`anvato`, `nebula`, `zingmp3`, `digitalconcerthall`, the `vice` locked path, `douyutv`, and the `senategov`/`cspan`/`mtv` auth paths) match and fail typed, with the one-sentence reason in the manifest.
- An m3u8 or mpd URL is one row, so manifest subtitles are not parsed; the port carries no cookies, tokens, or signed media URLs; upstream `_WORKING = False` classes are translated as partial.
- One helper was added in the phase: `sha512Hex` (`anydownload`) for the public Vice preplay signature, with known-vector tests.
