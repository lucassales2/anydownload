---
type: phase
status: done
milestone: D5
tags: [project, engine, kmp, toolkit, delivery]
---

# Phase 5 — Media toolkit

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-009](../03-decisions/ADR-009-Media-toolkit-phase.md) · [ADR-007](../03-decisions/ADR-007-Generic-extractor-phase.md) · [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md) · [Phase 4](Phase-4-Extractor-Core-and-YouTube.md)

**Start here if you are implementing.** This note is the handoff for Phase D5. ADR-004 remains the end state. D4's extractor core, YouTube single video, and single-file downloads stay. This phase merges one video stream with one audio stream, and copies or converts audio, on desktop, Android, and iOS. Web stays a documented gap.

Owner direction, 2026-09-25: the media toolkit is this phase. The phase after it is [spotDL parity](Phase-6-SpotDL-Parity.md). X/Twitter is the next named site after that. Upstream pin stays `2026.08.19`; that tag was still the latest release on this date.

## Done looks like

On desktop, Android, and iOS, a video whose best picture is a separate video stream plus a separate audio stream downloads as one playable file. Edit enables the resolutions a merge can satisfy and explains the rest. Audio that is already M4A or Opus is copied out. MP3, WAV, and FLAC are produced only where the host toolkit can encode them; otherwise the choice stays disabled with the reason. Web keeps every merge and transcode choice disabled. A missing tool fails typed. Direct-file, generic-page, and single-file YouTube downloads behave as they do after D4.

## Rules for every D5 task

- No backend, login, MeTube HTTP/Socket.IO client, or `shared/network` use.
- No `ProcessBuilder`, Python, or Chaquopy in common code. FFmpeg stays in `apps/desktop`.
- Do not vendor FFmpeg. Do not copy MeTube, NewPipe, or YtDlp-kt.
- The upstream pin stays `2026.08.19`. Do not move it mid-phase.
- Cookies, signed media URLs, and raw tool stderr stay out of logs, history, fixtures, and this vault.
- Unknown progress stays unknown. Postprocessing progress is a phase name, not an invented percent.
- The typed-options compiler may emit one `+` merge. It never emits multiple outputs, clips, or a free-form spec string.
- A merge copies streams. It does not re-encode. If the container cannot hold the codecs, the job fails typed.
- Tests use local synthetic media fixtures. A live YouTube merge is opt-in on desktop only, against the public URL already allowed in the oracle test. No cookies and no private media.
- Do not add captions, thumbnails, chapter split, SponsorBlock, or embed-metadata. Those stay in [T-015](../06-tasks/T-015-Captions-thumbnails-metadata.md) and [T-016](../06-tasks/T-016-Clips-chapters-SponsorBlock.md).
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, and move its Kanban card. The board column is the status.

## Layout

```text
shared/core
  postprocess/          MediaToolkit contract and capabilities
shared/ui               Edit enables only what the host capability allows
apps/desktop            FFmpeg/ffprobe on PATH; argument list; no vendored binary
apps/android            MediaMuxer / MediaExtractor; no bundled FFmpeg
apps/ios                AVFoundation; no subprocess
apps/web                No toolkit; choices stay disabled
```

Package: `com.anydownlod.core.postprocess`. Engines stay in `com.anydownlod.core.engine`.

## Task order

Do them in this order. Dependencies are the links in each task note.

| Order | Task | Delivers |
| --- | --- | --- |
| 1 | [T-075](../06-tasks/T-075-Media-toolkit-contract.md) | `MediaToolkit` contract: merge, extract audio, capabilities |
| 2 | [T-076](../06-tasks/T-076-Desktop-ffmpeg.md) | Desktop FFmpeg adapter; probe, cancel, missing-binary failure |
| 3 | [T-077](../06-tasks/T-077-Engine-executes-a-merge.md) | Compiler emits one merge when the host can; engine downloads both sides |
| 4 | [T-078](../06-tasks/T-078-Edit-follows-capabilities.md) | Edit enables what the host can merge or encode |
| 5 | [T-079](../06-tasks/T-079-Gate-desktop-merge.md) | **Gate:** desktop merge works; web still refuses |
| 6 | [T-080](../06-tasks/T-080-Android-mediamuxer.md) | Android remux and audio stream-copy |
| 7 | [T-081](../06-tasks/T-081-Ios-avfoundation.md) | iOS remux and audio stream-copy |
| 8 | [T-082](../06-tasks/T-082-Audio-containers.md) | MP3/WAV/FLAC on desktop; mobile copy-only unless proven |
| 9 | [T-083](../06-tasks/T-083-Phase-5-verification.md) | Four-host capability matrix and README |

