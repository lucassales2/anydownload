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

- [ ] `shared/ui` commonMain has both ViewModel Compose dependencies at the Metro version; `androidx.lifecycle:lifecycle-viewmodel` is absent; desktop JVM has `kotlinx-coroutines-swing`.
- [ ] Each host graph extends `ViewModelGraph`.
- [ ] `AnyDownloadViewModelFactory` is `@Inject @ContributesBinding(AppScope::class) @SingleIn(AppScope::class)` and subclasses `MetroViewModelFactory`.
- [ ] `App` keeps the core `AppGraph` first parameter and installs `LocalMetroViewModelFactory`; `ShellUiHarness` installs it too; the test factory map is empty.
- [ ] `./gradlew :shared:ui:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` succeeds.

## Evidence / notes

Not started.
