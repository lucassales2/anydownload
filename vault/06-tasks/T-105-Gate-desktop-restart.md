---
id: T-105
type: task
priority: P0
milestone: D8
tags: [task, desktop, gate]
---

# T-105 — Gate: desktop download, failure, and restart

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

On desktop, one fixture URL finishes as a file, one bad URL becomes a failed row, and a new process sees both rows. This is the M1 desktop gate.

## Dependencies

- [T-103](T-103-Hosts-restore-queue.md).
- [T-104](T-104-Worker-cancel-retry.md).

## Context the next session needs

`DesktopAppRelaunchTest` already covers an interrupted CLI-era job. This gate must go through the shared HTTP engine and the shared job document, not only the yt-dlp process adapter. Use a local fixture server or the existing fake transfer. Do not require a live site.

## Work

- Add a desktop test that submits a fixture media URL, waits until `COMPLETED`, and reads the file from the download root. Assert the file was written in chunks: the test double must not have been asked to hold the whole body as one buffer before the first write. A practical check is that the engine's file handle received more than one write, or that the existing chunked downloader test is cited and still green.
- Submit a URL that fails typed (`UNSUPPORTED_SOURCE` or `NETWORK_FAILURE`). The row is `FAILED`, the message has no URL query and no cookie, and the root has no new completed file.
- Save, build a new store and engine from the same directory, and see both rows. The completed artifact is still the same relative path. Retrying the completed job does not add a second file.
- Record the desktop OS in the task Evidence. A live public URL is not required.

## Acceptance criteria

- [x] A fixture URL completes to one file inside the download root.
- [x] A failed URL is a failed row with a redacted message and no completed file.
- [x] A second process loads both rows, and retrying the completed job does not write another copy.
- [x] Evidence names the OS and the Gradle command.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0), on-device JVM desktop host.

New test: `anydownload` (`fixtureCompletesFailureIsRedactedAndRelaunchRestoresBothRows`). It builds the desktop HTTP stack from the shared pieces (`HttpDownloadEngine` + `DesktopFileStore` + `DesktopStore` with the shared `JobDocumentStore`), not the yt-dlp process adapter, and uses a fake chunked transfer (no live site).

What the test asserts:

- The fixture URL streams 64 KiB through 4 KiB reads; `COMPLETED` with one artifact `clip.mp4` whose bytes match the payload, and a `CountingFileStore` sees more than one write, so the body is not buffered as one blob. The existing `JavaNetChunkedDownloadTest` (`shared/core` jvmTest) also stays green in the same run.
- The bad URL (`.../private?token=SECRET`, 503) is `FAILED` with `NETWORK_FAILURE`; the message contains no URL, query, `SECRET`, or cookie text; the row has no artifact; the download root still has exactly the one completed file.
- A second `DesktopProcess` built on the same state directory loads both rows (`COMPLETED` artifact still `clip.mp4`, failed row still `FAILED`), makes no transfer request at start, and `retry` of the completed row leaves it `COMPLETED` with no new request and no second file.

Verification run:

- `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.DesktopHttpRestartGateTest"` — BUILD SUCCESSFUL; 1 test, 0 failures.
- Full desktop suite `./gradlew :apps:desktop:test` — 131 tests, 0 failures (run as part of the T-104 verification batch; separate runs of the full suite were green).
