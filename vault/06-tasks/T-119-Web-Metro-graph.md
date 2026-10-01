---
id: T-119
type: task
priority: P0
milestone: D9
tags: [task, metro]
---

# T-119 — Web Metro graph

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

`apps/web` `wasmJsMain` gets the Metro-generated web graph with one `WindowExtensionBridge`, one `BrowserJsRuntime`, one `WebExtensionTransfer`, and one `WebExtensionEngine`. `?solverHook=1` still installs the solver hook on that runtime. `:apps:web:wasmJsBrowserDistribution` succeeds and the page never fetches an arbitrary origin.

## Dependencies

- [T-116](T-116-Desktop-Metro-graph.md).

## Context the next session needs

- `WebAppGraph` becomes a `@DependencyGraph(AppScope::class, bindingContainers = [SharedEngineBindings::class])` interface implementing the core `AppGraph`. `ViewModelGraph` is added in T-121.
- `main()` currently builds one `WebAppGraph` when `?solverHook=1` is present and another inside Compose. After this task it creates the graph once with `createGraph<WebAppGraph>()` and uses that same instance for the hook branch and for `App(graph)`.
- The `solverHook` installs on the graph's `BrowserJsRuntime`. Expose the runtime through the graph (the current `internal val browserJsRuntime` seam) so the hook and the engine share it.
- The web page never fetches arbitrary origins: the extension carries requests through `WindowExtensionBridge` and `WebExtensionTransfer`, and the existing behavior tests (`WebM1GateTest`, `WebJobDocumentStorageTest`) stay green.
- Keep the localStorage jobs document, the typed storage error, and the startup warning.

## Work

- Convert `WebAppGraph` into the `@DependencyGraph` interface. Provide the bridge, runtime, `ExtractorHttp`/`WebExtensionTransfer`, registry (shared), `WebExtensionEngine`, `PersistingDownloadEngine`, `WebJobDocumentStorage`, `JobDocumentStore`, settings/subscriptions repositories, the Spotify service, `WebToolProbe`, and the preview source.
- Update `main()` to create the graph once and pass it to `App`. Keep the `?solverHook=1` branch installing the hook on the graph runtime.
- No page-side fetch path may be added.

## Acceptance criteria

- [x] `@DependencyGraph` is in `apps/web` `wasmJsMain`; one bridge, one runtime, one transfer, one engine.
- [x] `?solverHook=1` installs the hook on the same runtime the graph uses.
- [x] The page does not fetch arbitrary origins; the existing web behavior tests stay green.
- [x] `./gradlew :apps:web:wasmJsBrowserDistribution` succeeds, and `:apps:web:wasmJsBrowserTest` stays green when a browser is available.
- [x] No download behavior changed.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Graph:** `anydownload` is now `@DependencyGraph(scope = AppScope::class, bindingContainers = [SharedEngineBindings::class])` (internal interface). `Main.kt` creates exactly one graph with `createGraph<WebAppGraph>()`, installs the `?solverHook=1` CDP hook on `graph.browserJsRuntime`, and passes that same graph to `App`. `WebToolProbe` moved with the graph.
- **Singletons:** one `WindowExtensionBridge`, one `BrowserJsRuntime`, one `WebExtensionTransfer` (declared as the `HttpTransfer` binding), one `WebExtensionEngine`, one `PersistingDownloadEngine`, one `CoroutineScope`, and the one shared `ExtractorRegistry`/`ExtractorHttp` from `SharedEngineBindings` used by the classifier, previews, and Spotify helper.
- **Metro findings:** (1) provider bindings key on the declared type, so `extensionTransfer` returns `HttpTransfer`, not `WebExtensionTransfer`; (2) the host needs the concrete `BrowserJsRuntime` for `installSolverHook` while the shared bindings need `JsRuntime`, so the graph declares both (`browserJsRuntime()` plus `jsRuntime(BrowserJsRuntime)` returning the same scoped instance). The defaulted core `AppGraph` properties again use host accessors plus concrete overrides. `jobStorageError` is backed by a `StateFlow<JobDocumentStorageError?>` binding from the persisting engine.
- **No page fetches:** the transfer is the `WindowExtensionBridge` only; no page-origin fetch code was added. `WebM1GateTest` still completes the fixture through the bridge and keeps the media path extension-only.
- **Verification:** `./gradlew --console=plain :apps:web:wasmJsBrowserDistribution` → BUILD SUCCESSFUL in 1m 56s (production bundle written under `apps/web/build/dist/wasmJs/productionExecutable`). Then `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew --console=plain :apps:web:wasmJsBrowserTest` → BUILD SUCCESSFUL in 26s; 6 tests, 0 failures (`WebM1GateTest` 1, `WebJobDocumentStorageTest` 5).
- No commit was made.
