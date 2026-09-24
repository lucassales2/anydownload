---
id: T-049
type: task
priority: P0
milestone: D3
tags: [task, ios, engine]
---

# T-049 — iOS downloads a matching HTML page

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

The iOS host downloads the media file referenced by a local HTML fixture into the sandbox. Unresolved HTML still shows extractor-not-implemented. No AVFoundation work.

## Dependencies

- [T-046](T-046-Engine-uses-generic-extractor.md).
- [T-042](T-042-Ios-http-download.md) — NSURLSession transfer and sandbox store stay.

## Work

- The shared extractor runs on the HTML body the iOS transfer already fetched. The media GET uses the same one-hop transfer.
- Foreground-only stays honest. Do not add a background URLSession.
- A site URL the subset cannot resolve still fails typed, with no Python and no CLI.

## Acceptance criteria

- [ ] An iOS test or simulator run downloads the fixture media and the job reaches COMPLETED with the file in the sandbox.
- [ ] Unresolved HTML fails typed.
- [ ] `./gradlew :shared:core:iosSimulatorArm64Test` passes, or the blocker and next action are written here.

## Evidence / notes

Not started.
