---
id: T-040
type: task
priority: P0
milestone: D2
tags: [task, desktop, engine]
---

# T-040 — Desktop routes direct files through Kotlin HTTP

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md)

## Outcome

The desktop host uses the shared HTTP engine for direct files and keeps `YtDlpCliEngine` for every other URL.

## Dependencies

- [T-039](T-039-Platform-http-and-files.md).
- D1 desktop CLI (`YtDlpCliEngine`) stays.

## Work

- Compose a routing engine in `apps/desktop` only: classify, then dispatch.
- Direct-file jobs never spawn yt-dlp.
- NeedsExtractor jobs keep the D1 CLI path, including missing-tool behavior.
- Settings tool probe still reports yt-dlp and ffmpeg. Add an honest line that direct files do not need them.
- Persist jobs through the existing JSON store.

## Acceptance criteria

- [x] Desktop tests: a mock direct file completes without `ProcessBuilder`; an HTML/site URL still goes to the CLI fake/fixture path.
- [x] Cancel of a direct-file job stops the HTTP stream and leaves no completed file.
- [x] `./gradlew :apps:desktop:test` passes.

## Evidence / notes

Done on 2026-09-23.

Commands run:

- `./gradlew :apps:desktop:test` - 92 tests, 0 failures (9 opt-in live yt-dlp checks skipped without media URLs).
- `./gradlew :shared:ui:jvmTest :shared:core:jvmTest` - 77 + 92 tests, 0 failures, after the settings copy change.

New desktop-only code in `com.anydownlod.desktop.engine`:

- `DesktopRoutingEngine` - merges the `HttpDownloadEngine` and `YtDlpCliEngine` job flows (each engine owns exactly its own jobs), routes submit/start/cancel/retry/removeHistory/deleteArtifacts to the owning engine.
- `DesktopRouteClassifier` - bounded HEAD probe (3 s timeouts) that validates every hop with `UrlPolicy`; direct non-HTML 2xx -> HTTP engine; HTML, unknown content type, errors, and redirect-budget overflow -> CLI. Policy-rejected URLs (userinfo/loopback) route to the HTTP engine so no process ever sees them. `resumeRoute` splits persisted jobs by URL extension without network for startup.
- `DesktopFileStore` - re-reads the current download root on every call.
- `Main.kt` wires both engines + router; both engines persist the merged list through the existing `DesktopStore.saveJobs`, so `jobs.json` never loses one engine's rows. The subscription repository and shutdown path use the router graph.

Settings copy: `tools_hint` now states the probe still reports yt-dlp/ffmpeg versions and that direct file downloads work without them (en + pt-BR). The existing `FreshWindowsInstallWithoutYtDlpTest` assertion was updated to the new copy.

Test names (`DesktopRoutingEngineTest`, `DesktopRouteClassifierTest`, in-memory transfer/file fakes plus real `com.sun.net.httpserver` fixture with a strictly test-only loopback exception):

- `directFileCompletesWithoutSpawningAnyProcess` (ProcessBuilder never invoked; artifact bytes verified)
- `siteOrHtmlUrlStaysOnTheCliPath` (FakeCliProcess; HTTP engine never touched)
- `cancelOfDirectFileStopsTheStreamAndLeavesNoCompletedFile` (CANCELLED; download root left empty)
- `missingYtDlpStillFailsSiteUrlsThroughTheCliPath` (ENGINE_UNAVAILABLE; HTTP engine untouched)
- `directFileHeadIsRoutedToTheHttpEngine`, `htmlPageIsRoutedToTheCli`, `redirectToOneHopAwayDirectFileIsRoutedToTheHttpEngine`, `errorStatusIsRoutedToTheCliAsFallback`, `defaultPolicyRefusesToSendLoopbackToTheCli`, `resumeRouteSplitsPersistedJobsByUrlShape`
