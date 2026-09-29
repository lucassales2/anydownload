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

- [ ] `QueueViewModel` and `QueueUiState` exist with the listed fields and methods; the ViewModel is contributed with `@ViewModelKey` and `@ContributesIntoMap(AppScope::class)`.
- [ ] `state` combines `engine.jobs` with the state flows and calls `QueuePresenter.rows`.
- [ ] `QueueScreen` takes `onCopyUrls`, `onOpenSource`, and a `QueueViewModel` default of `metroViewModel()`; it does not call `engine.start`, `engine.cancel`, or read `engine.jobs`.
- [ ] `AppShell` passes `graph.openUrl` and no longer passes the graph into the list.
- [ ] The test factory maps `QueueViewModel::class` to `QueueViewModel(engine)`; `QueuePresenterTest` runs without a Compose rule and with `Dispatchers.setMain`.
- [ ] `./gradlew :shared:ui:jvmTest` passes, including `QueueHistoryUiTest`.

## Evidence / notes

Not started.
