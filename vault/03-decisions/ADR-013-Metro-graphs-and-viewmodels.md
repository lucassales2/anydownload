---
id: ADR-013
type: adr
status: proposed
created: 2026-09-29
tags: [architecture, decisions, metro, viewmodel]
---

# ADR-013 — Metro dependency graphs and MetroX ViewModels

[Home](../Home.md) · [Decision log](Decision-log.md) · [Metro loop prompt](../00-project/Metro-Loop-prompt.md) · [Kanban](../Kanban.md)

Does not supersede any earlier ADR. [ADR-004](ADR-004-Local-kotlin-engine.md) remains the end state. This record changes how the four hosts assemble the objects that already exist. It does not change download behavior, the extractor catalog, the engine contract, or the product workflows of the screens.

## Context

The four hosts hand-build the same object graph, and one screen owns decisions that should be testable without Compose:

- `AndroidExtractors`, `IosExtractors`, `WebAppGraph`, desktop `Main.kt`, and `DesktopPreviewSource` each repeat the production extractor list (`YoutubeIE`, `YoutubeTabIE`, `TwitterIE`). A change to that list must be made in five places.
- Each host separately constructs `ExtractorHttp`, a `JsRuntime`, and an `ExtractorRegistry`. Desktop builds `DesktopSpotifyTokenStore(stateDirectory)` twice, once for the library and once for the auth service.
- `QueueScreen` owns `selectedIds`, `pendingCancelIds`, and `statusMessage`. `QueuePresenter` is only used for pure row mapping; the “which rows start”, “when cancel asks for confirmation”, and “what copies” rules live in the composable and cannot be exercised without the Compose test rule.
- The project has no dependency-injection container. [ADR-012](ADR-012-On-device-core-phase.md) closed M1 and M2 with the same host wiring.

Kotlin is pinned at `2.4.20` (`gradle/libs.versions.toml`). Metro’s compatibility page (checked 2026-09-29) lists Kotlin `2.4.20` for Metro `1.2.0` and newer, and Metro `1.4.5` (2026-09-24) is the newest stable release then. Metro supports Wasm and Apple aggregation at that Kotlin version. The owner directed the migration plan on 2026-09-29.

## Proposed decision

**Phase D9 (Metro)** is the next refactor work: [T-113](../06-tasks/T-113-Metro-migration-plan.md) through [T-120](../06-tasks/T-120-Metro-verification.md), with [T-120](../06-tasks/T-120-Metro-verification.md) as the verification gate. The loop prompt is [Metro-Loop-prompt](../00-project/Metro-Loop-prompt.md). No product behavior changes.

**Part 1 — graphs.**

- Pin Metro **1.4.5** — the newest stable whose compatibility page lists Kotlin `2.4.20` — with `apply false` on the root, then applied on `shared/core`, `shared/ui`, `apps/android`, `apps/desktop`, and `apps/web`. Not on `shared/network`, `tools/port-manifest`, or `apps/android-engine-tests`.
- `SharedEngineBindings` is a `@BindingContainer` in `shared/core` commonMain. Its ordered `@Provides` are `ExtractorHttp`, `YoutubeIE(http, jsRuntime)`, `YoutubeTabIE(http)`, `TwitterIE(http)`, and `ExtractorRegistry(listOf(youtube, youtubeTab, twitter))`, each `@SingleIn(AppScope::class)`. The playlist tab stays in the list: [T-108](../06-tasks/T-108-Youtube-playlist-subset.md) shipped playlist expansion in D8, and this migration must not regress it. No `GenericIE` and no `@IntoSet` for extractors.
- `@DependencyGraph` is declared only in the host source sets: desktop `jvmMain`, Android `main`, `shared/ui` `iosMain`, and web `wasmJsMain`. The graphs implement the existing `AppGraph` contract and are created with Metro’s `createGraph` or `createGraphFactory` intrinsics.
- Each graph owns one `HttpTransfer`, one `JsRuntime`, one `ExtractorRegistry`, and one host `CoroutineScope`, all `@SingleIn(AppScope)`. Desktop also scopes one `DesktopSpotifyTokenStore`.
- `AppGraph` stays the type `App()` takes. `InMemoryAppGraph` stays in `shared/core` and does not reference ViewModel types. `HttpDownloadEngine` keeps its normal constructor; it is not `@Inject`ed.

