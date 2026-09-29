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

- [x] `@DependencyGraph` is only in `apps/desktop` `jvmMain`; the factory carries `stateDirectory`, `defaultDownloadRoot`, `processRunner`, `resolveExecutable`, and `ioDispatcher`.
- [x] One registry is shared by previews, the HTTP engine, and CLI routing, with a test proving the identity.
- [x] One `DesktopSpotifyTokenStore` is shared by the library and the auth service.
- [x] `DesktopApp` still loads `DesktopStore`, saves on close, and shuts the CLI down.
- [x] `./gradlew :apps:desktop:test` passes, including `DesktopAppRelaunchTest`.
- [x] No download behavior changed.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Graph:** new `apps/desktop/src/jvmMain/kotlin/com/anydownlod/desktop/DesktopGraph.kt` with `@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class])` and a `@DependencyGraph.Factory` taking the five inputs. `DesktopApp.open` keeps its signature/defaults and builds the graph with `createGraphFactory<DesktopGraph.Factory>().create(...)`, then persists the normalized rows once with `graph.store.saveAll(...)`. `DesktopApp.close()` still saves all state after the shutdown hook runs `graph.cliEngine.shutdown()` and `graph.scope.cancel()`.
- **One registry:** `DesktopGraphRegistryTest` opens a real graph, asserts `graph.previews` is a `DesktopPreviewSource`, and `assertSame(graph.extractorRegistry, previews.registry)`. The HTTP engine, CLI classifier, startup split, and Spotify matcher all consume the same `SharedEngineBindings` `ExtractorRegistry` (`@SingleIn(AppScope)`).
- **One token store:** `spotifyTokenStore(stateDirectory)` is `@SingleIn`; both `SpotifyDownloadService` (via `SpotifyLibraryClient`) and `SpotifyAuthService` take that one `SpotifyTokenStore`.
- **Metro finding 1 — function providers:** Metro has `enableFunctionProviders` on, so `@Provides` factory parameters of parameter-less function type are intrinsic provider types. `defaultDownloadRoot: () -> String` was rejected with “may not be intrinsic types”. It is now carried by the small `DesktopDefaultDownloadRoot` wrapper (recorded here per the loop rule). `resolveExecutable: (String) -> String?` has a parameter and stayed a plain factory param. The `shutdown: () -> Unit` binding was rejected for the same reason, so the graph exposes `cliEngine` and `scope` accessors and `open` composes the shutdown lambda.
- **Metro finding 2 — defaulted accessors:** for the six core `AppGraph` properties that have default getters (`previews`, `startupWarning`, `cookieStore`, `toolkitCapabilities`, `spotify`, `spotifyAuth`), a plain abstract override was not generated by Metro and the first test run hit `AbstractMethodError` (`getPreviews`, `getSpotify`, `getStartupWarning`). The graph now declares host-specific accessors (`hostPreviews`, `hostStartupWarning`, `hostCookieStore`, `hostToolkitCapabilities`, `hostSpotify`, `hostSpotifyAuth`) and concrete overrides read them. Metro generates the host accessors from the bindings, and `previews` and friends keep the same public type as `AppGraph`.
- **Verification:** `./gradlew --console=plain :apps:desktop:test` → BUILD SUCCESSFUL in 12s; test XMLs total 132 tests, 0 failures, 15 skipped (the opt-in live checks). `DesktopGraphRegistryTest` 1/0, `DesktopAppRelaunchTest` 3/0, `DesktopPreviewSourceTest` 3/0, `DesktopXStatusGateTest` 1/0. The graph-main compile alone was also run after each fix.
- No commit was made.
