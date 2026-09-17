---
type: product-brief
status: proposed
tags: [product, scope]
---

# Product brief

[Home](../Home.md) · [Feature parity](Feature-parity.md) · [User flows](User-flows.md) · [Open questions](../00-project/Open-questions.md)

## Vision

Give users a consistent way to save authorized video/audio from yt-dlp-supported social and video platforms, with MeTube-equivalent download workflows on **Android, iOS, Windows/macOS/Linux desktop, and web**.

**Working name:** AnyDownload. **Current deliverable:** a public planning repository and Obsidian vault. **Implementation status:** not started.

## Intended users — proposed

- A person saving their own or permitted public media for offline use.
- A self-hosting user who wants downloads to continue on a trusted server while their phone/browser is closed.
- A user archiving authorized playlists/channels or following new uploads through subscriptions.

The initial recommendation is a **single-owner, self-hosted server**, not an anonymous public download service. Authentication remains required. Multi-tenant hosting and billing are not implied.

## Product principles

1. Share domain/networking code where Kotlin supports it; use platform-specific storage, sharing, accessibility, and lifecycle APIs where needed.
2. Treat a source-media job and exporting its files to a device as separate operations.
3. Make progress, errors, retries, and retention understandable; do not silently delete media or claim unsupported formats.
4. Prefer a trusted remote engine for cross-platform reach. Local desktop/Android execution is a separate optional track.
5. Match the reviewed MeTube workflows without copying its implementation or assuming its API is a stable public contract.
6. Apply secure defaults to every server/API, including self-hosted installations.

## Scope by release

### M1 vertical slice

A configured, authenticated client on every target can submit an authorized URL, monitor a server job, and export a completed artifact. Demonstrate disconnect/reconnect and a useful failure message. This proves architecture, not broad downloader coverage.

### M2 MVP

Durable queue with concurrency limits, cancellation/retry, playlists/channels, batch links, useful video/audio quality profiles, completed history, and secure streaming file delivery. Device export works within each OS/browser's limitations.

### M3 parity candidate

Complete the [feature matrix](Feature-parity.md), including captions/thumbnails, clips, chapter splitting, SponsorBlock, cookies, presets/options, subscriptions, integrations, self-host controls, and engine updates. Document every platform/security difference.

### M4 release

Tested packages and browser deployment, license notices, support matrix, user/admin documentation, security/accessibility checks, and viable distribution channels. Store acceptance is not assumed.

## Non-goals and boundaries

- A literal guarantee to download **any** media. yt-dlp site support is version-dependent and may break.
- DRM removal, paywall circumvention, credential theft, or access to media without permission. Authorized cookie-based access is distinct from bypassing access controls.
- Reimplementing yt-dlp's extractors in Kotlin or running an arbitrary native CLI inside a browser.
- Standalone iOS execution, public multi-tenant hosting, billing, social feeds, or a full music/video library manager in the initial plan.
- Unrestricted shell commands, third-party plugin execution, or arbitrary server filesystem access from download options.
- Guaranteed byte-level pause/resume or queue drag-reordering: these were not established as MeTube download controls in the reviewed baseline. Subscription pause/resume is in scope.
- Automatic compatibility with every existing MeTube extension/client; equivalent integration workflows and compatibility requirements are separate decisions.

## Acceptance definition

- All four target families pass the same core remote-mode journey.
- Each parity row has reproducible evidence on the relevant targets and server, not just a checked implementation task.
- Restart/reconnect do not lose durable jobs or create unintended duplicates.
- Protected artifacts/cookies are inaccessible without authorization; errors and logs contain no secrets.
- Large artifacts are streamed/exported without loading an entire media file into application memory.
- Accessibility, offline/error states, minimum platform versions, quotas, and retention have explicit tested requirements before release.

No performance claims, dates, staffing assumptions, or store-approval guarantees have been made. Approve scope through [T-003](../06-tasks/T-003-Approve-product-scope.md).
