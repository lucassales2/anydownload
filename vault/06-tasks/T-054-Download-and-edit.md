---
id: T-054
type: task
priority: P0
milestone: D3
tags: [task, ui]
---

# T-054 — Download and collapsible edit

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

The metadata preview has two actions: Download, and Edit download. Edit expands and collapses. The panel starts collapsed. Expanded content is video or audio, quality, and format.

## Dependencies

- [T-053](T-053-Validate-then-preview.md).

## Work

- **Download** starts the job with the options currently selected (defaults when the panel was never opened).
- **Edit download** toggles one panel. Collapsed hides the choices. Expanded shows media type (video or audio), quality, and format. Those choices feed the same allowlist options Download uses.
- Captions, clips, cookies, destination, and the disabled custom yt-dlp JSON stay out of this panel.
- Do not invent formats the preview metadata did not return. Unknown quality stays unknown.

## Acceptance criteria

- [ ] UI tests: the panel is collapsed on open; Edit download reveals video/audio, quality, and format; collapsing hides them; Download submits one job and does not require the panel to have been opened.
- [ ] The panel does not show captions, clips, cookies, or custom JSON.
- [ ] `./gradlew :shared:ui:jvmTest` passes.

## Evidence / notes

Not started.
