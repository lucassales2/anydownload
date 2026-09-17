---
id: T-025
type: task
priority: P2
milestone: M5
tags: [task, research, android]
---

# T-025 — Investigate an optional Android local engine

[Home](../Home.md) · [Kanban](../Kanban.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

A go/no-go ADR for local Android execution without assuming a desktop JVM wrapper solves native runtime packaging.

## Dependencies

- [T-004](T-004-Validate-KMP-targets.md).
- [T-005](T-005-Choose-backend-engine.md).
- [T-006](T-006-Review-security-licensing.md).
- [T-010](T-010-Remote-vertical-slice.md).

## Acceptance criteria

- [ ] Evaluate maintained Android-compatible runtime approaches with yt-dlp/Python, FFmpeg and required JS components on target ABIs/OS versions.
- [ ] Test execution restrictions, packaging size, battery/thermal/network impact, cancellation and foreground/background/notification requirements on devices.
- [ ] Validate scoped storage, secret handling, updates and all redistributed dependency/store-policy obligations.
- [ ] Document capabilities, maintenance costs, local/remote UX and owner decision; no claim of iOS/web standalone equivalence.

## Evidence / notes

Not started; optional and non-blocking for remote release. Any further runtime/library adoption requires a new reviewed decision.
