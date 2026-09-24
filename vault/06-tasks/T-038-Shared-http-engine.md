---
id: T-038
type: task
priority: P0
milestone: D2
tags: [task, engine, kmp]
---

# T-038 — Shared HTTP download engine

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md)

## Outcome

A shared `DownloadEngine` implementation that streams one direct HTTP(S) file to disk. No HTML extraction. No process. No Python.

## Dependencies

- [T-006](T-006-Review-security-licensing.md) must have recorded that this slice copies no upstream extractor source.

## Context the next session needs

`DownloadEngine` already exists in `shared/core`. Desktop uses `YtDlpCliEngine`. Other hosts use `InMemoryDownloadEngine`. Keep that interface. Add a real implementation that hosts can construct with platform HTTP and file ports.

Do not create a new Gradle module. Do not reference `ProcessBuilder` or Chaquopy here.

## Work

- Classify a URL as `DirectFile` or `NeedsExtractor`. Direct means the response is a downloadable body (not `text/html`). Classification may use a HEAD or the GET Content-Type. Unknown HTML is `NeedsExtractor`, not a successful download.
- Reject non-HTTP(S), userinfo, and loopback/link-local/reserved destinations on every redirect. Reuse and extend `SourceUrlValidator` rather than inventing a second URL type.
- Stream the body to a temp file under the download root, then rename onto the artifact path. Do not hold the whole file in memory.
- Honor `StartPolicy`, idempotency keys, cancel, retry, `removeHistory`, and `deleteArtifacts` as the interface already specifies.
- Progress: bytes and optional Content-Length percent. Speed and ETA stay null unless measured. Unknown stays unknown.
- If the URL needs an extractor, fail with a typed, redacted error the UI can map. Do not call yt-dlp from this class.
- Custom yt-dlp JSON on the request is ignored.

## Acceptance criteria

- [x] Unit tests cover success, cancel mid-stream, HTTP error, redirect to a blocked address, HTML Content-Type treated as NeedsExtractor, and idempotent double submit.
- [x] Tests use a local mock server or in-memory dispatcher. No live third-party hosts in default CI.
- [x] Common code still has no process or Python API.
- [x] Evidence lists commands and test names.

## Evidence / notes

Done on 2026-09-23. Engine and ports land now; T-039 supplies the per-target HTTP/file actuals.

Commands run:

- `./gradlew :shared:core:jvmTest` - 85 tests, 0 failures.
- `./gradlew :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:core:compileAndroidMain` - all target families compile the new common code.
- `grep -rn -i 'processbuilder\|python\|chaquopy' shared/core/src/commonMain/` - no process or Python API (only the doc comment that says so).

Test names (in-memory dispatcher and fakes only, fixture host `https://fixtures.example.com`):

- `directFileStreamsToTempThenPublishesAndCompletes` (success)
- `cancelMidStreamDiscardsTempAndCancelsTheJob` (cancel mid-stream)
- `httpErrorFailsWithMappedErrorCode`, `rateLimitedStatusMapsToRateLimited` (HTTP errors)
- `redirectToBlockedAddressFailsWithoutFetchingIt` (redirect to a blocked address)
- `htmlContentTypeIsNeedsExtractorAndFailsTyped` (HTML Content-Type -> NeedsExtractor)
- `idempotentDoubleSubmitReturnsTheOriginalJobOnce` (idempotent double submit)
- plus: manual start, invalid URL/userinfo/loopback rejection, allowed redirects, redirect budget, retry, removeHistory, deleteArtifacts, progress percent known/unknown, `UrlPolicyTest`, `UrlClassifierTest`, `ArtifactNameTest`.

Delivered under `com.anydownlod.core.engine` and `com.anydownlod.core.platform`:

- `HttpDownloadEngine` - job lifecycle, per-hop URL policy, streaming to temp then publish, typed errors.
- `UrlPolicy` (reuses `SourceUrlValidator` intent, adds userinfo + local/reserved destination checks), `UrlClassifier` (DirectFile vs NeedsExtractor via Content-Type), `ArtifactName` (normalized, traversal-rejected output naming).
- Ports for T-039: `HttpTransfer`/`HttpBody`, `FileStore`/`FileHandle`, plus a tiny `engineCriticalSection` expect/actual (JVM/Android `synchronized`; native/wasm single-threaded).
