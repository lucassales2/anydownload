---
type: test-plan
status: proposed
tags: [delivery, testing]
---

# Testing strategy

[Home](../Home.md) · [Feature parity](../01-product/Feature-parity.md) · [Lifecycle](../02-architecture/Download-lifecycle.md) · [Risk register](Risk-register.md)

Shared unit tests exist for the draft client. This plan defines evidence needed before claiming a local engine works on a target or that a MeTube workflow is done.

## Layers

| Layer | Required coverage |
| --- | --- |
| Shared Kotlin unit tests | Option validation/layering, state reducers, idempotency semantics, errors, serialization, event ordering, format/capability presentation. |
| Engine tests | Metadata, video/audio, postprocessing, captions/artwork, playlists, clips/chapters; cancel, crash, timeouts, bad output, and disk full. |
| Persistence | Restart during every phase, interrupted-attempt recovery, duplicate submissions, subscription seen IDs, bounded retries, and retention. |
| Platform integration | Incoming shares/deep links, file save/open/cancellation, suspension, browser fetch limits, and large-file memory behavior. |
| UI/accessibility | Keyboard-only and assistive tech, focus, contrast/themes, large text, narrow/wide layouts, meaningful unknown progress, error/offline states. |
| Security | Redirects to unexpected hosts, path traversal, option injection, cookie redaction/deletion. |
| Packaging | Repeatable local builds for iOS, Compose/Wasm, Android, and desktop, plus license notices. |

## Fixture policy

- Prefer tiny locally owned/licensed media and controlled HTTP fixtures for deterministic CI. Any local-address fixture exception belongs in an isolated test environment, never production defaults.
- Mock extractor/network boundaries for most tests; include negative cases for unavailable/private/unsupported media.
- Run a small **opt-in** live-site smoke suite against explicitly authorized samples. Site changes/rate limits are expected; do not make ordinary pull requests depend on third-party availability or personal cookies.
- Keep secrets out of fixtures, snapshots, recordings, logs, screenshots and GitHub Actions artifacts. Use synthetic cookie files for ordinary tests.
- Capture exact engine/runtime versions and expected output checksums/metadata where deterministic.

### Extractor tests (D4 onward)

- Extractor cases follow yt-dlp's `_TESTS` shape and run through the shared harness ([T-059](../06-tasks/T-059-Extractor-test-harness.md)): fixture mode by default, live mode only with `-PliveExtractorTests=true` against the public URLs listed in the owning task note.
- Fixtures for sites that return signed media URLs (YouTube's `googlevideo`), visitor ids, or tokens are synthesized, never recorded raw. The recorder redacts before writing and a test greps fixtures for real hosts.
- The installed `yt-dlp` on desktop is an opt-in oracle ([T-062](../06-tasks/T-062-Desktop-ytdlp-oracle.md), `-PytDlpOracle=true`): `yt-dlp -J` output is normalized and compared with the Kotlin `InfoDict`; the diff prints field names and values, never URLs. Version drift from the pin skips the test and says so.
- JavaScript runtime adapters are tested on each host with a synthetic challenge against the bundled solver; script output never appears in test logs.

## Platform evidence matrix

| Target family | Minimum evidence before release |
| --- | --- |
| Android | Emulator and representative physical device; scoped storage, share intent, credential storage, foreground/background constraints. |
| iOS | Simulator plus physical device; Files/share export, Keychain, suspension/reconnection, background transfer boundaries, signing/distribution feasibility. |
| Desktop | Windows, macOS, Linux; file paths with Unicode/spaces, cancellation, packaging and artifact open/reveal. CPU coverage must be stated explicitly. |
| Web | Chrome/Edge, Firefox, and Safari, including a mobile browser; keyboard/screen reader, memory, cross-origin fetch limits, and tab suspension. |

Exact minimum versions and performance budgets are outputs of [T-004](../06-tasks/T-004-Validate-KMP-targets.md), not assumptions here. Use progressively large generated files to prove bounded memory for exports; choose size/time budgets from measurements.

## High-risk scenarios

1. User double-taps Add while the network response is lost: exactly one intended job.
2. The app restarts mid-download: the queue reloads and the interrupted attempt is not marked completed.
3. Postprocessing fails: no fake completed artifact; recovery is deterministic.
4. Cancelling a playlist expansion or an active download leaves no runaway child processes.
5. Removing history with file deletion disabled preserves media; file deletion enabled removes only the intended owned artifacts.
6. Expired cookies, unsupported captions/profile, changing source formats, proxy/network failure and engine update all produce safe actionable errors.
7. Repeated subscription scans do not redownload seen items unexpectedly; crash recovery does not lose new eligible items.
8. A save fails or is cancelled on iOS or in the browser: the history row matches the file that actually exists.

## Parity and release gates

[T-022](../06-tasks/T-022-Parity-audit.md) produces one evidence/result entry per F-ID against the pinned MeTube baseline, with target/version and approved differences. [T-023](../06-tasks/T-023-Release-readiness.md) validates packaging, accessibility, security, notices and user/admin docs. A checked card or screenshot alone is not full parity evidence.

## Current documentation checks

For this planning-only repository, validate local Markdown links, frontmatter/JSON, unique task IDs, one card per task, acyclic existing dependencies, task-to-parity links, no accidental secret/runtime/plugin files in Git, and public repository visibility. No app build or automated application-test pass can be claimed yet.
