---
id: T-067
type: task
priority: P0
milestone: D4
tags: [task, web, engine]
---

# T-067 — Web: extension carries extractor requests; page saves the chosen format

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [T-050](T-050-Web-extension-generic.md)

## Outcome

With the extension loaded, the Compose/Wasm page previews and downloads a YouTube single video. The page never fetches an origin; the extension performs the innertube `POST`, and the browser's downloader saves the selected format. Without the extension, Add refuses.

## Dependencies

- [T-063](T-063-Preview-from-extractor.md), [T-056](T-056-Http-request-port.md).

## Context the next session needs

MV3 `fetch` cannot set some headers (`User-Agent`, `Origin`, `Referer` variants). The `visionos` client is identified by the JSON context and `X-YouTube-Client-Name`/`Version` headers, which the extension can set. Record what the browser refused in the reply and in the README. `chrome.downloads.download` accepts a URL and headers; ranged chunking is not available there, so the web host downloads the format URL whole through the browser downloader and reports the browser's own progress, as in D2.

## Work

- `background.js`: `fetch-request` message (method, headers, body, range) with `checkUrl` on the URL before the request, bounded reads for text/JSON bodies, and the effective headers in the reply. Keep `probe`, `fetch-page`, and `download` messages.
- `WindowExtensionBridge` and `WebExtensionTransfer`: implement the request port over the bridge.
- `WebExtensionEngine`: registry first; matched URL → extract via the bridge transfer → select → `download` message with the format URL and its `httpHeaders` (allowlisted); unmatched → existing D2/D3 behavior.
- Host permissions: confirm the manifest already covers `youtube.com`, `googlevideo.com`, and `ytimg.com`; if not, add them and record the change.
- Tests: `node --test` for the new message (method/body arrive, policy refusal, refused-header report); `WebExtensionEngineTest` for the YouTube path with a fake bridge; page source still has no `fetch(`.
- Real Chromium run with the extension, a public YouTube URL, and the CDP harness from T-050: preview, audio download completes in `chrome.downloads`, file on disk. Record browser and extension versions.

## Acceptance criteria

- [ ] `node --test apps/web-extension/test/bridge.test.mjs` passes with the new cases.
- [ ] `WebExtensionEngineTest` covers matched, unmatched, and missing-extension paths.
- [ ] Real Chromium run recorded: extension service worker performed the innertube `POST` and the media `GET`; the page performed no fetch.
- [ ] README lists the headers MV3 refused and that web progress is the browser's.

## Evidence / notes

Not started.
