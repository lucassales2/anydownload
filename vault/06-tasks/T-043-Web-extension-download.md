---
id: T-043
type: task
priority: P0
milestone: D2
tags: [task, web, extension]
---

# T-043 — Web extension download path

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md)

## Outcome

The Compose/Wasm page stays the UI. A Manifest V3 extension with host permissions fetches the direct file and saves it. The page never fetches arbitrary origins.

## Dependencies

- [T-039](T-039-Platform-http-and-files.md).

## Work

- Add `apps/web-extension` (MV3). Permissions: host access needed to fetch the user-submitted URL, plus `downloads`. Prefer optional host permissions if the browser allows prompting per site.
- Bridge: page requests a download; extension reports progress, completion, or failure. No server. No Socket.IO.
- Without the extension, Settings/Add show an unavailable state and do not create a fake completed job.
- Direct-file success uses `browser.downloads` / `chrome.downloads`. NeedsExtractor fails with the same typed error as iOS.
- Document how to load the unpacked extension in Chromium. Safari/Firefox support is best-effort in this task; record gaps rather than blocking the phase.
- Store publication of the extension is out of scope.

## Acceptance criteria

- [ ] README or the task Evidence explains unpacked load steps.
- [ ] A local or public direct-file fixture completes through the extension and appears in the page queue.
- [ ] With the extension disabled, Add refuses to start and the page performs no cross-origin fetch.
- [ ] No private URLs or cookies are committed.

## Evidence / notes

Not started. This is the web download path for D2, not a MeTube companion from F-23.
