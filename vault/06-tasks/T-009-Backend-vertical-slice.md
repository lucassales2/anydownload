---
id: T-009
type: task
priority: P0
milestone: M1
tags: [task, engine, security]
---

# T-009 — Local engine vertical slice

[Home](../Home.md) · [Kanban](../Kanban.md) · [Architecture](../02-architecture/Architecture.md) · [Security](../04-delivery/Security-and-licensing.md)

## Outcome

One public URL downloads inside the app, with progress, a device file, and a clean failure. Covers foundational F-01/F-07/F-26 behavior. No server.

## Dependencies

- [T-007](T-007-Define-UX-and-contract.md), including the M0 exit gate.

## Acceptance criteria

- [ ] Accept one public URL, report progress, and write a finalized file on the device.
- [ ] Keep paths inside the app storage root. Do not log cookies or signed media URLs.
- [ ] Bound work and reject unsafe option/path input.
- [ ] Test extraction, cancellation, and failure with integration tests. Postprocessing uses the media toolkit chosen for that target.

## Evidence / notes

Not started. Do not add a server or shell out to the Python yt-dlp CLI from common code.
