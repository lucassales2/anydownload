---
id: T-020
type: task
priority: P1
milestone: D15
tags: [task, ux, integrations]
---

# T-020 — Platform sharing, integrations and adaptive UX

[Home](../Home.md) · [Kanban](../Kanban.md) · [User flows](../01-product/User-flows.md)

## Outcome

F-22/F-23 adaptive settings/UI and approved MeTube-equivalent link-submission integrations.

## Dependencies

- [T-010](T-010-Remote-vertical-slice.md).
- [T-014](T-014-Storage-and-delivery.md).
- [T-017](T-017-Options-and-presets.md). Phase: [Phase 15](../00-project/Phase-15-Subscriptions-sharing.md).

## Acceptance criteria

- [ ] Implement mobile sharing/deep-link paths and approved browser extension/bookmarklet/shortcut/desktop integration deliverables from T-003.
- [ ] Specify compatibility with existing MeTube ecosystem clients or document equivalent alternatives; no implicit protocol compatibility claim.
- [ ] Provide responsive layouts, theme modes, remembered local options, bulk actions, safe copy/export, toasts and engine/app versions.
- [ ] Test origin/HTTPS/auth boundaries for external submissions, user consent, clipboard denial and inaccessible server states.
- [ ] Validate keyboard/screen-reader, focus, large text and contrast across target families.

## Evidence / notes

Not started. Scheduled 2026-09-29 as D15 in [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md). Share entry points are Android share, the iOS share sheet, desktop paste, and the existing web extension. Companion integrations beyond those stay separate deliverables.