## What this phase covers

| Host | Single file (D4) | Merge video + audio | Audio copy (M4A/Opus) | MP3 / WAV / FLAC |
| --- | --- | --- | --- | --- |
| Desktop | Unchanged | FFmpeg on `PATH`, stream copy | FFmpeg stream copy | FFmpeg transcode, ffprobe-checked |
| Android | Unchanged | MediaMuxer remux | MediaExtractor copy | Disabled unless T-082 proves a platform encoder |
| iOS | Unchanged | AVFoundation remux | AVFoundation copy | Disabled unless T-082 proves a platform encoder |
| Web | Unchanged | Disabled, host gap | Disabled, host gap | Disabled, host gap |

Parity rows: F-02 and F-03 gain desktop evidence and a partial mobile remux. [T-013](../06-tasks/T-013-Media-formats.md) stays open. [T-022](../06-tasks/T-022-Parity-audit.md) stays open.

## Verified (2026-09-25)

[T-083](../06-tasks/T-083-Phase-5-verification.md) recorded the four-host capability matrix:

| Host | Tool | Merge | M4A / Opus | MP3 / WAV / FLAC |
| --- | --- | --- | --- | --- |
| Desktop | `ffmpeg`/`ffprobe` on `PATH` | **Pass** — stream copy to one MP4, local fixture and opt-in live YouTube | **Pass** — stream copy | **Pass** — MP3/WAV/FLAC transcode, `ffprobe`-checked |
| Android | `MediaMuxer`/`MediaExtractor` | **Pass (JVM-equivalent)** — fake port plus a compiled instrumented test; no emulator here | **Pass (copy)**; Opus uses the WebM muxer | Disabled — no platform encoder or WAV muxer |
| iOS | `AVFoundation` | **Pass (simulator)** — `AVAssetExportPresetPassthrough` | **Pass (copy)**; Opus copies to CAF because AVFoundation has no Ogg writer | Disabled — no PCM/WAV preset |
| Web | none | Disabled — host gap | Only a native single-file stream; no toolkit extract | Disabled — host gap |

Opt-in live run: the public Big Buck Bunny URL merged through the shared engine and desktop toolkit (`state=COMPLETED artifacts=1 video=1 audio=1`, the `merging` phase was observed, and no yt-dlp process started). No FFmpeg binary, cookie, private URL, or media file was added. [T-013](../06-tasks/T-013-Media-formats.md) and [T-022](../06-tasks/T-022-Parity-audit.md) stay open.

## Explicitly later

- [Phase 6 — spotDL parity](Phase-6-SpotDL-Parity.md), after this phase. X / Twitter is the named site after D6.
- Captions, thumbnails, embed metadata, clips, chapters, SponsorBlock.
- YouTube playlists, live, cookies, PO tokens.
- A web merge or transcode. Bundling FFmpeg. Retiring the desktop CLI or Chaquopy. Store submission.

## How to start a session

Paste the prompt in [Phase 5 loop prompt](Phase-5-Loop-prompt.md) as the first message.

1. Read this note, [ADR-009](../03-decisions/ADR-009-Media-toolkit-phase.md), and [ADR-007](../03-decisions/ADR-007-Generic-extractor-phase.md).
2. On [Kanban](../Kanban.md), take the first D5 card whose dependencies are Done.
3. Read that task note fully before editing code.
4. Do not pick up Phase 6, X/Twitter, playlists, cookies, captions, or M2/M3 cards.
