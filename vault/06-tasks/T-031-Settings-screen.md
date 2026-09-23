---
id: T-031
type: task
priority: P1
milestone: D1
tags: [task, ui, settings]
---

# T-031 — Settings screen

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

Settings edits the local preferences MeTube exposes as environment variables: folders, filename templates, concurrency, cookies status, presets, theme, and tool versions. Values go through `SettingsRepository`. The desktop host can pick a folder. Cookie bytes are never shown.

## Dependencies

- [T-027](T-027-Desktop-app-shell.md). Theme already lives in the header; this screen edits the same setting.

## Context the next session needs

There is no server config file in D1. Everything MeTube would read from the environment becomes a control on this screen, except the withdrawn server rows (bind address, URL prefix, TLS, robots, directory listing).

Reviewed MeTube defaults to preserve when the repository is empty:

| Setting | Default |
| --- | --- |
| Output template | `%(title)s.%(ext)s` |
| Playlist template | `%(playlist_title)s/%(title)s.%(ext)s` |
| Channel template | `%(channel)s/%(title)s.%(ext)s` |
| Chapter template | `%(title)s - %(section_number)02d - %(section_title)s.%(ext)s` |
| Max concurrent downloads | 3 |
| Clear completed after | 0 seconds, meaning off |
| Subscription default interval | 60 minutes |
| Theme | system |

The download root is a directory the desktop user picks. The in-memory fake may leave it blank; the screen then says a folder is required before a real download and still lets the rest of the form be edited. T-033 enforces the requirement when the CLI engine is wired. This task only has to show the gap.

Cookie section copy, from the user flows: the file stays on this computer, it can expose an account, and importing it does not guarantee the site will allow the download. Status is **Not configured** or **Configured**. Never display names, values, or file contents. Actions: Import, Replace, Delete. They call repository methods and a host callback `pickCookieFile` that the fake/default leaves unimplemented (button explains that import is wired on desktop in T-035). Deleting configured state is immediate after a confirm. Do not read the file in this task.

Presets: list of name plus a short summary of keys. Add opens a dialog for a name and a small structured form of allowlisted keys only (the same tokens the add form already has: concurrent-independent format extras can wait for T-035). If a structured preset editor is too large, ship add/remove/reorder of named presets whose payload is built from checkboxes you document in the task evidence, not a free-form JSON text area. Free-form JSON is the Q-09 field and stays out of Settings too.

Tools: show yt-dlp and ffmpeg as "Not checked" until a probe exists. T-033 fills the probe. Do not spawn a process here.

Concurrency is a positive integer. Clear-completed-after is a non-negative integer, labeled in minutes in the UI if that is clearer, stored as seconds on the model. 0 remains off.

Templates are text fields. Reject a template that contains an absolute path or a `..` segment. yt-dlp template tokens such as `%(title)s` are allowed. This is a string check, not a yt-dlp run.

## Work

Replace the T-027 Settings placeholder in `com.anydownlod.ui.settings`.

Sections, in order: Storage, Filenames, Queue, Cookies, Presets, Appearance, Tools.

Storage: current download root, **Choose folder** button. The button invokes `pickFolder: () -> String?` supplied by the host. `apps/desktop` implements it with a JVM directory chooser (`java.awt.FileDialog` or `JFileChooser` in `DIRECTORIES_ONLY` mode). Other hosts pass a callback that returns null, and the button can show "Folder selection is available on desktop". Saving a chosen path calls `SettingsRepository`.

Filenames: the four templates and the filename-prefix note pointing at the add form (prefix stays per download, not a global setting, unless you find the add form already editing it — do not duplicate the prefix here).

Queue: max concurrent downloads, clear-completed-after.

Cookies and presets and tools as described above.

Appearance: the same system/light/dark setting as the header. Changing it here updates the header.

Keep labels visible, not placeholder-only. Restore-defaults sets the template and concurrency defaults above after a confirm, and does not clear the cookie file or the download root.

Tests: template rejection for `..` and absolute paths, concurrency below 1 rejected, cookie repository update never takes a file body, restore-defaults leaves the cookie flag alone.

## Verification

Desktop: change theme from Settings and see the shell update, reject a bad template, set concurrency to 2, open the folder chooser and select a directory, confirm the path is shown. Run the tests.

## Out of scope

Writing the JSON store (T-032), running yt-dlp, parsing a cookie file (T-035), applying templates to a real download (T-034).

## Acceptance criteria

- [x] Settings shows storage, four templates, concurrency, clear-completed, cookies status, presets, theme, and tool rows.
- [x] Desktop folder picking stores the chosen directory. Non-desktop hosts still compile.
- [x] Cookie actions never display or accept file contents. Import is visibly not finished until T-035.
- [x] Custom yt-dlp JSON is not an editor on this screen.
- [x] Unsafe templates and concurrency below 1 are rejected. Restore-defaults does not clear cookies or the download root.
- [x] Tests and the desktop click-through are in Evidence.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :shared:core:jvmTest :shared:ui:cleanJvmTest :shared:ui:jvmTest :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL, 107 tests total (core 47, shared/ui 60), 0 failures. New suites: `SettingsPresenterTest` 7 and `SettingsUiTest` 4.
- `./gradlew :apps:desktop:run` — the window opened (seeded fake graph), no exception/error lines, and it closed cleanly.

Click-through: macOS Accessibility/Screen Recording permission is still not granted, so the clicks ran through the Compose test runtime in `SettingsUiTest`. Performed: opened Settings and saw Storage, Filenames, Queue, Cookies, Presets, Appearance, and Tools (with yt-dlp/ffmpeg rows from the probe); rejected `../%(title)s.%(ext)s` with the inline ".." error and no write; saved `%(title)s-%(id)s.%(ext)s`; rejected concurrency 0 and saved 2; clicked Choose folder and stored the host-chosen path; picked Dark and confirmed the header chip is selected after closing; with cookies configured, clicked Import (host callback returned a path; the T-035 notice was shown) then Delete with its confirm and saw status return to Not configured; added a preset named Small with `writeMetadata`, added Large, moved Large up, removed it; restored defaults and confirmed templates/concurrency reset while the download root, cookie flag, and presets stayed. Presenter tests add absolute-path/parent-segment/empty-segment template rejection, minutes-to-seconds conversion, cookie flag having no contents API, and preset add/move/remove ordering.

What landed:

- `com.anydownlod.core.AppGraph` gained `pickFolder` and `pickCookieFile` host callbacks with null/no-op defaults. Desktop `main` implements `pickFolder` with a `JFileChooser` in `DIRECTORIES_ONLY` mode; `pickCookieFile` stays unimplemented until T-035, and the screen explains that instead of pretending to import.
- `com.anydownlod.ui.settings.SettingsPresenter`: validated template/concurrency/clear-completed setters, native path picking, preset add/remove/move, cookie flag only, and `restoreDefaults` that preserves the download root, cookie flag, and presets.
- `com.anydownlod.ui.settings.SettingsScreen` replaces the T-027 placeholder with the seven ordered sections, visible labels, inline errors, a live tool probe, cookie copy that never shows contents, a structured preset dialog, and a restore-defaults confirm. `SettingsPlaceholder.kt` is deleted.
- Preset editor allowlist documented for T-035: `embedSubtitles`, `writeMetadata`, `writeThumbnail`, `splitByChapters`, `sponsorBlockRemove` (booleans only; free-form JSON stays disabled).
