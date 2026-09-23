---
id: T-026
type: task
priority: P0
milestone: D1
tags: [task, domain, desktop]
---

# T-026 — Shared domain and engine seam

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [ADR-005](../03-decisions/ADR-005-Desktop-metube-phase.md) · [Lifecycle](../02-architecture/Download-lifecycle.md)

## Outcome

Shared Kotlin types and interfaces that every later D1 screen and the desktop yt-dlp adapter share. An in-memory fake implements them so the UI can be built before any process exists.

## Dependencies

None. Read the phase note before editing. This is the first D1 task.

## Context the next session needs

`shared/core` already has a thin server-era model:

- `com.anydownlod.core.domain.MediaType` — `VIDEO`, `AUDIO`, `CAPTIONS`, `THUMBNAIL`. Keep these four. Captions-only and thumbnail-only downloads are first-class, matching MeTube.
- `StartPolicy` — `AUTOMATIC` (MeTube auto-start yes) and `MANUAL` (stay pending until Start).
- `JobState` — `RESOLVING`, `PENDING`, `SCHEDULED`, `QUEUED`, `DOWNLOADING`, `POSTPROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `UNKNOWN`. Keep this enum. D1 uses `SCHEDULED` for MeTube's "upcoming / waiting" rows. Do not add a second status enum.
- `JobProgress`, `JobError`, `DownloadRequest`, `DownloadJob`. These are too small for the MeTube form. Extend them. Rewrite comments that say "server", "device transfer", or "API outline". The authority is the on-device store.
- `SourceUrlValidator` rejects whitespace and non-HTTP(S) URLs. Batch paste must split on newlines first, then validate each line. Do not loosen the validator to accept a multi-line string.
- `shared/network` and `Capabilities` are the withdrawn server client. Do not add D1 fields there. Do not delete the module in this task.

There is no `DownloadEngine` yet. Create it in `shared/core`. Do not create a new Gradle module. Do not reference `ProcessBuilder`, `java.lang.Process`, or yt-dlp anywhere in `shared/core`.

## Work

### Download request

Extend `DownloadRequest` so one value can describe a MeTube add-form submission. Include at least:

- Source URL, `MediaType`, `StartPolicy`.
- Video container profile: auto, MP4, iOS-compatible. Nullable when the media type is not video.
- Video codec preference: auto, H.264, HEVC, AV1, VP9.
- Quality: best, worst, or a resolution token the UI chose (store the token, do not parse yt-dlp format strings here).
- Audio container: M4A, MP3, Opus, WAV, FLAC, plus an optional bitrate token. Used when media type is audio.
- Captions: language code, manual-vs-automatic preference, output format SRT, TXT, VTT, or TTML. Used when media type is captions, and also as an optional sidecar when embedding is requested on video/audio.
- Thumbnail-only is `MediaType.THUMBNAIL`. Optional flags for embedding subtitles, writing metadata, and writing thumbnail sidecars belong on the request for every media type that MeTube allows them on.
- Filename prefix, destination folder (relative name inside the download root, not an absolute path), playlist item limit (0 means no limit), clip start and end as optional timestamps.
- Flags: split by chapters, SponsorBlock removal.
- Selected preset ids, in order.
- A client-generated idempotency key so a double-clicked Download becomes one job.

Leave a named placeholder for custom yt-dlp JSON, always empty. Q-09 is open. No code path reads a user-supplied option map in this task.

### Job, attempt, artifact, subscription, settings

`DownloadJob` needs a stable id, the request, `JobState`, revision, timestamps, title and source host when known, optional thumbnail URL, parent batch or subscription id, latest `JobProgress`, latest `JobError`, and the artifact list. Percent, bytes, speed, and ETA are nullable.

Add an attempt concept, even if the fake only keeps the latest one: a retry creates a new attempt and does not rewrite the failed attempt as if it never happened. See the lifecycle note.

An artifact has an id, owning job id, kind (video, audio, captions, thumbnail, chapter, metadata sidecar), file name, relative path under the download root, and optional size. The fake can store paths that are not real files.

A subscription stores: id, source URL, display name, paused flag, check interval in minutes, title-filter regex (empty means all), skip-members-only flag, the `DownloadRequest` options captured at subscribe time (without the one-off URL), last check, next check, last error, and a bounded set of seen ids. Download options are fixed at creation. Name, interval, filter, and members-only can change later. That matches MeTube: changing quality means delete and subscribe again.

Settings are an observable object, not environment variables:

- Download root (absolute path, desktop-only; the shared type may hold a string the fake leaves blank).
- Output templates, defaulting to MeTube's reviewed defaults: `%(title)s.%(ext)s`, playlist `%(playlist_title)s/%(title)s.%(ext)s`, channel `%(channel)s/%(title)s.%(ext)s`, chapter `%(title)s - %(section_number)02d - %(section_title)s.%(ext)s`.
- Max concurrent downloads, default 3.
- Clear-completed-after seconds, default 0 (off).
- Theme: system, light, dark.
- Cookie state: not configured, or configured. Never store cookie contents on this type. A later desktop task stores the file path outside the job.
- Named presets: id, name, ordered JSON object limited to keys the app already understands. The fake starts with an empty list.
- Tool probe: yt-dlp present or not, optional version; ffmpeg present or not, optional version. The fake reports both missing.

### Interfaces

Put these in `shared/core` and implement them with in-memory fakes under `com.anydownlod.core.fake`:

- `DownloadEngine` — submit, start, cancel, retry, observe jobs as a `Flow`. Submit with `StartPolicy.AUTOMATIC` lands in `RESOLVING` then `QUEUED`. Submit with `MANUAL` lands in `PENDING`. Cancel of a non-terminal job becomes `CANCELLED`. Retry of a failed job creates a new attempt and queues it. Double submit with the same idempotency key returns the original job.
- `SubscriptionRepository` — add, update name/interval/filter/members-only, pause, resume, delete, check-now, check-all, observe the list. The fake's check-now records a timestamp and does not download.
- `SettingsRepository` — observe and update settings, add/remove/reorder presets, mark cookies configured or clear them. The fake keeps this in memory.
- `ToolProbe` can be a function on settings or a small interface the fake implements. Desktop will replace it in T-033. Do not shell out here.

Also provide a factory the hosts can call, something like `InMemoryAppGraph`, that wires the three fakes together. Android, iOS, and web will use it in T-027. The fake accepts a seed list of jobs so screenshot and UI work can show every state without clicking: pending, scheduled, downloading with known percent, downloading with unknown total, post-processing, completed with an artifact, failed with a redacted `JobError`.

`JobError.code` uses the lifecycle categories: invalid URL/options, unsupported source, unavailable/private, login required, rate limited, network, extraction, unsupported format, post-processing, disk full, cancelled, engine unavailable. `message` is one short sentence a person can act on. `retryable` is false for invalid options and cancelled.

### Tests

Add `commonTest` coverage for:

- URL batch splitting: blank lines dropped, each line validated, one bad line reported without discarding the good ones.
- Idempotent submit.
- Manual start, cancel, retry-creates-a-new-attempt.
- Progress with a null percent stays null.
- Subscription option snapshot does not change when settings or the add form change later.
- Relative destination folders reject empty segments, `.`, `..`, and absolute paths.
- Preset reorder and cookie state never contain a secret value. There is no field for one.

Run `./gradlew :shared:core:jvmTest`.

## Out of scope

Compose UI, JSON files, `ProcessBuilder`, yt-dlp argument mapping, Android/iOS/web behavior, deleting `shared/network`.

## Acceptance criteria

- [x] `DownloadRequest`, `DownloadJob`, subscriptions, and settings can represent every MeTube control listed in the phase note, including a disabled custom-JSON placeholder that nothing reads.
- [x] `DownloadEngine`, `SubscriptionRepository`, and `SettingsRepository` live in `shared/core`. In-memory fakes implement them. Shared code has no process API.
- [x] Existing `JobState` and `MediaType` values are reused. Server-era comments on the touched types are rewritten for the local app.
- [x] `commonTest` covers idempotent submit, retry attempts, batch URL splitting, path rejection, and null progress.
- [x] `:shared:core:jvmTest` passes. Evidence below names the command and the date.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :shared:core:cleanJvmTest :shared:core:jvmTest --no-build-cache` — **44 tests, 0 failures** (`InMemoryDownloadEngineTest` 11, `InMemorySubscriptionRepositoryTest` 7, `InMemorySettingsRepositoryTest` 6, `BatchUrlValidatorTest` 5, `RelativePathValidatorTest` 5, plus the existing `JobStateTest` 4, `SourceUrlValidatorTest` 4, `MediaTypeTest` 2).
- `./gradlew :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:ui:compileKotlinIosSimulatorArm64 :apps:web:compileKotlinWasmJs :apps:android:compileDebugKotlin` — success, so the changed core compiles for JVM, Wasm, iOS, and the Android host.
- `grep -rn "ProcessBuilder\|java.lang.Process\|Runtime.getRuntime" shared/` — no matches. Only doc comments name yt-dlp; no shared code references a process or an engine binary.

