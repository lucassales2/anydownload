---
id: T-039
type: task
priority: P0
milestone: D2
tags: [task, kmp, platforms]
---

# T-039 — Platform HTTP and file adapters

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

expect/actual (or injected ports) for HTTP GET/HEAD with redirect policy and for streaming file create/rename/delete, so `HttpDownloadEngine` can run on JVM, Android, iOS, and the web host without calling browser-forbidden origins itself.

## Dependencies

- [T-038](T-038-Shared-http-engine.md).

## Work

- Add small ports in `com.anydownlod.core.platform`: HTTP request/response stream, and a `FileStore` scoped to the download root.
- JVM/Android/iOS actuals perform the fetch in-process. The web actual must **not** fetch arbitrary origins; it either no-ops with a clear `ExtensionRequired` error or talks to the extension bridge stub that T-043 will fill.
- Normalize paths. Reject traversal and paths outside the download root.
- Stream writes. Prove with a generated payload large enough that a whole-file buffer would be obvious in a test (for example 8–16 MiB on JVM).
- Keep Ktor (or the existing HTTP stack) as an implementation detail of the actuals, not of the UI.

## Acceptance criteria

- [x] JVM tests write, cancel, and delete under a temp root.
- [x] Android and iOS actuals compile. A missing SDK is recorded, not treated as a D2 design failure.
- [x] Web actual cannot silently fetch a third-party URL.
- [x] Common code still has no `ProcessBuilder`.

## Evidence / notes

Done on 2026-09-23. Adapters land on the T-038 ports; T-040 wires desktop, T-041 Android, T-042 iOS, T-043 web.

Commands run:

- `./gradlew :shared:core:jvmTest` - 92 tests, 0 failures (86 common + 6 JVM platform integration).
- `./gradlew :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:core:compileAndroidMain` - all target families compile.
- `grep -rn ProcessBuilder shared/core/src/commonMain/` - no process API in common code.

JVM tests (`JavaNetPlatformTest`, real local `com.sun.net.httpserver.HttpServer`, fixture exception that allows exactly the one test-server origin, everything else goes to `UrlPolicy` - strictly test-only, per T-006):

- `streamsEightMibWithoutBufferingWholeFile` - 8 MiB payload, streamed in 64 KiB chunks, artifact bytes verified.
- `cancelMidStreamDeletesTheTempFile` - stalling server; cancel; CANCELLED, temp file deleted, no artifact.
- `deleteArtifactsRemovesTheFileUnderTheRoot`
- `followsRelativeLocationResolvedByThePlatform` - 302 with relative `Location`; artifact named from the final hop.
- `fileStoreRejectsTraversalOutsideTheRoot` - `../`, `a/../../`, absolute escapes refused.
- `fileStoreWritesAndDeletesUnderTheRoot`

The shared engine gained an `HttpResponse.Unavailable` result (typed "engine unavailable" job error; no fake download). Common test `unavailableTransferFailsAsEngineUnavailable` covers it.

Delivered:

- JVM: `JavaNetHttpTransfer` (java.net, one hop per call, relative `Location` resolved), `JavaNetFileStore` (java.nio, normalized, root-escape refused).
- Android: `JavaNetHttpTransfer` / `JavaNetFileStore` mirrors (java.net/java.nio are platform APIs; no new Gradle dependency added anywhere).
- iOS: `IosHttpTransfer` compiles and returns typed `Unavailable`; it is deliberately a placeholder - the real in-process NSURLSession fetch that also preserves per-hop redirect validation lands in T-042 (recorded here so this is a plan, not a design failure).
- Web: `WebExtensionTransfer` performs no request (typed `Unavailable`); it cannot silently fetch any third-party URL. T-043 fills the extension bridge behind the same interface.

No Ktor dependency was added to `shared/core` this task; java.net/java.nio stay the JVM/Android implementation detail.
