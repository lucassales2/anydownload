---
type: phase
status: done
milestone: D17
tags: [project, engine, extractors]
---

# Phase 17 — Remaining large sites

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [Plan](Phase-17-22-Plan.md) · [File list](Catalog-D17-D21.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-09-30. 46 engineer-days.** Depends on D16, which is Done. The 23 extractor files still in the 25–80 KB decimal band after D16’s nine large files, largest first, one file per wake. The ordered list is fixed in [Catalog D17–D21](Catalog-D17-D21.md): `adobepass.py`, `dplay.py`, `nbc.py`, `cbc.py`, `niconico.py`, `brightcove.py`, `pbs.py`, `nhk.py`, `zdf.py`, `rai.py`, `nrk.py`, `weverse.py`, `pornhub.py`, `ard.py`, `zattoo.py`, `openrec.py`, `xhamster.py`, `neteasemusic.py`, `panopto.py`, `tvp.py`, `vrt.py`, `kaltura.py`, `gamejolt.py`. Session prompt: [Phase 17–22 loop](Phase-17-22-Loop-prompt.md).

## Task

[T-126](../06-tasks/T-126-Remaining-large-sites.md).

## Evidence

All 23 files are translated or accounted for, one file per wake, from pin `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`). The per-file evidence, harness results, and command lines are in [T-126](../06-tasks/T-126-Remaining-large-sites.md) (“File N of 23 — …”).

Commands (phase-final, 2026-09-30):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — core 1,023, ui 121, android-engine 41, port-manifest 25, all 0 failures. Desktop 146 tests: 2 failures, 15 skipped; both are pre-existing owner-work/flaky tests (`FreshWindowsInstallWithoutYtDlpTest` phone-UI scroll semantics and `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled` cleanup timing), neither of which uses the extractor registry.
- Cross-target compiles (`:shared:core` wasm main+test and iOS main+test, `:shared:ui` iOS main, `:apps:web` wasm, `:apps:android:compileDebugKotlin`) — BUILD SUCCESSFUL.
- `./gradlew --no-parallel :tools:port-manifest:run` then `./gradlew --no-parallel :tools:port-manifest:check` — ok: 1,751 upstream classes, 0 ported, 200 partial, 88 planned, 1,463 not started.

## Limits

- Four files are only login/account/DRM walls (`weverse.py`, `zattoo.py`, `neteasemusic.py`, `vrt.py`): their URL forms match and fail typed, with URL-matching and typed-failure tests instead of passable harness cases, and the reason is one sentence on every manifest row.
- No new extractor files were added beyond the per-file translations; unmatched URLs still fall through to the desktop CLI / Android Chaquopy until T-131.
- The pin did not move; no upstream Python was committed.
