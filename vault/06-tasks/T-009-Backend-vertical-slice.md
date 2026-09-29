---
id: T-009
type: task
priority: P0
milestone: M1
tags: [task, engine, security]
---

# T-009 — Local engine vertical slice

[Home](../Home.md) · [Kanban](../Kanban.md) · [Architecture](../02-architecture/Architecture.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

One public URL downloads inside the app, with progress, a device file, and a clean failure. Covers foundational F-01/F-07/F-26 behavior. No server.

## Dependencies

- [T-007](T-007-Define-UX-and-contract.md), including the M0 exit gate.

## Acceptance criteria

- [x] Accept one public URL, report progress, and write a finalized file on the device.
- [x] Keep paths inside the app storage root. Do not log cookies or signed media URLs.
- [x] Bound work and reject unsafe option/path input.
- [x] Test extraction, cancellation, and failure with integration tests. Postprocessing uses the media toolkit chosen for that target.

## Evidence / notes

Not started as this card. Direct-file and extractor downloads already exist from D2–D7. [T-105](T-105-Gate-desktop-restart.md) and [T-106](T-106-Four-host-m1.md) in [Phase 8](../00-project/Phase-8-On-device-core.md) record the evidence and close this card. Do not add a server or shell out to the Python yt-dlp CLI from common code. The first URL is a direct media URL. Spotify matching is [T-037](T-037-Spotify-youtube-match.md). Packager limits are in [Client yt-dlp options](../05-research/Client-yt-dlp-options.md).

### Closed by Phase 8 (2026-09-29)

- One public/fixture URL: `DesktopHttpRestartGateTest` on desktop and the T-106 host gates (Android JVM-equivalent, iOS simulator, web wasm) each complete a fixture media URL, show phase progress, and register the finalized artifact inside the host root. `HttpDownloadEngine` streams bytes as they arrive in 64 KiB chunks; the desktop gate counts more than one file-handle write.
- Paths stay under the host root: `JavaNetFileStore`/`IosFileStore` `resolve` refuses traversal and the shared `JobDocumentStore` clears an escaping destination on load. Jobs carry a relative path only; no cookie material or signed media URL enters a job row or a log.
- Bounded work: `UrlPolicy` (scheme/host/blocked destination), `MAX_HTML_BYTES` page cap, `MANIFEST_MAX_BYTES`, the 50-item playlist cap (T-107), and `RelativePathValidator`/typed options. Unsafe input fails with a typed error before any request.
- Integration tests: `HttpDownloadEngineTest` (extraction, cancel mid-stream, failure, retry), `EngineExtractionTest`, `EngineMergeTest`, `WebExtensionEngineTest`, plus the T-105/T-106 host gates. Postprocessing routes through the per-target `MediaToolkit` (`DesktopFfmpegToolkit`, `AndroidMediaToolkit`, `IosMediaToolkit`) and stays capability-gated on web.
