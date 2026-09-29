---
id: T-122
type: task
priority: P0
milestone: D9
tags: [task, metro, viewmodel]
---

# T-122 — QueueViewModel and QueueScreen

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

`QueueViewModel` owns the Downloading list state in `shared/ui` commonMain. `QueueScreen` only lays out, resolves strings, and forwards events. `AppShell` passes `graph.openUrl` to the list and stops passing the graph into it. The test factory maps `QueueViewModel` to a real ViewModel over an `InMemoryDownloadEngine`, and `:shared:ui:jvmTest` passes including `QueueHistoryUiTest`.

## Dependencies

- [T-121](T-121-ViewModel-factory.md).

## Context the next session needs

- `QueuePresenter` keeps the pure `rows(jobs)` mapping (it still decides which states are in the list and builds `QueueRow`s). The ViewModel calls `QueuePresenter.rows`; the screen never computes rows.
- `stringResource` and `LocalClipboardManager` stay in the composable. The ViewModel returns `UiText` for `statusMessage` and URL strings for the copy actions. The screen writes to the clipboard.
- `QueueScreen` must not call `engine.start`, `engine.cancel`, or read `engine.jobs`. That is the T-120 invariant; keep it grep-able.
- Add, Preview, Settings, History, and Subscriptions keep their presenters. Do not refactor them here.
- The test factory map from T-121 stays test-only. `QueueViewModel` is contributed with `@ViewModelKey`/`@ContributesIntoMap` for the production graph.

## Work

- Add `QueueViewModel` in `shared/ui` commonMain:

  ```kotlin
  @Inject
  @ViewModelKey
  @ContributesIntoMap(AppScope::class)
  class QueueViewModel(private val engine: DownloadEngine) : ViewModel()
  ```

- `QueueUiState` holds `rows`, `selectedIds`, `pendingCancelIds`, `statusMessage: UiText?`, `working`, `notStarted`, and `batchCopyEnabled`.
- `state` combines `engine.jobs` with the selection, pending-cancel, and status flows and calls `QueuePresenter.rows`.
- Methods: `toggle`, `toggleAll`, `startSelected`, `requestCancelSelected`, `confirmCancel`, `dismissCancel`, `copySelected` (returns the selected URL strings), `copyBatch` (returns the batch URL strings), and `noteCopied` (sets the status message).
- The rules to preserve from the current screen: start only `PENDING`/`SCHEDULED`; cancel only non-terminal rows; rows that need confirmation set `pendingCancelIds` instead of cancelling immediately; selection is pruned when rows disappear; batch copy includes every child of the selection’s parent batches.
- Rewrite `QueueScreen` as `QueueScreen(onCopyUrls: (String) -> Unit, onOpenSource: (String) -> Unit, modifier: Modifier = Modifier, viewModel: QueueViewModel = metroViewModel())`. Keep the existing testTags; keep the confirm dialog.
- Update `AppShell` to call `QueueScreen(onCopyUrls = onCopyUrls, onOpenSource = graph.openUrl)` and remove the graph from the list call.
- Rewrite `QueuePresenterTest` against `QueueViewModel` and `InMemoryDownloadEngine` using `Dispatchers.setMain`; no Compose rule. Cover the row mapping, bulk start/cancel, selection pruning, the confirm flow, and the copy URLs.
- Fill the test factory map: `QueueViewModel::class` to `QueueViewModel(engine)`, so `QueueHistoryUiTest` and the other shell tests run the real ViewModel.

## Acceptance criteria

- [x] `QueueViewModel` and `QueueUiState` exist with the listed fields and methods; the ViewModel is contributed with `@ViewModelKey` and `@ContributesIntoMap(AppScope::class)`.
- [x] `state` combines `engine.jobs` with the state flows and calls `QueuePresenter.rows`.
- [x] `QueueScreen` takes `onCopyUrls`, `onOpenSource`, and a `QueueViewModel` default of `metroViewModel()`; it does not call `engine.start`, `engine.cancel`, or read `engine.jobs`.
- [x] `AppShell` passes `graph.openUrl` and no longer passes the graph into the list.
- [x] The test factory maps `QueueViewModel::class` to `QueueViewModel(engine)`; `QueuePresenterTest` runs without a Compose rule and with `Dispatchers.setMain`.
- [x] `./gradlew :shared:ui:jvmTest` passes, including `QueueHistoryUiTest`.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **ViewModel:** `shared/ui/src/commonMain/kotlin/com/anydownlod/ui/queue/QueueViewModel.kt` adds `@Inject @ViewModelKey @ContributesIntoMap(AppScope::class) class QueueViewModel(private val engine: DownloadEngine) : ViewModel()` and `QueueUiState(rows, selectedIds, pendingCancelIds, statusMessage, working, notStarted, batchCopyEnabled)`. `state` is a `combine(engine.jobs, selectedIds, pendingCancelIds, statusMessage)` over `QueuePresenter.rows`, started eagerly in `viewModelScope`. An init collector prunes selection and pending ids when a row leaves the list (the old screen-side `LaunchedEffect`).
- **Methods:** `toggle`, `toggleAll`, `startSelected`, `requestCancelSelected`, `confirmCancel`, `dismissCancel`, `copySelected`, `copyBatch`, `noteCopied`. `startSelected(ids = selectedIds)` and `requestCancelSelected(ids = selectedIds)` carry an optional id set so the row-level Start and Cancel buttons go through the same ViewModel decisions without new method names. `noteCopied` sets `UiText.of(Res.string.copied_urls, count)`; the copy methods return URL strings and the screen does the clipboard write.
- **Screen:** `QueueScreen(onCopyUrls, onOpenSource, modifier, viewModel = metroViewModel())` renders `state` and forwards events only. `stringResource` and `LocalClipboardManager` stay in the composable. `grep -n "engine\.\(start\|cancel\|jobs\)"` and `grep -n AppGraph` on the file both return nothing, and the only call site is `AppShell` (`QueueScreen(onCopyUrls = onCopyUrls, onOpenSource = graph.openUrl)`).
- **Tests:** `ShellUiHarness` now maps `QueueViewModel::class` to `QueueViewModel(graph.engine)` in its fallback factory, so the shell click-through uses the real ViewModel. `QueuePresenterTest` was rewritten against `QueueViewModel` and `InMemoryDownloadEngine` with `Dispatchers.setMain(UnconfinedTestDispatcher())` and `resetMain`, no Compose rule. It covers row filtering, indeterminate/known progress, unknown state, start and cancel rules, the confirm flow, toggle/toggle-all, selection pruning, batch copy (including completed/failed siblings and the forbidden-token check), disabled batch copy, and the copied status message.
- **Verification:** `./gradlew --console=plain :shared:ui:jvmTest` → BUILD SUCCESSFUL in 32s; 103 tests, 0 failures (`QueuePresenterTest` 13/0, `QueueHistoryUiTest` 4/0, `AppShellTest` 2/0). Then `./gradlew --console=plain :shared:ui:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64 :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs` → BUILD SUCCESSFUL in 44s.
- No commit was made.
