---
id: T-116
type: task
priority: P0
milestone: D9
tags: [task, metro]
---

# T-116 — Desktop Metro graph

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

`apps/desktop` `jvmMain` gets the Metro-generated desktop graph. `DesktopApp.open` builds it with `createGraphFactory`, keeping the five factory inputs. Preview, the HTTP engine, and CLI routing share one `ExtractorRegistry`; one `DesktopSpotifyTokenStore` serves the library and the auth service. `DesktopApp` still loads `DesktopStore`, saves all state on close, and shuts the CLI down. `:apps:desktop:test` passes.

## Dependencies

- [T-115](T-115-Shared-engine-bindings.md).

## Context the next session needs

- `DesktopApp.open` is the only entry point `main()` and `DesktopAppRelaunchTest` use. Keep its signature and defaults; change only how the graph is built inside.
- Factory inputs stay exactly `stateDirectory: Path`, `defaultDownloadRoot: () -> String`, `processRunner: CliProcessRunner`, `resolveExecutable: (String) -> String?`, and `ioDispatcher: CoroutineDispatcher`. Annotate them `@Provides` on the factory. If Metro rejects a function-typed parameter, wrap that input in a small class and record it in Evidence.
- Metro graphs are cheap and scoped. Use `@DependencyGraph(AppScope::class, bindingContainers = [SharedEngineBindings::class])`. `ViewModelGraph` is added in T-121, not here.
- `HttpDownloadEngine` keeps its normal constructor; the graph calls it. The graph also owns the single `CoroutineScope` for the engines, subscriptions, and scheduler.
- One `DesktopSpotifyTokenStore(stateDirectory)` binding, injected into both `SpotifyLibraryClient` and `SpotifyAuthService`.
- `DesktopPreviewSource` takes the graph registry (T-115). The test in this task must prove the registry instance in previews is the same instance the HTTP engine and classifier use.

## Work

- Add the `@DependencyGraph` in `apps/desktop` `jvmMain` implementing the core `AppGraph`, with a `@DependencyGraph.Factory` whose `create` takes the five inputs.
- Move the object construction from `DesktopApp.open` into the graph: `DesktopStore`, `PersistingSettingsRepository`, `JavaNetHttpTransfer`, `QuickJsRuntime`, the shared registry, `DesktopRoutingEngine`, `YtDlpCliEngine`, `DesktopFfmpegToolkit`, `DesktopPreviewSource`, the Spotify service/fallback matcher, `SpotifyAuthService`, `DesktopCookieStore`, and `DesktopSubscriptionRepository`. `desktopGraph(...)` is replaced by the generated graph.
- `DesktopApp.open` creates the store, then the graph via `createGraphFactory<...Factory>().create(...)`, keeps the startup warning and the initial `store.saveAll(...)`, and still returns `DesktopApp(store, graph, shutdownEngine)`.
- Add a test that shows the `ExtractorRegistry` used by the preview source is the graph's registry (for example by comparing the extractor instance identity exposed on both sides).
- Keep `DesktopAppRelaunchTest` compiling and green.

## Acceptance criteria

- [ ] `@DependencyGraph` is only in `apps/desktop` `jvmMain`; the factory carries `stateDirectory`, `defaultDownloadRoot`, `processRunner`, `resolveExecutable`, and `ioDispatcher`.
- [ ] One registry is shared by previews, the HTTP engine, and CLI routing, with a test proving the identity.
- [ ] One `DesktopSpotifyTokenStore` is shared by the library and the auth service.
- [ ] `DesktopApp` still loads `DesktopStore`, saves on close, and shuts the CLI down.
- [ ] `./gradlew :apps:desktop:test` passes, including `DesktopAppRelaunchTest`.
- [ ] No download behavior changed.

## Evidence / notes

Not started.
