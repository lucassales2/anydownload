---
id: T-047
type: task
priority: P0
milestone: D3
tags: [task, desktop, engine]
---

# T-047 — Desktop routes a matching page through Kotlin

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

A desktop HTML fixture with one media element downloads without `ProcessBuilder`. Every URL the generic subset does not resolve still uses `YtDlpCliEngine`.

## Dependencies

- [T-046](T-046-Engine-uses-generic-extractor.md).
- D1 CLI and D2 `DesktopRoutingEngine` stay.

## Work

- Extend the desktop route so a page the shared extractor resolves is owned by `HttpDownloadEngine`.
- Unresolved HTML and other site URLs keep the CLI path, including missing-tool behavior.
- Direct files still never spawn a process.
- Settings still report yt-dlp and ffmpeg. One honest line: a simple media page does not need them; other sites do.
- Jobs still persist through the existing JSON store.

## Acceptance criteria

- [x] Desktop tests: fixture HTML completes with no process; a non-matching site URL still reaches the CLI fake; direct file still skips the CLI.
- [x] Cancel of the HTML-backed job leaves no completed file.
- [x] `./gradlew :apps:desktop:test` passes.

## Evidence / notes

Done 2026-09-24.

- `DesktopRouteClassifier` now decides by the page body, not just the HEAD: when the HEAD probe says HTML/unknown, a bounded GET (≤ `HttpDownloadEngine.MAX_HTML_BYTES`, same policy-checked redirect budget) feeds the shared `GenericExtractor`. Exactly one media URL that passes policy → `DIRECT_FILE` (owned by `HttpDownloadEngine`); zero, several, page I/O errors, or a loopback/reserved candidate → `YTDLP_CLI`, so unresolved HTML and site URLs keep the CLI path and its missing-tool behavior. Direct files still never spawn a process. `Main.kt` wiring unchanged (`classify = { url -> DesktopRouteClassifier().route(url) }`); jobs persist through the existing JSON store as before.
- Seam: the shared `GenericExtractor.extract` gained an optional `candidateCheck` (default `UrlPolicy::check`), and `HttpDownloadEngine` passes its own `urlCheck` — production behavior identical, tests may allow exactly one loopback fixture origin (mirrors the engine's existing `urlCheck` pattern).
- Settings still report yt-dlp/ffmpeg; `tools_hint` (EN + pt-BR) now says a simple media page needs neither yt-dlp nor ffmpeg while site and other video URLs still do; the FreshWindows UI test asserts the new copy.
- Tests: classifier — `htmlFixtureWithOneMediaElementIsRoutedToTheHttpEngine`, `htmlWithoutMediaIsRoutedToTheCli` (both over a local `HttpServer`, fixture exception for exactly the loopback origin; the removed `htmlPageIsRoutedToTheCli` was replaced). Routing — `htmlFixtureRoutedByClassifierCompletesWithoutProcess` (real classifier + fixture page + fake transfer: COMPLETED, artifact file on disk, zero processes), `nonMatchingHtmlPageStillReachesTheCliPath` (CLI fake runs, http engine untouched), `cancelOfHtmlFixtureJobLeavesNoCompletedFile` (CANCELLED, download root empty). `FreshWindowsInstallWithoutYtDlpTest` updated for the link-only home (no queue chrome in `App`): asserts the job lands FAILED with the missing-tool message in the persisted graph instead of clicking the old shell tabs.
- Verification: `./gradlew :apps:desktop:test` — 101 tests, 0 failures; `:shared:core:jvmTest` 124 and `:shared:ui:jvmTest` 71, both 0 failures; iOS-sim/Wasm/Android compiles BUILD SUCCESSFUL.
