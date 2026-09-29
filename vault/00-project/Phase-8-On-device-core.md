---
type: phase
status: done
milestone: D8
tags: [project, queue, platforms, delivery]
---

# Phase 8 — On-device queue, playlists, and history

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md) · [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) · [Download lifecycle](../02-architecture/Download-lifecycle.md) · [Phase 7](Phase-7-X-Twitter.md)

**Start here if you are implementing.** This note is the handoff for Phase D8. ADR-004 remains the end state. D1 through D7 stay. This phase closes the remaining M1 and M2 work: the same local queue on all four hosts, then bounded playlists, batches, names, and history.

Owner direction, 2026-09-29: plan M1 and M2 as one loop. The next extractor site is not chosen here. The upstream pin stays `2026.08.19`.

## Done looks like

On desktop, Android, iOS, and web, a public or fixture URL becomes a job, shows progress, and writes a file on the device. A bad URL becomes a failed row with a redacted error. Closing the app drops active work to a retryable failure and, on the next launch, the same rows are back. A finished file is not downloaded again just because the app restarted.

A YouTube playlist fixture expands into child jobs, at most 50, and each selected child can download through the existing single-video path. A newline batch already creates children; this phase also copies those URLs back out on request. History can drop a row without deleting the file, and can delete the file without dropping the row. Cancel does neither to a finished file.

## What already exists — do not rebuild

| Piece | Where it lives | D8 uses it as |
| --- | --- | --- |
| Job states, attempts, artifacts, errors, idempotency | `shared/core` domain | The contract. T-101 writes it down and closes T-007. |
| Download, cancel, retry, one-file publish | `HttpDownloadEngine` | The worker. T-104 adds the races and the cap, and does not replace the class. |
| Desktop `jobs.json`, interrupt-on-load, clear-completed | `DesktopStore` | The behavior to share. Active states become `FAILED` / `ENGINE_UNAVAILABLE`, retryable. No auto-resume. |
| Queue, history, add, settings screens | `shared/ui`, built in D1 | The UI. T-109 and T-110 add export and make the three destructive actions obvious. |
| Newline batch split | `AddFormPresenter` | Kept. T-109 adds copy/export. |
| Merge and audio containers | D5 toolkit | Kept. T-111 only proves the profile controls follow those capabilities. |
| YouTube single video, generic page, X status, Spotify | D3–D7 | Unchanged URL behavior, except a playlist URL gains entries. |

Android, iOS, and web still construct an in-memory engine. That is the M1 hole.

## Rules for every D8 task

- No backend, app login, MeTube HTTP/Socket.IO client, or new `shared/network` API.
- No `ProcessBuilder`, Python, or Chaquopy in common code. Desktop CLI and Android Chaquopy stay only for URLs the registry does not match.
- Do not vendor yt-dlp. Do not copy MeTube, NewPipe, or YtDlp-kt. A YouTube playlist translation is file-by-file with the Unlicense notice and the pin.
- The upstream pin stays `2026.08.19`. Do not move it mid-phase.
- Cookies, signed media URLs, and private URLs stay out of logs, history, fixtures, and this vault. Tests use redacted fixtures or `*.example` hosts.
- Unknown progress stays unknown. Do not invent a percent.
- A download writes as bytes arrive. Do not buffer a whole media file in memory.
- Playlist expansion stops at 50 entries. `playlistItemLimit` 0 means that cap, not "everything".
- Cancel, remove history, and delete file stay three different operations.
- Do not add cookies, subscriptions, captions, thumbnails-as-artifacts, clips, chapters, SponsorBlock, PO tokens, or live HLS.
- Do not commit a media file. Do not commit unless a task explicitly tells you to.
- When a task is finished, check its acceptance boxes, write what you ran under Evidence, and move its Kanban card. The board column is the status. Leave T-007 through T-014 where they are until the D8 task named below moves them.

## Layout

```text
shared/core
  persist/             job document codec, interrupt, stale revision, clear-completed
  engine/              playlist expansion on the existing engine
  extract/youtube/     YoutubeTabIE, playlist URL only
apps/desktop           existing jobs.json path, now fed by the shared codec
apps/android           sandbox state file, separate from the download root
apps/ios               sandbox state file
apps/web               localStorage for the job document only
shared/ui              export/copy, and labels that keep cancel / remove / delete apart
```

