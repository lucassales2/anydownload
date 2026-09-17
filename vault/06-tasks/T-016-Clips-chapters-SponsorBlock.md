---
id: T-016
type: task
priority: P1
milestone: M3
tags: [task, media, parity]
---

# T-016 — Clips, chapters and SponsorBlock

[Home](../Home.md) · [Kanban](../Kanban.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

F-16/F-17 advanced media output plus chapter naming/delivery aspects of F-11/F-13.

## Dependencies

- [T-013](T-013-Media-formats.md).
- [T-014](T-014-Storage-and-delivery.md).

## Acceptance criteria

- [ ] Validate clip start/end and URL timestamp precedence; document accuracy, keyframe/transcode costs and invalid-range errors.
- [ ] Split available chapters using confined templates and expose every chapter artifact with size/open/download actions.
- [ ] Implement opt-in SponsorBlock behavior with clear unavailable-marker/source/error handling.
- [ ] Test interactions among clipping, splitting, presets and cancellation; prevent unsafe postprocessor or template injection.

## Evidence / notes

Not started. Extra external service use and format limitations must be visible to users.
