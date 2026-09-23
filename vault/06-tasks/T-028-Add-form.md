---
id: T-028
type: task
priority: P1
milestone: D1
tags: [task, ui, downloads]
---

# T-028 — Add form

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

The add region is the MeTube download form. Submitting it calls the T-026 engine fake. Every control exists and affects the `DownloadRequest`. Nothing spawns yt-dlp yet.

## Dependencies

- [T-027](T-027-Desktop-app-shell.md).

## Context the next session needs

MeTube keeps this form on screen above the lists. It is not a separate route. Two primary actions sit together: **Download** and **Subscribe**. Subscribe's behavior is specified in T-030; this task still places the button and passes the current form options into `SubscriptionRepository.add` when the URL validates. If T-030 has not built the table yet, the new row only needs to appear once that screen exists, so call the repository from here.

`SourceUrlValidator` rejects whitespace. A batch is several URLs separated by newlines. Split, trim, drop blanks, validate each line, then submit one request per valid URL. Show per-line errors and still submit the valid lines.

A double click on Download uses one idempotency key for that click's submission. A later, separate click is a new download of the same URL. That matches the user-flow rule for repeated taps versus an intentional second download.

Preset ids on the request are the ones checked in the advanced section. The fake starts with no presets; the form still lists whatever `SettingsRepository` returns so T-031 and T-035 can fill them.

## Work

Build the form in `com.anydownlod.ui.add`. Bind it to the shell slot from T-027.

Always visible:

- Multi-line URL field. Placeholder tells the user that one URL per line is a batch.
- Media type: Video, Audio, Captions, Thumbnail. Changing type shows only the fields that apply, and hides the others. Do not leave a video resolution selected while submitting captions-only; the request's unused fields are null.
- Auto-start: yes maps to `StartPolicy.AUTOMATIC`, no maps to `MANUAL`.
- Filename prefix.
- Destination folder: a single relative path segment or nested relative path. Reject `.`, `..`, and absolute paths inline, using the same rules as T-026. This is the custom folder name, not the download root (the root is Settings).
- Download and Subscribe buttons. Both disabled while the field is empty. Subscribe stays disabled when the field contains more than one URL; MeTube subscribes to a channel or playlist, not a pasted batch.

Video fields:

- Container profile: Auto, MP4, iOS compatible.
- Codec: Auto, H.264, HEVC, AV1, VP9.
- Quality: Best, Worst, and a fixed list of common heights (2160, 1440, 1080, 720, 480, 360). The fake does not query yt-dlp for the real ladder. Label the list as a preference, not a promise that the file will be that height.

Audio fields:

- Container: M4A, MP3, Opus, WAV, FLAC.
- Quality/bitrate choices that depend on the container. Lossless choices (WAV, FLAC) do not offer a bitrate. Lossy choices do. The label should say when the result will be converted rather than copied. Exact yt-dlp flags are T-034; store a stable token on the request.

Captions fields:

