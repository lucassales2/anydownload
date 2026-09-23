---
id: ADR-005
type: adr
status: accepted
created: 2026-09-21
tags: [architecture, decisions, desktop, ui]
---

# ADR-005 — Desktop MeTube phase before the Kotlin port

[Home](../Home.md) · [Decision log](Decision-log.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state: an in-process Kotlin engine on iOS, Compose/Wasm, Android, and desktop, with no backend and no app login.

## Context

On 2026-09-21 the owner ordered the implementation sequence. The first phase ports MeTube's user-facing workflows into Compose and runs them on desktop only. That phase calls the yt-dlp program already installed on the machine. It does not port yt-dlp's extractors to Kotlin, and it does not ship Android, iOS, or web behavior.

The existing scaffold (`shared/network`, the capabilities card in `shared/ui`) still talks to a withdrawn server. ADR-004 forbids `ProcessBuilder` and a Python runtime in shared code. A desktop-only adapter can call yt-dlp without putting that process API in common code.

MeTube is AGPL-3.0. Feature behavior is the source of truth ([feature parity](../01-product/Feature-parity.md), reviewed 2026-09-16 at commit `6708a882294a6e8c5ffe097354c7eee42eb0f309`). Copying MeTube's Angular or Python into this repository is out of bounds. yt-dlp's standalone release executables can bundle GPL code; this phase invokes a user-installed `yt-dlp` and `ffmpeg` and does not vendor those binaries.

## Decision

- **Phase D1** is the current implementation work. Tasks are [T-026](../06-tasks/T-026-Shared-domain-and-engine-seam.md) through [T-036](../06-tasks/T-036-Phase-1-verification.md), sequenced in the [phase note](../00-project/Phase-1-Desktop-MeTube.md).
- The desktop app shows MeTube's surfaces: Add, Downloading, Completed, Subscriptions, and Settings, including the advanced download options, empty states, and destructive confirms.
- Downloads, metadata, playlists, and subscription checks run through `yt-dlp` on `PATH`. Merging and audio conversion run through `ffmpeg` on `PATH` when yt-dlp needs them. Missing tools are a visible Settings state, not a crash.
- Shared Kotlin owns the domain, the `DownloadEngine` interface, and the Compose UI. Only `apps/desktop` may spawn a process. Common code has no `ProcessBuilder`, no Python assumption, and no MeTube HTTP or Socket.IO client.
- The UI depends on repository and engine interfaces. An in-memory fake implements them first so every screen exists before the process adapter does. A desktop JSON store then replaces the fake on the desktop host.
- Android, iOS, and web hosts keep compiling against the shared UI. They receive the in-memory fake. They do not gain a download engine in this phase.
- Free-form yt-dlp JSON stays visible and disabled. [Q-09](../00-project/Open-questions.md) is still open. The process adapter receives an allowlist of arguments built by the app, never a shell string.
- The Kotlin extractor port, and behavior on the other three targets, starts after D1. That work still follows ADR-004.

## Alternatives

- Start the Kotlin extractor port immediately, and design the UI around whatever one extractor can do. Rejected for this phase: the owner wants the full MeTube surface first, with a working downloader on desktop.
- Draw the screens with no engine at all. Rejected: the owner said this phase uses yt-dlp, so the screens have to perform the workflows, not only display them.
- Shell out to yt-dlp from common code so every target shares one adapter. Rejected: Wasm and iOS cannot host that process, and ADR-004 still forbids it in shared code.

## Consequences

D1 can download real media on desktop while the Kotlin port does not exist yet. The `DownloadEngine` seam is what lets a later phase replace `YtDlpCliEngine` without rewriting the screens.

Risks: yt-dlp's progress text and exit codes vary by version; tests must pin parser fixtures rather than a live site. A user without `yt-dlp` or `ffmpeg` installed gets an explicit unavailable state. License obligations for a future bundled binary, or for copied extractor source, stay in [T-006](../06-tasks/T-006-Review-security-licensing.md) and are not solved by this phase.

## Validation / approval

Owner, 2026-09-21, from the implementation-planning messages that defined this phase and asked for the tasks. Evidence is those messages. D1 is complete when [T-036](../06-tasks/T-036-Phase-1-verification.md) records a desktop run through the MeTube workflows.
