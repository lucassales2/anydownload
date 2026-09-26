---
type: product-brief
status: accepted
tags: [product, scope]
---

# Product brief

[Home](../Home.md) · [Feature parity](Feature-parity.md) · [User flows](User-flows.md) · [Open questions](../00-project/Open-questions.md)

## Vision

AnyDownload is a local downloader. It ports yt-dlp's media support and MeTube's download workflows into one app on **iOS, web (Compose/Wasm), Android, and desktop**. Everything runs on the device. There is no backend and no app login.

**Name:** AnyDownload (`anydownload`). **Repository slug:** `anydownlod` until a rename is requested. **Purpose:** a portfolio project. Store publication is out of scope. **Implementation status:** a draft remote-client scaffold exists and does not match this direction.

Accepted by the owner on 2026-09-21. Record: [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md).

## Intended use

The owner uses the app locally to save media from sites yt-dlp supports, with MeTube-style queue, format, playlist, and history workflows. Spotify queries follow spotDL: metadata from Spotify, audio from a match, then the same engine. That full surface is Phase D6, after the media toolkit. There is no server owner, no multi-user mode, and no AnyDownload account. A Spotify login stored on the device is only for that user's library.

## Product principles

1. Share the engine, domain, and UI in Kotlin. Use platform adapters for HTTP, files, sharing, and lifecycle.
2. Run extraction, download, queue, and history inside the app.
3. Make progress, errors, retries, and retention understandable. Do not silently delete media.
4. Track yt-dlp's site support as the coverage goal, and MeTube's reviewed workflows as the product surface. Match behavior, not MeTube's wire protocol.
5. Ship local builds for the portfolio. Do not block features on store review.

## Scope by release

### M1 vertical slice

On every target, the in-app engine accepts one public URL, shows progress, and writes a media file on the device. A failed URL shows a useful error. This proves the local engine, not full site coverage.

### M2 MVP

On-device queue with concurrency limits, cancellation/retry, playlists/channels, batch links, useful video/audio quality profiles, and completed history. Files stay on the device, within each OS/browser's storage limits.

### M3 parity candidate

Complete the [feature matrix](Feature-parity.md) locally: captions/thumbnails, clips, chapter splitting, SponsorBlock, cookies, presets/options, subscriptions, and sharing. Document every platform difference. Self-host server controls are dropped.

### M4 portfolio build

Repeatable local build instructions for iOS, Compose/Wasm, Android, and desktop, plus license notices for ported and bundled code. Store listing, signing for public release, and hosted deployment are out of scope.

## Non-goals and boundaries

- A claim that the first build already downloads every site yt-dlp supports. That coverage is the goal of the port and moves with upstream.
- DRM removal, paywall circumvention, or credential theft.
- A required backend, app account, public multi-tenant host, or billing.
- Shelling out to the Python yt-dlp CLI as the shared engine. The engine is Kotlin.
- Unrestricted shell commands or third-party plugin execution from download options.
- Guaranteed byte-level pause/resume or queue drag-reordering: these were not established as MeTube download controls in the reviewed baseline. Subscription pause/resume is in scope.
- MeTube HTTP/Socket.IO compatibility, or compatibility with every existing MeTube extension. The target is the same workflows.
- App Store or Play publication.

## Acceptance definition

- All four target families pass the same local journey: submit a URL, watch progress, keep the file on the device.
- Each parity row has reproducible evidence on the relevant targets, not just a checked implementation task.
- Restarting the app does not lose the on-device queue or create unintended duplicates.
- Errors and logs contain no cookies or other secrets.
- Large files are written as they arrive, without holding the whole media file in memory.
- Accessibility, error states, and minimum platform versions are explicit. Store approval is not a release criterion.

## Superseded proposal

Until 2026-09-21 the brief recommended a single-owner authenticated server, with local desktop/Android engines as an optional later track, and treated a Kotlin reimplementation of yt-dlp as out of scope. That proposal is withdrawn. Original reasoning remains in [ADR-001](../03-decisions/ADR-001-Execution-model.md).
