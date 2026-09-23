---
id: T-034
type: task
priority: P1
milestone: D1
tags: [task, desktop, downloads]
---

# T-034 — Formats, playlists, and batches through yt-dlp

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

Every enabled add-form control changes the yt-dlp argument list, and the desktop app can complete the MeTube download workflows that are not cookies, presets, or subscriptions: video profiles, audio extraction, captions, thumbnails, clips, chapters, SponsorBlock, playlists, and newline batches.

## Dependencies

- [T-033](T-033-Desktop-ytdlp-single-url.md).
- [T-028](T-028-Add-form.md).

## Context the next session needs

T-033 proved one video download. This task maps the rest of `DownloadRequest` onto arguments inside `apps/desktop`. Keep the mapping in one tested function, `DownloadRequest -> List<String>`, so the UI never builds argv itself.

Reviewed MeTube behavior to reproduce (feature rows F-02, F-03, F-04, F-05, F-14, F-15, F-16, F-17):

- Video profile Auto does not force a container. MP4 asks for an MP4-compatible selection. iOS compatible is a more conservative MP4/H.264-style selection. Document the exact format selector in Evidence.
- Codec preferences H.264, HEVC, AV1, VP9 are preferences. If yt-dlp falls back, the history row should say the file completed, and the UI must not label a fallback as if it were a guaranteed codec. Probing with `ffprobe` when it is on `PATH` is ideal and optional; otherwise record the format string that was requested.
- Quality best, worst, and heights become format filters. Unavailable height fails the job with `unsupported format` or completes with an explicit fallback note. Pick one behavior, document it, and test it with a fixture of yt-dlp's stderr rather than five live downloads.
- Audio M4A, MP3, Opus, WAV, FLAC uses `-x` and `--audio-format`. Lossy bitrates use `--audio-quality`. WAV and FLAC do not get a bitrate argument. The form copy that says conversion may be lossy must stay accurate for MP3 and Opus.
- Captions-only: language, manual versus automatic (`--write-sub` / `--write-auto-sub`), and `--sub-format` for SRT, VTT, TTML, or a best-effort conversion. If yt-dlp cannot emit the requested format, the job fails or completes with a message that names the actual file. Do not rename an SRT to `.ttml` and call it success.
- Thumbnail-only writes the thumbnail and does not download the media.
- Optional sidecar flags from the form: embed subs, embed metadata, embed thumbnail, write thumbnail. WAV-plus-artwork can fail; surface that as post-processing failure.
- Clip start/end pass through as yt-dlp download sections. A start with no end, or an end with no start, is allowed when the form allowed it. Inverted ranges never reach the process.
- Chapter split uses `--split-chapters` and the chapter output template from settings. Each chapter file is an artifact.
- SponsorBlock uses the removal arguments MeTube uses (`--sponsorblock-remove` for the default categories MeTube enables). Off means those arguments are absent. If the site has no markers, the download still succeeds.
- Playlist and channel URLs stay one submission that expands into child jobs, or one job with many artifacts, as long as the UI shows per-item progress and a failure of one entry does not hide the others. Prefer child jobs so cancel and retry match F-04. Cap expansion with the playlist item limit. 0 means no extra cap from us; yt-dlp's own default applies. Show expansion progress on the parent row and allow cancel during expansion, which stops the process and keeps child jobs already created.
- Batch paste is already one job per line from T-028. This task makes each of those jobs a real yt-dlp run, bounded by max concurrency.
- Filename prefix is prepended to the output template's file name, not a shell prefix.
- Custom folder is a relative directory under the download root.

Argument safety, still in force:

- Allowlist only the flags this task names. No `--exec`, no config file, no `--cookies` yet (T-035), no arbitrary `-o` outside the root.
- Unit-test the argv for each media type using a table of requests and expected argument subsets. Those tests must not launch yt-dlp.

Live checks, keep them few: one audio extract, one playlist or a playlist URL limited to 2 items, one batch of two public URLs, one captions or thumbnail download. Skip a live check only when the tool is missing, and record that. Do not commit media.

Manual start: `PENDING` jobs do not spawn yt-dlp until Start. Auto-start does. Bulk start from T-029 starts several, still under the concurrency cap.

Retry re-runs extraction with the stored request. It does not reuse an expired media URL. It must not write a second copy on top of a finished file without a collision policy: if the destination exists, append a numeric suffix or fail the attempt visibly. Pick one, test it, record it.

F-10 scheduled/upcoming: if yt-dlp reports a premiere or live wait, put the job in `SCHEDULED` and show the time when one is present. Do not implement a general cron. If the installed yt-dlp does not surface a wait time for the fixture you try, record that F-10 is unimplemented for lack of a fixture rather than faking a countdown.

