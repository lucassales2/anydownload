---
id: T-033
type: task
priority: P0
milestone: D1
tags: [task, desktop, engine]
---

# T-033 — Desktop yt-dlp engine for one URL

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [ADR-005](../03-decisions/ADR-005-Desktop-metube-phase.md)

## Outcome

On desktop, pasting one public HTTP(S) URL and choosing Download runs the installed `yt-dlp`, streams progress into the Downloading list, and leaves a finished file in the download root. Cancel stops the process. Quit and relaunch shows the finished job in Completed, or a retryable failure if the app was closed mid-download.

This is the first real download. Format pickers, playlists, cookies, and subscriptions still use defaults. Video plus automatic start is enough.

## Dependencies

- [T-029](T-029-Queue-and-history-screens.md), so progress has a place to appear.
- [T-032](T-032-Desktop-json-store.md), so the result survives restart.
- [T-028](T-028-Add-form.md) should be done so the URL comes from the form. If the form is incomplete, a single URL field that calls this engine is not an acceptable shortcut; finish the form task first.

## Context the next session needs

ADR-004 forbids a process in shared code. ADR-005 allows it in `apps/desktop` for this phase. Put `YtDlpCliEngine` in `com.anydownlod.desktop.engine`. `shared/core` and `shared/ui` stay free of `ProcessBuilder`.

Do not vendor yt-dlp or FFmpeg. Resolve `yt-dlp` and `ffmpeg` from `PATH`. If `yt-dlp` is missing, the tool probe in Settings says it is missing, and submit fails the job immediately with `engine unavailable` and a message that names the missing tool. Do not silently no-op. FFmpeg may be absent for a single-file format that does not need merging; if yt-dlp then fails, surface that as a post-processing or engine error, and show ffmpeg as missing in Settings when the probe cannot find it.

Probe by running `yt-dlp --version` and `ffmpeg -version` with an argument list, a short timeout, and no shell. Store only the version line.

Invocation rules:

- `ProcessBuilder` with a list of arguments. No `sh -c`, no string concatenation of the URL into a command line.
- The URL is a single argument after the options.
- Working directory and output template point inside the download root. Pass an output template of the form `-o` plus an absolute path under that root using the settings template. If the settings template contains a directory token, still resolve the final path and reject anything that escapes the root after normalization.
- `--no-mtime` is optional. Do not pass `--exec`, `--config-locations`, plugin paths, or a user JSON blob.
- Prefer a stable progress format. yt-dlp can print newline-delimited progress with `--newline` and `--progress-template`. Pick one template, document the yt-dlp version used, and unit-test the parser against fixture lines checked into `apps/desktop` test resources. Do not depend on a live website in unit tests.
- Also capture the title from yt-dlp's metadata print (`--print` / `--no-simulate` sequencing is easy to get wrong). A practical approach: one process with `--newline`, a progress template, and a print template for title and filename on separate prefixes the parser understands. If that conflicts, a first `--dump-single-json` for metadata and a second process for the download is acceptable for this task. Cancel must cover whichever process is running.
- Concurrent downloads: honor settings, default 3, with a simple semaphore. This task can be tested with one job.

Progress mapping:

- Destination percent, downloaded bytes, total bytes, speed, and ETA when the template includes them.
- Missing total stays null percent. Do not treat `NA` or `Unknown` as zero.
- Phases: while resolving metadata, `RESOLVING`; while queued behind the semaphore, `QUEUED`; during transfer, `DOWNLOADING`; if yt-dlp reports a post-processing destination, `POSTPROCESSING`; success after the process exits 0 and the expected file exists, `COMPLETED`; non-zero exit, `FAILED`.

Cancellation: destroy the process and its children. On JVM, `Process.destroy()` does not always kill ffmpeg children. Use the platform process tree (Java 9+ `Process.descendants()` / `toHandle().destroy()`) and record the method in Evidence. The job ends `CANCELLED`, not `COMPLETED`. A partial file may remain in the download root; do not register it as a finished artifact.

Artifacts: on success, register one artifact with the real relative path and size. **Open file** and **Reveal in folder** from T-029 must work for that path. **Delete file** unlinks the files under the root and only those files. Refuse paths that normalize outside the root.

Errors: map a non-zero exit to a short message. Keep the raw stderr in memory for the session if useful, but the `JobError.message` stored on the job is redacted: no cookie header, no full media URL query if it looks signed. Truncate long output.

Idempotency stays in the engine from T-026. The CLI is not invoked twice for a double click.

Network: this task may download one real public video the implementer is allowed to fetch. Do not commit that file, its URL if it is private, or cookies. `https://example.com` will fail extraction; that failure path must also be demonstrated. A successful path needs a real site the implementer chooses at run time and describes only as "one public video" in the vault.

Closing the window destroys active processes and relies on T-032 to mark them failed on the next launch. Save state after the process is destroyed.

## Work

- Implement the engine and the parser.
- Replace the desktop graph's download engine with this one. Subscriptions and settings stay on the JSON store.
- Settings tool rows show the probe result.
- Add JVM tests for the parser fixtures, escape rejection, missing binary, and cancel state. Mock the process if you can do it without a live binary; a parser test plus a fake `Process` runner is better than skipping tests when yt-dlp is installed only on one machine.

