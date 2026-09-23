---
type: phase
status: done
milestone: D1
tags: [project, desktop, ui, delivery]
---

# Phase 1 — Desktop MeTube

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-005](../03-decisions/ADR-005-Desktop-metube-phase.md) · [Feature parity](../01-product/Feature-parity.md)

**Verified 2026-09-21 — see [T-036](../06-tasks/T-036-Phase-1-verification.md) for the twelve-step run-through, commands, and tool versions.**

**Start here if you are implementing.** This note is the handoff for Phase D1. ADR-004 remains the end state (in-process Kotlin engine on every target). This phase does not implement that port.

Owner direction, 2026-09-21: port MeTube's workflows into Compose, run on desktop only, and perform downloads with the yt-dlp already installed on the machine. Do not port yt-dlp.

## Done

`./gradlew :apps:desktop:run` opens one desktop window that can add a URL with MeTube's options, show a live queue, keep completed and failed history across restarts, manage subscriptions, and edit local settings. Files land in the folder the user chose. `yt-dlp` and `ffmpeg` come from `PATH`. If either is missing, Settings says so and Add does not pretend a download started.

Android, iOS, and web still build. They show the same screens backed by an in-memory fake. They do not download.

## Rules for every D1 task

- Reimplement behavior. Do not copy MeTube's Angular, Python, stylesheets, or tests into this repository. MeTube is AGPL-3.0. The reviewed baseline is commit `6708a882294a6e8c5ffe097354c7eee42eb0f309` (2026-09-16).
- Do not copy yt-dlp source into this repository. Do not vendor a yt-dlp or FFmpeg binary. Do not add YtDlp-kt.
- Do not add a server, a login, or a MeTube HTTP/Socket.IO client. Leave `shared/network` unused. Do not extend it.
- `ProcessBuilder` and any other process API live only under `apps/desktop`. Shared modules (`shared/core`, `shared/ui`) must not reference them.
- Build yt-dlp arguments as a list. Never concatenate a shell command. Never pass the disabled custom-JSON field through.
- Cookie file contents, signed media URLs, and raw process stderr do not go into logs, history rows, toasts, or this vault.
- Unknown progress stays unknown. Do not invent a percent, speed, or ETA.
- Remove history, delete the file, and cancel a job are three different actions with different confirms.
- Keep the public vault free of real cookies, credentials, and private media URLs. Tests use `https://example.com/watch?v=fixture` style URLs.
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, and move its Kanban card. The board column is the status.

## Layout the UI and engine must follow

MeTube is one desktop window: an add form on top, three lists under it, and settings opened from the header. Match that information architecture in Material 3. A later phone layout can restack these same composables; D1 sizes the window for desktop (starting size already in `apps/desktop/.../Main.kt`, widen toward 1100×800 when the form needs it).

```text
shared/core     Domain, validation, DownloadEngine, repository interfaces, in-memory fakes
shared/ui       Compose screens and presenters. Depends on core. Does not start processes
apps/desktop    JSON store, folder picker, YtDlpCliEngine, window host
```

Suggested packages, so later tasks do not invent a second layout:

| Piece | Package |
| --- | --- |
| Domain and interfaces | `com.anydownlod.core` |
| In-memory fakes | `com.anydownlod.core.fake` |
| Shell, Add, Downloading, Completed, Subscriptions, Settings | `com.anydownlod.ui.shell`, `.add`, `.queue`, `.history`, `.subscriptions`, `.settings` |
| JSON store | `com.anydownlod.desktop.store` |
| Process engine | `com.anydownlod.desktop.engine` |

`App(...)` stays the composable the Android, iOS, desktop, and web hosts call. D1 changes its parameters from the server `AppConfig` to the engine and repositories. Hosts that are not desktop pass the in-memory fakes. Delete `HomePresenter` and the capabilities card once nothing calls them.

