---
id: T-118
type: task
priority: P0
milestone: D9
tags: [task, metro]
---

# T-118 — iOS Metro graph

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

`shared/ui` `iosMain` gets the Metro-generated iOS graph. `MainViewController` builds it with `createGraph`. The graph owns one `IosHttpTransfer`, one `QuickJsRuntime`, the shared registry, `IosFileStore` under Documents, `HttpDownloadEngine`, and `IosMediaToolkit`. `:shared:ui:iosSimulatorArm64Test` and `:shared:core:iosSimulatorArm64Test` pass.

## Dependencies

- [T-116](T-116-Desktop-Metro-graph.md).

## Context the next session needs

- `IosAppGraph` is currently a class in `shared/ui/src/iosMain`; it becomes a `@DependencyGraph(AppScope::class, bindingContainers = [SharedEngineBindings::class])` interface implementing the core `AppGraph`. `ViewModelGraph` is added in T-121.
- `MainViewController` uses `createGraph<IosAppGraph>()`. Keep the SwiftUI entry point name (`MainViewControllerKt.MainViewController()`) unchanged.
- Keep the iOS storage split: `IosFileStore` rooted in Documents for media, and the jobs document under Application Support. Keep the startup warning behavior from the restore result.
- Delete `IosExtractors`. The `IosXStatusTest` builds the production list through the shared factory from T-115.
- `HttpDownloadEngine` and `PersistingDownloadEngine` keep their constructors; the graph calls them. Do not `@Inject` `HttpDownloadEngine`.

## Work

- Convert `IosAppGraph` into the `@DependencyGraph` interface. Move the `CoroutineScope`, `transfer`, `jsRuntime`, registry, `IosFileStore`, `IosMediaToolkit`, job storage, restore, and engine construction into graph `@Provides` bindings, each scoped to `AppScope` where it is a singleton.
- Keep `IosToolProbe`, the Spotify service, the preview source, and the settings/subscriptions repositories on the graph.
- Update `MainViewController` and any other `IosAppGraph()` call site to `createGraph`.
- Keep the simulator tests green; if a test needs the registry, it calls the shared factory from T-115.

## Acceptance criteria

- [x] `@DependencyGraph` is in `shared/ui` `iosMain`; `MainViewController` builds the graph with `createGraph`.
- [x] One each: `IosHttpTransfer`, `QuickJsRuntime`, `IosFileStore` under Documents, `HttpDownloadEngine`, `IosMediaToolkit`, and one shared registry.
- [x] The jobs document under Application Support and the startup warning behave as before.
- [x] `./gradlew :shared:ui:iosSimulatorArm64Test :shared:core:iosSimulatorArm64Test` passes.
- [x] No download behavior changed.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Graph:** `shared/ui/src/iosMain/kotlin/com/anydownlod/ui/IosAppGraph.kt` is now `@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class])` (internal interface). `MainViewController` builds it with `createGraph<IosAppGraph>()`. `IosToolProbe` moved with the graph; the absent Python/CLI behavior is unchanged.
- **Singletons:** one `IosHttpTransfer`, one `QuickJsRuntime`, one `SharedEngineBindings` `ExtractorRegistry`, one `IosMediaToolkit`, one `CoroutineScope`, and one `ExtractorHttp` (now shared by the Spotify metadata client and the audio matcher). `IosFileStore` stays rooted in Documents; `IosSandboxRoot` wraps that path as a unique binding. `IosJobDocumentStorage` stays under Application Support, and the restore is computed once into `IosRestoredQueue` (jobs + warning).
- **Persist cycle:** the engine `persist` lambda resolves `PersistingDownloadEngine` through a Metro `Provider`, so the construction order matches the old late-initialized `PersistingDownloadEngine` without the guard.
- **Metro finding:** provider bindings key on the declared return type, not the implementation type. `fun httpTransfer(): IosHttpTransfer` did not satisfy `SharedEngineBindings`’ `HttpTransfer` parameter (`[Metro/MissingBinding] No binding found for HttpTransfer`); returning `HttpTransfer` fixed it. The defaulted AppGraph properties (`previews`, `startupWarning`, `toolkitCapabilities`, `spotify`) again use host accessors plus concrete overrides.
- **Verification:** `./gradlew --console=plain :shared:ui:iosSimulatorArm64Test :shared:core:iosSimulatorArm64Test` → BUILD SUCCESSFUL in 39s. Test XMLs: `:shared:ui:iosSimulatorArm64Test` 8 tests, 0 failures; `:shared:core:iosSimulatorArm64Test` 476 tests, 0 failures; `IosXStatusTest` 1/0 through the shared production factory.
- No commit was made.