**Part 2 — ViewModels.**

- `shared/ui` commonMain adds `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` and `dev.zacsweers.metro:metrox-viewmodel-compose` at the Metro plugin version. The Android `androidx.lifecycle:lifecycle-viewmodel` artifact is not added. Desktop JVM adds `kotlinx-coroutines-swing` so the ViewModel’s `Dispatchers.Main` works there.
- Each host graph extends MetroX `ViewModelGraph`. `AnyDownloadViewModelFactory` is `@Inject`, `@ContributesBinding(AppScope::class)`, `@SingleIn(AppScope::class)`, and subclasses `MetroViewModelFactory`. `App` and the test shell install `LocalMetroViewModelFactory`.
- `QueueViewModel` is `@Inject`, `@ViewModelKey`, `@ContributesIntoMap(AppScope::class)`, and takes the `DownloadEngine`. It owns selection, start, cancel confirmation, and copy decisions. `QueueScreen` only lays out, resolves strings, and forwards events; it never calls `engine.start`, `engine.cancel`, or reads `engine.jobs`. `stringResource` and `LocalClipboardManager` stay in the composable; the ViewModel returns `UiText` and URL strings.
- Add, Preview, Settings, History, and Subscriptions keep their presenters. `QueuePresenter` keeps the pure `rows` mapping that `QueueViewModel` calls.

## Alternatives

- **Keep the hand-wired graphs.** Rejected: five copies of the production list, per-host divergence, and manual singleton bookkeeping (the double `DesktopSpotifyTokenStore`) already caused duplication.
- **Koin, kotlin-inject, Dagger, or Hilt.** Rejected: the rules call for Metro annotations only, no `javax.inject` interop. Metro’s compiler-generated graphs also validate at build time and support the Wasm and Apple targets at Kotlin `2.4.20`.
- **One `@DependencyGraph` in `commonMain`.** Rejected: Metro documents that a graph defined in common code cannot see platform-specific contributions; each host source set must own its final graph. A temporary smoke graph in `shared/core` commonMain only proves the plugin in T-114 and is removed in T-115.
- **Plain state-holder classes instead of MetroX ViewModels.** Rejected: the ViewModel keeps lifecycle-aware state and reuses the existing `QueuePresenter` rules, and MetroX is built on the multiplatform lifecycle the project already implies through Compose.
- **Move only the extractor list, leave the UI as is.** Rejected: the largest testability gap is the screen-owned queue state, which Part 2 removes.

## Consequences

Benefits: one production extractor list; compile-time graph validation; selection, cancel, and copy rules testable through `QueueViewModel` without Compose; one token store; hosts stop hand-building the graph.

Costs and risks: new Gradle plugin and runtime dependencies; the Metro pin is coupled to Kotlin `2.4.20`; generated code grows in the build. If the Metro plugin fails on Kotlin `2.4.20`, the loop stops and records a blocker — Kotlin is not downgraded. If Metro rejects a function-typed `@Provides` factory input, the input is wrapped in a small class and recorded in the task Evidence. Behavior stays: playlist expansion, preview routing to the same registry, Android’s Chaquopy fallback for unmatched URLs, web `?solverHook=1`, queue persistence, and downloads.

## Validation / approval

Directed by the owner on 2026-09-29. Accepted by [T-120](../06-tasks/T-120-Metro-verification.md) only after its ten-command suite passes and the three invariants hold: `shared/core` commonMain has no `ProcessBuilder`, `App()` still takes `AppGraph`, and `QueueScreen` does not call `engine.start`, `engine.cancel`, or read `engine.jobs`. Proposed until then.