Package for the document: `com.anydownlod.core.persist`. Engines stay in `com.anydownlod.core.engine`.

## Task order

Do them in this order. Dependencies are the links in each task note. M2 tasks do not start before T-106 is Done.

| Order | Task | Milestone | Delivers |
| --- | --- | --- | --- |
| 1 | [T-101](../06-tasks/T-101-On-device-contract.md) | M1 | Written contract from the code that exists. Closes T-007. |
| 2 | [T-102](../06-tasks/T-102-Shared-job-document.md) | M1 | Shared codec and interrupt rules. Desktop keeps its file. |
| 3 | [T-103](../06-tasks/T-103-Hosts-restore-queue.md) | M1 | Android, iOS, and web load and save that document. |
| 4 | [T-104](../06-tasks/T-104-Worker-cancel-retry.md) | M1 | Concurrency, cancel-versus-complete, retry does not duplicate a finished file. `SCHEDULED` for not-yet-available. |
| 5 | [T-105](../06-tasks/T-105-Gate-desktop-restart.md) | M1 | **Gate:** desktop fixture URL, failed URL, restart restores the row. |
| 6 | [T-106](../06-tasks/T-106-Four-host-m1.md) | M1 | **Gate:** the same journey on four hosts. Closes T-008, T-009, T-010. |
| 7 | [T-107](../06-tasks/T-107-Playlist-expansion.md) | M2 | Entries become child jobs, cap 50, cancel keeps children. |
| 8 | [T-108](../06-tasks/T-108-Youtube-playlist-subset.md) | M2 | `YoutubeTabIE` partial, playlist fixture only. |
| 9 | [T-109](../06-tasks/T-109-Batch-export.md) | M2 | Copy or export the URLs of a batch or a playlist. |
| 10 | [T-110](../06-tasks/T-110-History-names-folders.md) | M2 | Names, collisions, remove versus delete, disk failure. |
| 11 | [T-111](../06-tasks/T-111-Profile-honesty.md) | M2 | Profile controls match D5 capabilities. Closes T-013. |
| 12 | [T-112](../06-tasks/T-112-Phase-8-verification.md) | M2 | Four-host M2 table. Closes T-011, T-012, T-014. Accepts ADR-012. |

## Restart rules

Copied from `DesktopStore.load` so every host does the same thing:

| State on disk | After load |
| --- | --- |
| `RESOLVING`, `QUEUED`, `DOWNLOADING`, `POSTPROCESSING` | `FAILED`, `ENGINE_UNAVAILABLE`, retryable. The attempt is closed. The file is not finished. |
| `PENDING`, `SCHEDULED` | Unchanged. |
| `COMPLETED`, `FAILED`, `CANCELLED` | Unchanged. A completed artifact stays registered. |
| Newer unknown wire name | `UNKNOWN`, still visible. |

A retry of `FAILED` or `CANCELLED` appends an attempt and queues the job. A retry of `COMPLETED` is refused. Two submits with the same idempotency key return the first job.

## Playlist rules

| Input | Result |
| --- | --- |
| Playlist URL the extractor expands | One parent row plus one child job per entry, order preserved, cap 50. |
| User limit 10 | 10 children even if the page has more. |
| User limit 0 | 50, and the UI can say the cap is 50. |
| Entry unavailable or private | That child is `FAILED` with a redacted code. Other children still enqueue. |
| Cancel during expansion | Children already created stay. No further entries are fetched. |
| Same video id twice in one playlist | The second child is not created. |
| Single video, direct file, X status, Spotify | No entry expansion. Existing behavior. |

Child downloads use the existing engine. The parent does not itself download media bytes.

## Verification (2026-09-29)

D8 is verified. The four-host table below is the M2 exit gate: the same on-device queue, bounded playlists, and history actions, with each host's honest limit recorded. The queue-restart, no-duplicate-retry, and streaming-write claims cite tests.

