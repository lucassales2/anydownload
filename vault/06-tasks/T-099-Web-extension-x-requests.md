---
id: T-099
type: task
priority: P0
milestone: D7
tags: [task, web, extension, twitter]
---

# T-099 — Web: the extension carries the X lookup and the media GET

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 7](../00-project/Phase-7-X-Twitter.md) · [T-096](T-096-Gate-selected-media-download.md)

## Outcome

Web registers `TwitterIE` behind the extension request port and mirrors the selection download path: the guest lookup and every selected media GET go through the extension, and the page performs no `x.com`, `twitter.com`, or syndication/CDN fetch of its own.

## Dependencies

- [T-096](T-096-Gate-selected-media-download.md).

## Work

- `WebAppGraph`: add `TwitterIE(extensionHttp)` to `extractorRegistry` beside `YoutubeIE`.
- `WebExtensionEngine.extractAndDownloadViaBridge`: mirror T-096's media path — when the extraction has `media`, require `selectedMediaIds`, re-resolve each selected media's format, and hand each selected media URL to the bridge downloader so the browser saves one file per selected video. An empty selection fails typed before any media URL is sent.
- Extension: the guest lookup is an ordinary `fetch` through the bridge request port. The declared `User-Agent: Googlebot` is dropped by MV3's forbidden-header list; the browser's own user agent is sent instead, and the effective-header reply already reports that difference. Record it as the web gap; the fixture path must not depend on setting the header. No cookie and no `authorization` header ever rides this lookup.
- Confirm the media GET rides the existing browser-download path (`chrome.downloads`) and needs no page fetch.
- Tests: the Node bridge test gains a case where a syndication lookup with an id/token query is fetched by the service worker with no `cookie` header and the response is redacted to the page; the wasm browser test previews the fixture status, selects both videos, and downloads two files through the extension fakes; a page-source check (like the existing "the Compose/Wasm page source performs no fetch call" test) asserts no X/Twitter origin appears in a page `fetch`.

## Acceptance criteria

- [x] Web's registry includes `TwitterIE`; a matched status previews over the extension request port.
- [x] The extension carries the guest lookup and the selected media GETs; one file per selected video.
- [x] The page performs zero X/Twitter/syndication/CDN fetches.
- [x] The MV3 `user-agent` drop is recorded as a web gap; no cookie or `authorization` is sent for this lookup.
- [x] `:shared:core:wasmJsBrowserTest` and `node --test apps/web-extension/test/bridge.test.mjs` pass.

## Evidence / notes

Done 2026-09-25.

What landed:

- `apps/web/src/wasmJsMain/kotlin/com/anydownlod/web/WebAppGraph.kt`: registers `TwitterIE(extensionHttp)` beside `YoutubeIE`; the registry serves preview and download over the extension request port.
- `WebExtensionEngine`: `downloadViaBridge` is split into `bridgeDownload` (policy check plus one extension download) and completion, so the new `downloadSelectedMediaViaBridge` mirrors T-096: an empty selection fails `INVALID_URL_OPTIONS` before any media URL; a stale selected id fails `UNAVAILABLE_OR_PRIVATE`; each selected media resolves its own format and is handed to `bridge.download(..., saveViaBlob = true)`; each completed file is appended as an artifact on the status job and the job completes once all are saved. Web has no toolkit, so merge and audio extract fail typed.
- Node bridge tests: `fetch-request carries the X syndication lookup without a cookie or authorization` (the lookup is fetched, a cookie header is refused, MV3 drops `user-agent`, and no authorization rides along) and `the page source never fetches an X or Twitter origin`.
- Wasm tests: `WebExtensionEngineTest.aStatusSelectionSavesOneFilePerSelectedVideo` and `anEmptyStatusSelectionFailsBeforeAnyMediaUrl`.
- MV3 gap recorded: the extractor's declared `User-Agent: Googlebot` is dropped by the extension's forbidden-header list; the browser's own user agent is sent and the effective-header reply reports the difference. The fixture path does not depend on the header. No cookie or `authorization` is sent for this lookup.

Verification run 2026-09-25:

- `node --test apps/web-extension/test/bridge.test.mjs` — 25 tests, 0 failures.
- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest` — 435 tests, 0 failures (`WebExtensionEngineTest` 16 / 0).
- `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :shared:core:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:web:wasmJsBrowserDistribution` — BUILD SUCCESSFUL; core JVM 483 / 0, UI JVM 90 / 0, core iOS 446 / 0, android-engine 22 / 0, and `web.js` produced.
- The page source has no `fetch` call at all (existing check), and the new check confirms no X/Twitter origin appears in a page fetch.
