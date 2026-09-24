---
id: T-050
type: task
priority: P0
milestone: D3
tags: [task, web, engine]
---

# T-050 — Web extension fetches the page and the media

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

With the extension loaded, a matching HTML fixture saves the media file. The Compose/Wasm page performs no cross-origin fetch. Without the extension, Add refuses.

## Dependencies

- [T-045](T-045-Generic-extractor-subset.md) — the Kotlin extractor.
- [T-043](T-043-Web-extension-download.md) — MV3 bridge and direct-file save stay.

## Work

- The extension fetches the HTML. The page runs the shared extractor on those bytes and asks the extension to save the chosen media URL.
- The extension applies the same URL policy before either fetch. It does not run a second extractor in JavaScript.
- Zero-match and two-match pages fail typed and save nothing.
- Direct-file downloads from D2 still work.
- Extension absent: Add reports that the extension is required and the page makes no network call.

## Acceptance criteria

- [ ] Extension tests: matching fixture saves the media bytes; non-matching HTML saves nothing; direct file still saves.
- [ ] A real Chromium load shows the service worker fetched the page and the media. The page source still has no `fetch(` of an origin.
- [ ] `node --test apps/web-extension/test/bridge.test.mjs` passes.

## Evidence / notes

Not started. Do not paste private URLs. Use the local fixture host.
