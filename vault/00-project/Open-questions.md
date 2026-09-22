---
type: decisions-needed
tags: [project, decisions]
---

# Open questions

[Home](../Home.md) · [Decision log](../03-decisions/Decision-log.md) · [Roadmap](Roadmap.md)

Owner answers from 2026-09-21 are recorded in [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md). Rows still marked open are not decided.

| ID | Question | Decision or starting point | Gate / owner task |
| --- | --- | --- | --- |
| Q-01 | Is **AnyDownload** the final name? Keep `anydownlod`, or rename the repository? | **Decided:** product name is AnyDownload (`anydownload`). Repository slug stays `anydownlod` until a rename is requested. | M0 / [T-003](../06-tasks/T-003-Approve-product-scope.md) |
| Q-02 | Is a user-managed remote server acceptable, or must a phone work independently? | **Decided:** local-only on iOS, Compose/Wasm, Android, and desktop. No required backend. | M0 / T-003, [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) |
| Q-03 | Who uses the server: one owner, a trusted household, or unrelated public users? Who hosts/pays for it? | **Decided:** there is no server and no app login. The app is a local portfolio project. | M0 / T-003 |
| Q-04 | Build a new backend or connect to existing MeTube? Is MeTube API compatibility required? | **Decided:** port yt-dlp and MeTube workflows into the app. No MeTube protocol compatibility. | M0 / [ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md) |
| Q-05 | Can Compose/Wasm meet browser, accessibility, bundle-size, and file-export requirements? | Test Chrome/Edge, Firefox, Safari, including mobile browsers. Keep Kotlin/JS + web-specific UI as a fallback. Record exact versions. | M0 / [T-004](../06-tasks/T-004-Validate-KMP-targets.md) |
| Q-06 | Which minimum OS versions, desktop CPU architectures, and distribution channels are required? | Targets are decided. Minimum versions come from T-004. Distribution channels are out of scope. | M0 / T-004 |
| Q-07 | What project license and dependency-distribution model should be used? | **Decided for this project:** MIT, 2026-09-21. yt-dlp, FFmpeg, and other dependency licenses remain open in T-006. | M0 / T-006 |
| Q-08 | Are App Store / Play Store releases mandatory, and are platform download permissions obtainable? | **Decided:** publication is out of scope. Portfolio builds only. | M0 / T-003 |
| Q-09 | Are security-scoped yt-dlp overrides acceptable instead of arbitrary executable options? | Allowlist safe options; exclude shell execution and arbitrary filesystem/network overrides. Record this as a parity difference requiring approval. | M0 / T-003, T-006 |
| Q-10 | How should cookies be stored, scoped, expired, and shared between devices? | **Decided:** cookies stay on this device. No sync between devices. Import is opt-in. Design details remain in [T-018](../06-tasks/T-018-Cookie-lifecycle.md). | M3 / T-018 |
| Q-11 | Which MeTube snapshot defines “same features”? | **Decided:** the pinned 2026-09-16 review. New upstream features are explicit scope changes. Self-host rows F-24 and F-25 are withdrawn. | Audit in [T-022](../06-tasks/T-022-Parity-audit.md) |
| Q-12 | Required quotas, concurrency, retention, and maximum media/playlist size? | Files live on the device. Exact limits still need measurement. | Before M2 / [T-011](../06-tasks/T-011-Durable-queue.md), [T-014](../06-tasks/T-014-Storage-and-delivery.md) |
| Q-13 | Does a subscription import existing items or only future uploads? What happens after failures? | Make initial scan policy explicit; durable seen IDs and retry semantics; avoid silent back-catalog downloads. | Before M3 / [T-019](../06-tasks/T-019-Subscriptions.md) |
| Q-14 | Which integrations must ship: Android/iOS sharing, extensions, bookmarklets, shortcuts, Raycast? | Native share entry points. No submission API, because there is no server. Companion integrations stay in [T-020](../06-tasks/T-020-Sharing-and-UX.md). | M3 / T-020 |

## Record an answer

Update the relevant product/architecture note and ADR, note the approver/date/evidence in the owning task, and adjust dependencies and parity rows if needed. Do not erase earlier reasoning when a decision changes; supersede it.
