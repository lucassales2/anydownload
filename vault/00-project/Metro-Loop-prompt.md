---
type: prompt
milestone: D9
tags: [project, metro, viewmodel, handoff]
---

# Metro loop prompt

[Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [T-113](../06-tasks/T-113-Metro-migration-plan.md)

Paste the block below as the first message in a new agent session after T-113 is done. It implements the Metro migration one task at a time until [T-120](../06-tasks/T-120-Metro-verification.md) is done or a task is blocked.

Do not also arm a short `/loop` interval against this prompt. A second wake will edit the same files.

## Prompt

```
Goal: Implement AnyDownload Metro graphs and MetroX ViewModels, one #metro Kanban task at a time, until T-120 is Done or a task is blocked. Do not start if any #D8 card is In progress or T-112 is not Done. This loop does not edit D8 notes or move D8 cards. Do not change download behavior.

Part 1 is Metro. It generates the four host graphs. AppGraph stays the type App() takes. InMemoryAppGraph stays in shared/core and does not reference ViewModel types. HttpDownloadEngine stays a normal constructor. Part 2 is ViewModel. MetroX creates QueueViewModel. QueueScreen renders state and forwards events. It does not decide selection, cancel confirmation, or which jobs start. Add, Preview, Settings, History, and Subscriptions keep their presenters.

- T-113 — Docs only. ADR-013. Copy this prompt to vault/00-project/Metro-Loop-prompt.md. Task notes T-114 through T-122. #metro cards in Backlog. No Gradle change.
- T-114 — Pin the newest Metro whose compatibility page lists Kotlin 2.4.20. Plugin, apply false on the root, then on shared/core, shared/ui, apps/android, apps/desktop, apps/web. Not on shared/network, tools/port-manifest, or android-engine-tests. Smoke graph in shared/core commonMain. Prove :shared:core:jvmTest, :shared:core:compileKotlinWasmJs, :shared:core:compileKotlinIosSimulatorArm64, and :apps:android:assembleDebug. If the plugin fails on Kotlin 2.4.20, stop. Do not downgrade Kotlin.
- T-115 — SharedEngineBindings in commonMain. @Provides extractorHttp, youtube(http, jsRuntime), twitter(http), and registry as ExtractorRegistry(listOf(youtube, twitter)). Delete the copied lists in AndroidExtractors, IosExtractors, WebAppGraph, desktop Main.kt, and DesktopPreviewSource. DesktopPreviewSource.create takes that registry and has no JavaNetHttpTransfer() default. android-engine-tests and the iOS test still build the production list through the public function the @Provides calls. :apps:android-engine-tests:test and :shared:core:jvmTest pass.
- T-116 — DesktopApp.open becomes createGraphFactory. Factory inputs stay stateDirectory, defaultDownloadRoot, processRunner, resolveExecutable, and ioDispatcher. One registry shared by preview, HTTP engine, and CLI routing. One DesktopSpotifyTokenStore. DesktopApp still loads DesktopStore, saves on close, and shuts the CLI down. DesktopAppRelaunchTest still compiles. A test shows the preview registry is the graph registry. :apps:desktop:test passes.
- T-117 — Android graph from Context and ChaquopyPort, default NoChaquopyPort. One JavaNetHttpTransfer, one QuickJsRuntime, shared registry, AndroidRoutingEngine, base Spotify helper. Unmatched URLs still go to Chaquopy. :apps:android:assembleDebug and :apps:android-engine-tests:test pass.
- T-118 — iOS graph in shared/ui iosMain. MainViewController uses createGraph. One IosHttpTransfer, one QuickJsRuntime, IosFileStore under Documents, HttpDownloadEngine, IosMediaToolkit. :shared:ui:iosSimulatorArm64Test and :shared:core:iosSimulatorArm64Test pass.
- T-119 — Web graph. One WindowExtensionBridge, one BrowserJsRuntime, one WebExtensionTransfer, WebExtensionEngine. solverHook=1 still works. :apps:web:wasmJsBrowserDistribution succeeds. The page does not fetch arbitrary origins.
- T-121 — ViewModel factory. shared/ui commonMain gets org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose and dev.zacsweers.metro:metrox-viewmodel-compose at the Metro plugin version. Do not add androidx.lifecycle:lifecycle-viewmodel. Desktop JVM gets kotlinx-coroutines-swing. Each host graph extends ViewModelGraph. AnyDownloadViewModelFactory is @Inject @ContributesBinding(AppScope::class) @SingleIn(AppScope::class) and subclasses MetroViewModelFactory. App and ShellUiHarness install LocalMetroViewModelFactory. The test factory map is empty until T-122. :shared:ui:compileKotlinWasmJs and :shared:ui:compileKotlinIosSimulatorArm64 succeed. If Wasm cannot see a shared/ui contribution, stop.
- T-122 — QueueViewModel in shared/ui commonMain: @Inject @ViewModelKey @ContributesIntoMap(AppScope::class), constructor takes DownloadEngine. QueueUiState holds rows, selectedIds, pendingCancelIds, statusMessage, working, notStarted, and batchCopyEnabled. state combines engine.jobs with those flows and calls QueuePresenter.rows. Methods: toggle, toggleAll, startSelected, requestCancelSelected, confirmCancel, dismissCancel, copySelected, copyBatch, noteCopied. QueueScreen(onCopyUrls, onOpenSource, viewModel = metroViewModel()) only lays out, resolves strings, and shows the confirm dialog. AppShell passes graph.openUrl and stops passing the graph into the list. The test factory maps QueueViewModel::class to QueueViewModel(engine). Rewrite QueuePresenterTest against QueueViewModel and InMemoryDownloadEngine with Dispatchers.setMain. No Compose rule in that test. :shared:ui:jvmTest passes, including QueueHistoryUiTest.
- T-120 — Run ./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :apps:web:wasmJsBrowserDistribution :tools:port-manifest:check. Confirm shared/core commonMain has no ProcessBuilder. Confirm App() still takes AppGraph. Confirm QueueScreen does not call engine.start, engine.cancel, or read engine.jobs. Accept ADR-013.

T-115 depends on T-114. T-116 depends on T-115. T-117, T-118, and T-119 depend on T-116. T-121 depends on T-119. T-122 depends on T-121. T-120 depends on T-122.

Rules:
- Metro annotations only. No javax.inject, Dagger or Hilt interop, or Koin.
- @DependencyGraph only in the host source set: desktop jvmMain, Android main, shared/ui iosMain, web wasmJsMain.
- Extractors are an ordered @Provides list: YoutubeIE, then TwitterIE. No GenericIE. No @IntoSet for extractors.
- @ContributesIntoMap and @ContributesBinding are only for ViewModels: @ViewModelKey on QueueViewModel, and one binding for AnyDownloadViewModelFactory.
- Do not @Inject YoutubeIE or HttpDownloadEngine. YoutubeIE keeps its jsRuntime default for tests. The graph @Provides requires JsRuntime.
- One @SingleIn(AppScope) each for HttpTransfer, JsRuntime, ExtractorHttp, ExtractorRegistry, and the host CoroutineScope. Desktop scopes one token store. Use Metro's AppScope if it already exists.
- Do not put a @DependencyGraph under com.anydownlod.android.engine.
- If Metro rejects a function-type @Provides parameter, wrap it in a small class and record that in Evidence.
- Declare the Metro plugin with apply false on the root, then apply it in each module.
- stringResource and LocalClipboardManager stay in the composable. The ViewModel returns UiText and URL strings.
- Do not commit. Do not vendor yt-dlp. Common code has no ProcessBuilder.

Loop:
1. Read vault/Kanban.md. Consider only cards tagged #metro (T-113 through T-122).
2. If a #metro card is In progress, resume it. Otherwise take the Ready #metro card. If none is Ready, take the first Backlog #metro card whose dependencies are Done.
3. If T-113 is not Done and the vault notes do not exist, T-113 is the eligible task. Write the notes from this prompt. Do not start T-114 in that same turn.
4. If none is eligible, or T-120 is Done, stop. Summarize which #metro tasks are Done.
5. Move that card to In progress before editing.
6. Implement only that task. The acceptance text in this prompt is the definition of done until the task note exists; after T-113, the task note is the definition of done.
7. Run the verification that task names. On failure, fix it before leaving the task. If you cannot finish, move the card to Blocked, write the blocker and the next action under Evidence, and stop.
8. When the criteria pass, check the boxes, write Evidence with the date and the commands you ran, and move the card to Done.
9. Go back to step 1. When you stop, report the last task you finished, the verification you actually ran, and the next eligible task if the migration is not done.

Work toward the goal. When the goal is fully met, call loop_control with status "done" and explain why. If more work is needed, call loop_control with status "next" describing what's left.
```

## Notes for the next session

- T-113 wrote this file plus [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) and the task notes T-114 through T-122. No Gradle file changed.
- The board cards are tagged `#metro`; T-114 starts from Backlog because it has no dependency.
- The Metro version to pin is the newest stable whose compatibility page lists Kotlin `2.4.20`; checked 2026-09-29, that is `1.4.5`.
