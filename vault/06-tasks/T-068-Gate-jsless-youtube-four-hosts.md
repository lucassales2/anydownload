---
id: T-068
type: task
priority: P0
milestone: D4
tags: [task, verification, youtube]
---

# T-068 — Gate: JS-less YouTube single video on four hosts

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md)

## Outcome

The mid-phase gate from [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md): without any JavaScript runtime, a public YouTube single video previews and downloads on desktop, Android, iOS, and web, or a host is recorded as blocked. No EJS task starts before this is Done.

## Dependencies

- [T-055](T-055-Port-manifest-and-equivalence-matrix.md) through [T-067](T-067-Web-extension-carries-requests.md).

## Work

1. Pick one public, permanently available video (the Big Buck Bunny test URL from upstream is fine). Do not use private, age-gated, or member videos.
2. On each host: preview shows title/channel/duration/thumbnail; download audio (M4A) and the best progressive video; cancel one download; MP3 fails typed; a playlist URL fails `UNSUPPORTED_SOURCE`; a private-video fixture fails typed.
3. Desktop: assert no process spawned for the YouTube job; unmatched site URL still on the CLI.
4. Android: emulator run, or the JVM-equivalent suite plus the APK assemble, recorded honestly.
5. iOS: simulator run; unmatched HTML still typed.
6. Web: real Chromium with the extension; without it Add refuses.
7. Run `-PliveExtractorTests=true` for the YouTube live case and `-PytDlpOracle=true` on desktop; record the results and dates.
8. Regenerate the coverage table (`YoutubeIE` becomes `partial`, scope "single video, visionos client, no JS").

## Acceptance criteria

- [ ] Four-host table with pass/fail/blocked, versions, and the hidden-format count reported by each host.
- [ ] Oracle and live harness results recorded (pass, or the redacted field diff).
- [ ] Coverage block regenerated; manifest updated.
- [ ] No private URL, cookie, token, or `googlevideo` URL added to the repository.

## Evidence / notes

Not started.