Existing types to extend, not replace: `MediaType`, `StartPolicy`, `JobState`, `JobProgress`, `JobError`, `DownloadRequest`, `DownloadJob`, `SourceUrlValidator`. Their comments still describe a server. Rewrite those comments so they describe the local app. `Capabilities` is a leftover server advertisement; Settings uses a local tool probe instead of growing that type.

## Task order

Do them in this order. Later tasks assume the interfaces and screens already exist.

| Order | Task | Delivers |
| --- | --- | --- |
| 1 | [T-026](../06-tasks/T-026-Shared-domain-and-engine-seam.md) | Domain, `DownloadEngine`, repositories, in-memory fakes, unit tests |
| 2 | [T-027](../06-tasks/T-027-Desktop-app-shell.md) | Window chrome, navigation, theme, empty states, hosts still compile |
| 3 | [T-028](../06-tasks/T-028-Add-form.md) | Full add form against the fake |
| 4 | [T-029](../06-tasks/T-029-Queue-and-history-screens.md) | Downloading and Completed, including destructive confirms |
| 5 | [T-030](../06-tasks/T-030-Subscriptions-screen.md) | Subscriptions table against the fake |
| 6 | [T-031](../06-tasks/T-031-Settings-screen.md) | Settings against the fake, including the folder-picker callback |
| 7 | [T-032](../06-tasks/T-032-Desktop-json-store.md) | Restart-safe JSON store wired into the desktop host |
| 8 | [T-033](../06-tasks/T-033-Desktop-ytdlp-single-url.md) | One public URL downloads through `yt-dlp` and appears in the queue |
| 9 | [T-034](../06-tasks/T-034-Desktop-ytdlp-formats-and-batches.md) | The rest of the add form mapped onto yt-dlp arguments |
| 10 | [T-035](../06-tasks/T-035-Desktop-cookies-presets-subscriptions.md) | Cookies, presets, and subscription checks |
| 11 | [T-036](../06-tasks/T-036-Phase-1-verification.md) | Desktop run-through, README, and evidence |

T-028 through T-031 touch different packages and can proceed in that order as soon as T-027 is done. T-032 needs T-026 only, and should land before T-033 so the first real download is already persistent.

## What this phase covers from the parity matrix

| Tasks | Rows |
| --- | --- |
| T-028, T-033, T-034 | F-01, F-02, F-03, F-14, F-15, F-16, F-17 |
| T-028, T-029, T-034 | F-04, F-05, F-06, F-07, F-08, F-10 |
| T-029, T-032 | F-09, F-11, F-12, F-13 |
| T-031, T-035 | F-18, F-19, F-22 |
| T-030, T-035 | F-21 |
| Shown disabled | F-20 (Q-09 still open) |
| Not this phase | F-23 companion apps, F-24 and F-25 server operations, F-26 as a server egress proxy |

Parity here means the desktop workflow behaves like the reviewed MeTube UI while yt-dlp does the extraction. It is not evidence for the Kotlin port. [T-022](../06-tasks/T-022-Parity-audit.md) still audits the final engine.

## Explicitly later

- Porting yt-dlp extractors, the JavaScript challenge runtime, or a shared media toolkit.
- Spotify metadata and YouTube matching ([T-037](../06-tasks/T-037-Spotify-youtube-match.md)).
- Android, iOS, and web downloads, share sheets, and background execution.
- Bundling yt-dlp or FFmpeg inside the app.
- Browser extensions, bookmarklets, iOS shortcuts, Raycast.
- Enabling the free-form yt-dlp JSON box.

## How to start a session

Paste the prompt in [Phase 1 loop prompt](Phase-1-Loop-prompt.md) as the first message. It walks T-026 through T-036 one card at a time.

1. Read this note and [ADR-005](../03-decisions/ADR-005-Desktop-metube-phase.md).
2. On [Kanban](../Kanban.md), take the first D1 card whose dependencies are Done.
3. Read that task note fully before editing code. The acceptance checklist is the definition of done.
4. Do not pick up [T-004](../06-tasks/T-004-Validate-KMP-targets.md) or the Kotlin engine tasks as part of D1.
