---
id: T-121
type: task
priority: P0
milestone: D9
tags: [task, metro, viewmodel]
---

# T-121 — ViewModel factory and LocalMetroViewModelFactory

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

`shared/ui` commonMain uses the multiplatform lifecycle ViewModel Compose artifact and MetroX ViewModel Compose at the Metro plugin version. Desktop JVM adds the Swing main dispatcher. Each host graph extends MetroX `ViewModelGraph`, `AnyDownloadViewModelFactory` is contributed to `AppScope`, and `App` and the test shell install `LocalMetroViewModelFactory`. `:shared:ui:compileKotlinWasmJs` and `:shared:ui:compileKotlinIosSimulatorArm64` succeed.

## Dependencies

- [T-119](T-119-Web-Metro-graph.md). T-117 and T-118 have run by board order; all four host graphs must already exist.

## Context the next session needs

- MetroX `ViewModelGraph` supplies the `viewModelProviders`, `assistedFactoryProviders`, and `manualAssistedFactoryProviders` maps plus `metroViewModelFactory`. `MetroViewModelFactory` is a `ViewModelProvider.Factory` built from those maps, so a host graph that extends `ViewModelGraph` can expose `metroViewModelFactory`.
- Use `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` and `dev.zacsweers.metro:metrox-viewmodel-compose`, both at the T-114 Metro version. Do not add `androidx.lifecycle:lifecycle-viewmodel`.
- `App` keeps taking the core `AppGraph` as its first parameter. It also takes the factory it installs: production hosts pass the graph's `metroViewModelFactory`; the default is an `AnyDownloadViewModelFactory` with empty maps so the existing `App(graph)` test call sites keep compiling. Record the exact signature in Evidence.
- `ShellUiHarness` installs `LocalMetroViewModelFactory` too. Its factory map is empty in this task; T-122 maps `QueueViewModel` to `QueueViewModel(engine)`.
- If Wasm cannot see a shared/ui contribution, stop and move the card to Blocked with the compiler output.

## Work

- Add the version-catalog entries and dependencies. Add `kotlinx-coroutines-swing` to the desktop JVM source set/dependencies.
- Add `AnyDownloadViewModelFactory` in `shared/ui` commonMain:

  ```kotlin
  @Inject
  @ContributesBinding(AppScope::class)
  @SingleIn(AppScope::class)
  class AnyDownloadViewModelFactory(
      override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
      override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
      override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
  ) : MetroViewModelFactory()
  ```

  Adjust the exact map types to the MetroX `1.4.5` API and record it.
- Extend each host graph with `ViewModelGraph`.
- Have `App` provide `LocalMetroViewModelFactory` around its content, and `ShellUiHarness` do the same. Keep the empty test map.
- Run the two compile gates. Do not start the `QueueViewModel` work; that is T-122.

## Acceptance criteria

- [x] `shared/ui` commonMain has both ViewModel Compose dependencies at the Metro version; `androidx.lifecycle:lifecycle-viewmodel` is absent; desktop JVM has `kotlinx-coroutines-swing`.
- [x] Each host graph extends `ViewModelGraph`.
- [x] `AnyDownloadViewModelFactory` is `@Inject @ContributesBinding(AppScope::class) @SingleIn(AppScope::class)` and subclasses `MetroViewModelFactory`.
- [x] `App` keeps the core `AppGraph` first parameter and installs `LocalMetroViewModelFactory`; `ShellUiHarness` installs it too; the test factory map is empty.
- [x] `./gradlew :shared:ui:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` succeeds.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Dependencies:** `gradle/libs.versions.toml` adds `lifecycle = "2.10.0"`, `androidx-lifecycle-viewmodel-compose = org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`, `metrox-viewmodel-compose = dev.zacsweers.metro:metrox-viewmodel-compose` (Metro `1.4.5`), and `kotlinx-coroutines-swing`. `shared/ui` commonMain declares the first two as `api` so every host module can see `ViewModelGraph`, `MetroViewModelFactory`, and `LocalMetroViewModelFactory`; `apps/desktop` adds `kotlinx-coroutines-swing`. The MetroX `1.4.5` module metadata depends on JetBrains lifecycle `2.10.0`, so that is the aligned version. No `androidx.lifecycle:lifecycle-viewmodel` was added directly.
- **Factory:** `anydownload` — `@Inject @ContributesBinding(AppScope::class) @SingleIn(AppScope::class)` subclassing `MetroViewModelFactory` with the three multibinding maps as `override val` constructor parameters, exactly the MetroX shape.
- **Host graphs:** `DesktopGraph`, `AndroidAppGraph`, `IosAppGraph`, and `WebAppGraph` now extend `ViewModelGraph` as well as the core `AppGraph`.
- **Install:** `App(graph, viewModelFactory = null)` keeps the core `AppGraph` first parameter. It resolves the factory as the explicit parameter, else `(graph as? ViewModelGraph)?.metroViewModelFactory`, else a remembered empty-map `AnyDownloadViewModelFactory`, and wraps its content in `CompositionLocalProvider(LocalMetroViewModelFactory provides ...)`. `ShellUiHarness` does the same with an optional `viewModelFactory` parameter; its map is empty in this task, ready for T-122.
- **Wasm contribution check:** passed. `:apps:web:compileKotlinWasmJs` compiled the `WebAppGraph` extending `ViewModelGraph` and resolved the shared/ui `AnyDownloadViewModelFactory` contribution. The loop's “if Wasm cannot see a shared/ui contribution, stop” condition did not trigger.
- **Verification:** `./gradlew --console=plain :shared:ui:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64 :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs` → BUILD SUCCESSFUL in 6m 38s. Then `./gradlew --console=plain :shared:ui:jvmTest` → BUILD SUCCESSFUL, 99 tests, 0 failures; `./gradlew --console=plain :apps:desktop:cleanTest :apps:desktop:test` → BUILD SUCCESSFUL, 132 tests, 0 failures, 15 skipped. A first combined run hit the known load-sensitive `DesktopSpotifyDownloadGateTest` once; the desktop suite passed alone, matching the T-112 evidence note.
- No commit was made.