What landed:

- `com.anydownlod.core.domain`: extended `DownloadRequest` with `DownloadOptions` (video profile/codec/quality, audio container/bitrate, captions language/preference/format, embed/metadata/thumbnail flags, filename prefix, relative destination folder, playlist item limit, clip start/end, chapter split, SponsorBlock, ordered preset ids, cookie opt-in, an always-empty custom-JSON placeholder, idempotency key); `DownloadJob` with request, revision, timestamps, title/source host/thumbnail, parent batch or subscription id, latest progress and error, artifact list, and a `JobAttempt` history that retry appends to; `Subscription` with captured options, interval/filter/members-only, last/next check, last error, and ordered seen ids; `AppSettings` with MeTube's reviewed template/concurrency defaults, theme, a boolean-only cookie state, and presets; `JobErrorCode` from the lifecycle categories.
- `com.anydownlod.core`: `DownloadEngine`, `SubscriptionRepository`, `SettingsRepository`, `ToolProbe`, and `AppGraph`.
- `com.anydownlod.core.fake`: `InMemoryDownloadEngine` (idempotent submit, automatic RESOLVING→QUEUED, manual PENDING, cancel, retry appends an attempt, seeds every D1 state), `InMemorySubscriptionRepository` (check-now only records timestamps; update never touches captured options), `InMemorySettingsRepository`, `InMemoryToolProbe` (both tools missing), and `InMemoryAppGraph`/`seeded()`.
- `com.anydownlod.core.validation`: `BatchUrlValidator` (split on newlines, drop blanks, report each bad line without discarding good ones) and `RelativePathValidator` (rejects empty segments, `.`, `..`, and POSIX/Windows/UNC absolute paths).

Notes for the next session:

- `shared/network`'s DTO mapping was adapted to the new domain shape so the stale module still compiles. No server behavior was added and no new module was created; T-027 can drop the dependency.
- `:apps:desktop:compileKotlin` is currently `NO-SOURCE` because `Main.kt` lives under `src/jvmMain` while the module uses the Kotlin JVM plugin. T-027 must wire that source set before `:apps:desktop:run` can open the window.
