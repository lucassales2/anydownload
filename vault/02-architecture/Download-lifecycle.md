---
type: domain-design
status: proposed
tags: [architecture, domain]
---

# Download lifecycle and domain

[Home](../Home.md) · [Architecture](Architecture.md) · [API outline](API-outline.md) · [Testing](../04-delivery/Testing-strategy.md)

On-device domain for the local engine. Not a claim that MeTube uses these exact entities. There is no server profile and no separate device-transfer entity.

## Core entities

| Entity | Purpose / important fields |
| --- | --- |
| Download request | Source URL, type/profile/quality, bounded playlist options, destination folder, selected presets, approved overrides, cookie reference, start policy. |
| Job | Stable ID, request, state, revision, timestamps, source metadata, parent batch/subscription ID. Stored on the device. |
| Attempt | Engine version, immutable effective options, progress, start/end, error category, redacted diagnostics, retry relationship. |
| Artifact | ID, owning job, relative path inside the app storage root, kind, media type, filename, size, optional checksum. |
| Subscription | Source, enabled state, interval/next check, name/filter, initial scan policy, options, bounded seen IDs, error/check history. Checks run while the app is open. |
| Preset / secret | Versioned approved options or a local cookie reference. Secrets are not written into logs or history rows. |

## Job states

The domain enum is `com.anydownlod.core.domain.JobState`. Wire names are lowercase and stable; a name a newer writer adds loads as `UNKNOWN` and stays visible instead of dropping the row.

| State | Wire name | Kind | Meaning |
| --- | --- | --- | --- |
| `RESOLVING` | `resolving` | active | Accepted; the engine is extracting metadata and selecting a format. |
| `PENDING` | `pending` | waiting | Accepted with `StartPolicy.MANUAL`; no work starts until `start`. |
| `SCHEDULED` | `scheduled` | waiting | The source is not available yet. The user starts it again later. No timer and no live stream. |
| `QUEUED` | `queued` | active | Waiting for a worker slot; a retry re-enters here. |
| `DOWNLOADING` | `downloading` | active | Bytes are being written as they arrive. |
| `POSTPROCESSING` | `postprocessing` | active | Merge, remux, or audio extraction is running through the host toolkit. |
| `COMPLETED` | `completed` | terminal | Every artifact was finalized and registered. |
| `FAILED` | `failed` | terminal | A typed error stopped the attempt; `retry` may start a new attempt. |
| `CANCELLED` | `cancelled` | terminal | The user stopped non-terminal work; `retry` may start a new attempt. |
| `UNKNOWN` | `unknown` | read-only | A newer or unreadable wire name. Rendered, never scheduled. |

`isActive` is `RESOLVING`, `QUEUED`, `DOWNLOADING`, `POSTPROCESSING`. `isTerminal` is `COMPLETED`, `FAILED`, `CANCELLED`.

## Transitions

These are the transitions the engines implement today. Every transition also updates `updatedAtEpochMillis` and increments `revision`; the latest revision wins a stale-write race.

| From | To | Trigger |
| --- | --- | --- |
| (new) | `RESOLVING` | `submit` accepts a request and opens the first attempt. |
| `RESOLVING` | `PENDING` | `submit` with `StartPolicy.MANUAL`. |
| `RESOLVING` | `QUEUED` | `submit` with `StartPolicy.AUTOMATIC`. |
| `RESOLVING` | `FAILED` | Extraction or validation error. |
| `PENDING` | `QUEUED` | `start`. |
| `SCHEDULED` | `QUEUED` | `start` — the explicit recheck. A source that is still unavailable re-enters `SCHEDULED`. |
| `SCHEDULED` | `FAILED` | Availability check fails. |
| `QUEUED` | `DOWNLOADING` | A worker slot is acquired. |
| `DOWNLOADING` | `POSTPROCESSING` | Media bytes finished; the toolkit runs if the request needs it. |
| `POSTPROCESSING` | `COMPLETED` | Every artifact is finalized and registered. |
| any active | `FAILED` | Network failure, unsupported format, disk/quota exhausted, postprocessing failure, engine unavailable. |
| any non-terminal | `CANCELLED` | `cancel`. |
| `FAILED` or `CANCELLED` | `QUEUED` | `retry` appends a new attempt and queues it. `retry` of `COMPLETED` is refused. |

