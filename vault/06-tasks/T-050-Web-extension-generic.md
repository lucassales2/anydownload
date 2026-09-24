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

- [x] Extension tests: matching fixture saves the media bytes; non-matching HTML saves nothing; direct file still saves.
- [x] A real Chromium load shows the service worker fetched the page and the media. The page source still has no `fetch(` of an origin.
- [x] `node --test apps/web-extension/test/bridge.test.mjs` passes.

## Evidence / notes

Done 2026-09-24.

- Extension (`background.js`): new `fetch-page` message — policy check before the GET (same `checkUrl` as probe/download), bounded 512 KiB read into a redacted text payload plus the FINAL url after the browser's redirects; replies `fetch-page-reply kind=final`. No extractor runs in JavaScript; the page's Kotlin extractor is the only one. Added a CDP verification hook (`self.__anydownloadBridge`, the exact bridge instance, unreachable from web pages because the service worker context is isolated). `content.js` relays the new message type already (generic passthrough).
- Page bridge (`WindowExtensionBridge`): `fetchPage` over the same postMessage JSON protocol (`fetch-page`/`fetch-page-reply`, `html` wire field); the page performs no fetch of any origin.
- Engine (`WebExtensionEngine`): on a `NEEDS_EXTRACTOR` probe, `bridge.fetchPage(finalUrl)` → revalidate the final page URL with `UrlPolicy` → `GenericExtractor.extract(pageUrl, html)` → `Direct` is saved through the unchanged D2 `downloadViaBridge` (media URL policy-checked again; the extension's own `checkUrl` runs again before the browser downloads it); `Failed` → typed, redacted `EXTRACTION_FAILURE`. Direct files keep the D2 path; missing extension still fails `ENGINE_UNAVAILABLE` with zero bridge calls.
- Extension tests (`node --test apps/web-extension/test/bridge.test.mjs`): 12 tests, 0 failures — new `fetch-page` cases (final reply with html, 512 KiB caps, loopback refusal without fetch, non-ok page fails without bytes) plus the existing 8.
- JVM engine tests (`WebExtensionEngineTest`): new `matchingHtmlPageSavesTheMediaUrl` (fetchPage called, media URL handed to the bridge download, COMPLETED with artifact), `twoMatchHtmlPageFailsTypedAndSavesNothing` (EXTRACTION_FAILURE, no download), `pageFetchFailureMapsTyped`; the old HTML probe test now goes through fetchPage and still fails typed with no download; direct-file and missing-extension tests unchanged. `:shared:core:jvmTest` — 127 tests, 0 failures; `:apps:web:wasmJsBrowserDistribution` / `compileKotlinWasmJs` BUILD SUCCESSFUL.
- Real Chromium proof (Brave = Chromium, this machine): launched with `--load-extension=apps/web-extension`, `--host-resolver-rules="MAP fixtures.example.test 127.0.0.1"` (fixture host passes the extension's own policy check while resolving locally), a local fixture server with request log, and CDP. The driver found the extension service worker (`chrome-extension://…/background.js`) and drove the real bridge: `fetch-page` returned the fixture HTML (bounded: false — page is small), `download` returned completed, and the fixture server log recorded `GET /watch` and `GET /media/clip.bin`. The browser's downloader saved the media: `Browser.setDownloadBehavior` + `chrome.downloads.search` showed state `complete`, `filename /tmp/anydl-t050-dl/clip.bin`, 262144/262144 bytes, and the file existed on disk with that exact size. (Headless mode alone refused the save as USER_CANCELED — the harness needed the standard CDP download-behavior override, same as any automation setup.) All scratch processes/files removed afterwards.
- Page source check: `grep -rn 'fetch(' apps/web/src shared/ui/src/wasmJsMain` — no matches.