## Verification

With yt-dlp installed: download one public video to the chosen folder, watch percent or indeterminate progress, see the file, open it, quit, relaunch, see it on Completed. Cancel a second download mid-way and confirm the process is gone (`pgrep` or Activity Monitor) and the row is Cancelled. With the binary renamed or PATH cleared for one run, confirm Settings and a failed job. Run the unit tests.

## Out of scope

Audio/codec/caption argument mapping (T-034), playlists, cookies, presets, subscription scans, bundling yt-dlp, Kotlin extractors.

## Acceptance criteria

- [x] A single video URL on desktop runs `yt-dlp` from `PATH` and registers a file inside the download root.
- [x] The queue shows resolving, downloading, and completed, with null progress left null.
- [x] Cancel stops the process tree and records `CANCELLED`.
- [x] Relaunch shows the completed job, or a retryable failure if the process was killed by quitting.
- [x] Missing `yt-dlp` is visible in Settings and as `engine unavailable`.
- [x] Shared modules still have no process API. Parser tests do not need the network.
- [x] Evidence records the yt-dlp version, the progress template, how the process tree is killed, and that a public video succeeded. It does not record the video URL if the implementer would rather not, but it must record the failure-path result for an unsupported URL.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Tool versions on this machine: **yt-dlp 2026.08.19**, **ffmpeg 9.0.1** (both resolved from `PATH`; nothing bundled).

Commands run:

- `./gradlew :apps:desktop:test` — `DesktopStoreTest` 12, `DesktopAppRelaunchTest` 2, `YtDlpProgressParserTest` 4, `YtDlpArgumentsTest` 3, `YtDlpCliEngineTest` 4, `PathToolProbeTest` 3, `LiveYtDlpCheckTest` 3 (skipped without URLs); all 0 failures.
- `./gradlew :shared:core:jvmTest :shared:ui:cleanJvmTest :shared:ui:jvmTest :apps:desktop:test :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL, **138 tests, 3 skipped (the live checks), 0 failures**.
- Live checks, run with `-Danydownlod.live.url=... -Danydownlod.live.cancelUrl=... -Danydownlod.live.failureUrl=... -Danydownlod.live.root=/tmp/...`: one public sample video **COMPLETED** with `percent=100.0` and a real artifact inside the download root (991 KB file on disk); a mid-download **cancel recorded `CANCELLED`** and a `ProcessHandle` scan found no surviving `bin/yt-dlp` process; the unsupported URL `https://example.com` **FAILED** with the generic redacted message. No video URL is stored in the repository (the live tests read it from system properties).
- `./gradlew :apps:desktop:run` — the store-backed window opened with the CLI engine, no exception/error lines, and closed cleanly.

Progress integration (validated against the installed yt-dlp, one process, argv list only):
- `--progress-template 'download:DL|%(progress.status)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s'`
- `--progress-template 'postprocess:PP|%(progress.status)s'`
- `--print 'TITLE|%(title)s'` and `--print 'after_move:FILE|%(filepath)s'`
- `--newline --progress --no-mtime --no-simulate -o <root>/<template> -- <url>`

Two findings that the fixtures alone would have missed: `--print` implies quiet, so `--progress` is required to keep the download template, and `%(filepath)s` is only final with the `after_move:` prefix. Postprocess progress arrives on stderr, so the stderr drain also feeds the parser there; download progress arrives on stdout. Missing totals and `NA` values stay null; percent is computed only from known downloaded/total bytes.

Process tree kill: capture `process.toHandle().descendants()`, call `destroy()` on each descendant and on the process, wait up to 2 seconds, then `destroyForcibly()` the descendants and process. Proven by the live cancel check.

Missing yt-dlp: `submit`/`start` resolve the binary on `PATH` before spawning; a missing tool fails the job immediately with `ENGINE_UNAVAILABLE` and a message naming yt-dlp. `PathToolProbe` runs `yt-dlp --version` / `ffmpeg -version` with a 5-second timeout, keeps only the first line, and reports both as unavailable when the resolver finds nothing; the Settings tool rows render that directly.

What landed: `com.anydownlod.desktop.engine` (`CliProcess`/`JavaCliProcessRunner`/`ExecutableOnPath`, `YtDlpProgressParser`, `YtDlpArguments`/`DownloadPaths`, `PathToolProbe`, `YtDlpCliEngine`), the checked-in parser fixture resource, the desktop graph now using the CLI engine plus `PathToolProbe`, and the desktop close path destroying live processes before the final save. T-033 maps the video default only; T-034 fills in the rest of `DownloadRequest`.

**T-036 follow-up (2026-09-21):** the phase run-through found that desktop `openFile`/`revealFile` were still the `AppGraph` no-ops. They are now wired in `apps/desktop/Main.kt` to `java.awt.Desktop.open` / `browseFileDirectory` with the artifact path resolved and escape-checked under the download root, which closes this card's Open file / Reveal in folder requirement.
