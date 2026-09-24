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

- [x] README or the task Evidence explains unpacked load steps.
- [x] A local or public direct-file fixture completes through the extension and appears in the page queue.
- [x] With the extension disabled, Add refuses to start and the page performs no cross-origin fetch.
- [x] No private URLs or cookies are committed.

## Evidence / notes

Done on 2026-09-23. This is the web download path for D2, not a MeTube companion from F-23.

Commands run:

- `node --test apps/web-extension/test/bridge.test.mjs` - 8 tests, 0 failures (blocklist, filename sanitization, probe classification, chrome.downloads handoff, loopback refusal, cancel, HTML refusal).
- `./gradlew :apps:web:wasmJsBrowserDistribution` - the page dist builds.
- Real-browser proof (Brave = Chromium, extension loaded via `--load-extension` + `--enable-unsafe-extension-debugging`): connected to the extension service worker via CDP and ran the same `chrome.downloads.download` path the bridge uses - the browser fetched the local fixture (`GET /files/tiny.bin 200` in the server log) and saved `tiny.bin` (1,048,576 bytes) to the configured download directory. The extension's real network path works in a real browser.
- `grep -rn 'fetch(' apps/web/src shared/ui/src/wasmJsMain` - **no matches**: the page performs no cross-origin fetch; only `window.postMessage` plus serialized JSON strings cross the page↔extension boundary.
- Sweep: `:apps:desktop:test` 92, `:shared:core:jvmTest` 100, `:shared:ui:jvmTest` 77, `:apps:android-engine-tests:test` 7 - all green; wasm compiles.

Queue evidence: `WebExtensionEngineTest` (common, JVM) proves the page queue lifecycle over a fake bridge - direct file -> COMPLETED with artifact; missing extension -> ENGINE_UNAVAILABLE with **no** probe/download call; HTML -> typed EXTRACTION_FAILURE; blocklisted final URL refused before download; cancel aborts via bridge; idempotent submit; unknown progress stays unknown.

Delivered:

- `apps/web-extension`: MV3 manifest (`downloads` + `host_permissions` http/https), `content.js` (marks `window.__anydownloadExtension`, relays page<->background), `background.js` (probe classification via HEAD + ranged-GET fallback; download hands the validated final URL to `chrome.downloads` - the browser streams and saves, the service worker never buffers a body byte, so private bytes never cross the extension context; cancel calls `chrome.downloads.cancel`; extension-side blocklist refuses loopback/private/credentials before any fetch), `README.md` with unpacked-load steps and the manual click-through, and `node --test` unit coverage.
- `shared/core`: `WebExtensionBridge` port + `WebExtensionEngine` (page queue owner) with 7 tests.
- `apps/web`: `WindowExtensionBridge` (JSON-string postMessage protocol; Kotlin/Wasm has no `dynamic`, so payloads use kotlinx.serialization) + `WebAppGraph`, wired in `Main.kt`. Without the extension, `available` is false and Add fails before any message.

Recorded gaps (not D2 blockers): static `http://*/* https://*/*` host permissions for now per-site `optional_host_permissions` prompting is a documentation-flagged improvement; Safari/Firefox best-effort; the page↔content-script relay could not be automated under `--remote-debugging` (Chromium does not inject content scripts into pages opened via CDP), so that hop is covered by unit tests + the documented manual click-through rather than an automated browser run; the browser saves to its default downloads folder and the page queue shows completion when the extension answers.