A **retry creates a new attempt** on the same logical job. The failed attempt keeps its own state, progress, and error; a retry never rewrites history and never downloads a second copy of an artifact a completed job already registered. Two `submit` calls with the same non-blank idempotency key return the first job; removing history releases the key.

“Scheduled” means availability/waiting behavior for supported upcoming sources. User-chosen calendar scheduling is not implied, and D8 adds no countdown, timer, or live stream: a not-yet-available source becomes `SCHEDULED` and the user starts it again later. Subscription scheduling stays a separate mechanism.

## Restart on load (D8)

Closing the app interrupts active work. On the next load every host applies the same rule, copied from `DesktopStore.load`:

| State on disk | After load |
| --- | --- |
| `RESOLVING`, `QUEUED`, `DOWNLOADING`, `POSTPROCESSING` | `FAILED` with `JobErrorCode.ENGINE_UNAVAILABLE`, `retryable = true`, a redacted message, and the open attempt closed. |
| `PENDING`, `SCHEDULED` | Unchanged. |
| `COMPLETED`, `FAILED`, `CANCELLED` | Unchanged. A completed artifact stays registered. |
| A newer unknown wire name | `UNKNOWN`, still visible. |

Restored active work is never auto-resumed and no partial temp file is promoted to a finished artifact. The row is back, marked retryable, and the user chooses retry.

## Destructive actions are separate

| Operation | Effect on the row | Effect on files |
| --- | --- | --- |
| `cancel(jobId)` | Non-terminal work becomes `CANCELLED` with `JobErrorCode.CANCELLED`. A terminal row is unchanged. | None. A finished file is not deleted. |
| `removeHistory(jobId)` | The row and its in-memory idempotency mapping are dropped. Returns false for an unknown id. | None. The file stays on disk. |
| `deleteArtifacts(jobId)` | The row stays; matching artifacts are marked `removed`. | Each artifact under the download root is deleted. Partial failures are returned by file name and never throw. |

Removing a queue item is still `cancel`, not deletion of an existing artifact. On web, a file the browser already handed to the user cannot generally be deleted later by the app; that limitation is recorded, not hidden.

## No login and no HTTP job API

ADR-004 makes the app local-only. There is no account, no login step, no server, and no HTTP job API; the [API outline](API-outline.md) is a withdrawn server sketch. Screens call the in-process `DownloadEngine` and observe its `StateFlow<List<DownloadJob>>`; the on-device store is authoritative, and raw process output, signed media URLs, and cookie material never enter a job.

## Invariants to test

- A persisted accepted job survives app restart. On-device storage is authoritative; in-memory progress is not.
- Idempotency keys distinguish network retries from intentional repeat downloads. Playlist expansion needs per-child dedupe and bounded growth.
- Only one active attempt owns a job/worker lease. A restart marks abandoned active work `FAILED` with `ENGINE_UNAVAILABLE`, retryable; it never requeues or resumes by itself.
- Completed means postprocessing, validation and artifact registration succeeded. Temporary/partial files must not be served as final output.
- Cancellation is race-safe and stops the in-app download and postprocess work; terminal completion versus cancel has a deterministic persisted winner.
- Percent/size/ETA may be unknown or reset between streams/phases. Preserve units, phase and attempt identity.
- UI updates come from the in-app job state. Duplicate progress events must not regress state.
- Partial download resumption depends on extractor, format, and temp-file availability. Cancellation/retry is not a universal pause/resume feature.
- Removing history, deleting the on-device file, and cancelling work are separate operations.

## Subscription specifics

Store seen IDs and scan outcome durably; define whether failed/skipped items remain eligible, how manual retries work, and how overlapping subscriptions avoid unintended duplicates. Bound scans and regex execution; coalesce overlapping checks. Persist UTC timestamps and define backoff/jitter and downtime catch-up policy. Cookies/options are referenced without exposing values.

## Suggested error categories

Invalid URL/options · unsupported source · unavailable/private media · site login required · rate limited · network failure · extraction failure · unsupported format · postprocessing failure · disk/quota exhausted · cancelled · engine unavailable.

Expose an actionable message and retryability. Raw extractor diagnostics, private URLs, headers, and cookie values are not safe to show in the UI or publish in bug reports.
