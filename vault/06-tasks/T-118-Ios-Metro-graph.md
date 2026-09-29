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

- [ ] `@DependencyGraph` is in `shared/ui` `iosMain`; `MainViewController` builds the graph with `createGraph`.
- [ ] One each: `IosHttpTransfer`, `QuickJsRuntime`, `IosFileStore` under Documents, `HttpDownloadEngine`, `IosMediaToolkit`, and one shared registry.
- [ ] The jobs document under Application Support and the startup warning behave as before.
- [ ] `./gradlew :shared:ui:iosSimulatorArm64Test :shared:core:iosSimulatorArm64Test` passes.
- [ ] No download behavior changed.

## Evidence / notes

Not started.
