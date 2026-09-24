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

- [x] `node --test apps/web-extension/test/bridge.test.mjs` passes with the new cases.
- [x] `WebExtensionEngineTest` covers matched, unmatched, and missing-extension paths.
- [x] Real Chromium run recorded: extension service worker performed the innertube `POST` and the media `GET`; the page performed no fetch.
- [x] README lists the headers MV3 refused and that web progress is the browser's.

## Evidence / notes

Done 2026-09-24.

- `WebExtensionEngine` takes the `ExtractorRegistry`; a matched URL is extracted through the bridge request port, one format is selected with the shared selector, and the selected URL plus its allowlisted `httpHeaders` go to the `download` message with `saveViaBlob`. Unmatched URLs keep the D2/D3 probe path; a missing extension still fails `ENGINE_UNAVAILABLE`. `WebExtensionBridge.download`/`WindowExtensionBridge` carry headers and the flag; `background.js` forwards the sanitized/effective set.
- `WebAppGraph` shares one `WindowExtensionBridge` + `ExtractorRegistry(YoutubeIE(ExtractorHttp(WebExtensionTransfer(bridge))))` across the engine and `ExtractorMediaPreviewSource`.
- MV3 forces `Origin` to the extension origin on the innertube `POST`, and YouTube answers **403** to `chrome-extension://…` and `http://127.0.0.1:…` origins (curl-verified; `https://www.youtube.com` returns 200). A static `declarativeNetRequest` ruleset (`rules.json`, `declarativeNetRequest` permission) sets `Origin: https://www.youtube.com` and the visionos user agent on `youtube.com/youtubei/*`; no cookies, PO tokens, or signed URLs are touched.
- The direct `chrome.downloads` fetch of a signed `googlevideo` URL is unreliable in this environment (`NETWORK_FAILED`/stalls), so matched formats use the new offscreen saver: `offscreen.html`/`offscreen.js` fetch the media with the extension's host permissions, build a `File`, and return an extension-origin Blob URL; the service worker hands that URL to `chrome.downloads` (the downloads API is not exposed in offscreen documents). The page never fetches.
- Also fixed in `background.js`: `isPrivateIpv4` misread any four-label DNS name (for example `cdn.fixtures.example.net`) as an IPv4 literal and blocked it; the check now requires all-numeric parts.
- Tests: `node --test apps/web-extension/test/bridge.test.mjs` → **22 pass** (new: the DNR rule/manifest resource, the `download` header allowlist, the offscreen route and Blob URL, the offscreen save, and the page-source `fetch(` scan); `WebExtensionEngineTest` adds matched extract/select/save (no probe, format URL, allowlisted headers, `saveViaBlob`) and matched-failure typed without probing, alongside the existing unmatched/missing-extension cases; `:shared:core:jvmTest` and `:apps:web:compileKotlinWasmJs` green.
- Real Chromium run (Brave 153.1.95.104, Chromium 153.0.8010.53): the extension service worker fetched the watch page (≈1.2 MB, visitor data found), performed the innertube `POST` → `status=OK`, 27 formats; a `fetch-request` media `GET` returned `206` (65 KiB range); the offscreen saver fetched the audio and `chrome.downloads` finished `state=complete`, **3,638,963 bytes on disk**; after a page reload with `Network` enabled the page made **zero** youtube/googlevideo/ytimg requests. `T-067 CDP RUN OK`.
- Minor web caveat recorded: the browser names a blob-URL save with the blob UUID rather than the extension's suggested filename; the job row reports the suggested name, so the browser's saved file name can differ.
- README and the extension README list the MV3-refused headers, the DNR rewrite, and that web progress is the browser's downloader's.
