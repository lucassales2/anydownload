---
type: decisions-needed
tags: [project, decisions]
---

# Open questions

[Home](../Home.md) · [Decision log](../03-decisions/Decision-log.md) · [Roadmap](Roadmap.md)

No response is assumed from the owner. Recommendations below are inputs to review, not accepted requirements.

| ID | Question | Starting recommendation | Gate / owner task |
| --- | --- | --- | --- |
| Q-01 | Is **AnyDownload** the final name? Keep `anydownlod`, or rename the repository? | Use AnyDownload as a working name; keep the current folder/repo spelling until confirmed. Check naming/trademark conflicts before release. | M0 / [T-003](../06-tasks/T-003-Approve-product-scope.md) |
| Q-02 | Is a user-managed remote server acceptable, or must a phone work independently? | Remote-first on all four targets; optional local Android/desktop later. Standalone requirements may materially change feasibility. | M0 / T-003, [T-005](../06-tasks/T-005-Choose-backend-engine.md) |
| Q-03 | Who uses the server: one owner, a trusted household, or unrelated public users? Who hosts/pays for it? | Start with one self-hosted owner; require authentication even in that mode. No public download service in the initial scope. | M0 / T-003, [T-006](../06-tasks/T-006-Review-security-licensing.md) |
| Q-04 | Build a new backend or connect to existing MeTube? Is MeTube API compatibility required? | Compare MeTube adapter, Ktor + isolated worker, and Python service with the same spike. Do not confuse feature parity with protocol compatibility. | M0 / T-005 |
| Q-05 | Can Compose/Wasm meet browser, accessibility, bundle-size, and file-export requirements? | Test Chrome/Edge, Firefox, Safari, including mobile browsers. Keep Kotlin/JS + web-specific UI as a fallback. Record exact versions. | M0 / [T-004](../06-tasks/T-004-Validate-KMP-targets.md) |
| Q-06 | Which minimum OS versions, desktop CPU architectures, and distribution channels are required? | Evaluate Android + iOS, Windows/macOS/Linux, and browser support explicitly. Do not invent minimum versions before toolchain validation. | M0 / T-004, T-006 |
| Q-07 | What project license and dependency-distribution model should be used? | Decide before adopting copyleft code or shipping yt-dlp/FFmpeg binaries. No default license selected. | M0 / T-006 |
| Q-08 | Are App Store / Play Store releases mandatory, and are platform download permissions obtainable? | Assess store policies early, especially Apple's third-party media-download rule; a remote engine does not remove that policy risk. | M0 / T-006 |
| Q-09 | Are security-scoped yt-dlp overrides acceptable instead of arbitrary executable options? | Allowlist safe options; exclude shell execution and arbitrary filesystem/network overrides. Record this as a parity difference requiring approval. | M0 / T-003, T-006 |
| Q-10 | How should cookies be stored, scoped, expired, and shared between devices? | Explicit opt-in to a trusted server, per-owner secret storage, never returned to clients or logged. No browser-cookie scraping on iOS/web. | M0 / T-006; design in [T-018](../06-tasks/T-018-Cookie-lifecycle.md) |
| Q-11 | Which MeTube snapshot defines “same features”? | Use the pinned 2026-09-16 review, then audit before release. New upstream features are explicit scope changes. | M0 / T-003; audit in [T-022](../06-tasks/T-022-Parity-audit.md) |
| Q-12 | Required quotas, concurrency, retention, and maximum media/playlist size? | Bounded defaults, no unbounded jobs; show whether files live on server or device. Exact limits need measurement and owner input. | Before M2 / [T-011](../06-tasks/T-011-Durable-queue.md), [T-014](../06-tasks/T-014-Storage-and-delivery.md) |
| Q-13 | Does a subscription import existing items or only future uploads? What happens after failures? | Make initial scan policy explicit; durable seen IDs and retry semantics; avoid silent back-catalog downloads. | Before M3 / [T-019](../06-tasks/T-019-Subscriptions.md) |
| Q-14 | Which integrations must ship: Android/iOS sharing, extensions, bookmarklets, shortcuts, Raycast? | Native share entry points plus a documented submission API; prioritize equivalent browser workflows, decide exact compatibility separately. | M0 scope / T-003; implementation in [T-020](../06-tasks/T-020-Sharing-and-UX.md) |

## Record an answer

Update the relevant product/architecture note and ADR, note the approver/date/evidence in the owning task, and adjust dependencies and parity rows if needed. Do not erase earlier reasoning when a decision changes; supersede it.