| M2 claim | Desktop | Android | iOS | Web |
| --- | --- | --- | --- | --- |
| One fixture URL completes to a file; a failed URL is a redacted row; a new process restores both | `DesktopHttpRestartGateTest` (T-105) | `AndroidM1GateTest` (JVM-equivalent; no emulator) | `IosM1GateTest` (simulator-only) | `WebM1GateTest` (wasm/Brave; no browser click-through) |
| Queue survives restart; active work reloads `FAILED`/`ENGINE_UNAVAILABLE`, retryable; no auto-resume | `DesktopStoreTest`, `DesktopHttpRestartGateTest`, `JobDocumentStoreTest` | `AndroidM1GateTest` through `AndroidJobDocumentStorage` | `IosM1GateTest` through `IosJobDocumentStorage` | `WebM1GateTest` through `WebJobDocumentStorage` |
| Retry does not duplicate a finished file | `retryOfCompletedJobDoesNotDownloadAgain`, `lateCancelAfterCompletionKeepsTheCompletedFile` | host gate reload + retry | host gate reload + retry | host gate reload + retry |
| Files are written as bytes arrive | `DesktopHttpRestartGateTest` asserts more than one file-handle write; `JavaNetChunkedDownloadTest` | shared `HttpDownloadEngine` chunk loop | shared `HttpDownloadEngine` chunk loop | shared `WebExtensionEngine` progress path |
| Playlist fixture expands to bounded child jobs | shared `EnginePlaylistExpansionTest` (cap 50, limit 0 means 50, failed/duplicate/cancel rules) and `YoutubePlaylistEngineTest` (children download through `YoutubeIE`) | shared | shared | shared |
| Remove history, delete file, and cancel stay separate | `QueueHistoryUiTest`, `removeHistoryDropsTheRowButKeepsTheFile`, `deleteArtifactsRemovesThePublishedFile` | shared UI + engine | shared UI + engine | `deleteArtifactsReportsTheBrowserLimitationAndKeepsTheRow` |

Commands and counts: core jvm 519, core wasm 465, core iOS 476, ui jvm 99, ui iOS 8, desktop 131, android-engine-tests 26, web wasm 6, all 0 failures; `:apps:android:assembleDebug` and `:tools:port-manifest:validatePortManifest` green. Host limits: Android has no emulator/device run; iOS is simulator-only; web has no real browser click-through. Desktop was run on macOS 26.5.2 (Darwin 25.5.0).

## What this phase covers

| Milestone row | D8 answer | Closed by |
| --- | --- | --- |
| M1 one URL, progress, file, failed URL, four hosts | Fixture or public URL on each host, plus restore | T-105, T-106 |
| F-06–F-10 queue, retry, concurrency, restart | Shared document and worker | T-102–T-104, T-112 |
| F-10 countdown for upcoming media | Cut. Not-yet-available becomes `SCHEDULED`. No timer and no live stream. | T-104 records the cut |
| F-04 playlist and channel | YouTube playlist subset only. Channels stay out. | T-107, T-108 |
| F-05 batch import and export | Import already exists. Export/copy is new. | T-109 |
| F-08, F-11–F-13 history, folders, names | Safe names, collision, remove versus delete | T-110 |
| F-02, F-03 profiles | Honest mapping onto D5. No new codec. | T-111 |

## Explicitly later

- M3: captions, thumbnails, clips, chapters, SponsorBlock, presets beyond the allowlist, cookies, subscriptions on the shared engine, sharing, the parity audit (T-015–T-020, T-022).
- M4: portfolio build instructions (T-023).
- YouTube channels, mixes, continuations past the fixture, live, PO tokens, comments, subtitles.
- Any site that is not already ported. The next site is chosen when a later phase is planned.
- Auto-resume, a universal pause, and drag-to-reorder.
- Raising the playlist cap above 50.
- Retiring the desktop CLI or Chaquopy. Store submission.

## How to start a session

Paste the prompt in [Phase 8 loop prompt](Phase-8-Loop-prompt.md) as the first message, after D7 is verified.

1. Read this note and [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md).
2. On [Kanban](../Kanban.md), take the first D8 card whose dependencies are Done.
3. Read that task note fully before editing code.
4. Do not pick up T-015 or any later M3 card, and do not port another site.
