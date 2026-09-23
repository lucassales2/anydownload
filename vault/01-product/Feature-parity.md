---
type: feature-matrix
status: proposed
tags: [product, parity, research]
---

# MeTube feature-parity matrix

[Home](../Home.md) · [Product brief](Product-brief.md) · [Roadmap](../00-project/Roadmap.md) · [Upstream review](../05-research/Upstream-review.md)

**Baseline reviewed:** 2026-09-16, MeTube commit `6708a882294a6e8c5ffe097354c7eee42eb0f309`. This is a source/documentation inventory, **not a runtime parity test**. Every AnyDownload capability below is **planned, not implemented**.

Phase D1 implements the desktop workflows for these rows by calling an installed yt-dlp, not by porting it. The task map is in [Phase 1](../00-project/Phase-1-Desktop-MeTube.md). A D1 pass is not the [T-022](../06-tasks/T-022-Parity-audit.md) audit.

Evidence keys: [R — README](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/README.md), [U — UI controls](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/ui/src/app/app.html), [F — formats](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/ui/src/app/interfaces/formats.ts), [D — format/postprocessing implementation](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/app/dl_formats.py), [A — server handlers](https://github.com/alexta69/metube/blob/6708a882294a6e8c5ffe097354c7eee42eb0f309/app/main.py).

| ID | Reviewed MeTube capability | AnyDownload acceptance target | Milestone / tasks | Evidence |
| --- | --- | --- | --- | --- |
| F-01 | Submit a video/social-media URL supported by yt-dlp. | Single-URL local download on iOS, Compose/Wasm, Android, and desktop; metadata/title, unsupported/login-required errors, source attribution. Coverage grows with the Kotlin port. | M1 / [T-010](../06-tasks/T-010-Remote-vertical-slice.md) | R, U, A |
| F-02 | Video profiles: Auto, MP4, iOS compatible; Auto/H.264/HEVC/AV1/VP9 codec preferences; best/worst and resolution choices. | Equivalent choices with honest fallback and container/codec compatibility; distinguish selection/muxing from transcoding. | M2 / [T-013](../06-tasks/T-013-Media-formats.md) | U, F, D |
| F-03 | Audio M4A, MP3, Opus, WAV, FLAC; format-dependent quality/bitrate choices. | Extract/convert audio with FFmpeg when needed; validate output and explain lossy conversion. | M2 / T-013 | F, D |
| F-04 | Playlists/channels, item limits, playlist/channel naming templates. | Expand into bounded child jobs, allow cancellation during expansion, handle unavailable entries and item limits without hiding partial failures. | M2 / [T-012](../06-tasks/T-012-Playlists-and-batches.md), [T-014](../06-tasks/T-014-Storage-and-delivery.md) | R, U, A |
| F-05 | Batch URL import, progress/cancel, export and clipboard copy. | Parse newline-separated URLs; report per-URL results; export/copy queued/completed/failed URLs with user consent. | M2 / T-012 | U |
| F-06 | Auto-start or pending/manual-start jobs; start/cancel individually or in bulk; cancel URL addition. | Durable pending/queued states, manual start, bulk actions and cancellation of extraction/download process trees. | M2 / [T-011](../06-tasks/T-011-Durable-queue.md), T-012 | U, A |
| F-07 | Live job status, progress, speed, ETA; real-time UI updates. | Honest phase-aware progress, unknown totals supported. Closing the app interrupts active work and reloads the queue. | M1–M2 / T-010, T-011 | U, A |
| F-08 | Failed-item details/copy and individual/bulk retry. | Redacted actionable errors; retry preserves approved options, re-extracts URLs as needed, avoids duplicate artifacts. | M2 / T-011, T-014 | U, A |
| F-09 | Persistent queue/pending/completed state and configurable concurrency. | Restart-safe job history and attempts; bounded worker slots and recoverable partial files where supported. | M2 / T-011 | R, A |
| F-10 | Scheduled/upcoming job status and countdown visible in queue. | Reproduce availability/waiting behavior for supported upcoming sources; show due time and manual-start/cancel semantics. Do not infer arbitrary calendar scheduling from this UI. | M2 / T-011 | U |
| F-11 | Completed file open/download links, history, record removal, optional file deletion, auto-clear. | On-device history, open/share, record removal, and file deletion; preserve related chapter/sidecar links; test retention semantics. | M2–M3 / T-014, [T-016](../06-tasks/T-016-Clips-chapters-SponsorBlock.md) | R, U, A |
| F-12 | Video/audio roots, temporary/state directories, custom-folder selection/creation, exclusions, default folder. | On-device folders with normalized relative paths, permissions, and disk-space checks. | M2–M3 / T-014 | R, U, A |
| F-13 | Filename prefix, output templates for single/playlist/channel/chapter files, long-filename handling. | Safe templates and collision policy; confine final and intermediate paths; preserve extension and platform filename limits. | M2–M3 / T-014, T-016 | R, U |
| F-14 | Captions-only: language, manual/automatic preference, SRT/TXT/VTT/TTML. | Preserve source/preference controls; convert supported outputs and document best-effort formats. Never label fallback output as a guaranteed requested format. | M3 / [T-015](../06-tasks/T-015-Captions-thumbnails-metadata.md) | U, F, D |
| F-15 | JPG thumbnails; audio artwork/metadata; optional subtitle embedding and media/feed info/thumbnail sidecars. | Standalone artwork, metadata/embedding options, and playlist/channel sidecars; expose all output artifacts. WAV/artwork and other format limitations remain explicit. | M3 / T-015, [T-017](../06-tasks/T-017-Options-and-presets.md) | R, F, D |
| F-16 | Clip start/end, including source URL start-time handling. | Validated time ranges, defined interaction with URL timestamps, clipping accuracy/cost documented. | M3 / T-016 | U, A |
| F-17 | Chapter splitting with naming template; SponsorBlock removal. | Multiple chapter artifacts, template safety, opt-in SponsorBlock, useful behavior when markers/chapters are unavailable. | M3 / T-016 | R, U, D |
| F-18 | Upload/replace/delete browser-exported cookies and show active status. | Local opt-in cookie file; secret reference only in jobs; size/format validation, deletion, and redaction. No promise that cookies guarantee site access. | M3 / [T-018](../06-tasks/T-018-Cookie-lifecycle.md) | R, U, A |
| F-19 | Global yt-dlp JSON options via environment/file; watched reload; named multiple presets; file precedence; ordered layering and null clearing. | On-device global settings, ordered presets, and overrides; immutable effective options per attempt. | M3 / T-017 | R, A, D |
| F-20 | Optional per-download free-form yt-dlp options (upstream warns of command-execution risk). | **Open:** allowlisted overrides versus a closer yt-dlp pass-through. Shell execution stays out. Owner decision is Q-09. | M3 / T-017; review [T-006](../06-tasks/T-006-Review-security-licensing.md) | R, A |
| F-21 | Channel/playlist subscriptions: interval, bounded scans/seen IDs, title regex, skip members-only, rename, pause/resume, manual check/all/selected, delete, status/errors. | Persistent scheduler and dedupe; explicit initial backlog policy; bounded/time-limited filter evaluation; all controls and restart behavior tested. | M3 / [T-019](../06-tasks/T-019-Subscriptions.md) | R, U, A |
| F-22 | Responsive UI, light/dark/auto theme, remembered options, selection/bulk controls, toasts, version information. | Equivalent adaptive UX on all targets plus keyboard, screen-reader, and large-text support. Preferences are local. | M3 / [T-020](../06-tasks/T-020-Sharing-and-UX.md) | R, U |
| F-23 | Ecosystem integrations: browser extensions/bookmarklets, iOS shortcut, Android sharing apps, Raycast extension. | Native share entry points and equivalent external URL-submission workflows. Decide which companion artifacts ship and whether existing MeTube clients must work unchanged. | M3 / T-020; scope in [T-003](../06-tasks/T-003-Approve-product-scope.md) | R |
| F-24 | Reverse proxy, TLS, URL prefix, host/port/IPv6, public artifact URLs, CORS, optional directory listing and robots file. | **Withdrawn.** No self-host server. | — / [T-021](../06-tasks/T-021-Self-host-operations.md) | R, A |
| F-25 | Self-hosted amd64/arm64 containers; permissions/umask; logs; yt-dlp version/update controls including optional nightly updates. | **Withdrawn** as a server package. The app shows its engine revision locally. | — / T-021 | R, U, A |
| F-26 | Private-address rejection by default, credential-aware CORS, protected state directories. | Local URL and path checks; cookies and queue state stay on the device and out of logs. | M1 / [T-009](../06-tasks/T-009-Backend-vertical-slice.md) | R, A |

## How to use this matrix

1. Keep IDs stable and add new rows when the reviewed upstream scope changes.
2. Each implementation task must link test/evidence for its rows. A task being Done is not itself proof of cross-platform parity.
3. [T-022](../06-tasks/T-022-Parity-audit.md) records result, engine version, platform, fixtures, and differences for every row.
4. Security/platform adaptations require explicit approval and release notes. “Parity with documented differences” is more accurate than “identical” when F-20 or integrations differ.
5. Defaults and exact option behavior must be confirmed against a runnable pinned MeTube instance during the audit; source review alone is not sufficient.

## Not established as baseline download features

- Generic active-download pause/resume or drag-to-reorder queues. Subscription pause/resume **is** confirmed.
- Full music tagging/library organization, social feeds, multi-user SaaS, or DRM bypass.
- A stable, versioned MeTube API for arbitrary clients. Its reviewed real-time channel uses **Socket.IO**, not a plain WebSocket protocol.
