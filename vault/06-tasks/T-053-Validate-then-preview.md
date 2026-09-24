---
id: T-053
type: task
priority: P0
milestone: D3
tags: [task, ui]
---

# T-053 — Validate, then metadata preview

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

Submitting the link field checks that the text is one compatible HTTP(S) URL. A bad value stays on the field with an error. A compatible URL opens the existing metadata preview. Nothing downloads yet.

## Dependencies

- [T-052](T-052-Link-only-home.md).

## Context the next session needs

Reuse `SourceUrlValidator` and `ClipboardLink.compatibleUrl`. Do not add a second URL type. [PreviewScreen.kt](../../shared/ui/src/commonMain/kotlin/com/anydownlod/ui/preview/PreviewScreen.kt) already loads title, thumbnail, and metadata; open that screen. Do not start a job from the field.

## Work

- Submit accepts one HTTP(S) URL. Reject blank, non-HTTP(S), and userinfo URLs with the existing validator messages.
- A compatible URL sets the preview target and shows `PreviewScreen`.
- Pasting or typing alone does not submit and does not start a download.
- A failed preview load stays on the preview with a redacted error and a way back to the field. Do not pretend a job exists.

## Acceptance criteria

- [ ] UI tests: an invalid string stays on the field with an error; `https://example.com/watch` opens the preview; the job list is unchanged.
- [ ] No clipboard read was added back.
- [ ] `./gradlew :shared:ui:jvmTest` passes.

## Evidence / notes

Not started. Use fixture hosts only. Do not paste private URLs.
