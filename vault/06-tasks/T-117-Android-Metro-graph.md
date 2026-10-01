---
id: T-117
type: task
priority: P0
milestone: D9
tags: [task, metro]
---

# T-117 — Android Metro graph

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

`apps/android` `main` gets the Metro-generated Android graph, built from `Context` and a `ChaquopyPort` that defaults to `NoChaquopyPort`. The graph owns one `JavaNetHttpTransfer`, one `QuickJsRuntime`, the shared registry, and `AndroidRoutingEngine`. One Spotify helper serves metadata, matching, and queueing. Unmatched URLs still route to Chaquopy. `:apps:android:assembleDebug` and `:apps:android-engine-tests:test` pass.

## Dependencies

- [T-116](T-116-Desktop-Metro-graph.md).

## Context the next session needs

- The graph is `@DependencyGraph(AppScope::class, bindingContainers = [SharedEngineBindings::class])` and implements the core `AppGraph`. Its `@DependencyGraph.Factory` takes `@Provides context: Context` and `@Provides port: ChaquopyPort = NoChaquopyPort`.
- Do not put a `@DependencyGraph` under `com.anydownlod.android.engine`. That package stays pure and JVM-testable; the engine tests build the routing and extractor classes directly.
- `MainActivity` creates the graph once with `createGraphFactory<...Factory>().create(applicationContext)`. Keep the route classifier and the resumed-job split between HTTP and Chaquopy jobs unchanged.
- The `AndroidRoutingEngine` classification is the behavior contract: `YoutubeIE`, `YoutubeTabIE`, and `TwitterIE` go through Kotlin, everything else goes to Chaquopy. Keep it.
- The single `ExtractorHttp` binding from `SharedEngineBindings` feeds `SpotifyMetadataClients`, `AudioMatcher`, and the preview source.

## Work

- Convert `AndroidAppGraph` into the `@DependencyGraph` interface with the factory above. Keep a public factory function for tests if `engine-tests` or android tests construct it directly; record what changed in Evidence.
- Provide the graph internals as `@SingleIn(AppScope::class)`: transfer, runtime, registry (from the binding container), `JavaNetFileStore`, `AndroidJobDocumentStorage`, `JobDocumentStore`, `HttpDownloadEngine`, `ChaquopyEngine`, `AndroidRoutingEngine`, `PersistingDownloadEngine`, settings/subscriptions repositories, the Spotify service, `AndroidMediaToolkit`, tool probe, preview source, and `openUrl`.
- Keep the jobs-document restore, the interrupted-job warning, and the `jobsSnapshot` merge.
- Wire `MainActivity` to the graph factory.

## Acceptance criteria

- [x] `@DependencyGraph` is in `apps/android` `main` and not under `com.anydownlod.android.engine`.
- [x] The factory takes `Context` and `ChaquopyPort` with `NoChaquopyPort` as the default.
- [x] One transfer, one runtime, and one shared registry feed the routing engine, previews, and Spotify helper.
- [x] Unmatched URLs still route to Chaquopy; `:apps:android-engine-tests:test` proves the routes it proved before.
- [x] `./gradlew :apps:android:assembleDebug :apps:android-engine-tests:test` passes.
- [x] No download behavior changed.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Graph:** `anydownload` is now `@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class])`, package `com.anydownlod.android` — not `com.anydownlod.android.engine`. Its `Factory` takes `@Provides context: Context` and `@Provides port: ChaquopyPort = NoChaquopyPort`. `MainActivity` uses `createGraphFactory<AndroidAppGraph.Factory>().create(applicationContext)`, so the default port is exercised by the app.
- **Metro finding:** Kotlin rejects default parameter values on a `fun interface` abstract method (“Functional interface abstract method cannot have a default value”), so the factory is a regular one-method `interface` rather than `fun interface`; Metro generates it and the `NoChaquopyPort` default is kept at the call site.
- **Defaulted AppGraph properties:** the same workaround as T-116 — `previews`, `startupWarning`, `toolkitCapabilities`, `spotify`, and `openUrl` are concrete overrides over `hostPreviews`, `hostStartupWarning`, `hostToolkitCapabilities`, `hostSpotify`, and `hostOpenUrl`, which Metro implements from the bindings. The nullable `String?` startup-warning binding worked in the Android graph.
- **Singletons:** one `JavaNetHttpTransfer`, one `QuickJsRuntime`, one `SharedEngineBindings` `ExtractorRegistry`, one `AndroidMediaToolkit`, one `CoroutineScope`, and one `ExtractorHttp`. The HTTP engine, Chaquopy engine, classifier, previews, and Spotify service all use the same registry/HTTP binding. `persist` resolves the `PersistingDownloadEngine` through a Metro `Provider` so the child-engine constructor cycle stays broken; the routing engine still merges the two job flows and the `jobsSnapshot` merge is unchanged.
- **Restore:** one `AndroidRestoredJobs` binding runs `JobDocumentStore.restore` once, splits the rows with `AndroidRouteClassifier.resumeRoute`, and carries the startup warning. An unavailable port still fails site URLs typed with engine-unavailable and never pretends to run Python.
- **Verification:** `./gradlew --console=plain :apps:android:assembleDebug :apps:android-engine-tests:test` → BUILD SUCCESSFUL (8s). Engine tests: 26 tests, 0 failures, including `AndroidRouteClassifierTest` 4/0 and `AndroidXStatusTest` 2/0. The debug APK was produced at `apps/android/build/outputs/apk/debug/android-debug.apk`.
- No commit was made.
