---
id: T-027
type: task
priority: P0
milestone: D1
tags: [task, ui, desktop]
---

# T-027 — Desktop app shell

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [User flows](../01-product/User-flows.md)

## Outcome

The desktop window is an AnyDownload shell for MeTube's sections: header, add region, and tabs for Downloading, Completed, and Subscriptions, plus a way to open Settings. The server capabilities card is gone. Other platform hosts still compile and show the same shell on the in-memory fake.

## Dependencies

- [T-026](T-026-Shared-domain-and-engine-seam.md).

## Context the next session needs

Today `shared/ui/.../App.kt` loads `AnyDownloadApi` and shows a "Server" card via `HomePresenter`. `AppConfig` still has `serverBaseUrl`. These hosts call `App()` with no arguments:

- `apps/desktop/src/jvmMain/kotlin/com/anydownlod/desktop/Main.kt`
- `apps/android/src/main/kotlin/com/anydownlod/android/MainActivity.kt`
- `apps/web/src/wasmJsMain/kotlin/com/anydownlod/web/Main.kt`
- `shared/ui/src/iosMain/kotlin/com/anydownlod/ui/MainViewController.kt`

`shared/ui/build.gradle.kts` exposes `shared/network` as an `api` dependency. After this task the shell must not call the API. If nothing else in `shared/ui` needs `shared/network`, drop that dependency. Do not delete the `shared/network` module.

The window starts at 1000×700. The add form in T-028 needs more width; set the default near 1100×800 in `Main.kt` as part of this task so later screens are not clipped.

## Work

Replace `App`, `HomePresenter`, and `HomeState` with a shell that takes the T-026 graph (engine, subscription repository, settings repository). Default the parameter to `InMemoryAppGraph` so the four hosts keep compiling without each of them constructing dependencies. Desktop may pass an explicit graph later; this task uses the fake everywhere, including desktop.

Header:

- Title "AnyDownload".
- Theme control bound to settings: system, light, dark. Apply it with Material 3 color schemes. System follows the OS theme on desktop.
- A Settings button that opens the settings surface. The surface can be an empty titled placeholder until T-031. Closing it returns to the same tab.
- No server URL, no API version, no login.

Body:

- A slot above the tabs for the add form. Until T-028, the slot is a short explanation that a URL goes here, not a second copy of the old scaffold text.
- Tabs: **Downloading**, **Completed**, **Subscriptions**. Remember the selected tab while Settings is open.
- Each tab has its own empty state with the primary action named in text ("Add a URL", "Finished downloads appear here", "Subscribe from the add form"). Do not build the real rows in this task.

Keyboard: the tab list and Settings button are focusable and operable with the keyboard. Theme is not indicated by color alone; the control has a text label.

Do not add bottom navigation. D1 is a desktop window. Keep the three list regions as separate composables so a later milestone can stack them.

`apps/desktop` is the only host this task is required to run. Android, iOS, and web only need to compile. Do not add process code, file dialogs, or a real download.

Delete the capabilities UI and `HomePresenter`. Update comments in `App.kt` that still say the screens are undesigned.

## Verification

- `./gradlew :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin`
- `./gradlew :apps:desktop:run` and click every tab, open and close Settings, and switch theme. Quit the window.
- Compile at least one other host if the toolchain is present (`:apps:android:assembleDebug` or `:apps:web:compileKotlinWasmJs`). If it is not present, say so in Evidence. Do not spend the task installing SDKs.

## Out of scope

Form fields, job rows, subscription rows, settings fields, persistence, yt-dlp.

## Acceptance criteria

- [x] Desktop launches into the shell. The server card and `HomePresenter` are gone.
- [x] Header has the title, a labeled theme control bound to settings, and Settings open/close.
- [x] Downloading, Completed, and Subscriptions show distinct empty states. The add slot is reserved above the tabs.
- [x] Android, iOS, and web entry points still call `App()` and compile against the in-memory fake. `shared/ui` no longer needs `shared/network` unless a comment explains the remaining use.
- [x] Evidence records the Gradle commands and what was clicked in the desktop window.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :shared:ui:cleanJvmTest :shared:ui:jvmTest :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL. `AppShellTest` ran 2 tests, 0 failures.
- `./gradlew :apps:desktop:run` — an `AnyDownload` window opened (java process `com.anydownlod.desktop.MainKt`), the Gradle log had no exception/error lines, and the window closed cleanly.

Click-through: the host session does not have macOS Accessibility or Screen Recording permission (`cliclick` reports "Accessibility privileges not enabled" and `screencapture` fails), so OS-level clicking is not possible here. The same clicks were performed headlessly through the Compose test runtime in `shared/ui/src/jvmTest/.../AppShellTest.kt`, which clicks Completed, Subscriptions, Settings, Close, Dark, and Light against the real composables. It asserts that each tab shows its own empty state, that closing Settings returns to Subscriptions (not the first tab), and that the theme control writes `LIGHT`/`DARK` through `SettingsRepository`. The desktop window launch itself was verified by process and log.

What landed:

- `App(graph: AppGraph = remember { InMemoryAppGraph() })` replaces the old capabilities scaffold. `HomePresenter.kt` and the server card are deleted; no host code changed because they all call `App()`.
- `com.anydownlod.ui.shell`: `AppShell` (header, add slot, `PrimaryTabRow` for Downloading/Completed/Subscriptions), `ShellTab`, `AppHeader` (title, labeled System/Light/Dark `FilterChip`s bound to `SettingsRepository`, Settings button), `EmptyStatePanel`.
- `com.anydownlod.ui.add`, `.queue`, `.history`, `.subscriptions`: placeholder add card and one distinct empty state per tab, ready for T-028/T-029/T-030 to replace.
- `com.anydownlod.ui.settings.SettingsPlaceholder`: full-window titled surface with a Close button.
- `shared/ui` no longer depends on `shared/network`; it keeps `api(project(":shared:core"))` for the graph types.
- `apps/desktop`: window default is 1100×800 and `src/jvmMain/kotlin` is registered in the Kotlin JVM module, so `Main.kt` actually compiles (it was `NO-SOURCE`).
- `shared/ui` now has a `jvmTest` source set with `compose.uiTest`/`compose.desktop.currentOs`, so later presenter tests can run headless.