- Language (a text field is enough; do not pretend to know the video's tracks).
- Preference: manual, automatic, or either.
- Format: SRT, TXT, VTT, TTML.

Advanced section, collapsed by default, state remembered for the session:

- Playlist item limit, integer, 0 means all.
- Clip start and clip end, as `HH:MM:SS` or seconds. Invalid ranges (end before start, only one side filled is allowed) show an inline error and block submit.
- Split by chapters.
- SponsorBlock removal, off by default. The label says it needs SponsorBlock data and may no-op.
- Embed subtitles, write metadata, write thumbnail sidecar. Available when they make sense for the media type. WAV artwork limitations can be a caption on the control; do not hide the control.
- Option presets: multi-select from settings, order preserved.
- Custom yt-dlp options: a text field that is visible, disabled, and explained as not available yet (Q-09). It must not be copied onto the request.

Submit:

- Valid lines become engine submissions. The URL field clears only the lines that were accepted. Failed lines stay.
- Show a short confirmation of how many jobs were added, and how many were left pending versus started. Use the in-app state, not a toast library requirement. A simple status line is enough if it is announced for a screen reader (live region or equivalent focus move).
- Cancel is implicit: clearing the field or fixing errors before submit. There is no background "add" yet, so do not add a fake cancel-add button that does nothing. When T-034 makes playlist expansion slow, that task adds cancellation of the expansion.

Accessibility: every control has a label, errors are text next to the control, and the advanced section is a real disclosure the keyboard can toggle. Disabled custom JSON is still labeled.

Presenter tests in `shared/ui` or `shared/core` (whichever module already can run JVM tests without a display) should cover: video versus audio field clearing, batch split with one bad line, idempotent double submit, subscribe rejected for multiple URLs, and the custom JSON field never appearing on the request. If Compose UI tests are awkward in this project, test the presenter/state mapper and keep the composable thin.

## Verification

Run the desktop app, submit one example.com URL with auto-start on, one with auto-start off, a two-line batch with one invalid line, and a Subscribe. Confirm the fake jobs and the subscription exist in memory (the Downloading tab may still be empty until T-029; a temporary debug line is acceptable only if T-029 is not done, and it must be removed if T-029's rows already show the jobs). Run the unit tests.

## Out of scope

Real yt-dlp, progress rows, settings editors, cookie upload, persistence across restart.

## Acceptance criteria

- [x] The form contains every control listed above. Video, audio, captions, and thumbnail each show the relevant subset.
- [x] Download submits one `DownloadRequest` per valid line through `DownloadEngine`. One bad line does not drop the good lines. A double click does not create two jobs.
- [x] Subscribe stores one subscription from the current options and refuses a batch.
- [x] Custom yt-dlp JSON is visible, disabled, and absent from the request.
- [x] Presenter tests cover the cases in Work. Evidence records the desktop click-through.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :shared:ui:cleanJvmTest :shared:ui:jvmTest :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL. `AddFormPresenterTest` 15 tests, `AddFormUiTest` 5 tests, `AppShellTest` 2 tests, all 0 failures.
- `./gradlew :apps:desktop:run` — the window opened with the new form, the log had no exception/error lines, and the window closed cleanly.

Click-through: macOS Accessibility/Screen Recording permission is still not granted to this host, so the interactions ran headlessly through the Compose test runtime against the real composables (`AddFormUiTest`). Performed: typed one URL and clicked Download (1 job with that URL); switched Auto-start off, typed a URL, clicked Download (job in `PENDING`); pasted a 3-line batch with one invalid line and clicked Download (two more jobs; the bad line stays in the field with an inline "Only http:// and https:// sources are supported." error); typed one channel URL and clicked Subscribe (one subscription); appended a second URL (Subscribe disabled, still one subscription); asserted both buttons are disabled with an empty field; clicked Audio then Captions and asserted only the relevant field groups exist. The presenter tests cover the remaining controls: video profile/codec/quality, audio container/bitrate (lossless clears it), caption language/preference/format, filename prefix, relative destination, playlist limit, clip parsing and inverted-range rejection, chapter split, SponsorBlock, embed/metadata/thumbnail flags, preset ordering, manual start, repeated same-URL download, and an always-empty custom-JSON field.

What landed:

- `com.anydownlod.ui.add`: `AddFormState` (every control, inline destination/playlist/clip errors, `toDownloadOptions` that nulls fields which do not apply), `AddFormPresenter` (batch split via `BatchUrlValidator`, one batch idempotency key per click, accepted lines cleared and rejected lines kept, status line, subscribe with captured options and the Settings interval), `AddForm` (the form card bound into the T-027 slot), plus `parseClipTimestamp`.
- The presenter is created once in `App` and passed through `AppShell`, so form and advanced-disclosure state survive opening Settings and switching tabs. `AddUrlPlaceholder` is deleted.
- A double click produces one batch key and clears accepted lines immediately; the second click has nothing to submit. Pasting the same URL again later gets a new key and creates a new job. Both directions are tested.
- The custom yt-dlp JSON box is visible, disabled, labeled as unavailable (Q-09), and never copied into `DownloadOptions`.