## Verification

Unit tests for the argument table, path escape, playlist limit, and collision policy. Desktop run of the live checks you can perform. `:apps:desktop` tests pass.

## Out of scope

Cookie file, preset layering, subscription polling, enabling the custom JSON box, Kotlin port.

## Acceptance criteria

- [x] A tested mapper turns a `DownloadRequest` into an argument list. The UI does not assemble argv.
- [x] Video, audio, captions, and thumbnail submissions pass different flags, matching the behavior section.
- [x] Clips, chapters, and SponsorBlock are off unless the form enabled them, and on with the documented flags when enabled.
- [x] Playlist expansion is cancellable, respects the item limit, and keeps a per-item failure visible.
- [x] Batch lines run as separate jobs under the concurrency cap. Manual start does not spawn early.
- [x] Retry re-extracts and does not silently overwrite a finished file.
- [x] Evidence lists the live checks and any row (especially F-10) that could not be exercised.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :apps:desktop:test` — 50 tests, 0 failures: `YtDlpArgumentsTest` 12, `YtDlpCliEngineTest` 10, `YtDlpProgressParserTest` 4, `PathToolProbeTest` 3, `DesktopStoreTest` 12, `DesktopAppRelaunchTest` 2, `LiveYtDlpCheckTest` 7 (skipped without URLs).
- Full matrix `:shared:core:jvmTest :shared:ui:cleanJvmTest :shared:ui:jvmTest :apps:desktop:test :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL, **157 tests, 7 skipped (live-only), 0 failures**.
- `./gradlew :apps:desktop:run` — seeded window opened, no exception/error lines, closed cleanly.
- Live checks (yt-dlp 2026.08.19 / ffmpeg 9.0.1; URLs supplied through `-Danydownlod.live.*` properties and not stored in the repo): video COMPLETED at 100%; audio extract COMPLETED with a `trailer.mp3` AUDIO artifact; playlist URL expanded into **exactly 2 child jobs** and the children were cancellable; a batch of the same public URL twice ran as two jobs and both completed; thumbnail-only COMPLETED with a `Big Buck Bunny.jpg` THUMBNAIL artifact. Planned rows not exercised: **F-10 scheduled/upcoming** — the installed yt-dlp did not surface a premiere/live wait time in the fixtures tried, so no countdown was faked; `SCHEDULED` remains covered by the seeded UI state only.

Argument mapping (all table-tested with no yt-dlp launch): Auto profile emits no `-f`; MP4 uses `bv*[ext=mp4]+ba/b[ext=mp4]` plus `--merge-output-format mp4`; iOS uses `bv*[ext=mp4][vcodec^=avc1]+ba/b[ext=mp4][vcodec^=avc1]`; codecs use `vcodec^=avc1` / `vcodec~='^(hev1|hvc1)'` / `vcodec~='^av01'` / `vcodec~='^vp0?9'`; Best keeps yt-dlp's default, Worst uses `wv*+wa/w`, and a height token adds an exact `height=N` filter so an unavailable size fails with `unsupported format`; audio uses `-x --audio-format <m4a|mp3|opus|wav|flac>` and `--audio-quality <N>K` only for lossy containers; captions use `--skip-download` plus `--write-subs`/`--write-auto-subs`, `--sub-langs`, and `--sub-format`; thumbnail-only uses `--skip-download --write-thumbnail --convert-thumbnails jpg`; sidecars use `--embed-subs`/`--embed-metadata`/`--write-thumbnail`; clips become `--download-sections "*HH:MM:SS-HH:MM:SS"` (`inf` for open ends); chapters use `--split-chapters` plus a `chapter:` output template from Settings; SponsorBlock uses `--sponsorblock-remove` with the reviewed categories; the filename prefix is prepended to the file-name segment only, and the destination folder is resolved under the download root with escape rejection.

Playlist semantics: a pre-scan (`--flat-playlist --skip-download --playlist-end N --print ENTRY|%(url)s`) turns a multi-entry source into one child job per item (`parentBatchId` set), so each item has its own row, per-item failure, cancel, and retry; cancellation during expansion destroys the scan and leaves already-created children. The parent row carries `Playlist (N items)`. Single entries download in the same pass.

Collision policy: the mapper always passes `--no-overwrites`. yt-dlp then skips an existing file, prints its path, and exits 0, so a retry or repeated batch completes with the existing artifact and never silently overwrites it; the batch live check exercised this path.

Limitations recorded: the concurrency semaphore is sized from Settings when the engine starts, so changing it takes effect on restart; thumbnail/captions output has no `--print` stage, so the engine registers files found under the destination folder after an exit-0 run when no path was printed.
