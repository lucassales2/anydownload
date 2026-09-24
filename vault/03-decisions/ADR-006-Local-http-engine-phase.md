---
id: ADR-006
type: adr
status: accepted
created: 2026-09-23
tags: [architecture, decisions, engine, platforms]
---

# ADR-006 — Local HTTP engine first; extension on web; Chaquopy on Android

[Home](../Home.md) · [Decision log](Decision-log.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md)

Does not supersede [ADR-004](ADR-004-Local-kotlin-engine.md). ADR-004 remains the end state: an in-process Kotlin port of yt-dlp on every target. Does not supersede [ADR-005](ADR-005-Desktop-metube-phase.md). Desktop still calls installed yt-dlp for URLs this phase cannot fetch as a direct file.

## Context

Phase D1 is done. The owner restated the product goal on 2026-09-23: port yt-dlp to Kotlin and run locally on iOS, Android, web, and desktop, with no backend. A full extractor catalog in one loop is not realistic. The browser cannot fetch arbitrary YouTube or CDN origins from a page. iOS and web have no subprocess for FFmpeg. License review (T-006) was still open.

Owner answers, 2026-09-23:

- This loop exits at **M1 only**: one authorized public URL on each host, or a written blocker.
- **Web** uses a **browser extension** with host permissions as the download path. The Compose/Wasm page stays the UI.
- **Android** may add a **Chaquopy** adapter under `apps/android` so pinned yt-dlp can run there while the Kotlin port is thin.
- The first engine slice is **direct HTTP(S) file download only**. No extractor. No merge or audio convert.
- Download options stay an **allowlist**. Free-form yt-dlp JSON stays disabled. No shell.
- Unlicense yt-dlp (and Unlicense ejs) may be translated into this MIT tree **with notices**. Never copy MeTube, NewPipe, or YtDlp-kt. Reimplement spotDL's workflow; copy its MIT source only after a written T-006 note.

## Decision

- **Phase D2** is the next implementation work. Tasks are [T-006](../06-tasks/T-006-Review-security-licensing.md), closing Q-09 on [T-003](../06-tasks/T-003-Approve-product-scope.md), [T-038](../06-tasks/T-038-Shared-http-engine.md) through [T-044](../06-tasks/T-044-Phase-2-verification.md), sequenced in the [phase note](../00-project/Phase-2-Local-Kotlin-Engine.md). [T-004](../06-tasks/T-004-Validate-KMP-targets.md) is closed by T-044 evidence, not by a separate spike that ignores these rules.
- Shared Kotlin owns a `HttpDownloadEngine` (or equivalent) behind the existing `DownloadEngine` seam. It streams a direct HTTP(S) body to a file. It does not parse HTML, run JavaScript, or call a media toolkit.
- Platform adapters own HTTP and file I/O. Common code still has no `ProcessBuilder` and no Python.
- Desktop: direct files go through the Kotlin HTTP engine. Every other URL stays on the installed CLI from ADR-005.
- Android: direct files go through the Kotlin HTTP engine. Other URLs may go through Chaquopy + pinned yt-dlp, only under `apps/android`.
- iOS: Kotlin HTTP only. Non-direct URLs fail with a clear “extractor not implemented” error. Foreground-only is acceptable.
- Web: the page does not fetch arbitrary origins. The extension fetches and saves. Without the extension, Add does not pretend a download started.
- Q-09 is **allowlist**. The disabled custom-JSON field stays disabled.
- Extractor translation is **out of this phase**. T-006 still writes the license inventory so the next phase can copy Unlicense source.

## Alternatives

- Port every yt-dlp extractor in this loop. Rejected: owner chose M1 only.
- In-page YouTube/CDN fetches with no extension. Rejected: CORS makes that infeasible without a backend.
- Kotlin-only on Android with no Chaquopy. Rejected for this phase: owner wants upstream coverage on Android while the port is thin.
- Include merge/audio extract now. Rejected: no per-target toolkit decision yet; this slice is HTTP-only.

## Consequences

D2 can prove the shared engine on all four families with one file URL. Desktop and Android keep a path to yt-dlp-supported sites (CLI and Chaquopy). iOS and web do not download YouTube in this phase. A later phase ports extractors and yt-dlp-ejs, then retires Chaquopy.

Risks: extension store packaging is out of scope (sideload / unpacked load is enough). Chaquopy grows the Android APK and freezes a yt-dlp version. HTTP-only is not site coverage; the UI must not claim otherwise.

## Validation / approval

Owner, 2026-09-23, from the planning answers that chose M1-only, extension-now, Chaquopy-interim, HTTP-only, allowlist, and port-Unlicense. D2 is complete when [T-044](../06-tasks/T-044-Phase-2-verification.md) records a direct-file download on each host, or a written blocker.
