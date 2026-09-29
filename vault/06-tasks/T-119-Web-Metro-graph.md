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

- [ ] `@DependencyGraph` is in `apps/web` `wasmJsMain`; one bridge, one runtime, one transfer, one engine.
- [ ] `?solverHook=1` installs the hook on the same runtime the graph uses.
- [ ] The page does not fetch arbitrary origins; the existing web behavior tests stay green.
- [ ] `./gradlew :apps:web:wasmJsBrowserDistribution` succeeds, and `:apps:web:wasmJsBrowserTest` stays green when a browser is available.
- [ ] No download behavior changed.

## Evidence / notes

Not started.
