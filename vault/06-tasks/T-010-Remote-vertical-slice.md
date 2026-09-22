---
id: T-010
type: task
priority: P0
milestone: M1
tags: [task, integration, platforms]
---

# T-010 — Local flow on all four targets

[Home](../Home.md) · [Kanban](../Kanban.md) · [User flows](../01-product/User-flows.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

iOS, Compose/Wasm, Android, and desktop each submit a URL, show progress, and keep the file on the device. Initial F-01/F-07 evidence. No login.

## Dependencies

- [T-008](T-008-Scaffold-KMP-clients.md).
- [T-009](T-009-Backend-vertical-slice.md).

## Acceptance criteria

- [ ] Record end-to-end runs on every target family with exact OS/browser/build versions and which desktop OS was run.
- [ ] Write the file as it arrives, without holding the whole media file in memory. Cancelling leaves no completed file.
- [ ] Show a failed or unsupported URL. Closing the app interrupts active work and restores the queue.
- [ ] Link safe logs, screenshots, and tests, and document target limitations.

## Evidence / notes

Not started. This is a vertical slice, not full yt-dlp or MeTube parity.
