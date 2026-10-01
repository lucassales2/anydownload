---
id: T-115
type: task
priority: P0
milestone: D9
tags: [task, metro]
---

# T-115 — SharedEngineBindings and one production extractor list

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

`SharedEngineBindings` lives in `shared/core` commonMain as a Metro binding container. It provides `ExtractorHttp`, `YoutubeIE`, `YoutubeTabIE`, `TwitterIE`, and the ordered `ExtractorRegistry`, each scoped to `AppScope`. The five hand-written copies of the production extractor list are deleted, and the engine tests build the production registry through the same public shared factory the graph uses. `DesktopPreviewSource.create` takes that registry and no longer creates its own transfer.

## Dependencies

- [T-114](T-114-Pin-Metro-and-smoke-graph.md).

## Context the next session needs

- The production order must not change: `YoutubeIE`, `YoutubeTabIE`, `TwitterIE`, no `GenericIE`, no `@IntoSet`. The loop prompt's `listOf(youtube, twitter)` shorthand was written before [T-107](T-107-Playlist-expansion.md) and [T-108](T-108-Youtube-playlist-subset.md); dropping `YoutubeTabIE` would regress the D8 playlist feature. Keep the playlist tab in the registry and record the reason in Evidence.
- `YoutubeIE` keeps its `jsRuntime: JsRuntime = NoJsRuntime` default for tests. The graph `@Provides` takes `JsRuntime` explicitly. Do not `@Inject` `YoutubeIE` or `HttpDownloadEngine`.
- `@ContributesIntoMap` and `@ContributesBinding` are reserved for the ViewModel work in T-121/T-122. This task uses `@BindingContainer` and `@Provides` only.
- This task does not add a `@DependencyGraph`; the four hosts still build their present graphs, now from the shared factory. Delete the temporary smoke graph from T-114.
- `ExtractorRegistry` rejects an empty registry and requires a generic extractor, if present, to be last. The production list has no generic extractor.

## Work

- Add `SharedEngineBindings` in `shared/core` commonMain as a `@BindingContainer` with `@Provides @SingleIn(AppScope::class)` for:
  - `extractorHttp(transfer: HttpTransfer): ExtractorHttp`
  - `youtube(http: ExtractorHttp, jsRuntime: JsRuntime): YoutubeIE`
  - `youtubeTab(http: ExtractorHttp): YoutubeTabIE`
  - `twitter(http: ExtractorHttp): TwitterIE`
  - `registry(youtube: YoutubeIE, youtubeTab: YoutubeTabIE, twitter: TwitterIE): ExtractorRegistry` in that order.
- Add a public common factory that builds the production `ExtractorRegistry` from a transfer and runtime (used by the tests, `AndroidExtractors` callers, the iOS test, and the host classes until T-116–T-119 move them). The `@Provides` registry must assemble from the same three bound instances, so a host never holds two copies of an extractor.
- Delete `AndroidExtractors` and `IosExtractors`, and remove the inline `ExtractorRegistry(...)` lists from `WebAppGraph`, desktop `Main.kt`, and `DesktopPreviewSource`.
- Change `DesktopPreviewSource.create` to take the `ExtractorRegistry` and remove the `transfer: HttpTransfer = JavaNetHttpTransfer()` default. It must still fall back to the installed CLI for URLs no extractor matches.
- Keep the existing tests green: `:shared:core:jvmTest` extractor and playlist tests, `:apps:android-engine-tests:test`, and the iOS `IosXStatusTest` still build the production list through the public shared factory.

## Acceptance criteria

- [x] `SharedEngineBindings` exists in commonMain with the five `@Provides` bindings scoped to `AppScope`.
- [x] The production order is `YoutubeIE`, `YoutubeTabIE`, `TwitterIE`; no `GenericIE`; no `@IntoSet`.
- [x] `AndroidExtractors`, `IosExtractors`, and the four inline production lists are gone; the public shared factory is the only source of the list.
- [x] `DesktopPreviewSource.create` takes the registry and has no `JavaNetHttpTransfer()` default.
- [x] `./gradlew :apps:android-engine-tests:test :shared:core:jvmTest` passes; `:apps:desktop:compileKotlin` and `:shared:ui:compileKotlinIosSimulatorArm64` still compile.
- [x] No download behavior changed (playlist matching, preview routing, and direct-file downloads stay as they were).

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Shared bindings:** `anydownload` adds a `@BindingContainer` `object` with `@Provides @SingleIn(AppScope::class)` for `extractorHttp(transfer)`, `youtube(http, jsRuntime)`, `youtubeTab(http)`, `twitter(http)`, and `extractorRegistry(youtube, youtubeTab, twitter)`. It uses Metro’s own `dev.zacsweers.metro.AppScope`. The public `productionExtractorRegistry(youtube, youtubeTab, twitter)` is what the registry `@Provides` calls; convenience overloads `(ExtractorHttp, JsRuntime)` and `(HttpTransfer, JsRuntime)` serve the host classes and tests until the graphs land. The first desktop compile failed because the hosts hold an `ExtractorHttp`, not a raw transfer; the `ExtractorHttp` overload fixed it. No function-typed `@Provides` parameter was involved.
- **Order preserved:** the registry is `listOf(youtube, youtubeTab, twitter)`. `YoutubeTabIE` stays between the video and Twitter extractors so `/playlist?list=` behaves as it did in D8. The loop prompt’s `listOf(youtube, twitter)` shorthand predates T-107/T-108; dropping the playlist extractor would have been a behavior change, which the migration forbids. No `GenericIE` and no `@IntoSet`.
- **Deleted:** `anydownload`, `anydownload`, and the temporary T-114 `MetroSmokeGraph.kt`/`MetroSmokeGraphTest.kt`. The inline production lists in `AndroidAppGraph`, `IosAppGraph`, `WebAppGraph`, desktop `Main.kt`, and `DesktopPreviewSource` all call the shared factory now.
- **Desktop preview:** `DesktopPreviewSource.create(registry, runner, resolveExecutable, workingDirectory, kotlinTimeoutMillis)` takes the registry; the `transfer = JavaNetHttpTransfer()` and `jsRuntime` defaults are gone. Desktop `Main.kt` passes its one `extractorRegistry` into both the HTTP engine/classifier and the preview source.
- **Tests updated to the shared factory:** `AndroidXStatusTest` (two registries), `IosXStatusTest`, `DesktopPreviewSourceTest` (both create sites, `NoJsRuntime` to match the old default), and `DesktopXStatusGateTest` (passes its own Twitter-only fixture registry).
- **Verification run:** `./gradlew --console=plain :shared:core:jvmTest :apps:android-engine-tests:test --rerun-tasks` → BUILD SUCCESSFUL. Test XMLs: `:shared:core:jvmTest` 519 tests, 0 failures (including `YoutubeTabIEFixturesTest` 5 and `YoutubePlaylistEngineTest` 1), `:apps:android-engine-tests:test` 26 tests, 0 failures (including `AndroidXStatusTest` 2). Then `./gradlew --console=plain :apps:desktop:compileKotlin :shared:ui:compileKotlinIosSimulatorArm64 :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs` → BUILD SUCCESSFUL, and `./gradlew --console=plain :apps:desktop:compileTestKotlin :shared:ui:compileTestKotlinIosSimulatorArm64` → BUILD SUCCESSFUL.
- No commit was made.
