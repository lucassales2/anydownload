---
type: test-plan
status: proposed
tags: [delivery, testing]
---

# Testing strategy

[Home](../Home.md) · [Feature parity](../01-product/Feature-parity.md) · [Lifecycle](../02-architecture/Download-lifecycle.md) · [Risk register](Risk-register.md)

No application tests exist yet. This plan defines evidence needed before claiming platform support or feature parity.

## Layers

| Layer | Required coverage |
| --- | --- |
| Shared Kotlin unit tests | Option validation/layering, state reducers, idempotency semantics, errors, serialization, event ordering, format/capability presentation. |
| Contract tests | API version negotiation, auth/authorization, pagination, error schema, immutable attempt options, snapshot/event recovery, file headers/Range behavior. |
| Worker integration | Authorized media fixtures; metadata, video/audio, FFmpeg, captions/artwork, playlists, clips/chapters; cancel process tree, engine crash, timeouts, bad output and disk full. |
| Persistence/scheduler | Restart during every phase, orphan attempt recovery, duplicate submissions, subscription seen IDs/initial scan/overlapping checks, bounded retries and retention. |
| Platform integration | Auth storage, incoming shares/deep links, file picker/export/cancellation, background artifact transfer, browser restrictions and large-file memory behavior. |
| UI/accessibility | Keyboard-only and assistive tech, focus, contrast/themes, large text, narrow/wide layouts, meaningful unknown progress, error/offline states. |
| Security | SSRF through redirects/DNS/IPv6, worker egress, path traversal/symlinks, option/command injection, credential redaction/deletion, authorization and browser CSRF/CORS. |
| Packaging/operations | Reproducible dependencies and notices, Linux amd64/arm64 host images, per-OS client packages, upgrade/rollback, backup/restore and health/readiness. |

## Fixture policy

- Prefer tiny locally owned/licensed media and controlled HTTP fixtures for deterministic CI. Any local-address fixture exception belongs in an isolated test environment, never production defaults.
- Mock extractor/network boundaries for most tests; include negative cases for unavailable/private/unsupported media.
- Run a small **opt-in** live-site smoke suite against explicitly authorized samples. Site changes/rate limits are expected; do not make ordinary pull requests depend on third-party availability or personal cookies.
- Keep secrets out of fixtures, snapshots, recordings, logs, screenshots and GitHub Actions artifacts. Use synthetic cookie files for ordinary tests.
- Capture exact engine/runtime versions and expected output checksums/metadata where deterministic.

## Platform evidence matrix

| Target family | Minimum evidence before release |
| --- | --- |
| Android | Emulator and representative physical device; scoped storage, share intent, credential storage, foreground/background constraints. |
| iOS | Simulator plus physical device; Files/share export, Keychain, suspension/reconnection, background transfer boundaries, signing/distribution feasibility. |
| Desktop | Windows, macOS, Linux; file paths with Unicode/spaces, cancellation, packaging and artifact open/reveal. CPU coverage must be stated explicitly. |
| Web | Supported Chrome/Edge, Firefox, Safari versions and selected mobile browsers; feature detection, keyboard/screen reader, authenticated download, memory and tab suspension. |

Exact minimum versions and performance budgets are outputs of [T-004](../06-tasks/T-004-Validate-KMP-targets.md), not assumptions here. Use progressively large generated files to prove bounded memory for exports; choose size/time budgets from measurements.

## High-risk scenarios

1. User double-taps Add while the network response is lost: exactly one intended job.
2. Client reconnects after missing/duplicated events: authoritative final state without regressions.
3. Server dies during FFmpeg: no fake completed artifact; recovery is deterministic.
4. Cancelling a playlist expansion or an active download leaves no runaway child processes.
5. Removing history with file deletion disabled preserves media; file deletion enabled removes only the intended owned artifacts.
6. Expired cookies, unsupported captions/profile, changing source formats, proxy/network failure and engine update all produce safe actionable errors.
7. Repeated subscription scans do not redownload seen items unexpectedly; crash recovery does not lose new eligible items.
8. Export to iOS/browser fails or is cancelled while server job remains completed.

## Parity and release gates

[T-022](../06-tasks/T-022-Parity-audit.md) produces one evidence/result entry per F-ID against the pinned MeTube baseline, with target/version and approved differences. [T-023](../06-tasks/T-023-Release-readiness.md) validates packaging, accessibility, security, notices and user/admin docs. A checked card or screenshot alone is not full parity evidence.

## Current documentation checks

For this planning-only repository, validate local Markdown links, frontmatter/JSON, unique task IDs, one card per task, acyclic existing dependencies, task-to-parity links, no accidental secret/runtime/plugin files in Git, and public repository visibility. No app build or automated application-test pass can be claimed yet.
